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
class AdditionalDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // -- EmailIngestDao --

    @Test
    fun emailIngest_insertAndFindByMessageId() = runBlocking {
        val dao = db.emailIngestDao()
        dao.insert(EmailIngestLogEntity(messageId = "msg-1", sender = "bank@test.com", subject = "Payment", receivedAt = 1000L, processStatus = "NEW"))
        val found = dao.findByMessageId("msg-1")
        assertThat(found).isNotNull()
        assertThat(found!!.sender).isEqualTo("bank@test.com")
    }

    @Test
    fun emailIngest_ignoreDuplicateMessageId() = runBlocking {
        val dao = db.emailIngestDao()
        dao.insert(EmailIngestLogEntity(messageId = "dup", sender = "a@a.com", subject = "S1", receivedAt = 1L, processStatus = "NEW"))
        val result = dao.insert(EmailIngestLogEntity(messageId = "dup", sender = "b@b.com", subject = "S2", receivedAt = 2L, processStatus = "NEW"))
        assertThat(result).isEqualTo(-1L)
    }

    @Test
    fun emailIngest_updateAndObserveRecent() = runBlocking {
        val dao = db.emailIngestDao()
        dao.insert(EmailIngestLogEntity(messageId = "m1", sender = "s@b.com", subject = "Subj", receivedAt = 1000L, processStatus = "NEW"))
        val inserted = dao.findByMessageId("m1")!!
        dao.update(inserted.copy(processStatus = "PARSED"))
        val updated = dao.findByMessageId("m1")
        assertThat(updated!!.processStatus).isEqualTo("PARSED")
    }

    // -- LocationSampleDao --

    @Test
    fun locationSample_insertAndFindClosest() = runBlocking {
        val dao = db.locationSampleDao()
        dao.insert(LocationSampleEntity(latitude = 28.61, longitude = 77.23, accuracy = 10f, capturedAt = 5000L))
        dao.insert(LocationSampleEntity(latitude = 28.62, longitude = 77.24, accuracy = 5f, capturedAt = 6000L))
        val closest = dao.findClosest(0L, 10_000L, 5500L)
        assertThat(closest).isNotNull()
    }

    @Test
    fun locationSample_latest_returnsMostRecent() = runBlocking {
        val dao = db.locationSampleDao()
        dao.insert(LocationSampleEntity(latitude = 1.0, longitude = 1.0, accuracy = 1f, capturedAt = 100L))
        dao.insert(LocationSampleEntity(latitude = 2.0, longitude = 2.0, accuracy = 2f, capturedAt = 200L))
        val latest = dao.latest()
        assertThat(latest).isNotNull()
        assertThat(latest!!.capturedAt).isEqualTo(200L)
    }

    @Test
    fun locationSample_pruneOlderThan() = runBlocking {
        val dao = db.locationSampleDao()
        dao.insert(LocationSampleEntity(latitude = 1.0, longitude = 1.0, accuracy = 1f, capturedAt = 100L))
        dao.insert(LocationSampleEntity(latitude = 2.0, longitude = 2.0, accuracy = 1f, capturedAt = 200L))
        dao.pruneOlderThan(150L)
        val latest = dao.latest()
        assertThat(latest!!.capturedAt).isEqualTo(200L)
    }

    // -- PendingClassificationDao --

    @Test
    fun pendingClassification_upsertAndDue() = runBlocking {
        val dao = db.pendingClassificationDao()
        dao.upsert(PendingClassificationEntity(transactionId = "txn-1", scheduledAt = 100L))
        dao.upsert(PendingClassificationEntity(transactionId = "txn-2", scheduledAt = 50L))
        val due = dao.due(75L)
        assertThat(due).hasSize(1)
        assertThat(due[0].transactionId).isEqualTo("txn-2")
    }

    @Test
    fun pendingClassification_deleteAndUpdate() = runBlocking {
        val dao = db.pendingClassificationDao()
        dao.upsert(PendingClassificationEntity(transactionId = "txn-1", scheduledAt = 100L))
        dao.update(PendingClassificationEntity(transactionId = "txn-1", scheduledAt = 100L, attempts = 3, status = "RETRY"))
        dao.delete("txn-1")
        val due = dao.due(999L)
        assertThat(due).isEmpty()
    }

    // -- SyncOutboxDao --

    @Test
    fun syncOutbox_insertAndPeek() = runBlocking {
        val dao = db.syncOutboxDao()
        dao.insert(SyncOutboxEntity(entityType = "transaction", entityId = "t1", operation = "UPSERT"))
        dao.insert(SyncOutboxEntity(entityType = "transaction", entityId = "t2", operation = "UPSERT"))
        val peeked = dao.peek()
        assertThat(peeked).hasSize(2)
    }

    @Test
    fun syncOutbox_deleteAndBumpAttempts() = runBlocking {
        val dao = db.syncOutboxDao()
        val id = dao.insert(SyncOutboxEntity(entityType = "transaction", entityId = "t1", operation = "UPSERT"))
        dao.bumpAttempts(id)
        dao.delete(id)
        assertThat(dao.peek()).isEmpty()
    }

    // -- SyncStateDao --

    @Test
    fun syncState_putAndGet() = runBlocking {
        val dao = db.syncStateDao()
        dao.put(SyncStateEntity(key = "last_sync", value = "2025-03-15"))
        val value = dao.get("last_sync")
        assertThat(value).isEqualTo("2025-03-15")
    }

    @Test
    fun syncState_putReplacesExisting() = runBlocking {
        val dao = db.syncStateDao()
        dao.put(SyncStateEntity(key = "token", value = "old"))
        dao.put(SyncStateEntity(key = "token", value = "new"))
        val value = dao.get("token")
        assertThat(value).isEqualTo("new")
    }

    @Test
    fun syncState_getReturnsNullForMissingKey() = runBlocking {
        val value = db.syncStateDao().get("nonexistent")
        assertThat(value).isNull()
    }
}
