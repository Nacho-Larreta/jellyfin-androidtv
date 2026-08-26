package org.jellyfin.androidtv.auth.session

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SessionAdmissionBarrier {
	private val mutex = Mutex()
	private var snapshot: SessionSnapshot? = null
	private var closed = true

	suspend fun initialize(activeSnapshot: SessionSnapshot) = mutex.withLock {
		if (snapshot == null) {
			snapshot = activeSnapshot
			closed = false
		}
	}

	suspend fun admit(): SessionSnapshot = mutex.withLock {
		if (closed) throw SessionSwitchInProgress()
		checkNotNull(snapshot)
	}

	suspend fun close(expectedSnapshot: SessionSnapshot) = mutex.withLock {
		val current = snapshot
		if (current != null && current != expectedSnapshot) {
			throw SessionSwitchRecoveryRequired("The admission barrier is bound to another session epoch.")
		}
		snapshot = expectedSnapshot
		closed = true
	}

	suspend fun recoverClosed(durableSnapshot: SessionSnapshot) = mutex.withLock {
		snapshot = durableSnapshot
		closed = true
	}

	suspend fun open(activeSnapshot: SessionSnapshot) = mutex.withLock {
		snapshot = activeSnapshot
		closed = false
	}
}
