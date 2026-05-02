package org.jellyfin.androidtv.ui.shared

import androidx.compose.ui.graphics.Color
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.model.JellyflixCollectionType
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.sdk.model.api.BaseItemDto

internal data class JellyflixLibraryStyle(
	val iconRes: Int,
	val accent: Color,
)

internal fun Collection<BaseItemDto>.jellyflixPrimaryNavigationViews(
	userViewsRepository: UserViewsRepository,
) = filter { it.jellyflixCollectionType(userViewsRepository).isPrimaryNavigationLibrary }
	.sortedWith(
		compareBy<BaseItemDto> { it.jellyflixCollectionType(userViewsRepository).primaryNavigationOrder }
			.thenBy { it.name.orEmpty() }
	)

internal fun BaseItemDto.jellyflixCollectionType(
	userViewsRepository: UserViewsRepository,
): JellyflixCollectionType {
	val type = userViewsRepository.getCollectionType(this)
	if (type != JellyflixCollectionType.HomeVideos) return type

	return when (name.orEmpty().trim().lowercase()) {
		"curso", "cursos", "course", "courses" -> JellyflixCollectionType.Courses
		"+18", "18+", "adult", "adultos", "adult videos" -> JellyflixCollectionType.AdultVideos
		else -> type
	}
}

internal fun BaseItemDto.jellyflixNavigationLabel(
	userViewsRepository: UserViewsRepository,
): String = when (jellyflixCollectionType(userViewsRepository)) {
	JellyflixCollectionType.Movies -> "Películas"
	JellyflixCollectionType.TvShows -> "Series"
	JellyflixCollectionType.AdultVideos -> "+18"
	JellyflixCollectionType.Courses -> "Cursos"
	else -> name.orEmpty()
}

internal fun BaseItemDto.jellyflixLibraryLabel(
	userViewsRepository: UserViewsRepository,
): String = when (jellyflixCollectionType(userViewsRepository)) {
	JellyflixCollectionType.AdultVideos -> "+18"
	JellyflixCollectionType.Courses -> name.takeUnless { it.isNullOrBlank() } ?: "Cursos"
	else -> name.orEmpty()
}

internal fun JellyflixCollectionType.jellyflixLibraryStyle() = when (this) {
	JellyflixCollectionType.Movies -> JellyflixLibraryStyle(R.drawable.ic_movie, Color(0xFFE32735))
	JellyflixCollectionType.TvShows -> JellyflixLibraryStyle(R.drawable.ic_tv, Color(0xFF4BB3FD))
	JellyflixCollectionType.Courses -> JellyflixLibraryStyle(R.drawable.ic_grid, Color(0xFFA855F7))
	JellyflixCollectionType.AdultVideos -> JellyflixLibraryStyle(R.drawable.ic_jellyflix_lock, Color(0xFFF59E0B))
	JellyflixCollectionType.HomeVideos -> JellyflixLibraryStyle(R.drawable.ic_tv_play, Color(0xFF38BDF8))
	else -> JellyflixLibraryStyle(R.drawable.ic_folder, Color(0xFF8B949E))
}
