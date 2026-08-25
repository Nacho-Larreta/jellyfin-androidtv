package org.jellyfin.androidtv.ui.startup

import android.view.View
import android.view.ViewTreeObserver

internal interface StartupFrameHost {
	val isAttached: Boolean

	fun currentObserver(): StartupPreDrawObserver

	fun post(runnable: Runnable)

	fun removeCallbacks(runnable: Runnable)

	fun invalidate()
}

internal interface StartupPreDrawObserver {
	val identity: Any
	val isAlive: Boolean

	fun add(listener: ViewTreeObserver.OnPreDrawListener)

	fun remove(listener: ViewTreeObserver.OnPreDrawListener)
}

private class ViewStartupFrameHost(private val root: View) : StartupFrameHost {
	override val isAttached: Boolean
		get() = root.isAttachedToWindow

	override fun currentObserver() = ViewStartupPreDrawObserver(root.viewTreeObserver)

	override fun post(runnable: Runnable) {
		root.post(runnable)
	}

	override fun removeCallbacks(runnable: Runnable) {
		root.removeCallbacks(runnable)
	}

	override fun invalidate() {
		root.invalidate()
	}
}

private class ViewStartupPreDrawObserver(
	private val observer: ViewTreeObserver,
) : StartupPreDrawObserver {
	override val identity: Any = observer
	override val isAlive: Boolean
		get() = observer.isAlive

	override fun add(listener: ViewTreeObserver.OnPreDrawListener) {
		observer.addOnPreDrawListener(listener)
	}

	override fun remove(listener: ViewTreeObserver.OnPreDrawListener) {
		observer.removeOnPreDrawListener(listener)
	}
}

internal class StartupFirstFrameCoordinator private constructor(
	private val host: StartupFrameHost,
	private val startContent: () -> Unit,
	private val revealContent: () -> Unit,
) : ViewTreeObserver.OnPreDrawListener {
	constructor(
		root: View,
		startContent: () -> Unit,
		revealContent: () -> Unit,
	) : this(ViewStartupFrameHost(root), startContent, revealContent)

	internal companion object {
		fun createForTesting(
			host: StartupFrameHost,
			startContent: () -> Unit,
			revealContent: () -> Unit,
		) = StartupFirstFrameCoordinator(host, startContent, revealContent)
	}

	private enum class Stage {
		CREATED,
		WAITING_FOR_STATIC_FRAME,
		CONTENT_START_SCHEDULED,
		CONTENT_STARTED,
		COMPLETE,
		CANCELLED,
	}

	private var stage = Stage.CREATED
	private var observer: StartupPreDrawObserver? = null
	private val startContentAfterStaticFrame = Runnable {
		if (stage != Stage.CONTENT_START_SCHEDULED) return@Runnable

		stage = Stage.CONTENT_STARTED
		startContent()
		host.invalidate()
	}

	fun install() {
		check(stage == Stage.CREATED)

		stage = Stage.WAITING_FOR_STATIC_FRAME
		observer = host.currentObserver().also { it.add(this) }
	}

	override fun onPreDraw(): Boolean {
		when (stage) {
			Stage.WAITING_FOR_STATIC_FRAME -> {
				stage = Stage.CONTENT_START_SCHEDULED
				host.post(startContentAfterStaticFrame)
			}

			Stage.CONTENT_STARTED -> {
				detach()
				stage = Stage.COMPLETE
				revealContent()
			}

			else -> Unit
		}

		return true
	}

	fun cancel() {
		if (stage == Stage.COMPLETE || stage == Stage.CANCELLED) return

		host.removeCallbacks(startContentAfterStaticFrame)
		detach()
		stage = Stage.CANCELLED
	}

	private fun detach() {
		val installedObserver = observer
		installedObserver
			?.takeIf(StartupPreDrawObserver::isAlive)
			?.remove(this)

		if (host.isAttached) {
			host.currentObserver()
				.takeIf { it.identity !== installedObserver?.identity && it.isAlive }
				?.remove(this)
		}

		observer = null
	}
}
