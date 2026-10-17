package org.linuxdo.android.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.core.content.edit
import androidx.lifecycle.viewModelScope
import java.io.ByteArrayOutputStream
import java.util.Collections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Request
import org.linuxdo.android.App
import org.linuxdo.android.BuildConfig
import org.linuxdo.android.Config
import org.linuxdo.android.net.loadDohServers
import org.linuxdo.android.service.NotificationWorker
import org.linuxdo.android.service.NotificationKeepAliveService
import org.linuxdo.android.service.NotificationHelper
import org.linuxdo.android.service.ApkUpdateInstaller
import org.linuxdo.android.data.TopicRepository
import org.linuxdo.android.data.DiscourseTagInfo
import org.linuxdo.android.data.DiscourseTagSearchResult
import org.linuxdo.android.data.Category
import org.linuxdo.android.data.TopicSortOrder
import org.linuxdo.android.data.FloatingAction
import org.linuxdo.android.data.flattenCategories
import org.linuxdo.android.data.fixedTagsForCategory
import org.linuxdo.android.data.CustomSection
import org.linuxdo.android.data.HomeFeedType
import org.linuxdo.android.data.Post
import org.linuxdo.android.data.TopicDetail
import org.linuxdo.android.data.TopicListItem
import org.linuxdo.android.data.DraftItem
import org.linuxdo.android.data.AttachedMedia
import org.linuxdo.android.data.SearchOrder
import org.linuxdo.android.data.ProfileSection
import org.linuxdo.android.data.ProfilePage
import org.linuxdo.android.data.NotificationFilter
import org.linuxdo.android.data.UploadLimits
import org.linuxdo.android.data.effectiveReplyToPostNumber
import org.linuxdo.android.data.threadRows
import org.linuxdo.android.data.TopicNotificationLevel
import org.linuxdo.android.data.PostResult
import org.linuxdo.android.data.PostFlagReason
import org.linuxdo.android.data.Reaction
import org.linuxdo.android.data.AutoRefreshInterval
import org.linuxdo.android.data.NotificationPollInterval
import org.linuxdo.android.data.NotificationScope
import org.linuxdo.android.net.HttpStatusException
import org.linuxdo.android.net.LoginRejectedException
import org.linuxdo.android.net.NeedsInteractiveVerification
import org.linuxdo.android.net.NetworkPath
import org.linuxdo.android.net.NetworkTimeoutException
import org.linuxdo.android.net.RiskControlException
import org.linuxdo.android.net.SecondFactorRequiredException
import org.linuxdo.android.ui.screen.TopicListUiState

enum class Screen { Boot, Login, Verify, List }

data class UpdateInfo(
    val latestVersion: String,
    val title: String,
    val changelog: String,
    val downloadUrl: String,
    val releasePageUrl: String,
    val mandatory: Boolean,
    /** 发布清单里的可选校验和；为空表示不校验，只有 version.json 路径会带上它。 */
    val apkSha256: String? = null,
)

/** 应用内下载安装的阶段。 */
enum class ApkUpdateStage { Idle, Downloading, Verifying, AwaitingPermission, Installing, Failed }

data class UpdateUiState(
    val currentVersion: String = BuildConfig.VERSION_NAME.substringBefore("-"),
    val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    val checking: Boolean = false,
    val manualChecking: Boolean = false,
    val updateInfo: UpdateInfo? = null,
    val showDialog: Boolean = false,
    val statusMessage: String? = null,
    val apkStage: ApkUpdateStage = ApkUpdateStage.Idle,
    val apkDownloadedBytes: Long = 0L,
    /** 总字节数；[ApkUpdateInstaller.TOTAL_UNKNOWN] 表示服务端没给 Content-Length。 */
    val apkTotalBytes: Long = 0L,
    val apkError: String? = null,
)

data class LoginUiState(
    val loading: Boolean = false,
    val sendingCode: Boolean = false,
    val error: String? = null,
    val emailSent: Boolean = false,
    val secondFactorRequired: Boolean = false,
    val backupEnabled: Boolean = false,
    val showWebLoginDialog: Boolean = false,
    val isRiskOrCaptchaBlocked: Boolean = false,
    val showRiskControlDialog: Boolean = false,
    val webAuthLoading: Boolean = false,
    val webAuthMessage: String? = null,
    val savedLogin: String = "",
    val savedPassword: String = "",
    val rememberPassword: Boolean = false,
)
enum class ThemeMode(val label: String) { System("跟随系统"), Light("浅色"), Dark("深色") }
enum class DetailLoad { Topic, Next, Previous }

data class DetailUiState(
    val topic: TopicDetail? = null,
    val posts: List<Post> = emptyList(),
    val consumedIds: Set<Long> = emptySet(),
    val category: Category? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val replyCursors: Map<Long, Int> = emptyMap(),
    val completedReplies: Set<Long> = emptySet(),
    val loadingReplies: Set<Long> = emptySet(),
    val reactingPosts: Set<Long> = emptySet(),
    val reactionErrors: Map<Long, String> = emptyMap(),
    val emojiUrls: Map<String, String> = emptyMap(),
    val windowStartId: Long? = null,
    val failedLoad: DetailLoad = DetailLoad.Topic,
    /**
     * 刚发出去的楼层 id。界面据此滚过去并高亮,用完调 clearCreatedPost 清掉。
     * 放在这里而不是让界面自己猜"楼层数变多了":它和新楼层是同一次状态更新送达的,
     * 界面不会读到"编辑器已关、但列表还没更新"的中间态。
     */
    val createdPostId: Long? = null,
    /** 评论区是否倒序(最新一级主评论在前)。1 楼主贴永远置顶,不参与翻转。 */
    val descending: Boolean = false,
    val noticeMessage: String? = null,
    val flagDialogPost: Post? = null,
    val flagError: String? = null,
    val flaggedPosts: Set<Long> = emptySet(),
    val notificationLevelUpdating: Boolean = false,
    /**
     * 定位版本号。每次带 postNumber 进入话题时（无论是否命中缓存）都递增，
     * TopicDetailScreen 据此重置 located 标志并强制重新执行滚动定位，
     * 彻底消除"已读过的帖子点通知后停在旧缓存位置"的问题。
     */
    val locateVersion: Int = 0,
) {
    val notificationLevel: TopicNotificationLevel
        get() = TopicNotificationLevel.fromCode(topic?.details?.notificationLevel)

    private val windowStart: Int get() = topic?.postStream?.stream.orEmpty().indexOf(windowStartId).coerceAtLeast(0)
    val remaining: List<Long> get() {
        val stream = topic?.postStream?.stream.orEmpty()
        // 倒序下"加载更多"是往更早的楼层回溯,不受向下窗口起点的约束。
        return if (descending) stream.filterNot { it in consumedIds }
        else stream.drop(windowStart).filterNot { it in consumedIds }
    }
    /** 倒序时不提供"加载之前的楼层"缺口 —— 那和倒序的回溯加载是同一个方向,会互相抢楼。 */
    val previousIds: List<Long> get() = if (descending) emptyList()
        else topic?.postStream?.stream.orEmpty().take(windowStart).filterNot { it in consumedIds }
}

data class CategoriesUiState(
    val items: List<Category> = emptyList(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val order: List<Long> = emptyList(),
)

/** 编辑器要做的事。三种目标共用一个输入框,区别只在提交时调哪个接口。 */
sealed interface ComposerTarget {
    val topicId: Long

    /**
     * 回复整个话题。不带 reply_to_post_number,所以不挂在任何楼层下面,
     * 发出去的楼层直接排在话题末尾 —— 这是网页端"回复"按钮的行为。
     */
    data class NewReply(override val topicId: Long) : ComposerTarget

    /** 回复某一楼层,结果会挂到该楼层下面。 */
    data class ReplyTo(
        override val topicId: Long,
        val postNumber: Int,
        val username: String,
    ) : ComposerTarget

    /** 编辑自己的楼层。 */
    data class Edit(
        override val topicId: Long,
        val postId: Long,
        val postNumber: Int,
    ) : ComposerTarget
}

data class ComposerUiState(
    val target: ComposerTarget? = null,
    val draft: String = "",
    /** 编辑要先取回原文,这期间输入框不可编辑。 */
    val loadingDraft: Boolean = false,
    val submitting: Boolean = false,
    /** 正在上传附件。期间不许提交 —— 不然短码还没插进草稿就发出去了。 */
    val uploading: Boolean = false,
    val attachments: List<AttachedMedia> = emptyList(),
    val error: String? = null,
) {
    val open: Boolean get() = target != null
    val canSubmit: Boolean get() = (draft.isNotBlank() || attachments.isNotEmpty()) && !submitting && !loadingDraft && !uploading
}

data class TopicCreatorUiState(
    val open: Boolean = false,
    val draftKey: String = "new_topic",
    val sequence: Long = 0L,
    val action: String = "createTopic",
    val replyTopicId: Long? = null,
    val replyToPostNumber: Int? = null,
    val title: String = "",
    val raw: String = "",
    val categoryId: Int? = null,
    val tags: List<String> = emptyList(),
    val availableTags: List<DiscourseTagInfo> = emptyList(),
    val requiredTagGroupName: String? = null,
    val requiredTagMinCount: Int = 1,
    val loadingTags: Boolean = false,
    val attachments: List<AttachedMedia> = emptyList(),
    val savingDraft: Boolean = false,
    val submitting: Boolean = false,
    val uploading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val savedSnapshot: String = "",
    val emojiUrls: Map<String, String> = emptyMap(),
) {
    val isReplyDraft: Boolean
        get() = action != "createTopic" && replyTopicId != null && replyTopicId > 0L

    val currentSnapshot: String
        get() = "${title.trim()}\u0000${raw.trim()}\u0000${categoryId ?: ""}\u0000${tags.joinToString(",")}\u0000${attachments.joinToString(",") { it.id }}"

    val hasUnsavedChanges: Boolean
        get() = (title.trim().isNotEmpty() || raw.trim().isNotEmpty() || attachments.isNotEmpty()) && currentSnapshot != savedSnapshot

    val canSubmit: Boolean
        get() = !submitting && !uploading && !savingDraft &&
            (raw.trim().isNotEmpty() || attachments.isNotEmpty()) && (isReplyDraft || title.trim().isNotEmpty())

    val canSaveDraft: Boolean
        get() = !submitting && !uploading && !savingDraft &&
            (raw.trim().isNotEmpty() || title.trim().isNotEmpty() || attachments.isNotEmpty())
}

data class DraftsUiState(
    val items: List<DraftItem> = emptyList(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val deletingKeys: Set<String> = emptySet(),
    val error: String? = null,
)

data class SearchUiState(
    val query: String = "",
    val results: TopicListUiState = TopicListUiState(initialLoading = false, hasMore = false),
    val excerpts: Map<Long, String> = emptyMap(),
    val order: SearchOrder = SearchOrder.Relevance,
)

data class ProfileUiState(
    val content: ProfilePage = ProfilePage(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    /** 这份数据拉完的时刻(elapsedRealtime),用来判断缓存有没有过期。 */
    val loadedAt: Long = 0L,
    /** 拉这份数据时的未读数。未读没涨说明列表大概率没变,可以接着用缓存。 */
    val unreadAtLoad: Int = 0,
)

data class UiState(
    val screen: Screen = Screen.Boot,
    val username: String? = null,
    val homeFeed: HomeFeedType = HomeFeedType.Latest,
    val latest: TopicListUiState = TopicListUiState(),
    val path: NetworkPath = NetworkPath.OKHTTP,
    val autoRefresh: Boolean = false,
    val cookieNames: List<String> = emptyList(),
    val cookieSavedAt: Long = 0L,
    val userAgent: String = "",
    val unreadCount: Int = 0,
    val unreadFilters: Set<NotificationFilter> = emptySet(),
    /** 各通知分类的未读条数。用于判断某个分类的缓存是否还新鲜。 */
    val unreadByFilter: Map<NotificationFilter, Int> = emptyMap(),
)


class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as App).container
    private val store = container.sessionStore
    private val browser = container.browser
    private val userApiKeyAuthService = container.userApiKeyAuthService
    val diagnostics = container.diagnostics

    private val initialHomeFeed = HomeFeedType.fromName(store.homeFeed())
    private val _feedStates = MutableStateFlow<Map<HomeFeedType, TopicListUiState>>(emptyMap())
    val feedStates = _feedStates.asStateFlow()
    private val _topicSortOrder = MutableStateFlow(store.getTopicSortOrder())
    val topicSortOrder: StateFlow<TopicSortOrder> = _topicSortOrder.asStateFlow()
    private val _topicSortAscending = MutableStateFlow(store.getTopicSortAscending())
    val topicSortAscending: StateFlow<Boolean> = _topicSortAscending.asStateFlow()
    private val _floatingBallEnabled = MutableStateFlow(store.isFloatingBallEnabled())
    val floatingBallEnabled: StateFlow<Boolean> = _floatingBallEnabled.asStateFlow()
    private val _floatingBallActions = MutableStateFlow(store.getFloatingBallActions())
    val floatingBallActions: StateFlow<List<FloatingAction>> = _floatingBallActions.asStateFlow()
    private val _floatingBallXRatio = MutableStateFlow(store.getFloatingBallXRatio())
    val floatingBallXRatio: StateFlow<Float> = _floatingBallXRatio.asStateFlow()
    private val _floatingBallYRatio = MutableStateFlow(store.getFloatingBallYRatio())
    val floatingBallYRatio: StateFlow<Float> = _floatingBallYRatio.asStateFlow()
    private val _floatingBallOnRight = MutableStateFlow(store.isFloatingBallOnRight())
    val floatingBallOnRight: StateFlow<Boolean> = _floatingBallOnRight.asStateFlow()
    private val _state = MutableStateFlow(
        UiState(
            userAgent = container.userAgent,
            homeFeed = initialHomeFeed,
            unreadCount = 0,
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val repository = TopicRepository(
        transportProvider = { container.transport(_state.value.path) },
        diagnostics = diagnostics,
        reading = container.reading,
        sessionStore = store,
        fallbackTransportProvider = { container.transport(NetworkPath.WEBVIEW) },
    )

    private var bootJob: Job? = null
    private var autoJob: Job? = null
    private var verifying = false
    private var resumeAfterVerification: (() -> Unit)? = null
    /**
     * 邮件登录被 CF 拦下、待重放的 token。
     * 与 resumeAfterVerification 分开是因为两者通过验证后的处理不同:
     * 重放登录要自己收尾(boot 或回登录页),而不是交给 boot() 去恢复一个页面加载。
     */
    private var pendingEmailLoginToken: String? = null
    private val loadingFeeds = java.util.concurrent.ConcurrentHashMap.newKeySet<HomeFeedType>()
    private var searchJob: Job? = null
    private var unreadJob: Job? = null
    private var unreadFailures = 0
    private var nextUnreadRefreshAt = 0L
    private val pageJobs = mutableMapOf<String, Job>()
    private val _details = MutableStateFlow<Map<Long, DetailUiState>>(emptyMap())
    val details = _details.asStateFlow()
    private val _categories = MutableStateFlow(CategoriesUiState(order = store.loadCategoryOrder()))
    val categories = _categories.asStateFlow()
    private val _customSection = MutableStateFlow(store.loadCustomSection())
    val customSection = _customSection.asStateFlow()
    private val _categoryTopics = MutableStateFlow<Map<Int, TopicListUiState>>(emptyMap())
    val categoryTopics = _categoryTopics.asStateFlow()
    private val _userTopicStates = MutableStateFlow<Map<String, TopicListUiState>>(emptyMap())
    val userTopicStates = _userTopicStates.asStateFlow()
    private val _search = MutableStateFlow(SearchUiState())
    val search = _search.asStateFlow()
    private val _profile = MutableStateFlow<Map<ProfileSection, ProfileUiState>>(emptyMap())
    val profile = _profile.asStateFlow()
    private val _notificationFilter = MutableStateFlow(NotificationFilter.All)
    val notificationFilter = _notificationFilter.asStateFlow()
    /**
     * 通知各分类的数据。`_profile` 的键是 ProfileSection,四个分类挤同一个槽位,
     * 所以额外按分类留一份,切标签才能命中缓存直接显示。
     */
    private val notificationCache = mutableMapOf<NotificationFilter, ProfileUiState>()
    private val _composer = MutableStateFlow(ComposerUiState())
    val composer = _composer.asStateFlow()
    private val _topicCreator = MutableStateFlow(TopicCreatorUiState())
    val topicCreator = _topicCreator.asStateFlow()
    private val _drafts = MutableStateFlow(DraftsUiState())
    val drafts = _drafts.asStateFlow()
    private val _treeView = MutableStateFlow(container.preferences.getBoolean("tree_view", true))
    val treeView = _treeView.asStateFlow()
    private val _syncReadProgress = MutableStateFlow(container.reading.syncReadProgress)
    val syncReadProgress = _syncReadProgress.asStateFlow()
    data class NotificationTarget(val topicId: Long, val postNumber: Int?)
    private val _pendingNotificationTarget = MutableStateFlow<NotificationTarget?>(null)
    val pendingNotificationTarget = _pendingNotificationTarget.asStateFlow()

    fun navigateToTopicFromNotification(
        topicId: Long,
        postNumber: Int?,
        notificationId: Long? = null,
        notificationType: Int? = null,
    ) {
        _pendingNotificationTarget.value = NotificationTarget(topicId, postNumber)
        if (notificationId != null && notificationId > 0L) {
            readNotification(notificationId, notificationType)
        }
        markNotificationsReadForTopic(topicId)
    }

    private fun markNotificationsReadForTopic(topicId: Long) {
        val matchingNotifIds = mutableSetOf<Pair<Long, Int?>>()
        _profile.value[ProfileSection.Notifications]?.content?.entries?.forEach { entry ->
            if (entry.topicId == topicId && entry.unread) {
                entry.id.toLongOrNull()?.let { id ->
                    matchingNotifIds.add(id to entry.notificationType)
                }
            }
        }
        notificationCache.values.forEach { cached ->
            cached.content.entries.forEach { entry ->
                if (entry.topicId == topicId && entry.unread) {
                    entry.id.toLongOrNull()?.let { id ->
                        matchingNotifIds.add(id to entry.notificationType)
                    }
                }
            }
        }
        matchingNotifIds.forEach { (id, type) ->
            readNotification(id, type)
        }
    }

    fun clearPendingNotificationTarget() {
        _pendingNotificationTarget.value = null
    }

    private val _autoRefreshInterval = MutableStateFlow(store.getAutoRefreshInterval())
    val autoRefreshInterval = _autoRefreshInterval.asStateFlow()

    private val _notificationPollInterval = MutableStateFlow(store.getNotificationPollInterval())
    val notificationPollInterval = _notificationPollInterval.asStateFlow()

    private val _notificationScope = MutableStateFlow(store.getNotificationScope())
    val notificationScope = _notificationScope.asStateFlow()

    private val _autoRefreshIncoming = MutableStateFlow(store.isAutoRefreshIncomingEnabled())
    val autoRefreshIncoming = _autoRefreshIncoming.asStateFlow()
    private val _backgroundNotification = MutableStateFlow(store.isBackgroundNotificationEnabled())
    val backgroundNotification = _backgroundNotification.asStateFlow()
    private val _customRealtimeSeconds = MutableStateFlow(store.getCustomRealtimeSeconds())
    val customRealtimeSeconds = _customRealtimeSeconds.asStateFlow()
    private val _keepAliveService = MutableStateFlow(store.isKeepAliveServiceEnabled())
    val keepAliveService = _keepAliveService.asStateFlow()

    // ── DoH 状态 ─────────────────────────────────────────────────────────────────
    private val _dohEnabled = MutableStateFlow(store.isDohEnabled())
    val dohEnabled = _dohEnabled.asStateFlow()

    private val _selectedDohServerUrl = MutableStateFlow(
        store.getDohServerUrl() ?: org.linuxdo.android.net.DohPresets.DEFAULT.url
    )
    val selectedDohServerUrl = _selectedDohServerUrl.asStateFlow()

    private val _dohAutoFailover = MutableStateFlow(store.isDohAutoFailoverEnabled())
    val dohAutoFailover = _dohAutoFailover.asStateFlow()

    /** 节点延迟测试结果 Map：key = DohServer.id, value = 延迟(ms)；-1L 表示超时 */
    private val _dohLatencies = MutableStateFlow<Map<String, Long?>>(emptyMap())
    val dohLatencies = _dohLatencies.asStateFlow()

    /** 节点探测诊断摘要 Map：key = DohServer.id, value = 状态摘要（如 ECH 已接受/站点访问成功等） */
    private val _dohSummaries = MutableStateFlow<Map<String, String>>(emptyMap())
    val dohSummaries = _dohSummaries.asStateFlow()

    /** 一键测速进行中状态（用于按钮转圈控制） */
    private val _isTestingAllDoh = MutableStateFlow(false)
    val isTestingAllDoh = _isTestingAllDoh.asStateFlow()

    /** 当前完整的 DoH 节点列表（支持增删改查与恢复预设） */
    private val _dohServers = MutableStateFlow<List<org.linuxdo.android.net.DohServer>>(
        org.linuxdo.android.net.loadDohServers(store)
    )
    val dohServers = _dohServers.asStateFlow()

    fun setDohEnabled(enabled: Boolean) {
        store.setDohEnabled(enabled)
        _dohEnabled.value = enabled
        container.rebuildHttpClient()
    }

    fun setDohServerSelected(server: org.linuxdo.android.net.DohServer) {
        store.setDohServerUrl(server.url)
        _selectedDohServerUrl.value = server.url
        container.dohCoordinator.manualSelect(server)
    }

    fun setDohAutoFailover(enabled: Boolean) {
        store.setDohAutoFailoverEnabled(enabled)
        _dohAutoFailover.value = enabled
        container.dohCoordinator.refresh()
    }

    fun testDohServer(server: org.linuxdo.android.net.DohServer) {
        viewModelScope.launch(Dispatchers.IO) {
            val netState = container.networkMonitor.detect()
            if (netState.shouldPauseDoh) {
                val reason = if (netState.isVpn) "VPN 已开启，直连已暂停" else "系统代理已开启，直连已暂停"
                _dohLatencies.update { it + (server.id to -1L) }
                _dohSummaries.update { it + (server.id to reason) }
                return@launch
            }
            // 先置 null（测速中）
            _dohLatencies.update { it + (server.id to null) }
            val result = org.linuxdo.android.net.DohProbeEngine.probeDetailed(server)
            // -1 表示超时
            _dohLatencies.update { it + (server.id to (result.latencyMs ?: -1L)) }
            _dohSummaries.update { it + (server.id to result.summary) }
        }
    }

    fun testAllDohServers() {
        viewModelScope.launch(Dispatchers.IO) {
            _isTestingAllDoh.value = true
            try {
                val netState = container.networkMonitor.detect()
                if (netState.shouldPauseDoh) {
                    val reason = if (netState.isVpn) "VPN 已开启，直连已暂停" else "系统代理已开启，直连已暂停"
                    val mapLatency = _dohServers.value.associate { it.id to -1L }
                    val mapSummary = _dohServers.value.associate { it.id to reason }
                    _dohLatencies.update { it + mapLatency }
                    _dohSummaries.update { it + mapSummary }
                    return@launch
                }
                val servers = _dohServers.value
                val jobs = servers.map { server ->
                    launch {
                        _dohLatencies.update { it + (server.id to null) }
                        val result = org.linuxdo.android.net.DohProbeEngine.probeDetailed(server)
                        _dohLatencies.update { it + (server.id to (result.latencyMs ?: -1L)) }
                        _dohSummaries.update { it + (server.id to result.summary) }
                    }
                }
                jobs.forEach { it.join() }
            } finally {
                _isTestingAllDoh.value = false
            }
        }
    }

    fun addCustomDohServer(url: String, name: String) {
        val existing = _dohServers.value
        val cleanUrl = url.trim()
        if (existing.any { it.url.trimEnd('/') == cleanUrl.trimEnd('/') }) return
        val newServer = org.linuxdo.android.net.DohServer(url = cleanUrl, name = name.trim().ifBlank { cleanUrl }, isCustom = true)
        val newList = existing + newServer
        saveDohServers(newList)
    }

    fun updateDohServer(oldServer: org.linuxdo.android.net.DohServer, newUrl: String, newName: String) {
        val existing = _dohServers.value
        val cleanUrl = newUrl.trim()
        val cleanName = newName.trim().ifBlank { cleanUrl }
        val updatedServer = oldServer.copy(url = cleanUrl, name = cleanName)
        val newList = existing.map { if (it.id == oldServer.id) updatedServer else it }
        saveDohServers(newList)
        if (_selectedDohServerUrl.value == oldServer.url) {
            setDohServerSelected(updatedServer)
        }
    }

    fun deleteDohServer(server: org.linuxdo.android.net.DohServer) {
        val newList = _dohServers.value.filter { it.id != server.id }
        val finalList = if (newList.isEmpty()) org.linuxdo.android.net.DohPresets.ALL else newList
        saveDohServers(finalList)
        if (_selectedDohServerUrl.value == server.url) {
            setDohServerSelected(finalList.first())
        }
    }

    fun resetDohServersToDefault() {
        store.setDohServersJson(null)
        store.setDohCustomServersJson(null)
        val defaultList = org.linuxdo.android.net.DohPresets.ALL
        _dohServers.value = defaultList
        setDohServerSelected(org.linuxdo.android.net.DohPresets.DEFAULT)
        container.rebuildHttpClient()
    }

    private fun saveDohServers(servers: List<org.linuxdo.android.net.DohServer>) {
        val jsonArr = org.json.JSONArray().apply {
            servers.forEach { s ->
                put(org.json.JSONObject().apply {
                    put("url", s.url)
                    put("name", s.name)
                    put("isCustom", s.isCustom)
                    if (s.bootstrapIps.isNotEmpty()) {
                        put("bootstrapIps", org.json.JSONArray(s.bootstrapIps))
                    }
                })
            }
        }
        store.setDohServersJson(jsonArr.toString())
        _dohServers.value = servers
        container.rebuildHttpClient()
    }

    private var autoProbeJob: Job? = null

    private fun restartAutoProbeLoop() {
        autoProbeJob?.cancel()
        val interval = store.getAutoRefreshInterval()
        if (interval == AutoRefreshInterval.OFF) return
        autoProbeJob = viewModelScope.launch {
            while (isActive) {
                delay(interval.seconds * 1000L)
                probeIncomingTopics(_state.value.homeFeed)
            }
        }
    }
    /** 开启同步后，话题的进入时刻、上次上报时刻与已上报时长。 */
    private val topicEnteredAt = mutableMapOf<Long, Long>()
    private val lastReportedAt = mutableMapOf<Long, Long>()
    private val reportedReadMs = mutableMapOf<Long, Long>()
    private val readReportJobs = mutableMapOf<Long, Job>()
    /** 当前处于前台可见、活跃状态的话题集合（被验证页遮挡、切后台或被下层路由盖住时不在其中）。 */
    private val activeTopicIds = Collections.synchronizedSet(mutableSetOf<Long>())
    /** 各话题在当前屏幕上当前可见的楼层号。 */
    private val currentVisiblePostsByTopic = mutableMapOf<Long, List<Int>>()
    /** 各话题在屏幕上实际浏览过但尚未结算上报的楼层号队列（保留阅读顺序，按批次结算，避免倒序饥饿）。 */
    private val pendingVisiblePostsByTopic = mutableMapOf<Long, LinkedHashSet<Int>>()

    fun setTopicActive(topicId: Long, active: Boolean) {
        if (active) {
            activeTopicIds.add(topicId)
        } else {
            activeTopicIds.remove(topicId)
        }
    }

    private fun isTopicActive(topicId: Long): Boolean {
        return _state.value.screen == Screen.List && activeTopicIds.contains(topicId)
    }
    private val _themeMode = MutableStateFlow(ThemeMode.entries.firstOrNull {
        it.name == container.preferences.getString("theme_mode", ThemeMode.System.name)
    } ?: ThemeMode.System)
    val themeMode = _themeMode.asStateFlow()

    private val _loginUiState = MutableStateFlow(
        LoginUiState(
            savedLogin = store.savedLogin().orEmpty(),
            savedPassword = store.savedPassword().orEmpty(),
            rememberPassword = store.isRememberPassword(),
        )
    )
    val loginUiState: StateFlow<LoginUiState> = _loginUiState.asStateFlow()

    private fun loadCachedUpdateInfo(): UpdateInfo? {
        val prefs = container.preferences
        val latestVersion = prefs.getString("cached_update_version", null)?.takeIf { it.isNotBlank() } ?: return null
        val currentVersion = BuildConfig.VERSION_NAME.substringBefore("-")
        if (compareVersions(latestVersion, currentVersion) <= 0) {
            clearCachedUpdateInfo()
            return null
        }
        return UpdateInfo(
            latestVersion = latestVersion,
            title = prefs.getString("cached_update_title", "").orEmpty(),
            changelog = prefs.getString("cached_update_changelog", "").orEmpty(),
            downloadUrl = prefs.getString("cached_update_download_url", "").orEmpty(),
            releasePageUrl = prefs.getString("cached_update_release_url", "").orEmpty(),
            mandatory = prefs.getBoolean("cached_update_mandatory", false),
            apkSha256 = prefs.getString("cached_update_apk_sha256", null),
        )
    }

    private fun saveCachedUpdateInfo(info: UpdateInfo) {
        container.preferences.edit {
            putString("cached_update_version", info.latestVersion)
            putString("cached_update_title", info.title)
            putString("cached_update_changelog", info.changelog)
            putString("cached_update_download_url", info.downloadUrl)
            putString("cached_update_release_url", info.releasePageUrl)
            putBoolean("cached_update_mandatory", info.mandatory)
            putString("cached_update_apk_sha256", info.apkSha256)
            putLong("cached_update_check_time", System.currentTimeMillis())
        }
    }

    private fun clearCachedUpdateInfo() {
        container.preferences.edit {
            remove("cached_update_version")
            remove("cached_update_title")
            remove("cached_update_changelog")
            remove("cached_update_download_url")
            remove("cached_update_release_url")
            remove("cached_update_mandatory")
            remove("cached_update_apk_sha256")
            remove("cached_update_check_time")
        }
    }

    fun onVersionItemClick() {
        val currentInfo = _updateState.value.updateInfo
        if (currentInfo != null) {
            _updateState.update { it.copy(showDialog = true) }
        } else {
            checkForUpdates(manual = true)
        }
    }

    private val _updateState = MutableStateFlow(
        UpdateUiState(
            updateInfo = loadCachedUpdateInfo(),
        )
    )
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    private var apkUpdateJob: Job? = null

    fun dismissUpdateDialog(ignoreThisVersion: Boolean = false) {
        val current = _updateState.value
        val info = current.updateInfo ?: return
        if (info.mandatory) return
        if (ignoreThisVersion) {
            container.preferences.edit { putString("ignored_update_version", info.latestVersion) }
        }
        // 关掉对话框就不该再占着带宽继续下。
        cancelApkUpdate()
        _updateState.update { it.copy(showDialog = false) }
    }

    /**
     * 应用内下载新版本安装包并拉起系统安装器。
     *
     * 跑在 viewModelScope 而不是前台服务:包体不大,正常网络数秒完成,用户就停在对话框上。
     * 进程被杀则下载中断,重开再下即可,不值得为此多拉一个常驻载体。
     *
     * Android 不允许第三方静默安装,所以这里最多做到「下完直接弹安装确认」。
     */
    fun startApkUpdate() {
        val info = _updateState.value.updateInfo ?: return
        if (apkUpdateJob?.isActive == true) return
        val context = getApplication<Application>()

        // 「安装未知应用」是 Android 8.0 起的特殊权限,Manifest 声明不等于已授予。
        if (!ApkUpdateInstaller.canInstall(context)) {
            _updateState.update {
                it.copy(apkStage = ApkUpdateStage.AwaitingPermission, apkError = null)
            }
            return
        }

        apkUpdateJob = viewModelScope.launch {
            _updateState.update {
                it.copy(
                    apkStage = ApkUpdateStage.Downloading,
                    apkDownloadedBytes = 0L,
                    apkTotalBytes = 0L,
                    apkError = null,
                )
            }
            val result = ApkUpdateInstaller.download(
                context = context,
                url = info.downloadUrl,
                expectedSha256 = info.apkSha256,
            ) { downloaded, total ->
                // 字节读完但方法还没返回,这段时间正是在算摘要,如实显示为校验中。
                val verifying = total > 0L && downloaded >= total && !info.apkSha256.isNullOrBlank()
                _updateState.update {
                    it.copy(
                        apkStage = if (verifying) ApkUpdateStage.Verifying else ApkUpdateStage.Downloading,
                        apkDownloadedBytes = downloaded,
                        apkTotalBytes = total,
                    )
                }
            }
            when (result) {
                is ApkUpdateInstaller.Result.Success -> {
                    _updateState.update { it.copy(apkStage = ApkUpdateStage.Installing, apkError = null) }
                    if (!ApkUpdateInstaller.install(context, result.file)) {
                        _updateState.update {
                            it.copy(
                                apkStage = ApkUpdateStage.Failed,
                                apkError = "无法拉起安装程序，请改用浏览器下载。",
                            )
                        }
                    }
                }
                is ApkUpdateInstaller.Result.Failure -> {
                    _updateState.update {
                        it.copy(apkStage = ApkUpdateStage.Failed, apkError = result.message)
                    }
                }
            }
        }
    }

    /** 用户返回授权页后重新发起;权限仍未授予时会再次回到等待授权态。 */
    fun retryApkUpdateAfterPermission() {
        _updateState.update { it.copy(apkStage = ApkUpdateStage.Idle, apkError = null) }
        startApkUpdate()
    }

    fun cancelApkUpdate() {
        apkUpdateJob?.cancel()
        apkUpdateJob = null
        _updateState.update {
            it.copy(
                apkStage = ApkUpdateStage.Idle,
                apkDownloadedBytes = 0L,
                apkTotalBytes = 0L,
                apkError = null,
            )
        }
    }

    /**
     * 检查最新版本，优先读取公开仓库根目录的 `version.json` 静态版本清单，
     * 若 `version.json` 不可达则回退至 GitHub Releases 接口。
     *
     * 必升级触发规则（满足任一即为必升级）：
     * 1. `version.json` 中 `forceUpdate: true`
     * 2. `version.json` 中 `minVersionCode` 大于当前 `versionCode`（或 `minVersionName` 高于当前版本）
     * 3. 回退 Release 正文包含 `[force]`、`[mandatory]`、`[必升]`、`[min_version: X.Y.Z]` 或 `[min_code: N]`
     */
    fun checkForUpdates(manual: Boolean = false) {
        if (_updateState.value.checking) {
            if (manual) {
                viewModelScope.launch(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "正在检查更新中，请稍候...", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }
        viewModelScope.launch {
            _updateState.update {
                it.copy(
                    checking = true,
                    manualChecking = manual,
                    statusMessage = if (manual) "正在检查新版本..." else it.statusMessage,
                )
            }
            try {
                val currentVersion = _updateState.value.currentVersion
                val currentCode = _updateState.value.currentVersionCode
                val info = withContext(Dispatchers.IO) {
                    for (manifestUrl in Config.UPDATE_VERSION_JSON_URLS) {
                        val manifestJson = runCatching {
                            val req = Request.Builder()
                                .url(manifestUrl)
                                .header("Accept", "application/vnd.github.raw+json, application/json;q=0.9")
                                .header("Cache-Control", "no-cache")
                                .header("User-Agent", container.userAgent)
                                .get()
                                .build()
                            container.httpClient.newCall(req).execute().use { resp ->
                                if (!resp.isSuccessful) {
                                    throw HttpStatusException(resp.code, "Manifest HTTP ${resp.code}")
                                }
                                resp.body?.string().orEmpty()
                            }
                        }.getOrNull()
                        if (!manifestJson.isNullOrBlank()) {
                            val (parsed, manifestInfo) = parseVersionManifest(manifestJson, currentVersion, currentCode)
                            if (parsed) {
                                return@withContext manifestInfo
                            }
                        }
                    }

                    val (_, localManifestInfo) = parseVersionManifest(
                        Config.FALLBACK_VERSION_MANIFEST_JSON,
                        currentVersion,
                        currentCode,
                    )
                    if (localManifestInfo != null) {
                        return@withContext localManifestInfo
                    }

                    val apiJson = runCatching {
                        val req = Request.Builder()
                            .url(Config.UPDATE_RELEASES_API_URL)
                            .header("Accept", "application/vnd.github+json")
                            .header("User-Agent", container.userAgent)
                            .get()
                            .build()
                        container.httpClient.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) {
                                throw HttpStatusException(resp.code, "GitHub API HTTP ${resp.code}")
                            }
                            resp.body?.string().orEmpty()
                        }
                    }.getOrNull()

                    if (!apiJson.isNullOrBlank()) {
                        parseReleaseUpdateInfo(apiJson, currentVersion, currentCode)
                    } else {
                        // 兜底：若 api.github.com 触发匿名限流或连接受阻，直接请求 GitHub Releases 页面利用 302 重定向提取最新 tag。
                        val req = Request.Builder()
                            .url(Config.UPDATE_RELEASES_PAGE_URL)
                            .header("Accept", "text/html,application/xhtml+xml")
                            .header("User-Agent", container.userAgent)
                            .get()
                            .build()
                        container.httpClient.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) {
                                throw HttpStatusException(resp.code, "GitHub Releases HTTP ${resp.code}")
                            }
                            val finalUrl = resp.request.url.toString()
                            val html = resp.body?.string().orEmpty()
                            parseReleaseHtmlFallback(finalUrl, html, currentVersion, currentCode)
                        }
                    }
                }
                if (info != null) {
                    saveCachedUpdateInfo(info)
                    val ignoredVersion = container.preferences.getString("ignored_update_version", null)
                    val shouldPopup = info.mandatory || manual || ignoredVersion != info.latestVersion
                    diagnostics.log("检测到新版本: v${info.latestVersion} (必升=${info.mandatory})")
                    _updateState.update {
                        it.copy(
                            checking = false,
                            manualChecking = false,
                            updateInfo = info,
                            showDialog = shouldPopup,
                            statusMessage = "发现新版本 v${info.latestVersion}",
                        )
                    }
                } else {
                    clearCachedUpdateInfo()
                    diagnostics.log("版本检查完成: 当前已是最新 (v$currentVersion)")
                    _updateState.update {
                        it.copy(
                            checking = false,
                            manualChecking = false,
                            updateInfo = null,
                            showDialog = false,
                            statusMessage = if (manual) "当前已是最新版本 (v$currentVersion)" else null,
                        )
                    }
                    if (manual) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(getApplication(), "当前已是最新版本 (v$currentVersion)", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                diagnostics.log("检查更新失败: ${error.message}")
                _updateState.update {
                    it.copy(
                        checking = false,
                        manualChecking = false,
                        statusMessage = if (manual) "检查更新失败，请稍后重试" else it.statusMessage,
                    )
                }
                if (manual) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "检查更新失败，请检查网络", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun parseVersionManifest(
        rawJson: String,
        currentVersion: String,
        currentCode: Int,
    ): Pair<Boolean, UpdateInfo?> {
        val outer = runCatching { Json.parseToJsonElement(rawJson).jsonObject }.getOrNull() ?: return false to null
        val root = if (outer["versionName"] == null && outer["encoding"]?.jsonPrimitive?.contentOrNull == "base64") {
            val encoded = outer["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val decoded = runCatching {
                String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT), Charsets.UTF_8)
            }.getOrNull().orEmpty()
            runCatching { Json.parseToJsonElement(decoded).jsonObject }.getOrNull() ?: return false to null
        } else {
            outer
        }

        val latestVersion = root["versionName"]?.jsonPrimitive?.contentOrNull
            ?.removePrefix("v")?.removePrefix("V")?.substringBefore("-")?.trim().orEmpty()
        if (latestVersion.isEmpty()) return false to null

        val latestCode = root["versionCode"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        val isNewer = if (latestCode > 0 && currentCode > 0) {
            latestCode > currentCode || compareVersions(latestVersion, currentVersion) > 0
        } else {
            compareVersions(latestVersion, currentVersion) > 0
        }
        if (!isNewer) return true to null

        val title = root["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: "v$latestVersion 版本更新"
        val changelogElement = root["changelog"]
        val changelog = when {
            changelogElement == null -> ""
            runCatching { changelogElement.jsonArray }.isSuccess -> {
                changelogElement.jsonArray
                    .mapNotNull { runCatching { it.jsonPrimitive.contentOrNull?.trim() }.getOrNull() }
                    .filter { it.isNotEmpty() }
                    .joinToString("\n")
            }
            else -> runCatching { changelogElement.jsonPrimitive.contentOrNull?.trim().orEmpty() }.getOrDefault("")
        }.ifBlank { "优化使用体验并修复已知问题。" }

        val forceUpdate = root["forceUpdate"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
        val minVersionCode = root["minVersionCode"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        val minVersionName = root["minVersionName"]?.jsonPrimitive?.contentOrNull
            ?.removePrefix("v")?.removePrefix("V")?.trim()
        val mandatory = forceUpdate ||
            (minVersionCode != null && currentCode < minVersionCode) ||
            (!minVersionName.isNullOrBlank() && compareVersions(currentVersion, minVersionName) < 0)

        val apkUrl = root["apkUrl"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: Config.UPDATE_RELEASES_PAGE_URL
        val releasePageUrl = root["releasePageUrl"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: Config.UPDATE_RELEASES_PAGE_URL
        // 可选校验和：留空或缺省即跳过校验，不阻塞发版。
        val apkSha256 = root["apkSha256"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

        return true to UpdateInfo(
            latestVersion = latestVersion,
            title = title,
            changelog = changelog,
            downloadUrl = apkUrl,
            releasePageUrl = releasePageUrl,
            mandatory = mandatory,
            apkSha256 = apkSha256,
        )
    }

    private fun parseReleaseUpdateInfo(
        rawJson: String,
        currentVersion: String,
        currentCode: Int,
    ): UpdateInfo? {
        val root = runCatching { Json.parseToJsonElement(rawJson).jsonObject }.getOrNull() ?: return null
        val tagName = root["tag_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val latestVersion = tagName.removePrefix("v").removePrefix("V").trim()
        if (latestVersion.isEmpty() || compareVersions(latestVersion, currentVersion) <= 0) {
            return null
        }
        val releaseName = root["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: "v$latestVersion 版本更新"
        val htmlUrl = root["html_url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: Config.UPDATE_RELEASES_PAGE_URL
        val assets = root["assets"]?.jsonArray.orEmpty()
        val apkUrl = assets.firstNotNullOfOrNull { element ->
            val obj = element.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val url = obj["browser_download_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) url else null
        } ?: htmlUrl

        val rawBody = root["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return buildUpdateInfo(latestVersion, releaseName, rawBody, apkUrl, htmlUrl, currentVersion, currentCode)
    }

    private fun parseReleaseHtmlFallback(
        finalUrl: String,
        html: String,
        currentVersion: String,
        currentCode: Int,
    ): UpdateInfo? {
        val tagName = finalUrl.substringAfter("/releases/tag/", "").substringBefore("?").substringBefore("#").trim()
            .ifBlank {
                Regex("""/releases/tag/(v?[0-9][^"'/\s<>]*)""").find(html)?.groupValues?.getOrNull(1).orEmpty()
            }
        val latestVersion = tagName.removePrefix("v").removePrefix("V").substringBefore("-").trim()
        if (latestVersion.isEmpty() || compareVersions(latestVersion, currentVersion) <= 0) {
            return null
        }
        val repoBase = Config.UPDATE_RELEASES_PAGE_URL.substringBefore("/releases")
        val apkUrl = "$repoBase/releases/download/$tagName/linuxdo-$latestVersion-release.apk"
        val bodyText = runCatching {
            org.jsoup.Jsoup.parse(html).select(".markdown-body").firstOrNull()?.wholeText()?.trim().orEmpty()
        }.getOrDefault("")
        return buildUpdateInfo(
            latestVersion = latestVersion,
            releaseName = "v$latestVersion 版本更新",
            rawBody = bodyText,
            apkUrl = apkUrl,
            htmlUrl = finalUrl.ifBlank { Config.UPDATE_RELEASES_PAGE_URL },
            currentVersion = currentVersion,
            currentCode = currentCode,
        )
    }

    private fun buildUpdateInfo(
        latestVersion: String,
        releaseName: String,
        rawBody: String,
        apkUrl: String,
        htmlUrl: String,
        currentVersion: String,
        currentCode: Int,
    ): UpdateInfo {
        val hasForceFlag = Regex("""\[(force|mandatory|必升)]|force_update\s*:\s*true""", RegexOption.IGNORE_CASE)
            .containsMatchIn(rawBody)
        val minVersionMatch = Regex("""\[min_version\s*:\s*v?([0-9.]+)]""", RegexOption.IGNORE_CASE)
            .find(rawBody)?.groupValues?.getOrNull(1)
        val minCodeMatch = Regex("""\[min_code\s*:\s*(\d+)]""", RegexOption.IGNORE_CASE)
            .find(rawBody)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val belowMinVersion = minVersionMatch != null && compareVersions(currentVersion, minVersionMatch) < 0
        val belowMinCode = minCodeMatch != null && currentCode < minCodeMatch
        val mandatory = hasForceFlag || belowMinVersion || belowMinCode

        val cleanChangelog = rawBody
            .replace(Regex("""\[(force|mandatory|必升)]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[min_version\s*:\s*v?[0-9.]+]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[min_code\s*:\s*\d+]""", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { "优化使用体验并修复已知问题。" }

        return UpdateInfo(
            latestVersion = latestVersion,
            title = releaseName,
            changelog = cleanChangelog,
            downloadUrl = apkUrl,
            releasePageUrl = htmlUrl,
            mandatory = mandatory,
        )
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val s1 = v1.removePrefix("v").removePrefix("V").trim()
        val s2 = v2.removePrefix("v").removePrefix("V").trim()
        val parts1 = s1.substringBefore("-").split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val parts2 = s2.substringBefore("-").split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val a = parts1.getOrElse(i) { 0 }
            val b = parts2.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        // 当数字一致时（如 1.3.0 与 1.3.0-beta），正式版高于预发尝鲜版 (SemVer 规范)
        val isPre1 = s1.contains("beta", ignoreCase = true) || s1.contains("alpha", ignoreCase = true) || s1.contains("preview", ignoreCase = true)
        val isPre2 = s2.contains("beta", ignoreCase = true) || s2.contains("alpha", ignoreCase = true) || s2.contains("preview", ignoreCase = true)
        if (!isPre1 && isPre2) return 1
        if (isPre1 && !isPre2) return -1
        return 0
    }

    private var loginJob: Job? = null

    fun dismissLoginError() {
        _loginUiState.update { it.copy(error = null, emailSent = false, isRiskOrCaptchaBlocked = false) }
    }

    fun isDarkMode(): Boolean {
        if (_themeMode.value == ThemeMode.Dark) return true
        if (_themeMode.value == ThemeMode.Light) return false
        val app = getApplication<android.app.Application>()
        val uiMode = app.resources.configuration.uiMode
        return (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    fun openWebLoginModal(
        login: String? = null,
        password: String? = null,
        secondFactorToken: String? = null,
        secondFactorMethod: Int = 1,
        rememberPassword: Boolean = false,
    ) {
        loginJob?.cancel()
        loginJob = null
        val u = login.orEmpty()
        val p = password.orEmpty()
        _loginUiState.update {
            it.copy(
                loading = false,
                sendingCode = false,
                error = null,
                isRiskOrCaptchaBlocked = false,
                showWebLoginDialog = true,
            )
        }
        val onResult: (Boolean, String?, Boolean) -> Unit = { success, errorMsg, need2FA ->
            viewModelScope.launch {
                when {
                    success -> {
                        diagnostics.log("安全验证与网页登录全流程成功，正在同步会话...")
                        android.webkit.CookieManager.getInstance().flush()
                        store.syncFromWebView()
                        if (u.isNotBlank()) {
                            store.saveCredentials(u, p, rememberPassword)
                        }
                        _loginUiState.update {
                            LoginUiState(
                                savedLogin = u,
                                savedPassword = if (rememberPassword) p else "",
                                rememberPassword = rememberPassword,
                            )
                        }
                        boot()
                    }
                    need2FA -> {
                        diagnostics.log("安全验证通过，需要进行二步验证 (2FA)")
                        _loginUiState.update {
                            it.copy(
                                showWebLoginDialog = false,
                                secondFactorRequired = true,
                                error = null,
                            )
                        }
                    }
                    else -> {
                        diagnostics.log("安全验证或登录失败: $errorMsg")
                        _loginUiState.update {
                            it.copy(
                                showWebLoginDialog = false,
                                loading = false,
                                error = errorMsg ?: "登录失败，请重试。",
                            )
                        }
                    }
                }
            }
        }
        if (!secondFactorToken.isNullOrBlank() && browser.resumeSecondFactorLogin(
                secondFactorToken = secondFactorToken,
                secondFactorMethod = secondFactorMethod,
                onResult = onResult,
            )
        ) {
            diagnostics.log("在原网页认证会话中续交 2FA 验证码")
            return
        }

        diagnostics.log("拉起内联安全认证弹框 (hCaptcha / CF / 2FA 全流程)")
        browser.loadCaptchaLogin(
            login = u,
            password = p,
            secondFactorToken = secondFactorToken,
            secondFactorMethod = secondFactorMethod,
            isDark = isDarkMode(),
            onResult = onResult,
        )
    }

    fun closeWebLoginModal() {
        browser.cancelCaptchaLogin()
        _loginUiState.update { it.copy(showWebLoginDialog = false) }
    }

    fun resetSecondFactor() {
        browser.discardPendingSecondFactorLogin()
        _loginUiState.update { it.copy(secondFactorRequired = false, backupEnabled = false, error = null) }
    }

    /**
     * 原生界面只收集凭据；账号密码、hCaptcha、Cloudflare 与 2FA 均在同一认证 WebView 中提交。
     * 先走 OkHttp 再让 WebView 重提会拆开浏览器指纹和 h_captcha_temp_id，导致 CF 后无法续交。
     */
    fun loginWithPassword(
        login: String,
        password: String,
        secondFactorToken: String? = null,
        secondFactorMethod: Int = 1,
        rememberPassword: Boolean = false,
    ) {
        if (_loginUiState.value.showWebLoginDialog) return
        loginJob?.cancel()
        loginJob = null
        diagnostics.log("原生表单已提交，转入同一 WebView 认证会话")
        openWebLoginModal(login, password, secondFactorToken, secondFactorMethod, rememberPassword)
    }

    /** 发送邮箱一次性登录链接。 */
    fun sendLoginEmail(email: String) {
        if (_loginUiState.value.sendingCode || _loginUiState.value.showWebLoginDialog) return
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            _loginUiState.update { it.copy(sendingCode = true, error = null, emailSent = false) }
            try {
                repository.sendEmailLogin(email)
                if (!isActive) return@launch
                _loginUiState.update { it.copy(sendingCode = false, emailSent = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: NeedsInteractiveVerification) {
                if (!isActive) return@launch
                _loginUiState.update {
                    it.copy(
                        sendingCode = false,
                        error = "发送邮件触发了人机安全验证，需在安全验证页面完成验证。",
                        isRiskOrCaptchaBlocked = true,
                    )
                }
            } catch (error: RiskControlException) {
                if (!isActive) return@launch
                diagnostics.log("发送邮件触发 403 风控: ${error.message}")
                _loginUiState.update {
                    it.copy(
                        sendingCode = false,
                        showRiskControlDialog = true,
                        error = null,
                    )
                }
            } catch (error: NetworkTimeoutException) {
                if (!isActive) return@launch
                _loginUiState.update { it.copy(sendingCode = false, error = "发送请求超时 (20s)，请检查网络连接。") }
            } catch (error: HttpStatusException) {
                if (!isActive) return@launch
                val isCaptcha = (error.status == 403 && (error.message?.contains("invalid_access") == true || error.message?.contains("captcha", ignoreCase = true) == true)) || error.message?.contains("人机") == true
                _loginUiState.update {
                    it.copy(
                        sendingCode = false,
                        error = if (isCaptcha) "发送邮件触发了站点安全验证，需在安全验证页面完成验证。" else "发送失败 (${error.message})",
                        isRiskOrCaptchaBlocked = isCaptcha,
                    )
                }
            } catch (error: Exception) {
                if (!isActive) return@launch
                _loginUiState.update { it.copy(sendingCode = false, error = error.message ?: "发送失败，请稍后重试。") }
            }
        }
    }

    /**
     * 用用户从邮件链接中复制的 token 完成登录（支持 2FA 两步验证）。
     */
    fun loginWithEmailCode(
        email: String,
        token: String,
        secondFactorToken: String? = null,
        secondFactorMethod: Int = 1,
    ) {
        if (_loginUiState.value.loading || _loginUiState.value.showWebLoginDialog) return
        val sanitizedToken = token.trim()
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            _loginUiState.update { it.copy(loading = true, error = null) }
            try {
                if (sanitizedToken.isBlank()) {
                    _loginUiState.update { it.copy(loading = false, error = "请输入邮件链接中的 token") }
                    return@launch
                }
                repository.emailLoginWithToken(sanitizedToken, secondFactorToken, secondFactorMethod)
                if (!isActive) return@launch
                diagnostics.log("邮件 token 登录成功,复核会话")
                _loginUiState.update { LoginUiState() }
                boot()
            } catch (error: CancellationException) {
                throw error
            } catch (error: SecondFactorRequiredException) {
                if (!isActive) return@launch
                _loginUiState.update {
                    it.copy(
                        loading = false,
                        secondFactorRequired = true,
                        backupEnabled = error.backupEnabled,
                        error = null,
                    )
                }
            } catch (error: NeedsInteractiveVerification) {
                if (!isActive) return@launch
                _loginUiState.update {
                    it.copy(
                        loading = false,
                        error = "验证请求触发了安全验证，需在安全验证页面完成验证。",
                        isRiskOrCaptchaBlocked = true,
                    )
                }
            } catch (error: RiskControlException) {
                if (!isActive) return@launch
                diagnostics.log("邮件登录触发 403 风控: ${error.message}")
                _loginUiState.update {
                    it.copy(
                        loading = false,
                        showRiskControlDialog = true,
                        error = null,
                    )
                }
            } catch (error: NetworkTimeoutException) {
                if (!isActive) return@launch
                _loginUiState.update { it.copy(loading = false, error = "验证请求超时 (20s)，请检查当前代理节点。") }
            } catch (error: LoginRejectedException) {
                if (!isActive) return@launch
                _loginUiState.update { it.copy(loading = false, error = error.message ?: "登录链接无效或已过期。") }
            } catch (error: HttpStatusException) {
                if (!isActive) return@launch
                val isCaptcha = (error.status == 403 && (error.message?.contains("invalid_access") == true || error.message?.contains("captcha", ignoreCase = true) == true)) || error.message?.contains("人机") == true
                _loginUiState.update {
                    it.copy(
                        loading = false,
                        error = if (isCaptcha) "验证请求触发了安全验证，需在安全验证页面完成验证。" else "服务器响应异常 (${error.message})",
                        isRiskOrCaptchaBlocked = isCaptcha,
                    )
                }
            } catch (error: Exception) {
                if (!isActive) return@launch
                _loginUiState.update { it.copy(loading = false, error = error.message ?: "登录失败，请检查 token 是否正确。") }
            }
        }
    }

    /** CF 验证通过后重放被拦下的邮件登录。 */
    private fun retryEmailLogin(sanitizedToken: String) {
        viewModelScope.launch {
            _loginUiState.update { it.copy(loading = true, error = null) }
            try {
                repository.emailLoginWithToken(sanitizedToken)
                _loginUiState.update { LoginUiState() }
                boot()
            } catch (error: CancellationException) {
                throw error
            } catch (error: SecondFactorRequiredException) {
                _loginUiState.update {
                    it.copy(
                        loading = false,
                        secondFactorRequired = true,
                        backupEnabled = error.backupEnabled,
                        error = null,
                    )
                }
                _state.update { it.copy(screen = Screen.Login) }
            } catch (error: RiskControlException) {
                diagnostics.log("重试邮件登录遇到 403 风控: ${error.message}")
                _loginUiState.update {
                    it.copy(
                        loading = false,
                        showRiskControlDialog = true,
                        error = null,
                    )
                }
                _state.update { it.copy(screen = Screen.Login) }
            } catch (error: Exception) {
                _loginUiState.update {
                    it.copy(loading = false, error = error.message ?: "登录失败，请重新获取验证码。")
                }
                _state.update { it.copy(screen = Screen.Login) }
            }
        }
    }

    fun dismissRiskControlDialog() {
        _loginUiState.update { it.copy(showRiskControlDialog = false) }
    }

    /** 发起网页授权认证（拉起系统浏览器或外部应用） */
    fun startWebAuth(context: Context? = null) {
        val targetContext = context ?: getApplication<Application>()
        viewModelScope.launch {
            _loginUiState.update {
                it.copy(
                    showRiskControlDialog = false,
                    webAuthLoading = true,
                    webAuthMessage = "正在准备网页授权...",
                    error = null,
                )
            }
            try {
                val url = userApiKeyAuthService.buildAuthorizeUrl()
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                targetContext.startActivity(intent)
                _loginUiState.update {
                    it.copy(
                        webAuthLoading = true,
                        webAuthMessage = "已调起浏览器，请在网页中完成授权...",
                    )
                }
            } catch (e: Exception) {
                diagnostics.log("拉起网页授权失败: ${e.message}")
                _loginUiState.update {
                    it.copy(
                        webAuthLoading = false,
                        webAuthMessage = null,
                        error = "无法打开浏览器，请检查是否安装了浏览器应用",
                    )
                }
            }
        }
    }

    fun handleWebAuthCallback(callbackUri: String) {
        val uri = runCatching { Uri.parse(callbackUri) }.getOrNull() ?: return
        handleWebAuthCallback(uri)
    }

    /** 接收深链回调并兑换令牌 */
    fun handleWebAuthCallback(uri: Uri) {
        viewModelScope.launch {
            _loginUiState.update {
                it.copy(
                    webAuthLoading = true,
                    webAuthMessage = "正在兑换登录令牌并验证...",
                    error = null,
                )
            }
            try {
                userApiKeyAuthService.handleAuthRedirect(uri)
                diagnostics.log("网页授权登录成功，重新加载会话")
                _loginUiState.update { LoginUiState() }
                boot()
            } catch (e: Exception) {
                diagnostics.log("网页授权登录兑换失败: ${e.message}")
                _loginUiState.update {
                    it.copy(
                        webAuthLoading = false,
                        webAuthMessage = null,
                        error = e.message ?: "网页授权登录失败，请重试",
                    )
                }
            }
        }
    }

    fun cancelWebAuth() {
        userApiKeyAuthService.cancel()
        _loginUiState.update {
            it.copy(
                webAuthLoading = false,
                webAuthMessage = null,
            )
        }
    }



    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        container.preferences.edit { putString("theme_mode", mode.name) }
    }

    fun setTreeView(enabled: Boolean) {
        _treeView.value = enabled
        container.preferences.edit { putBoolean("tree_view", enabled) }
    }

    fun setAutoRefreshIncoming(enabled: Boolean) {
        setAutoRefreshInterval(if (enabled) AutoRefreshInterval.MIN_1 else AutoRefreshInterval.OFF)
    }

    fun setAutoRefreshInterval(interval: AutoRefreshInterval) {
        store.setAutoRefreshInterval(interval)
        _autoRefreshInterval.value = store.getAutoRefreshInterval()
        _autoRefreshIncoming.value = store.isAutoRefreshIncomingEnabled()
        restartAutoProbeLoop()
    }

    fun setBackgroundNotification(enabled: Boolean) {
        store.setBackgroundNotificationEnabled(enabled)
        val interval = store.getNotificationPollInterval()
        _notificationPollInterval.value = interval
        _backgroundNotification.value = store.isBackgroundNotificationEnabled()
        val app = getApplication<Application>()
        if (interval != NotificationPollInterval.OFF) {
            NotificationWorker.enqueue(app)
        } else {
            NotificationWorker.cancel(app)
        }
        syncKeepAliveService()
    }

    fun setNotificationPollInterval(interval: NotificationPollInterval) {
        store.setNotificationPollInterval(interval)
        _notificationPollInterval.value = store.getNotificationPollInterval()
        _backgroundNotification.value = store.isBackgroundNotificationEnabled()
        val app = getApplication<Application>()
        if (interval != NotificationPollInterval.OFF) {
            NotificationWorker.enqueue(app)
        } else {
            NotificationWorker.cancel(app)
        }
        // 常驻通知上写着当前检测频率,开着保活就重启一次让文案和轮询周期一起跟上新档位。
        syncKeepAliveService()
    }

    fun setCustomRealtimeSeconds(seconds: Int) {
        val valid = seconds.coerceIn(1, 60)
        store.setCustomRealtimeSeconds(valid)
        _customRealtimeSeconds.value = store.getCustomRealtimeSeconds()
        val app = getApplication<Application>()
        if (store.getNotificationPollInterval() == NotificationPollInterval.REALTIME) {
            NotificationWorker.syncSilentLoop(app)
            syncKeepAliveService()
        }
    }

    /**
     * 按「后台检测频率」与「保活开关」两个条件对齐前台服务的运行状态。
     *
     * 档位关掉时不该留着常驻通知,所以这里是两个条件的与,不只看保活开关。
     */
    private fun syncKeepAliveService() {
        val app = getApplication<Application>()
        if (store.isBackgroundNotificationEnabled()) {
            NotificationKeepAliveService.start(app)
        } else {
            NotificationKeepAliveService.stop(app)
        }
    }

    fun setNotificationScope(scope: NotificationScope) {
        store.setNotificationScope(scope)
        _notificationScope.value = store.getNotificationScope()
        // 未读口径跟着作用域变,立刻按新口径重算铃铛与桌面角标,否则要等下一轮刷新才对得上。
        refreshUnread(force = true, reloadNotifications = false)
    }

    fun setKeepAliveService(enabled: Boolean) {
        store.setKeepAliveServiceEnabled(enabled)
        _keepAliveService.value = store.isKeepAliveServiceEnabled()
        syncKeepAliveService()
    }

    /**
     * 开/关「同步阅读进度与统计」。
     *
     * 关闭是默认的纯净模式:网络行为与不记录时完全一致,未读蓝点靠本地已读集合消除。
     * 关掉时顺手清掉在途计时,避免刚关完又飞出去一个上报。
     */
    fun setSyncReadProgress(enabled: Boolean) {
        if (_syncReadProgress.value == enabled) return
        _syncReadProgress.value = enabled
        container.reading.setSyncReadProgress(enabled)
        if (!enabled) {
            readReportJobs.values.forEach { it.cancel() }
            readReportJobs.clear()
            topicEnteredAt.clear()
            lastReportedAt.clear()
            reportedReadMs.clear()
            pendingVisiblePostsByTopic.clear()
            currentVisiblePostsByTopic.clear()
        }
        diagnostics.log(if (enabled) "已开启阅读进度同步(低频 timings 上报)" else "已关闭阅读进度同步(仅本地标记已读)")
    }

    /** 记录用户在屏幕上实际浏览到的楼层号 */
    fun onPostsVisible(topicId: Long, postNumbers: List<Int>) {
        if (topicId <= 0L || postNumbers.isEmpty()) return
        val valid = postNumbers.filter { it > 0 }
        if (valid.isEmpty()) return
        currentVisiblePostsByTopic[topicId] = valid
        val queue = pendingVisiblePostsByTopic.getOrPut(topicId) { LinkedHashSet() }
        valid.forEach { queue.add(it) }
    }

    private fun knownPostsCount(topicId: Long): Int {
        _details.value[topicId]?.let { detail ->
            val fromDetail = maxOf(
                detail.topic?.postsCount ?: 0,
                detail.topic?.postStream?.stream?.size ?: 0,
                detail.posts.maxOfOrNull { it.postNumber } ?: 0,
            )
            if (fromDetail > 0) return fromDetail
        }
        _state.value.latest.items.firstOrNull { it.topic.id == topicId }?.topic?.postsCount?.takeIf { it > 0 }?.let { return it }
        _feedStates.value.values.firstNotNullOfOrNull { list ->
            list.items.firstOrNull { it.topic.id == topicId }?.topic?.postsCount?.takeIf { it > 0 }
        }?.let { return it }
        _categoryTopics.value.values.firstNotNullOfOrNull { list ->
            list.items.firstOrNull { it.topic.id == topicId }?.topic?.postsCount?.takeIf { it > 0 }
        }?.let { return it }
        _search.value.results.items.firstOrNull { it.topic.id == topicId }?.topic?.postsCount?.takeIf { it > 0 }?.let { return it }
        return 0
    }

    /**
     * 本地记下"这帖看过了"(连同当前总楼层数),就地把普通列表里的未读蓝点摘掉,
     * 并将该帖从首页「未读」列表中就地移除。
     *
     * 纯净模式下服务端压根不知道我们读过,所以不能等 unseen 从服务端变化 ——
     * 只有本地这份持久化集合能保证下拉刷新、切分类、重启后蓝点与已读帖都不再反弹。
     */
    private fun markTopicSeen(topicId: Long, postsCount: Int = knownPostsCount(topicId)) {
        val changed = container.reading.markSeen(topicId, postsCount)
        fun clearUnseen(list: TopicListUiState): TopicListUiState {
            val patched = list.items.map { item ->
                if (item.topic.id == topicId && item.topic.unseen) {
                    item.copy(topic = item.topic.copy(unseen = false))
                } else item
            }
            return if (patched != list.items) list.copy(items = patched) else list
        }
        fun removeReadFromUnread(list: TopicListUiState): TopicListUiState {
            val filtered = list.items.filterNot { it.topic.id == topicId }
            return if (filtered.size != list.items.size) list.copy(items = filtered) else list
        }
        _feedStates.update { states ->
            states.mapValues { (feed, list) ->
                if (feed == HomeFeedType.Unread) removeReadFromUnread(list) else clearUnseen(list)
            }
        }
        _state.update { state ->
            val updatedLatest = if (state.homeFeed == HomeFeedType.Unread) {
                removeReadFromUnread(state.latest)
            } else {
                clearUnseen(state.latest)
            }
            if (updatedLatest != state.latest) state.copy(latest = updatedLatest) else state
        }
        if (!changed) return
        _categoryTopics.update { states -> states.mapValues { (_, list) -> clearUnseen(list) } }
        _search.update { it.copy(results = clearUnseen(it.results)) }
    }

    /**
     * 开始为这次阅读计时。
     * 停留满 3.5s 时首次上报以建立 baseline 与初始可见楼层；
     * 随后在帖内阅读期间按心跳（35s）周期性上报，确保两次上报间隔 < 100s，使服务端 time_read 正确累加。
     */
    private fun beginReadTracking(topicId: Long) {
        if (!_syncReadProgress.value || topicId in topicEnteredAt) return
        val now = SystemClock.elapsedRealtime()
        topicEnteredAt[topicId] = now
        lastReportedAt[topicId] = now
        readReportJobs[topicId]?.cancel()
        readReportJobs[topicId] = viewModelScope.launch {
            delay(Config.READ_REPORT_THRESHOLD_MS)
            val startedAt = topicEnteredAt[topicId] ?: return@launch
            // 弱网或首拉时楼层数据可能尚未完成解析与写入，轮询等待楼层就绪（最多等 5 秒）
            var waited = 0
            while (_details.value[topicId]?.posts.isNullOrEmpty() && waited < 5 && topicId in topicEnteredAt) {
                delay(1_000L)
                waited++
            }
            if (topicId !in topicEnteredAt) return@launch
            if (isTopicActive(topicId)) {
                val stayed = SystemClock.elapsedRealtime() - startedAt
                if (sendTimings(topicId, stayed)) {
                    reportedReadMs[topicId] = stayed
                    lastReportedAt[topicId] = SystemClock.elapsedRealtime()
                }
            } else {
                lastReportedAt[topicId] = SystemClock.elapsedRealtime()
            }

            // 帖内驻留心跳：每隔 READ_REPORT_HEARTBEAT_MS 累计增量上报一次，维持 0 < diff < 100s
            while (isActive && topicId in topicEnteredAt) {
                delay(Config.READ_REPORT_HEARTBEAT_MS)
                if (topicId !in topicEnteredAt) break
                if (!isTopicActive(topicId)) {
                    // 页面失活或应用处于后台：暂停心跳上报，顺延基准时间
                    lastReportedAt[topicId] = SystemClock.elapsedRealtime()
                    continue
                }
                val lastReport = lastReportedAt[topicId] ?: SystemClock.elapsedRealtime()
                val delta = SystemClock.elapsedRealtime() - lastReport
                if (delta >= 10_000L) {
                    if (sendTimings(topicId, delta)) {
                        reportedReadMs[topicId] = (reportedReadMs[topicId] ?: 0L) + delta
                        lastReportedAt[topicId] = SystemClock.elapsedRealtime()
                    }
                }
            }
        }
    }

    /** 离开话题。停留时长比上次上报多出一截时在有效窗口（5s~90s）内补报一次。 */
    fun leaveTopic(topicId: Long) {
        setTopicActive(topicId, false)
        readReportJobs.remove(topicId)?.cancel()
        val startedAt = topicEnteredAt.remove(topicId) ?: return
        val lastReport = lastReportedAt.remove(topicId) ?: startedAt
        reportedReadMs.remove(topicId)
        val pendingSet = pendingVisiblePostsByTopic.remove(topicId)
        val currentVisible = currentVisiblePostsByTopic.remove(topicId).orEmpty()
        if (!_syncReadProgress.value) return
        val now = SystemClock.elapsedRealtime()
        val totalStayed = now - startedAt
        if (totalStayed < Config.READ_REPORT_THRESHOLD_MS) return
        val pendingDelta = now - lastReport
        // 增量需在有效上报窗口内（>= 5s 且 < 90s，上游 Discourse 仅在 diff < 100s 时累加阅读时长）
        if (pendingDelta in 5_000L..90_000L) {
            val batch = pendingSet?.take(READ_REPORT_POST_CAP)?.takeIf { it.isNotEmpty() }
                ?: currentVisible.take(READ_REPORT_POST_CAP)
            if (batch.isNotEmpty()) {
                viewModelScope.launch {
                    sendTimings(topicId, pendingDelta, numbersOverride = batch)
                }
            }
        }
    }

    private suspend fun sendTimings(
        topicId: Long,
        timeMs: Long,
        numbersOverride: List<Int>? = null,
    ): Boolean {
        // 优先使用显式指定的楼层列表或待上报队列中的可见楼层（FIFO 顺序取至多 60 楼）
        val pendingSet = pendingVisiblePostsByTopic[topicId]
        val candidateNumbers = numbersOverride
            ?: pendingSet?.take(READ_REPORT_POST_CAP)?.takeIf { it.isNotEmpty() }
            ?: currentVisiblePostsByTopic[topicId]?.take(READ_REPORT_POST_CAP).orEmpty()
        val numbers = candidateNumbers.filter { it > 0 }.distinct()
        // 严禁回退到整个话题已加载楼层：若从未采集到屏幕可见楼层，坚决不虚报未读楼层
        if (numbers.isEmpty()) return false

        val success = repository.recordTopicTimings(topicId, timeMs, numbers)
        if (success && numbersOverride == null) {
            // 心跳上报成功后，从待上报队列中剔除已结算批次
            pendingSet?.removeAll(numbers.toSet())
        }
        return success
    }

    fun loadProfile(section: ProfileSection, refresh: Boolean = false, more: Boolean = false) {
        val username = _state.value.username ?: return
        if (section == ProfileSection.Summary && !more) {
            loadDrafts(refresh = refresh, silent = true)
        }
        val filter = _notificationFilter.value
        val previous = _profile.value[section] ?: ProfileUiState()
        if (previous.loading) return
        if (!refresh && !more && previous.loaded && isCacheUsable(section, filter, previous)) return
        if (more && !previous.content.hasMore) return
        val cursor = if (more) previous.content.nextCursor else 0
        pageJobs["profile:$section"] = viewModelScope.launch {
            _profile.update { it + (section to previous.copy(loading = true, error = null)) }
            try {
                val fresh = fetchProfilePage(username, section, filter, previous, more)
                _profile.update { it + (section to fresh) }
                if (section == ProfileSection.Notifications) notificationCache[filter] = fresh
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _profile.update { it + (section to previous.copy(error = error.message ?: "加载失败")) }
                handlePageFailure(error) { loadProfile(section, refresh = true) }
            } finally {
                _profile.update { states -> states[section]?.let { states + (section to it.copy(loading = false)) } ?: states }
            }
        }
    }

    /** 拉一页并组装成新状态。前台加载和后台预取共用这一条路径,避免两套逻辑走偏。 */
    private suspend fun fetchProfilePage(
        username: String,
        section: ProfileSection,
        filter: NotificationFilter,
        previous: ProfileUiState,
        more: Boolean,
    ): ProfileUiState {
        val cursor = if (more) previous.content.nextCursor else 0
        val excludeIds = if (more && section == ProfileSection.Notifications) {
            previous.content.entries.map { it.id }.toSet()
        } else emptySet()
        val unreadNow = unreadBaseline(section, filter)
        val result = repository.fetchProfile(username, section, cursor, filter, excludeIds)
        val entries = if (more) (previous.content.entries + result.entries).distinctBy { it.id } else result.entries
        return ProfileUiState(
            // 防死循环的守卫看的是游标有没有往前走,而不是条目有没有变多 ——
            // 低频通知类型完全可能某一轮一条都没命中,但后面还有。
            content = result.copy(entries = entries,
                hasMore = result.hasMore && (!more || result.nextCursor > previous.content.nextCursor)),
            loaded = true,
            loadedAt = SystemClock.elapsedRealtime(),
            unreadAtLoad = unreadNow,
        )
    }

    /**
     * 这个分区该拿哪个未读数做基准。
     * 通知按分类分项取;总结/活动/账号这些和未读无关的分区返回 0,等于只受 TTL 约束 ——
     * 别因为来了条回复就把账号资料的缓存也作废。
     */
    private fun unreadBaseline(section: ProfileSection, filter: NotificationFilter): Int = when (section) {
        ProfileSection.Notifications -> _state.value.unreadByFilter[filter] ?: 0
        ProfileSection.Messages -> _state.value.unreadByFilter[NotificationFilter.Messages] ?: 0
        else -> 0
    }

    /**
     * 这份缓存还能不能直接拿来显示。
     *
     * 两条判据取"与":该分区对应的未读没涨 **且** 没到 TTL。
     * 未读必须按分类取 —— 铃铛的总数只算回复类和私信类,点赞压根不计入,
     * 拿总数判断会让点赞分类永远判定为没变化、一直吃旧缓存。
     * 未读变少是用户自己读掉的,列表已经就地更新过,所以用 `<=` 而不是 `==`。
     */
    private fun isCacheUsable(
        section: ProfileSection,
        filter: NotificationFilter,
        state: ProfileUiState,
    ): Boolean {
        if (!state.loaded) return false
        if (SystemClock.elapsedRealtime() - state.loadedAt >= Config.PROFILE_CACHE_TTL_MS) return false
        return unreadBaseline(section, filter) <= state.unreadAtLoad
    }

    /**
     * 切换通知分类。
     *
     * 四个分类共用 `_profile` 里同一个槽位(键是 ProfileSection),所以这里额外用
     * notificationCache 按分类留一份。命中且仍新鲜就直接贴回去 —— 点标签立刻有内容,
     * 不用再等一轮网络。再次点击当前已选中的分类标签时强制刷新。
     */
    fun setNotificationFilter(filter: NotificationFilter) {
        if (_notificationFilter.value == filter) {
            loadProfile(ProfileSection.Notifications, refresh = true)
            return
        }
        pageJobs["profile:${ProfileSection.Notifications}"]?.cancel()
        _notificationFilter.value = filter
        val cached = notificationCache[filter]
        if (cached != null && isCacheUsable(ProfileSection.Notifications, filter, cached)) {
            _profile.update { it + (ProfileSection.Notifications to cached.copy(loading = false, error = null)) }
            return
        }
        notificationCache.remove(filter)
        _profile.update { it - ProfileSection.Notifications }
        loadProfile(ProfileSection.Notifications)
    }

    fun retryTopic(id: Long, postNumber: Int? = null) {
        val failed = _details.value[id]?.failedLoad
        loadTopic(id, more = failed == DetailLoad.Next, previous = failed == DetailLoad.Previous,
            retry = true, postNumber = postNumber)
    }

    fun refreshTopic(id: Long, silent: Boolean = false) {
        val current = _details.value[id] ?: return
        if (current.loading || current.refreshing || current.reactingPosts.isNotEmpty()) return
        if (pageJobs["topic:$id"]?.isActive == true) return
        pageJobs["topic:$id"] = viewModelScope.launch {
            if (!silent) {
                _details.update { states ->
                    states[id]?.let { states + (id to it.copy(refreshing = true, error = null)) } ?: states
                }
            }
            try {
                // 默认带上 track_visit=true 记录用户活跃与话题浏览。
                val freshTopic = repository.fetchTopic(id, trackVisit = true)
                val freshById = freshTopic.postStream.posts.associateBy { it.id }
                val latest = _details.value[id] ?: current
                val wasAtEnd = latest.remaining.isEmpty()
                val updatedExisting = latest.posts.map { freshById[it.id] ?: it }
                var mergedPosts = (updatedExisting + freshTopic.postStream.posts)
                    .distinctBy { it.id }
                    .sortedBy { it.postNumber }
                var consumed = latest.consumedIds + freshTopic.postStream.posts.map { it.id }

                val windowIdx = freshTopic.postStream.stream.indexOf(latest.windowStartId).coerceAtLeast(0)
                val newRemaining = freshTopic.postStream.stream.drop(windowIdx).filterNot { it in consumed }
                // 若刷新前用户已看完旧楼层，且服务端出现了新回复，直接把最新一批新回复拉下来并入列表。
                if (wasAtEnd && newRemaining.isNotEmpty()) {
                    val tailBatch = newRemaining.take(20)
                    val fetchedTail = runCatching { repository.fetchPosts(id, tailBatch) }.getOrNull()
                    if (fetchedTail != null) {
                        mergedPosts = (mergedPosts + fetchedTail).distinctBy { it.id }.sortedBy { it.postNumber }
                        consumed = consumed + tailBatch
                    }
                }
                _details.update { states ->
                    val cur = states[id] ?: return@update states
                    states + (id to cur.copy(
                        topic = freshTopic,
                        posts = mergedPosts,
                        consumedIds = consumed,
                        refreshing = false,
                        error = null,
                    ))
                }
                markTopicSeen(
                    id,
                    maxOf(
                        freshTopic.postsCount,
                        freshTopic.postStream.stream.size,
                        mergedPosts.maxOfOrNull { it.postNumber } ?: 0,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!silent) {
                    _details.update { states ->
                        states[id]?.let {
                            states + (id to it.copy(error = error.message ?: "刷新失败", failedLoad = DetailLoad.Topic))
                        } ?: states
                    }
                    handlePageFailure(error) { refreshTopic(id, silent = false) }
                }
            } finally {
                _details.update { states ->
                    states[id]?.let { states + (id to it.copy(refreshing = false)) } ?: states
                }
            }
        }
    }

    fun loadTopic(id: Long, more: Boolean = false, retry: Boolean = false, postNumber: Int? = null, previous: Boolean = false) {
        val current = _details.value[id] ?: DetailUiState()
        if (!more && !previous) {
            markTopicSeen(id)
            beginReadTracking(id)
        }
        val targetLoaded = postNumber == null || current.posts.any { it.postNumber == postNumber }
        val isExplicitJump = !more && !previous && postNumber != null && !targetLoaded
        if (current.reactingPosts.isNotEmpty() || (current.loading && !isExplicitJump)) return
        if (!more && !previous && current.topic != null && !retry && targetLoaded) {
            // 再次进入已缓存的话题时，后台增量同步最新 postStream 与新增回复，避免卡在旧评论数。
            // 若带了 postNumber，无论是否命中缓存，都递增 locateVersion 触发界面重新定位；
            // 同时始终执行后台静默刷新，确保楼层数与内容是最新的。
            if (postNumber != null) {
                _details.update { states ->
                    states[id]?.let { states + (id to it.copy(locateVersion = it.locateVersion + 1)) } ?: states
                }
            }
            refreshTopic(id, silent = true)
            return
        }
        if (more && current.remaining.isEmpty()) return
        if (previous && current.previousIds.isEmpty()) return
        pageJobs["topic:$id"]?.cancel()
        pageJobs["topic:$id"] = viewModelScope.launch {
            _details.update { it + (id to current.copy(loading = true, error = null)) }
            try {
                if (more || previous) {
                    val requested = when {
                        previous -> current.previousIds.takeLast(20)
                        // 倒序是由新到旧回溯,所以从尚未加载的楼层尾部往前取。
                        current.descending -> current.remaining.takeLast(20)
                        else -> current.remaining.take(20)
                    }
                    val posts = repository.fetchPosts(id, requested)
                    _details.update {
                        val latest = it[id] ?: current
                        it + (id to latest.copy(
                            posts = (latest.posts + posts).distinctBy { post -> post.id }.sortedBy { post -> post.postNumber },
                            consumedIds = latest.consumedIds + requested,
                            windowStartId = if (previous) requested.first() else current.windowStartId,
                            error = null, failedLoad = DetailLoad.Topic,
                        ))
                    }
                } else {
                    val topic = repository.fetchTopic(id, postNumber, trackVisit = true)
                    val category = repository.categoryById(topic.categoryId)
                    val emojis = repository.emojiUrls()
                    val rootPost = current.posts.filter { it.postNumber == 1 }
                    val posts = if (postNumber != null) (rootPost + topic.postStream.posts).distinctBy { it.id }.sortedBy { it.postNumber }
                        else topic.postStream.posts.distinctBy { it.id }
                    val missing = postNumber != null && posts.none { it.postNumber == postNumber }
                    _details.update {
                        it + (id to DetailUiState(topic, posts, posts.map { post -> post.id }.toSet(), category,
                            error = if (missing) "通知对应楼层 #$postNumber 已删除或不可访问" else null,
                            emojiUrls = emojis, windowStartId = topic.postStream.posts.minByOrNull { it.postNumber }?.id))
                    }
                    markTopicSeen(
                        id,
                        maxOf(
                            topic.postsCount,
                            topic.postStream.stream.size,
                            posts.maxOfOrNull { it.postNumber } ?: 0,
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _details.update { it + (id to current.copy(error = error.message ?: "加载失败",
                    failedLoad = when { previous -> DetailLoad.Previous; more -> DetailLoad.Next; else -> DetailLoad.Topic })) }
                handlePageFailure(error) { loadTopic(id, more, retry = true, postNumber = postNumber, previous = previous) }
            } finally {
                _details.update { states -> states[id]?.let { states + (id to it.copy(loading = false)) } ?: states }
            }
        }
    }

    /**
     * 切换评论区正序 / 倒序。
     *
     * 短帖(楼层已全在手上)和切回正序都是纯内存翻转,0 网络请求。
     * 长帖切到倒序时只保留 **1 楼 + stream 尾部的连续窗口**,而不是把开头的 2~20 楼和
     * 尾部的 281~300 楼混排:混排一是滑过 #281 会直接跳到 #20、中间断层两百多楼,
     * 二是 #299 若回复过 #5,树形排列会把最新回复吸进排在最底部的 #5 楼中楼里。
     * 切回正序时 windowStartId 仍指着尾窗首个,现有的"加载之前的楼层"缺口会自动补位。
     */
    fun toggleCommentOrder(topicId: Long) {
        val current = _details.value[topicId] ?: return
        if (current.loading || current.refreshing) return
        val target = !current.descending
        val stream = current.topic?.postStream?.stream.orEmpty()
        val tail = stream.takeLast(TAIL_WINDOW)
        val tailMissing = tail.filterNot { it in current.consumedIds }
        if (!target || tailMissing.isEmpty()) {
            _details.update { states ->
                states[topicId]?.let { states + (topicId to it.copy(descending = target)) } ?: states
            }
            return
        }
        pageJobs["topic:$topicId"]?.cancel()
        pageJobs["topic:$topicId"] = viewModelScope.launch {
            _details.update { it + (topicId to current.copy(loading = true, error = null, descending = true)) }
            try {
                val fetched = repository.fetchPosts(topicId, tailMissing)
                _details.update { states ->
                    val latest = states[topicId] ?: return@update states
                    val merged = (latest.posts.filter { it.postNumber == 1 || it.id in tail } + fetched)
                        .distinctBy { it.id }
                        .sortedBy { it.postNumber }
                    val anchor = merged.firstOrNull { it.postNumber > 1 } ?: merged.firstOrNull()
                    states + (topicId to latest.copy(
                        posts = merged,
                        consumedIds = merged.map { it.id }.toSet(),
                        windowStartId = anchor?.id ?: latest.windowStartId,
                        // 窗口换了一批楼层,楼中楼的游标和"已取完"标记跟着作废,否则展开按钮会点不动。
                        replyCursors = emptyMap(),
                        completedReplies = emptySet(),
                        descending = true,
                        error = null,
                        failedLoad = DetailLoad.Topic,
                    ))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _details.update { states ->
                    states[topicId]?.let {
                        states + (topicId to it.copy(descending = current.descending, error = error.message ?: "切换顺序失败"))
                    } ?: states
                }
                handlePageFailure(error) { toggleCommentOrder(topicId) }
            } finally {
                _details.update { states -> states[topicId]?.let { states + (topicId to it.copy(loading = false)) } ?: states }
            }
        }
    }

    fun react(topicId: Long, postId: Long, reaction: String) {
        val current = _details.value[topicId] ?: return
        if (current.loading) return
        val original = current.posts.firstOrNull { it.id == postId } ?: return

        val alreadyReacted = original.currentUserReaction?.id == reaction
        val newCurrentUserReaction = if (alreadyReacted) null else Reaction(id = reaction, count = 1, canUndo = true)
        val newReactions = original.reactions.toMutableList()
        val existingIndex = newReactions.indexOfFirst { it.id == reaction }
        if (alreadyReacted) {
            if (existingIndex >= 0) {
                val currentCount = newReactions[existingIndex].count
                if (currentCount <= 1) {
                    newReactions.removeAt(existingIndex)
                } else {
                    newReactions[existingIndex] = newReactions[existingIndex].copy(count = currentCount - 1)
                }
            }
        } else {
            val oldReactionId = original.currentUserReaction?.id
            if (oldReactionId != null) {
                val oldIndex = newReactions.indexOfFirst { it.id == oldReactionId }
                if (oldIndex >= 0) {
                    val count = newReactions[oldIndex].count
                    if (count <= 1) newReactions.removeAt(oldIndex) else newReactions[oldIndex] = newReactions[oldIndex].copy(count = count - 1)
                }
            }
            if (existingIndex >= 0) {
                newReactions[existingIndex] = newReactions[existingIndex].copy(count = newReactions[existingIndex].count + 1)
            } else {
                newReactions.add(Reaction(id = reaction, count = 1, canUndo = true))
            }
        }
        val optimisticPost = original.copy(
            currentUserReaction = newCurrentUserReaction,
            reactions = newReactions,
        )
        _details.update { states ->
            val latest = states[topicId] ?: return@update states
            states + (topicId to latest.copy(
                posts = latest.posts.map { if (it.id == postId) optimisticPost else it },
                reactionErrors = latest.reactionErrors - postId,
            ))
        }

        pageJobs["reaction:$postId"] = viewModelScope.launch {
            try {
                val updated = repository.toggleReaction(postId, reaction).copy(boosts = original.boosts, canBoost = original.canBoost)
                _details.update { states ->
                    val latest = states[topicId] ?: return@update states
                    states + (topicId to latest.copy(
                        posts = latest.posts.map { if (it.id == postId) updated else it }
                    ))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _details.update { states ->
                    val latest = states[topicId] ?: return@update states
                    states + (topicId to latest.copy(
                        posts = latest.posts.map { if (it.id == postId) original else it },
                        reactionErrors = latest.reactionErrors + (postId to (error.message ?: "回应失败")),
                    ))
                }
                handlePageFailure(error) { react(topicId, postId, reaction) }
            }
        }
    }

    fun refreshUnread(force: Boolean = false, reloadNotifications: Boolean = true) {
        if (unreadJob?.isActive == true || _state.value.screen != Screen.List) return
        if (force) {
            nextUnreadRefreshAt = 0L
            unreadFailures = 0
        } else if (SystemClock.elapsedRealtime() < nextUnreadRefreshAt) {
            return
        }
        unreadJob = viewModelScope.launch {
            try {
                val beforeByFilter = _state.value.unreadByFilter
                val summary = repository.fetchUnreadSummary(store.getNotificationScope())
                unreadFailures = 0
                nextUnreadRefreshAt = 0L
                _state.update { it.copy(
                    unreadCount = summary.count,
                    unreadFilters = summary.unreadFilters,
                    unreadByFilter = summary.unreadByFilter,
                ) }
                if (!store.isBadgeTestHolding()) {
                    store.setLastBadgeCount(summary.count)
                    NotificationHelper.updateBadgeCount(getApplication(), summary.count, source = "前台未读刷新")
                }
                // 任一分类的未读变多才需要动。变少是用户自己读掉的,列表已经就地更新过。
                val grew = summary.unreadByFilter.any { (filter, now) -> now > (beforeByFilter[filter] ?: 0) }
                if (reloadNotifications && grew) prefetchUnreadNotifications()
            }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                unreadFailures = (unreadFailures + 1).coerceAtMost(2)
                nextUnreadRefreshAt = SystemClock.elapsedRealtime() + 5_000L
                diagnostics.log("未读数量刷新失败: ${error.javaClass.simpleName}")
            }
        }
    }

    /**
     * 预热有未读的通知分类,让用户点标签时直接有内容。
     *
     * 一轮只预热一个分类:repository 的 gate 是全局串行锁,预取一旦占住它,
     * 用户紧接着的操作就得排在后面等。未读每 10 秒轮询一次,几轮下来相关分类都会热好,
     * 而单次占用是有上限的。当前正在看的分类优先,其余按声明顺序补。
     */
    private fun prefetchUnreadNotifications() {
        if (loadingFeeds.isNotEmpty() || pageJobs.values.any { it.isActive }) return
        val username = _state.value.username ?: return
        val current = _notificationFilter.value
        val unread = _state.value.unreadFilters
        val target = (listOf(current) + NotificationFilter.entries).distinct()
            .filter { it == current || it in unread }
            .firstOrNull { filter ->
                val cached = notificationCache[filter]
                cached == null || !isCacheUsable(ProfileSection.Notifications, filter, cached)
            } ?: return

        pageJobs["prefetch:notifications"] = viewModelScope.launch {
            // 预取的正好是用户当前看的那一类时,把 loading 立起来:
            // 既让界面有反馈,也挡住 loadProfile 再发一次重复请求。
            val showing = _notificationFilter.value == target
            if (showing) {
                _profile.update { states ->
                    states + (ProfileSection.Notifications to
                        (states[ProfileSection.Notifications] ?: ProfileUiState()).copy(loading = true, error = null))
                }
            }
            try {
                val fresh = fetchProfilePage(username, ProfileSection.Notifications, target, ProfileUiState(), more = false)
                notificationCache[target] = fresh
                if (_notificationFilter.value == target) {
                    _profile.update { it + (ProfileSection.Notifications to fresh) }
                }
                diagnostics.log("已预取通知分类: ${target.label}")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // 用户还没点进来,预取失败不该弹错误;真点进去时会正常重试并展示错误。
                diagnostics.log("预取通知失败: ${error.javaClass.simpleName}")
            } finally {
                if (showing) {
                    _profile.update { states ->
                        states[ProfileSection.Notifications]?.let {
                            states + (ProfileSection.Notifications to it.copy(loading = false))
                        } ?: states
                    }
                }
            }
        }
    }

    fun readNotification(id: Long) {
        readNotification(id, null)
    }

    fun readNotification(id: Long, knownType: Int?) {
        if (pageJobs["notification:$id"]?.isActive == true) return
        // 先本地扣减再发请求。否则角标要等 markNotificationRead 和 fetchUnreadSummary
        // 两趟请求(各自还有 1 秒节流 + 往返)才更新,用户点进去再退回来会看到它还亮着。
        val type = knownType ?: _profile.value[ProfileSection.Notifications]?.content?.entries
            ?.firstOrNull { it.id == id.toString() }?.notificationType
            ?: notificationCache.values.firstNotNullOfOrNull { page ->
                page.content.entries.firstOrNull { it.id == id.toString() }?.notificationType
            }
        markReadLocally(id, type)
        pageJobs["notification:$id"] = viewModelScope.launch {
            try {
                repository.markNotificationRead(id)
                // 等正在进行的分页统计结束,避免已读后的强制刷新被 isActive 守卫丢掉。
                unreadJob?.join()
                // 这条通知上面已经就地标成已读了,不要再整段重拉 —— 那会把用户翻过的页面
                // 和滚动位置一起丢掉(低频分类还要为此多扫好几页)。
                refreshUnread(force = true, reloadNotifications = false)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { diagnostics.log("通知已读状态未同步: ${error.javaClass.simpleName}") }
        }
    }

    /**
     * 就地把一条通知标为已读,并把它从未读计数里扣掉。
     *
     * **按条减 1,不是清零。** 有 5 条未读回复时读掉 1 条,回复分类的计数变 4、角标继续亮着,
     * 减到 0 才摘点。拿不到这条的类型(不在当前已加载列表里)就默认扣减 1 个主铃铛数,随后的 refreshUnread 会用服务端权威数据覆盖。
     */
    private fun markReadLocally(id: Long, type: Int?) {
        val key = id.toString()
        fun markRead(page: ProfilePage) = page.copy(
            entries = page.entries.map { if (it.id == key) it.copy(unread = false) else it },
        )
        _profile.update { panels ->
            panels[ProfileSection.Notifications]?.let { notices ->
                panels + (ProfileSection.Notifications to notices.copy(content = markRead(notices.content)))
            } ?: panels
        }
        // 按分类的缓存也要跟着改,否则切出去再切回来这条又变回未读。
        notificationCache.replaceAll { _, cached -> cached.copy(content = markRead(cached.content)) }
        if (type == null) {
            _state.update { state ->
                state.copy(unreadCount = (state.unreadCount - 1).coerceAtLeast(0))
            }
            if (!store.isBadgeTestHolding()) {
                store.setLastBadgeCount(_state.value.unreadCount)
                NotificationHelper.updateBadgeCount(getApplication(), _state.value.unreadCount, source = "本地已读")
            }
            return
        }
        _state.update { state ->
            val reduced = state.unreadByFilter.mapValues { (filter, count) ->
                if (filter.includes(type)) (count - 1).coerceAtLeast(0) else count
            }
            state.copy(
                unreadByFilter = reduced,
                unreadFilters = reduced.filterValues { it > 0 }.keys,
                // 口径跟随通知作用域,与桌面角标、推送下发共用 NotificationScope.includes。
                unreadCount = if (store.getNotificationScope().includes(type)) {
                    (state.unreadCount - 1).coerceAtLeast(0)
                } else state.unreadCount,
            )
        }
        if (!store.isBadgeTestHolding()) {
            store.setLastBadgeCount(_state.value.unreadCount)
            NotificationHelper.updateBadgeCount(getApplication(), _state.value.unreadCount, source = "本地已读")
        }
    }

    fun refreshPost(topicId: Long, postId: Long) {
        changePost(topicId, postId) { repository.fetchPosts(topicId, listOf(postId)).single() }
    }

    fun setSolution(topicId: Long, postId: Long) {
        val post = _details.value[topicId]?.posts?.firstOrNull { it.id == postId } ?: return
        if (post.acceptedAnswer && !post.canUnacceptAnswer || !post.acceptedAnswer && !post.canAcceptAnswer) return
        changePost(topicId, postId) {
            val accepted = !it.acceptedAnswer
            repository.setSolution(postId, accepted)
            it.copy(acceptedAnswer = accepted, canAcceptAnswer = !accepted, canUnacceptAnswer = accepted)
        }
    }

    fun createBoost(topicId: Long, postId: Long, raw: String) {
        changePost(topicId, postId) { post ->
            val boost = repository.createBoost(postId, raw)
            post.copy(boosts = post.boosts + boost, canBoost = false)
        }
    }

    fun deleteBoost(topicId: Long, postId: Long, boostId: Long) {
        changePost(topicId, postId) { post ->
            repository.deleteBoost(boostId)
            post.copy(boosts = post.boosts.filterNot { it.id == boostId }, canBoost = !post.yours)
        }
    }

    private fun changePost(topicId: Long, postId: Long, action: suspend (Post) -> Post) {
        val current = _details.value[topicId] ?: return
        if (current.loading || current.reactingPosts.isNotEmpty()) return
        val original = current.posts.firstOrNull { it.id == postId } ?: return
        pageJobs["reaction:$postId"] = viewModelScope.launch {
            _details.update { it + (topicId to current.copy(reactingPosts = setOf(postId), reactionErrors = current.reactionErrors - postId)) }
            try {
                val updated = action(original)
                _details.update { states ->
                    val latest = states[topicId] ?: return@update states
                    states + (topicId to latest.copy(posts = latest.posts.map {
                        when {
                            it.id == updated.id -> updated
                            updated.acceptedAnswer && it.acceptedAnswer -> it.copy(acceptedAnswer = false,
                                canAcceptAnswer = it.canUnacceptAnswer, canUnacceptAnswer = false)
                            else -> it
                        }
                    }))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _details.update { states -> states[topicId]?.let {
                    states + (topicId to it.copy(reactionErrors = it.reactionErrors + (postId to (error.message ?: "回应失败"))))
                } ?: states }
                // toggle 不是幂等操作,验证后只刷新状态,不自动重放写请求。
                handlePageFailure(error) { loadTopic(topicId, retry = true) }
            } finally {
                _details.update { states -> states[topicId]?.let { states + (topicId to it.copy(reactingPosts = emptySet())) } ?: states }
            }
        }
    }

    /** 打开编辑器。编辑已有楼层时顺带把原文取回来填进输入框。 */
    fun openComposer(target: ComposerTarget) {
        if (_composer.value.submitting) return
        val needsDraft = target is ComposerTarget.Edit
        _composer.value = ComposerUiState(target = target, loadingDraft = needsDraft)
        if (target !is ComposerTarget.Edit) return
        pageJobs["draft:${target.postId}"] = viewModelScope.launch {
            try {
                val raw = repository.fetchPostRaw(target.postId)
                _composer.update { if (it.target == target) it.copy(draft = raw, loadingDraft = false) else it }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _composer.update {
                    if (it.target == target) it.copy(loadingDraft = false,
                        error = error.message ?: "原文加载失败，请关闭后重试") else it
                }
            }
        }
    }

    fun updateDraft(text: String) {
        _composer.update { if (it.open) it.copy(draft = text, error = null) else it }
    }

    fun closeComposer() {
        if (_composer.value.submitting) return
        _composer.value = ComposerUiState()
    }

    private fun assembleRaw(raw: String, attachments: List<AttachedMedia>): String {
        if (attachments.isEmpty()) return raw
        val unincluded = attachments.filter { !raw.contains(it.uploaded.shortUrl) }
        if (unincluded.isEmpty()) return raw
        val gap = if (raw.isBlank() || raw.endsWith("\n")) "" else "\n\n"
        return raw + gap + unincluded.joinToString("\n") { it.toMarkdown() }
    }

    /**
     * 上传选中的文件或图片。
     * 上传成功后以缩略图/卡片原样展示供用户确认，不把难懂的 Markdown 短码强塞进输入框；
     * 最终在发送或保存草稿时自动转化为 Discourse 编码。
     */
    fun attachToComposer(uri: Uri) {
        val current = _composer.value
        if (!current.open || current.uploading || current.submitting) return
        pageJobs["upload"] = viewModelScope.launch {
            _composer.update { it.copy(uploading = true, error = null) }
            try {
                val picked = withContext(Dispatchers.IO) { readPickedFile(uri) }
                val uploaded = repository.uploadFile(picked.name, picked.mimeType, picked.bytes)
                val isImg = uploaded.width > 0 || UploadLimits.isCompressibleImage(picked.name) ||
                    picked.mimeType.startsWith("image/")
                val media = AttachedMedia(
                    name = uploaded.fileName,
                    previewUrl = uri.toString(),
                    uploaded = uploaded,
                    isImage = isImg,
                )
                _composer.update {
                    if (!it.open) it.copy(uploading = false) else {
                        it.copy(attachments = it.attachments + media, uploading = false)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _composer.update { it.copy(uploading = false, error = error.message ?: "上传失败，请稍后重试") }
                handlePageFailure(error) {}
            }
        }
    }

    fun removeComposerAttachment(id: String) {
        _composer.update { it.copy(attachments = it.attachments.filterNot { a -> a.id == id }) }
    }

    private data class PickedFile(val name: String, val mimeType: String, val bytes: ByteArray)

    /** 先按大小、类型预检,再限量读取；位图按需降采样。 */
    private fun readPickedFile(uri: Uri): PickedFile {
        val resolver = getApplication<Application>().contentResolver
        var displayName: String? = null
        var reportedSize: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameColumn >= 0 && !cursor.isNull(nameColumn)) displayName = cursor.getString(nameColumn)
                    val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                        reportedSize = cursor.getLong(sizeColumn).takeIf { it >= 0 }
                    }
                }
            }
        val name = displayName?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "upload"
        val bytes = UploadLimits.readBytes(name, reportedSize) { resolver.openInputStream(uri) }
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        if (!UploadLimits.isCompressibleImage(name)) return PickedFile(name, mime, bytes)
        return shrinkImage(name, bytes) ?: PickedFile(name, mime, bytes)
    }

    /**
     * 把超尺寸/超大的位图收一收。返回 null 表示不需要动或处理不了(调用方原样上传)。
     *
     * PNG 优先仍编码成 PNG —— 转 JPEG 会把透明区域填成黑色。只有降采样后 PNG 还是太大时
     * 才退成 JPEG,那种情况下用户本来也没得选(4MB 是硬上限)。
     * GIF/SVG/AVIF/JXL 不走这里:动图会被压成静态,后两者解码支持也不稳。
     */
    private fun shrinkImage(name: String, bytes: ByteArray): PickedFile? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) return@runCatching null
        // 既没超尺寸也没超大小就别碰,重编码只会白掉画质。
        if (longEdge <= UploadLimits.MAX_IMAGE_EDGE && bytes.size <= UploadLimits.MAX_BYTES) return@runCatching null

        var sample = 1
        while (longEdge / sample > UploadLimits.MAX_IMAGE_EDGE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return@runCatching null

        val isPng = UploadLimits.extensionOf(name) == "png"
        val out = ByteArrayOutputStream()
        if (isPng) {
            decoded.compress(Bitmap.CompressFormat.PNG, 100, out)
            if (out.size() <= UploadLimits.MAX_BYTES) {
                decoded.recycle()
                return@runCatching PickedFile(name, "image/png", out.toByteArray())
            }
        }
        var quality = UploadLimits.JPEG_QUALITY
        do {
            out.reset()
            decoded.compress(Bitmap.CompressFormat.JPEG, quality, out)
            quality -= 10
        } while (out.size() > UploadLimits.MAX_BYTES && quality >= 40)
        decoded.recycle()
        PickedFile("${name.substringBeforeLast('.', name)}.jpg", "image/jpeg", out.toByteArray())
    }.getOrNull()

    /** 提交编辑器内容。成功后就地并入已加载楼层,不整页重拉。 */
    fun submitComposer() {
        val current = _composer.value
        val target = current.target ?: return
        if (!current.canSubmit) return
        val raw = assembleRaw(current.draft, current.attachments)
        pageJobs["composer"] = viewModelScope.launch {
            _composer.update { it.copy(submitting = true, error = null) }
            try {
                when (target) {
                    is ComposerTarget.NewReply -> {
                        when (val result = repository.createPost(target.topicId, raw)) {
                            is PostResult.Success -> applyCreatedPost(target.topicId, result.post, forceTailWindow = true)
                            is PostResult.Enqueued -> setNoticeMessage(target.topicId, "您的回复已提交，正等待管理员审核后显示")
                        }
                    }
                    is ComposerTarget.ReplyTo -> {
                        when (val result = repository.createPost(target.topicId, raw, target.postNumber)) {
                            is PostResult.Success -> applyCreatedPost(target.topicId, result.post, forceTailWindow = false)
                            is PostResult.Enqueued -> setNoticeMessage(target.topicId, "您的回复已提交，正等待管理员审核后显示")
                        }
                    }
                    is ComposerTarget.Edit -> applyEditedPost(target.topicId, repository.updatePost(target.postId, raw))
                }
                _composer.value = ComposerUiState()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _composer.update { it.copy(submitting = false, error = error.message ?: "提交失败，请稍后重试") }
                handlePageFailure(error) {}
            }
        }
    }

    fun loadDrafts(refresh: Boolean = false, silent: Boolean = false) {
        if (_state.value.username == null) return
        val current = _drafts.value
        if (current.loading) return
        if (!refresh && current.loaded) return
        pageJobs["drafts"] = viewModelScope.launch {
            if (!silent || !current.loaded) {
                _drafts.update { it.copy(loading = true, error = null) }
            }
            try {
                val list = repository.fetchDrafts()
                _drafts.update {
                    it.copy(
                        items = list,
                        loaded = true,
                        loading = false,
                        error = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _drafts.update {
                    it.copy(
                        loading = false,
                        error = if (silent && it.loaded) null else (error.message ?: "草稿加载失败"),
                    )
                }
                if (!silent) {
                    handlePageFailure(error) { loadDrafts(refresh = true) }
                }
            }
        }
    }

    fun deleteDraft(draft: DraftItem) {
        if (_state.value.username == null) return
        if (draft.draftKey in _drafts.value.deletingKeys) return
        pageJobs["deleteDraft:${draft.draftKey}"] = viewModelScope.launch {
            _drafts.update { it.copy(deletingKeys = it.deletingKeys + draft.draftKey, error = null) }
            try {
                repository.deleteDraft(draft.draftKey, draft.sequence)
                _drafts.update {
                    it.copy(
                        items = it.items.filterNot { item -> item.draftKey == draft.draftKey },
                        deletingKeys = it.deletingKeys - draft.draftKey,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _drafts.update {
                    it.copy(
                        deletingKeys = it.deletingKeys - draft.draftKey,
                        error = error.message ?: "删除草稿失败",
                    )
                }
                handlePageFailure(error) {}
            }
        }
    }

    /**
     * 打开创建新话题界面。
     * - 从首页进入时 `initialCategoryId` 为 `null`（分类默认为空）。
     * - 从指定分区进入时 `initialCategoryId` 为当前分区的 `categoryId`（已包含当前所选等级 Lv.0~3）。
     */
    fun openTopicCreator(initialCategoryId: Int? = null) {
        if (_topicCreator.value.submitting || _topicCreator.value.savingDraft) return
        if (!_categories.value.loaded && !_categories.value.loading) {
            loadCategories()
        }
        val existingNewTopicDraft = _drafts.value.items.firstOrNull { it.draftKey == "new_topic" }
        val baseState = TopicCreatorUiState(
            open = true,
            draftKey = "new_topic",
            sequence = existingNewTopicDraft?.sequence ?: 0L,
            action = "createTopic",
            title = "",
            raw = "",
            categoryId = initialCategoryId,
            tags = emptyList(),
        )
        _topicCreator.value = baseState.copy(savedSnapshot = baseState.currentSnapshot)
        populateCreatorEmojisAndSequence("new_topic")
        loadTopicCreatorTags(initialCategoryId)
    }

    /** 如果是话题回复类草稿，直接打开该话题的回复栏并将草稿内容填入。 */
    fun openReplyWithDraft(draft: DraftItem) {
        val topicId = draft.topicId ?: return
        val target = if (draft.replyToPostNumber != null) {
            ComposerTarget.ReplyTo(topicId = topicId, postNumber = draft.replyToPostNumber, username = "")
        } else {
            ComposerTarget.NewReply(topicId = topicId)
        }
        _composer.value = ComposerUiState(
            target = target,
            draft = draft.raw,
            loadingDraft = false,
            submitting = false,
            uploading = false,
        )
    }

    /** 从草稿列表点击编辑，将草稿内容填入发布器。 */
    fun openDraftInCreator(draft: DraftItem) {
        if (_topicCreator.value.submitting || _topicCreator.value.savingDraft) return
        if (!_categories.value.loaded && !_categories.value.loading) {
            loadCategories()
        }
        val baseState = TopicCreatorUiState(
            open = true,
            draftKey = draft.draftKey,
            sequence = draft.sequence,
            action = draft.action,
            replyTopicId = draft.topicId,
            replyToPostNumber = draft.replyToPostNumber,
            title = draft.title,
            raw = draft.raw,
            categoryId = draft.categoryId,
            tags = draft.tags,
        )
        _topicCreator.value = baseState.copy(savedSnapshot = baseState.currentSnapshot)
        populateCreatorEmojisAndSequence(draft.draftKey)
        loadTopicCreatorTags(draft.categoryId)
    }

    private fun populateCreatorEmojisAndSequence(targetDraftKey: String) {
        viewModelScope.launch {
            val emojis = runCatching { repository.emojiUrls() }.getOrDefault(emptyMap())
            if (emojis.isNotEmpty()) {
                _topicCreator.update { if (it.open) it.copy(emojiUrls = emojis) else it }
            }
            if (!_drafts.value.loaded) {
                runCatching { repository.fetchDrafts(burst = true) }.getOrNull()?.let { list ->
                    _drafts.update { it.copy(items = list, loaded = true, loading = false, error = null) }
                    val matched = list.firstOrNull { it.draftKey == targetDraftKey }
                    if (matched != null) {
                        _topicCreator.update {
                            if (it.open && it.draftKey == targetDraftKey) it.copy(sequence = matched.sequence) else it
                        }
                    }
                }
            }
        }
    }

    fun loadTopicCreatorTags(categoryId: Int?, query: String = "") {
        pageJobs["tags:creator"]?.cancel()
        pageJobs["tags:creator"] = viewModelScope.launch {
            _topicCreator.update { it.copy(loadingTags = true) }
            val currentSelected = _topicCreator.value.tags
            val searchResult = repository.searchTags(
                categoryId = categoryId,
                query = query,
                selectedTags = currentSelected,
                limit = 8,
                filterForInput = true,
            )
            _topicCreator.update {
                if (it.open) it.copy(
                    availableTags = searchResult.results,
                    requiredTagGroupName = searchResult.requiredTagName,
                    requiredTagMinCount = searchResult.requiredTagMinCount,
                    loadingTags = false
                ) else it
            }
        }
    }

    private var tagSearchDebounceJob: Job? = null
    fun searchTopicCreatorTags(query: String) {
        tagSearchDebounceJob?.cancel()
        tagSearchDebounceJob = viewModelScope.launch {
            delay(300L) // 照搬 FluxDO 300ms 防抖
            val currentCat = _topicCreator.value.categoryId
            loadTopicCreatorTags(currentCat, query)
        }
    }

    fun updateTopicCreatorTitle(title: String) {
        _topicCreator.update { if (it.open) it.copy(title = title, error = null, notice = null) else it }
    }

    fun updateTopicCreatorRaw(raw: String) {
        _topicCreator.update { if (it.open) it.copy(raw = raw, error = null, notice = null) else it }
    }

    fun updateTopicCreatorCategory(categoryId: Int?) {
        _topicCreator.update { if (it.open) it.copy(categoryId = categoryId, error = null, notice = null) else it }
        loadTopicCreatorTags(categoryId)
    }

    fun updateTopicCreatorTags(tags: List<String>) {
        _topicCreator.update { if (it.open) it.copy(tags = tags.distinct(), error = null, notice = null) else it }
    }

    fun clearTopicCreatorNotice() {
        _topicCreator.update { it.copy(notice = null) }
    }

    fun closeTopicCreator() {
        val current = _topicCreator.value
        if (current.submitting || current.savingDraft) return
        _topicCreator.value = TopicCreatorUiState()
    }

    fun attachToTopicCreator(uri: Uri) {
        val current = _topicCreator.value
        if (!current.open || current.uploading || current.submitting || current.savingDraft) return
        pageJobs["upload:creator"] = viewModelScope.launch {
            _topicCreator.update { it.copy(uploading = true, error = null, notice = null) }
            try {
                val picked = withContext(Dispatchers.IO) { readPickedFile(uri) }
                val uploaded = repository.uploadFile(picked.name, picked.mimeType, picked.bytes)
                val isImg = uploaded.width > 0 || UploadLimits.isCompressibleImage(picked.name) ||
                    picked.mimeType.startsWith("image/")
                val media = AttachedMedia(
                    name = uploaded.fileName,
                    previewUrl = uri.toString(),
                    uploaded = uploaded,
                    isImage = isImg,
                )
                _topicCreator.update {
                    if (!it.open) it.copy(uploading = false) else {
                        it.copy(attachments = it.attachments + media, uploading = false)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _topicCreator.update { it.copy(uploading = false, error = error.message ?: "上传失败，请稍后重试") }
                handlePageFailure(error) {}
            }
        }
    }

    fun removeTopicCreatorAttachment(id: String) {
        _topicCreator.update { it.copy(attachments = it.attachments.filterNot { a -> a.id == id }) }
    }

    fun saveTopicDraft(closeAfterSave: Boolean = false) {
        val current = _topicCreator.value
        if (!current.open || !current.canSaveDraft) return
        val rawToSave = assembleRaw(current.raw, current.attachments)
        pageJobs["saveDraft"] = viewModelScope.launch {
            _topicCreator.update { it.copy(savingDraft = true, error = null, notice = null) }
            try {
                val newSeq = repository.saveDraft(
                    draftKey = current.draftKey,
                    sequence = current.sequence,
                    title = current.title.trim(),
                    raw = rawToSave,
                    categoryId = current.categoryId,
                    tags = current.tags,
                    action = current.action,
                    topicId = current.replyTopicId,
                    replyToPostNumber = current.replyToPostNumber,
                )
                if (closeAfterSave) {
                    _topicCreator.value = TopicCreatorUiState()
                    Toast.makeText(getApplication(), "草稿已保存", Toast.LENGTH_SHORT).show()
                } else {
                    _topicCreator.update {
                        val next = it.copy(
                            sequence = newSeq,
                            savingDraft = false,
                            notice = "草稿已保存",
                        )
                        next.copy(savedSnapshot = next.currentSnapshot)
                    }
                }
                loadDrafts(refresh = true, silent = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _topicCreator.update { it.copy(savingDraft = false, error = error.message ?: "保存草稿失败，请稍后重试") }
                handlePageFailure(error) {}
            }
        }
    }

    fun submitTopicCreator(onPublished: (Long, String, Int?) -> Unit) {
        val current = _topicCreator.value
        if (!current.open || !current.canSubmit) return
        if (!current.isReplyDraft && current.categoryId == null) {
            _topicCreator.update { it.copy(error = "请先选择话题分类") }
            return
        }
        val rawToSubmit = assembleRaw(current.raw, current.attachments)
        pageJobs["createTopic"] = viewModelScope.launch {
            _topicCreator.update { it.copy(submitting = true, error = null, notice = null) }
            try {
                if (current.isReplyDraft) {
                    val targetTopicId = requireNotNull(current.replyTopicId)
                    when (val result = repository.createPost(
                        topicId = targetTopicId,
                        raw = rawToSubmit,
                        replyToPostNumber = current.replyToPostNumber,
                        draftKey = current.draftKey,
                    )) {
                        is PostResult.Success -> {
                            applyCreatedPost(targetTopicId, result.post, forceTailWindow = current.replyToPostNumber == null)
                            _drafts.update { state -> state.copy(items = state.items.filterNot { it.draftKey == current.draftKey }) }
                            _topicCreator.value = TopicCreatorUiState()
                            onPublished(targetTopicId, current.title.ifBlank { "话题 #$targetTopicId" }, result.post.postNumber)
                        }
                        is PostResult.Enqueued -> {
                            _drafts.update { state -> state.copy(items = state.items.filterNot { it.draftKey == current.draftKey }) }
                            _topicCreator.value = TopicCreatorUiState()
                            Toast.makeText(getApplication(), "您的回复已提交，正等待管理员审核后显示", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    when (val result = repository.createTopic(
                        title = current.title,
                        raw = rawToSubmit,
                        categoryId = current.categoryId,
                        tags = current.tags,
                        draftKey = current.draftKey,
                    )) {
                        is PostResult.Success -> {
                            val newTopicId = result.post.topicId ?: 0L
                            _drafts.update { state -> state.copy(items = state.items.filterNot { it.draftKey == current.draftKey }) }
                            _topicCreator.value = TopicCreatorUiState()
                            refreshLatest()
                            if (newTopicId > 0L) {
                                onPublished(newTopicId, current.title.trim(), null)
                            } else {
                                Toast.makeText(getApplication(), "话题已发布", Toast.LENGTH_SHORT).show()
                            }
                        }
                        is PostResult.Enqueued -> {
                            _drafts.update { state -> state.copy(items = state.items.filterNot { it.draftKey == current.draftKey }) }
                            _topicCreator.value = TopicCreatorUiState()
                            Toast.makeText(getApplication(), "您的话题已提交，正等待管理员审核后显示", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _topicCreator.update { it.copy(submitting = false, error = error.message ?: "发布失败，请稍后重试") }
                handlePageFailure(error) {}
            }
        }
    }

    /** 列表长按话题：将话题首帖添加到书签。 */
    fun bookmarkTopicFromList(topicId: Long, onResult: (String) -> Unit) {
        if (_state.value.username == null) {
            onResult("请先登录账号")
            return
        }
        if (pageJobs["listBookmark:$topicId"]?.isActive == true) return
        val cachedFirstPostId = _details.value[topicId]?.posts?.firstOrNull { it.postNumber == 1 }?.id
        pageJobs["listBookmark:$topicId"] = viewModelScope.launch {
            try {
                val bookmarkId = repository.bookmarkTopic(topicId, cachedFirstPostId)
                _details.update { states ->
                    val detail = states[topicId] ?: return@update states
                    states + (topicId to detail.copy(
                        posts = detail.posts.map {
                            if (it.postNumber == 1) it.copy(bookmarked = true, bookmarkId = bookmarkId) else it
                        }
                    ))
                }
                onResult("已添加到书签")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onResult(error.message ?: "添加书签失败")
                handlePageFailure(error) {}
            }
        }
    }

    /** 列表长按话题：举报该话题（自动解析 1 楼 postId 提交举报）。 */
    fun submitTopicFlagFromList(
        topicId: Long,
        reasonId: Int,
        message: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        if (_state.value.username == null) {
            onError("请先登录账号")
            return
        }
        val reason = PostFlagReason.entries.firstOrNull { it.id == reasonId } ?: return
        if (reason.requiresMessage && message.isNullOrBlank()) {
            onError("请填写具体说明")
            return
        }
        if (pageJobs["listFlag:$topicId"]?.isActive == true) return
        val cachedFirstPostId = _details.value[topicId]?.posts?.firstOrNull { it.postNumber == 1 }?.id
        pageJobs["listFlag:$topicId"] = viewModelScope.launch {
            try {
                val postId = cachedFirstPostId ?: repository.fetchTopicFirstPostId(topicId)
                repository.flagPost(postId, reasonId, message)
                _details.update { states ->
                    val detail = states[topicId] ?: return@update states
                    states + (topicId to detail.copy(flaggedPosts = detail.flaggedPosts + postId))
                }
                onSuccess()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onError(error.message ?: "举报提交失败，请稍后再试")
                handlePageFailure(error) {}
            }
        }
    }

    /**
     * 新楼层必须同时登记进 consumedIds 和 stream:
     * 不进 consumedIds,`remaining` 会把它当成"未加载楼层"再拉一次;
     * 不进 stream,话题总楼层数和楼层进度就对不上。
     */
    private suspend fun applyCreatedPost(topicId: Long, post: Post, forceTailWindow: Boolean) {
        val snapshot = _details.value[topicId]
        val atTail = forceTailWindow || post.replyToPostNumber == null || !_treeView.value
        if (snapshot != null && atTail && snapshot.remaining.isNotEmpty()) {
            val tailTopic = runCatching {
                repository.fetchTopic(topicId, postNumber = post.postNumber, burst = true)
            }.getOrNull()
            if (tailTopic != null) {
                _details.update { states ->
                    val current = states[topicId] ?: return@update states
                    val firstPost = current.posts.firstOrNull { it.postNumber == 1 }
                    val tailPosts = (tailTopic.postStream.posts + post)
                        .distinctBy { it.id }
                        .sortedBy { it.postNumber }
                    val windowAnchor = tailPosts.firstOrNull { it.postNumber > 1 }?.id
                        ?: tailPosts.firstOrNull()?.id
                        ?: post.id
                    val mergedPosts = (listOfNotNull(firstPost) + tailPosts)
                        .distinctBy { it.id }
                        .sortedBy { it.postNumber }
                    val rawStream = tailTopic.postStream.stream
                    val fullStream = if (post.id in rawStream) rawStream else rawStream + post.id
                    states + (topicId to current.copy(
                        topic = tailTopic.copy(postStream = tailTopic.postStream.copy(stream = fullStream)),
                        posts = mergedPosts,
                        consumedIds = mergedPosts.map { it.id }.toSet(),
                        windowStartId = windowAnchor,
                        createdPostId = post.id,
                        error = null,
                    ))
                }
                return
            }
        }
        applyNewPost(topicId, post)
    }

    private fun applyNewPost(topicId: Long, post: Post) {
        _details.update { states ->
            val current = states[topicId] ?: return@update states
            val stream = current.topic?.postStream?.stream.orEmpty()
            val topic = current.topic?.let {
                if (post.id in stream) it
                else it.copy(postStream = it.postStream.copy(stream = stream + post.id))
            }
            states + (topicId to current.copy(
                topic = topic,
                posts = (current.posts + post).distinctBy { it.id }.sortedBy { it.postNumber }
                    // 父楼层的 reply_count 同步 +1,否则"更多回复"的计数判断会漏掉服务端其余回复。1 楼为主贴不挂楼中楼。
                    .map { if (it.postNumber == post.replyToPostNumber && it.postNumber > 1) it.copy(replyCount = it.replyCount + 1) else it },
                consumedIds = current.consumedIds + post.id,
                windowStartId = current.windowStartId ?: post.id,
                createdPostId = post.id,
            ))
        }
    }

    /** 界面已经滚到新楼层了,清掉标记,免得后面再触发一次。 */
    fun clearCreatedPost(topicId: Long) {
        _details.update { states ->
            states[topicId]?.takeIf { it.createdPostId != null }
                ?.let { states + (topicId to it.copy(createdPostId = null)) } ?: states
        }
    }

    private fun applyEditedPost(topicId: Long, post: Post) {
        _details.update { states ->
            val current = states[topicId] ?: return@update states
            states + (topicId to current.copy(
                // 编辑只改内容。响应里缺字段会被反序列化成默认值,整条替换会清空点赞/简评,
                // 更糟的是 reply_to_post_number 变 null 会把这条回复抬成根节点、打乱树形排列,
                // 所以除 cooked 之外的既有状态一律保留。
                posts = current.posts.map { existing ->
                    if (existing.id != post.id) existing
                    else post.copy(
                        postNumber = existing.postNumber,
                        replyToPostNumber = existing.replyToPostNumber,
                        replyCount = existing.replyCount,
                        boosts = existing.boosts, reactions = existing.reactions,
                        currentUserReaction = existing.currentUserReaction, canBoost = existing.canBoost,
                        acceptedAnswer = existing.acceptedAnswer, canAcceptAnswer = existing.canAcceptAnswer,
                        canUnacceptAnswer = existing.canUnacceptAnswer,
                        avatarTemplate = post.avatarTemplate ?: existing.avatarTemplate,
                        username = post.username.ifBlank { existing.username },
                        createdAt = post.createdAt ?: existing.createdAt,
                        yours = existing.yours,
                        canEdit = existing.canEdit,
                        canDelete = existing.canDelete,
                        bookmarked = existing.bookmarked,
                        bookmarkId = existing.bookmarkId,
                    )
                },
            ))
        }
    }

    /** 删除楼层。#1 楼会连带删除整个话题,由界面负责在确认框里说清楚。 */
    fun deletePost(topicId: Long, postId: Long) {
        val current = _details.value[topicId] ?: return
        if (current.loading || current.reactingPosts.isNotEmpty()) return
        if (current.posts.none { it.id == postId && it.canDelete }) return
        pageJobs["delete:$postId"] = viewModelScope.launch {
            _details.update { it + (topicId to current.copy(reactingPosts = setOf(postId),
                reactionErrors = current.reactionErrors - postId)) }
            try {
                repository.deletePost(postId)
                _details.update { states ->
                    val latest = states[topicId] ?: return@update states
                    val removed = latest.posts.firstOrNull { it.id == postId }
                    val topic = latest.topic?.let { topic ->
                        topic.copy(postStream = topic.postStream.copy(
                            stream = topic.postStream.stream.filterNot { it == postId }))
                    }
                    states + (topicId to latest.copy(
                        topic = topic,
                        posts = latest.posts.filterNot { it.id == postId }.map {
                            if (it.postNumber == removed?.replyToPostNumber && it.postNumber > 1) {
                                it.copy(replyCount = (it.replyCount - 1).coerceAtLeast(0))
                            } else it
                        },
                        consumedIds = latest.consumedIds - postId,
                        windowStartId = if (latest.windowStartId == postId) {
                            latest.posts.firstOrNull { it.id != postId }?.id
                        } else latest.windowStartId,
                    ))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _details.update { states -> states[topicId]?.let {
                    states + (topicId to it.copy(reactionErrors = it.reactionErrors + (postId to (error.message ?: "删除失败"))))
                } ?: states }
                handlePageFailure(error) { loadTopic(topicId, retry = true) }
            } finally {
                _details.update { states -> states[topicId]?.let { states + (topicId to it.copy(reactingPosts = emptySet())) } ?: states }
            }
        }
    }

    fun setNoticeMessage(topicId: Long, message: String) {
        _details.update { states ->
            val current = states[topicId] ?: return@update states
            states + (topicId to current.copy(noticeMessage = message))
        }
        viewModelScope.launch {
            delay(3500)
            _details.update { states ->
                val current = states[topicId] ?: return@update states
                if (current.noticeMessage == message) {
                    states + (topicId to current.copy(noticeMessage = null))
                } else states
            }
        }
    }

    fun toggleBookmark(topicId: Long, post: Post) {
        val snapshot = _details.value[topicId] ?: return
        val original = snapshot.posts.firstOrNull { it.id == post.id } ?: return
        if (_state.value.username == null || _composer.value.submitting) return
        if (original.bookmarked && (original.bookmarkId ?: 0L) <= 0L) {
            _details.update { states -> states[topicId]?.let {
                states + (topicId to it.copy(reactionErrors = it.reactionErrors +
                    (post.id to "缺少书签信息，请刷新楼层后再取消收藏")))
            } ?: states }
            return
        }
        val newBookmarked = !original.bookmarked
        _details.update { states ->
            val current = states[topicId] ?: return@update states
            states + (topicId to current.copy(
                posts = current.posts.map { if (it.id == post.id) it.copy(bookmarked = newBookmarked) else it },
                reactionErrors = current.reactionErrors - post.id,
            ))
        }
        pageJobs["bookmark:${post.id}"] = viewModelScope.launch {
            var confirmed = false
            try {
                val bookmarkId = if (newBookmarked) {
                    repository.bookmarkPost(post.id)
                } else {
                    repository.unbookmarkPost(requireNotNull(original.bookmarkId))
                    null
                }
                _details.update { states -> states[topicId]?.let { current ->
                    states + (topicId to current.copy(posts = current.posts.map {
                        if (it.id == post.id) it.copy(bookmarked = newBookmarked, bookmarkId = bookmarkId) else it
                    }))
                } ?: states }
                confirmed = true
                setNoticeMessage(topicId, if (newBookmarked) "已添加到书签" else "已从书签移除")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _details.update { states -> states[topicId]?.let {
                    states + (topicId to it.copy(reactionErrors = it.reactionErrors +
                        (post.id to (error.message ?: "书签操作失败"))))
                } ?: states }
                handlePageFailure(error) {}
            } finally {
                if (!confirmed) {
                    _details.update { states ->
                        val current = states[topicId] ?: return@update states
                        states + (topicId to current.copy(
                            posts = current.posts.map {
                                if (it.id == post.id) it.copy(bookmarked = original.bookmarked, bookmarkId = original.bookmarkId) else it
                            },
                        ))
                    }
                }
            }
        }
    }

    fun openFlagDialog(topicId: Long, post: Post) {
        val username = _state.value.username ?: return
        _details.update { states ->
            val current = states[topicId] ?: return@update states
            val latest = current.posts.firstOrNull { it.id == post.id } ?: return@update states
            if (latest.yours || latest.username.equals(username, true) || post.id in current.flaggedPosts ||
                current.reactingPosts.isNotEmpty()) return@update states
            states + (topicId to current.copy(flagDialogPost = latest, flagError = null))
        }
    }

    fun closeFlagDialog(topicId: Long) {
        _details.update { states ->
            val current = states[topicId] ?: return@update states
            if (current.flagDialogPost?.id in current.reactingPosts) return@update states
            states + (topicId to current.copy(flagDialogPost = null, flagError = null))
        }
    }

    fun submitFlag(topicId: Long, postId: Long, reasonId: Int, message: String? = null) {
        val current = _details.value[topicId] ?: return
        val post = current.posts.firstOrNull { it.id == postId } ?: return
        val username = _state.value.username ?: return
        if (current.flagDialogPost?.id != postId || current.reactingPosts.isNotEmpty() ||
            post.yours || post.username.equals(username, true) || postId in current.flaggedPosts) return
        val reason = PostFlagReason.entries.firstOrNull { it.id == reasonId } ?: return
        if (reason.requiresMessage && message.isNullOrBlank()) return
        _details.update { states -> states[topicId]?.let {
            states + (topicId to it.copy(reactingPosts = it.reactingPosts + postId, flagError = null))
        } ?: states }
        pageJobs["flag:$postId"] = viewModelScope.launch {
            try {
                repository.flagPost(postId, reasonId, message)
                _details.update { states -> states[topicId]?.let {
                    states + (topicId to it.copy(flagDialogPost = null, flaggedPosts = it.flaggedPosts + postId))
                } ?: states }
                setNoticeMessage(topicId, "举报已提交，感谢您的反馈")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _details.update { states -> states[topicId]?.let {
                    states + (topicId to it.copy(flagError = error.message ?: "举报提交失败，请稍后再试"))
                } ?: states }
                handlePageFailure(error) {}
            } finally {
                _details.update { states -> states[topicId]?.let {
                    states + (topicId to it.copy(reactingPosts = it.reactingPosts - postId))
                } ?: states }
            }
        }
    }

    fun setTopicNotificationLevel(topicId: Long, level: TopicNotificationLevel) {
        if (_state.value.username == null || pageJobs["notification:$topicId"]?.isActive == true) return
        val detailLevel = _details.value[topicId]?.topic?.details?.notificationLevel
        val snapshot = NotificationLevelSnapshot(detailLevel, _state.value.homeFeed, _state.value.latest, _feedStates.value,
            _categoryTopics.value, _userTopicStates.value, _search.value.results)
        val items = _feedStates.value.values.flatMap { it.items } + _state.value.latest.items +
            _categoryTopics.value.values.flatMap { it.items } + _userTopicStates.value.values.flatMap { it.items } +
            _search.value.results.items
        val previousLevel = store.getTopicNotificationLevel(topicId)?.let(TopicNotificationLevel::fromCode)
            ?: items.firstOrNull { it.topic.id == topicId }?.notificationLevel
        if (previousLevel == level && (detailLevel == null || detailLevel == level.code) &&
            items.filter { it.topic.id == topicId }.all { it.notificationLevel == level }) return
        updateNotificationLevel(topicId, level, changing = true)
        pageJobs["notification:$topicId"] = viewModelScope.launch {
            var confirmed = false
            try {
                repository.setTopicNotificationLevel(topicId, level.code)
                confirmed = true
                setNoticeMessage(topicId, "通知级别已设为「${level.title}」")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Toast.makeText(getApplication(), error.message ?: "通知级别设置失败，已恢复原状态", Toast.LENGTH_LONG).show()
                handlePageFailure(error) {}
            } finally {
                if (confirmed) {
                    updateNotificationLevel(topicId, level, changing = false)
                    if (level == TopicNotificationLevel.Muted) removeMutedTopicFromLatest(topicId)
                } else {
                    restoreNotificationLevel(topicId, snapshot)
                }
            }
        }
    }

    private data class NotificationLevelSnapshot(
        val detailLevel: Int?,
        val homeFeed: HomeFeedType,
        val latest: TopicListUiState,
        val feeds: Map<HomeFeedType, TopicListUiState>,
        val categories: Map<Int, TopicListUiState>,
        val userTopics: Map<String, TopicListUiState>,
        val search: TopicListUiState,
    )

    private fun restoreNotificationLevel(topicId: Long, snapshot: NotificationLevelSnapshot) {
        fun restoreList(current: TopicListUiState, previous: TopicListUiState?): TopicListUiState {
            fun restoreItems(items: List<TopicListItem>, originals: List<TopicListItem>): List<TopicListItem> {
                val original = originals.firstOrNull { it.topic.id == topicId }
                return items.map { item ->
                    if (item.topic.id != topicId || !item.notificationLevelUpdating) item
                    else item.copy(
                        topic = item.topic.copy(notificationLevel = original?.topic?.notificationLevel),
                        notificationLevel = original?.notificationLevel,
                        notificationLevelUpdating = false,
                    )
                }
            }
            return current.copy(
                items = restoreItems(current.items, previous?.items.orEmpty()),
                pendingPage = current.pendingPage?.let { page ->
                    page.copy(items = restoreItems(page.items, previous?.pendingPage?.items.orEmpty()))
                },
            )
        }
        _details.update { states -> states[topicId]?.let { current ->
            states + (topicId to current.copy(
                topic = current.topic?.let { it.copy(details = it.details.copy(notificationLevel = snapshot.detailLevel ?: 1)) },
                notificationLevelUpdating = false,
            ))
        } ?: states }
        _feedStates.update { feeds -> feeds.mapValues { restoreList(it.value, snapshot.feeds[it.key]) } }
        _state.update { current -> current.copy(latest = restoreList(current.latest,
            if (current.homeFeed == snapshot.homeFeed) snapshot.latest else snapshot.feeds[current.homeFeed])) }
        _categoryTopics.update { categories -> categories.mapValues { restoreList(it.value, snapshot.categories[it.key]) } }
        _userTopicStates.update { users -> users.mapValues { restoreList(it.value, snapshot.userTopics[it.key]) } }
        _search.update { it.copy(results = restoreList(it.results, snapshot.search)) }
    }

    private fun updateNotificationLevel(
        topicId: Long,
        level: TopicNotificationLevel?,
        changing: Boolean,
        detailLevel: Int? = level?.code,
    ) {
        fun updateList(list: TopicListUiState): TopicListUiState {
            fun updateItems(items: List<TopicListItem>) = items.map { item ->
                if (item.topic.id == topicId) item.copy(
                    topic = item.topic.copy(notificationLevel = level?.code),
                    notificationLevel = level,
                    notificationLevelUpdating = changing,
                ) else item
            }
            return list.copy(items = updateItems(list.items),
                pendingPage = list.pendingPage?.let { it.copy(items = updateItems(it.items)) })
        }
        _details.update { states ->
            val current = states[topicId] ?: return@update states
            val currentTopic = current.topic ?: return@update states
            val updatedTopic = currentTopic.copy(
                details = currentTopic.details.copy(notificationLevel = detailLevel ?: 1)
            )
            states + (topicId to current.copy(topic = updatedTopic, notificationLevelUpdating = changing))
        }
        _feedStates.update { feeds -> feeds.mapValues { updateList(it.value) } }
        _state.update { it.copy(latest = updateList(it.latest)) }
        _categoryTopics.update { categories -> categories.mapValues { updateList(it.value) } }
        _userTopicStates.update { users -> users.mapValues { updateList(it.value) } }
        _search.update { it.copy(results = updateList(it.results)) }
    }

    private fun removeMutedTopicFromLatest(topicId: Long) {
        fun removeTopic(list: TopicListUiState) = list.copy(
            items = list.items.filterNot { it.topic.id == topicId },
            pendingPage = list.pendingPage?.let { page -> page.copy(items = page.items.filterNot { it.topic.id == topicId }) },
            incomingTopicIds = list.incomingTopicIds - topicId,
            highlightedTopicIds = list.highlightedTopicIds - topicId,
        )
        _feedStates.update { feeds -> feeds[HomeFeedType.Latest]?.let {
            feeds + (HomeFeedType.Latest to removeTopic(it))
        } ?: feeds }
        if (_state.value.homeFeed == HomeFeedType.Latest) _state.update { it.copy(latest = removeTopic(it.latest)) }
    }

    /** 回到 1 楼。窗口化加载下 1 楼可能还没取回来,这时从头重拉整个话题。 */
    fun reloadFromStart(topicId: Long) {
        val current = _details.value[topicId] ?: return
        if (current.loading) return
        pageJobs["topic:$topicId"]?.cancel()
        _details.update { it - topicId }
        loadTopic(topicId)
    }

    fun loadReplies(topicId: Long, postId: Long) {
        val current = _details.value[topicId] ?: return
        if (postId in current.loadingReplies || current.reactingPosts.isNotEmpty()) return
        val tree = threadRows(current.posts)
        val matchedNode = tree.firstOrNull { it.post.id == postId }
        val rootPostId = matchedNode?.rootPostId ?: postId
        val rootNode = tree.firstOrNull { it.post.id == rootPostId }
        val pendingSubtreeIds = rootNode?.unloadedCandidateIds?.filterNot { it in current.completedReplies }.orEmpty()
        if (postId in current.completedReplies && pendingSubtreeIds.isEmpty()) return

        val activeLoadingIds = mutableSetOf(postId, rootPostId)
        pageJobs["replies:$topicId:$rootPostId"] = viewModelScope.launch {
            _details.update { states ->
                states[topicId]?.let { states + (topicId to it.copy(loadingReplies = it.loadingReplies + activeLoadingIds, error = null)) } ?: states
            }
            try {
                var iterations = 0
                val maxIterations = 30
                while (isActive && iterations < maxIterations) {
                    iterations++
                    val snapshot = _details.value[topicId] ?: break
                    val currentTree = threadRows(snapshot.posts)
                    val currentRootRow = currentTree.firstOrNull { it.post.id == rootPostId }
                    val nextCandidateId = if (postId !in snapshot.completedReplies) {
                        postId
                    } else {
                        currentRootRow?.unloadedCandidateIds?.firstOrNull { it !in snapshot.completedReplies }
                    } ?: break

                    if (nextCandidateId !in activeLoadingIds) {
                        activeLoadingIds.add(nextCandidateId)
                        _details.update { states ->
                            states[topicId]?.let { states + (topicId to it.copy(loadingReplies = it.loadingReplies + nextCandidateId)) } ?: states
                        }
                    }

                    val after = snapshot.replyCursors[nextCandidateId] ?: 1
                    val replies = repository.fetchReplies(nextCandidateId, after)
                    val cursor = replies.maxOfOrNull { it.postNumber }

                    _details.update { states ->
                        val latest = states[topicId] ?: return@update states
                        val parent = latest.posts.firstOrNull { it.id == nextCandidateId }
                        val parentNumber = parent?.postNumber
                        val normalizedReplies = replies.map { reply ->
                            if (parentNumber != null && reply.postNumber > parentNumber) {
                                reply.copy(replyToPostNumber = parentNumber)
                            } else {
                                reply
                            }
                        }
                        val replyById = normalizedReplies.associateBy { it.id }
                        val patchedExisting = latest.posts.map { existing ->
                            val fetched = replyById[existing.id] ?: return@map existing
                            val targetParent = fetched.effectiveReplyToPostNumber() ?: parentNumber
                            val existingParent = existing.effectiveReplyToPostNumber()
                            val hasLoadedParent = existingParent != null && latest.posts.any { it.postNumber == existingParent && it.postNumber < existing.postNumber }
                            if (!hasLoadedParent && targetParent != null && existing.postNumber > targetParent) {
                                existing.copy(replyToPostNumber = targetParent)
                            } else {
                                existing
                            }
                        }
                        val existingIds = patchedExisting.map { it.id }.toSet()
                        val mergedPosts = (patchedExisting + normalizedReplies.filterNot { it.id in existingIds }).sortedBy { it.postNumber }
                        val childCount = if (parentNumber != null) {
                            mergedPosts.count { it.postNumber > parentNumber && it.effectiveReplyToPostNumber() == parentNumber }
                        } else 0
                        val progressed = cursor != null && cursor > after
                        val isCandidateDone = replies.isEmpty() || replies.size < 20 || !progressed || (parent != null && childCount >= parent.replyCount)
                        states + (topicId to latest.copy(
                            posts = mergedPosts,
                            consumedIds = latest.consumedIds + replies.map { post -> post.id },
                            replyCursors = if (cursor == null) latest.replyCursors else latest.replyCursors + (nextCandidateId to cursor),
                            completedReplies = if (isCandidateDone) latest.completedReplies + nextCandidateId else latest.completedReplies,
                        ))
                    }

                    if (replies.isEmpty()) {
                        _details.update { states ->
                            states[topicId]?.let { states + (topicId to it.copy(completedReplies = it.completedReplies + nextCandidateId)) } ?: states
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _details.update { states -> states[topicId]?.let { states + (topicId to it.copy(error = error.message ?: "回复加载失败")) } ?: states }
                handlePageFailure(error) { loadReplies(topicId, postId) }
            } finally {
                _details.update { states -> states[topicId]?.let { states + (topicId to it.copy(loadingReplies = it.loadingReplies - activeLoadingIds)) } ?: states }
            }
        }
    }

    fun saveCategoryOrder(ids: List<Long>) {
        store.saveCategoryOrder(ids)
        _categories.update { current ->
            current.copy(order = store.loadCategoryOrder())
        }
    }

    fun resetCategoryOrder() {
        store.resetCategoryOrder()
        _categories.update { it.copy(order = emptyList()) }
    }

    fun saveCustomSection(section: CustomSection) {
        val oldSection = _customSection.value
        val oldServerLinkIds = oldSection?.links?.mapNotNull { it.serverId }.orEmpty()
        store.saveCustomSection(section)
        _customSection.value = section

        // 异步向 Discourse 服务端同步（打通 PC 端）
        viewModelScope.launch {
            try {
                val updated = repository.saveCustomSidebarSection(section, oldServerLinkIds)
                store.saveCustomSection(updated)
                _customSection.value = updated
            } catch (e: Exception) {
                diagnostics.log("同步自定义版块至服务端失败: ${e.message}")
            }
        }
    }

    fun deleteCustomSection() {
        val oldSection = _customSection.value
        val serverId = oldSection?.serverId
        store.saveCustomSection(null)
        _customSection.value = null

        // 异步从 Discourse 服务端删除（打通 PC 端）
        if (serverId != null && serverId > 0L) {
            viewModelScope.launch {
                try {
                    repository.deleteCustomSidebarSection(serverId)
                } catch (e: Exception) {
                    diagnostics.log("服务端删除自定义版块失败: ${e.message}")
                }
            }
        }
    }

    /**
     * 与服务端同步自定义版块（与 PC 端打通）：
     * 若 PC 端有配置自定义版块，自动拉取下来；
     * 若 PC 端已删除，且本地版块之前绑定过 serverId，则同步清理本地。
     */
    fun syncCustomSectionFromServer() {
        viewModelScope.launch {
            try {
                val remoteSection = repository.fetchCustomSidebarSection()
                if (remoteSection != null) {
                    store.saveCustomSection(remoteSection)
                    _customSection.value = remoteSection
                } else {
                    val current = _customSection.value
                    if (current?.serverId != null && current.serverId > 0L) {
                        store.saveCustomSection(null)
                        _customSection.value = null
                    }
                }
            } catch (e: Exception) {
                diagnostics.log("同步远端自定义版块失败: ${e.message}")
            }
        }
    }

    fun loadCategories(retry: Boolean = false) {
        syncCustomSectionFromServer()
        val current = _categories.value
        if (current.loading || (current.loaded && !retry)) return
        pageJobs["categories"] = viewModelScope.launch {
            _categories.value = current.copy(loading = true, error = null)
            try {
                _categories.value = CategoriesUiState(items = repository.fetchCategories(), loaded = true,
                    order = store.loadCategoryOrder())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _categories.value = current.copy(error = error.message ?: "加载失败")
                handlePageFailure(error) { loadCategories(retry = true) }
            } finally {
                _categories.update { it.copy(loading = false) }
            }
        }
    }

    fun probeIncomingTopics(feed: HomeFeedType, categoryId: Int? = null) {
        if (!store.isAutoRefreshIncomingEnabled()) return
        viewModelScope.launch {
            try {
                if (categoryId != null) {
                    val currentCategoryState = _categoryTopics.value[categoryId] ?: return@launch
                    if (currentCategoryState.items.isEmpty()) return@launch
                    val currentIds = currentCategoryState.items.map { it.topic.id }.toSet()
                    val category = _categories.value.items.firstOrNull { it.id == categoryId }
                    val slug = category?.slug ?: "c"
                    val sortOrder = _topicSortOrder.value.apiValue
                    val sortAscending = _topicSortAscending.value.takeIf { sortOrder != null }
                    val freshPage = repository.fetchCategoryTopics(slug, categoryId, 0, present = false, order = sortOrder, ascending = sortAscending)
                    // 旧列表首个非置顶话题在新鲜列表中的位置；排在它之前的旧帖视为被新回复顶上来的
                    val firstOldNonPinnedId = currentCategoryState.items.firstOrNull { !it.topic.pinned }?.topic?.id
                    val firstOldIdx = if (firstOldNonPinnedId != null) freshPage.items.indexOfFirst { it.topic.id == firstOldNonPinnedId } else -1
                    val incoming = freshPage.items.filterIndexed { idx, item ->
                        item.topic.id !in currentIds || (firstOldIdx > 0 && idx < firstOldIdx && item.topic.id in currentIds)
                    }
                    if (incoming.isNotEmpty()) {
                        val incomingIds = incoming.map { it.topic.id }.toSet()
                        _categoryTopics.update { current ->
                            val existing = current[categoryId] ?: return@update current
                            current + (categoryId to existing.copy(
                                pendingPage = freshPage,
                                incomingTopicIds = incomingIds,
                            ))
                        }
                    }
                } else {
                    val currentFeedState = _feedStates.value[feed] ?: return@launch
                    if (currentFeedState.items.isEmpty()) return@launch
                    val currentIds = currentFeedState.items.map { it.topic.id }.toSet()
                    val sortOrder = _topicSortOrder.value.apiValue
                    val sortAscending = _topicSortAscending.value.takeIf { sortOrder != null }
                    val freshPage = repository.fetchFeed(feed, 0, awaitCategories = false, present = false, order = sortOrder, ascending = sortAscending)
                    // 旧列表首个非置顶话题在新鲜列表中的位置；排在它之前的旧帖视为被新回复顶上来的
                    val firstOldNonPinnedId = currentFeedState.items.firstOrNull { !it.topic.pinned }?.topic?.id
                    val firstOldIdx = if (firstOldNonPinnedId != null) freshPage.items.indexOfFirst { it.topic.id == firstOldNonPinnedId } else -1
                    val incoming = freshPage.items.filterIndexed { idx, item ->
                        item.topic.id !in currentIds || (firstOldIdx > 0 && idx < firstOldIdx && item.topic.id in currentIds)
                    }
                    if (incoming.isNotEmpty()) {
                        val incomingIds = incoming.map { it.topic.id }.toSet()
                        _feedStates.update { current ->
                            val existing = current[feed] ?: return@update current
                            current + (feed to existing.copy(
                                pendingPage = freshPage,
                                incomingTopicIds = incomingIds,
                            ))
                        }
                        if (_state.value.homeFeed == feed) {
                            _state.update { currentUi ->
                                currentUi.copy(
                                    latest = currentUi.latest.copy(
                                        pendingPage = freshPage,
                                        incomingTopicIds = incomingIds,
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (ignored: Exception) {
                // 静默忽略所有探测错误与 CF 挑战，绝不弹窗
            }
        }
    }

    fun applyIncomingTopics(feed: HomeFeedType, categoryId: Int? = null) {
        if (categoryId != null) {
            val current = _categoryTopics.value[categoryId] ?: return
            val pending = current.pendingPage ?: return
            val newItems = (pending.items + current.items).distinctBy { it.topic.id }
            val refreshedIds = pending.items.map { it.topic.id }.toSet()
            val highlightJobKey = "incoming-highlight:category:$categoryId"
            pageJobs[highlightJobKey]?.cancel()
            _categoryTopics.update { map ->
                map + (categoryId to current.copy(
                    items = newItems,
                    pendingPage = null,
                    incomingTopicIds = emptySet(),
                    highlightedTopicIds = refreshedIds,
                ))
            }
            pageJobs[highlightJobKey] = viewModelScope.launch {
                delay(2500)
                _categoryTopics.update { map ->
                    val c = map[categoryId] ?: return@update map
                    map + (categoryId to c.copy(highlightedTopicIds = emptySet()))
                }
            }
        } else {
            val current = _feedStates.value[feed] ?: _state.value.latest
            val pending = current.pendingPage ?: return
            val newItems = (pending.items + current.items).distinctBy { it.topic.id }
            // 高亮表示本轮刷新覆盖的整批话题，不能复用仅用于提示计数的“新动态”子集。
            val refreshedIds = pending.items.map { it.topic.id }.toSet()
            val highlightJobKey = "incoming-highlight:feed:${feed.name}"
            pageJobs[highlightJobKey]?.cancel()
            val updatedState = current.copy(
                items = newItems,
                pendingPage = null,
                incomingTopicIds = emptySet(),
                highlightedTopicIds = refreshedIds,
            )
            _feedStates.update { it + (feed to updatedState) }
            if (_state.value.homeFeed == feed) {
                _state.update { it.copy(latest = updatedState) }
            }
            pageJobs[highlightJobKey] = viewModelScope.launch {
                delay(2500)
                _feedStates.update { currentMap ->
                    val f = currentMap[feed] ?: return@update currentMap
                    currentMap + (feed to f.copy(highlightedTopicIds = emptySet()))
                }
                if (_state.value.homeFeed == feed) {
                    _state.update { currentUi ->
                        currentUi.copy(latest = currentUi.latest.copy(highlightedTopicIds = emptySet()))
                    }
                }
            }
        }
    }

    fun setTopicSort(order: TopicSortOrder, currentCategoryId: Int? = null, currentCategorySlug: String? = null) {
        val currentOrder = _topicSortOrder.value
        val currentAscending = _topicSortAscending.value
        val (newOrder, newAscending) = if (order != TopicSortOrder.Default && order == currentOrder) {
            order to !currentAscending
        } else {
            order to false
        }
        if (newOrder == currentOrder && newAscending == currentAscending) return
        _topicSortOrder.value = newOrder
        _topicSortAscending.value = newAscending
        store.setTopicSortOrder(newOrder)
        store.setTopicSortAscending(newAscending)

        pageJobs.filter { it.key.startsWith("category:") || it.key.startsWith("feed:") || it.key.startsWith("bookmark:") || it.key.startsWith("created:") }.values.forEach { it.cancel() }
        _feedStates.value = emptyMap()
        _categoryTopics.value = emptyMap()
        _userTopicStates.value = emptyMap()
        if (currentCategoryId != null && !currentCategorySlug.isNullOrBlank()) {
            loadCategory(currentCategoryId, currentCategorySlug, refresh = true)
        }
        refreshLatest()
    }

    fun setFloatingBallEnabled(enabled: Boolean) {
        _floatingBallEnabled.value = enabled
        store.setFloatingBallEnabled(enabled)
    }

    fun setFloatingBallActions(actions: List<FloatingAction>) {
        val valid = actions.distinct().take(5)
        _floatingBallActions.value = valid
        store.setFloatingBallActions(valid)
    }

    fun updateFloatingBallPosition(xRatio: Float, yRatio: Float) {
        _floatingBallXRatio.value = xRatio
        _floatingBallYRatio.value = yRatio
        _floatingBallOnRight.value = xRatio >= 0.5f
        store.setFloatingBallXRatio(xRatio)
        store.setFloatingBallYRatio(yRatio)
        store.setFloatingBallOnRight(xRatio >= 0.5f)
    }

    fun updateFloatingBallPosition(yRatio: Float, onRight: Boolean) {
        _floatingBallYRatio.value = yRatio
        _floatingBallOnRight.value = onRight
        val xRatio = if (onRight) 0.95f else 0.05f
        _floatingBallXRatio.value = xRatio
        store.setFloatingBallXRatio(xRatio)
        store.setFloatingBallYRatio(yRatio)
        store.setFloatingBallOnRight(onRight)
    }

    fun loadCategory(id: Int, slug: String, refresh: Boolean = false, more: Boolean = false) {
        val current = _categoryTopics.value[id]
        if (pageJobs["category:$id"]?.isActive == true) return
        if (!refresh && !more && current != null) return
        val previous = current ?: TopicListUiState()
        if (more && !previous.hasMore) return
        val page = if (more) previous.page + 1 else 0
        pageJobs["category:$id"] = viewModelScope.launch {
            _categoryTopics.update { it + (id to previous.copy(
                refreshing = refresh, loadingMore = more, initialLoading = previous.items.isEmpty(), error = null,
            )) }
            try {
                val sortOrder = _topicSortOrder.value.apiValue
                val sortAscending = _topicSortAscending.value.takeIf { sortOrder != null }
                val result = repository.fetchCategoryTopics(slug, id, page, order = sortOrder, ascending = sortAscending)
                val items = if (more) (previous.items + result.items).distinctBy { it.topic.id } else result.items
                _categoryTopics.update { it + (id to TopicListUiState(
                    items = items,
                    page = page,
                    hasMore = result.hasMore && (!more || items.size > previous.items.size),
                    initialLoading = false,
                    pendingPage = if (page == 0) null else previous.pendingPage,
                    incomingTopicIds = if (page == 0) emptySet() else previous.incomingTopicIds,
                )) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _categoryTopics.update { it + (id to previous.copy(initialLoading = false, error = error.message ?: "加载失败")) }
                handlePageFailure(error) { loadCategory(id, slug, refresh = !more, more = more) }
            } finally {
                _categoryTopics.update { states -> states[id]?.let {
                    states + (id to it.copy(initialLoading = false, refreshing = false, loadingMore = false))
                } ?: states }
            }
        }
    }

    fun loadUserTopics(username: String, isBookmark: Boolean, refresh: Boolean = false, more: Boolean = false) {
        val key = "${if (isBookmark) "bookmark" else "created"}:$username"
        val current = _userTopicStates.value[key]
        if (pageJobs[key]?.isActive == true) return
        if (!refresh && !more && current != null) return
        val previous = current ?: TopicListUiState()
        if (more && !previous.hasMore) return
        val page = if (more) previous.page + 1 else 0
        pageJobs[key] = viewModelScope.launch {
            _userTopicStates.update { it + (key to previous.copy(
                refreshing = refresh, loadingMore = more, initialLoading = previous.items.isEmpty(), error = null,
            )) }
            try {
                val result = if (isBookmark) repository.fetchUserBookmarks(username, page) else repository.fetchUserCreatedTopics(username, page)
                val items = if (more) (previous.items + result.items).distinctBy { it.topic.id } else result.items
                _userTopicStates.update { it + (key to TopicListUiState(items, page,
                    result.hasMore && (!more || items.size > previous.items.size), initialLoading = false)) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _userTopicStates.update { it + (key to previous.copy(initialLoading = false, error = error.message ?: "加载失败")) }
                handlePageFailure(error) { loadUserTopics(username, isBookmark, refresh = !more, more = more) }
            } finally {
                _userTopicStates.update { states -> states[key]?.let {
                    states + (key to it.copy(initialLoading = false, refreshing = false, loadingMore = false))
                } ?: states }
            }
        }
    }

    fun updateSearch(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        _search.value = SearchUiState(query, TopicListUiState(initialLoading = trimmed.isNotEmpty(), hasMore = false), order = _search.value.order)
        if (trimmed.isEmpty()) return
        searchJob = viewModelScope.launch {
            delay(500)
            performSearch(trimmed, 1)
        }
    }

    fun setSearchOrder(order: SearchOrder) {
        if (_search.value.order == order) return
        _search.update { it.copy(order = order) }
        updateSearch(_search.value.query)
    }

    fun loadMoreSearch() {
        val current = _search.value
        if (searchJob?.isActive == true || !current.results.hasMore) return
        searchJob = viewModelScope.launch { performSearch(current.query.trim(), current.results.page + 1) }
    }

    private suspend fun performSearch(query: String, page: Int) {
        val previous = _search.value
        _search.update { it.copy(results = it.results.copy(initialLoading = page == 1, loadingMore = page > 1, error = null)) }
        try {
            val result = repository.search(query, page, previous.order)
            val items = if (page == 1) result.items else (previous.results.items + result.items).distinctBy { it.topic.id }
            _search.update { it.copy(
                results = TopicListUiState(items, page, result.hasMore && (page == 1 || items.size > previous.results.items.size), initialLoading = false),
                excerpts = if (page == 1) result.excerpts else previous.excerpts + result.excerpts,
            ) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _search.update { it.copy(results = it.results.copy(initialLoading = false, loadingMore = false, error = error.message ?: "搜索失败")) }
            handlePageFailure(error) { updateSearch(_search.value.query) }
        }
    }

    private suspend fun handlePageFailure(error: Exception, retry: () -> Unit) {
        diagnostics.log("浏览页面加载失败: ${error.message}")
        if (error is NeedsInteractiveVerification) {
            resumeAfterVerification = retry
            enterWebScreen(Screen.Verify, error.message.orEmpty())
        }
    }

    init {
        val app = getApplication<Application>()
        NotificationHelper.ensureNotificationChannels(app)
        if (store.isBackgroundNotificationEnabled()) {
            NotificationWorker.enqueue(app)
        }
        syncKeepAliveService()
        boot()
        restartAutoProbeLoop()
        checkForUpdates(manual = false)
    }

    fun testPushNotification() {
        val app = getApplication<Application>()
        NotificationHelper.showNotification(
            context = app,
            notificationId = 999999,
            title = "LINUX DO 通知测试",
            content = "这是一条本地测试通知，您的通知推送功能工作正常！",
            badgeCount = _state.value.unreadCount.takeIf { it > 0 },
        )
    }

    fun boot() {
        if (repository.cachedCategoryList() == null) {
            container.preferences.getString("cached_categories_json", null)?.let { rawJson ->
                repository.seedCategoriesFromJson(rawJson)
                repository.cachedCategoryList()?.let { list ->
                    _categories.value = CategoriesUiState(items = list, loaded = true, order = store.loadCategoryOrder())
                    backfillCategories(repository.cachedCategoryMap())
                }
            }
        }
        bootJob?.cancel()
        bootJob = viewModelScope.launch {
            verifying = false
            _state.update { it.copy(screen = Screen.Boot, latest = it.latest.copy(error = null)) }
            store.restoreMissingToWebView()
            refreshCookieInfo()
            if (!store.hasSessionCookie()) {
                enterWebScreen(Screen.Login)
                return@launch
            }
            try {
                val cachedUser = container.preferences.getString("cached_username", null)?.takeIf { it.isNotBlank() }
                if (cachedUser != null) {
                    diagnostics.log("命中本地会话缓存($cachedUser),直拉首屏列表并后台复核")
                    _state.update { it.copy(username = cachedUser, screen = Screen.List) }
                    refreshUnread(force = true)
                    resumeAfterVerification?.also { resumeAfterVerification = null }?.invoke()
                    loadLatest(page = 0, pullRefresh = false)
                    reconcileSessionAndCategories(cachedUser)
                    return@launch
                }
                val username = repository.fetchCurrentUser()
                if (username == null) {
                    diagnostics.log("会话 Cookie 存在但服务端判定未登录,转入登录")
                    // 服务端都说无效了就别留着它:同步是合并式的,不主动清掉的话
                    // 每次启动都会拿这个死 token 白跑一次校验。
                    store.clearSessionCookie()
                    enterWebScreen(Screen.Login)
                } else {
                    diagnostics.log("登录态有效: $username")
                    container.preferences.edit { putString("cached_username", username) }
                    _state.update { it.copy(username = username, screen = Screen.List) }
                    refreshUnread(force = true)
                    resumeAfterVerification?.also { resumeAfterVerification = null }?.invoke()
                    loadLatest(page = 0, pullRefresh = false)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: NeedsInteractiveVerification) {
                enterWebScreen(Screen.Verify, error.message.orEmpty())
            } catch (error: Exception) {
                diagnostics.log("启动检查失败: ${error.message}")
                _state.update {
                    it.copy(
                        screen = Screen.List,
                        latest = it.latest.copy(initialLoading = false, error = error.message),
                    )
                }
            }
        }
    }

    /**
     * 首屏列表上屏后，后台静默探测分类树更新并复核会话有效性（Stale-While-Revalidate）。
     * 既保证冷启动首屏 0 阻塞秒开，又确保服务端分类变动或会话失效能够及时同步。
     */
    private fun reconcileSessionAndCategories(cachedUser: String) {
        syncCategoriesInBackground()
        viewModelScope.launch {
            try {
                val realUser = repository.fetchCurrentUser()
                when {
                    realUser == null -> {
                        diagnostics.log("后台复核发现会话已失效,清除缓存并转入登录")
                        container.preferences.edit { remove("cached_username") }
                        store.clearSessionCookie()
                        enterWebScreen(Screen.Login)
                    }
                    realUser != cachedUser -> {
                        diagnostics.log("后台复核用户名更新: $cachedUser -> $realUser")
                        container.preferences.edit { putString("cached_username", realUser) }
                        _state.update { it.copy(username = realUser) }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: NeedsInteractiveVerification) {
                enterWebScreen(Screen.Verify, error.message.orEmpty())
            } catch (error: Exception) {
                diagnostics.log("后台会话复核跳过: ${error.javaClass.simpleName}")
            }
        }
    }

    private fun backfillCategories(known: Map<Int, Category>) {
        if (known.isEmpty()) return
        _feedStates.update { currentMap ->
            currentMap.mapValues { (_, feedState) ->
                val patched = feedState.items.map { item ->
                    val cat = item.topic.categoryId?.let { known[it] }
                    if (cat != item.category) item.copy(category = cat) else item
                }
                if (patched != feedState.items) feedState.copy(items = patched) else feedState
            }
        }
        _state.update { state ->
            val patched = state.latest.items.map { item ->
                val cat = item.topic.categoryId?.let { known[it] }
                if (cat != item.category) item.copy(category = cat) else item
            }
            if (patched != state.latest.items) state.copy(latest = state.latest.copy(items = patched)) else state
        }
        _categoryTopics.update { map ->
            map.mapValues { (_, listState) ->
                val patched = listState.items.map { item ->
                    val cat = item.topic.categoryId?.let { known[it] }
                    if (cat != item.category) item.copy(category = cat) else item
                }
                if (patched != listState.items) listState.copy(items = patched) else listState
            }
        }
    }

    private fun syncCategoriesInBackground() {
        pageJobs["sync:categories"] = viewModelScope.launch {
            try {
                val updated = repository.refreshCategoriesFromServer()
                val known = repository.cachedCategoryMap()
                if (updated != null) {
                    val (freshList, rawJson) = updated
                    container.preferences.edit { putString("cached_categories_json", rawJson) }
                    _categories.value = CategoriesUiState(items = freshList, loaded = true, order = store.loadCategoryOrder())
                    diagnostics.log("分类表已从服务端同步更新(${known.size} 项)")
                }
                backfillCategories(known)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                diagnostics.log("后台分类探测跳过: ${error.javaClass.simpleName}")
            }
        }
    }

    fun switchHomeFeed(feed: HomeFeedType) {
        if (_state.value.homeFeed == feed) return
        val cached = _feedStates.value[feed]
        _state.update {
            it.copy(
                homeFeed = feed,
                latest = cached ?: TopicListUiState(initialLoading = true),
            )
        }
        store.saveHomeFeed(feed.name)
        if (cached == null || (cached.items.isEmpty() && !cached.loadingMore && !cached.refreshing)) {
            viewModelScope.launch { loadFeed(feed, page = 0, pullRefresh = false) }
        }
    }

    fun refreshLatest() {
        val feed = _state.value.homeFeed
        viewModelScope.launch { loadFeed(feed, page = 0, pullRefresh = true) }
    }

    fun retryLatest() {
        val feed = _state.value.homeFeed
        viewModelScope.launch { loadFeed(feed, page = 0, pullRefresh = false) }
    }

    fun loadMoreLatest() {
        val feed = _state.value.homeFeed
        val currentPage = _feedStates.value[feed]?.page ?: _state.value.latest.page
        viewModelScope.launch { loadFeed(feed, page = currentPage + 1, pullRefresh = false) }
    }

    fun setPath(path: NetworkPath) {
        if (path == _state.value.path) return
        _state.update { it.copy(path = path) }
        diagnostics.log("切换网络路径 -> ${path.label}")
        retryLatest()
    }

    fun toggleAutoRefresh() {
        val enable = !_state.value.autoRefresh
        _state.update { it.copy(autoRefresh = enable) }
        autoJob?.cancel()
        if (!enable) {
            diagnostics.log("自动刷新已关闭")
            return
        }
        diagnostics.log("自动刷新已开启(每 ${Config.AUTO_REFRESH_INTERVAL_MS / 60_000} 分钟)")
        autoJob = viewModelScope.launch {
            while (isActive) {
                delay(Config.AUTO_REFRESH_INTERVAL_MS)
                if (_state.value.screen == Screen.List) loadLatest(page = 0, pullRefresh = false)
            }
        }
    }

    /**
     * 由界面在验证页每秒轮询一次;完成条件满足后回到启动流程重新校验会话。
     *
     * 这里**必须**以"拿到会话 Cookie"为完成条件。早先 `Screen.Verify -> true` 是无条件成立,
     * 只要 WebView 打开的页面不是 CF 挑战页就立刻判定验证完成 —— 而邮件登录的确认页
     * 域名和标题都正常,于是刚打开就被当成验证通过,随即 boot() 发现没有 _t 又弹回登录页。
     */
    suspend fun pollVerification() {
        if (verifying) return
        if (!browser.isPageCleared()) return
        val cookies = store.syncFromWebView()
        val hasSession = cookies.containsKey(Config.SESSION_COOKIE)
        when (_state.value.screen) {
            Screen.Verify -> when {
                pendingEmailLoginToken != null -> {
                    val token = pendingEmailLoginToken ?: return
                    verifying = true
                    pendingEmailLoginToken = null
                    diagnostics.log("安全验证完成,重放邮件登录")
                    retryEmailLogin(token)
                }
                resumeAfterVerification != null -> {
                    verifying = true
                    val resume = resumeAfterVerification
                    resumeAfterVerification = null
                    diagnostics.log("安全验证完成,恢复中断的操作")
                    _state.update { it.copy(screen = Screen.List) }
                    verifying = false
                    resume?.invoke()
                }
                hasSession -> {
                    verifying = true
                    diagnostics.log("安全验证完成,重新校验会话")
                    boot()
                }
                // 页面不再被挑战但仍无会话:本来就没登录,回登录页收场,不要在这里干等。
                else -> {
                    diagnostics.log("安全验证通过但无会话,回到登录页")
                    enterWebScreen(Screen.Login)
                }
            }
            Screen.Login -> if (hasSession && !verifying) {
                verifying = true
                diagnostics.log("检测到登录 Cookie _t，正在校验服务端真实会话...")
                val verifiedUser = runCatching { repository.fetchCurrentUser() }.getOrNull()
                if (!verifiedUser.isNullOrBlank()) {
                    diagnostics.log("服务端会话确认有效 (用户: $verifiedUser)，关闭弹层进入论坛")
                    _loginUiState.update { LoginUiState() }
                    boot()
                } else {
                    diagnostics.log("Cookie 存在但服务端尚未建立有效会话，继续等待页面完成登录")
                    verifying = false
                }
            }
            else -> Unit
        }
    }

    fun logout() {
        setBackgroundNotification(false)
        autoJob?.cancel()
        searchJob?.cancel()
        unreadJob?.cancel()
        pageJobs.values.forEach { it.cancel() }
        pageJobs.clear()
        readReportJobs.values.forEach { it.cancel() }
        readReportJobs.clear()
        topicEnteredAt.clear()
        lastReportedAt.clear()
        reportedReadMs.clear()
        activeTopicIds.clear()
        pendingVisiblePostsByTopic.clear()
        currentVisiblePostsByTopic.clear()
        resumeAfterVerification = null
        pendingEmailLoginToken = null
        container.preferences.edit { remove("cached_username") }
        _details.value = emptyMap()
        _categories.value = CategoriesUiState()
        _categoryTopics.value = emptyMap()
        _userTopicStates.value = emptyMap()
        _search.value = SearchUiState()
        _profile.value = emptyMap()
        notificationCache.clear()
        _notificationFilter.value = NotificationFilter.All
        _composer.value = ComposerUiState()
        _topicCreator.value = TopicCreatorUiState()
        _drafts.value = DraftsUiState()
        viewModelScope.launch {
            store.clear()
            browser.reset()
            diagnostics.log("已登出并清除 Cookie")
            _feedStates.value = emptyMap()
            _state.update { UiState(path = it.path, userAgent = it.userAgent, homeFeed = initialHomeFeed) }
            _loginUiState.update {
                LoginUiState(
                    savedLogin = store.savedLogin().orEmpty(),
                    savedPassword = store.savedPassword().orEmpty(),
                    rememberPassword = store.isRememberPassword(),
                )
            }
            enterWebScreen(Screen.Login)
        }
    }

    private suspend fun enterWebScreen(screen: Screen, reason: String = "") {
        if (screen == Screen.Verify) diagnostics.recordInteractiveRequired(reason.ifBlank { "未知" })
        _state.update { it.copy(screen = screen) }
        // Login 现在是原生 Compose 页面，不需要在 WebView 里打开登录 URL。
        // Verify 时打开根 URL 触发 Cloudflare 人机验证。
        if (screen == Screen.Verify) {
            browser.openUrl("${Config.BASE_URL}/")
        }
    }

    private suspend fun loadLatest(page: Int, pullRefresh: Boolean) =
        loadFeed(_state.value.homeFeed, page, pullRefresh)

    private suspend fun loadFeed(feed: HomeFeedType, page: Int, pullRefresh: Boolean) {
        val current = _feedStates.value[feed] ?: _state.value.latest
        if (!loadingFeeds.add(feed) || current.refreshing || current.loadingMore) {
            // 已有加载在途，不重复发请求。但下拉刷新不能静默吞掉：下拉指示器要等
            // refreshing 由 true 回落才收起，直接 return 会让它一直转。
            // 让这次下拉跟随在途加载走完一次完整跳变（带超时兜底，避免无限等待）。
            if (pullRefresh && page == 0 && !current.refreshing) {
                setFeedRefreshing(feed, true)
                var waitedMs = 0L
                while (loadingFeeds.contains(feed) && waitedMs < FEED_REFRESH_JOIN_TIMEOUT_MS) {
                    delay(50)
                    waitedMs += 50
                }
                setFeedRefreshing(feed, false)
            }
            return
        }

        val loadingState = current.copy(
            refreshing = pullRefresh && page == 0,
            loadingMore = page > 0,
            initialLoading = page == 0 && current.items.isEmpty() && !pullRefresh,
            error = null,
        )
        _feedStates.update { it + (feed to loadingState) }
        if (_state.value.homeFeed == feed) {
            _state.update { it.copy(latest = loadingState) }
        }

        try {
            val sortOrder = _topicSortOrder.value.apiValue
            val sortAscending = _topicSortAscending.value.takeIf { sortOrder != null }
            val result = repository.fetchFeed(feed, page, awaitCategories = false, order = sortOrder, ascending = sortAscending)
            if (page == 0 && pageJobs["sync:categories"]?.isActive != true && repository.cachedCategoryList() == null) {
                syncCategoriesInBackground()
            }
            val knownCategories = repository.cachedCategoryMap()
            val freshItems = if (knownCategories.isNotEmpty()) {
                result.items.map { item ->
                    val cat = item.category ?: item.topic.categoryId?.let { knownCategories[it] }
                    if (cat != item.category) item.copy(category = cat) else item
                }
            } else result.items
            val existing = _feedStates.value[feed]?.items ?: emptyList()
            val merged = if (page == 0) {
                freshItems.distinctBy { it.topic.id }
            } else {
                (existing + freshItems).distinctBy { it.topic.id }
            }
            val newState = current.copy(
                items = merged,
                page = page,
                hasMore = result.hasMore && (page == 0 || merged.size > existing.size),
                refreshing = false,
                loadingMore = false,
                initialLoading = false,
                error = null,
                pendingPage = if (page == 0) null else current.pendingPage,
                incomingTopicIds = if (page == 0) emptySet() else current.incomingTopicIds,
            )
            _feedStates.update { it + (feed to newState) }
            if (_state.value.homeFeed == feed) {
                _state.update { it.copy(latest = newState) }
            }
            refreshCookieInfo()
        } catch (error: CancellationException) {
            throw error
        } catch (error: NeedsInteractiveVerification) {
            settleFeedLoading(feed)
            enterWebScreen(Screen.Verify, error.message.orEmpty())
        } catch (error: HttpStatusException) {
            val msg = if (error.status == 403 && (_state.value.username == null || (feed == HomeFeedType.Unread || feed == HomeFeedType.Posted))) {
                "请先登录账号后查看${feed.label}"
            } else {
                error.message ?: "加载失败 (HTTP ${error.status})"
            }
            diagnostics.log("加载 ${feed.label} 失败: $msg")
            settleFeedLoading(feed, msg)
        } catch (error: Exception) {
            diagnostics.log("加载 ${feed.label} 异常: ${error.message}")
            settleFeedLoading(feed, error.message ?: error.javaClass.simpleName)
        } finally {
            loadingFeeds.remove(feed)
        }
    }

    /** 只翻转下拉刷新标志，不动已有数据：用于让被合并的下拉请求也有一次完整的状态跳变。 */
    private fun setFeedRefreshing(feed: HomeFeedType, refreshing: Boolean) {
        val current = _feedStates.value[feed] ?: _state.value.latest
        val next = current.copy(refreshing = refreshing)
        _feedStates.update { it + (feed to next) }
        if (_state.value.homeFeed == feed) {
            _state.update { it.copy(latest = next) }
        }
    }

    private fun settleFeedLoading(feed: HomeFeedType, message: String? = null) {
        val current = _feedStates.value[feed] ?: _state.value.latest
        val settled = current.copy(
            refreshing = false,
            loadingMore = false,
            initialLoading = false,
            error = message,
        )
        _feedStates.update { it + (feed to settled) }
        if (_state.value.homeFeed == feed) {
            _state.update { it.copy(latest = settled) }
        }
    }

    private fun settleLoading(message: String? = null) =
        settleFeedLoading(_state.value.homeFeed, message)

    private fun refreshCookieInfo() {
        val cookies = store.syncFromWebView()
        _state.update {
            it.copy(cookieNames = cookies.keys.sorted(), cookieSavedAt = store.savedAtMillis())
        }
    }

    private companion object {
        /** 长帖切倒序时一次拉取的尾部楼层数,与 fetchPosts 的单批上限一致。 */
        const val TAIL_WINDOW = 20

        /** 单次阅读进度上报最多携带多少个楼层号。 */
        const val READ_REPORT_POST_CAP = 60

        /** 下拉刷新跟随在途加载的最长等待，超时就收起指示器，避免卡在转圈。 */
        const val FEED_REFRESH_JOIN_TIMEOUT_MS = 15_000L
    }
}
