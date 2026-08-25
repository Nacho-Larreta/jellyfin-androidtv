package org.jellyfin.androidtv.ui.playback

enum class PlayerRemoteDirection {
	Up,
	Down,
}

data class PlayerRemoteInputState @JvmOverloads constructor(
	val liveTv: Boolean = false,
	val popupVisible: Boolean = false,
	val guideVisible: Boolean = false,
	val dialogVisible: Boolean = false,
	val controlsVisible: Boolean = false,
	val progressFocused: Boolean = false,
)

enum class PlayerRemoteDirectionalAction {
	RevealControlsAndFocus,
	PassThrough,
}

enum class PlayerRemoteActivationAction {
	TogglePlayback,
	PassThrough,
}

enum class PlayerProgressActivationEffect {
	ConsumeAndKeepTimelineFocus,
}

object PlayerRemoteInputPolicy {
	@JvmStatic
	fun directionalAction(
		direction: PlayerRemoteDirection,
		state: PlayerRemoteInputState,
	): PlayerRemoteDirectionalAction = when (direction) {
		PlayerRemoteDirection.Up,
		PlayerRemoteDirection.Down -> if (
			!state.liveTv &&
			!state.popupVisible &&
			!state.guideVisible &&
			!state.dialogVisible &&
			!state.controlsVisible
		) {
			PlayerRemoteDirectionalAction.RevealControlsAndFocus
		} else {
			PlayerRemoteDirectionalAction.PassThrough
		}
	}

	@JvmStatic
	fun activationAction(state: PlayerRemoteInputState): PlayerRemoteActivationAction =
		if (state.controlsVisible || state.progressFocused) {
			PlayerRemoteActivationAction.PassThrough
		} else {
			PlayerRemoteActivationAction.TogglePlayback
		}

	@JvmStatic
	fun progressActivationEffect(): PlayerProgressActivationEffect =
		PlayerProgressActivationEffect.ConsumeAndKeepTimelineFocus
}
