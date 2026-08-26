package org.jellyfin.androidtv.ui.base.designsystem

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.ui.base.LocalTextStyle
import org.jellyfin.design.Tokens
import org.jellyfin.design.token.SemanticColorPair
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TvPrimitiveCompositionTests {
	@Test
	fun `surface composes its semantic container and real Text Icon ink local`() {
		Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
			val activity = controller.get()
			val registry = TvFocusOwnerRegistry()
			var composedColors = SemanticColorPair(Color.Unspecified, Color.Unspecified)
			var composedTextColor = Color.Unspecified
			val composeView = ComposeView(activity).apply {
				setContent {
					TvAction(
						spec = TvActionSpec(TvSemantics("play", "Play")),
						onActivate = {},
						focusOwners = registry,
					) {
						composedColors = LocalTvSurfaceColors.current
						composedTextColor = LocalTextStyle.current.color
					}
				}
			}
			activity.setContentView(composeView)
			shadowOf(android.os.Looper.getMainLooper()).idle()

			composedColors shouldBe Tokens.SemanticColor.actionPrimary
			composedTextColor shouldBe Tokens.SemanticColor.actionPrimary.content
		}
	}

	@Test
	fun `each focusable primitive publishes its semantic typography role`() {
		Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
			val activity = controller.get()
			val registry = TvFocusOwnerRegistry()
			val styles = mutableMapOf<String, TextStyle>()
			val composeView = ComposeView(activity).apply {
				setContent {
					androidx.compose.foundation.layout.Column {
						TvAction(TvActionSpec(TvSemantics("action", "Action")), {}, registry) {
							styles["action"] = LocalTextStyle.current
						}
						TvChip(TvChipSpec(TvSemantics("chip", "Chip")), {}, registry) {
							styles["chip"] = LocalTextStyle.current
						}
						TvAvatar(TvAvatarSpec(TvSemantics("avatar", "Avatar")), {}, registry) {
							styles["avatar"] = LocalTextStyle.current
						}
						TvMediaCard(TvMediaCardSpec(TvSemantics("card", "Card")), {}, registry) {
							styles["card"] = LocalTextStyle.current
						}
					}
				}
			}
			activity.setContentView(composeView)
			shadowOf(android.os.Looper.getMainLooper()).idle()

			styles.getValue("action").shouldMatchTypeRole(Tokens.SemanticTypography.labelLarge)
			styles.getValue("chip").shouldMatchTypeRole(Tokens.SemanticTypography.labelLarge)
			styles.getValue("avatar").shouldMatchTypeRole(Tokens.SemanticTypography.labelMedium)
			styles.getValue("card").shouldMatchTypeRole(Tokens.SemanticTypography.titleMedium)
		}
	}
}

private fun TextStyle.shouldMatchTypeRole(expected: TextStyle) {
	fontFamily shouldBe expected.fontFamily
	fontSize shouldBe expected.fontSize
	lineHeight shouldBe expected.lineHeight
	fontWeight shouldBe expected.fontWeight
	letterSpacing shouldBe expected.letterSpacing
}
