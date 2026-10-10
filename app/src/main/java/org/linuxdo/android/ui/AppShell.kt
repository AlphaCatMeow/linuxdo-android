package org.linuxdo.android.ui

import android.app.Dialog
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView as AndroidTextView
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import org.linuxdo.android.net.BrowserSession
import org.linuxdo.android.Config
import org.linuxdo.android.ui.design.IosActivityIndicator
import org.linuxdo.android.ui.design.IosMetrics
import org.linuxdo.android.ui.design.IosTheme
import org.linuxdo.android.ui.nav.IosNavHost
import org.linuxdo.android.ui.nav.NavStack
import org.linuxdo.android.ui.nav.Route
import org.linuxdo.android.ui.screen.TopicListScreen
import org.linuxdo.android.ui.screen.TopicDetailScreen
import org.linuxdo.android.ui.screen.CategoryListScreen
import org.linuxdo.android.ui.screen.SearchScreen
import org.linuxdo.android.ui.screen.TopicListUiState
import org.linuxdo.android.ui.screen.LoginScreen
import org.linuxdo.android.ui.nav.MainTab
import org.linuxdo.android.ui.nav.TabBar

/**
 * 应用外壳。
 *
 * 分层顺序是硬约束:全局单例 WebView 必须留在导航之外的最外层,靠 zIndex 切换显隐。
 * Login 屏使用原生 Compose，安全验证只在独立对话框中显示轻量 WebView。
 */
@Composable
fun AppShell(viewModel: MainViewModel, browser: BrowserSession) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loginUiState by viewModel.loginUiState.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    // 全局 WebView 在 CF 验证时显示；登录人机验证使用独立对话框。
    val showWeb = state.screen == Screen.Verify
    val showWebLoginModal = state.screen == Screen.Login && loginUiState.showWebLoginDialog
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(lifecycle, state.screen, state.latest.initialLoading) {
        if (state.screen == Screen.List && !state.latest.initialLoading) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.refreshUnread(force = true)
            while (true) { delay(Config.UNREAD_POLL_INTERVAL_MS); viewModel.refreshUnread() }
        }
    }

    LaunchedEffect(showWeb) {
        if (showWeb) {
            while (true) {
                delay(1_000)
                viewModel.pollVerification()
            }
        }
    }

    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    IosTheme(dark = dark) {
        Box(
            Modifier
                .fillMaxSize()
                .background(IosTheme.colors.groupedBackground)
                .systemBarsPadding()
                .imePadding()
                .clipToBounds(),
        ) {
            // 验证期间保留导航的组合身份，返回后恢复原页面与滚动位置。
            if (state.username != null || state.screen == Screen.List) {
                key(state.username) { MainNavigation(state, viewModel, lifecycle) }
            }

            if (state.screen == Screen.Boot) BootScreen()

            // 原生登录页面。
            if (state.screen == Screen.Login) {
                LoginScreen(
                    state = loginUiState,
                    onPasswordLogin = viewModel::loginWithPassword,
                    onSendEmailCode = viewModel::sendLoginEmail,
                    onEmailCodeLogin = viewModel::loginWithEmailCode,
                    onDismissError = viewModel::dismissLoginError,
                    onResetSecondFactor = viewModel::resetSecondFactor,
                    onOpenWebLogin = viewModel::openWebLoginModal,
                    onStartWebAuth = viewModel::startWebAuth,
                    onDismissRiskControl = viewModel::dismissRiskControlDialog,
                    onCancelWebAuth = viewModel::cancelWebAuth,
                )
            }

            // CF 的全屏验证和后台 WebView 使用全局页面内 AndroidView；登录人机验证在弹框中。
            if (!showWebLoginModal) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(if (showWeb) 2f else -1f)
                        .then(if (showWeb) Modifier.background(IosTheme.colors.groupedBackground) else Modifier),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier = if (showWeb) {
                            Modifier
                                .fillMaxSize()
                                .padding(top = IosMetrics.navBarHeight)
                        } else {
                            Modifier.fillMaxSize()
                        },
                    ) {
                        if (showWeb) WebTopBar(state.screen)
                        val context = LocalContext.current
                        AndroidView(
                            factory = { browser.attach(context) },
                            onRelease = { browser.detach() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }
                }
            }
            else {
                NativeWebLoginDialog(browser, viewModel::closeWebLoginModal, dark)
            }

            val updateInfo = updateState.updateInfo
            if (updateState.showDialog && updateInfo != null) {
                UpdateDialog(
                    currentVersion = updateState.currentVersion,
                    info = updateInfo,
                    onDismiss = viewModel::dismissUpdateDialog,
                )
            }
        }
    }
}

@Composable
private fun NativeWebLoginDialog(
    browser: BrowserSession,
    onClose: () -> Unit,
    dark: Boolean,
) {
    val context = LocalContext.current
    DisposableEffect(browser, context, dark) {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val surfaceColor = if (dark) AndroidColor.rgb(28, 28, 30) else AndroidColor.WHITE
        val labelColor = if (dark) AndroidColor.rgb(245, 245, 247) else AndroidColor.rgb(28, 28, 30)
        val secondaryColor = if (dark) AndroidColor.rgb(142, 142, 147) else AndroidColor.rgb(142, 142, 147)
        val dividerColor = if (dark) AndroidColor.rgb(56, 56, 58) else AndroidColor.rgb(229, 229, 234)
        val dialog = Dialog(context)
        var dismissedByComposition = false

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(surfaceColor)
                cornerRadii = floatArrayOf(
                    dp(20).toFloat(), dp(20).toFloat(),
                    dp(20).toFloat(), dp(20).toFloat(),
                    0f, 0f, 0f, 0f,
                )
            }
        }
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), 0, dp(16), 0)
        }
        val icon = AndroidTextView(context).apply {
            text = "🛡️"
            gravity = Gravity.CENTER_VERTICAL
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        }
        val title = AndroidTextView(context).apply {
            text = "完成人机验证"
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(labelColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(8), 0, 0, 0)
        }
        val close = AndroidTextView(context).apply {
            text = "✕"
            gravity = Gravity.CENTER
            setTextColor(secondaryColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (dark) AndroidColor.rgb(44, 44, 46) else AndroidColor.rgb(242, 242, 247))
            }
            setOnClickListener { dialog.dismiss() }
        }
        header.addView(icon, LinearLayout.LayoutParams(dp(32), ViewGroup.LayoutParams.MATCH_PARENT))
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        header.addView(close, LinearLayout.LayoutParams(dp(30), dp(30)))
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        root.addView(View(context).apply { setBackgroundColor(dividerColor) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))

        val webView = browser.attachAuth(context)
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener {
            if (!dismissedByComposition) onClose()
        }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.heightPixels * 0.92f).toInt(),
            )
            setGravity(Gravity.BOTTOM)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.5f }
        }

        onDispose {
            dismissedByComposition = true
            dialog.setOnDismissListener(null)
            root.removeView(webView)
            dialog.dismiss()
            browser.detachAuth(webView)
        }
    }
}

@Composable
private fun MainNavigation(state: UiState, viewModel: MainViewModel, lifecycle: Lifecycle) {
    val stacks = MainTab.entries.associateWith { tab ->
        key(tab) { rememberSaveable(saver = NavStack.Saver) { NavStack(tab.root) } }
    }
    val scrollRequests = MainTab.entries.associateWith { tab ->
        key(tab) { rememberSaveable { mutableIntStateOf(0) } }
    }
    var selected by rememberSaveable { mutableStateOf(MainTab.Latest) }
    val holder = rememberSaveableStateHolder()
    val stack = stacks.getValue(selected)
    val scrollToTopRequest = scrollRequests.getValue(selected).intValue
    val details by viewModel.details.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val categoryTopics by viewModel.categoryTopics.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val notificationFilter by viewModel.notificationFilter.collectAsStateWithLifecycle()
    val composer by viewModel.composer.collectAsStateWithLifecycle()
    val treeView by viewModel.treeView.collectAsStateWithLifecycle()
    val syncReadProgress by viewModel.syncReadProgress.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val browsing = state.screen == Screen.List
    val openNotifications = {
        if (stack.current != Route.Notifications) stack.push(Route.Notifications)
        viewModel.loadProfile(org.linuxdo.android.data.ProfileSection.Notifications, refresh = true)
        viewModel.refreshUnread(force = true)
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            holder.SaveableStateProvider(selected.name) {
                key(selected) {
                    IosNavHost(stack, interactive = browsing) { route ->
                        val active = browsing && stack.current == route
                        LaunchedEffect(route, active) {
                            if (active) when (route) {
                                is Route.TopicDetail -> viewModel.loadTopic(route.topicId, postNumber = route.postNumber)
                                Route.CategoryList -> viewModel.loadCategories()
                                is Route.CategoryTopics -> viewModel.loadCategory(route.categoryId, route.slug)
                                else -> Unit
                            }
                        }
                        when (route) {
                            Route.TopicList -> TopicListScreen(
                                title = state.homeFeed.label,
                                state = state.latest,
                                onRefresh = viewModel::refreshLatest,
                                onLoadMore = viewModel::loadMoreLatest,
                                onOpenTopic = { topic -> stack.push(Route.TopicDetail(topic.id, topic.title)) },
                                active = active,
                                scrollToTopRequest = scrollToTopRequest,
                                trailing = { UnreadBell(state.unreadCount, openNotifications) },
                                currentFeed = state.homeFeed,
                                onFeedSelect = viewModel::switchHomeFeed,
                                hasUnread = state.unreadCount > 0,
                            )

                            is Route.TopicDetail -> {
                                val lifecycleState by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
                                val isForeground = active && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
                                LaunchedEffect(route.topicId, isForeground) {
                                    viewModel.setTopicActive(route.topicId, isForeground)
                                }
                                // 离开这篇帖子时结算一次阅读进度(仅在用户开启同步后才会真正发请求)。
                                DisposableEffect(route.topicId) {
                                    onDispose {
                                        viewModel.setTopicActive(route.topicId, false)
                                        viewModel.leaveTopic(route.topicId)
                                    }
                                }
                                TopicDetailScreen(
                                    title = route.title,
                                    state = details[route.topicId] ?: DetailUiState(loading = true),
                                    onBack = { stack.pop() },
                                    onRetry = { viewModel.retryTopic(route.topicId, route.postNumber) },
                                    onRefresh = { viewModel.refreshTopic(route.topicId) },
                                    onLoadMore = { viewModel.loadTopic(route.topicId, more = true) },
                                    onLoadPrevious = { viewModel.loadTopic(route.topicId, previous = true) },
                                    initialPostNumber = route.postNumber,
                                    onUnknownTag = { viewModel.diagnostics.log("未知 cooked 标签: $it") },
                                    treeView = treeView,
                                    onLoadReplies = { viewModel.loadReplies(route.topicId, it) },
                                    onReact = { postId, reaction -> viewModel.react(route.topicId, postId, reaction) },
                                    onBoost = { postId, raw -> viewModel.createBoost(route.topicId, postId, raw) },
                                    onDeleteBoost = { postId, boostId -> viewModel.deleteBoost(route.topicId, postId, boostId) },
                                    onRefreshPost = { viewModel.refreshPost(route.topicId, it) },
                                    onSolution = { viewModel.setSolution(route.topicId, it) },
                                    composer = composer,
                                    onOpenComposer = viewModel::openComposer,
                                    onDraftChange = viewModel::updateDraft,
                                    onSubmitComposer = viewModel::submitComposer,
                                    onCloseComposer = viewModel::closeComposer,
                                    onDeletePost = { viewModel.deletePost(route.topicId, it) },
                                    onReloadFromStart = { viewModel.reloadFromStart(route.topicId) },
                                    onCreatedPostShown = { viewModel.clearCreatedPost(route.topicId) },
                                    onAttach = viewModel::attachToComposer,
                                    topicId = route.topicId,
                                    onOpenTopic = { id, title, postNumber ->
                                        stack.push(Route.TopicDetail(id, title, postNumber))
                                    },
                                    active = active,
                                    trailing = { UnreadBell(state.unreadCount, openNotifications) },
                                    onToggleOrder = { viewModel.toggleCommentOrder(route.topicId) },
                                    onPostsVisible = { viewModel.onPostsVisible(route.topicId, it) },
                                )
                            }
                            Route.CategoryList -> CategoryListScreen(categories,
                                onRetry = { viewModel.loadCategories(retry = true) },
                                onOpen = { stack.push(Route.CategoryTopics(it.id, it.slug, it.name)) },
                                scrollToTopRequest = scrollToTopRequest,
                                trailing = { UnreadBell(state.unreadCount, openNotifications) })

                            is Route.CategoryTopics -> TopicListScreen(route.name,
                                state = categoryTopics[route.categoryId] ?: TopicListUiState(),
                                onRefresh = { viewModel.loadCategory(route.categoryId, route.slug, refresh = true) },
                                onLoadMore = { viewModel.loadCategory(route.categoryId, route.slug, more = true) },
                                onOpenTopic = { stack.push(Route.TopicDetail(it.id, it.title)) },
                                onBack = { stack.pop() }, active = active,
                                trailing = { UnreadBell(state.unreadCount, openNotifications) })

                            Route.Search -> SearchScreen(search, viewModel::updateSearch, viewModel::loadMoreSearch,
                                onOpenTopic = { stack.push(Route.TopicDetail(it.id, it.title)) },
                                onOrder = viewModel::setSearchOrder, active = active,
                                scrollToTopRequest = scrollToTopRequest,
                                trailing = { UnreadBell(state.unreadCount, openNotifications) })

                            Route.Profile -> ProfileScreen(state.username, profile, viewModel::loadProfile,
                                onOpenTopic = { id, title, postNumber -> stack.push(Route.TopicDetail(id, title, postNumber)) },
                                onDiagnostics = { stack.push(Route.Diagnostics) },
                                onSettings = { stack.push(Route.Settings) },
                                onLogout = viewModel::logout,
                                active = active,
                                onReadNotification = viewModel::readNotification,
                                notificationFilter = notificationFilter,
                                onNotificationFilter = viewModel::setNotificationFilter,
                                unreadFilters = state.unreadFilters,
                                scrollToTopRequest = scrollToTopRequest,
                                trailing = { UnreadBell(state.unreadCount, openNotifications) })


                            Route.Settings -> SettingsScreen(
                                treeView = treeView,
                                onTreeView = viewModel::setTreeView,
                                onBack = { stack.pop() },
                                themeMode = themeMode,
                                onThemeMode = viewModel::setThemeMode,
                                updateState = updateState,
                                onCheckUpdate = { viewModel.checkForUpdates(manual = true) },
                                syncReadProgress = syncReadProgress,
                                onSyncReadProgress = viewModel::setSyncReadProgress,
                                onDiagnostics = { stack.push(Route.Diagnostics) },
                                onLogout = viewModel::logout,
                            )

                            Route.Notifications -> ProfileScreen(state.username, profile, viewModel::loadProfile,
                                onOpenTopic = { id, title, postNumber -> stack.push(Route.TopicDetail(id, title, postNumber)) },
                                onDiagnostics = { stack.push(Route.Diagnostics) }, onSettings = { stack.push(Route.Settings) },
                                active = active, notificationOnly = true, onBack = { stack.pop() },
                                onReadNotification = viewModel::readNotification,
                                notificationFilter = notificationFilter,
                                onNotificationFilter = viewModel::setNotificationFilter,
                                unreadFilters = state.unreadFilters)


                            Route.Diagnostics -> DiagnosticsScreen(
                                state = state,
                                viewModel = viewModel,
                                onBack = { stack.pop() },
                            )

                        }
                    }
                }
            }
        }
        TabBar(selected, homeFeedLabel = state.homeFeed.label) { tab ->
            if (browsing) {
                if (tab == selected) {
                    stacks.getValue(tab).popToRoot()
                    scrollRequests.getValue(tab).intValue++
                } else selected = tab
            }
        }
    }
}

@Composable
private fun BootScreen() {
    Box(
        Modifier.fillMaxSize().background(IosTheme.colors.groupedBackground),
        contentAlignment = Alignment.Center,
    ) {
        IosActivityIndicator(diameter = 28.dp)
    }
}
