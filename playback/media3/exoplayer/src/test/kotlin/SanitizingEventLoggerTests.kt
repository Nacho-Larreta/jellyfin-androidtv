package org.jellyfin.playback.media3.exoplayer

import android.os.SystemClock
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import timber.log.Timber

class SanitizingEventLoggerTests : FreeSpec({
	beforeEach {
		mockkStatic(SystemClock::class)
		every { SystemClock.elapsedRealtime() } returns 0L
	}
	afterEach {
		Timber.uprootAll()
		unmockkStatic(SystemClock::class)
	}

	"Media3 EventLogger routes a sanitized diagnostic into Timber" {
		val capturedMessages = mutableListOf<String>()
		val marker = "event-logger-marker"
		Timber.plant(CapturingTree(capturedMessages))

		val eventLogger = SanitizingEventLogger()
		val logMethod = SanitizingEventLogger::class.java.getDeclaredMethod("logd", String::class.java)
		logMethod.isAccessible = true
		logMethod.invoke(
			eventLogger,
			"mediaItem [https://viewer:$marker@example.test/Videos/QWxhZGRpbjpvcGVuLXNlc2FtZQ_1234567890?ApiKey=$marker]",
		)

		capturedMessages.size shouldBe 1
		capturedMessages.single() shouldNotContain marker
	}
})

private class CapturingTree(
	private val messages: MutableList<String>,
) : Timber.Tree() {
	override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
		messages += message
	}
}
