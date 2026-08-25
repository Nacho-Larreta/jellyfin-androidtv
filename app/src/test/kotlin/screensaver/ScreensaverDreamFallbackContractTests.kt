package org.jellyfin.androidtv.screensaver

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import java.nio.file.Files
import java.nio.file.Path

class ScreensaverDreamFallbackContractTests : FunSpec({
	test("LibraryShowcase and NowPlaying cross the same policy boundary") {
		val source = dreamViewModelSource()

		source.contains("selectEligibleNowPlayingItem(visibleEntry?.baseItem, policy)").shouldBeTrue()
		source.contains("selectEligibleLibraryItems(items, policy)").shouldBeTrue()
	}

	test("all failed batches use the no-items delay and retain the same policy") {
		val source = dreamViewModelSource()

		source.contains("else getRandomLibraryShowcaseItems(\n\t\t\t\t\tpolicy = policy").shouldBeTrue()
		source.contains("if (eligibleItems.isEmpty())").shouldBeTrue()
		source.contains("if (!emittedContent)").shouldBeTrue()
		source.contains("delay(noItemsDelay)").shouldBeTrue()
		source.contains("errorDelay").shouldBeFalse()
	}

	test("policy replay expires so lowering the ceiling cannot reuse a stale policy") {
		dreamViewModelSource().contains("SharingStarted.WhileSubscribed(replayExpirationMillis = 0)").shouldBeTrue()
	}
})

private fun dreamViewModelSource(): String = listOf(
	Path.of("app/src/main/java/org/jellyfin/androidtv/integration/dream/DreamViewModel.kt"),
	Path.of("src/main/java/org/jellyfin/androidtv/integration/dream/DreamViewModel.kt"),
).first(Files::exists).toFile().readText()
