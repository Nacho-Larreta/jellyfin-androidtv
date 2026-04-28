package org.jellyfin.androidtv.ui.home

import org.jellyfin.androidtv.util.apiclient.JellyfinImage
import org.jellyfin.sdk.model.api.BaseItemDto

data class HomeHeroItemData(
	val baseItem: BaseItemDto,
	val title: String,
	val subtitle: String?,
	val overview: String?,
	val backdrop: JellyfinImage?,
	val poster: JellyfinImage?,
	val logo: JellyfinImage?,
	val progress: Float,
	val resumeLabel: String,
)

sealed interface HomeHeroState {
	data object Loading : HomeHeroState
	data object Empty : HomeHeroState
	data class Content(val items: List<HomeHeroItemData>) : HomeHeroState
	data class Error(val throwable: Throwable) : HomeHeroState
}
