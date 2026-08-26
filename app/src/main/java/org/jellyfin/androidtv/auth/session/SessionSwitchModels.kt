package org.jellyfin.androidtv.auth.session

import java.util.UUID

@JvmInline
value class ActiveProfileCredential internal constructor(internal val value: String) {
	init {
		require(value.isNotBlank())
	}

	companion object {
		fun fromToken(token: String) = ActiveProfileCredential(token)
	}

	override fun toString(): String = "ActiveProfileCredential(REDACTED)"
}

@JvmInline
value class OwnerRecoveryCredential internal constructor(internal val value: String) {
	init {
		require(value.isNotBlank())
	}

	companion object {
		fun fromToken(token: String) = OwnerRecoveryCredential(token)
	}

	override fun toString(): String = "OwnerRecoveryCredential(REDACTED)"
}

data class SessionSnapshot(
	val serverId: UUID,
	val deviceId: String,
	val profileUserId: UUID,
	val credential: ActiveProfileCredential,
	val sessionEpoch: Long,
	val profileSelectorId: UUID? = null,
	val ownerUserId: UUID? = null,
) {
	init {
		require(deviceId.isNotBlank())
		require(sessionEpoch >= 0)
	}
}

data class OwnerRecoverySession(
	val serverId: UUID,
	val deviceId: String,
	val ownerUserId: UUID,
	val profileSelectorId: UUID,
	val credential: OwnerRecoveryCredential,
) {
	init {
		require(deviceId.isNotBlank())
	}
}

sealed interface SessionSwitchAuthority {
	data object ActiveProfile : SessionSwitchAuthority
	data object OwnerRecovery : SessionSwitchAuthority
}

data class SessionSwitchRequest(
	val switchId: UUID,
	val targetProfileUserId: UUID,
	val pin: String? = null,
	val authority: SessionSwitchAuthority = SessionSwitchAuthority.ActiveProfile,
) {
	override fun toString(): String =
		"SessionSwitchRequest(switchId=$switchId, targetProfileUserId=$targetProfileUserId, pin=REDACTED, authority=$authority)"
}

enum class PendingSwitchPhase {
	PREPARING,
	QUIESCING,
	COMMITTING,
	COMMIT_UNKNOWN,
	INSTALLING,
}

data class PendingSwitchRecord(
	val switchId: UUID,
	val targetProfileUserId: UUID,
	val oldProfileUserId: UUID,
	val oldSessionEpoch: Long,
	val phase: PendingSwitchPhase,
	val createdAtEpochMillis: Long,
	val authority: SessionSwitchAuthority = SessionSwitchAuthority.ActiveProfile,
)

data class CommittedPendingCleanup(
	val switchId: UUID,
)

data class SessionEnvelope(
	val activeProfile: SessionSnapshot,
	val ownerRecovery: OwnerRecoverySession? = null,
	val pendingSwitch: PendingSwitchRecord? = null,
	val cleanupMarker: CommittedPendingCleanup? = null,
	val authorityGeneration: Long = 0,
) {
	init {
		require(authorityGeneration >= 0)
	}
}

enum class ServerSwitchState {
	PREPARED,
	COMMITTED,
	EXPIRED,
	ABORTED,
}

data class ServerSwitchResult(
	val switchId: UUID,
	val profileSelectorId: UUID,
	val ownerUserId: UUID,
	val targetProfileUserId: UUID,
	val state: ServerSwitchState,
	val activeCredential: ActiveProfileCredential? = null,
)

sealed interface SessionSwitchOutcome {
	data class CommittedPendingCleanup(
		val switchId: UUID,
		val snapshot: SessionSnapshot,
	) : SessionSwitchOutcome

	data class Completed(
		val switchId: UUID,
		val snapshot: SessionSnapshot,
	) : SessionSwitchOutcome

	data class Restored(val snapshot: SessionSnapshot) : SessionSwitchOutcome
}

class SwitchAlreadyInProgress : IllegalStateException("A different profile switch is already in progress.")

class SessionSwitchInProgress : IllegalStateException("Profile-scoped work is blocked while the session is switching.")

class SessionSwitchRecoveryRequired(message: String, cause: Throwable? = null) :
	IllegalStateException(message, cause)

class SessionSwitchRejected(
	message: String,
	cause: Throwable? = null,
	val statusCode: Int? = null,
) :
	IllegalStateException(message, cause)

class SessionSwitchCommitUnknown(cause: Throwable) :
	IllegalStateException("The server commit outcome is unknown.", cause)
