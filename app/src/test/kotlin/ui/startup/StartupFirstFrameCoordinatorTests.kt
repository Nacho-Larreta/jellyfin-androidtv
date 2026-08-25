package org.jellyfin.androidtv.ui.startup

import android.view.ViewTreeObserver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.util.ArrayDeque

class StartupFirstFrameCoordinatorTests : FunSpec({
	test("completion detaches from observer installed after root attachment") {
		val host = FakeStartupFrameHost()
		var contentStarts = 0
		var contentReveals = 0
		val coordinator = StartupFirstFrameCoordinator.createForTesting(
			host = host,
			startContent = { contentStarts++ },
			revealContent = { contentReveals++ },
		)

		coordinator.install()
		val detachedObserver = host.observer
		host.attachAndMergeObserver()

		detachedObserver.isAlive shouldBe false
		host.observer.listenerCount shouldBe 1

		host.observer.dispatchPreDraw()
		host.runPostedCallbacks()
		host.observer.dispatchPreDraw()

		contentStarts shouldBe 1
		contentReveals shouldBe 1
		host.invalidations shouldBe 1
		detachedObserver.removeCalls shouldBe 0
		host.observer.removeCalls shouldBe 1
		host.observer.listeners.shouldBeEmpty()

		host.observer.dispatchPreDraw()
		contentStarts shouldBe 1
		contentReveals shouldBe 1
	}

	test("cancel after root attachment detaches from the current observer") {
		val host = FakeStartupFrameHost()
		var contentStarts = 0
		var contentReveals = 0
		val coordinator = StartupFirstFrameCoordinator.createForTesting(
			host = host,
			startContent = { contentStarts++ },
			revealContent = { contentReveals++ },
		)

		coordinator.install()
		val detachedObserver = host.observer
		host.attachAndMergeObserver()
		coordinator.cancel()

		detachedObserver.isAlive shouldBe false
		detachedObserver.removeCalls shouldBe 0
		host.observer.removeCalls shouldBe 1
		host.observer.listeners.shouldBeEmpty()
		host.removedCallbacks shouldBe 1

		host.observer.dispatchPreDraw()
		host.runPostedCallbacks()
		contentStarts shouldBe 0
		contentReveals shouldBe 0
	}
})

private class FakeStartupFrameHost : StartupFrameHost {
	override var isAttached = false
		private set

	var observer = FakeStartupPreDrawObserver()
		private set

	var invalidations = 0
		private set

	var removedCallbacks = 0
		private set

	private val postedCallbacks = ArrayDeque<Runnable>()

	override fun currentObserver(): StartupPreDrawObserver = observer

	override fun post(runnable: Runnable) {
		postedCallbacks.addLast(runnable)
	}

	override fun removeCallbacks(runnable: Runnable) {
		removedCallbacks++
		postedCallbacks.remove(runnable)
	}

	override fun invalidate() {
		invalidations++
	}

	fun attachAndMergeObserver() {
		check(!isAttached)

		val attachedObserver = FakeStartupPreDrawObserver()
		observer.mergeInto(attachedObserver)
		observer = attachedObserver
		isAttached = true
	}

	fun runPostedCallbacks() {
		while (postedCallbacks.isNotEmpty()) postedCallbacks.removeFirst().run()
	}
}

private class FakeStartupPreDrawObserver : StartupPreDrawObserver {
	override val identity = Any()
	override var isAlive = true
		private set

	val listeners = linkedSetOf<ViewTreeObserver.OnPreDrawListener>()
	var removeCalls = 0
		private set

	val listenerCount: Int
		get() = listeners.size

	override fun add(listener: ViewTreeObserver.OnPreDrawListener) {
		check(isAlive)
		listeners.add(listener)
	}

	override fun remove(listener: ViewTreeObserver.OnPreDrawListener) {
		check(isAlive)
		removeCalls++
		listeners.remove(listener)
	}

	fun dispatchPreDraw() {
		listeners.toList().forEach(ViewTreeObserver.OnPreDrawListener::onPreDraw)
	}

	fun mergeInto(target: FakeStartupPreDrawObserver) {
		check(isAlive)
		target.listeners.addAll(listeners)
		listeners.clear()
		isAlive = false
	}
}
