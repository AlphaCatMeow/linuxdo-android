package org.linuxdo.android.net

import android.net.Uri
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Cookie
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.linuxdo.android.Config
import org.linuxdo.android.data.Diagnostics
import org.linuxdo.android.data.SessionStore

/**
 * Discourse User API Key 网页授权认证服务。
 * 流程对齐官方 DiscourseHub 与 FluxDO:
 * 1. 本地生成 2048 位 RSA 密钥对、client_id、nonce。
 * 2. 拼接授权 URL (scopes=one_time_password, auth_redirect=discourse://auth_redirect) 拉起浏览器。
 * 3. 浏览器授权后回调 discourse://auth_redirect?payload=...&oneTimePassword=...。
 * 4. RSA 私钥解密 payload 校验 nonce，解密 oneTimePassword 拿到一次性令牌。
 * 5. 请求 CSRF 后 POST /session/otp/:token 兑换 _t 会话 Cookie，完成登录。
 * 6. 吊销该临时 User API Key (用完即焚)。
 */
class UserApiKeyAuthService(
    private val store: SessionStore,
    private var httpClient: OkHttpClient,
    private val diagnostics: Diagnostics,
    /**
     * CSRF 请求被 Cloudflare 拦下时的静默重验证入口。
     * 网页授权是"外部浏览器授权 → 回到 App 内原生网络栈兑换"两段式，
     * 浏览器里的 cf_clearance 不会共享给 App，兑换第一次几乎必然撞盾；
     * 这里交给 WebView 在后台过一次人机验证，成功后再重放一次请求。
     */
    private val silentRevalidate: suspend (String) -> Boolean = { false },
) {
    /** 动态更新底层的 OkHttpClient（DoH 节点切换或开关变更后调用） */
    fun updateClient(newClient: OkHttpClient) {
        this.httpClient = newClient
    }

    private var pendingPrivateKey: PrivateKey? = null
    private var pendingNonce: String? = null

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 生成 RSA 密钥对并构造网页授权 URL。
     */
    @Synchronized
    fun buildAuthorizeUrl(): String {
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048)
        val keyPair = keyPairGen.generateKeyPair()

        pendingPrivateKey = keyPair.private
        val nonce = UUID.randomUUID().toString()
        pendingNonce = nonce
        val clientId = UUID.randomUUID().toString()

        val publicKeyBytes = keyPair.public.encoded
        val base64Pub = Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP)
        val pem = buildString {
            append("-----BEGIN PUBLIC KEY-----\n")
            base64Pub.chunked(64).forEach { append(it).append("\n") }
            append("-----END PUBLIC KEY-----")
        }

        diagnostics.log("生成 User API Key 授权参数: clientId=$clientId, nonce=$nonce")

        return Uri.parse("${Config.BASE_URL}/user-api-key/new").buildUpon()
            .appendQueryParameter("application_name", "LINUX DO")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("scopes", "one_time_password")
            .appendQueryParameter("public_key", pem)
            .appendQueryParameter("nonce", nonce)
            .appendQueryParameter("auth_redirect", AUTH_REDIRECT)
            .build()
            .toString()
    }

    /**
     * 用户取消授权或重置时清理密钥状态。
     */
    @Synchronized
    fun cancel() {
        pendingPrivateKey = null
        pendingNonce = null
    }

    /**
     * 处理深链回调 discourse://auth_redirect?payload=...&oneTimePassword=...
     */
    suspend fun handleAuthRedirect(uri: Uri) = withContext(Dispatchers.IO) {
        val (privateKey, expectedNonce) = synchronized(this@UserApiKeyAuthService) {
            val key = pendingPrivateKey ?: throw IllegalStateException("未发起过网页授权或授权已过期，请重新尝试")
            val nonce = pendingNonce ?: throw IllegalStateException("授权 nonce 丢失，请重新尝试")
            key to nonce
        }

        val payloadParam = uri.getQueryParameter("payload")
            ?: throw IllegalArgumentException("授权回调缺少 payload 参数")
        val otpParam = uri.getQueryParameter("oneTimePassword")
            ?: throw IllegalArgumentException("授权回调缺少 oneTimePassword 一次性口令")

        // 1. RSA 私钥解密 payload
        val decryptedPayload = decryptRsa(payloadParam, privateKey)
            ?: throw IllegalStateException("授权 payload 解密失败")

        val payloadObj = runCatching { json.parseToJsonElement(decryptedPayload).jsonObject }.getOrNull()
            ?: throw IllegalStateException("授权 payload 不是合法的 JSON 格式")

        val returnedNonce = payloadObj["nonce"]?.jsonPrimitive?.contentOrNull
        if (returnedNonce != expectedNonce) {
            diagnostics.log("授权回调 nonce 不匹配: 期待 $expectedNonce, 实际 $returnedNonce")
            throw IllegalStateException("授权回调验证失败 (nonce 不匹配)")
        }

        val apiKey = payloadObj["key"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("授权回调未包含 API Key")

        // 2. RSA 私钥解密 oneTimePassword
        val otp = decryptRsa(otpParam, privateKey)
            ?: throw IllegalStateException("一次性登录令牌解密失败")

        val cleanOtp = otp.trim()
        if (!cleanOtp.matches(Regex("^[0-9a-fA-F]+$"))) {
            throw IllegalStateException("登录口令格式异常: $cleanOtp")
        }

        diagnostics.log("User API Key 解密成功，正在兑换会话 Cookie")

        // 3. 兑换 OTP 为会话 Cookie (_t)
        redeemOtp(cleanOtp)

        // 4. 用完即焚：吊销该 User API Key
        revokeKey(apiKey)

        // 5. 成功后清除私钥和 nonce
        synchronized(this@UserApiKeyAuthService) {
            pendingPrivateKey = null
            pendingNonce = null
        }
    }

    private fun decryptRsa(base64Cipher: String, privateKey: PrivateKey): String? = runCatching {
        val normalized = base64Cipher.replace("\\s".toRegex(), "")
        val cipherBytes = Base64.decode(normalized, Base64.DEFAULT)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        val decryptedBytes = cipher.doFinal(cipherBytes)
        String(decryptedBytes, Charsets.UTF_8)
    }.getOrNull()

    /**
     * 兑换 OTP：
     * 先取 CSRF 及匹配的 _forum_session，然后不跟随重定向 POST /session/otp/:token，拦截第一跳 302 的 Set-Cookie。
     */
    private suspend fun redeemOtp(otp: String) {
        val csrf = fetchCsrf()
            ?: throw IllegalStateException("获取 CSRF Token 失败，无法完成兑换")

        val otpClient = httpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url("${Config.BASE_URL}/session/otp/$otp")
            .post("".toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("X-CSRF-Token", csrf)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .build()

        var responseCode = 0
        val response = otpClient.newCall(request).execute()
        response.use { resp ->
            responseCode = resp.code
            diagnostics.log("兑换 OTP 响应码: ${resp.code}")
            if (resp.code !in 200..302) {
                val errBody = resp.body?.string()?.take(300).orEmpty()
                diagnostics.log("兑换 OTP 失败响应体: $errBody")
            }
            val parsedCookies = Cookie.parseAll(resp.request.url, resp.headers).toMutableList()
            // 显式提取 Set-Cookie 中的 _t，防止因严格域名匹配丢失
            val rawSetCookies = resp.headers.values("Set-Cookie")
            for (raw in rawSetCookies) {
                val match = Regex("(?:^|;\\s*)_t=([^;]+)").find(raw)
                if (match != null) {
                    val tValue = match.groupValues[1].trim()
                    if (tValue.isNotEmpty() && parsedCookies.none { it.name == Config.SESSION_COOKIE }) {
                        parsedCookies.add(
                            Cookie.Builder()
                                .name(Config.SESSION_COOKIE)
                                .value(tValue)
                                .domain(Config.HOST)
                                .path("/")
                                .secure()
                                .httpOnly()
                                .build()
                        )
                    }
                }
            }

            if (parsedCookies.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    store.applyResponseCookies(parsedCookies)
                }
            }
        }

        // 校验是否拿到会话 Cookie
        val hasSession = withContext(Dispatchers.Main) {
            store.hasSessionCookie()
        }
        if (!hasSession) {
            val errorHint = if (responseCode == 403) "安全防护校验拦截（HTTP 403），请稍后重试"
                else "登录令牌兑换成功但未获取到会话凭证 (_t)，请重试"
            throw IllegalStateException(errorHint)
        }
        diagnostics.log("会话凭证 (_t) 已成功持久化")
    }

    /**
     * 获取 CSRF Token 并同步保存关联会话 Cookie。
     *
     * 首次失败（多为 Cloudflare 403 挑战）不直接判死：先让 WebView 静默过一次盾，
     * 拿到新的 cf_clearance 后再重放一次。两次都拿不到才返回 null。
     */
    private suspend fun fetchCsrf(): String? {
        requestCsrf()?.let { return it }

        val revalidated = runCatching { silentRevalidate("网页授权 CSRF 获取失败") }.getOrDefault(false)
        if (!revalidated) {
            diagnostics.log("CSRF 静默重验证未通过，放弃本次网页授权兑换")
            return null
        }
        diagnostics.log("CSRF 静默重验证已完成，重放请求")
        return requestCsrf()
    }

    /**
     * 单次 CSRF 请求。
     *
     * 端点是 Discourse 标准的 /session/csrf（不带 .json）：带后缀的地址在无会话 Cookie 时
     * 更容易被 301/404 或挑战页顶掉，取不到 JSON 体。Headers 与网页端完全一致，
     * 让服务端按 AJAX 请求处理。
     */
    private suspend fun requestCsrf(): String? {
        // OkHttp 侧读的是加密存储里的 Cookie 快照，必须先与 WebView 现场对齐，
        // 否则刚在静默重验证里拿到的 cf_clearance 根本带不上。
        withContext(Dispatchers.Main) { store.syncFromWebView() }

        val request = Request.Builder()
            .url("${Config.BASE_URL}/session/csrf")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("X-Requested-With", "XMLHttpRequest")
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { resp ->
                val cookies = Cookie.parseAll(resp.request.url, resp.headers)
                if (cookies.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        store.applyResponseCookies(cookies)
                    }
                }
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    diagnostics.log(
                        "CSRF 请求失败: HTTP ${resp.code} " +
                            "cf-mitigated=${resp.header("cf-mitigated") ?: "-"} " +
                            "body=${body.take(120)}",
                    )
                    return@use null
                }
                json.parseToJsonElement(body).jsonObject["csrf"]?.jsonPrimitive?.contentOrNull
            }
        }.getOrNull()
    }

    /**
     * 吊销临时生成的 User API Key，避免无用凭证留存服务端。
     */
    private fun revokeKey(apiKey: String) {
        try {
            val request = Request.Builder()
                .url("${Config.BASE_URL}/user-api-key/revoke")
                .post("".toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("User-Api-Key", apiKey)
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            httpClient.newCall(request).execute().use { resp ->
                diagnostics.log("吊销 User API Key 响应码: ${resp.code}")
            }
        } catch (e: Exception) {
            diagnostics.log("吊销 User API Key 异常 (可忽略): ${e.message}")
        }
    }

    companion object {
        const val AUTH_REDIRECT = "discourse://auth_redirect"
    }
}
