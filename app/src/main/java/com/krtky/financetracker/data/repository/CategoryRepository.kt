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
            val defaults = listOf(
                CategoryEntity(name = "Food", icon = "restaurant", color = 0xFFE67E22, sortOrder = 1, isSystem = true, isQuickAction = true),
                CategoryEntity(name = "Travel", icon = "directions_bus", color = 0xFF3498DB, sortOrder = 2, isSystem = true, isQuickAction = true),
                CategoryEntity(name = "Shopping", icon = "shopping_bag", color = 0xFF9B59B6, sortOrder = 3, isSystem = true, isQuickAction = true),
                CategoryEntity(name = "Coaching/Education", icon = "school", color = 0xFF1ABC9C, sortOrder = 4, isSystem = true, isQuickAction = true),
                CategoryEntity(name = "Entertainment", icon = "movie", color = 0xFFE74C3C, sortOrder = 5, isSystem = true, isQuickAction = true),
                CategoryEntity(name = "Other", icon = "more_horiz", color = 0xFF7F8C8D, sortOrder = 6, isSystem = true, isQuickAction = true),
                CategoryEntity(name = "Transfer", icon = "swap_horiz", color = 0xFF34495E, sortOrder = 7, isSystem = true, isQuickAction = false),
                CategoryEntity(name = "Salary/Income", icon = "payments", color = 0xFF27AE60, sortOrder = 8, isSystem = true, isQuickAction = true),
            )
            defaults.forEach { dao.upsert(it) }
        }
        // Repair income icons on existing installs
        dao.getAll()
            .filter {
                (it.name.contains("Salary", true) || it.name.contains("Income", true)) &&
                    it.icon !in setOf("payments", "salary", "income")
            }
            .forEach { dao.upsert(it.copy(icon = "payments", isQuickAction = true)) }
    }
}
