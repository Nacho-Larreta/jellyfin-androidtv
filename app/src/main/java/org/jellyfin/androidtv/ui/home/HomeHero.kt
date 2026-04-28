package org.jellyfin.androidtv.ui.home

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItemSelectAction
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.compose.koinInject

@Composable
fun HomeHero(
	state: HomeHeroState,
	modifier: Modifier = Modifier,
	focusRequester: FocusRequester,
) {
	when (state) {
		is HomeHeroState.Content -> HomeHeroContent(
			items = state.items,
			focusRequester = focusRequester,
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
	focusRequester: FocusRequester,
	modifier: Modifier = Modifier,
) {
	if (items.isEmpty()) return

	var selectedIndex by remember(items) { mutableIntStateOf(0) }
	val selectedItem = items[selectedIndex.coerceIn(items.indices)]
	val context = LocalContext.current
	val itemLauncher = koinInject<ItemLauncher>()
	val launchSelected = {
		val rowItems = items.map { item ->
			BaseItemDtoBaseRowItem(
				item = item.baseItem,
				staticHeight = true,
				selectAction = BaseRowItemSelectAction.Play,
			)
		}
		itemLauncher.launch(rowItems[selectedIndex], MutableObjectAdapter<Any>().apply { rowItems.forEach(::add) }, context)
	}

	Box(
		modifier = modifier
			.height(360.dp)
			.fillMaxWidth()
			.focusRestorer(focusRequester)
			.focusGroup()
			.clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
			.background(Color(0xFF080808))
	) {
		HomeHeroBackdrop(selectedItem)

		Row(
			modifier = Modifier
				.fillMaxSize()
				.padding(start = 56.dp, end = 48.dp, top = 18.dp, bottom = 26.dp),
			verticalAlignment = Alignment.Bottom,
		) {
			HomeHeroCopy(
				item = selectedItem,
				onResume = launchSelected,
				focusRequester = focusRequester,
				modifier = Modifier
					.weight(1f)
					.padding(bottom = 10.dp),
			)

			Spacer(Modifier.width(34.dp))

			HomeHeroResumeRail(
				items = items.take(HERO_RAIL_LIMIT),
				selectedIndex = selectedIndex,
				onSelected = { selectedIndex = it },
				onLaunch = { index ->
					selectedIndex = index
					val rowItem = BaseItemDtoBaseRowItem(
						item = items[index].baseItem,
						staticHeight = true,
						selectAction = BaseRowItemSelectAction.Play,
					)
					itemLauncher.launch(rowItem, MutableObjectAdapter<Any>().apply { add(rowItem) }, context)
				},
			)
		}
	}

	LaunchedEffect(items) {
		focusRequester.requestFocus()
	}
}

@Composable
private fun HomeHeroBackdrop(item: HomeHeroItemData) {
	val api = koinInject<ApiClient>()
	val backdrop = item.backdrop

	if (backdrop != null) {
		AsyncImage(
			modifier = Modifier.fillMaxSize(),
			url = backdrop.getUrl(api, fillWidth = 1280, fillHeight = 720),
			blurHash = backdrop.blurHash,
			aspectRatio = 16f / 9f,
			scaleType = ImageView.ScaleType.CENTER_CROP,
		)
	} else {
		Box(
			modifier = Modifier
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
					0f to Color.Black.copy(alpha = 0.90f),
					0.50f to Color.Black.copy(alpha = 0.58f),
					1f to Color.Black.copy(alpha = 0.16f),
				)
			)
	)
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(
				Brush.verticalGradient(
					0f to Color.Black.copy(alpha = 0.18f),
					0.62f to Color.Transparent,
					1f to Color(0xFF050505),
				)
			)
	)
}

@Composable
private fun HomeHeroCopy(
	item: HomeHeroItemData,
	onResume: () -> Unit,
	focusRequester: FocusRequester,
	modifier: Modifier = Modifier,
) {
	val api = koinInject<ApiClient>()

	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.Bottom,
	) {
		Text(
			text = stringResource(R.string.lbl_continue_watching).uppercase(),
			color = Color.White.copy(alpha = 0.72f),
			fontSize = 13.sp,
			fontWeight = FontWeight.Bold,
			letterSpacing = 2.4.sp,
			maxLines = 1,
		)

		Spacer(Modifier.height(12.dp))

		if (item.logo != null) {
			AsyncImage(
				modifier = Modifier
					.height(82.dp)
					.fillMaxWidth(0.56f),
				url = item.logo.getUrl(api, maxWidth = 520),
				blurHash = item.logo.blurHash,
				aspectRatio = item.logo.aspectRatio ?: 3.6f,
				scaleType = ImageView.ScaleType.FIT_START,
			)
		} else {
			Text(
				text = item.title,
				color = Color.White,
				fontSize = 42.sp,
				fontWeight = FontWeight.ExtraBold,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
			)
		}

		Spacer(Modifier.height(12.dp))

		item.subtitle?.let { subtitle ->
			Text(
				text = subtitle,
				color = Color.White.copy(alpha = 0.80f),
				fontSize = 16.sp,
				fontWeight = FontWeight.SemiBold,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			Spacer(Modifier.height(12.dp))
		}

		item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
			Text(
				text = overview,
				color = Color.White.copy(alpha = 0.78f),
				fontSize = 15.sp,
				lineHeight = 20.sp,
				maxLines = 3,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.fillMaxWidth(0.60f),
			)
			Spacer(Modifier.height(18.dp))
		}

		HomeHeroProgress(item.progress)
		Spacer(Modifier.height(18.dp))

		Button(
			onClick = onResume,
			modifier = Modifier.focusRequester(focusRequester),
			shape = RoundedCornerShape(8.dp),
			colors = ButtonDefaults.colors(
				containerColor = Color.White,
				contentColor = Color.Black,
				focusedContainerColor = Color.White,
				focusedContentColor = Color.Black,
			),
			contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
		) {
			Icon(
				imageVector = ImageVector.vectorResource(R.drawable.ic_play),
				contentDescription = null,
				tint = Color.Black,
				modifier = Modifier.size(20.dp),
			)
			Spacer(Modifier.width(8.dp))
			Text(
				text = item.resumeLabel,
				fontSize = 16.sp,
				fontWeight = FontWeight.Bold,
				maxLines = 1,
			)
		}
	}
}

@Composable
private fun HomeHeroProgress(progress: Float) {
	Box(
		modifier = Modifier
			.width(260.dp)
			.height(5.dp)
			.clip(RoundedCornerShape(999.dp))
			.background(Color.White.copy(alpha = 0.24f))
	) {
		Box(
			modifier = Modifier
				.fillMaxHeight()
				.fillMaxWidth(progress.coerceIn(0f, 1f))
				.background(JellyfinTheme.colorScheme.rangeControlFill)
		)
	}
}

@Composable
private fun HomeHeroResumeRail(
	items: List<HomeHeroItemData>,
	selectedIndex: Int,
	onSelected: (Int) -> Unit,
	onLaunch: (Int) -> Unit,
) {
	Column(
		modifier = Modifier
			.width(330.dp)
			.padding(bottom = 10.dp),
		verticalArrangement = Arrangement.spacedBy(10.dp),
	) {
		items.forEachIndexed { index, item ->
			HomeHeroResumeCard(
				item = item,
				selected = selectedIndex == index,
				onFocus = { onSelected(index) },
				onClick = { onLaunch(index) },
			)
		}
	}
}

@Composable
private fun HomeHeroResumeCard(
	item: HomeHeroItemData,
	selected: Boolean,
	onFocus: () -> Unit,
	onClick: () -> Unit,
) {
	val api = koinInject<ApiClient>()
	val poster = item.poster

	Button(
		onClick = onClick,
		modifier = Modifier
			.fillMaxWidth()
			.height(68.dp)
			.onFocusChanged { if (it.isFocused) onFocus() }
			.border(
				width = if (selected) 1.5.dp else 1.dp,
				color = if (selected) Color.White.copy(alpha = 0.70f) else Color.White.copy(alpha = 0.12f),
				shape = RoundedCornerShape(10.dp),
			),
		shape = RoundedCornerShape(10.dp),
		colors = ButtonDefaults.colors(
			containerColor = Color.Black.copy(alpha = 0.45f),
			contentColor = Color.White,
			focusedContainerColor = Color.White.copy(alpha = 0.18f),
			focusedContentColor = Color.White,
		),
		contentPadding = PaddingValues(8.dp),
	) {
		if (poster != null) {
			AsyncImage(
				modifier = Modifier
					.height(52.dp)
					.aspectRatio(poster.aspectRatio ?: 2f / 3f)
					.clip(RoundedCornerShape(6.dp)),
				url = poster.getUrl(api, maxHeight = 140),
				blurHash = poster.blurHash,
				aspectRatio = poster.aspectRatio ?: 2f / 3f,
				scaleType = ImageView.ScaleType.CENTER_CROP,
			)
		}

		Spacer(Modifier.width(10.dp))

		Column(
			modifier = Modifier.weight(1f),
			verticalArrangement = Arrangement.Center,
		) {
			Text(
				text = item.title,
				fontSize = 14.sp,
				fontWeight = FontWeight.Bold,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			item.subtitle?.let { subtitle ->
				Text(
					text = subtitle,
					color = Color.White.copy(alpha = 0.70f),
					fontSize = 12.sp,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
		}
	}
}

private const val HERO_RAIL_LIMIT = 4
