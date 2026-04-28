package org.jellyfin.androidtv.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.model.JellyflixCollectionType
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.CollectionType
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface UserViewsRepository {
	val views: Flow<Collection<BaseItemDto>>

	suspend fun getUserViews(includeHidden: Boolean? = null): Collection<BaseItemDto>
	fun getCollectionType(item: BaseItemDto?): JellyflixCollectionType
	fun isSupported(collectionType: CollectionType?): Boolean
	fun isSupported(item: BaseItemDto?): Boolean
	fun allowViewSelection(collectionType: CollectionType?): Boolean
	fun allowGridView(collectionType: CollectionType?): Boolean
}

class UserViewsRepositoryImpl(
	private val api: ApiClient,
	private val userRepository: UserRepository,
	private val okHttpFactory: OkHttpFactory,
	private val httpClientOptions: HttpClientOptions,
) : UserViewsRepository {
	private val json = Json { ignoreUnknownKeys = true }
	private val collectionTypesByItemId = ConcurrentHashMap<UUID, JellyflixCollectionType>()

	override val views = userRepository.currentUser
		.filterNotNull()
		.map { getUserViews() }
		.flowOn(Dispatchers.IO)

	override suspend fun getUserViews(includeHidden: Boolean?): Collection<BaseItemDto> = withContext(Dispatchers.IO) {
		val currentUser = userRepository.currentUser.value ?: return@withContext emptyList()
		val payload = requestUserViewsPayload(
			userId = currentUser.id,
			includeHidden = includeHidden,
		) ?: return@withContext emptyList()
		val normalizedPayload = normalizeUserViewsPayloadForSdk(payload)

		collectionTypesByItemId.putAll(normalizedPayload.collectionTypesByItemId)

		json.decodeFromString<BaseItemDtoQueryResult>(normalizedPayload.payload)
			.items
			.filter(::isSupported)
			.sortedWith(
				compareBy<BaseItemDto> { getCollectionType(it).primaryNavigationOrder }
					.thenBy { it.name.orEmpty() }
			)
	}

	override fun getCollectionType(item: BaseItemDto?): JellyflixCollectionType {
		val itemId = item?.id
		if (itemId != null) {
			collectionTypesByItemId[itemId]?.let { return it }
		}

		return JellyflixCollectionType.fromSdk(item?.collectionType)
	}

	override fun isSupported(collectionType: CollectionType?) = JellyflixCollectionType.fromSdk(collectionType) !in unsupportedCollectionTypes
	override fun isSupported(item: BaseItemDto?) = getCollectionType(item) !in unsupportedCollectionTypes
	override fun allowViewSelection(collectionType: CollectionType?) =
		JellyflixCollectionType.fromSdk(collectionType) !in disallowViewSelectionCollectionTypes

	override fun allowGridView(collectionType: CollectionType?) =
		JellyflixCollectionType.fromSdk(collectionType) !in disallowGridViewCollectionTypes

	private fun requestUserViewsPayload(
		userId: UUID,
		includeHidden: Boolean?,
	): String? {
		val baseUrl = api.baseUrl ?: return null
		val accessToken = api.accessToken ?: return null
		val url = baseUrl
			.trimEnd('/')
			.plus("/UserViews")
			.toHttpUrl()
			.newBuilder()
			.addQueryParameter("userId", userId.toString())
			.apply {
				if (includeHidden != null) {
					addQueryParameter("includeHidden", includeHidden.toString())
				}
			}
			.build()

		val request = Request.Builder()
			.url(url)
			.header("Accept", JSON_MEDIA_TYPE)
			.header("Authorization", buildAuthorizationHeader(accessToken))
			.get()
			.build()

		okHttpFactory.createClient(httpClientOptions).newCall(request).execute().use { response ->
			if (!response.isSuccessful) {
				Timber.w("User views request failed with status %s.", response.code)
				return null
			}

			return response.body?.string().orEmpty()
		}
	}

	private fun buildAuthorizationHeader(accessToken: String): String = AuthorizationHeaderBuilder.buildHeader(
		api.clientInfo.name,
		api.clientInfo.version,
		api.deviceInfo.id,
		api.deviceInfo.name,
		accessToken,
	)

	private companion object {
		private const val JSON_MEDIA_TYPE = "application/json"

		private val unsupportedCollectionTypes = setOf(
			JellyflixCollectionType.Books,
			JellyflixCollectionType.Folders,
		)

		private val disallowViewSelectionCollectionTypes = setOf(
			JellyflixCollectionType.LiveTv,
			JellyflixCollectionType.Music,
			JellyflixCollectionType.Photos,
		)

		private val disallowGridViewCollectionTypes = setOf(
			JellyflixCollectionType.LiveTv,
			JellyflixCollectionType.Music,
		)
	}
}

internal data class NormalizedUserViewsPayload(
	val payload: String,
	val collectionTypesByItemId: Map<UUID, JellyflixCollectionType>,
)

internal fun normalizeUserViewsPayloadForSdk(payload: String): NormalizedUserViewsPayload {
	val json = Json { ignoreUnknownKeys = true }
	val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull()
		?: return NormalizedUserViewsPayload(payload = payload, collectionTypesByItemId = emptyMap())
	val items = root["Items"]?.jsonArray ?: return NormalizedUserViewsPayload(payload = payload, collectionTypesByItemId = emptyMap())
	val collectionTypesByItemId = mutableMapOf<UUID, JellyflixCollectionType>()
	val normalizedItems = items.map(::normalizeUserViewItem)
		.map { (item, itemId, collectionType) ->
			if (itemId != null) {
				collectionTypesByItemId[itemId] = collectionType
			}

			item
		}
	val normalizedRoot = JsonObject(root + ("Items" to JsonArray(normalizedItems)))

	return NormalizedUserViewsPayload(
		payload = json.encodeToString(JsonObject.serializer(), normalizedRoot),
		collectionTypesByItemId = collectionTypesByItemId,
	)
}

private data class NormalizedUserViewItem(
	val item: JsonElement,
	val itemId: UUID?,
	val collectionType: JellyflixCollectionType,
)

private fun normalizeUserViewItem(item: JsonElement): NormalizedUserViewItem {
	val itemObject = runCatching { item.jsonObject }.getOrNull()
		?: return NormalizedUserViewItem(item, null, JellyflixCollectionType.Unknown)
	val rawCollectionType = itemObject["CollectionType"]?.jsonPrimitive?.contentOrNull
	val collectionType = JellyflixCollectionType.fromRaw(rawCollectionType)
	val itemId = itemObject["Id"]?.jsonPrimitive?.contentOrNull?.toUuidOrNull()
	val normalizedCollectionType = collectionType.normalizedCollectionType
		?.name
		?.lowercase()

	if (normalizedCollectionType == null || normalizedCollectionType == rawCollectionType) {
		return NormalizedUserViewItem(item, itemId, collectionType)
	}

	return NormalizedUserViewItem(
		item = JsonObject(itemObject + ("CollectionType" to JsonPrimitive(normalizedCollectionType))),
		itemId = itemId,
		collectionType = collectionType,
	)
}

private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
