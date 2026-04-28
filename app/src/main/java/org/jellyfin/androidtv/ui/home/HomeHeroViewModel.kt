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
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import timber.log.Timber

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
					val response = api.itemsApi.getResumeItems(createResumeRequest()).content
					response.items.map { it.toHomeHeroItemData(context) }
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

	private fun createResumeRequest() = GetResumeItemsRequest(
		limit = HERO_ITEM_LIMIT,
		fields = ItemRepository.itemFields,
		imageTypeLimit = 1,
		enableTotalRecordCount = false,
		mediaTypes = listOf(MediaType.VIDEO),
		excludeItemTypes = setOf(BaseItemKind.AUDIO_BOOK),
	)

	private fun BaseItemDto.toHomeHeroItemData(context: Context): HomeHeroItemData {
		val playbackTicks = userData?.playbackPositionTicks ?: 0L
		val runtimeTicks = runTimeTicks ?: 0L
		val resumeMillis = playbackTicks / TICKS_PER_MILLISECOND
		val remainingMinutes = ((runtimeTicks - playbackTicks) / TICKS_PER_MINUTE)
			.coerceAtLeast(0)
			.toInt()
		val resumeLabel = if (resumeMillis > 0) {
			context.getString(R.string.lbl_resume_from, TimeUtils.formatMillis(resumeMillis))
		} else {
			context.getString(R.string.lbl_play)
		}

		return HomeHeroItemData(
			baseItem = this,
			title = getFullName(context).orEmpty(),
			subtitle = buildSubtitle(context, remainingMinutes),
			overview = overview,
			backdrop = itemBackdropImages.firstOrNull() ?: parentBackdropImages.firstOrNull(),
			poster = itemImages[ImageType.PRIMARY] ?: parentImages[ImageType.PRIMARY],
			logo = itemImages[ImageType.LOGO] ?: parentImages[ImageType.LOGO],
			progress = if (runtimeTicks > 0L) (playbackTicks.toFloat() / runtimeTicks).coerceIn(0f, 1f) else 0f,
			resumeLabel = resumeLabel,
		)
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
