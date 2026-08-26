package org.jellyfin.androidtv.ui.base.designsystem

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.ui.base.colorScheme
import org.jellyfin.design.Tokens
import org.jellyfin.design.token.SemanticColorPair

class LegacyColorSchemeTests : FunSpec({
	test("legacy focus maps the approved indicator and separator pair") {
		val scheme = colorScheme()
		val pair = SemanticColorPair(scheme.buttonFocused, scheme.onButtonFocused)

		pair shouldBe Tokens.SemanticColor.legacyFocus
		scheme.focusIndicator shouldBe Tokens.SemanticColor.focusIndicator
		scheme.focusSeparator shouldBe Tokens.SemanticColor.focusSeparator
		contrastRatio(pair).shouldBeGreaterThanOrEqual(3.0)
	}

	test("legacy disabled controls use one semantic container ink pair") {
		val scheme = colorScheme()
		val pair = SemanticColorPair(scheme.buttonDisabled, scheme.onButtonDisabled)

		pair shouldBe Tokens.SemanticColor.actionDisabled
		contrastRatio(pair).shouldBeGreaterThanOrEqual(4.5)
	}
})
