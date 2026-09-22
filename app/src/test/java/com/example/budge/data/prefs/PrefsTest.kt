package com.example.budge.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Tests for the language and first-launch currency rules.
 *
 * The currency rules are the ones a user notices immediately (a euro sign on a
 * dollar budget is wrong on every screen), and the yuan/yen codes exist precisely
 * because a bare `¥` cannot be told apart.
 */
class PrefsTest {
    // -----------------------------------------------------------------------
    // Currency
    // -----------------------------------------------------------------------

    @Test
    fun `chinese and japanese share one yen sign`() {
        // The same symbol for both: an amount shows "¥1,234.56" and says nothing about
        // CNY versus JPY. Which currencies the sign covers is the picker's label.
        assertEquals(Currencies.YEN_SIGN, defaultCurrencyFor(Locale.SIMPLIFIED_CHINESE))
        assertEquals(Currencies.YEN_SIGN, defaultCurrencyFor(Locale.JAPANESE))
        assertEquals("¥", Currencies.YEN_SIGN)
    }

    @Test
    fun `russian gets the ruble`() {
        assertEquals(Currencies.RUBLE, defaultCurrencyFor(Locale.forLanguageTag("ru")))
        assertEquals(Currencies.RUBLE, defaultCurrencyFor(Locale.forLanguageTag("ru-RU")))
    }

    @Test
    fun `other European languages get the euro`() {
        val european = listOf("fr", "de", "es", "it", "pt", "nl", "pl", "sv", "da", "fi", "el", "cs")
        for (tag in european) {
            assertEquals("$tag should be EUR", Currencies.EURO, defaultCurrencyFor(Locale.forLanguageTag(tag)))
        }
        // Regions do not change it: Austrian German and Brazilian Portuguese still map by language.
        assertEquals(Currencies.EURO, defaultCurrencyFor(Locale.forLanguageTag("de-AT")))
        assertEquals(Currencies.EURO, defaultCurrencyFor(Locale.forLanguageTag("pt-BR")))
    }

    @Test
    fun `english and every unlisted language fall back to the dollar`() {
        // English keeps `$` even though it is a European language.
        assertEquals(Currencies.DOLLAR, defaultCurrencyFor(Locale.ENGLISH))
        assertEquals(Currencies.DOLLAR, defaultCurrencyFor(Locale.US))
        assertEquals(Currencies.DOLLAR, defaultCurrencyFor(Locale.UK))
        // Neither European nor the three special cases.
        assertEquals(Currencies.DOLLAR, defaultCurrencyFor(Locale.KOREA))
        assertEquals(Currencies.DOLLAR, defaultCurrencyFor(Locale.forLanguageTag("ar")))
        assertEquals(Currencies.DOLLAR, defaultCurrencyFor(Locale.forLanguageTag("th")))
        assertEquals(Currencies.DOLLAR, defaultCurrencyFor(Locale.forLanguageTag("hi")))
    }

    @Test
    fun `every offered sign names the currencies it stands for`() {
        // A missing label would render an empty line in the picker, and the point of the
        // labels is that a shared sign is not silently attributed to one country.
        for (token in Currencies.choices) {
            val label = Currencies.labels[token]
            assertTrue("$token has no label", !label.isNullOrBlank())
        }
        assertEquals(Currencies.choices.size, Currencies.labels.size)
    }

    @Test
    fun `a shared sign lists every common currency that uses it`() {
        val dollar = Currencies.labels.getValue(Currencies.DOLLAR)
        for (code in listOf("USD", "CAD", "AUD", "NZD", "HKD", "SGD", "TWD")) {
            assertTrue("$$ should cover $code", dollar.contains(code))
        }

        val pound = Currencies.labels.getValue(Currencies.POUND)
        for (code in listOf("GBP", "EGP", "LBP", "SYP", "SSP", "SDG")) {
            assertTrue("£ should cover $code", pound.contains(code))
        }

        assertTrue(Currencies.labels.getValue(Currencies.YEN_SIGN).contains("CNY"))
        assertTrue(Currencies.labels.getValue(Currencies.YEN_SIGN).contains("JPY"))
    }

    @Test
    fun `a sign no other currency uses stays a single code`() {
        // The euro sign is the euro's alone, and ₽ was designed for the Russian ruble
        // (Belarus writes "Br"), so neither entry is a list.
        assertEquals("EUR", Currencies.labels.getValue(Currencies.EURO))
        assertEquals("RUB", Currencies.labels.getValue(Currencies.RUBLE))
    }

    @Test
    fun `every offered currency is a distinct sign`() {
        assertEquals(Currencies.choices.size, Currencies.choices.toSet().size)
        assertTrue(Currencies.choices.contains(Currencies.RUBLE))
        assertTrue(Currencies.choices.contains(Currencies.YEN_SIGN))
        // One choice list, five signs, and no token that is longer than a sign.
        assertEquals(5, Currencies.choices.size)
        assertTrue(Currencies.choices.all { it.length == 1 })
    }

    // -----------------------------------------------------------------------
    // Language
    // -----------------------------------------------------------------------

    @Test
    fun `each stored language code maps to its locale`() {
        val expected =
            mapOf(
                Prefs.CHINESE to "zh",
                Prefs.ENGLISH to "en",
                Prefs.FRENCH to "fr",
                Prefs.GERMAN to "de",
                Prefs.SPANISH to "es",
                Prefs.RUSSIAN to "ru",
                Prefs.JAPANESE to "ja",
                Prefs.ITALIAN to "it",
                Prefs.PORTUGUESE to "pt",
            )

        for ((code, language) in expected) {
            assertEquals("stored code $code", language, appLocaleFor(code).language)
        }
        // Every one of them is also a language the app ships strings for.
        assertEquals(9, expected.values.toSet().size)
    }

    @Test
    fun `follow system and unknown codes resolve through the device locale`() {
        // Following the system means: the device language when the app ships it,
        // English otherwise (including an unreadable locale).
        val device = deviceLocale().language.lowercase(Locale.ROOT)
        val expected = if (device in Prefs.SUPPORTED_LANGUAGES) device else "en"

        assertEquals(expected, appLocaleFor(Prefs.FOLLOW_SYSTEM).language)
        assertEquals(expected, appLocaleFor(null).language)
        assertEquals(expected, appLocaleFor("kl-GL").language)
    }

    @Test
    fun `a readable language with no translation formats as English`() {
        // Android falls back to values/ (English) for these, so the date formatting
        // must not stay in a language the labels are not in.
        assertEquals(Locale.ENGLISH, supportedLocaleFor(Locale.KOREA))
        assertEquals(Locale.ENGLISH, supportedLocaleFor(Locale.forLanguageTag("ar")))
        assertEquals(Locale.ENGLISH, supportedLocaleFor(Locale.forLanguageTag("th")))
        assertEquals(Locale.ENGLISH, supportedLocaleFor(Locale.forLanguageTag("hi")))

        // A supported language is kept as-is, regions included.
        assertEquals("fr", supportedLocaleFor(Locale.FRENCH).language)
        assertEquals("pt", supportedLocaleFor(Locale.forLanguageTag("pt-BR")).language)
        assertEquals("zh", supportedLocaleFor(Locale.SIMPLIFIED_CHINESE).language)
    }

    @Test
    fun `the device locale is never null`() {
        // The whole point of deviceLocale() is that an unreadable locale degrades to
        // English rather than leaving the app without one.
        assertTrue(deviceLocale().language.isNotEmpty())
    }
}
