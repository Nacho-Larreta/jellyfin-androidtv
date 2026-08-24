package org.jellyfin.androidtv.ui.input

internal enum class RemoteKeyPhase {
	DOWN,
	UP,
}

internal data class RemoteKeyStroke(
	val keyCode: Int,
	val phase: RemoteKeyPhase,
	val repeatCount: Int = 0,
)

/**
 * Routes the first down event of a physical key press and keeps its ownership
 * stable until the matching up event.
 */
internal class RemoteKeyPressRouter(
	private val routeInitialDown: (keyCode: Int) -> Boolean,
) {
	private val activePresses = mutableMapOf<Int, Boolean>()

	fun reset() {
		activePresses.clear()
	}

	fun route(event: RemoteKeyStroke): Boolean = when (event.phase) {
		RemoteKeyPhase.DOWN -> routeDown(event)
		RemoteKeyPhase.UP -> activePresses.remove(event.keyCode) ?: false
	}

	private fun routeDown(event: RemoteKeyStroke): Boolean {
		activePresses[event.keyCode]?.let { return it }
		if (event.repeatCount > 0) return false

		return routeInitialDown(event.keyCode).also { consumed ->
			activePresses[event.keyCode] = consumed
		}
	}
}
