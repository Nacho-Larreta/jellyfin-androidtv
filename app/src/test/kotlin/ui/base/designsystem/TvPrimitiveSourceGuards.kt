package org.jellyfin.androidtv.ui.base.designsystem

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import java.nio.file.Files
import java.nio.file.Path

class TvPrimitiveSourceGuards : FunSpec({
	test("redesigned primitives consume semantic tokens and contain no suppression") {
		val source = redesignedSource()

		source.contains("Tokens.Color.").shouldBeFalse()
		source.contains("Color(0x").shouldBeFalse()
		source.contains(".dp").shouldBeFalse()
		source.contains("@Suppress").shouldBeFalse()
		source.contains("Tokens.SemanticColor").shouldBeTrue()
		source.contains("Tokens.SemanticComponent").shouldBeTrue()
	}

	test("focus is observed independently from selected state") {
		val source = redesignedSource()

		source.contains(".tvFocusOwner(spec.semantics.id, binding.focusOwners)").shouldBeTrue()
		source.contains(".onFocusChanged").shouldBeTrue()
		source.contains("selected = spec.state.selection == TvSelection.Selected").shouldBeTrue()
		source.contains("targetValue = if (runtime.focused").shouldBeTrue()
	}

	test("named roles and states are exposed by actual focusable controls") {
		val source = redesignedSource()
		val iconSource = sourcePath(
			"app/src/main/java/org/jellyfin/androidtv/ui/base/Icon.kt",
			"src/main/java/org/jellyfin/androidtv/ui/base/Icon.kt",
		).toFile().readText()

		source.contains("role = Role.Button").shouldBeTrue()
		source.contains("role = Role.Checkbox").shouldBeTrue()
		source.contains("progressBarRangeInfo = ProgressBarRangeInfo").shouldBeTrue()
		source.contains("contentDescription = accessibleName").shouldBeTrue()
		source.contains("disabled()").shouldBeTrue()
		source.contains("LocalTvContentColor provides spec.colors.content").shouldBeTrue()
		source.contains("ProvideTextStyle(spec.textStyle.copy(color = spec.colors.content))").shouldBeTrue()
		iconSource.contains("tint: Color = LocalTextStyle.current.color").shouldBeTrue()
		iconSource.contains("tint.takeUnless { it == Color.Unspecified }?.let(ColorFilter::tint)").shouldBeTrue()
		iconSource.contains("ColorFilter::tint").shouldBeTrue()
		iconSource.contains(".paint(painter, colorFilter = colorFilter").shouldBeTrue()
	}

	test("modal containment restore and nearest-layer Back remain explicit") {
		val source = redesignedSource()

		source.contains("DialogProperties(").shouldBeTrue()
		source.contains(".focusGroup()").shouldBeTrue()
		source.contains("BackHandler { environment.layers.handleBack() }").shouldBeTrue()
		source.contains("environment.focusOwners.restore(latestRestoreFocus)").shouldBeTrue()
	}

	test("reduced motion removes focus scaling instead of slowing product timing") {
		val source = redesignedSource()

		source.contains("rememberSystemMotionPreference()").shouldBeTrue()
		source.contains("focusAnimationSpec(reducedMotion)").shouldBeTrue()
		source.contains("focused && !reducedMotion").shouldBeTrue()
		source.contains("Tokens.SemanticMotion.standardEasing").shouldBeTrue()
	}
})

private fun redesignedSource(): String = listOf(
	"TvFocusableSurface.kt",
	"TvFocusBoundary.kt",
	"TvControlPrimitives.kt",
	"TvProgress.kt",
	"TvProtectionScrim.kt",
	"TvOverlay.kt",
).joinToString(separator = "\n") { fileName ->
	sourcePath(
		"app/src/main/java/org/jellyfin/androidtv/ui/base/designsystem/$fileName",
		"src/main/java/org/jellyfin/androidtv/ui/base/designsystem/$fileName",
	).toFile().readText()
}

private fun sourcePath(vararg candidates: String): Path = candidates
	.map(Path::of)
	.first(Files::exists)
