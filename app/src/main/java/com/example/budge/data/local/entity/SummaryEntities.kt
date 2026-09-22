package com.example.budge.data.local.entity

import androidx.room.Embedded
import androidx.room.PrimaryKey

/**
 * A transaction flattened with its category display attributes.
 *
 * Produced by the join queries in [com.example.budge.data.local.dao.TransactionDao]; the
 * category fields are denormalized aliases (not foreign keys) so lists can be rendered
 * without a second query per row.
 */
data class TransactionWithCategory(
    @Embedded
    val transaction: TransactionEntity,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColor: Long,
)

/**
 * One category's aggregated spending over a date range, as returned by
 * [com.example.budge.data.local.dao.TransactionDao.getCategorySummaries].
 *
 * [type] identifies whether the grouping belongs to the expense or income side, and [total]
 * is the summed amount in the currency's smallest unit.
 */
data class CategorySummaryEntity(
    val categoryId: Long,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColor: Long,
    val total: Long,
    val type: Int,
)
