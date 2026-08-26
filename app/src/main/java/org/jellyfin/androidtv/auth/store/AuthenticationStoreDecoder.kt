package org.jellyfin.androidtv.auth.store

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jellyfin.androidtv.auth.model.AuthenticationStoreServer
import timber.log.Timber
import java.io.IOException
import java.util.UUID

internal class AuthenticationStoreDecoder(
	private val storeFile: AuthenticationStoreFile,
	private val json: Json,
	private val currentVersion: Int,
) {
	fun load(): Map<UUID, AuthenticationStoreServer> {
		if (!storeFile.exists()) return emptyMap()
		val root = parseRoot() ?: return emptyMap()
		if (root["version"].versionOrNull() != currentVersion) return reject("version")
		val servers = decodeServers(root["servers"] ?: return reject("servers-missing"))
			?: return emptyMap()
		return if (servers.values.all(AuthenticationStoreServer::hasValidAuthorityState)) {
			servers
		} else {
			reject("authority-invariant")
		}
	}

	private fun parseRoot(): JsonObject? = try {
		json.parseToJsonElement(storeFile.readText()).jsonObject
	} catch (_: SerializationException) {
		rejectNull("parse")
	} catch (_: IllegalArgumentException) {
		rejectNull("root-schema")
	} catch (_: IOException) {
		rejectNull("read")
	}

	private fun decodeServers(element: JsonElement): Map<UUID, AuthenticationStoreServer>? = try {
		json.decodeFromJsonElement(element)
	} catch (_: SerializationException) {
		rejectNull("servers-decode")
	} catch (_: IllegalArgumentException) {
		rejectNull("servers-schema")
	}

	private fun reject(code: String): Map<UUID, AuthenticationStoreServer> {
		Timber.e("Authentication store rejected: %s", code)
		return emptyMap()
	}

	private fun rejectNull(code: String): Nothing? {
		Timber.e("Authentication store rejected: %s", code)
		return null
	}
}

private fun JsonElement?.versionOrNull(): Int? = try {
	this?.jsonPrimitive?.intOrNull
} catch (_: IllegalArgumentException) {
	null
}
