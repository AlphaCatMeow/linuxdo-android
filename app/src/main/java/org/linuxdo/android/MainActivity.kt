package org.linuxdo.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import org.linuxdo.android.ui.AppShell
import org.linuxdo.android.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.toString()?.let { uri ->
            viewModel.handleWebAuthCallback(uri)
        }
        handleNotificationIntent(intent)
        val browser = (application as App).container.browser
        setContent {
            AppShell(viewModel, browser)
        }
    }

    override fun onResume() {
        super.onResume()
        val sessionStore = (application as App).container.sessionStore
        sessionStore.setAppInForeground(true)
        // 回到前台后由前台服务的协程轮询接管，撤掉最小化兜底闹钟
        org.linuxdo.android.service.NotificationKeepAliveService.cancelMinimizedPoll(applicationContext)
        if (sessionStore.isBackgroundNotificationEnabled() && sessionStore.hasSession()) {
            org.linuxdo.android.service.NotificationKeepAliveService.start(applicationContext)
            if (!org.linuxdo.android.service.NotificationHelper.isIgnoringBatteryOptimizations(this) &&
                !sessionStore.hasAutoPromptedBatteryWhitelist()
            ) {
                sessionStore.markAutoPromptedBatteryWhitelist()
                org.linuxdo.android.service.NotificationHelper.promptIgnoreBatteryOptimizationsIfNeeded(this)
            }
        }
        viewModel.refreshUnread(force = true)
    }

    override fun onStop() {
        super.onStop()
        val appContext = applicationContext
        val sessionStore = (application as App).container.sessionStore
        sessionStore.setAppInForeground(false)
        // 退回桌面（最小化）时立即触发一次即时后台未读检查，不留时间空隙
        org.linuxdo.android.service.NotificationWorker.triggerImmediate(appContext)
        // 亮屏最小化：预约兜底闹钟，补上「亮屏时 Doze 闹钟被取消、只剩协程」的缺口。
        // 息屏场景由 handleScreenOff 的 Doze 阶梯接管，这里不抢。
        if (org.linuxdo.android.service.NotificationKeepAliveService.isScreenInteractive(appContext)) {
            org.linuxdo.android.service.NotificationKeepAliveService.scheduleMinimizedPoll(appContext)
        }
        // 荣耀 MagicOS / 华为桌面在用户打开过应用界面后会自动清空桌面图标角标；
        // 退回桌面 (onStop) 时必须把当前未读数重新写回桌面 Provider，并延迟补写一次确保桌面 onResume 后生效。
        val badge = sessionStore.getLastBadgeCount()
        org.linuxdo.android.service.NotificationHelper.updateBadgeCount(appContext, badge, source = "退出前台")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val latest = sessionStore.getLastBadgeCount()
            org.linuxdo.android.service.NotificationHelper.updateBadgeCount(appContext, latest, source = "退出前台400ms")
        }, 400L)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.toString()?.let { uri ->
            viewModel.handleWebAuthCallback(uri)
        }
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val topicId = intent.getLongExtra("topic_id", -1L)
        val notificationId = intent.getLongExtra("notification_id", -1L).takeIf { it > 0L }
        val notificationType = intent.getIntExtra("notification_type", -1).takeIf { it > 0 }
        val postNumber = intent.getIntExtra("post_number", -1).takeIf { it > 0 }

        intent.removeExtra("topic_id")
        intent.removeExtra("notification_id")
        intent.removeExtra("notification_type")
        intent.removeExtra("post_number")

        if (topicId > 0L) {
            viewModel.navigateToTopicFromNotification(topicId, postNumber, notificationId, notificationType)
        } else if (notificationId != null) {
            viewModel.readNotification(notificationId, notificationType)
        }
    }
}
