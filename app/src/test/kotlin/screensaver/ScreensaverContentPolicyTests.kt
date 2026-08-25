package org.jellyfin.androidtv.screensaver

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.ParentalRating
import org.jellyfin.sdk.model.api.ParentalRatingScore

class ScreensaverContentPolicyTests : FunSpec({
	context("persisted age ceiling") {
		test("fresh installs default to general audiences") {
			UserPreferences.screensaverAgeRatingMax.defaultValue shouldBe 0
		}

		withData(
			nameFn = { "keeps supported ceiling $it" },
			ts = ScreensaverContentPolicy.supportedAgeCeilings,
		) { ceiling ->
			ScreensaverContentPolicy.resolveAgeCeiling(ceiling) shouldBe ceiling
		}

		withData(
			nameFn = { "fails closed for ${it?.let { value -> value::class.simpleName + " " + value } ?: "missing"}" },
			ts = listOf<Any?>(null, 3, 22, -2, 5.0, 5.5, "5", "not-a-rating"),
		) { persistedValue ->
			ScreensaverContentPolicy.resolveAgeCeiling(persistedValue) shouldBe 0
		}

	}

	context("rating eligibility") {
		ScreensaverContentPolicy.supportedAgeCeilings
			.filterNot { it == ScreensaverContentPolicy.UNLIMITED }
			.forEach { ceiling ->
				test("ceiling $ceiling includes its boundary and rejects one above") {
					val policy = policy(ceiling)

					policy.isEligible(customRating = null, officialRating = ceiling.toString()) shouldBe true
					if (ceiling < 21) {
						policy.isEligible(customRating = null, officialRating = (ceiling + 1).toString()) shouldBe false
					}
				}
			}

		test("general audiences rejects every configured higher ceiling") {
			val policy = policy(0)

			listOf(5, 10, 13, 14, 16, 18, 21).forEach { rating ->
				policy.isEligible(customRating = null, officialRating = rating.toString()) shouldBe false
			}
		}

		test("custom rating takes precedence over official rating") {
			val policy = policy(10)

			policy.isEligible(customRating = "PG-13", officialRating = "0") shouldBe false
			policy.isEligible(customRating = "Approved", officialRating = "21") shouldBe true
		}

		test("blank custom rating falls back to official rating") {
			policy(0).isEligible(customRating = "  ", officialRating = "Approved") shouldBe true
		}

		withData(
			nameFn = { "rejects unresolved rating ${it ?: "missing"}" },
			ts = listOf(null, "", "unknown", "Unrated", "-1", "1.5", "22", "05", " 5 "),
		) { rating ->
			policy(21).isEligible(customRating = null, officialRating = rating) shouldBe false
		}

		test("unlimited still requires a resolvable rating and allows catalog-owned special scores") {
			val policy = policy(ScreensaverContentPolicy.UNLIMITED)

			policy.isEligible(customRating = null, officialRating = null) shouldBe false
			policy.isEligible(customRating = null, officialRating = "unknown") shouldBe false
			policy.isEligible(customRating = null, officialRating = "XXX") shouldBe true
			policy.isEligible(customRating = null, officialRating = "Banned") shouldBe true
		}

		test("empty or unusable catalogs reject canonical numeric ratings") {
			val emptyCatalogPolicy = ScreensaverContentPolicy.fromCatalog(
				persistedAgeCeiling = 0,
				parentalRatings = emptyList(),
			)
			val unusableCatalogPolicy = ScreensaverContentPolicy.fromCatalog(
				persistedAgeCeiling = 0,
				parentalRatings = listOf(
					ParentalRating(name = "Unrated", value = null, ratingScore = null),
				),
			)

			emptyCatalogPolicy.isEligible(customRating = null, officialRating = "0") shouldBe false
			unusableCatalogPolicy.isEligible(customRating = null, officialRating = "0") shouldBe false
		}
	}

	context("library query") {
		test("bounded policy sends the exact fail-closed query") {
			val query = policy(13).libraryQuery(batchSize = 60)

			query.includeItemTypes shouldContainExactly listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
			query.recursive shouldBe true
			query.sortBy shouldContainExactly listOf(ItemSortBy.RANDOM)
			query.limit shouldBe 60
			query.imageTypes shouldContainExactly listOf(ImageType.BACKDROP)
			query.fields shouldContainExactly listOf(ItemFields.CUSTOM_RATING)
			query.hasParentalRating shouldBe true
			query.maxOfficialRating shouldBe "13"
		}

		test("unlimited omits only the maximum rating") {
			val bounded = policy(13).libraryQuery(batchSize = 60)
			val unlimited = policy(ScreensaverContentPolicy.UNLIMITED).libraryQuery(batchSize = 60)

			unlimited shouldBe bounded.copy(maxOfficialRating = null)
		}
	}
})

private fun policy(ageCeiling: Int) = ScreensaverContentPolicy.fromCatalog(
	persistedAgeCeiling = ageCeiling,
	parentalRatings = listOf(
		ParentalRating(name = "Approved", value = 0, ratingScore = ParentalRatingScore(score = 0)),
		ParentalRating(name = "PG-13", value = 13, ratingScore = ParentalRatingScore(score = 13)),
		ParentalRating(name = "XXX", value = 1000, ratingScore = ParentalRatingScore(score = 1000)),
		ParentalRating(name = "Banned", value = 1001, ratingScore = ParentalRatingScore(score = 1001)),
		ParentalRating(name = "Unrated", value = null, ratingScore = null),
	),
)
