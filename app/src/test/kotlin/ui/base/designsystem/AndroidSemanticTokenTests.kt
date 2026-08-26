package org.jellyfin.androidtv.ui.base.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jellyfin.design.AndroidDesignMapping
import org.jellyfin.design.Tokens
import org.jellyfin.design.token.SemanticColorPair

class AndroidSemanticTokenTests : FunSpec({
	test("the Android mapping declares the approved contract and isolated component inventory") {
		AndroidDesignMapping.ContractVersion shouldBe "2.0.0"
		AndroidDesignMapping.Platform shouldBe "android-tv"
		AndroidDesignMapping.Theme shouldBe "dark"
		AndroidDesignMapping.ImplementedComponentIds.shouldContainExactly(
			"atom.action",
			"atom.avatar",
			"atom.chip",
			"atom.focus-indicator",
			"atom.progress",
			"atom.protection-scrim",
			"molecule.media-card",
			"molecule.modal-layer",
		)
	}

	test("every opaque filled role has a WCAG AA container and ink receipt") {
		val filledRoles = mapOf(
			"informative" to Tokens.SemanticColor.informative,
			"action.primary" to Tokens.SemanticColor.actionPrimary,
			"action.secondary" to Tokens.SemanticColor.actionSecondary,
			"action.destructive" to Tokens.SemanticColor.actionDestructive,
			"action.disabled" to Tokens.SemanticColor.actionDisabled,
			"legacy.focus" to Tokens.SemanticColor.legacyFocus,
			"state.selected" to Tokens.SemanticColor.selected,
			"state.success" to Tokens.SemanticColor.success,
			"state.warning" to Tokens.SemanticColor.warning,
			"state.error" to Tokens.SemanticColor.error,
			"input" to Tokens.SemanticColor.input,
			"input.focused" to Tokens.SemanticColor.inputFocused,
		)

		filledRoles.values.forEach { pair ->
			contrastRatio(pair).shouldBeGreaterThanOrEqual(4.5)
		}
	}

	test("focused input replaces the rejected low contrast pair") {
		contrastRatio(Tokens.SemanticColor.inputFocused).shouldBeGreaterThanOrEqual(4.5)
		Tokens.SemanticColor.inputFocused shouldBe Tokens.SemanticColor.actionPrimary
	}

	test("the two-channel focus boundary is maximally distinct") {
		contrastRatio(
			SemanticColorPair(
				container = Tokens.SemanticColor.focusIndicator,
				content = Tokens.SemanticColor.focusSeparator,
			)
		).shouldBeGreaterThanOrEqual(3.0)
	}

	test("typography roles are complete TextStyles and motion uses typed easing") {
		val roles = listOf(
			Tokens.SemanticTypography.displayLarge,
			Tokens.SemanticTypography.displayMedium,
			Tokens.SemanticTypography.titleLarge,
			Tokens.SemanticTypography.titleMedium,
			Tokens.SemanticTypography.bodyLarge,
			Tokens.SemanticTypography.bodyMedium,
			Tokens.SemanticTypography.labelLarge,
			Tokens.SemanticTypography.labelMedium,
			Tokens.SemanticTypography.metadata,
			Tokens.SemanticTypography.numericTimeline,
		)
		roles.forEach { style ->
			style.fontFamily.shouldNotBeNull()
			style.fontWeight.shouldNotBeNull()
			style.fontSize.value.toInt().shouldBeGreaterThan(0)
			style.lineHeight.value.toInt().shouldBeGreaterThan(0)
			(style.letterSpacing != TextUnit.Unspecified) shouldBe true
		}
		Tokens.SemanticTypography.numericTimeline.fontFamily shouldBe FontFamily.Monospace
		Tokens.SemanticMotion.standardEasing.shouldBeInstanceOf<CubicBezierEasing>()
		Tokens.SemanticMotion.enterEasing.shouldBeInstanceOf<CubicBezierEasing>()
		Tokens.SemanticMotion.exitEasing.shouldBeInstanceOf<CubicBezierEasing>()
	}

	test("component dimensions stay outside system spacing and full shape is percentage based") {
		Tokens.SemanticSpace.space0.value shouldBe 0f
		Tokens.SemanticSpace.space9.value shouldBe 80f
		Tokens.SemanticComponent.tvMinimumTarget.value shouldBe 48f
		Tokens.SemanticComponent.mediaCardStandardWidth.value shouldBe 240f
		Tokens.SemanticShape.FullPercent shouldBe 50
	}
})

internal fun contrastRatio(pair: SemanticColorPair): Double {
	val lighter = maxOf(relativeLuminance(pair.container), relativeLuminance(pair.content))
	val darker = minOf(relativeLuminance(pair.container), relativeLuminance(pair.content))
	return (lighter + 0.05) / (darker + 0.05)
}

private fun relativeLuminance(color: Color): Double = sequenceOf(color.red, color.green, color.blue)
	.map { component ->
		val value = component.toDouble()
		if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
	}
	.toList()
	.let { (red, green, blue) -> 0.2126 * red + 0.7152 * green + 0.0722 * blue }
