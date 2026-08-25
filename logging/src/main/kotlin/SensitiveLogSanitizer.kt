package org.jellyfin.androidtv.logging

import java.net.URI
import java.net.URISyntaxException

object SensitiveLogSanitizer {
	private const val REDACTED_URI = "<redacted-uri>"
	private const val REDACTED_VALUE = "<redacted>"
	private const val URI_SAFE_REDACTED_VALUE = "_redacted_"

	private val absoluteUriPattern = Regex(
		pattern = """(?i)\b[a-z][a-z0-9+.-]*://[^\s<>]+""",
	)
	private val schemeRelativeUriPattern = Regex(
		pattern = """(?i)(?<!:)//[^\s<>]+""",
	)
	private val relativeUriPattern = Regex("""(?i)(?<![:/])/(?!/)[^\s<>]+""")
	private val sensitiveHeaderPattern = Regex(
		pattern = """(?i)\b(authorization|proxy-authorization|cookie|set-cookie)\b\s*(?:=|:)\s*(?:(?:bearer|basic)\s+)?(?:"[^"]*"|'[^']*'|[^,\r\n]+)""",
	)
	private val sensitiveFieldPattern = Regex(
		pattern = """(?i)["']?\b(api[_-]?key|access[_-]?token|refresh[_-]?token|session[_-]?token|token|secret|password|credential|quick[_-]?connect[_-]?(?:code|secret)|code|pin|device[_-]?id|playback[_-]?session[_-]?id|media[_-]?source[_-]?id|x[-_](?:emby|mediabrowser)[-_]token)\b["']?\s*(?:=|:)\s*(?:"[^"]*"|'[^']*'|[^,\s;&#/?]+)""",
	)
	private val sensitivePathNamePattern = Regex(
		pattern = """(?i)^(?:api[_-]?key|access[_-]?token|refresh[_-]?token|session[_-]?token|token|secret|password|credential|quick[_-]?connect[_-]?(?:code|secret)|code|pin)$""",
	)
	private val opaqueIdentifierPattern = Regex("""^[a-zA-Z0-9_-]{16,}$""")
	private val numericIdentifierPattern = Regex("""^\d{6,}$""")
	private val resourceNamePattern = Regex("""^.+\.[a-zA-Z0-9]{1,8}$""")
	private val percentEncodedBytePattern = Regex("""%([a-fA-F0-9]{2})""")
	private val safeDecodedPunctuation = setOf(':', '/', '%', '.', '_', '-', '+', '@')

	fun sanitize(message: String?): String = sanitizeText(message.orEmpty())

	fun sanitize(message: String?, error: Throwable?): String {
		val diagnostic = buildString {
			if (!message.isNullOrBlank()) append(message)
			if (error != null) {
				if (isNotEmpty()) appendLine()
				append(error.stackTraceToString())
			}
		}
		return sanitizeText(diagnostic)
	}

	private fun sanitizeText(message: String): String {
		val originalSafeMessage = sanitizeRecognizedCandidates(message)
		val structurallyDecodedMessage = decodeWithoutCreatingTokenDelimiters(originalSafeMessage)
		return sanitizeRecognizedCandidates(structurallyDecodedMessage)
	}

	private fun sanitizeRecognizedCandidates(message: String): String {
		val headerSafeMessage = sensitiveHeaderPattern.replace(message) { match ->
			"${match.groupValues[1]}=$REDACTED_VALUE"
		}
		val fieldSafeMessage = sensitiveFieldPattern.replace(headerSafeMessage) { match ->
			"${match.groupValues[1]}=$URI_SAFE_REDACTED_VALUE"
		}
		val absoluteUriSafeMessage = absoluteUriPattern.replace(fieldSafeMessage) { match ->
			sanitizeAbsoluteUri(match.value)
		}
		val networkPathSafeMessage = schemeRelativeUriPattern.replace(absoluteUriSafeMessage) { match ->
			sanitizeSchemeRelativeUri(match.value)
		}
		val relativeUriSafeMessage = relativeUriPattern.replace(networkPathSafeMessage) { match ->
			sanitizeRelativeUri(match.value)
		}
		return sensitiveFieldPattern.replace(relativeUriSafeMessage) { match ->
			"${match.groupValues[1]}=$REDACTED_VALUE"
		}
	}

	private fun sanitizeAbsoluteUri(rawToken: String): String {
		val (token, trailingPunctuation) = splitTrailingPunctuation(rawToken)
		return try {
			val decodedToken = decodeRepeatedly(token)
			val uri = URI(decodedToken.substringBefore('?').substringBefore('#'))
			val scheme = uri.scheme ?: return REDACTED_URI
			val host = uri.host ?: return REDACTED_URI
			val hostWithPort = formatHostWithPort(host, uri.port)
			"$scheme://$hostWithPort${sanitizePath(uri.rawPath)}$trailingPunctuation"
		} catch (_: URISyntaxException) {
			REDACTED_URI
		}
	}

	private fun sanitizeSchemeRelativeUri(rawToken: String): String {
		val (token, trailingPunctuation) = splitTrailingPunctuation(rawToken)
		return try {
			val decodedToken = decodeRepeatedly(token)
			val uri = URI(decodedToken.substringBefore('?').substringBefore('#'))
			val host = uri.host ?: return REDACTED_URI
			"//${formatHostWithPort(host, uri.port)}${sanitizePath(uri.rawPath)}$trailingPunctuation"
		} catch (_: URISyntaxException) {
			REDACTED_URI
		}
	}

	private fun sanitizeRelativeUri(rawToken: String): String {
		val (token, trailingPunctuation) = splitTrailingPunctuation(rawToken)
		return try {
			val decodedToken = decodeRepeatedly(token)
			val uri = URI(decodedToken.substringBefore('?').substringBefore('#'))
			val path = uri.rawPath ?: return REDACTED_URI
			if (!uri.scheme.isNullOrEmpty() || !path.startsWith('/')) return REDACTED_URI
			"${sanitizePath(path)}$trailingPunctuation"
		} catch (_: URISyntaxException) {
			REDACTED_URI
		}
	}

	private fun sanitizePath(rawPath: String?): String {
		if (rawPath.isNullOrEmpty()) return ""
		var redactNextSegment = false
		return rawPath.split('/').joinToString("/") { rawSegment ->
			val segment = rawSegment.substringBefore(';')
			when {
				redactNextSegment -> {
					redactNextSegment = false
					URI_SAFE_REDACTED_VALUE
				}
				sensitivePathNamePattern.matches(segment) -> {
					redactNextSegment = true
					segment
				}
				opaqueIdentifierPattern.matches(segment) -> "_id_"
				numericIdentifierPattern.matches(segment) -> "_id_"
				resourceNamePattern.matches(segment) -> "_resource_"
				else -> segment
			}
		}
	}

	private fun decodeRepeatedly(value: String): String {
		var decoded = value
		repeat(5) {
			val next = percentEncodedBytePattern.replace(decoded) { match ->
				match.groupValues[1].toInt(16).toChar().toString()
			}
			if (next == decoded) return decoded
			decoded = next
		}
		return decoded
	}

	private fun decodeWithoutCreatingTokenDelimiters(value: String): String {
		var decoded = value
		repeat(5) {
			val next = percentEncodedBytePattern.replace(decoded) { match ->
				val character = match.groupValues[1].toInt(16).toChar()
				if (character.isLetterOrDigit() || character in safeDecodedPunctuation) character.toString()
				else match.value
			}
			if (next == decoded) return decoded
			decoded = next
		}
		return decoded
	}

	private fun formatHostWithPort(host: String, port: Int): String = buildString {
		if (host.contains(':')) append("[$host]") else append(host)
		if (port >= 0) append(":$port")
	}

	private fun splitTrailingPunctuation(value: String): Pair<String, String> {
		val token = value.trimEnd(',', '.', ';', ')', ']', '}', '\'', '"')
		return token to value.substring(token.length)
	}
}
