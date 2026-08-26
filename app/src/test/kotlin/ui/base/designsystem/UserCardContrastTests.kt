package org.jellyfin.androidtv.ui.base.designsystem

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.card.UserCard
import org.jellyfin.design.token.SemanticColorPair
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UserCardContrastTests {
	@get:Rule
	val composeRule = createComposeRule()

	@Test
	fun `focused UserCard content uses the canvas ink with real composed contrast`() {
		var textStyle = TextStyle.Default
		var canvas = Color.Unspecified
		var canvasInk = Color.Unspecified
		lateinit var interactionSource: MutableInteractionSource
		composeRule.setContent {
			JellyfinTheme {
				canvas = JellyfinTheme.colorScheme.background
				canvasInk = JellyfinTheme.colorScheme.onBackground
				interactionSource = remember { MutableInteractionSource() }
				Box(Modifier.background(canvas)) {
					UserCard(
						image = { Box(Modifier.fillMaxSize()) },
						name = { Text("Profile", onTextLayout = { textStyle = it.layoutInput.style }) },
						onClick = {},
						interactionSource = interactionSource,
						modifier = Modifier
							.testTag(UserCardTag)
							.size(110.dp),
					)
				}
			}
		}

		composeRule.onNodeWithTag(UserCardTag)
			.performSemanticsAction(SemanticsActions.RequestFocus)
			.assertIsFocused()
		composeRule.runOnIdle { interactionSource.tryEmit(FocusInteraction.Focus()) }
		composeRule.runOnIdle {
			textStyle.color shouldBe canvasInk
			contrastRatio(
				SemanticColorPair(
					container = canvas,
					content = textStyle.color,
				)
			).shouldBeGreaterThanOrEqual(4.5)
		}
	}

	private companion object {
		const val UserCardTag = "user-card"
	}
}
