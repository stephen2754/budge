package com.example.budge.data.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.Locale

/**
 * Every DataStore key the app uses, plus the values it accepts.
 *
 * Keys were once written out at each call site. Keeping them here means a typo shows
 * up as a compile error instead of as a preference that silently reads as unset.
 */
object Prefs {
    const val LANGUAGE = "language"
    const val THEME = "theme"
    const val CURRENCY_SYMBOL = "currency_symbol"

    /**
     * Set once the seeded categories have been inserted.
     *
     * The flag, not "is the table empty", is what decides whether to seed. Otherwise
     * deleting every category would bring them all back on the next launch.
     */
    const val CATEGORIES_SEEDED = "categories_seeded"

    /** "Follow the device locale", plus every language the app ships translations for. */
    const val FOLLOW_SYSTEM = "system"
    const val CHINESE = "zh"
    const val ENGLISH = "en"
    const val FRENCH = "fr"
    const val GERMAN = "de"
    const val SPANISH = "es"
    const val RUSSIAN = "ru"
    const val JAPANESE = "ja"
    const val ITALIAN = "it"
    const val PORTUGUESE = "pt"

    const val LIGHT = "light"
    const val DARK = "dark"

    /** Every language the app actually ships translations for. */
    val SUPPORTED_LANGUAGES = setOf(CHINESE, ENGLISH, FRENCH, GERMAN, SPANISH, RUSSIAN, JAPANESE, ITALIAN, PORTUGUESE)

    val languageKey = stringPreferencesKey(LANGUAGE)
    val themeKey = stringPreferencesKey(THEME)
    val currencySymbolKey = stringPreferencesKey(CURRENCY_SYMBOL)
    val categoriesSeededKey = booleanPreferencesKey(CATEGORIES_SEEDED)
}

/** The device locale, or English if it cannot be read. */
fun deviceLocale(): Locale =
    try {
        Locale.getDefault() ?: Locale.ENGLISH
    } catch (_: Throwable) {
        Locale.ENGLISH
    }

/**
 * [locale] if the app has translations for it, English otherwise.
 *
 * Android falls back to `values/` for an untranslated language, so the formatting
 * locale has to fall back with it. Otherwise a Korean phone would show English labels
 * next to Korean month names.
 */
fun supportedLocaleFor(locale: Locale): Locale =
    if (locale.language.lowercase(Locale.ROOT) in Prefs.SUPPORTED_LANGUAGES) locale else Locale.ENGLISH

/** The locale a stored language value means. `"system"` and unknown values use the device locale. */
fun appLocaleFor(language: String?): Locale =
    when (language) {
        Prefs.CHINESE -> Locale.SIMPLIFIED_CHINESE
        Prefs.ENGLISH -> Locale.ENGLISH
        Prefs.FRENCH -> Locale.FRENCH
        Prefs.GERMAN -> Locale.GERMAN
        Prefs.SPANISH -> Locale.forLanguageTag("es")
        Prefs.RUSSIAN -> Locale.forLanguageTag("ru")
        Prefs.JAPANESE -> Locale.JAPANESE
        Prefs.ITALIAN -> Locale.ITALIAN
        Prefs.PORTUGUESE -> Locale.forLanguageTag("pt")
        else -> supportedLocaleFor(deviceLocale())
    }

/** Currency tokens the app can show in front of an amount. */
object Currencies {
    const val DOLLAR = "$"
    const val EURO = "€"

    /**
     * The `¥` sign. The yuan and the yen both use it.
     *
     * Amounts show the sign on its own (`¥1,234.56`), and which of the two currencies is
     * meant is left to the reader: a sign is not a currency, and the app does not know
     * which one the ledger is kept in.
     */
    const val YEN_SIGN = "¥"

    const val POUND = "£"
    const val RUBLE = "₽"
    const val RUPEE = "₹"
    const val WON = "₩"
    const val LIRA = "₺"

    /** Brazil writes its currency this way: a letter and the sign, never the sign alone. */
    const val REAL = "R$"

    /**
     * Every choice offered in Settings, in display order — the signs a reader is most
     * likely to be looking for first.
     *
     * The picker shows these tokens and nothing beside them. It used to name the
     * currencies sharing each sign ("USD / CAD / AUD / ..."), which read as though the
     * row were choosing a currency rather than a sign: most of these signs are shared,
     * the lists were never complete, and a figure on screen shows the sign alone anyway.
     *
     * The set stops at signs that are both distinct and common. The sterling-pegged
     * territory pounds, the peso and dollar families that only prefix a `$`, and the
     * currencies whose sign is a word (`kr`, `zł`, `R`, `CHF`) are not offered.
     */
    val choices =
        listOf(DOLLAR, EURO, YEN_SIGN, POUND, RUBLE, RUPEE, WON, LIRA, REAL)
}

/**
 * European languages that have no dedicated token in this app. English is excluded
 * on purpose (it keeps `$`), and Russian is handled before this set.
 */
private val EUROPEAN_LANGUAGES =
    setOf(
        "bg", "bs", "ca", "cs", "cy", "da", "de", "el", "es", "et", "eu", "fi", "fr", "ga", "gl",
        "hr", "hu", "is", "it", "lt", "lv", "mk", "mt", "nb", "nl", "nn", "no", "pl", "pt", "ro",
        "sk", "sl", "sq", "sr", "sv", "uk",
    )

/**
 * The sign a device on [locale] starts with.
 *
 * Chinese and Japanese get `¥`, Russian `₽`, other European languages `€`, and
 * everything else, English included, `$`.
 *
 * Only a first launch uses this. A stored sign is never rewritten.
 */
fun defaultCurrencyFor(locale: Locale): String =
    when (locale.language.lowercase(Locale.ROOT)) {
        Prefs.CHINESE, Prefs.JAPANESE -> Currencies.YEN_SIGN
        Prefs.RUSSIAN -> Currencies.RUBLE
        in EUROPEAN_LANGUAGES -> Currencies.EURO
        else -> Currencies.DOLLAR
    }
