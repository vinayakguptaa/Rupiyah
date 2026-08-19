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
class TabDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TabDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.tabDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndGetById() = runBlocking {
        val id = dao.upsert(TabEntity(name = "Groceries"))
        val loaded = dao.getById(id)
        assertThat(loaded).isNotNull()
        assertThat(loaded!!.name).isEqualTo("Groceries")
    }

    @Test
    fun observeActive_excludesArchived() = runBlocking {
        val activeId = dao.upsert(TabEntity(name = "Active"))
        dao.upsert(TabEntity(name = "Archived", archived = true))
        val active = dao.observeActive().first()
        assertThat(active).hasSize(1)
        assertThat(active[0].id).isEqualTo(activeId)
    }

    @Test
    fun update_modifiesEntity() = runBlocking {
        val id = dao.upsert(TabEntity(name = "Old Name"))
        dao.update(TabEntity(id = id, name = "New Name"))
        val loaded = dao.getById(id)
        assertThat(loaded!!.name).isEqualTo("New Name")
    }

    @Test
    fun getByName_findsByName() = runBlocking {
        dao.upsert(TabEntity(name = "Fuel"))
        val found = dao.getByName("Fuel")
        assertThat(found).isNotNull()
    }

    @Test
    fun getAll_returnsAllIncludingArchived() = runBlocking {
        dao.upsert(TabEntity(name = "A"))
        dao.upsert(TabEntity(name = "B", archived = true))
        assertThat(dao.getAll()).hasSize(2)
    }
}
