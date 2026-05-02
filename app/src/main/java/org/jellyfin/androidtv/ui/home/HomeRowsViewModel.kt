package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItemSelectAction
import org.jellyfin.androidtv.ui.shared.jellyflixCollectionType
import org.jellyfin.androidtv.ui.shared.jellyflixLibraryLabel
import org.jellyfin.androidtv.ui.shared.jellyflixLibraryStyle
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest

class HomeRowsViewModel(
	private val context: Context,
	private val api: ApiClient,
	private val userRepository: UserRepository,
	private val userViewsRepository: UserViewsRepository,
) : ViewModel() {
	private val mutableState = mutableStateOf<HomeRowsState>(HomeRowsState.Loading)
	val state: State<HomeRowsState> = mutableState

	init {
		refresh()
	}

	fun refresh() {
		viewModelScope.launch {
			mutableState.value = HomeRowsState.Loading

			mutableState.value = withContext(Dispatchers.IO) {
				runCatching {
					val userViews = userViewsRepository.getUserViews()
					val libraries = loadLibraries(userViews)
					val rows = buildList {
						loadResumeRow()?.let(::add)
						addAll(loadLatestRows(userViews))
					}

					if (libraries.isEmpty() && rows.isEmpty()) {
						HomeRowsState.Empty
					} else {
						HomeRowsState.Content(
							libraries = libraries,
							rows = rows,
						)
					}
				}.getOrElse(HomeRowsState::Error)
			}
		}
	}

	private fun loadLibraries(userViews: Collection<BaseItemDto>): List<HomeLibraryItemData> =
		userViews
			.filter { userViewsRepository.isSupported(it) }
			.sortedWith(
				compareBy<BaseItemDto> { it.jellyflixCollectionType(userViewsRepository).primaryNavigationOrder }
					.thenBy { it.name.orEmpty() }
			)
			.take(HOME_LIBRARY_PILL_LIMIT)
			.map { view ->
				val collectionType = view.jellyflixCollectionType(userViewsRepository)
				val style = collectionType.jellyflixLibraryStyle()

				HomeLibraryItemData(
					id = view.id.toString(),
					title = view.jellyflixLibraryLabel(userViewsRepository),
					count = view.childCount?.takeIf { it > 0 }?.toString(),
					iconRes = style.iconRes,
					accent = style.accent,
					source = view,
				)
			}

	private suspend fun loadResumeRow(): HomeMediaRowData? {
		val items = api.loadHomeResumeItems(RESUME_ITEM_LIMIT)

		if (items.isEmpty()) return null

		return HomeMediaRowData(
			id = "resume",
			title = context.getString(R.string.lbl_continue_watching),
			subtitle = "Reanuda donde lo dejaste",
			kind = HomeMediaRowKind.Resume,
			items = items.map { item ->
				BaseItemDtoBaseRowItem(
					item = item,
					preferParentThumb = true,
					staticHeight = true,
					selectAction = BaseRowItemSelectAction.Play,
				)
			},
		)
	}

	private suspend fun loadLatestRows(userViews: Collection<BaseItemDto>): List<HomeMediaRowData> {
		val latestExcludes = userRepository.currentUser.value
			?.configuration
			?.latestItemsExcludes
			.orEmpty()

		return userViews
			.filter { view ->
				userViewsRepository.isSupported(view) &&
					view.collectionType !in EXCLUDED_LATEST_COLLECTIONS &&
					view.id !in latestExcludes
			}
			.mapNotNull { view ->
				val latestItems = api.userLibraryApi.getLatestMedia(
					GetLatestMediaRequest(
						fields = ItemRepository.itemFields,
						imageTypeLimit = 1,
						parentId = view.id,
						groupItems = true,
						limit = LATEST_ITEM_LIMIT,
					)
				).content

				if (latestItems.isEmpty()) return@mapNotNull null

				HomeMediaRowData(
					id = "latest-${view.id}",
					title = context.getString(
						R.string.lbl_latest_in,
						view.jellyflixLibraryLabel(userViewsRepository)
					),
					kind = HomeMediaRowKind.Latest,
					items = latestItems.map { item ->
						BaseItemDtoBaseRowItem(
							item = item,
							preferParentThumb = true,
							staticHeight = true,
							selectAction = BaseRowItemSelectAction.Play,
						)
					},
				)
			}
	}

	private companion object {
		const val RESUME_ITEM_LIMIT = 30
		const val LATEST_ITEM_LIMIT = 30
		const val HOME_LIBRARY_PILL_LIMIT = 5

		val EXCLUDED_LATEST_COLLECTIONS = setOf(
			CollectionType.PLAYLISTS,
			CollectionType.LIVETV,
			CollectionType.BOXSETS,
			CollectionType.BOOKS,
		)
	}
}

sealed interface HomeRowsState {
	data object Loading : HomeRowsState
	data object Empty : HomeRowsState
	data class Content(
		val libraries: List<HomeLibraryItemData>,
		val rows: List<HomeMediaRowData>,
	) : HomeRowsState
	data class Error(val error: Throwable) : HomeRowsState
}

data class HomeLibraryItemData(
	val id: String,
	val title: String,
	val count: String?,
	val iconRes: Int,
	val accent: Color,
	val source: BaseItemDto,
)

data class HomeMediaRowData(
	val id: String,
	val title: String,
	val subtitle: String? = null,
	val kind: HomeMediaRowKind,
	val items: List<BaseRowItem>,
)

enum class HomeMediaRowKind {
	Resume,
	Latest,
}
