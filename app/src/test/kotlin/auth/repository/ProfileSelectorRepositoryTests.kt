package org.jellyfin.androidtv.auth.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.auth.model.isValidProfilePin
import org.jellyfin.androidtv.auth.model.ProfileSelector
import org.jellyfin.androidtv.auth.model.ProfileSelectorUser
import org.jellyfin.androidtv.auth.store.AuthenticationStore
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import java.util.UUID

class ProfileSelectorRepositoryTests : FunSpec({
	test("accepts only 4 to 8 ASCII digits as a profile PIN") {
		listOf("0001", "1234", "12345678").forEach { pin ->
			isValidProfilePin(pin) shouldBe true
		}

		listOf<String?>(null, "", "123", "123456789", "12a4", "12 4", "١٢٣٤", "12١4").forEach { pin ->
			isValidProfilePin(pin) shouldBe false
		}
	}

	test("serializes a valid PIN without dropping leading zeroes") {
		encodeProfileActivationRequest("0001", Json { encodeDefaults = true }) shouldBe "{\"Pin\":\"0001\"}"
	}

	test("serializes an omitted PIN for profiles that do not require one") {
		encodeProfileActivationRequest(null, Json { encodeDefaults = true }) shouldBe "{\"Pin\":null}"
	}

	test("rejects invalid PIN before a request body can be created") {
		val exception = shouldThrow<ProfileSelectorApiException> {
			encodeProfileActivationRequest("12 4")
		}

		exception.code shouldBe "PROFILE_PIN_INVALID_FORMAT"
	}

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

	test("sign out exposes a failed durable invalidation") {
		val serverId = UUID.randomUUID()
		val ownerUserId = UUID.randomUUID()
		val store = mockk<AuthenticationStore> {
			every { invalidateProfileSelector(serverId, ownerUserId) } returns false
		}
		val repository = ProfileSelectorRepositoryImpl(
			apiClient = mockk<ApiClient>(),
			authenticationStore = store,
			okHttpFactory = mockk<OkHttpFactory>(),
			httpClientOptions = mockk<HttpClientOptions>(),
		)

		repository.signOut(
			Session(
				userId = ownerUserId,
				serverId = serverId,
				accessToken = "owner-token",
			)
		) shouldBe false
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
