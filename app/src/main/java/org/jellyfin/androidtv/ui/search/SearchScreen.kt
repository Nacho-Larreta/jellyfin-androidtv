package org.jellyfin.androidtv.ui.search

import android.view.KeyEvent as AndroidKeyEvent
import android.widget.ImageView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItemSelectAction
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.ui.search.composable.SearchTextInput
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private sealed interface SearchSelection {
	data object Toolbar : SearchSelection
	data object Input : SearchSelection
	data class RecentSearch(val index: Int) : SearchSelection
	data object ClearHistory : SearchSelection
	data class Genre(val index: Int) : SearchSelection
	data class Trending(val index: Int) : SearchSelection
	data class Filter(val index: Int) : SearchSelection
	data object TopResult : SearchSelection
	data class RowItem(val rowIndex: Int, val itemIndex: Int) : SearchSelection
}

private data class SearchShortcut(
	val label: String,
	val query: String,
)

private data class SearchGenre(
	val label: String,
	val count: Int,
	val query: String,
	val color: Color,
	val iconRes: Int,
	val exploreItem: SearchExploreItem,
)

private data class TrendingShortcut(
	val title: String,
	val subtitle: String,
	val query: String,
	val exploreItem: SearchExploreItem,
)

private data class SearchFilter(
	val label: String,
	val groupIndex: Int?,
	val count: Int,
)

private data class SearchDisplayGroup(
	val sourceIndex: Int,
	val labelRes: Int,
	val items: List<BaseItemDto>,
)

private enum class SearchBlankRowKind {
	RECENT,
	GENRE,
	TRENDING,
}

private data class SearchBlankRow(
	val kind: SearchBlankRowKind,
	val startIndex: Int,
	val endIndex: Int,
)

private val GENRE_COLORS = listOf(
	Color(0xFF6B1D24),
	Color(0xFF1D5D7D),
	Color(0xFF6E2BA4),
	Color(0xFF237045),
	Color(0xFF8A2323),
	Color(0xFF8A531B),
	Color(0xFF34397F),
	Color(0xFF2F2F2F),
	Color(0xFF7B2E55),
	Color(0xFF454545),
	Color(0xFF286341),
	Color(0xFF4A4A4A),
)

private const val GENRE_COLUMN_COUNT = 4
private const val TRENDING_COLUMN_COUNT = 6

private fun rowCount(itemCount: Int, columns: Int) =
	if (itemCount == 0) 0 else (itemCount + columns - 1) / columns

private fun SearchExploreItem.launchableItem(): BaseItemDto? = item ?: representativeItem

private fun genreIconFor(label: String): Int = when (label.lowercase()) {
	"drama" -> R.drawable.ic_clapperboard
	"animación", "animation" -> R.drawable.ic_tv
	"acción", "action" -> R.drawable.ic_movie
	"ciencia ficción", "science fiction", "sci-fi" -> R.drawable.ic_flask
	"thriller" -> R.drawable.ic_masks
	"comedia", "comedy" -> R.drawable.ic_heart
	"documentales", "documentary", "documental" -> R.drawable.ic_photo
	"terror", "horror" -> R.drawable.ic_zzz
	"romance" -> R.drawable.ic_heart
	"fantasía", "fantasy" -> R.drawable.ic_lightbulb
	"crimen", "crime" -> R.drawable.ic_abc
	"familiar", "family" -> R.drawable.ic_users
	else -> R.drawable.ic_grid
}

private fun rowBoundedIndex(
	index: Int,
	delta: Int,
	columns: Int,
	lastIndex: Int,
): Int {
	val rowStart = (index / columns) * columns
	val rowEnd = minOf(rowStart + columns - 1, lastIndex)

	return (index + delta).coerceIn(rowStart, rowEnd)
}

private fun isSearchNavigationKeyCode(keyCode: Int): Boolean =
	when (keyCode) {
		AndroidKeyEvent.KEYCODE_BACK,
		AndroidKeyEvent.KEYCODE_DPAD_LEFT,
		AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
		AndroidKeyEvent.KEYCODE_DPAD_UP,
		AndroidKeyEvent.KEYCODE_DPAD_DOWN,
		AndroidKeyEvent.KEYCODE_DPAD_CENTER,
		AndroidKeyEvent.KEYCODE_ENTER,
		AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> true
		else -> false
	}

@Composable
internal fun SearchScreen(
	initialQuery: String,
	onBackPressedHandlerChange: (() -> Boolean) -> Unit = {},
	onKeyPressedHandlerChange: ((Int) -> Boolean) -> Unit = {},
) {
	val context = LocalContext.current
	val viewModel = koinViewModel<SearchViewModel>()
	val itemLauncher = koinInject<ItemLauncher>()
	val backgroundService = koinInject<BackgroundService>()
	val searchResults by viewModel.searchResultsFlow.collectAsState()
	val discovery by viewModel.searchDiscoveryFlow.collectAsState()
	val toolbarFocusRequester = remember { FocusRequester() }
	val contentFocusRequester = remember { FocusRequester() }
	val listState = rememberLazyListState()
	val density = LocalDensity.current
	var query by rememberSaveable { mutableStateOf(initialQuery) }
	var inputEditing by rememberSaveable { mutableStateOf(false) }
	var selection by remember {
		mutableStateOf<SearchSelection>(
			if (initialQuery.isBlank()) SearchSelection.Input else SearchSelection.TopResult
		)
	}
	var selectedFilterIndex by rememberSaveable { mutableStateOf(0) }
	var pendingShortcutQuery by rememberSaveable { mutableStateOf<String?>(null) }

	val recentSearches = discovery.history.map { entry -> SearchShortcut(entry.label, entry.query) }
	val genreShortcuts = discovery.genres.mapIndexed { index, item ->
		SearchGenre(
			label = item.title,
			count = item.count,
			query = item.query,
			color = GENRE_COLORS[index % GENRE_COLORS.size],
			iconRes = genreIconFor(item.title),
			exploreItem = item,
		)
	}
	val trendingSearches = discovery.collections.map { item ->
		TrendingShortcut(
			title = item.title,
			subtitle = item.subtitle,
			query = item.query,
			exploreItem = item,
		)
	}
	val genreRowCount = rowCount(genreShortcuts.size, GENRE_COLUMN_COUNT)
	val blankRows = buildList {
		if (recentSearches.isNotEmpty()) {
			add(SearchBlankRow(SearchBlankRowKind.RECENT, startIndex = 0, endIndex = recentSearches.lastIndex))
		}
		repeat(genreRowCount) { rowIndex ->
			val startIndex = rowIndex * GENRE_COLUMN_COUNT
			add(
				SearchBlankRow(
					kind = SearchBlankRowKind.GENRE,
					startIndex = startIndex,
					endIndex = minOf(startIndex + GENRE_COLUMN_COUNT - 1, genreShortcuts.lastIndex),
				)
			)
		}
		if (trendingSearches.isNotEmpty()) {
			add(SearchBlankRow(SearchBlankRowKind.TRENDING, startIndex = 0, endIndex = trendingSearches.lastIndex))
		}
	}
	val groups = searchResults
		.mapIndexed { index, group ->
			SearchDisplayGroup(
				sourceIndex = index,
				labelRes = group.labelRes,
				items = group.items.toList(),
			)
		}
		.filter { group -> group.items.isNotEmpty() }
	val totalResults = groups.sumOf { group -> group.items.size }
	val filters = buildList {
		add(SearchFilter("Todo", null, totalResults))
		groups.forEach { group ->
			add(SearchFilter(context.getString(group.labelRes), group.sourceIndex, group.items.size))
		}
	}
	val visibleGroups = filters
		.getOrNull(selectedFilterIndex)
		?.groupIndex
		?.let { selectedGroupIndex -> groups.filter { group -> group.sourceIndex == selectedGroupIndex } }
		?: groups
	val topResult = groups.firstOrNull()?.items?.firstOrNull()
	val contentSignature = buildString {
		append(query)
		append('|').append(selectedFilterIndex)
		append("|h:").append(recentSearches.joinToString(",") { it.query })
		append("|genres:").append(genreShortcuts.joinToString(",") { it.exploreItem.id ?: it.label })
		append("|collections:").append(trendingSearches.joinToString(",") { it.exploreItem.id ?: it.title })
		groups.forEach { group ->
			append("|g:").append(group.sourceIndex).append(':').append(group.items.joinToString(",") { it.id.toString() })
		}
	}

	fun blankSelectionForRow(row: SearchBlankRow, preferredColumn: Int): SearchSelection =
		when (row.kind) {
			SearchBlankRowKind.RECENT -> {
				val column = preferredColumn.coerceIn(0, recentSearches.size)
				if (column == recentSearches.size) SearchSelection.ClearHistory
				else SearchSelection.RecentSearch(column)
			}
			SearchBlankRowKind.GENRE -> {
				val index = (row.startIndex + preferredColumn).coerceIn(row.startIndex, row.endIndex)
				SearchSelection.Genre(index)
			}
			SearchBlankRowKind.TRENDING -> {
				val index = (row.startIndex + preferredColumn).coerceIn(row.startIndex, row.endIndex)
				SearchSelection.Trending(index)
			}
		}

	fun blankRowPosition(target: SearchSelection): Pair<Int, Int>? =
		when (target) {
			is SearchSelection.RecentSearch -> blankRows
				.indexOfFirst { row -> row.kind == SearchBlankRowKind.RECENT }
				.takeIf { rowIndex -> rowIndex >= 0 }
				?.let { rowIndex -> rowIndex to target.index.coerceIn(0, recentSearches.lastIndex) }
			SearchSelection.ClearHistory -> blankRows
				.indexOfFirst { row -> row.kind == SearchBlankRowKind.RECENT }
				.takeIf { rowIndex -> rowIndex >= 0 }
				?.let { rowIndex -> rowIndex to recentSearches.size }
			is SearchSelection.Genre -> blankRows
				.indexOfFirst { row ->
					row.kind == SearchBlankRowKind.GENRE && target.index in row.startIndex..row.endIndex
				}
				.takeIf { rowIndex -> rowIndex >= 0 }
				?.let { rowIndex ->
					val row = blankRows[rowIndex]
					rowIndex to (target.index - row.startIndex)
				}
			is SearchSelection.Trending -> blankRows
				.indexOfFirst { row -> row.kind == SearchBlankRowKind.TRENDING }
				.takeIf { rowIndex -> rowIndex >= 0 }
				?.let { rowIndex -> rowIndex to target.index.coerceIn(0, trendingSearches.lastIndex) }
			else -> null
		}

	fun firstBlankSelection(): SearchSelection =
		blankRows.firstOrNull()?.let { row -> blankSelectionForRow(row, preferredColumn = 0) }
			?: SearchSelection.Input

	fun firstResultSelection(filterIndex: Int = selectedFilterIndex): SearchSelection =
		when {
			query.isBlank() -> firstBlankSelection()
			totalResults == 0 -> SearchSelection.Input
			filterIndex == 0 && topResult != null -> SearchSelection.TopResult
			groups.isNotEmpty() -> SearchSelection.RowItem(0, 0)
			else -> SearchSelection.Input
		}

	fun firstBrowseSelection(): SearchSelection =
		if (query.isBlank()) {
			firstBlankSelection()
		} else {
			when {
				filters.size > 1 -> SearchSelection.Filter(selectedFilterIndex)
				else -> firstResultSelection()
			}
		}

	fun normalizeSelection(target: SearchSelection): SearchSelection =
		when (target) {
			SearchSelection.Toolbar -> SearchSelection.Toolbar
			SearchSelection.Input -> SearchSelection.Input
			is SearchSelection.RecentSearch -> if (recentSearches.isEmpty()) {
				firstBlankSelection()
			} else {
				SearchSelection.RecentSearch(target.index.coerceIn(0, recentSearches.lastIndex))
			}
			SearchSelection.ClearHistory -> if (recentSearches.isEmpty()) firstBlankSelection() else SearchSelection.ClearHistory
			is SearchSelection.Genre -> if (genreShortcuts.isEmpty()) {
				firstBlankSelection()
			} else {
				SearchSelection.Genre(target.index.coerceIn(0, genreShortcuts.lastIndex))
			}
			is SearchSelection.Trending -> if (trendingSearches.isEmpty()) {
				firstBlankSelection()
			} else {
				SearchSelection.Trending(target.index.coerceIn(0, trendingSearches.lastIndex))
			}
			is SearchSelection.Filter -> {
				if (filters.size <= 1) SearchSelection.Input
				else SearchSelection.Filter(target.index.coerceIn(0, filters.lastIndex))
			}
			SearchSelection.TopResult -> {
				if (selectedFilterIndex == 0 && topResult != null) SearchSelection.TopResult
				else firstResultSelection()
			}
			is SearchSelection.RowItem -> {
				val row = visibleGroups.getOrNull(target.rowIndex)
				if (row == null || row.items.isEmpty()) {
					firstResultSelection()
				} else {
					SearchSelection.RowItem(
						rowIndex = target.rowIndex.coerceIn(0, visibleGroups.lastIndex),
						itemIndex = target.itemIndex.coerceIn(0, row.items.lastIndex),
					)
				}
			}
		}

	fun select(target: SearchSelection): Boolean {
		val normalized = normalizeSelection(target)
		if (normalized != SearchSelection.Input) inputEditing = false
		selection = normalized
		when (normalized) {
			SearchSelection.Toolbar -> runCatching { toolbarFocusRequester.requestFocus() }
			SearchSelection.Input -> runCatching { contentFocusRequester.requestFocus() }
			else -> runCatching { contentFocusRequester.requestFocus() }
		}
		return true
	}

	fun requestFocusForSelection(target: SearchSelection) {
		when (normalizeSelection(target)) {
			SearchSelection.Toolbar -> runCatching { toolbarFocusRequester.requestFocus() }
			SearchSelection.Input -> runCatching { contentFocusRequester.requestFocus() }
			else -> runCatching { contentFocusRequester.requestFocus() }
		}
	}

	fun handleBack(): Boolean =
		if (selection == SearchSelection.Input && inputEditing) {
			inputEditing = false
			true
		} else if (selection != SearchSelection.Toolbar) {
			select(SearchSelection.Toolbar)
		} else {
			false
		}

	fun submitShortcutSearch(shortcutQuery: String): Boolean {
		query = shortcutQuery
		selectedFilterIndex = 0
		pendingShortcutQuery = shortcutQuery
		viewModel.searchImmediately(shortcutQuery)
		// Keep the keyboard closed while results load; content focus is restored once results arrive.
		select(SearchSelection.Toolbar)
		return true
	}

	fun submitGenreBrowse(genre: SearchGenre): Boolean {
		query = genre.label
		selectedFilterIndex = 0
		pendingShortcutQuery = genre.query
		viewModel.browseGenre(genre.query)
		// Keep the keyboard closed while genre results load; content focus is restored once results arrive.
		select(SearchSelection.Toolbar)
		return true
	}

	fun launchItem(item: BaseRowItem, rowItems: List<BaseRowItem>): Boolean {
		val adapter = MutableObjectAdapter<Any>().apply {
			rowItems.forEach(::add)
		}
		itemLauncher.launch(item, adapter, context)
		return true
	}

	fun activateSelection(): Boolean =
		when (val target = normalizeSelection(selection)) {
			SearchSelection.Toolbar,
			SearchSelection.Input -> false
			is SearchSelection.RecentSearch -> submitShortcutSearch(recentSearches[target.index].query)
			SearchSelection.ClearHistory -> {
				viewModel.clearSearchHistory()
				select(firstBlankSelection())
			}
			is SearchSelection.Genre -> submitGenreBrowse(genreShortcuts[target.index])
			is SearchSelection.Trending -> {
				val exploreItem = trendingSearches[target.index].exploreItem.launchableItem()
				if (exploreItem == null) {
					submitShortcutSearch(trendingSearches[target.index].query)
				} else {
					val rowItem = searchRowItem(exploreItem)
					launchItem(rowItem, listOf(rowItem))
				}
			}
			is SearchSelection.Filter -> {
				selectedFilterIndex = target.index
				select(firstResultSelection(target.index))
			}
			SearchSelection.TopResult -> {
				val item = topResult?.let(::searchRowItem) ?: return false
				launchItem(item, listOf(item))
			}
			is SearchSelection.RowItem -> {
				val items = visibleGroups.getOrNull(target.rowIndex)
					?.items
					?.map(::searchRowItem)
					.orEmpty()
				val item = items.getOrNull(target.itemIndex) ?: return false
				launchItem(item, items)
			}
		}

	fun moveBlankHorizontal(target: SearchSelection, delta: Int): Boolean {
		val (rowIndex, column) = blankRowPosition(target) ?: return false
		val row = blankRows.getOrNull(rowIndex) ?: return false
		return select(blankSelectionForRow(row, column + delta))
	}

	fun moveBlankVertical(target: SearchSelection, delta: Int): Boolean {
		val (rowIndex, column) = blankRowPosition(target) ?: return if (delta > 0) {
			select(firstBlankSelection())
		} else {
			select(SearchSelection.Input)
		}
		val nextRowIndex = rowIndex + if (delta > 0) 1 else -1
		val nextRow = blankRows.getOrNull(nextRowIndex)

		return when {
			nextRow != null -> select(blankSelectionForRow(nextRow, column))
			delta < 0 -> select(SearchSelection.Input)
			else -> false
		}
	}

	fun moveHorizontal(delta: Int): Boolean =
		when (val target = normalizeSelection(selection)) {
			is SearchSelection.RecentSearch,
			SearchSelection.ClearHistory,
			is SearchSelection.Genre,
			is SearchSelection.Trending -> moveBlankHorizontal(target, delta)
			is SearchSelection.Filter -> select(SearchSelection.Filter(target.index + delta))
			is SearchSelection.RowItem -> select(SearchSelection.RowItem(target.rowIndex, target.itemIndex + delta))
			else -> false
		}

	fun moveVertical(delta: Int): Boolean =
		when (val target = normalizeSelection(selection)) {
			SearchSelection.Toolbar -> if (delta > 0) select(firstBrowseSelection()) else true
			SearchSelection.Input -> if (delta > 0) {
				if (query.isBlank()) select(firstBlankSelection())
				else when {
					filters.size > 1 -> select(SearchSelection.Filter(selectedFilterIndex))
					else -> select(firstResultSelection())
				}
			} else {
				select(SearchSelection.Toolbar)
			}
			is SearchSelection.RecentSearch,
			SearchSelection.ClearHistory,
			is SearchSelection.Genre,
			is SearchSelection.Trending -> if (query.isBlank()) {
				moveBlankVertical(target, delta)
			} else {
				if (delta > 0) select(firstResultSelection()) else select(SearchSelection.Input)
			}
			is SearchSelection.Filter -> if (delta > 0) {
				selectedFilterIndex = target.index
				select(firstResultSelection(target.index))
			} else {
				select(SearchSelection.Input)
			}
			SearchSelection.TopResult -> if (delta > 0) {
				if (visibleGroups.isNotEmpty()) select(SearchSelection.RowItem(0, 0)) else true
			} else {
				if (filters.size > 1) select(SearchSelection.Filter(selectedFilterIndex)) else select(SearchSelection.Input)
			}
			is SearchSelection.RowItem -> if (delta > 0) {
				val nextRowIndex = (target.rowIndex + 1..visibleGroups.lastIndex)
					.firstOrNull { visibleGroups[it].items.isNotEmpty() }
				if (nextRowIndex == null) true else select(
					SearchSelection.RowItem(
						rowIndex = nextRowIndex,
						itemIndex = target.itemIndex.coerceAtMost(visibleGroups[nextRowIndex].items.lastIndex),
					)
				)
			} else {
				val previousRowIndex = (target.rowIndex - 1 downTo 0)
					.firstOrNull { visibleGroups[it].items.isNotEmpty() }
				when {
					previousRowIndex != null -> select(
						SearchSelection.RowItem(
							rowIndex = previousRowIndex,
							itemIndex = target.itemIndex.coerceAtMost(visibleGroups[previousRowIndex].items.lastIndex),
						)
					)
					selectedFilterIndex == 0 && topResult != null -> select(SearchSelection.TopResult)
					filters.size > 1 -> select(SearchSelection.Filter(selectedFilterIndex))
					else -> select(SearchSelection.Input)
				}
			}
		}

	fun handleKey(keyCode: Int): Boolean {
		if (selection == SearchSelection.Input) {
			return when (keyCode) {
				AndroidKeyEvent.KEYCODE_BACK -> handleBack()
				AndroidKeyEvent.KEYCODE_DPAD_UP -> {
					inputEditing = false
					moveVertical(-1)
				}
				AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
					inputEditing = false
					moveVertical(1)
				}
				AndroidKeyEvent.KEYCODE_DPAD_CENTER,
				AndroidKeyEvent.KEYCODE_ENTER,
				AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
					inputEditing = true
					true
				}
				else -> false
			}
		}

		return when (keyCode) {
			AndroidKeyEvent.KEYCODE_BACK -> handleBack()
			AndroidKeyEvent.KEYCODE_DPAD_LEFT -> moveHorizontal(-1)
			AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> moveHorizontal(1)
			AndroidKeyEvent.KEYCODE_DPAD_UP -> moveVertical(-1)
			AndroidKeyEvent.KEYCODE_DPAD_DOWN -> moveVertical(1)
			AndroidKeyEvent.KEYCODE_DPAD_CENTER,
			AndroidKeyEvent.KEYCODE_ENTER,
			AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> activateSelection()
			else -> false
		}
	}

	fun handleComposeKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
		val nativeEvent = event.nativeKeyEvent
		val keyCode = nativeEvent.keyCode

		if (!isSearchNavigationKeyCode(keyCode)) return false

		return when (event.type) {
			KeyEventType.KeyDown -> {
				if (nativeEvent.repeatCount > 0) true else handleKey(keyCode)
			}
			KeyEventType.KeyUp -> true
			else -> false
		}
	}

	fun blankContentIndex(target: SearchSelection): Int {
		val rowIndex = blankRowPosition(target)?.first ?: return 0
		return 1 + rowIndex
	}

	fun resultContentIndex(target: SearchSelection): Int {
		val topResultVisible = selectedFilterIndex == 0 && topResult != null

		return when (target) {
			is SearchSelection.Filter -> 0
			SearchSelection.TopResult -> if (topResultVisible) 1 else 0
			is SearchSelection.RowItem -> 1 + (if (topResultVisible) 1 else 0) + target.rowIndex
			else -> 0
		}
	}

	fun contentListIndex(target: SearchSelection): Int =
		if (query.isBlank()) blankContentIndex(target) else resultContentIndex(target)

	val currentBackHandler by rememberUpdatedState(newValue = ::handleBack)
	val currentKeyHandler by rememberUpdatedState(newValue = ::handleKey)

	SideEffect {
		onBackPressedHandlerChange { currentBackHandler() }
		onKeyPressedHandlerChange { keyCode -> currentKeyHandler(keyCode) }
	}

	LaunchedEffect(Unit) {
		if (initialQuery.isNotBlank()) {
			viewModel.searchImmediately(initialQuery)
		}
		select(if (initialQuery.isBlank()) SearchSelection.Input else SearchSelection.TopResult)
	}

	LaunchedEffect(contentSignature) {
		selectedFilterIndex = selectedFilterIndex.coerceIn(0, filters.lastIndex.coerceAtLeast(0))
		val normalizedSelection = if (pendingShortcutQuery == query && totalResults > 0) {
			pendingShortcutQuery = null
			firstResultSelection()
		} else {
			if (pendingShortcutQuery != null && pendingShortcutQuery != query) pendingShortcutQuery = null
			normalizeSelection(selection)
		}

		if (normalizedSelection != selection) {
			select(normalizedSelection)
		} else {
			selection = normalizedSelection
		}

		val selectedBaseItem = when (val target = normalizedSelection) {
			SearchSelection.TopResult -> topResult
			is SearchSelection.RowItem -> visibleGroups.getOrNull(target.rowIndex)?.items?.getOrNull(target.itemIndex)
			else -> null
		}
		if (selectedBaseItem != null) backgroundService.setBackground(selectedBaseItem)
		else backgroundService.clearBackgrounds()
	}

	LaunchedEffect(selection, contentSignature) {
		val normalizedSelection = normalizeSelection(selection)
		val index = contentListIndex(normalizedSelection)
		val layoutInfo = listState.layoutInfo
		val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
		val verticalSafeArea = with(density) { 24.dp.roundToPx() }
		val isComfortablyVisible = itemInfo != null &&
			itemInfo.offset >= layoutInfo.viewportStartOffset + verticalSafeArea &&
			itemInfo.offset + itemInfo.size <= layoutInfo.viewportEndOffset - verticalSafeArea

		if (!isComfortablyVisible) {
			listState.animateScrollToItem(index)
		}
	}

	LaunchedEffect(selection) {
		if (!inputEditing) requestFocusForSelection(selection)
	}

	LaunchedEffect(inputEditing) {
		if (!inputEditing && selection != SearchSelection.Toolbar) {
			runCatching { contentFocusRequester.requestFocus() }
		}
	}

	Column(
		modifier = Modifier
			.fillMaxSize()
			.background(Color(0xFF141414))
			.onPreviewKeyEvent(::handleComposeKey)
			.onKeyEvent(::handleComposeKey)
	) {
		MainToolbar(
			activeButton = MainToolbarActiveButton.Search,
			downFocusRequester = contentFocusRequester,
			focusRequester = toolbarFocusRequester,
			showFocusVisuals = selection == SearchSelection.Toolbar,
			onNavigateDown = { select(firstBrowseSelection()) },
		)

		SearchHeader(
			query = query,
			selected = selection == SearchSelection.Input,
			editing = inputEditing,
			onQueryChange = {
				query = it
				selectedFilterIndex = 0
				viewModel.searchDebounced(it)
			},
			onQuerySubmit = {
				inputEditing = false
				viewModel.searchImmediately(query)
				select(firstResultSelection())
			},
			onEditingChange = { inputEditing = it },
			onMoveFromInput = { delta -> moveVertical(delta) },
			showKeyboardOnFocus = false,
		)

		Box(
			modifier = Modifier
				.weight(1f)
				.fillMaxWidth()
				.focusRequester(contentFocusRequester)
				.onPreviewKeyEvent(::handleComposeKey)
				.onKeyEvent(::handleComposeKey)
				.focusable(),
		) {
			LazyColumn(
				state = listState,
				modifier = Modifier.fillMaxSize(),
				userScrollEnabled = false,
			) {
				item(key = "filters") {
					if (query.isNotBlank() && filters.size > 1) {
						SearchFilters(
							filters = filters,
							selectedFilterIndex = selectedFilterIndex,
							focusedFilterIndex = (selection as? SearchSelection.Filter)?.index,
						)
					} else {
						Spacer(modifier = Modifier.height(20.dp))
					}
				}

				if (query.isBlank()) {
					if (recentSearches.isNotEmpty()) {
						item(key = "recent-searches") {
							SearchRecentSearchesSection(
								recentSearches = recentSearches,
								focusedRecentIndex = (selection as? SearchSelection.RecentSearch)?.index,
								clearHistorySelected = selection == SearchSelection.ClearHistory,
							)
						}
					}

					genreShortcuts.chunked(GENRE_COLUMN_COUNT).forEachIndexed { rowIndex, genres ->
						item(key = "genre-row-$rowIndex") {
							SearchGenreRow(
								genres = genres,
								rowStartIndex = rowIndex * GENRE_COLUMN_COUNT,
								focusedGenreIndex = (selection as? SearchSelection.Genre)?.index,
							)
						}
					}

					if (trendingSearches.isNotEmpty()) {
						item(key = "trending-searches") {
							SearchTrendingSearchesSection(
								trendingSearches = trendingSearches,
								focusedTrendingIndex = (selection as? SearchSelection.Trending)?.index,
							)
						}
					}
				} else if (totalResults == 0) {
					item(key = "no-results") {
						SearchNoResults(query)
					}
				} else {
					if (selectedFilterIndex == 0 && topResult != null) {
						item(key = "top-result") {
							SearchTopResult(
								item = searchRowItem(topResult),
								selected = selection == SearchSelection.TopResult,
							)
						}
					}

					itemsIndexed(
						items = visibleGroups,
						key = { _, group -> group.labelRes },
					) { rowIndex, group ->
						SearchResultRow(
							title = stringResource(group.labelRes),
							items = group.items.map(::searchRowItem),
							rowIndex = rowIndex,
							selectedItemIndex = (selection as? SearchSelection.RowItem)
								?.takeIf { it.rowIndex == rowIndex }
								?.itemIndex,
						)
					}
				}

				item(key = "footer") {
					Spacer(modifier = Modifier.height(96.dp))
				}
			}
		}
	}
}

@Composable
private fun SearchHeader(
	query: String,
	selected: Boolean,
	editing: Boolean,
	onQueryChange: (String) -> Unit,
	onQuerySubmit: () -> Unit,
	onEditingChange: (Boolean) -> Unit,
	onMoveFromInput: (Int) -> Unit,
	showKeyboardOnFocus: Boolean,
) {
	val textFieldFocusRequester = remember { FocusRequester() }

	fun handleSearchInputKeyCode(keyCode: Int): Boolean =
		when (keyCode) {
			AndroidKeyEvent.KEYCODE_DPAD_UP -> {
				onEditingChange(false)
				onMoveFromInput(-1)
				true
			}
			AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
				onEditingChange(false)
				onMoveFromInput(1)
				true
			}
			AndroidKeyEvent.KEYCODE_DPAD_CENTER,
			AndroidKeyEvent.KEYCODE_ENTER,
			AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
				onEditingChange(true)
				true
			}
			else -> false
		}

	LaunchedEffect(selected) {
		if (!selected) onEditingChange(false)
	}

	LaunchedEffect(editing, selected) {
		if (!selected) return@LaunchedEffect

		runCatching {
			if (editing) textFieldFocusRequester.requestFocus()
		}
	}

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = 54.dp, top = 32.dp, end = 54.dp, bottom = 14.dp)
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
		) {
			Box(
				modifier = Modifier
					.weight(1f)
					.shadow(if (selected) 24.dp else 0.dp, SEARCH_INPUT_SHAPE)
					.border(
						width = if (selected) 3.dp else 1.dp,
						color = if (selected) Color.White else Color.White.copy(alpha = 0.10f),
						shape = SEARCH_INPUT_SHAPE,
					)
					.clip(SEARCH_INPUT_SHAPE)
					.background(Color.White.copy(alpha = 0.04f))
					.padding(2.dp)
			) {
				if (editing) {
					SearchTextInput(
						query = query,
						onQueryChange = onQueryChange,
						onQuerySubmit = {
							onEditingChange(false)
							onQuerySubmit()
						},
						placeholder = "Busca títulos, personas, géneros...",
						canFocus = true,
						forceFocused = selected,
						showKeyboardOnFocus = editing || showKeyboardOnFocus,
						onKeyPressed = { keyCode ->
							handleSearchInputKeyCode(keyCode)
						},
						modifier = Modifier
							.fillMaxWidth()
							.focusRequester(textFieldFocusRequester),
					)
				} else {
					SearchInputDisplay(
						query = query,
						placeholder = "Busca títulos, personas, géneros...",
						selected = selected,
					)
				}
			}
		}
	}
}

@Composable
private fun SearchInputDisplay(
	query: String,
	placeholder: String,
	selected: Boolean,
) {
	val text = query.ifBlank { placeholder }
	val textColor = if (query.isBlank()) Color.White.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.90f)

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 22.dp, vertical = 18.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Icon(
			ImageVector.vectorResource(R.drawable.ic_search),
			contentDescription = null,
			tint = Color.White.copy(alpha = if (selected) 0.96f else 0.62f),
			modifier = Modifier.size(28.dp),
		)
		Spacer(Modifier.width(16.dp))
		Text(
			text = text,
			color = textColor,
			fontSize = 20.sp,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
	}
}

@Composable
private fun SearchFilters(
	filters: List<SearchFilter>,
	selectedFilterIndex: Int,
	focusedFilterIndex: Int?,
) {
	LazyRow(
		contentPadding = PaddingValues(horizontal = 54.dp),
		horizontalArrangement = Arrangement.spacedBy(10.dp),
		userScrollEnabled = false,
	) {
		itemsIndexed(filters) { index, filter ->
			val selected = selectedFilterIndex == index
			val focused = focusedFilterIndex == index
			Row(
				modifier = Modifier
					.border(
						width = if (focused) 2.dp else 1.dp,
						color = when {
							focused -> Color.White
							selected -> Color.White.copy(alpha = 0.90f)
							else -> Color.White.copy(alpha = 0.10f)
						},
						shape = FILTER_SHAPE,
					)
					.background(
						color = if (selected) Color.White else Color.White.copy(alpha = 0.04f),
						shape = FILTER_SHAPE,
					)
					.padding(horizontal = 15.dp, vertical = 9.dp),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Text(
					text = filter.label,
					color = if (selected) Color.Black else Color.White.copy(alpha = 0.86f),
					fontSize = 14.sp,
					fontWeight = FontWeight.ExtraBold,
					maxLines = 1,
				)
				Text(
					text = filter.count.toString(),
					color = if (selected) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.45f),
					fontSize = 12.sp,
					fontWeight = FontWeight.Bold,
					maxLines = 1,
				)
			}
		}
	}
}

@Composable
private fun SearchTopResult(
	item: BaseRowItem,
	selected: Boolean,
) {
	val context = LocalContext.current
	val api = koinInject<ApiClient>()
	val imageUrl = item.searchLandscapeImageUrl(api, maxWidth = 720, maxHeight = 405)
	val title = item.getCardName(context) ?: item.getName(context).orEmpty()
	val subtitle = item.getSubText(context).orEmpty()

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = 54.dp, top = 30.dp, end = 54.dp)
	) {
		SectionEyebrow("Mejor resultado")

		Spacer(modifier = Modifier.height(14.dp))

		Row(
			modifier = Modifier
				.fillMaxWidth()
				.shadow(if (selected) 30.dp else 0.dp, TOP_RESULT_SHAPE)
				.border(
					width = if (selected) 3.dp else 1.dp,
					color = if (selected) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.08f),
					shape = TOP_RESULT_SHAPE,
				)
				.clip(TOP_RESULT_SHAPE)
				.background(Color(0xFF1A1A1A)),
		) {
			Box(
				modifier = Modifier
					.width(370.dp)
					.height(208.dp)
					.background(Color(0xFF202020)),
			) {
				if (imageUrl != null) {
					AsyncImage(
						modifier = Modifier.fillMaxSize(),
						url = imageUrl,
						aspectRatio = 16f / 9f,
						scaleType = ImageView.ScaleType.CENTER_CROP,
					)
				}
				Box(
					modifier = Modifier
						.matchParentSize()
						.background(
							Brush.horizontalGradient(
								listOf(
									Color.Transparent,
									Color(0xFF1A1A1A).copy(alpha = 0.96f),
								)
							)
						)
				)
			}

			Column(
				modifier = Modifier
					.weight(1f)
					.padding(horizontal = 26.dp, vertical = 22.dp),
				verticalArrangement = Arrangement.Center,
			) {
				Text(
					text = title,
					color = Color.White,
					fontSize = 38.sp,
					fontWeight = FontWeight.ExtraBold,
					letterSpacing = (-0.9).sp,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)

				if (subtitle.isNotBlank()) {
					Spacer(modifier = Modifier.height(8.dp))
					Text(
						text = subtitle,
						color = Color.White.copy(alpha = 0.68f),
						fontSize = 15.sp,
						fontWeight = FontWeight.SemiBold,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}

				Spacer(modifier = Modifier.height(18.dp))

				Row(
					modifier = Modifier
						.background(Color.White, RoundedCornerShape(4.dp))
						.padding(horizontal = 18.dp, vertical = 10.dp),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_play),
						contentDescription = null,
						tint = Color.Black,
						modifier = Modifier.size(18.dp),
					)
					Text(
						text = "Abrir",
						color = Color.Black,
						fontSize = 14.sp,
						fontWeight = FontWeight.ExtraBold,
					)
				}
			}
		}
	}
}

@Composable
private fun SearchResultRow(
	title: String,
	items: List<BaseRowItem>,
	rowIndex: Int,
	selectedItemIndex: Int?,
) {
	val rowListState = rememberLazyListState()
	val safeSelectedIndex = selectedItemIndex?.coerceIn(0, (items.size - 1).coerceAtLeast(0))

	LaunchedEffect(safeSelectedIndex, items.map { it.itemId }) {
		if (safeSelectedIndex != null) rowListState.animateScrollToItem(safeSelectedIndex)
	}

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(top = if (rowIndex == 0) 34.dp else 46.dp)
	) {
		Row(
			modifier = Modifier.padding(horizontal = 54.dp),
			verticalAlignment = Alignment.Bottom,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Text(
				text = title,
				color = Color.White,
				fontSize = 25.sp,
				fontWeight = FontWeight.ExtraBold,
				letterSpacing = (-0.3).sp,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)

			Text(
				modifier = Modifier.padding(bottom = 3.dp),
				text = "${items.size}",
				color = Color.White.copy(alpha = 0.45f),
				fontSize = 15.sp,
				fontWeight = FontWeight.Bold,
			)
		}

		Spacer(modifier = Modifier.height(16.dp))

		LazyRow(
			state = rowListState,
			contentPadding = PaddingValues(horizontal = 54.dp),
			horizontalArrangement = Arrangement.spacedBy(22.dp),
			userScrollEnabled = false,
		) {
			itemsIndexed(
				items = items,
				key = { index, item -> item.itemId ?: "$title-$index" },
			) { index, item ->
				SearchResultCard(
					item = item,
					selected = safeSelectedIndex == index,
				)
			}
		}
	}
}

@Composable
private fun SearchResultCard(
	item: BaseRowItem,
	selected: Boolean,
) {
	val context = LocalContext.current
	val api = koinInject<ApiClient>()
	val scale by animateFloatAsState(targetValue = if (selected) 1.07f else 1f, label = "search-card-scale")
	val imageUrl = item.searchLandscapeImageUrl(api, maxWidth = 560, maxHeight = 315)
	val title = item.getCardName(context) ?: item.getName(context).orEmpty()
	val subtitle = item.getSubText(context).orEmpty()
	val kindLabel = item.baseItem?.type?.searchKindLabel()

	Column(
		modifier = Modifier
			.width(260.dp)
			.graphicsLayer {
				scaleX = scale
				scaleY = scale
			}
	) {
		Box(
			modifier = Modifier
				.shadow(if (selected) 24.dp else 0.dp, CARD_SHAPE)
				.border(
					border = BorderStroke(if (selected) 3.dp else 0.dp, Color.White.copy(alpha = 0.94f)),
					shape = CARD_SHAPE,
				)
				.clip(CARD_SHAPE)
				.background(Color(0xFF191919))
		) {
			if (imageUrl != null) {
				AsyncImage(
					modifier = Modifier
						.width(260.dp)
						.height(146.dp),
					url = imageUrl,
					aspectRatio = 16f / 9f,
					scaleType = ImageView.ScaleType.CENTER_CROP,
				)
			} else {
				Box(
					modifier = Modifier
						.width(260.dp)
						.height(146.dp)
						.background(
							Brush.linearGradient(
								listOf(
									Color(0xFF232323),
									Color(0xFF101010),
								)
							)
						),
					contentAlignment = Alignment.Center,
				) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_search),
						contentDescription = null,
						tint = Color.White.copy(alpha = 0.36f),
						modifier = Modifier.size(34.dp),
					)
				}
			}

			Box(
				modifier = Modifier
					.matchParentSize()
					.background(Color.Black.copy(alpha = if (selected) 0.02f else 0.16f))
			)

			if (!kindLabel.isNullOrBlank()) {
				Text(
					modifier = Modifier
						.align(Alignment.TopStart)
						.padding(8.dp)
						.background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(2.dp))
						.padding(horizontal = 7.dp, vertical = 4.dp),
					text = kindLabel,
					color = Color.White,
					fontSize = 10.sp,
					fontWeight = FontWeight.ExtraBold,
					letterSpacing = 1.2.sp,
					maxLines = 1,
				)
			}

			if (selected) {
				Box(
					modifier = Modifier
						.align(Alignment.Center)
						.size(52.dp)
						.background(Color.White.copy(alpha = 0.93f), CircleShape),
					contentAlignment = Alignment.Center,
				) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_play),
						contentDescription = null,
						tint = Color.Black,
						modifier = Modifier.size(31.dp),
					)
				}
			}
		}

		Spacer(modifier = Modifier.height(10.dp))

		Text(
			modifier = Modifier.widthIn(max = 260.dp),
			text = title,
			color = Color.White,
			fontSize = 16.sp,
			fontWeight = FontWeight.ExtraBold,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)

		if (subtitle.isNotBlank()) {
			Text(
				modifier = Modifier.widthIn(max = 260.dp),
				text = subtitle,
				color = Color.White.copy(alpha = 0.55f),
				fontSize = 13.sp,
				fontWeight = FontWeight.Bold,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

@Composable
private fun SearchRecentSearchesSection(
	recentSearches: List<SearchShortcut>,
	focusedRecentIndex: Int?,
	clearHistorySelected: Boolean,
) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = 54.dp, top = 28.dp, end = 54.dp, bottom = 16.dp),
	) {
		SearchDiscoverySection(
			title = "Búsquedas recientes",
			trailing = "Borrar historial",
			trailingSelected = clearHistorySelected,
		)

		Spacer(modifier = Modifier.height(14.dp))

		Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
			recentSearches.forEachIndexed { index, shortcut ->
				RecentSearchChip(
					shortcut = shortcut,
					selected = focusedRecentIndex == index,
				)
			}
		}
	}
}

@Composable
private fun SearchGenreRow(
	genres: List<SearchGenre>,
	rowStartIndex: Int,
	focusedGenreIndex: Int?,
) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = 54.dp, end = 54.dp, bottom = 12.dp),
	) {
		if (rowStartIndex == 0) {
			Spacer(modifier = Modifier.height(18.dp))

			SearchDiscoverySection(
				title = "Explorar por género",
				trailing = "Tocá uno para ver todo el contenido",
			)

			Spacer(modifier = Modifier.height(16.dp))
		}

		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			genres.forEachIndexed { localIndex, genre ->
				val index = rowStartIndex + localIndex
				GenreShortcutCard(
					genre = genre,
					selected = focusedGenreIndex == index,
					modifier = Modifier.weight(1f),
				)
			}
			repeat(GENRE_COLUMN_COUNT - genres.size) {
				Spacer(modifier = Modifier.weight(1f))
			}
		}
	}
}

@Composable
private fun SearchTrendingSearchesSection(
	trendingSearches: List<TrendingShortcut>,
	focusedTrendingIndex: Int?,
) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = 54.dp, top = 20.dp, end = 54.dp, bottom = 28.dp),
	) {
		SearchDiscoverySection(
			title = "Explorar por colección",
			trailing = "Sagas y grupos personalizados",
		)

		Spacer(modifier = Modifier.height(16.dp))

		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			trendingSearches.forEachIndexed { index, trend ->
				TrendingShortcutCard(
					trend = trend,
					selected = focusedTrendingIndex == index,
					modifier = Modifier.weight(1f),
				)
			}
		}
	}
}

@Composable
private fun SearchDiscoverySection(
	title: String,
	trailing: String,
	trailingSelected: Boolean = false,
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.Bottom,
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		Text(
			text = title,
			color = Color.White,
			fontSize = 25.sp,
			fontWeight = FontWeight.ExtraBold,
			letterSpacing = (-0.3).sp,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)

		Box(
			modifier = Modifier
				.border(
					width = if (trailingSelected) 2.dp else 0.dp,
					color = Color.White.copy(alpha = 0.92f),
					shape = FILTER_SHAPE,
				)
				.background(
					color = if (trailingSelected) Color.White.copy(alpha = 0.12f) else Color.Transparent,
					shape = FILTER_SHAPE,
				)
				.padding(horizontal = 12.dp, vertical = 7.dp)
		) {
			Text(
				text = trailing,
				color = if (trailingSelected) Color.White else Color.White.copy(alpha = 0.45f),
				fontSize = 13.sp,
				fontWeight = FontWeight.Bold,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

@Composable
private fun RecentSearchChip(
	shortcut: SearchShortcut,
	selected: Boolean,
) {
	val scale by animateFloatAsState(targetValue = if (selected) 1.05f else 1f, label = "recent-search-scale")

	Row(
		modifier = Modifier
			.graphicsLayer {
				scaleX = scale
				scaleY = scale
			}
			.border(
				width = if (selected) 2.dp else 1.dp,
				color = if (selected) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.10f),
				shape = FILTER_SHAPE,
			)
			.background(
				color = if (selected) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.045f),
				shape = FILTER_SHAPE,
			)
			.padding(horizontal = 16.dp, vertical = 9.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text = "↻",
			color = Color.White.copy(alpha = 0.46f),
			fontSize = 13.sp,
			fontWeight = FontWeight.ExtraBold,
		)
		Text(
			text = shortcut.label,
			color = Color.White.copy(alpha = 0.88f),
			fontSize = 14.sp,
			fontWeight = FontWeight.ExtraBold,
			maxLines = 1,
		)
	}
}

@Composable
private fun GenreShortcutCard(
	genre: SearchGenre,
	selected: Boolean,
	modifier: Modifier = Modifier,
) {
	val api = koinInject<ApiClient>()
	val scale by animateFloatAsState(targetValue = if (selected) 1.04f else 1f, label = "genre-shortcut-scale")
	val representativeItem = genre.exploreItem.representativeItem ?: genre.exploreItem.item
	val representativeImageUrl = representativeItem
		?.let(::searchRowItem)
		?.searchLandscapeImageUrl(api, maxWidth = 480, maxHeight = 270)

	Box(
		modifier = modifier
			.height(92.dp)
			.graphicsLayer {
				scaleX = scale
				scaleY = scale
			}
			.shadow(if (selected) 20.dp else 0.dp, GENRE_CARD_SHAPE)
			.border(
				width = if (selected) 2.dp else 1.dp,
				color = if (selected) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.09f),
				shape = GENRE_CARD_SHAPE,
			)
			.clip(GENRE_CARD_SHAPE)
			.background(Color(0xFF171717))
	) {
		if (representativeImageUrl != null) {
			AsyncImage(
				modifier = Modifier.matchParentSize(),
				url = representativeImageUrl,
				aspectRatio = 16f / 9f,
				scaleType = ImageView.ScaleType.CENTER_CROP,
			)
		}

		Box(
			modifier = Modifier
				.matchParentSize()
				.background(
					Brush.linearGradient(
						listOf(
							genre.color.copy(alpha = if (selected) 0.80f else 0.58f),
							Color.Black.copy(alpha = 0.58f),
							Color.Black.copy(alpha = 0.88f),
						)
					)
				)
		)

		Box(
			modifier = Modifier
				.align(Alignment.TopEnd)
				.padding(top = 14.dp, end = 14.dp)
				.size(38.dp)
				.background(Color.Black.copy(alpha = 0.22f), CircleShape),
			contentAlignment = Alignment.Center,
		) {
			Icon(
				imageVector = ImageVector.vectorResource(genre.iconRes),
				contentDescription = null,
				tint = Color.White.copy(alpha = 0.36f),
				modifier = Modifier.size(21.dp),
			)
		}

		Column(
			modifier = Modifier
				.align(Alignment.BottomStart)
				.fillMaxWidth()
				.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
		) {
			Text(
				text = genre.label,
				color = Color.White,
				fontSize = 18.sp,
				fontWeight = FontWeight.ExtraBold,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			Spacer(modifier = Modifier.height(2.dp))
			Text(
				text = "${genre.count} títulos",
				color = Color.White.copy(alpha = 0.62f),
				fontSize = 12.sp,
				fontWeight = FontWeight.Bold,
				maxLines = 1,
			)
		}
	}
}

@Composable
private fun TrendingShortcutCard(
	trend: TrendingShortcut,
	selected: Boolean,
	modifier: Modifier = Modifier,
) {
	val api = koinInject<ApiClient>()
	val scale by animateFloatAsState(targetValue = if (selected) 1.04f else 1f, label = "trending-shortcut-scale")
	val representativeItem = trend.exploreItem.representativeItem ?: trend.exploreItem.item
	val representativeImageUrl = representativeItem
		?.let(::searchRowItem)
		?.searchLandscapeImageUrl(api, maxWidth = 220, maxHeight = 124)

	Row(
		modifier = modifier
			.graphicsLayer {
				scaleX = scale
				scaleY = scale
			}
			.border(
				width = if (selected) 2.dp else 1.dp,
				color = if (selected) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.09f),
				shape = TREND_CARD_SHAPE,
			)
			.background(
				color = if (selected) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.035f),
				shape = TREND_CARD_SHAPE,
			)
			.padding(horizontal = 12.dp, vertical = 11.dp),
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Box(
			modifier = Modifier
				.width(56.dp)
				.height(32.dp)
				.clip(RoundedCornerShape(3.dp))
				.background(Color.White.copy(alpha = 0.06f)),
			contentAlignment = Alignment.Center,
		) {
			if (representativeImageUrl != null) {
				AsyncImage(
					modifier = Modifier.matchParentSize(),
					url = representativeImageUrl,
					aspectRatio = 16f / 9f,
					scaleType = ImageView.ScaleType.CENTER_CROP,
				)
			} else {
				Icon(
					imageVector = ImageVector.vectorResource(R.drawable.ic_folder),
					contentDescription = null,
					tint = Color.White.copy(alpha = 0.35f),
					modifier = Modifier.size(18.dp),
				)
			}
		}

		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = trend.title,
				color = Color.White,
				fontSize = 15.sp,
				fontWeight = FontWeight.ExtraBold,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			Text(
				text = trend.subtitle,
				color = Color.White.copy(alpha = 0.55f),
				fontSize = 11.sp,
				fontWeight = FontWeight.Bold,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

@Composable
private fun SearchNoResults(query: String) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 54.dp, vertical = 64.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		Icon(
			imageVector = ImageVector.vectorResource(R.drawable.ic_search),
			contentDescription = null,
			tint = Color.White.copy(alpha = 0.30f),
			modifier = Modifier.size(52.dp),
		)
		Spacer(modifier = Modifier.height(18.dp))
		Text(
			text = "Sin resultados para \"$query\"",
			color = Color.White,
			fontSize = 26.sp,
			fontWeight = FontWeight.ExtraBold,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
		Spacer(modifier = Modifier.height(8.dp))
		Text(
			text = "Probá con otro título, actor o colección.",
			color = Color.White.copy(alpha = 0.58f),
			fontSize = 15.sp,
			fontWeight = FontWeight.SemiBold,
		)
	}
}

@Composable
private fun SectionEyebrow(text: String) {
	Text(
		text = text.uppercase(),
		color = Color.White.copy(alpha = 0.58f),
		fontSize = 13.sp,
		fontWeight = FontWeight.ExtraBold,
		letterSpacing = 2.3.sp,
		maxLines = 1,
	)
}

private fun searchRowItem(item: org.jellyfin.sdk.model.api.BaseItemDto) = BaseItemDtoBaseRowItem(
	item = item,
	preferParentThumb = true,
	staticHeight = true,
	selectAction = BaseRowItemSelectAction.Play,
)

private fun BaseRowItem.searchLandscapeImageUrl(
	api: ApiClient,
	maxWidth: Int,
	maxHeight: Int,
): String? = listOf(ImageType.THUMB, ImageType.BANNER, ImageType.POSTER)
	.firstNotNullOfOrNull { imageType ->
		getImage(imageType)?.getUrl(
			api,
			maxWidth = maxWidth,
			maxHeight = maxHeight,
			fillWidth = maxWidth,
			fillHeight = maxHeight,
		)
	}

private fun BaseItemKind.searchKindLabel() = when (this) {
	BaseItemKind.MOVIE -> "PELÍCULA"
	BaseItemKind.SERIES -> "SERIE"
	BaseItemKind.EPISODE -> "EPISODIO"
	BaseItemKind.VIDEO -> "VIDEO"
	BaseItemKind.LIVE_TV_PROGRAM -> "PROGRAMA"
	BaseItemKind.LIVE_TV_CHANNEL -> "CANAL"
	BaseItemKind.PLAYLIST -> "PLAYLIST"
	BaseItemKind.MUSIC_ARTIST -> "ARTISTA"
	BaseItemKind.MUSIC_ALBUM -> "ÁLBUM"
	BaseItemKind.AUDIO -> "CANCIÓN"
	BaseItemKind.PHOTO_ALBUM -> "ÁLBUM"
	BaseItemKind.PHOTO -> "FOTO"
	BaseItemKind.BOX_SET -> "COLECCIÓN"
	BaseItemKind.PERSON -> "PERSONA"
	else -> null
}

private val SEARCH_INPUT_SHAPE = RoundedCornerShape(6.dp)
private val FILTER_SHAPE = RoundedCornerShape(999.dp)
private val GENRE_CARD_SHAPE = RoundedCornerShape(6.dp)
private val TREND_CARD_SHAPE = RoundedCornerShape(6.dp)
private val TOP_RESULT_SHAPE = RoundedCornerShape(6.dp)
private val CARD_SHAPE = RoundedCornerShape(6.dp)
