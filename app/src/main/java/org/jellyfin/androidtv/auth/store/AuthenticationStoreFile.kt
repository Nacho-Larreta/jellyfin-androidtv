package org.jellyfin.androidtv.auth.store

import androidx.core.util.AtomicFile
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

internal interface AuthenticationStoreFile {
	fun exists(): Boolean

	@Throws(IOException::class)
	fun readText(): String

	@Throws(IOException::class)
	fun replace(contents: String)
}

internal class AtomicAuthenticationStoreFile(
	path: File,
) : AuthenticationStoreFile {
	private val file = AtomicFile(path)

	override fun exists(): Boolean = file.baseFile.exists()

	override fun readText(): String = file.openRead().bufferedReader().use { it.readText() }

	override fun replace(contents: String) {
		val output = file.startWrite()
		try {
			val writer = OutputStreamWriter(output, StandardCharsets.UTF_8)
			writer.write(contents)
			writer.flush()
			file.finishWrite(output)
		} catch (error: IOException) {
			file.failWrite(output)
			throw error
		}
	}
}
