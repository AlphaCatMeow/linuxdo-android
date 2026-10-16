package org.linuxdo.android.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.linuxdo.android.App
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * 应用内下载并安装新版本 APK。
 *
 * Android 不允许第三方应用静默安装,所以这里只负责把安装包取到本地并拉起系统安装器,
 * 确认弹窗与首次「安装未知应用」授权都必须由用户完成。
 */
object ApkUpdateInstaller {

    private const val APK_DIR = "apk"
    private const val MIME_APK = "application/vnd.android.package-archive"

    /** 进度回调的最小间隔字节数,避免每读一个 8KB 块就触发一次重组。 */
    private const val PROGRESS_STEP_BYTES = 128 * 1024L

    /** 下载响应未给 Content-Length 时的总长占位,UI 据此退化为不确定进度。 */
    const val TOTAL_UNKNOWN = -1L

    sealed interface Result {
        data class Success(val file: File) : Result
        data class Failure(val message: String) : Result
    }

    /**
     * 流式下载安装包到 `cacheDir/apk/`,边下边算 SHA-256。
     *
     * [expectedSha256] 为空时跳过校验(发布清单没填就不阻塞升级);
     * 填了但不匹配则删除临时文件并返回失败,绝不把来源不明的包交给安装器。
     */
    suspend fun download(
        context: Context,
        url: String,
        expectedSha256: String?,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val diagnostics = (appContext as? App)?.container?.diagnostics
        val target = runCatching { prepareTargetFile(appContext, url) }.getOrElse { error ->
            return@withContext Result.Failure("无法准备下载目录：${error.javaClass.simpleName}")
        }

        try {
            val response = buildDownloadClient(appContext)
                .newCall(Request.Builder().url(url).get().build())
                .execute()

            response.use {
                if (!it.isSuccessful) {
                    target.delete()
                    diagnostics?.log("[应用内更新] 下载失败 HTTP=${it.code} url=$url")
                    return@withContext Result.Failure("下载失败（HTTP ${it.code}）")
                }
                val body = it.body ?: run {
                    target.delete()
                    return@withContext Result.Failure("下载失败：响应为空")
                }

                val total = body.contentLength().takeIf { length -> length > 0L } ?: TOTAL_UNKNOWN
                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                var lastReported = 0L
                onProgress(0L, total)

                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            // 协程被取消(用户关闭对话框)时立刻跳出,由 catch 清理半成品。
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read
                            if (downloaded - lastReported >= PROGRESS_STEP_BYTES) {
                                lastReported = downloaded
                                onProgress(downloaded, total)
                            }
                        }
                        output.flush()
                    }
                }
                onProgress(downloaded, total)

                if (downloaded <= 0L) {
                    target.delete()
                    return@withContext Result.Failure("下载失败：未取到任何数据")
                }

                val expected = expectedSha256?.trim()?.lowercase()?.takeIf { hash -> hash.isNotEmpty() }
                if (expected != null) {
                    val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
                    if (actual != expected) {
                        target.delete()
                        diagnostics?.log("[应用内更新] SHA256 校验失败 期望=$expected 实际=$actual")
                        return@withContext Result.Failure("安装包校验失败，已终止安装。请改用浏览器下载。")
                    }
                }

                diagnostics?.log(
                    "[应用内更新] 下载完成 ${downloaded}B 校验=${if (expected != null) "通过" else "未提供"} 文件=${target.name}",
                )
                Result.Success(target)
            }
        } catch (error: Throwable) {
            target.delete()
            // CancellationException 要原样抛出,否则协程取消会被当成普通失败。
            if (error is kotlinx.coroutines.CancellationException) throw error
            diagnostics?.log("[应用内更新] 下载异常 ${error.javaClass.simpleName}: ${error.message.orEmpty().take(160)}")
            Result.Failure("下载失败：${error.javaClass.simpleName}")
        }
    }

    /**
     * 共享客户端的读超时按图片请求调的(25 秒),对弱网下载整包偏紧,这里派生一份放宽。
     * 派生会复用连接池与拦截器,其中 SessionHeaderInterceptor 已对非论坛域名剥离
     * Cookie 与 X-CSRF-Token,所以下载 GitHub 直链不会带上论坛会话。
     */
    private fun buildDownloadClient(appContext: Context): OkHttpClient {
        val shared = (appContext as? App)?.container?.httpClient
        val builder = shared?.newBuilder() ?: OkHttpClient.Builder()
        return builder
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    /** 清空旧安装包后返回本次写入的目标文件。 */
    private fun prepareTargetFile(appContext: Context, url: String): File {
        val dir = File(appContext.cacheDir, APK_DIR)
        if (!dir.exists()) dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        return File(dir, resolveFileName(url))
    }

    /** 用链接末段作为文件名,系统安装器会展示它;非法字符一律剔除。 */
    private fun resolveFileName(url: String): String {
        val raw = runCatching { Uri.parse(url).lastPathSegment }.getOrNull().orEmpty()
        val safe = raw.replace(Regex("[^A-Za-z0-9._-]"), "")
        return when {
            safe.endsWith(".apk", ignoreCase = true) && safe.length > 4 -> safe
            safe.isNotEmpty() -> "$safe.apk"
            else -> "update.apk"
        }
    }

    /** Android 8.0 起安装来源是单独的特殊权限,与 Manifest 声明无关。 */
    fun canInstall(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
    }

    /** 跳转到本应用的「安装未知应用」开关页。 */
    fun unknownSourceSettingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    /**
     * 把下载好的包交给系统安装器。返回 false 表示连安装界面都没拉起来,
     * 此时调用方应退回浏览器下载。
     */
    fun install(context: Context, file: File): Boolean {
        if (!file.exists() || file.length() <= 0L) return false
        return runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, MIME_APK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrElse { error ->
            (context.applicationContext as? App)?.container?.diagnostics
                ?.log("[应用内更新] 拉起安装器失败 ${error.javaClass.simpleName}: ${error.message.orEmpty().take(160)}")
            false
        }
    }
}
