package com.example.budge.data.update

/**
 * One third-party library the app is built on, for the licence screen.
 *
 * Apache-2.0 obliges an app to carry the attributions of what it ships, so this list is
 * kept beside the build file: it names the components the released APK actually
 * contains. Build-time-only tooling (KSP, the Compose compiler plugin) is not shipped
 * and is therefore not listed.
 */
data class OpenSourceComponent(
    val name: String,
    val license: String,
    val url: String,
)

/**
 * Everything the app ships that carries a licence notice.
 *
 * Every runtime dependency of this build is Apache-2.0, which is why the licence column
 * is uniform; the full text of each licence lives at the linked project page rather than
 * being copied into the app.
 */
val openSourceComponents: List<OpenSourceComponent> =
    listOf(
        OpenSourceComponent("AndroidX / Jetpack Compose", "Apache-2.0", "https://github.com/androidx/androidx"),
        OpenSourceComponent("Kotlin standard library", "Apache-2.0", "https://github.com/JetBrains/kotlin"),
        OpenSourceComponent("kotlinx.coroutines", "Apache-2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
        OpenSourceComponent("Dagger / Hilt", "Apache-2.0", "https://github.com/google/dagger"),
        OpenSourceComponent("Room", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/room"),
        OpenSourceComponent("DataStore", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/datastore"),
        OpenSourceComponent("Gson", "Apache-2.0", "https://github.com/google/gson"),
    )
