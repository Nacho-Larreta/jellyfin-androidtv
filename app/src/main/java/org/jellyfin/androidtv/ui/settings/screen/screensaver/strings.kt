package org.jellyfin.androidtv.ui.settings.screen.screensaver

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.screensaver.ScreensaverContentPolicy
import org.jellyfin.androidtv.util.getQuantityString
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Composable
@Stable
fun getScreensaverAgeRatingOptions() = buildList {
	ScreensaverContentPolicy.supportedAgeCeilings.forEach { age ->
		val label = when (age) {
			ScreensaverContentPolicy.DEFAULT_AGE_CEILING -> stringResource(R.string.pref_screensaver_ageratingmax_zero)
			ScreensaverContentPolicy.UNLIMITED -> stringResource(R.string.pref_screensaver_ageratingmax_unlimited)
			else -> stringResource(R.string.pref_screensaver_ageratingmax_entry, age)
		}
		add(age to label)
	}
}

@Composable
@Stable
fun getScreensaverTimeoutOptions() = buildList {
	val context = LocalContext.current

	add(30.seconds to context.getQuantityString(R.plurals.seconds, 30))
	add(1.minutes to context.getQuantityString(R.plurals.minutes, 1))
	add(2.5.minutes to context.getQuantityString(R.plurals.minutes, 2.5))
	add(5.minutes to context.getQuantityString(R.plurals.minutes, 5))
	add(10.minutes to context.getQuantityString(R.plurals.minutes, 10))
	add(15.minutes to context.getQuantityString(R.plurals.minutes, 15))
	add(30.minutes to context.getQuantityString(R.plurals.minutes, 30))
}
