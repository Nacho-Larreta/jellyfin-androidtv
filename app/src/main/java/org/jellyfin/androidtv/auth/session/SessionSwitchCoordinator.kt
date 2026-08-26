package org.jellyfin.androidtv.auth.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.util.UUID

fun interface SessionSwitchQuiescePort {
	suspend fun stopAndReport(snapshot: SessionSnapshot, switchId: UUID): SessionQuiesceResult
}

sealed interface SessionQuiesceResult {
	data object NotActive : SessionQuiesceResult
	data class Acknowledged(val reportKey: String) : SessionQuiesceResult
	data class Failed(val reason: String) : SessionQuiesceResult
}

interface SessionSwitchRuntimePort {
	fun currentSnapshot(): SessionSnapshot?
	suspend fun installCommitted(snapshot: SessionSnapshot): Boolean
}

data class SessionSwitchEnvironment(
	val api: SessionSwitchApi,
	val store: SessionSwitchStore,
	val barrier: SessionAdmissionBarrier,
	val quiescePort: SessionSwitchQuiescePort,
	val runtimePort: SessionSwitchRuntimePort,
	val clock: Clock = Clock.systemUTC(),
)

interface SessionSwitchOperations {
	suspend fun switch(request: SessionSwitchRequest): SessionSwitchOutcome
	suspend fun recover(serverId: UUID): SessionSwitchOutcome?
	suspend fun completeCleanup(serverId: UUID, switchId: UUID): SessionSnapshot
}

class SessionSwitchCoordinator(
	environment: SessionSwitchEnvironment,
) : SessionSwitchOperations {
	private val ownershipMutex = Mutex()
	private val operationMutex = Mutex()
	private var inFlight: InFlightSwitch? = null
	private val transaction = SessionSwitchTransaction(environment)

	override suspend fun switch(request: SessionSwitchRequest): SessionSwitchOutcome {
		val ownership = acquire(request)
		if (!ownership.owner) return ownership.completion.await().getOrThrow()

		val result = runCatching {
			withContext(NonCancellable) {
				operationMutex.withLock { transaction.execute(request) }
			}
		}
		ownership.completion.complete(result)
		release(request.switchId, ownership.completion)
		return result.getOrThrow()
	}

	override suspend fun recover(serverId: UUID): SessionSwitchOutcome? = withContext(NonCancellable) {
		operationMutex.withLock { transaction.recover(serverId) }
	}

	override suspend fun completeCleanup(serverId: UUID, switchId: UUID): SessionSnapshot = withContext(NonCancellable) {
		operationMutex.withLock { transaction.completeCleanup(serverId, switchId) }
	}

	private suspend fun acquire(request: SessionSwitchRequest): SwitchOwnership = ownershipMutex.withLock {
		val current = inFlight
		if (current != null) {
			if (current.request != request) throw SwitchAlreadyInProgress()
			return@withLock SwitchOwnership(owner = false, completion = current.completion)
		}
		val completion = CompletableDeferred<Result<SessionSwitchOutcome>>()
		inFlight = InFlightSwitch(request, completion)
		SwitchOwnership(owner = true, completion = completion)
	}

	private suspend fun release(
		switchId: UUID,
		completion: CompletableDeferred<Result<SessionSwitchOutcome>>,
	) = ownershipMutex.withLock {
		if (inFlight?.request?.switchId == switchId && inFlight?.completion === completion) inFlight = null
	}

	private data class InFlightSwitch(
		val request: SessionSwitchRequest,
		val completion: CompletableDeferred<Result<SessionSwitchOutcome>>,
	)

	private data class SwitchOwnership(
		val owner: Boolean,
		val completion: CompletableDeferred<Result<SessionSwitchOutcome>>,
	)
}
