plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// The signing key reaches Gradle only through environment variables.
// If any of them is missing, the release build is produced unsigned.
val keystoreFile: String? = System.getenv("KEYSTORE_FILE")
val keystorePassword: String? = System.getenv("KEYSTORE_PASSWORD")
val keyAliasName: String? = System.getenv("KEY_ALIAS")
val keyPasswordValue: String? = System.getenv("KEY_PASSWORD")

// The version name is written once, in gradle.properties. The version code grows with every GitHub run,
// so every upload is higher than the one before. The offset keeps it above the first builds, which used 1.
val appVersionName: String = providers.gradleProperty("appVersionName").get()
val githubRunNumber: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
val versionCodeOffset = 100

val canSignRelease: Boolean =
    !keystoreFile.isNullOrBlank() &&
        file(keystoreFile).isFile &&
        !keystorePassword.isNullOrBlank() &&
        !keyAliasName.isNullOrBlank() &&
        !keyPasswordValue.isNullOrBlank()

android {
    namespace = "com.westly.twelvepits"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.westly.twelvepits"
        minSdk = 26
        targetSdk = 36
        versionCode = githubRunNumber + versionCodeOffset
        versionName = appVersionName
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = file(requireNotNull(keystoreFile))
                storePassword = keystorePassword
                keyAlias = keyAliasName
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            // R8 shrinks and renames the code, and unused resources are removed.
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (canSignRelease) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

// Show every unit test and its printed output in the GitHub Actions log.
tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // Two phones: finds the other phone and sends the moves over Bluetooth and Wi-Fi, with no internet.
    implementation(libs.play.services.nearby)
    implementation(libs.play.services.base)

    testImplementation(libs.junit)
}
