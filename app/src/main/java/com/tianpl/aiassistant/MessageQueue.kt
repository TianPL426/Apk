package com.tianpl.aiassistant

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

const val STATUS_PENDING = "pending"
const val STATUS_UPLOADED = "uploaded"

@Entity(tableName = "notification_queue")
data class QueuedNotification(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "client_message_id")
    val clientMessageId: String,
    @ColumnInfo(name = "source_app") val sourceApp: String?,
    @ColumnInfo(name = "package_name") val packageName: String?,
    val sender: String?,
    @ColumnInfo(name = "conversation_name") val conversationName: String?,
    val title: String?,
    val content: String?,
    @ColumnInfo(name = "received_at") val receivedAt: Long?,
    @ColumnInfo(name = "captured_at") val capturedAt: Long?,
    @ColumnInfo(name = "notification_key") val notificationKey: String?,
    @ColumnInfo(name = "payload_hash") val payloadHash: String,
    @ColumnInfo(name = "upload_status") val uploadStatus: String = STATUS_PENDING,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "uploaded_at") val uploadedAt: Long? = null,
)

data class CapturedNotification(
    val clientMessageId: String,
    val sourceApp: String,
    val packageName: String,
    val sender: String?,
    val conversationName: String?,
    val title: String?,
    val content: String,
    val receivedAt: Long,
    val capturedAt: Long,
    val notificationKey: String?,
)

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(notification: QueuedNotification): Long

    @Query(
        "SELECT * FROM notification_queue " +
            "WHERE upload_status = 'pending' ORDER BY captured_at ASC LIMIT :limit"
    )
    suspend fun pending(limit: Int = 50): List<QueuedNotification>

    @Query("SELECT * FROM notification_queue WHERE client_message_id = :id")
    suspend fun find(id: String): QueuedNotification?

    @Query("SELECT COUNT(*) FROM notification_queue WHERE upload_status = :status")
    suspend fun count(status: String): Int

    @Query(
        """
        UPDATE notification_queue SET
            source_app = NULL,
            package_name = NULL,
            sender = NULL,
            conversation_name = NULL,
            title = NULL,
            content = NULL,
            received_at = NULL,
            captured_at = NULL,
            notification_key = NULL,
            upload_status = 'uploaded',
            uploaded_at = :uploadedAt,
            last_error = NULL
        WHERE client_message_id = :id
        """
    )
    suspend fun markUploaded(id: String, uploadedAt: Long)

    @Query(
        """
        UPDATE notification_queue SET
            upload_status = 'pending',
            attempt_count = attempt_count + 1,
            last_error = :error
        WHERE client_message_id = :id
        """
    )
    suspend fun markPending(id: String, error: String)
}

@Database(entities = [QueuedNotification::class], version = 1, exportSchema = false)
abstract class QueueDatabase : RoomDatabase() {
    abstract fun notifications(): NotificationDao
}

object QueueDatabaseProvider {
    @Volatile private var instance: QueueDatabase? = null

    fun get(context: Context): QueueDatabase = instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
            context.applicationContext,
            QueueDatabase::class.java,
            "notification-queue.db",
        ).build().also { instance = it }
    }
}

interface UploadScheduler {
    fun enqueue()
}

class CaptureQueue(
    private val dao: NotificationDao,
    private val settings: CaptureSettings,
    private val scheduler: UploadScheduler,
) {
    suspend fun capture(notification: CapturedNotification): Boolean {
        if (!settings.captureEnabled || notification.receivedAt < settings.enabledSince) {
            return false
        }
        val queued = QueuedNotification(
            clientMessageId = notification.clientMessageId,
            sourceApp = notification.sourceApp,
            packageName = notification.packageName,
            sender = notification.sender,
            conversationName = notification.conversationName,
            title = notification.title,
            content = notification.content,
            receivedAt = notification.receivedAt,
            capturedAt = notification.capturedAt,
            notificationKey = notification.notificationKey,
            payloadHash = payloadHash(notification),
        )
        val inserted = dao.insert(queued) != -1L
        if (inserted) scheduler.enqueue()
        return inserted
    }

    private fun payloadHash(notification: CapturedNotification): String {
        val value = listOf(
            notification.clientMessageId,
            notification.sourceApp,
            notification.packageName,
            notification.sender,
            notification.conversationName,
            notification.title,
            notification.content,
            notification.receivedAt,
            notification.capturedAt,
            notification.notificationKey,
        ).joinToString("\u0000") { it?.toString().orEmpty() }
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
