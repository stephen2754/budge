package com.example.budge.data.backup

import com.example.budge.data.local.entity.BudgetEntity
import com.example.budge.data.local.entity.CategoryEntity
import com.example.budge.data.local.entity.TransactionEntity
import com.example.budge.model.Amount
import com.example.budge.model.TransactionType
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
 *
 * `format` says which shape the file is. It is what lets a build refuse a backup written
 * by a later one instead of reading the keys it knows, finding them empty, and wiping the
 * ledger on the strength of that reading.
 */
private data class BackupDocument(
    @SerializedName("format") val format: Int? = null,
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

/**
 * The format this build writes, and the highest it can read.
 *
 * Files written before the marker existed carry none and are read as this format, which is
 * what they are. A file that declares a higher one is refused rather than read: the keys it
 * moved to would come back as empty lists, and an import wipes every table before it
 * writes, so reading it as "this backup holds nothing here" silently loses whatever the
 * newer build recorded. Refusing it keeps the ledger and says so.
 */
private const val BACKUP_FORMAT = 1

private val gson = Gson()

/**
 * Parses [json] into [BackupData], or returns null if the text is not a backup.
 *
 * Null is the answer that matters. Gson turns `{}` or `{"foo":1}` into an empty record
 * set without complaint, and the caller wipes the database on the strength of this
 * verdict. A file that names the right keys but carries no records at all is refused for
 * the same reason: restoring it would erase everything the user has and then report
 * "Import successful". `{"transactions":[],"categories":[],"budgets":[],"schemaVersion":99}`
 * used to be exactly that file.
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

    // The tree parsed above is handed to Gson rather than the string: parsing the same
    // document twice cost a second tokenisation and kept a whole JsonElement tree alive
    // across it, which on a large ledger is the difference between one copy of the file in
    // memory and three.
    val document =
        try {
            gson.fromJson(root, BackupDocument::class.java)
        } catch (_: Exception) {
            return null
        } ?: return null

    if (document.format?.let { it > BACKUP_FORMAT } == true) return null

    val data =
        BackupData(
            transactions = document.transactions.orEmpty(),
            categories = document.categories.orEmpty(),
            budgets = document.budgets.orEmpty(),
        )

    return data.takeUnless { it.isEmpty }?.sanitized()
}

/**
 * The records a file may hold, with their values brought back into the ranges the app
 * defines.
 *
 * A backup can come from anywhere the file picker can reach, and restoring it wipes the
 * ledger first, so what it says is believed. Two values have to be checked or the ledger
 * ends up disagreeing with itself:
 *
 * - **Direction.** The app defines two, expense (0) and income (1). A row stored with
 *   anything else means an expense — `TransactionType.fromValue`'s rule, which this uses
 *   SQL totals count only 0 and 1, so the row appears in the list and is missing from the
 *   figure above it. Coercing the stored value makes the two agree.
 * - **Amount.** The entry form refuses anything outside 1..[Amount.MAX_CENTS], and the
 *   repository clamps what SQL sums to that range; a file could put an out-of-range value
 *   straight into a row, and a day's subtotal — which adds rows up in a Long and is not
 *   clamped — could then wrap into a small, plausible-looking number.
 *
 * Values are brought into range rather than dropped. A record with a strange amount is
 * still a record the user meant to keep, and discarding rows silently is the failure this
 * whole file is written to avoid.
 */
fun BackupData.sanitized(): BackupData =
    copy(
        transactions =
            transactions.map { transaction ->
                transaction.copy(
                    // Through the app's own mapping, not a range clamp: an unknown
                    // direction has one meaning here — the same one the list and the totals
                    // already give it — and a second rule would be free to drift from it.
                    type = TransactionType.fromValue(transaction.type).value,
                    amount = transaction.amount.coerceIn(0L, Amount.MAX_CENTS),
                )
            },
    )

/** Serializes a backup to the JSON written to the user's file. */
fun encodeBackup(data: BackupData): String =
    gson.toJson(BackupDocument(BACKUP_FORMAT, data.transactions, data.categories, data.budgets))
