plugins {
	alias(libs.plugins.android.library)
}

android {
	namespace = "org.jellyfin.androidtv.logging"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	defaultConfig {
		minSdk = libs.versions.android.minSdk.get().toInt()
	}

	lint {
		lintConfig = file("$rootDir/android-lint.xml")
		abortOnError = false
	}

	testOptions.unitTests.all {
		it.useJUnitPlatform()
	}
}

dependencies {
	implementation(libs.timber)

	testImplementation(libs.kotest.runner.junit5)
	testImplementation(libs.kotest.assertions)
	testImplementation(libs.jellyfin.sdk)
	testImplementation(libs.slf4j.api)
	testImplementation(libs.slf4j.timber)
}
