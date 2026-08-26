package org.jellyfin.androidtv.auth.repository

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.androidtv.auth.model.AuthenticationActiveProfileSession
import org.jellyfin.androidtv.auth.model.AuthenticationCommittedPendingCleanup
import org.jellyfin.androidtv.auth.model.AuthenticationPendingSwitch
import org.jellyfin.androidtv.auth.model.AuthenticationPendingSwitchPhase
import org.jellyfin.androidtv.auth.model.AuthenticationSessionEnvelope
import org.jellyfin.androidtv.auth.model.AuthenticationStoreServer
import org.jellyfin.androidtv.auth.model.AuthenticationStoreUser
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.session.ActiveProfileCredential
import org.jellyfin.androidtv.auth.session.SessionSnapshot
import org.jellyfin.androidtv.auth.store.AuthenticationAuthoritySnapshot
import org.jellyfin.androidtv.auth.store.AuthenticationPreferences
import org.jellyfin.androidtv.auth.store.AuthenticationStore
import org.jellyfin.androidtv.preference.PreferencesRepository
import org.jellyfin.androidtv.preference.TelemetryPreferences
import org.jellyfin.androidtv.preference.constant.UserSelectBehavior
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.operations.ClientLogApi
import org.jellyfin.sdk.api.operations.UserApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.UserDto
import java.util.UUID

class SessionRepositoryPlaybackTests : FunSpec({
	test("destroying a session quiesces playback before clearing identity") {
		val events = mutableListOf<String>()
		val fixture = SessionRepositoryFixture(events)
		val session = fixture.session()
		fixture.allowCurrentUserLookupFor(session)
		val repository = fixture.repository(RecordingPlaybackQuiescePort(events))
		repository.switchCurrentSession(session)
		events.clear()

		repository.destroyCurrentSession()

		events.shouldContainExactly(
			"quiesce-playback",
			"clear-api",
			"clear-user",
			"clear-server",
			"clear-crash-url",
			"clear-crash-token",
			"invalidate-preferences",
		)
		repository.currentSession.value shouldBe null
		repository.state.value shouldBe SessionRepositoryState.READY
	}

	test("always authenticate restore quiesces playback before clearing identity") {
		val events = mutableListOf<String>()
		val fixture = SessionRepositoryFixture(events).apply {
			every { authenticationPreferences[AuthenticationPreferences.alwaysAuthenticate] } returns true
			every {
				authenticationPreferences[AuthenticationPreferences.autoLoginUserBehavior]
			} returns UserSelectBehavior.LAST_USER
		}
		val session = fixture.session()
		fixture.allowCurrentUserLookupFor(session)
		val repository = fixture.repository(RecordingPlaybackQuiescePort(events))
		repository.switchCurrentSession(session)
		events.clear()

		repository.restoreSession(destroyOnly = false)

		events.shouldContainExactly(
			"quiesce-playback",
			"clear-api",
			"clear-user",
			"clear-server",
			"clear-crash-url",
			"clear-crash-token",
			"invalidate-preferences",
		)
		repository.currentSession.value shouldBe null
		repository.state.value shouldBe SessionRepositoryState.READY
	}

	test("authentication failure quiesces playback before clearing identity") {
		val events = mutableListOf<String>()
		val fixture = SessionRepositoryFixture(events)
		val currentSession = fixture.session()
		fixture.allowCurrentUserLookupFor(currentSession)
		val repository = fixture.repository(RecordingPlaybackQuiescePort(events))
		repository.switchCurrentSession(currentSession)
		events.clear()
		val failingSession = Session(
			userId = UUID.randomUUID(),
			serverId = UUID.randomUUID(),
			accessToken = "candidate-token",
		)
		fixture.failCurrentUserLookupFor(failingSession)

		val switched = repository.switchCurrentSession(failingSession)

		switched.shouldBeFalse()
		events.shouldContainExactly(
			"bind-api:${failingSession.accessToken}",
			"bind-api:${currentSession.accessToken}",
			"quiesce-playback",
			"clear-api",
			"clear-user",
			"clear-server",
			"clear-crash-url",
			"clear-crash-token",
			"invalidate-preferences",
		)
		repository.currentSession.value shouldBe null
		repository.state.value shouldBe SessionRepositoryState.READY
	}

	test("preparing profile selection quiesces playback without clearing identity") {
		val events = mutableListOf<String>()
		val fixture = SessionRepositoryFixture(events)
		val session = fixture.session()
		fixture.allowCurrentUserLookupFor(session)
		val repository = fixture.repository(RecordingPlaybackQuiescePort(events))
		repository.switchCurrentSession(session)
		events.clear()

		repository.prepareForProfileSelection()

		events.shouldContainExactly("quiesce-playback")
		repository.currentSession.value shouldBe session
		fixture.userState.value shouldBe fixture.authenticatedUser
		fixture.serverState.value?.id shouldBe session.serverId
		repository.state.value shouldBe SessionRepositoryState.READY
	}

	test("failed playback quiesce keeps the authenticated surface blocked") {
		val events = mutableListOf<String>()
		val fixture = SessionRepositoryFixture(events)
		val session = fixture.session()
		fixture.allowCurrentUserLookupFor(session)
		val repository = fixture.repository(
			PlaybackQuiescePort { error("playback stop failed") }
		)
		repository.switchCurrentSession(session)
		events.clear()
		val userBeforeDestroy = fixture.userState.value
		val serverBeforeDestroy = fixture.serverState.value

		shouldThrow<IllegalStateException> {
			repository.destroyCurrentSession()
		}

		events shouldBe emptyList()
		fixture.userState.value shouldBe userBeforeDestroy
		fixture.serverState.value shouldBe serverBeforeDestroy
		repository.currentSession.value shouldBe session
		repository.state.value shouldBe SessionRepositoryState.INVALIDATING_SESSION
	}

	test("authentication and quiesce failure restores the previous API identity") {
		val events = mutableListOf<String>()
		val fixture = SessionRepositoryFixture(events)
		val currentSession = fixture.session(accessToken = "current-token")
		fixture.allowCurrentUserLookupFor(currentSession)
		var expectedIdentityAtQuiesce: RecordedApiIdentity? = null
		val repository = fixture.repository(
			PlaybackQuiescePort {
				fixture.apiIdentity() shouldBe expectedIdentityAtQuiesce
				events += "quiesce-playback"
				error("playback stop failed")
			}
		)
		repository.switchCurrentSession(currentSession)
		val previousApiIdentity = fixture.apiIdentity()
		expectedIdentityAtQuiesce = previousApiIdentity
		val previousUser = fixture.userState.value
		val previousServer = fixture.serverState.value
		events.clear()
		val candidate = fixture.session(accessToken = "candidate-token")
		fixture.failCurrentUserLookupFor(candidate)

		shouldThrow<IllegalStateException> {
			repository.switchCurrentSession(candidate)
		}

		fixture.apiIdentity() shouldBe previousApiIdentity
		events.shouldContainExactly(
			"bind-api:${candidate.accessToken}",
			"bind-api:${currentSession.accessToken}",
			"quiesce-playback",
		)
		repository.currentSession.value shouldBe currentSession
		fixture.userState.value shouldBe previousUser
		fixture.serverState.value shouldBe previousServer
		repository.state.value shouldBe SessionRepositoryState.INVALIDATING_SESSION
	}

	test("initial authentication and quiesce failure restores an unauthenticated API") {
		val events = mutableListOf<String>()
		val fixture = SessionRepositoryFixture(events)
		fixture.clearPublishedIdentity()
		fixture.seedApiIdentity(
			baseUrl = "https://stale.example",
			accessToken = "stale-token",
		)
		val unauthenticatedApiIdentity = fixture.unauthenticatedApiIdentity()
		val repository = fixture.repository(
			PlaybackQuiescePort {
				fixture.apiIdentity() shouldBe unauthenticatedApiIdentity
				events += "quiesce-playback"
				error("playback stop failed")
			}
		)
		val candidate = fixture.session(accessToken = "candidate-token")
		fixture.failCurrentUserLookupFor(candidate)

		shouldThrow<IllegalStateException> {
			repository.switchCurrentSession(candidate)
		}

		fixture.apiIdentity() shouldBe unauthenticatedApiIdentity
		events.shouldContainExactly(
			"bind-api:${candidate.accessToken}",
			"clear-api",
			"quiesce-playback",
		)
		repository.currentSession.value shouldBe null
		fixture.userState.value shouldBe null
		fixture.serverState.value shouldBe null
		repository.state.value shouldBe SessionRepositoryState.INVALIDATING_SESSION
	}

	test("last-user restore never substitutes the owner recovery identity") {
		val events = mutableListOf<String>()
		val fixture = SessionRepositoryFixture(events)
		val ownerUserId = UUID.randomUUID()
		val activeSession = fixture.session(accessToken = "active-profile-token").copy(
			ownerUserId = ownerUserId,
			profileSelectorId = UUID.randomUUID(),
		)
		fixture.configureLastUserRestore(activeSession, ownerUserId)
		fixture.allowCurrentUserLookupFor(activeSession)
		val repository = fixture.repository(RecordingPlaybackQuiescePort(events))

		repository.restoreSession(destroyOnly = false)

		repository.currentSession.value shouldBe activeSession
		events.first() shouldBe "bind-api:active-profile-token"
	}

	test("committed install rejects a Users Me identity mismatch before publication") {
		val events = mutableListOf<String>()
		val fixture = SessionRepositoryFixture(events)
		val candidate = fixture.session(accessToken = "committed-token")
		fixture.mismatchCurrentUserLookupFor(candidate)
		val repository = fixture.repository(RecordingPlaybackQuiescePort(events))

		repository.switchCurrentSession(candidate).shouldBeFalse()

		repository.currentSession.value shouldBe null
		fixture.userState.value shouldBe fixture.authenticatedUser
	}

	test("ordinary install stays unpublished when terminal authority wins after capture") {
		val events = mutableListOf<String>()
		val fixture = SessionRepositoryFixture(events)
		val candidate = fixture.session(accessToken = "candidate-token")
		val authorityCaptured = CompletableDeferred<Unit>()
		val releaseCurrentUser = CompletableDeferred<Unit>()
		fixture.clearPublishedIdentity()
		fixture.pauseCurrentUserLookupFor(candidate, releaseCurrentUser)
		fixture.captureAuthorityFor(candidate, authorityCaptured)
		val repository = fixture.repository(RecordingPlaybackQuiescePort(events))

		coroutineScope {
			val switching = async { repository.switchCurrentSession(candidate) }
			authorityCaptured.await()
			fixture.invalidateAuthority()
			releaseCurrentUser.complete(Unit)

			switching.await().shouldBeFalse()
		}

		repository.currentSession.value shouldBe null
		fixture.userState.value shouldBe null
		fixture.serverState.value shouldBe null
	}

	test("ordinary preparing snapshot cannot replace newer same-generation committed cleanup") {
		val events = mutableListOf<String>()
		val fixture = SessionRepositoryFixture(events)
		val candidate = fixture.session(accessToken = "candidate-token").copy(deviceId = "device")
		val switchId = UUID.randomUUID()
		val preparingEnvelope = AuthenticationSessionEnvelope(
			activeProfile = AuthenticationActiveProfileSession(
				profileUserId = candidate.userId,
				accessToken = candidate.accessToken,
				deviceId = candidate.deviceId!!,
			),
			pendingSwitch = AuthenticationPendingSwitch(
				switchId = switchId,
				targetProfileUserId = UUID.randomUUID(),
				oldProfileUserId = candidate.userId,
				oldSessionEpoch = 0,
				phase = AuthenticationPendingSwitchPhase.PREPARING,
				createdAtEpochMillis = 1,
			),
		)
		val preparingAuthority = AuthenticationAuthoritySnapshot(generation = 5, envelope = preparingEnvelope)
		val pending = preparingEnvelope.pendingSwitch!!
		val committedEnvelope = preparingEnvelope.copy(
			activeProfile = preparingEnvelope.activeProfile!!.copy(
				profileUserId = pending.targetProfileUserId,
				accessToken = "committed-token",
			),
			sessionEpoch = 1,
			pendingSwitch = pending.copy(phase = AuthenticationPendingSwitchPhase.INSTALLING),
			cleanupMarker = AuthenticationCommittedPendingCleanup(switchId),
		)
		val committedAuthority = preparingAuthority.copy(envelope = committedEnvelope)
		val authorityCaptured = CompletableDeferred<Unit>()
		val releaseCurrentUser = CompletableDeferred<Unit>()
		fixture.clearPublishedIdentity()
		fixture.pauseCurrentUserLookupFor(candidate, releaseCurrentUser)
		fixture.useAuthority(candidate.serverId, preparingAuthority)
		fixture.captureAuthorityFor(candidate, authorityCaptured)
		val repository = fixture.repository(RecordingPlaybackQuiescePort(events))

		coroutineScope {
			val switching = async { repository.switchCurrentSession(candidate) }
			authorityCaptured.await()
			fixture.installNewerAuthority(committedAuthority)
			releaseCurrentUser.complete(Unit)

			switching.await().shouldBeFalse()
		}

		repository.currentSession.value shouldBe null
		fixture.userState.value shouldBe null
		fixture.serverState.value shouldBe null
		fixture.authoritySnapshot() shouldBe committedAuthority
	}

	test("committed install persists the exact durable envelope") {
		val events = mutableListOf<String>()
		val fixture = SessionRepositoryFixture(events)
		val deviceId = "committed-device"
		val session = fixture.session(accessToken = "committed-token").copy(
			deviceId = deviceId,
			sessionEpoch = 11,
		)
		val cleanupSwitchId = UUID.randomUUID()
		val durableEnvelope = AuthenticationSessionEnvelope(
			activeProfile = AuthenticationActiveProfileSession(
				profileUserId = session.userId,
				accessToken = session.accessToken,
				deviceId = deviceId,
			),
			sessionEpoch = session.sessionEpoch,
			cleanupMarker = AuthenticationCommittedPendingCleanup(cleanupSwitchId),
		)
		val authority = AuthenticationAuthoritySnapshot(generation = 7, envelope = durableEnvelope)
		fixture.allowCurrentUserLookupFor(session)
		fixture.useAuthority(session.serverId, authority)
		val repository = fixture.repository(RecordingPlaybackQuiescePort(events))
		val snapshot = SessionSnapshot(
			serverId = session.serverId,
			deviceId = deviceId,
			profileUserId = session.userId,
			credential = ActiveProfileCredential.fromToken(session.accessToken),
			sessionEpoch = session.sessionEpoch,
		)

		repository.installCommittedSession(snapshot) shouldBe true

		repository.currentSession.value shouldBe session
		fixture.verifyExactEnvelopeReplacement(session.serverId, authority)
	}

	test("committed install rejects a durable owner authority mismatch before publication") {
		val events = mutableListOf<String>()
		val fixture = SessionRepositoryFixture(events)
		val deviceId = "committed-device"
		val session = fixture.session(accessToken = "committed-token").copy(
			deviceId = deviceId,
			profileSelectorId = UUID.randomUUID(),
			ownerUserId = UUID.randomUUID(),
			sessionEpoch = 11,
		)
		val durableEnvelope = AuthenticationSessionEnvelope(
			activeProfile = AuthenticationActiveProfileSession(
				profileUserId = session.userId,
				accessToken = session.accessToken,
				deviceId = deviceId,
				profileSelectorId = session.profileSelectorId,
				ownerUserId = session.ownerUserId,
			),
			sessionEpoch = session.sessionEpoch,
			cleanupMarker = AuthenticationCommittedPendingCleanup(UUID.randomUUID()),
		)
		fixture.clearPublishedIdentity()
		fixture.useAuthority(
			session.serverId,
			AuthenticationAuthoritySnapshot(generation = 7, envelope = durableEnvelope),
		)
		val repository = fixture.repository(RecordingPlaybackQuiescePort(events))
		val mismatchedSnapshot = SessionSnapshot(
			serverId = session.serverId,
			deviceId = deviceId,
			profileUserId = session.userId,
			credential = ActiveProfileCredential.fromToken(session.accessToken),
			sessionEpoch = session.sessionEpoch,
			profileSelectorId = session.profileSelectorId,
			ownerUserId = UUID.randomUUID(),
		)

		repository.installCommittedSession(mismatchedSnapshot).shouldBeFalse()

		repository.currentSession.value shouldBe null
		fixture.userState.value shouldBe null
		fixture.serverState.value shouldBe null
		fixture.verifyNoEnvelopeReplacement()
	}
})

private class RecordingPlaybackQuiescePort(
	private val events: MutableList<String>,
) : PlaybackQuiescePort {
	override suspend fun quiesceIfCreated() {
		events += "quiesce-playback"
	}
}

private class SessionRepositoryFixture(
	private val events: MutableList<String>,
) {
	val authenticatedUser = mockk<UserDto>(relaxed = true)
	val authenticatedServer = Server(
		id = UUID.randomUUID(),
		name = "Authenticated server",
		address = "https://jellyfin.example",
		version = Jellyfin.apiVersion.toString(),
	)
	val userState = MutableStateFlow<UserDto?>(authenticatedUser)
	val serverState = MutableStateFlow<Server?>(authenticatedServer)
	val authenticationPreferences = mockk<AuthenticationPreferences>(relaxed = true)
	private val authenticationStore = mockk<AuthenticationStore>(relaxed = true)
	private val preferencesRepository = mockk<PreferencesRepository>(relaxed = true)
	private val defaultDeviceInfo = mockk<DeviceInfo>(relaxed = true)
	private val defaultClientInfo = mockk<ClientInfo>(relaxed = true)
	private val telemetryPreferences = mockk<TelemetryPreferences>(relaxed = true)
	private var apiBaseUrl: String? = null
	private var apiAccessToken: String? = null
	private var apiClientInfo = defaultClientInfo
	private var apiDeviceInfo = defaultDeviceInfo
	private var currentAuthority: AuthenticationAuthoritySnapshot? = null
	private val apiClient = mockk<ApiClient>(relaxed = true) {
		every { baseUrl } answers { apiBaseUrl }
		every { accessToken } answers { apiAccessToken }
		every { clientInfo } answers { apiClientInfo }
		every { deviceInfo } answers { apiDeviceInfo }
		every { update(any(), any(), any(), any()) } answers {
			apiBaseUrl = firstArg()
			apiAccessToken = secondArg()
			apiClientInfo = thirdArg()
			apiDeviceInfo = arg(3)
			events += if (apiBaseUrl == null && apiAccessToken == null) {
				"clear-api"
			} else {
				"bind-api:$apiAccessToken"
			}
		}
	}
	private val userRepository = mockk<UserRepository> {
		every { currentUser } returns userState
		every { setCurrentUser(any()) } answers {
			val user = firstArg<UserDto?>()
			if (user == null) events += "clear-user"
			userState.value = user
		}
	}
	private val serverRepository = mockk<ServerRepository> {
		every { storedServers } returns MutableStateFlow(emptyList())
		every { discoveredServers } returns MutableStateFlow(emptyList())
		every { currentServer } returns serverState
		every { setCurrentServer(any()) } answers {
			val server = firstArg<Server?>()
			if (server == null) events += "clear-server"
			serverState.value = server
		}
	}

	init {
		every { authenticationStore.replaceSessionEnvelope(any(), any(), any(), any()) } answers {
			val expected = secondArg<AuthenticationAuthoritySnapshot>()
			val updated = thirdArg<AuthenticationSessionEnvelope>()
			if (currentAuthority != expected) null else expected.copy(envelope = updated).also { currentAuthority = it }
		}
		every {
			telemetryPreferences[TelemetryPreferences.crashReportUrl] = ""
		} answers { events += "clear-crash-url" }
		every {
			telemetryPreferences[TelemetryPreferences.crashReportToken] = ""
		} answers { events += "clear-crash-token" }
		coEvery { preferencesRepository.onSessionChanged() } coAnswers { events += "invalidate-preferences" }
	}

	fun repository(playbackQuiescePort: PlaybackQuiescePort) = SessionRepositoryImpl(
		authenticationPreferences = authenticationPreferences,
		authenticationStore = authenticationStore,
		userApiClient = apiClient,
		preferencesRepository = preferencesRepository,
		defaultDeviceInfo = defaultDeviceInfo,
		userRepository = userRepository,
		serverRepository = serverRepository,
		telemetryPreferences = telemetryPreferences,
		playbackQuiescePort = playbackQuiescePort,
	)

	fun failCurrentUserLookupFor(session: Session) {
		configureServer(session)
		every { apiClient.getOrCreateApi(UserApi::class, any()) } answers { UserApi(apiClient) }
		coEvery { apiClient.request(any(), any(), any(), any(), any()) } throws ApiClientException()
	}

	fun mismatchCurrentUserLookupFor(session: Session) {
		configureServer(session)
		val mismatchedUser = mockk<UserDto> {
			every { id } returns UUID.randomUUID()
		}
		val userApi = mockk<UserApi> {
			coEvery { getCurrentUser() } returns Response(mismatchedUser, 200, emptyMap())
		}
		every { apiClient.getOrCreateApi(UserApi::class, any()) } returns userApi
	}

	fun allowCurrentUserLookupFor(session: Session) {
		configureServer(session)
		every { authenticatedUser.id } returns session.userId
		val userApi = mockk<UserApi> {
			coEvery { getCurrentUser() } returns Response(authenticatedUser, 200, emptyMap())
		}
		every { apiClient.getOrCreateApi(UserApi::class, any()) } returns userApi
		every { apiClient.getOrCreateApi(ClientLogApi::class, any()) } returns mockk(relaxed = true)
	}

	fun pauseCurrentUserLookupFor(session: Session, release: CompletableDeferred<Unit>) {
		configureServer(session)
		every { authenticatedUser.id } returns session.userId
		val userApi = mockk<UserApi> {
			coEvery { getCurrentUser() } coAnswers {
				release.await()
				Response(authenticatedUser, 200, emptyMap())
			}
		}
		every { apiClient.getOrCreateApi(UserApi::class, any()) } returns userApi
		every { apiClient.getOrCreateApi(ClientLogApi::class, any()) } returns mockk(relaxed = true)
	}

	fun captureAuthorityFor(session: Session, captured: CompletableDeferred<Unit>) {
		every { authenticationStore.getAuthoritySnapshot(session.serverId) } answers {
			captured.complete(Unit)
			currentAuthority
		}
	}

	fun invalidateAuthority() {
		currentAuthority = currentAuthority?.let { authority ->
			authority.copy(generation = authority.generation + 1, envelope = null)
		}
	}

	fun useAuthority(serverId: UUID, authority: AuthenticationAuthoritySnapshot) {
		currentAuthority = authority
		every { authenticationStore.getAuthoritySnapshot(serverId) } returns authority
	}

	fun installNewerAuthority(authority: AuthenticationAuthoritySnapshot) {
		currentAuthority = authority
	}

	fun authoritySnapshot(): AuthenticationAuthoritySnapshot? = currentAuthority

	fun verifyExactEnvelopeReplacement(serverId: UUID, authority: AuthenticationAuthoritySnapshot) {
		verify(exactly = 1) {
			authenticationStore.replaceSessionEnvelope(
				serverId,
				authority,
				authority.envelope!!,
				false,
			)
		}
	}

	fun verifyNoEnvelopeReplacement() {
		verify(exactly = 0) {
			authenticationStore.replaceSessionEnvelope(any(), any(), any(), any())
		}
	}

	fun apiIdentity() = RecordedApiIdentity(
		baseUrl = apiBaseUrl,
		accessToken = apiAccessToken,
		clientInfo = apiClientInfo,
		deviceInfo = apiDeviceInfo,
	)

	fun clearPublishedIdentity() {
		userState.value = null
		serverState.value = null
	}

	fun seedApiIdentity(baseUrl: String?, accessToken: String?) {
		apiBaseUrl = baseUrl
		apiAccessToken = accessToken
	}

	fun unauthenticatedApiIdentity() = RecordedApiIdentity(
		baseUrl = null,
		accessToken = null,
		clientInfo = apiClientInfo,
		deviceInfo = defaultDeviceInfo,
	)

	fun session(accessToken: String = "access-token") = Session(
		userId = UUID.randomUUID(),
		serverId = UUID.randomUUID(),
		accessToken = accessToken,
	)

	fun configureLastUserRestore(session: Session, ownerUserId: UUID) {
		every { authenticationPreferences[AuthenticationPreferences.alwaysAuthenticate] } returns false
		every {
			authenticationPreferences[AuthenticationPreferences.autoLoginUserBehavior]
		} returns UserSelectBehavior.LAST_USER
		every { authenticationPreferences[AuthenticationPreferences.lastServerId] } returns session.serverId.toString()
		every { authenticationPreferences[AuthenticationPreferences.lastUserId] } returns session.userId.toString()
		every { authenticationPreferences[AuthenticationPreferences.lastOwnerUserId] } returns ownerUserId.toString()
		every {
			authenticationStore.getAuthoritySnapshot(session.serverId)
		} returns AuthenticationAuthoritySnapshot(generation = 0, envelope = null)
		every { authenticationStore.getUser(session.serverId, session.userId) } returns AuthenticationStoreUser(
			name = "Active profile",
			accessToken = session.accessToken,
			profileSelectorId = session.profileSelectorId,
			profileSelectorOwnerUserId = ownerUserId,
		)
	}

	private fun configureServer(session: Session) {
		val server = Server(
			id = session.serverId,
			name = "Test server",
			address = "https://jellyfin-${session.serverId}.example",
			version = Jellyfin.apiVersion.toString(),
		)
		coEvery { serverRepository.getServer(session.serverId, true) } returns server
		every { authenticationStore.getServer(session.serverId) } returns AuthenticationStoreServer(
			name = server.name,
			address = server.address,
			version = server.version,
		)
		currentAuthority = AuthenticationAuthoritySnapshot(generation = 0, envelope = null)
		every { authenticationStore.getAuthoritySnapshot(session.serverId) } answers { currentAuthority }
	}
}

private data class RecordedApiIdentity(
	val baseUrl: String?,
	val accessToken: String?,
	val clientInfo: ClientInfo,
	val deviceInfo: DeviceInfo,
)
