package org.jellyfin.androidtv.auth.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.auth.model.ProfileSelector
import org.jellyfin.androidtv.auth.model.ProfileSelectorUser
import java.util.UUID

class ProfileSelectorRepositoryTests : FunSpec({
	test("returns remembered profile when it is eligible") {
		val ownerId = UUID.randomUUID()
		val rememberedProfile = profile(
			id = UUID.randomUUID(),
			ownerUserId = ownerId,
		)
		val selector = selector(
			ownerUserId = ownerId,
			profiles = listOf(rememberedProfile, profile(UUID.randomUUID(), ownerId)),
		)

		resolveAutoProfileCandidate(selector, rememberedProfile.id) shouldBe rememberedProfile
	}

	test("ignores remembered profile when it requires pin") {
		val ownerId = UUID.randomUUID()
		val protectedProfile = profile(
			id = UUID.randomUUID(),
			ownerUserId = ownerId,
			requiresPin = true,
		)
		val selector = selector(
			ownerUserId = ownerId,
			autoSelectSingleProfile = false,
			profiles = listOf(protectedProfile, profile(UUID.randomUUID(), ownerId)),
		)

		resolveAutoProfileCandidate(selector, protectedProfile.id) shouldBe null
	}

	test("auto-selects the only eligible profile when the selector allows it") {
		val ownerId = UUID.randomUUID()
		val onlyEligibleProfile = profile(
			id = UUID.randomUUID(),
			ownerUserId = ownerId,
		)
		val selector = selector(
			ownerUserId = ownerId,
			autoSelectSingleProfile = true,
			profiles = listOf(
				onlyEligibleProfile,
				profile(UUID.randomUUID(), ownerId, requiresPin = true),
				profile(UUID.randomUUID(), ownerId, isDisabled = true),
			),
		)

		resolveAutoProfileCandidate(selector, rememberedProfileUserId = null) shouldBe onlyEligibleProfile
	}

	test("does not auto-select when multiple eligible profiles exist") {
		val ownerId = UUID.randomUUID()
		val selector = selector(
			ownerUserId = ownerId,
			autoSelectSingleProfile = true,
			profiles = listOf(
				profile(UUID.randomUUID(), ownerUserId = ownerId),
				profile(UUID.randomUUID(), ownerUserId = ownerId),
			),
		)

		resolveAutoProfileCandidate(selector, rememberedProfileUserId = null) shouldBe null
	}
})

private fun selector(
	ownerUserId: UUID,
	autoSelectSingleProfile: Boolean = true,
	profiles: List<ProfileSelectorUser>,
): ProfileSelector = ProfileSelector(
	id = UUID.randomUUID(),
	ownerUserId = ownerUserId,
	ownerUserName = "Owner",
	isCurrentUserOwner = true,
	canManageProfiles = true,
	autoSelectSingleProfile = autoSelectSingleProfile,
	currentDeviceProfileUserId = null,
	profiles = profiles,
)

private fun profile(
	id: UUID,
	ownerUserId: UUID,
	requiresPin: Boolean = false,
	isDisabled: Boolean = false,
): ProfileSelectorUser = ProfileSelectorUser(
	id = id,
	serverId = UUID.randomUUID(),
	name = "Profile-$id",
	imageTag = null,
	ownerUserId = ownerUserId,
	profileSelectorId = UUID.randomUUID(),
	displayOrder = 0,
	requiresPin = requiresPin,
	isActive = false,
	isOwner = false,
	isDisabled = isDisabled,
	hasParentalRestrictions = false,
)
