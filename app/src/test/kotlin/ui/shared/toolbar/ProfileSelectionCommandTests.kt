package org.jellyfin.androidtv.ui.shared.toolbar

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly

class ProfileSelectionCommandTests : FunSpec({
	test("prepares the session before opening the profile selector") {
		val events = mutableListOf<String>()

		command(events, supportsProfileSelector = true).execute()

		events.shouldContainExactly("prepare-profile-selection", "open-selector")
	}

	test("destroys the session before startup navigation and finishing the activity") {
		val events = mutableListOf<String>()

		command(events, supportsProfileSelector = false).execute()

		events.shouldContainExactly("destroy-session", "open-startup", "finish-activity")
	}
})

private fun command(events: MutableList<String>, supportsProfileSelector: Boolean) = ProfileSelectionCommand(
	supportsProfileSelector = { supportsProfileSelector },
	prepareForProfileSelection = { events += "prepare-profile-selection" },
	openStartup = { openProfileSelector -> events += if (openProfileSelector) "open-selector" else "open-startup" },
	destroySession = { events += "destroy-session" },
	finishActivity = { events += "finish-activity" },
)
