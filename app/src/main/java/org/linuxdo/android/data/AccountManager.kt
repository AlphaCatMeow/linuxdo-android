package org.linuxdo.android.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.linuxdo.android.Config

@Serializable
data class AccountSession(
    val userId: Long,
    val username: String,
    val name: String? = null,
    val avatarTemplate: String? = null,
    val animatedAvatar: String? = null,
    val trustLevel: Int? = null,
    val token: String,
    val forumSession: String? = null,
    val lastActiveAt: Long = System.currentTimeMillis(),
    /** 非活跃账号最近一次隔离会话保活成功时刻；不等同于用户实际活跃时间。 */
    val lastKeepAliveAt: Long = 0L,
    /** 最近一次保活尝试时刻，用于限制失效账号在前台反复探测。 */
    val lastKeepAliveAttemptAt: Long = 0L,
) {
    /** _forum_session 可缺省，_t 和合法用户 ID 是本地热挂载的必要条件。 */
    val hasUsableCredentials: Boolean
        get() = userId > 0L && token.isNotBlank()
}

@Serializable
private data class StoredAccounts(
    val activeUserId: Long = 0L,
    val accounts: List<AccountSession> = emptyList(),
)

class AccountManager(context: Context) {
    private val preferences: SharedPreferences = encryptedPreferences(context)
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile
    private var memoryCache: StoredAccounts? = null

    data class AccountSnapshot(
        val accounts: List<AccountSession>,
        val activeUserId: Long,
    )

    @Synchronized
    fun snapshot(): AccountSnapshot {
        val current = read()
        return AccountSnapshot(
            accounts = current.accounts.sortedByDescending { it.lastActiveAt },
            activeUserId = current.activeUserId,
        )
    }

    @Synchronized
    fun accounts(): List<AccountSession> = snapshot().accounts

    @Synchronized
    fun activeUserId(): Long = read().activeUserId

    @Synchronized
    fun account(userId: Long): AccountSession? = read().accounts.firstOrNull { it.userId == userId }

    @Synchronized
    fun canAddAccount(): Boolean = read().accounts.size < MAX_ACCOUNTS

    @Synchronized
    fun remember(
        userId: Long,
        username: String,
        cookies: Map<String, String>,
        user: DiscourseUser? = null,
    ): AccountSession? {
        val token = cookies[Config.SESSION_COOKIE]?.takeIf(String::isNotBlank) ?: return null
        if (userId <= 0L || username.isBlank()) return null
        val current = read()
        val existing = current.accounts.firstOrNull { it.userId == userId }
        if (existing == null && current.accounts.size >= MAX_ACCOUNTS) {
            return null
        }
        val animated = user?.animatedAvatar?.takeIf(::isAnimatedAvatarPath)
            ?: AvatarCache.get(username)?.takeIf(::isAnimatedAvatarPath)
            ?: existing?.animatedAvatar?.takeIf(::isAnimatedAvatarPath)
        val account = AccountSession(
            userId = userId,
            username = username,
            name = user?.name ?: existing?.name,
            avatarTemplate = user?.avatarTemplate ?: existing?.avatarTemplate,
            animatedAvatar = animated,
            trustLevel = user?.trustLevel ?: existing?.trustLevel,
            token = token,
            forumSession = cookies[FORUM_SESSION_COOKIE] ?: existing?.forumSession,
            lastActiveAt = System.currentTimeMillis(),
            lastKeepAliveAt = existing?.lastKeepAliveAt ?: 0L,
            lastKeepAliveAttemptAt = existing?.lastKeepAliveAttemptAt ?: 0L,
        )
        write(current.copy(activeUserId = userId, accounts = (current.accounts.filterNot { it.userId == userId } + account)))
        return account
    }

    @Synchronized
    fun setActive(userId: Long) {
        val current = read()
        val account = current.accounts.firstOrNull { it.userId == userId } ?: return
        write(current.copy(
            activeUserId = userId,
            accounts = current.accounts.filterNot { it.userId == userId } + account.copy(lastActiveAt = System.currentTimeMillis()),
        ))
    }

    /**
     * 仅更新非活跃账号的保活结果。不会改动当前活动槽位，也不会把保活视为用户活跃操作。
     */
    @Synchronized
    fun recordKeepAlive(
        userId: Long,
        token: String? = null,
        forumSession: String? = null,
        timestamp: Long = System.currentTimeMillis(),
    ): AccountSession? {
        val current = read()
        val account = current.accounts.firstOrNull { it.userId == userId } ?: return null
        val updated = account.copy(
            token = token?.takeIf(String::isNotBlank) ?: account.token,
            forumSession = forumSession?.takeIf(String::isNotBlank) ?: account.forumSession,
            lastKeepAliveAt = timestamp,
            lastKeepAliveAttemptAt = timestamp,
        )
        write(current.copy(accounts = current.accounts.filterNot { it.userId == userId } + updated))
        return updated
    }

    @Synchronized
    fun recordKeepAliveAttempt(userId: Long, timestamp: Long = System.currentTimeMillis()) {
        val current = read()
        val account = current.accounts.firstOrNull { it.userId == userId } ?: return
        write(current.copy(
            accounts = current.accounts.filterNot { it.userId == userId } +
                account.copy(lastKeepAliveAttemptAt = timestamp),
        ))
    }

    @Synchronized
    fun remove(userId: Long) {
        val current = read()
        val nextAccounts = current.accounts.filterNot { it.userId == userId }
        val nextActive = if (current.activeUserId == userId) {
            nextAccounts.firstOrNull()?.userId ?: 0L
        } else {
            current.activeUserId
        }
        write(current.copy(activeUserId = nextActive, accounts = nextAccounts))
    }

    @Synchronized
    fun clear() {
        memoryCache = StoredAccounts()
        preferences.edit { remove(KEY_ACCOUNTS) }
    }

    @Synchronized
    fun updateAnimatedAvatar(username: String, animatedAvatar: String) {
        if (username.isBlank() || animatedAvatar.isBlank() || !isAnimatedAvatarPath(animatedAvatar)) return
        AvatarCache.record(username, animatedAvatar)
        val current = read()
        var changed = false
        val cleanName = username.trim().removePrefix("@")
        val nextAccounts = current.accounts.map { account ->
            if (account.username.equals(cleanName, ignoreCase = true)) {
                if (account.animatedAvatar != animatedAvatar) {
                    changed = true
                    account.copy(animatedAvatar = animatedAvatar)
                } else account
            } else account
        }
        if (changed) {
            write(current.copy(accounts = nextAccounts))
        }
    }

    private fun read(): StoredAccounts {
        memoryCache?.let { return it }
        val loaded = runCatching {
            json.decodeFromString<StoredAccounts>(preferences.getString(KEY_ACCOUNTS, null).orEmpty()).also { stored ->
                stored.accounts.forEach { acc ->
                    if (!acc.animatedAvatar.isNullOrBlank() && isAnimatedAvatarPath(acc.animatedAvatar)) {
                        AvatarCache.record(acc.username, acc.animatedAvatar)
                    }
                }
            }
        }.getOrDefault(StoredAccounts())
        memoryCache = loaded
        return loaded
    }

    private fun write(value: StoredAccounts) {
        memoryCache = value
        preferences.edit { putString(KEY_ACCOUNTS, json.encodeToString(value)) }
    }

    companion object {
        const val MAX_ACCOUNTS = 3
        private const val PREFS_NAME = "account_sessions_secure"
        private const val KEY_ACCOUNTS = "accounts"
        private const val FORUM_SESSION_COOKIE = "_forum_session"

        fun encryptedPreferences(context: Context): SharedPreferences {
            fun create() = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            return runCatching { create() }.getOrElse {
                context.deleteSharedPreferences(PREFS_NAME)
                create()
            }
        }
    }
}
