package org.jellyfin.androidtv.ui.home

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItemSelectAction
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.koin.compose.koinInject

private sealed interface HomeSelection {
	data object Toolbar : HomeSelection
	data object Hero : HomeSelection
	data class Library(val index: Int) : HomeSelection
	data class Media(val rowIndex: Int, val itemIndex: Int) : HomeSelection
}

@Composable
internal fun HomeScreen(
	heroState: HomeHeroState,
	rowsState: HomeRowsState,
	onBackPressedHandlerChange: (() -> Boolean) -> Unit,
	onKeyPressedHandlerChange: ((Int) -> Boolean) -> Unit,
	onRestoreFocusChange: (() -> Unit) -> Unit,
) {
	val context = LocalContext.current
	val density = LocalDensity.current
	val itemLauncher = koinInject<ItemLauncher>()
	val navigationRepository = koinInject<NavigationRepository>()
	val listState = rememberLazyListState()
	val scope = rememberCoroutineScope()
	val toolbarFocusRequester = remember { FocusRequester() }

	val content = rowsState as? HomeRowsState.Content
	val libraries = content?.libraries.orEmpty()
	val rows = content?.rows.orEmpty()
	val hasHero = heroState is HomeHeroState.Content && heroState.items.isNotEmpty()
	val contentSignature = buildString {
		append(hasHero)
		libraries.forEach { append("|l:").append(it.id) }
		rows.forEach { row -> append("|r:").append(row.id).append(':').append(row.items.size) }
	}

	var selection by remember { mutableStateOf<HomeSelection>(HomeSelection.Toolbar) }
	var didSelectInitialContent by remember { mutableStateOf(false) }
	var scrollJob by remember { mutableStateOf<Job?>(null) }

	fun firstBodySelection(): HomeSelection =
		when {
			hasHero -> HomeSelection.Hero
			libraries.isNotEmpty() -> HomeSelection.Library(0)
			rows.firstOrNull { it.items.isNotEmpty() } != null -> {
				val rowIndex = rows.indexOfFirst { it.items.isNotEmpty() }
				HomeSelection.Media(rowIndex, 0)
			}
			else -> HomeSelection.Toolbar
		}

	fun normalizeSelection(target: HomeSelection): HomeSelection =
		when (target) {
			HomeSelection.Toolbar -> HomeSelection.Toolbar
			HomeSelection.Hero -> if (hasHero) HomeSelection.Hero else firstBodySelection()
			is HomeSelection.Library -> {
				if (libraries.isEmpty()) firstBodySelection()
				else HomeSelection.Library(target.index.coerceIn(0, libraries.lastIndex))
			}
			is HomeSelection.Media -> {
				val row = rows.getOrNull(target.rowIndex)
				if (row == null || row.items.isEmpty()) {
					firstBodySelection()
				} else {
					HomeSelection.Media(
						rowIndex = target.rowIndex.coerceIn(0, rows.lastIndex),
						itemIndex = target.itemIndex.coerceIn(0, row.items.lastIndex),
					)
				}
			}
		}

	fun lazyItemPositionForSelection(target: HomeSelection): Pair<Int, Int>? =
		when (target) {
			HomeSelection.Toolbar -> null
			HomeSelection.Hero -> if (hasHero) 0 to 0 else null
			is HomeSelection.Library -> if (libraries.isNotEmpty()) {
				if (hasHero) {
					0 to with(density) { HOME_LIBRARY_FOCUS_SCROLL_OFFSET.roundToPx() }
				} else {
					0 to 0
				}
			} else {
				null
			}
			is HomeSelection.Media -> {
				val heroOffset = if (hasHero) 1 else 0
				val librariesOffset = if (libraries.isNotEmpty()) 1 else 0
				(heroOffset + librariesOffset + target.rowIndex) to 0
			}
		}

	fun select(target: HomeSelection): Boolean {
		val normalized = normalizeSelection(target)
		if (normalized == HomeSelection.Toolbar) {
			selection = normalized
			runCatching { toolbarFocusRequester.requestFocus() }
			return true
		}

		selection = normalized
		return true
	}

	fun launchHero(): Boolean {
		val item = (heroState as? HomeHeroState.Content)?.items?.firstOrNull()?.baseItem ?: return false
		val rowItem = BaseItemDtoBaseRowItem(
			item = item,
			staticHeight = true,
			selectAction = BaseRowItemSelectAction.Play,
		)
		val adapter = MutableObjectAdapter<Any>().apply { add(rowItem) }

		itemLauncher.launch(rowItem, adapter, context)
		return true
	}

	fun launchLibrary(index: Int): Boolean {
		val library = libraries.getOrNull(index) ?: return false
		navigationRepository.navigate(
			itemLauncher.getUserViewDestination(library.source),
			replace = true,
		)
		return true
	}

	fun launchMedia(rowIndex: Int, itemIndex: Int): Boolean {
		val row = rows.getOrNull(rowIndex) ?: return false
		val item = row.items.getOrNull(itemIndex) ?: return false
		val adapter = MutableObjectAdapter<Any>().apply {
			row.items.forEach(::add)
		}

		itemLauncher.launch(item, adapter, context)
		return true
	}

	fun activateSelection(): Boolean =
		when (val target = normalizeSelection(selection)) {
			HomeSelection.Toolbar -> false
			HomeSelection.Hero -> launchHero()
			is HomeSelection.Library -> launchLibrary(target.index)
			is HomeSelection.Media -> launchMedia(target.rowIndex, target.itemIndex)
		}

	fun firstMediaRowSelection(): HomeSelection? {
		val firstRowIndex = rows.indexOfFirst { it.items.isNotEmpty() }
		return firstRowIndex.takeIf { it >= 0 }?.let { HomeSelection.Media(it, 0) }
	}

	fun moveHorizontal(delta: Int): Boolean =
		when (val target = normalizeSelection(selection)) {
			HomeSelection.Toolbar,
			HomeSelection.Hero -> false
			is HomeSelection.Library -> select(HomeSelection.Library(target.index + delta))
			is HomeSelection.Media -> select(HomeSelection.Media(target.rowIndex, target.itemIndex + delta))
		}

	fun moveVertical(delta: Int): Boolean =
		when (val target = normalizeSelection(selection)) {
			HomeSelection.Toolbar -> if (delta > 0) select(firstBodySelection()) else true
			HomeSelection.Hero -> if (delta > 0) {
				when {
					libraries.isNotEmpty() -> select(HomeSelection.Library(0))
					firstMediaRowSelection() != null -> select(firstMediaRowSelection()!!)
					else -> true
				}
			} else {
				select(HomeSelection.Toolbar)
			}
			is HomeSelection.Library -> if (delta > 0) {
				firstMediaRowSelection()?.let(::select) ?: true
			} else {
				if (hasHero) select(HomeSelection.Hero) else select(HomeSelection.Toolbar)
			}
			is HomeSelection.Media -> if (delta > 0) {
				val nextRowIndex = (target.rowIndex + 1..rows.lastIndex).firstOrNull { rows[it].items.isNotEmpty() }
				if (nextRowIndex == null) true else select(
					HomeSelection.Media(
						rowIndex = nextRowIndex,
						itemIndex = target.itemIndex.coerceAtMost(rows[nextRowIndex].items.lastIndex),
					)
				)
			} else {
				val previousRowIndex = (target.rowIndex - 1 downTo 0).firstOrNull { rows[it].items.isNotEmpty() }
				when {
					previousRowIndex != null -> select(
						HomeSelection.Media(
							rowIndex = previousRowIndex,
							itemIndex = target.itemIndex.coerceAtMost(rows[previousRowIndex].items.lastIndex),
						)
					)
					libraries.isNotEmpty() -> select(HomeSelection.Library(0))
					hasHero -> select(HomeSelection.Hero)
					else -> select(HomeSelection.Toolbar)
				}
			}
		}

	fun handleBack(): Boolean =
		if (selection != HomeSelection.Toolbar) select(HomeSelection.Toolbar) else false

	fun handleKey(keyCode: Int): Boolean =
		when (keyCode) {
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

	fun restoreFocus() {
		runCatching { toolbarFocusRequester.requestFocus() }
		if (selection == HomeSelection.Toolbar) select(firstBodySelection())
	}

	LaunchedEffect(Unit) {
		runCatching { toolbarFocusRequester.requestFocus() }
	}

	LaunchedEffect(contentSignature) {
		selection = normalizeSelection(selection)
		if (!didSelectInitialContent && firstBodySelection() != HomeSelection.Toolbar) {
			didSelectInitialContent = true
			selection = firstBodySelection()
		}
	}

	LaunchedEffect(selection, contentSignature) {
		val (targetIndex, scrollOffset) = lazyItemPositionForSelection(normalizeSelection(selection)) ?: return@LaunchedEffect
		scrollJob?.cancel()
		scrollJob = scope.launch {
			listState.animateScrollToItem(targetIndex, scrollOffset)
		}
	}

	SideEffect {
		onBackPressedHandlerChange(::handleBack)
		onKeyPressedHandlerChange(::handleKey)
		onRestoreFocusChange(::restoreFocus)
	}

	DisposableEffect(Unit) {
		onDispose { scrollJob?.cancel() }
	}

	Column(
		modifier = Modifier
			.fillMaxSize()
			.background(Color.Black)
			.onPreviewKeyEvent { event ->
				if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

				val keyCode = event.nativeKeyEvent.keyCode
				if (selection == HomeSelection.Toolbar && keyCode !in BODY_ENTRY_KEYS) {
					keyCode == AndroidKeyEvent.KEYCODE_BACK && handleBack()
				} else {
					handleKey(keyCode)
				}
			}
	) {
		MainToolbar(
			modifier = Modifier.onFocusChanged {
				if (it.hasFocus) selection = HomeSelection.Toolbar
			},
			activeButton = MainToolbarActiveButton.Home,
			downFocusRequester = null,
			onNavigateDown = { select(firstBodySelection()) },
			focusRequester = toolbarFocusRequester,
			showFocusVisuals = selection == HomeSelection.Toolbar,
		)

		LazyColumn(
			state = listState,
			modifier = Modifier
				.weight(1f)
				.fillMaxWidth()
				.background(Color.Black),
			userScrollEnabled = false,
		) {
			if (hasHero) {
				item(key = "hero") {
					HomeHero(
						state = heroState,
						selected = selection == HomeSelection.Hero,
					)
				}
			}

			if (libraries.isNotEmpty()) {
				item(key = "libraries") {
					HomeLibraryPills(
						libraries = libraries,
						selectedIndex = (selection as? HomeSelection.Library)?.index,
					)
				}
			}

			when (val state = rowsState) {
				HomeRowsState.Empty,
				is HomeRowsState.Error,
				HomeRowsState.Loading -> {
					item(key = "rows-placeholder") {
						Spacer(modifier = Modifier.height(24.dp))
					}
				}

				is HomeRowsState.Content -> {
					itemsIndexed(
						items = state.rows,
						key = { _, row -> row.id },
					) { index, row ->
						HomeMediaRow(
							row = row,
							rowIndex = index,
							selectedItemIndex = (selection as? HomeSelection.Media)
								?.takeIf { it.rowIndex == index }
								?.itemIndex,
						)
					}
				}
			}

			item(key = "footer-spacer") {
				Spacer(modifier = Modifier.height(90.dp))
			}
		}
	}
}

private val BODY_ENTRY_KEYS = setOf(
	AndroidKeyEvent.KEYCODE_DPAD_DOWN,
	AndroidKeyEvent.KEYCODE_BACK,
)

private val HOME_LIBRARY_FOCUS_SCROLL_OFFSET = 180.dp
