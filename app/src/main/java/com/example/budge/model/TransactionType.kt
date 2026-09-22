package com.example.budge.model

/** Whether a transaction or category is money coming in or going out. */
enum class TransactionType(
    val value: Int,
) {
    EXPENSE(0),
    INCOME(1),
    ;

    companion object {
        /**
         * Maps a stored int back to the enum, falling back to [EXPENSE] for
         * unknown values. The int is the persisted representation in Room.
         */
        fun fromValue(value: Int): TransactionType = entries.firstOrNull { it.value == value } ?: EXPENSE
    }
}
