package org.jellyfin.androidtv.ui.base.designsystem

import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner

internal class TvInputCancellation(
	private val cancel: () -> Unit,
) : LifecycleEventObserver, ViewTreeObserver.OnWindowFocusChangeListener {
	override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
		when (event) {
			Lifecycle.Event.ON_PAUSE,
			Lifecycle.Event.ON_STOP,
			Lifecycle.Event.ON_DESTROY -> cancel()
			else -> Unit
		}
	}

	override fun onWindowFocusChanged(hasFocus: Boolean) {
		if (!hasFocus) cancel()
	}
}

@Composable
internal fun TvInputCancellationEffect(onCancel: () -> Unit) {
	val latestCancel by rememberUpdatedState(onCancel)
	val view = LocalView.current

	DisposableEffect(view) {
		val observer = TvInputCancellation { latestCancel() }
		val lifecycle = view.findViewTreeLifecycleOwner()?.lifecycle
		val viewTreeObserver = view.viewTreeObserver
		lifecycle?.addObserver(observer)
		viewTreeObserver.addOnWindowFocusChangeListener(observer)

		onDispose {
			lifecycle?.removeObserver(observer)
			removeWindowFocusObserver(view, viewTreeObserver, observer)
		}
	}
}

private fun removeWindowFocusObserver(
	view: View,
	registeredObserver: ViewTreeObserver,
	listener: ViewTreeObserver.OnWindowFocusChangeListener,
) {
	val activeObserver = if (registeredObserver.isAlive) registeredObserver else view.viewTreeObserver
	if (activeObserver.isAlive) activeObserver.removeOnWindowFocusChangeListener(listener)
}
