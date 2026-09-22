package com.example.budge

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.example.budge.data.prefs.Prefs
import com.example.budge.data.prefs.appLocaleFor
import com.example.budge.di.dataStore
import com.example.budge.ui.LocalAppLocale
import com.example.budge.ui.navigation.BudgeNavGraph
import com.example.budge.ui.theme.BudgeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Single-activity app entry point. Applies the persisted language by wrapping
 * the base context with a new configuration locale, observes the theme and
 * language preferences, and recreates itself whenever the language changes.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var dataStore: DataStore<Preferences>

    // The locale must be applied here, before any resources are created,
    // so every string the Activity loads uses the chosen language. Applying
    // it via updateBaseContextLocale keeps LocalContext.current the Activity
    // itself, which Hilt's hiltViewModel() requires.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(updateBaseContextLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw edge to edge on every supported release, not only where the platform
        // forces it (targetSdk 35 does so on Android 15+).
        //
        // This is what makes the bottom navigation bar come out the right height.
        // Where the window is *not* edge to edge, the decor has already inset the
        // Compose root by the system bars while `WindowInsets.systemBars` still reports
        // them, so `NavigationBar` padded itself a second time: the bar measured taller
        // than the row it draws its icons in, and the extra band above it showed the
        // page background instead of content.
        enableEdgeToEdge()
        setContent {
            val theme by dataStore.data
                .map { it[Prefs.themeKey] ?: Prefs.FOLLOW_SYSTEM }
                .collectAsState(initial = Prefs.FOLLOW_SYSTEM)

            // Start from the language actually applied in attachBaseContext so the
            // initial value never differs from the applied locale (avoids a
            // recreate() loop on startup).
            val language by dataStore.data
                .map { it[Prefs.languageKey] ?: Prefs.FOLLOW_SYSTEM }
                .collectAsState(initial = appliedLanguage)

            val appLocale = remember(language) { appLocaleFor(language) }

            val activity = LocalContext.current as Activity
            // Recreate the Activity when the stored language diverges from the
            // one already applied to the base context; this makes the system
            // rebuild resources/strings under the new locale.
            LaunchedEffect(language) {
                if (appliedLanguage != language) {
                    appliedLanguage = language
                    activity.recreate()
                }
            }

            CompositionLocalProvider(LocalAppLocale provides appLocale) {
                BudgeTheme(themeMode = theme) {
                    BudgeNavGraph()
                }
            }
        }
    }

    /**
     * Reads the persisted language and returns a context whose configuration
     * carries the matching locale, falling back to the device locale on
     * `"system"` or on any read failure.
     *
     * The read is blocking by necessity: it has to complete before any resource
     * is resolved, which is earlier than any coroutine can run. It is one small
     * file read, and it is the only remaining synchronous DataStore access — the
     * database and preference work that used to sit on this path now runs on the
     * application scope. Adopting the platform per-app language API (API 33+, with
     * `android:localeConfig`) would remove it entirely.
     */
    private fun updateBaseContextLocale(context: Context): Context {
        val language =
            try {
                runBlocking {
                    context.applicationContext.dataStore.data
                        .first()[Prefs.languageKey] ?: Prefs.FOLLOW_SYSTEM
                }
            } catch (_: Exception) {
                Prefs.FOLLOW_SYSTEM
            }
        appliedLanguage = language
        val locale = appLocaleFor(language)
        return try {
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            context.createConfigurationContext(config)
        } catch (_: Exception) {
            context
        }
    }

    companion object {
        // Locale currently applied to the base context. Kept process-wide and
        // volatile so it survives recreation and is visible to both the
        // attachBaseContext read and the onCreate recomposition check.
        @Volatile
        private var appliedLanguage: String = Prefs.FOLLOW_SYSTEM
    }
}
