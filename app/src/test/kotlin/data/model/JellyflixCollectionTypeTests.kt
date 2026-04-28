package org.jellyfin.androidtv.data.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.CollectionType

class JellyflixCollectionTypeTests : FunSpec({
	test("maps custom raw library types to SDK-compatible video libraries") {
		JellyflixCollectionType.fromRaw("courses") shouldBe JellyflixCollectionType.Courses
		JellyflixCollectionType.fromRaw("adultvideos") shouldBe JellyflixCollectionType.AdultVideos
		JellyflixCollectionType.Courses.normalizedCollectionType shouldBe CollectionType.HOMEVIDEOS
		JellyflixCollectionType.AdultVideos.normalizedCollectionType shouldBe CollectionType.HOMEVIDEOS
	}

	test("keeps primary navigation order aligned with the Jellyflix header") {
		JellyflixCollectionType.primaryNavigationLibraries
			.sortedBy { it.primaryNavigationOrder }
			.map { it.rawValue } shouldBe listOf(
			"movies",
			"tvshows",
			"courses",
			"adultvideos",
		)
	}
})
