package com.example.budge.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.budge.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for the `categories` table.
 *
 * Categories are split into built-in default rows (seeded on first launch) and user-defined
 * ones. Default categories are read-only: deletes are restricted to user-created rows so the
 * app always has a baseline set to fall back on.
 */
@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    /**
     * Deletes any category, seeded or not.
     *
     * The `isDefault` flag records where a row came from, not what the user may do
     * with it: a built-in category is an ordinary category once it exists, so it can
     * be renamed, re-typed, re-coloured and deleted. Returns the number of rows
     * removed, so a caller can tell "deleted" from "no such row".
     */
    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY sortOrder ASC, name ASC")
    fun getByType(type: Int): Flow<List<CategoryEntity>>

    /** One-shot variant used where a suspend result is needed (backup restore). */
    @Query("SELECT * FROM categories WHERE type = :type ORDER BY sortOrder ASC, name ASC LIMIT 1")
    suspend fun getFirstByType(type: Int): CategoryEntity?

    @Query("SELECT * FROM categories ORDER BY type ASC, sortOrder ASC, name ASC")
    fun getAll(): Flow<List<CategoryEntity>>

    /** One-shot variant used by the backup export. */
    @Query("SELECT * FROM categories ORDER BY type ASC, sortOrder ASC, name ASC")
    suspend fun getAllOnce(): List<CategoryEntity>

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCount(): Int
}
