package org.jellyfin.androidtv.auth.session

import org.jellyfin.androidtv.auth.model.isValidProfilePin
import java.util.UUID

internal class SessionSwitchTransaction(
	private val environment: SessionSwitchEnvironment,
) {
	private val persistence = SessionSwitchPersistence(environment.store)
	private val recovery = SessionSwitchRecovery(environment, persistence)

	suspend fun execute(request: SessionSwitchRequest): SessionSwitchOutcome {
		if (request.pin != null && !isValidProfilePin(request.pin)) {
			throw SessionSwitchRejected("Profile PIN must contain 4 to 8 ASCII digits.")
		}
		val runtimeSnapshot = environment.runtimePort.currentSnapshot()
			?: throw SessionSwitchRejected("An active authenticated session is required.")
		val envelope = persistence.loadOrInitialize(runtimeSnapshot)
		resumeDurableSwitch(request, envelope, runtimeSnapshot)?.let { return it }
		validateRuntimeIdentity(runtimeSnapshot, envelope.activeProfile)

		val credential = resolveCredential(envelope, request.authority)
		val pending = createPendingSwitch(request, envelope)
		val preparingEnvelope = persistence.replace(envelope, envelope.copy(pendingSwitch = pending))
		environment.barrier.initialize(preparingEnvelope.activeProfile)
		environment.barrier.close(preparingEnvelope.activeProfile)
		prepare(request, pending, credential, preparingEnvelope)
		val quiescingEnvelope = quiesce(request, pending, preparingEnvelope, credential)
		return commit(request, pending, quiescingEnvelope, credential)
	}

	suspend fun recover(serverId: UUID): SessionSwitchOutcome? {
		val envelope = persistence.load(serverId) ?: return null
		environment.barrier.recoverClosed(envelope.activeProfile)
		envelope.cleanupMarker?.let { cleanup ->
			recovery.installCommitted(envelope, cleanup.switchId)
			return SessionSwitchOutcome.CommittedPendingCleanup(envelope.activeProfile)
		}

		val pending = envelope.pendingSwitch ?: run {
			environment.barrier.open(envelope.activeProfile)
			return SessionSwitchOutcome.Restored(envelope.activeProfile)
		}
		return recovery.resume(envelope, pending, resolveCredential(envelope, pending.authority))
	}

	suspend fun completeCleanup(serverId: UUID, switchId: UUID): SessionSnapshot {
		val envelope = persistence.load(serverId)
			?: throw SessionSwitchRecoveryRequired("No durable session exists for cleanup completion.")
		if (envelope.cleanupMarker?.switchId != switchId) {
			throw SessionSwitchRecoveryRequired("Cleanup completion does not match the durable switch.")
		}
		val cleaned = persistence.replace(envelope, envelope.copy(pendingSwitch = null, cleanupMarker = null))
		environment.barrier.open(cleaned.activeProfile)
		return cleaned.activeProfile
	}

	private suspend fun resumeDurableSwitch(
		request: SessionSwitchRequest,
		envelope: SessionEnvelope,
		runtimeSnapshot: SessionSnapshot,
	): SessionSwitchOutcome? {
		envelope.cleanupMarker?.let { cleanup ->
			if (cleanup.switchId != request.switchId) throw SwitchAlreadyInProgress()
			validateDurableRequest(request, envelope, cleanupRequired = true)
			environment.barrier.close(envelope.activeProfile)
			recovery.installCommitted(envelope, request.switchId)
			return SessionSwitchOutcome.CommittedPendingCleanup(envelope.activeProfile)
		}

		val pending = envelope.pendingSwitch ?: return null
		if (pending.switchId != request.switchId) throw SwitchAlreadyInProgress()
		validateDurableRequest(request, envelope, cleanupRequired = false)
		validateRuntimeIdentity(runtimeSnapshot, envelope.activeProfile)
		environment.barrier.recoverClosed(envelope.activeProfile)
		return recovery.resume(envelope, pending, resolveCredential(envelope, pending.authority))
	}

	private suspend fun prepare(
		request: SessionSwitchRequest,
		pending: PendingSwitchRecord,
		credential: SessionSwitchCredential,
		envelope: SessionEnvelope,
	) {
		try {
			val prepared = environment.api.prepare(credential, request.switchId, request.targetProfileUserId, request.pin)
			validatePreparedResult(prepared, pending)
		} catch (error: SessionSwitchRejected) {
			recovery.restoreOldEnvelope(envelope)
			throw error
		}
	}

	private suspend fun quiesce(
		request: SessionSwitchRequest,
		pending: PendingSwitchRecord,
		envelope: SessionEnvelope,
		credential: SessionSwitchCredential,
	): SessionEnvelope {
		val quiescingEnvelope = persistence.replacePhase(envelope, pending, PendingSwitchPhase.QUIESCING)
		when (val result = environment.quiescePort.stopAndReport(quiescingEnvelope.activeProfile, request.switchId)) {
			SessionQuiesceResult.NotActive -> Unit
			is SessionQuiesceResult.Acknowledged -> if (result.reportKey.isBlank()) {
				recovery.abortBeforeCommit(quiescingEnvelope, pending, credential)
				throw SessionSwitchRejected("Old-session quiesce returned an empty report key.")
			}
			is SessionQuiesceResult.Failed -> {
				recovery.abortBeforeCommit(quiescingEnvelope, pending, credential)
				throw SessionSwitchRejected("Old-session quiesce did not settle: ${result.reason}")
			}
		}
		return quiescingEnvelope
	}

	private suspend fun commit(
		request: SessionSwitchRequest,
		pending: PendingSwitchRecord,
		envelope: SessionEnvelope,
		credential: SessionSwitchCredential,
	): SessionSwitchOutcome {
		val committingEnvelope = persistence.replacePhase(envelope, pending, PendingSwitchPhase.COMMITTING)
		val committed = try {
			environment.api.commit(credential, request.switchId)
		} catch (uncertainty: SessionSwitchCommitUnknown) {
			val unknownEnvelope = persistence.replacePhase(
				committingEnvelope,
				pending,
				PendingSwitchPhase.COMMIT_UNKNOWN,
			)
			return recovery.resolveUnknownCommit(unknownEnvelope, pending, credential, uncertainty)
		} catch (rejection: SessionSwitchRejected) {
			recovery.abortBeforeCommit(committingEnvelope, pending, credential)
			throw rejection
		}
		return recovery.installConfirmedCommit(committingEnvelope, pending, committed)
	}

	private fun createPendingSwitch(request: SessionSwitchRequest, envelope: SessionEnvelope) = PendingSwitchRecord(
		switchId = request.switchId,
		targetProfileUserId = request.targetProfileUserId,
		oldProfileUserId = envelope.activeProfile.profileUserId,
		oldSessionEpoch = envelope.activeProfile.sessionEpoch,
		phase = PendingSwitchPhase.PREPARING,
		createdAtEpochMillis = environment.clock.millis(),
		authority = request.authority,
	)
}

private fun validateDurableRequest(
	request: SessionSwitchRequest,
	envelope: SessionEnvelope,
	cleanupRequired: Boolean,
) {
	val pending = envelope.pendingSwitch ?: throw SwitchAlreadyInProgress()
	val requestMatches = pending.targetProfileUserId == request.targetProfileUserId &&
		pending.authority == request.authority
	val cleanupMatches = !cleanupRequired || (
		envelope.cleanupMarker?.switchId == request.switchId &&
		pending.phase == PendingSwitchPhase.INSTALLING &&
		envelope.activeProfile.profileUserId == request.targetProfileUserId
	)
	if (!requestMatches || !cleanupMatches) throw SwitchAlreadyInProgress()
}

internal class SessionSwitchPersistence(
	private val store: SessionSwitchStore,
) {
	fun load(serverId: UUID): SessionEnvelope? = store.load(serverId)

	fun loadOrInitialize(snapshot: SessionSnapshot): SessionEnvelope {
		val stored = load(snapshot.serverId)
		if (stored != null) return stored
		val initialized = SessionEnvelope(activeProfile = snapshot)
		return replace(expected = null, updated = initialized)
	}

	fun replacePhase(envelope: SessionEnvelope, pending: PendingSwitchRecord, phase: PendingSwitchPhase) =
		replace(envelope, envelope.copy(pendingSwitch = pending.copy(phase = phase)))

	fun replace(expected: SessionEnvelope?, updated: SessionEnvelope): SessionEnvelope =
		store.replace(expected, updated)
			?: throw SessionSwitchRecoveryRequired("The durable session envelope could not be replaced.")
}

private fun resolveCredential(
	envelope: SessionEnvelope,
	authority: SessionSwitchAuthority,
): SessionSwitchCredential = when (authority) {
	SessionSwitchAuthority.ActiveProfile -> activeCredential(envelope)
	SessionSwitchAuthority.OwnerRecovery -> SessionSwitchCredential.Recovery(
		envelope.ownerRecovery
			?: throw SessionSwitchRejected("Owner recovery requires explicit reauthentication."),
	)
}

internal fun activeCredential(envelope: SessionEnvelope) = SessionSwitchCredential.Active(envelope.activeProfile)

private fun validateRuntimeIdentity(runtime: SessionSnapshot, durable: SessionSnapshot) {
	val runtimeIdentity = listOf(runtime.serverId, runtime.deviceId, runtime.profileUserId, runtime.sessionEpoch)
	val durableIdentity = listOf(durable.serverId, durable.deviceId, durable.profileUserId, durable.sessionEpoch)
	if (runtimeIdentity != durableIdentity) {
		throw SessionSwitchRecoveryRequired("Runtime identity does not match the durable session envelope.")
	}
}

private fun validatePreparedResult(result: ServerSwitchResult, pending: PendingSwitchRecord) {
	validateResultIdentity(result, pending)
	if (result.state != ServerSwitchState.PREPARED) {
		throw SessionSwitchRejected("Prepare returned ${result.state} instead of PREPARED.")
	}
}

internal fun validateResultIdentity(result: ServerSwitchResult, pending: PendingSwitchRecord) {
	if (result.switchId != pending.switchId || result.targetProfileUserId != pending.targetProfileUserId) {
		throw SessionSwitchRecoveryRequired("Server switch identity does not match the durable request.")
	}
}
