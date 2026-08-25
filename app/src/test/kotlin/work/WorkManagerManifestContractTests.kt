package org.jellyfin.androidtv.work

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class WorkManagerManifestContractTests {
	@Test
	fun `application removes only WorkManager automatic startup metadata`() {
		val manifest = parseXml(projectFile("app/src/main/AndroidManifest.xml"))
		val startupProvider = manifest.getElementsByTagName("provider")
			.asElements()
			.single { it.androidAttribute("name") == ANDROIDX_STARTUP_PROVIDER }
		val metadata = startupProvider.getElementsByTagName("meta-data").asElements()

		val workManagerInitializer = requireNotNull(
			metadata.singleOrNull { it.androidAttribute("name") == WORK_MANAGER_INITIALIZER },
		) { "WorkManager initializer removal marker is missing" }

		assertEquals("remove", workManagerInitializer.toolsAttribute("node"))
		assertEquals(
			setOf(WORK_MANAGER_INITIALIZER),
			metadata
				.filter { it.toolsAttribute("node") == "remove" }
				.mapTo(mutableSetOf()) { it.androidAttribute("name") },
		)
		assertEquals("merge", startupProvider.toolsAttribute("node"))
		assertTrue(
			setOf(
				"org.jellyfin.androidtv.LogInitializer",
				"org.jellyfin.androidtv.di.KoinInitializer",
				"org.jellyfin.androidtv.SessionInitializer",
			).all { initializer ->
				metadata.any {
					it.androidAttribute("name") == initializer &&
						it.androidAttribute("value") == "androidx.startup"
				}
			},
		)
	}
}

private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
private const val TOOLS_NAMESPACE = "http://schemas.android.com/tools"
private const val ANDROIDX_STARTUP_PROVIDER = "androidx.startup.InitializationProvider"
private const val WORK_MANAGER_INITIALIZER = "androidx.work.WorkManagerInitializer"

private fun projectFile(relativePath: String): File {
	val workingDirectory = requireNotNull(System.getProperty("user.dir"))
	var directory = File(workingDirectory).canonicalFile
	repeat(5) {
		val candidate = File(directory, relativePath)
		if (candidate.isFile) return candidate
		directory = directory.parentFile ?: return@repeat
	}
	error("Unable to locate $relativePath")
}

private fun parseXml(file: File) = DocumentBuilderFactory.newInstance().run {
	isNamespaceAware = true
	newDocumentBuilder().parse(file)
}

private fun org.w3c.dom.NodeList.asElements() = (0 until length)
	.mapNotNull { item(it) as? Element }

private fun Element.androidAttribute(name: String) = getAttributeNS(ANDROID_NAMESPACE, name)

private fun Element.toolsAttribute(name: String) = getAttributeNS(TOOLS_NAMESPACE, name)
