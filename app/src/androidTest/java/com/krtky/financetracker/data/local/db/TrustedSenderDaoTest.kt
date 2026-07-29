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
class TrustedSenderDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TrustedSenderDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.trustedSenderDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndObserve() = runBlocking {
        dao.upsert(TrustedSenderEntity(emailPattern = "alerts@bank.com", walletLabel = "Bank", enabled = true))
        val all = dao.observeAll().first()
        assertThat(all).hasSize(1)
        assertThat(all[0].emailPattern).isEqualTo("alerts@bank.com")
    }

    @Test
    fun upsert_replacesByPattern() = runBlocking {
        dao.upsert(TrustedSenderEntity(emailPattern = "dup@test.com", walletLabel = "A", enabled = true))
        dao.upsert(TrustedSenderEntity(emailPattern = "dup@test.com", walletLabel = "B", enabled = false))
        val all = dao.observeAll().first()
        assertThat(all).hasSize(1)
        assertThat(all[0].walletLabel).isEqualTo("B")
    }

    @Test
    fun getEnabled_returnsOnlyEnabled() = runBlocking {
        dao.upsert(TrustedSenderEntity(emailPattern = "a@a.com", walletLabel = "A", enabled = true))
        dao.upsert(TrustedSenderEntity(emailPattern = "b@b.com", walletLabel = "B", enabled = false))
        val enabled = dao.getEnabled()
        assertThat(enabled).hasSize(1)
        assertThat(enabled[0].emailPattern).isEqualTo("a@a.com")
    }

    @Test
    fun delete_removesSender() = runBlocking {
        val id = dao.upsert(TrustedSenderEntity(emailPattern = "remove@me.com", walletLabel = "Remove", enabled = true))
        dao.delete(id)
        assertThat(dao.observeAll().first()).isEmpty()
    }
}
