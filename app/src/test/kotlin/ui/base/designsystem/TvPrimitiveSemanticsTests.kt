package org.jellyfin.androidtv.ui.base.designsystem

import android.app.Application
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.LocalTextStyle
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.design.Tokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TvPrimitiveSemanticsTests {
	@get:Rule
	val composeRule = createComposeRule()

	@Test
	fun `mounted primitives expose actual role state focus and progress semantics`() {
		val focusOwners = TvFocusOwnerRegistry()
		composeRule.setContent {
			Column {
				TvAction(
					spec = TvActionSpec(TvSemantics("play", "Play")),
					onActivate = {},
					focusOwners = focusOwners,
				) {}
				TvChip(
					spec = TvChipSpec(
						semantics = TvSemantics("favorites", "Favorites"),
						state = TvComponentState(selection = TvSelection.Selected),
					),
					onActivate = {},
					focusOwners = focusOwners,
				) {}
				TvAction(
					spec = TvActionSpec(
						semantics = TvSemantics("locked", "Kids profile", "Locked"),
						state = TvComponentState(availability = TvAvailability.Locked),
					),
					onActivate = {},
					focusOwners = focusOwners,
				) {}
				TvProgress(progress = 0.4f, buffered = 0.7f, accessibleName = "Playback progress")
			}
		}

		composeRule.onNodeWithContentDescription("Play")
			.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
			.assertIsEnabled()
		composeRule.runOnIdle { focusOwners.restore(TvFocusRestoreRequest("play")) }
		composeRule.onNodeWithContentDescription("Play").assertIsFocused()

		composeRule.onNodeWithContentDescription("Favorites")
			.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
			.assertIsSelected()
		composeRule.onNodeWithContentDescription("Kids profile")
			.assertIsNotEnabled()
			.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Locked"))
		composeRule.onNodeWithContentDescription("Playback progress")
			.assert(
				SemanticsMatcher.expectValue(
					SemanticsProperties.ProgressBarRangeInfo,
					ProgressBarRangeInfo(0.4f, 0f..1f),
				)
			)
	}

	@Test
	fun `focus eligibility follows Ready Disabled Ready recomposition`() {
		val focusOwners = TvFocusOwnerRegistry()
		var availability by mutableStateOf(TvAvailability.Ready)
		composeRule.setContent {
			TvAction(
				spec = TvActionSpec(
					semantics = TvSemantics("toggle", "Toggle"),
					state = TvComponentState(availability = availability),
				),
				onActivate = {},
				focusOwners = focusOwners,
			) {}
		}

		composeRule.runOnIdle {
			focusOwners.restore(TvFocusRestoreRequest("toggle")) shouldBe "toggle"
			availability = TvAvailability.Disabled
		}
		composeRule.runOnIdle {
			focusOwners.restore(TvFocusRestoreRequest("toggle")) shouldBe null
			availability = TvAvailability.Ready
		}
		composeRule.runOnIdle {
			focusOwners.restore(TvFocusRestoreRequest("toggle")) shouldBe "toggle"
		}
	}

	@Test
	fun `overlay callback recomposition retains one layer and uses latest callback`() {
		val layers = TvLayerCoordinator()
		val focusOwners = TvFocusOwnerRegistry()
		val handled = mutableListOf<String>()
		var restoreAttempts = 0
		var callback by mutableStateOf<() -> Unit>({ handled += "first" })
		val triggerRegistration = focusOwners.register("trigger") {
			restoreAttempts++
			true
		}
		composeRule.setContent {
			TvOverlay(
				spec = TvOverlaySpec(
					semantics = TvSemantics("dialog", "Dialog"),
					restoreFocus = TvFocusRestoreRequest("trigger"),
					initialFocusId = "dialog-action",
				),
				onDismissRequest = callback,
				environment = TvOverlayEnvironment(layers, focusOwners),
			) {}
		}

		composeRule.runOnIdle { callback = { handled += "second" } }
		composeRule.runOnIdle {
			restoreAttempts shouldBe 0
			layers.handleBack() shouldBe true
			handled shouldBe listOf("second")
		}
		triggerRegistration.close()
	}

	@Test
	fun `all avatar variants retain at least 48dp focus and semantic bounds`() {
		val focusOwners = TvFocusOwnerRegistry()
		composeRule.setContent {
			Row {
				TvComponentSize.entries.forEach { size ->
					TvAvatar(
						spec = TvAvatarSpec(TvSemantics("avatar-$size", "$size avatar"), size = size),
						onActivate = {},
						focusOwners = focusOwners,
					) {}
				}
			}
		}

		TvComponentSize.entries.forEach { size ->
			composeRule.onNodeWithContentDescription("$size avatar")
				.assertWidthIsAtLeast(Tokens.SemanticComponent.tvMinimumTarget)
				.assertHeightIsAtLeast(Tokens.SemanticComponent.tvMinimumTarget)
		}
	}

	@Test
	fun `mounted Text and Icon consume the action semantic type and ink`() {
		val focusOwners = TvFocusOwnerRegistry()
		var laidOutStyle = androidx.compose.ui.text.TextStyle.Default
		var iconCallsiteInk = Color.Unspecified
		composeRule.setContent {
			TvAction(
				spec = TvActionSpec(TvSemantics("typed-action", "Typed action")),
				onActivate = {},
				focusOwners = focusOwners,
			) {
				Row {
					Text("Play", onTextLayout = { laidOutStyle = it.layoutInput.style })
					val inheritedInk = LocalTextStyle.current.color
					SideEffect { iconCallsiteInk = inheritedInk }
					Icon(
						painter = ColorPainter(Color.White),
						contentDescription = "Typed action icon",
					)
				}
			}
		}

		composeRule.runOnIdle {
			laidOutStyle.fontFamily shouldBe Tokens.SemanticTypography.labelLarge.fontFamily
			laidOutStyle.fontSize shouldBe Tokens.SemanticTypography.labelLarge.fontSize
			laidOutStyle.lineHeight shouldBe Tokens.SemanticTypography.labelLarge.lineHeight
			laidOutStyle.fontWeight shouldBe Tokens.SemanticTypography.labelLarge.fontWeight
			laidOutStyle.letterSpacing shouldBe Tokens.SemanticTypography.labelLarge.letterSpacing
			laidOutStyle.color shouldBe Tokens.SemanticColor.actionPrimary.content
			iconCallsiteInk shouldBe Tokens.SemanticColor.actionPrimary.content
		}
	}

	@Test
	fun `Escape dispatched to the mounted overlay closes once and restores on unmount`() {
		val layers = TvLayerCoordinator()
		val focusOwners = TvFocusOwnerRegistry()
		var open by mutableStateOf(true)
		var dismissals = 0
		var restoreAttempts = 0
		val triggerRegistration = focusOwners.register("trigger") {
			restoreAttempts++
			true
		}
		composeRule.setContent {
			if (open) {
				TvOverlay(
					spec = TvOverlaySpec(
						semantics = TvSemantics("dialog", "Dialog"),
						restoreFocus = TvFocusRestoreRequest("trigger"),
						initialFocusId = "dialog-action",
					),
					onDismissRequest = {
						dismissals++
						open = false
					},
					environment = TvOverlayEnvironment(layers, focusOwners),
				) {
					TvAction(
						spec = TvActionSpec(TvSemantics("dialog-action", "Dialog action")),
						onActivate = {},
						focusOwners = focusOwners,
					) {}
				}
			}
		}

		composeRule.onNodeWithContentDescription("Dialog action")
			.assertIsFocused()
			.performKeyInput {
				keyDown(Key.Escape)
				keyUp(Key.Escape)
			}
		composeRule.runOnIdle {
			dismissals shouldBe 1
			restoreAttempts shouldBe 1
		}
		triggerRegistration.close()
	}

	@Test
	fun `runtime animator scale change removes focused scale from the mounted action`() {
		val resolver = androidx.test.core.app.ApplicationProvider
			.getApplicationContext<Application>()
			.contentResolver
		val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
		val originalScale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
		try {
			Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
			val focusOwners = TvFocusOwnerRegistry()
			var contentCoordinates: LayoutCoordinates? = null
			composeRule.setContent {
				TvAction(
					spec = TvActionSpec(TvSemantics("motion-action", "Motion action")),
					onActivate = {},
					focusOwners = focusOwners,
				) {
					Box(
						Modifier
							.size(20.dp)
							.onGloballyPositioned { contentCoordinates = it }
					)
				}
			}
			composeRule.onNodeWithContentDescription("Motion action")
				.performSemanticsAction(SemanticsActions.RequestFocus)
				.assertIsFocused()
			val defaultWidth = composeRule.runOnIdle { contentCoordinates.transformedWidth() }

			Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
			resolver.notifyChange(uri, null)
			composeRule.waitForIdle()
			val reducedWidth = composeRule.runOnIdle { contentCoordinates.transformedWidth() }

			defaultWidth.shouldBeGreaterThan(reducedWidth)
		} finally {
			Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, originalScale)
			resolver.notifyChange(uri, null)
		}
	}
}

private fun LayoutCoordinates?.transformedWidth(): Float {
	val coordinates = requireNotNull(this)
	val start = coordinates.localToRoot(Offset.Zero)
	val end = coordinates.localToRoot(Offset(coordinates.size.width.toFloat(), 0f))
	return end.x - start.x
}
