package org.jellyfin.androidtv.ui.settings

import android.app.Application
import android.os.Build
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.preference.PreferenceManager
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.screensaver.ScreensaverContentPolicy
import org.jellyfin.androidtv.ui.settings.screen.screensaver.ScreensaverAgeRatingSelection
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ParentalRating
import org.jellyfin.sdk.model.api.ParentalRatingScore
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class ScreensaverAgeRatingCrossRecreationTests {
	@get:Rule
	val composeRule = createComposeRule()

	private lateinit var application: Application

	@Before
	fun clearPreferences() {
		application = RuntimeEnvironment.getApplication()
		PreferenceManager.getDefaultSharedPreferences(application).edit().clear().commit()
	}

	@Test
	fun `D-pad selection survives recreation and bounds the Dream library query`() {
		val targetAgeCeiling = 16
		val targetLabel = application.getString(R.string.pref_screensaver_ageratingmax_entry, targetAgeCeiling)
		var userPreferences = UserPreferences(application)
		val recreation = mutableIntStateOf(0)
		var completedSelections = 0

		composeRule.setContent {
			key(recreation.intValue) {
				ScreensaverAgeRatingSelection(
					userPreferences = userPreferences,
					onSelectionComplete = { completedSelections++ },
				)
			}
		}

		composeRule.onNodeWithContentDescription(targetLabel)
			.performScrollTo()
			.performSemanticsAction(SemanticsActions.RequestFocus)
			.assertIsFocused()
			.performKeyInput {
				keyDown(Key.DirectionCenter)
				keyUp(Key.DirectionCenter)
			}
		composeRule.runOnIdle {
			completedSelections shouldBe 1
		}

		val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
		sharedPreferences.all[UserPreferences.screensaverAgeRatingMax.key] shouldBe targetAgeCeiling

		composeRule.runOnIdle {
			userPreferences = UserPreferences(application)
			recreation.intValue++
		}
		composeRule.onNodeWithContentDescription(targetLabel).assertIsSelected()

		val recreatedPolicy = ScreensaverContentPolicy.fromCatalog(
			persistedAgeCeiling = userPreferences.readScreensaverAgeRatingMax(),
			parentalRatings = listOf(
				ParentalRating(
					name = targetAgeCeiling.toString(),
					value = targetAgeCeiling,
					ratingScore = ParentalRatingScore(score = targetAgeCeiling),
				),
			),
		)
		val recreatedDreamQuery = recreatedPolicy.libraryQuery(batchSize = 60)

		recreatedDreamQuery.includeItemTypes shouldContainExactly listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
		recreatedDreamQuery.imageTypes shouldContainExactly listOf(ImageType.BACKDROP)
		recreatedDreamQuery.fields shouldContainExactly listOf(ItemFields.CUSTOM_RATING)
		recreatedDreamQuery.hasParentalRating shouldBe true
		recreatedDreamQuery.maxOfficialRating shouldBe targetAgeCeiling.toString()
		recreatedDreamQuery.maxOfficialRating shouldNotBe ScreensaverContentPolicy.DEFAULT_AGE_CEILING.toString()
		recreatedDreamQuery.maxOfficialRating shouldNotBe null
	}
}
