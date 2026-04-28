package org.jellyfin.androidtv.data.repository

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jellyfin.androidtv.data.model.JellyflixCollectionType
import java.util.UUID

class UserViewsRepositoryTests : FunSpec({
	test("normalizes custom user view collection types for the pinned SDK") {
		val coursesId = UUID.randomUUID()
		val adultVideosId = UUID.randomUUID()
		val moviesId = UUID.randomUUID()
		val payload = """
			{
			  "Items": [
			    {
			      "Id": "$coursesId",
			      "Name": "Cursos",
			      "CollectionType": "courses"
			    },
			    {
			      "Id": "$adultVideosId",
			      "Name": "+18",
			      "CollectionType": "adultvideos"
			    },
			    {
			      "Id": "$moviesId",
			      "Name": "Películas",
			      "CollectionType": "movies"
			    }
			  ],
			  "TotalRecordCount": 3,
			  "StartIndex": 0
			}
		""".trimIndent()

		val normalized = normalizeUserViewsPayloadForSdk(payload)
		val normalizedCollectionTypes = Json.parseToJsonElement(normalized.payload)
			.jsonObject["Items"]!!
			.jsonArray
			.map { item -> item.jsonObject["CollectionType"]!!.jsonPrimitive.contentOrNull }

		normalized.collectionTypesByItemId[coursesId] shouldBe JellyflixCollectionType.Courses
		normalized.collectionTypesByItemId[adultVideosId] shouldBe JellyflixCollectionType.AdultVideos
		normalized.collectionTypesByItemId[moviesId] shouldBe JellyflixCollectionType.Movies
		normalizedCollectionTypes shouldBe listOf("homevideos", "homevideos", "movies")
	}
})
