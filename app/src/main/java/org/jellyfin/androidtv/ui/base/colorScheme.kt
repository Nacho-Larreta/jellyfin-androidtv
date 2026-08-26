package org.jellyfin.androidtv.ui.base

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import org.jellyfin.design.Tokens

fun colorScheme(): ColorScheme = ColorScheme(
	background = Tokens.SemanticColor.surfaceCanvas,
	onBackground = Tokens.SemanticColor.contentPrimary,
	button = Tokens.SemanticColor.actionSecondary.container,
	onButton = Tokens.SemanticColor.actionSecondary.content,
	buttonFocused = Tokens.SemanticColor.legacyFocus.container,
	onButtonFocused = Tokens.SemanticColor.legacyFocus.content,
	buttonDisabled = Tokens.SemanticColor.actionDisabled.container,
	onButtonDisabled = Tokens.SemanticColor.actionDisabled.content,
	buttonActive = Tokens.SemanticColor.selected.container,
	onButtonActive = Tokens.SemanticColor.selected.content,
	input = Tokens.SemanticColor.input.container,
	onInput = Tokens.SemanticColor.input.content,
	inputFocused = Tokens.SemanticColor.inputFocused.container,
	onInputFocused = Tokens.SemanticColor.inputFocused.content,
	rangeControlBackground = Tokens.SemanticColor.progressTrack,
	rangeControlFill = Tokens.SemanticColor.progressPlayed,
	rangeControlKnob = Tokens.SemanticColor.contentPrimary,
	seekbarBuffer = Tokens.SemanticColor.progressBuffered,
	recording = Tokens.SemanticColor.error.container,
	onRecording = Tokens.SemanticColor.error.content,
	badge = Tokens.SemanticColor.informative.container,
	onBadge = Tokens.SemanticColor.informative.content,
	listHeader = Tokens.SemanticColor.contentPrimary,
	listOverline = Tokens.SemanticColor.contentDisabled,
	listHeadline = Tokens.SemanticColor.contentPrimary,
	listCaption = Tokens.SemanticColor.contentSecondary,
	listButton = Color.Transparent,
	listButtonFocused = Tokens.SemanticColor.interactionFocusLayer,
	focusIndicator = Tokens.SemanticColor.focusIndicator,
	focusSeparator = Tokens.SemanticColor.focusSeparator,
	surface = Tokens.SemanticColor.surfaceRaised,
	scrim = Tokens.SemanticColor.overlayScrim,
)

@Immutable
data class ColorScheme(
	val background: Color,
	val onBackground: Color,

	val button: Color,
	val onButton: Color,
	val buttonFocused: Color,
	val onButtonFocused: Color,
	val buttonDisabled: Color,
	val onButtonDisabled: Color,
	val buttonActive: Color,
	val onButtonActive: Color,

	val input: Color,
	val onInput: Color,
	val inputFocused: Color,
	val onInputFocused: Color,

	val rangeControlBackground: Color,
	val rangeControlFill: Color,
	val rangeControlKnob: Color,
	val seekbarBuffer: Color,

	val recording: Color,
	val onRecording: Color,

	val badge: Color,
	val onBadge: Color,

	val listHeader: Color,
	val listOverline: Color,
	val listHeadline: Color,
	val listCaption: Color,
	val listButton: Color,
	val listButtonFocused: Color,
	val focusIndicator: Color,
	val focusSeparator: Color,

	val surface: Color,
	val scrim: Color,
)

val LocalColorScheme = staticCompositionLocalOf { colorScheme() }
