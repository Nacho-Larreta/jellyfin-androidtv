package org.jellyfin.androidtv.ui.search

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

class SearchFocusReclaimHandshakeTests : FunSpec({
	test("focus requested before Compose is ready is replayed when the handler installs") {
		val handshake = SearchFocusReclaimHandshake()

		handshake.reclaim().shouldBeFalse()
		handshake.install { true }.shouldBeTrue()
		handshake.reclaim().shouldBeTrue()
	}

	test("a ready Compose owner needs no installation-time replay") {
		val handshake = SearchFocusReclaimHandshake()

		handshake.install { true }.shouldBeFalse()
		handshake.reclaim().shouldBeTrue()
	}

	test("a failed Compose claim remains pending for the next ready handler") {
		val handshake = SearchFocusReclaimHandshake()

		handshake.install { false }.shouldBeFalse()
		handshake.reclaim().shouldBeFalse()
		handshake.install { true }.shouldBeTrue()
		handshake.reclaim().shouldBeTrue()
	}

	test("clearing the handshake removes the stale handler and pending request") {
		val handshake = SearchFocusReclaimHandshake()
		handshake.install { true }
		handshake.reclaim().shouldBeTrue()

		handshake.clear()

		handshake.install { true }.shouldBeFalse()
	}
})
