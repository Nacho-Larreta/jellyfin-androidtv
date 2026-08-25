package org.jellyfin.androidtv.screensaver

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.ParentalRating
import java.util.Locale

data class ScreensaverLibraryQuery(
	val includeItemTypes: List<BaseItemKind>,
	val recursive: Boolean,
	val sortBy: List<ItemSortBy>,
	val limit: Int,
	val imageTypes: List<ImageType>,
	val fields: List<ItemFields>,
	val hasParentalRating: Boolean,
	val maxOfficialRating: String?,
)

class ScreensaverContentPolicy private constructor(
	val ageCeiling: Int,
	private val catalogScores: Map<String, Int>,
) {
	val isUnlimited: Boolean = ageCeiling == UNLIMITED

	fun libraryQuery(batchSize: Int) = ScreensaverLibraryQuery(
		includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
		recursive = true,
		sortBy = listOf(ItemSortBy.RANDOM),
		limit = batchSize,
		imageTypes = listOf(ImageType.BACKDROP),
		fields = listOf(ItemFields.CUSTOM_RATING),
		hasParentalRating = true,
		maxOfficialRating = ageCeiling.takeUnless { isUnlimited }?.toString(),
	)

	fun isEligible(item: BaseItemDto): Boolean = isEligible(
		customRating = item.customRating,
		officialRating = item.officialRating,
	)

	fun isEligible(customRating: String?, officialRating: String?): Boolean {
		if (catalogScores.isEmpty()) return false

		val effectiveRating = customRating?.takeIf(String::isNotBlank) ?: officialRating
		val score = resolveRating(effectiveRating) ?: return false
		return isUnlimited || score <= ageCeiling
	}

	private fun resolveRating(rating: String?): Int? {
		if (rating.isNullOrEmpty()) return null

		catalogScores[rating.lowercase(Locale.ROOT)]?.let { return it }
		if (!CANONICAL_AGE.matches(rating)) return null

		return rating.toInt()
	}

	companion object {
		const val UNLIMITED = -1
		const val DEFAULT_AGE_CEILING = 0

		val supportedAgeCeilings = listOf(0, 5, 10, 13, 14, 16, 18, 21, UNLIMITED)

		private val CANONICAL_AGE = Regex("(?:0|[1-9]|1[0-9]|2[01])")

		fun resolveAgeCeiling(persistedValue: Any?): Int =
			(persistedValue as? Int)
				?.takeIf(supportedAgeCeilings::contains)
				?: DEFAULT_AGE_CEILING

		fun fromCatalog(
			persistedAgeCeiling: Any?,
			parentalRatings: List<ParentalRating>,
		): ScreensaverContentPolicy {
			val scores = parentalRatings.mapNotNull { rating ->
				val score = rating.ratingScore?.score ?: return@mapNotNull null
				rating.name.lowercase(Locale.ROOT) to score
			}.toMap()

			return ScreensaverContentPolicy(
				ageCeiling = resolveAgeCeiling(persistedAgeCeiling),
				catalogScores = scores,
			)
		}
	}
}
