package org.jellyfin.androidtv.auth.session

import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.sdk.api.client.ApiClient

class SessionRepositoryRuntimePort(
	private val sessionRepository: SessionRepository,
	private val apiClient: ApiClient,
) : SessionSwitchRuntimePort {
	override fun currentSnapshot(): SessionSnapshot? =
		sessionRepository.currentSession.value?.let { session ->
			SessionSnapshot(
				serverId = session.serverId,
				deviceId = session.deviceId ?: apiClient.deviceInfo.id,
				profileUserId = session.userId,
				credential = ActiveProfileCredential.fromToken(session.accessToken),
				sessionEpoch = session.sessionEpoch,
				profileSelectorId = session.profileSelectorId,
				ownerUserId = session.ownerUserId,
			)
		}

	override suspend fun installCommitted(snapshot: SessionSnapshot): Boolean =
		sessionRepository.installCommittedSession(snapshot)
}
