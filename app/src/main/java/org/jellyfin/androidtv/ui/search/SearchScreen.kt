package org.jellyfin.androidtv.ui.search

import android.view.KeyEvent as AndroidKeyEvent
import android.widget.ImageView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
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
import org.jellyfin.androidtv.ui.search.composable.SearchVoiceInput
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.speech.rememberSpeechRecognizerAvailability
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private sealed interface SearchSelection {
	data object Toolbar : SearchSelection
	data object Input : SearchSelection
	data class Filter(val index: Int) : SearchSelection
	data object TopResult : SearchSelection
	data class RowItem(val rowIndex: Int, val itemIndex: Int) : SearchSelection
}

private data class SearchFilter(
	val label: String,
	val groupIndex: Int?,
	val count: Int,
)

private data class SearchDisplayGroup(
	val sourceIndex: Int,
	val labelRes: Int,
	val items: List<org.jellyfin.sdk.model.api.BaseItemDto>,
)

@Composable
internal fun SearchScreen(
	initialQuery: String,
) {
	val context = LocalContext.current
	val viewModel = koinViewModel<SearchViewModel>()
	val itemLauncher = koinInject<ItemLauncher>()
	val backgroundService = koinInject<BackgroundService>()
	val searchResults by viewModel.searchResultsFlow.collectAsState()
	val toolbarFocusRequester = remember { FocusRequester() }
	val inputFocusRequester = remember { FocusRequester() }
	val listState = rememberLazyListState()
	val speechRecognizerAvailability = rememberSpeechRecognizerAvailability()
	var query by rememberSaveable { mutableStateOf(initialQuery) }
	var selection by remember { mutableStateOf<SearchSelection>(SearchSelection.Input) }
	var selectedFilterIndex by rememberSaveable { mutableStateOf(0) }

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
		groups.forEach { group ->
			append("|g:").append(group.sourceIndex).append(':').append(group.items.joinToString(",") { it.id.toString() })
		}
	}

	fun firstResultSelection(filterIndex: Int = selectedFilterIndex): SearchSelection =
		when {
			query.isBlank() -> SearchSelection.Input
			totalResults == 0 -> SearchSelection.Input
			filterIndex == 0 && topResult != null -> SearchSelection.TopResult
			groups.isNotEmpty() -> SearchSelection.RowItem(0, 0)
			else -> SearchSelection.Input
		}

	fun normalizeSelection(target: SearchSelection): SearchSelection =
		when (target) {
			SearchSelection.Toolbar -> SearchSelection.Toolbar
			SearchSelection.Input -> SearchSelection.Input
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
		selection = normalized
		when (normalized) {
			SearchSelection.Toolbar -> runCatching { toolbarFocusRequester.requestFocus() }
			SearchSelection.Input -> runCatching { inputFocusRequester.requestFocus() }
			else -> Unit
		}
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

	fun moveHorizontal(delta: Int): Boolean =
		when (val target = normalizeSelection(selection)) {
			is SearchSelection.Filter -> select(SearchSelection.Filter(target.index + delta))
			is SearchSelection.RowItem -> select(SearchSelection.RowItem(target.rowIndex, target.itemIndex + delta))
			else -> false
		}

	fun moveVertical(delta: Int): Boolean =
		when (val target = normalizeSelection(selection)) {
			SearchSelection.Toolbar -> if (delta > 0) select(SearchSelection.Input) else true
			SearchSelection.Input -> if (delta > 0) {
				when {
					filters.size > 1 -> select(SearchSelection.Filter(selectedFilterIndex))
					else -> select(firstResultSelection())
				}
			} else {
				select(SearchSelection.Toolbar)
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

	fun handleKey(keyCode: Int): Boolean =
		when (keyCode) {
			AndroidKeyEvent.KEYCODE_BACK -> if (selection != SearchSelection.Toolbar) select(SearchSelection.Toolbar) else false
			AndroidKeyEvent.KEYCODE_DPAD_LEFT -> moveHorizontal(-1)
			AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> moveHorizontal(1)
			AndroidKeyEvent.KEYCODE_DPAD_UP -> moveVertical(-1)
			AndroidKeyEvent.KEYCODE_DPAD_DOWN -> moveVertical(1)
			AndroidKeyEvent.KEYCODE_DPAD_CENTER,
			AndroidKeyEvent.KEYCODE_ENTER,
			AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> activateSelection()
			else -> false
		}

	LaunchedEffect(Unit) {
		if (initialQuery.isNotBlank()) {
			viewModel.searchImmediately(initialQuery)
			selection = SearchSelection.TopResult
		}
		select(if (initialQuery.isBlank()) SearchSelection.Input else SearchSelection.TopResult)
	}

	LaunchedEffect(contentSignature) {
		selectedFilterIndex = selectedFilterIndex.coerceIn(0, filters.lastIndex.coerceAtLeast(0))
		selection = normalizeSelection(selection)
		val selectedBaseItem = when (val target = selection) {
			SearchSelection.TopResult -> topResult
			is SearchSelection.RowItem -> visibleGroups.getOrNull(target.rowIndex)?.items?.getOrNull(target.itemIndex)
			else -> null
		}
		if (selectedBaseItem != null) backgroundService.setBackground(selectedBaseItem)
		else backgroundService.clearBackgrounds()
	}

	LaunchedEffect(selection, contentSignature) {
		val index = when (normalizeSelection(selection)) {
			SearchSelection.Toolbar,
			SearchSelection.Input -> 0
			is SearchSelection.Filter -> 0
			SearchSelection.TopResult -> 1
			is SearchSelection.RowItem -> {
				val topOffset = if (selectedFilterIndex == 0 && topResult != null) 1 else 0
				1 + topOffset + (selection as SearchSelection.RowItem).rowIndex
			}
		}
		listState.animateScrollToItem(index)
	}

	Column(
		modifier = Modifier
			.fillMaxSize()
			.background(Color(0xFF141414))
			.onPreviewKeyEvent { event ->
				if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
				handleKey(event.nativeKeyEvent.keyCode)
			}
	) {
		MainToolbar(
			activeButton = MainToolbarActiveButton.Search,
			focusRequester = toolbarFocusRequester,
			showFocusVisuals = selection == SearchSelection.Toolbar,
			onNavigateDown = { select(SearchSelection.Input) },
		)

		SearchHeader(
			query = query,
			selected = selection == SearchSelection.Input,
			speechRecognizerAvailability = speechRecognizerAvailability,
			inputFocusRequester = inputFocusRequester,
			onQueryChange = {
				query = it
				selectedFilterIndex = 0
				viewModel.searchDebounced(it)
			},
			onQuerySubmit = {
				viewModel.searchImmediately(query)
				select(firstResultSelection())
			},
		)

		LazyColumn(
			state = listState,
			modifier = Modifier
				.weight(1f)
				.fillMaxWidth(),
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
				item(key = "empty") {
					SearchEmptyState()
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

@Composable
private fun SearchHeader(
	query: String,
	selected: Boolean,
	speechRecognizerAvailability: Boolean,
	inputFocusRequester: FocusRequester,
	onQueryChange: (String) -> Unit,
	onQuerySubmit: () -> Unit,
) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = 54.dp, top = 30.dp, end = 54.dp, bottom = 18.dp)
	) {
		Text(
			text = "Buscar",
			color = Color.White,
			fontSize = 48.sp,
			fontWeight = FontWeight.ExtraBold,
			letterSpacing = (-1.2).sp,
		)

		Spacer(modifier = Modifier.height(18.dp))

		Row(
			horizontalArrangement = Arrangement.spacedBy(14.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			if (speechRecognizerAvailability) {
				SearchVoiceInput(
					onQueryChange = onQueryChange,
					onQuerySubmit = onQuerySubmit,
				)
			}

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
				SearchTextInput(
					query = query,
					onQueryChange = onQueryChange,
					onQuerySubmit = onQuerySubmit,
					modifier = Modifier
						.fillMaxWidth()
						.focusRequester(inputFocusRequester),
				)
			}
		}
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
	val image = item.getImage(ImageType.THUMB) ?: item.getImage(ImageType.POSTER)
	val imageUrl = image?.getUrl(api, maxWidth = 720, maxHeight = 405, fillWidth = 720, fillHeight = 405)
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
	val image = item.getImage(ImageType.THUMB) ?: item.getImage(ImageType.POSTER)
	val imageUrl = image?.getUrl(api, maxWidth = 560, maxHeight = 315, fillWidth = 560, fillHeight = 315)
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
private fun SearchEmptyState() {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 54.dp, vertical = 46.dp),
	) {
		SectionEyebrow("Explorar")
		Spacer(modifier = Modifier.height(14.dp))
		Text(
			text = "Encontrá títulos, personas y colecciones",
			color = Color.White,
			fontSize = 30.sp,
			fontWeight = FontWeight.ExtraBold,
			letterSpacing = (-0.5).sp,
		)
		Spacer(modifier = Modifier.height(10.dp))
		Text(
			text = "Escribí para ver el mejor resultado, filtrar por tipo y navegar filas optimizadas para control remoto.",
			color = Color.White.copy(alpha = 0.62f),
			fontSize = 16.sp,
			fontWeight = FontWeight.SemiBold,
		)
		Spacer(modifier = Modifier.height(30.dp))
		Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
			SearchHintPill("Películas")
			SearchHintPill("Series")
			SearchHintPill("Personas")
			SearchHintPill("Colecciones")
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
private fun SearchHintPill(label: String) {
	Row(
		modifier = Modifier
			.border(1.dp, Color.White.copy(alpha = 0.10f), FILTER_SHAPE)
			.background(Color.White.copy(alpha = 0.04f), FILTER_SHAPE)
			.padding(horizontal = 16.dp, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text = label,
			color = Color.White.copy(alpha = 0.78f),
			fontSize = 14.sp,
			fontWeight = FontWeight.Bold,
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
private val TOP_RESULT_SHAPE = RoundedCornerShape(6.dp)
private val CARD_SHAPE = RoundedCornerShape(6.dp)
