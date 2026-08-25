package org.jellyfin.androidtv.ui.startup

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Looper
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import coil3.ImageLoader
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.model.ServerAdditionState
import org.jellyfin.androidtv.auth.repository.AuthenticationRepository
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.ServerUserRepository
import org.jellyfin.androidtv.auth.repository.Session
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.SessionRepositoryState
import org.jellyfin.androidtv.auth.store.AuthenticationPreferences
import org.jellyfin.androidtv.data.model.AppNotification
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.startup.fragment.SelectServerFragment
import org.jellyfin.androidtv.ui.startup.fragment.StartupToolbarFragment
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
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
class StartupActivityColdStartTests {
	private val probe = ColdStartProbe()

	@Before
	fun setUp() {
		stopKoin()
		val application: Application = RuntimeEnvironment.getApplication()
		val userPreferences = UserPreferences(application)

		startKoin {
			androidContext(application)
			modules(module {
				single { userPreferences }
				single { createBackgroundService(application, userPreferences) }
				single<SessionRepository> { SessionlessSessionRepository() }
				single<NotificationsRepository> { EmptyNotificationsRepository() }
				single<MediaManager> { probe.resolvePlayback() }
				factory { NoSessionStartupRouter(probe::lastServerId) }
				viewModel {
					StartupViewModel(
						serverRepository = EmptyServerRepository(),
						serverUserRepository = mockk<ServerUserRepository>(relaxed = true),
						authenticationRepository = mockk<AuthenticationRepository>(relaxed = true),
						authenticationPreferences = AuthenticationPreferences(application),
					)
				}
			})
		}
	}

	@After
	fun tearDown() {
		stopKoin()
	}

	@Test
	fun `sessionless cold start routes without resolving playback`() {
		val application: Application = RuntimeEnvironment.getApplication()
		shadowOf(application).grantPermissions(
			Manifest.permission.INTERNET,
			Manifest.permission.ACCESS_NETWORK_STATE,
		)
		val intent = Intent(application, StartupActivity::class.java)
			.putExtra(StartupActivity.EXTRA_HIDE_SPLASH, true)

		Robolectric.buildActivity(StartupActivity::class.java, intent).use { controller ->
			controller.create()
			controller.get().supportFragmentManager.fragmentFactory = StartupFragmentFactory()
			controller.start()
			shadowOf(Looper.getMainLooper()).idle()
			controller.resume().visible()
			repeat(3) {
				shadowOf(Looper.getMainLooper()).idle()
				controller.get().supportFragmentManager.executePendingTransactions()
			}

			assertEquals(1, probe.lastServerRequests)
			assertEquals(0, probe.playbackResolutions)
			assertTrue(
				controller.get().supportFragmentManager.fragments.any { it is SelectServerFragment }
			)
		}
	}

	private fun createBackgroundService(
		application: Application,
		userPreferences: UserPreferences,
	) = BackgroundService(
		context = application,
		jellyfin = mockk<Jellyfin>(relaxed = true),
		api = mockk<ApiClient>(relaxed = true),
		userPreferences = userPreferences,
		imageLoader = mockk<ImageLoader>(relaxed = true),
	)
}

private class ColdStartProbe {
	var lastServerRequests = 0
		private set
	var playbackResolutions = 0
		private set

	suspend fun lastServerId(): UUID? {
		lastServerRequests++
		return null
	}

	fun resolvePlayback(): Nothing {
		playbackResolutions++
		throw AssertionError("Sessionless startup resolved MediaManager")
	}
}

private class StartupFragmentFactory : FragmentFactory() {
	override fun instantiate(classLoader: ClassLoader, className: String): Fragment =
		if (className == StartupToolbarFragment::class.java.name) Fragment()
		else super.instantiate(classLoader, className)
}

private class SessionlessSessionRepository : SessionRepository {
	override val currentSession: StateFlow<Session?> = MutableStateFlow(null)
	override val state: StateFlow<SessionRepositoryState> = MutableStateFlow(SessionRepositoryState.READY)

	override suspend fun restoreSession(destroyOnly: Boolean) = unexpectedCall()
	override suspend fun switchCurrentSession(serverId: UUID, userId: UUID) = unexpectedCall()
	override suspend fun switchCurrentSession(session: Session) = unexpectedCall()
	override suspend fun prepareForProfileSelection() = unexpectedCall()
	override suspend fun destroyCurrentSession() = unexpectedCall()
}

private class EmptyServerRepository : ServerRepository {
	override val storedServers: StateFlow<List<Server>> = MutableStateFlow(emptyList())
	override val discoveredServers: StateFlow<List<Server>> = MutableStateFlow(emptyList())
	override val currentServer: StateFlow<Server?> = MutableStateFlow(null)

	override suspend fun loadStoredServers() = Unit
	override suspend fun loadDiscoveryServers() = Unit
	override fun setCurrentServer(server: Server?) = unexpectedCall()
	override fun addServer(address: String): Flow<ServerAdditionState> = emptyFlow()
	override suspend fun getServer(id: UUID, eagerUpdate: Boolean) = unexpectedCall()
	override suspend fun updateServer(server: Server, force: Boolean) = unexpectedCall()
	override suspend fun deleteServer(server: UUID) = unexpectedCall()
}

private class EmptyNotificationsRepository : NotificationsRepository {
	override val notifications: StateFlow<List<AppNotification>> = MutableStateFlow(emptyList())

	override fun dismissNotification(item: AppNotification) = unexpectedCall()
	override fun addDefaultNotifications() = unexpectedCall()
	override fun updateServerNotifications(server: Server?) = unexpectedCall()
}

private fun unexpectedCall(): Nothing = error("Unexpected dependency call in sessionless cold start")
