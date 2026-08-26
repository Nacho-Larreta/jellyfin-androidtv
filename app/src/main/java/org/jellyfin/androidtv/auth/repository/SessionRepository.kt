package org.jellyfin.androidtv.auth.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.model.AuthenticationActiveProfileSession
import org.jellyfin.androidtv.auth.model.AuthenticationSessionEnvelope
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.session.ActiveProfileCredential
import org.jellyfin.androidtv.auth.session.SessionSnapshot
import org.jellyfin.androidtv.auth.store.AuthenticationAuthoritySnapshot
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
	val sessionEpoch: Long = 0,
	val deviceId: String? = null,
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
	suspend fun installCommittedSession(snapshot: SessionSnapshot): Boolean
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

	override suspend fun installCommittedSession(snapshot: SessionSnapshot): Boolean = withContext(NonCancellable) {
		currentSessionMutex.withLock {
			val authority = authenticationStore.getAuthoritySnapshot(snapshot.serverId)
			val durable = authority?.envelope
			if (durable?.matches(snapshot) != true) {
				return@withLock false
			}
			_state.value = SessionRepositoryState.SWITCHING_SESSION
			val installed = setCurrentSessionLocked(snapshot.toSession(), authority)
			_state.value = if (installed) SessionRepositoryState.READY else SessionRepositoryState.INVALIDATING_SESSION
			installed
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

	private suspend fun setCurrentSessionLocked(
		session: Session?,
		committedAuthority: AuthenticationAuthoritySnapshot? = null,
	): Boolean {
		var server: Server? = null
		val expectedAuthority = session?.let { committedAuthority ?: authenticationStore.getAuthoritySnapshot(it.serverId) }

		if (session != null) {
			if (expectedAuthority == null) return false
			authenticationPreferences[AuthenticationPreferences.lastServerId] = session.serverId.toString()
			authenticationPreferences[AuthenticationPreferences.lastUserId] = session.userId.toString()
			authenticationPreferences[AuthenticationPreferences.lastOwnerUserId] = session.ownerUserId?.toString().orEmpty()

			server = serverRepository.getServer(session.serverId, true)
			if (server == null || !server.versionSupported) return false
		}

		val deviceInfo = session.resolveDeviceInfo(defaultDeviceInfo)
		Timber.i("Updating current session. userId=${session?.userId} server=${server?.serverVersion}")

		val previousApiBinding = captureApiClientBinding()
		val applied = userApiClient.applySession(session, deviceInfo)
		if (applied && session != null) {
			val authority = checkNotNull(expectedAuthority)
			try {
				val user = withContext(Dispatchers.IO) {
					userApiClient.userApi.getCurrentUser().content
				}
				if (user.id != session.userId) {
					throw SessionIdentityMismatchException()
				}
				if (!persistActiveSession(session, authority, committedAuthority != null)) {
					throw SessionPersistenceException()
				}
				userRepository.setCurrentUser(user)
				serverRepository.setCurrentServer(server)
			} catch (err: ApiClientException) {
				Timber.e(err, "Unable to authenticate: bad response when getting user info")
				_state.value = SessionRepositoryState.INVALIDATING_SESSION
				restoreApiClientBinding(previousApiBinding)
				destroyCurrentSessionLocked()
				return false
			} catch (err: SessionPersistenceException) {
				Timber.e(err, "Unable to durably install the authenticated session")
				_state.value = SessionRepositoryState.INVALIDATING_SESSION
				restoreApiClientBinding(previousApiBinding)
				return false
			} catch (err: SessionIdentityMismatchException) {
				Timber.e(err, "Authenticated identity does not match the requested session")
				_state.value = SessionRepositoryState.INVALIDATING_SESSION
				restoreApiClientBinding(previousApiBinding)
				return false
			}

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
			val durable = authenticationStore.getAuthoritySnapshot(lastServerId)?.envelope
			val durableActive = durable?.activeProfile
			if (durableActive != null && durableActive.profileUserId == lastUserId) {
				return Session(
					userId = durableActive.profileUserId,
					serverId = lastServerId,
					accessToken = durableActive.accessToken,
					ownerUserId = durableActive.ownerUserId,
					profileSelectorId = durableActive.profileSelectorId,
					sessionEpoch = durable.sessionEpoch,
					deviceId = durableActive.deviceId,
				)
			}
			createUserSession(
				serverId = lastServerId,
				userId = lastUserId,
				ownerUserId = lastOwnerUserId,
				profileSelectorId = authenticationStore.getUser(lastServerId, lastUserId)?.profileSelectorId,
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

	private fun persistActiveSession(
		session: Session,
		expectedAuthority: AuthenticationAuthoritySnapshot,
		committed: Boolean,
	): Boolean {
		if (committed) {
			val durableEnvelope = expectedAuthority.envelope ?: return false
			return authenticationStore.replaceSessionEnvelope(
				session.serverId,
				expectedAuthority,
				durableEnvelope,
			) != null
		}
		val currentEnvelope = expectedAuthority.envelope
		val nextEnvelope = AuthenticationSessionEnvelope(
			activeProfile = AuthenticationActiveProfileSession(
				profileUserId = session.userId,
				accessToken = session.accessToken,
				deviceId = session.deviceId ?: userApiClient.deviceInfo.id,
				profileSelectorId = session.profileSelectorId,
				ownerUserId = session.ownerUserId,
			),
			ownerRecovery = currentEnvelope?.ownerRecovery,
			sessionEpoch = session.sessionEpoch,
			pendingSwitch = currentEnvelope?.pendingSwitch,
			cleanupMarker = currentEnvelope?.cleanupMarker,
		)
		return authenticationStore.replaceSessionEnvelope(
			session.serverId,
			expectedAuthority,
			nextEnvelope,
			requireActiveUserToken = true,
		) != null
	}
}

private fun Session?.resolveDeviceInfo(defaultDeviceInfo: DeviceInfo): DeviceInfo = when {
	this == null -> defaultDeviceInfo
	deviceId != null -> defaultDeviceInfo.copy(id = deviceId)
	else -> defaultDeviceInfo.forUser(userId)
}

private fun AuthenticationSessionEnvelope.matches(snapshot: SessionSnapshot): Boolean {
	if (cleanupMarker == null || sessionEpoch != snapshot.sessionEpoch) return false
	val active = activeProfile ?: return false
	return DurableActiveIdentity(
		profileUserId = active.profileUserId,
		deviceId = active.deviceId,
		accessToken = active.accessToken,
		profileSelectorId = active.profileSelectorId,
		ownerUserId = active.ownerUserId,
	) == DurableActiveIdentity(
		profileUserId = snapshot.profileUserId,
		deviceId = snapshot.deviceId,
		accessToken = snapshot.credential.value,
		profileSelectorId = snapshot.profileSelectorId,
		ownerUserId = snapshot.ownerUserId,
	)
}

private data class DurableActiveIdentity(
	val profileUserId: UUID,
	val deviceId: String,
	val accessToken: String,
	val profileSelectorId: UUID?,
	val ownerUserId: UUID?,
)

private fun SessionSnapshot.toSession() = Session(
	userId = profileUserId,
	serverId = serverId,
	accessToken = credential.token(),
	ownerUserId = ownerUserId,
	profileSelectorId = profileSelectorId,
	sessionEpoch = sessionEpoch,
	deviceId = deviceId,
)

private fun ActiveProfileCredential.token(): String = value

private class SessionPersistenceException : IllegalStateException("Unable to persist active session.")

private class SessionIdentityMismatchException : IllegalStateException("Authenticated identity mismatch.")

private data class ApiClientBindingSnapshot(
	val baseUrl: String?,
	val accessToken: String?,
	val clientInfo: ClientInfo,
	val deviceInfo: DeviceInfo,
)
