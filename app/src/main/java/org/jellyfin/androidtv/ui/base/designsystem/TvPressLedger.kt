package org.jellyfin.androidtv.ui.base.designsystem

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

internal enum class TvPressPhase {
	Down,
	Up,
}

internal data class TvPressEvent(
	val key: Long,
	val phase: TvPressPhase,
	val repeatCount: Int = 0,
)

internal class TvPressLedger(
	private val activate: () -> Unit,
) {
	private var activeKey: Long? = null

	fun route(event: TvPressEvent): Boolean = when (event.phase) {
		TvPressPhase.Down -> routeDown(event)
		TvPressPhase.Up -> routeUp(event)
	}

	fun cancel() {
		activeKey = null
	}

	private fun routeDown(event: TvPressEvent): Boolean {
		if (activeKey == event.key) return true
		if (activeKey != null || event.repeatCount > 0) return true

		activeKey = event.key
		return true
	}

	private fun routeUp(event: TvPressEvent): Boolean {
		if (activeKey != event.key) return false

		activeKey = null
		activate()
		return true
	}
}

internal fun KeyEvent.toTvPressEvent(): TvPressEvent? {
	val activationKey = when (key) {
		Key.DirectionCenter,
		Key.Enter,
		Key.NumPadEnter,
		Key.Spacebar -> key.keyCode
		else -> return null
	}

	return when (type) {
		KeyEventType.KeyDown -> TvPressEvent(
			key = activationKey,
			phase = TvPressPhase.Down,
			repeatCount = nativeKeyEvent.repeatCount,
		)
		KeyEventType.KeyUp -> TvPressEvent(
			key = activationKey,
			phase = TvPressPhase.Up,
		)
		else -> null
	}
}

internal fun KeyEvent.toTvEscapePressEvent(): TvPressEvent? {
	if (key != Key.Escape) return null
	return when (type) {
		KeyEventType.KeyDown -> TvPressEvent(
			key = key.keyCode,
			phase = TvPressPhase.Down,
			repeatCount = nativeKeyEvent.repeatCount,
		)
		KeyEventType.KeyUp -> TvPressEvent(key = key.keyCode, phase = TvPressPhase.Up)
		else -> null
	}
}
