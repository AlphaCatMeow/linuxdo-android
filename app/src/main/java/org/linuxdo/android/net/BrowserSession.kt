package org.linuxdo.android.net

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.linuxdo.android.BuildConfig
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import org.linuxdo.android.Config
import org.linuxdo.android.data.Diagnostics
import org.linuxdo.android.data.SessionStore

/**
 * 全应用唯一的 WebView 会话:
 * - 登录/交互验证时被界面显示出来;
 * - 其余时间躺在界面下层,用于静默通过 Cloudflare 挑战,或作为路径 B 的网络层。
 * 同一个 WebView 与同一个固定 UA,才能保证 cf_clearance 与后续请求的特征一致。
 */
class BrowserSession(
    private val appContext: Context,
    val userAgent: String,
    private val store: SessionStore,
    private val diagnostics: Diagnostics,
) {
    private var webViewInstance: WebView? = null
    private var authWebView: WebView? = null
    private var pendingAuthHtml: String? = null
    private var authHtmlInjected: Boolean = false
    private var cfCompletionProbing: Boolean = false
    private var cfQuietRounds: Int = 0
    private val pending = ConcurrentHashMap<String, CompletableDeferred<TransportResult>>()
    private val solveMutex = Mutex()

    @Volatile
    private var finishedCount = 0

    private var onAuthCallback: ((success: Boolean, errorMsg: String?, need2FA: Boolean) -> Unit)? = null
    private var cachedLogin: String = ""
    private var cachedPassword: String = ""
    /** 非空表示当前内联页处于"发送登录邮件"模式，值为目标邮箱。 */
    private var cachedEmailSend: String? = null
    /** 非空表示认证 WebView 正在整页打开邮件登录链接（Fluxdo 同款兑换方式）。 */
    private var pendingEmailLink: String? = null
    private var emailLinkCompleting: Boolean = false
    /** 首个 onPageFinished 往往早于 Cookie 落盘，剩余复查次数。 */
    private var emailLinkRechecksRemaining: Int = 0
    private var cachedSecondFactor: String? = null
    private var isDarkCached: Boolean = false
    private var isInCfMode: Boolean = false
    private var cfRetryUsed: Boolean = false
    private var cfProbeScheduled: Boolean = false
    private var cfClearanceBeforeChallenge: String? = null
    private var pendingHcaptchaToken: String? = null
    private var hcaptchaTempReady: Boolean = false
    private var retryingLoginWithHcaptchaToken: Boolean = false
    private var awaitingSecondFactorLogin: Boolean = false
    private var attachedActivityContext: Context? = null
    private var cfDialog: Dialog? = null
    private var cfWebView: WebView? = null

    fun cancelCaptchaLogin() {
        isInCfMode = false
        cfRetryUsed = false
        cfProbeScheduled = false
        cfCompletionProbing = false
        cfQuietRounds = 0
        cfClearanceBeforeChallenge = null
        pendingHcaptchaToken = null
        hcaptchaTempReady = false
        retryingLoginWithHcaptchaToken = false
        awaitingSecondFactorLogin = false
        pendingAuthHtml = null
        authHtmlInjected = false
        cachedEmailSend = null
        dismissCfDialog()
        onAuthCallback = null
    }

    fun hasPendingSecondFactorLogin(): Boolean = awaitingSecondFactorLogin

    /**
     * Fluxdo 的 2FA 重试继续使用已有 WebView 的 h_captcha_temp_id；不要退回 OkHttp，
     * 否则 Cloudflare 指纹与登录临时会话都会断开。
     */
    fun resumeSecondFactorLogin(
        secondFactorToken: String,
        secondFactorMethod: Int,
        onResult: (success: Boolean, errorMsg: String?, need2FA: Boolean) -> Unit,
    ): Boolean {
        if (!awaitingSecondFactorLogin || secondFactorToken.isBlank()) return false
        onAuthCallback = onResult
        cachedSecondFactor = secondFactorToken
        cachedSecondFactorMethod = secondFactorMethod
        isInCfMode = false
        val view = authWebView ?: return false
        view.post {
            executeWebLogin(view, null, cachedSecondFactor, cachedSecondFactorMethod)
        }
        return true
    }

    fun discardPendingSecondFactorLogin() {
        awaitingSecondFactorLogin = false
        cachedSecondFactor = null
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onResult(id: String, status: Int, mitigated: String, body: String) {
            pending.remove(id)?.complete(
                TransportResult(status, body, ChallengeDetector.isChallenge(status, mitigated, body)),
            )
        }

        @JavascriptInterface
        fun onAuthResult(jsonStr: String) {
            Log.d(TAG, "onAuthResult: $jsonStr")
            diagnostics.log("收到安全验证登录结果: $jsonStr")
            val cb = onAuthCallback ?: return
            try {
                val obj = JSONObject(jsonStr)
                val phase = obj.optString("phase")
                val status = obj.optInt("status")
                val body = obj.optString("body")

                when (phase) {
                    "session" -> {
                        if (!pendingHcaptchaToken.isNullOrBlank()) hcaptchaTempReady = true
                        retryingLoginWithHcaptchaToken = false
                        pendingHcaptchaToken = null
                        val resObj = try { JSONObject(body) } catch (_: Exception) { null }
                        val reason = resObj?.optString("reason")
                        val error = resObj?.optString("error")
                        val user = resObj?.optJSONObject("user")?.optString("username")

                        if (reason == "second_factor" || reason == "invalid_second_factor") {
                            awaitingSecondFactorLogin = true
                            val msg = if (reason == "invalid_second_factor") {
                                error?.takeIf { it.isNotBlank() } ?: "二次验证码错误，请重新输入"
                            } else {
                                "需要进行二次验证 (2FA)"
                            }
                            cb(false, msg, true)
                        } else if (!error.isNullOrBlank() && user.isNullOrBlank()) {
                            awaitingSecondFactorLogin = false
                            cb(false, error, false)
                        } else if (status == 200 || !user.isNullOrBlank()) {
                            awaitingSecondFactorLogin = false
                            cb(true, null, false)
                        } else {
                            awaitingSecondFactorLogin = false
                            cb(false, "登录失败: HTTP $status", false)
                        }
                    }
                    "cf_needed", "csrf" -> {
                        if (obj.optBoolean("captchaAccepted")) {
                            hcaptchaTempReady = true
                            pendingHcaptchaToken = null
                        } else {
                            obj.optString("hcaptchaToken")
                                .takeIf { it.isNotBlank() }
                                ?.let { pendingHcaptchaToken = it }
                        }
                        if (!cfRetryUsed) {
                            cfRetryUsed = true
                            diagnostics.log("CSRF 遇到 Cloudflare 盾 ($status)，正在安全弹窗内启动 /challenge 验证...")
                            launchCfChallenge()
                        } else {
                            cb(false, "Cloudflare 验证已失效，请重试 (CSRF $status)", false)
                        }
                    }
                    "hcaptcha" -> {
                        hcaptchaTempReady = false
                        if (retryingLoginWithHcaptchaToken || cachedEmailSend != null) {
                            // 令牌可能在用户完成 CF 的过程中到期；回退到可见 hCaptcha，
                            // 不能把已经通过的 CF 也当作失败。
                            retryingLoginWithHcaptchaToken = false
                            pendingHcaptchaToken = null
                            diagnostics.log("hCaptcha 令牌已过期，重新展示图形验证")
                            authWebView?.post {
                                loadCaptchaLogin(
                                    login = cachedLogin,
                                    password = cachedPassword,
                                    secondFactorToken = cachedSecondFactor,
                                    secondFactorMethod = cachedSecondFactorMethod,
                                    isDark = isDarkCached,
                                    resetCfRetry = false,
                                    emailSendTo = cachedEmailSend,
                                    onResult = cb,
                                )
                            }
                        } else {
                            awaitingSecondFactorLogin = false
                            cb(false, "人机验证未通过，请重试", false)
                        }
                    }
                    "email_sent" -> {
                        diagnostics.log("登录邮件已通过内联安全验证发送")
                        awaitingSecondFactorLogin = false
                        cb(true, null, false)
                    }
                    "exception" -> cb(false, "网络请求异常: $body", false)
                    else -> cb(false, "未知异常 ($phase)", false)
                }
            } catch (e: Exception) {
                cb(false, "响应解析失败: ${e.message}", false)
            }
        }

        @JavascriptInterface
        fun onHcaptchaPass(token: String) {
            if (token.isBlank()) return
            val view = authWebView ?: return
            view.postDelayed(
                {
                    if (authWebView !== view || onAuthCallback == null) return@postDelayed
                    pendingHcaptchaToken = token
                    hcaptchaTempReady = false
                    retryingLoginWithHcaptchaToken = false
                    // 对齐 Fluxdo：回灌时排除会话凭据。Fluxdo 所有 CF 相关同步都带
                    // excludeCookieNames: authCookieNames，理由是"避免旧会话回写" ——
                    // 一枚失效的旧 _t 被灌回去，Discourse 就会在已有会话状态下处理登录。
                    store.restoreMissingToWebView(exclude = setOf(Config.SESSION_COOKIE))
                    CookieManager.getInstance().flush()
                    executeWebLogin(view, token, cachedSecondFactor, cachedSecondFactorMethod)
                },
                HCAPTCHA_CALLBACK_SETTLE_DELAY_MS,
            )
        }

        /** 登录页在关键节点回传的现场快照，只写诊断日志，不参与流程判定。 */
        @JavascriptInterface
        fun onDiagnostic(message: String) {
            if (message.isBlank()) return
            diagnostics.log("登录页诊断: $message")
        }
    }

    /**
     * Fluxdo 把 CF 验证放在独立 WebView 中，登录页保持存活，以便验证后直接复用
     * hCaptcha token / h_captcha_temp_id 继续提交 session。Android 也必须保持这个边界。
     */
    private fun launchCfChallenge() {
        isInCfMode = true
        cfProbeScheduled = false
        cfCompletionProbing = false
        cfQuietRounds = 0
        val loginView = authWebView ?: return
        loginView.post {
            if (!isInCfMode) return@post
            cfClearanceBeforeChallenge = currentCfClearance()
            val cleared = store.clearCookie(CF_CLEARANCE_COOKIE)
            CookieManager.getInstance().flush()
            if (cleared) {
                diagnostics.log(
                    "已清除旧 cf_clearance ${clearanceFingerprint(cfClearanceBeforeChallenge)}，" +
                        "打开独立 Cloudflare 验证窗口",
                )
            } else {
                // 删不掉时 CF 会拿旧凭据直接放行回源(不出盾),整轮验证就会空转。
                // 把它记成显式告警,避免又把"落到 404 页"误读成验证成功。
                diagnostics.log(
                    "旧 cf_clearance 删除失败，仍残留 ${currentCfClearance()?.length ?: 0} 字符；" +
                        "Cloudflare 可能直接放行而不出盾",
                )
            }
            showCfChallengeDialog()
        }
    }

    private fun showCfChallengeDialog() {
        if (!isInCfMode || cfDialog?.isShowing == true) return
        val context = attachedActivityContext ?: run {
            isInCfMode = false
            onAuthCallback?.invoke(false, "无法打开 Cloudflare 验证窗口", false)
            return
        }

        val challengeView = WebView(MutableContextWrapper(appContext)).also { view ->
            (view.context as MutableContextWrapper).baseContext = context
            configureWebView(view)
            view.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    Log.d(TAG, "CF onPageStarted: $url")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    CookieManager.getInstance().flush()
                    Log.d(TAG, "CF onPageFinished: $url")
                    val currentUrl = url.orEmpty()
                    if (view == null) return
                    if (isBareChallengeUrl(currentUrl)) {
                        // 对齐 Fluxdo：检查页面是否已到达源站 404（CF 放行回源）
                        view.evaluateJavascript(ORIGIN_NOT_FOUND_PROBE_JS) { result ->
                            if (decodeJsValue(result) == "true") {
                                awaitFreshClearanceThen(view, "源站 404 页面(DOM 特征)")
                                return@evaluateJavascript
                            }
                            detectCfChallengeCompletion(view, currentUrl)
                        }
                    } else {
                        detectCfChallengeCompletion(view, currentUrl)
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?,
                ) {
                    val url = request?.url?.toString().orEmpty()
                    val status = errorResponse?.statusCode ?: 0
                    diagnostics.log("CF WebView HTTP 错误 [$status]: $url" + cfMitigatedSuffix(errorResponse))
                    if (request?.isForMainFrame != true || view == null) return
                    if (status == 404 && isBareChallengeUrl(url) && !isCfMitigatedChallenge(errorResponse)) {
                        // 对齐 Fluxdo：/challenge 是站点专用的 CF 验证入口。
                        // 未通过时 CF 接管并返回 challenge；通过后请求落到源站，源站没有该页面返回 404。
                        // 但 404 只能说明"这次请求没被 CF 接管",不等于本轮验证签发了新 clearance ——
                        // 旧 clearance 残留时 CF 同样会直接放行回源。所以这里不直接收口。
                        awaitFreshClearanceThen(view, "主框架 /challenge 返回源站 404")
                        return
                    }
                    scheduleCfCompletionProbe(view, 250L)
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        diagnostics.log("CF WebView 资源错误 [${error?.errorCode ?: 0}]: ${request.url}")
                    }
                }
            }
            view.webChromeClient = createLoggingChromeClient("CF")
        }

        val dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(challengeView)
            setCanceledOnTouchOutside(false)
            setOnDismissListener {
                if (cfDialog === this && isInCfMode) {
                    val dismissedView = cfWebView
                    cfDialog = null
                    cfWebView = null
                    isInCfMode = false
                    dismissedView?.destroy()
                    onAuthCallback?.invoke(false, "Cloudflare 验证已取消，请重试", false)
                }
            }
        }
        cfDialog = dialog
        cfWebView = challengeView
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.WHITE))
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.heightPixels * 0.92f).toInt(),
            )
            setGravity(Gravity.BOTTOM)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.5f }
        }
        challengeView.loadUrl("${Config.BASE_URL}/challenge")
        scheduleCfCompletionProbe(challengeView)
    }

    private fun detectCfChallengeCompletion(challengeView: WebView, url: String?) {
        if (!isInCfMode) return
        diagnostics.log("Cloudflare 页面已完成一次加载: ${url.orEmpty()}")
        scheduleCfCompletionProbe(challengeView, 250L)
    }

    /** CF 成功时不保证会再触发导航回调，因此持续观察新的 cf_clearance Cookie。 */
    private fun scheduleCfCompletionProbe(challengeView: WebView, delayMs: Long = CF_PROBE_INTERVAL_MS) {
        if (
            !isInCfMode ||
            cfProbeScheduled ||
            cfCompletionProbing ||
            cfWebView !== challengeView ||
            cfDialog?.isShowing != true
        ) return
        cfProbeScheduled = true
        challengeView.postDelayed(
            {
                cfProbeScheduled = false
                if (!isInCfMode || cfWebView !== challengeView || cfDialog?.isShowing != true) return@postDelayed
                if (cfCompletionProbing) return@postDelayed

                val freshClearance = currentCfClearance()
                if (!isFreshCfClearance(freshClearance)) {
                    scheduleCfCompletionProbe(challengeView)
                    return@postDelayed
                }
                // 新 clearance 出现不等于挑战已经做完。Cloudflare 的 JS challenge 跑在
                // 页面加载之后,期间就可能先落一枚 clearance;此时收口会拿到一枚半成品凭据,
                // 紧接着的 csrf 必然再被 cf-mitigated=challenge 拒掉 —— 实测三次失败
                // 全是"页面加载完 2~3 秒就判定完成"这个形态。对齐 Fluxdo 的
                // hasActiveCfChallenge:必须等页面上的挑战标记消失才算真的过了。
                challengeView.evaluateJavascript(ACTIVE_CHALLENGE_PROBE_JS) { raw ->
                    if (!isInCfMode || cfWebView !== challengeView || cfDialog?.isShowing != true) {
                        return@evaluateJavascript
                    }
                    if (decodeJsValue(raw) == "true") {
                        cfQuietRounds = 0
                        diagnostics.log(
                            "已出现新 cf_clearance ${clearanceFingerprint(freshClearance)}，但页面仍在验证中，继续等待",
                        )
                        scheduleCfCompletionProbe(challengeView)
                        return@evaluateJavascript
                    }
                    // CF 完成后会导航回 /challenge，导航那一瞬间页面是空的,
                    // 挑战标记同样查不到。连续两轮静默才收口,把这个空窗挡掉。
                    cfQuietRounds++
                    if (cfQuietRounds >= CF_QUIET_ROUNDS_REQUIRED) {
                        completeCfChallenge(challengeView, "新 cf_clearance 且页面连续 $cfQuietRounds 轮无挑战标记")
                    } else {
                        scheduleCfCompletionProbe(challengeView)
                    }
                }
            },
            delayMs,
        )
    }

    /**
     * "没被 CF 接管"不等于"本轮签发了新凭据"。
     *
     * 源站 404 只说明这次请求回了源,而旧 clearance 残留时 CF 同样会直接放行回源。
     * 此前在这两种情况下都直接收口,于是拿着一枚已被判废的旧 clearance 去重放登录,
     * 必然再撞一次 403,最后以"Cloudflare 验证已失效"收场。改为先确认新 clearance
     * 真的出现;等不到就如实报失败,而不是让用户再看一遍同样的错误。
     */
    private fun awaitFreshClearanceThen(view: WebView, reason: String) {
        if (!isInCfMode || cfCompletionProbing) return
        cfCompletionProbing = true
        cfProbeScheduled = false
        diagnostics.log("$reason，开始确认是否签发了新 cf_clearance")
        val deadline = SystemClock.elapsedRealtime() + CF_COMPLETION_PROBE_TIMEOUT_MS

        fun poll() {
            if (!isInCfMode || cfWebView !== view || cfDialog?.isShowing != true) {
                cfCompletionProbing = false
                return
            }
            CookieManager.getInstance().flush()
            if (isFreshCfClearance(currentCfClearance())) {
                cfCompletionProbing = false
                completeCfChallenge(view, reason)
                return
            }
            if (SystemClock.elapsedRealtime() >= deadline) {
                cfCompletionProbing = false
                isInCfMode = false
                diagnostics.log(
                    "$reason，但 ${CF_COMPLETION_PROBE_TIMEOUT_MS}ms 内未出现新 cf_clearance，判定本轮验证未完成",
                )
                dismissCfDialog()
                onAuthCallback?.invoke(false, "Cloudflare 未签发新的验证凭据，请重试", false)
                return
            }
            view.postDelayed({ poll() }, CF_PROBE_INTERVAL_MS)
        }

        view.postDelayed({ poll() }, CF_COOKIE_SETTLE_DELAY_MS)
    }

    /**
     * 与 Fluxdo 一致：新 clearance 出现即收口。不要在这里再请求 /session/csrf，
     * 否则验证后的短暂限流会把成功的 CF 流程误判为失败。
     */
    private fun completeCfChallenge(probeView: WebView, reason: String) {
        if (!isInCfMode) return
        val loginView = authWebView ?: return
        diagnostics.log(
            "Cloudflare 验证完成($reason)，clearance=${clearanceFingerprint(currentCfClearance())}，" +
                "关闭验证窗口并恢复原认证请求",
        )
        isInCfMode = false
        cfProbeScheduled = false
        cfCompletionProbing = false
        CookieManager.getInstance().flush()
        store.syncFromWebView()
        dismissCfDialog()
        // 对齐 Fluxdo：底层网络栈沉淀后直接重放，绝不手动回灌以破坏原生 Cookie
        loginView.postDelayed(
            {
                if (authWebView !== loginView || onAuthCallback == null) return@postDelayed
                CookieManager.getInstance().flush()
                resumePendingWebLogin(loginView)
            },
            CF_SETTLE_DELAY_MS,
        )
    }

    private fun currentCfClearance(): String? {
        val cm = CookieManager.getInstance()
        val header1 = cm.getCookie(Config.BASE_URL)
        val header2 = cm.getCookie("${Config.BASE_URL}/")
        return (SessionStore.parseCookieHeader(header1)[CF_CLEARANCE_COOKIE]
            ?: SessionStore.parseCookieHeader(header2)[CF_CLEARANCE_COOKIE])
            ?.takeIf { it.isNotBlank() }
    }

    private fun isFreshCfClearance(value: String?): Boolean =
        !value.isNullOrBlank() && value != cfClearanceBeforeChallenge

    /**
     * 只认不带查询串的 /challenge。
     *
     * CF 在挑战过程中会带上 __cf_chl_* 查询串反复请求同一路径，那些响应不能当作
     * "已回源"的判据；对齐 Fluxdo 的 _isBareVerifyUrl。
     */
    private fun isBareChallengeUrl(url: String): Boolean {
        val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return parsed.host == Config.HOST &&
            parsed.path == CF_CHALLENGE_PATH &&
            parsed.query.isNullOrEmpty()
    }

    /** 带 `cf-mitigated: challenge` 的响应是 CF 自己接管的挑战，不能算放行。 */
    private fun isCfMitigatedChallenge(response: WebResourceResponse?): Boolean =
        response?.responseHeaders
            ?.entries
            ?.any { it.key.equals("cf-mitigated", ignoreCase = true) && it.value.orEmpty().contains("challenge", true) }
            ?: false

    private fun cfMitigatedSuffix(response: WebResourceResponse?): String {
        val headers = response?.responseHeaders ?: return ""
        val interesting = headers.entries
            .filter { it.key.equals("cf-mitigated", true) || it.key.equals("cf-ray", true) }
            .joinToString(" ") { "${it.key.lowercase()}=${it.value}" }
        return if (interesting.isEmpty()) "" else " [$interesting]"
    }

    private fun resumePendingWebLogin(loginView: WebView) {
        when {
            awaitingSecondFactorLogin && !cachedSecondFactor.isNullOrBlank() -> {
                executeWebLogin(loginView, null, cachedSecondFactor, cachedSecondFactorMethod)
            }
            hcaptchaTempReady -> {
                retryingLoginWithHcaptchaToken = true
                executeWebLogin(loginView, null, cachedSecondFactor, cachedSecondFactorMethod)
            }
            !pendingHcaptchaToken.isNullOrBlank() -> {
                val hcaptchaToken = pendingHcaptchaToken
                retryingLoginWithHcaptchaToken = true
                executeWebLogin(loginView, hcaptchaToken, cachedSecondFactor, cachedSecondFactorMethod)
            }
            else -> {
                diagnostics.log("CF 通过后未保留 hCaptcha token，重新展示图形验证")
                loadCaptchaLogin(
                    login = cachedLogin,
                    password = cachedPassword,
                    secondFactorToken = cachedSecondFactor,
                    secondFactorMethod = cachedSecondFactorMethod,
                    isDark = isDarkCached,
                    resetCfRetry = false,
                    emailSendTo = cachedEmailSend,
                    onResult = onAuthCallback ?: { _, _, _ -> },
                )
            }
        }
    }

    private fun executeWebLogin(
        view: WebView,
        hcaptchaToken: String?,
        secondFactorToken: String?,
        secondFactorMethod: Int,
    ) {
        val hcaptchaJson = hcaptchaToken?.let(JSONObject::quote) ?: "null"
        val secondFactorJson = secondFactorToken?.let(JSONObject::quote) ?: "null"
        val secondFactorMethodJson = JSONObject.quote(secondFactorMethod.toString())
        view.evaluateJavascript(
            """
                (function () {
                  if (typeof window.__linuxdoRunLogin !== 'function') return false;
                  if (typeof window.__linuxdoShowSubmitting === 'function') {
                    window.__linuxdoShowSubmitting();
                  }
                  window.__linuxdoRunLogin($hcaptchaJson, $secondFactorJson, $secondFactorMethodJson);
                  return true;
                })();
            """.trimIndent(),
        ) { raw ->
            if (decodeJsValue(raw) == "true") return@evaluateJavascript
            retryingLoginWithHcaptchaToken = false
            diagnostics.log("网页登录脚本已丢失，重新展示图形验证")
            loadCaptchaLogin(
                login = cachedLogin,
                password = cachedPassword,
                secondFactorToken = cachedSecondFactor,
                secondFactorMethod = cachedSecondFactorMethod,
                isDark = isDarkCached,
                resetCfRetry = false,
                onResult = onAuthCallback ?: { _, _, _ -> },
            )
        }
    }

    private fun dismissCfDialog() {
        val dialog = cfDialog ?: return
        val view = cfWebView
        cfDialog = null
        cfWebView = null
        cfProbeScheduled = false
        cfCompletionProbing = false
        cfQuietRounds = 0
        dialog.setOnDismissListener(null)
        dialog.dismiss()
        view?.destroy()
    }

    /**
     * 借鉴 Fluxdo 的安全认证机制：
     * 不加载臃肿的 Discourse Ember 单页应用，而是用 BaseURL=https://linux.do 加载极简的
     * 内联 HTML。在没有任何 CSS 污染的标准容器里渲染 hCaptcha，并在验证通过后直接在
     * WebView 内核中同源 fetch 完成 CSRF -> hCaptcha Token 兑换 -> session.json 登录。
     * 若 CSRF 被 CF 拦截，保留本页 hCaptcha/2FA 会话，交由独立 CF 窗口完成后自动重放。
     */
    private var cachedSecondFactorMethod: Int = 1

    fun loadCaptchaLogin(
        login: String,
        password: String,
        secondFactorToken: String? = null,
        secondFactorMethod: Int = 1,
        isDark: Boolean = false,
        resetCfRetry: Boolean = true,
        /** 非空时切换为"发送登录邮件"模式：同一套 hCaptcha/CF 机制，提交的是 /u/email-login.json。 */
        emailSendTo: String? = null,
        onResult: (success: Boolean, errorMsg: String?, need2FA: Boolean) -> Unit,
    ) {
        onAuthCallback = onResult
        cachedLogin = login
        cachedPassword = password
        cachedSecondFactor = secondFactorToken
        cachedSecondFactorMethod = secondFactorMethod
        cachedEmailSend = emailSendTo
        isDarkCached = isDark
        isInCfMode = false
        hcaptchaTempReady = false
        if (resetCfRetry) {
            cfRetryUsed = false
            pendingHcaptchaToken = null
            retryingLoginWithHcaptchaToken = false
            awaitingSecondFactorLogin = false
        }

        val uJson = JSONObject.quote(login)
        val pJson = JSONObject.quote(password)
        val emailJson = JSONObject.quote(emailSendTo.orEmpty())
        val emailMode = !emailSendTo.isNullOrBlank()
        val sfJson = if (!secondFactorToken.isNullOrBlank()) JSONObject.quote(secondFactorToken) else "null"
        val sfMethodJson = JSONObject.quote(secondFactorMethod.toString())
        val bgBadge = if (isDark) "rgba(10, 132, 255, 0.22)" else "rgba(0, 122, 255, 0.12)"
        val tipColor = if (isDark) "#f5f5f7" else "#1c1c1e"
        val statusColor = if (isDark) "#0a84ff" else "#007aff"
        val hTheme = if (isDark) "dark" else "light"

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
              <style>
                * { box-sizing: border-box; }
                html, body {
                  margin: 0; padding: 0; width: 100%; height: 100%; background: transparent;
                  overflow-x: hidden;
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                  -webkit-text-size-adjust: 100%;
                }
                .wrap {
                  width: 100%; min-height: 100%;
                  display: flex; flex-direction: column; align-items: center; justify-content: flex-start;
                  gap: 16px; padding: 24px 16px 20px 16px; text-align: center;
                }
                .badge {
                  width: 48px; height: 48px; border-radius: 50%; display: flex;
                  align-items: center; justify-content: center;
                  background: $bgBadge; font-size: 24px;
                }
                .tip {
                  font-size: 14px; line-height: 1.5; color: $tipColor; margin: 0; max-width: 300px;
                  text-align: center; font-weight: 500;
                }
                .h-captcha { min-height: 78px; margin-top: 4px; }
                .spinner {
                  width: 30px; height: 30px; border: 3px solid rgba(0, 122, 255, 0.25);
                  border-radius: 50%; border-top-color: $statusColor;
                  animation: spin 0.8s linear infinite; display: inline-block;
                }
                .submitting {
                  position: fixed; inset: 0; z-index: 10; display: none;
                  flex-direction: column; align-items: center; justify-content: center; gap: 16px;
                  padding: 32px 16px; background: ${if (isDark) "rgba(28, 28, 30, 0.94)" else "rgba(255, 255, 255, 0.94)"};
                  color: $tipColor; font-size: 15px; font-weight: 500;
                }
                @keyframes spin { to { transform: rotate(360deg); } }
              </style>
            </head>
            <body>
              <div id="main-content" class="wrap">
                <div class="badge">🛡️</div>
                <p class="tip">${if (emailMode) "勾选下方方框，确认你不是机器人即可发送登录邮件" else "勾选下方方框，确认你不是机器人即可继续登录"}</p>
                <div id="cap" class="h-captcha"
                  data-sitekey="a776b4ac-8c4c-441e-986a-c6ee9ed8cf08"
                  data-callback="onPass"
                  data-theme="$hTheme"
                  data-size="normal"></div>
              </div>
              <div id="submitting" class="submitting">
                <div class="spinner"></div>
                <div>正在验证登录…</div>
              </div>
              <script>
                function callBridge(name, payload) {
                  try {
                    if (window.__ldb && window.__ldb[name]) {
                      window.__ldb[name](payload);
                    }
                  } catch (e) {}
                }

                function onPass(token) {
                  callBridge('onHcaptchaPass', token);
                }

                function showSubmitting() {
                  var overlay = document.getElementById('submitting');
                  if (overlay) overlay.style.display = 'flex';
                }

                async function runLogin(hcaptchaToken, secondFactorOverride, secondFactorMethodOverride) {
                  var identifier = $uJson;
                  var password = $pJson;
                  var secondFactor = arguments.length > 1 ? secondFactorOverride : $sfJson;
                  var secondFactorMethod = arguments.length > 2 ? secondFactorMethodOverride : $sfMethodJson;

                  function isCloudflareChallenge(response, text) {
                    var mitigated = (response.headers.get('cf-mitigated') || '').toLowerCase();
                    var body = (text || '').toLowerCase();
                    return (response.status === 403 || response.status === 429) && (
                      mitigated === 'challenge' ||
                      body.indexOf('just a moment') !== -1 ||
                      body.indexOf('challenge-platform') !== -1 ||
                      body.indexOf('cf-chl-') !== -1 ||
                      body.indexOf('cf-mitigated') !== -1
                    );
                  }

                  // 把 CF 的权威判定头原样带回宿主。403 到底是 CF 拦的还是 Discourse 自己
                  // 返的，只看状态码分不出来，cf-ray 还能让站方按请求定位。
                  function reportResponse(label, response) {
                    try {
                      var names = [];
                      var raw = document.cookie || '';
                      raw.split(';').forEach(function (part) {
                        var i = part.indexOf('=');
                        if (i > 0) names.push(part.substring(0, i).trim());
                      });
                      callBridge('onDiagnostic',
                        label + ' status=' + response.status +
                        ' cf-mitigated=' + (response.headers.get('cf-mitigated') || '-') +
                        ' cf-ray=' + (response.headers.get('cf-ray') || '-') +
                        ' 页面可见Cookie=[' + names.join(',') + ']');
                    } catch (e) {}
                  }

                  try {
                    callBridge('onDiagnostic',
                      'fetch 前上下文 href=' + window.location.href +
                      ' origin=' + window.location.origin +
                      ' hasToken=' + (hcaptchaToken ? 'yes' : 'no'));
                  } catch (e) {}

                  try {
                    // 1. 获取 CSRF Token (与内核会话一致)。对齐 Fluxdo：单次获取，避免在遇到挑战或限流时高频轮询引发 429
                    var c = await fetch('/session/csrf', {
                      method: 'GET',
                      headers: { 'X-Requested-With': 'XMLHttpRequest', 'Accept': 'application/json' },
                      credentials: 'include',
                      cache: 'no-store'
                    });
                    if (c.status !== 200) {
                      var cBody = await c.text();
                      reportResponse('csrf 失败', c);
                      var isCf = isCloudflareChallenge(c, cBody);
                      callBridge('onAuthResult', JSON.stringify({
                        phase: isCf ? 'cf_needed' : 'csrf',
                        status: c.status,
                        body: cBody,
                        hcaptchaToken: isCf ? hcaptchaToken : null
                      }));
                      return;
                    }
                    var csrf = (await c.json()).csrf;

                    // 2. 首次登录兑换 hCaptcha token；2FA 重试沿用 h_captcha_temp_id。
                    if (hcaptchaToken) {
                      var endpoints = ['/captcha/hcaptcha/create.json', '/hcaptcha/create.json'];
                      var hOk = false;
                      var hLast = null;
                      for (var i = 0; i < endpoints.length; i++) {
                        var ep = endpoints[i];
                        try {
                          var h = await fetch(ep, {
                            method: 'POST',
                            credentials: 'include',
                            headers: {
                              'Content-Type': 'application/x-www-form-urlencoded',
                              'X-CSRF-Token': csrf,
                              'X-Requested-With': 'XMLHttpRequest'
                            },
                            body: 'token=' + encodeURIComponent(hcaptchaToken)
                          });
                          var hText = await h.text();
                          if (isCloudflareChallenge(h, hText)) {
                            callBridge('onAuthResult', JSON.stringify({
                              phase: 'cf_needed',
                              status: h.status,
                              body: hText,
                              hcaptchaToken: hcaptchaToken,
                              captchaAccepted: false
                            }));
                            return;
                          }
                          hLast = { endpoint: ep, status: h.status, body: hText };
                          if (h.status === 200) { hOk = true; break; }
                          if (h.status !== 404) break;
                        } catch (e) {
                          hLast = { endpoint: ep, status: 0, body: String(e) };
                        }
                      }
                      if (!hOk) {
                        callBridge('onAuthResult', JSON.stringify({ phase: 'hcaptcha', status: hLast ? hLast.status : 0, body: JSON.stringify(hLast) }));
                        return;
                      }
                    }

                    // 3. 提交登录 session.json
                    var form = 'login=' + encodeURIComponent(identifier) + '&password=' + encodeURIComponent(password);
                    if (secondFactor) {
                      form += '&second_factor_token=' + encodeURIComponent(secondFactor) + '&second_factor_method=' + encodeURIComponent(secondFactorMethod);
                    }
                    var s = await fetch('/session.json', {
                      method: 'POST',
                      credentials: 'include',
                      headers: {
                        'Content-Type': 'application/x-www-form-urlencoded',
                        'X-CSRF-Token': csrf,
                        'X-Requested-With': 'XMLHttpRequest',
                        'Accept': 'application/json'
                      },
                      body: form
                    });
                    var sText = await s.text();
                    if (s.status !== 200) reportResponse('session 非 200', s);
                    if (isCloudflareChallenge(s, sText)) {
                      callBridge('onAuthResult', JSON.stringify({
                        phase: 'cf_needed',
                        status: s.status,
                        body: sText,
                        captchaAccepted: !!hcaptchaToken
                      }));
                      return;
                    }
                    callBridge('onAuthResult', JSON.stringify({ phase: 'session', status: s.status, body: sText }));
                  } catch (e) {
                    callBridge('onAuthResult', JSON.stringify({ phase: 'exception', status: 0, body: String(e) }));
                  }
                }
                /**
                 * 发信模式：与 runLogin 共用同一套 CSRF / hCaptcha 兑换 / CF 挑战时序，
                 * 只把最后一步换成 POST /u/email-login.json。
                 * 站点的邮件接口在触发人机验证时返回 403 invalid_access ——
                 * 换到 h_captcha_temp_id 后再提交才会被放行。
                 */
                async function runEmailSend(hcaptchaToken) {
                  var email = $emailJson;

                  function isCloudflareChallenge(response, text) {
                    var mitigated = (response.headers.get('cf-mitigated') || '').toLowerCase();
                    var body = (text || '').toLowerCase();
                    return (response.status === 403 || response.status === 429) && (
                      mitigated === 'challenge' ||
                      body.indexOf('just a moment') !== -1 ||
                      body.indexOf('challenge-platform') !== -1 ||
                      body.indexOf('cf-chl-') !== -1 ||
                      body.indexOf('cf-mitigated') !== -1
                    );
                  }

                  try {
                    var c = await fetch('/session/csrf', {
                      method: 'GET',
                      headers: { 'X-Requested-With': 'XMLHttpRequest', 'Accept': 'application/json' },
                      credentials: 'include',
                      cache: 'no-store'
                    });
                    if (c.status !== 200) {
                      var cBody = await c.text();
                      var isCf = isCloudflareChallenge(c, cBody);
                      callBridge('onAuthResult', JSON.stringify({
                        phase: isCf ? 'cf_needed' : 'csrf',
                        status: c.status,
                        body: cBody,
                        hcaptchaToken: isCf ? hcaptchaToken : null
                      }));
                      return;
                    }
                    var csrf = (await c.json()).csrf;

                    // 勾选后先换 h_captcha_temp_id（cookie 由内核自动带上）。
                    if (hcaptchaToken) {
                      var endpoints = ['/captcha/hcaptcha/create.json', '/hcaptcha/create.json'];
                      var hOk = false;
                      var hLast = null;
                      for (var i = 0; i < endpoints.length; i++) {
                        var ep = endpoints[i];
                        try {
                          var h = await fetch(ep, {
                            method: 'POST',
                            credentials: 'include',
                            headers: {
                              'Content-Type': 'application/x-www-form-urlencoded',
                              'X-CSRF-Token': csrf,
                              'X-Requested-With': 'XMLHttpRequest'
                            },
                            body: 'token=' + encodeURIComponent(hcaptchaToken)
                          });
                          var hText = await h.text();
                          if (isCloudflareChallenge(h, hText)) {
                            callBridge('onAuthResult', JSON.stringify({
                              phase: 'cf_needed',
                              status: h.status,
                              body: hText,
                              hcaptchaToken: hcaptchaToken,
                              captchaAccepted: false
                            }));
                            return;
                          }
                          hLast = { endpoint: ep, status: h.status, body: hText };
                          if (h.status === 200) { hOk = true; break; }
                          if (h.status !== 404) break;
                        } catch (e) {
                          hLast = { endpoint: ep, status: 0, body: String(e) };
                        }
                      }
                      if (!hOk) {
                        callBridge('onAuthResult', JSON.stringify({
                          phase: 'hcaptcha',
                          status: hLast ? hLast.status : 0,
                          body: JSON.stringify(hLast)
                        }));
                        return;
                      }
                    }

                    // 最后一个参数不传时站点默认发"登录链接"邮件。
                    var r = await fetch('/u/email-login.json', {
                      method: 'POST',
                      credentials: 'include',
                      headers: {
                        'Content-Type': 'application/json',
                        'X-CSRF-Token': csrf,
                        'X-Requested-With': 'XMLHttpRequest',
                        'Accept': 'application/json'
                      },
                      body: JSON.stringify({ login: email })
                    });
                    var rText = await r.text();
                    if (isCloudflareChallenge(r, rText)) {
                      callBridge('onAuthResult', JSON.stringify({
                        phase: 'cf_needed',
                        status: r.status,
                        body: rText,
                        hcaptchaToken: hcaptchaToken,
                        captchaAccepted: false
                      }));
                      return;
                    }
                    if (r.status === 403) {
                      // 站内人机验证未通过：让页面重新渲染一次勾选框。
                      callBridge('onAuthResult', JSON.stringify({ phase: 'hcaptcha', status: r.status, body: rText }));
                      return;
                    }
                    if (r.status !== 200) {
                      callBridge('onAuthResult', JSON.stringify({ phase: 'exception', status: r.status, body: rText }));
                      return;
                    }
                    callBridge('onAuthResult', JSON.stringify({ phase: 'email_sent', status: r.status, body: rText }));
                  } catch (e) {
                    callBridge('onAuthResult', JSON.stringify({ phase: 'exception', status: 0, body: String(e) }));
                  }
                }
                window.__linuxdoRunLogin = ${if (emailMode) "runEmailSend" else "runLogin"};
                window.__linuxdoShowSubmitting = showSubmitting;
              </script>
              <script src="https://js.hcaptcha.com/1/api.js" async defer></script>
            </body>
            </html>
        """.trimIndent()

        // 调用时 WebView 已长期挂在 AppShell 中且处于主线程。直接导航能避开弹窗
        // 切换期间 View.post 回调被旧父容器丢弃的问题。
        pendingAuthHtml = html
        pendingEmailLink = null
        authWebView?.let { loadAuthHtml(it, html) }
    }

    /**
     * 邮件链接兑换：对齐 Fluxdo 的 WebViewLoginPage —— 不在原生栈里模拟请求，
     * 而是让认证 WebView 整页打开完整链接，由站点自己完成 CF/人机验证并种下 _t。
     *
     * 这是兑换链路的兜底：原生 POST /session/email-login/:token 被站点拦下时，
     * 整页导航往往还能过（真实浏览器指纹 + 站点自己的验证页）。
     */
    fun loadEmailLinkLogin(
        url: String,
        isDark: Boolean = false,
        onResult: (success: Boolean, errorMsg: String?, need2FA: Boolean) -> Unit,
    ) {
        onAuthCallback = onResult
        cachedEmailSend = null
        isDarkCached = isDark
        isInCfMode = false
        pendingAuthHtml = null
        authHtmlInjected = false
        pendingEmailLink = url
        emailLinkCompleting = true
        emailLinkRechecksRemaining = EMAIL_LINK_RECHECK_TIMES
        diagnostics.log("改用 WebView 整页打开邮件登录链接: $url")
        authWebView?.let { view ->
            view.loadUrl(url)
        }
    }

    /**
     * 整页链接登录的结果判定：只看是否真的落到了会话 Cookie _t。
     *
     * 兑换是"导航 → 302 → Set-Cookie → 跳首页"一长串跳转，首个 onPageFinished
     * 触发时 Cookie 往往还没被内核写进 CookieManager。这里先与 WebView 同步一次，
     * 没拿到就短间隔复查若干轮，而不是一次判死。
     */
    private fun checkEmailLinkLogin(view: WebView, url: String?) {
        if (!emailLinkCompleting || pendingEmailLink == null) return
        CookieManager.getInstance().flush()
        val hasSession = runCatching { store.syncFromWebView().containsKey(Config.SESSION_COOKIE) }
            .getOrDefault(false)
        if (hasSession) {
            emailLinkCompleting = false
            pendingEmailLink = null
            diagnostics.log("邮件链接已在 WebView 内完成登录，会话 Cookie 已同步")
            onAuthCallback?.invoke(true, null, false)
            return
        }
        if (emailLinkRechecksRemaining > 0) {
            emailLinkRechecksRemaining--
            view.postDelayed(
                {
                    if (emailLinkCompleting && authWebView === view) {
                        checkEmailLinkLogin(view, url)
                    }
                },
                EMAIL_LINK_RECHECK_INTERVAL_MS,
            )
            return
        }
        emailLinkCompleting = false
        diagnostics.log("邮件链接页面已停止跳转但仍无会话 Cookie: ${url.orEmpty()}")
        onAuthCallback?.invoke(false, "登录链接未能完成登录，请重新获取验证码后重试", false)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportMultipleWindows(false)
            userAgentString = userAgent
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
    }

    /**
     * 先对站点做一次真实导航,再把内联登录页 document.write 进这个第一方文档。
     *
     * 不能直接 loadDataWithBaseURL:那样文档从未真正访问过站点,它发出的 fetch 在
     * Cloudflare 眼里与真实页面不是同一个主体(Sec-Fetch-Site / Origin / Referer 都不同,
     * opaque 上下文下 Origin 可能是 null),于是出现过"真实导航 /challenge 被直接放行、
     * 同一个 CookieManager 下的 fetch 却 403"的分裂。先导航再注入之后,
     * 登录用的三个 fetch 与签发 cf_clearance 的那次导航共享同一个第一方上下文。
     *
     * 附带收益:这次导航本身要过 Cloudflare。需要出盾时盾就落在登录窗口里,
     * 用户先过盾再见九宫格 —— 与 Fluxdo 先 CF 后 hCaptcha 的主路径同形,
     * 且不必改动登录入口。
     */
    private fun loadAuthHtml(view: WebView, html: String) {
        pendingAuthHtml = html
        authHtmlInjected = false
        diagnostics.log("独立登录 WebView 正在对 $AUTH_BOOTSTRAP_PATH 做第一方导航,随后注入内联 hCaptcha 页面")
        view.setBackgroundColor(Color.TRANSPARENT)
        view.loadUrl("${Config.BASE_URL}$AUTH_BOOTSTRAP_PATH")
    }

    /** 退回旧的 data URL 方式。bootstrap 导航失败时宁可降级,也不能让登录整体不可用。 */
    private fun loadAuthHtmlInline(view: WebView, reason: String) {
        val html = pendingAuthHtml ?: return
        if (authHtmlInjected) return
        authHtmlInjected = true
        diagnostics.log("bootstrap 导航不可用($reason),回退到 data URL 方式加载内联登录页")
        view.setBackgroundColor(Color.TRANSPARENT)
        view.loadDataWithBaseURL(Config.BASE_URL, html, "text/html", "utf-8", "${Config.BASE_URL}/login")
    }

    /**
     * bootstrap 导航完成后决定是否注入内联登录页。
     *
     * 探测结果同时写进诊断日志:origin 能直接证实/证伪"opaque 上下文"的猜测,
     * document.cookie 与宿主侧 CookieManager 的对照能区分"根本没有 clearance"
     * 和"有 clearance 但页面上下文带不上"这两种完全不同的根因。
     */
    private fun maybeInjectAuthHtml(view: WebView, url: String?) {
        val html = pendingAuthHtml ?: return
        if (authHtmlInjected) return
        if (!url.orEmpty().startsWith(Config.BASE_URL)) return

        view.evaluateJavascript(AUTH_CONTEXT_PROBE_JS) { raw ->
            if (authWebView !== view || authHtmlInjected) return@evaluateJavascript
            val probe = runCatching { JSONObject(decodeJsValue(raw)) }.getOrNull()
            val origin = probe?.optString("origin").orEmpty()
            val cookie = probe?.optString("cookie").orEmpty()
            val isChallenge = probe?.optBoolean("challenge") == true
            val hostCookie = CookieManager.getInstance().getCookie(Config.BASE_URL).orEmpty()
            diagnostics.log(
                "登录上下文探测: origin=$origin challenge=$isChallenge " +
                    "宿主侧Cookie=[${cookieNamesOf(hostCookie)}] " +
                    "clearance=${clearanceFingerprint(currentCfClearance())} " +
                    // cf_clearance 与 _forum_session 都是 HttpOnly,JS 永远读不到它们,
                    // 这一项只用来确认脚本确实跑在站点上下文里,不能当作"带不上 Cookie"的判据。
                    "页面非HttpOnly可见=[${cookieNamesOf(cookie)}]",
            )

            when {
                // 盾就在这个窗口里,此刻注入会把挑战页冲掉。按兵不动,
                // 等 CF 自己放行后重新导航回 bootstrap 页,下一次 onPageFinished 再注入。
                isChallenge -> diagnostics.log("bootstrap 导航遇到 Cloudflare 挑战,等待用户在登录窗口内完成验证")
                origin == Config.BASE_URL -> {
                    authHtmlInjected = true
                    diagnostics.log("已取得站点第一方上下文,注入内联 hCaptcha 登录页")
                    view.evaluateJavascript(
                        "(function(){document.open();document.write(${JSONObject.quote(html)});document.close();})();",
                        null,
                    )
                }
                else -> loadAuthHtmlInline(view, "origin=$origin")
            }
        }
    }

    private fun cookieNamesOf(header: String): String =
        SessionStore.parseCookieHeader(header).keys.joinToString(",")

    /**
     * clearance 的值指纹。
     * 日志里需要能区分"同一枚旧凭据"和"CF 刚换发的新凭据",但不该把完整凭据写进文件。
     */
    private fun clearanceFingerprint(value: String?): String =
        if (value.isNullOrBlank()) "无" else "${value.take(8)}…(${value.length}字符)"


    /**
     * 与 Fluxdo 的登录对话框一致：每次认证使用独立的可见 WebView，
     * 不复用全局后台 WebView 的页面、连接与导航状态。
     */
    fun attachAuth(activityContext: Context): WebView {
        check(Looper.myLooper() == Looper.getMainLooper()) { "WebView 只能在主线程访问" }
        attachedActivityContext = activityContext
        authWebView?.let { view ->
            (view.context as? MutableContextWrapper)?.baseContext = activityContext
            (view.parent as? ViewGroup)?.removeView(view)
            return view
        }

        val view = WebView(MutableContextWrapper(appContext))
        (view.context as MutableContextWrapper).baseContext = activityContext
        configureWebView(view)
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val urlString = request?.url?.toString().orEmpty()
                if (urlString.startsWith("discourse://auth_redirect")) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, request?.url).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        activityContext.startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        diagnostics.log("拦截 discourse 网页授权跳转失败: ${e.message}")
                    }
                }
                return super.shouldOverrideUrlLoading(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                Log.d(TAG, "登录 onPageStarted: $url")
                // 重新发起 bootstrap 导航(含 CF 放行后的回跳)时解锁注入,
                // 否则过完盾回到 bootstrap 页就永远等不到注入了。
                if (url.orEmpty().startsWith("${Config.BASE_URL}$AUTH_BOOTSTRAP_PATH")) {
                    authHtmlInjected = false
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                CookieManager.getInstance().flush()
                Log.d(TAG, "登录 onPageFinished: $url")
                view?.let { maybeInjectAuthHtml(it, url) }
                view?.let { checkEmailLinkLogin(it, url) }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?,
            ) {
                diagnostics.log(
                    "登录 WebView HTTP 错误 [${errorResponse?.statusCode ?: 0}]: ${request?.url}" +
                        cfMitigatedSuffix(errorResponse),
                )
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                diagnostics.log(
                    "登录 WebView 资源错误 [${error?.errorCode ?: 0}: ${error?.description}]: ${request?.url}",
                )
                // 只有主框架的 bootstrap 导航失败才降级,子资源错误(图片/脚本)不算。
                if (request?.isForMainFrame == true && view != null && !authHtmlInjected) {
                    loadAuthHtmlInline(view, "导航错误 ${error?.errorCode ?: 0}")
                }
            }
        }
        view.webChromeClient = createLoggingChromeClient("登录")
        view.addJavascriptInterface(Bridge(), BRIDGE_NAME)
        authWebView = view
        val link = pendingEmailLink
        if (link != null) {
            // 链接兑换：直接整页导航，等 onPageFinished 判定 _t 是否落地。
            emailLinkCompleting = true
            view.loadUrl(link)
        } else {
            pendingAuthHtml?.let { loadAuthHtml(view, it) }
        }
        return view
    }

    /**
     * 2FA 输入页会暂时卸下认证 WebView，但保留它的 h_captcha_temp_id；
     * 其他收场路径则彻底销毁，下一次登录从全新的 WebView 开始。
     */
    fun detachAuth(view: WebView) {
        if (authWebView !== view) return
        (view.context as? MutableContextWrapper)?.baseContext = appContext
        if (awaitingSecondFactorLogin) return
        authWebView = null
        pendingAuthHtml = null
        authHtmlInjected = false
        view.stopLoading()
        view.removeJavascriptInterface(BRIDGE_NAME)
        view.destroy()
    }

    private fun createLoggingChromeClient(scope: String): WebChromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            val msg = consoleMessage?.message().orEmpty()
            val source = consoleMessage?.sourceId().orEmpty()
            val line = consoleMessage?.lineNumber() ?: 0
            val level = consoleMessage?.messageLevel()
            diagnostics.log("$scope WebView Console [$level] ($source:$line): $msg")
            return true
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun webView(): WebView {
        check(Looper.myLooper() == Looper.getMainLooper()) { "WebView 只能在主线程访问" }
        webViewInstance?.let { return it }

        // 开启 Chrome 开发者工具远程调试支持（可通过电脑 chrome://inspect 深入审查页面与控制台）
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        val webView = WebView(MutableContextWrapper(appContext))
        configureWebView(webView)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                Log.d(TAG, "onPageStarted: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                finishedCount++
                CookieManager.getInstance().flush()
                Log.d(TAG, "onPageFinished: $url")
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: android.net.http.SslError?,
            ) {
                // 安全规范：正式构建必须严格校验 SSL 证书，杜绝放行所有 SSL 错误带来的中间人攻击安全隐患。
                val errUrl = error?.url.orEmpty()
                val primaryErr = error?.primaryError ?: -1
                diagnostics.log("WebView SSL 错误 [$primaryErr]: $errUrl")
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "SSL 错误已忽略(DEBUG): $primaryErr")
                    handler?.proceed()
                } else {
                    handler?.cancel()
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?,
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                val url = request?.url?.toString().orEmpty()
                val status = errorResponse?.statusCode ?: 0
                val reason = errorResponse?.reasonPhrase.orEmpty()
                diagnostics.log("WebView HTTP 错误 [$status $reason]: $url")
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                val url = request?.url?.toString().orEmpty()
                val code = error?.errorCode ?: 0
                val desc = error?.description?.toString().orEmpty()
                diagnostics.log("WebView 资源错误 [$code: $desc]: $url")
            }
        }
        webView.webChromeClient = createLoggingChromeClient("登录")
        webView.addJavascriptInterface(Bridge(), BRIDGE_NAME)
        webViewInstance = webView
        return webView
    }

    /** 挂到界面时把 Context 换成 Activity,键盘/弹窗才正常;必要时先从旧父容器摘下。 */
    fun attach(activityContext: Context): WebView {
        val webView = webView()
        attachedActivityContext = activityContext
        (webView.context as? MutableContextWrapper)?.baseContext = activityContext
        (webView.parent as? ViewGroup)?.removeView(webView)
        return webView
    }

    /** 从界面卸下时还原为 Application Context,避免泄漏 Activity。 */
    fun detach() {
        (webViewInstance?.context as? MutableContextWrapper)?.baseContext = appContext
    }

    suspend fun openUrl(url: String) = withContext(Dispatchers.Main) {
        val view = webView()
        awaitFirstLayout(view)
        view.loadUrl(url)
    }

    /**
     * 等 WebView 完成首次布局再加载页面。
     *
     * AndroidView 的 factory 刚创建出 WebView 时还没测量,宽高是 0。此时 loadUrl,
     * 像 Cloudflare 挑战页那种用 `100vh` + flex 垂直居中的静态页面会按 0 高度算出
     * "中心",把内容全堆在顶部;等 WebView 测量完成变大,页面不监听 resize 也就不会重排,
     * 于是看到的就是内容偏上、顶部元素被切掉半截。
     */
    private suspend fun awaitFirstLayout(view: WebView) {
        if (view.width > 0 && view.height > 0) return
        withTimeoutOrNull(LAYOUT_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : View.OnLayoutChangeListener {
                    override fun onLayoutChange(
                        v: View?, left: Int, top: Int, right: Int, bottom: Int,
                        oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int,
                    ) {
                        if (right - left <= 0 || bottom - top <= 0) return
                        view.removeOnLayoutChangeListener(this)
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
                view.addOnLayoutChangeListener(listener)
                continuation.invokeOnCancellation { view.removeOnLayoutChangeListener(listener) }
            }
        }
    }

    suspend fun currentUrl(): String? = withContext(Dispatchers.Main) { webView().url }

    suspend fun currentTitle(): String = evalString("document.title")

    /** 当前页已在站内且不再是 Cloudflare 挑战页。 */
    suspend fun isPageCleared(): Boolean {
        val host = currentUrl()?.let { Uri.parse(it).host }
        if (host != Config.HOST) return false
        val title = currentTitle()
        return title.isNotBlank() && !ChallengeDetector.isChallengeTitle(title)
    }

    /**
     * 静默重验证:在 WebView 里重新加载首页,等待挑战自动通过,并把最新 Cookie 同步回加密存储。
     * 超时返回 false,由调用方决定是否转入可见的交互验证。
     */
    suspend fun clearChallenge(reason: String): Boolean = solveMutex.withLock {
        diagnostics.log("静默重验证开始: $reason")
        val finishedBefore = finishedCount
        openUrl("${Config.BASE_URL}/")
        val deadline = SystemClock.elapsedRealtime() + Config.CHALLENGE_TIMEOUT_MS
        var cleared = false
        while (SystemClock.elapsedRealtime() < deadline) {
            delay(POLL_INTERVAL_MS)
            if (finishedCount > finishedBefore && isPageCleared()) {
                cleared = true
                break
            }
        }
        withContext(Dispatchers.Main) { store.syncFromWebView() }
        diagnostics.log(if (cleared) "静默重验证成功" else "静默重验证超时")
        cleared
    }

    /** 路径 B:在已通过挑战的页面内用 fetch() 请求,走真实 Chrome 网络栈。 */
    suspend fun fetchJson(
        path: String,
        csrf: String? = null,
        method: String = "GET",
        body: String? = null,
        contentType: String = "application/json; charset=utf-8",
        present: Boolean = true,
    ): TransportResult {
        if (!isPageCleared()) clearChallenge("fetch 前置检查")
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<TransportResult>()
        pending[id] = deferred
        withContext(Dispatchers.Main) {
            webView().evaluateJavascript(buildFetchScript(id, path, csrf, method, body, contentType, present), null)
        }
        val result = withTimeoutOrNull(Config.FETCH_TIMEOUT_MS) { deferred.await() }
        pending.remove(id)
        return result ?: TransportResult(0, "WebView fetch 超时", false)
    }

    suspend fun reset() = withContext(Dispatchers.Main) {
        val webView = webView()
        webView.loadUrl("about:blank")
        webView.clearCache(true)
        webView.clearHistory()
    }

    private fun buildFetchScript(
        id: String,
        path: String,
        csrf: String?,
        method: String,
        body: String?,
        contentType: String,
        present: Boolean = true,
    ): String = """
        (function () {
          var id = ${JSONObject.quote(id)};
          fetch(${JSONObject.quote(path)}, {
            method: ${JSONObject.quote(method)},
            ${if (body == null) "" else "body: ${JSONObject.quote(body)},"}
            credentials: 'include',
            headers: {
              'Accept': 'application/json',
              'X-Requested-With': 'XMLHttpRequest'
              ${if (present) ", 'Discourse-Present': 'true'" else ""}
              ${if (body == null) "" else ", 'Content-Type': " + JSONObject.quote(contentType)}
              ${if (csrf == null) "" else ", 'X-CSRF-Token': " + JSONObject.quote(csrf)}
            }
          }).then(function (r) {
            return r.text().then(function (t) {
              $BRIDGE_NAME.onResult(id, r.status, r.headers.get('cf-mitigated') || '', t);
            });
          }).catch(function (e) {
            $BRIDGE_NAME.onResult(id, 0, '', String(e));
          });
        })();
    """.trimIndent()

    private suspend fun evalString(script: String): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            webView().evaluateJavascript(script) { raw -> continuation.resume(decodeJsValue(raw)) }
        }
    }

    private fun decodeJsValue(raw: String?): String {
        if (raw == null || raw == "null") return ""
        return try {
            JSONTokener(raw).nextValue()?.toString().orEmpty()
        } catch (error: JSONException) {
            raw
        }
    }

    private companion object {
        const val TAG = "BrowserSession"
        const val BRIDGE_NAME = "__ldb"
        const val CF_CLEARANCE_COOKIE = "cf_clearance"
        const val CF_CHALLENGE_PATH = "/challenge"

        /**
         * 登录页的第一方 bootstrap 路径。
         * 取站点上最轻的静态资源:只是为了拿到真实的 linux.do 文档上下文,不需要它的内容。
         */
        const val AUTH_BOOTSTRAP_PATH = "/robots.txt"

        const val CF_SETTLE_DELAY_MS = 1_500L
        const val CF_PROBE_INTERVAL_MS = 750L

        /** 邮件链接兑换时，首个 onPageFinished 之后还要复查几轮 Cookie。 */
        const val EMAIL_LINK_RECHECK_TIMES = 15
        const val EMAIL_LINK_RECHECK_INTERVAL_MS = 800L

        /** 等 Chromium 把 Set-Cookie 落到 CookieManager 的时间。 */
        const val CF_COOKIE_SETTLE_DELAY_MS = 300L

        /** 判定"已回源"后，等待新 cf_clearance 出现的上限，对齐 Fluxdo 的完成探测窗口。 */
        const val CF_COMPLETION_PROBE_TIMEOUT_MS = 8_000L

        /** 收口前要求连续多少轮查不到挑战标记，用来挡掉 CF 完成跳转瞬间的空窗。 */
        const val CF_QUIET_ROUNDS_REQUIRED = 2

        const val HCAPTCHA_CALLBACK_SETTLE_DELAY_MS = 300L
        const val POLL_INTERVAL_MS = 700L
        const val LAYOUT_TIMEOUT_MS = 1_500L

        /** 探测登录 WebView 的真实执行上下文,顺带回报是否正停在 CF 挑战页。 */
        const val AUTH_CONTEXT_PROBE_JS = """
            (function () {
              var html = '';
              try { html = (document.documentElement ? document.documentElement.innerHTML : '').toLowerCase(); } catch (e) {}
              var title = (document.title || '').toLowerCase();
              var challenge = html.indexOf('cf_chl_opt') !== -1 ||
                html.indexOf('challenge-platform') !== -1 ||
                title.indexOf('just a moment') !== -1;
              return JSON.stringify({
                origin: window.location.origin,
                href: window.location.href,
                challenge: challenge,
                cookie: document.cookie || ''
              });
            })();
        """

        /**
         * 页面上是否还有活跃的 Cloudflare 挑战,对齐 Fluxdo 的 hasActiveCfChallenge。
         * 新 clearance 可能在 JS challenge 跑完之前就落下来,只有这些标记都消失才算真的过了。
         */
        const val ACTIVE_CHALLENGE_PROBE_JS = """
            (function () {
              var html = '';
              try { html = document.documentElement ? document.documentElement.innerHTML : ''; } catch (e) {}
              return html.indexOf('cf-turnstile') !== -1 ||
                html.indexOf('challenge-running') !== -1 ||
                html.indexOf('challenge-stage') !== -1 ||
                html.indexOf('cf_chl_opt') !== -1;
            })();
        """

        /**
         * 源站 404 的特征集,对齐 Fluxdo 的 isOriginNotFound。
         * 刻意不匹配裸 "404" —— 论坛标题、帖子内容里出现这三个字符太常见,会把
         * 仍在验证中的页面误判成已回源。
         */
        const val ORIGIN_NOT_FOUND_PROBE_JS = """
            (function () {
              var html = '';
              try { html = (document.documentElement ? document.documentElement.innerHTML : '') + ' ' + (document.title || ''); } catch (e) {}
              html = html.toLowerCase();
              return html.indexOf('page-not-found') !== -1 ||
                html.indexOf('discourse-no-results') !== -1 ||
                html.indexOf('"errortype":"notfound"') !== -1 ||
                html.indexOf('404-body') !== -1 ||
                html.indexOf('exist or is private') !== -1 ||
                html.indexOf('page you requested') !== -1;
            })();
        """
    }
}
