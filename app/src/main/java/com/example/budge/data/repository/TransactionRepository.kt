package com.example.budge.data.repository

import com.example.budge.data.local.dao.TransactionDao
import com.example.budge.data.local.entity.TransactionEntity
import com.example.budge.model.Amount
import com.example.budge.model.CategorySummary
import com.example.budge.model.MonthlySummary
import com.example.budge.model.Transaction
import com.example.budge.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for transaction data. Wraps [TransactionDao],
 * maps Room entities to the domain [Transaction] model, and exposes
 * reactive [Flow]s so the UI stays in sync with database changes.
 */
@Singleton
class TransactionRepository
    @Inject
    constructor(
        private val transactionDao: TransactionDao,
    ) {
        fun getByDateRange(
            startTime: Long,
            endTime: Long,
        ): Flow<List<Transaction>> =
            transactionDao.getByDateRange(startTime, endTime).map { entities ->
                entities.map { it.toDomain() }
            }

        /**
         * Emits a [MonthlySummary] for [yearMonth]. The month is converted to
         * an exclusive millisecond range (`[start, end)`) and totals default
         * to zero when no rows exist in the range.
         */
        fun getMonthlySummary(yearMonth: YearMonth): Flow<MonthlySummary> {
            val (startTime, endTime) = yearMonthToRange(yearMonth)
            return getSummary(startTime, endTime)
        }

        /**
         * Income and expense totals for an arbitrary range.
         *
         * One SQL aggregate backs every period the statistics screen offers, so the
         * year and day views do not load a range's transactions just to add them up.
         */
        fun getSummary(
            startTime: Long,
            endTime: Long,
        ): Flow<MonthlySummary> =
            transactionDao.getMonthlySummary(startTime, endTime).map { total ->
                MonthlySummary(
                    // SQLite sums in 64 bits; these are amounts, so they are clamped
                    // into the 32-bit range the rest of the app works in.
                    totalExpense = Amount.coerceToAmount(total?.totalExpense ?: 0L),
                    totalIncome = Amount.coerceToAmount(total?.totalIncome ?: 0L),
                )
            }

        fun getCategorySummaries(yearMonth: YearMonth): Flow<List<CategorySummary>> {
            val (startTime, endTime) = yearMonthToRange(yearMonth)
            return getCategorySummariesByDateRange(startTime, endTime)
        }

        /**
         * Per-category totals for an arbitrary time range, so the same query
         * can back both monthly and custom-range reports.
         */
        fun getCategorySummariesByDateRange(
            startTime: Long,
            endTime: Long,
        ): Flow<List<CategorySummary>> =
            transactionDao.getCategorySummaries(startTime, endTime).map { entities ->
                entities.map { entity ->
                    CategorySummary(
                        categoryId = entity.categoryId,
                        categoryName = entity.categoryName,
                        categoryIcon = entity.categoryIcon,
                        categoryColor = entity.categoryColor,
                        total = Amount.coerceToAmount(entity.total),
                        type = TransactionType.fromValue(entity.type),
                    )
                }
            }

        suspend fun getById(id: Long): Transaction? = transactionDao.getById(id)?.toDomain()

        suspend fun insert(transaction: Transaction): Long = transactionDao.insert(transaction.toEntity())

        suspend fun update(transaction: Transaction) = transactionDao.update(transaction.toEntity())

        suspend fun deleteById(id: Long) = transactionDao.deleteById(id)

        /** Used by the UI to block deleting a category that still has transactions. */
        suspend fun getTransactionCountForCategory(categoryId: Long): Int = transactionDao.getCountByCategoryId(categoryId)

        companion object {
            /**
             * Converts a month to an exclusive epoch-millis range covering the
             * whole month in the system time zone. Start is the first instant
             * of the month; end is the first instant of the following month.
             */
            fun yearMonthToRange(yearMonth: YearMonth): Pair<Long, Long> {
                val zone = ZoneId.systemDefault()
                val startTime =
                    yearMonth
                        .atDay(1)
                        .atStartOfDay(zone)
                        .toInstant()
                        .toEpochMilli()
                val endTime =
                    yearMonth
                        .plusMonths(1)
                        .atDay(1)
                        .atStartOfDay(zone)
                        .toInstant()
                        .toEpochMilli()
                return Pair(startTime, endTime)
            }
        }

        /** Combines the joined category snapshot into a full domain [Transaction]. */
        private fun com.example.budge.data.local.entity.TransactionWithCategory.toDomain() =
            Transaction(
                id = transaction.id,
                type = TransactionType.fromValue(transaction.type),
                amount = transaction.amount,
                categoryId = transaction.categoryId,
                categoryName = categoryName,
                categoryIcon = categoryIcon,
                categoryColor = categoryColor,
                note = transaction.note,
                timestamp = transaction.timestamp,
                createdAt = transaction.createdAt,
                updatedAt = transaction.updatedAt,
            )

        /** Maps a bare entity (used when no category join is needed) to the domain model. */
        private fun TransactionEntity.toDomain() =
            Transaction(
                id = id,
                type = TransactionType.fromValue(type),
                amount = amount,
                categoryId = categoryId,
                note = note,
                timestamp = timestamp,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )

        /** Converts the domain model back to the Room entity (int transaction type). */
        private fun Transaction.toEntity() =
            TransactionEntity(
                id = id,
                type = type.value,
                amount = amount,
                categoryId = categoryId,
                note = note,
                timestamp = timestamp,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
    }
