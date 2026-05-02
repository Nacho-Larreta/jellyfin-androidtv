package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.flow.filterNotNull
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.ProfileSelectorRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.ProvideTextStyle
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.profileAvatarPalette
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.navigation.ActivityDestinations
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.shared.jellyflixNavigationLabel
import org.jellyfin.androidtv.ui.shared.jellyflixPrimaryNavigationViews
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.primaryImage
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.compose.koinInject

enum class MainToolbarActiveButton {
	User,
	Home,
	Search,

	None,
}

@Composable
fun MainToolbar(
	activeButton: MainToolbarActiveButton = MainToolbarActiveButton.None,
	downFocusRequester: FocusRequester? = null,
	onNavigateDown: () -> Unit = {},
	modifier: Modifier = Modifier,
	focusRequester: FocusRequester? = null,
	showFocusVisuals: Boolean = true,
) {
	val userRepository = koinInject<UserRepository>()
	val api = koinInject<ApiClient>()

	// Prevent user image to disappear when signing out by skipping null values
	val currentUser by remember { userRepository.currentUser.filterNotNull() }.collectAsState(null)
	val userImage = remember(currentUser) { currentUser?.primaryImage?.getUrl(api) }

	MainToolbar(
		userImage = userImage,
		currentUserName = currentUser?.name.orEmpty(),
		currentUserSeed = currentUser?.id?.toString().orEmpty(),
		activeButton = activeButton,
		downFocusRequester = downFocusRequester,
		onNavigateDown = onNavigateDown,
		modifier = modifier,
		externalFocusRequester = focusRequester,
		showFocusVisuals = showFocusVisuals,
	)
}

@Suppress("LongMethod")
@Composable
private fun MainToolbar(
	userImage: String? = null,
	currentUserName: String,
	currentUserSeed: String,
	activeButton: MainToolbarActiveButton,
	downFocusRequester: FocusRequester?,
	onNavigateDown: () -> Unit,
	modifier: Modifier = Modifier,
	externalFocusRequester: FocusRequester? = null,
	showFocusVisuals: Boolean = true,
) {
	val focusRequester = externalFocusRequester ?: remember { FocusRequester() }
	val navigationRepository = koinInject<NavigationRepository>()
	val mediaManager = koinInject<MediaManager>()
	val profileSelectorRepository = koinInject<ProfileSelectorRepository>()
	val sessionRepository = koinInject<SessionRepository>()
	val userViewsRepository = koinInject<UserViewsRepository>()
	val itemLauncher = koinInject<ItemLauncher>()
	val activity = LocalActivity.current
	val currentSession by remember { sessionRepository.currentSession }.collectAsState(null)
	val profileColorIndex by produceState<Int?>(initialValue = null, currentSession) {
		val session = currentSession ?: return@produceState
		val selector = runCatching { profileSelectorRepository.getCurrentSelector(session) }.getOrNull() ?: return@produceState
		value = selector.profiles.indexOfFirst { profile -> profile.id == session.userId }.takeIf { it >= 0 }
	}
	val userViews by remember { userViewsRepository.views }.collectAsState(emptyList())
	val primaryUserViews = remember(userViews, userViewsRepository) {
		userViews.jellyflixPrimaryNavigationViews(userViewsRepository)
	}
	val activeButtonColors = ButtonDefaults.colors(
		containerColor = Color.Transparent,
		contentColor = Color.White,
		focusedContainerColor = if (showFocusVisuals) Color.White.copy(alpha = 0.16f) else Color.Transparent,
		focusedContentColor = Color.White,
	)
	val inactiveButtonColors = ButtonDefaults.colors(
		containerColor = Color.Transparent,
		contentColor = Color.White.copy(alpha = 0.66f),
		focusedContainerColor = if (showFocusVisuals) Color.White.copy(alpha = 0.16f) else Color.Transparent,
		focusedContentColor = Color.White,
	)
	val openProfileSelector = {
		if (activeButton != MainToolbarActiveButton.User) {
			mediaManager.clearAudioQueue()

			if (profileSelectorRepository.supportsProfileSelector(currentSession)) {
				activity?.let { currentActivity ->
					currentActivity.startActivity(
						ActivityDestinations.startup(
							context = currentActivity,
							hideSplash = true,
							openProfileSelector = true,
						)
					)
				}
			} else {
				sessionRepository.destroyCurrentSession()

				activity?.let { currentActivity ->
					currentActivity.startActivity(
						ActivityDestinations.startup(
							context = currentActivity,
						)
					)
					currentActivity.finishAfterTransition()
				}
			}
		}
	}

	Box(
		modifier = modifier
			.height(76.dp)
			.fillMaxWidth()
			.padding(horizontal = 54.dp)
			.onPreviewKeyEvent { handleToolbarKey(it, onNavigateDown) }
			.focusRestorer(focusRequester)
			.focusGroup(),
	) {
		Box(
			modifier = Modifier
				.width(280.dp)
				.align(Alignment.CenterStart),
			contentAlignment = Alignment.CenterStart,
		) {
			JellyflixWordmark()
		}

		ToolbarButtons(
			modifier = Modifier
				.align(Alignment.Center)
				.focusRequester(focusRequester)
		) {
			ProvideTextStyle(JellyfinTheme.typography.default.copy(fontWeight = FontWeight.SemiBold)) {
				TvHeaderNavButton(
					text = "Inicio",
					selected = activeButton == MainToolbarActiveButton.Home,
					downFocusRequester = downFocusRequester,
					onNavigateDown = onNavigateDown,
					onClick = {
						if (activeButton != MainToolbarActiveButton.Home) {
							navigationRepository.navigate(
								Destinations.home,
								replace = true,
							)
						}
					},
					colors = if (activeButton == MainToolbarActiveButton.Home) activeButtonColors else inactiveButtonColors,
				)

				primaryUserViews.forEach { userView ->
					TvHeaderNavButton(
						text = userView.jellyflixNavigationLabel(userViewsRepository),
						selected = false,
						downFocusRequester = downFocusRequester,
						onNavigateDown = onNavigateDown,
						onClick = {
							navigationRepository.navigate(
								itemLauncher.getUserViewDestination(userView),
								replace = true,
							)
						},
						colors = inactiveButtonColors,
					)
				}

				TvHeaderNavButton(
					text = "Mi lista",
					selected = false,
					downFocusRequester = downFocusRequester,
					onNavigateDown = onNavigateDown,
					onClick = {
						val defaultLibrary = primaryUserViews.firstOrNull()
						if (defaultLibrary != null) {
							navigationRepository.navigate(
								Destinations.libraryFavorites(defaultLibrary),
								replace = true,
							)
						} else {
							navigationRepository.navigate(Destinations.home, replace = true)
						}
					},
					colors = inactiveButtonColors,
				)

				TvHeaderNavButton(
					text = "Buscar",
					selected = activeButton == MainToolbarActiveButton.Search,
					downFocusRequester = downFocusRequester,
					onNavigateDown = onNavigateDown,
					onClick = {
						if (activeButton != MainToolbarActiveButton.Search) {
							navigationRepository.navigate(Destinations.search())
						}
					},
					colors = if (activeButton == MainToolbarActiveButton.Search) activeButtonColors else inactiveButtonColors,
				)
			}
		}

		Box(
			modifier = Modifier
				.width(205.dp)
				.align(Alignment.CenterEnd),
			contentAlignment = Alignment.CenterEnd,
		) {
			ProfileSwitcherButton(
				name = currentUserName.ifBlank { stringResource(R.string.jellyflix_profile_fallback) },
				image = userImage,
				colorSeed = currentUserSeed.ifBlank { currentUserName },
				colorIndex = profileColorIndex,
				selected = activeButton == MainToolbarActiveButton.User,
				downFocusRequester = downFocusRequester,
				onNavigateDown = onNavigateDown,
				onClick = openProfileSelector,
				showFocusVisuals = showFocusVisuals,
			)
		}
	}
}

@Composable
private fun JellyflixWordmark() {
	Row(
		horizontalArrangement = Arrangement.spacedBy(10.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Icon(
			imageVector = ImageVector.vectorResource(R.drawable.ic_user),
			contentDescription = null,
			tint = Color.White,
			modifier = Modifier.size(20.dp),
		)

		Text(
			text = "JELLYFIN",
			color = Color.White,
			fontSize = 22.sp,
			fontWeight = FontWeight.ExtraBold,
			letterSpacing = 3.4.sp,
			maxLines = 1,
		)
	}
}

@Composable
private fun TvHeaderNavButton(
	text: String,
	selected: Boolean,
	colors: org.jellyfin.androidtv.ui.base.button.ButtonColors,
	downFocusRequester: FocusRequester?,
	onNavigateDown: () -> Unit,
	onClick: () -> Unit,
) {
	Button(
		onClick = onClick,
		modifier = Modifier
			.focusProperties {
				downFocusRequester?.let { down = it }
				onExit = {
					if (requestedFocusDirection == FocusDirection.Down) {
						onNavigateDown()
					}
				}
			}
			.onPreviewKeyEvent { handleToolbarKey(it, onNavigateDown) },
		shape = RoundedCornerShape(999.dp),
		colors = colors,
		contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp),
	) {
		Text(
			text = text,
			fontSize = 14.sp,
			fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
			maxLines = 1,
		)
	}
}

@Composable
private fun ProfileSwitcherButton(
	name: String,
	image: String?,
	colorSeed: String,
	colorIndex: Int?,
	selected: Boolean,
	downFocusRequester: FocusRequester?,
	onNavigateDown: () -> Unit,
	onClick: () -> Unit,
	showFocusVisuals: Boolean,
) {
	val colors = ButtonDefaults.colors(
		containerColor = Color.Transparent,
		contentColor = Color.White,
		focusedContainerColor = if (showFocusVisuals) Color.White.copy(alpha = 0.12f) else Color.Transparent,
		focusedContentColor = Color.White,
	)

	Button(
		onClick = onClick,
		modifier = Modifier
			.focusProperties {
				downFocusRequester?.let { down = it }
				onExit = {
					if (requestedFocusDirection == FocusDirection.Down) {
						onNavigateDown()
					}
				}
			}
			.onPreviewKeyEvent { handleToolbarKey(it, onNavigateDown) },
		shape = RoundedCornerShape(10.dp),
		colors = colors,
		contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
	) {
		Text(
			text = name,
			color = Color.White.copy(alpha = 0.82f),
			fontSize = 15.sp,
			fontWeight = FontWeight.SemiBold,
			textAlign = TextAlign.End,
			overflow = TextOverflow.Ellipsis,
			maxLines = 1,
			modifier = Modifier.width(68.dp),
		)

		Spacer(Modifier.width(8.dp))

		ProfileAvatarBadge(
			name = name,
			image = image,
			colorSeed = colorSeed,
			colorIndex = colorIndex,
			selected = selected,
		)
	}
}

@Composable
private fun ProfileAvatarBadge(
	name: String,
	image: String?,
	colorSeed: String,
	colorIndex: Int?,
	selected: Boolean,
) {
	val imagePainter = rememberAsyncImagePainter(image)
	val imageState by imagePainter.state.collectAsState()
	val imageVisible = imageState is AsyncImagePainter.State.Success
	val palette = remember(colorIndex, colorSeed, name) { profileAvatarPalette(colorIndex, colorSeed.ifBlank { name }) }
	val shape = RoundedCornerShape(8.dp)

	Box(
		contentAlignment = Alignment.Center,
		modifier = Modifier
			.size(42.dp)
			.clip(shape)
			.background(Brush.linearGradient(listOf(palette.start, palette.end)))
			.border(
				width = if (selected) 2.dp else 0.dp,
				color = Color.White.copy(alpha = if (selected) 0.82f else 0f),
				shape = shape,
			)
	) {
		if (imageVisible) {
			Image(
				painter = imagePainter,
				contentDescription = stringResource(R.string.lbl_switch_user),
				contentScale = ContentScale.Crop,
				modifier = Modifier.fillMaxSize(),
			)
		} else {
			Text(
				text = name.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
				color = Color.White,
				fontSize = 21.sp,
				fontWeight = FontWeight.ExtraBold,
			)
		}
	}
}

private fun handleToolbarKey(
	event: KeyEvent,
	onNavigateDown: () -> Unit,
): Boolean {
	if (event.type != KeyEventType.KeyDown) return false

	return when {
		event.key == Key.DirectionDown || event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
			onNavigateDown()
			true
		}

		else -> false
	}
}
