package com.example.budge.model

/**
 * Aggregated totals per category for a date range. `total` is in integer
 * cents; the category fields are denormalized copies for display.
 */
data class CategorySummary(
    val categoryId: Long,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColor: Long,
    val total: Long,
    val type: TransactionType,
)

/**
 * Income and expense totals for one period, in integer cents.
 *
 * The period itself is not carried here: the screen already knows which one it asked
 * for, and a label stored alongside the numbers can only disagree with it.
 */
data class MonthlySummary(
    val totalExpense: Long,
    val totalIncome: Long,
)
