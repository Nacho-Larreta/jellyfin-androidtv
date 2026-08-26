@file:UseSerializers(UUIDSerializer::class)

package org.jellyfin.androidtv.auth.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jellyfin.androidtv.auth.model.ProfileAuthenticationResultDto
import org.jellyfin.androidtv.auth.store.AuthenticationStore
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.io.IOException
import java.util.Locale
import java.util.UUID

sealed interface SessionSwitchCredential {
	val serverId: UUID
	val deviceId: String
	val token: String

	data class Active(
		val snapshot: SessionSnapshot,
	) : SessionSwitchCredential {
		override val serverId = snapshot.serverId
		override val deviceId = snapshot.deviceId
		override val token = snapshot.credential.value
	}

	data class Recovery(
		val session: OwnerRecoverySession,
	) : SessionSwitchCredential {
		override val serverId = session.serverId
		override val deviceId = session.deviceId
		override val token = session.credential.value
	}
}

interface SessionSwitchApi {
	suspend fun prepare(
		credential: SessionSwitchCredential,
		switchId: UUID,
		targetProfileUserId: UUID,
		pin: String?,
	): ServerSwitchResult

	suspend fun commit(credential: SessionSwitchCredential, switchId: UUID): ServerSwitchResult
	suspend fun status(credential: SessionSwitchCredential, switchId: UUID): ServerSwitchResult
	suspend fun abort(credential: SessionSwitchCredential, switchId: UUID): ServerSwitchResult
}

class OkHttpSessionSwitchApi(
	private val apiClient: ApiClient,
	private val authenticationStore: AuthenticationStore,
	private val okHttpFactory: OkHttpFactory,
	private val httpClientOptions: HttpClientOptions,
) : SessionSwitchApi {
	private val json = Json {
		encodeDefaults = true
		ignoreUnknownKeys = true
		serializersModule = SerializersModule {
			contextual(UUIDSerializer())
		}
	}

	override suspend fun prepare(
		credential: SessionSwitchCredential,
		switchId: UUID,
		targetProfileUserId: UUID,
		pin: String?,
	): ServerSwitchResult = execute(
		credential = credential,
		method = HttpMethod.POST,
		path = switchPath(switchId, "Prepare"),
		body = json.encodeToString(ProfileSwitchPrepareDto(targetProfileUserId, pin)),
		commitOutcomeCanBeUnknown = false,
	)

	override suspend fun commit(
		credential: SessionSwitchCredential,
		switchId: UUID,
	): ServerSwitchResult = execute(
		credential = credential,
		method = HttpMethod.POST,
		path = switchPath(switchId, "Commit"),
		body = "{}",
		commitOutcomeCanBeUnknown = true,
	)

	override suspend fun status(
		credential: SessionSwitchCredential,
		switchId: UUID,
	): ServerSwitchResult = execute(
		credential = credential,
		method = HttpMethod.GET,
		path = switchPath(switchId),
		commitOutcomeCanBeUnknown = false,
	)

	override suspend fun abort(
		credential: SessionSwitchCredential,
		switchId: UUID,
	): ServerSwitchResult = execute(
		credential = credential,
		method = HttpMethod.DELETE,
		path = switchPath(switchId),
		commitOutcomeCanBeUnknown = false,
	)

	private suspend fun execute(
		credential: SessionSwitchCredential,
		method: HttpMethod,
		path: String,
		body: String? = null,
		commitOutcomeCanBeUnknown: Boolean,
	): ServerSwitchResult = withContext(Dispatchers.IO) {
		val serverAddress = authenticationStore.getServer(credential.serverId)?.address
			?: throw SessionSwitchRejected("No server address is available for the session switch.")
		val request = Request.Builder()
			.url(serverAddress.trimEnd('/') + "/" + path)
			.header("Accept", JSON_MEDIA_TYPE.toString())
			.header("Authorization", authorizationHeader(credential))
			.applyMethod(method, body)
			.build()

		val response = try {
			okHttpFactory.createClient(httpClientOptions).newCall(request).execute()
		} catch (error: IOException) {
			if (commitOutcomeCanBeUnknown) throw SessionSwitchCommitUnknown(error)
			throw SessionSwitchRecoveryRequired("Unable to reach the profile switch endpoint.", error)
		}

		response.use { current ->
			val payload = current.body?.string().orEmpty()
			if (!current.isSuccessful) {
				val failure = SessionSwitchRejected(
					"Profile switch request failed with status ${current.code}.",
					statusCode = current.code,
				)
				if (commitOutcomeCanBeUnknown && current.code >= SERVER_ERROR_STATUS) {
					throw SessionSwitchCommitUnknown(failure)
				}
				throw failure
			}
			json.decodeFromString<ProfileSwitchResultDto>(payload).toDomain()
		}
	}

	private fun Request.Builder.applyMethod(method: HttpMethod, body: String?): Request.Builder = when (method) {
		HttpMethod.GET -> get()
		HttpMethod.POST -> post((body ?: "{}").toRequestBody(JSON_MEDIA_TYPE))
		HttpMethod.DELETE -> delete()
	}

	private fun authorizationHeader(credential: SessionSwitchCredential): String =
		AuthorizationHeaderBuilder.buildHeader(
			apiClient.clientInfo.name,
			apiClient.clientInfo.version,
			credential.deviceId,
			apiClient.deviceInfo.name,
			credential.token,
		)

	private fun switchPath(switchId: UUID, action: String? = null): String = buildString {
		append("ProfileSelectors/Current/Switches/")
		append(switchId)
		if (action != null) {
			append('/')
			append(action)
		}
	}

	private enum class HttpMethod {
		GET,
		POST,
		DELETE,
	}

	private companion object {
		const val SERVER_ERROR_STATUS = 500
		val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
	}
}

@Serializable
private data class ProfileSwitchPrepareDto(
	@SerialName("TargetProfileUserId") val targetProfileUserId: UUID,
	@SerialName("Pin") val pin: String? = null,
)

@Serializable
private data class ProfileSwitchResultDto(
	@SerialName("SwitchId") val switchId: UUID,
	@SerialName("ProfileSelectorId") val profileSelectorId: UUID,
	@SerialName("OwnerUserId") val ownerUserId: UUID,
	@SerialName("TargetProfileUserId") val targetProfileUserId: UUID,
	@SerialName("State") val state: String,
	@SerialName("AuthenticationResult") val authenticationResult: ProfileAuthenticationResultDto? = null,
) {
	fun toDomain(): ServerSwitchResult {
		val serverState = ServerSwitchState.valueOf(state.uppercase(Locale.ROOT))
		val committedAuthentication = authenticationResult?.takeIf { serverState == ServerSwitchState.COMMITTED }
		if (serverState == ServerSwitchState.COMMITTED && committedAuthentication?.user?.id != targetProfileUserId) {
			throw SessionSwitchRecoveryRequired("Committed authentication identity does not match the switch target.")
		}
		return ServerSwitchResult(
			switchId = switchId,
			profileSelectorId = profileSelectorId,
			ownerUserId = ownerUserId,
			targetProfileUserId = targetProfileUserId,
			state = serverState,
			activeCredential = committedAuthentication?.accessToken?.let(ActiveProfileCredential::fromToken),
		)
	}
}
