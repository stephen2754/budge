package com.example.budge.data.repository

import androidx.room.withTransaction
import com.example.budge.data.backup.BackupData
import com.example.budge.data.local.BudgeDatabase
import com.example.budge.model.TransactionType
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a restore actually did, so the screen can report it instead of claiming
 * an unqualified success.
 */
data class ImportReport(
    val transactions: Int,
    val categories: Int,
    val budgets: Int,
    /** Transactions whose category was missing from the file and were re-homed. */
    val reassigned: Int,
)

/**
 * Reads and writes the whole database as a single backup document.
 *
 * The database work lives here rather than in the settings view model so the
 * destructive path has one owner, one transaction, and can be reasoned about (and
 * tested) without a UI.
 */
@Singleton
class BackupRepository
    @Inject
    constructor(
        private val database: BudgeDatabase,
        private val categoryRepository: CategoryRepository,
    ) {
        /** Snapshot of everything a backup restores. */
        suspend fun export(): BackupData =
            BackupData(
                transactions = database.transactionDao().getAllOnce(),
                categories = database.categoryDao().getAllOnce(),
                budgets = database.budgetDao().getAllOnce(),
            )

        /**
         * Replaces the whole database with [data] in one transaction.
         *
         * Categories and budgets are restored with their original ids so the
         * transactions keep pointing at the right rows. A transaction whose
         * `categoryId` is absent from the file is **re-homed** to a category of the
         * same type rather than dropped: silently discarding records the user
         * believes they restored is the worst possible failure mode here, and it is
         * exactly what used to happen to a file carrying transactions but no
         * categories.
         */
        suspend fun import(
            data: BackupData,
            locale: Locale,
        ): ImportReport =
            database.withTransaction {
                // Explicit deletes rather than RoomDatabase.clearAllTables(): that
                // method opens *and commits* a transaction of its own, so calling it
                // here nested one transaction inside another and made the atomicity
                // of a restore depend on how SQLite stacks them. The order respects
                // the transactions -> categories RESTRICT key.
                database.transactionDao().deleteAll()
                database.budgetDao().deleteAll()
                database.categoryDao().deleteAll()

                if (data.categories.isNotEmpty()) {
                    database.categoryDao().insertAll(data.categories)
                }
                if (data.budgets.isNotEmpty()) {
                    database.budgetDao().insertAll(data.budgets)
                }

                val knownCategoryIds = data.categories.mapTo(mutableSetOf()) { it.id }
                val fallbackIds = mutableMapOf<TransactionType, Long>()
                var reassigned = 0

                val restored =
                    data.transactions.map { transaction ->
                        if (transaction.categoryId != 0L && transaction.categoryId in knownCategoryIds) {
                            transaction
                        } else {
                            val type = TransactionType.fromValue(transaction.type)
                            val fallbackId =
                                fallbackIds[type]
                                    ?: categoryRepository
                                        .ensureCategoryOfType(type, locale)
                                        .also { fallbackIds[type] = it }
                            reassigned++
                            transaction.copy(categoryId = fallbackId)
                        }
                    }

                if (restored.isNotEmpty()) {
                    database.transactionDao().insertAll(restored)
                }

                ImportReport(
                    transactions = restored.size,
                    categories = data.categories.size,
                    budgets = data.budgets.size,
                    reassigned = reassigned,
                )
            }

        /**
         * Deletes every record and re-seeds the built-in categories in [locale], so
         * the app is immediately usable again. Preferences are deliberately left
         * alone: "clear all records" is about the ledger, not the settings.
         */
        suspend fun clearAll(locale: Locale) {
            database.withTransaction {
                database.transactionDao().deleteAll()
                database.budgetDao().deleteAll()
                database.categoryDao().deleteAll()
                categoryRepository.insertDefaultCategories(locale)
            }
        }
    }
