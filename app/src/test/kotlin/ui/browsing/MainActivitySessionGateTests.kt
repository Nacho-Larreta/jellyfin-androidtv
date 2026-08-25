package org.jellyfin.androidtv.ui.browsing

import android.app.Application
import android.os.Build
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.androidtv.auth.repository.Session
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.SessionRepositoryState
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.InteractionTrackerViewModel
import org.jellyfin.androidtv.ui.startup.StartupActivity
import org.jellyfin.sdk.model.api.UserDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class MainActivitySessionGateTests {
	private val probe = MainActivityRuntimeProbe()

	@Before
	fun setUp() {
		stopKoin()
		val application: Application = RuntimeEnvironment.getApplication()
		val session = Session(
			userId = UUID.randomUUID(),
			serverId = UUID.randomUUID(),
			accessToken = "access-token",
		)
		val sessionRepository = mockk<SessionRepository> {
			every { currentSession } returns MutableStateFlow(session)
			every { state } returns MutableStateFlow(SessionRepositoryState.INVALIDATING_SESSION)
		}
		val userRepository = mockk<UserRepository> {
			every { currentUser } returns MutableStateFlow(mockk<UserDto>(relaxed = true))
		}

		startKoin {
			androidContext(application)
			modules(module {
				single { UserPreferences(application) }
				single<SessionRepository> { sessionRepository }
				single<UserRepository> { userRepository }
				viewModel<InteractionTrackerViewModel> { probe.resolveAuthenticatedRuntime() }
			})
		}
	}

	@After
	fun tearDown() {
		stopKoin()
	}

	@Test
	fun `invalidating session cannot enter the authenticated activity`() {
		Robolectric.buildActivity(MainActivity::class.java).use { controller ->
			val activity = controller.create().get()

			assertTrue(activity.isFinishing)
			assertEquals(
				StartupActivity::class.java.name,
				shadowOf(activity).nextStartedActivity.component?.className,
			)
			assertEquals(0, probe.authenticatedRuntimeResolutions)
		}
	}
}

private class MainActivityRuntimeProbe {
	var authenticatedRuntimeResolutions = 0
		private set

	fun resolveAuthenticatedRuntime(): Nothing {
		authenticatedRuntimeResolutions++
		throw AssertionError("Invalidating session entered the authenticated runtime")
	}
}
