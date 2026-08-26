package org.jellyfin.androidtv.ui.base.designsystem

import android.app.Application
import android.widget.FrameLayout
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.input.key.KeyEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeZero
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TvInputIntegrationTests {
	@Test
	fun `real Escape down repeat and up close the nearest layer once`() {
		val handled = mutableListOf<String>()
		val layers = TvLayerCoordinator()
		layers.activate("modal", TvLayer.Modal) { handled += "modal" }
		layers.activate("menu", TvLayer.Menu) { handled += "menu" }

		layers.routeEscape(escapeEvent(AndroidKeyEvent.ACTION_DOWN)).shouldBeTrue()
		layers.routeEscape(escapeEvent(AndroidKeyEvent.ACTION_DOWN, repeatCount = 1)).shouldBeTrue()
		handled.shouldContainExactly()
		layers.routeEscape(escapeEvent(AndroidKeyEvent.ACTION_UP)).shouldBeTrue()
		handled.shouldContainExactly("menu")
		layers.routeEscape(escapeEvent(AndroidKeyEvent.ACTION_UP)) shouldBe false
	}

	@Test
	fun `lifecycle pause cancels down before background key up`() {
		assertLifecycleCancellation(Lifecycle.Event.ON_PAUSE)
	}

	@Test
	fun `lifecycle stop cancels down before background key up`() {
		assertLifecycleCancellation(Lifecycle.Event.ON_STOP)
	}

	@Test
	fun `mounted cancellation effect cancels a press on lifecycle pause`() {
		assertMountedEffectCancellation { owner, _ ->
			owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
		}
	}

	@Test
	fun `mounted cancellation effect cancels a press on lifecycle stop`() {
		assertMountedEffectCancellation { owner, _ ->
			owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
		}
	}

	@Test
	fun `mounted cancellation effect cancels a press on window focus loss`() {
		assertMountedEffectCancellation { _, composeView ->
			composeView.dispatchRegisteredWindowFocus(false)
		}
	}

	private fun assertLifecycleCancellation(event: Lifecycle.Event) {
		var activations = 0
		val ledger = TvPressLedger { activations++ }
		val owner = TestLifecycleOwner()
		owner.registry.addObserver(TvInputCancellation(ledger::cancel))
		owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
		owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

		ledger.route(TvPressEvent(23, TvPressPhase.Down)).shouldBeTrue()
		owner.registry.handleLifecycleEvent(event)
		ledger.route(TvPressEvent(23, TvPressPhase.Up)) shouldBe false
		activations.shouldBeZero()
	}

	private fun assertMountedEffectCancellation(cancel: (TestLifecycleOwner, ComposeView) -> Unit) {
		Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
			val activity = controller.get()
			val lifecycleOwner = TestLifecycleOwner().apply {
				registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
				registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
			}
			val focusOwners = TvFocusOwnerRegistry()
			var activations = 0
			val composeView = ComposeView(activity).apply {
				setViewTreeLifecycleOwner(lifecycleOwner)
				setContent {
					TvAction(
						spec = TvActionSpec(TvSemantics("mounted-action", "Mounted action")),
						onActivate = { activations++ },
						focusOwners = focusOwners,
					) {}
				}
			}
			activity.setContentView(FrameLayout(activity).apply { addView(composeView) })
			shadowOf(android.os.Looper.getMainLooper()).idle()
			focusOwners.restore(TvFocusRestoreRequest("mounted-action")) shouldBe "mounted-action"
			shadowOf(android.os.Looper.getMainLooper()).idle()

			composeView.dispatchKeyEvent(androidKeyEvent(AndroidKeyEvent.ACTION_DOWN)).shouldBeTrue()
			cancel(lifecycleOwner, composeView)
			composeView.dispatchKeyEvent(androidKeyEvent(AndroidKeyEvent.ACTION_UP)) shouldBe false
			activations.shouldBeZero()
		}
	}
}

private fun ComposeView.dispatchRegisteredWindowFocus(hasFocus: Boolean) {
	val dispatch = android.view.ViewTreeObserver::class.java.getDeclaredMethod(
		"dispatchOnWindowFocusChange",
		Boolean::class.javaPrimitiveType,
	)
	dispatch.isAccessible = true
	dispatch.invoke(viewTreeObserver, hasFocus)
}

private fun escapeEvent(action: Int, repeatCount: Int = 0): KeyEvent = KeyEvent(
	AndroidKeyEvent(
		0L,
		0L,
		action,
		AndroidKeyEvent.KEYCODE_ESCAPE,
		repeatCount,
	),
)

private fun androidKeyEvent(action: Int): AndroidKeyEvent = AndroidKeyEvent(
	0L,
	0L,
	action,
	AndroidKeyEvent.KEYCODE_DPAD_CENTER,
	0,
)

private class TestLifecycleOwner : LifecycleOwner {
	val registry = LifecycleRegistry(this)
	override val lifecycle: Lifecycle = registry
}
