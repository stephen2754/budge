package com.example.budge.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A transaction category in the `categories` table.
 *
 * Categories exist either as seeded defaults ([isDefault] = true) or as user-created rows.
 * [type] mirrors the transaction type convention (expense = 0, income = 1); [icon] is a
 * material icon name and [color] its ARGB value, both used for display. [sortOrder] controls
 * display ordering within each type.
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String = "",
    val icon: String = "",
    val color: Long = 0L,
    val type: Int = 0,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
)
