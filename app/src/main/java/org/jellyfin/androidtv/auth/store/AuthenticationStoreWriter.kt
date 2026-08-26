package org.jellyfin.androidtv.auth.store

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import org.jellyfin.androidtv.auth.model.AuthenticationStoreServer
import timber.log.Timber
import java.io.IOException
import java.util.UUID

internal class AuthenticationStoreWriter(
	private val storeFile: AuthenticationStoreFile,
	private val json: Json,
	private val currentVersion: Int,
) {
	fun replace(servers: Map<UUID, AuthenticationStoreServer>): Boolean {
		if (!servers.values.all(AuthenticationStoreServer::hasValidAuthorityState)) {
			Timber.e("Authentication store rejected: write-authority-invariant")
			return false
		}
		val root = JsonObject(mapOf(
			"version" to JsonPrimitive(currentVersion),
			"servers" to json.encodeToJsonElement(servers),
		))
		return try {
			storeFile.replace(json.encodeToString(root))
			true
		} catch (error: IOException) {
			Timber.e(error, "Unable to atomically replace authentication store")
			false
		}
	}
}

internal class AuthenticationStoreState(
	initialServers: Map<UUID, AuthenticationStoreServer>,
	private val writer: AuthenticationStoreWriter,
) {
	var servers: Map<UUID, AuthenticationStoreServer> = initialServers
		private set

	fun replace(updatedServers: Map<UUID, AuthenticationStoreServer>): Boolean {
		if (!writer.replace(updatedServers)) return false
		servers = updatedServers
		return true
	}
}
