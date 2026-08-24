package org.jellyfin.androidtv.ui.input

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RemoteKeyPressRouterTests : FunSpec({
	test("a consumed press dispatches one action and owns repeat and up") {
		val routedKeys = mutableListOf<Int>()
		val router = RemoteKeyPressRouter { keyCode ->
			routedKeys += keyCode
			true
		}

		router.route(RemoteKeyStroke(keyCode = 20, phase = RemoteKeyPhase.DOWN)) shouldBe true
		router.route(RemoteKeyStroke(keyCode = 20, phase = RemoteKeyPhase.DOWN, repeatCount = 1)) shouldBe true
		router.route(RemoteKeyStroke(keyCode = 20, phase = RemoteKeyPhase.DOWN, repeatCount = 2)) shouldBe true
		router.route(RemoteKeyStroke(keyCode = 20, phase = RemoteKeyPhase.UP)) shouldBe true

		routedKeys shouldBe listOf(20)
	}

	test("an unhandled press remains available to the normal dispatch chain") {
		var routeCount = 0
		val router = RemoteKeyPressRouter {
			routeCount++
			false
		}

		router.route(RemoteKeyStroke(keyCode = 19, phase = RemoteKeyPhase.DOWN)) shouldBe false
		router.route(RemoteKeyStroke(keyCode = 19, phase = RemoteKeyPhase.DOWN, repeatCount = 1)) shouldBe false
		router.route(RemoteKeyStroke(keyCode = 19, phase = RemoteKeyPhase.UP)) shouldBe false

		routeCount shouldBe 1
	}

	test("an orphan repeat or up is never promoted to a new action") {
		var routeCount = 0
		val router = RemoteKeyPressRouter {
			routeCount++
			true
		}

		router.route(RemoteKeyStroke(keyCode = 23, phase = RemoteKeyPhase.DOWN, repeatCount = 1)) shouldBe false
		router.route(RemoteKeyStroke(keyCode = 23, phase = RemoteKeyPhase.UP)) shouldBe false
		routeCount shouldBe 0
	}

	test("reset releases a press when Android drops its key up event") {
		var routeCount = 0
		val router = RemoteKeyPressRouter {
			routeCount++
			true
		}

		router.route(RemoteKeyStroke(keyCode = 20, phase = RemoteKeyPhase.DOWN)) shouldBe true
		router.reset()
		router.route(RemoteKeyStroke(keyCode = 20, phase = RemoteKeyPhase.DOWN)) shouldBe true

		routeCount shouldBe 2
	}
})
