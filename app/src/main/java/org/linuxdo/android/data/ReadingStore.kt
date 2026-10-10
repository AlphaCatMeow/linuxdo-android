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

    /** 记录各已读话题在本地最后点阅时的总楼层数(postsCount)，用于「未读」列表判断是否有新增楼层。 */
    private val seenPostsCount: LinkedHashMap<Long, Int> = linkedMapOf<Long, Int>().apply {
        prefs.getString(KEY_SEEN_POST_COUNTS, null)
            ?.split(',')
            ?.forEach { entry ->
                val parts = entry.split(':')
                if (parts.size == 2) {
                    val id = parts[0].trim().toLongOrNull()
                    val count = parts[1].trim().toIntOrNull()
                    if (id != null && count != null && count > 0) {
                        put(id, count)
                    }
                }
            }
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

    /**
     * 判断话题在「未读」列表中是否已在本地读到最新楼层、应当排除：
     * - 未在本地点开过 -> false（保留展示）
     * - 已点开过，且当前总楼层数 <= 本地已读记录楼层数（或旧记录未存楼层数） -> true（排除）
     * - 已点开过，但服务端总楼层数增长超过了本地记录 -> false（有新回复，重新展示）
     */
    fun isUnreadExcluded(topicId: Long, currentPostsCount: Int): Boolean = synchronized(seen) {
        if (topicId !in seen) return false
        val recorded = seenPostsCount[topicId] ?: 0
        recorded <= 0 || currentPostsCount <= recorded
    }

    /** 返回 true 表示这次是新标记已读或更新了已读楼层上限，调用方可据此刷新界面。 */
    fun markSeen(topicId: Long, postsCount: Int = 0): Boolean {
        if (topicId <= 0L) return false
        var changed = false
        var seenCsv = ""
        var countsCsv = ""
        synchronized(seen) {
            // 重新 add 一次把它挪到队尾,保证活跃话题不会被当成最早的淘汰掉。
            val fresh = seen.remove(topicId).not()
            seen.add(topicId)
            val prevCount = seenPostsCount[topicId] ?: 0
            val countUpdated = postsCount > prevCount
            if (postsCount > 0) {
                seenPostsCount[topicId] = maxOf(prevCount, postsCount)
            }
            while (seen.size > MAX_SEEN_TOPICS) {
                val oldest = seen.firstOrNull() ?: break
                seen.remove(oldest)
                seenPostsCount.remove(oldest)
            }
            changed = fresh || countUpdated
            seenCsv = seen.joinToString(",")
            countsCsv = seenPostsCount.entries.joinToString(",") { "${it.key}:${it.value}" }
        }
        prefs.edit {
            putString(KEY_SEEN_TOPICS, seenCsv)
            putString(KEY_SEEN_POST_COUNTS, countsCsv)
        }
        return changed
    }

    private companion object {
        const val PREFS_NAME = "reading_preferences"
        const val KEY_SEEN_TOPICS = "seen_topic_ids"
        const val KEY_SEEN_POST_COUNTS = "seen_topic_post_counts"
        const val KEY_SYNC_READ_PROGRESS = "sync_read_progress"
        const val MAX_SEEN_TOPICS = 2_000
    }
}
