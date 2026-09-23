// Budge app module build script.
//
// Single-module Android app (Jetpack Compose + Room + Hilt). The Room plugin
// exports schema JSON files so database migrations can be reviewed and tested.
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Export Room schemas (one JSON per schema version) under app/schemas.
room {
    schemaDirectory("$projectDir/schemas")
}

// Every language the app itself translates (values-*/strings.xml). Keep in step with
// that folder list: a locale missing here is silently dropped from the APK.
val LOCALES = listOf("en", "zh", "fr", "de", "es", "ru", "ja", "it", "pt")

// Release signing is opt-in. `keystore.properties` holds the keystore path and its two
// passwords and is git-ignored, so a clone without it still builds — the release APK just
// comes out unsigned and therefore not installable. See README "Building a release".
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseKeystore = keystorePropertiesFile.exists()

android {
    namespace = "com.example.budge"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.budge"
        minSdk = 26 // Android 8.0
        targetSdk = 35
        versionCode = 2
        // The channel a build belongs to is read from this string (see
        // AppVersion.channelOf): an "-alpha.N" or "-beta.N" suffix is what makes the
        // update check offer prereleases, and the absence of one is what keeps them out.
        versionName = "0.1.0-alpha.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The app ships nine languages; the AndroidX libraries ship translations for
        // about eighty more, and every one of them lands in resources.arsc. Keep only
        // the languages we actually translate. (AGP 8.9+ spells this
        // `androidResources.localeFilters`; this project is pinned to 8.7.)
        resourceConfigurations += LOCALES
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 + resource shrinking for release builds.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Left unset when keystore.properties is absent, which is what keeps the
            // build working on a machine that has no signing key.
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    lint {
        // `lintVitalRelease` runs as part of `assembleRelease`. On a machine whose
        // platform-tools report their revision as "37.0", AGP 8.7.3 fails to parse it
        // ("For input string: \"37.0\"") and the release build dies before lint has read
        // a single source file. `lintDebug` hits the same parser, so nothing is lost by
        // skipping the vital check here; run lint on a machine with older platform-tools,
        // or drop this block once the toolchain is upgraded.
        checkReleaseBuilds = false
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
        // Generates BuildConfig.VERSION_NAME, which the About row shows, so the
        // displayed version cannot drift from versionName above.
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"

            // Reflection metadata for the Kotlin standard library. Only kotlin-reflect
            // ever reads these files, and this app has no use for kotlin-reflect: R8
            // strips every class of it, so the metadata would ship unread.
            excludes += "**/*.kotlin_builtins"
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose (BOM manages versions)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.ui.test.manifest)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Navigation: the tabs live in a HorizontalPager, so only Hilt's
    // ViewModel-for-Compose integration is needed; navigation-compose itself is
    // not used and arrives transitively with it anyway.
    implementation(libs.hilt.navigation.compose)

    // Room (KSP annotation processing)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt (KSP annotation processing)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Gson
    implementation(libs.gson)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    androidTestImplementation(libs.room.testing)
}
