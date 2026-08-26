package org.jellyfin.androidtv.di

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jellyfin.androidtv.auth.session.SessionSwitchApi
import org.jellyfin.androidtv.auth.session.SessionSwitchCoordinator
import org.jellyfin.androidtv.auth.session.SessionSwitchQuiescePort
import org.jellyfin.androidtv.auth.session.SessionSwitchRuntimePort
import org.jellyfin.androidtv.auth.session.SessionSwitchStore
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class AuthSessionModuleTests : FunSpec({
	test("concurrent Koin resolutions share one coordinator and composite quiesce port") {
		val application = koinApplication {
			allowOverride(true)
			modules(
				authModule,
				module {
					single<SessionSwitchApi> { mockk() }
					single<SessionSwitchStore> { mockk() }
					single<SessionSwitchRuntimePort> { mockk() }
				},
			)
		}

		try {
			val coordinators = coroutineScope {
				List(16) {
					async(Dispatchers.Default) { application.koin.get<SessionSwitchCoordinator>() }
				}.awaitAll()
			}
			val quiescePorts = coroutineScope {
				List(16) {
					async(Dispatchers.Default) { application.koin.get<SessionSwitchQuiescePort>() }
				}.awaitAll()
			}

			coordinators.all { it === coordinators.first() } shouldBe true
			quiescePorts.all { it === quiescePorts.first() } shouldBe true
			(coordinators.first() === application.koin.get<SessionSwitchCoordinator>()) shouldBe true
		} finally {
			application.close()
		}
	}
})
