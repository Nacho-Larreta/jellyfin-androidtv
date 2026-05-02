package org.jellyfin.androidtv.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text

@Composable
fun HomeLibraryPills(
	libraries: List<HomeLibraryItemData>,
	selectedIndex: Int?,
	modifier: Modifier = Modifier,
) {
	if (libraries.isEmpty()) return

	Column(
		modifier = modifier
			.background(Color(0xFF050505))
			.padding(start = 54.dp, end = 54.dp, top = 26.dp, bottom = 20.dp),
	) {
		Text(
			text = "Mis bibliotecas",
			color = Color.White,
			fontSize = 25.sp,
			fontWeight = FontWeight.ExtraBold,
			maxLines = 1,
		)

		Spacer(Modifier.height(22.dp))

		Row(
			horizontalArrangement = Arrangement.spacedBy(24.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			libraries.forEachIndexed { index, library ->
				HomeLibraryPill(
					library = library,
					selected = selectedIndex == index,
				)
			}
		}
	}
}

@Composable
private fun HomeLibraryPill(
	library: HomeLibraryItemData,
	selected: Boolean,
) {
	val pillShape = RoundedCornerShape(999.dp)
	val scale by animateFloatAsState(
		targetValue = if (selected) 1.035f else 1f,
		label = "home-library-pill-scale",
	)

	Row(
		modifier = Modifier
			.width(HOME_LIBRARY_PILL_WIDTH)
			.height(76.dp)
			.graphicsLayer {
				scaleX = scale
				scaleY = scale
			}
			.shadow(
				elevation = if (selected) 18.dp else 0.dp,
				shape = pillShape,
				clip = false,
			)
			.border(
				width = if (selected) 3.dp else 1.dp,
				color = if (selected) Color.White.copy(alpha = 0.86f) else Color.White.copy(alpha = 0.16f),
				shape = pillShape,
			)
			.clip(pillShape)
			.background(Color.White.copy(alpha = if (selected) 0.22f else 0.08f))
			.padding(start = 17.dp, end = 18.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Box(
			modifier = Modifier
				.size(46.dp)
				.clip(CircleShape)
				.background(library.accent.copy(alpha = if (selected) 0.30f else 0.18f)),
			contentAlignment = Alignment.Center,
		) {
			Icon(
				imageVector = ImageVector.vectorResource(library.iconRes),
				contentDescription = null,
				tint = library.accent,
				modifier = Modifier.size(22.dp),
			)
		}

		Spacer(Modifier.width(17.dp))

		Text(
			text = library.title,
			color = Color.White,
			fontSize = 18.sp,
			fontWeight = FontWeight.ExtraBold,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.weight(1f),
		)

		library.count?.let {
			Text(
				text = it,
				color = Color.White.copy(alpha = 0.62f),
				fontSize = 16.sp,
				fontWeight = FontWeight.Bold,
				maxLines = 1,
			)
		}
	}
}

private val HOME_LIBRARY_PILL_WIDTH = 266.dp
