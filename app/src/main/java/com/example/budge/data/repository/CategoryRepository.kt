package com.example.budge.data.repository

import com.example.budge.data.local.dao.CategoryDao
import com.example.budge.data.local.entity.CategoryEntity
import com.example.budge.model.Category
import com.example.budge.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for category data. Wraps [CategoryDao], maps Room
 * entities to the domain [Category] model, and exposes reactive [Flow]s.
 * Also owns seeding and localization of the built-in default categories.
 */
@Singleton
class CategoryRepository
    @Inject
    constructor(
        private val categoryDao: CategoryDao,
    ) {
        fun getAll(): Flow<List<Category>> =
            categoryDao.getAll().map { entities ->
                entities.map { it.toDomain() }
            }

        fun getByType(type: TransactionType): Flow<List<Category>> =
            categoryDao.getByType(type.value).map { entities ->
                entities.map { it.toDomain() }
            }

        suspend fun getById(id: Long): Category? = categoryDao.getById(id)?.toDomain()

        suspend fun insert(category: Category): Long = categoryDao.insert(category.toEntity())

        suspend fun update(category: Category) = categoryDao.update(category.toEntity())

        suspend fun deleteById(id: Long): Int = categoryDao.deleteById(id)

        suspend fun getCount(): Int = categoryDao.getCount()

        /**
         * Inserts the locale's built-in categories, but only when the table is empty.
         *
         * Called once, on first launch, with the *device* locale — the names the user
         * sees then are the ones they keep. From that point the rows are ordinary
         * categories: editable, deletable, and never renamed behind the user's back.
         *
         * Runs on the application scope, so the UI may briefly observe an empty list;
         * the screens collect a Room `Flow` and fill in as soon as the rows land.
         */
        suspend fun seedDefaultsIfEmpty(locale: Locale = Locale.getDefault()) {
            if (categoryDao.getCount() > 0) return
            categoryDao.insertAll(defaultCategoriesFor(locale).map { it.toEntity() })
        }

        /**
         * Inserts the locale's built-in categories unconditionally. Used by the
         * "clear all records" path, which has already emptied the table.
         */
        suspend fun insertDefaultCategories(locale: Locale) {
            categoryDao.insertAll(defaultCategoriesFor(locale).map { it.toEntity() })
        }

        /**
         * Id of a usable category of [type], creating the locale's built-in
         * category of that type when the table has none at all.
         *
         * Backup restore uses this so a transaction whose `categoryId` is not in
         * the file is re-homed instead of being dropped on the floor.
         */
        suspend fun ensureCategoryOfType(
            type: TransactionType,
            locale: Locale,
        ): Long {
            categoryDao.getFirstByType(type.value)?.let { return it.id }
            val template = defaultCategoriesFor(locale).first { it.type == type.value }
            return categoryDao.insert(template.toEntity())
        }

        // Seeded rows are flagged as such for provenance; the flag gates nothing.
        private fun DefaultCategory.toEntity() =
            CategoryEntity(
                name = name,
                icon = icon,
                color = color,
                type = type,
                isDefault = true,
                sortOrder = sortOrder,
            )

        private fun CategoryEntity.toDomain() =
            Category(
                id = id,
                name = name,
                icon = icon,
                color = color,
                type = TransactionType.fromValue(type),
                isDefault = isDefault,
                sortOrder = sortOrder,
            )

        private fun Category.toEntity() =
            CategoryEntity(
                id = id,
                name = name,
                icon = icon,
                color = color,
                type = type.value,
                isDefault = isDefault,
                sortOrder = sortOrder,
            )
    }
