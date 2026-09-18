package com.tianpl.aiassistant

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.concurrent.TimeUnit

interface MessageSender {
    suspend fun send(notification: QueuedNotification)
}

class HttpMessageSender(private val settings: AppSettings) : MessageSender {
    override suspend fun send(notification: QueuedNotification) {
        val serverUrl = settings.serverUrl.trimEnd('/')
        val token = settings.token
        if (serverUrl.isBlank() || token.isBlank()) {
            throw IOException("Server URL and token are required")
        }
        val receivedAt = requireNotNull(notification.receivedAt)
        val capturedAt = requireNotNull(notification.capturedAt)
        val content = requireNotNull(notification.content)
        val body = JSONObject().apply {
            put("client_message_id", notification.clientMessageId)
            put("source_app", notification.sourceApp)
            put("package_name", notification.packageName)
            put("source_type", "notification")
            put("conversation_name", notification.conversationName)
            put("conversation_type", JSONObject.NULL)
            put("sender", notification.sender)
            put("title", notification.title)
            put("raw_text", content)
            put("received_at", Instant.ofEpochMilli(receivedAt).toString())
            put("captured_at", Instant.ofEpochMilli(capturedAt).toString())
            put("device_id", settings.deviceId)
            put("notification_id", notification.notificationKey)
        }.toString()

        request("$serverUrl/api/messages", "POST", token, body)
    }

    suspend fun testConnection() {
        val serverUrl = settings.serverUrl.trimEnd('/')
        val token = settings.token
        if (serverUrl.isBlank() || token.isBlank()) {
            throw IOException("Server URL and token are required")
        }
        request("$serverUrl/api/messages", "GET", token, null)
    }

    private suspend fun request(url: String, method: String, token: String, body: String?) {
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("X-Assistant-Token", token)
                connection.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                if (status !in 200..299) {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    throw IOException("HTTP $status: ${error.orEmpty().take(200)}")
                }
            } finally {
                connection.disconnect()
            }
        }
    }
}

class UploadCoordinator(
    private val dao: NotificationDao,
    private val sender: MessageSender,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun uploadPending(): Boolean {
        var allUploaded = true
        for (notification in dao.pending()) {
            try {
                sender.send(notification)
                dao.markUploaded(notification.clientMessageId, clock())
            } catch (error: Exception) {
                allUploaded = false
                dao.markPending(
                    notification.clientMessageId,
                    error.message.orEmpty().take(500),
                )
            }
        }
        return allUploaded
    }
}

class WorkUploadScheduler(private val context: Context) : UploadScheduler {
    override fun enqueue() {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun ensurePeriodicRetry() {
        val request = PeriodicWorkRequestBuilder<UploadWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun networkConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private companion object {
        const val IMMEDIATE_WORK = "upload-pending-messages"
        const val PERIODIC_WORK = "retry-pending-messages"
    }
}

class UploadWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val dao = QueueDatabaseProvider.get(applicationContext).notifications()
        val sender = HttpMessageSender(AppSettings(applicationContext))
        return if (UploadCoordinator(dao, sender).uploadPending()) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
