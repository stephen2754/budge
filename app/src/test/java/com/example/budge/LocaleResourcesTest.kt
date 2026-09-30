package com.example.budge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks that the nine translation files stay in step.
 *
 * `values/strings.xml` is the contract: every other locale has to define the same keys,
 * with the same format specifiers in the same order. A missing key silently shows English,
 * and a specifier that does not match its translation throws
 * `IllegalFormatConversionException` at the moment the string is formatted — inside a
 * screen the user is looking at. Neither is caught by compiling the app.
 *
 * The one documented exception is the in-app release-note summaries, which are written in
 * English and Chinese only; every other locale falls back to English on purpose.
 */
class LocaleResourcesTest {
    private fun resDir(): File =
        listOf(File("src/main/res"), File("app/src/main/res"))
            .firstOrNull { it.isDirectory }
            ?: error("could not find res/ from ${File(".").absolutePath}")

    private fun stringsFile(locale: String) = File(resDir(), "$locale/strings.xml")

    private fun keysIn(locale: String): Set<String> =
        Regex("""<(?:string|plurals) name="([^"]+)"""")
            .findAll(stringsFile(locale).readText())
            .map { it.groupValues[1] }
            .toSet()

    /**
     * The `%1$s`-style specifiers each key uses, so translations can be compared.
     *
     * A plain string keeps them in order, because their order is positional. A plural is
     * compared as the distinct set of placeholders across its items: the number of items
     * follows the language's own rules, so only the placeholders they use can be required
     * to agree.
     */
    private fun specifiersIn(locale: String): Map<String, List<String>> {
        val text = stringsFile(locale).readText()
        val specifier = Regex("""%\d+\$[sd]""")
        val result = mutableMapOf<String, List<String>>()

        Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(text)
            .forEach { match ->
                result[match.groupValues[1]] = specifier.findAll(match.groupValues[2]).map { it.value }.toList()
            }

        Regex("""<plurals name="([^"]+)">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(text)
            .forEach { match ->
                result[match.groupValues[1]] =
                    specifier.findAll(match.groupValues[2]).map { it.value }.distinct().sorted().toList()
            }

        return result
    }

    @Test
    fun `every locale defines the same keys`() {
        val default = keysIn("values")
        val releaseNotes = default.filter { it.startsWith("release_notes_") }.toSet()

        assertTrue("the release-note summaries should exist", releaseNotes.isNotEmpty())
        for (locale in LOCALES.drop(1)) {
            val translated = keysIn(locale)
            assertEquals(
                "$locale and the default differ outside the release-note summaries",
                default - releaseNotes,
                translated - releaseNotes,
            )
        }
    }

    @Test
    fun `the release-note summaries exist in English and Chinese only`() {
        val releaseNotes = keysIn("values").filter { it.startsWith("release_notes_") }.toSet()

        assertEquals(releaseNotes, keysIn("values-zh").filter { it.startsWith("release_notes_") }.toSet())
        for (locale in LOCALES.drop(2)) {
            assertTrue(
                "$locale must not carry release-note summaries: they fall back to English",
                keysIn(locale).none { it.startsWith("release_notes_") },
            )
        }
    }

    @Test
    fun `format specifiers match wherever a locale defines a key`() {
        val default = specifiersIn("values")

        for (locale in LOCALES.drop(1)) {
            specifiersIn(locale).forEach { (key, specs) ->
                assertEquals("$key in $locale has different format arguments", default[key], specs)
            }
        }
    }

    @Test
    fun `every plural carries the forms its language needs`() {
        for (locale in LOCALES) {
            val text = stringsFile(locale).readText()
            val blocks = Regex("""<plurals name="([^"]+)">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL).findAll(text)
            assertTrue("$locale defines no plurals", blocks.any())
            blocks.forEach { block ->
                val quantities = Regex("""quantity="([^"]+)"""").findAll(block.groupValues[2]).map { it.groupValues[1] }.toSet()
                assertTrue(
                    "${block.groupValues[1]} in $locale has no 'other' form, which every locale needs",
                    "other" in quantities,
                )
            }
        }
    }

    @Test
    fun `russian carries the three forms its plural rules ask for`() {
        val quantities =
            Regex("""quantity="([^"]+)"""")
                .findAll(stringsFile("values-ru").readText())
                .map { it.groupValues[1] }
                .toSet()

        // Russian needs one (1, 21), few (2-4) and many (0, 5-20): a single form produced
        // "1 операций".
        assertTrue("russian is missing plural forms: $quantities", quantities.containsAll(setOf("one", "few", "many")))
    }

    private companion object {
        val LOCALES =
            listOf(
                "values",
                "values-zh",
                "values-fr",
                "values-de",
                "values-es",
                "values-ru",
                "values-ja",
                "values-it",
                "values-pt",
            )
    }
}
