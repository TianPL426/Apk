package com.tianpl.aiassistant

import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var settings: AppSettings
    private lateinit var accessStatus: TextView
    private lateinit var captureSwitch: Switch
    private lateinit var serverUrl: EditText
    private lateinit var token: EditText
    private lateinit var connectionStatus: TextView
    private lateinit var pendingCount: TextView
    private lateinit var uploadedCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings(this)
        val scheduler = WorkUploadScheduler(this)
        scheduler.ensurePeriodicRetry()
        scheduler.enqueue()
        setContentView(buildContent())
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildContent(): ScrollView {
        val padding = (20 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        content.addView(TextView(this).apply {
            text = "AI Assistant — Stage 3"
            textSize = 24f
        })
        accessStatus = label(content, "Notification Access: checking")
        content.addView(Button(this).apply {
            text = "Open Notification Access"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        })

        captureSwitch = Switch(this).apply {
            text = "Capture new notifications"
            isChecked = settings.captureEnabled
            setOnCheckedChangeListener { _, enabled ->
                settings.setCaptureEnabled(enabled)
                connectionStatus.text = if (enabled) {
                    "Capture enabled for notifications posted from now on"
                } else {
                    "Capture disabled"
                }
            }
        }
        content.addView(captureSwitch)

        label(content, "Windows Server URL")
        serverUrl = EditText(this).apply {
            hint = "https://your-pc.ts.net:10000"
            setText(settings.serverUrl)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        content.addView(serverUrl, matchWidth())

        label(content, "X-Assistant-Token")
        token = EditText(this).apply {
            hint = "Stored only in this app's private settings"
            setText(settings.token)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        content.addView(token, matchWidth())

        content.addView(Button(this).apply {
            text = "Save Settings"
            setOnClickListener {
                saveSettings()
                connectionStatus.text = "Settings saved"
                WorkUploadScheduler(this@MainActivity).enqueue()
            }
        })
        content.addView(Button(this).apply {
            text = "Connection Test"
            setOnClickListener { testConnection() }
        })
        connectionStatus = label(content, "Connection: not tested")
        pendingCount = label(content, "Pending: —")
        uploadedCount = label(content, "Uploaded: —")

        return ScrollView(this).apply { addView(content) }
    }

    private fun saveSettings() {
        settings.serverUrl = serverUrl.text.toString()
        settings.token = token.text.toString()
    }

    private fun testConnection() {
        saveSettings()
        connectionStatus.text = "Connection: testing"
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { HttpMessageSender(settings).testConnection() }
            }
            connectionStatus.text = result.fold(
                onSuccess = { "Connection: success" },
                onFailure = { "Connection: ${it.message}" },
            )
            refreshCounts()
        }
    }

    private fun refreshStatus() {
        val component = ComponentName(this, NotificationCaptureService::class.java)
        val manager = getSystemService(NotificationManager::class.java)
        val enabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            manager.isNotificationListenerAccessGranted(component)
        } else {
            Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
                ?.contains(component.flattenToString()) == true
        }
        accessStatus.text = "Notification Access: ${if (enabled) "enabled" else "disabled"}"
        captureSwitch.isChecked = settings.captureEnabled
        refreshCounts()
    }

    private fun refreshCounts() {
        scope.launch {
            val dao = QueueDatabaseProvider.get(this@MainActivity).notifications()
            val counts = withContext(Dispatchers.IO) {
                dao.count(STATUS_PENDING) to dao.count(STATUS_UPLOADED)
            }
            pendingCount.text = "Pending: ${counts.first}"
            uploadedCount.text = "Uploaded: ${counts.second}"
        }
    }

    private fun label(parent: LinearLayout, value: String): TextView = TextView(this).also {
        it.text = value
        it.textSize = 16f
        it.setPadding(0, 16, 0, 8)
        parent.addView(it, matchWidth())
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
}
