package org.jellyfin.androidtv.screensaver

import android.app.Application
import android.os.Build
import androidx.preference.PreferenceManager
import org.jellyfin.androidtv.preference.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class ScreensaverPreferenceTests {
	@Test
	fun `malformed SharedPreferences storage is read fail-closed without a type crash`() {
		val application: Application = RuntimeEnvironment.getApplication()
		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
		sharedPreferences.edit()
			.clear()
			.putFloat(UserPreferences.screensaverAgeRatingMax.key, 5.5f)
			.commit()

		assertEquals(0, UserPreferences(application).readScreensaverAgeRatingMax())
	}
}
