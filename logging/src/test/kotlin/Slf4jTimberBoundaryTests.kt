package org.jellyfin.androidtv.logging

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.slf4j.LoggerFactory
import timber.log.Timber

class Slf4jTimberBoundaryTests : FreeSpec({
	afterEach { Timber.uprootAll() }

	"sanitize an SDK-shaped Quick Connect message across SLF4J and Timber" {
		val capturedMessages = mutableListOf<String>()
		val marker = "sdk-sink-marker"
		Timber.plant(SanitizingDebugTree.forTest { _, _, message -> capturedMessages += message })

		LoggerFactory.getLogger("org.jellyfin.sdk.api.okhttp.OkHttpClient").info(
			"HTTP GET https://example.test/QuickConnect/Connect?Secret={}&Code={}",
			marker,
			marker,
		)

		capturedMessages.size shouldBe 1
		capturedMessages.single() shouldBe "HTTP GET https://example.test/QuickConnect/Connect"
		capturedMessages.single() shouldNotContain marker
	}

	"sanitize the actual SDK Quick Connect polling request before Timber" {
		val capturedMessages = mutableListOf<String>()
		val marker = "actual-sdk-quick-connect-marker"
		Timber.plant(SanitizingDebugTree.forTest { _, _, message -> capturedMessages += message })
		val apiFactory = OkHttpFactory()
		val api = apiFactory.create(
			baseUrl = "http://127.0.0.1:1",
			accessToken = null,
			clientInfo = ClientInfo("Logging boundary test", "1"),
			deviceInfo = DeviceInfo("logging-boundary-test", "Logging boundary test"),
			httpClientOptions = HttpClientOptions(),
			socketConnectionFactory = apiFactory,
		)

		val result = runCatching { api.quickConnectApi.getQuickConnectState(marker) }
		val capturedDiagnostic = capturedMessages.joinToString("\n")

		result.isFailure shouldBe true
		capturedDiagnostic shouldContain "GET http://127.0.0.1:1/QuickConnect/Connect"
		capturedDiagnostic shouldNotContain marker
	}
})
