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
    const val RUBLE = "₽"
    const val POUND = "£"

    /**
     * The `¥` sign. The yuan and the yen both use it.
     *
     * Amounts show the sign on its own (`¥1,234.56`). Which of the two currencies is
     * meant is spelled out once, in the picker's "CNY / JPY" label.
     */
    const val YEN_SIGN = "¥"

    /** Every choice offered in Settings, in display order. */
    val choices = listOf(DOLLAR, EURO, RUBLE, POUND, YEN_SIGN)

    /**
     * The currencies each sign stands for, shown beside it in the picker.
     *
     * Where a sign is shared, all of its common users are listed, so someone holding
     * Egyptian pounds recognises the `£` row. The lists stop short of being exhaustive:
     *
     * - `$` covers the majors that write the sign bare. Brazil (`R$`) and Mexico
     *   (`Mex$`) add a prefix, so they do not really share it, and the peso family the
     *   brief excluded is left out.
     * - `£` covers the sovereign currencies written `£`. The six sterling-pegged
     *   territory pounds (Falkland, Gibraltar, Guernsey, Jersey, Man, Saint Helena)
     *   are not currencies a reader is likely to hold.
     * - `€` and `₽` stay single. No other currency uses the euro sign, and the ruble
     *   sign is Russia's alone; Belarus writes `Br`.
     */
    val labels =
        mapOf(
            DOLLAR to "USD / CAD / AUD / NZD / HKD / SGD / TWD",
            EURO to "EUR",
            RUBLE to "RUB",
            POUND to "GBP / EGP / LBP / SYP / SSP / SDG",
            YEN_SIGN to "CNY / JPY",
        )
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
