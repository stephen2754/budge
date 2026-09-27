package com.example.budge.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.budge.data.local.entity.CategorySummaryEntity
import com.example.budge.data.local.entity.TransactionEntity
import com.example.budge.data.local.entity.TransactionWithCategory
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for the `transactions` table.
 *
 * Besides CRUD operations it provides the read queries the UI relies on: transactions in a
 * date range, a per-month income/expense rollup, and per-category spending totals.
 */
@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity): Long

    /** Batch insert used by backup restore; one statement per list, not per row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    /**
     * Returns all transactions in [startTime, endTime), joined with their category display
     * data, ordered newest-first.
     *
     * A LEFT JOIN with `COALESCE` keeps a transaction visible even when its category row is
     * gone (possible if the database was restored or edited outside the app): the joined
     * columns are non-null in [TransactionWithCategory], so a bare `c.name` would otherwise
     * fail to map. The relation is RESTRICT-guarded, so in-app deletes cannot orphan a row.
     */
    @Query(
        """
        SELECT t.*,
            COALESCE(c.name, '') AS categoryName,
            COALESCE(c.icon, '') AS categoryIcon,
            COALESCE(c.color, 0) AS categoryColor
        FROM transactions t
        LEFT JOIN categories c ON t.categoryId = c.id
        WHERE t.timestamp >= :startTime AND t.timestamp < :endTime
        ORDER BY t.timestamp DESC
        """,
    )
    fun getByDateRange(
        startTime: Long,
        endTime: Long,
    ): Flow<List<TransactionWithCategory>>

    /** One-shot read of the raw rows, used by the backup export. */
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAllOnce(): List<TransactionEntity>

    /**
     * Aggregates expense (type = 0) and income (type = 1) totals over [startTime, endTime)
     * in a single pass. A null [MonthlyTotal] means the range contains no transactions.
     */
    @Query(
        """
        SELECT
            SUM(CASE WHEN type = 0 THEN amount ELSE 0 END) AS totalExpense,
            SUM(CASE WHEN type = 1 THEN amount ELSE 0 END) AS totalIncome
        FROM transactions
        WHERE timestamp >= :startTime AND timestamp < :endTime
        """,
    )
    fun getMonthlySummary(
        startTime: Long,
        endTime: Long,
    ): Flow<MonthlyTotal?>

    /**
     * Groups amounts by category **and direction** over [startTime, endTime), returning each
     * group's total and the direction it belongs to. Results are ordered by total so the UI
     * can render a top-spenders breakdown.
     *
     * The direction has to be `t.type`, and it has to be part of the grouping. A category
     * can be re-typed after it already has transactions — [CategoryRepository.update] allows
     * it, and the category dialog offers it — while every transaction keeps the type it was
     * recorded with. Summing a category's amounts and labelling the sum from the category's
     * *current* type therefore moved an expense onto the income side the moment a category
     * was re-typed, and the per-category breakdown stopped adding up to the totals and
     * balance the same screen shows above it. Grouping by the transaction's own type keeps
     * every amount on the side it was recorded on, the side the totals query counts it on.
     *
     * A category used in both directions now appears once per direction, which is what the
     * screen wants: it splits the list into an expense breakdown and an income one.
     */
    @Query(
        """
        SELECT
            c.id AS categoryId,
            c.name AS categoryName,
            c.icon AS categoryIcon,
            c.color AS categoryColor,
            SUM(t.amount) AS total,
            t.type AS type
        FROM transactions t
        INNER JOIN categories c ON t.categoryId = c.id
        WHERE t.timestamp >= :startTime AND t.timestamp < :endTime
        GROUP BY c.id, t.type
        ORDER BY total DESC
        """,
    )
    fun getCategorySummaries(
        startTime: Long,
        endTime: Long,
    ): Flow<List<CategorySummaryEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun getCountByCategoryId(categoryId: Long): Int
}

/**
 * Summed income and expense for a date range. Individual fields are nullable because
 * SUM over an empty table yields NULL; callers must treat null as zero.
 */
data class MonthlyTotal(
    val totalExpense: Long?,
    val totalIncome: Long?,
)
