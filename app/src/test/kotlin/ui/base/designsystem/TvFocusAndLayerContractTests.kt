package org.jellyfin.androidtv.ui.base.designsystem

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jellyfin.design.Tokens

class TvFocusAndLayerContractTests : FunSpec({
	test("actual focus callbacks maintain exactly one stable owner") {
		val registry = TvFocusOwnerRegistry()

		registry.onFocusChanged("card-a", true)
		registry.currentOwnerId shouldBe "card-a"
		registry.onFocusChanged("card-b", true)
		registry.currentOwnerId shouldBe "card-b"
		registry.onFocusChanged("card-a", false)
		registry.currentOwnerId shouldBe "card-b"
		registry.onFocusChanged("card-b", false)
		registry.currentOwnerId shouldBe null
	}

	test("restore follows trigger sibling section and invoking-control fallback order") {
		val attempts = mutableListOf<String>()
		val registry = TvFocusOwnerRegistry()
		registry.register("trigger", canFocus = { false }) { attempts += "trigger"; true }
		registry.register("sibling") { attempts += "sibling"; true }
		registry.register("section") { attempts += "section"; true }
		registry.register("invoker") { attempts += "invoker"; true }

		registry.restore(
			TvFocusRestoreRequest(
				triggerId = "trigger",
				siblingIds = listOf("missing", "sibling"),
				sectionOwnerId = "section",
				invokingControlId = "invoker",
			)
		) shouldBe "sibling"
		attempts.shouldContainExactly("sibling")
	}

	test("restore retries when an asynchronous stable target is inserted") {
		val attempts = mutableListOf<String>()
		val registry = TvFocusOwnerRegistry()

		registry.restore(TvFocusRestoreRequest(triggerId = "async-card")) shouldBe null
		registry.register("async-card") { attempts += "async-card"; true }

		attempts.shouldContainExactly("async-card")
	}

	test("Back closes only the nearest active layer") {
		val handled = mutableListOf<String>()
		val layers = TvLayerCoordinator()
		layers.activate("route", TvLayer.Route) { handled += "route" }
		layers.activate("modal", TvLayer.Modal) { handled += "modal" }
		val menu = layers.activate("menu", TvLayer.Menu) { handled += "menu" }

		layers.handleBack() shouldBe true
		handled.shouldContainExactly("menu")
		menu.close()
		layers.handleBack() shouldBe true
		handled.shouldContainExactly("menu", "modal")
	}

	test("the last opened peer layer owns Back") {
		val handled = mutableListOf<String>()
		val layers = TvLayerCoordinator()
		layers.activate("first-menu", TvLayer.Menu) { handled += "first" }
		layers.activate("second-menu", TvLayer.Menu) { handled += "second" }

		layers.handleBack() shouldBe true
		handled.shouldContainExactly("second")
	}

	test("system zero animation scale selects reduced motion without changing operation timing") {
		motionPreferenceForScale(0f) shouldBe TvMotionPreference.Reduced
		motionPreferenceForScale(0.5f) shouldBe TvMotionPreference.Default
		motionPreferenceForScale(1f) shouldBe TvMotionPreference.Default
	}

	test("reduced focus motion snaps while default motion consumes typed easing") {
		focusAnimationSpec(true).shouldBeInstanceOf<SnapSpec<Float>>()
		val defaultSpec = focusAnimationSpec(false).shouldBeInstanceOf<TweenSpec<Float>>()
		defaultSpec.durationMillis shouldBe Tokens.SemanticMotion.FastMillis
		defaultSpec.easing shouldBe Tokens.SemanticMotion.standardEasing
	}

	test("focus indicator and separator expose adjacent non-overlapping widths on one outline") {
		val outline = Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, 0f, 100f, 80f))
		val strokes = tvFocusBoundaryStrokes(outline, indicatorWidth = 3f, separatorWidth = 2f)

		strokes.indicator.width / 2f shouldBe 3f
		(strokes.separator.width - strokes.indicator.width) / 2f shouldBe 2f
		(strokes.indicator.outline === outline).shouldBeTrue()
		(strokes.separator.outline === outline).shouldBeTrue()
	}

	test("fixed rounded focus shapes reuse one exact outline for both rings") {
		val rounded = RoundedCornerShape(12.dp).createOutline(
			size = Size(100f, 80f),
			layoutDirection = LayoutDirection.Ltr,
			density = Density(1f),
		)

		val strokes = tvFocusBoundaryStrokes(rounded, indicatorWidth = 3f, separatorWidth = 2f)
		(strokes.indicator.outline === rounded).shouldBeTrue()
		(strokes.separator.outline === rounded).shouldBeTrue()
	}
})
