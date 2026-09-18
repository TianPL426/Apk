package com.tianpl.aiassistant

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.UUID

class NotificationCaptureService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(notification: StatusBarNotification) {
        if (notification.packageName == packageName) return
        val settings = AppSettings(this)
        if (!settings.captureEnabled || notification.postTime < settings.enabledSince) return

        val captured = extract(notification) ?: return
        val dao = QueueDatabaseProvider.get(this).notifications()
        val queue = CaptureQueue(dao, settings, WorkUploadScheduler(this))
        scope.launch { queue.capture(captured) }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val scheduler = WorkUploadScheduler(this)
        scheduler.ensurePeriodicRetry()
        scheduler.enqueue()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun extract(statusBarNotification: StatusBarNotification): CapturedNotification? {
        val extras = statusBarNotification.notification.extras
        val message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                ?.let(Notification.MessagingStyle.Message::getMessagesFromBundleArray)
                ?.lastOrNull()
        } else {
            null
        }
        val content = (
            message?.text
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            )?.toString()?.trim().orEmpty()
        if (content.isEmpty()) return null

        val packageName = statusBarNotification.packageName
        val sourceApp = try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (_: Exception) {
            packageName
        }
        val conversation = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?.toString()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val messageSender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            message?.senderPerson?.name?.toString()
        } else {
            null
        }
        val sender = messageSender ?: title
        val clientMessageId = stableClientMessageId(
            packageName,
            statusBarNotification.key,
            statusBarNotification.postTime,
        )

        return CapturedNotification(
            clientMessageId = clientMessageId,
            sourceApp = sourceApp,
            packageName = packageName,
            sender = sender,
            conversationName = conversation,
            title = title,
            content = content,
            receivedAt = statusBarNotification.postTime,
            capturedAt = System.currentTimeMillis(),
            notificationKey = statusBarNotification.key,
        )
    }
}

internal fun stableClientMessageId(
    packageName: String,
    notificationKey: String,
    postTime: Long,
): String {
    val stableSource = "$packageName|$notificationKey|$postTime"
    return UUID.nameUUIDFromBytes(
        stableSource.toByteArray(StandardCharsets.UTF_8)
    ).toString()
}
