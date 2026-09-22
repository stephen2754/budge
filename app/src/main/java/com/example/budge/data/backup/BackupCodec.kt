package com.example.budge.data.backup

import com.example.budge.data.local.entity.BudgetEntity
import com.example.budge.data.local.entity.CategoryEntity
import com.example.budge.data.local.entity.TransactionEntity
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName

/**
 * The shape of a JSON backup on disk.
 *
 * Fields are nullable because Gson builds the object without running its constructor:
 * a key missing from the file leaves the field `null` rather than taking the default
 * written here.
 *
 * Each field also has an explicit [SerializedName]. Gson maps JSON by field name, and
 * R8 renames these fields in release builds, so without the annotations a release build
 * wrote `{"a":…,"b":…}` and read every backup back as empty. An import wipes the
 * database before restoring, so that turned a restore into data loss reported as
 * success.
 */
private data class BackupDocument(
    @SerializedName("transactions") val transactions: List<TransactionEntity>? = null,
    @SerializedName("categories") val categories: List<CategoryEntity>? = null,
    @SerializedName("budgets") val budgets: List<BudgetEntity>? = null,
)

/** A parsed backup. The collections are never null, whether or not the file had them. */
data class BackupData(
    val transactions: List<TransactionEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
) {
    val isEmpty: Boolean
        get() = transactions.isEmpty() && categories.isEmpty() && budgets.isEmpty()
}

/** Top-level keys a file must carry at least one of to be accepted as a backup. */
val BACKUP_KEYS = setOf("transactions", "categories", "budgets")

private val gson = Gson()

/**
 * Parses [json] into [BackupData], or returns null if the text is not a backup.
 *
 * Null is the answer that matters. Gson turns `{}` or `{"foo":1}` into an empty record
 * set without complaint, and the caller wipes the database on the strength of this
 * verdict.
 */
fun decodeBackup(json: String): BackupData? {
    val root =
        try {
            JsonParser.parseString(json)
        } catch (_: Exception) {
            return null
        }
    if (!root.isJsonObject) return null
    if (root.asJsonObject.keySet().none { it in BACKUP_KEYS }) return null

    val document =
        try {
            gson.fromJson(json, BackupDocument::class.java)
        } catch (_: Exception) {
            return null
        } ?: return null

    return BackupData(
        transactions = document.transactions.orEmpty(),
        categories = document.categories.orEmpty(),
        budgets = document.budgets.orEmpty(),
    )
}

/** Serializes a backup to the JSON written to the user's file. */
fun encodeBackup(data: BackupData): String =
    gson.toJson(BackupDocument(data.transactions, data.categories, data.budgets))
