package org.jellyfin.androidtv.ui.home

import android.widget.ImageView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.compose.koinInject

@Composable
fun HomeHero(
	state: HomeHeroState,
	selected: Boolean,
	modifier: Modifier = Modifier,
	collapsed: Boolean = false,
) {
	when (state) {
		is HomeHeroState.Content -> HomeHeroContent(
			items = state.items,
			selected = selected,
			collapsed = collapsed,
			modifier = modifier,
		)

		HomeHeroState.Empty,
		is HomeHeroState.Error,
		HomeHeroState.Loading -> Unit
	}
}

@Composable
private fun HomeHeroContent(
	items: List<HomeHeroItemData>,
	selected: Boolean,
	collapsed: Boolean,
	modifier: Modifier = Modifier,
) {
	if (items.isEmpty()) return

	val selectedItem = items.first()
	val heroHeight by animateDpAsState(
		targetValue = if (collapsed) 0.dp else 390.dp,
		animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
		label = "homeHeroHeight",
	)
	val heroAlpha by animateFloatAsState(
		targetValue = if (collapsed) 0f else 1f,
		animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
		label = "homeHeroAlpha",
	)

	Box(
		modifier = modifier
			.height(heroHeight)
			.fillMaxWidth()
			.clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
			.background(Color(0xFF080808))
	) {
		HomeHeroBackdrop(
			item = selectedItem,
			modifier = Modifier.graphicsLayer { alpha = heroAlpha },
		)

		Row(
			modifier = Modifier
				.fillMaxSize()
				.graphicsLayer { alpha = heroAlpha }
				.padding(start = 54.dp, end = 58.dp, top = 8.dp, bottom = 28.dp),
			verticalAlignment = Alignment.Bottom,
		) {
			HomeHeroCopy(
				item = selectedItem,
				selected = selected,
				modifier = Modifier
					.weight(1f)
					.padding(bottom = 8.dp),
			)

			Spacer(Modifier.width(34.dp))

			HomeHeroPoster(selectedItem)
		}
	}
}

@Composable
private fun HomeHeroBackdrop(
	item: HomeHeroItemData,
	modifier: Modifier = Modifier,
) {
	val api = koinInject<ApiClient>()
	val backdrop = item.backdrop

	if (backdrop != null) {
		AsyncImage(
			modifier = modifier.fillMaxSize(),
			url = backdrop.getUrl(api, fillWidth = 1280, fillHeight = 720),
			blurHash = backdrop.blurHash,
			aspectRatio = 16f / 9f,
			scaleType = ImageView.ScaleType.CENTER_CROP,
		)
	} else {
		Box(
			modifier = modifier
				.fillMaxSize()
				.background(
					Brush.linearGradient(
						colors = listOf(
							Color(0xFF18242A),
							Color(0xFF0A0A0A),
						)
					)
				)
		)
	}

	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(
				Brush.horizontalGradient(
					0f to Color.Black.copy(alpha = 0.88f),
					0.54f to Color.Black.copy(alpha = 0.50f),
					1f to Color.Black.copy(alpha = 0.10f),
				)
			)
	)
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(
				Brush.verticalGradient(
					0f to Color.Black.copy(alpha = 0.12f),
					0.62f to Color.Transparent,
					1f to Color(0xFF050505),
				)
			)
	)
}

@Composable
private fun HomeHeroCopy(
	item: HomeHeroItemData,
	selected: Boolean,
	modifier: Modifier = Modifier,
) {
	val actionShape = RoundedCornerShape(8.dp)
	val actionContainerColor = if (selected) Color(0xFF2F2F2F) else Color.White.copy(alpha = 0.94f)
	val actionContentColor = if (selected) Color.White else Color.Black

	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.Bottom,
	) {
		Text(
			text = item.eyebrowLabel.uppercase(),
			color = Color.White.copy(alpha = 0.72f),
			fontSize = 12.sp,
			fontWeight = FontWeight.Bold,
			letterSpacing = 2.4.sp,
			maxLines = 1,
		)

		Spacer(Modifier.height(10.dp))

		Text(
			text = item.title.uppercase(),
			color = Color.White,
			fontSize = 48.sp,
			lineHeight = 49.sp,
			fontWeight = FontWeight.ExtraBold,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.fillMaxWidth(0.72f),
		)

		Spacer(Modifier.height(10.dp))

		HomeHeroMetadata(item)
		Spacer(Modifier.height(10.dp))

		item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
			Text(
				text = overview,
				color = Color.White.copy(alpha = 0.78f),
				fontSize = 15.sp,
				lineHeight = 20.sp,
				maxLines = 3,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.fillMaxWidth(0.66f),
			)
			Spacer(Modifier.height(14.dp))
		}

		HomeHeroProgress(item.progress, item.remainingLabel)
		Spacer(Modifier.height(14.dp))

		Box(
			modifier = Modifier
				.graphicsLayer {
					val scale = if (selected) 1.035f else 1f
					scaleX = scale
					scaleY = scale
				}
				.shadow(
					elevation = if (selected) 18.dp else 0.dp,
					shape = actionShape,
					clip = false,
				)
				.border(
					width = if (selected) 2.dp else 0.dp,
					color = if (selected) Color.White.copy(alpha = 0.82f) else Color.Transparent,
					shape = actionShape,
				)
				.clip(actionShape),
		) {
			Row(
				modifier = Modifier
					.background(actionContainerColor, actionShape)
					.padding(horizontal = 26.dp, vertical = 12.dp),
				horizontalArrangement = Arrangement.Center,
				verticalAlignment = Alignment.CenterVertically,
			) {
				Icon(
					imageVector = ImageVector.vectorResource(R.drawable.ic_play),
					contentDescription = null,
					tint = actionContentColor,
					modifier = Modifier.size(21.dp),
				)
				Spacer(Modifier.width(9.dp))
				Text(
					text = item.resumeLabel,
					color = actionContentColor,
					fontSize = 16.sp,
					fontWeight = FontWeight.Bold,
					maxLines = 1,
				)
			}
		}
	}
}

@Composable
private fun HomeHeroMetadata(item: HomeHeroItemData) {
	Row(
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		item.ratingLabel?.let { rating ->
			Text(
				text = rating,
				color = Color(0xFF6BE36E),
				fontSize = 14.sp,
				fontWeight = FontWeight.ExtraBold,
				maxLines = 1,
			)
		}

		item.metadataParts.forEachIndexed { index, part ->
			if (item.ratingLabel != null || index > 0) {
				Text(
					text = "·",
					color = Color.White.copy(alpha = 0.48f),
					fontSize = 14.sp,
					fontWeight = FontWeight.Bold,
				)
			}

			Text(
				text = part,
				color = Color.White.copy(alpha = 0.82f),
				fontSize = 14.sp,
				fontWeight = FontWeight.SemiBold,
				maxLines = 1,
			)
		}
	}
}

@Composable
private fun HomeHeroProgress(progress: Float, remainingLabel: String?) {
	if (progress <= 0f && remainingLabel == null) return

	Row(
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Box(
			modifier = Modifier
				.width(300.dp)
				.height(4.dp)
				.clip(RoundedCornerShape(999.dp))
				.background(Color.White.copy(alpha = 0.26f))
		) {
			Box(
				modifier = Modifier
					.fillMaxHeight()
					.fillMaxWidth(progress.coerceIn(0f, 1f))
					.background(Color(0xFFE50914))
			)
		}

		remainingLabel?.let {
			Text(
				text = it,
				color = Color.White.copy(alpha = 0.72f),
				fontSize = 12.sp,
				fontWeight = FontWeight.SemiBold,
			)
		}
	}
}

@Composable
private fun HomeHeroPoster(item: HomeHeroItemData) {
	val api = koinInject<ApiClient>()
	val poster = item.poster ?: return

	AsyncImage(
		modifier = Modifier
			.width(142.dp)
			.aspectRatio(poster.aspectRatio ?: 2f / 3f)
			.clip(RoundedCornerShape(12.dp))
			.border(
				width = 1.dp,
				color = Color.White.copy(alpha = 0.14f),
				shape = RoundedCornerShape(12.dp),
			),
		url = poster.getUrl(api, maxHeight = 380),
		blurHash = poster.blurHash,
		aspectRatio = poster.aspectRatio ?: 2f / 3f,
		scaleType = ImageView.ScaleType.CENTER_CROP,
	)
}
