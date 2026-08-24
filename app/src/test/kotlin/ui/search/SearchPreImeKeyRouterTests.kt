package org.jellyfin.androidtv.ui.search

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.ui.input.RemoteKeyPhase
import org.jellyfin.androidtv.ui.input.RemoteKeyStroke

class SearchPreImeKeyRouterTests : FunSpec({
	val backKeyCode = 4
	val otherKeyCode = 19

	test("a handled Back press owns down repeat and up as one interaction") {
		val routedKeys = mutableListOf<Int>()
		val router = SearchPreImeKeyRouter(backKeyCode) { keyCode ->
			routedKeys += keyCode
			true
		}

		router.route(RemoteKeyStroke(backKeyCode, RemoteKeyPhase.DOWN)) shouldBe true
		router.route(RemoteKeyStroke(backKeyCode, RemoteKeyPhase.DOWN, repeatCount = 1)) shouldBe true
		router.route(RemoteKeyStroke(backKeyCode, RemoteKeyPhase.UP)) shouldBe true
		routedKeys shouldBe listOf(backKeyCode)
	}

	test("an unhandled key remains available to the normal pre IME dispatch chain") {
		var routeCount = 0
		val router = SearchPreImeKeyRouter(backKeyCode) {
			routeCount++
			true
		}

		router.route(RemoteKeyStroke(otherKeyCode, RemoteKeyPhase.DOWN)) shouldBe false
		router.route(RemoteKeyStroke(otherKeyCode, RemoteKeyPhase.DOWN, repeatCount = 1)) shouldBe false
		router.route(RemoteKeyStroke(otherKeyCode, RemoteKeyPhase.UP)) shouldBe false
		routeCount shouldBe 0
	}

	test("orphan events stay unhandled and reset releases Back ownership") {
		var routeCount = 0
		val router = SearchPreImeKeyRouter(backKeyCode) {
			routeCount++
			true
		}

		router.route(RemoteKeyStroke(backKeyCode, RemoteKeyPhase.DOWN, repeatCount = 1)) shouldBe false
		router.route(RemoteKeyStroke(backKeyCode, RemoteKeyPhase.UP)) shouldBe false

		router.route(RemoteKeyStroke(backKeyCode, RemoteKeyPhase.DOWN)) shouldBe true
		router.reset()
		router.route(RemoteKeyStroke(backKeyCode, RemoteKeyPhase.UP)) shouldBe false
		router.route(RemoteKeyStroke(backKeyCode, RemoteKeyPhase.DOWN)) shouldBe true
		routeCount shouldBe 2
	}
})
