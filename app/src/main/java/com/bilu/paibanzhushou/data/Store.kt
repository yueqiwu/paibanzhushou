package com.bilu.paibanzhushou.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 本地存储，键名与 uniapp 版本保持一致，便于迁移。
 */
object Store {
    private const val PREF = "paiban_store"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        }
    }

    fun readStringOrNull(key: String): String? {
        if (!::prefs.isInitialized) return null
        return try {
            prefs.getString(key, null)
        } catch (t: Throwable) {
            null
        }
    }

    fun readRaw(key: String, fallback: String = ""): String = readStringOrNull(key) ?: fallback

    fun writeRaw(key: String, value: String) {
        if (!::prefs.isInitialized) return
        try {
            prefs.edit().putString(key, value).apply()
        } catch (t: Throwable) {
        }
    }

    fun remove(key: String) {
        if (!::prefs.isInitialized) return
        try {
            prefs.edit().remove(key).apply()
        } catch (t: Throwable) {
        }
    }
}
