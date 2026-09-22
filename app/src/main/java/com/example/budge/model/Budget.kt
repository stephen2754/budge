package com.example.budge.model

/**
 * A spending limit for a single calendar month, keyed by [month] (first
 * millisecond of the month in the system time zone). `amount` is in integer
 * cents, matching the [Transaction] convention.
 */
data class Budget(
    val id: Long = 0,
    val month: Long = 0L,
    val amount: Long = 0L,
)
