package org.jellyfin.androidtv.data.model

import org.jellyfin.sdk.model.api.CollectionType

private const val NAV_ORDER_UNKNOWN = 1000
private const val NAV_ORDER_MOVIES = 10
private const val NAV_ORDER_TV_SHOWS = 20
private const val NAV_ORDER_COURSES = 30
private const val NAV_ORDER_ADULT_VIDEOS = 40
private const val NAV_ORDER_MUSIC = 100
private const val NAV_ORDER_MUSIC_VIDEOS = 110
private const val NAV_ORDER_TRAILERS = 120
private const val NAV_ORDER_HOME_VIDEOS = 130
private const val NAV_ORDER_BOX_SETS = 140
private const val NAV_ORDER_BOOKS = 150
private const val NAV_ORDER_PHOTOS = 160
private const val NAV_ORDER_LIVE_TV = 170
private const val NAV_ORDER_PLAYLISTS = 180
private const val NAV_ORDER_FOLDERS = 190

/**
 * Local collection-type wrapper for Jellyflix-only library modes that are not part of the
 * Android TV SDK version pinned by this app.
 */
enum class JellyflixCollectionType(
	val rawValue: String,
	val normalizedCollectionType: CollectionType?,
	val primaryNavigationOrder: Int,
) {
	Unknown("unknown", CollectionType.UNKNOWN, NAV_ORDER_UNKNOWN),
	Movies("movies", CollectionType.MOVIES, NAV_ORDER_MOVIES),
	TvShows("tvshows", CollectionType.TVSHOWS, NAV_ORDER_TV_SHOWS),
	Music("music", CollectionType.MUSIC, NAV_ORDER_MUSIC),
	MusicVideos("musicvideos", CollectionType.MUSICVIDEOS, NAV_ORDER_MUSIC_VIDEOS),
	Trailers("trailers", CollectionType.TRAILERS, NAV_ORDER_TRAILERS),
	HomeVideos("homevideos", CollectionType.HOMEVIDEOS, NAV_ORDER_HOME_VIDEOS),
	BoxSets("boxsets", CollectionType.BOXSETS, NAV_ORDER_BOX_SETS),
	Books("books", CollectionType.BOOKS, NAV_ORDER_BOOKS),
	Photos("photos", CollectionType.PHOTOS, NAV_ORDER_PHOTOS),
	LiveTv("livetv", CollectionType.LIVETV, NAV_ORDER_LIVE_TV),
	Playlists("playlists", CollectionType.PLAYLISTS, NAV_ORDER_PLAYLISTS),
	Folders("folders", CollectionType.FOLDERS, NAV_ORDER_FOLDERS),
	Courses("courses", CollectionType.HOMEVIDEOS, NAV_ORDER_COURSES),
	AdultVideos("adultvideos", CollectionType.HOMEVIDEOS, NAV_ORDER_ADULT_VIDEOS);

	val isPrimaryNavigationLibrary: Boolean
		get() = this in primaryNavigationLibraries

	val isCustomVideoLibrary: Boolean
		get() = this == Courses || this == AdultVideos

	companion object {
		val primaryNavigationLibraries = setOf(Movies, TvShows, Courses, AdultVideos)

		fun fromRaw(rawValue: String?): JellyflixCollectionType {
			if (rawValue.isNullOrBlank()) return Unknown

			return entries.firstOrNull { type ->
				type.rawValue.equals(rawValue, ignoreCase = true)
			} ?: Unknown
		}

		fun fromSdk(collectionType: CollectionType?): JellyflixCollectionType = when (collectionType) {
			CollectionType.MOVIES -> Movies
			CollectionType.TVSHOWS -> TvShows
			CollectionType.MUSIC -> Music
			CollectionType.MUSICVIDEOS -> MusicVideos
			CollectionType.TRAILERS -> Trailers
			CollectionType.HOMEVIDEOS -> HomeVideos
			CollectionType.BOXSETS -> BoxSets
			CollectionType.BOOKS -> Books
			CollectionType.PHOTOS -> Photos
			CollectionType.LIVETV -> LiveTv
			CollectionType.PLAYLISTS -> Playlists
			CollectionType.FOLDERS -> Folders
			CollectionType.UNKNOWN,
			null -> Unknown
		}
	}
}
