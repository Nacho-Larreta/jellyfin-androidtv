package org.jellyfin.androidtv.ui.playback

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.repository.PlaybackQuiescePort
import java.util.concurrent.atomic.AtomicReference

internal class PlaybackQuiesceRegistry(
	private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : PlaybackQuiescePort {
	private val quiesceAction = AtomicReference<(() -> Unit)?>(null)

	fun register(action: () -> Unit) {
		quiesceAction.set(action)
	}

	override suspend fun quiesceIfCreated() {
		val action = quiesceAction.get() ?: return
		withContext(mainDispatcher) {
			action()
		}
	}
}
