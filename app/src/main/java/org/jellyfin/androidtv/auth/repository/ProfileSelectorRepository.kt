@file:UseSerializers(UUIDSerializer::class)

package org.jellyfin.androidtv.auth.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import org.jellyfin.androidtv.auth.model.AuthenticationStoreUser
import org.jellyfin.androidtv.auth.model.ProfileActivationRequest
import org.jellyfin.androidtv.auth.model.ProfileActivationResultDto
import org.jellyfin.androidtv.auth.model.ProfileSelector
import org.jellyfin.androidtv.auth.model.ProfileSelectorDto
import org.jellyfin.androidtv.auth.model.ProfileSelectorProfileDto
import org.jellyfin.androidtv.auth.model.ProfileSelectorUser
import org.jellyfin.androidtv.auth.store.AuthenticationStore
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import timber.log.Timber
import java.io.IOException
import java.time.Instant
import java.util.UUID

sealed interface ProfileSelectorStartupAction {
	data object ContinueToApp : ProfileSelectorStartupAction
	data class ShowSelector(val selector: ProfileSelector) : ProfileSelectorStartupAction
	data class SwitchSession(val session: Session) : ProfileSelectorStartupAction
}

class ProfileSelectorApiException(
	val statusCode: Int,
	val code: String?,
	override val message: String,
) : IllegalStateException(message)

interface ProfileSelectorRepository {
	fun supportsProfileSelector(session: Session?): Boolean
	suspend fun resolveStartupAction(session: Session, forceSelector: Boolean = false): ProfileSelectorStartupAction
	suspend fun getCurrentSelector(session: Session): ProfileSelector?
	suspend fun activateProfile(session: Session, profileUserId: UUID, pin: String? = null): Session
	fun signOut(session: Session)
}

class ProfileSelectorRepositoryImpl(
	private val apiClient: ApiClient,
	private val authenticationStore: AuthenticationStore,
	private val okHttpFactory: OkHttpFactory,
	private val httpClientOptions: HttpClientOptions,
) : ProfileSelectorRepository {
	private val json = Json {
		encodeDefaults = true
		ignoreUnknownKeys = true
		serializersModule = SerializersModule {
			contextual(UUIDSerializer())
		}
	}

	override fun supportsProfileSelector(session: Session?): Boolean {
		if (session == null) {
			return false
		}

		return session.profileSelectorId != null || session.ownerUserId != null
	}

	override suspend fun resolveStartupAction(session: Session, forceSelector: Boolean): ProfileSelectorStartupAction {
		if (!forceSelector && session.isActiveProfileSession()) {
			return ProfileSelectorStartupAction.ContinueToApp
		}

		val selector = getCurrentSelector(session)
			?: return ProfileSelectorStartupAction.ContinueToApp

		if (selector.profiles.isEmpty()) {
			return ProfileSelectorStartupAction.ContinueToApp
		}

		cacheSelectorState(session.serverId, session.userId, selector)

		if (forceSelector) {
			return ProfileSelectorStartupAction.ShowSelector(selector)
		}

		val autoCandidate = resolveAutoProfile(session.serverId, selector)
		if (autoCandidate != null) {
			if (autoCandidate.id == session.userId) {
				return ProfileSelectorStartupAction.ContinueToApp
			}

			val switchedSession = activateProfile(session, autoCandidate.id)
			return ProfileSelectorStartupAction.SwitchSession(switchedSession)
		}

		return if (session.isOwnerContext()) {
			ProfileSelectorStartupAction.ShowSelector(selector)
		} else {
			ProfileSelectorStartupAction.ContinueToApp
		}
	}

	override suspend fun getCurrentSelector(session: Session): ProfileSelector? {
		val response = executeRequest(
			session = session,
			method = "GET",
			path = "ProfileSelectors/Current",
		)

		response.use { current ->
			val payload = current.body?.string().orEmpty()
			return when {
				current.isSuccessful -> json.decodeFromString<ProfileSelectorDto>(payload).toModel(session.serverId)
				current.code == 404 -> {
					clearSelectorState(session.serverId, resolveOwnerUserId(session))
					null
				}

				else -> throw parseApiError(current.code, payload)
			}
		}
	}

	override suspend fun activateProfile(session: Session, profileUserId: UUID, pin: String?): Session {
		val requestBody = encodeProfileActivationRequest(pin, json)
		val response = executeRequest(
			session = session,
			method = "POST",
			path = "ProfileSelectors/Current/Profiles/$profileUserId/Activate",
			body = requestBody,
		)

		response.use { current ->
			val payload = current.body?.string().orEmpty()
			if (!current.isSuccessful) {
				throw parseApiError(current.code, payload)
			}

			val activation = json.decodeFromString<ProfileActivationResultDto>(payload)
			val authenticationResult = requireNotNull(activation.authenticationResult.user) {
				"Profile activation completed without user payload."
			}
			val accessToken = requireNotNull(activation.authenticationResult.accessToken) {
				"Profile activation completed without access token."
			}

			cacheActivation(
				serverId = session.serverId,
				result = activation,
			)

			return Session(
				userId = authenticationResult.id,
				serverId = session.serverId,
				accessToken = accessToken,
				ownerUserId = activation.ownerUserId,
				profileSelectorId = activation.profileSelectorId,
			)
		}
	}

	override fun signOut(session: Session) {
		val ownerUserId = resolveOwnerUserId(session) ?: return
		val users = authenticationStore.getUsers(session.serverId).orEmpty()

		for ((userId, userInfo) in users) {
			if (userId != ownerUserId && userInfo.profileSelectorOwnerUserId != ownerUserId) {
				continue
			}

			authenticationStore.putUser(
				session.serverId,
				userId,
				userInfo.copy(accessToken = null)
			)
		}
	}

	private suspend fun executeRequest(
		session: Session,
		method: String,
		path: String,
		body: String? = null,
	) = withContext(Dispatchers.IO) {
		val client = okHttpFactory.createClient(httpClientOptions)
		val request = Request.Builder()
			.url(buildUrl(session, path))
			.header("Accept", JSON_MEDIA_TYPE.toString())
			.header("Authorization", buildAuthorizationHeader(session.accessToken))
			.apply {
				when (method) {
					"GET" -> get()
					"POST" -> post((body ?: "{}").toRequestBody(JSON_MEDIA_TYPE))
					else -> error("Unsupported HTTP method: $method")
				}
			}
			.build()

		try {
			client.newCall(request).execute()
		} catch (error: IOException) {
			Timber.e(error, "Profile selector request failed: %s %s", method, path)
			throw ProfileSelectorApiException(
				statusCode = 500,
				code = "PROFILE_SELECTOR_NETWORK_ERROR",
				message = "Unable to reach the Jellyfin profile selector endpoint.",
			)
		}
	}

	private fun buildUrl(session: Session, path: String): String {
		val baseUrl = apiClient.baseUrl ?: authenticationStore.getServer(session.serverId)?.address
			?: throw ProfileSelectorApiException(
				statusCode = 500,
				code = "PROFILE_SELECTOR_SERVER_MISSING",
				message = "No server address is available for the current session.",
			)

		return baseUrl.trimEnd('/') + "/" + path
	}

	private fun buildAuthorizationHeader(accessToken: String): String = AuthorizationHeaderBuilder.buildHeader(
		apiClient.clientInfo.name,
		apiClient.clientInfo.version,
		apiClient.deviceInfo.id,
		apiClient.deviceInfo.name,
		accessToken,
	)

	private fun parseApiError(statusCode: Int, payload: String): ProfileSelectorApiException {
		val problem = runCatching { json.decodeFromString<ProblemDto>(payload) }.getOrNull()
		return ProfileSelectorApiException(
			statusCode = statusCode,
			code = problem?.code ?: problem?.title,
			message = problem?.detail ?: "Profile selector request failed with status $statusCode.",
		)
	}

	private fun resolveAutoProfile(serverId: UUID, selector: ProfileSelector): ProfileSelectorUser? = resolveAutoProfileCandidate(
		selector = selector,
		rememberedProfileUserId = authenticationStore
			.getUser(serverId, selector.ownerUserId)
			?.profileSelectorLastProfileUserId
			?: selector.currentDeviceProfileUserId,
	)

	private fun cacheSelectorState(serverId: UUID, currentUserId: UUID, selector: ProfileSelector) {
		val activeProfileUserId = selector.currentDeviceProfileUserId
		val owner = authenticationStore.getUser(serverId, selector.ownerUserId)
		if (owner != null) {
			authenticationStore.putUser(
				serverId,
				selector.ownerUserId,
				owner.copy(
					profileSelectorId = selector.id,
					profileSelectorLastProfileUserId = activeProfileUserId,
					profileSelectorOwnerUserId = selector.ownerUserId,
				)
			)
		}

		val currentUser = authenticationStore.getUser(serverId, currentUserId)
		if (currentUser != null) {
			authenticationStore.putUser(
				serverId,
				currentUserId,
				currentUser.copy(
					profileSelectorId = selector.id,
					profileSelectorOwnerUserId = selector.ownerUserId,
				)
			)
		}
	}

	private fun clearSelectorState(serverId: UUID, ownerUserId: UUID?) {
		if (ownerUserId == null) {
			return
		}

		val owner = authenticationStore.getUser(serverId, ownerUserId) ?: return
		authenticationStore.putUser(
			serverId,
			ownerUserId,
			owner.copy(
				profileSelectorId = null,
				profileSelectorLastProfileUserId = null,
				profileSelectorOwnerUserId = null,
			)
		)
	}

	private fun cacheActivation(serverId: UUID, result: ProfileActivationResultDto) {
		val now = Instant.now().toEpochMilli()
		val activeProfile = requireNotNull(result.authenticationResult.user) {
			"Profile activation did not return user information."
		}
		val accessToken = requireNotNull(result.authenticationResult.accessToken) {
			"Profile activation did not return an access token."
		}
		val rememberedProfileUserId = activeProfile.id.takeIf { it == result.ownerUserId }

		authenticationStore.getUser(serverId, result.ownerUserId)?.let { owner ->
			authenticationStore.putUser(
				serverId,
				result.ownerUserId,
				owner.copy(
					lastUsed = now,
					profileSelectorId = result.profileSelectorId,
					profileSelectorLastProfileUserId = activeProfile.id,
					profileSelectorOwnerUserId = result.ownerUserId,
				)
			)
		}

		val existingProfileUser = authenticationStore.getUser(serverId, activeProfile.id)
		val updatedProfileUser = existingProfileUser?.copy(
			name = activeProfile.name ?: existingProfileUser.name,
			lastUsed = now,
			imageTag = activeProfile.primaryImageTag ?: existingProfileUser.imageTag,
			accessToken = accessToken,
			profileSelectorId = result.profileSelectorId,
			profileSelectorLastProfileUserId = rememberedProfileUserId,
			profileSelectorOwnerUserId = result.ownerUserId,
		) ?: AuthenticationStoreUser(
			name = activeProfile.name ?: "Profile",
			lastUsed = now,
			imageTag = activeProfile.primaryImageTag,
			accessToken = accessToken,
			profileSelectorId = result.profileSelectorId,
			profileSelectorLastProfileUserId = rememberedProfileUserId,
			profileSelectorOwnerUserId = result.ownerUserId,
		)

		authenticationStore.putUser(serverId, activeProfile.id, updatedProfileUser)
	}

	private fun resolveOwnerUserId(session: Session): UUID? =
		session.ownerUserId ?: session.userId

	private fun Session.isActiveProfileSession(): Boolean =
		ownerUserId != null && ownerUserId != userId

	private fun Session.isOwnerContext(): Boolean =
		ownerUserId == null || ownerUserId == userId

	private fun ProfileSelectorDto.toModel(serverId: UUID): ProfileSelector = ProfileSelector(
		id = profileSelectorId,
		ownerUserId = ownerUserId,
		ownerUserName = ownerUserName,
		isCurrentUserOwner = isCurrentUserOwner,
		canManageProfiles = canManageProfiles,
		autoSelectSingleProfile = autoSelectSingleProfile,
		currentDeviceProfileUserId = currentDeviceProfileUserId,
		profiles = profiles
			.filter(ProfileSelectorProfileDto::isVisible)
			.sortedWith(
				compareByDescending<ProfileSelectorProfileDto> { it.isOwner }
					.thenBy(ProfileSelectorProfileDto::displayOrder)
			)
			.map { profile ->
				ProfileSelectorUser(
					id = profile.profileUserId,
					serverId = serverId,
					name = profile.name,
					accessToken = null,
					imageTag = profile.primaryImageTag,
					ownerUserId = ownerUserId,
					profileSelectorId = profileSelectorId,
					displayOrder = profile.displayOrder,
					requiresPin = profile.requiresPin,
					isActive = profile.isActive,
					isOwner = profile.isOwner,
					isDisabled = profile.isDisabled,
					hasParentalRestrictions = profile.hasParentalRestrictions,
				)
			},
	)

	@Serializable
	private data class ProblemDto(
		val title: String? = null,
		val detail: String? = null,
		val code: String? = null,
	)

	private companion object {
		val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
	}
}

internal fun encodeProfileActivationRequest(pin: String?, json: Json = Json): String {
	val request = ProfileActivationRequest(pin = pin)
	if (!request.hasValidPinFormat()) {
		throw ProfileSelectorApiException(
			statusCode = 400,
			code = "PROFILE_PIN_INVALID_FORMAT",
			message = "Profile PIN must contain 4 to 8 ASCII digits.",
		)
	}

	return json.encodeToString(request)
}

internal fun resolveAutoProfileCandidate(
	selector: ProfileSelector,
	rememberedProfileUserId: UUID?,
): ProfileSelectorUser? {
	val rememberedProfile = rememberedProfileUserId?.let { rememberedId ->
		selector.profiles.firstOrNull { profile ->
			profile.id == rememberedId && !profile.isDisabled && !profile.requiresPin
		}
	}
	if (rememberedProfile != null) {
		return rememberedProfile
	}

	val eligibleProfiles = selector.profiles.filter { !it.isDisabled && !it.requiresPin }
	return when {
		selector.autoSelectSingleProfile && eligibleProfiles.size == 1 -> eligibleProfiles.single()
		else -> null
	}
}
