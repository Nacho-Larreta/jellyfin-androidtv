package org.jellyfin.androidtv.auth.store

import org.jellyfin.androidtv.auth.model.AuthenticationPendingSwitch
import org.jellyfin.androidtv.auth.model.AuthenticationPendingSwitchAuthority
import org.jellyfin.androidtv.auth.model.AuthenticationPendingSwitchPhase
import org.jellyfin.androidtv.auth.model.AuthenticationSessionEnvelope
import org.jellyfin.androidtv.auth.model.AuthenticationStoreServer
import org.jellyfin.androidtv.auth.model.AuthenticationStoreUser
import java.util.UUID

internal data class UserAuthorityBinding(
	val accessToken: String?,
	val profileSelectorId: UUID?,
	val profileSelectorOwnerUserId: UUID?,
)

internal fun AuthenticationStoreUser.authorityBinding() = UserAuthorityBinding(
	accessToken = accessToken,
	profileSelectorId = profileSelectorId,
	profileSelectorOwnerUserId = profileSelectorOwnerUserId,
)

internal fun AuthenticationStoreUser.withMetadataFrom(candidate: AuthenticationStoreUser) = copy(
	name = candidate.name,
	lastUsed = candidate.lastUsed,
	imageTag = candidate.imageTag,
)

internal fun AuthenticationSessionEnvelope?.invalidateFor(users: Set<UUID>): AuthenticationSessionEnvelope? {
	this ?: return null
	val active = activeProfile ?: return null
	val recovery = ownerRecovery
	val authorityAffected = active.profileUserId in users ||
		active.ownerUserId in users ||
		recovery?.ownerUserId in users
	return if (authorityAffected) null else this
}

internal fun AuthenticationStoreServer.hasValidAuthorityState(): Boolean {
	if (authorityGeneration < 0) return false
	if (users.values.any { it.accessToken != null && it.accessToken.isBlank() }) return false
	return sessionEnvelope?.hasValidAuthorityState() ?: true
}

private fun AuthenticationSessionEnvelope.hasValidAuthorityState(): Boolean {
	val active = activeProfile ?: return hasNoAuthority()
	if (active.accessToken.isBlank() || active.deviceId.isBlank() || sessionEpoch < 0) return false
	if ((active.profileSelectorId == null) != (active.ownerUserId == null)) return false
	return hasValidRecoveryBinding() && hasValidPendingSwitch()
}

private fun AuthenticationSessionEnvelope.hasNoAuthority() =
	ownerRecovery == null && pendingSwitch == null && cleanupMarker == null && sessionEpoch == 0L

private fun AuthenticationSessionEnvelope.hasValidRecoveryBinding(): Boolean {
	val recovery = ownerRecovery ?: return true
	val active = activeProfile ?: return false
	return recovery.accessToken.isNotBlank() &&
		recovery.deviceId.isNotBlank() &&
		recovery.deviceId == active.deviceId &&
		recovery.ownerUserId == active.ownerUserId &&
		recovery.profileSelectorId == active.profileSelectorId
}

private fun AuthenticationSessionEnvelope.hasValidPendingSwitch(): Boolean {
	val pending = pendingSwitch ?: return cleanupMarker == null
	val recoveryAuthorityIsBound = pending.authority != AuthenticationPendingSwitchAuthority.OWNER_RECOVERY ||
		ownerRecovery != null
	val installing = pending.phase == AuthenticationPendingSwitchPhase.INSTALLING
	val phaseMatchesCleanup = installing == (cleanupMarker != null)
	val cleanupMatchesSwitch = cleanupMarker?.switchId?.let { it == pending.switchId } ?: true
	return pending.oldSessionEpoch >= 0 &&
		pending.createdAtEpochMillis >= 0 &&
		recoveryAuthorityIsBound &&
		phaseMatchesCleanup &&
		cleanupMatchesSwitch &&
		matchesPendingPhase(pending, installing)
}

private fun AuthenticationSessionEnvelope.matchesPendingPhase(
	pending: AuthenticationPendingSwitch,
	installing: Boolean,
): Boolean {
	val active = activeProfile ?: return false
	return if (installing) {
		pending.oldSessionEpoch != Long.MAX_VALUE &&
			active.profileUserId == pending.targetProfileUserId &&
			sessionEpoch == pending.oldSessionEpoch + 1
	} else {
		active.profileUserId == pending.oldProfileUserId && sessionEpoch == pending.oldSessionEpoch
	}
}
