package org.jellyfin.androidtv.util.coil

import android.util.Log
import coil3.util.Logger
import org.jellyfin.androidtv.logging.SensitiveLogSanitizer
import timber.log.Timber

class CoilTimberLogger(
	override var minLevel: Logger.Level = Logger.Level.Debug,
) : Logger {
	override fun log(tag: String, level: Logger.Level, message: String?, throwable: Throwable?) {
		val priority = when (level) {
			Logger.Level.Verbose -> Log.VERBOSE
			Logger.Level.Debug -> Log.DEBUG
			Logger.Level.Info -> Log.INFO
			Logger.Level.Warn -> Log.WARN
			Logger.Level.Error -> Log.ERROR
		}

		val safeTag = SensitiveLogSanitizer.sanitize("CoilTimberLogger.$tag")
		val safeMessage = SensitiveLogSanitizer.sanitize(message, throwable)
		Timber.tag(safeTag).log(priority, safeMessage)
	}
}
