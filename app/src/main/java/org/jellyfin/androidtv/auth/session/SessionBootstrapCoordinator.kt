package org.jellyfin.androidtv.auth.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.androidtv.auth.repository.SessionRepository
import java.util.UUID

enum class SessionBootstrapOutcome {
	Ready,
	RecoveryRequired,
}

enum class SessionBootstrapState {
	CLOSED,
	RECOVERING,
	RESTORING,
	READY,
	RECOVERY_REQUIRED,
}

class SessionBootstrapCoordinator(
	private val sessionRepository: SessionRepository,
	private val sessionSwitchLifecycle: SessionSwitchLifecyclePort,
	private val lastServerId: () -> UUID?,
) {
	private val mutex = Mutex()
	private val mutableState = MutableStateFlow(SessionBootstrapState.CLOSED)
	val state: StateFlow<SessionBootstrapState> = mutableState.asStateFlow()

	suspend fun initialize(): SessionBootstrapOutcome = mutex.withLock {
		if (state.value == SessionBootstrapState.READY) return@withLock SessionBootstrapOutcome.Ready

		mutableState.value = SessionBootstrapState.RECOVERING
		try {
			val serverId = lastServerId()
			if (serverId != null) sessionSwitchLifecycle.recover(serverId)
			mutableState.value = SessionBootstrapState.RESTORING
			sessionRepository.restoreSessionForBootstrap()
			sessionRepository.publishSessionReady()
			mutableState.value = SessionBootstrapState.READY
			SessionBootstrapOutcome.Ready
		} catch (cancellation: CancellationException) {
			throw cancellation
		} catch (_: SessionSwitchRecoveryRequired) {
			failClosed()
		} catch (_: SessionSwitchRejected) {
			failClosed()
		} catch (_: SwitchAlreadyInProgress) {
			failClosed()
		}
	}

	private suspend fun failClosed(): SessionBootstrapOutcome {
		sessionRepository.failSessionRestore()
		mutableState.value = SessionBootstrapState.RECOVERY_REQUIRED
		return SessionBootstrapOutcome.RecoveryRequired
	}
}
