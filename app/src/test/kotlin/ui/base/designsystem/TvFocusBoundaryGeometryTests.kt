package org.jellyfin.androidtv.ui.base.designsystem

import android.app.Application
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import io.kotest.matchers.booleans.shouldBeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE)
class TvFocusBoundaryGeometryTests {
	@Test
	fun `generic non-rectangular focus shape reuses one exact outline for both rings`() {
		val generic = Outline.Generic(
			Path().apply {
				moveTo(0f, 40f)
				lineTo(50f, 0f)
				lineTo(100f, 40f)
				lineTo(50f, 80f)
				close()
			}
		)

		val strokes = tvFocusBoundaryStrokes(generic, indicatorWidth = 3f, separatorWidth = 2f)

		(strokes.indicator.outline === generic).shouldBeTrue()
		(strokes.separator.outline === generic).shouldBeTrue()
	}
}
