package com.example.budge.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Tests for the built-in category tables.
 *
 * Re-localization matches an existing built-in to its translation by
 * `(type, sortOrder)`, so the *shape* of every language's table has to be
 * identical. If one language ever drifts — a missing name, a swapped slot — the
 * pass would rename the wrong category, and a table with a different length would
 * simply crash on indexing. These tests pin that contract.
 */
class DefaultCategoriesTest {
    private val languages =
        listOf("en", "zh", "fr", "de", "es", "ru", "ja", "it", "pt")

    /** The (type, sortOrder) key of every slot, which must be language-independent. */
    private fun keysOf(categories: List<DefaultCategory>) = categories.map { it.type to it.sortOrder }

    @Test
    fun `every shipped language has a full table`() {
        for (language in languages) {
            val categories = defaultCategoriesFor(Locale.forLanguageTag(language))
            assertEquals("$language should have 12 built-ins", 12, categories.size)
            assertTrue("$language has a blank name", categories.none { it.name.isBlank() })
        }
    }

    @Test
    fun `every language uses the same slots in the same order`() {
        val reference = keysOf(defaultCategoriesFor(Locale.ENGLISH))

        for (language in languages) {
            assertEquals(
                "$language must use the same (type, sortOrder) slots as English",
                reference,
                keysOf(defaultCategoriesFor(Locale.forLanguageTag(language))),
            )
        }

        // The keys themselves must be unique, or two built-ins could claim the same
        // slot and overwrite each other's name during localization.
        assertEquals(reference.size, reference.toSet().size)
    }

    @Test
    fun `names are unique within a language`() {
        for (language in languages) {
            val names = defaultCategoriesFor(Locale.forLanguageTag(language)).map { it.name }
            assertEquals("$language has duplicate category names", names.size, names.toSet().size)
        }
    }

    @Test
    fun `the names are actually translated, not left in English`() {
        val english = defaultCategoriesFor(Locale.ENGLISH).map { it.name }

        for (language in listOf("zh", "fr", "de", "es", "ru", "ja", "it", "pt")) {
            val names = defaultCategoriesFor(Locale.forLanguageTag(language)).map { it.name }
            assertTrue("$language was left in English", names != english)
        }
    }

    @Test
    fun `the icon, colour and type of a slot do not depend on the language`() {
        val reference = defaultCategoriesFor(Locale.ENGLISH)

        for (language in languages) {
            val categories = defaultCategoriesFor(Locale.forLanguageTag(language))
            categories.forEachIndexed { index, category ->
                assertEquals(reference[index].icon, category.icon)
                assertEquals(reference[index].color, category.color)
                assertEquals(reference[index].type, category.type)
                assertEquals(reference[index].sortOrder, category.sortOrder)
            }
        }
    }

    @Test
    fun `an unknown language falls back to the English table`() {
        val unknown = defaultCategoriesFor(Locale.forLanguageTag("kl"))
        val english = defaultCategoriesFor(Locale.ENGLISH)

        assertEquals(english.map { it.name }, unknown.map { it.name })
        // It must still be a usable set, not an empty one.
        assertEquals(12, unknown.size)
    }

    @Test
    fun `expense slots precede income slots`() {
        val categories = defaultCategoriesFor(Locale.ENGLISH)
        assertEquals(8, categories.count { it.type == 0 })
        assertEquals(4, categories.count { it.type == 1 })
    }
}
