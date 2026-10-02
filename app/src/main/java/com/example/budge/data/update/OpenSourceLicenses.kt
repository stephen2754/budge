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
 * Every runtime dependency of this build is Apache-2.0, which is why the licence column is
 * uniform; the full text of each licence lives at the linked project page rather than being
 * copied into the app — every link here is the project's own repository, and each one was
 * checked to resolve. Room and DataStore have no repository of their own because they are
 * part of AndroidX, whose monorepo (and the DataStore directory inside it) is what they
 * link to.
 */
val openSourceComponents: List<OpenSourceComponent> =
    listOf(
        OpenSourceComponent("AndroidX / Jetpack Compose", "Apache-2.0", "https://github.com/androidx/androidx"),
        OpenSourceComponent("Kotlin standard library", "Apache-2.0", "https://github.com/JetBrains/kotlin"),
        OpenSourceComponent("kotlinx.coroutines", "Apache-2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
        OpenSourceComponent("Dagger / Hilt", "Apache-2.0", "https://github.com/google/dagger"),
        OpenSourceComponent("Room", "Apache-2.0", "https://github.com/androidx/androidx"),
        OpenSourceComponent("DataStore", "Apache-2.0", "https://github.com/androidx/androidx/tree/androidx-main/datastore"),
        OpenSourceComponent("Gson", "Apache-2.0", "https://github.com/google/gson"),
    )
