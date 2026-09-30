package com.example.budge.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `every offered choice is a sign, not a currency code`() {
        // The picker lists signs and nothing else. A three-letter ISO code here would be a
        // label that leaked back into a place that should show a symbol, and "R$" is the
        // one two-character sign on offer.
        for (token in Currencies.choices) {
            assertTrue("a blank choice would render an empty row", token.isNotBlank())
            assertTrue("$token is too long to be a sign", token.length <= 2)
            assertFalse("$token is an ISO code, not a sign", Regex("^[A-Z]{3}$").matches(token))
        }
    }

    @Test
    fun `the offered signs are distinct and stay a short list`() {
        assertEquals(Currencies.choices.size, Currencies.choices.toSet().size)
        assertTrue(Currencies.choices.contains(Currencies.RUBLE))
        assertTrue(Currencies.choices.contains(Currencies.YEN_SIGN))
        assertTrue(Currencies.choices.contains(Currencies.RUPEE))
        // Short on purpose: this is a list of signs, and every extra row is another choice
        // for a reader who only wants the one their ledger is kept in.
        assertTrue("the picker is getting long: ${Currencies.choices.size} rows", Currencies.choices.size <= 12)
    }

    @Test
    fun `every default the app can pick is on offer`() {
        // BudgeApplication replaces a stored symbol that is not in `choices` with the
        // locale default, so a default missing from the list would be silently re-picked
        // on every launch instead of kept.
        for (tag in listOf("zh", "ja", "ru", "fr", "de", "es", "it", "pt", "en", "th", "hi")) {
            val token = defaultCurrencyFor(Locale.forLanguageTag(tag))
            assertTrue("$tag defaults to $token, which is not offered", Currencies.choices.contains(token))
        }
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
