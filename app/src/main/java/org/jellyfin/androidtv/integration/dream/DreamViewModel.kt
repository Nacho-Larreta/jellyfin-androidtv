package org.jellyfin.androidtv.integration.dream

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.integration.dream.model.DreamContent
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.screensaver.ScreensaverContentPolicy
import org.jellyfin.androidtv.screensaver.selectEligibleLibraryItems
import org.jellyfin.androidtv.screensaver.selectEligibleNowPlayingItem
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.playback.core.PlaybackManager
import org.jellyfin.playback.core.queue.queue
import org.jellyfin.playback.jellyfin.queue.baseItem
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.localizationApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@SuppressLint("StaticFieldLeak")
class DreamViewModel(
	private val api: ApiClient,
	private val imageLoader: ImageLoader,
	private val context: Context,
	playbackManager: PlaybackManager,
	private val userPreferences: UserPreferences,
) : ViewModel() {
	private val noItemsDelay = 2.minutes
	private val sharingStarted = SharingStarted.WhileSubscribed(replayExpirationMillis = 0)

	private val _contentPolicy = loadContentPolicy(noItemsDelay)
		.stateIn(viewModelScope, sharingStarted, null)

	private val _mediaContent = combine(playbackManager.queue.entry, _contentPolicy) { entry, policy ->
		val visibleEntry = entry?.takeIf { it.visibleInScreensaver }
		val baseItem = selectEligibleNowPlayingItem(visibleEntry?.baseItem, policy)
		if (visibleEntry == null || baseItem == null) null
		else DreamContent.NowPlaying(visibleEntry, baseItem)
	}
		.stateIn(viewModelScope, sharingStarted, null)

	@OptIn(ExperimentalCoroutinesApi::class)
	private val _libraryContent = flow {
		// Load first library item after 2 seconds
		// to force the logo at the start of the screensaver
		emit(null)
		delay(2.seconds)

		emitAll(
			_contentPolicy.flatMapLatest { policy ->
				if (policy == null) flowOf(null)
				else getRandomLibraryShowcaseItems(
					policy = policy,
					// A batch size of 60 should be equal to 30 minutes of items
					batchSize = 60,
					emitDelay = 30.seconds,
					noItemsDelay = noItemsDelay,
				)
			}
		)
	}
		.distinctUntilChanged()
		.stateIn(viewModelScope, sharingStarted, null)

	val content = combine(_mediaContent, _libraryContent) { mediaContent, libraryContent ->
		mediaContent ?: libraryContent ?: DreamContent.Logo
	}.stateIn(
		scope = viewModelScope,
		started = sharingStarted,
		initialValue = _mediaContent.value ?: _libraryContent.value ?: DreamContent.Logo,
	)

	private fun getRandomLibraryShowcaseItems(
		policy: ScreensaverContentPolicy,
		batchSize: Int,
		emitDelay: Duration,
		noItemsDelay: Duration,
	): Flow<DreamContent.LibraryShowcase?> = flow {
		while (true) {
			val query = policy.libraryQuery(batchSize)
			val items = callOrNull {
				withContext(Dispatchers.IO) {
					val response by api.itemsApi.getItems(
						includeItemTypes = query.includeItemTypes,
						recursive = query.recursive,
						sortBy = query.sortBy,
						limit = query.limit,
						imageTypes = query.imageTypes,
						fields = query.fields,
						maxOfficialRating = query.maxOfficialRating,
						hasParentalRating = query.hasParentalRating,
					)
					response.items
				}
			}

			val eligibleItems = selectEligibleLibraryItems(items, policy)
			if (eligibleItems.isEmpty()) {
				emit(null)
				delay(noItemsDelay)
			} else {
				var emittedContent = false
				for (item in eligibleItems) {
					val showcase = item.asLibraryShowcase() ?: continue
					emit(showcase)
					emittedContent = true
					delay(emitDelay)
				}

				if (!emittedContent) {
					emit(null)
					delay(noItemsDelay)
				}
			}
		}
	}.cancellable()

	private fun loadContentPolicy(retryDelay: Duration): Flow<ScreensaverContentPolicy?> = flow {
		while (true) {
			val policy = callOrNull {
				val parentalRatings by api.localizationApi.getParentalRatings()
				ScreensaverContentPolicy.fromCatalog(
					persistedAgeCeiling = userPreferences.readScreensaverAgeRatingMax(),
					parentalRatings = parentalRatings,
				)
			}

			emit(policy)
			if (policy != null) return@flow
			delay(retryDelay)
		}
	}

	private suspend fun BaseItemDto.asLibraryShowcase(): DreamContent.LibraryShowcase? = callOrNull {
		withContext(Dispatchers.IO) {
			val logoUrl = itemImages[ImageType.LOGO]?.getUrl(api)
			val backdropUrl = itemBackdropImages.randomOrNull()?.getUrl(api)

			// Require a backdrop
			if (backdropUrl == null) return@withContext null

			// Only attempt to load logo if there is one, wrap in async {} to load it parallel with the backdrop
			val logo = logoUrl?.let { url ->
				async {
					imageLoader.execute(
						request = ImageRequest.Builder(context).data(url).build()
					).image?.toBitmap()
				}
			}

			val backdrop = imageLoader.execute(
				request = ImageRequest.Builder(context).data(backdropUrl).build()
			).image?.toBitmap()

			if (backdrop == null) null
			else DreamContent.LibraryShowcase(this@asLibraryShowcase, backdrop, logo?.await())
		}
	}

	private suspend fun <T> callOrNull(block: suspend () -> T): T? = try {
		block()
	} catch (error: Exception) {
		currentCoroutineContext().ensureActive()
		Timber.e(error)
		null
	}
}
