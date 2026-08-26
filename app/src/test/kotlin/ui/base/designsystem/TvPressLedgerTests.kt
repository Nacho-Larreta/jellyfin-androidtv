package org.jellyfin.androidtv.ui.base.designsystem

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeZero
import io.kotest.matchers.shouldBe

class TvPressLedgerTests : FunSpec({
	test("activation completes once on matching key up") {
		var activations = 0
		val ledger = TvPressLedger { activations++ }

		ledger.route(TvPressEvent(23, TvPressPhase.Down)).shouldBeTrue()
		activations.shouldBeZero()
		ledger.route(TvPressEvent(23, TvPressPhase.Down, repeatCount = 1)).shouldBeTrue()
		ledger.route(TvPressEvent(23, TvPressPhase.Down, repeatCount = 2)).shouldBeTrue()
		activations.shouldBeZero()
		ledger.route(TvPressEvent(23, TvPressPhase.Up)).shouldBeTrue()
		activations shouldBe 1
	}

	test("focus loss cancels an incomplete press") {
		var activations = 0
		val ledger = TvPressLedger { activations++ }

		ledger.route(TvPressEvent(23, TvPressPhase.Down)).shouldBeTrue()
		ledger.cancel()
		ledger.route(TvPressEvent(23, TvPressPhase.Up)) shouldBe false
		activations.shouldBeZero()
	}

	test("orphan repeat and key up never become actions") {
		var activations = 0
		val ledger = TvPressLedger { activations++ }

		ledger.route(TvPressEvent(23, TvPressPhase.Down, repeatCount = 1)).shouldBeTrue()
		ledger.route(TvPressEvent(23, TvPressPhase.Up)) shouldBe false
		activations.shouldBeZero()
	}

	test("a second activation key cannot steal an active transaction") {
		var activations = 0
		val ledger = TvPressLedger { activations++ }

		ledger.route(TvPressEvent(23, TvPressPhase.Down)).shouldBeTrue()
		ledger.route(TvPressEvent(66, TvPressPhase.Down)).shouldBeTrue()
		ledger.route(TvPressEvent(66, TvPressPhase.Up)) shouldBe false
		ledger.route(TvPressEvent(23, TvPressPhase.Up)).shouldBeTrue()
		activations shouldBe 1
	}

	test("window focus loss cancels down before key up") {
		var activations = 0
		val ledger = TvPressLedger { activations++ }
		val observer = TvInputCancellation(ledger::cancel)

		ledger.route(TvPressEvent(23, TvPressPhase.Down)).shouldBeTrue()
		observer.onWindowFocusChanged(false)
		ledger.route(TvPressEvent(23, TvPressPhase.Up)) shouldBe false
		activations.shouldBeZero()
	}
})
