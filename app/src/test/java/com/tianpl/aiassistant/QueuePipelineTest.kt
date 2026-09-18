package com.tianpl.aiassistant

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueuePipelineTest {
    private lateinit var context: Context
    private lateinit var database: QueueDatabase
    private lateinit var dao: NotificationDao
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "queue-test-${UUID.randomUUID()}.db"
        database = openDatabase()
        dao = database.notifications()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun notificationIsPersistedBeforeUploadIsScheduled() = runBlocking {
        val input = captured("capture-first")
        var scheduled = false
        var rowExistedWhenScheduled = false
        val scheduler = object : UploadScheduler {
            override fun enqueue() {
                scheduled = true
                rowExistedWhenScheduled = runBlocking { dao.find(input.clientMessageId) != null }
            }
        }

        val inserted = CaptureQueue(
            dao,
            FakeSettings(enabled = true, since = 1),
            scheduler,
        ).capture(input)

        assertTrue(inserted)
        assertTrue(scheduled)
        assertTrue(rowExistedWhenScheduled)
        assertEquals(1, dao.count(STATUS_PENDING))
    }

    @Test
    fun successfulUploadKeepsOnlyMinimalReceipt() = runBlocking {
        insertPending("online")
        val sender = FakeSender()

        assertTrue(UploadCoordinator(dao, sender) { 9_999 }.uploadPending())

        val uploaded = requireNotNull(dao.find("online"))
        assertEquals(STATUS_UPLOADED, uploaded.uploadStatus)
        assertEquals(9_999L, uploaded.uploadedAt)
        assertNull(uploaded.content)
        assertNull(uploaded.notificationKey)
        assertEquals(1, sender.sent.size)
    }

    @Test
    fun offlineMessageRemainsPendingAndUploadsAfterRecovery() = runBlocking {
        insertPending("offline-recovery")
        val sender = FakeSender(fail = true)
        val coordinator = UploadCoordinator(dao, sender)

        assertFalse(coordinator.uploadPending())
        assertEquals(STATUS_PENDING, dao.find("offline-recovery")?.uploadStatus)
        assertEquals(1, dao.find("offline-recovery")?.attemptCount)

        sender.fail = false
        assertTrue(coordinator.uploadPending())
        assertEquals(STATUS_UPLOADED, dao.find("offline-recovery")?.uploadStatus)
    }

    @Test
    fun uploadedMessageIsNotSentAgain() = runBlocking {
        insertPending("single-send")
        val sender = FakeSender()
        val coordinator = UploadCoordinator(dao, sender)

        assertTrue(coordinator.uploadPending())
        assertTrue(coordinator.uploadPending())

        assertEquals(listOf("single-send"), sender.sent)
    }

    @Test
    fun captureSwitchAndEnableTimeRejectDisabledOrHistoricalNotifications() = runBlocking {
        val scheduler = CountingScheduler()
        val disabledQueue = CaptureQueue(
            dao,
            FakeSettings(enabled = false, since = 1),
            scheduler,
        )
        val enabledQueue = CaptureQueue(
            dao,
            FakeSettings(enabled = true, since = 2_000),
            scheduler,
        )

        assertFalse(disabledQueue.capture(captured("disabled", receivedAt = 3_000)))
        assertFalse(enabledQueue.capture(captured("historical", receivedAt = 1_999)))
        assertEquals(0, dao.count(STATUS_PENDING))
        assertEquals(0, scheduler.count)
    }

    @Test
    fun pendingMessageSurvivesDatabaseReopen() = runBlocking {
        insertPending("restart")
        database.close()

        database = openDatabase()
        dao = database.notifications()

        val restored = dao.find("restart")
        assertNotNull(restored)
        assertEquals(STATUS_PENDING, restored?.uploadStatus)
        assertEquals("message-restart", restored?.content)
    }

    @Test
    fun notificationIdentityIsStable() {
        val first = stableClientMessageId("com.example", "key", 123)
        val retry = stableClientMessageId("com.example", "key", 123)
        val other = stableClientMessageId("com.example", "key", 124)

        assertEquals(first, retry)
        assertFalse(first == other)
    }

    private fun openDatabase() = Room.databaseBuilder(
        context,
        QueueDatabase::class.java,
        databaseName,
    ).build()

    private suspend fun insertPending(id: String) {
        dao.insert(
            QueuedNotification(
                clientMessageId = id,
                sourceApp = "Example",
                packageName = "com.example",
                sender = "Sender",
                conversationName = "Conversation",
                title = "Title",
                content = "message-$id",
                receivedAt = 1_000,
                capturedAt = 2_000,
                notificationKey = "key-$id",
                payloadHash = "hash-$id",
            )
        )
    }

    private fun captured(id: String, receivedAt: Long = 3_000) = CapturedNotification(
        clientMessageId = id,
        sourceApp = "Example",
        packageName = "com.example",
        sender = "Sender",
        conversationName = "Conversation",
        title = "Title",
        content = "message-$id",
        receivedAt = receivedAt,
        capturedAt = 4_000,
        notificationKey = "key-$id",
    )

    private data class FakeSettings(
        val enabled: Boolean,
        val since: Long,
    ) : CaptureSettings {
        override val captureEnabled = enabled
        override val enabledSince = since
    }

    private class CountingScheduler : UploadScheduler {
        var count = 0
        override fun enqueue() {
            count += 1
        }
    }

    private class FakeSender(var fail: Boolean = false) : MessageSender {
        val sent = mutableListOf<String>()

        override suspend fun send(notification: QueuedNotification) {
            if (fail) throw IOException("offline")
            sent += notification.clientMessageId
        }
    }
}
