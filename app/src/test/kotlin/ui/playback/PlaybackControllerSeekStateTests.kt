package org.jellyfin.androidtv.ui.playback

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files
import java.nio.file.Path

class PlaybackControllerSeekStateTests : FunSpec({
	test("normal media ready is not consumed by an inactive seek transaction") {
		val coordinator = SeekPlaybackCoordinator()

		coordinator.mediaReady().shouldBeNull()
		coordinator.isActive().shouldBeFalse()
	}

	test("paused rebuilt seek prepares without start and completes once on media ready") {
		val coordinator = SeekPlaybackCoordinator()

		coordinator.begin(PlaybackController.PlaybackState.PAUSED).shouldBeTrue()
		val restart = coordinator.streamRestarting()

		restart.controllerState shouldBe PlaybackController.PlaybackState.BUFFERING
		restart.engineIntent shouldBe SeekPlaybackCoordinator.EngineIntent.PAUSE
		restart.reportLoop shouldBe SeekPlaybackCoordinator.ReportLoop.NONE
		restart.completionEffects shouldBe SeekPlaybackCoordinator.CompletionEffects.NONE
		restart.overlayIntent shouldBe SeekPlaybackCoordinator.OverlayIntent.NONE
		coordinator.isAwaitingPausedMediaReady().shouldBeTrue()

		val completion = coordinator.mediaReady()!!

		completion.controllerState shouldBe PlaybackController.PlaybackState.PAUSED
		completion.engineIntent shouldBe SeekPlaybackCoordinator.EngineIntent.NONE
		completion.reportLoop shouldBe SeekPlaybackCoordinator.ReportLoop.PAUSED
		completion.completionEffects shouldBe SeekPlaybackCoordinator.CompletionEffects.PREPARED_MEDIA
		completion.overlayIntent shouldBe SeekPlaybackCoordinator.OverlayIntent.NONE
		coordinator.mediaReady().shouldBeNull()
		coordinator.playbackStarted().shouldBeNull()
		coordinator.isActive().shouldBeFalse()
	}

	test("playing rebuilt seek ignores media ready and completes once when playback starts") {
		val coordinator = SeekPlaybackCoordinator()

		coordinator.begin(PlaybackController.PlaybackState.PLAYING).shouldBeTrue()
		val restart = coordinator.streamRestarting()

		restart.controllerState shouldBe PlaybackController.PlaybackState.BUFFERING
		restart.engineIntent shouldBe SeekPlaybackCoordinator.EngineIntent.START
		coordinator.isAwaitingPlayingStart().shouldBeTrue()
		coordinator.mediaReady().shouldBeNull()

		val completion = coordinator.playbackStarted()!!

		completion.controllerState shouldBe PlaybackController.PlaybackState.PLAYING
		completion.engineIntent shouldBe SeekPlaybackCoordinator.EngineIntent.NONE
		completion.reportLoop shouldBe SeekPlaybackCoordinator.ReportLoop.PLAYING
		completion.completionEffects shouldBe SeekPlaybackCoordinator.CompletionEffects.PREPARED_MEDIA
		completion.overlayIntent shouldBe SeekPlaybackCoordinator.OverlayIntent.HIDE
		coordinator.playbackStarted().shouldBeNull()
		coordinator.mediaReady().shouldBeNull()
		coordinator.isActive().shouldBeFalse()
	}

	test("ready after abort is ignored and repeat preserves the first state") {
		val coordinator = SeekPlaybackCoordinator()

		coordinator.begin(PlaybackController.PlaybackState.PAUSED).shouldBeTrue()
		coordinator.begin(PlaybackController.PlaybackState.PLAYING).shouldBeFalse()
		coordinator.streamRestarting()
		coordinator.abort()
		coordinator.abort()

		coordinator.mediaReady().shouldBeNull()
		coordinator.playbackStarted().shouldBeNull()
		coordinator.isActive().shouldBeFalse()
	}

	test("direct seek preserves paused and playing without an engine transport command") {
		val paused = SeekPlaybackCoordinator()
		paused.begin(PlaybackController.PlaybackState.PAUSED).shouldBeTrue()

		paused.directSeekCompleted() shouldBe SeekPlaybackCoordinator.Transition.DIRECT_PAUSED

		val playing = SeekPlaybackCoordinator()
		playing.begin(PlaybackController.PlaybackState.PLAYING).shouldBeTrue()

		playing.directSeekCompleted() shouldBe SeekPlaybackCoordinator.Transition.DIRECT_PLAYING
	}

	test("media ready is emitted once for each media generation") {
		val guard = MediaReadyOneShotGate()

		guard.claimReady().shouldBeFalse()
		guard.beginGeneration()
		guard.claimReady().shouldBeTrue()
		guard.claimReady().shouldBeFalse()
		guard.beginGeneration()
		guard.claimReady().shouldBeTrue()
	}

	test("controller orders restart intent before prepare and retains initial video start delay") {
		val controllerSource = source("PlaybackController.java")
		val videoManagerSource = source("VideoManager.java")
		val seekBody = controllerSource
			.substringAfter("public void seek(long pos, boolean skipToNext)", "")
			.substringBefore("private long currentSkipPos", "")
		val rebuiltResponse = seekBody
			.substringAfter("public void onResponse(StreamInfo response)", "")
			.substringBefore("public void onError(Exception exception)", "")
		val mediaSetup = videoManagerSource
			.substringAfter("public void setMediaStreamInfo(ApiClient api, StreamInfo streamInfo)", "")
			.substringBefore("private int offsetStreamIndex", "")

		rebuiltResponse shouldContain "seekPlaybackCoordinator.streamRestarting()"
		rebuiltResponse shouldContain "applySeekTransition"
		rebuiltResponse.indexOf("applySeekTransition").shouldBeLessThan(rebuiltResponse.indexOf("setMediaStreamInfo"))
		controllerSource shouldContain "getVideoStartDelay()"
		controllerSource shouldContain "mHandler.postDelayed"
		videoManagerSource shouldContain "playbackState == Player.STATE_READY"
		videoManagerSource shouldContain "mPlaybackControllerNotifiable.onMediaReady()"
		mediaSetup shouldContain "beginGeneration()"
		rebuiltResponse shouldNotContain "mVideoManager.play()"
	}
})

private fun source(fileName: String): String = sequenceOf(
	Path.of("app/src/main/java/org/jellyfin/androidtv/ui/playback/$fileName"),
	Path.of("src/main/java/org/jellyfin/androidtv/ui/playback/$fileName"),
).first { Files.exists(it) }.toFile().readText()

private fun Int.shouldBeLessThan(other: Int) {
	(this >= 0 && other >= 0 && this < other) shouldBe true
}
