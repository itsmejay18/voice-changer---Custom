package com.vicechanger.app.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * The only persistence primitive in the app. Keeping it behind an interface means every
 * store (settings, custom presets) can be exercised in plain JVM unit tests with an
 * in-memory implementation, with no Android framework involved.
 */
interface KeyValueStore {
    fun getString(key: String, defaultValue: String? = null): String?
    fun putString(key: String, value: String?)
    fun getFloat(key: String, defaultValue: Float): Float
    fun putFloat(key: String, value: Float)
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getInt(key: String, defaultValue: Int): Int
    fun putInt(key: String, value: Int)
    fun remove(key: String)
    fun contains(key: String): Boolean
}

class SharedPrefsStore(context: Context) : KeyValueStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun getString(key: String, defaultValue: String?): String? =
        prefs.getString(key, defaultValue)

    override fun putString(key: String, value: String?) = prefs.edit { putString(key, value) }

    override fun getFloat(key: String, defaultValue: Float): Float = prefs.getFloat(key, defaultValue)

    override fun putFloat(key: String, value: Float) = prefs.edit { putFloat(key, value) }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        prefs.getBoolean(key, defaultValue)

    override fun putBoolean(key: String, value: Boolean) = prefs.edit { putBoolean(key, value) }

    override fun getInt(key: String, defaultValue: Int): Int = prefs.getInt(key, defaultValue)

    override fun putInt(key: String, value: Int) = prefs.edit { putInt(key, value) }

    override fun remove(key: String) = prefs.edit { remove(key) }

    override fun contains(key: String): Boolean = prefs.contains(key)

    companion object {
        const val FILE_NAME = "vice_changer_settings"
    }
}

/** Generic helpers used by the codecs so no store implementation repeats the parsing. */
object StoreValues {
    fun parseFloat(raw: String?, fallback: Float): Float =
        raw?.toFloatOrNull() ?: fallback

    fun parseInt(raw: String?, fallback: Int): Int =
        raw?.toIntOrNull() ?: fallback

    fun parseBoolean(raw: String?, fallback: Boolean): Boolean = when (raw) {
        "true" -> true
        "false" -> false
        else -> fallback
    }
}
