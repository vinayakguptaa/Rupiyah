package com.krtky.financetracker.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: CategoryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.categoryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndGetById() = runBlocking {
        val id = dao.upsert(CategoryEntity(name = "Food", icon = "restaurant", color = 0xFFE67E22, sortOrder = 1))
        val loaded = dao.getById(id)
        assertThat(loaded).isNotNull()
        assertThat(loaded!!.name).isEqualTo("Food")
    }

    @Test
    fun observeAll_returnsAll() = runBlocking {
        dao.upsert(CategoryEntity(name = "Food", sortOrder = 1))
        dao.upsert(CategoryEntity(name = "Travel", sortOrder = 2))
        val all = dao.observeAll().first()
        assertThat(all).hasSize(2)
    }

    @Test
    fun upsert_replacesExisting() = runBlocking {
        val id = dao.upsert(CategoryEntity(name = "Food", icon = "restaurant", sortOrder = 1))
        dao.upsert(CategoryEntity(id = id, name = "Food Updated", icon = "restaurant", sortOrder = 1))
        val loaded = dao.getById(id)
        assertThat(loaded!!.name).isEqualTo("Food Updated")
    }

    @Test
    fun delete_removesCategory() = runBlocking {
        val id = dao.upsert(CategoryEntity(name = "Temp", sortOrder = 99))
        dao.delete(id)
        assertThat(dao.getById(id)).isNull()
    }

    @Test
    fun getByName_findsByName() = runBlocking {
        dao.upsert(CategoryEntity(name = "Shopping", sortOrder = 1))
        val found = dao.getByName("Shopping")
        assertThat(found).isNotNull()
    }

    @Test
    fun getQuickActions_limitsToSix() = runBlocking {
        repeat(8) { i ->
            dao.upsert(CategoryEntity(name = "Cat$i", isQuickAction = true, sortOrder = i))
        }
        val qa = dao.getQuickActions()
        assertThat(qa).hasSize(6)
    }
}
