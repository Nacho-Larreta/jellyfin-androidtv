package org.jellyfin.androidtv.ui.search.composable

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.LocalTextStyle
import org.jellyfin.androidtv.ui.base.ProvideTextStyle
import org.jellyfin.androidtv.ui.base.Text

@Composable
fun SearchTextInput(
	query: String,
	onQueryChange: (query: String) -> Unit,
	onQuerySubmit: () -> Unit,
	modifier: Modifier = Modifier,
	placeholder: String = "",
	canFocus: Boolean = true,
	forceFocused: Boolean = false,
	showKeyboardOnFocus: Boolean = true,
	onFocusChange: (focused: Boolean) -> Unit = {},
	onKeyPressed: (keyCode: Int) -> Boolean = { false },
) {
	val interactionSource = remember { MutableInteractionSource() }
	val inputFocused by interactionSource.collectIsFocusedAsState()
	val focused = forceFocused || (canFocus && inputFocused)

	fun isTvNavigationKey(keyCode: Int): Boolean =
		keyCode == AndroidKeyEvent.KEYCODE_BACK ||
			keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP ||
			keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN ||
			keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT ||
			keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT ||
			keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
			keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
			keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER

	fun handleTvNavigation(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
		val keyCode = event.nativeKeyEvent.keyCode

		return when (event.type) {
			KeyEventType.KeyDown -> isTvNavigationKey(keyCode) && onKeyPressed(keyCode)
			KeyEventType.KeyUp -> isTvNavigationKey(keyCode)
			else -> false
		}
	}

	val color = when {
		focused -> JellyfinTheme.colorScheme.inputFocused to JellyfinTheme.colorScheme.onInputFocused
		else -> JellyfinTheme.colorScheme.input to JellyfinTheme.colorScheme.onInput
	}

	ProvideTextStyle(
		LocalTextStyle.current.copy(
			color = color.second,
			fontSize = 20.sp,
		)
	) {
		BasicTextField(
			modifier = modifier
				.semantics { contentDescription = placeholder }
				.onPreviewKeyEvent(::handleTvNavigation)
				.onFocusChanged { onFocusChange(it.hasFocus) }
				.focusProperties { this.canFocus = canFocus },
			value = query,
			singleLine = true,
			readOnly = !canFocus,
			interactionSource = interactionSource,
			onValueChange = { onQueryChange(it) },
			keyboardActions = KeyboardActions { onQuerySubmit() },
			keyboardOptions = KeyboardOptions.Default.copy(
				keyboardType = KeyboardType.Text,
				imeAction = ImeAction.Search,
				autoCorrectEnabled = true,
				showKeyboardOnFocus = showKeyboardOnFocus,
			),
			textStyle = LocalTextStyle.current,
			cursorBrush = SolidColor(color.first),
			decorationBox = { innerTextField ->
				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier
						.padding(horizontal = 22.dp, vertical = 18.dp)
				) {
					Icon(
						ImageVector.vectorResource(R.drawable.ic_search),
						contentDescription = null,
						modifier = Modifier.size(28.dp),
					)
					Spacer(Modifier.width(16.dp))
					Box(modifier = Modifier.weight(1f)) {
						if (query.isEmpty() && placeholder.isNotBlank()) {
							Text(
								text = placeholder,
								color = color.second.copy(alpha = 0.42f),
								fontSize = 20.sp,
								maxLines = 1,
							)
						}
						innerTextField()
					}
				}
			}
		)
	}
}
