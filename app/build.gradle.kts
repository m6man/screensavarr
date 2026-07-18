plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
}

fun Project.getProperty(name: String): String? =
	findProperty(name)?.toString() ?: System.getenv(name.uppercase().replace('.', '_'))

fun Project.getVersionName(fallback: String = "0.0.0-dev.1"): String =
	getProperty("screensavarr.version")?.removePrefix("v") ?: fallback

fun getVersionCode(versionName: String): Int {
	val (core, preRelease) = versionName.split('-', limit = 2).let { parts ->
		parts.first() to parts.getOrNull(1)
	}
	val (major, minor, patch) = core.split('.').map(String::toInt).take(3)
	val preReleaseNumber = preRelease?.substringAfter('.')?.toIntOrNull() ?: 99

	return major * 1_000_000 + minor * 10_000 + patch * 100 + preReleaseNumber
}

android {
	namespace = "app.screensavarr"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	defaultConfig {
		minSdk = libs.versions.android.minSdk.get().toInt()
		targetSdk = libs.versions.android.targetSdk.get().toInt()

		applicationId = "app.screensavarr"
		versionName = project.getVersionName()
		versionCode = getVersionCode(versionName!!)
	}

	buildFeatures {
		compose = true
	}

	signingConfigs {
		val keystoreFile = getProperty("keystore.file")
		val keystorePassword = getProperty("keystore.password")
		val signingKeyAlias = getProperty("signing.key.alias")
		val signingKeyPassword = getProperty("signing.key.password")

		if (keystoreFile != null && keystorePassword != null && signingKeyAlias != null && signingKeyPassword != null) {
			create("release") {
				storeFile = file(keystoreFile)
				storePassword = keystorePassword
				keyAlias = signingKeyAlias
				keyPassword = signingKeyPassword
			}
		}
	}

	dependenciesInfo {
		includeInBundle = false
		includeInApk = false
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

base.archivesName.set("screensavarr-v${project.getVersionName()}")

tasks.register("versionTxt") {
	val path = layout.buildDirectory.asFile.get().resolve("version.txt")

	doLast {
		val versionString = "v${android.defaultConfig.versionName}=${android.defaultConfig.versionCode}"
		logger.info("Writing [$versionString] to $path")
		path.writeText("$versionString\n")
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
