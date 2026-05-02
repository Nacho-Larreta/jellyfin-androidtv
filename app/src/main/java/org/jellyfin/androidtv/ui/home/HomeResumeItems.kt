package org.jellyfin.androidtv.ui.home

import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaType

internal suspend fun ApiClient.loadHomeResumeItems(limit: Int): List<BaseItemDto> =
	itemsApi.getResumeItems(
		fields = ItemRepository.itemFields,
		imageTypeLimit = 1,
		limit = limit,
		mediaTypes = listOf(MediaType.VIDEO),
		includeItemTypes = listOf(BaseItemKind.EPISODE, BaseItemKind.MOVIE, BaseItemKind.VIDEO),
		excludeActiveSessions = true,
	).content.items
		.orEmpty()
		.filter(BaseItemDto::hasHomeResumeProgress)

private val BaseItemDto.hasHomeResumeProgress: Boolean
	get() {
		val playbackTicks = userData?.playbackPositionTicks ?: 0L
		val runtimeTicks = runTimeTicks ?: 0L

		return playbackTicks > 0L && runtimeTicks > playbackTicks
	}
