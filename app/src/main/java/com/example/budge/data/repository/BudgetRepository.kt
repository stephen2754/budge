package com.example.budge.data.repository

import com.example.budge.data.local.dao.BudgetDao
import com.example.budge.data.local.entity.BudgetEntity
import com.example.budge.model.Budget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for monthly budgets.
 *
 * Read-only: the app shows a budget when one exists, and there is still no screen that
 * writes one. When that screen arrives, the writer belongs here — it has to look the
 * month up first, because the table has no unique index to lean on.
 */
@Singleton
class BudgetRepository
    @Inject
    constructor(
        private val budgetDao: BudgetDao,
    ) {
        fun getByMonth(month: Long): Flow<Budget?> =
            budgetDao.getByMonth(month).map { entity ->
                entity?.toDomain()
            }

        private fun BudgetEntity.toDomain() =
            Budget(
                id = id,
                month = month,
                amount = amount,
            )

        private fun Budget.toEntity() =
            BudgetEntity(
                id = id,
                month = month,
                amount = amount,
            )
    }
