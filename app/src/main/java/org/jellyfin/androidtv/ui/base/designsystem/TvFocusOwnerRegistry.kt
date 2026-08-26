package org.jellyfin.androidtv.ui.base.designsystem

import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.core.view.isVisible

data class TvFocusRestoreRequest(
	val triggerId: String,
	val siblingIds: List<String> = emptyList(),
	val sectionOwnerId: String? = null,
	val invokingControlId: String? = null,
)

class TvFocusOwnerRegistry {
	private data class Target(
		val canFocus: () -> Boolean,
		val requestFocus: () -> Boolean,
	)

	private val targets = linkedMapOf<String, Target>()
	private var pendingRestore: TvFocusRestoreRequest? = null

	var currentOwnerId: String? = null
		private set

	fun register(
		id: String,
		canFocus: () -> Boolean = { true },
		requestFocus: () -> Boolean,
	): AutoCloseable {
		require(id.isNotBlank()) { "Focus owner IDs must be stable and non-blank." }
		require(id !in targets) { "Focus owner '$id' is already registered." }

		targets[id] = Target(canFocus, requestFocus)
		pendingRestore?.let(::restore)
		return AutoCloseable {
			targets.remove(id)
			if (currentOwnerId == id) currentOwnerId = null
		}
	}

	fun onFocusChanged(id: String, focused: Boolean) {
		if (focused) {
			currentOwnerId = id
		} else if (currentOwnerId == id) {
			currentOwnerId = null
		}
	}

	fun restore(request: TvFocusRestoreRequest): String? {
		val candidates = buildList {
			add(request.triggerId)
			addAll(request.siblingIds)
			request.sectionOwnerId?.let(::add)
			request.invokingControlId?.let(::add)
		}.distinct()

		val restoredId = candidates.firstOrNull { id ->
			val target = targets[id] ?: return@firstOrNull false
			target.canFocus() && target.requestFocus()
		}
		pendingRestore = if (restoredId == null) request else null
		return restoredId
	}

	fun retryPendingRestore(): String? = pendingRestore?.let(::restore)

	fun clear() {
		currentOwnerId = null
		pendingRestore = null
	}
}

fun Modifier.tvFocusOwner(
	id: String,
	registry: TvFocusOwnerRegistry,
): Modifier = onFocusChanged { state -> registry.onFocusChanged(id, state.hasFocus) }

fun View.bindTvFocusOwner(
	id: String,
	registry: TvFocusOwnerRegistry,
): AutoCloseable {
	val registration = registry.register(
		id = id,
		canFocus = { isFocusable && isEnabled && isVisible },
		requestFocus = { requestFocus() },
	)
	val observer = ViewTreeObserver.OnGlobalFocusChangeListener { oldFocus, newFocus ->
		if (oldFocus === this) registry.onFocusChanged(id, false)
		if (newFocus === this) registry.onFocusChanged(id, true)
	}
	val registeredViewTreeObserver = viewTreeObserver
	registeredViewTreeObserver.addOnGlobalFocusChangeListener(observer)

	return AutoCloseable {
		removeGlobalFocusObserver(this, registeredViewTreeObserver, observer)
		registration.close()
	}
}

private fun removeGlobalFocusObserver(
	view: View,
	registeredObserver: ViewTreeObserver,
	listener: ViewTreeObserver.OnGlobalFocusChangeListener,
) {
	val activeObserver = if (registeredObserver.isAlive) registeredObserver else view.viewTreeObserver
	if (activeObserver.isAlive) activeObserver.removeOnGlobalFocusChangeListener(listener)
}
