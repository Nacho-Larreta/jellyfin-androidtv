package org.jellyfin.androidtv.ui.home

import android.widget.ImageView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.compose.koinInject

@Composable
fun HomeMediaRow(
	row: HomeMediaRowData,
	rowIndex: Int,
	selectedItemIndex: Int?,
	modifier: Modifier = Modifier,
) {
	val rowListState = rememberLazyListState()
	val safeSelectedIndex = selectedItemIndex?.coerceIn(0, (row.items.size - 1).coerceAtLeast(0))

	LaunchedEffect(safeSelectedIndex, row.items.map { it.itemId }) {
		if (safeSelectedIndex != null) {
			rowListState.animateScrollToItem(safeSelectedIndex)
		}
	}

	Column(
		modifier = modifier
			.fillMaxWidth()
			.padding(top = if (rowIndex == 0) 34.dp else 48.dp)
	) {
		Row(
			modifier = Modifier.padding(horizontal = 54.dp),
			verticalAlignment = Alignment.Bottom,
			horizontalArrangement = Arrangement.spacedBy(14.dp)
		) {
			Text(
				text = row.title,
				color = Color.White,
				fontSize = 26.sp,
				fontWeight = FontWeight.ExtraBold,
				letterSpacing = (-0.4).sp,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)

			row.subtitle?.let { subtitle ->
				Text(
					modifier = Modifier.padding(bottom = 3.dp),
					text = subtitle,
					color = Color.White.copy(alpha = 0.48f),
					fontSize = 15.sp,
					fontWeight = FontWeight.Bold,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
		}

		Spacer(modifier = Modifier.height(18.dp))

		LazyRow(
			state = rowListState,
			contentPadding = PaddingValues(horizontal = 54.dp),
			horizontalArrangement = Arrangement.spacedBy(26.dp),
			userScrollEnabled = false,
		) {
			itemsIndexed(
				items = row.items,
				key = { index, item -> item.itemId ?: "${row.id}-$index" },
			) { index, item ->
				HomeMediaCard(
					item = item,
					row = row,
					selected = safeSelectedIndex == index,
				)
			}
		}
	}
}

@Composable
private fun HomeMediaCard(
	item: BaseRowItem,
	row: HomeMediaRowData,
	selected: Boolean,
) {
	val context = LocalContext.current
	val api = koinInject<ApiClient>()
	val scale by animateFloatAsState(targetValue = if (selected) 1.07f else 1f, label = "home-media-card-scale")
	val baseItem = item.baseItem
	val image = item.getImage(ImageType.THUMB) ?: item.getImage(ImageType.POSTER)
	val imageUrl = image?.getUrl(api, maxWidth = 760, maxHeight = 428, fillWidth = 760, fillHeight = 428)
	val progress = baseItem?.let(::playbackProgress) ?: 0f
	val title = item.getCardName(context) ?: item.getName(context).orEmpty()
	val subtitle = when (row.kind) {
		HomeMediaRowKind.Resume -> remainingTimeLabel(baseItem)
		HomeMediaRowKind.Latest -> item.getSubText(context)
	}

	Column(
		modifier = Modifier
			.width(CARD_WIDTH)
			.graphicsLayer {
				scaleX = scale
				scaleY = scale
			}
	) {
		Box(
			modifier = Modifier
				.shadow(if (selected) 26.dp else 0.dp, CARD_SHAPE)
				.border(
					border = BorderStroke(if (selected) 3.dp else 0.dp, Color.White.copy(alpha = 0.94f)),
					shape = CARD_SHAPE,
				)
				.clip(CARD_SHAPE)
				.background(Color(0xFF151515))
		) {
			if (imageUrl != null) {
				AsyncImage(
					modifier = Modifier
						.width(CARD_WIDTH)
						.height(CARD_HEIGHT),
					url = imageUrl,
					aspectRatio = 16f / 9f,
					scaleType = ImageView.ScaleType.CENTER_CROP,
				)
			} else {
				Box(
					modifier = Modifier
						.width(CARD_WIDTH)
						.height(CARD_HEIGHT)
						.background(
							Brush.linearGradient(
								listOf(
									Color(0xFF202020),
									Color(0xFF101010),
								)
							)
						)
				)
			}

			Box(
				modifier = Modifier
					.matchParentSize()
					.background(Color.Black.copy(alpha = if (selected) 0.02f else 0.10f))
			)

			if (selected) {
				Box(
					modifier = Modifier
						.align(Alignment.Center)
						.size(64.dp)
						.background(Color.White.copy(alpha = 0.92f), CircleShape),
					contentAlignment = Alignment.Center
				) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_play),
						contentDescription = null,
						tint = Color.Black,
						modifier = Modifier.size(38.dp),
					)
				}
			}

			if (progress > 0f) {
				Box(
					modifier = Modifier
						.align(Alignment.BottomStart)
						.fillMaxWidth()
						.height(5.dp),
				) {
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.height(5.dp)
							.background(Color.White.copy(alpha = 0.28f))
					)
					Box(
						modifier = Modifier
							.fillMaxWidth(progress)
							.height(5.dp)
							.background(Color(0xFFE50914))
					)
				}
			}
		}

		Spacer(modifier = Modifier.height(12.dp))

		Text(
			modifier = Modifier.widthIn(max = CARD_WIDTH),
			text = title,
			color = Color.White,
			fontSize = 17.sp,
			fontWeight = FontWeight.ExtraBold,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)

		if (!subtitle.isNullOrBlank()) {
			Text(
				modifier = Modifier.widthIn(max = CARD_WIDTH),
				text = subtitle,
				color = Color.White.copy(alpha = 0.58f),
				fontSize = 15.sp,
				fontWeight = FontWeight.Bold,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

private fun playbackProgress(item: org.jellyfin.sdk.model.api.BaseItemDto): Float {
	val runtimeTicks = item.runTimeTicks ?: return 0f
	if (runtimeTicks <= 0L) return 0f

	val positionTicks = item.userData?.playbackPositionTicks ?: return 0f
	return (positionTicks.toFloat() / runtimeTicks.toFloat()).coerceIn(0f, 1f)
}

private fun remainingTimeLabel(item: org.jellyfin.sdk.model.api.BaseItemDto?): String? {
	val runtimeTicks = item?.runTimeTicks ?: return null
	val positionTicks = item.userData?.playbackPositionTicks ?: return null
	val remainingMinutes = ((runtimeTicks - positionTicks).coerceAtLeast(0L) / TICKS_PER_MINUTE).toInt()

	return when {
		remainingMinutes <= 0 -> null
		remainingMinutes == 1 -> "1 min restante"
		else -> "$remainingMinutes min restantes"
	}
}

private val CARD_WIDTH = 330.dp
private val CARD_HEIGHT = 186.dp
private val CARD_SHAPE = RoundedCornerShape(10.dp)
private const val TICKS_PER_MINUTE = 600_000_000L
