package org.jellyfin.androidtv.auth.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.store.AuthenticationPreferences
import org.jellyfin.androidtv.auth.store.AuthenticationStore
import org.jellyfin.androidtv.preference.PreferencesRepository
import org.jellyfin.androidtv.preference.TelemetryPreferences
import org.jellyfin.androidtv.preference.constant.UserSelectBehavior.DISABLED
import org.jellyfin.androidtv.preference.constant.UserSelectBehavior.LAST_USER
import org.jellyfin.androidtv.preference.constant.UserSelectBehavior.SPECIFIC_USER
import org.jellyfin.androidtv.util.sdk.forUser
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.clientLogApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID

data class Session(
	val userId: UUID,
	val serverId: UUID,
	val accessToken: String,
	val ownerUserId: UUID? = null,
	val profileSelectorId: UUID? = null,
)

enum class SessionRepositoryState {
	READY,
	RESTORING_SESSION,
	SWITCHING_SESSION,
	INVALIDATING_SESSION,
}

interface SessionRepository {
	val currentSession: StateFlow<Session?>
	val state: StateFlow<SessionRepositoryState>

	suspend fun restoreSession(destroyOnly: Boolean)
	suspend fun switchCurrentSession(serverId: UUID, userId: UUID): Boolean
	suspend fun switchCurrentSession(session: Session): Boolean
	suspend fun prepareForProfileSelection()
	suspend fun destroyCurrentSession()
}

class SessionRepositoryImpl(
	private val authenticationPreferences: AuthenticationPreferences,
	private val authenticationStore: AuthenticationStore,
	private val userApiClient: ApiClient,
	private val preferencesRepository: PreferencesRepository,
	private val defaultDeviceInfo: DeviceInfo,
	private val userRepository: UserRepository,
	private val serverRepository: ServerRepository,
	private val telemetryPreferences: TelemetryPreferences,
	private val playbackQuiescePort: PlaybackQuiescePort,
) : SessionRepository {
	private val currentSessionMutex = Mutex()
	private val _currentSession = MutableStateFlow<Session?>(null)
	override val currentSession = _currentSession.asStateFlow()
	private val _state = MutableStateFlow(SessionRepositoryState.READY)
	override val state = _state.asStateFlow()

	override suspend fun restoreSession(destroyOnly: Boolean): Unit = withContext(NonCancellable) {
		currentSessionMutex.withLock {
			Timber.i("Restoring session")

			_state.value = SessionRepositoryState.RESTORING_SESSION

			val alwaysAuthenticate = authenticationPreferences[AuthenticationPreferences.alwaysAuthenticate]
			val autoLoginBehavior = authenticationPreferences[AuthenticationPreferences.autoLoginUserBehavior]

			when {
				alwaysAuthenticate -> destroyCurrentSessionLocked()
				autoLoginBehavior == DISABLED -> destroyCurrentSessionLocked()
				autoLoginBehavior == LAST_USER && !destroyOnly -> setCurrentSessionLocked(createLastUserSession())
				autoLoginBehavior == SPECIFIC_USER && !destroyOnly -> {
					val serverId = authenticationPreferences[AuthenticationPreferences.autoLoginServerId].toUUIDOrNull()
					val userId = authenticationPreferences[AuthenticationPreferences.autoLoginUserId].toUUIDOrNull()
					if (serverId != null && userId != null) setCurrentSessionLocked(createUserSession(serverId, userId))
				}
			}

			_state.value = SessionRepositoryState.READY
		}
	}

	override suspend fun switchCurrentSession(serverId: UUID, userId: UUID): Boolean {
		val session = createUserSession(serverId, userId)
			?: run {
				Timber.w("Could not switch to non-existing session for user $userId")
				return false
			}

		return switchCurrentSession(session)
	}

	override suspend fun switchCurrentSession(session: Session): Boolean = withContext(NonCancellable) {
		currentSessionMutex.withLock {
			// No change in session - don't switch
			if (currentSession.value == session) {
				Timber.d("Current session is the same as the requested session")
				return@withLock true
			}

			_state.value = SessionRepositoryState.SWITCHING_SESSION
			Timber.i("Switching current session to user ${session.userId}")

			val switched = setCurrentSessionLocked(session)
			_state.value = SessionRepositoryState.READY
			switched
		}
	}

	override suspend fun prepareForProfileSelection(): Unit = withContext(NonCancellable) {
		currentSessionMutex.withLock {
			playbackQuiescePort.quiesceIfCreated()
		}
	}

	override suspend fun destroyCurrentSession(): Unit = withContext(NonCancellable) {
		currentSessionMutex.withLock {
			destroyCurrentSessionLocked()
		}
	}

	private suspend fun destroyCurrentSessionLocked() {
		Timber.i("Destroying current session")

		_state.value = SessionRepositoryState.INVALIDATING_SESSION
		playbackQuiescePort.quiesceIfCreated()
		setCurrentSessionLocked(null)
		_state.value = SessionRepositoryState.READY
	}

	private suspend fun setCurrentSessionLocked(session: Session?): Boolean {
		var server: Server? = null

		if (session != null) {
			// Update last active user
			authenticationPreferences[AuthenticationPreferences.lastServerId] = session.serverId.toString()
			authenticationPreferences[AuthenticationPreferences.lastUserId] = session.userId.toString()
			authenticationPreferences[AuthenticationPreferences.lastOwnerUserId] = session.ownerUserId?.toString().orEmpty()

			// Check if server version is supported
			server = serverRepository.getServer(session.serverId, true)
			if (server == null || !server.versionSupported) return false
		}

		// Update session after binding the apiclient settings
		val deviceInfo = session?.let { defaultDeviceInfo.forUser(it.userId) } ?: defaultDeviceInfo
		Timber.i("Updating current session. userId=${session?.userId} server=${server?.serverVersion}")

		val previousApiBinding = captureApiClientBinding()
		val applied = userApiClient.applySession(session, deviceInfo)
		if (applied && session != null) {
			try {
				val user = withContext(Dispatchers.IO) {
					userApiClient.userApi.getCurrentUser().content
				}
				userRepository.setCurrentUser(user)
				serverRepository.setCurrentServer(server)
			} catch (err: ApiClientException) {
				Timber.e(err, "Unable to authenticate: bad response when getting user info")
				_state.value = SessionRepositoryState.INVALIDATING_SESSION
				restoreApiClientBinding(previousApiBinding)
				destroyCurrentSessionLocked()
				return false
			}

			// Update crash reporting URL
			val crashReportUrl = userApiClient.clientLogApi.logFileUrl()
			telemetryPreferences[TelemetryPreferences.crashReportUrl] = crashReportUrl
			telemetryPreferences[TelemetryPreferences.crashReportToken] = session.accessToken
		} else {
			userRepository.setCurrentUser(null)
			serverRepository.setCurrentServer(null)
			telemetryPreferences[TelemetryPreferences.crashReportUrl] = ""
			telemetryPreferences[TelemetryPreferences.crashReportToken] = ""
		}
		preferencesRepository.onSessionChanged()
		_currentSession.value = session

		return true
	}

	private fun captureApiClientBinding() = if (_currentSession.value == null) {
		ApiClientBindingSnapshot(
			baseUrl = null,
			accessToken = null,
			clientInfo = userApiClient.clientInfo,
			deviceInfo = defaultDeviceInfo,
		)
	} else {
		ApiClientBindingSnapshot(
			baseUrl = userApiClient.baseUrl,
			accessToken = userApiClient.accessToken,
			clientInfo = userApiClient.clientInfo,
			deviceInfo = userApiClient.deviceInfo,
		)
	}

	private fun restoreApiClientBinding(binding: ApiClientBindingSnapshot) {
		userApiClient.update(
			baseUrl = binding.baseUrl,
			accessToken = binding.accessToken,
			clientInfo = binding.clientInfo,
			deviceInfo = binding.deviceInfo,
		)
	}

	private fun createLastUserSession(): Session? {
		val lastUserId = authenticationPreferences[AuthenticationPreferences.lastUserId].toUUIDOrNull()
		val lastServerId = authenticationPreferences[AuthenticationPreferences.lastServerId].toUUIDOrNull()
		val lastOwnerUserId = authenticationPreferences[AuthenticationPreferences.lastOwnerUserId].toUUIDOrNull()

		return if (lastUserId != null && lastServerId != null) {
			val restoreUserId = lastOwnerUserId ?: lastUserId
			createUserSession(
				serverId = lastServerId,
				userId = restoreUserId,
				ownerUserId = lastOwnerUserId,
				profileSelectorId = authenticationStore.getUser(lastServerId, restoreUserId)?.profileSelectorId,
			)
		}
		else null
	}

	private fun createUserSession(
		serverId: UUID,
		userId: UUID,
		ownerUserId: UUID? = null,
		profileSelectorId: UUID? = null,
	): Session? {
		val account = authenticationStore.getUser(serverId, userId)
		if (account?.accessToken == null) return null

		return Session(
			userId = userId,
			serverId = serverId,
			accessToken = account.accessToken,
			ownerUserId = ownerUserId ?: account.profileSelectorOwnerUserId,
			profileSelectorId = profileSelectorId ?: account.profileSelectorId,
		)
	}

	private fun ApiClient.applySession(session: Session?, newDeviceInfo: DeviceInfo = defaultDeviceInfo): Boolean {
		if (session == null) {
			update(
				baseUrl = null,
				accessToken = null,
				deviceInfo = newDeviceInfo,
			)
		} else {
			val server = authenticationStore.getServer(session.serverId)
				?: return false

			update(
				baseUrl = server.address,
				accessToken = session.accessToken,
				deviceInfo = newDeviceInfo,
			)
		}

		return true
	}
}

private data class ApiClientBindingSnapshot(
	val baseUrl: String?,
	val accessToken: String?,
	val clientInfo: ClientInfo,
	val deviceInfo: DeviceInfo,
)
