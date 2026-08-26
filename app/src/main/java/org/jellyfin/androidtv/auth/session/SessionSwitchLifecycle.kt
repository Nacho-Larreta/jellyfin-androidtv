package org.jellyfin.androidtv.auth.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.userApi
import java.util.UUID

fun interface SessionSwitchResetPort {
	suspend fun reset(snapshot: SessionSnapshot)
}

fun interface SessionSwitchReconnectPort {
	suspend fun reconnectAndVerify(snapshot: SessionSnapshot)
}

fun interface SessionSwitchCompletionPort {
	suspend fun publish(receipt: SessionSwitchCompletionReceipt)
}

fun interface SessionSwitchCompletionParticipant {
	suspend fun publish(receipt: SessionSwitchCompletionReceipt)
}

data class SessionSwitchCompletionReceipt(
	val switchId: UUID,
	val serverId: UUID,
	val profileUserId: UUID,
	val sessionEpoch: Long,
)

interface SessionSwitchLifecyclePort {
	suspend fun switch(request: SessionSwitchRequest): SessionSwitchOutcome
	suspend fun recover(serverId: UUID): SessionSwitchOutcome?
}

class SessionSwitchLifecycle(
	private val operations: SessionSwitchOperations,
	private val resetPort: SessionSwitchResetPort,
	private val reconnectPort: SessionSwitchReconnectPort,
	private val completionPort: SessionSwitchCompletionPort,
) : SessionSwitchLifecyclePort {
	private val ownershipMutex = Mutex()
	private val lifecycleMutex = Mutex()
	private var inFlight: InFlightLifecycle? = null

	override suspend fun switch(request: SessionSwitchRequest): SessionSwitchOutcome {
		val ownership = acquire(request)
		if (!ownership.owner) return ownership.completion.await().getOrThrow()

		val result = runCatching {
			lifecycleMutex.withLock { finish(operations.switch(request)) }
		}
		withContext(NonCancellable) {
			ownership.completion.complete(result)
			release(request.switchId, ownership.completion)
		}
		return result.getOrThrow()
	}

	override suspend fun recover(serverId: UUID): SessionSwitchOutcome? =
		lifecycleMutex.withLock { operations.recover(serverId)?.let { finish(it) } }

	private suspend fun acquire(request: SessionSwitchRequest): LifecycleOwnership = ownershipMutex.withLock {
		val current = inFlight
		if (current != null) {
			if (current.request != request) throw SwitchAlreadyInProgress()
			return@withLock LifecycleOwnership(owner = false, completion = current.completion)
		}
		val completion = CompletableDeferred<Result<SessionSwitchOutcome>>()
		inFlight = InFlightLifecycle(request, completion)
		LifecycleOwnership(owner = true, completion = completion)
	}

	private suspend fun release(
		switchId: UUID,
		completion: CompletableDeferred<Result<SessionSwitchOutcome>>,
	) = ownershipMutex.withLock {
		if (inFlight?.request?.switchId == switchId && inFlight?.completion === completion) inFlight = null
	}

	private suspend fun finish(outcome: SessionSwitchOutcome): SessionSwitchOutcome = when (outcome) {
		is SessionSwitchOutcome.CommittedPendingCleanup -> finishCommitted(outcome)
		is SessionSwitchOutcome.Completed,
		is SessionSwitchOutcome.Restored,
		-> outcome
	}

	private suspend fun finishCommitted(
		outcome: SessionSwitchOutcome.CommittedPendingCleanup,
	): SessionSwitchOutcome.Completed {
		resetPort.reset(outcome.snapshot)
		reconnectPort.reconnectAndVerify(outcome.snapshot)
		completionPort.publish(outcome.completionReceipt())
		val cleanedSnapshot = operations.completeCleanup(outcome.snapshot.serverId, outcome.switchId)
		return SessionSwitchOutcome.Completed(outcome.switchId, cleanedSnapshot)
	}

	private data class InFlightLifecycle(
		val request: SessionSwitchRequest,
		val completion: CompletableDeferred<Result<SessionSwitchOutcome>>,
	)

	private data class LifecycleOwnership(
		val owner: Boolean,
		val completion: CompletableDeferred<Result<SessionSwitchOutcome>>,
	)
}

class InstalledSessionResetPort(
	private val runtimePort: SessionSwitchRuntimePort,
) : SessionSwitchResetPort {
	override suspend fun reset(snapshot: SessionSnapshot) {
		if (runtimePort.currentSnapshot() != snapshot) {
			throw SessionSwitchRecoveryRequired("Installed runtime does not match committed session cleanup.")
		}
	}
}

class SessionIdentityReconnectPort(
	private val apiClient: ApiClient,
) : SessionSwitchReconnectPort {
	override suspend fun reconnectAndVerify(snapshot: SessionSnapshot) {
		val user = try {
			withContext(Dispatchers.IO) { apiClient.userApi.getCurrentUser().content }
		} catch (failure: ApiClientException) {
			throw SessionSwitchRecoveryRequired("Unable to verify the reconnected session identity.", failure)
		}
		if (user.id != snapshot.profileUserId) {
			throw SessionSwitchRecoveryRequired("Reconnected session identity does not match the committed profile.")
		}
	}
}

class CompositeSessionSwitchCompletionPort(
	private val participants: List<SessionSwitchCompletionParticipant>,
) : SessionSwitchCompletionPort {
	override suspend fun publish(receipt: SessionSwitchCompletionReceipt) {
		if (participants.isEmpty()) {
			throw SessionSwitchRecoveryRequired("No session switch completion consumer acknowledged cleanup.")
		}
		participants.forEach { participant -> participant.publish(receipt) }
	}
}

private fun SessionSwitchOutcome.CommittedPendingCleanup.completionReceipt() = SessionSwitchCompletionReceipt(
	switchId = switchId,
	serverId = snapshot.serverId,
	profileUserId = snapshot.profileUserId,
	sessionEpoch = snapshot.sessionEpoch,
)
