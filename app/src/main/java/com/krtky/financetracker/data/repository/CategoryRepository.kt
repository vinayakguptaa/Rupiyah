package com.krtky.financetracker.data.repository

import com.krtky.financetracker.data.local.db.AppDatabase
import com.krtky.financetracker.data.local.db.CategoryEntity
import com.krtky.financetracker.data.local.db.toDomain
import com.krtky.financetracker.data.local.db.toEntity
import com.krtky.financetracker.domain.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    db: AppDatabase,
) {
    private val dao = db.categoryDao()

    fun observeAll(): Flow<List<Category>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getAll(): List<Category> = dao.getAll().map { it.toDomain() }

    suspend fun getQuickActions(): List<Category> = dao.getQuickActions().map { it.toDomain() }

    suspend fun upsert(category: Category): Long = dao.upsert(category.toEntity())

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun seedDefaultsIfEmpty() {
        if (dao.getAll().isEmpty()) {
            defaultCategories().forEach { dao.upsert(it) }
            return
        }
        ensureCashflowCategories()
    }

    /**
     * Add any missing cashflow default categories on existing installs without
     * overwriting user renames. Also renames legacy "Salary/Income" → "Salary".
     */
    private suspend fun ensureCashflowCategories() {
        val existing = dao.getAll()

        // Legacy rename
        existing
            .filter { it.name.equals("Salary/Income", true) || it.name.equals("Salary/ Income", true) }
            .forEach { dao.upsert(it.copy(name = "Salary", icon = "payments", isQuickAction = true)) }

        existing
            .filter {
                (it.name.contains("Salary", true) || it.name.contains("Income", true)) &&
                    it.icon !in setOf("payments", "salary", "income")
            }
            .forEach { dao.upsert(it.copy(icon = "payments", isQuickAction = true)) }

        val afterRename = dao.getAll().associateBy { it.name.lowercase() }
        var orderBase = (dao.getAll().maxOfOrNull { it.sortOrder } ?: 0) + 1
        for (def in defaultCategories()) {
            if (afterRename.containsKey(def.name.lowercase())) continue
            dao.upsert(def.copy(id = 0, sortOrder = orderBase++))
        }
    }

    companion object {
        fun defaultCategories(): List<CategoryEntity> = listOf(
            CategoryEntity(name = "Travel", icon = "directions_bus", color = 0xFF3498DB, sortOrder = 1, isSystem = true, isQuickAction = true),
            CategoryEntity(name = "Food", icon = "restaurant", color = 0xFFE67E22, sortOrder = 2, isSystem = true, isQuickAction = true),
            CategoryEntity(name = "Groceries", icon = "grocery", color = 0xFF27AE60, sortOrder = 3, isSystem = true, isQuickAction = true),
            CategoryEntity(name = "Rent", icon = "home", color = 0xFF8E44AD, sortOrder = 4, isSystem = true, isQuickAction = false),
            CategoryEntity(name = "Health", icon = "local_hospital", color = 0xFFE74C3C, sortOrder = 5, isSystem = true, isQuickAction = false),
            CategoryEntity(name = "Fuel", icon = "local_gas_station", color = 0xFFF39C12, sortOrder = 6, isSystem = true, isQuickAction = true),
            CategoryEntity(name = "Clothing", icon = "checkroom", color = 0xFF9B59B6, sortOrder = 7, isSystem = true, isQuickAction = false),
            CategoryEntity(name = "Subscriptions", icon = "subscriptions", color = 0xFF1ABC9C, sortOrder = 8, isSystem = true, isQuickAction = false),
            CategoryEntity(name = "Entertainment", icon = "movie", color = 0xFFE74C3C, sortOrder = 9, isSystem = true, isQuickAction = true),
            CategoryEntity(name = "Utilities", icon = "build", color = 0xFF7F8C8D, sortOrder = 10, isSystem = true, isQuickAction = false),
            CategoryEntity(name = "Family", icon = "pets", color = 0xFFE91E63, sortOrder = 11, isSystem = true, isQuickAction = false),
            CategoryEntity(name = "Transfer", icon = "swap_horiz", color = 0xFF34495E, sortOrder = 12, isSystem = true, isQuickAction = false),
            CategoryEntity(name = "Investment", icon = "trending_up", color = 0xFF16A085, sortOrder = 13, isSystem = true, isQuickAction = true),
            CategoryEntity(name = "Settlement", icon = "payments", color = 0xFF2980B9, sortOrder = 14, isSystem = true, isQuickAction = false),
            CategoryEntity(name = "Salary", icon = "payments", color = 0xFF27AE60, sortOrder = 15, isSystem = true, isQuickAction = true),
            CategoryEntity(name = "Professional", icon = "work", color = 0xFF2C3E50, sortOrder = 16, isSystem = true, isQuickAction = false),
            CategoryEntity(name = "Dividend", icon = "trending_up", color = 0xFF1ABC9C, sortOrder = 17, isSystem = true, isQuickAction = false),
            CategoryEntity(name = "Interest", icon = "account_balance", color = 0xFF3498DB, sortOrder = 18, isSystem = true, isQuickAction = false),
            CategoryEntity(name = "Fees & Charges", icon = "more_horiz", color = 0xFF95A5A6, sortOrder = 19, isSystem = true, isQuickAction = false),
            CategoryEntity(name = "Tax", icon = "account_balance", color = 0xFF7F8C8D, sortOrder = 20, isSystem = true, isQuickAction = false),
            CategoryEntity(name = "Other", icon = "more_horiz", color = 0xFF7F8C8D, sortOrder = 21, isSystem = true, isQuickAction = true),
        )
    }
}
