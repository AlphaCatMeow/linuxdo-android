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

    @Volatile
    private var currentUserId: Long = 0L

    private fun seenKey(userId: Long = currentUserId) =
        if (userId > 0L) "${KEY_SEEN_TOPICS}_$userId" else KEY_SEEN_TOPICS

    private fun postCountsKey(userId: Long = currentUserId) =
        if (userId > 0L) "${KEY_SEEN_POST_COUNTS}_$userId" else KEY_SEEN_POST_COUNTS

    private fun lastPostsKey(userId: Long = currentUserId) =
        if (userId > 0L) "${KEY_LAST_SEEN_POSTS}_$userId" else KEY_LAST_SEEN_POSTS

    private val seen: LinkedHashSet<Long> = linkedSetOf()
    private val seenPostsCount: LinkedHashMap<Long, Int> = linkedMapOf()
    private val lastSeenPostNumbers: LinkedHashMap<Long, Int> = linkedMapOf()

    init {
        synchronized(seen) {
            synchronized(lastSeenPostNumbers) {
                loadForUser(currentUserId)
            }
        }
    }

    private fun loadForUser(userId: Long) {
        val sKey = seenKey(userId)
        val cKey = postCountsKey(userId)
        val lKey = lastPostsKey(userId)

        var seenRaw = prefs.getString(sKey, null)
        var countRaw = prefs.getString(cKey, null)
        var lastPostsRaw = prefs.getString(lKey, null)

        // 迁移旧版未带 userId 的全局数据
        if (seenRaw == null && userId > 0L) {
            val legacySeen = prefs.getString(KEY_SEEN_TOPICS, null)
            if (!legacySeen.isNullOrBlank()) {
                seenRaw = legacySeen
                countRaw = prefs.getString(KEY_SEEN_POST_COUNTS, null)
                lastPostsRaw = prefs.getString(KEY_LAST_SEEN_POSTS, null)
                prefs.edit {
                    putString(sKey, seenRaw)
                    putString(cKey, countRaw)
                    putString(lKey, lastPostsRaw)
                    remove(KEY_SEEN_TOPICS)
                    remove(KEY_SEEN_POST_COUNTS)
                    remove(KEY_LAST_SEEN_POSTS)
                }
            }
        }

        seen.clear()
        seenRaw?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.let(seen::addAll)

        seenPostsCount.clear()
        countRaw?.split(',')
            ?.forEach { entry ->
                val parts = entry.split(':')
                if (parts.size == 2) {
                    val id = parts[0].trim().toLongOrNull()
                    val count = parts[1].trim().toIntOrNull()
                    if (id != null && count != null && count > 0) {
                        seenPostsCount[id] = count
                    }
                }
            }

        lastSeenPostNumbers.clear()
        lastPostsRaw?.split(',')
            ?.forEach { entry ->
                val parts = entry.split(':')
                if (parts.size == 2) {
                    val id = parts[0].trim().toLongOrNull()
                    val postNumber = parts[1].trim().toIntOrNull()
                    if (id != null && id > 0L && postNumber != null && postNumber > 0) {
                        lastSeenPostNumbers[id] = postNumber
                    }
                }
            }
    }

    fun switchUser(userId: Long) {
        val targetId = maxOf(0L, userId)
        if (currentUserId == targetId) return
        synchronized(seen) {
            synchronized(lastSeenPostNumbers) {
                currentUserId = targetId
                loadForUser(targetId)
            }
        }
    }

    @Volatile
    var syncReadProgress: Boolean = prefs.getBoolean(KEY_SYNC_READ_PROGRESS, true)
        private set

    fun setSyncReadProgress(enabled: Boolean) {
        if (syncReadProgress == enabled) return
        syncReadProgress = enabled
        prefs.edit { putBoolean(KEY_SYNC_READ_PROGRESS, enabled) }
    }

    fun seenTopicIds(): Set<Long> = synchronized(seen) { seen.toSet() }

    fun isSeen(topicId: Long): Boolean = synchronized(seen) { topicId in seen }

    fun getLastSeenPost(topicId: Long): Int? = synchronized(lastSeenPostNumbers) {
        lastSeenPostNumbers[topicId]
    }

    fun recordLastSeenPost(topicId: Long, postNumber: Int) {
        if (topicId <= 0L || postNumber <= 0) return
        val (serialized, key) = synchronized(lastSeenPostNumbers) {
            val previous = lastSeenPostNumbers[topicId] ?: 0
            if (postNumber <= previous) return
            lastSeenPostNumbers.remove(topicId)
            lastSeenPostNumbers[topicId] = postNumber
            while (lastSeenPostNumbers.size > MAX_SEEN_TOPICS) {
                lastSeenPostNumbers.remove(lastSeenPostNumbers.keys.first())
            }
            lastSeenPostNumbers.entries.joinToString(",") { "${it.key}:${it.value}" } to lastPostsKey()
        }
        prefs.edit { putString(key, serialized) }
    }

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
        val (sKey, cKey) = synchronized(seen) {
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
            seenKey() to postCountsKey()
        }
        prefs.edit {
            putString(sKey, seenCsv)
            putString(cKey, countsCsv)
        }
        return changed
    }

    private companion object {
        const val PREFS_NAME = "reading_preferences"
        const val KEY_SEEN_TOPICS = "seen_topic_ids"
        const val KEY_SEEN_POST_COUNTS = "seen_topic_post_counts"
        const val KEY_LAST_SEEN_POSTS = "seen_topic_last_posts"
        const val KEY_SYNC_READ_PROGRESS = "sync_read_progress"
        const val MAX_SEEN_TOPICS = 2_000
    }
}
