package com.example.budge.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.budge.data.local.dao.BudgetDao
import com.example.budge.data.local.dao.CategoryDao
import com.example.budge.data.local.dao.TransactionDao
import com.example.budge.data.local.entity.BudgetEntity
import com.example.budge.data.local.entity.CategoryEntity
import com.example.budge.data.local.entity.TransactionEntity

/**
 * Room database for the Budge app.
 *
 * Holds the three core tables — transactions, categories, and budgets — and exposes a
 * DAO per table. Every persisted field is a native SQLite type (`Long`, `Int`, `String`,
 * `Boolean`), so no type converters are needed. The schema is exported for migration
 * tooling.
 *
 * Versions 1, 2 and 3 are byte-identical (same identity hash), so the migrations
 * registered in `DatabaseModule` are no-ops. The first real schema change must ship
 * genuine migration SQL of its own.
 */
@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        BudgetEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class BudgeDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    abstract fun categoryDao(): CategoryDao

    abstract fun budgetDao(): BudgetDao
}
