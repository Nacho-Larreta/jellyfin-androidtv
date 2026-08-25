package org.jellyfin.androidtv.logging

import timber.log.Timber

class SanitizingDebugTree private constructor(
	private val testSink: SanitizedLogSink?,
) : Timber.DebugTree() {
	constructor() : this(null)

	companion object {
		internal fun forTest(testSink: SanitizedLogSink) = SanitizingDebugTree(testSink)
	}

	override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
		val safeTag = SensitiveLogSanitizer.sanitize(tag)
		val safeMessage = SensitiveLogSanitizer.sanitize(message, t)
		val normalizedTag = safeTag.ifEmpty { null }
		if (testSink == null) super.log(priority, normalizedTag, safeMessage, null)
		else testSink.write(priority, normalizedTag, safeMessage)
	}
}

internal fun interface SanitizedLogSink {
	fun write(priority: Int, tag: String?, message: String)
}
