package org.jellyfin.androidtv.ui.settings.screen.screensaver

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.RadioButton
import org.jellyfin.androidtv.ui.base.list.ListControl
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject

@Composable
fun SettingsScreensaverAgeRatingScreen() {
	val router = LocalRouter.current
	val userPreferences = koinInject<UserPreferences>()
	val selectedAgeRating = remember { userPreferences.readScreensaverAgeRatingMax() }
	val options = getScreensaverAgeRatingOptions()
	LaunchedEffect(Unit) {
		userPreferences[UserPreferences.screensaverAgeRatingMax] = selectedAgeRating
	}

	SettingsColumn(modifier = Modifier.selectableGroup()) {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_screensaver).uppercase()) },
				headingContent = { Text(stringResource(R.string.pref_screensaver_ageratingmax)) },
			)
		}

		items(options) { (ageRating, heading) ->
			ScreensaverAgeRatingOption(
				heading = heading,
				selected = selectedAgeRating == ageRating,
				onClick = {
					userPreferences[UserPreferences.screensaverAgeRatingMax] = ageRating
					router.back()
				}
			)
		}
	}
}

@Composable
private fun ScreensaverAgeRatingOption(
	heading: String,
	selected: Boolean,
	onClick: () -> Unit,
) {
	val interactionSource = remember { MutableInteractionSource() }

	ListControl(
		headingContent = { Text(heading) },
		trailingContent = { RadioButton(checked = selected) },
		interactionSource = interactionSource,
		modifier = Modifier
			.selectable(
				selected = selected,
				interactionSource = interactionSource,
				indication = null,
				role = Role.RadioButton,
				onClick = onClick,
			)
			.semantics { contentDescription = heading },
	)
}
