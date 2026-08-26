package org.jellyfin.androidtv.ui.base.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import org.jellyfin.design.Tokens

@Composable
fun TvProgress(
	progress: Float,
	accessibleName: String,
	modifier: Modifier = Modifier,
	buffered: Float = progress,
) {
	val safeProgress = progress.coerceIn(0f, 1f)
	val safeBuffered = buffered.coerceIn(safeProgress, 1f)
	Box(
		modifier = modifier
			.fillMaxWidth()
			.height(Tokens.SemanticComponent.progressHeight)
			.clip(RoundedCornerShape(percent = Tokens.SemanticShape.FullPercent))
			.background(Tokens.SemanticColor.progressTrack)
			.semantics {
				contentDescription = accessibleName
				progressBarRangeInfo = ProgressBarRangeInfo(safeProgress, 0f..1f)
			},
	) {
		ProgressLayer(safeBuffered, Tokens.SemanticColor.progressBuffered)
		ProgressLayer(safeProgress, Tokens.SemanticColor.progressPlayed)
	}
}

@Composable
private fun ProgressLayer(progress: Float, color: androidx.compose.ui.graphics.Color) {
	Box(
		Modifier
			.fillMaxWidth(progress)
			.fillMaxHeight()
			.background(color)
	)
}
