package com.tianpl.aiassistant

import android.content.Context
import java.util.UUID

interface CaptureSettings {
    val captureEnabled: Boolean
    val enabledSince: Long
}

class AppSettings(context: Context) : CaptureSettings {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    override val captureEnabled: Boolean
        get() = preferences.getBoolean(KEY_CAPTURE_ENABLED, false)

    override val enabledSince: Long
        get() = preferences.getLong(KEY_ENABLED_SINCE, Long.MAX_VALUE)

    var serverUrl: String
        get() = preferences.getString(KEY_SERVER_URL, "").orEmpty()
        set(value) = preferences.edit().putString(KEY_SERVER_URL, value.trim()).apply()

    var token: String
        get() = preferences.getString(KEY_TOKEN, "").orEmpty()
        set(value) = preferences.edit().putString(KEY_TOKEN, value).apply()

    val deviceId: String
        get() {
            val existing = preferences.getString(KEY_DEVICE_ID, null)
            if (existing != null) return existing
            return UUID.randomUUID().toString().also {
                preferences.edit().putString(KEY_DEVICE_ID, it).commit()
            }
        }

    fun setCaptureEnabled(enabled: Boolean, now: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putBoolean(KEY_CAPTURE_ENABLED, enabled)
            .putLong(KEY_ENABLED_SINCE, if (enabled) now else Long.MAX_VALUE)
            .commit()
    }

    private companion object {
        const val KEY_CAPTURE_ENABLED = "capture_enabled"
        const val KEY_ENABLED_SINCE = "enabled_since"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_TOKEN = "token"
        const val KEY_DEVICE_ID = "device_id"
    }
}
