package org.jellyfin.androidtv.ui.base.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

internal data class TvFocusBoundaryStroke(
	val outline: Outline,
	val width: Float,
)

internal data class TvFocusBoundaryStrokes(
	val separator: TvFocusBoundaryStroke,
	val indicator: TvFocusBoundaryStroke,
)

internal fun tvFocusBoundaryStrokes(
	outline: Outline,
	indicatorWidth: Float,
	separatorWidth: Float,
): TvFocusBoundaryStrokes {
	require(indicatorWidth >= 0f && separatorWidth >= 0f)
	return TvFocusBoundaryStrokes(
		separator = TvFocusBoundaryStroke(
			outline = outline,
			width = (indicatorWidth + separatorWidth) * 2f,
		),
		indicator = TvFocusBoundaryStroke(outline = outline, width = indicatorWidth * 2f),
	)
}

internal fun Modifier.tvFocusBoundary(
	indicatorWidth: Dp,
	separatorWidth: Dp,
	indicatorColor: Color,
	separatorColor: Color,
	shape: Shape,
): Modifier = drawWithCache {
	val outline = shape.createOutline(
		size,
		layoutDirection,
		this,
	)
	val strokes = tvFocusBoundaryStrokes(
		outline = outline,
		indicatorWidth = indicatorWidth.toPx(),
		separatorWidth = separatorWidth.toPx(),
	)
	onDrawWithContent {
		drawContent()
		drawFocusOutline(
			outline = strokes.separator.outline,
			color = separatorColor,
			stroke = Stroke(width = strokes.separator.width),
		)
		drawFocusOutline(
			outline = strokes.indicator.outline,
			color = indicatorColor,
			stroke = Stroke(width = strokes.indicator.width),
		)
	}
}

private fun DrawScope.drawFocusOutline(
	outline: Outline,
	color: Color,
	stroke: Stroke,
) {
	when (outline) {
		is Outline.Rectangle -> drawRect(
			color = color,
			topLeft = outline.rect.topLeft,
			size = outline.rect.size,
			style = stroke,
		)
		is Outline.Rounded -> drawPath(
			path = Path().apply { addRoundRect(outline.roundRect) },
			color = color,
			style = stroke,
		)
		is Outline.Generic -> drawPath(
			path = outline.path,
			color = color,
			style = stroke,
		)
	}
}
