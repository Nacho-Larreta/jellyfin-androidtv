package org.jellyfin.androidtv.logging

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class SensitiveLogSanitizerTests : FreeSpec({
	"URL-bearing diagnostics" - {
		"remove userinfo, query, fragment and dynamic identifiers" {
			val marker = "credential-marker"
			val message = "Playing https://viewer:$marker@example.test/Videos/0123456789abcdef0123456789abcdef/stream.mp4?ApiKey=$marker&MediaSourceId=$marker#$marker"

			val sanitized = SensitiveLogSanitizer.sanitize(message)

			sanitized shouldContain "Playing https://example.test/Videos/_id_/_resource_"
			sanitized shouldNotContain marker
			sanitized shouldNotContain "ApiKey"
			sanitized shouldNotContain "MediaSourceId"
		}

		"remove mixed-case duplicated and reordered Quick Connect secrets" {
			val marker = "quick-connect-marker"
			val message = "HTTP GET http://example.test/QuickConnect/Connect?SeCrEt=$marker&status=pending&sEcReT=$marker&CoDe=$marker"

			val sanitized = SensitiveLogSanitizer.sanitize(message)

			sanitized shouldBe "HTTP GET http://example.test/QuickConnect/Connect"
			sanitized shouldNotContain marker
		}

		"decode an encoded URL before removing its credentials" {
			val marker = "encoded-marker"
			val message = "SDK request https%3A%2F%2Fuser%3A$marker%40example.test%2FQuickConnect%2FConnect%3FSecret%3D$marker%26Code%3D$marker%23$marker"

			val sanitized = SensitiveLogSanitizer.sanitize(message)

			sanitized shouldBe "SDK request https://example.test/QuickConnect/Connect"
			sanitized shouldNotContain marker
		}

		"decode a double-percent-encoded absolute URL before removing credentials" {
			val marker = "double-encoded-absolute-marker"
			val message = "SDK request https%253A%252F%252Fuser%253A$marker%2540example.test%252FQuickConnect%252FConnect%253FSecret%253D$marker"

			val sanitized = SensitiveLogSanitizer.sanitize(message)

			sanitized shouldBe "SDK request https://example.test/QuickConnect/Connect"
			sanitized shouldNotContain marker
		}

		"remove an encoded query from a relative SDK request target" {
			val marker = "relative-marker"
			val message = "HTTP GET /QuickConnect/Connect%3FCode%3D$marker%26Secret%3D$marker"

			val sanitized = SensitiveLogSanitizer.sanitize(message)

			sanitized shouldBe "HTTP GET /QuickConnect/Connect"
			sanitized shouldNotContain marker
		}

		"remove a double-percent-encoded query from a relative SDK target" {
			val marker = "double-encoded-relative-marker"
			val message = "HTTP GET %252FQuickConnect%252FConnect%253FCode%253D$marker%2526Secret%253D$marker"

			val sanitized = SensitiveLogSanitizer.sanitize(message)

			sanitized shouldBe "HTTP GET /QuickConnect/Connect"
			sanitized shouldNotContain marker
		}

		"remove quoted query values without leaving their contents behind" {
			val marker = "quoted-query-marker"
			val message = "GET https://example.test/QuickConnect/Connect?Secret=\"$marker\"&Code='$marker'"

			val sanitized = SensitiveLogSanitizer.sanitize(message)

			sanitized shouldBe "GET https://example.test/QuickConnect/Connect"
			sanitized shouldNotContain marker
		}

		"keep percent-encoded newlines inside the URL candidate until its query is removed" {
			val marker = "SEC_REVIEW_MARKER_9Z"
			val message = "GET https://example.test/path?Secret=prefix%0A$marker"

			val sanitized = SensitiveLogSanitizer.sanitize(message)

			sanitized shouldBe "GET https://example.test/path"
			sanitized shouldNotContain marker
		}

		"remove scheme-relative userinfo and query credentials" {
			val marker = "scheme-relative-marker"
			val message = "Coil //viewer:$marker@example.test/Images/0123456789abcdef0123456789abcdef?api_key=$marker"

			val sanitized = SensitiveLogSanitizer.sanitize(message)

			sanitized shouldBe "Coil //example.test/Images/_id_"
			sanitized shouldNotContain marker
		}

		"remove matrix credentials and opaque path secrets" {
			val marker = "matrix-marker"
			val base64UrlSecret = "QWxhZGRpbjpvcGVuLXNlc2FtZQ_1234567890"
			val message = "GET https://example.test/Videos;token=$marker/$base64UrlSecret/stream.m3u8"

			val sanitized = SensitiveLogSanitizer.sanitize(message)

			sanitized shouldBe "GET https://example.test/Videos/_id_/_resource_"
			sanitized shouldNotContain marker
			sanitized shouldNotContain base64UrlSecret
		}

		"remove secrets carried in named path segments" {
			val marker = "named-path-marker"
			val message = "GET /QuickConnect/secret/$marker/status"

			val sanitized = SensitiveLogSanitizer.sanitize(message)

			sanitized shouldBe "GET /QuickConnect/secret/_redacted_/status"
			sanitized shouldNotContain marker
		}

		"remove opaque secrets from relative paths without query parameters" {
			val marker = "QWxhZGRpbjpvcGVuLXNlc2FtZQ_1234567890"
			val message = "GET /Videos/$marker/stream.m3u8"

			val sanitized = SensitiveLogSanitizer.sanitize(message)

			sanitized shouldBe "GET /Videos/_id_/_resource_"
			sanitized shouldNotContain marker
		}

		"redact malformed URI tokens instead of returning their raw value" {
			val marker = "malformed-marker"
			val message = "Coil failed https://user:$marker@example.test/%zz?token=$marker"

			val sanitized = SensitiveLogSanitizer.sanitize(message)

			sanitized shouldContain "Coil failed <redacted-uri>"
			sanitized shouldNotContain marker
		}

		"remain stable when a diagnostic crosses more than one safe sink" {
			val message = "GET https://example.test/Videos/0123456789abcdef0123456789abcdef/stream.mp4?ApiKey=marker"
			val sanitized = SensitiveLogSanitizer.sanitize(message)

			SensitiveLogSanitizer.sanitize(sanitized) shouldBe sanitized
		}
	}

	"structured diagnostics" - {
		"redact credential fields outside URLs case-insensitively" {
			val marker = "structured-marker"
			val message = "secret=$marker, ACCESS_TOKEN: $marker, code=$marker, state=pending"

			val sanitized = SensitiveLogSanitizer.sanitize(message)

			sanitized shouldBe "secret=<redacted>, ACCESS_TOKEN=<redacted>, code=<redacted>, state=pending"
			sanitized shouldNotContain marker
		}

		"redact credential fields serialized as JSON" {
			val marker = "json-marker"
			val message = "SDK payload {\"Secret\":\"$marker\",\"deviceId\":\"$marker\",\"state\":\"pending\"}"

			val sanitized = SensitiveLogSanitizer.sanitize(message)

			sanitized shouldNotContain marker
			sanitized shouldContain "Secret=<redacted>"
			sanitized shouldContain "deviceId=<redacted>"
			sanitized shouldContain "state"
		}

		"redact authorization and cookie headers" {
			val marker = "header-marker"
			val message = "Authorization: Bearer $marker, Proxy-Authorization=Basic $marker, Cookie: session=$marker"

			val sanitized = SensitiveLogSanitizer.sanitize(message)

			sanitized shouldNotContain marker
			sanitized shouldContain "Authorization=<redacted>"
			sanitized shouldContain "Proxy-Authorization=<redacted>"
			sanitized shouldContain "Cookie=<redacted>"
		}

		"keep encoded header delimiters inside the credential candidate" {
			val cookieMarker = "COOKIE_REVIEW_MARKER"
			val authorizationMarker = "AUTH_REVIEW_MARKER"
			val message = "Cookie: session=prefix%2C$cookieMarker\nAuthorization: Bearer prefix%0A$authorizationMarker"

			val sanitized = SensitiveLogSanitizer.sanitize(message)

			sanitized shouldBe "Cookie=<redacted>\nAuthorization=<redacted>"
			sanitized shouldNotContain cookieMarker
			sanitized shouldNotContain authorizationMarker
		}

		"sanitize throwable messages before they can reach Timber or ACRA" {
			val marker = "throwable-marker"
			val error = IllegalStateException("Request failed at https://example.test/Videos/0123456789abcdef0123456789abcdef?api_key=$marker")

			val sanitized = SensitiveLogSanitizer.sanitize("Playback failed", error)

			sanitized shouldContain "Playback failed"
			sanitized shouldContain "IllegalStateException"
			sanitized shouldNotContain marker
			sanitized shouldNotContain "api_key"
		}
	}
})
