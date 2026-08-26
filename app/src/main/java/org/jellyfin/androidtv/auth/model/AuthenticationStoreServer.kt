@file:UseSerializers(UUIDSerializer::class)

package org.jellyfin.androidtv.auth.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.time.Instant
import java.util.UUID

/**
 * Locally stored server information. New properties require default values or deserialization will fail.
 */
@Serializable
data class AuthenticationStoreServer(
	val name: String,
	val address: String,
	val version: String? = null,
	@SerialName("login_disclaimer")  val loginDisclaimer: String? = null,
	@SerialName("splashscreen_enabled")  val splashscreenEnabled: Boolean = false,
	@SerialName("setup_completed")  val setupCompleted: Boolean = true,
	@SerialName("last_used") val lastUsed: Long = Instant.now().toEpochMilli(),
	@SerialName("last_refreshed") val lastRefreshed: Long = Instant.now().toEpochMilli(),
	val users: Map<UUID, AuthenticationStoreUser> = emptyMap(),
	@SerialName("session_envelope") val sessionEnvelope: AuthenticationSessionEnvelope? = null,
	@SerialName("authority_generation") val authorityGeneration: Long = 0,
)

@Serializable
data class AuthenticationSessionEnvelope(
	@SerialName("active_profile") val activeProfile: AuthenticationActiveProfileSession? = null,
	@SerialName("owner_recovery") val ownerRecovery: AuthenticationOwnerRecoverySession? = null,
	@SerialName("session_epoch") val sessionEpoch: Long = 0,
	@SerialName("pending_switch") val pendingSwitch: AuthenticationPendingSwitch? = null,
	@SerialName("cleanup_marker") val cleanupMarker: AuthenticationCommittedPendingCleanup? = null,
)

@Serializable
data class AuthenticationActiveProfileSession(
	@SerialName("profile_user_id") val profileUserId: UUID,
	@SerialName("access_token") val accessToken: String,
	@SerialName("device_id") val deviceId: String,
	@SerialName("profile_selector_id") val profileSelectorId: UUID? = null,
	@SerialName("owner_user_id") val ownerUserId: UUID? = null,
) {
	override fun toString(): String =
		"AuthenticationActiveProfileSession(profileUserId=$profileUserId, deviceId=$deviceId, accessToken=REDACTED)"
}

@Serializable
data class AuthenticationOwnerRecoverySession(
	@SerialName("owner_user_id") val ownerUserId: UUID,
	@SerialName("access_token") val accessToken: String,
	@SerialName("profile_selector_id") val profileSelectorId: UUID,
	@SerialName("device_id") val deviceId: String,
) {
	override fun toString(): String =
		"AuthenticationOwnerRecoverySession(ownerUserId=$ownerUserId, profileSelectorId=$profileSelectorId, " +
			"deviceId=$deviceId, accessToken=REDACTED)"
}

@Serializable
data class AuthenticationPendingSwitch(
	@SerialName("switch_id") val switchId: UUID,
	@SerialName("target_profile_user_id") val targetProfileUserId: UUID,
	@SerialName("old_profile_user_id") val oldProfileUserId: UUID,
	@SerialName("old_session_epoch") val oldSessionEpoch: Long,
	@SerialName("phase") val phase: AuthenticationPendingSwitchPhase,
	@SerialName("authority") val authority: AuthenticationPendingSwitchAuthority =
		AuthenticationPendingSwitchAuthority.ACTIVE_PROFILE,
	@SerialName("created_at_epoch_millis") val createdAtEpochMillis: Long,
)

@Serializable
enum class AuthenticationPendingSwitchPhase {
	PREPARING,
	QUIESCING,
	COMMITTING,
	COMMIT_UNKNOWN,
	INSTALLING,
}

@Serializable
enum class AuthenticationPendingSwitchAuthority {
	ACTIVE_PROFILE,
	OWNER_RECOVERY,
}

@Serializable
data class AuthenticationCommittedPendingCleanup(
	@SerialName("switch_id") val switchId: UUID,
)
