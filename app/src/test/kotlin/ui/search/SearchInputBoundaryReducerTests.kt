package org.jellyfin.androidtv.ui.search

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SearchInputBoundaryReducerTests : FunSpec({
	test("Back dismisses editing and moves to an available browse target") {
		val result = SearchInputBoundaryReducer.reduce(
			state = SearchInputBoundaryState(editing = true),
			event = SearchInputBoundaryEvent.BackPressed(browseTargetAvailable = true),
		)

		result.state shouldBe SearchInputBoundaryState(editing = false)
		result.effect shouldBe SearchInputBoundaryEffect.FocusBrowse
	}

	test("Back waits for a late async row instead of losing focus") {
		val waiting = SearchInputBoundaryReducer.reduce(
			state = SearchInputBoundaryState(editing = true),
			event = SearchInputBoundaryEvent.BackPressed(browseTargetAvailable = false),
		)

		waiting.state shouldBe SearchInputBoundaryState(editing = false, pendingBrowseFocus = true)
		waiting.effect shouldBe SearchInputBoundaryEffect.KeepInputFocus

		val restored = SearchInputBoundaryReducer.reduce(
			state = waiting.state,
			event = SearchInputBoundaryEvent.BrowseAvailabilityChanged(available = true),
		)

		restored.state shouldBe SearchInputBoundaryState(editing = false)
		restored.effect shouldBe SearchInputBoundaryEffect.FocusBrowse
	}

	test("IME dismissal restores browse focus only after the IME was observed") {
		val imeShown = SearchInputBoundaryReducer.reduce(
			state = SearchInputBoundaryState(editing = true),
			event = SearchInputBoundaryEvent.ImeVisibilityChanged(visible = true, browseTargetAvailable = true),
		)

		val imeHidden = SearchInputBoundaryReducer.reduce(
			state = imeShown.state,
			event = SearchInputBoundaryEvent.ImeVisibilityChanged(visible = false, browseTargetAvailable = true),
		)

		imeHidden.state shouldBe SearchInputBoundaryState(editing = false)
		imeHidden.effect shouldBe SearchInputBoundaryEffect.FocusBrowse
	}

	test("a hidden IME before editing is a no-op") {
		SearchInputBoundaryReducer.reduce(
			state = SearchInputBoundaryState(),
			event = SearchInputBoundaryEvent.ImeVisibilityChanged(visible = false, browseTargetAvailable = true),
		) shouldBe SearchInputBoundaryResult(SearchInputBoundaryState(), SearchInputBoundaryEffect.None)
	}

	test("a nonblank query restores pending browse focus when async results arrive") {
		val waiting = SearchInputBoundaryReducer.reduce(
			state = SearchInputBoundaryState(editing = true),
			event = SearchInputBoundaryEvent.BackPressed(
				browseTargetAvailable = hasSearchBrowseTarget(
					query = "alien",
					blankBrowseTargetAvailable = false,
					resultCount = 0,
				),
			),
		)

		val restored = SearchInputBoundaryReducer.reduce(
			state = waiting.state,
			event = SearchInputBoundaryEvent.BrowseAvailabilityChanged(
				available = hasSearchBrowseTarget(
					query = "alien",
					blankBrowseTargetAvailable = false,
					resultCount = 1,
				),
			),
		)

		waiting.effect shouldBe SearchInputBoundaryEffect.KeepInputFocus
		restored.effect shouldBe SearchInputBoundaryEffect.FocusBrowse
		restored.state.pendingBrowseFocus shouldBe false
		firstSearchBrowseDestination(
			query = "alien",
			blankBrowseTargetAvailable = true,
			filterCount = 2,
			allFilterSelected = true,
			topResultAvailable = true,
			resultGroupAvailable = true,
			resultCount = 1,
		) shouldBe SearchBrowseDestination.Filter
		firstSearchBrowseDestination(
			query = "alien",
			blankBrowseTargetAvailable = true,
			filterCount = 1,
			allFilterSelected = true,
			topResultAvailable = true,
			resultGroupAvailable = true,
			resultCount = 1,
		) shouldBe SearchBrowseDestination.TopResult
	}

	test("pending nonblank browse resolution cannot select hidden blank content") {
		resolvePendingSearchBrowseNavigation(
			pending = true,
			boundaryState = SearchInputBoundaryState(pendingBrowseFocus = true),
			query = "alien",
			blankBrowseTargetAvailable = true,
			filterCount = 1,
			allFilterSelected = true,
			topResultAvailable = true,
			resultGroupAvailable = true,
			resultCount = 1,
		) shouldBe PendingSearchBrowseResolution(
			boundaryState = SearchInputBoundaryState(),
			pending = false,
			destination = SearchBrowseDestination.TopResult,
		)
	}
})
