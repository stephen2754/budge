package com.example.budge.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single money movement in the `transactions` table.
 *
 * [type] distinguishes expense (0) from income (1); [amount] is stored as the currency's
 * smallest unit (e.g. cents) to avoid floating-point drift. [categoryId] points at a
 * [CategoryEntity], restricted on delete so a category in use cannot be removed. The
 * [timestamp] is the user-facing transaction date, whereas [createdAt]/[updatedAt] track
 * audit history. [note] is optional free text.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["categoryId"]), Index(value = ["timestamp"])],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: Int = 0,
    val amount: Long = 0L,
    val categoryId: Long = 0L,
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
