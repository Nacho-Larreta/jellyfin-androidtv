package org.jellyfin.androidtv.ui.base.designsystem

enum class TvLayer {
	Route,
	ScreenLocal,
	PlayerOverlay,
	Modal,
	Menu,
	Ime,
}

class TvLayerCoordinator {
	private data class ActiveLayer(
		val id: String,
		val layer: TvLayer,
		val order: Long,
		val onBack: () -> Unit,
	)

	private val activeLayers = linkedMapOf<String, ActiveLayer>()
	private var nextOrder = 0L
	private val escapeLedger = TvPressLedger { handleBack() }

	fun activate(id: String, layer: TvLayer, onBack: () -> Unit): AutoCloseable {
		require(id.isNotBlank()) { "Layer IDs must be stable and non-blank." }
		require(id !in activeLayers) { "Layer '$id' is already active." }

		activeLayers[id] = ActiveLayer(id, layer, nextOrder++, onBack)
		return AutoCloseable { activeLayers.remove(id) }
	}

	fun handleBack(): Boolean {
		val nearest = activeLayers.values.maxWithOrNull(
			compareBy<ActiveLayer> { it.layer.ordinal }.thenBy { it.order }
		) ?: return false
		nearest.onBack()
		return true
	}

	fun routeEscape(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
		val pressEvent = event.toTvEscapePressEvent() ?: return false
		return escapeLedger.route(pressEvent)
	}

	fun cancelPendingInput() = escapeLedger.cancel()
}
