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
class TransactionDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TransactionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.transactionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndGetById() = runBlocking {
        val txn = TransactionEntity(
            id = "txn-1", type = "DEBIT", amountPaise = 5_00_00L,
            occurredAt = 1000L, recordedAt = 1000L, source = "MANUAL",
            classificationStatus = "PENDING", updatedAt = 1000L,
        )
        dao.insert(txn)
        val loaded = dao.getById("txn-1")
        assertThat(loaded).isNotNull()
        assertThat(loaded!!.amountPaise).isEqualTo(5_00_00L)
    }

    @Test
    fun insert_ignoresDuplicateId() = runBlocking {
        val txn = TransactionEntity(
            id = "dup", type = "DEBIT", amountPaise = 100L,
            occurredAt = 1L, recordedAt = 1L, source = "MANUAL",
            classificationStatus = "PENDING", updatedAt = 1L,
        )
        val first = dao.insert(txn)
        assertThat(first).isNotEqualTo(-1L)
        val second = dao.insert(txn.copy(amountPaise = 200L))
        assertThat(second).isEqualTo(-1L)
        val loaded = dao.getById("dup")
        assertThat(loaded!!.amountPaise).isEqualTo(100L)
    }

    @Test
    fun observeAll_returnsOnlyNonDeleted() = runBlocking {
        dao.insert(
            TransactionEntity(id = "a", type = "DEBIT", amountPaise = 100L, occurredAt = 1L, recordedAt = 1L, source = "MANUAL", classificationStatus = "PENDING", updatedAt = 1L)
        )
        dao.insert(
            TransactionEntity(id = "b", type = "DEBIT", amountPaise = 200L, occurredAt = 2L, recordedAt = 2L, source = "MANUAL", classificationStatus = "PENDING", updatedAt = 2L)
        )
        dao.softDelete("b")
        val all = dao.observeAll().first()
        assertThat(all).hasSize(1)
        assertThat(all[0].id).isEqualTo("a")
    }

    @Test
    fun update_modifiesTransaction() = runBlocking {
        dao.insert(
            TransactionEntity(id = "u1", type = "DEBIT", amountPaise = 100L, occurredAt = 1L, recordedAt = 1L, source = "MANUAL", classificationStatus = "PENDING", updatedAt = 1L)
        )
        dao.update(
            TransactionEntity(id = "u1", type = "CREDIT", amountPaise = 999L, occurredAt = 1L, recordedAt = 1L, source = "MANUAL", classificationStatus = "CLASSIFIED", updatedAt = 2L)
        )
        val loaded = dao.getById("u1")
        assertThat(loaded!!.type).isEqualTo("CREDIT")
        assertThat(loaded.amountPaise).isEqualTo(999L)
    }

    @Test
    fun sumByType_aggregatesCorrectly() = runBlocking {
        val now = System.currentTimeMillis()
        dao.insert(
            TransactionEntity(id = "s1", type = "CREDIT", amountPaise = 10_00_00L, occurredAt = now, recordedAt = now, source = "MANUAL", classificationStatus = "CLASSIFIED", updatedAt = now)
        )
        dao.insert(
            TransactionEntity(id = "s2", type = "DEBIT", amountPaise = 4_00_00L, occurredAt = now, recordedAt = now, source = "MANUAL", classificationStatus = "CLASSIFIED", updatedAt = now)
        )
        dao.insert(
            TransactionEntity(id = "s3", type = "DEBIT", amountPaise = 1_00_00L, occurredAt = now, recordedAt = now, source = "MANUAL", classificationStatus = "CLASSIFIED", updatedAt = now)
        )
        val income = dao.sumByType("CREDIT", 0L, now + 1)
        val expense = dao.sumByType("DEBIT", 0L, now + 1)
        assertThat(income).isEqualTo(10_00_00L)
        assertThat(expense).isEqualTo(5_00_00L)
    }

    @Test
    fun findByEmailMessageId() = runBlocking {
        dao.insert(
            TransactionEntity(id = "e1", type = "DEBIT", amountPaise = 100L, occurredAt = 1L, recordedAt = 1L, source = "EMAIL", classificationStatus = "PENDING", updatedAt = 1L, emailMessageId = "msg-1")
        )
        val found = dao.findByEmailMessageId("msg-1")
        assertThat(found).isNotNull()
        assertThat(found!!.id).isEqualTo("e1")
    }

    @Test
    fun findByContentHash() = runBlocking {
        dao.insert(
            TransactionEntity(id = "h1", type = "DEBIT", amountPaise = 100L, occurredAt = 1L, recordedAt = 1L, source = "MANUAL", classificationStatus = "PENDING", updatedAt = 1L, contentHash = "abc123")
        )
        val found = dao.findByContentHash("abc123")
        assertThat(found).isNotNull()
    }

    @Test
    fun categorySpend_returnsAggregatedData() = runBlocking {
        val now = System.currentTimeMillis()
        dao.insert(
            TransactionEntity(id = "c1", type = "DEBIT", amountPaise = 3_00_00L, occurredAt = now, recordedAt = now, source = "MANUAL", classificationStatus = "CLASSIFIED", updatedAt = now, categoryId = 1L)
        )
        dao.insert(
            TransactionEntity(id = "c2", type = "DEBIT", amountPaise = 2_00_00L, occurredAt = now, recordedAt = now, source = "MANUAL", classificationStatus = "CLASSIFIED", updatedAt = now, categoryId = 1L)
        )
        dao.insert(
            TransactionEntity(id = "c3", type = "DEBIT", amountPaise = 1_00_00L, occurredAt = now, recordedAt = now, source = "MANUAL", classificationStatus = "CLASSIFIED", updatedAt = now, categoryId = 2L)
        )
        val spend = dao.categorySpend(0L, now + 1)
        assertThat(spend).hasSize(2)
        assertThat(spend.first { it.categoryId == 1L }.totalPaise).isEqualTo(5_00_00L)
    }

    @Test
    fun getUnsynced_returnsOnlyNonDeletedUnsynced() = runBlocking {
        dao.insert(
            TransactionEntity(id = "x1", type = "DEBIT", amountPaise = 100L, occurredAt = 1L, recordedAt = 1L, source = "MANUAL", classificationStatus = "CLASSIFIED", updatedAt = 1L, sheetsSynced = false)
        )
        dao.insert(
            TransactionEntity(id = "x2", type = "DEBIT", amountPaise = 100L, occurredAt = 2L, recordedAt = 2L, source = "MANUAL", classificationStatus = "CLASSIFIED", updatedAt = 2L, sheetsSynced = true)
        )
        val unsynced = dao.getUnsynced()
        assertThat(unsynced).hasSize(1)
        assertThat(unsynced[0].id).isEqualTo("x1")
    }
}
