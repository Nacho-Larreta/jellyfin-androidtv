package org.jellyfin.androidtv.ui.input

import android.view.KeyEvent

internal fun KeyEvent.toRemoteKeyStrokeOrNull(): RemoteKeyStroke? = when (action) {
	KeyEvent.ACTION_DOWN -> RemoteKeyStroke(keyCode, RemoteKeyPhase.DOWN, repeatCount)
	KeyEvent.ACTION_UP -> RemoteKeyStroke(keyCode, RemoteKeyPhase.UP)
	else -> null
}
