package org.jellyfin.androidtv.auth.session

import org.jellyfin.androidtv.auth.model.AuthenticationActiveProfileSession
import org.jellyfin.androidtv.auth.model.AuthenticationCommittedPendingCleanup
import org.jellyfin.androidtv.auth.model.AuthenticationOwnerRecoverySession
import org.jellyfin.androidtv.auth.model.AuthenticationPendingSwitch
import org.jellyfin.androidtv.auth.model.AuthenticationPendingSwitchAuthority
import org.jellyfin.androidtv.auth.model.AuthenticationPendingSwitchPhase
import org.jellyfin.androidtv.auth.model.AuthenticationSessionEnvelope
import org.jellyfin.androidtv.auth.store.AuthenticationAuthoritySnapshot
import org.jellyfin.androidtv.auth.store.AuthenticationStore
import java.util.UUID

interface SessionSwitchStore {
	fun load(serverId: UUID): SessionEnvelope?
	fun replace(expected: SessionEnvelope?, updated: SessionEnvelope): SessionEnvelope?
}

class AuthenticationSessionSwitchStore(
	private val authenticationStore: AuthenticationStore,
) : SessionSwitchStore {
	override fun load(serverId: UUID): SessionEnvelope? =
		authenticationStore.getAuthoritySnapshot(serverId)?.let { snapshot ->
			snapshot.envelope?.toDomain(serverId, snapshot.generation)
		}

	override fun replace(expected: SessionEnvelope?, updated: SessionEnvelope): SessionEnvelope? {
		val serverId = updated.activeProfile.serverId
		val expectedAuthority = AuthenticationAuthoritySnapshot(
			generation = expected?.authorityGeneration ?: updated.authorityGeneration,
			envelope = expected?.toStored(),
		)
		return authenticationStore.replaceSessionEnvelope(
			serverId,
			expectedAuthority,
			updated.toStored(),
		)?.let { persisted -> persisted.envelope?.toDomain(serverId, persisted.generation) }
	}
}

private fun AuthenticationSessionEnvelope.toDomain(serverId: UUID, authorityGeneration: Long): SessionEnvelope? {
	val storedActive = activeProfile ?: return null
	val active = SessionSnapshot(
		serverId = serverId,
		deviceId = storedActive.deviceId,
		profileUserId = storedActive.profileUserId,
		credential = ActiveProfileCredential.fromToken(storedActive.accessToken),
		sessionEpoch = sessionEpoch,
		profileSelectorId = storedActive.profileSelectorId,
		ownerUserId = storedActive.ownerUserId,
	)
	return SessionEnvelope(
		activeProfile = active,
		ownerRecovery = ownerRecovery?.let { recovery ->
			OwnerRecoverySession(
				serverId = serverId,
				deviceId = recovery.deviceId,
				ownerUserId = recovery.ownerUserId,
				profileSelectorId = recovery.profileSelectorId,
				credential = OwnerRecoveryCredential.fromToken(recovery.accessToken),
			)
		},
		pendingSwitch = pendingSwitch?.toDomain(),
		cleanupMarker = cleanupMarker?.let { CommittedPendingCleanup(it.switchId) },
		authorityGeneration = authorityGeneration,
	)
}

private fun SessionEnvelope.toStored() = AuthenticationSessionEnvelope(
	activeProfile = AuthenticationActiveProfileSession(
		profileUserId = activeProfile.profileUserId,
		accessToken = activeProfile.credential.value,
		deviceId = activeProfile.deviceId,
		profileSelectorId = activeProfile.profileSelectorId,
		ownerUserId = activeProfile.ownerUserId,
	),
	ownerRecovery = ownerRecovery?.let { recovery ->
		AuthenticationOwnerRecoverySession(
			ownerUserId = recovery.ownerUserId,
			accessToken = recovery.credential.value,
			profileSelectorId = recovery.profileSelectorId,
			deviceId = recovery.deviceId,
		)
	},
	sessionEpoch = activeProfile.sessionEpoch,
	pendingSwitch = pendingSwitch?.toStored(),
	cleanupMarker = cleanupMarker?.let { AuthenticationCommittedPendingCleanup(it.switchId) },
)

private fun AuthenticationPendingSwitch.toDomain() = PendingSwitchRecord(
	switchId = switchId,
	targetProfileUserId = targetProfileUserId,
	oldProfileUserId = oldProfileUserId,
	oldSessionEpoch = oldSessionEpoch,
	phase = PendingSwitchPhase.valueOf(phase.name),
	createdAtEpochMillis = createdAtEpochMillis,
	authority = when (authority) {
		AuthenticationPendingSwitchAuthority.ACTIVE_PROFILE -> SessionSwitchAuthority.ActiveProfile
		AuthenticationPendingSwitchAuthority.OWNER_RECOVERY -> SessionSwitchAuthority.OwnerRecovery
	},
)

private fun PendingSwitchRecord.toStored() = AuthenticationPendingSwitch(
	switchId = switchId,
	targetProfileUserId = targetProfileUserId,
	oldProfileUserId = oldProfileUserId,
	oldSessionEpoch = oldSessionEpoch,
	phase = AuthenticationPendingSwitchPhase.valueOf(phase.name),
	createdAtEpochMillis = createdAtEpochMillis,
	authority = when (authority) {
		SessionSwitchAuthority.ActiveProfile -> AuthenticationPendingSwitchAuthority.ACTIVE_PROFILE
		SessionSwitchAuthority.OwnerRecovery -> AuthenticationPendingSwitchAuthority.OWNER_RECOVERY
	},
)
