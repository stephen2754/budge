package com.example.budge.data.backup

import com.example.budge.data.local.entity.BudgetEntity
import com.example.budge.data.local.entity.CategoryEntity
import com.example.budge.data.local.entity.TransactionEntity
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the backup wire format.
 *
 * The single most important behaviour here is that [decodeBackup] refuses
 * anything that is not a backup. Gson will happily deserialize `{}` into an empty
 * record set, and an import that accepted one wiped the database and restored
 * nothing while reporting success.
 */
class BackupCodecTest {
    private val categories =
        listOf(
            CategoryEntity(id = 1, name = "Dining", icon = "restaurant", color = 0xFFD32F2F, type = 0, isDefault = true, sortOrder = 1),
            CategoryEntity(id = 9, name = "Salary", icon = "payments", color = 0xFFAFB42B, type = 1, isDefault = true, sortOrder = 1),
        )

    private val transactions =
        listOf(
            TransactionEntity(id = 5, type = 0, amount = 1234, categoryId = 1, note = "lunch", timestamp = 1_700_000_000_000, createdAt = 1, updatedAt = 2),
            TransactionEntity(id = 6, type = 1, amount = 500_000, categoryId = 9, note = null, timestamp = 1_700_000_100_000, createdAt = 3, updatedAt = 4),
        )

    private val budgets = listOf(BudgetEntity(id = 1, month = 202608, amount = 200_000))

    @Test
    fun `round trips every collection and field`() {
        val original = BackupData(transactions = transactions, categories = categories, budgets = budgets)

        val decoded = decodeBackup(encodeBackup(original))

        assertNotNull(decoded)
        assertEquals(transactions, decoded!!.transactions)
        assertEquals(categories, decoded.categories)
        assertEquals(budgets, decoded.budgets)
    }

    /**
     * The on-disk keys are a compatibility contract: every backup a user has
     * already exported uses these names. [com.google.gson.annotations.SerializedName]
     * pins them against R8's field renaming in release builds — without it, R8
     * turned the fields into `a`/`b` and a release build could not read its own
     * export, or any earlier backup.
     */
    @Test
    fun `writes the documented top-level keys`() {
        val json = encodeBackup(BackupData(transactions = transactions, categories = categories, budgets = budgets))
        val keys = JsonParser.parseString(json).asJsonObject.keySet()

        assertEquals(setOf("transactions", "categories", "budgets"), keys)
    }

    @Test
    fun `accepts a backup whose collections are all empty`() {
        // A user who clears their records and exports is a legitimate backup: it
        // restores to "nothing", which is what the file says.
        val decoded = decodeBackup("""{"transactions":[],"categories":[],"budgets":[]}""")

        assertNotNull(decoded)
        assertTrue(decoded!!.isEmpty)
    }

    @Test
    fun `accepts a backup that only carries some of the keys`() {
        val decoded = decodeBackup("""{"transactions":[{"id":1,"type":0,"amount":5,"categoryId":2,"timestamp":1,"createdAt":1,"updatedAt":1}]}""")

        assertNotNull(decoded)
        assertEquals(1, decoded!!.transactions.size)
        // Absent keys must come back as empty lists, not null: Gson bypasses the
        // constructor and would otherwise leave the field null.
        assertEquals(emptyList<CategoryEntity>(), decoded.categories)
        assertEquals(emptyList<BudgetEntity>(), decoded.budgets)
    }

    @Test
    fun `rejects documents that are not backups`() {
        // Each of these used to validate and then wipe the database.
        assertNull("an empty object is not a backup", decodeBackup("{}"))
        assertNull("unrelated JSON is not a backup", decodeBackup("""{"unrelated":1}"""))
        assertNull("an array is not a backup", decodeBackup("[]"))
        assertNull("a scalar is not a backup", decodeBackup("42"))
        assertNull("malformed JSON is not a backup", decodeBackup("{not json"))
        assertNull("nothing is not a backup", decodeBackup(""))
    }

    @Test
    fun `ignores unknown keys alongside a real one`() {
        val decoded = decodeBackup("""{"transactions":[],"schemaVersion":99,"unknown":[1,2]}""")

        assertNotNull(decoded)
        assertTrue(decoded!!.isEmpty)
    }

    @Test
    fun `keeps category references intact so a restore can match them`() {
        val decoded = decodeBackup(encodeBackup(BackupData(transactions = transactions, categories = categories)))!!

        val knownIds = decoded.categories.map { it.id }.toSet()
        assertTrue(decoded.transactions.all { it.categoryId in knownIds })
    }
}
