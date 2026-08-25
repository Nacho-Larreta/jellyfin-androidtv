package org.jellyfin.androidtv

import android.content.Context
import androidx.startup.Initializer
import org.jellyfin.androidtv.logging.SanitizingDebugTree
import timber.log.Timber

class LogInitializer : Initializer<Unit> {
	override fun create(context: Context) {
		// Enable improved logging for leaking resources
		// https://wh0.github.io/2020/08/12/closeguard.html
		if (BuildConfig.DEBUG) {
			try {
				Class.forName("dalvik.system.CloseGuard")
					.getMethod("setEnabled", Boolean::class.javaPrimitiveType)
					.invoke(null, true)
			} catch (e: ReflectiveOperationException) {
				throw IllegalStateException("Unable to enable CloseGuard", e)
			}
		}

		if (BuildConfig.DEBUG) {
			Timber.plant(SanitizingDebugTree())
			Timber.i("Sanitizing debug tree planted")
		}
	}

	override fun dependencies() = emptyList<Class<out Initializer<*>>>()
}
