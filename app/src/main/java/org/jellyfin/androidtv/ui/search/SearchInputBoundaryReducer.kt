package org.jellyfin.androidtv.ui.search

internal data class SearchInputBoundaryState(
	val editing: Boolean = false,
	val imeWasVisible: Boolean = false,
	val pendingBrowseFocus: Boolean = false,
)

internal sealed interface SearchInputBoundaryEvent {
	data class EditingChanged(val editing: Boolean) : SearchInputBoundaryEvent
	data class BackPressed(val browseTargetAvailable: Boolean) : SearchInputBoundaryEvent
	data class ImeVisibilityChanged(
		val visible: Boolean,
		val browseTargetAvailable: Boolean,
	) : SearchInputBoundaryEvent
	data class BrowseAvailabilityChanged(val available: Boolean) : SearchInputBoundaryEvent
}

internal enum class SearchInputBoundaryEffect {
	None,
	KeepInputFocus,
	FocusBrowse,
}

internal data class SearchInputBoundaryResult(
	val state: SearchInputBoundaryState,
	val effect: SearchInputBoundaryEffect,
)

internal fun hasSearchBrowseTarget(
	query: String,
	blankBrowseTargetAvailable: Boolean,
	resultCount: Int,
): Boolean = if (query.isBlank()) blankBrowseTargetAvailable else resultCount > 0

internal enum class SearchBrowseDestination {
	Input,
	BlankContent,
	Filter,
	TopResult,
	Result,
}

internal fun firstSearchBrowseDestination(
	query: String,
	blankBrowseTargetAvailable: Boolean,
	filterCount: Int,
	allFilterSelected: Boolean,
	topResultAvailable: Boolean,
	resultGroupAvailable: Boolean,
	resultCount: Int,
): SearchBrowseDestination = when {
	query.isBlank() && blankBrowseTargetAvailable -> SearchBrowseDestination.BlankContent
	query.isBlank() -> SearchBrowseDestination.Input
	resultCount == 0 -> SearchBrowseDestination.Input
	filterCount > 1 -> SearchBrowseDestination.Filter
	allFilterSelected && topResultAvailable -> SearchBrowseDestination.TopResult
	resultGroupAvailable -> SearchBrowseDestination.Result
	else -> SearchBrowseDestination.Input
}

internal data class PendingSearchBrowseResolution(
	val boundaryState: SearchInputBoundaryState,
	val pending: Boolean,
	val destination: SearchBrowseDestination,
)

internal fun resolvePendingSearchBrowseNavigation(
	pending: Boolean,
	boundaryState: SearchInputBoundaryState,
	query: String,
	blankBrowseTargetAvailable: Boolean,
	filterCount: Int,
	allFilterSelected: Boolean,
	topResultAvailable: Boolean,
	resultGroupAvailable: Boolean,
	resultCount: Int,
): PendingSearchBrowseResolution? {
	if (!pending || !hasSearchBrowseTarget(query, blankBrowseTargetAvailable, resultCount)) return null

	val boundaryResult = SearchInputBoundaryReducer.reduce(
		state = boundaryState,
		event = SearchInputBoundaryEvent.BrowseAvailabilityChanged(available = true),
	)
	return PendingSearchBrowseResolution(
		boundaryState = boundaryResult.state,
		pending = false,
		destination = firstSearchBrowseDestination(
			query = query,
			blankBrowseTargetAvailable = blankBrowseTargetAvailable,
			filterCount = filterCount,
			allFilterSelected = allFilterSelected,
			topResultAvailable = topResultAvailable,
			resultGroupAvailable = resultGroupAvailable,
			resultCount = resultCount,
		),
	)
}

internal object SearchInputBoundaryReducer {
	fun reduce(
		state: SearchInputBoundaryState,
		event: SearchInputBoundaryEvent,
	): SearchInputBoundaryResult = when (event) {
		is SearchInputBoundaryEvent.EditingChanged -> SearchInputBoundaryResult(
			state = state.copy(
				editing = event.editing,
				imeWasVisible = if (event.editing) state.imeWasVisible else false,
				pendingBrowseFocus = false,
			),
			effect = SearchInputBoundaryEffect.None,
		)
		is SearchInputBoundaryEvent.BackPressed -> leaveInput(state, event.browseTargetAvailable)
		is SearchInputBoundaryEvent.ImeVisibilityChanged -> when {
			!state.editing -> SearchInputBoundaryResult(state, SearchInputBoundaryEffect.None)
			event.visible -> SearchInputBoundaryResult(
				state.copy(imeWasVisible = true),
				SearchInputBoundaryEffect.None,
			)
			state.imeWasVisible -> leaveInput(state, event.browseTargetAvailable)
			else -> SearchInputBoundaryResult(state, SearchInputBoundaryEffect.None)
		}
		is SearchInputBoundaryEvent.BrowseAvailabilityChanged -> when {
			state.pendingBrowseFocus && event.available -> SearchInputBoundaryResult(
				state.copy(pendingBrowseFocus = false),
				SearchInputBoundaryEffect.FocusBrowse,
			)
			else -> SearchInputBoundaryResult(state, SearchInputBoundaryEffect.None)
		}
	}

	private fun leaveInput(
		state: SearchInputBoundaryState,
		browseTargetAvailable: Boolean,
	): SearchInputBoundaryResult = if (browseTargetAvailable) {
		SearchInputBoundaryResult(
			state = state.copy(editing = false, imeWasVisible = false, pendingBrowseFocus = false),
			effect = SearchInputBoundaryEffect.FocusBrowse,
		)
	} else {
		SearchInputBoundaryResult(
			state = state.copy(editing = false, imeWasVisible = false, pendingBrowseFocus = true),
			effect = SearchInputBoundaryEffect.KeepInputFocus,
		)
	}
}
