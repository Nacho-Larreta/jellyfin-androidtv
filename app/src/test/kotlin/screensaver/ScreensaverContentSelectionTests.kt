package org.jellyfin.androidtv.screensaver

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ParentalRating
import org.jellyfin.sdk.model.api.ParentalRatingScore
import java.util.UUID

class ScreensaverContentSelectionTests : FunSpec({
	val policy = ScreensaverContentPolicy.fromCatalog(
		persistedAgeCeiling = 0,
		parentalRatings = listOf(
			ParentalRating("Approved", 0, ParentalRatingScore(0)),
			ParentalRating("PG-13", 13, ParentalRatingScore(13)),
		),
	)

	test("library selection rejects API failure, empty, image-less, and unsafe batches") {
		selectEligibleLibraryItems(items = null, policy).shouldBeEmpty()
		selectEligibleLibraryItems(items = emptyList(), policy).shouldBeEmpty()
		selectEligibleLibraryItems(items = listOf(item(rating = "Approved")), policy).shouldBeEmpty()
		selectEligibleLibraryItems(items = listOf(item(rating = "PG-13", backdrop = true)), policy).shouldBeEmpty()
	}

	test("library selection accepts only rated safe items with backdrop art") {
		val safeItem = item(rating = "Approved", backdrop = true)
		val result = selectEligibleLibraryItems(
			items = listOf(
				safeItem,
				item(rating = null, backdrop = true),
				item(rating = "unknown", backdrop = true),
				item(rating = "PG-13", backdrop = true),
			),
			policy = policy,
		)

		result shouldBe listOf(safeItem)
	}

	test("NowPlaying uses the same rating policy and requires ambient artwork") {
		selectEligibleNowPlayingItem(item(rating = null, primary = true), policy) shouldBe null
		selectEligibleNowPlayingItem(item(rating = "PG-13", primary = true), policy) shouldBe null
		selectEligibleNowPlayingItem(item(rating = "Approved"), policy) shouldBe null

		val safeNowPlaying = item(rating = "Approved", primary = true)
		selectEligibleNowPlayingItem(safeNowPlaying, policy) shouldBe safeNowPlaying
	}

	test("catalog unavailability keeps NowPlaying on the neutral fallback") {
		selectEligibleNowPlayingItem(item(rating = "Approved", primary = true), policy = null) shouldBe null
	}
})

private fun item(
	rating: String?,
	backdrop: Boolean = false,
	primary: Boolean = false,
) = mockk<BaseItemDto>(relaxed = true) {
	every { id } returns UUID.randomUUID()
	every { customRating } returns null
	every { officialRating } returns rating
	every { backdropImageTags } returns if (backdrop) listOf("backdrop") else null
	every { imageTags } returns if (primary) mapOf(ImageType.PRIMARY to "primary") else null
	every { imageBlurHashes } returns null
	every { primaryImageAspectRatio } returns null
	every { albumPrimaryImageTag } returns null
	every { albumId } returns null
	every { parentPrimaryImageItemId } returns null
	every { parentPrimaryImageTag } returns null
	every { parentLogoItemId } returns null
	every { parentLogoImageTag } returns null
	every { parentArtItemId } returns null
	every { parentArtImageTag } returns null
	every { parentThumbItemId } returns null
	every { parentThumbImageTag } returns null
}
