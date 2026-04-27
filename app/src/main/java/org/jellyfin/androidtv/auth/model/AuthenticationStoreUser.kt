@file:UseSerializers(UUIDSerializer::class)

package org.jellyfin.androidtv.auth.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.time.Instant
import java.util.UUID

/**
 * Locally stored user information. New properties require default values or deserialization will fail.
 */
@Serializable
data class AuthenticationStoreUser(
	val name: String,
	@SerialName("last_used") val lastUsed: Long = Instant.now().toEpochMilli(),
	@SerialName("image_tag") val imageTag: String? = null,
	@SerialName("access_token") val accessToken: String? = null,
	@SerialName("profile_selector_id") val profileSelectorId: UUID? = null,
	@SerialName("profile_selector_last_profile_user_id") val profileSelectorLastProfileUserId: UUID? = null,
	@SerialName("profile_selector_owner_user_id") val profileSelectorOwnerUserId: UUID? = null,
)
