package org.linuxdo.android.data

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.Cookie
import org.json.JSONObject
import org.linuxdo.android.Config

/**
 * 会话 Cookie 的加密持久化,同时负责与 WebView CookieManager 双向同步。
 *
 * 约定:WebView 的 CookieManager 是"现场真相";只要它非空就以它为准并回写加密存储,
 * 只有它为空(例如 WebView 数据被清)时才用加密存储恢复。
 * 涉及 CookieManager 的方法请在主线程调用。
 */
class SessionStore(context: Context) {
    private val prefs: SharedPreferences = createPrefs(context)

    fun storedCookies(): Map<String, String> {
        val raw = prefs.getString(KEY_COOKIES, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { json.getString(it) }
        }.getOrDefault(emptyMap())
    }

    fun savedAtMillis(): Long = prefs.getLong(KEY_SAVED_AT, 0L)

    fun userAgentOrNull(): String? = prefs.getString(KEY_USER_AGENT, null)

    fun saveUserAgent(userAgent: String) {
        prefs.edit().putString(KEY_USER_AGENT, userAgent).apply()
    }

    /**
     * 以 WebView 现场 Cookie 为准同步到加密存储,返回当前有效的 Cookie。
     *
     * **合并,不是整份替换。** WebView 里缺某个 Cookie 不等于服务端撤销了它 ——
     * Cloudflare 会给匿名请求也种上 __cf_bm/cf_clearance,所以"WebView 非空"完全可能是
     * "只剩 CF 的 Cookie、_t 已经丢了"(系统清理 WebView 存储、进程被杀、过挑战都可能)。
     * 整份覆盖会把存储里完好的 _t 抹掉,而那是最后一份备份,抹掉就真掉登录且不可恢复。
     * 真正的撤销走 applyResponseCookies 的过期分支,或 clear()/clearSessionCookie()。
     */
    fun syncFromWebView(): Map<String, String> {
        val web = parseCookieHeader(CookieManager.getInstance().getCookie(Config.BASE_URL))
        val stored = storedCookies()
        if (web.isEmpty()) return stored
        val merged = stored + web
        if (merged != stored) save(merged)
        return merged
    }

    /**
     * 把加密存储里有、而 WebView 里缺的 Cookie 补回 WebView。
     *
     * 不能只在 WebView 完全为空时才恢复:CF 的 Cookie 会让 WebView 看起来"非空",
     * 而真正要紧的 _t 可能已经没了。只补缺失项,WebView 里已有的不动 —— 那些更新。
     *
     * [exclude] 用于排除本次不该回灌的 Cookie。登录链路要排除会话凭据:
     * 一枚失效的旧 _t 被灌回去,会让 Discourse 在已有会话状态下处理 /session.json。
     */
    fun restoreMissingToWebView(exclude: Set<String> = emptySet()) {
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        val stored = storedCookies()
        if (stored.isEmpty()) return
        val web = parseCookieHeader(manager.getCookie(Config.BASE_URL))
        val missing = stored.filterKeys { key ->
            key !in web && key !in exclude && !key.equals(Config.CF_CLEARANCE_COOKIE, ignoreCase = true) && !key.startsWith("__cf") && !key.startsWith("_cf")
        }
        if (missing.isEmpty()) return
        missing.forEach { (name, value) ->
            manager.setCookie(Config.BASE_URL, "$name=$value; Domain=${Config.HOST}; Path=/; Secure")
        }
        manager.flush()
    }

    /**
     * 只丢掉会话凭据,保留 CF 等其他 Cookie。
     *
     * 服务端明确判定未登录时才调用。合并式同步会一直保着 _t,不主动清掉的话每次启动都会
     * 拿这个已经失效的 token 白跑一次 /session/current,还可能被重新塞回 WebView。
     */
    fun clearSessionCookie() {
        save(storedCookies() - Config.SESSION_COOKIE)
        CookieManager.getInstance().apply {
            setCookie(Config.BASE_URL, "${Config.SESSION_COOKIE}=; Max-Age=0; Domain=${Config.HOST}; Path=/")
            flush()
        }
    }

    /**
     * 清除一个指定 Cookie，供 CF 重新验证前淘汰可能失效的 clearance 使用。
     * 返回清除后 WebView 里是否已经不存在该 Cookie。
     *
     * Chromium 删除 Cookie 的判定要求属性匹配上原件,只写 Path/Domain/Secure 覆盖不到
     * cf_clearance 这种 `SameSite=None; Secure` 的 Cookie —— 更不用说 CF 可能额外下发的
     * Partitioned(CHIPS) 副本,它存在独立的分区 jar 里,删除头必须同带 Secure+Partitioned
     * 才能命中。漏掉这些属性时删除会静默失败,旧 clearance 继续留在 WebView 里,
     * CF 随后直接放行回源(不出盾),于是整条重新验证流程拿着一枚已被判废的凭据空转。
     */
    fun clearCookie(name: String): Boolean {
        save(storedCookies() - name)
        val manager = CookieManager.getInstance()
        // CF 可能以 host-only、linux.do 或 .linux.do 三种形式写入 Cookie,
        // 每种形式又要覆盖上述四类属性组合。
        val domains = listOf(null, Config.HOST, ".${Config.HOST}")
        val attributes = listOf(
            "",
            "; Secure",
            "; Secure; SameSite=None",
            "; Secure; SameSite=None; Partitioned",
        )
        domains.forEach { domain ->
            val domainPart = domain?.let { "; Domain=$it" }.orEmpty()
            attributes.forEach { attribute ->
                manager.setCookie(
                    Config.BASE_URL,
                    "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/$domainPart$attribute",
                )
            }
        }
        manager.flush()
        return parseCookieHeader(manager.getCookie(Config.BASE_URL))[name].isNullOrBlank()
    }

    /** 应用 OkHttp 响应里的 Set-Cookie,同时写入加密存储与 WebView,保证两边一致。 */
    fun applyResponseCookies(cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val manager = CookieManager.getInstance()
        val merged = syncFromWebView().toMutableMap()
        val now = System.currentTimeMillis()
        cookies.forEach { cookie ->
            if (cookie.expiresAt < now) {
                merged.remove(cookie.name)
                manager.setCookie(
                    Config.BASE_URL,
                    "${cookie.name}=; Max-Age=0; Domain=${Config.HOST}; Path=/",
                )
            } else {
                merged[cookie.name] = cookie.value
                manager.setCookie(Config.BASE_URL, cookie.toString())
            }
        }
        manager.flush()
        save(merged)
    }

    fun hasSessionCookie(): Boolean = syncFromWebView().containsKey(Config.SESSION_COOKIE)

    fun clear() {
        prefs.edit().remove(KEY_COOKIES).remove(KEY_SAVED_AT).apply()
        val manager = CookieManager.getInstance()
        manager.removeAllCookies(null)
        manager.flush()
    }

    private fun save(cookies: Map<String, String>) {
        prefs.edit()
            .putString(KEY_COOKIES, JSONObject(cookies as Map<*, *>).toString())
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    fun homeFeed(): String? = prefs.getString(KEY_HOME_FEED, null)

    fun saveHomeFeed(feed: String) {
        prefs.edit().putString(KEY_HOME_FEED, feed).apply()
    }

    fun savedLogin(): String? = prefs.getString(KEY_SAVED_LOGIN, null)

    fun savedPassword(): String? = if (isRememberPassword()) prefs.getString(KEY_SAVED_PASSWORD, null) else null

    fun isRememberPassword(): Boolean = prefs.getBoolean(KEY_REMEMBER_PASSWORD, false)

    fun saveCredentials(login: String, password: String, remember: Boolean) {
        val editor = prefs.edit()
        editor.putString(KEY_SAVED_LOGIN, login)
        editor.putBoolean(KEY_REMEMBER_PASSWORD, remember)
        if (remember) {
            editor.putString(KEY_SAVED_PASSWORD, password)
        } else {
            editor.remove(KEY_SAVED_PASSWORD)
        }
        editor.apply()
    }

    companion object {
        private const val PREFS_NAME = "session_secure"
        private const val KEY_COOKIES = "cookies"
        private const val KEY_SAVED_AT = "saved_at"
        private const val KEY_USER_AGENT = "user_agent"
        private const val KEY_HOME_FEED = "home_feed"
        private const val KEY_SAVED_LOGIN = "saved_login"
        private const val KEY_SAVED_PASSWORD = "saved_password"
        private const val KEY_REMEMBER_PASSWORD = "remember_password"

        fun parseCookieHeader(raw: String?): Map<String, String> =
            raw.orEmpty().split(";").mapNotNull { part ->
                val index = part.indexOf('=')
                if (index <= 0) null else part.substring(0, index).trim() to part.substring(index + 1).trim()
            }.toMap()

        fun toCookieHeader(cookies: Map<String, String>): String =
            cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

        private fun createPrefs(context: Context): SharedPreferences {
            fun build(): SharedPreferences {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                return EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }
            return try {
                build()
            } catch (error: Exception) {
                // Keystore 密钥失效(如恢复备份/重装)会导致无法解密,清掉旧文件重新开始
                context.deleteSharedPreferences(PREFS_NAME)
                build()
            }
        }
    }
}
