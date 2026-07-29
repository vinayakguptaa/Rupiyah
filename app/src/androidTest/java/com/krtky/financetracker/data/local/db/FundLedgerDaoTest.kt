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
class FundLedgerDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: FundLedgerDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.fundLedgerDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndQuery() = runBlocking {
        dao.insert(FundLedgerEntity(fundId = 1L, entryType = "CREDIT", amountPaise = 10_00_00L, balanceAfterPaise = 10_00_00L))
        val entries = dao.getForFund(1L)
        assertThat(entries).hasSize(1)
        assertThat(entries[0].amountPaise).isEqualTo(10_00_00L)
    }

    @Test
    fun observeForFund_returnsEntries() = runBlocking {
        dao.insert(FundLedgerEntity(fundId = 1L, entryType = "CREDIT", amountPaise = 10_00_00L, balanceAfterPaise = 10_00_00L))
        val entries = dao.observeForFund(1L).first()
        assertThat(entries).isNotEmpty()
    }

    @Test
    fun latestBalance_returnsMostRecent() = runBlocking {
        dao.insert(FundLedgerEntity(fundId = 1L, entryType = "CREDIT", amountPaise = 10_00_00L, balanceAfterPaise = 10_00_00L))
        dao.insert(FundLedgerEntity(fundId = 1L, entryType = "DEBIT", amountPaise = 3_00_00L, balanceAfterPaise = 7_00_00L))
        val balance = dao.latestBalance(1L)
        assertThat(balance).isEqualTo(7_00_00L)
    }

    @Test
    fun latestBalance_returnsNullWhenEmpty() = runBlocking {
        val balance = dao.latestBalance(999L)
        assertThat(balance).isNull()
    }

    @Test
    fun totalCredits_sumsCorrectly() = runBlocking {
        dao.insert(FundLedgerEntity(fundId = 1L, entryType = "CREDIT", amountPaise = 10_00_00L, balanceAfterPaise = 10_00_00L))
        dao.insert(FundLedgerEntity(fundId = 1L, entryType = "CREDIT", amountPaise = 5_00_00L, balanceAfterPaise = 15_00_00L))
        assertThat(dao.totalCredits(1L)).isEqualTo(15_00_00L)
    }

    @Test
    fun totalDebits_sumsCorrectly() = runBlocking {
        dao.insert(FundLedgerEntity(fundId = 1L, entryType = "DEBIT", amountPaise = 3_00_00L, balanceAfterPaise = 7_00_00L))
        dao.insert(FundLedgerEntity(fundId = 1L, entryType = "DEBIT", amountPaise = 2_00_00L, balanceAfterPaise = 5_00_00L))
        assertThat(dao.totalDebits(1L)).isEqualTo(5_00_00L)
    }

    @Test
    fun deleteForTransaction_removesEntry() = runBlocking {
        val id = dao.insert(FundLedgerEntity(fundId = 1L, transactionId = "txn-1", entryType = "DEBIT", amountPaise = 100L, balanceAfterPaise = 100L))
        dao.deleteForTransaction("txn-1")
        assertThat(dao.getForFund(1L)).isEmpty()
    }

    @Test
    fun deleteAllForFund_removesAllEntries() = runBlocking {
        dao.insert(FundLedgerEntity(fundId = 1L, entryType = "CREDIT", amountPaise = 100L, balanceAfterPaise = 100L))
        dao.insert(FundLedgerEntity(fundId = 1L, entryType = "DEBIT", amountPaise = 50L, balanceAfterPaise = 50L))
        dao.deleteAllForFund(1L)
        assertThat(dao.getForFund(1L)).isEmpty()
    }
}
