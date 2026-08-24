package org.jellyfin.androidtv.ui.search

import org.jellyfin.androidtv.ui.input.RemoteKeyPressRouter
import org.jellyfin.androidtv.ui.input.RemoteKeyStroke

internal class SearchPreImeKeyRouter(
	private val backKeyCode: Int,
	handleBack: (keyCode: Int) -> Boolean,
) {
	private val pressRouter = RemoteKeyPressRouter { keyCode ->
		keyCode == backKeyCode && handleBack(keyCode)
	}

	fun route(event: RemoteKeyStroke): Boolean = pressRouter.route(event)

	fun reset() = pressRouter.reset()
}
