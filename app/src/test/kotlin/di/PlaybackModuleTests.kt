package org.jellyfin.androidtv.di

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import org.jellyfin.androidtv.auth.repository.PlaybackQuiescePort
import org.jellyfin.androidtv.ui.playback.PlaybackQuiesceRegistry
import org.jellyfin.playback.core.PlaybackManager
import org.jellyfin.playback.core.PlayerState
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class PlaybackModuleTests : FunSpec({
	test("resolving playback registers quiesce without eager graph creation") {
		var playbackGraphCreations = 0
		val playbackState = mockk<PlayerState>(relaxed = true)
		val playbackManager = mockk<PlaybackManager> {
			every { state } returns playbackState
		}
		val application = koinApplication {
			allowOverride(true)
			modules(
				createPlaybackModule {
					playbackGraphCreations++
					playbackManager
				},
				module {
					single { PlaybackQuiesceRegistry(Dispatchers.Unconfined) }
				},
			)
		}

		try {
			val quiescePort = application.koin.get<PlaybackQuiescePort>()
			quiescePort.quiesceIfCreated()
			quiescePort.quiesceIfCreated()
			playbackGraphCreations shouldBe 0
			verify(exactly = 0) { playbackState.stop() }

			application.koin.get<PlaybackManager>() shouldBe playbackManager
			playbackGraphCreations shouldBe 1
			quiescePort.quiesceIfCreated()

			verify(exactly = 1) { playbackState.stop() }
		} finally {
			application.close()
		}
	}
})
