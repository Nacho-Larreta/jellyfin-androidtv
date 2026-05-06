package org.jellyfin.androidtv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.sdk.model.api.BaseItemKind
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class SearchDiscoveryState(
	val history: List<SearchHistoryEntry> = emptyList(),
	val genres: List<SearchExploreItem> = emptyList(),
	val collections: List<SearchExploreItem> = emptyList(),
	val loading: Boolean = false,
)

class SearchViewModel(
	private val searchRepository: SearchRepository
) : ViewModel() {
	companion object {
		private val debounceDuration = 600.milliseconds

		private val groups = mapOf(
			R.string.lbl_movies to setOf(BaseItemKind.MOVIE),
			R.string.lbl_series to setOf(BaseItemKind.SERIES),
			R.string.lbl_episodes to setOf(BaseItemKind.EPISODE),
			R.string.lbl_videos to setOf(BaseItemKind.VIDEO),
			R.string.lbl_programs to setOf(BaseItemKind.LIVE_TV_PROGRAM),
			R.string.channels to setOf(BaseItemKind.LIVE_TV_CHANNEL),
			R.string.lbl_playlists to setOf(BaseItemKind.PLAYLIST),
			R.string.lbl_artists to setOf(BaseItemKind.MUSIC_ARTIST),
			R.string.lbl_albums to setOf(BaseItemKind.MUSIC_ALBUM),
			R.string.lbl_songs to setOf(BaseItemKind.AUDIO),
			R.string.photo_albums to setOf(BaseItemKind.PHOTO_ALBUM),
			R.string.photos to setOf(BaseItemKind.PHOTO),
			R.string.lbl_collections to setOf(BaseItemKind.BOX_SET),
			R.string.lbl_people to setOf(BaseItemKind.PERSON),
		)
	}

	private var searchJob: Job? = null

	private var previousQuery: String? = null

	private var previousRecordedQuery: String? = null

	private val _searchResultsFlow = MutableStateFlow<Collection<SearchResultGroup>>(emptyList())
	val searchResultsFlow = _searchResultsFlow.asStateFlow()

	private val _searchDiscoveryFlow = MutableStateFlow(SearchDiscoveryState(loading = true))
	val searchDiscoveryFlow = _searchDiscoveryFlow.asStateFlow()

	init {
		refreshDiscovery()
	}

	fun refreshDiscovery() {
		viewModelScope.launch {
			val currentState = _searchDiscoveryFlow.value
			_searchDiscoveryFlow.value = currentState.copy(loading = true)

			val history = async { searchRepository.getSearchHistory().getOrNull() }
			val genres = async { searchRepository.getGenres().getOrNull() }
			val collections = async { searchRepository.getCollections().getOrNull() }

			_searchDiscoveryFlow.value = SearchDiscoveryState(
				history = history.await() ?: currentState.history,
				genres = genres.await() ?: currentState.genres,
				collections = collections.await() ?: currentState.collections,
				loading = false,
			)
		}
	}

	fun clearSearchHistory() {
		viewModelScope.launch {
			searchRepository.clearSearchHistory()
			_searchDiscoveryFlow.value = _searchDiscoveryFlow.value.copy(history = emptyList())
		}
	}

	fun searchImmediately(query: String) = searchDebounced(query, 0.milliseconds)

	fun browseGenre(genre: String): Boolean {
		val trimmed = genre.trim()
		val stateKey = "genre:$trimmed"
		if (stateKey == previousQuery) return false
		previousQuery = stateKey

		searchJob?.cancel()

		if (trimmed.isBlank()) {
			_searchResultsFlow.value = emptyList()
			return true
		}

		searchJob = viewModelScope.launch {
			_searchResultsFlow.value = groups.map { (stringRes, itemKinds) ->
				async {
					val result = searchRepository.searchByGenre(trimmed, itemKinds)
					val items = result.getOrNull().orEmpty()

					SearchResultGroup(stringRes, items)
				}
			}.awaitAll()
		}

		return true
	}

	fun searchDebounced(query: String, debounce: Duration = debounceDuration): Boolean {
		val trimmed = query.trim()
		if (trimmed == previousQuery) return false
		previousQuery = trimmed

		searchJob?.cancel()

		if (trimmed.isBlank()) {
			_searchResultsFlow.value = emptyList()
			return true
		}

		searchJob = viewModelScope.launch {
			delay(debounce)
			recordSearch(trimmed)

			_searchResultsFlow.value = groups.map { (stringRes, itemKinds) ->
				async {
					val result = searchRepository.search(trimmed, itemKinds)
					val items = result.getOrNull().orEmpty()

					SearchResultGroup(stringRes, items)
				}
			}.awaitAll()
		}

		return true
	}

	private fun recordSearch(searchTerm: String) {
		if (searchTerm == previousRecordedQuery || searchTerm.isBlank()) return

		previousRecordedQuery = searchTerm
		viewModelScope.launch {
			searchRepository.recordSearchHistory(searchTerm)
			refreshDiscovery()
		}
	}
}
