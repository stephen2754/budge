package com.example.budge.model

/**
 * A single income or expense entry. Amounts are stored as integer cents
 * (Long) to avoid floating-point rounding errors; format them for display
 * in the UI layer.
 *
 * The category fields are denormalized snapshots joined from the category
 * table, so a transaction stays self-describing even if the category is
 * later deleted or renamed.
 */
data class Transaction(
    val id: Long = 0,
    val type: TransactionType = TransactionType.EXPENSE,
    val amount: Long = 0L,
    val categoryId: Long = 0L,
    val categoryName: String = "",
    val categoryIcon: String = "",
    val categoryColor: Long = 0L,
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
