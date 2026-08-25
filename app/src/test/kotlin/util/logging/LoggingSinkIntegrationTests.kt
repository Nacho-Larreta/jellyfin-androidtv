package org.jellyfin.androidtv.util.logging

import coil3.util.Logger
import io.mockk.every
import io.mockk.mockk
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.acra.ReportField
import org.acra.data.CrashReportData
import org.jellyfin.androidtv.telemetry.TelemetryService
import org.jellyfin.androidtv.util.coil.CoilTimberLogger
import timber.log.Timber

class LoggingSinkIntegrationTests : FreeSpec({
	afterEach { Timber.uprootAll() }

	"Coil logger sanitizes messages and throwables before Timber" {
		val capturedMessages = mutableListOf<String>()
		val marker = "coil-sink-marker"
		Timber.plant(CapturingTree(capturedMessages))

		CoilTimberLogger(Logger.Level.Debug).log(
			tag = "NetworkFetcher",
			level = Logger.Level.Error,
			message = "Failed https://user:$marker@example.test/Images/image.jpg?ApiKey=$marker",
			throwable = IllegalStateException("Authorization: Bearer $marker"),
		)

		capturedMessages.single() shouldNotContain marker
		capturedMessages.single() shouldContain "https://example.test/Images/_resource_"
	}

	"ACRA report boundary sanitizes logcat and stack trace before transmission" {
		val marker = "acra-sink-marker"
		val report = mockk<CrashReportData>(relaxed = true)
		every { report.getString(any()) } returns ""
		every { report.getString(ReportField.STACK_TRACE) } returns "Failure Authorization: Bearer $marker"
		every { report.getString(ReportField.LOGCAT) } returns "GET https://example.test/Videos/$marker?Secret=$marker"
		val sender = TelemetryService.AcraReportSender(null, null, includeLogs = true)

		val sanitizedReport = sender.buildSanitizedReport(report)

		sanitizedReport shouldNotContain marker
		sanitizedReport shouldContain "Authorization=<redacted>"
	}
})

private class CapturingTree(
	private val messages: MutableList<String>,
) : Timber.Tree() {
	override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
		messages += if (t == null) message else "$message\n${t.stackTraceToString()}"
	}
}
