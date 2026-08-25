package org.jellyfin.androidtv.ui.search

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly

class SearchFocusRestorationTests : FunSpec({
	test("input exit waits for the committed focus tree before reclaiming focus") {
		val steps = mutableListOf<String>()

		val restored = restoreSearchFocusAfterInputExit(
			clearFocus = { steps += "clear" },
			awaitFocusTreeCommit = { steps += "frame" },
			reclaimFocus = {
				steps += "reclaim"
				true
			},
		)

		restored.shouldBeTrue()
		steps.shouldContainExactly("clear", "frame", "reclaim")
	}

	test("input exit reports a failed reclaim without skipping the frame boundary") {
		val steps = mutableListOf<String>()

		val restored = restoreSearchFocusAfterInputExit(
			clearFocus = { steps += "clear" },
			awaitFocusTreeCommit = { steps += "frame" },
			reclaimFocus = {
				steps += "reclaim"
				false
			},
		)

		restored.shouldBeFalse()
		steps.shouldContainExactly("clear", "frame", "reclaim")
	}
})
