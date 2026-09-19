package com.krtky.financetracker.data.repository

import com.krtky.financetracker.data.local.db.AppDatabase
import com.krtky.financetracker.data.local.db.CategoryEntity
import com.krtky.financetracker.data.local.db.toDomain
import com.krtky.financetracker.data.local.db.toEntity
import com.krtky.financetracker.domain.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.krtky.financetracker.data.prefs.UserPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    db: AppDatabase,
    private val userPreferences: UserPreferences,
) {
    private val dao = db.categoryDao()

    fun observeAll(): Flow<List<Category>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getAll(): List<Category> = dao.getAll().map { it.toDomain() }

    suspend fun getQuickActions(): List<Category> = dao.getQuickActions().map { it.toDomain() }

    suspend fun upsert(category: Category): Long = dao.upsert(category.toEntity())

    suspend fun delete(id: Long) = dao.delete(id)

    /**
     * Cold-start defaults: seed a fresh install, or on existing installs rename
     * legacy salary labels and insert any missing default rows. Never overwrites
     * icon/color on categories the user already has (Settings customizations).
     * Runs migration only once so user deletions are not resurrected.
     */
    suspend fun ensureDefaults() {
        if (dao.getAll().isEmpty()) {
            defaultCategories().forEach { dao.upsert(it) }
            userPreferences.setCategoriesDefaultsMigrated(true)
            return
        }
        if (!userPreferences.isCategoriesDefaultsMigrated()) {
            migrateExistingDefaults()
            userPreferences.setCategoriesDefaultsMigrated(true)
        }
    }

    private suspend fun migrateExistingDefaults() {
        val existing = dao.getAll()

        existing
            .filter { it.name.equals("Salary/Income", true) || it.name.equals("Salary/ Income", true) }
            .forEach { row ->
                dao.upsert(row.copy(name = "Salary", isQuickAction = true))
            }

        val afterRename = dao.getAll()
        val byName = afterRename.associateBy { it.name.lowercase() }
        var orderBase = (afterRename.maxOfOrNull { it.sortOrder } ?: 0) + 1

        for (def in defaultCategories()) {
            if (byName.containsKey(def.name.lowercase())) continue
            dao.upsert(def.copy(id = 0, sortOrder = orderBase++))
        }
    }

    companion object {
        fun defaultCategories(): List<CategoryEntity> = listOf(
            CategoryEntity(name = "Travel", icon = "commute", color = 0xFF0288D1, sortOrder = 1, isSystem = true, isQuickAction = true),
            CategoryEntity(name = "Food", icon = "restaurant", color = 0xFFFF5722, sortOrder = 2, isSystem = true, isQuickAction = true),
            CategoryEntity(name = "Groceries", icon = "shopping_cart", color = 0xFF4CAF50, sortOrder = 3, isSystem = true, isQuickAction = true),
            CategoryEntity(name = "Rent", icon = "home", color = 0xFF673AB7, sortOrder = 4, isSystem = true, isQuickAction = false),
            CategoryEntity(name = "Health", icon = "medical_services", color = 0xFFF44336, sortOrder = 5, isSystem = true, isQuickAction = false),
            CategoryEntity(name = "Fuel", icon = "local_gas_station", color = 0xFFFF9800, sortOrder = 6, isSystem = true, isQuickAction = true),
            CategoryEntity(name = "Clothing", icon = "checkroom", color = 0xFFE040FB, sortOrder = 7, isSystem = true, isQuickAction = false),
            CategoryEntity(name = "Subscriptions", icon = "subscriptions", color = 0xFF9C27B0, sortOrder = 8, isSystem = true, isQuickAction = false),
            CategoryEntity(name = "Entertainment", icon = "movie", color = 0xFF7C4DFF, sortOrder = 9, isSystem = true, isQuickAction = true),
            CategoryEntity(name = "Utilities", icon = "lightbulb", color = 0xFFFFC107, sortOrder = 10, isSystem = true, isQuickAction = false),
            CategoryEntity(name = "Family", icon = "family_restroom", color = 0xFFE91E63, sortOrder = 11, isSystem = true, isQuickAction = false),
            CategoryEntity(name = "Electronics", icon = "devices", color = 0xFF3F51B5, sortOrder = 12, isSystem = true, isQuickAction = false),
            CategoryEntity(name = "Transfer", icon = "swap_horiz", color = 0xFF607D8B, sortOrder = 13, isSystem = true, isQuickAction = false),
            CategoryEntity(name = "Investment", icon = "trending_up", color = 0xFF2E7D32, sortOrder = 14, isSystem = true, isQuickAction = true),
            CategoryEntity(name = "Settlement", icon = "handshake", color = 0xFF009688, sortOrder = 15, isSystem = true, isQuickAction = false),
            CategoryEntity(name = "Salary", icon = "account_balance_wallet", color = 0xFF1B5E20, sortOrder = 16, isSystem = true, isQuickAction = true),
            CategoryEntity(name = "Professional", icon = "work", color = 0xFF795548, sortOrder = 17, isSystem = true, isQuickAction = false),
            CategoryEntity(name = "Dividend", icon = "payments", color = 0xFF8BC34A, sortOrder = 18, isSystem = true, isQuickAction = false),
            CategoryEntity(name = "Interest", icon = "percent", color = 0xFF00BCD4, sortOrder = 19, isSystem = true, isQuickAction = false),
            CategoryEntity(name = "Fees & Charges", icon = "receipt_long", color = 0xFFD32F2F, sortOrder = 20, isSystem = true, isQuickAction = false),
            CategoryEntity(name = "Tax", icon = "account_balance", color = 0xFF455A64, sortOrder = 21, isSystem = true, isQuickAction = false),
            CategoryEntity(name = "Donation", icon = "volunteer_activism", color = 0xFFEF6C00, sortOrder = 22, isSystem = true, isQuickAction = false),
            CategoryEntity(name = "Other", icon = "more_horiz", color = 0xFF9E9E9E, sortOrder = 23, isSystem = true, isQuickAction = true),
        )
    }
}
