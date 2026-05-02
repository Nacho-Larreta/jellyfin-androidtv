package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.util.TimeUtils
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentBackdropImages
import org.jellyfin.androidtv.util.apiclient.parentImages
import org.jellyfin.androidtv.util.sdk.getFullName
import org.jellyfin.androidtv.util.sdk.getSubName
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest
import timber.log.Timber
import java.util.Locale

class HomeHeroViewModel(
	private val context: Context,
	private val api: ApiClient,
) : ViewModel() {
	private val _state = MutableStateFlow<HomeHeroState>(HomeHeroState.Loading)
	val state: StateFlow<HomeHeroState> = _state

	init {
		refresh()
	}

	fun refresh() {
		viewModelScope.launch {
			_state.value = HomeHeroState.Loading

			runCatching {
				withContext(Dispatchers.IO) {
					val resumeItems = api.loadHomeResumeItems(HERO_ITEM_LIMIT)
					if (resumeItems.isNotEmpty()) {
						resumeItems.map {
							it.toHomeHeroItemData(
								context = context,
								eyebrowLabel = context.getString(R.string.lbl_continue_where_left_off),
								showResumeProgress = true,
							)
						}
					} else {
						loadLatestHeroItems().map {
							it.toHomeHeroItemData(
								context = context,
								eyebrowLabel = context.getString(R.string.lbl_latest),
								showResumeProgress = false,
							)
						}
					}
				}
			}.fold(
				onSuccess = { items ->
					_state.value = if (items.isEmpty()) HomeHeroState.Empty else HomeHeroState.Content(items)
				},
				onFailure = { error ->
					Timber.w(error, "Failed to load home hero resume items")
					_state.value = HomeHeroState.Error(error)
				}
			)
		}
	}

	private suspend fun loadLatestHeroItems(): List<BaseItemDto> {
		val moviesAndEpisodes = api.userLibraryApi.getLatestMedia(
			createLatestRequest(listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE))
		).content

		return moviesAndEpisodes.ifEmpty {
			api.userLibraryApi.getLatestMedia(
				createLatestRequest(listOf(BaseItemKind.VIDEO))
			).content
		}
	}

	private fun createLatestRequest(includeItemTypes: List<BaseItemKind>) = GetLatestMediaRequest(
		limit = HERO_ITEM_LIMIT,
		fields = ItemRepository.itemFields,
		imageTypeLimit = 1,
		groupItems = true,
		includeItemTypes = includeItemTypes,
	)

	private fun BaseItemDto.toHomeHeroItemData(
		context: Context,
		eyebrowLabel: String,
		showResumeProgress: Boolean,
	): HomeHeroItemData {
		val playbackTicks = userData?.playbackPositionTicks ?: 0L
		val runtimeTicks = runTimeTicks ?: 0L
		val resumeMillis = playbackTicks / TICKS_PER_MILLISECOND
		val hasResumeProgress = showResumeProgress && playbackTicks > 0L && runtimeTicks > 0L
		val remainingMinutes = if (hasResumeProgress) {
			((runtimeTicks - playbackTicks) / TICKS_PER_MINUTE)
				.coerceAtLeast(0)
				.toInt()
		} else {
			0
		}
		val resumeLabel = if (resumeMillis > 0) {
			context.getString(R.string.lbl_resume_from, TimeUtils.formatMillis(resumeMillis))
		} else {
			context.getString(R.string.lbl_play)
		}

		return HomeHeroItemData(
			baseItem = this,
			eyebrowLabel = eyebrowLabel,
			title = getFullName(context).orEmpty(),
			subtitle = buildSubtitle(context, remainingMinutes),
			ratingLabel = communityRating?.let { rating -> String.format(Locale.US, "%.1f/10", rating) },
			metadataParts = buildMetadataParts(),
			remainingLabel = remainingMinutes
				.takeIf { hasResumeProgress && it > 0 }
				?.let { minutes ->
					context.getString(
						R.string.lbl_remaining_minutes,
						context.resources.getQuantityString(R.plurals.minutes, minutes, minutes)
					)
				},
			overview = overview,
			backdrop = itemBackdropImages.firstOrNull() ?: parentBackdropImages.firstOrNull(),
			poster = itemImages[ImageType.PRIMARY] ?: parentImages[ImageType.PRIMARY],
			logo = itemImages[ImageType.LOGO] ?: parentImages[ImageType.LOGO],
			progress = if (hasResumeProgress) (playbackTicks.toFloat() / runtimeTicks).coerceIn(0f, 1f) else 0f,
			resumeLabel = resumeLabel,
		)
	}

	private fun BaseItemDto.buildMetadataParts(): List<String> = listOfNotNull(
		productionYear?.toString(),
		officialRating,
		runTimeTicks?.let(::formatRuntime),
	).filter { it.isNotBlank() }

	private fun formatRuntime(runtimeTicks: Long): String {
		val totalMinutes = (runtimeTicks / TICKS_PER_MINUTE).coerceAtLeast(0)
		val hours = totalMinutes / 60
		val minutes = totalMinutes % 60

		return when {
			hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
			hours > 0 -> "${hours}h"
			else -> "${minutes}m"
		}
	}

	private fun BaseItemDto.buildSubtitle(context: Context, remainingMinutes: Int): String? {
		val subName = getSubName(context)
		val remaining = remainingMinutes
			.takeIf { it > 0 }
			?.let { minutes ->
				context.getString(
					R.string.lbl_remaining_minutes,
					context.resources.getQuantityString(R.plurals.minutes, minutes, minutes)
				)
			}

		return listOfNotNull(subName, remaining)
			.filter { it.isNotBlank() }
			.joinToString(" - ")
			.takeIf { it.isNotBlank() }
	}

	companion object {
		private const val HERO_ITEM_LIMIT = 8
		private const val TICKS_PER_MILLISECOND = 10_000L
		private const val TICKS_PER_MINUTE = 600_000_000L
	}
}
