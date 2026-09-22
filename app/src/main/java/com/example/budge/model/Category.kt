package com.example.budge.model

/**
 * A spending/income bucket that transactions are grouped into.
 *
 * `isDefault` records that the row was seeded on first launch. It is **provenance,
 * not a permission**: a seeded category is edited and deleted exactly like one the
 * user created, and nothing re-localizes it behind the user's back.
 */
data class Category(
    val id: Long = 0,
    val name: String = "",
    val icon: String = "",
    val color: Long = 0L,
    val type: TransactionType = TransactionType.EXPENSE,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
)
