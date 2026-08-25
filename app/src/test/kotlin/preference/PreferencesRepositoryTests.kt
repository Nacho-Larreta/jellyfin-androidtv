package org.jellyfin.androidtv.preference

import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jellyfin.sdk.api.client.ApiClient

class PreferencesRepositoryTests : FunSpec({
	test("invalidates local preferences without fetching when no server is configured") {
		val api = mockk<ApiClient> {
			every { baseUrl } returns null
			every { accessToken } returns null
		}
		val liveTvPreferences = mockk<LiveTvPreferences>(relaxed = true)
		val userSettingPreferences = mockk<UserSettingPreferences>(relaxed = true)

		PreferencesRepository(api, liveTvPreferences, userSettingPreferences).onSessionChanged()

		verify(exactly = 1) {
			liveTvPreferences.clearCache()
			userSettingPreferences.clearCache()
		}
		coVerify(exactly = 0) {
			liveTvPreferences.update()
			userSettingPreferences.update()
		}
	}

	test("invalidates local preferences without fetching when no access token is configured") {
		val api = mockk<ApiClient> {
			every { baseUrl } returns "https://jellyfin.example"
			every { accessToken } returns "   "
		}
		val liveTvPreferences = mockk<LiveTvPreferences>(relaxed = true)
		val userSettingPreferences = mockk<UserSettingPreferences>(relaxed = true)

		PreferencesRepository(api, liveTvPreferences, userSettingPreferences).onSessionChanged()

		verify(exactly = 1) {
			liveTvPreferences.clearCache()
			userSettingPreferences.clearCache()
		}
		coVerify(exactly = 0) {
			liveTvPreferences.update()
			userSettingPreferences.update()
		}
	}

	test("invalidates then fetches remote preferences after an authenticated session is configured") {
		val api = mockk<ApiClient> {
			every { baseUrl } returns "https://jellyfin.example"
			every { accessToken } returns "access-token"
		}
		val liveTvPreferences = mockk<LiveTvPreferences>(relaxed = true)
		val userSettingPreferences = mockk<UserSettingPreferences>(relaxed = true)
		coEvery { liveTvPreferences.update() } returns true
		coEvery { userSettingPreferences.update() } returns true

		PreferencesRepository(api, liveTvPreferences, userSettingPreferences).onSessionChanged()

		coVerifyOrder {
			liveTvPreferences.clearCache()
			userSettingPreferences.clearCache()
			liveTvPreferences.update()
			userSettingPreferences.update()
		}
		coVerify(exactly = 1) {
			liveTvPreferences.update()
			userSettingPreferences.update()
		}
	}
})
