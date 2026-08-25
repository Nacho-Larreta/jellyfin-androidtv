package org.jellyfin.playback.media3.exoplayer

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.util.EventLogger
import org.jellyfin.androidtv.logging.SensitiveLogSanitizer
import timber.log.Timber

@OptIn(UnstableApi::class)
class SanitizingEventLogger : EventLogger() {
	override fun logd(message: String) {
		Timber.d(SensitiveLogSanitizer.sanitize(message))
	}

	override fun loge(message: String) {
		Timber.e(SensitiveLogSanitizer.sanitize(message))
	}
}
