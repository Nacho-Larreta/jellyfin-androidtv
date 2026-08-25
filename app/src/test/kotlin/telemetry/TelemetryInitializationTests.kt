package org.jellyfin.androidtv.telemetry

import android.app.Application
import android.os.Build
import org.acra.ACRA
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class TelemetryInitializationTests {
	@Test
	fun `initialization installs the ACRA uncaught exception handler on an attached application`() {
		val application: Application = RuntimeEnvironment.getApplication()

		TelemetryService.init(application)

		assertTrue(ACRA.isInitialised)
		assertSame(ACRA.errorReporter, Thread.getDefaultUncaughtExceptionHandler())
	}
}
