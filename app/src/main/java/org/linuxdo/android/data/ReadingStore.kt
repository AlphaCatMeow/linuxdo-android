package org.linuxdo.android.data

import android.content.Context
import androidx.core.content.edit

/**
 * 阅读状态的本地持久化:已读话题集合 + 是否同步阅读进度到服务端。
 *
 * 默认走"纯净模式" —— 不发任何写请求,靠本地 [markSeen] 让 APP 内的未读小蓝点永久消失。
 * 这样下拉刷新、切分类、重启进程都不会让看过的帖子又冒出蓝点,而网络行为与不记录时完全一致。
 * 用户若需要网页端同步已读或让总结页的阅读数据增长,再手动打开 [syncReadProgress]。
 */
class ReadingStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 已读话题 ID。按写入顺序存成逗号分隔串并定长截断 —— 这是个"最近看过什么"的滚动窗口,
     * 不是完整历史,所以超出容量时丢最早的那些(它们早就滚出列表了)。
     */
    private val seen: LinkedHashSet<Long> = linkedSetOf<Long>().apply {
        prefs.getString(KEY_SEEN_TOPICS, null)
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.let(::addAll)
    }

    @Volatile
    var syncReadProgress: Boolean = prefs.getBoolean(KEY_SYNC_READ_PROGRESS, false)
        private set

    fun setSyncReadProgress(enabled: Boolean) {
        if (syncReadProgress == enabled) return
        syncReadProgress = enabled
        prefs.edit { putBoolean(KEY_SYNC_READ_PROGRESS, enabled) }
    }

    fun seenTopicIds(): Set<Long> = synchronized(seen) { seen.toSet() }

    fun isSeen(topicId: Long): Boolean = synchronized(seen) { topicId in seen }

    /** 返回 true 表示这次确实是新标记的,调用方可据此决定要不要刷界面。 */
    fun markSeen(topicId: Long): Boolean {
        if (topicId <= 0L) return false
        val snapshot = synchronized(seen) {
            // 重新 add 一次把它挪到队尾,保证活跃话题不会被当成最早的淘汰掉。
            val fresh = seen.remove(topicId).not()
            seen.add(topicId)
            while (seen.size > MAX_SEEN_TOPICS) {
                val oldest = seen.firstOrNull() ?: break
                seen.remove(oldest)
            }
            fresh to seen.joinToString(",")
        }
        prefs.edit { putString(KEY_SEEN_TOPICS, snapshot.second) }
        return snapshot.first
    }

    private companion object {
        const val PREFS_NAME = "reading_preferences"
        const val KEY_SEEN_TOPICS = "seen_topic_ids"
        const val KEY_SYNC_READ_PROGRESS = "sync_read_progress"
        const val MAX_SEEN_TOPICS = 2_000
    }
}
