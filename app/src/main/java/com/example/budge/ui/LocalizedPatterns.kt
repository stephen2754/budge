package com.example.budge.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The app locale's own arrangement for a CLDR skeleton such as `"yMMMMd"`.
 *
 * The date formatters used to hard-code patterns — `"MMM d, EEE"`, `"d MMMM yyyy"`,
 * `"HH:mm"` — which put every language into an English order: French read "août 11, mar."
 * where it writes "mar. 11 août", a US reader got "11 August 2026", and every 12-hour
 * locale was shown a 24-hour clock. The platform already knows the order, separator and
 * clock each locale uses, so the patterns are asked of it rather than guessed at.
 *
 * This sits apart from `Formats.kt` because it is the one formatting helper that needs the
 * Android framework; everything in that file stays plain Kotlin and stays unit-tested.
 */
@Composable
fun localizedPattern(
    skeleton: String,
    fallback: String,
): String {
    val locale = LocalAppLocale.current
    return remember(skeleton, locale) {
        // The platform is asked for a pattern; the caller's old fixed pattern is the
        // answer if it will not give one, so an unhelpful skeleton costs a wrong order
        // rather than a crash on a screen the user is looking at.
        runCatching { DateFormat.getBestDateTimePattern(locale, skeleton) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: fallback
    }
}

/**
 * True when the device itself is set to a 24-hour clock.
 *
 * The time picker used to be pinned to 24 hours, which contradicts the rest of the system
 * for anyone who chose otherwise.
 */
@Composable
fun uses24HourClock(): Boolean {
    val context = LocalContext.current
    return remember(context) { DateFormat.is24HourFormat(context) }
}
