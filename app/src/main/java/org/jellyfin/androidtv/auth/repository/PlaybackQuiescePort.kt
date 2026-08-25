package org.jellyfin.androidtv.auth.repository

fun interface PlaybackQuiescePort {
	suspend fun quiesceIfCreated()
}
