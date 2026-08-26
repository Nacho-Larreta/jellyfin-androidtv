package org.jellyfin.design.token

import androidx.compose.ui.graphics.Color

data class SemanticColorPair(
	val container: Color,
	val content: Color,
)

object SemanticColorTokens {
	val surfaceCanvas = ColorTokens.colorGrey975
	val surfaceRaised = ColorTokens.colorBluegrey900
	val surfaceOverlay = ColorTokens.colorGrey950
	val overlayScrim = ColorTokens.colorBlack.copy(alpha = 0.72f)
	val overlayProtection = ColorTokens.colorBlack.copy(alpha = 0.80f)

	val contentPrimary = ColorTokens.colorBluegrey25
	val contentSecondary = ColorTokens.colorGrey200
	val contentDisabled = ColorTokens.colorGrey500

	val borderSubtle = ColorTokens.colorBluegrey700
	val borderStrong = ColorTokens.colorBluegrey200

	val brandAccent = ColorTokens.colorRed500
	val focusIndicator = ColorTokens.colorWhite
	val focusSeparator = ColorTokens.colorBlack

	val informative = SemanticColorPair(
		container = ColorTokens.colorCyan500,
		content = ColorTokens.colorBlack,
	)

	val actionPrimary = SemanticColorPair(
		container = ColorTokens.colorGrey50,
		content = ColorTokens.colorBluegrey950,
	)
	val actionSecondary = SemanticColorPair(
		container = ColorTokens.colorBluegrey800,
		content = ColorTokens.colorBluegrey25,
	)
	val actionTertiary = SemanticColorPair(
		container = Color.Transparent,
		content = ColorTokens.colorBluegrey25,
	)
	val actionDestructive = SemanticColorPair(
		container = ColorTokens.colorRed700,
		content = ColorTokens.colorRed25,
	)
	val actionDisabled = SemanticColorPair(
		container = ColorTokens.colorBluegrey900,
		content = ColorTokens.colorGrey400,
	)
	val legacyFocus = SemanticColorPair(
		container = focusIndicator,
		content = focusSeparator,
	)

	val selected = SemanticColorPair(
		container = ColorTokens.colorCyan700,
		content = ColorTokens.colorCyan25,
	)
	val success = SemanticColorPair(
		container = ColorTokens.colorGreen700,
		content = ColorTokens.colorGreen25,
	)
	val warning = SemanticColorPair(
		container = ColorTokens.colorYellow400,
		content = ColorTokens.colorYellow975,
	)
	val error = SemanticColorPair(
		container = ColorTokens.colorRed700,
		content = ColorTokens.colorRed25,
	)

	val interactionHoverLayer = ColorTokens.colorWhite.copy(alpha = 0.08f)
	val interactionFocusLayer = ColorTokens.colorWhite.copy(alpha = 0.12f)
	val interactionPressedLayer = ColorTokens.colorBlack.copy(alpha = 0.12f)

	val progressTrack = ColorTokens.colorBluegrey700
	val progressBuffered = ColorTokens.colorBluegrey300
	val progressPlayed = ColorTokens.colorCyan500

	val input = SemanticColorPair(
		container = ColorTokens.colorBluegrey800,
		content = ColorTokens.colorBluegrey25,
	)
	val inputFocused = actionPrimary
}
