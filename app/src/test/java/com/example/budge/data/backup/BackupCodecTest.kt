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

        // `format` is part of the contract too: it is what tells a later build's file
        // apart from one this build wrote, and a file that declares a higher format is
        // refused rather than read as an empty backup.
        assertEquals(setOf("format", "transactions", "categories", "budgets"), keys)
    }

    @Test
    fun `refuses a backup that carries no records at all`() {
        // An empty ledger exports to exactly this, but so does a document that merely
        // names the keys — an older export carries no marker to tell them apart, and a
        // restore of either wipes every table before it writes. A
        // restore wipes every table before it writes, so accepting one of these turned
        // "import this file" into "erase my ledger" and then said it succeeded. Clearing
        // the records is a separate, deliberate action in Settings.
        assertNull(decodeBackup("""{"transactions":[],"categories":[],"budgets":[]}"""))
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
    fun `ignores unknown keys alongside real records`() {
        val decoded = decodeBackup(
            """{"transactions":[{"id":1,"type":0,"amount":5,"categoryId":2,"timestamp":1,"createdAt":1,"updatedAt":1}],"schemaVersion":99,"unknown":[1,2]}""",
        )

        assertNotNull(decoded)
        assertEquals(1, decoded!!.transactions.size)
    }

    @Test
    fun `a document that only mentions the keys is not a backup`() {
        assertNull(decodeBackup("""{"transactions":[],"schemaVersion":99,"unknown":[1,2]}"""))
    }

    @Test
    fun `writes a format marker and reads it back`() {
        val json = encodeBackup(BackupData(transactions = transactions, categories = categories))

        assertTrue("the file has to say which shape it is", json.contains(""""format":1"""))

        val decoded = decodeBackup(json)
        assertNotNull(decoded)
        assertEquals(transactions.size, decoded!!.transactions.size)
    }

    @Test
    fun `refuses a backup written by a later format`() {
        // Its record keys may have moved. Reading it as this format would find the keys it
        // knows empty, and an import wipes every table before it writes — so the honest
        // answer is to refuse the file and keep the ledger.
        val later =
            """{"format":2,"transactions":[{"id":1,"type":0,"amount":5,"categoryId":2,"timestamp":1,"createdAt":1,"updatedAt":1}]}"""

        assertNull(decodeBackup(later))
    }

    @Test
    fun `accepts a file written before the marker existed`() {
        val legacy =
            """{"transactions":[{"id":1,"type":0,"amount":5,"categoryId":2,"timestamp":1,"createdAt":1,"updatedAt":1}]}"""

        val decoded = decodeBackup(legacy)

        assertNotNull(decoded)
        assertEquals(1, decoded!!.transactions.size)
    }

    @Test
    fun `brings an out-of-range direction and amount back into range`() {
        // A file the picker can reach is believed, and a restore wipes the ledger first. A
        // row whose direction is neither of the two the app defines would render as an
        // expense while the SQL totals — which count only 0 and 1 — left it out of the
        // figures entirely; an amount past the ceiling could wrap a day's subtotal into a
        // small, plausible number. Both are brought into range rather than dropped.
        val json =
            encodeBackup(BackupData(transactions = transactions, categories = categories))
                .replace("\"type\":0", "\"type\":7")
                .replace("\"amount\":1234", "\"amount\":-5")
                .replace("\"amount\":500000", "\"amount\":9999999999999")

        val decoded = decodeBackup(json)

        assertNotNull(decoded)
        val rows = decoded!!.transactions.sortedBy { it.id }
        assertEquals("an unknown direction becomes an expense", 0, rows[0].type)
        assertEquals("a negative amount is clamped to zero", 0L, rows[0].amount)
        assertEquals("an amount past the ceiling is clamped to it", 4_294_967_295L, rows[1].amount)
    }

    @Test
    fun `keeps category references intact so a restore can match them`() {
        val decoded = decodeBackup(encodeBackup(BackupData(transactions = transactions, categories = categories)))!!

        val knownIds = decoded.categories.map { it.id }.toSet()
        assertTrue(decoded.transactions.all { it.categoryId in knownIds })
    }
}
