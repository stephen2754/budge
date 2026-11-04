package com.example.budge.model

/**
 * A spending limit for a single calendar month, keyed by [month].
 *
 * [month] is a **yyyyMM** code (for example `202608`), not a timestamp — the same
 * representation `BudgetEntity` stores and `BudgetDao` queries by. `amount` is in integer
 * cents, matching the [Transaction] convention.
 */
data class Budget(
    val id: Long = 0,
    val month: Long = 0L,
    val amount: Long = 0L,
)
