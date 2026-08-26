package org.jellyfin.androidtv.auth.session

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.UUID

class SessionSwitchLifecycleTests : FunSpec({
	test("finishes a confirmed switch before clearing durable cleanup") {
		val fixture = SessionSwitchLifecycleFixture()

		val outcome = fixture.lifecycle.switch(fixture.request)

		outcome shouldBe SessionSwitchOutcome.Completed(fixture.switchId, fixture.snapshot)
		fixture.events.shouldContainExactly(
			"switch",
			"reset",
			"reconnect",
			"complete-event",
			"clear-cleanup",
		)
	}

	test("restored pre-commit session does not run post-commit cleanup") {
		val fixture = SessionSwitchLifecycleFixture(
			switchOutcome = SessionSwitchOutcome.Restored(sessionSnapshot()),
		)

		val outcome = fixture.lifecycle.switch(fixture.request)

		outcome shouldBe fixture.switchOutcome
		fixture.events.shouldContainExactly("switch")
	}

	test("recovery finishes a committed cleanup marker in the same order") {
		val fixture = SessionSwitchLifecycleFixture()

		val outcome = fixture.lifecycle.recover(fixture.snapshot.serverId)

		outcome shouldBe SessionSwitchOutcome.Completed(fixture.switchId, fixture.snapshot)
		fixture.events.shouldContainExactly(
			"recover",
			"reset",
			"reconnect",
			"complete-event",
			"clear-cleanup",
		)
	}

	test("post-commit failure retains cleanup and stops later phases") {
		SessionLifecyclePhase.entries
			.filterNot { it == SessionLifecyclePhase.CANCELLATION }
			.forEach { failedPhase ->
			val fixture = SessionSwitchLifecycleFixture(failedPhase = failedPhase)

			shouldThrow<SessionSwitchRecoveryRequired> {
				fixture.lifecycle.recover(fixture.snapshot.serverId)
			}

			fixture.cleanupCleared shouldBe false
			fixture.events.contains("clear-cleanup") shouldBe false
		}
	}

	test("replay after acknowledged completion retries the same durable switch") {
		val fixture = SessionSwitchLifecycleFixture(failCleanupOnce = true)

		shouldThrow<SessionSwitchRecoveryRequired> {
			fixture.lifecycle.recover(fixture.snapshot.serverId)
		}
		val recovered = fixture.lifecycle.recover(fixture.snapshot.serverId)

		recovered shouldBe SessionSwitchOutcome.Completed(fixture.switchId, fixture.snapshot)
		fixture.completedSwitches.shouldContainExactly(fixture.switchId, fixture.switchId)
		fixture.cleanupCleared shouldBe true
	}

	test("same request joins the complete post-commit lifecycle") {
		val resetStarted = CompletableDeferred<Unit>()
		val releaseReset = CompletableDeferred<Unit>()
		val fixture = SessionSwitchLifecycleFixture(
			resetPause = ResetPause(resetStarted, releaseReset),
		)

		coroutineScope {
			val first = async { fixture.lifecycle.switch(fixture.request) }
			resetStarted.await()
			val replay = async(start = CoroutineStart.UNDISPATCHED) { fixture.lifecycle.switch(fixture.request) }
			releaseReset.complete(Unit)

			first.await() shouldBe replay.await()
		}

		fixture.events.shouldContainExactly(
			"switch",
			"reset",
			"reconnect",
			"complete-event",
			"clear-cleanup",
		)
	}

	test("different request is rejected while post-commit lifecycle owns the switch") {
		val resetStarted = CompletableDeferred<Unit>()
		val releaseReset = CompletableDeferred<Unit>()
		val fixture = SessionSwitchLifecycleFixture(
			resetPause = ResetPause(resetStarted, releaseReset),
		)

		coroutineScope {
			val first = async { fixture.lifecycle.switch(fixture.request) }
			resetStarted.await()

			shouldThrow<SwitchAlreadyInProgress> {
				fixture.lifecycle.switch(fixture.request.copy(switchId = UUID.randomUUID()))
			}
			releaseReset.complete(Unit)
			first.await()
		}

		fixture.events.count { it == "switch" } shouldBe 1
	}

	test("empty completion composite fails closed") {
		val port = CompositeSessionSwitchCompletionPort(emptyList())

		shouldThrow<SessionSwitchRecoveryRequired> {
			port.publish(
				SessionSwitchCompletionReceipt(
					switchId = UUID.randomUUID(),
					serverId = UUID.randomUUID(),
					profileUserId = UUID.randomUUID(),
					sessionEpoch = 1,
				)
			)
		}
	}

	test("cancellation releases lifecycle ownership for recovery retry") {
		val fixture = SessionSwitchLifecycleFixture(failedPhase = SessionLifecyclePhase.CANCELLATION)

		shouldThrow<CancellationException> { fixture.lifecycle.switch(fixture.request) }
		shouldThrow<CancellationException> {
			fixture.lifecycle.switch(fixture.request.copy(switchId = UUID.randomUUID()))
		}

		fixture.events.count { it == "switch" } shouldBe 2
	}
})

private enum class SessionLifecyclePhase {
	RESET,
	RECONNECT,
	COMPLETION,
	CANCELLATION,
}

private data class ResetPause(
	val started: CompletableDeferred<Unit>,
	val release: CompletableDeferred<Unit>,
)

private class SessionSwitchLifecycleFixture(
	val switchId: UUID = UUID.randomUUID(),
	val snapshot: SessionSnapshot = sessionSnapshot(),
	val switchOutcome: SessionSwitchOutcome = SessionSwitchOutcome.CommittedPendingCleanup(switchId, snapshot),
	private val failedPhase: SessionLifecyclePhase? = null,
	private var failCleanupOnce: Boolean = false,
	private val resetPause: ResetPause? = null,
) {
	val events = mutableListOf<String>()
	val completedSwitches = mutableListOf<UUID>()
	var cleanupCleared = false
		private set
	val request = SessionSwitchRequest(switchId, UUID.randomUUID())

	private val operations = object : SessionSwitchOperations {
		override suspend fun switch(request: SessionSwitchRequest): SessionSwitchOutcome {
			events += "switch"
			return switchOutcome
		}

		override suspend fun recover(serverId: UUID): SessionSwitchOutcome? {
			events += "recover"
			return switchOutcome
		}

		override suspend fun completeCleanup(serverId: UUID, switchId: UUID): SessionSnapshot {
			events += "clear-cleanup"
			if (failCleanupOnce) {
				failCleanupOnce = false
				throw SessionSwitchRecoveryRequired("cleanup write failed")
			}
			cleanupCleared = true
			return snapshot
		}
	}

	val lifecycle = SessionSwitchLifecycle(
		operations = operations,
		resetPort = SessionSwitchResetPort {
			events += "reset"
			resetPause?.started?.complete(Unit)
			resetPause?.release?.await()
			if (failedPhase == SessionLifecyclePhase.CANCELLATION) {
				throw CancellationException("lifecycle cancelled")
			}
			if (failedPhase == SessionLifecyclePhase.RESET) {
				throw SessionSwitchRecoveryRequired("reset failed")
			}
		},
		reconnectPort = SessionSwitchReconnectPort {
			events += "reconnect"
			if (failedPhase == SessionLifecyclePhase.RECONNECT) {
				throw SessionSwitchRecoveryRequired("reconnect failed")
			}
		},
		completionPort = SessionSwitchCompletionPort { receipt ->
			events += "complete-event"
			completedSwitches += receipt.switchId
			if (failedPhase == SessionLifecyclePhase.COMPLETION) {
				throw SessionSwitchRecoveryRequired("completion failed")
			}
		},
	)
}

private fun sessionSnapshot() = SessionSnapshot(
	serverId = UUID.randomUUID(),
	deviceId = "device-id",
	profileUserId = UUID.randomUUID(),
	credential = ActiveProfileCredential.fromToken("access-token"),
	sessionEpoch = 1,
)
