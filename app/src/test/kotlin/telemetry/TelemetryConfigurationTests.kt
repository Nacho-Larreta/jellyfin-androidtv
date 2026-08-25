package org.jellyfin.androidtv.telemetry

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.acra.ReportField
import org.acra.config.CoreConfigurationBuilder

class TelemetryConfigurationTests : FunSpec({
	test("ACRA collects exactly the fields consumed by the report sender") {
		val expectedReportContent = listOf(
			ReportField.STACK_TRACE,
			ReportField.LOGCAT,
			ReportField.APP_VERSION_NAME,
			ReportField.APP_VERSION_CODE,
			ReportField.PACKAGE_NAME,
			ReportField.BUILD,
			ReportField.BUILD_CONFIG,
			ReportField.ANDROID_VERSION,
			ReportField.BRAND,
			ReportField.PRODUCT,
			ReportField.PHONE_MODEL,
			ReportField.USER_APP_START_DATE,
			ReportField.USER_CRASH_DATE,
		)
		val configuration = CoreConfigurationBuilder()

		TelemetryService.configureReportContent(configuration)

		val actualReportContent = requireNotNull(configuration.reportContent)
		actualReportContent shouldBe expectedReportContent
		actualReportContent shouldNotContain ReportField.INITIAL_CONFIGURATION
	}
})
