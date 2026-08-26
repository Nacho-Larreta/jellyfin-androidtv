package org.jellyfin.androidtv.auth.store

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.jellyfin.androidtv.auth.model.AuthenticationSessionEnvelope
import org.jellyfin.androidtv.auth.model.AuthenticationStoreServer
import org.jellyfin.androidtv.auth.model.AuthenticationStoreUser
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.util.UUID

data class AuthenticationAuthoritySnapshot(
	val generation: Long,
	val envelope: AuthenticationSessionEnvelope?,
) {
	init {
		require(generation >= 0)
	}
}

/**
 * Storage for authentication related entities. Stores servers with users inside, including
 * access tokens.
 *
 * The data is stored in a JSON file located in the applications data directory.
 */
class AuthenticationStore internal constructor(
	private val storeFile: AuthenticationStoreFile,
) {
	constructor(context: Context) : this(
		AtomicAuthenticationStoreFile(context.filesDir.resolve("authentication_store.json"))
	)

	private val json = Json {
		encodeDefaults = true
		serializersModule = SerializersModule {
			contextual(UUIDSerializer())
		}
		ignoreUnknownKeys = true
	}

	private val decoder = AuthenticationStoreDecoder(storeFile, json, CURRENT_VERSION)
	private val writer = AuthenticationStoreWriter(storeFile, json, CURRENT_VERSION)
	private val state by lazy { AuthenticationStoreState(decoder.load(), writer) }
	private val store get() = state.servers

	@Synchronized
	fun getServers(): Map<UUID, AuthenticationStoreServer> = store

	@Synchronized
	fun getUsers(server: UUID): Map<UUID, AuthenticationStoreUser>? = getServer(server)?.users

	@Synchronized
	fun getServer(serverId: UUID) = store[serverId]

	@Synchronized
	fun getUser(serverId: UUID, userId: UUID) = getUsers(serverId)?.get(userId)

	@Synchronized
	fun getAuthoritySnapshot(serverId: UUID): AuthenticationAuthoritySnapshot? =
		getServer(serverId)?.let { server ->
			AuthenticationAuthoritySnapshot(server.authorityGeneration, server.sessionEnvelope)
		}

	@Synchronized
	fun replaceSessionEnvelope(
		serverId: UUID,
		expected: AuthenticationAuthoritySnapshot,
		updatedEnvelope: AuthenticationSessionEnvelope,
		requireActiveUserToken: Boolean = false,
	): AuthenticationAuthoritySnapshot? {
		val server = store[serverId] ?: return null
		val current = AuthenticationAuthoritySnapshot(server.authorityGeneration, server.sessionEnvelope)
		if (current != expected) return null
		if (requireActiveUserToken && !server.hasMatchingActiveUser(updatedEnvelope)) return null
		if (!state.replace(store + (serverId to server.copy(sessionEnvelope = updatedEnvelope)))) return null
		return current.copy(envelope = updatedEnvelope)
	}

	@Synchronized
	fun putServer(id: UUID, server: AuthenticationStoreServer): Boolean {
		val current = store[id]
		val safeServer = if (current == null) server else server.copy(
			users = current.users,
			sessionEnvelope = current.sessionEnvelope,
			authorityGeneration = current.authorityGeneration,
		)
		return state.replace(store + (id to safeServer))
	}

	@Synchronized
	fun putUser(server: UUID, userId: UUID, userInfo: AuthenticationStoreUser): Boolean {
		val serverInfo = store[server] ?: return false
		val existing = serverInfo.users[userId] ?: return false
		val updated = serverInfo.copy(
			users = serverInfo.users + (userId to existing.withMetadataFrom(userInfo)),
		)
		return state.replace(store + (server to updated))
	}

	@Synchronized
	fun updateServer(
		id: UUID,
		create: AuthenticationStoreServer? = null,
		update: (AuthenticationStoreServer) -> AuthenticationStoreServer,
	): Boolean {
		val current = store[id] ?: create ?: return false
		val candidate = update(current)
		val safeServer = candidate.copy(
			users = current.users,
			sessionEnvelope = current.sessionEnvelope,
			authorityGeneration = current.authorityGeneration,
		)
		return state.replace(store + (id to safeServer))
	}

	@Synchronized
	fun updateUser(
		server: UUID,
		userId: UUID,
		create: AuthenticationStoreUser? = null,
		update: (AuthenticationStoreUser) -> AuthenticationStoreUser,
	): Boolean {
		val serverInfo = store[server] ?: return false
		val existing = serverInfo.users[userId]
		val updatedUser = update(existing ?: create ?: return false)
		val authorityChanged = existing != null && existing.authorityBinding() != updatedUser.authorityBinding()
		val envelope = if (authorityChanged) {
			serverInfo.sessionEnvelope.invalidateFor(setOf(userId))
		} else {
			serverInfo.sessionEnvelope
		}
		val generation = if (authorityChanged) serverInfo.nextAuthorityGeneration() ?: return false else serverInfo.authorityGeneration
		val updatedServer = serverInfo.copy(
			users = serverInfo.users + (userId to updatedUser),
			sessionEnvelope = envelope,
			authorityGeneration = generation,
		)
		return state.replace(store + (server to updatedServer))
	}

	@Synchronized
	fun invalidateUser(server: UUID, user: UUID): Boolean {
		val serverInfo = store[server] ?: return false
		val storedUser = serverInfo.users[user] ?: return false
		val generation = serverInfo.nextAuthorityGeneration() ?: return false
		val updated = serverInfo.copy(
			users = serverInfo.users + (user to storedUser.copy(accessToken = null)),
			sessionEnvelope = serverInfo.sessionEnvelope.invalidateFor(setOf(user)),
			authorityGeneration = generation,
		)
		return state.replace(store + (server to updated))
	}

	@Synchronized
	fun invalidateProfileSelector(server: UUID, ownerUserId: UUID): Boolean {
		val serverInfo = store[server] ?: return false
		val affectedUsers = serverInfo.users.filter { (userId, user) ->
			userId == ownerUserId || user.profileSelectorOwnerUserId == ownerUserId
		}.keys
		if (affectedUsers.isEmpty()) return false
		val generation = serverInfo.nextAuthorityGeneration() ?: return false
		val users = serverInfo.users.mapValues { (userId, user) ->
			if (userId in affectedUsers) user.copy(accessToken = null) else user
		}
		val updated = serverInfo.copy(
			users = users,
			sessionEnvelope = serverInfo.sessionEnvelope.invalidateFor(affectedUsers),
			authorityGeneration = generation,
		)
		return state.replace(store + (server to updated))
	}

	/**
	 * Removes the server and stored users from the credential store.
	 */
	@Synchronized
	fun removeServer(server: UUID): Boolean {
		return state.replace(store - server)
	}

	@Synchronized
	fun removeUser(server: UUID, user: UUID): Boolean {
		val serverInfo = store[server] ?: return false
		if (user !in serverInfo.users) return false
		val generation = serverInfo.nextAuthorityGeneration() ?: return false

		val updated = serverInfo.copy(
			users = serverInfo.users - user,
			sessionEnvelope = serverInfo.sessionEnvelope.invalidateFor(setOf(user)),
			authorityGeneration = generation,
		)
		return state.replace(store + (server to updated))
	}

	private companion object {
		const val CURRENT_VERSION = 2
	}
}
