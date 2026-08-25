package org.jellyfin.androidtv.ui.startup

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class NoSessionStartupRouterTests : FunSpec({
	test("routes a sessionless cold start to server selection") {
		val router = NoSessionStartupRouter(
			lastServerId = { null },
		)

		router.route() shouldBe NoSessionStartupDestination.ServerSelection
	}

	test("routes a remembered server without opening server selection") {
		val serverId = UUID.randomUUID()
		val router = NoSessionStartupRouter(
			lastServerId = { serverId },
		)

		router.route() shouldBe NoSessionStartupDestination.Server(serverId)
	}
})
