package org.jellyfin.androidtv.ui.base.designsystem

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberSystemMotionPreference(): TvMotionPreference {
	val context = LocalContext.current
	val resolver = context.contentResolver
	val animationScaleUri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
	var preference by remember(resolver) { mutableStateOf(resolver.readMotionPreference()) }

	DisposableEffect(resolver, animationScaleUri) {
		val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
			override fun onChange(selfChange: Boolean) {
				preference = resolver.readMotionPreference()
			}
		}
		resolver.registerContentObserver(animationScaleUri, false, observer)
		onDispose { resolver.unregisterContentObserver(observer) }
	}

	return preference
}

private fun android.content.ContentResolver.readMotionPreference(): TvMotionPreference {
	val animationScale = Settings.Global.getFloat(
		this,
		Settings.Global.ANIMATOR_DURATION_SCALE,
		1f,
	)
	return motionPreferenceForScale(animationScale)
}

internal fun motionPreferenceForScale(animationScale: Float): TvMotionPreference =
	if (animationScale == 0f) TvMotionPreference.Reduced else TvMotionPreference.Default
