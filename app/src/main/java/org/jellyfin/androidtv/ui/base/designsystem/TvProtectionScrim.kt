package org.jellyfin.androidtv.ui.base.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jellyfin.design.Tokens

@Composable
fun TvProtectionScrim(
	modifier: Modifier = Modifier,
	content: @Composable BoxScope.() -> Unit = {},
) {
	Box(
		modifier = modifier.background(Tokens.SemanticColor.overlayProtection),
		content = content,
	)
}
