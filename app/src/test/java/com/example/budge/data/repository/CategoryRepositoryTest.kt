package com.example.budge.data.repository

import com.example.budge.data.local.dao.CategoryDao
import com.example.budge.data.local.entity.CategoryEntity
import com.example.budge.model.Category
import com.example.budge.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Tests for the category seeding and localization rules, exercised against an
 * in-memory [CategoryDao].
 *
 * These are the rules that decide which names a user sees on every launch, and the
 * ones that used to silently revert a rename.
 */
class CategoryRepositoryTest {
    private val dao = FakeCategoryDao()
    private val repository = CategoryRepository(dao)

    @Test
    fun `seeds the built-ins once, in the language asked for`() = runBlocking {
        repository.seedDefaultsIfEmpty(Locale.SIMPLIFIED_CHINESE)

        assertEquals(12, dao.getCount())
        assertTrue(dao.getAllOnce().all { it.isDefault })
        assertTrue(dao.getAllOnce().any { it.name == "餐饮" })

        // Second call must be a no-op: the table is no longer empty.
        repository.seedDefaultsIfEmpty(Locale.ENGLISH)

        assertEquals(12, dao.getCount())
        assertTrue("existing names must not be replaced by a re-seed", dao.getAllOnce().any { it.name == "餐饮" })
    }

    @Test
    fun `seeds English names for an English locale`() = runBlocking {
        repository.seedDefaultsIfEmpty(Locale.ENGLISH)

        assertTrue(dao.getAllOnce().any { it.name == "Dining" })
        assertTrue(dao.getAllOnce().any { it.name == "Salary" })
    }

    @Test
    fun `re-homes a transaction onto a default category of the same type`() = runBlocking {
        // This is the backup-restore path for a transaction whose category is not
        // in the file: it must get a usable category rather than be dropped.
        val expenseId = repository.ensureCategoryOfType(TransactionType.EXPENSE, Locale.SIMPLIFIED_CHINESE)
        val created = dao.getById(expenseId)

        assertNotNull(created)
        assertEquals(0, created!!.type)
        assertTrue(created.isDefault)

        // A second call for the same type reuses the row.
        assertEquals(expenseId, repository.ensureCategoryOfType(TransactionType.EXPENSE, Locale.SIMPLIFIED_CHINESE))
        assertEquals(1, dao.getCount())

        val incomeId = repository.ensureCategoryOfType(TransactionType.INCOME, Locale.SIMPLIFIED_CHINESE)
        assertEquals(1, dao.getById(incomeId)!!.type)
        assertEquals(2, dao.getCount())
    }

    @Test
    fun `a seeded category is an ordinary category from then on`() = runBlocking {
        // Seeding names the built-ins in one language; after that they are the user's,
        // so nothing may rename, re-type or protect them.
        repository.seedDefaultsIfEmpty(Locale.ENGLISH)
        val seeded = repository.getById(dao.getAllOnce().first { it.name == "Dining" }.id)!!

        repository.update(seeded.copy(name = "Eating out", type = TransactionType.INCOME, color = 0xFF123456))

        val edited = repository.getById(seeded.id)!!
        assertEquals("Eating out", edited.name)
        assertEquals(TransactionType.INCOME, edited.type)
        assertEquals(0xFF123456, edited.color)
        assertTrue("the row still records where it came from", dao.getById(seeded.id)!!.isDefault)
    }

    @Test
    fun `re-seeds the defaults for the clear-all path`() = runBlocking {
        repository.seedDefaultsIfEmpty(Locale.ENGLISH)
        dao.deleteAll()

        repository.insertDefaultCategories(Locale.SIMPLIFIED_CHINESE)

        assertEquals(12, dao.getCount())
        assertTrue(dao.getAllOnce().any { it.name == "餐饮" })
    }

    @Test
    fun `a seeded category can be deleted like any other`() = runBlocking {
        repository.seedDefaultsIfEmpty(Locale.ENGLISH)
        val seeded = dao.getAllOnce().first { it.isDefault }

        assertEquals(1, repository.deleteById(seeded.id))
        assertEquals(11, dao.getCount())
        assertNull(dao.getById(seeded.id))
    }
}

/**
 * Minimal in-memory [CategoryDao]. Only the behaviour the repository depends on is
 * modelled.
 */
private class FakeCategoryDao : CategoryDao {
    private val rows = mutableListOf<CategoryEntity>()
    private var nextId = 1L

    override suspend fun insert(category: CategoryEntity): Long {
        val id = if (category.id == 0L) nextId++ else category.id
        rows.removeAll { it.id == id }
        rows += category.copy(id = id)
        return id
    }

    override suspend fun insertAll(categories: List<CategoryEntity>) {
        categories.forEach { insert(it) }
    }

    override suspend fun update(category: CategoryEntity) {
        val index = rows.indexOfFirst { it.id == category.id }
        if (index >= 0) rows[index] = category
    }

    override suspend fun delete(category: CategoryEntity) {
        rows.removeAll { it.id == category.id }
    }

    // Mirrors the DAO, which no longer guards on isDefault: a seeded category deletes
    // exactly like one the user created.
    override suspend fun deleteById(id: Long): Int {
        val before = rows.size
        rows.removeAll { it.id == id }
        return before - rows.size
    }

    override suspend fun getById(id: Long): CategoryEntity? = rows.firstOrNull { it.id == id }

    override fun getByType(type: Int): Flow<List<CategoryEntity>> = flowOf(rows.filter { it.type == type })

    override suspend fun getFirstByType(type: Int): CategoryEntity? =
        rows.filter { it.type == type }.minByOrNull { it.sortOrder }

    override fun getAll(): Flow<List<CategoryEntity>> = flowOf(rows.toList())

    override suspend fun getAllOnce(): List<CategoryEntity> = rows.toList()

    override suspend fun deleteAll() {
        rows.clear()
    }

    override suspend fun getCount(): Int = rows.size
}
