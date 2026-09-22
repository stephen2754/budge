package com.example.budge.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.budge.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for the `budgets` table.
 *
 * A budget is keyed by `month` in **yyyyMM** form (e.g. `202608`), the same encoding
 * `StatsViewModel.yearMonthToMonthCode` produces. At most one row per month is a
 * convention, not a constraint: the table has no unique index on `month`, so whatever
 * writes budgets later has to look the row up first.
 */
@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: BudgetEntity): Long

    /** Batch insert used by backup restore. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(budgets: List<BudgetEntity>)

    @Update
    suspend fun update(budget: BudgetEntity)

    @Query("SELECT * FROM budgets WHERE month = :month")
    fun getByMonth(month: Long): Flow<BudgetEntity?>

    /** One-shot read of every budget, used by the backup export. */
    @Query("SELECT * FROM budgets ORDER BY month ASC")
    suspend fun getAllOnce(): List<BudgetEntity>

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
}
