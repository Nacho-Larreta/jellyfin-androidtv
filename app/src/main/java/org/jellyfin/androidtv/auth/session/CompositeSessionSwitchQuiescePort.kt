package org.jellyfin.androidtv.auth.session

import java.util.UUID

fun interface SessionSwitchQuiesceParticipant {
	suspend fun stopAndReport(snapshot: SessionSnapshot, switchId: UUID): SessionQuiesceResult
}

class CompositeSessionSwitchQuiescePort(
	private val participants: List<SessionSwitchQuiesceParticipant>,
) : SessionSwitchQuiescePort {
	override suspend fun stopAndReport(snapshot: SessionSnapshot, switchId: UUID): SessionQuiesceResult {
		var acknowledged = false
		for (participant in participants) {
			when (val result = participant.stopAndReport(snapshot, switchId)) {
				SessionQuiesceResult.NotActive -> Unit
				is SessionQuiesceResult.Acknowledged -> acknowledged = true
				is SessionQuiesceResult.Failed -> return result
			}
		}
		return if (acknowledged) {
			SessionQuiesceResult.Acknowledged(COMPOSITE_REPORT_KEY)
		} else {
			SessionQuiesceResult.NotActive
		}
	}

	private companion object {
		const val COMPOSITE_REPORT_KEY = "composite-quiesce-acknowledged"
	}
}
