package org.jellyfin.androidtv.ui.search

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import java.nio.file.Files
import java.nio.file.Path

class SearchSemanticFocusContractTests : FunSpec({
	test("the virtual D-pad owner is a named live semantic focus target") {
		val source = searchScreenSource()

		source.contains("private fun Modifier.searchSemanticFocusOwner").shouldBeTrue()
		source.contains("liveRegion = LiveRegionMode.Polite").shouldBeTrue()
		source.contains(".focusable()").shouldBeTrue()
		source.contains("search_a11y_input").shouldBeTrue()
	}

	test("semantic focus replaces the deprecated announcement-only bridge") {
		searchScreenSource().contains("announceForAccessibility").shouldBeFalse()
	}

	test("the Fragment host delegates focus reclaim to the current Compose owner") {
		val source = searchFragmentSource()

		source.contains("if (focusReclaimHandshake.reclaim()) return").shouldBeTrue()
		source.contains("if (focusReclaimHandshake.install(handler))").shouldBeTrue()
		source.contains("host.post(host::reclaimRemoteFocus)").shouldBeTrue()
		source.contains("requestFocusFromTouch").shouldBeFalse()
	}
})

private fun searchScreenSource(): String = listOf(
	Path.of("app/src/main/java/org/jellyfin/androidtv/ui/search/SearchScreen.kt"),
	Path.of("src/main/java/org/jellyfin/androidtv/ui/search/SearchScreen.kt"),
).first { Files.exists(it) }.toFile().readText()

private fun searchFragmentSource(): String = listOf(
	Path.of("app/src/main/java/org/jellyfin/androidtv/ui/search/SearchFragment.kt"),
	Path.of("src/main/java/org/jellyfin/androidtv/ui/search/SearchFragment.kt"),
).first { Files.exists(it) }.toFile().readText()
