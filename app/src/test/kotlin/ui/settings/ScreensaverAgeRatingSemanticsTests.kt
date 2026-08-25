package org.jellyfin.androidtv.ui.settings

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import java.nio.file.Files
import java.nio.file.Path

class ScreensaverAgeRatingSemanticsTests : FunSpec({
	test("age choices expose one named radio group with selected state") {
		val source = ageRatingScreenSource()

		source.contains("Modifier.selectableGroup()").shouldBeTrue()
		source.contains("selected = selected").shouldBeTrue()
		source.contains("role = Role.RadioButton").shouldBeTrue()
		source.contains("contentDescription = heading").shouldBeTrue()
	}

	test("D-pad focus is visible and Back restores focus through the settings host") {
		val ageRatingSource = ageRatingScreenSource()
		val listControlSource = source("app/src/main/java/org/jellyfin/androidtv/ui/base/list/ListControl.kt")
		val settingsLayoutSource = source("app/src/main/java/org/jellyfin/androidtv/ui/settings/composable/SettingsLayout.kt")

		ageRatingSource.contains("interactionSource = interactionSource").shouldBeTrue()
		listControlSource.contains("focusedContainerColor").shouldBeTrue()
		settingsLayoutSource.contains(".focusRestorer()").shouldBeTrue()
	}

	test("the obsolete allow-unrated switch is absent from Settings") {
		val source = source("app/src/main/java/org/jellyfin/androidtv/ui/settings/screen/screensaver/SettingsScreensaverScreen.kt")

		source.contains("screensaverAgeRatingRequired").shouldBeFalse()
		source.contains("pref_screensaver_ageratingrequired_title").shouldBeFalse()
	}
})

private fun ageRatingScreenSource() =
	source("app/src/main/java/org/jellyfin/androidtv/ui/settings/screen/screensaver/SettingsScreensaverAgeRatingScreen.kt")

private fun source(path: String): String = listOf(
	Path.of(path),
	Path.of(path.removePrefix("app/")),
).first(Files::exists).toFile().readText()
