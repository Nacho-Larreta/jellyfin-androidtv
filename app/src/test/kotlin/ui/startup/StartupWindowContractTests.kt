package org.jellyfin.androidtv.ui.startup

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StartupWindowContractTests : FunSpec({
	test("StartupActivity uses an opaque branded startup window") {
		val projectRoot = generateSequence(File(System.getProperty("user.dir").orEmpty())) { it.parentFile }
			.first { File(it, "app/src/main/AndroidManifest.xml").isFile }
		val manifest = parseXml(File(projectRoot, "app/src/main/AndroidManifest.xml"))
		val startupActivity = manifest.getElementsByTagName("activity")
			.asElementSequence()
			.first { it.getAttributeNS(ANDROID_NAMESPACE, "name") == ".ui.startup.StartupActivity" }

		startupActivity.getAttributeNS(ANDROID_NAMESPACE, "theme") shouldBe "@style/Theme.Jellyfin.Startup"

		val themes = parseXml(File(projectRoot, "app/src/main/res/values/theme_jellyfin.xml"))
		val startupTheme = themes.getElementsByTagName("style")
			.asElementSequence()
			.first { it.getAttribute("name") == "Theme.Jellyfin.Startup" }
		startupTheme.getElementsByTagName("item")
			.asElementSequence()
			.first { it.getAttribute("name") == "android:windowBackground" }
			.textContent
			.trim() shouldBe "@drawable/startup_window"

		val startupWindow = parseXml(File(projectRoot, "app/src/main/res/drawable/startup_window.xml"))
		startupWindow.documentElement.nodeName shouldBe "layer-list"
		val layers = startupWindow.getElementsByTagName("item").asElementSequence().toList()
		layers.first().getAttributeNS(ANDROID_NAMESPACE, "drawable") shouldBe "@color/not_quite_black"
		layers[1].getAttributeNS(ANDROID_NAMESPACE, "drawable") shouldBe "@drawable/app_logo"

		val colors = parseXml(File(projectRoot, "app/src/main/res/values/colors.xml"))
		val startupBackground = colors.getElementsByTagName("color")
			.asElementSequence()
			.first { it.getAttribute("name") == "not_quite_black" }
			.textContent
			.trim()
		startupBackground shouldBe "#101010"
		val argb = "FF${startupBackground.removePrefix("#")}".uppercase()
		argb.length shouldBe 8
		argb.take(2) shouldBe "FF"

		val startupLayout = parseXml(File(projectRoot, "app/src/main/res/layout/activity_startup.xml"))
		startupLayout.documentElement.getAttributeNS(ANDROID_NAMESPACE, "background") shouldBe "@color/not_quite_black"
		val staticSurface = startupLayout.getElementsByTagName("View")
			.asElementSequence()
			.first { it.getAttributeNS(ANDROID_NAMESPACE, "id") == "@+id/startup_surface" }
		staticSurface.getAttributeNS(ANDROID_NAMESPACE, "background") shouldBe "@drawable/startup_window"
		staticSurface.getAttributeNS(ANDROID_NAMESPACE, "importantForAccessibility") shouldBe "no"

		val composeBackground = startupLayout.getElementsByTagName("androidx.compose.ui.platform.ComposeView")
			.asElementSequence()
			.first { it.getAttributeNS(ANDROID_NAMESPACE, "id") == "@+id/background" }
		composeBackground.getAttributeNS(ANDROID_NAMESPACE, "visibility") shouldBe "invisible"

		val tvThemes = parseXml(File(projectRoot, "app/src/main/res/values-v31/theme_jellyfin.xml"))
		val tvStartupTheme = tvThemes.getElementsByTagName("style")
			.asElementSequence()
			.first { it.getAttribute("name") == "Theme.Jellyfin.Startup" }
		val tvThemeItems = tvStartupTheme.getElementsByTagName("item")
			.asElementSequence()
			.associate { it.getAttribute("name") to it.textContent.trim() }
		tvThemeItems["android:windowSplashScreenBackground"] shouldBe "@color/not_quite_black"
		tvThemeItems["android:windowSplashScreenIconBackgroundColor"] shouldBe "@color/not_quite_black"
	}
})

private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"

private fun parseXml(file: File) = DocumentBuilderFactory.newInstance().run {
	isNamespaceAware = true
	newDocumentBuilder().parse(file)
}

private fun org.w3c.dom.NodeList.asElementSequence() = sequence {
	for (index in 0 until length) {
		val node = item(index)
		if (node is org.w3c.dom.Element) {
			yield(node)
		}
	}
}
