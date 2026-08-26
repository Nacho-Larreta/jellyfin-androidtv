package org.jellyfin.androidtv.auth.session

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class SessionSwitchCoordinatorTests : FunSpec({
	test("commit is confirmed before the target identity is durably installed and published") {
		val fixture = SessionSwitchFixture()

		val outcome = fixture.coordinator().switch(fixture.request)

		outcome shouldBe SessionSwitchOutcome.CommittedPendingCleanup(fixture.targetSnapshot())
		fixture.events.shouldContainExactly(
			"store:PREPARING:old",
			"prepare:Active",
			"store:QUIESCING:old",
			"quiesce:old",
			"store:COMMITTING:old",
			"commit:Active",
			"store:INSTALLING:target",
			"install:target",
		)
		fixture.store.envelope?.cleanupMarker shouldBe CommittedPendingCleanup(fixture.request.switchId)
		shouldThrow<SessionSwitchInProgress> { fixture.barrier.admit() }
	}

	test("lost commit response is persisted as unknown and resolved by status") {
		val fixture = SessionSwitchFixture().apply {
			api.commitFailure = SessionSwitchCommitUnknown(IllegalStateException("response lost"))
		}

		fixture.coordinator().switch(fixture.request)

		fixture.events.shouldContainExactly(
			"store:PREPARING:old",
			"prepare:Active",
			"store:QUIESCING:old",
			"quiesce:old",
			"store:COMMITTING:old",
			"commit:Active",
			"store:COMMIT_UNKNOWN:old",
			"status:Active",
			"store:INSTALLING:target",
			"install:target",
		)
	}

	test("same switch joins one operation while a distinct switch is rejected") {
		val fixture = SessionSwitchFixture()
		val prepareEntered = CompletableDeferred<Unit>()
		val releasePrepare = CompletableDeferred<Unit>()
		fixture.api.prepareGate = suspend {
			prepareEntered.complete(Unit)
			releasePrepare.await()
		}

		coroutineScope {
			val owner = async { fixture.coordinator.switch(fixture.request) }
			prepareEntered.await()
			val replay = async { fixture.coordinator.switch(fixture.request) }
			shouldThrow<SwitchAlreadyInProgress> {
				fixture.coordinator.switch(fixture.request.copy(switchId = UUID.randomUUID()))
			}
			releasePrepare.complete(Unit)
			owner.await() shouldBe replay.await()
		}

		fixture.api.prepareCalls shouldBe 1
		fixture.api.commitCalls shouldBe 1
	}

	test("same switch id joins only an exactly equivalent in-memory request") {
		val mismatches = listOf<(SessionSwitchRequest) -> SessionSwitchRequest>(
			{ request -> request.copy(targetProfileUserId = UUID.randomUUID()) },
			{ request -> request.copy(pin = "9876") },
			{ request -> request.copy(authority = SessionSwitchAuthority.OwnerRecovery) },
		)
		for (mismatch in mismatches) {
			val fixture = SessionSwitchFixture()
			val prepareEntered = CompletableDeferred<Unit>()
			val releasePrepare = CompletableDeferred<Unit>()
			fixture.api.prepareGate = suspend {
				prepareEntered.complete(Unit)
				releasePrepare.await()
			}

			coroutineScope {
				val owner = async { fixture.coordinator.switch(fixture.request) }
				prepareEntered.await()
				try {
					shouldThrow<SwitchAlreadyInProgress> {
						withTimeout(250) {
							fixture.coordinator.switch(mismatch(fixture.request))
						}
					}
				} finally {
					releasePrepare.complete(Unit)
				}
				owner.await()
			}
		}
	}

	test("request string representation never exposes its PIN") {
		val fixture = SessionSwitchFixture()

		fixture.request.toString().contains("0123") shouldBe false
		fixture.request.toString().contains("REDACTED") shouldBe true
	}

	test("pre-commit restart aborts the durable operation and restores old admission") {
		val fixture = SessionSwitchFixture()
		fixture.store.envelope = fixture.initialEnvelope.copy(
			pendingSwitch = fixture.pending(PendingSwitchPhase.QUIESCING),
		)
		fixture.api.statusResult = fixture.serverResult(ServerSwitchState.PREPARED, credential = null)

		val outcome = fixture.coordinator().recover(fixture.serverId)

		outcome shouldBe SessionSwitchOutcome.Restored(fixture.oldSnapshot)
		fixture.events.shouldContainExactly(
			"status:Active",
			"abort:Active",
			"store:none:old",
		)
		fixture.barrier.admit() shouldBe fixture.oldSnapshot
	}

	test("pre-commit restart restores old admission when prepare never reached the server") {
		val fixture = SessionSwitchFixture()
		fixture.store.envelope = fixture.initialEnvelope.copy(
			pendingSwitch = fixture.pending(PendingSwitchPhase.PREPARING),
		)
		fixture.api.statusFailure = SessionSwitchRejected("not found", statusCode = 404)

		val outcome = fixture.coordinator().recover(fixture.serverId)

		outcome shouldBe SessionSwitchOutcome.Restored(fixture.oldSnapshot)
		fixture.events.shouldContainExactly("status:Active", "store:none:old")
		fixture.barrier.admit() shouldBe fixture.oldSnapshot
	}

	test("commit-unknown restart resolves the exact switch and increments epoch once") {
		val fixture = SessionSwitchFixture()
		fixture.store.envelope = fixture.initialEnvelope.copy(
			pendingSwitch = fixture.pending(PendingSwitchPhase.COMMIT_UNKNOWN),
		)

		fixture.coordinator().recover(fixture.serverId)
		fixture.coordinator().recover(fixture.serverId)

		fixture.store.envelope?.activeProfile shouldBe fixture.targetSnapshot()
		fixture.store.envelope?.activeProfile?.sessionEpoch shouldBe fixture.oldSnapshot.sessionEpoch + 1
		fixture.api.statusCalls shouldBe 1
		fixture.runtime.installCalls shouldBe 2
	}

	test("same switch retry resumes its durable unknown commit instead of preparing again") {
		val fixture = SessionSwitchFixture()
		fixture.store.envelope = fixture.initialEnvelope.copy(
			pendingSwitch = fixture.pending(PendingSwitchPhase.COMMIT_UNKNOWN),
		)

		fixture.coordinator().switch(fixture.request)

		fixture.api.prepareCalls shouldBe 0
		fixture.api.statusCalls shouldBe 1
		fixture.store.envelope?.activeProfile shouldBe fixture.targetSnapshot()
	}

	test("durable same-id recovery rejects a mismatched target or authority") {
		val mismatches = listOf<(SessionSwitchRequest) -> SessionSwitchRequest>(
			{ request -> request.copy(targetProfileUserId = UUID.randomUUID()) },
			{ request -> request.copy(authority = SessionSwitchAuthority.OwnerRecovery) },
		)
		for (mismatch in mismatches) {
			val fixture = SessionSwitchFixture()
			fixture.store.envelope = fixture.initialEnvelope.copy(
				pendingSwitch = fixture.pending(PendingSwitchPhase.COMMIT_UNKNOWN),
			)

			shouldThrow<SwitchAlreadyInProgress> {
				fixture.coordinator().switch(mismatch(fixture.request))
			}

			fixture.api.statusCalls shouldBe 0
			fixture.runtime.installed shouldBe emptyList()
		}
	}

	test("cleanup replay requires the exact durable target and authority") {
		val fixture = SessionSwitchFixture()
		fixture.store.envelope = fixture.initialEnvelope.copy(
			activeProfile = fixture.targetSnapshot(),
			pendingSwitch = fixture.pending(PendingSwitchPhase.INSTALLING),
			cleanupMarker = CommittedPendingCleanup(fixture.request.switchId),
		)

		shouldThrow<SwitchAlreadyInProgress> {
			fixture.coordinator().switch(fixture.request.copy(authority = SessionSwitchAuthority.OwnerRecovery))
		}

		fixture.runtime.installed shouldBe emptyList()
	}

	test("unknown commit stays fail closed when status no longer has the switch") {
		val fixture = SessionSwitchFixture()
		fixture.store.envelope = fixture.initialEnvelope.copy(
			pendingSwitch = fixture.pending(PendingSwitchPhase.COMMIT_UNKNOWN),
		)
		fixture.api.statusFailure = SessionSwitchRejected("not found", statusCode = 404)

		shouldThrow<SessionSwitchRecoveryRequired> {
			fixture.coordinator().recover(fixture.serverId)
		}

		fixture.runtime.installed shouldBe emptyList()
		shouldThrow<SessionSwitchInProgress> { fixture.barrier.admit() }
	}

	test("invalid prepare response restores the old durable envelope") {
		val fixture = SessionSwitchFixture()
		fixture.api.prepareResult = fixture.serverResult(ServerSwitchState.ABORTED, credential = null)

		shouldThrow<SessionSwitchRejected> { fixture.coordinator().switch(fixture.request) }

		fixture.store.envelope shouldBe fixture.initialEnvelope
		fixture.barrier.admit() shouldBe fixture.oldSnapshot
	}

	test("owner recovery authorizes only the switch call and is never installed as active") {
		val fixture = SessionSwitchFixture()
		val recovery = fixture.ownerRecovery()
		fixture.store.envelope = fixture.initialEnvelope.copy(ownerRecovery = recovery)
		val request = fixture.request.copy(authority = SessionSwitchAuthority.OwnerRecovery)

		fixture.coordinator().switch(request)

		fixture.api.credentials.all { it is SessionSwitchCredential.Recovery } shouldBe true
		fixture.runtime.installed.single().credential shouldBe fixture.targetCredential
		fixture.runtime.installed.single().credential.value shouldBe "target-token"
	}

	test("restart preserves the typed recovery authority without persisting a second active token") {
		val fixture = SessionSwitchFixture()
		fixture.store.envelope = fixture.initialEnvelope.copy(
			ownerRecovery = fixture.ownerRecovery(),
			pendingSwitch = fixture.pending(PendingSwitchPhase.COMMIT_UNKNOWN).copy(
				authority = SessionSwitchAuthority.OwnerRecovery,
			),
		)

		fixture.coordinator().recover(fixture.serverId)

		fixture.api.credentials.single()::class shouldBe SessionSwitchCredential.Recovery::class
		fixture.store.envelope?.activeProfile?.credential shouldBe fixture.targetCredential
	}

	test("missing recovery authority fails before preparing and preserves active identity") {
		val fixture = SessionSwitchFixture()

		shouldThrow<SessionSwitchRejected> {
			fixture.coordinator().switch(
				fixture.request.copy(authority = SessionSwitchAuthority.OwnerRecovery)
			)
		}

		fixture.api.prepareCalls shouldBe 0
		fixture.runtime.installed shouldBe emptyList()
	}

	test("non-ASCII PIN is rejected before durable or network side effects") {
		val fixture = SessionSwitchFixture()

		shouldThrow<SessionSwitchRejected> {
			fixture.coordinator().switch(fixture.request.copy(pin = "١٢٣٤"))
		}

		fixture.events shouldBe emptyList()
		fixture.api.prepareCalls shouldBe 0
	}

	test("fake clock owns the durable creation timestamp") {
		val instant = Instant.parse("2026-08-25T14:00:00Z")
		val fixture = SessionSwitchFixture(clock = Clock.fixed(instant, ZoneOffset.UTC))

		fixture.coordinator().switch(fixture.request)

		fixture.store.writes.first().pendingSwitch?.createdAtEpochMillis shouldBe instant.toEpochMilli()
	}

	test("durable write failure prevents network and runtime side effects") {
		val fixture = SessionSwitchFixture()
		fixture.store.failNextWrite = true

		shouldThrow<SessionSwitchRecoveryRequired> {
			fixture.coordinator().switch(fixture.request)
		}

		fixture.events shouldBe emptyList()
		fixture.api.prepareCalls shouldBe 0
		fixture.runtime.installed shouldBe emptyList()
	}

	test("terminal invalidation after preparing prevents every later durable and runtime install") {
		val fixture = SessionSwitchFixture()
		val prepareEntered = CompletableDeferred<Unit>()
		val releasePrepare = CompletableDeferred<Unit>()
		fixture.api.prepareGate = suspend {
			prepareEntered.complete(Unit)
			releasePrepare.await()
		}

		coroutineScope {
			val switching = async {
				shouldThrow<SessionSwitchRecoveryRequired> {
					fixture.coordinator.switch(fixture.request)
				}
			}
			prepareEntered.await()
			fixture.store.invalidateAuthority()
			releasePrepare.complete(Unit)
			switching.await()
		}

		fixture.store.envelope shouldBe null
		fixture.runtime.installed shouldBe emptyList()
	}

	test("stale preparing phase cannot replace a same-generation committed cleanup marker") {
		val fixture = SessionSwitchFixture()
		val prepareEntered = CompletableDeferred<Unit>()
		val releasePrepare = CompletableDeferred<Unit>()
		fixture.api.prepareGate = suspend {
			prepareEntered.complete(Unit)
			releasePrepare.await()
		}

		coroutineScope {
			val switching = async {
				shouldThrow<SessionSwitchRecoveryRequired> {
					fixture.coordinator.switch(fixture.request)
				}
			}
			prepareEntered.await()
			val preparing = fixture.store.envelope!!
			fixture.store.envelope = preparing.copy(
				activeProfile = fixture.targetSnapshot(),
				pendingSwitch = preparing.pendingSwitch!!.copy(phase = PendingSwitchPhase.INSTALLING),
				cleanupMarker = CommittedPendingCleanup(fixture.request.switchId),
			)
			releasePrepare.complete(Unit)
			switching.await()
		}

		fixture.store.envelope?.pendingSwitch?.phase shouldBe PendingSwitchPhase.INSTALLING
		fixture.store.envelope?.cleanupMarker shouldBe CommittedPendingCleanup(fixture.request.switchId)
		fixture.runtime.installed shouldBe emptyList()
	}

	test("restart converges after a failpoint at every durable phase write") {
		for (failedWrite in 1..4) {
			val fixture = SessionSwitchFixture()
			fixture.store.failWriteNumber = failedWrite

			shouldThrow<SessionSwitchRecoveryRequired> {
				fixture.coordinator().switch(fixture.request)
			}
			fixture.runtime.installed shouldBe emptyList()
			fixture.store.failWriteNumber = null
			fixture.api.statusResult = when (failedWrite) {
				2, 3 -> fixture.serverResult(ServerSwitchState.PREPARED, credential = null)
				else -> fixture.serverResult(ServerSwitchState.COMMITTED)
			}

			val recovered = fixture.coordinator().recover(fixture.serverId)
			if (failedWrite == 4) {
				recovered shouldBe SessionSwitchOutcome.CommittedPendingCleanup(fixture.targetSnapshot())
			} else {
				recovered shouldBe SessionSwitchOutcome.Restored(fixture.oldSnapshot)
			}
		}
	}

	test("cleanup completion atomically clears markers before reopening admission") {
		val fixture = SessionSwitchFixture()
		fixture.coordinator().switch(fixture.request)
		fixture.events.clear()

		val active = fixture.coordinator().completeCleanup(fixture.serverId, fixture.request.switchId)

		active shouldBe fixture.targetSnapshot()
		fixture.events.shouldContainExactly("store:none:target")
		fixture.barrier.admit() shouldBe fixture.targetSnapshot()
	}

	test("epoch exhaustion remains fail closed without installing the target") {
		val fixture = SessionSwitchFixture()
		fixture.store.envelope = fixture.initialEnvelope.copy(
			activeProfile = fixture.oldSnapshot.copy(sessionEpoch = Long.MAX_VALUE),
		)
		fixture.runtime.current = fixture.oldSnapshot.copy(sessionEpoch = Long.MAX_VALUE)

		shouldThrow<SessionSwitchRecoveryRequired> { fixture.coordinator().switch(fixture.request) }

		fixture.runtime.installed shouldBe emptyList()
		shouldThrow<SessionSwitchInProgress> { fixture.barrier.admit() }
	}
})

private class SessionSwitchFixture(
	private val clock: Clock = Clock.fixed(Instant.parse("2026-08-25T12:00:00Z"), ZoneOffset.UTC),
) {
	val events = mutableListOf<String>()
	val serverId: UUID = UUID.randomUUID()
	private val deviceId = "device"
	private val ownerUserId = UUID.randomUUID()
	private val selectorId = UUID.randomUUID()
	private val oldUserId = UUID.randomUUID()
	private val targetUserId = UUID.randomUUID()
	val targetCredential = ActiveProfileCredential.fromToken("target-token")
	val oldSnapshot = SessionSnapshot(
		serverId = serverId,
		deviceId = deviceId,
		profileUserId = oldUserId,
		credential = ActiveProfileCredential.fromToken("old-token"),
		sessionEpoch = 7,
		profileSelectorId = selectorId,
		ownerUserId = ownerUserId,
	)
	val initialEnvelope = SessionEnvelope(activeProfile = oldSnapshot)
	val request = SessionSwitchRequest(UUID.randomUUID(), targetUserId, pin = "0123")
	val store = RecordingSessionSwitchStore(events, initialEnvelope)
	val barrier = SessionAdmissionBarrier()
	val api = RecordingSessionSwitchApi(events, ::serverResult)
	val runtime = RecordingSessionRuntimePort(events, oldSnapshot)
	val coordinator = coordinator()

	fun coordinator() = SessionSwitchCoordinator(
		environment = SessionSwitchEnvironment(
			api = api,
			store = store,
			barrier = barrier,
			quiescePort = SessionSwitchQuiescePort { snapshot, _ ->
				events += "quiesce:${snapshot.label()}"
				SessionQuiesceResult.Acknowledged("report-key")
			},
			runtimePort = runtime,
			clock = clock,
		),
	)

	fun pending(phase: PendingSwitchPhase) = PendingSwitchRecord(
		switchId = request.switchId,
		targetProfileUserId = request.targetProfileUserId,
		oldProfileUserId = oldSnapshot.profileUserId,
		oldSessionEpoch = oldSnapshot.sessionEpoch,
		phase = phase,
		createdAtEpochMillis = clock.millis(),
	)

	fun targetSnapshot() = oldSnapshot.copy(
		profileUserId = targetUserId,
		credential = targetCredential,
		sessionEpoch = 8,
	)

	fun ownerRecovery() = OwnerRecoverySession(
		serverId = serverId,
		deviceId = deviceId,
		ownerUserId = ownerUserId,
		profileSelectorId = selectorId,
		credential = OwnerRecoveryCredential.fromToken("recovery-token"),
	)

	fun serverResult(
		state: ServerSwitchState,
		credential: ActiveProfileCredential? = targetCredential,
	) = ServerSwitchResult(
		switchId = request.switchId,
		profileSelectorId = selectorId,
		ownerUserId = ownerUserId,
		targetProfileUserId = targetUserId,
		state = state,
		activeCredential = credential,
	)
}

private class RecordingSessionSwitchStore(
	private val events: MutableList<String>,
	initialEnvelope: SessionEnvelope,
) : SessionSwitchStore {
	private var authorityGeneration = initialEnvelope.authorityGeneration
	var envelope: SessionEnvelope? = initialEnvelope
		set(value) {
			field = value
			if (value != null) authorityGeneration = value.authorityGeneration
		}
	var failNextWrite = false
	var failWriteNumber: Int? = null
	private var writeAttempts = 0
	val writes = mutableListOf<SessionEnvelope>()

	override fun load(serverId: UUID): SessionEnvelope? = envelope

	override fun replace(expected: SessionEnvelope?, updated: SessionEnvelope): SessionEnvelope? {
		writeAttempts++
		if (expected != envelope || updated.authorityGeneration != authorityGeneration) return null
		if (failNextWrite || failWriteNumber == writeAttempts) {
			failNextWrite = false
			return null
		}
		this.envelope = updated
		writes += updated
		events += "store:${updated.pendingSwitch?.phase ?: "none"}:${updated.activeProfile.label()}"
		return updated
	}

	fun invalidateAuthority() {
		authorityGeneration++
		envelope = null
	}
}

private class RecordingSessionSwitchApi(
	private val events: MutableList<String>,
	private val result: (ServerSwitchState, ActiveProfileCredential?) -> ServerSwitchResult,
) : SessionSwitchApi {
	var prepareCalls = 0
	var commitCalls = 0
	var statusCalls = 0
	var commitFailure: Throwable? = null
	var statusFailure: Throwable? = null
	var prepareResult: ServerSwitchResult? = null
	var prepareGate: suspend () -> Unit = {}
	var statusResult: ServerSwitchResult = result(ServerSwitchState.COMMITTED, ActiveProfileCredential.fromToken("target-token"))
	val credentials = mutableListOf<SessionSwitchCredential>()

	override suspend fun prepare(
		credential: SessionSwitchCredential,
		switchId: UUID,
		targetProfileUserId: UUID,
		pin: String?,
	): ServerSwitchResult {
		prepareCalls++
		credentials += credential
		events += "prepare:${credential.label()}"
		prepareGate()
		return prepareResult ?: result(ServerSwitchState.PREPARED, null)
	}

	override suspend fun commit(
		credential: SessionSwitchCredential,
		switchId: UUID,
	): ServerSwitchResult {
		commitCalls++
		credentials += credential
		events += "commit:${credential.label()}"
		commitFailure?.let { throw it }
		return result(ServerSwitchState.COMMITTED, ActiveProfileCredential.fromToken("target-token"))
	}

	override suspend fun status(
		credential: SessionSwitchCredential,
		switchId: UUID,
	): ServerSwitchResult {
		statusCalls++
		credentials += credential
		events += "status:${credential.label()}"
		statusFailure?.let { throw it }
		return statusResult
	}

	override suspend fun abort(
		credential: SessionSwitchCredential,
		switchId: UUID,
	): ServerSwitchResult {
		credentials += credential
		events += "abort:${credential.label()}"
		return result(ServerSwitchState.ABORTED, null)
	}
}

private class RecordingSessionRuntimePort(
	private val events: MutableList<String>,
	var current: SessionSnapshot,
) : SessionSwitchRuntimePort {
	val installed = mutableListOf<SessionSnapshot>()
	val installCalls: Int get() = installed.size

	override fun currentSnapshot(): SessionSnapshot = current

	override suspend fun installCommitted(snapshot: SessionSnapshot): Boolean {
		events += "install:${snapshot.label()}"
		installed += snapshot
		current = snapshot
		return true
	}
}

private fun SessionSnapshot.label() = if (credential.value == "old-token") "old" else "target"

private fun SessionSwitchCredential.label() = when (this) {
	is SessionSwitchCredential.Active -> "Active"
	is SessionSwitchCredential.Recovery -> "Recovery"
}
