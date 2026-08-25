package org.jellyfin.androidtv.work

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.work.Configuration
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import org.jellyfin.androidtv.JellyfinApplication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class WorkManagerLazyInitializationTests {
	@Test
	fun `application provides configuration without initialization until first real use`() {
		val application = JellyfinApplication()
		val runtimeApplication = RuntimeEnvironment.getApplication()
		val baseContext = JellyfinApplicationContext(runtimeApplication, application)

		ReflectionHelpers.callInstanceMethod<Void>(
			application,
			"attach",
			ClassParameter.from(Context::class.java, baseContext),
		)

		assertFalse(WorkManager.isInitialized())

		val providerCandidate: Any = application
		assertTrue(providerCandidate is Configuration.Provider)
		val provider = providerCandidate as Configuration.Provider
		assertNotNull(provider.workManagerConfiguration)
		assertFalse(WorkManager.isInitialized())

		val workManager = WorkManager.getInstance(application)
		val operation = workManager.cancelUniqueWork("lazy-initialization-probe")

		operation.result.get(5, TimeUnit.SECONDS)
		assertTrue(WorkManager.isInitialized())
	}
}

private class JellyfinApplicationContext(
	base: Context,
	private val application: JellyfinApplication,
) : ContextWrapper(base) {
	override fun getApplicationContext(): Context = application
}
