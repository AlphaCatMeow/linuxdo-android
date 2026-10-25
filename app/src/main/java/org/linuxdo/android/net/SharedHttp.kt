package org.linuxdo.android.net

import android.content.Context
import android.webkit.CookieManager
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Cookie
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.linuxdo.android.Config
import org.linuxdo.android.data.SessionStore

/**
 * 全应用共用的 HTTP 客户端。
 *
 * 图片(Coil)和 JSON 请求必须走同一个客户端:cf_clearance 与 UA 绑定,
 * 图片请求若用了 Coil 默认客户端(不同 UA、没有 Cookie),会被 Cloudflare 直接拦掉,
 * 结果就是列表里头像全空。
 *
 * @param store     会话 Cookie 存储
 * @param userAgent 全局统一 UA
 * @param dns          可选的自定义 DNS 实现（注入 DoH 时传入 AdaptiveDohDns 实例；null 表示使用系统默认 DNS）
 * @param dohTransport 可选的原生 DoH + ECH TLS 传输层（注入后替换底层 Socket 与 TLS 握手）
 */
fun buildSharedHttpClient(
    store: SessionStore,
    userAgent: String,
    dns: Dns? = null,
    dohTransport: DohTransport? = null,
    failoverCoordinator: DohFailoverCoordinator? = null,
): OkHttpClient {
    val dispatcher = Dispatcher().apply {
        maxRequests = 64
        maxRequestsPerHost = 16
    }
    val connectionPool = ConnectionPool(16, 5, TimeUnit.MINUTES)
    val builder = OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .connectionPool(connectionPool)
        .connectTimeout(Config.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        // 读超时保持较长:Coil 的图片请求复用同一个客户端,大图需要时间读完。
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)

    if (failoverCoordinator != null) {
        // 应用级故障上报与幂等单次重试拦截器（置于最外层，感知 DNS/连接/TLS 全阶段）
        builder.addInterceptor(DohFailoverInterceptor(failoverCoordinator))
    }

    if (dohTransport != null) {
        // 原生 ECH + TLS 传输配置：注入 DNS、SocketFactory、SSLSocketFactory (Rustls ECH)
        dohTransport.configure(builder)
    } else if (dns != null) {
        builder.dns(dns)
    }

    return builder
        .addInterceptor { chain ->
            val request = chain.request()
            val session = request.tag(ActiveSessionRequest::class.java)
                ?: ActiveSessionRequest(store.sessionGeneration)
            chain.proceed(request.newBuilder().tag(ActiveSessionRequest::class.java, session).build())
        }
        .addInterceptor(SiteImageGateInterceptor)
        .addNetworkInterceptor(
            SessionHeaderInterceptor(
                store,
                userAgent,
                transportLabel = when {
                    dohTransport != null -> "DoH+ECH"
                    dns != null -> "DoH-DNS"
                    else -> "系统网络"
                },
            ),
        )
        .build()
}

/**
 * 结合设置开关以及是否开启 VPN/代理综合判断，解析出当前应走哪个 HTTP 客户端。
 *
 * 判定矩阵：
 * 1. 优先复用 [AppContainer] 的当前活跃客户端（若实例存在）；
 *    - 进程存活时，协调器已持续监听 VPN 状态、用户开关与代次自动故障转移，享有活跃连接池和 ECH 会话复用；
 * 2. 兜底与静态独立唤醒路径：
 *    - 用户开关未开启 DoH (`!sessionStore.isDohEnabled()`) -> 走普通系统网络；
 *    - 检测到 VPN 或系统代理开启 (`networkMonitor.detect().shouldPauseDoh`) -> 自动暂停 DoH，走系统代理网络；
 *    - 开启 DoH 且处于无代理直连网络 -> 必须走原生 DoH + ECH TLS 传输通道（构建失败则降级纯 DNS）。
 */
fun resolveActiveHttpClient(
    context: Context,
    sessionStore: SessionStore,
): OkHttpClient {
    val app = context.applicationContext as? org.linuxdo.android.App
    val container = app?.container
    if (container != null) {
        return container.httpClient
    }

    val userAgent = org.linuxdo.android.AppContainer.resolveUserAgent(context, sessionStore)
    val isDohSwitchOn = sessionStore.isDohEnabled()
    val isVpnOrProxyActive = NetworkStateMonitor(context).detect().shouldPauseDoh
    val forceOverProxy = sessionStore.isDohForceOverProxyEnabled()

    // 综合判断：只有设置开启且（无 VPN/代理直连，或用户开启了代理下保持 DoH）时，才走 DoH + ECH
    val shouldUseDoh = isDohSwitchOn && (!isVpnOrProxyActive || forceOverProxy)

    val transport = if (shouldUseDoh) {
        buildActiveDohTransport(sessionStore)
    } else {
        null
    }

    val dns = if (shouldUseDoh && transport == null) {
        buildDohDns(sessionStore)
    } else {
        null
    }

    return buildSharedHttpClient(
        store = sessionStore,
        userAgent = userAgent,
        dns = dns,
        dohTransport = transport,
    )
}

/**
 * 构建仅用于 DoH 探测与 Bootstrap 连接的底层客户端。
 *
 * 此客户端：
 * - 不注入 DoH（避免递归依赖）
 * - 不注入 Cookie/UA（减少指纹）
 * - 超时参数与探测需求匹配（连接 5s，读 5s）
 *
 * 由 [AppContainer] 在初始化 DoH 时传入 [AdaptiveDohDns] 使用。
 */
fun buildBootstrapClient(): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

/** 给站内请求统一注入固定 UA 与会话 Cookie。 */
private class SessionHeaderInterceptor(
    private val store: SessionStore,
    private val userAgent: String,
    private val transportLabel: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val host = original.url.host
        val isLinuxDoHost = host == Config.HOST || host.endsWith(".${Config.HOST}")
        if (!isLinuxDoHost || !original.url.isHttps) {
            return chain.proceed(original.newBuilder().removeHeader("Cookie").removeHeader("X-CSRF-Token").build())
        }

        val generation = original.tag(ActiveSessionRequest::class.java)?.generation
            ?: store.sessionGeneration
        fun checkSession() {
            if (generation != store.sessionGeneration || store.sessionChanging) {
                throw IOException("会话已切换，请重新操作")
            }
        }
        checkSession()

        val builder = original.newBuilder().header("User-Agent", userAgent)
        val siteReferer = when (host) {
            "credit.linux.do" -> "https://credit.linux.do/home"
            "cdk.linux.do" -> "https://cdk.linux.do/dashboard"
            else -> null
        }
        if (siteReferer != null) {
            if (original.header("Origin") == null) {
                builder.header("Origin", "https://$host")
            }
            if (original.header("Referer") == null) {
                builder.header("Referer", siteReferer)
            }
        } else if (original.header("Referer") == null) {
            builder.header("Referer", "${Config.BASE_URL}/")
        }
        var rawWebCookieHeader = ""
        val cookieHeader = runBlocking(Dispatchers.Main) {
            checkSession()
            rawWebCookieHeader = CookieManager.getInstance().getCookie(original.url.toString()).orEmpty()
            if (host == Config.HOST) SessionStore.toCookieHeader(store.syncFromWebView())
            else rawWebCookieHeader
        }
        val sentClearance = SessionStore.parseCookieHeader(cookieHeader)[Config.CF_CLEARANCE_COOKIE]
        original.tag(ActiveSessionRequest::class.java)?.sentClearanceFp = NetDiagnostics.fingerprint(sentClearance)
        builder.removeHeader("Cookie")
        if (cookieHeader.isNotBlank()) {
            builder.header("Cookie", cookieHeader)
        }
        val outgoing = builder.build()
        val response = chain.proceed(outgoing)
        runCatching { NetDiagnostics.traceOkHttp(chain, outgoing, response, cookieHeader, rawWebCookieHeader, transportLabel) }
        try {
            // 每次重定向都在进入下一跳前落 Cookie；普通 API、图片和 OAuth 共用此入口。
            runBlocking(Dispatchers.Main) {
                checkSession()
                store.applyResponseCookies(
                    Cookie.parseAll(original.url, response.headers),
                    requestUrl = original.url.toString(),
                    requestGeneration = generation,
                    sentCookies = SessionStore.parseCookieHeader(cookieHeader),
                )
            }
            return response
        } catch (error: Exception) {
            response.close()
            throw error
        }
    }
}

data class ActiveSessionRequest(
    val generation: Long,
    @Volatile var sentClearanceFp: String = "-",
)

/**
 * 图片失败是否值得稍后自动重试：门禁拒绝、网络抖动、429/408/5xx。
 * 404、403（受保护附件 / 盾拦截）、解码失败等确定性错误返回 false，交给调用方降级或占位，不去反复撞墙。
 */
fun isTransientImageError(error: Throwable?): Boolean = when (error) {
    is coil.network.HttpException -> error.response.code.let { it == 408 || it == 429 || it >= 500 }
    is IOException -> true
    else -> false
}

/**
 * 等到下一次图片重试的时机：先等图片门禁冷却结束，再叠加指数退避与随机抖动，
 * 避免一屏失败的图在同一刻集体重发，重新把 Cloudflare 计数打满。
 */
suspend fun awaitImageRetrySlot(attempt: Int) {
    val backoffMs = 4_000L shl attempt.coerceIn(0, 3)
    val jitterMs = kotlin.random.Random.nextLong(0L, 3_000L)
    kotlinx.coroutines.delay(maxOf(SiteImageGateInterceptor.cooldownRemainingMs(), backoffMs) + jitterMs)
}

/** 每张图自动重试的上限，超过后停在占位，等用户手动重载或重新进入页面。 */
const val IMAGE_AUTO_RETRY_LIMIT = 2

/**
 * 站内图片（头像 / 字母头像 / 上传图）的独立计量池。
 *
 * Coil 与 API 共用同一个客户端（cf_clearance 绑 UA），但图片原本不过任何限频：
 * 亮屏后列表刷新，一屏几十个新头像会以 maxRequestsPerHost=16 的并发同时打出去，
 * Cloudflare 按 IP 合并计数，直接整批 429 质询，连带 API 一起变危险。
 *
 * - 并发封顶 [MAX_CONCURRENT]，其余在 Coil 的 IO 线程上排队
 * - 任意一张图 429 后进入图片冷却，期间站内图片直接失败（显示占位），不再继续撞墙
 * - 同时通知后台计量池退让；不进全局冷却，前台 API 自己撞到 429 时会再进
 */
internal object SiteImageGateInterceptor : Interceptor {
    /** 门禁主动拒绝（排队超时 / 冷却中）。界面据此判定为瞬时失败，冷却后再重试，而不是降级或放弃。 */
    class ThrottledException(message: String) : IOException(message)

    private const val MAX_CONCURRENT = 4
    private const val ACQUIRE_TIMEOUT_MS = 15_000L
    private const val DEFAULT_COOLDOWN_MS = org.linuxdo.android.data.TopicRepository.RATE_LIMIT_COOLDOWN_MS
    private const val MAX_COOLDOWN_MS = 1 * 60_000L
    private val imagePathPrefixes = listOf("/user_avatar/", "/letter_avatar", "/uploads/", "/images/emoji/")

    private val permits = java.util.concurrent.Semaphore(MAX_CONCURRENT, true)
    @Volatile private var cooldownUntil = 0L

    private fun isSiteImage(url: okhttp3.HttpUrl): Boolean {
        val host = url.host
        if (host != Config.HOST && !host.endsWith(".${Config.HOST}")) return false
        val path = url.encodedPath
        return imagePathPrefixes.any { path.startsWith(it) }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!isSiteImage(request.url)) return chain.proceed(request)
        checkCooldown()
        if (!permits.tryAcquire(ACQUIRE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            throw ThrottledException("站内图片排队超时")
        }
        val response = try {
            if (chain.call().isCanceled()) throw IOException("Canceled")
            checkCooldown()
            chain.proceed(request)
        } finally {
            // 头部到达即释放：头像体积很小，读 body 不必占着并发名额
            permits.release()
        }
        if (response.code == 429) enterCooldown(response)
        return response
    }

    fun cooldownRemainingMs(): Long =
        (cooldownUntil - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    private fun checkCooldown() {
        val remaining = cooldownRemainingMs()
        if (remaining > 0L) throw ThrottledException("站内图片限流冷却中，还剩 ${remaining / 1_000L}s")
    }

    private fun enterCooldown(response: Response) {
        val cfChallenge = response.header("cf-mitigated")?.contains("challenge", ignoreCase = true) == true
        val cooldownMs = HttpHeaderUtils.rateLimitCooldownMs(
            HttpHeaderUtils.parseRetryAfter(response.header("Retry-After")),
            if (cfChallenge) DEFAULT_COOLDOWN_MS * 2 else DEFAULT_COOLDOWN_MS,
        ).coerceAtMost(MAX_COOLDOWN_MS)
        val now = android.os.SystemClock.elapsedRealtime()
        val first = synchronized(this) {
            val wasCooling = now < cooldownUntil
            cooldownUntil = maxOf(cooldownUntil, now + cooldownMs)
            !wasCooling
        }
        // 计量池按惩罚窗口去重，后到的较长冷却或 CF 质询仍需同步。
        org.linuxdo.android.data.ApiQuotaPool.onServerRateLimited(cooldownMs, cfChallenge)
        if (!first) return
        NetDiagnostics.log(
            "站内图片限流(${if (cfChallenge) "CF质询" else "429"}): ${response.request.url.encodedPath}，" +
                "图片暂停 ${cooldownMs / 1_000L}s，后台池同步退让",
        )
    }
}

/**
 * 403/挑战定位用的脱敏诊断：Cookie 值、CSRF 一律只记 SHA-256 前 6 位指纹，绝不落明文。
 * 由 AppContainer 注入 [sink]；未注入时静默不记。
 */
object NetDiagnostics {
    @Volatile var sink: org.linuxdo.android.data.Diagnostics? = null

    private val throttle = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val cfErrorCode = Regex("error code:?\\s*(10\\d\\d)", RegexOption.IGNORE_CASE)

    fun fingerprint(value: String?): String {
        if (value.isNullOrEmpty()) return "-"
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.take(3).joinToString("") { "%02x".format(it) }
    }

    /** 同名 Cookie 在原始 Cookie 头里出现的所有值指纹，用于发现根域/子域/分区副本并存。 */
    fun cookieFingerprints(rawHeader: String, name: String): List<String> =
        rawHeader.split(';').map { it.trim() }
            .filter { it.substringBefore('=') == name }
            .map { fingerprint(it.substringAfter('=')) }

    fun cookieNames(rawHeader: String): String =
        rawHeader.split(';').map { it.trim().substringBefore('=') }.filter { it.isNotBlank() }.joinToString(",")

    /** 响应体只提取特征标签，不记正文。 */
    fun bodyFeatures(body: String): String {
        val head = body.take(8_000)
        val tags = buildList {
            if (head.contains("BAD CSRF", ignoreCase = true)) add("BAD_CSRF")
            if (head.contains("Just a moment", ignoreCase = true)) add("JUST_A_MOMENT")
            if (head.contains("challenge-platform")) add("CHALLENGE_PLATFORM")
            if (head.contains("cf-chl", ignoreCase = true)) add("CF_CHL")
            if (head.contains("turnstile", ignoreCase = true)) add("TURNSTILE")
            if (head.contains("Attention Required", ignoreCase = true)) add("ATTENTION_REQUIRED")
            if (head.contains("Sorry, you have been blocked", ignoreCase = true)) add("CF_BLOCKED")
            cfErrorCode.find(head)?.let { add("CF_" + it.groupValues[1]) }
            if (head.contains("not_logged_in", ignoreCase = true)) add("NOT_LOGGED_IN")
            if (head.contains("invalid_access", ignoreCase = true)) add("INVALID_ACCESS")
            if (head.trimStart().startsWith("{")) add("JSON")
            if (head.contains("<html", ignoreCase = true)) add("HTML")
        }
        return if (tags.isEmpty()) "len=${body.length}" else tags.joinToString("|") + ",len=${body.length}"
    }

    fun log(message: String) {
        sink?.log("[诊断] $message")
    }

    /** 同一类 GET 失败 60 秒内只记一次，写请求每次都记。 */
    private fun shouldTrace(method: String, path: String, status: Int): Boolean {
        if (method != "GET") return true
        if (status < 400) return false
        val key = "$method $path $status"
        val now = android.os.SystemClock.elapsedRealtime()
        val last = throttle[key]
        if (last != null && now - last < 60_000L) return false
        throttle[key] = now
        if (throttle.size > 256) throttle.clear()
        return true
    }

    fun traceOkHttp(
        chain: Interceptor.Chain,
        request: okhttp3.Request,
        response: Response,
        sentCookieHeader: String,
        rawWebCookieHeader: String,
        transportLabel: String,
    ) {
        if (sink == null) return
        val path = request.url.encodedPath
        if (!shouldTrace(request.method, path, response.code)) return
        val connection = chain.connection()
        val route = connection?.route()
        val proxyType = route?.proxy?.type()?.name ?: "?"
        val peer = route?.socketAddress?.address?.hostAddress ?: "?"
        val handshake = runCatching { connection?.handshake() }.getOrNull()
        val features = if (response.code >= 400) {
            runCatching { bodyFeatures(response.peekBody(16_384).string()) }.getOrDefault("peek失败")
        } else "-"
        val setCookieNames = response.headers("Set-Cookie").joinToString(",") { it.substringBefore('=') }.ifBlank { "-" }
        val webClearance = cookieFingerprints(rawWebCookieHeader, Config.CF_CLEARANCE_COOKIE)
        val sentClearance = cookieFingerprints(sentCookieHeader, Config.CF_CLEARANCE_COOKIE)
        val webToken = cookieFingerprints(rawWebCookieHeader, Config.SESSION_COOKIE)
        val sentToken = cookieFingerprints(sentCookieHeader, Config.SESSION_COOKIE)
        log(
            "A ${request.method} ${request.url.host}$path -> ${response.code}" +
                " | cf-mitigated=${response.header("cf-mitigated") ?: "-"}" +
                " cf-ray=${response.header("cf-ray") ?: "-"}" +
                " server=${response.header("server") ?: "-"}" +
                " ctype=${response.header("content-type")?.substringBefore(';') ?: "-"}" +
                " body=$features" +
                " | 发出cf_clearance=${sentClearance.size}份${sentClearance}" +
                " WebView原始cf_clearance=${webClearance.size}份${webClearance}" +
                " 发出_t=${sentToken} WebView_t=${webToken}" +
                " hasCsrf=${!request.header("X-CSRF-Token").isNullOrBlank()}" +
                " 发出Cookie名=[${cookieNames(sentCookieHeader)}]" +
                " Set-Cookie=[$setCookieNames]" +
                " | 链路 mode=$transportLabel proxy=$proxyType peer=$peer proto=${connection?.protocol() ?: "?"}" +
                " tls=${handshake?.tlsVersion?.javaName ?: "?"} cipher=${handshake?.cipherSuite?.javaName ?: "?"}" +
                " ua=${fingerprint(request.header("User-Agent"))}",
        )
    }
}
