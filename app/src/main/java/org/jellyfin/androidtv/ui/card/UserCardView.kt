package org.jellyfin.androidtv.ui.card

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.LocalTextStyle
import org.jellyfin.androidtv.ui.base.ProfileAvatarActiveColor
import org.jellyfin.androidtv.ui.base.ProfilePicture
import org.jellyfin.androidtv.ui.base.ProvideTextStyle
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.profileAvatarPalette
import org.jellyfin.androidtv.util.MenuBuilder
import org.jellyfin.androidtv.util.popupMenu
import org.jellyfin.androidtv.util.showIfNotEmpty

enum class UserCardVisualStyle {
	Default,
	ProfileSelector,
}

@Suppress("LongParameterList")
@Composable
fun UserCard(
	image: @Composable () -> Unit,
	name: @Composable () -> Unit,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
	shape: Shape = CircleShape,
	focusedBorderColor: Color? = null,
	unfocusedBorderColor: Color? = null,
	focusedTextColor: Color? = null,
	unfocusedTextColor: Color? = null,
	badge: (@Composable BoxScope.() -> Unit)? = null,
	indicator: (@Composable BoxScope.() -> Unit)? = null,
) {
	val focused by interactionSource.collectIsFocusedAsState()

	val color = when {
		focused -> (focusedBorderColor ?: JellyfinTheme.colorScheme.buttonFocused) to
			(focusedTextColor ?: JellyfinTheme.colorScheme.onBackground)
		else -> (unfocusedBorderColor ?: JellyfinTheme.colorScheme.button) to
			(unfocusedTextColor ?: JellyfinTheme.colorScheme.onBackground)
	}
	val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "UserCardFocusScale")

	Column(
		modifier = modifier
			.scale(scale)
			.focusable(interactionSource = interactionSource)
			.clickable(interactionSource = interactionSource, onClick = onClick, indication = null)
	) {
		Box(
			modifier = Modifier
				.aspectRatio(1f)
				.clip(shape)
				.border(2.dp, color.first, shape)
		) {
			image()
			indicator?.invoke(this)
			badge?.invoke(this)
		}

		Spacer(Modifier.height(8.dp))

		Box(
			modifier = Modifier
				.fillMaxWidth()
				.basicMarquee(
					iterations = if (focused) Int.MAX_VALUE else 0,
					initialDelayMillis = 0,
				),
			contentAlignment = Alignment.TopCenter,
		) {
			ProvideTextStyle(
				LocalTextStyle.current.copy(
					color = color.second,
				)
			) {
				name()
			}
		}
	}
}

class UserCardView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {
	var name by mutableStateOf<String?>(null)
	var image by mutableStateOf<String?>(null)
	var badgeText by mutableStateOf<String?>(null)
	var metaText by mutableStateOf<String?>(null)
	var activeIndicator by mutableStateOf(false)
	var colorSeed by mutableStateOf<String?>(null)
	var colorIndex by mutableStateOf<Int?>(null)
	var visualStyle by mutableStateOf(UserCardVisualStyle.Default)
	private var focused by mutableStateOf(false)

	init {
		isFocusable = true
		descendantFocusability = FOCUS_BLOCK_DESCENDANTS
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) defaultFocusHighlightEnabled = false
	}

	fun setPopupMenu(init: MenuBuilder.() -> Unit) {
		setOnLongClickListener {
			popupMenu(context, this, init = init).showIfNotEmpty()
		}
	}

	override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
		if (super.onKeyUp(keyCode, event)) return true

		// Menu key should show the popup menu
		if (event.keyCode == KeyEvent.KEYCODE_MENU) return performLongClick()

		return false
	}

	override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
		super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)

		focused = gainFocus
	}

	@Suppress("LongMethod")
	@Composable
	override fun Content() {
		val interactionSource = remember { MutableInteractionSource() }

		// Forward focus events to the interaction source
		val focusInteraction = remember { FocusInteraction.Focus() }
		LaunchedEffect(focused) {
			if (focused) interactionSource.emit(focusInteraction)
			else interactionSource.emit(FocusInteraction.Unfocus(focusInteraction))
		}

		if (visualStyle == UserCardVisualStyle.ProfileSelector) {
			ProfileSelectorUserCard(interactionSource)
			return
		}

		UserCard(
			image = {
				ProfilePicture(
					url = image,
					iconPadding = PaddingValues(24.dp),
					modifier = Modifier.fillMaxSize()
				)
			},
			name = {
				Text(
					text = name.orEmpty(),
					maxLines = 1
				)
			},
			indicator = if (activeIndicator) {
				{
					Box(
						modifier = Modifier
							.padding(10.dp)
							.align(Alignment.TopStart)
							.width(12.dp)
							.height(12.dp)
							.clip(CircleShape)
							.background(JellyfinTheme.colorScheme.badge)
					)
				}
			} else null,
			badge = badgeText?.let { text ->
				{
					Box(
						modifier = Modifier
							.align(Alignment.TopEnd)
							.padding(10.dp)
							.clip(RoundedCornerShape(999.dp))
							.background(JellyfinTheme.colorScheme.scrim)
							.padding(horizontal = 8.dp, vertical = 4.dp)
					) {
						Text(
							text = text,
							maxLines = 1
						)
					}
				}
			},
			modifier = Modifier
				.padding(horizontal = 6.dp, vertical = 8.dp)
				.width(110.dp),
			interactionSource = interactionSource,
			// Forward pointer clicks from Compose to the Android View listener used by Leanback presenters.
			onClick = { this@UserCardView.performClick() }
		)
	}

	@Suppress("LongMethod")
		@Composable
		private fun ProfileSelectorUserCard(interactionSource: MutableInteractionSource) {
			val profilePalette = remember(colorIndex, colorSeed, name) {
				profileAvatarPalette(colorIndex, colorSeed ?: name.orEmpty())
			}
		val imageShape = RoundedCornerShape(18.dp)

		UserCard(
			image = {
				Box(
					modifier = Modifier
						.fillMaxSize()
						.background(Brush.linearGradient(listOf(profilePalette.start, profilePalette.end)))
				) {
					if (image.isNullOrBlank()) {
						Text(
							text = name?.trim()?.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
							color = Color.White,
							fontSize = 58.sp,
							fontWeight = FontWeight.ExtraBold,
							textAlign = TextAlign.Center,
							modifier = Modifier.align(Alignment.Center)
						)
					} else {
						ProfilePicture(
							url = image,
							iconPadding = PaddingValues(42.dp),
							modifier = Modifier.fillMaxSize()
						)
					}
				}
			},
			name = {
				Column(
					horizontalAlignment = Alignment.CenterHorizontally,
					modifier = Modifier.fillMaxWidth()
				) {
					Text(
						text = name.orEmpty(),
						fontSize = 18.sp,
						fontWeight = FontWeight.SemiBold,
						textAlign = TextAlign.Center,
						overflow = TextOverflow.Ellipsis,
						maxLines = 1,
						modifier = Modifier.fillMaxWidth()
					)

					if (!metaText.isNullOrBlank()) {
						Spacer(Modifier.height(2.dp))

						Text(
							text = metaText.orEmpty(),
							color = Color.White.copy(alpha = 0.44f),
							fontSize = 11.sp,
							fontWeight = FontWeight.Normal,
							textAlign = TextAlign.Center,
							overflow = TextOverflow.Ellipsis,
							maxLines = 1,
							modifier = Modifier.fillMaxWidth()
						)
					}
				}
			},
			indicator = if (activeIndicator) {
				{
					Box(
						modifier = Modifier
							.padding(14.dp)
							.align(Alignment.TopStart)
							.width(12.dp)
							.height(12.dp)
							.clip(CircleShape)
							.background(ProfileAvatarActiveColor)
					)
				}
			} else null,
			badge = badgeText?.let { text ->
				{
					Box(
						modifier = Modifier
							.align(Alignment.TopEnd)
							.padding(12.dp)
							.clip(RoundedCornerShape(999.dp))
							.background(JellyfinTheme.colorScheme.scrim)
							.border(1.dp, Color.White.copy(alpha = 0.32f), RoundedCornerShape(999.dp))
							.padding(horizontal = 10.dp, vertical = 5.dp)
					) {
						Text(
							text = text,
							color = Color.White,
							fontSize = 10.sp,
							fontWeight = FontWeight.Bold,
							letterSpacing = 0.5.sp,
							maxLines = 1
						)
					}
				}
			},
			modifier = Modifier
				.padding(horizontal = 12.dp, vertical = 12.dp)
				.width(128.dp),
			interactionSource = interactionSource,
			shape = imageShape,
			focusedBorderColor = Color.White,
			unfocusedBorderColor = Color.Transparent,
			focusedTextColor = Color.White,
			unfocusedTextColor = Color.White.copy(alpha = 0.82f),
			onClick = { this@UserCardView.performClick() }
		)
	}
}
