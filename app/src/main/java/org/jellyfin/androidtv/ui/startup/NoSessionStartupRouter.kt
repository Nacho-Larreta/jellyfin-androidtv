package org.jellyfin.androidtv.ui.startup

import java.util.UUID

internal sealed interface NoSessionStartupDestination {
	data class Server(val id: UUID) : NoSessionStartupDestination
	data object ServerSelection : NoSessionStartupDestination
}

internal class NoSessionStartupRouter(
	private val lastServerId: suspend () -> UUID?,
) {
	suspend fun route(): NoSessionStartupDestination {
		val serverId = lastServerId()
		return if (serverId == null) {
			NoSessionStartupDestination.ServerSelection
		} else {
			NoSessionStartupDestination.Server(serverId)
		}
	}
}
