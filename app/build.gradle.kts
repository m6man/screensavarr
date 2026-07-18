plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
}

fun Project.optionalProperty(name: String): String? =
	findProperty(name)?.toString() ?: System.getenv(name.uppercase().replace('.', '_'))

fun versionCodeFrom(versionName: String): Int {
	val (major, minor, patch) = versionName.substringBefore('-').split('.').map(String::toInt)
	val preRelease = versionName.substringAfter('-', "").substringAfter('.', "").toIntOrNull() ?: 99

	return major * 1_000_000 + minor * 10_000 + patch * 100 + preRelease
}

val appVersionName = project.optionalProperty("screensavarr.version")?.removePrefix("v") ?: "0.0.0-dev.1"
val releaseKeystoreFile = project.optionalProperty("keystore.file")
val releaseKeystorePassword = project.optionalProperty("keystore.password")
val releaseKeyAlias = project.optionalProperty("signing.key.alias")
val releaseKeyPassword = project.optionalProperty("signing.key.password")
val releaseSigningValues = listOf(releaseKeystoreFile, releaseKeystorePassword, releaseKeyAlias, releaseKeyPassword)

require(releaseSigningValues.all { it == null } || releaseSigningValues.all { it != null }) {
	"Release signing requires keystore.file, keystore.password, signing.key.alias, and signing.key.password."
}

android {
	namespace = "app.screensavarr"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	defaultConfig {
		minSdk = libs.versions.android.minSdk.get().toInt()
		targetSdk = libs.versions.android.targetSdk.get().toInt()

		applicationId = "app.screensavarr"
		versionCode = versionCodeFrom(appVersionName)
		versionName = appVersionName
	}

	buildFeatures {
		compose = true
	}

	dependenciesInfo {
		includeInBundle = false
		includeInApk = false
	}

	signingConfigs {
		if (releaseSigningValues.all { it != null }) {
			create("release") {
				storeFile = file(requireNotNull(releaseKeystoreFile))
				storePassword = requireNotNull(releaseKeystorePassword)
				keyAlias = requireNotNull(releaseKeyAlias)
				keyPassword = requireNotNull(releaseKeyPassword)
			}
		}
	}

	buildTypes {
		release {
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))

			signingConfig = signingConfigs.findByName("release")
		}

		debug {
			// Use different application id to run release and debug at the same time
			applicationIdSuffix = ".debug"
		}
	}
}

dependencies {
	implementation(libs.kotlinx.coroutines)
	implementation(libs.kotlinx.serialization.json)

	implementation(libs.androidx.core)
	implementation(libs.androidx.activity)
	implementation(libs.androidx.activity.compose)
	implementation(libs.bundles.androidx.lifecycle)
	implementation(libs.bundles.androidx.compose)

}
