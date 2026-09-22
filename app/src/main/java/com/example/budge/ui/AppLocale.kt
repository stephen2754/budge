package com.example.budge.ui

import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/**
 * CompositionLocal exposing the locale the app is currently rendering in
 * (which may differ from the device locale when the user picked a language).
 * Defaults to the device locale. Provided in [MainActivity] via
 * CompositionLocalProvider.
 */
val LocalAppLocale = staticCompositionLocalOf { Locale.getDefault() }
