package org.linuxdo.android.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class DraftStore(context: Context) {
    private val preferences = encryptedPreferences(context)

    fun get(userId: Long, key: String): String? =
        if (userId <= 0L || key.isBlank()) null else preferences.getString(storageKey(userId, key), null)

    fun put(userId: Long, key: String, text: String) {
        if (userId <= 0L || key.isBlank()) return
        preferences.edit().apply {
            if (text.isBlank()) remove(storageKey(userId, key))
            else putString(storageKey(userId, key), text)
        }.apply()
    }

    fun clear(userId: Long) {
        if (userId <= 0L) return
        preferences.edit().apply {
            preferences.all.keys.filter { it.startsWith("$userId:") }.forEach { key -> remove(key) }
        }.apply()
    }

    private fun storageKey(userId: Long, key: String) = "$userId:$key"

    private companion object {
        const val PREFS_NAME = "composer_drafts_secure"

        fun encryptedPreferences(context: Context) = runCatching {
            createEncryptedPreferences(context)
        }.getOrElse {
            context.deleteSharedPreferences(PREFS_NAME)
            createEncryptedPreferences(context)
        }

        private fun createEncryptedPreferences(context: Context) = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
