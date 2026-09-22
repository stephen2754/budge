package com.example.budge.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A monthly spending limit in the `budgets` table.
 *
 * [month] is a month code in **yyyyMM** form (e.g. `202608`), which is what
 * `StatsViewModel.yearMonthToMonthCode` looks up — not an epoch timestamp.
 * [amount] is the budgeted figure in the currency's smallest unit, consistent
 * with how transaction amounts are stored.
 *
 * The table has no unique index on `month`, so "one budget per month" is a
 * convention the writers keep, not something the schema enforces.
 */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val month: Long = 0L,
    val amount: Long = 0L,
)
