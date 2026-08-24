package org.jellyfin.androidtv.ui.playback

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

class PlayerRemoteInputPolicyTests : FunSpec({
	test("hidden VOD controls reveal and consume Up and Down") {
		val state = PlayerRemoteInputState(
			liveTv = false,
			popupVisible = false,
			guideVisible = false,
			controlsVisible = false,
		)

		PlayerRemoteInputPolicy.directionalAction(PlayerRemoteDirection.Up, state) shouldBe
			PlayerRemoteDirectionalAction.RevealControlsAndFocus
		PlayerRemoteInputPolicy.directionalAction(PlayerRemoteDirection.Down, state) shouldBe
			PlayerRemoteDirectionalAction.RevealControlsAndFocus
	}

	test("visible VOD controls pass navigation to Leanback") {
		PlayerRemoteInputPolicy.directionalAction(
			direction = PlayerRemoteDirection.Down,
			state = PlayerRemoteInputState(controlsVisible = true),
		) shouldBe PlayerRemoteDirectionalAction.PassThrough
	}

	test("Live TV popup guide and dialog preserve their native handlers") {
		listOf(
			PlayerRemoteInputState(liveTv = true),
			PlayerRemoteInputState(popupVisible = true),
			PlayerRemoteInputState(guideVisible = true),
			PlayerRemoteInputState(dialogVisible = true),
		).forEach { state ->
			PlayerRemoteInputPolicy.directionalAction(PlayerRemoteDirection.Up, state) shouldBe
				PlayerRemoteDirectionalAction.PassThrough
		}
	}

	test("progress activation is consumed while keeping timeline focus") {
		PlayerRemoteInputPolicy.progressActivationEffect() shouldBe
			PlayerProgressActivationEffect.ConsumeAndKeepTimelineFocus
	}

	test("transport presenter consumes progress activation without synthesizing an action") {
		val sourcePath = sequenceOf(
			Path.of("app/src/main/java/org/jellyfin/androidtv/ui/playback/overlay/CustomPlaybackTransportControlGlue.java"),
			Path.of("src/main/java/org/jellyfin/androidtv/ui/playback/overlay/CustomPlaybackTransportControlGlue.java"),
		).first { Files.exists(it) }
		val callbackBody = sourcePath.toFile().readText()
			.substringAfter("protected void onProgressBarClicked")
			.substringBefore("@Override")

		callbackBody shouldContain "PlayerRemoteInputPolicy.progressActivationEffect()"
		callbackBody shouldContain "PlayerProgressActivationEffect.ConsumeAndKeepTimelineFocus"
		callbackBody shouldContain "return;"
		callbackBody shouldNotContain "super.onProgressBarClicked"
		callbackBody shouldNotContain "onActionClicked"
		callbackBody shouldNotContain "PlayPause"
	}
})
