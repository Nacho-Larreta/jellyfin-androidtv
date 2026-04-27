@file:UseSerializers(UUIDSerializer::class)

package org.jellyfin.androidtv.auth.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.util.UUID

data class ProfileSelector(
	val id: UUID,
	val ownerUserId: UUID,
	val ownerUserName: String?,
	val isCurrentUserOwner: Boolean,
	val canManageProfiles: Boolean,
	val autoSelectSingleProfile: Boolean,
	val currentDeviceProfileUserId: UUID?,
	val profiles: List<ProfileSelectorUser>,
)

data class ProfileSelectorUser(
	override val id: UUID,
	override val serverId: UUID,
	override val name: String,
	override val accessToken: String? = null,
	override val imageTag: String?,
	val ownerUserId: UUID,
	val profileSelectorId: UUID,
	val displayOrder: Int,
	val requiresPin: Boolean,
	val isActive: Boolean,
	val isOwner: Boolean,
	val isDisabled: Boolean,
	val hasParentalRestrictions: Boolean,
) : User() {
	override fun withToken(accessToken: String) = copy(accessToken = accessToken)
}

@Serializable
data class ProfileSelectorDto(
	@SerialName("ProfileSelectorId") val profileSelectorId: UUID,
	@SerialName("OwnerUserId") val ownerUserId: UUID,
	@SerialName("OwnerUserName") val ownerUserName: String? = null,
	@SerialName("IsCurrentUserOwner") val isCurrentUserOwner: Boolean = false,
	@SerialName("CanManageProfiles") val canManageProfiles: Boolean = false,
	@SerialName("AutoSelectSingleProfile") val autoSelectSingleProfile: Boolean = false,
	@SerialName("CurrentDeviceProfileUserId") val currentDeviceProfileUserId: UUID? = null,
	@SerialName("Profiles") val profiles: List<ProfileSelectorProfileDto> = emptyList(),
)

@Serializable
data class ProfileSelectorProfileDto(
	@SerialName("ProfileUserId") val profileUserId: UUID,
	@SerialName("Name") val name: String,
	@SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
	@SerialName("DisplayOrder") val displayOrder: Int = 0,
	@SerialName("IsVisible") val isVisible: Boolean = true,
	@SerialName("RequiresPin") val requiresPin: Boolean = false,
	@SerialName("IsDisabled") val isDisabled: Boolean = false,
	@SerialName("IsActive") val isActive: Boolean = false,
	@SerialName("IsOwner") val isOwner: Boolean = false,
	@SerialName("HasParentalRestrictions") val hasParentalRestrictions: Boolean = false,
)

@Serializable
data class ProfileActivationRequest(
	@SerialName("Pin") val pin: String? = null,
)

@Serializable
data class ProfileActivationResultDto(
	@SerialName("ProfileSelectorId") val profileSelectorId: UUID,
	@SerialName("OwnerUserId") val ownerUserId: UUID,
	@SerialName("ActiveProfileUserId") val activeProfileUserId: UUID,
	@SerialName("AuthenticationResult") val authenticationResult: ProfileAuthenticationResultDto,
)

@Serializable
data class ProfileAuthenticationResultDto(
	@SerialName("AccessToken") val accessToken: String? = null,
	@SerialName("User") val user: ProfileActivationUserDto? = null,
)

@Serializable
data class ProfileActivationUserDto(
	@SerialName("Id") val id: UUID,
	@SerialName("Name") val name: String? = null,
	@SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
)
