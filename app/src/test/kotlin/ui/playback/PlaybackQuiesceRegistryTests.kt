package org.jellyfin.androidtv.ui.playback

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.Dispatchers

class PlaybackQuiesceRegistryTests : FunSpec({
	test("quiescing without a created playback graph is an idempotent no-op") {
		val events = mutableListOf<String>()
		val registry = PlaybackQuiesceRegistry(Dispatchers.Unconfined)

		registry.quiesceIfCreated()
		registry.quiesceIfCreated()
		registry.register { events += "quiesce" }
		registry.quiesceIfCreated()

		events.shouldContainExactly("quiesce")
	}

	test("quiesces an already created playback graph on every session boundary") {
		val events = mutableListOf<String>()
		val registry = PlaybackQuiesceRegistry(Dispatchers.Unconfined)
		registry.register { events += "quiesce" }

		registry.quiesceIfCreated()
		registry.quiesceIfCreated()

		events.shouldContainExactly("quiesce", "quiesce")
	}
})
