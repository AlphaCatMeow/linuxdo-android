package org.linuxdo.android.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ReadingHistoryEntry(
    val topicId: Long,
    val title: String,
    val lastSeenAt: Long,
    val lastSeenPostNumber: Int = 0,
    val authorUsername: String? = null,
    val avatarTemplate: String? = null,
    val categoryName: String? = null,
    val categoryColor: String? = null,
    val totalReplies: Int = 0,
)

class HistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile
    private var currentUserId: Long = 0L

    @Synchronized
    fun switchUser(userId: Long) {
        currentUserId = maxOf(0L, userId)
    }

    private fun storageKey(userId: Long): String =
        if (userId > 0L) "${KEY_ENTRIES}_$userId" else KEY_ENTRIES

    @Synchronized
    fun entries(userId: Long = currentUserId): List<ReadingHistoryEntry> {
        val key = storageKey(userId)
        val raw = preferences.getString(key, null)
        if (raw == null && userId > 0L) {
            val legacy = preferences.getString(KEY_ENTRIES, null)
            if (!legacy.isNullOrBlank()) {
                preferences.edit {
                    putString(key, legacy)
                    remove(KEY_ENTRIES)
                }
                return runCatching {
                    json.decodeFromString<List<ReadingHistoryEntry>>(legacy)
                }.getOrDefault(emptyList()).sortedByDescending { it.lastSeenAt }
            }
        }
        return runCatching {
            json.decodeFromString<List<ReadingHistoryEntry>>(raw.orEmpty())
        }.getOrDefault(emptyList()).sortedByDescending { it.lastSeenAt }
    }

    @Synchronized
    fun record(
        topicId: Long,
        title: String,
        postNumber: Int = 0,
        authorUsername: String? = null,
        avatarTemplate: String? = null,
        categoryName: String? = null,
        categoryColor: String? = null,
        totalReplies: Int = 0,
        userId: Long = currentUserId,
    ) {
        if (topicId <= 0L || title.isBlank()) return
        val current = entries(userId).toMutableList()
        val previous = current.firstOrNull { it.topicId == topicId }
        current.removeAll { it.topicId == topicId }
        current.add(
            0,
            ReadingHistoryEntry(
                topicId = topicId,
                title = title,
                lastSeenAt = System.currentTimeMillis(),
                lastSeenPostNumber = maxOf(previous?.lastSeenPostNumber ?: 0, postNumber),
                authorUsername = authorUsername ?: previous?.authorUsername,
                avatarTemplate = avatarTemplate ?: previous?.avatarTemplate,
                categoryName = categoryName ?: previous?.categoryName,
                categoryColor = categoryColor ?: previous?.categoryColor,
                totalReplies = if (totalReplies > 0) totalReplies else (previous?.totalReplies ?: 0),
            ),
        )
        val raw = json.encodeToString(current.take(MAX_ENTRIES))
        preferences.edit { putString(storageKey(userId), raw) }
    }

    @Synchronized
    fun clear(userId: Long = currentUserId) {
        preferences.edit { remove(storageKey(userId)) }
    }

    private companion object {
        const val PREFERENCES = "reading_history"
        const val KEY_ENTRIES = "entries"
        const val MAX_ENTRIES = 500
    }
}
