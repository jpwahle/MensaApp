package com.lkaesberg.mensaapp

import android.content.Context
import com.lkaesberg.mensaapp.notifications.AndroidNotificationContext
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

/**
 * Returns the SharedPreferences-backed store when the activity has attached a
 * Context, otherwise an in-memory map-backed Settings so Compose Preview /
 * unit tests don't crash. The real app always reaches the SharedPreferences
 * path because MainActivity.onCreate calls [AndroidNotificationContext.attach]
 * before composing [App].
 */
actual fun createAppSettings(): Settings {
    val ctx = AndroidNotificationContext.contextOrNull() ?: return InMemorySettings()
    return SharedPreferencesSettings(ctx.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE))
}

/** Tiny in-memory [Settings] used as a preview/test fallback. Not thread-safe. */
private class InMemorySettings : Settings {
    private val store = mutableMapOf<String, Any?>()
    override val keys: Set<String> get() = store.keys.toSet()
    override val size: Int get() = store.size
    override fun clear() { store.clear() }
    override fun remove(key: String) { store.remove(key) }
    override fun hasKey(key: String): Boolean = store.containsKey(key)
    override fun putInt(key: String, value: Int) { store[key] = value }
    override fun getInt(key: String, defaultValue: Int): Int = store[key] as? Int ?: defaultValue
    override fun getIntOrNull(key: String): Int? = store[key] as? Int
    override fun putLong(key: String, value: Long) { store[key] = value }
    override fun getLong(key: String, defaultValue: Long): Long = store[key] as? Long ?: defaultValue
    override fun getLongOrNull(key: String): Long? = store[key] as? Long
    override fun putString(key: String, value: String) { store[key] = value }
    override fun getString(key: String, defaultValue: String): String = store[key] as? String ?: defaultValue
    override fun getStringOrNull(key: String): String? = store[key] as? String
    override fun putFloat(key: String, value: Float) { store[key] = value }
    override fun getFloat(key: String, defaultValue: Float): Float = store[key] as? Float ?: defaultValue
    override fun getFloatOrNull(key: String): Float? = store[key] as? Float
    override fun putDouble(key: String, value: Double) { store[key] = value }
    override fun getDouble(key: String, defaultValue: Double): Double = store[key] as? Double ?: defaultValue
    override fun getDoubleOrNull(key: String): Double? = store[key] as? Double
    override fun putBoolean(key: String, value: Boolean) { store[key] = value }
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = store[key] as? Boolean ?: defaultValue
    override fun getBooleanOrNull(key: String): Boolean? = store[key] as? Boolean
}
