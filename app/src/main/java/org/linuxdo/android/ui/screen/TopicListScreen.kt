package org.linuxdo.android.ui.screen

import org.linuxdo.android.data.TopicPage
import org.linuxdo.android.data.TopicNotificationLevel
import org.linuxdo.android.data.PostFlagReason
import org.linuxdo.android.data.FloatingAction
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt
import org.jsoup.Jsoup
import org.linuxdo.android.data.Category
import org.linuxdo.android.data.Topic
import org.linuxdo.android.data.TopicSortOrder
import org.linuxdo.android.data.HomeFeedType
import org.linuxdo.android.data.TopicListItem
import org.linuxdo.android.ui.design.IosActivityIndicator
import org.linuxdo.android.ui.design.IosDivider
import org.linuxdo.android.ui.design.IosLargeTitleScaffold
import org.linuxdo.android.ui.design.IosLoadingBox
import org.linuxdo.android.ui.design.IosMetrics
import org.linuxdo.android.ui.design.IosTheme
import org.linuxdo.android.ui.design.UserAvatar
import org.linuxdo.android.ui.design.avatarShape
import org.linuxdo.android.ui.format.RelativeTime

data class TopicListUiState(
    val items: List<TopicListItem> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
    val initialLoading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val pendingPage: TopicPage? = null,
    val incomingTopicIds: Set<Long> = emptySet(),
    val highlightedTopicIds: Set<Long> = emptySet(),
)

/** "最新"与"某分类下的帖子"用同一个列表页,只是标题与数据源不同。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicListScreen(
    title: String,
    state: TopicListUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenTopic: (Topic) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    active: Boolean = true,
    scrollToTopRequest: Int = 0,
    trailing: @Composable () -> Unit = {},
    currentFeed: HomeFeedType? = null,
    onFeedSelect: ((HomeFeedType) -> Unit)? = null,
    hasUnread: Boolean = false,
    categoryVariants: List<Category> = emptyList(),
    currentCategoryId: Int? = null,
    onSelectCategoryVariant: ((Category) -> Unit)? = null,
    onApplyIncoming: (() -> Unit)? = null,
    onSetNotificationLevel: ((Topic, TopicNotificationLevel) -> Unit)? = null,
    onCreateTopic: (() -> Unit)? = null,
    onBookmarkTopic: ((Topic, (String) -> Unit) -> Unit)? = null,
    onFlagTopic: ((Topic, Int, String?, () -> Unit, (String) -> Unit) -> Unit)? = null,
    sortOrder: TopicSortOrder = TopicSortOrder.Default,
    sortAscending: Boolean = false,
    onSortChange: (TopicSortOrder) -> Unit = {},
    floatingBallEnabled: Boolean = true,
    floatingBallActions: List<FloatingAction> = FloatingAction.DEFAULT_ACTIONS,
    floatingBallXRatio: Float = 0.95f,
    floatingBallYRatio: Float = 1.0f,
    floatingBallOnRight: Boolean = true,
    onFloatingBallPositionChange: (Float, Float) -> Unit = { _, _ -> },
    onNavigateToDrafts: (() -> Unit)? = null,
    onNavigateToSearch: (() -> Unit)? = null,
    onNavigateToCategories: (() -> Unit)? = null,
) {
    val feedListStates = remember { mutableMapOf<HomeFeedType, LazyListState>() }
    val categoryListStates = remember { mutableMapOf<Int, LazyListState>() }
    val defaultListState = rememberLazyListState()
    val listState = when {
        currentFeed != null -> feedListStates.getOrPut(currentFeed) { LazyListState() }
        currentCategoryId != null -> categoryListStates.getOrPut(currentCategoryId) { LazyListState() }
        else -> defaultListState
    }
    val pullState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var noticeToast by remember { mutableStateOf<String?>(null) }
    var showSortDialog by remember { mutableStateOf(false) }
    var flagTargetTopic by remember { mutableStateOf<Topic?>(null) }
    var flagSubmitting by remember { mutableStateOf(false) }
    var flagError by remember { mutableStateOf<String?>(null) }

    fun showNotice(message: String) {
        noticeToast = message
        scope.launch {
            delay(2500)
            if (noticeToast == message) noticeToast = null
        }
    }

    val copyTopicLink: (Topic) -> Unit = { topic ->
        clipboardManager.setText(AnnotatedString("https://linux.do/t/topic/${topic.id}"))
        showNotice("已复制话题链接")
    }
    val bookmarkTopicAction: ((Topic) -> Unit)? = if (onBookmarkTopic != null) {
        { topic -> onBookmarkTopic(topic) { msg -> showNotice(msg) } }
    } else null
    val flagTopicAction: ((Topic) -> Unit)? = if (onFlagTopic != null) {
        { topic ->
            flagError = null
            flagSubmitting = false
            flagTargetTopic = topic
        }
    } else null

    var revealedTopicId by remember(listState) { mutableStateOf<Long?>(null) }
    LaunchedEffect(active, listState.isScrollInProgress, state.refreshing) {
        if (!active || listState.isScrollInProgress || state.refreshing) revealedTopicId = null
    }
    val hasCategoryDropdown = categoryVariants.size > 1 && onSelectCategoryVariant != null

    // 距底部还有 4 项时就开始取下一页,滚到底时数据通常已经就位。
    // 必须要求列表已有真实数据:空列表时占位项也会让 lastVisible 命中阈值,
    // 配合"失败不更新 page"就会变成每秒重打同一页的死循环。
    val nearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(active, nearEnd, state.hasMore, state.loadingMore, state.items.size, state.error) {
        val canLoadMore = active && state.hasMore && !state.refreshing && !state.initialLoading &&
            !state.loadingMore &&
            state.items.isNotEmpty() &&
            state.error == null
        if (nearEnd && canLoadMore) onLoadMore()
    }


    val (pinnedGroups, plainItems) = remember(state.items) { pinnedPrefixGroups(state.items) }

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier,
        indicator = {
            val fraction = pullState.distanceFraction
            // 初始加载时列表中央已有加载指示，这里再叠一个下拉转圈就成了两个圈。
            if (!state.initialLoading && (state.refreshing || fraction > 0f)) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = IosMetrics.navBarHeight + 10.dp)
                        .graphicsLayer {
                            alpha = if (state.refreshing) 1f else min(1f, fraction * 1.4f)
                        },
                ) {
                    IosActivityIndicator(diameter = 22.dp)
                }
            }
        },
    ) {
        IosLargeTitleScaffold(
            title = title,
            listState = listState,
            onBack = onBack,
            compactTop = onBack == null,
            scrollToTopRequest = scrollToTopRequest,
            trailing = trailing,
            customLargeTitle = when {
                currentFeed != null && onFeedSelect != null -> {
                    {
                        HomeFeedTitleBar(
                            currentFeed = currentFeed,
                            onFeedSelect = onFeedSelect,
                            trailing = trailing,
                        )
                    }
                }
                hasCategoryDropdown -> {
                    {
                        CategoryVariantSelector(
                            title = title,
                            variants = categoryVariants,
                            currentCategoryId = currentCategoryId,
                            collapsed = false,
                            onSelect = { onSelectCategoryVariant?.invoke(it) },
                        )
                    }
                }
                else -> null
            },
            customCollapsedTitle = when {
                currentFeed != null && onFeedSelect != null -> {
                    {
                        HomeFeedCollapsedBar(
                            currentFeed = currentFeed,
                            onFeedSelect = onFeedSelect,
                        )
                    }
                }
                hasCategoryDropdown -> {
                    {
                        CategoryVariantSelector(
                            title = title,
                            variants = categoryVariants,
                            currentCategoryId = currentCategoryId,
                            collapsed = true,
                            onSelect = { onSelectCategoryVariant?.invoke(it) },
                        )
                    }
                }
                else -> null
            },
            floatingOverlay = {
                AnimatedVisibility(
                    visible = state.incomingTopicIds.isNotEmpty(),
                    enter = fadeIn(tween(200)) + slideInVertically(tween(250)) { -it },
                    exit = fadeOut(tween(150)) + slideOutVertically(tween(200)) { -it },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = if (onBack == null) 58.dp else IosMetrics.navBarHeight + 8.dp),
                ) {
                    IncomingTopicsBanner(
                        count = state.incomingTopicIds.size,
                        onClick = {
                            onApplyIncoming?.invoke()
                            scope.launch { listState.animateScrollToItem(0) }
                        },
                    )
                }
                if (floatingBallEnabled && floatingBallActions.isNotEmpty()) {
                    FloatingAssistiveBall(
                        actions = floatingBallActions,
                        sortOrder = sortOrder,
                        sortAscending = sortAscending,
                        initialXRatio = floatingBallXRatio,
                        initialYRatio = floatingBallYRatio,
                        onPositionChange = onFloatingBallPositionChange,
                        onActionClick = { action ->
                            when (action) {
                                FloatingAction.CreateTopic -> onCreateTopic?.invoke()
                                FloatingAction.Sort -> showSortDialog = true
                                FloatingAction.Drafts -> onNavigateToDrafts?.invoke()
                                FloatingAction.Search -> onNavigateToSearch?.invoke()
                                FloatingAction.Categories -> onNavigateToCategories?.invoke()
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 14.dp, bottom = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TopicSortFab(
                            sortOrder = sortOrder,
                            ascending = sortAscending,
                            onClick = { showSortDialog = true },
                        )
                        if (onCreateTopic != null) {
                            CreateTopicFab(onClick = onCreateTopic)
                        }
                    }
                }
                if (showSortDialog) {
                    TopicSortDialog(
                        currentOrder = sortOrder,
                        ascending = sortAscending,
                        onDismiss = { showSortDialog = false },
                        onSelectOrder = { order ->
                            onSortChange(order)
                            if (order == TopicSortOrder.Default || order != sortOrder) {
                                showSortDialog = false
                            }
                        },
                    )
                }
                noticeToast?.let { notice ->
                    Text(
                        notice,
                        style = IosTheme.type.footnote,
                        color = Color(0xFF30B85B),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 22.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(IosTheme.colors.card)
                            .border(1.dp, Color(0xFF34C759).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            },
        ) {
            state.error?.let { message ->
                item(key = "error") { ErrorNotice(message, onRefresh) }
            }
            if (state.initialLoading && state.items.isEmpty()) {
                item(key = "loading") { IosLoadingBox() }
            }
            if (!state.initialLoading && state.items.isEmpty() && state.error == null) {
                item(key = "empty") { EmptyNotice() }
            }

            val keyPrefix = currentFeed?.name ?: currentCategoryId ?: "topic"

            itemsIndexed(
                items = pinnedGroups,
                key = { _, group -> "${keyPrefix}_pinned_${group.first().topic.id}" },
            ) { _, group ->
                Column {
                    PinnedGroupCard(
                        items = group,
                        isHighlighted = group.any { it.topic.id in state.highlightedTopicIds },
                        onOpenTopic = onOpenTopic,
                        onSetNotificationLevel = onSetNotificationLevel,
                        onCopyLink = copyTopicLink,
                        onBookmark = bookmarkTopicAction,
                        onFlag = flagTopicAction,
                    )
                    IosDivider(startIndent = IosMetrics.screenPadding)
                }
            }

            itemsIndexed(
                items = plainItems,
                key = { _, item -> "${keyPrefix}_${item.topic.id}" },
            ) { index, item ->
                val isHighlighted = item.topic.id in state.highlightedTopicIds
                Column {
                    TopicRow(
                        item = item,
                        isHighlighted = isHighlighted,
                        revealed = revealedTopicId == item.topic.id,
                        onReveal = { opened -> revealedTopicId = item.topic.id.takeIf { opened } },
                        onSetNotificationLevel = if (onSetNotificationLevel != null) { level ->
                            onSetNotificationLevel(item.topic, level)
                        } else null,
                        onCopyLink = { copyTopicLink(item.topic) },
                        onBookmark = bookmarkTopicAction?.let { fn -> { fn(item.topic) } },
                        onFlag = flagTopicAction?.let { fn -> { fn(item.topic) } },
                        onClick = { onOpenTopic(item.topic) },
                    )
                    if (index < plainItems.lastIndex) {
                        IosDivider(startIndent = IosMetrics.screenPadding)
                    }
                }
            }

            if (state.loadingMore) {
                item(key = "load-more") { IosLoadingBox() }
            }
        }
    }

    flagTargetTopic?.let { topic ->
        TopicFlagDialog(
            topic = topic,
            submitting = flagSubmitting,
            error = flagError,
            onDismiss = {
                if (!flagSubmitting) {
                    flagTargetTopic = null
                    flagError = null
                }
            },
            onSubmit = { reasonId, message ->
                val handler = onFlagTopic ?: return@TopicFlagDialog
                flagSubmitting = true
                flagError = null
                handler(
                    topic,
                    reasonId,
                    message,
                    {
                        flagSubmitting = false
                        flagTargetTopic = null
                        showNotice("举报已提交，感谢您的反馈")
                    },
                    { err ->
                        flagSubmitting = false
                        flagError = err
                    },
                )
            },
        )
    }
}

/** 列表右下角创建新话题悬浮按钮（笔状图标，与详情页回复按钮风格一致）。 */
@Composable
private fun CreateTopicFab(onClick: () -> Unit) {
    val tintColor = IosTheme.colors.accent
    val bgBase = IosTheme.colors.card.copy(alpha = 0.95f)
    val bgTint = tintColor.copy(alpha = 0.12f)
    val borderColor = tintColor.copy(alpha = 0.28f)
    Box(
        Modifier
            .size(46.dp)
            .shadow(elevation = 3.dp, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .background(bgBase)
            .background(bgTint)
            .border(1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "创建新话题" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
                val stroke = Stroke(width = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val pen = Path().apply {
                    moveTo(4f, 20f)
                    lineTo(8.5f, 19f)
                    lineTo(19.5f, 8f)
                    lineTo(16f, 4.5f)
                    lineTo(5f, 15.5f)
                    close()
                }
                drawPath(pen, tintColor, style = stroke)
                drawLine(tintColor, Offset(13.5f, 7f), Offset(17f, 10.5f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                drawLine(tintColor, Offset(12.5f, 20f), Offset(20f, 20f), strokeWidth = 1.9f, cap = StrokeCap.Round)
            }
        }
    }
}

/** 列表右下角悬浮排序按钮（垂直双向箭头，位于发帖按钮上方）。 */
@Composable
private fun TopicSortFab(
    sortOrder: TopicSortOrder,
    ascending: Boolean,
    onClick: () -> Unit,
) {
    val isDefault = sortOrder == TopicSortOrder.Default
    val activeColor = IosTheme.colors.accent
    val tintColor = if (isDefault) IosTheme.colors.secondaryLabel else activeColor
    val bgBase = IosTheme.colors.card.copy(alpha = 0.94f)
    val borderColor = if (isDefault) IosTheme.colors.separator.copy(alpha = 0.4f) else activeColor.copy(alpha = 0.45f)

    Box(
        Modifier
            .size(44.dp)
            .shadow(elevation = 3.dp, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .background(bgBase)
            .border(1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "列表排序：${sortOrder.label}" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(19.dp)) {
            scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
                val stroke = Stroke(width = 2.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                // 左侧向上箭头 (x=8)
                drawLine(tintColor, Offset(8f, 18.5f), Offset(8f, 5.5f), strokeWidth = 2.1f, cap = StrokeCap.Round)
                val leftArrow = Path().apply {
                    moveTo(4.5f, 9.5f)
                    lineTo(8f, 5.5f)
                    lineTo(11.5f, 9.5f)
                }
                drawPath(leftArrow, tintColor, style = stroke)

                // 右侧向下箭头 (x=16)
                drawLine(tintColor, Offset(16f, 5.5f), Offset(16f, 18.5f), strokeWidth = 2.1f, cap = StrokeCap.Round)
                val rightArrow = Path().apply {
                    moveTo(12.5f, 14.5f)
                    lineTo(16f, 18.5f)
                    lineTo(19.5f, 14.5f)
                }
                drawPath(rightArrow, tintColor, style = stroke)
            }
        }
    }
}

/** 排序选项图标绘制 */
@Composable
private fun TopicSortIcon(
    order: TopicSortOrder,
    tint: Color,
    modifier: Modifier = Modifier.size(18.dp),
) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            when (order) {
                TopicSortOrder.Default -> {
                    // 三条横线 ≡
                    drawLine(tint, Offset(4f, 6.5f), Offset(20f, 6.5f), strokeWidth = 2f, cap = StrokeCap.Round)
                    drawLine(tint, Offset(4f, 12f), Offset(16f, 12f), strokeWidth = 2f, cap = StrokeCap.Round)
                    drawLine(tint, Offset(4f, 17.5f), Offset(12f, 17.5f), strokeWidth = 2f, cap = StrokeCap.Round)
                }
                TopicSortOrder.Activity -> {
                    // 逆时针时钟刷新 ↺
                    drawArc(
                        color = tint,
                        startAngle = -210f,
                        sweepAngle = 300f,
                        useCenter = false,
                        topLeft = Offset(4.5f, 4.5f),
                        size = androidx.compose.ui.geometry.Size(15f, 15f),
                        style = stroke,
                    )
                    val arrow = Path().apply {
                        moveTo(1.5f, 9.5f)
                        lineTo(4.5f, 6.5f)
                        lineTo(8f, 9.5f)
                    }
                    drawPath(arrow, tint, style = stroke)
                    drawLine(tint, Offset(12f, 12f), Offset(12f, 8.5f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                    drawLine(tint, Offset(12f, 12f), Offset(14.5f, 12f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                }
                TopicSortOrder.Created -> {
                    // 日历 📅
                    val cal = Path().apply {
                        moveTo(4f, 7f)
                        lineTo(20f, 7f)
                        lineTo(20f, 20f)
                        lineTo(4f, 20f)
                        close()
                    }
                    drawPath(cal, tint, style = stroke)
                    drawLine(tint, Offset(4f, 11f), Offset(20f, 11f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                    drawLine(tint, Offset(8f, 3.5f), Offset(8f, 7f), strokeWidth = 2f, cap = StrokeCap.Round)
                    drawLine(tint, Offset(16f, 3.5f), Offset(16f, 7f), strokeWidth = 2f, cap = StrokeCap.Round)
                }
                TopicSortOrder.Likes -> {
                    // 爱心 ♡
                    val heart = Path().apply {
                        moveTo(12f, 20.5f)
                        cubicTo(10.5f, 19f, 4f, 13.5f, 4f, 8.5f)
                        cubicTo(4f, 5.2f, 6.8f, 3.8f, 9.5f, 4.8f)
                        cubicTo(10.8f, 5.3f, 11.6f, 6.2f, 12f, 7.2f)
                        cubicTo(12.4f, 6.2f, 13.2f, 5.3f, 14.5f, 4.8f)
                        cubicTo(17.2f, 3.8f, 20f, 5.2f, 20f, 8.5f)
                        cubicTo(20f, 13.5f, 13.5f, 19f, 12f, 20.5f)
                        close()
                    }
                    drawPath(heart, tint, style = stroke)
                }
                TopicSortOrder.Views -> {
                    // 眼睛 👁
                    val eye = Path().apply {
                        moveTo(3f, 12f)
                        cubicTo(6.5f, 6f, 17.5f, 6f, 21f, 12f)
                        cubicTo(17.5f, 18f, 6.5f, 18f, 3f, 12f)
                        close()
                    }
                    drawPath(eye, tint, style = stroke)
                    drawCircle(tint, 2.8f, Offset(12f, 12f))
                }
                TopicSortOrder.Posts -> {
                    // 回复数 💬
                    val bubble = Path().apply {
                        moveTo(4f, 6f)
                        lineTo(20f, 6f)
                        lineTo(20f, 16f)
                        lineTo(10f, 16f)
                        lineTo(6f, 19.5f)
                        lineTo(6f, 16f)
                        lineTo(4f, 16f)
                        close()
                    }
                    drawPath(bubble, tint, style = stroke)
                }
                TopicSortOrder.Posters -> {
                    // 参与者 👥
                    drawCircle(tint, 3f, Offset(9f, 7.5f), style = stroke)
                    val body1 = Path().apply {
                        moveTo(4.5f, 18f)
                        cubicTo(4.5f, 14f, 13.5f, 14f, 13.5f, 18f)
                    }
                    drawPath(body1, tint, style = stroke)

                    drawCircle(tint, 2.4f, Offset(16.5f, 8.5f), style = stroke)
                    val body2 = Path().apply {
                        moveTo(13.8f, 18f)
                        cubicTo(13.8f, 15f, 19.5f, 15f, 19.5f, 18f)
                    }
                    drawPath(body2, tint, style = stroke)
                }
            }
        }
    }
}

/**
 * 列表排序选择弹出框（复刻参考图 1 风格：带标题Header、7个图标选项、选中对勾与升降序切换）。
 */
@Composable
private fun TopicSortDialog(
    currentOrder: TopicSortOrder,
    ascending: Boolean,
    onDismiss: () -> Unit,
    onSelectOrder: (TopicSortOrder) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 280.dp, max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(IosTheme.colors.card)
                .border(0.6.dp, IosTheme.colors.separator.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .padding(vertical = 6.dp),
        ) {
            // 顶部 Header: ≡ 排序：默认   ^
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopicSortIcon(
                    order = currentOrder,
                    tint = IosTheme.colors.label,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(12.dp))
                val sortSuffix = if (currentOrder != TopicSortOrder.Default) {
                    if (ascending) " (升序)" else " (降序)"
                } else ""
                Text(
                    text = "排序：${currentOrder.label}$sortSuffix",
                    style = IosTheme.type.headline.copy(fontWeight = FontWeight.SemiBold),
                    color = IosTheme.colors.label,
                    modifier = Modifier.weight(1f),
                )
                // 向上折叠指示符 ^
                Canvas(Modifier.size(14.dp)) {
                    val chevron = Path().apply {
                        moveTo(2f, 9.5f)
                        lineTo(7f, 4.5f)
                        lineTo(12f, 9.5f)
                    }
                    drawPath(chevron, Color(0xFF8E8E93), style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }

            IosDivider(startIndent = 14.dp, modifier = Modifier.padding(end = 14.dp))

            // 7 个排序条目
            TopicSortOrder.entries.forEach { order ->
                val isSelected = order == currentOrder
                val itemColor = if (isSelected) IosTheme.colors.accent else IosTheme.colors.label
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectOrder(order) }
                        .background(if (isSelected) IosTheme.colors.accent.copy(alpha = 0.08f) else Color.Transparent)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TopicSortIcon(
                        order = order,
                        tint = itemColor,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = order.label,
                        style = IosTheme.type.body.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = itemColor,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) {
                        if (order != TopicSortOrder.Default) {
                            Text(
                                text = if (ascending) "↑ 升序" else "↓ 降序",
                                style = IosTheme.type.caption.copy(fontWeight = FontWeight.Medium),
                                color = itemColor,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        Canvas(Modifier.size(16.dp)) {
                            val check = Path().apply {
                                moveTo(2.5f, 8.5f)
                                lineTo(6f, 12f)
                                lineTo(13.5f, 4.5f)
                            }
                            drawPath(check, itemColor, style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 话题长按操作菜单（提醒四档 + 复制链接、添加书签、举报）。[TopicRow] 与 [PinnedGroupCard] 共用。
 */
@Composable
private fun NotificationLevelMenu(
    expanded: Boolean,
    currentLevel: TopicNotificationLevel?,
    updating: Boolean,
    onDismiss: () -> Unit,
    onPick: ((TopicNotificationLevel) -> Unit)? = null,
    onCopyLink: (() -> Unit)? = null,
    onBookmark: (() -> Unit)? = null,
    onFlag: (() -> Unit)? = null,
) {
    if (!expanded) return
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(IosTheme.colors.card),
    ) {
        if (onPick != null) {
            val current = currentLevel ?: TopicNotificationLevel.Regular
            TopicNotificationLevel.entries.forEach { level ->
                val isSelected = level == current
                DropdownMenuItem(
                    enabled = !updating,
                    modifier = if (isSelected) Modifier.background(IosTheme.colors.accent.copy(alpha = 0.12f)) else Modifier,
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TopicNotificationIcon(level, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = level.title,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) IosTheme.colors.accent else IosTheme.colors.label,
                            )
                            if (isSelected) {
                                Spacer(Modifier.width(8.dp))
                                Text("✓", color = IosTheme.colors.accent, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    onClick = {
                        onDismiss()
                        onPick(level)
                    },
                )
            }
        }
        val hasExtraActions = onCopyLink != null || onBookmark != null || onFlag != null
        if (onPick != null && hasExtraActions) {
            IosDivider()
        }
        if (onCopyLink != null) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PostActionIcon(PostAction.CopyLink, false, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("复制链接", color = IosTheme.colors.label)
                    }
                },
                onClick = {
                    onDismiss()
                    onCopyLink()
                },
            )
        }
        if (onBookmark != null) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PostActionIcon(PostAction.Bookmark, false, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("添加书签", color = IosTheme.colors.label)
                    }
                },
                onClick = {
                    onDismiss()
                    onBookmark()
                },
            )
        }
        if (onFlag != null) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PostActionIcon(PostAction.Flag, false, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("举报", color = IosTheme.colors.label)
                    }
                },
                onClick = {
                    onDismiss()
                    onFlag()
                },
            )
        }
    }
}

/**
 * 把列表**头部连续**的置顶项按作者切成若干组,返回 (置顶分组, 其余话题)。
 *
 * 只吃头部前缀而不是全列表 filter:站方的置顶本来就排在最前,取前缀足以解决"置顶吃光首屏";
 * 全列表 filter 会把翻页后零散冒出来的置顶帖也抽到顶上,列表会在加载更多时自己跳一下。
 */
private fun pinnedPrefixGroups(
    items: List<TopicListItem>,
): Pair<List<List<TopicListItem>>, List<TopicListItem>> {
    val prefix = items.takeWhile { it.topic.pinned }
    if (prefix.isEmpty()) return emptyList<List<TopicListItem>>() to items
    // LinkedHashMap 保持作者首次出现的顺序,不打乱站方的置顶排序。
    val grouped = LinkedHashMap<String, MutableList<TopicListItem>>()
    prefix.forEach { entry ->
        grouped.getOrPut(entry.authorUsername ?: "") { mutableListOf() }.add(entry)
    }
    return grouped.values.map { it.toList() } to items.drop(prefix.size)
}

@Composable
private fun topicRowBackground(isHighlighted: Boolean): Color {
    val background by animateColorAsState(
        targetValue = if (isHighlighted) IosTheme.colors.accent.copy(alpha = 0.12f) else IosTheme.colors.card,
        animationSpec = tween(if (isHighlighted) 100 else 450), label = "topic-highlight",
    )
    return background
}

/**
 * 置顶帖合并卡。
 *
 * 站方同时挂多条全局置顶时,每条各铺一张完整卡片会把首屏吃光 —— 下拉刷新后想看的新帖只剩一条。
 * 这里把同一作者的连续置顶折成一张卡:只保留作者身份(头像 + 用户名 + Lv 徽章)和标题行,
 * 摘要、缩略图、回复/浏览/时间与标签全部省掉。标题仍走 [titleWithBadges],
 * 所以未读蓝点、"置顶""已关闭"标记不必重写一套。
 *
 * 不做左滑四档抽屉:一张卡对应多个话题,左滑该落到哪一条没有明确语义;
 * 要单独设免打扰就长按对应那一行标题。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PinnedGroupCard(
    items: List<TopicListItem>,
    onOpenTopic: (Topic) -> Unit,
    onSetNotificationLevel: ((Topic, TopicNotificationLevel) -> Unit)? = null,
    onCopyLink: ((Topic) -> Unit)? = null,
    onBookmark: ((Topic) -> Unit)? = null,
    onFlag: ((Topic) -> Unit)? = null,
    isHighlighted: Boolean = false,
) {
    val head = items.firstOrNull() ?: return
    val rowBackground = topicRowBackground(isHighlighted)
    var menuTopicId by remember { mutableStateOf<Long?>(null) }
    val hasAnyLongPress = onSetNotificationLevel != null || onCopyLink != null || onBookmark != null || onFlag != null
    Column(
        Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .padding(horizontal = IosMetrics.screenPadding, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(
                    avatarUrl = head.avatarUrl,
                    fallbackUrl = head.staticAvatarUrl,
                    username = head.authorUsername,
                    modifier = Modifier.size(24.dp),
                    cornerRadius = 3.5.dp,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = head.displayName.ifBlank { "匿名" },
                    style = IosTheme.type.footnote,
                    color = IosTheme.colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                head.trustLevel?.takeIf { it > 0 }?.let { level ->
                    Spacer(Modifier.width(4.dp))
                    LevelBadge(level)
                }
            }
            Text(
                text = "置顶 ${items.size}",
                style = IosTheme.type.footnote,
                color = IosTheme.colors.secondaryLabel,
            )
        }
        items.forEach { entry ->
            Spacer(Modifier.height(6.dp))
            Box {
                Text(
                    text = titleWithBadges(entry.topic, IosTheme.colors.accent),
                    style = IosTheme.type.body,
                    color = IosTheme.colors.label,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    inlineContent = unseenDotContent(IosTheme.colors.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onOpenTopic(entry.topic) },
                            onLongClick = if (hasAnyLongPress && !entry.notificationLevelUpdating) {
                                { menuTopicId = entry.topic.id }
                            } else null,
                        ),
                )
                NotificationLevelMenu(
                    expanded = menuTopicId == entry.topic.id && hasAnyLongPress,
                    currentLevel = entry.notificationLevel,
                    updating = entry.notificationLevelUpdating,
                    onDismiss = { menuTopicId = null },
                    onPick = onSetNotificationLevel?.let { fn -> { level -> fn(entry.topic, level) } },
                    onCopyLink = onCopyLink?.let { fn -> { fn(entry.topic) } },
                    onBookmark = onBookmark?.let { fn -> { fn(entry.topic) } },
                    onFlag = onFlag?.let { fn -> { fn(entry.topic) } },
                )
            }
        }
    }
}

/**
 * 紧凑高效风列表卡片(三段式)。
 *
 * 之所以不给摘要和多图预留固定区域:linux.do 的 /latest.json 对普通帖**不下发 excerpt**
 * (只有置顶帖和搜索结果有),图也只给单张 image_url。硬留版位要么大面积留白、一屏只剩两条,
 * 要么得逐帖拉详情去凑内容 —— 那是在拿跳盾风险换排版。所以这里"有就展开、没有就收起":
 * 顶栏(作者 + Lv + 分类)、主体(标题 + 按需摘要 + 按需首图)、底栏(回复 / 浏览 / 活动时间 + 标签)。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopicRow(
    item: TopicListItem,
    query: String? = null,
    isHighlighted: Boolean = false,
    revealed: Boolean = false,
    onReveal: (Boolean) -> Unit = {},
    onSetNotificationLevel: ((TopicNotificationLevel) -> Unit)? = null,
    onCopyLink: (() -> Unit)? = null,
    onBookmark: (() -> Unit)? = null,
    onFlag: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var showLevelMenu by remember { mutableStateOf(false) }
    val hasAnyLongPress = onSetNotificationLevel != null || onCopyLink != null || onBookmark != null || onFlag != null
    val topic = item.topic
    val drawerWidth = with(LocalDensity.current) { 240.dp.toPx() }
    var dragging by remember(topic.id) { mutableStateOf(false) }
    var dragOffset by remember(topic.id) { mutableFloatStateOf(0f) }
    val currentRevealed by rememberUpdatedState(revealed)
    val offset by animateFloatAsState(
        targetValue = if (dragging) dragOffset else if (revealed) -drawerWidth else 0f,
        animationSpec = if (dragging) snap() else tween(180), label = "topic-swipe",
    )
    val rowBackground = topicRowBackground(isHighlighted)
    val needle = query?.trim().orEmpty()
    val accent = IosTheme.colors.accent
    val excerpt = remember(topic.id, topic.excerpt, needle, accent) {
        val raw = topic.excerpt?.let { Jsoup.parseBodyFragment(it).text().trim() }?.takeIf { it.isNotEmpty() }
        if (raw.isNullOrBlank()) null
        else if (needle.isEmpty()) buildAnnotatedString { append(raw) }
        else buildAnnotatedString {
            var start = raw.indexOf(needle, ignoreCase = true)
            var cursor = 0
            while (start >= 0) {
                append(raw.substring(cursor, start))
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) {
                    append(raw.substring(start, start + needle.length))
                }
                cursor = start + needle.length
                start = raw.indexOf(needle, cursor, ignoreCase = true)
            }
            append(raw.substring(cursor))
        }
    }
    Box(Modifier.fillMaxWidth().clipToBounds()) {
        if (onSetNotificationLevel != null && (revealed || dragging || offset < 0f)) {
            Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.End) {
                TopicNotificationLevel.entries.forEach { level ->
                    Box(Modifier.width(60.dp).fillMaxHeight()
                        .background(notificationLevelColor(level))
                        .clickable(enabled = !item.notificationLevelUpdating) {
                            onReveal(false)
                            onSetNotificationLevel(level)
                        }, contentAlignment = Alignment.Center) {
                        Text(level.badgeLabel, style = IosTheme.type.footnote, color = Color.White)
                    }
                }
            }
        }
        Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = offset }
            .background(rowBackground)
            .then(if (onSetNotificationLevel == null) Modifier else Modifier.pointerInput(topic.id, drawerWidth, item.notificationLevelUpdating) {
                if (!item.notificationLevelUpdating) detectHorizontalDragGestures(
                    onDragStart = {
                        dragOffset = if (currentRevealed) -drawerWidth else 0f
                        dragging = true
                        onReveal(true)
                    },
                    onHorizontalDrag = { change, distance ->
                        change.consume()
                        dragOffset = (dragOffset + distance).coerceIn(-drawerWidth, 0f)
                    },
                    onDragEnd = { dragging = false; onReveal(dragOffset < -drawerWidth / 3f) },
                    onDragCancel = { dragging = false; onReveal(false) },
                )
            })
            .combinedClickable(
                onClick = { if (revealed) onReveal(false) else onClick() },
                onLongClick = if (hasAnyLongPress && !item.notificationLevelUpdating) {
                    { onReveal(false); showLevelMenu = true }
                } else null,
            )
            .padding(horizontal = IosMetrics.screenPadding, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 作者区整体吃掉除分类外的全部宽度,分类才能稳定贴住右边界 ——
            // 否则作者名和弹性空隙会平分剩余宽度,分类的位置就跟着用户名长短左右浮动,
            // 上下相邻卡片的分类对不成一条竖线。
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(
                    avatarUrl = item.avatarUrl,
                    fallbackUrl = item.staticAvatarUrl,
                    username = item.authorUsername,
                    modifier = Modifier.size(24.dp),
                    cornerRadius = 3.5.dp,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = item.displayName.ifBlank { "匿名" },
                    style = IosTheme.type.footnote,
                    color = IosTheme.colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                item.trustLevel?.takeIf { it > 0 }?.let { level ->
                    Spacer(Modifier.width(4.dp))
                    LevelBadge(level)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.notificationLevel?.takeIf { it != TopicNotificationLevel.Regular }?.let { level ->
                    TopicNotificationLevelTag(level)
                    Spacer(Modifier.width(6.dp))
                }
                item.category?.let {
                    CategoryTag(it)
                }
            }
        }
        NotificationLevelMenu(
            expanded = showLevelMenu && hasAnyLongPress,
            currentLevel = item.notificationLevel,
            updating = item.notificationLevelUpdating,
            onDismiss = { showLevelMenu = false },
            onPick = onSetNotificationLevel,
            onCopyLink = onCopyLink,
            onBookmark = onBookmark,
            onFlag = onFlag,
        )
        Spacer(Modifier.height(6.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Text(
                    text = titleWithBadges(topic, IosTheme.colors.accent, query),
                    style = IosTheme.type.body,
                    color = IosTheme.colors.label,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    inlineContent = unseenDotContent(IosTheme.colors.accent),
                )
                excerpt?.let { text ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = text,
                        style = IosTheme.type.footnote,
                        color = IosTheme.colors.secondaryLabel,
                        maxLines = if (query.isNullOrBlank()) 2 else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            topic.imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
                Spacer(Modifier.width(10.dp))
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 72.dp, height = 54.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(IosTheme.colors.fieldBackground),
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MetaStat(formatCount(topic.totalReplies), textWidth = 30.dp) { ReplyGlyph(it) }
            if (topic.views > 0) {
                Spacer(Modifier.width(10.dp))
                MetaStat(formatCount(topic.views), textWidth = 34.dp) { ViewGlyph(it) }
            }
            Spacer(Modifier.width(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClockGlyph(IosTheme.colors.tertiaryLabel)
                Spacer(Modifier.width(3.dp))
                Text(
                    text = RelativeTime.format(topic.bumpedAt ?: topic.lastPostedAt ?: topic.createdAt),
                    style = IosTheme.type.caption,
                    color = IosTheme.colors.secondaryLabel,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.weight(1f))
            topic.tags.take(2).forEach { tag ->
                Spacer(Modifier.width(4.dp))
                TagChip(tag)
            }
        }
        }
    }
}

/** 一档信任等级的配色。描边与文字都留 Brush 口子,好让高等级上金属渐变和彩虹。 */
private data class LevelPalette(
    val background: Brush,
    val border: Brush,
    val content: Color,
    val contentBrush: Brush? = null,
)

private val RAINBOW = listOf(
    Color(0xFFFF4D6D), Color(0xFFFF9F1C), Color(0xFFFFD60A),
    Color(0xFF34C759), Color(0xFF0A84FF), Color(0xFFAF52DE),
)

/**
 * 作者信任等级徽章。颜色本身就是身份:Lv1 蓝、Lv2 琥珀橙、Lv3 鎏金、Lv4 彩虹加金冠。
 *
 * Lv1/Lv2 的色值与「分类」页的 Lv 勋章同源,免得同一个 Lv 在两个页面是两种颜色。
 * Lv3 起刻意改成**深底亮字**:Lv2 和 Lv3 都在黄色系里,只调深浅在 10sp 的徽章上根本分不开,
 * 明暗反转才一眼可辨 —— 顺带让等级越高的牌子越有金属分量。
 * 这里一律用静态渐变、不挂流光动画:列表每行都要画一遍,动画那点光泽不值这个开销。
 */
@Composable
private fun LevelBadge(level: Int) {
    val isDark = IosTheme.colors.isDark
    val shape = RoundedCornerShape(4.dp)
    val palette = when {
        level >= 4 -> LevelPalette(
            background = Brush.horizontalGradient(
                listOf(Color(0xFF241233), Color(0xFF32124A), Color(0xFF1A1030)),
            ),
            border = Brush.horizontalGradient(RAINBOW),
            content = Color(0xFFFFF1C2),
            contentBrush = Brush.horizontalGradient(RAINBOW),
        )
        level == 3 -> LevelPalette(
            background = Brush.horizontalGradient(
                listOf(Color(0xFF3E2B08), Color(0xFF6B4A12), Color(0xFF4A3410)),
            ),
            border = Brush.horizontalGradient(
                listOf(Color(0xFFFFE9A8), Color(0xFFD4AF37), Color(0xFFFFD76A)),
            ),
            content = Color(0xFFFFD873),
        )
        level == 2 -> LevelPalette(
            background = Brush.horizontalGradient(
                if (isDark) listOf(Color(0xFF33230A), Color(0xFF47310E))
                else listOf(Color(0xFFFFF6DE), Color(0xFFFFE7B0)),
            ),
            border = SolidColor(if (isDark) Color(0xFF8F651A) else Color(0xFFEFC163)),
            content = if (isDark) Color(0xFFFFD166) else Color(0xFF824B05),
        )
        else -> LevelPalette(
            background = Brush.horizontalGradient(
                if (isDark) listOf(Color(0xFF13263E), Color(0xFF193354))
                else listOf(Color(0xFFEAF3FF), Color(0xFFDCEBFF)),
            ),
            border = SolidColor(if (isDark) Color(0xFF2B548C) else Color(0xFFBBD7FF)),
            content = if (isDark) Color(0xFF6EAEFF) else Color(0xFF155FD0),
        )
    }
    val weight = if (level >= 3) FontWeight.ExtraBold else FontWeight.SemiBold
    val textStyle = if (palette.contentBrush != null) {
        TextStyle(brush = palette.contentBrush, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = weight)
    } else {
        TextStyle(color = palette.content, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = weight)
    }
    Row(
        Modifier
            .clip(shape)
            .background(palette.background)
            .border(0.8.dp, palette.border, shape)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (level >= 4) {
            CrownGlyph(Color(0xFFFFD60A))
            Spacer(Modifier.width(2.dp))
        }
        Text(text = "Lv$level", style = textStyle)
    }
}

/** Lv4 专属小金冠。只有最高等级才画,所以列表里的数量天然很少。 */
@Composable
private fun CrownGlyph(color: Color) {
    Canvas(Modifier.size(8.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val crown = Path().apply {
                moveTo(3f, 19f)
                lineTo(1.5f, 7f)
                lineTo(7.5f, 12f)
                lineTo(12f, 4f)
                lineTo(16.5f, 12f)
                lineTo(22.5f, 7f)
                lineTo(21f, 19f)
                close()
            }
            drawPath(crown, color)
        }
    }
}

@Composable
private fun TagChip(tag: String) {
    Text(
        text = tag,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        color = IosTheme.colors.secondaryLabel,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(IosTheme.colors.fieldBackground)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/**
 * 底栏的一项统计。数字区**定宽**:回复数从 1 位变 3 位,整条底栏后面的浏览量和时间就会
 * 跟着左右平移,上下相邻卡片对不成竖线。同时开 `tnum` 等宽数字 —— 比例字体里 "1" 比 "8" 窄,
 * 不然即使容器定宽,数字本身也仍然参差。
 */
@Composable
private fun MetaStat(value: String, textWidth: Dp, glyph: @Composable (Color) -> Unit) {
    val color = IosTheme.colors.tertiaryLabel
    Row(verticalAlignment = Alignment.CenterVertically) {
        glyph(color)
        Spacer(Modifier.width(3.dp))
        Text(
            text = value,
            style = IosTheme.type.caption.copy(fontFeatureSettings = "tnum"),
            color = IosTheme.colors.secondaryLabel,
            maxLines = 1,
            modifier = Modifier.width(textWidth),
        )
    }
}

/** 回复气泡。自己画而不用 emoji —— 各机型的 emoji 字体会让这一行的高度和配色乱掉。 */
@Composable
private fun ReplyGlyph(color: Color) {
    Canvas(Modifier.size(11.dp)) {
        val bubbleHeight = size.height * 0.74f
        drawRoundRect(
            color = color,
            size = Size(size.width, bubbleHeight),
            cornerRadius = CornerRadius(size.width * 0.24f),
            style = Stroke(size.width * 0.12f),
        )
        val tail = Path().apply {
            moveTo(size.width * 0.26f, bubbleHeight * 0.92f)
            lineTo(size.width * 0.3f, size.height)
            lineTo(size.width * 0.54f, bubbleHeight * 0.92f)
            close()
        }
        drawPath(tail, color)
    }
}

@Composable
private fun ViewGlyph(color: Color) {
    Canvas(Modifier.size(11.dp)) {
        drawOval(
            color = color,
            topLeft = Offset(0f, size.height * 0.18f),
            size = Size(size.width, size.height * 0.64f),
            style = Stroke(size.width * 0.11f),
        )
        drawCircle(color, size.width * 0.16f, Offset(size.width / 2f, size.height / 2f))
    }
}

/** 小钟表图标。与气泡、眼睛保持相同的 11.dp 紧凑比例。 */
@Composable
private fun ClockGlyph(color: Color) {
    Canvas(Modifier.size(11.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            drawCircle(color, radius = 9f, center = Offset(12f, 12f), style = Stroke(2.2f))
            drawLine(color, Offset(12f, 7.5f), Offset(12f, 12f), strokeWidth = 2.2f, cap = StrokeCap.Round)
            drawLine(color, Offset(12f, 12f), Offset(15.5f, 12f), strokeWidth = 2.2f, cap = StrokeCap.Round)
        }
    }
}

/**
 * 未读蓝点与置顶/关闭标记都内联进标题文本流,和标题共占 2 行配额,
 * 不在标题上方额外占一行 —— 紧凑卡片经不起多一行的开销。
 */
private fun titleWithBadges(topic: Topic, accent: Color, query: String? = null) = buildAnnotatedString {
    if (topic.unseen) {
        appendInlineContent(UNSEEN_DOT, "•")
        append(" ")
    }
    if (topic.pinned) {
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)) {
            append("置顶")
        }
        append(" ")
    }
    if (topic.closed) {
        withStyle(SpanStyle(color = accent.copy(alpha = 0.7f), fontSize = 15.sp)) { append("已关闭") }
        append(" ")
    }
    val title = topic.title
    val needle = query?.trim().orEmpty()
    if (needle.isNotEmpty()) {
        var start = title.indexOf(needle, ignoreCase = true)
        var cursor = 0
        while (start >= 0) {
            append(title.substring(cursor, start))
            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) {
                append(title.substring(start, start + needle.length))
            }
            cursor = start + needle.length
            start = title.indexOf(needle, cursor, ignoreCase = true)
        }
        append(title.substring(cursor))
    } else {
        append(title)
    }
}

private const val UNSEEN_DOT = "unseen-dot"

@Composable
private fun unseenDotContent(accent: Color): Map<String, InlineTextContent> = remember(accent) {
    mapOf(
        UNSEEN_DOT to InlineTextContent(
            Placeholder(9.sp, 9.sp, PlaceholderVerticalAlign.TextCenter),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
            }
        },
    )
}

/** 浏览量这类大数按千位缩写,免得把这一行挤到换行。 */
internal fun formatCount(value: Int): String {
    if (value < 1_000) return value.coerceAtLeast(0).toString()
    val thousands = value / 1_000.0
    val text = if (thousands >= 100) {
        thousands.roundToInt().toString()
    } else {
        String.format(Locale.US, "%.1f", thousands).removeSuffix(".0")
    }
    return "${text}k"
}

@Composable
internal fun CategoryTag(category: Category, prominent: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(parseCategoryColor(category.color) ?: IosTheme.colors.tertiaryLabel),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = category.name,
            style = if (prominent) IosTheme.type.body else IosTheme.type.footnote,
            color = if (prominent) IosTheme.colors.label else IosTheme.colors.secondaryLabel,
            maxLines = 1,
        )
    }
}

@Composable
internal fun ErrorNotice(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = IosMetrics.screenPadding, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = message,
            style = IosTheme.type.subheadline,
            color = IosTheme.colors.secondaryLabel,
        )
        Text(
            text = "重试",
            style = IosTheme.type.body,
            color = IosTheme.colors.accent,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onRetry)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

@Composable
internal fun EmptyNotice() {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "这里还没有内容",
            style = IosTheme.type.subheadline,
            color = IosTheme.colors.tertiaryLabel,
        )
    }
}

/** Discourse 下发的分类色是不带 # 的六位十六进制。 */
fun parseCategoryColor(hex: String?): Color? {
    val cleaned = hex?.removePrefix("#")?.trim() ?: return null
    if (cleaned.length != 6) return null
    return runCatching { Color(cleaned.toLong(16) or 0xFF000000L) }.getOrNull()
}

@Composable
private fun HomeFeedTitleBar(
    currentFeed: HomeFeedType,
    onFeedSelect: (HomeFeedType) -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IosMetrics.largeTitleHeight)
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            HomeFeedType.entries.forEach { feed ->
                val selected = feed == currentFeed
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onFeedSelect(feed) }
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = feed.label,
                        fontSize = if (selected) 21.sp else 16.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) IosTheme.colors.label else IosTheme.colors.secondaryLabel,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(IosTheme.colors.accent),
                        )
                    } else {
                        Spacer(Modifier.height(3.dp))
                    }
                }
            }
        }
        trailing()
    }
}

@Composable
private fun HomeFeedCollapsedBar(
    currentFeed: HomeFeedType,
    onFeedSelect: (HomeFeedType) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        HomeFeedType.entries.forEach { feed ->
            val selected = feed == currentFeed
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onFeedSelect(feed) }
                    .padding(horizontal = 2.dp, vertical = 2.dp),
            ) {
                Text(
                    text = feed.label,
                    fontSize = if (selected) 16.sp else 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) IosTheme.colors.label else IosTheme.colors.secondaryLabel,
                )
                Spacer(Modifier.height(2.dp))
                if (selected) {
                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(1.25.dp))
                            .background(IosTheme.colors.accent),
                    )
                } else {
                    Spacer(Modifier.height(2.5.dp))
                }
            }
        }
    }
}

private val CATEGORY_LEVEL_REGEX = Regex("[,，\\s]*Lv\\s*(\\d+)\\s*$", RegexOption.IGNORE_CASE)

internal fun categoryLevelOf(category: Category): Int =
    CATEGORY_LEVEL_REGEX.find(category.name)?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 3) ?: 0

internal fun categoryBaseNameOf(name: String): String =
    name.replace(CATEGORY_LEVEL_REGEX, "").trim().ifBlank { name }

@Composable
private fun CategoryVariantSelector(
    title: String,
    variants: List<Category>,
    currentCategoryId: Int?,
    collapsed: Boolean,
    onSelect: (Category) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentCategory = variants.firstOrNull { it.id == currentCategoryId }
    val currentLevel = currentCategory?.let(::categoryLevelOf)
        ?: CATEGORY_LEVEL_REGEX.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 3)
        ?: 0
    val baseTitle = categoryBaseNameOf(currentCategory?.name ?: title)
    val isDark = IosTheme.colors.isDark

    val (pillBg, pillBorder, pillText) = levelPillColors(currentLevel, isDark)

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(
                    bottom = if (collapsed) 0.dp else 6.dp,
                    end = 4.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = baseTitle,
                style = if (collapsed) {
                    IosTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold)
                } else {
                    IosTheme.type.largeTitle
                },
                color = IosTheme.colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(pillBg)
                    .border(0.8.dp, pillBorder, RoundedCornerShape(6.dp))
                    .padding(
                        horizontal = if (collapsed) 6.dp else 8.dp,
                        vertical = if (collapsed) 2.dp else 3.5.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Lv.$currentLevel",
                    style = IosTheme.type.caption.copy(
                        fontSize = if (collapsed) 11.sp else 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = pillText,
                )
                DropdownChevronGlyph(
                    color = pillText,
                    expanded = expanded,
                    size = if (collapsed) 9.dp else 10.dp,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = IosTheme.colors.card,
            shape = RoundedCornerShape(14.dp),
        ) {
            variants.forEachIndexed { idx, variant ->
                val level = categoryLevelOf(variant)
                val selected = variant.id == currentCategoryId
                val (itemBg, itemBorder, itemText) = levelPillColors(level, isDark)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expanded = false
                            onSelect(variant)
                        }
                        .background(
                            if (selected) IosTheme.colors.accent.copy(alpha = 0.08f) else Color.Transparent,
                        )
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(parseCategoryColor(variant.color) ?: IosTheme.colors.tertiaryLabel),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = categoryBaseNameOf(variant.name),
                        style = IosTheme.type.subheadline,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) IosTheme.colors.accent else IosTheme.colors.label,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(itemBg)
                            .border(0.7.dp, itemBorder, RoundedCornerShape(5.dp))
                            .padding(horizontal = 6.dp, vertical = 1.5.dp),
                    ) {
                        Text(
                            text = "Lv.$level",
                            style = IosTheme.type.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = itemText,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(14.dp))
                    if (selected) {
                        Text(
                            text = "✓",
                            style = IosTheme.type.subheadline,
                            fontWeight = FontWeight.Bold,
                            color = IosTheme.colors.accent,
                        )
                    } else if (variant.topicCount > 0) {
                        Text(
                            text = "${variant.topicCount}",
                            style = IosTheme.type.caption,
                            color = IosTheme.colors.tertiaryLabel,
                        )
                    }
                }
                if (idx < variants.lastIndex) {
                    IosDivider(startIndent = 14.dp)
                }
            }
        }
    }
}

internal fun levelPillColors(level: Int, isDark: Boolean): Triple<Color, Color, Color> = when (level) {
    0 -> if (isDark) {
        Triple(Color(0xFF25282E), Color(0xFF3E434D), Color(0xFFD5D8E0))
    } else {
        Triple(Color(0xFFF0F2F6), Color(0xFFD5DAE3), Color(0xFF4A5160))
    }
    1 -> if (isDark) {
        Triple(Color(0xFF152942), Color(0xFF2B548C), Color(0xFF6EAEFF))
    } else {
        Triple(Color(0xFFEAF3FF), Color(0xFFBBD7FF), Color(0xFF155FD0))
    }
    2 -> if (isDark) {
        Triple(Color(0xFF36250B), Color(0xFF8F651A), Color(0xFFFFD166))
    } else {
        Triple(Color(0xFFFFF4D9), Color(0xFFEFC163), Color(0xFF824B05))
    }
    else -> Triple(Color(0xFF4A3410), Color(0xFFD4AF37), Color(0xFFFFE28A))
}

@Composable
private fun DropdownChevronGlyph(
    color: Color,
    expanded: Boolean,
    size: Dp,
) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            if (expanded) {
                moveTo(w * 0.18f, h * 0.68f)
                lineTo(w * 0.5f, h * 0.32f)
                lineTo(w * 0.82f, h * 0.68f)
            } else {
                moveTo(w * 0.18f, h * 0.34f)
                lineTo(w * 0.5f, h * 0.70f)
                lineTo(w * 0.82f, h * 0.34f)
            }
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = w * 0.2f, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun IncomingTopicsBanner(
    count: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = Color(0x33007AFF),
                ambientColor = Color(0x22007AFF),
            )
            .clip(RoundedCornerShape(22.dp))
            .background(IosTheme.colors.card)
            .border(1.dp, IosTheme.colors.accent.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Canvas(modifier = Modifier.size(7.dp)) {
                drawCircle(color = Color(0xFF007AFF))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "有 $count 条新话题动态，点击查看 ↑",
                style = IosTheme.type.footnote,
                color = IosTheme.colors.accent,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun TopicNotificationLevelTag(
    level: TopicNotificationLevel,
    modifier: Modifier = Modifier,
) {
    if (level == TopicNotificationLevel.Regular) return
    val color = notificationLevelColor(level)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(
            text = level.badgeLabel,
            style = IosTheme.type.footnote,
            color = color,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

private fun notificationLevelColor(level: TopicNotificationLevel): Color = when (level) {
    TopicNotificationLevel.Watching -> Color(0xFF007AFF)
    TopicNotificationLevel.Tracking -> Color(0xFF34C759)
    TopicNotificationLevel.Regular -> Color(0xFF8E8E93)
    TopicNotificationLevel.Muted -> Color(0xFFFF3B30)
}

@Composable
internal fun TopicNotificationIcon(level: TopicNotificationLevel, modifier: Modifier = Modifier) {
    val color = notificationLevelColor(level)
    Canvas(modifier.size(18.dp)) {
        scale(size.width / 24f, size.height / 24f, Offset.Zero) {
            val bell = Path().apply {
                moveTo(5f, 17f)
                lineTo(7f, 14f)
                lineTo(7f, 9f)
                cubicTo(7f, 2.5f, 17f, 2.5f, 17f, 9f)
                lineTo(17f, 14f)
                lineTo(19f, 17f)
                close()
            }
            drawPath(bell, color, style = Stroke(1.7f, cap = StrokeCap.Round))
            drawLine(color, Offset(10f, 20f), Offset(14f, 20f), 1.7f, StrokeCap.Round)
            if (level == TopicNotificationLevel.Muted) {
                drawLine(color, Offset(3f, 3f), Offset(21f, 21f), 1.7f, StrokeCap.Round)
            } else if (level == TopicNotificationLevel.Watching) {
                drawCircle(color, 2f, Offset(20f, 4f))
            }
        }
    }
}

@Composable
private fun TopicFlagDialog(
    topic: Topic,
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (reasonId: Int, message: String?) -> Unit,
) {
    var selectedReason by remember(topic.id) { mutableStateOf(PostFlagReason.Spam) }
    var customMessage by rememberSaveable(topic.id) { mutableStateOf("") }

    Dialog(onDismissRequest = { if (!submitting) onDismiss() }) {
        Column(
            modifier = Modifier
                .widthIn(min = 280.dp, max = 360.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(IosTheme.colors.card)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "举报话题",
                style = IosTheme.type.headline,
                fontWeight = FontWeight.SemiBold,
                color = IosTheme.colors.label,
            )
            Text(
                topic.title,
                style = IosTheme.type.footnote,
                color = IosTheme.colors.secondaryLabel,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PostFlagReason.entries.forEach { reason ->
                    val isSelected = selectedReason == reason
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) IosTheme.colors.fieldBackground else Color.Transparent)
                            .clickable(enabled = !submitting) { selectedReason = reason }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .border(
                                    1.5.dp,
                                    if (isSelected) IosTheme.colors.accent else IosTheme.colors.tertiaryLabel,
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(IosTheme.colors.accent, CircleShape),
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(reason.title, style = IosTheme.type.subheadline, fontWeight = FontWeight.Medium)
                            Text(reason.description, style = IosTheme.type.caption, color = IosTheme.colors.secondaryLabel)
                        }
                    }
                }
            }

            if (selectedReason.requiresMessage) {
                BasicTextField(
                    value = customMessage,
                    enabled = !submitting,
                    onValueChange = { customMessage = it },
                    textStyle = IosTheme.type.subheadline.copy(color = IosTheme.colors.label),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(IosTheme.colors.fieldBackground)
                        .padding(8.dp),
                    decorationBox = { innerTextField ->
                        if (customMessage.isEmpty()) {
                            Text("请详细说明需要版主处理的问题...", style = IosTheme.type.subheadline, color = IosTheme.colors.tertiaryLabel)
                        }
                        innerTextField()
                    },
                )
            }
            error?.let { Text(it, style = IosTheme.type.footnote, color = IosTheme.colors.destructive) }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(IosTheme.colors.fieldBackground)
                        .clickable(enabled = !submitting, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("取消", style = IosTheme.type.subheadline, color = IosTheme.colors.secondaryLabel)
                }
                val canSubmit = !submitting && (!selectedReason.requiresMessage || customMessage.isNotBlank())
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (canSubmit) IosTheme.colors.accent else IosTheme.colors.accent.copy(alpha = 0.4f))
                        .clickable(enabled = canSubmit) {
                            onSubmit(selectedReason.id, customMessage.trim().takeIf { selectedReason.requiresMessage })
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (submitting) "提交中…" else "提交",
                        style = IosTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                }
            }
        }
    }
}

/**
 * 辅助悬浮按钮设计方案（单功能直达 / 多功能伞型自适应展开，支持自由拖拽与边缘吸附记忆）。
 */
@Composable
private fun FloatingAssistiveBall(
    actions: List<FloatingAction>,
    sortOrder: TopicSortOrder,
    sortAscending: Boolean,
    initialXRatio: Float,
    initialYRatio: Float,
    onPositionChange: (Float, Float) -> Unit,
    onActionClick: (FloatingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }

    val expandProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "expandProgress",
    )

    val isSingle = actions.size == 1

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }

        val mainBallSize = 52.dp
        val mainBallSizePx = with(density) { mainBallSize.toPx() }

        // 自由拖拽安全边界：避开最顶部状态栏（32dp）与底栏（72dp），四周留 6dp 防切边
        val minX = with(density) { 6.dp.toPx() }
        val maxX = (screenWidthPx - mainBallSizePx - with(density) { 6.dp.toPx() }).coerceAtLeast(minX)
        val minY = with(density) { 32.dp.toPx() }
        val maxY = (screenHeightPx - mainBallSizePx - with(density) { 72.dp.toPx() }).coerceAtLeast(minY)

        // 采用真实绝对物理像素坐标：手指拖到哪停在哪，完全消除相对基准点重置与强行吸附造成的乱跳
        var currentXPx by remember {
            mutableFloatStateOf(
                (minX + initialXRatio.coerceIn(0f, 1f) * (maxX - minX)).coerceIn(minX, maxX)
            )
        }
        var currentYPx by remember {
            mutableFloatStateOf(
                (minY + initialYRatio.coerceIn(0f, 1f) * (maxY - minY)).coerceIn(minY, maxY)
            )
        }

        val mainCenter = Offset(currentXPx + mainBallSizePx / 2f, currentYPx + mainBallSizePx / 2f)

        // 四象限智能空间判定：
        // 1. 水平方向：右半区朝左展开，左半区朝右展开
        val isRightSide = mainCenter.x >= screenWidthPx / 2f
        // 2. 垂直方向：上半区（上方不足 142dp+56dp）朝下展开，下半区朝上展开
        val needExpandDownward = mainCenter.y < (with(density) { 142.dp.toPx() } + with(density) { 56.dp.toPx() })

        // 静止淡出透明度：未交互时半透明（0.55f），触碰或展开时平滑升为 1.0f
        val idleAlpha by animateFloatAsState(
            targetValue = if (isExpanded || isDragging) 1f else 0.55f,
            animationSpec = tween(250),
            label = "fabIdleAlpha",
        )

        // 1. 全屏轻柔半透明蒙层（展开时淡出）
        if (expandProgress > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.26f * expandProgress))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { isExpanded = false },
                    ),
            )
        }

        // 2. 触控涟漪光环（展开时从主球周围扩散出的淡蓝水纹圆环，参照设计图右侧）
        if (expandProgress > 0.01f) {
            val outerRadiusPx = with(density) { 38.dp.toPx() }
            val innerRadiusPx = with(density) { 32.dp.toPx() }

            // 外层水纹圆环
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (mainCenter.x - outerRadiusPx).roundToInt(),
                            (mainCenter.y - outerRadiusPx).roundToInt(),
                        )
                    }
                    .size(76.dp)
                    .border(
                        width = 1.2.dp,
                        color = Color(0xFF2196F3).copy(alpha = 0.22f * expandProgress),
                        shape = CircleShape,
                    ),
            )
            // 内层水纹圆环
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (mainCenter.x - innerRadiusPx).roundToInt(),
                            (mainCenter.y - innerRadiusPx).roundToInt(),
                        )
                    }
                    .size(64.dp)
                    .border(
                        width = 1.4.dp,
                        color = Color(0xFF2196F3).copy(alpha = 0.38f * expandProgress),
                        shape = CircleShape,
                    ),
            )
        }

        // 3. 静止底晕（静止时主球周围有极浅的微蓝晕圈，柔和贴边，不突兀）
        if (expandProgress < 0.05f) {
            val haloRadiusPx = with(density) { 29.dp.toPx() }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (mainCenter.x - haloRadiusPx).roundToInt(),
                            (mainCenter.y - haloRadiusPx).roundToInt(),
                        )
                    }
                    .size(58.dp)
                    .graphicsLayer { alpha = idleAlpha }
                    .background(Color(0xFF2196F3).copy(alpha = 0.12f), CircleShape),
            )
        }

        // 4. 展开的子按钮群（四象限智能朝向：右上向左下，右下向左上，左上向右下，左下向右上）
        if (!isSingle && expandProgress > 0.01f) {
            val subRadiusPx = with(density) { 142.dp.toPx() } * expandProgress
            val subBallSize = 42.dp
            val subBallSizePx = with(density) { subBallSize.toPx() }
            val count = actions.size

            val startAngle = if (isRightSide) {
                if (needExpandDownward) 184.0 else 84.0
            } else {
                if (needExpandDownward) 356.0 else 96.0
            }
            val endAngle = if (isRightSide) {
                if (needExpandDownward) 266.0 else 176.0
            } else {
                if (needExpandDownward) 274.0 else 4.0
            }

            actions.forEachIndexed { i, action ->
                val angle = if (count <= 1) 0.0 else startAngle + i.toDouble() * (endAngle - startAngle) / (count - 1).toDouble()
                val rad = Math.toRadians(angle)
                val subX = mainCenter.x + (subRadiusPx * cos(rad)).toFloat()
                val subY = mainCenter.y - (subRadiusPx * sin(rad)).toFloat()

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (subX - subBallSizePx / 2f).roundToInt(),
                                (subY - subBallSizePx / 2f).roundToInt(),
                            )
                        }
                        .graphicsLayer {
                            alpha = expandProgress.coerceIn(0f, 1f)
                            scaleX = 0.35f + 0.65f * expandProgress
                            scaleY = 0.35f + 0.65f * expandProgress
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // 子按钮圆球
                    Box(
                        modifier = Modifier
                            .size(subBallSize)
                            .shadow(elevation = 3.dp, shape = CircleShape, clip = false)
                            .clip(CircleShape)
                            .background(floatingActionColor(action))
                            .border(1.2.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                isExpanded = false
                                onActionClick(action)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        FloatingActionIcon(action = action, tint = Color.White, modifier = Modifier.size(19.dp))
                    }

                    // 精致白底药丸标签（文字小巧上移，避免相互压盖与遮挡）
                    Box(
                        modifier = Modifier
                            .offset(y = 21.dp)
                            .shadow(elevation = 1.5.dp, shape = RoundedCornerShape(8.dp), clip = false)
                            .clip(RoundedCornerShape(8.dp))
                            .background(IosTheme.colors.card.copy(alpha = 0.96f))
                            .border(0.5.dp, IosTheme.colors.separator.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 5.5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = action.shortLabel,
                            color = IosTheme.colors.label,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        // 5. 主悬浮球（采用绝对像素平移，松手直接驻留目标坐标，支持全屏幕自由拖放）
        val ballRotation = expandProgress * 90f

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(currentXPx.roundToInt(), currentYPx.roundToInt())
                }
                .size(mainBallSize)
                .graphicsLayer {
                    alpha = idleAlpha
                }
                .shadow(elevation = if (isExpanded) 5.dp else 2.5.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .then(
                    if (isSingle) {
                        // 单功能直达：半透明浅色底 + 优雅微蓝细边，低调不突兀
                        Modifier
                            .background(IosTheme.colors.card.copy(alpha = 0.92f))
                            .border(1.dp, Color(0xFF2196F3).copy(alpha = 0.35f), CircleShape)
                    } else if (isExpanded) {
                        // 展开后主球：饱满纯净天蓝 + 纯白细边
                        Modifier
                            .background(Color(0xFF2196F3))
                            .border(1.2.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                    } else {
                        // 收缩静止态主球：半透明主题天蓝渐变底 + 柔和白边（完全融入内容流，不抢视线）
                        Modifier
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF42A5F5), Color(0xFF1E88E5)),
                                ),
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    }
                )
                .pointerInput(isSingle, actions) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isDragging = true
                        var totalDragDistance = 0f
                        val pointerId = down.id
                        var hasMoved = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (change.changedToUp()) {
                                change.consume()
                                isDragging = false
                                if (!hasMoved) {
                                    if (isSingle) {
                                        onActionClick(actions.first())
                                    } else {
                                        isExpanded = !isExpanded
                                    }
                                } else {
                                    val finalXRatio = if (maxX > minX) ((currentXPx - minX) / (maxX - minX)).coerceIn(0f, 1f) else 0.5f
                                    val finalYRatio = if (maxY > minY) ((currentYPx - minY) / (maxY - minY)).coerceIn(0f, 1f) else 0.5f
                                    onPositionChange(finalXRatio, finalYRatio)
                                }
                                break
                            }

                            val drag = change.positionChange()
                            if (drag != Offset.Zero) {
                                totalDragDistance += drag.getDistance()
                                if (totalDragDistance > 8f) {
                                    hasMoved = true
                                    change.consume()
                                    currentXPx = (currentXPx + drag.x).coerceIn(minX, maxX)
                                    currentYPx = (currentYPx + drag.y).coerceIn(minY, maxY)
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (isSingle) {
                // 单功能展示：图标 + 下方小字
                val singleAction = actions.first()
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    FloatingActionIcon(
                        action = singleAction,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = singleAction.shortLabel,
                        style = IosTheme.type.caption.copy(fontWeight = FontWeight.Medium, fontSize = 9.sp),
                        color = IosTheme.colors.label,
                    )
                }
            } else {
                // 多功能主球：旋转动画，三条横线 ≡ 平滑过渡为 ×
                Box(
                    modifier = Modifier.graphicsLayer {
                        rotationZ = ballRotation
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(20.dp)) {
                        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
                            if (expandProgress > 0.5f) {
                                // × 图标
                                drawLine(Color.White, Offset(6.5f, 6.5f), Offset(17.5f, 17.5f), strokeWidth = 2.4f, cap = StrokeCap.Round)
                                drawLine(Color.White, Offset(17.5f, 6.5f), Offset(6.5f, 17.5f), strokeWidth = 2.4f, cap = StrokeCap.Round)
                            } else {
                                // 三条横线 ≡（汉堡菜单，圆润纯白）
                                drawLine(Color.White, Offset(5f, 7f), Offset(19f, 7f), strokeWidth = 2.2f, cap = StrokeCap.Round)
                                drawLine(Color.White, Offset(5f, 12f), Offset(19f, 12f), strokeWidth = 2.2f, cap = StrokeCap.Round)
                                drawLine(Color.White, Offset(5f, 17f), Offset(19f, 17f), strokeWidth = 2.2f, cap = StrokeCap.Round)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 辅助悬浮按钮各功能的柔和雅致专属配色（低饱和度，对齐系统级设计图）。 */
private fun floatingActionColor(action: FloatingAction): Color = when (action) {
    FloatingAction.CreateTopic -> Color(0xFFF06A55) // 暖珊瑚西柚橙
    FloatingAction.Sort -> Color(0xFF2196F3)        // 纯净主题天蓝
    FloatingAction.Drafts -> Color(0xFF8C9BB0)      // 雅致灰紫蓝
    FloatingAction.Search -> Color(0xFF68A398)      // 柔和灰海青
    FloatingAction.Categories -> Color(0xFFEAA63B)  // 暖金琥珀
}

/** 辅助悬浮按钮各功能的纯矢量精致手绘图标。 */
@Composable
private fun FloatingActionIcon(
    action: FloatingAction,
    tint: Color,
    modifier: Modifier = Modifier.size(20.dp),
) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 2.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            when (action) {
                FloatingAction.CreateTopic -> {
                    // 铅笔 ✏️
                    val pen = Path().apply {
                        moveTo(4f, 20f)
                        lineTo(8.5f, 19f)
                        lineTo(19.5f, 8f)
                        lineTo(16f, 4.5f)
                        lineTo(5f, 15.5f)
                        close()
                    }
                    drawPath(pen, tint, style = stroke)
                    drawLine(tint, Offset(13.5f, 7f), Offset(17f, 10.5f), strokeWidth = 1.9f, cap = StrokeCap.Round)
                    drawLine(tint, Offset(12.5f, 20f), Offset(20f, 20f), strokeWidth = 1.9f, cap = StrokeCap.Round)
                }
                FloatingAction.Sort -> {
                    // 垂直双向箭头 ⇅
                    drawLine(tint, Offset(8f, 18.5f), Offset(8f, 5.5f), strokeWidth = 2.1f, cap = StrokeCap.Round)
                    val leftArrow = Path().apply {
                        moveTo(4.5f, 9.5f)
                        lineTo(8f, 5.5f)
                        lineTo(11.5f, 9.5f)
                    }
                    drawPath(leftArrow, tint, style = stroke)

                    drawLine(tint, Offset(16f, 5.5f), Offset(16f, 18.5f), strokeWidth = 2.1f, cap = StrokeCap.Round)
                    val rightArrow = Path().apply {
                        moveTo(12.5f, 14.5f)
                        lineTo(16f, 18.5f)
                        lineTo(19.5f, 14.5f)
                    }
                    drawPath(rightArrow, tint, style = stroke)
                }
                FloatingAction.Drafts -> {
                    // 文件夹 📁
                    val folder = Path().apply {
                        moveTo(3.5f, 7f)
                        lineTo(8.5f, 7f)
                        lineTo(10.5f, 9.5f)
                        lineTo(20.5f, 9.5f)
                        lineTo(20.5f, 18.5f)
                        lineTo(3.5f, 18.5f)
                        close()
                    }
                    drawPath(folder, tint, style = stroke)
                    drawLine(tint, Offset(3.5f, 10.5f), Offset(20.5f, 10.5f), strokeWidth = 1.6f, cap = StrokeCap.Round)
                }
                FloatingAction.Search -> {
                    // 放大镜 🔍
                    drawCircle(tint, 5.5f, Offset(10.5f, 10.5f), style = stroke)
                    drawLine(tint, Offset(14.8f, 14.8f), Offset(20f, 20f), strokeWidth = 2.3f, cap = StrokeCap.Round)
                }
                FloatingAction.Categories -> {
                    // 分类板块 🗂（4 个圆角小卡片）
                    val r = 1.5f
                    drawRoundRect(tint, Offset(4.5f, 4.5f), Size(6.5f, 6.5f), CornerRadius(r, r), style = stroke)
                    drawRoundRect(tint, Offset(13f, 4.5f), Size(6.5f, 6.5f), CornerRadius(r, r), style = stroke)
                    drawRoundRect(tint, Offset(4.5f, 13f), Size(6.5f, 6.5f), CornerRadius(r, r), style = stroke)
                    drawRoundRect(tint, Offset(13f, 13f), Size(6.5f, 6.5f), CornerRadius(r, r), style = stroke)
                }
            }
        }
    }
}


