package org.jellyfin.androidtv.auth.session

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.androidtv.auth.repository.Session
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.SessionRepositoryState
import java.util.UUID

class SessionBootstrapCoordinatorTests : FunSpec({
	test("starts closed and recovers before restoring or publishing runtime") {
		val fixture = SessionBootstrapFixture(restoredSession = session())

		fixture.coordinator.state.value shouldBe SessionBootstrapState.CLOSED
		val outcome = fixture.coordinator.initialize()

		outcome shouldBe SessionBootstrapOutcome.Ready
		fixture.events.shouldContainExactly("recover", "restore-deferred", "publish-ready")
		fixture.coordinator.state.value shouldBe SessionBootstrapState.READY
		fixture.repository.state.value shouldBe SessionRepositoryState.READY
	}

	test("uses the durable last server when authentication policy restores no runtime") {
		val lastServerId = UUID.randomUUID()
		val fixture = SessionBootstrapFixture(restoredSession = null, lastServerId = lastServerId)

		fixture.coordinator.initialize() shouldBe SessionBootstrapOutcome.Ready

		fixture.recoveredServers.shouldContainExactly(lastServerId)
		fixture.events.shouldContainExactly("recover", "restore-deferred", "publish-ready")
	}

	test("does not invoke recovery when no server authority is known") {
		val fixture = SessionBootstrapFixture(restoredSession = null, lastServerId = null)

		fixture.coordinator.initialize() shouldBe SessionBootstrapOutcome.Ready

		fixture.events.shouldContainExactly("restore-deferred", "publish-ready")
	}

	test("applies authentication policy after committed recovery installs runtime") {
		val target = session()
		val fixture = SessionBootstrapFixture(
			restoredSession = null,
			lastServerId = target.serverId,
			recoveredRuntime = target,
		)

		fixture.coordinator.initialize() shouldBe SessionBootstrapOutcome.Ready

		fixture.events.shouldContainExactly(
			"recover",
			"restore-deferred",
			"publish-ready",
		)
		fixture.repository.currentSession.value shouldBe null
	}

	test("recovery failure keeps runtime closed and never publishes ready") {
		val fixture = SessionBootstrapFixture(
			restoredSession = session(),
			recoveryFailure = SessionSwitchRecoveryRequired("status unavailable"),
		)

		fixture.coordinator.initialize() shouldBe SessionBootstrapOutcome.RecoveryRequired

		fixture.events.shouldContainExactly("recover", "fail-closed")
		fixture.coordinator.state.value shouldBe SessionBootstrapState.RECOVERY_REQUIRED
		fixture.repository.state.value shouldBe SessionRepositoryState.INVALIDATING_SESSION
	}

	test("cancellation leaves bootstrap closed and propagates") {
		val fixture = SessionBootstrapFixture(
			restoredSession = session(),
			recoveryFailure = CancellationException("process stopped"),
		)

		shouldThrow<CancellationException> { fixture.coordinator.initialize() }

		fixture.events.shouldContainExactly("recover")
		fixture.coordinator.state.value shouldBe SessionBootstrapState.RECOVERING
		fixture.repository.state.value shouldBe SessionRepositoryState.RESTORING_SESSION
	}

	test("successful bootstrap replay is idempotent") {
		val fixture = SessionBootstrapFixture(restoredSession = session())

		fixture.coordinator.initialize() shouldBe SessionBootstrapOutcome.Ready
		fixture.coordinator.initialize() shouldBe SessionBootstrapOutcome.Ready

		fixture.events.shouldContainExactly("recover", "restore-deferred", "publish-ready")
	}
})

private class SessionBootstrapFixture(
	restoredSession: Session?,
	lastServerId: UUID? = restoredSession?.serverId,
	private val recoveredRuntime: Session? = null,
	private val recoveryFailure: Throwable? = null,
) {
	val events = mutableListOf<String>()
	val recoveredServers = mutableListOf<UUID>()
	val repository = BootstrapSessionRepository(restoredSession, events)
	private val lifecycle = object : SessionSwitchLifecyclePort {
		override suspend fun switch(request: SessionSwitchRequest): SessionSwitchOutcome = error("Unexpected switch")

		override suspend fun recover(serverId: UUID): SessionSwitchOutcome? {
			events += "recover"
			recoveredServers += serverId
			recoveryFailure?.let { throw it }
			recoveredRuntime?.let(repository::installRecoveredRuntime)
			return null
		}
	}
	val coordinator = SessionBootstrapCoordinator(
		sessionRepository = repository,
		sessionSwitchLifecycle = lifecycle,
		lastServerId = { lastServerId },
	)
}

private class BootstrapSessionRepository(
	private val policySession: Session?,
	private val events: MutableList<String>,
) : SessionRepository {
	private val mutableSession = MutableStateFlow<Session?>(null)
	override val currentSession: StateFlow<Session?> = mutableSession
	private val mutableState = MutableStateFlow(SessionRepositoryState.RESTORING_SESSION)
	override val state: StateFlow<SessionRepositoryState> = mutableState

	override suspend fun restoreSessionForBootstrap(): Session? {
		events += "restore-deferred"
		mutableState.value = SessionRepositoryState.RESTORING_SESSION
		mutableSession.value = policySession
		return policySession
	}

	override suspend fun restoreSession(destroyOnly: Boolean) = unexpected()

	override suspend fun publishSessionReady() {
		events += "publish-ready"
		mutableState.value = SessionRepositoryState.READY
	}

	override suspend fun failSessionRestore() {
		events += "fail-closed"
		mutableState.value = SessionRepositoryState.INVALIDATING_SESSION
	}

	fun installRecoveredRuntime(session: Session) {
		mutableSession.value = session
	}

	override suspend fun switchCurrentSession(serverId: UUID, userId: UUID) = unexpected()
	override suspend fun switchCurrentSession(session: Session) = unexpected()
	override suspend fun installCommittedSession(snapshot: SessionSnapshot) = unexpected()
	override suspend fun prepareForProfileSelection() = unexpected()
	override suspend fun destroyCurrentSession() = unexpected()
}

private fun session() = Session(
	userId = UUID.randomUUID(),
	serverId = UUID.randomUUID(),
	accessToken = "access-token",
)

private fun unexpected(): Nothing = error("Unexpected session repository call")
