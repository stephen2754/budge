package com.example.budge.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme =
    lightColorScheme(
        primary = md_theme_light_primary,
        onPrimary = md_theme_light_onPrimary,
        primaryContainer = md_theme_light_primaryContainer,
        onPrimaryContainer = md_theme_light_onPrimaryContainer,
        secondary = md_theme_light_secondary,
        onSecondary = md_theme_light_onSecondary,
        secondaryContainer = md_theme_light_secondaryContainer,
        onSecondaryContainer = md_theme_light_onSecondaryContainer,
        tertiary = md_theme_light_tertiary,
        onTertiary = md_theme_light_onTertiary,
        tertiaryContainer = md_theme_light_tertiaryContainer,
        onTertiaryContainer = md_theme_light_onTertiaryContainer,
        error = md_theme_light_error,
        onError = md_theme_light_onError,
        errorContainer = md_theme_light_errorContainer,
        onErrorContainer = md_theme_light_onErrorContainer,
        background = md_theme_light_background,
        onBackground = md_theme_light_onBackground,
        surface = md_theme_light_surface,
        onSurface = md_theme_light_onSurface,
        surfaceVariant = md_theme_light_surfaceVariant,
        onSurfaceVariant = md_theme_light_onSurfaceVariant,
        outline = md_theme_light_outline,
        outlineVariant = md_theme_light_outlineVariant,
        inverseSurface = md_theme_light_inverseSurface,
        inverseOnSurface = md_theme_light_inverseOnSurface,
        inversePrimary = md_theme_light_inversePrimary,
        surfaceTint = md_theme_light_surfaceTint,
        surfaceDim = md_theme_light_surfaceDim,
        surfaceBright = md_theme_light_surfaceBright,
        surfaceContainerLowest = md_theme_light_surfaceContainerLowest,
        surfaceContainerLow = md_theme_light_surfaceContainerLow,
        surfaceContainer = md_theme_light_surfaceContainer,
        surfaceContainerHigh = md_theme_light_surfaceContainerHigh,
        surfaceContainerHighest = md_theme_light_surfaceContainerHighest,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = md_theme_dark_primary,
        onPrimary = md_theme_dark_onPrimary,
        primaryContainer = md_theme_dark_primaryContainer,
        onPrimaryContainer = md_theme_dark_onPrimaryContainer,
        secondary = md_theme_dark_secondary,
        onSecondary = md_theme_dark_onSecondary,
        secondaryContainer = md_theme_dark_secondaryContainer,
        onSecondaryContainer = md_theme_dark_onSecondaryContainer,
        tertiary = md_theme_dark_tertiary,
        onTertiary = md_theme_dark_onTertiary,
        tertiaryContainer = md_theme_dark_tertiaryContainer,
        onTertiaryContainer = md_theme_dark_onTertiaryContainer,
        error = md_theme_dark_error,
        onError = md_theme_dark_onError,
        errorContainer = md_theme_dark_errorContainer,
        onErrorContainer = md_theme_dark_onErrorContainer,
        background = md_theme_dark_background,
        onBackground = md_theme_dark_onBackground,
        surface = md_theme_dark_surface,
        onSurface = md_theme_dark_onSurface,
        surfaceVariant = md_theme_dark_surfaceVariant,
        onSurfaceVariant = md_theme_dark_onSurfaceVariant,
        outline = md_theme_dark_outline,
        outlineVariant = md_theme_dark_outlineVariant,
        inverseSurface = md_theme_dark_inverseSurface,
        inverseOnSurface = md_theme_dark_inverseOnSurface,
        inversePrimary = md_theme_dark_inversePrimary,
        surfaceTint = md_theme_dark_surfaceTint,
        surfaceDim = md_theme_dark_surfaceDim,
        surfaceBright = md_theme_dark_surfaceBright,
        surfaceContainerLowest = md_theme_dark_surfaceContainerLowest,
        surfaceContainerLow = md_theme_dark_surfaceContainerLow,
        surfaceContainer = md_theme_dark_surfaceContainer,
        surfaceContainerHigh = md_theme_dark_surfaceContainerHigh,
        surfaceContainerHighest = md_theme_dark_surfaceContainerHighest,
    )

/**
 * App theme. `themeMode` picks light/dark (or system), and on Android 12+
 * dynamic color is preferred, falling back to the static Material 3 schemes
 * below. Also paints the status bar to match the surface color.
 *
 * The Material 3 typography scale is used as-is: the previous `Type.kt` was a
 * verbatim copy of that baseline, so passing it changed nothing.
 */
@Composable
fun BudgeTheme(
    themeMode: String = "system",
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val isDark =
        when (themeMode) {
            "dark" -> true
            "light" -> false
            else -> isSystemInDarkTheme()
        }
    val colorScheme =
        when {
            // Dynamic color (wallpaper-derived) is only available on Android 12+.
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            isDark -> {
                DarkColorScheme
            }

            else -> {
                LightColorScheme
            }
        }

    val view = LocalView.current
    if (!view.isInEditMode) {
        // Match the status bar to the theme surface so it blends with the app.
        // `statusBarColor` is deprecated and ignored from Android 15 onward,
        // where the app is edge-to-edge by default; only the icon appearance
        // still needs setting there.
        SideEffect {
            // A non-Activity host is possible (previews, embedded Compose), so
            // the cast is checked rather than forced.
            val window = (view.context as? Activity)?.window
            if (window != null) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    @Suppress("DEPRECATION")
                    window.statusBarColor = colorScheme.surface.toArgb()
                }
                val controller = WindowCompat.getInsetsController(window, view)
                // The app can force a theme that disagrees with the system, and
                // enableEdgeToEdge picks the icon colour from the *system* theme, so the
                // icons have to be re-stated here or they end up invisible against the
                // bar the app just painted.
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(LocalIsDarkTheme provides isDark) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

/**
 * Whether the active scheme is the dark one. Exposed so semantic colors with no
 * Material role — such as the "money in" green — can follow the theme instead of
 * being re-hardcoded as a literal in each screen.
 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/** The income ("money in") color for the active theme. */
@Composable
fun incomeColor(): Color = if (LocalIsDarkTheme.current) md_theme_dark_income else md_theme_light_income

/**
 * The window insets a content page needs to handle.
 *
 * None of them. The `NavigationBar` below the pager owns the bottom system inset, so a
 * page that reserved it again would leave a band of background above the bar and lift
 * the FAB by the same amount. The status bar is handled by each page's `TopAppBar`, and
 * the app is portrait-only, so there is nothing to dodge sideways either.
 */
val pageWindowInsets: WindowInsets = WindowInsets(0, 0, 0, 0)
