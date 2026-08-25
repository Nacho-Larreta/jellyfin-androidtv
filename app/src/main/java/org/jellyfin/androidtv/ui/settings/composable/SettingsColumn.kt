package org.jellyfin.androidtv.ui.settings.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jellyfin.design.Tokens

@Composable
fun SettingsColumn(
	modifier: Modifier = Modifier,
	content: LazyListScope.() -> Unit,
) = LazyColumn(
	modifier = modifier
		.padding(Tokens.Space.spaceSm),
	verticalArrangement = Arrangement.spacedBy(Tokens.Space.spaceXs),
	content = content,
)
