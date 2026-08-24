package org.jellyfin.androidtv.ui.browsing

import androidx.fragment.app.Fragment

interface PreDispatchKeyEventHandler {
	fun onPreDispatchKeyDown(keyCode: Int): Boolean
}

fun Fragment.dispatchPreKeyDown(keyCode: Int): Boolean {
	var result = childFragmentManager.fragments.asReversed().any { fragment ->
		fragment.isVisible && fragment.dispatchPreKeyDown(keyCode)
	}
	if (!result && isVisible && this is PreDispatchKeyEventHandler) result = onPreDispatchKeyDown(keyCode)
	return result
}
