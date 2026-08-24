package org.jellyfin.androidtv.ui.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import timber.log.Timber
import java.io.IOException
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

data class SearchHistoryEntry(
	val label: String,
	val query: String,
	val hitCount: Int,
)

enum class SearchExploreKind {
	Genre,
	Collection,
}

data class SearchExploreItem(
	val id: String?,
	val title: String,
	val subtitle: String,
	val query: String,
	val count: Int,
	val item: BaseItemDto?,
	val representativeItem: BaseItemDto?,
	val kind: SearchExploreKind,
)

interface SearchRepository {
	suspend fun search(
		searchTerm: String,
		itemTypes: Collection<BaseItemKind>,
	): Result<List<BaseItemDto>>

	suspend fun searchByGenre(
		genre: String,
		itemTypes: Collection<BaseItemKind>,
	): Result<List<BaseItemDto>>

	suspend fun getSearchHistory(limit: Int = DEFAULT_HISTORY_LIMIT): Result<List<SearchHistoryEntry>>
	suspend fun recordSearchHistory(searchTerm: String): Result<Unit>
	suspend fun clearSearchHistory(): Result<Unit>
	suspend fun getGenres(limit: Int = DEFAULT_EXPLORE_LIMIT): Result<List<SearchExploreItem>>
	suspend fun getCollections(limit: Int = DEFAULT_EXPLORE_LIMIT): Result<List<SearchExploreItem>>

	companion object {
		const val DEFAULT_HISTORY_LIMIT = 8
		const val DEFAULT_EXPLORE_LIMIT = 12
	}
}

class SearchRepositoryImpl(
	private val apiClient: ApiClient,
	private val sessionRepository: SessionRepository,
	private val okHttpFactory: OkHttpFactory,
	private val httpClientOptions: HttpClientOptions,
) : SearchRepository {
	companion object {
		private const val QUERY_LIMIT = 25
		private const val JSON_MEDIA_TYPE = "application/json"
		private const val EXPLORE_ITEM_TYPES = "Movie,Series,Episode,Video"
	}

	private val json = Json {
		ignoreUnknownKeys = true
		serializersModule = SerializersModule {
			contextual(UUIDSerializer())
		}
	}

	override suspend fun search(
		searchTerm: String,
		itemTypes: Collection<BaseItemKind>,
	): Result<List<BaseItemDto>> {
		val trimmed = searchTerm.trim()
		val primaryResult = searchItems(
			searchTerm = trimmed.takeIf { it.isNotBlank() },
			genre = null,
			itemTypes = itemTypes,
		)

		val primaryItems = primaryResult.getOrNull()
		if (primaryResult.isFailure || trimmed.isBlank()) return primaryResult

		val normalizedSearchTerm = trimmed.normalizedSearchKey()
		val fallbackItems = if (primaryItems.isNullOrEmpty() && normalizedSearchTerm != trimmed) {
			searchItems(
				searchTerm = normalizedSearchTerm.takeIf { it.isNotBlank() },
				genre = null,
				itemTypes = itemTypes,
			).getOrNull().orEmpty()
		} else {
			emptyList()
		}

		return Result.success((primaryItems.orEmpty() + fallbackItems).rankByBestSearchMatch(trimmed))
	}

	override suspend fun searchByGenre(
		genre: String,
		itemTypes: Collection<BaseItemKind>,
	): Result<List<BaseItemDto>> = searchItems(
		searchTerm = null,
		genre = genre.trim().takeIf { it.isNotBlank() },
		itemTypes = itemTypes,
	)

	private suspend fun searchItems(
		searchTerm: String?,
		genre: String?,
		itemTypes: Collection<BaseItemKind>,
	): Result<List<BaseItemDto>> = try {
		var request = GetItemsRequest(
			searchTerm = searchTerm,
			genres = genre?.let { setOf(it) },
			limit = QUERY_LIMIT,
			imageTypeLimit = 1,
			includeItemTypes = itemTypes,
			fields = ItemRepository.itemFields,
			recursive = true,
			enableTotalRecordCount = false,
		)

		// Special case for video row
		if (itemTypes.size == 1 && itemTypes.first() == BaseItemKind.VIDEO) {
			request = request.copy(
				mediaTypes = setOf(MediaType.VIDEO),
				includeItemTypes = null,
				excludeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE, BaseItemKind.TV_CHANNEL)
			)
		}

		val result = withContext(Dispatchers.IO) {
			apiClient.itemsApi.getItems(request).content
		}

		Result.success(result.items)
	} catch (e: ApiClientException) {
		Timber.e(e, "Failed to search for items")
		Result.failure(e)
	}

	override suspend fun getSearchHistory(limit: Int): Result<List<SearchHistoryEntry>> = withApiResult {
		val session = requireActiveSession()
		val ownerUserId = session.ownerUserId ?: session.userId
		val payload = executeRequest(
			path = "/Users/$ownerUserId/Profiles/${session.userId}/Search/History",
			queryParameters = mapOf("limit" to limit.toString()),
		)

		json.decodeFromString<List<SearchHistoryEntryDto>>(payload)
			.mapNotNull { entry ->
				val searchTerm = entry.searchTerm.trim()
				if (searchTerm.isBlank()) return@mapNotNull null

				SearchHistoryEntry(
					label = searchTerm,
					query = searchTerm,
					hitCount = entry.hitCount,
				)
			}
	}

	override suspend fun recordSearchHistory(searchTerm: String): Result<Unit> = withApiResult {
		val trimmed = searchTerm.trim()
		if (trimmed.isBlank()) return@withApiResult

		val session = requireActiveSession()
		val ownerUserId = session.ownerUserId ?: session.userId
		executeRequest(
			method = "POST",
			path = "/Users/$ownerUserId/Profiles/${session.userId}/Search/History",
			body = json.encodeToString(SearchHistoryUpdateRequestDto(trimmed)),
		)
	}

	override suspend fun clearSearchHistory(): Result<Unit> = withApiResult {
		val session = requireActiveSession()
		val ownerUserId = session.ownerUserId ?: session.userId
		executeRequest(
			method = "DELETE",
			path = "/Users/$ownerUserId/Profiles/${session.userId}/Search/History",
		)
	}

	override suspend fun getGenres(limit: Int): Result<List<SearchExploreItem>> = withApiResult {
		val session = requireActiveSession()
		val payload = executeRequest(
			path = "/Users/${session.userId}/Explore/Genres",
			queryParameters = mapOf(
				"limit" to limit.toString(),
				"includeItemTypes" to EXPLORE_ITEM_TYPES,
			),
		)

		json.decodeFromString<ExploreSectionDto>(payload).items.mapNotNull { item ->
			item.toExploreItem(SearchExploreKind.Genre)
		}
	}

	override suspend fun getCollections(limit: Int): Result<List<SearchExploreItem>> = withApiResult {
		val session = requireActiveSession()
		val payload = executeRequest(
			path = "/Users/${session.userId}/Explore/Collections",
			queryParameters = mapOf(
				"limit" to limit.toString(),
				"depth" to "2",
			),
		)

		json.decodeFromString<ExploreSectionDto>(payload).items.mapNotNull { item ->
			item.toExploreItem(SearchExploreKind.Collection)
		}
	}

	private suspend fun <T> withApiResult(block: suspend () -> T): Result<T> = try {
		Result.success(withContext(Dispatchers.IO) { block() })
	} catch (e: IOException) {
		Timber.e(e, "Search discovery request failed")
		Result.failure(e)
	} catch (e: IllegalStateException) {
		Timber.w(e, "Search discovery request skipped")
		Result.failure(e)
	} catch (e: Exception) {
		Timber.e(e, "Search discovery request failed")
		Result.failure(e)
	}

	private fun requireActiveSession() = sessionRepository.currentSession.value
		?: error("No active session")

	private fun executeRequest(
		method: String = "GET",
		path: String,
		queryParameters: Map<String, String> = emptyMap(),
		body: String? = null,
	): String {
		val baseUrl = apiClient.baseUrl ?: error("No server base URL")
		val accessToken = sessionRepository.currentSession.value?.accessToken
			?: apiClient.accessToken
			?: error("No access token")
		val url = baseUrl
			.trimEnd('/')
			.plus(path)
			.toHttpUrl()
			.newBuilder()
			.apply {
				queryParameters.forEach { (name, value) -> addQueryParameter(name, value) }
			}
			.build()
		val requestBody = body?.toRequestBody(JSON_MEDIA_TYPE.toMediaType())
		val request = Request.Builder()
			.url(url)
			.header("Accept", JSON_MEDIA_TYPE)
			.header("Authorization", buildAuthorizationHeader(accessToken))
			.method(method, requestBody)
			.build()

		okHttpFactory.createClient(httpClientOptions).newCall(request).execute().use { response ->
			if (!response.isSuccessful) {
				error("Request $path failed with HTTP ${response.code}")
			}

			return response.body?.string().orEmpty()
		}
	}

	private fun buildAuthorizationHeader(accessToken: String): String = AuthorizationHeaderBuilder.buildHeader(
		apiClient.clientInfo.name,
		apiClient.clientInfo.version,
		apiClient.deviceInfo.id,
		apiClient.deviceInfo.name,
		accessToken,
	)

	private fun ExploreItemDto.toExploreItem(kind: SearchExploreKind): SearchExploreItem? {
		val title = name.trim()
		if (title.isBlank()) return null

		val item = item.decodeBaseItem()
		val representativeItem = representativeItem.decodeBaseItem()
		val subtitle = when {
			itemCount > 0 -> "$itemCount títulos"
			overview?.isNotBlank() == true -> overview
			kind == SearchExploreKind.Collection -> "Colección"
			else -> "Género"
		}

		return SearchExploreItem(
			id = id,
			title = title,
			subtitle = subtitle,
			query = title,
			count = itemCount,
			item = item,
			representativeItem = representativeItem ?: item,
			kind = kind,
		)
	}

	private fun JsonElement?.decodeBaseItem(): BaseItemDto? =
		this?.let { element -> runCatching { json.decodeFromJsonElement<BaseItemDto>(element) }.getOrNull() }
}

@Serializable
private data class SearchHistoryEntryDto(
	@SerialName("SearchTerm") val searchTerm: String = "",
	@SerialName("HitCount") val hitCount: Int = 0,
)

@Serializable
private data class SearchHistoryUpdateRequestDto(
	@SerialName("SearchTerm") val searchTerm: String,
)

@Serializable
private data class ExploreSectionDto(
	@SerialName("Items") val items: List<ExploreItemDto> = emptyList(),
)

@Serializable
private data class ExploreItemDto(
	@SerialName("Id") val id: String? = null,
	@SerialName("Name") val name: String = "",
	@SerialName("Overview") val overview: String? = null,
	@SerialName("ItemCount") val itemCount: Int = 0,
	@SerialName("Item") val item: JsonElement? = null,
	@SerialName("RepresentativeItem") val representativeItem: JsonElement? = null,
)

private fun List<BaseItemDto>.rankByBestSearchMatch(searchTerm: String): List<BaseItemDto> {
	val normalizedQuery = searchTerm.normalizedSearchKey()
	if (normalizedQuery.isBlank()) return this

	return withIndex()
		.distinctBy { (_, item) -> item.id }
		.sortedWith(
			compareBy<IndexedValue<BaseItemDto>>(
				{ (_, item) -> item.bestMatchScore(normalizedQuery) },
				{ it.index },
			)
		)
		.map { it.value }
}

private fun BaseItemDto.bestMatchScore(normalizedQuery: String): Int {
	val searchableNames = listOfNotNull(name, originalTitle, seriesName)
		.map { it.normalizedSearchKey() }
		.filter { it.isNotBlank() }

	return searchableNames.minOfOrNull { candidate ->
		when {
			candidate == normalizedQuery -> 0
			candidate.startsWith(normalizedQuery) -> 10 + candidate.length - normalizedQuery.length
			candidate.split(' ').any { word -> word.startsWith(normalizedQuery) } -> 30
			candidate.contains(normalizedQuery) -> 50 + candidate.indexOf(normalizedQuery)
			candidate.isOrderedSubsequenceOf(normalizedQuery) -> 80 + candidate.length
			else -> 1_000
		}
	} ?: 1_000
}

private fun String.normalizedSearchKey(): String = Normalizer
	.normalize(this, Normalizer.Form.NFD)
	.replace("\\p{Mn}+".toRegex(), "")
	.lowercase(Locale.ROOT)
	.replace("[^\\p{Alnum}]+".toRegex(), " ")
	.trim()
	.replace("\\s+".toRegex(), " ")

private fun String.isOrderedSubsequenceOf(needle: String): Boolean {
	if (needle.isBlank()) return true

	var needleIndex = 0
	for (character in this) {
		if (character == needle[needleIndex]) needleIndex++
		if (needleIndex == needle.length) return true
	}

	return false
}
