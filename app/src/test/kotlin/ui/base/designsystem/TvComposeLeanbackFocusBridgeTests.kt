package org.jellyfin.androidtv.ui.base.designsystem

import android.app.Application
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.leanback.widget.BaseCardView
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.ComposeView
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TvComposeLeanbackFocusBridgeTests {
	@Test
	fun `Compose focus events publish the actual semantic owner`() {
		Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
			val activity = controller.get()
			val registry = TvFocusOwnerRegistry()
			lateinit var requester: FocusRequester
			val composeView = ComposeView(activity).apply {
				setContent {
					requester = remember { FocusRequester() }
					Box(
						Modifier
							.focusRequester(requester)
							.tvFocusOwner("compose-action", registry)
							.focusable()
					)
				}
			}
			activity.setContentView(composeView)
			shadowOf(android.os.Looper.getMainLooper()).idle()

			requester.requestFocus() shouldBe true
			shadowOf(android.os.Looper.getMainLooper()).idle()
			registry.currentOwnerId shouldBe "compose-action"
		}
	}

	@Test
	fun `Leanback View focus and Compose focus share one owner registry`() {
		Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
			val activity = controller.get()
			val registry = TvFocusOwnerRegistry()
			val root = FrameLayout(activity)
			val composeView = ComposeView(activity).apply {
				setContent {
					TvAction(
						spec = TvActionSpec(TvSemantics("compose-action", "Compose action")),
						onActivate = {},
						focusOwners = registry,
					) {}
				}
			}
			val leanbackControl = BaseCardView(activity).apply {
				isFocusable = true
				isFocusableInTouchMode = true
			}
			root.addView(composeView)
			root.addView(leanbackControl)
			activity.setContentView(root)
			shadowOf(android.os.Looper.getMainLooper()).idle()
			leanbackControl.bindTvFocusOwner("leanback-control", registry).use {
				registry.restore(TvFocusRestoreRequest("compose-action")) shouldBe "compose-action"
				shadowOf(android.os.Looper.getMainLooper()).idle()
				registry.currentOwnerId shouldBe "compose-action"
				leanbackControl.requestFocus() shouldBe true
				shadowOf(android.os.Looper.getMainLooper()).idle()
				registry.currentOwnerId shouldBe "leanback-control"
				registry.restore(TvFocusRestoreRequest("compose-action")) shouldBe "compose-action"
				shadowOf(android.os.Looper.getMainLooper()).idle()
				registry.currentOwnerId shouldBe "compose-action"
				registry.restore(TvFocusRestoreRequest("leanback-control")) shouldBe "leanback-control"
				shadowOf(android.os.Looper.getMainLooper()).idle()
				registry.currentOwnerId shouldBe "leanback-control"
			}
		}
	}

	@Test
	fun `Leanback binding preserves an existing focus listener before and after close`() {
		Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
			val activity = controller.get()
			val registry = TvFocusOwnerRegistry()
			val root = FrameLayout(activity)
			val sibling = View(activity).apply { isFocusableInTouchMode = true }
			var existingCallbacks = 0
			val leanbackControl = BaseCardView(activity).apply {
				isFocusable = true
				isFocusableInTouchMode = true
				setOnFocusChangeListener { _, _ -> existingCallbacks++ }
			}
			root.addView(leanbackControl)
			root.addView(sibling)
			activity.setContentView(root)

			val binding = leanbackControl.bindTvFocusOwner("leanback-control", registry)
			leanbackControl.requestFocus() shouldBe true
			shadowOf(android.os.Looper.getMainLooper()).idle()
			registry.currentOwnerId shouldBe "leanback-control"
			existingCallbacks shouldBe 1
			binding.close()
			sibling.requestFocus() shouldBe true
			leanbackControl.requestFocus() shouldBe true
			shadowOf(android.os.Looper.getMainLooper()).idle()
			existingCallbacks shouldBe 3
			registry.currentOwnerId shouldBe null
		}
	}
}
