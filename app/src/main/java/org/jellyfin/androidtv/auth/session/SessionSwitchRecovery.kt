package org.jellyfin.androidtv.auth.session

import java.util.UUID

internal class SessionSwitchRecovery(
	private val environment: SessionSwitchEnvironment,
	private val persistence: SessionSwitchPersistence,
) {
	suspend fun resume(
		envelope: SessionEnvelope,
		pending: PendingSwitchRecord,
		credential: SessionSwitchCredential,
	): SessionSwitchOutcome = when (pending.phase) {
		PendingSwitchPhase.PREPARING,
		PendingSwitchPhase.QUIESCING,
		-> restorePreCommit(envelope, pending, credential)

		PendingSwitchPhase.COMMITTING,
		PendingSwitchPhase.COMMIT_UNKNOWN,
		-> resolveUnknownCommit(envelope, pending, credential)

		PendingSwitchPhase.INSTALLING -> throw SessionSwitchRecoveryRequired(
			"An installing switch is missing its committed cleanup marker.",
		)
	}

	suspend fun resolveUnknownCommit(
		envelope: SessionEnvelope,
		pending: PendingSwitchRecord,
		credential: SessionSwitchCredential,
		uncertainty: SessionSwitchCommitUnknown? = null,
	): SessionSwitchOutcome {
		val status = resolveStatus(credential, pending, uncertainty)
		return when (status.state) {
			ServerSwitchState.COMMITTED -> installConfirmedCommit(envelope, pending, status)
			ServerSwitchState.PREPARED -> replayCommit(envelope, pending, credential)
			ServerSwitchState.EXPIRED,
			ServerSwitchState.ABORTED,
			-> restoreOldEnvelope(envelope)
		}
	}

	suspend fun installConfirmedCommit(
		envelope: SessionEnvelope,
		pending: PendingSwitchRecord,
		result: ServerSwitchResult,
	): SessionSwitchOutcome {
		validateResultIdentity(result, pending)
		if (result.state != ServerSwitchState.COMMITTED) {
			throw SessionSwitchRecoveryRequired("Commit returned ${result.state} instead of COMMITTED.")
		}
		val credential = result.activeCredential
			?: throw SessionSwitchRecoveryRequired("Committed switch omitted its active credential.")
		val installedSnapshot = createInstalledSnapshot(envelope, result, credential)
		val committedEnvelope = envelope.copy(
			activeProfile = installedSnapshot,
			pendingSwitch = pending.copy(phase = PendingSwitchPhase.INSTALLING),
			cleanupMarker = CommittedPendingCleanup(pending.switchId),
		)
		val persistedEnvelope = persistence.replace(envelope, committedEnvelope)
		installCommitted(persistedEnvelope, pending.switchId)
		return SessionSwitchOutcome.CommittedPendingCleanup(persistedEnvelope.activeProfile)
	}

	suspend fun installCommitted(envelope: SessionEnvelope, switchId: UUID) {
		if (envelope.cleanupMarker?.switchId != switchId) {
			throw SessionSwitchRecoveryRequired("Committed session is missing its cleanup marker.")
		}
		if (!environment.runtimePort.installCommitted(envelope.activeProfile)) {
			throw SessionSwitchRecoveryRequired("The committed session could not be installed.")
		}
	}

	suspend fun abortBeforeCommit(
		envelope: SessionEnvelope,
		pending: PendingSwitchRecord,
		credential: SessionSwitchCredential,
	) {
		try {
			environment.api.abort(credential, pending.switchId)
		} catch (rejection: SessionSwitchRejected) {
			throw SessionSwitchRecoveryRequired("Unable to verify the old session after abort.", rejection)
		} catch (failure: SessionSwitchRecoveryRequired) {
			throw SessionSwitchRecoveryRequired("Unable to verify the old session after abort.", failure)
		}
		restoreOldEnvelope(envelope)
	}

	suspend fun restoreOldEnvelope(envelope: SessionEnvelope): SessionSwitchOutcome.Restored {
		val restored = envelope.copy(pendingSwitch = null, cleanupMarker = null)
		val persistedEnvelope = persistence.replace(envelope, restored)
		environment.barrier.open(persistedEnvelope.activeProfile)
		return SessionSwitchOutcome.Restored(persistedEnvelope.activeProfile)
	}

	private suspend fun restorePreCommit(
		envelope: SessionEnvelope,
		pending: PendingSwitchRecord,
		credential: SessionSwitchCredential,
	): SessionSwitchOutcome {
		val status = try {
			environment.api.status(credential, pending.switchId)
		} catch (rejection: SessionSwitchRejected) {
			if (rejection.statusCode == NOT_FOUND_STATUS) return restoreOldEnvelope(envelope)
			throw SessionSwitchRecoveryRequired("Unable to verify the pre-commit switch outcome.", rejection)
		} catch (failure: SessionSwitchRecoveryRequired) {
			throw SessionSwitchRecoveryRequired("Unable to verify the pre-commit switch outcome.", failure)
		}
		validateResultIdentity(status, pending)
		if (status.state == ServerSwitchState.COMMITTED) {
			return installConfirmedCommit(envelope, pending, status)
		}
		if (status.state == ServerSwitchState.PREPARED) environment.api.abort(credential, pending.switchId)
		return restoreOldEnvelope(envelope)
	}

	private suspend fun resolveStatus(
		credential: SessionSwitchCredential,
		pending: PendingSwitchRecord,
		uncertainty: SessionSwitchCommitUnknown?,
	): ServerSwitchResult = try {
		environment.api.status(credential, pending.switchId).also { validateResultIdentity(it, pending) }
	} catch (rejection: SessionSwitchRejected) {
		throw SessionSwitchRecoveryRequired("Unable to resolve the durable commit outcome.", uncertainty ?: rejection)
	} catch (failure: SessionSwitchRecoveryRequired) {
		throw SessionSwitchRecoveryRequired("Unable to resolve the durable commit outcome.", uncertainty ?: failure)
	}

	private suspend fun replayCommit(
		envelope: SessionEnvelope,
		pending: PendingSwitchRecord,
		credential: SessionSwitchCredential,
	): SessionSwitchOutcome {
		val replay = try {
			environment.api.commit(credential, pending.switchId)
		} catch (unknown: SessionSwitchCommitUnknown) {
			throw SessionSwitchRecoveryRequired("Unable to replay the prepared durable commit.", unknown)
		} catch (rejection: SessionSwitchRejected) {
			throw SessionSwitchRecoveryRequired("Unable to replay the prepared durable commit.", rejection)
		}
		return installConfirmedCommit(envelope, pending, replay)
	}

	private companion object {
		const val NOT_FOUND_STATUS = 404
	}

	private fun createInstalledSnapshot(
		envelope: SessionEnvelope,
		result: ServerSwitchResult,
		credential: ActiveProfileCredential,
	) = SessionSnapshot(
		serverId = envelope.activeProfile.serverId,
		deviceId = envelope.activeProfile.deviceId,
		profileUserId = result.targetProfileUserId,
		credential = credential,
		sessionEpoch = incrementEpoch(envelope.activeProfile.sessionEpoch),
		profileSelectorId = result.profileSelectorId,
		ownerUserId = result.ownerUserId,
	)
}

private fun incrementEpoch(epoch: Long): Long = try {
	Math.incrementExact(epoch)
} catch (error: ArithmeticException) {
	throw SessionSwitchRecoveryRequired("The durable session epoch is exhausted.", error)
}
