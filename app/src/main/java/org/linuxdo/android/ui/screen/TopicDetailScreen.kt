package org.linuxdo.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import org.linuxdo.android.data.TopicNotificationLevel
import org.linuxdo.android.data.AttachedMedia
import org.linuxdo.android.data.AvatarCache
import org.linuxdo.android.ui.UnreadDotPosition
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import org.linuxdo.android.ui.RichMarkdownEditorView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.size.Size
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.net.URLDecoder
import org.jsoup.Jsoup
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.linuxdo.android.App
import org.linuxdo.android.Config
import org.linuxdo.android.data.Post
import org.linuxdo.android.data.Boost
import org.linuxdo.android.data.PostFlagReason
import org.linuxdo.android.data.effectiveReplyToPostNumber
import org.linuxdo.android.data.flatPostRows
import org.linuxdo.android.data.BoostLength
import org.linuxdo.android.data.UserCardData
import org.linuxdo.android.data.measureBoost
import org.linuxdo.android.data.avatarUrlOf
import org.linuxdo.android.data.TreePost
import org.linuxdo.android.data.DEFAULT_SUB_REPLY_LIMIT
import org.linuxdo.android.data.threadRows
import org.linuxdo.android.data.postAncestorIds
import org.linuxdo.android.ui.IosTextAction
import org.linuxdo.android.ui.ComposerTarget
import org.linuxdo.android.ui.ComposerUiState
import org.linuxdo.android.ui.DetailUiState
import org.linuxdo.android.ui.DetailLoad
import org.linuxdo.android.ui.design.*
import org.linuxdo.android.ui.format.RelativeTime
import org.linuxdo.android.ui.html.CookedBlock
import org.linuxdo.android.ui.html.CookedParser
import org.linuxdo.android.ui.html.InlineText
import org.linuxdo.android.net.IMAGE_AUTO_RETRY_LIMIT
import org.linuxdo.android.net.awaitImageRetrySlot
import org.linuxdo.android.net.isTransientImageError

private val LocalOpenTopic = compositionLocalOf<((Long, String, Int?) -> Unit)?> { null }
private val LocalSelectionResetKey = compositionLocalOf { 0 }
private val LocalOpenExternalUrl = compositionLocalOf<(String) -> Unit> { {} }
private val LocalVotePoll = compositionLocalOf<(Long, String, List<String>) -> Unit> { { _, _, _ -> } }
private val LocalPollBusy = compositionLocalOf<(Long) -> Boolean> { { false } }
private val LocalPollError = compositionLocalOf<(Long) -> String?> { { null } }
private val LocalDecryptSelection = compositionLocalOf<(String) -> Unit> { {} }
private val LocalTocTarget = compositionLocalOf<String?> { null }

private data class TocEntry(
    val postId: Long,
    val postNumber: Int,
    val level: Int,
    val title: String,
    val anchor: String,
)

private data class AuthorFilterItem(
    val userId: Long,
    val username: String,
    val avatarTemplate: String?,
    val isOp: Boolean,
    val postCount: Int,
)

private class CopyInterceptingTextToolbar(
    private val delegate: androidx.compose.ui.platform.TextToolbar,
    private val onCopied: () -> Unit,
) : androidx.compose.ui.platform.TextToolbar {
    override val status: TextToolbarStatus get() = delegate.status

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        delegate.showMenu(
            rect,
            onCopyRequested?.let { copy -> { copy(); onCopied() } },
            onPasteRequested,
            onCutRequested,
            onSelectAllRequested,
        )
    }

    override fun hide() = delegate.hide()
}

@Composable
private fun DecryptableSelection(content: @Composable () -> Unit) {
    val delegate = LocalTextToolbar.current
    val clipboard = LocalClipboardManager.current
    val onSelectionCopied = LocalDecryptSelection.current
    val toolbar = remember(delegate, clipboard, onSelectionCopied) {
        CopyInterceptingTextToolbar(delegate) {
            clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let(onSelectionCopied)
        }
    }
    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        SelectionContainer(content = content)
    }
}

private data class BoostTarget(
    val postId: Long,
    val postNumber: Int,
    val username: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicDetailScreen(
    title: String,
    state: DetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onLoadPrevious: () -> Unit,
    onUnknownTag: (String) -> Unit,
    treeView: Boolean,
    onLoadReplies: (Long) -> Unit,
    onReact: (Long, String) -> Unit,
    onBoost: (Long, String) -> Unit,
    onDeleteBoost: (Long, Long) -> Unit,
    onFlagBoost: (Long, Long, Int, String?) -> Unit = { _, _, _, _ -> },
    onOpenUser: (String, String?, String?, String?) -> Unit = { _, _, _, _ -> },
    onRefreshPost: (Long) -> Unit,
    onSolution: (Long) -> Unit,
    composer: ComposerUiState,
    onOpenComposer: (ComposerTarget) -> Unit,
    onDraftChange: (String) -> Unit,
    onSubmitComposer: () -> Unit,
    onCloseComposer: () -> Unit,
    onDeletePost: (Long) -> Unit,
    onReloadFromStart: () -> Unit,
    onCreatedPostShown: () -> Unit,
    onAttach: (Uri) -> Unit,
    topicId: Long,
    onRefresh: () -> Unit = {},
    onToggleOrder: () -> Unit = {},
    onOpenTopic: (Long, String, Int?) -> Unit = { _, _, _ -> },
    active: Boolean = true,
    signedIn: Boolean = false,
    showUnreadDot: Boolean = true,
    unreadDotPosition: UnreadDotPosition = UnreadDotPosition.Avatar,
    initialPostNumber: Int? = null,
    onPostsVisible: (List<Int>) -> Unit = {},
    onOverlayActive: (Boolean) -> Unit = {},
    onSetNotificationLevel: (TopicNotificationLevel) -> Unit = {},
    onToggleBookmark: (Post) -> Unit = {},
    onOpenFlagDialog: (Post) -> Unit = {},
    onCloseFlagDialog: () -> Unit = {},
    onSubmitFlag: (Long, Int, String?) -> Unit = { _, _, _ -> },
    onRemoveComposerAttachment: (String) -> Unit = {},
    onSetFilterUser: (Long?) -> Unit = {},
    onVotePoll: (Long, String, List<String>) -> Unit = { _, _, _ -> },
    onOpenExternalLink: (String) -> Unit = {},
    trailing: @Composable () -> Unit = {},
    blockedUsernames: Set<String> = emptySet(),
) {
    val list = rememberLazyListState()
    val pullState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    val textToolbar = LocalTextToolbar.current
    var selectionResetKey by remember { mutableIntStateOf(0) }
    var picture by remember { mutableStateOf<Pair<String, String>?>(null) }
    var collapsed by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var expanded by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var located by rememberSaveable(initialPostNumber, state.locateVersion) { mutableStateOf(initialPostNumber == null) }
    var highlightedPost by remember { mutableStateOf<Long?>(null) }
    var manageTarget by remember { mutableStateOf<Post?>(null) }
    var deleteTarget by remember { mutableStateOf<Post?>(null) }
    var boostTarget by remember { mutableStateOf<BoostTarget?>(null) }
    var boostDraft by rememberSaveable { mutableStateOf("") }
    var noticeToast by remember { mutableStateOf<String?>(null) }
    var authorMenuExpanded by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var tocTarget by remember { mutableStateOf<String?>(null) }
    var pendingTocJump by remember { mutableStateOf<TocEntry?>(null) }
    var decryptInput by remember { mutableStateOf<String?>(null) }
    var showDecryptSheet by remember { mutableStateOf(false) }
    var scannedCryptoResults by remember { mutableStateOf<List<DecodedResult>>(emptyList()) }
    var showCryptoPill by remember(topicId) { mutableStateOf(true) }
    LaunchedEffect(state.topic?.postStream?.posts, state.topic?.title) {
        val posts = state.topic?.postStream?.posts.orEmpty()
        val title = state.topic?.title
        if (posts.isNotEmpty()) {
            val results = withContext(Dispatchers.Default) {
                PostCryptoScanner.scanPosts(posts, title)
            }
            scannedCryptoResults = results
        }
    }
    var browserUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(noticeToast) {
        if (noticeToast != null) {
            delay(2000)
            noticeToast = null
        }
    }
    val clipboardManager = LocalClipboardManager.current
    val onCopyLinkAction: (Post) -> Unit = { p ->
        val url = "${Config.BASE_URL}/t/topic/$topicId/${p.postNumber}"
        clipboardManager.setText(AnnotatedString(url))
        noticeToast = "链接已复制到剪贴板"
    }
    val overlayActive = composer.open || boostTarget != null || picture != null ||
        manageTarget != null || deleteTarget != null || state.flagDialogPost != null
    LaunchedEffect(overlayActive) {
        onOverlayActive(overlayActive)
    }
    DisposableEffect(Unit) {
        onDispose { onOverlayActive(false) }
    }
    val topicAuthor = remember(state.posts) {
        state.posts.firstOrNull { it.postNumber == 1 }
    }
    val topicAuthorUsername = topicAuthor?.username
    val displayedPosts = remember(state.posts, state.filterUserId, blockedUsernames) {
        val filtered = state.filterUserId?.let { userId ->
            state.posts.filter { it.postNumber == 1 || it.userId == userId }
        } ?: state.posts
        if (blockedUsernames.isEmpty()) filtered
        else filtered.filterNot { post ->
            post.postNumber > 1 && post.username.lowercase() in blockedUsernames
        }
    }
    val rows = remember(displayedPosts, treeView, collapsed, expanded, state.descending) {
        if (treeView) {
            threadRows(displayedPosts, collapsed.toSet(), expanded.toSet(), descending = state.descending)
        } else {
            flatPostRows(displayedPosts, descending = state.descending)
        }
    }
    val tocEntries = remember(state.posts) {
        state.posts.sortedBy { it.postNumber }.flatMap { post ->
            fun headings(blocks: List<CookedBlock>): List<CookedBlock.Heading> = blocks.flatMap { block ->
                when (block) {
                    is CookedBlock.Heading -> listOf(block)
                    is CookedBlock.Quote -> headings(block.blocks)
                    is CookedBlock.ListBlock -> block.entries.flatMap(::headings)
                    else -> emptyList()
                }
            }
            headings(CookedParser.parsePostCached(post)).map { heading ->
                TocEntry(post.id, post.postNumber, heading.level, heading.title, heading.anchor)
            }
        }
    }
    val authorFilterItems = remember(state.posts, topicAuthorUsername) {
        val validPosts = state.posts.filter { it.userId != null }
        val counts = validPosts.groupingBy { it.userId!! }.eachCount()
        val firstMap = validPosts.associateBy { it.userId!! }
        counts.mapNotNull { (userId, count) ->
            val post = firstMap[userId] ?: return@mapNotNull null
            AuthorFilterItem(
                userId = userId,
                username = post.username,
                avatarTemplate = post.avatarTemplate,
                isOp = post.username == topicAuthorUsername,
                postCount = count,
            )
        }.sortedWith(
            compareByDescending<AuthorFilterItem> { it.isOp }
                .thenByDescending { it.postCount }
        )
    }
    val filterAuthors = remember(authorFilterItems) {
        authorFilterItems.map { it.userId to it.username }
    }
    LaunchedEffect(highlightedPost) {
        if (highlightedPost != null) {
            delay(2_000)
            highlightedPost = null
        }
    }
    val nearEnd by remember { derivedStateOf {
        (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= list.layoutInfo.totalItemsCount - 3
    } }
    LaunchedEffect(active, nearEnd, state.loading, state.consumedIds.size, state.error, located) {
        if (active && located && nearEnd && !state.loading && state.error == null &&
            state.posts.isNotEmpty() && state.remaining.isNotEmpty()) onLoadMore()
    }
    // 真实屏幕可见楼层提取（依据 Discourse screen-track.js 规范，仅上报在屏幕上可见的楼层号）
    val visiblePostNumbers by remember(rows) { derivedStateOf {
        val visibleIds = list.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? Long }.toSet()
        rows.filter { it.post.id in visibleIds }.map { it.post.postNumber }
    } }
    val currentPostNumber by remember(rows, visiblePostNumbers) { derivedStateOf {
        visiblePostNumbers.maxOrNull()
    } }
    val firstPostId = remember(rows) { rows.firstOrNull { it.post.postNumber == 1 }?.post?.id }
    val firstPostVisible by remember(list, firstPostId) {
        derivedStateOf { firstPostId != null && list.layoutInfo.visibleItemsInfo.any { it.key == firstPostId } }
    }
    LaunchedEffect(visiblePostNumbers) {
        if (visiblePostNumbers.isNotEmpty()) {
            onPostsVisible(visiblePostNumbers)
        }
    }
    // 本次打开期间已看过的楼层:仅本地内存,与阅读进度上报解耦,不产生任何请求。
    var sessionReadPosts by remember(topicId) { mutableStateOf(emptySet<Int>()) }
    LaunchedEffect(visiblePostNumbers, active, showUnreadDot) {
        if (!active || !showUnreadDot) return@LaunchedEffect
        val pending = visiblePostNumbers.filter { it !in sessionReadPosts }
        if (pending.isEmpty()) return@LaunchedEffect
        // 停留一小会儿才算读过,避免飞速滑过时蓝点一闪而没、用户根本没看清。
        delay(UNREAD_DOT_DWELL_MS)
        sessionReadPosts = sessionReadPosts + pending
    }
    val totalPosts = state.topic?.postStream?.stream?.size ?: 0
    val firstPostLoaded = state.posts.any { it.postNumber == 1 }
    val showTopError = !located || state.failedLoad == DetailLoad.Previous
    val canReply = state.topic?.canReply == true
    // 写操作全局互斥(ViewModel 侧也是),所以其他楼层的按钮要禁用;
    // 但转圈只画在真正被操作的那一条上,否则点一下"解决方案"整页都在转。
    val writeBusy = state.reactingPosts.isNotEmpty() || composer.submitting
    // 树形视图下,刚发的回复会挂在父楼层之下 —— 父楼层若是折叠或仅预览前3条,需自动展开以便直接看到新回复。
    var lastTarget by remember { mutableStateOf<ComposerTarget?>(null) }
    LaunchedEffect(composer.target) {
        val previous = lastTarget
        lastTarget = composer.target
        if (composer.target == null && previous is ComposerTarget.ReplyTo) {
            val ancestors = postAncestorIds(state.posts, previous.postNumber)
            val directParent = state.posts.firstOrNull { it.postNumber == previous.postNumber }?.id
            val targets = ancestors + setOfNotNull(directParent)
            collapsed = collapsed.filterNot { it in targets }
            expanded = (expanded + targets).distinct()
        }
    }
    // 1 楼在手上时就把它单独拆出来:它既要在前序缺口之上保持置顶,也是评论区头部栏的锚点。
    val opPinned = rows.firstOrNull()?.post?.postNumber == 1
    val headRows = if (opPinned) rows.take(1) else emptyList()
    val bodyRows = if (opPinned) rows.drop(1) else rows
    val replyTotal = (totalPosts - 1).coerceAtLeast(0)
    val showOrderBar = opPinned && (bodyRows.isNotEmpty() || replyTotal > 0)
    val topErrorCount = if (showTopError && state.error != null) 1 else 0

    // 1 楼（主帖）是话题的起点，物理上其上方绝无更早的可见楼层。
    // 若列表中已包含 1 楼，前序残留的 previousIds 仅为服务端历史流水中的被删帖或系统幽灵 ID。
    // 此时坚决收口关闭前序缺口，彻底避免因占位反复插入与重排引发的楼层横跳。
    // 从后序楼层跳进来且未加载到 1 楼时，hasHeadPost 为 false，缺口仍正常展示供向上回溯加载。
    val hasHeadPost = rows.any { it.post.postNumber == 1 }
    val showPreviousGap = state.previousIds.isNotEmpty() && !hasHeadPost

    fun lazyIndexOf(rowIndex: Int): Int {
        val afterHead = rowIndex >= headRows.size
        val orderBarBefore = if (showOrderBar && afterHead) 1 else 0
        val gapBefore = if (showPreviousGap && afterHead) 1 else 0
        return 1 + topErrorCount + orderBarBefore + gapBefore + rowIndex
    }

    LaunchedEffect(pendingTocJump, rows) {
        val target = pendingTocJump ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it.post.id == target.postId }
        if (index >= 0) {
            tocTarget = target.anchor
            list.animateScrollToItem(lazyIndexOf(index))
            withFrameNanos { }
        } else {
            // 目标楼层可能折叠着或还没加载进来。先尝试按楼层号就近定位，
            // 仍然找不到就放弃本次跳转——绝不把 pendingTocJump 留在原地，
            // 否则抽屉不关、界面看起来像卡死。
            val byNumber = rows.indexOfFirst { it.post.postNumber == target.postNumber }
            if (byNumber >= 0) {
                tocTarget = target.anchor
                list.animateScrollToItem(lazyIndexOf(byNumber))
                withFrameNanos { }
            }
        }
        pendingTocJump = null
        showToc = false
    }

    val onJumpToPostAction: (Int) -> Unit = { targetNum ->
        val targetIndex = rows.indexOfFirst { it.post.postNumber == targetNum }
        if (targetIndex >= 0) {
            val targetId = rows[targetIndex].post.id
            scope.launch {
                list.animateScrollToItem(lazyIndexOf(targetIndex))
                highlightedPost = targetId
            }
        }
    }

    // 切换正/倒序后把视口带回评论区头部,否则用户会停在原先的滚动偏移上、看不出顺序变了。
    var lastDescending by remember { mutableStateOf(state.descending) }
    LaunchedEffect(state.descending, state.loading, rows) {
        if (lastDescending == state.descending || state.loading) return@LaunchedEffect
        lastDescending = state.descending
        if (!showOrderBar) return@LaunchedEffect
        // 定位尚未完成时，不允许倒序切换滚动覆盖初始定位位置
        if (!located) return@LaunchedEffect
        list.scrollToItem(1 + topErrorCount + headRows.size)
    }

    LaunchedEffect(active, initialPostNumber, state.posts, rows, treeView, state.loading, state.error, located, state.previousIds.isNotEmpty(), state.locateVersion) {
        if (active && !located && !state.loading && state.error == null && initialPostNumber != null) {
            // 目标楼层可能已被删除(抽楼)或超出当前加载范围。此时不能直接 return，
            // 否则 located 永远为 false，会连带锁死倒序切换等依赖它的逻辑。
            // 降级为「第一个不小于目标楼层的可见楼层」，再不行就按帖子顺序找最近的一层。
            val target = state.posts.firstOrNull { it.postNumber == initialPostNumber }
                ?: state.posts.firstOrNull { it.postNumber >= initialPostNumber }
                ?: state.posts.minByOrNull { kotlin.math.abs(it.postNumber - initialPostNumber) }
                ?: run { located = true; return@LaunchedEffect }
            val anchorPostNumber = target.postNumber
            if (treeView) {
                val ancestors = postAncestorIds(state.posts, anchorPostNumber)
                val needUncollapse = collapsed.any { it in ancestors }
                val needExpand = ancestors.any { it !in expanded }
                if (needUncollapse || needExpand) {
                    if (needUncollapse) collapsed = collapsed.filterNot { it in ancestors }
                    if (needExpand) expanded = (expanded + ancestors).distinct()
                    // 关键修复：不再 return，等待两帧让 rows 因 collapsed/expanded 变化而重新计算
                    withFrameNanos { }
                    withFrameNanos { }
                }
            }
            // 展开或无需展开，继续执行滚动定位（不论是否展开过父楼层）
            val index = rows.indexOfFirst { it.post.postNumber == anchorPostNumber }
            if (index >= 0) {
                list.scrollToItem(lazyIndexOf(index))
                withFrameNanos { }
                if (list.layoutInfo.visibleItemsInfo.none { it.key == target.id }) {
                    list.scrollToItem(lazyIndexOf(index))
                }
                highlightedPost = target.id
            }
            // 无论是否滚动成功都终结定位，避免状态悬挂。
            located = true
        }
    }

    // 向上回填前序切片时锁定当前首个可见楼层锚点，新楼层前置插入后在同一帧恢复视口位置，防止跳屏。
    var upwardAnchor by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    var upwardPrevRowCount by remember { mutableIntStateOf(0) }
    val triggerLoadPrevious = {
        // 已包含 1 楼时不存在"更早的可见楼层"，前序仅剩被删除/占位的幽灵 id。
        // 再触发只会让列表围绕锚点重排，产生顶部反复跳动。
        if (!hasHeadPost && !state.loading && state.previousIds.isNotEmpty()) {
            val firstPostId = rows.firstOrNull { it.post.postNumber == 1 }?.post?.id
            val anchorItem = list.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                val key = info.key as? Long ?: return@firstOrNull false
                key != firstPostId
            }
            if (anchorItem != null) {
                upwardAnchor = (anchorItem.key as Long) to anchorItem.offset
                upwardPrevRowCount = rows.size
            }
            onLoadPrevious()
        }
    }
    LaunchedEffect(rows) {
        val anchor = upwardAnchor ?: return@LaunchedEffect
        if (rows.size > upwardPrevRowCount) {
            val idx = rows.indexOfFirst { it.post.id == anchor.first }
            if (idx >= 0) {
                list.scrollToItem(lazyIndexOf(idx), (-anchor.second).coerceAtLeast(0))
            }
            upwardAnchor = null
        }
    }
    val nearGapFromBelow by remember(rows, state.previousIds.size, showPreviousGap) {
        derivedStateOf {
            if (!showPreviousGap) return@derivedStateOf false
            val info = list.layoutInfo.visibleItemsInfo
            val hasGap = info.any { it.key == "load-previous" }
            val firstPostId = rows.firstOrNull { it.post.postNumber == 1 }?.post?.id
            val firstPostAtTop = firstPostId != null && info.firstOrNull()?.key == firstPostId
            hasGap && !firstPostAtTop && list.isScrollInProgress
        }
    }
    LaunchedEffect(active, nearGapFromBelow, state.loading, state.previousIds.size, state.error) {
        if (active && nearGapFromBelow && showPreviousGap && !state.loading && state.error == null && state.previousIds.isNotEmpty()) {
            triggerLoadPrevious()
        }
    }

    // 发完回复一步到位直接定格在新楼层并高亮（与 Web 端渲染完直达底部体验一致，不做长距离逐层滚动动画）。
    LaunchedEffect(state.createdPostId, state.posts, rows, treeView) {
        val created = state.createdPostId ?: return@LaunchedEffect
        val createdPost = state.posts.firstOrNull { it.id == created }
        if (treeView && createdPost != null) {
            val ancestors = postAncestorIds(state.posts, createdPost.postNumber)
            val needUncollapse = collapsed.any { it in ancestors }
            val needExpand = ancestors.any { it !in expanded }
            if (needUncollapse || needExpand) {
                if (needUncollapse) collapsed = collapsed.filterNot { it in ancestors }
                if (needExpand) expanded = (expanded + ancestors).distinct()
                return@LaunchedEffect
            }
        }
        val index = rows.indexOfFirst { it.post.id == created }
        if (index < 0) return@LaunchedEffect
        highlightedPost = created
        try {
            list.scrollToItem(lazyIndexOf(index))
        } finally {
            onCreatedPostShown()
        }
    }

    @Composable
    fun RenderPostEntry(row: TreePost) {
        val post = row.post
        val isTopicAuthor = topicAuthorUsername != null && post.username == topicAuthorUsername
        val unreadDotVisible = showUnreadDot && signedIn && !post.yours && !post.read &&
            post.postNumber !in sessionReadPosts
        if (!treeView || row.depth == 0) {
            val hasExpandedSubtree = treeView && row.subtreeSize > 0 && post.id !in collapsed
            val unloadedTargetId = row.unloadedCandidateIds.firstOrNull { it !in state.completedReplies }
            val hasUnloadedRootReplies = treeView && unloadedTargetId != null
            val showCollapsedSubtreeBar = treeView && !hasExpandedSubtree && (row.allLoadedCount > 0 || hasUnloadedRootReplies)
            Column(Modifier.fillMaxWidth().background(IosTheme.colors.card)) {
                PostRow(
                    post = post,
                    isTopicAuthor = isTopicAuthor,
                    compactBottom = hasExpandedSubtree || showCollapsedSubtreeBar,
                    showReplyToHint = !treeView,
                    unreadDotVisible = unreadDotVisible,
                    unreadDotPosition = unreadDotPosition,
                    onPicture = { orig, thumb -> picture = orig to thumb },
                    onUnknownTag = onUnknownTag,
                    reactions = state.topic?.validReactions.orEmpty(),
                    emojiUrls = state.emojiUrls,
                    busy = writeBusy,
                    pending = post.id in state.reactingPosts,
                    error = state.reactionErrors[post.id],
                    onReact = { onReact(post.id, it) },
                    onOpenBoost = { boostTarget = BoostTarget(post.id, post.postNumber, post.username) },
                    onDeleteBoost = { onDeleteBoost(post.id, it) },
                    onFlagBoost = { boostId, reasonId, message -> onFlagBoost(post.id, boostId, reasonId, message) },
                    onOpenUser = onOpenUser,
                    onRefresh = { onRefreshPost(post.id) },
                    onSolution = { onSolution(post.id) },
                    highlighted = highlightedPost == post.id,
                    canReply = canReply,
                    onReply = { onOpenComposer(ComposerTarget.ReplyTo(topicId, post.postNumber, post.username)) },
                    onManage = { manageTarget = post },
                    replyToUsername = row.replyToUsername,
                    onJumpToPost = onJumpToPostAction.takeUnless { row.parentMissing },
                    signedIn = signedIn,
                    canFlag = !post.yours && post.id !in state.flaggedPosts,
                    onToggleBookmark = { onToggleBookmark(post) },
                    onCopyLink = { onCopyLinkAction(post) },
                    onFlag = { onOpenFlagDialog(post) },
                    onEdit = { onOpenComposer(ComposerTarget.Edit(topicId, post.id, post.postNumber)) },
                    onDelete = { deleteTarget = post },
                )
                if (showCollapsedSubtreeBar) {
                    val replyLoading = row.unloadedCandidateIds.any { it in state.loadingReplies }
                    val totalHint = maxOf(row.totalSubtreeCount, row.allLoadedCount, post.replyCount, row.childCount)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(IosTheme.colors.fieldBackground)
                            .clickable(enabled = !replyLoading) {
                                collapsed = collapsed - post.id
                                if (row.allLoadedCount > DEFAULT_SUB_REPLY_LIMIT || unloadedTargetId != null) {
                                    expanded = (expanded + post.id).distinct()
                                }
                                if (unloadedTargetId != null) {
                                    collapsed = collapsed - unloadedTargetId
                                    onLoadReplies(unloadedTargetId)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    ) {
                        Text(
                            text = if (replyLoading) "正在加载回复…" else "查看全部 $totalHint 条回复 >",
                            style = IosTheme.type.footnote,
                            fontWeight = FontWeight.Medium,
                            color = IosTheme.colors.accent,
                        )
                    }
                }
                if (!hasExpandedSubtree) {
                    IosDivider()
                }
            }
        } else {
            // ── 贴吧式“楼中楼”子回复区域（默认最多展示前 3 条预览，点击展开全部，与主评论同宽） ──
            val selfHasMore = post.replyCount > row.childCount && post.id !in state.completedReplies
            val isRootExpanded = row.rootPostId in expanded
            val topCorner = if (row.isFirstInSubtree) 10.dp else 0.dp
            val boxShape = RoundedCornerShape(
                topStart = topCorner,
                topEnd = topCorner,
                bottomStart = 0.dp,
                bottomEnd = 0.dp,
            )
            Column(Modifier.fillMaxWidth().background(IosTheme.colors.card)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(boxShape)
                        .background(IosTheme.colors.fieldBackground),
                ) {
                    if (!row.isFirstInSubtree) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .height(0.5.dp)
                                .background(IosTheme.colors.separator.copy(alpha = 0.6f)),
                        )
                    }
                    SubReplyRow(
                        row = row,
                        isTopicAuthor = isTopicAuthor,
                        unreadDotVisible = unreadDotVisible,
                        unreadDotPosition = unreadDotPosition,
                        onPicture = { orig, thumb -> picture = orig to thumb },
                        onUnknownTag = onUnknownTag,
                        reactions = state.topic?.validReactions.orEmpty(),
                        emojiUrls = state.emojiUrls,
                        busy = writeBusy,
                        pending = post.id in state.reactingPosts,
                        error = state.reactionErrors[post.id],
                        onReact = { onReact(post.id, it) },
                        onOpenBoost = { boostTarget = BoostTarget(post.id, post.postNumber, post.username) },
                        onDeleteBoost = { onDeleteBoost(post.id, it) },
                        onFlagBoost = { boostId, reasonId, message -> onFlagBoost(post.id, boostId, reasonId, message) },
                        onOpenUser = onOpenUser,
                        onRefresh = { onRefreshPost(post.id) },
                        highlighted = highlightedPost == post.id,
                        canReply = canReply,
                        onReply = { onOpenComposer(ComposerTarget.ReplyTo(topicId, post.postNumber, post.username)) },
                        onManage = { manageTarget = post },
                        onToggleBookmark = { onToggleBookmark(post) },
                        signedIn = signedIn,
                        canFlag = !post.yours && post.id !in state.flaggedPosts,
                        onCopyLink = { onCopyLinkAction(post) },
                        onFlag = { onOpenFlagDialog(post) },
                        onEdit = { onOpenComposer(ComposerTarget.Edit(topicId, post.id, post.postNumber)) },
                        onDelete = { deleteTarget = post },
                        selfHasMore = selfHasMore && !row.isLastInSubtree && isRootExpanded,
                        selfLoadingReplies = post.id in state.loadingReplies,
                        onLoadSelfReplies = {
                            expanded = (expanded + row.rootPostId).distinct()
                            collapsed = collapsed - post.id
                            onLoadReplies(post.id)
                        },
                    )
                }
                if (row.isLastInSubtree) {
                    val moreTargetId = row.unloadedCandidateIds.firstOrNull { it !in state.completedReplies }
                    val moreLoading = row.unloadedCandidateIds.any { it in state.loadingReplies }
                    val hasHiddenLoaded = row.allLoadedCount > row.subtreeSize
                    val canExpandMore = hasHiddenLoaded || moreTargetId != null
                    val totalReplies = maxOf(row.totalSubtreeCount, row.allLoadedCount, row.subtreeSize)
                    val showCollapseButton = isRootExpanded || !canExpandMore
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                            .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                            .background(IosTheme.colors.fieldBackground)
                            .then(
                                if (canExpandMore && !showCollapseButton) {
                                    Modifier.clickable(enabled = !moreLoading) {
                                        expanded = (expanded + row.rootPostId).distinct()
                                        collapsed = collapsed - row.rootPostId
                                        if (moreTargetId != null) {
                                            collapsed = collapsed - moreTargetId
                                            onLoadReplies(moreTargetId)
                                        }
                                    }
                                } else {
                                    Modifier
                                },
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (canExpandMore) {
                            Text(
                                text = if (moreLoading) "正在加载回复…" else "查看全部 $totalReplies 条回复 >",
                                style = IosTheme.type.footnote,
                                fontWeight = FontWeight.Medium,
                                color = IosTheme.colors.accent,
                                modifier = if (showCollapseButton) {
                                    Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable(enabled = !moreLoading) {
                                            expanded = (expanded + row.rootPostId).distinct()
                                            collapsed = collapsed - row.rootPostId
                                            if (moreTargetId != null) {
                                                collapsed = collapsed - moreTargetId
                                                onLoadReplies(moreTargetId)
                                            }
                                        }
                                        .padding(vertical = 2.dp)
                                } else {
                                    Modifier.padding(vertical = 2.dp)
                                },
                            )
                        } else {
                            Text(
                                text = "共 $totalReplies 条回复",
                                style = IosTheme.type.caption,
                                color = IosTheme.colors.secondaryLabel,
                            )
                        }
                        if (showCollapseButton) {
                            Text(
                                text = "收起回复",
                                style = IosTheme.type.footnote,
                                color = IosTheme.colors.accent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        val rootId = row.rootPostId
                                        val visibleRoot = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == rootId }
                                        val rootRowIndex = rows.indexOfFirst { it.post.id == rootId }
                                        val needScrollToRoot = visibleRoot == null || visibleRoot.offset < 0
                                        if (row.allLoadedCount > DEFAULT_SUB_REPLY_LIMIT && rootId in expanded) {
                                            expanded = expanded - rootId
                                        } else {
                                            expanded = expanded - rootId
                                            collapsed = (collapsed + rootId).distinct()
                                        }
                                        if (needScrollToRoot && rootRowIndex >= 0) {
                                            val targetLazyIndex = lazyIndexOf(rootRowIndex)
                                            scope.launch {
                                                list.scrollToItem(targetLazyIndex, 0)
                                            }
                                        }
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                        }
                    }
                    IosDivider()
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalOpenTopic provides onOpenTopic,
        LocalSelectionResetKey provides selectionResetKey,
        LocalOpenExternalUrl provides { browserUrl = it },
        LocalVotePoll provides onVotePoll,
        LocalPollBusy provides { postId -> postId in state.reactingPosts },
        LocalPollError provides { postId -> state.reactionErrors[postId] },
        LocalDecryptSelection provides { selected ->
            val text = selected.trim()
            if (text.isNotBlank()) {
                decryptInput = text
                showDecryptSheet = true
            }
        },
        LocalTocTarget provides tocTarget,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(textToolbar) {
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial)
                        val downPos = down.position
                        val downTime = System.currentTimeMillis()
                        var moved = false

                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                val elapsed = System.currentTimeMillis() - downTime
                                if (!moved && elapsed < 400L) {
                                    if (textToolbar.status == TextToolbarStatus.Shown) {
                                        textToolbar.hide()
                                        selectionResetKey++
                                    }
                                }
                                break
                            } else {
                                val dx = change.position.x - downPos.x
                                val dy = change.position.y - downPos.y
                                if (dx * dx + dy * dy > 400f || event.changes.size > 1) {
                                    moved = true
                                }
                            }
                        }
                    }
                }
        ) {
            Column(Modifier.fillMaxSize().background(IosTheme.colors.groupedBackground)) {
                IosNavBar(title = state.topic?.title ?: title, onBack = onBack, trailing = trailing)
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = onRefresh,
                    state = pullState,
                    modifier = Modifier.weight(1f),
                    indicator = {
                        val fraction = pullState.distanceFraction
                        if (state.refreshing || fraction > 0f) {
                            Box(
                                Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 12.dp)
                                    .graphicsLayer {
                                        alpha = if (state.refreshing) 1f else min(1f, fraction * 1.4f)
                                    },
                            ) {
                                IosActivityIndicator(diameter = 22.dp)
                            }
                        }
                    },
                ) {
                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = list,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 76.dp),
                        ) {
                            item("title") {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(state.topic?.title ?: title, style = IosTheme.type.title3)
                                    // ── 药丸工具栏（分类固定靠左，功能按键整体靠右，不散漫且防误触） ──
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // 1. 分类标签（位置不动，稳定居左）
                                        state.category?.let { CategoryTag(it) }

                                        // 弹性占位：将后续功能按键组整体推向右端
                                        Spacer(Modifier.weight(1f))

                                        // 2. 功能按键组（整体靠右聚集，间距适度不散，热区舒适防误触）
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            // 目录药丸（仅当真正有目录时动态出现，保证绝对不重叠）
                                            if (tocEntries.isNotEmpty()) {
                                                Row(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(if (showToc) Color(0xFFEDE9FE) else Color(0xFFF5F3FF))
                                                        .then(if (showToc) Modifier.border(1.2.dp, Color(0xFF7E57C2), RoundedCornerShape(12.dp)) else Modifier)
                                                        .clickable {
                                                            showToc = !showToc
                                                            authorMenuExpanded = false
                                                        }
                                                        .padding(horizontal = 9.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                ) {
                                                    Text("📖", fontSize = 11.sp)
                                                    Text(
                                                        text = if (showToc) "目录 ▴" else "目录 ▾",
                                                        style = IosTheme.type.footnote,
                                                        color = Color(0xFF7E57C2),
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                    )
                                                }
                                            }

                                            // 解密药丸
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(if (scannedCryptoResults.isNotEmpty()) IosTheme.colors.accent.copy(alpha = 0.12f) else Color(0xFFFFF1F0))
                                                    .clickable { showDecryptSheet = true }
                                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            ) {
                                                Text("🔑", fontSize = 11.sp)
                                                Text(
                                                    text = if (scannedCryptoResults.isNotEmpty()) "解密 (${scannedCryptoResults.size})" else "解密",
                                                    style = IosTheme.type.footnote,
                                                    color = if (scannedCryptoResults.isNotEmpty()) IosTheme.colors.accent else Color(0xFFE53935),
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                )
                                            }

                                            // 筛选药丸 (展开态与筛选态高亮)
                                            if (authorFilterItems.isNotEmpty()) {
                                                val isFiltered = state.filterUserId != null
                                                val pillBg = when {
                                                    authorMenuExpanded -> Color(0xFFE0F2FE)
                                                    isFiltered -> IosTheme.colors.accent.copy(alpha = 0.12f)
                                                    IosTheme.colors.isDark -> Color(0xFF2C2C2E)
                                                    else -> IosTheme.colors.fieldBackground
                                                }
                                                val pillTextColor = when {
                                                    authorMenuExpanded || isFiltered -> Color(0xFF0284C7)
                                                    IosTheme.colors.isDark -> Color(0xFFE5E5EA)
                                                    else -> IosTheme.colors.label
                                                }
                                                Row(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(pillBg)
                                                        .then(if (authorMenuExpanded) Modifier.border(1.5.dp, Color(0xFF0284C7), RoundedCornerShape(12.dp)) else Modifier)
                                                        .clickable {
                                                            authorMenuExpanded = !authorMenuExpanded
                                                            showToc = false
                                                        }
                                                        .padding(horizontal = 9.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                ) {
                                                    val selectedName = authorFilterItems.firstOrNull { it.userId == state.filterUserId }?.username
                                                    Text(
                                                        text = when {
                                                            selectedName != null -> "只看 $selectedName ${if (authorMenuExpanded) "▴" else "▾"}"
                                                            authorMenuExpanded -> "筛选 ▴"
                                                            else -> "筛选 ▾"
                                                        },
                                                        style = IosTheme.type.footnote,
                                                        color = pillTextColor,
                                                        fontWeight = if (authorMenuExpanded || isFiltered) FontWeight.SemiBold else FontWeight.Medium,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                    )
                                                }
                                            }

                                            // 关注/通知级别药丸
                                            TopicNotificationSelector(
                                                currentLevel = state.notificationLevel,
                                                onSelectLevel = onSetNotificationLevel,
                                                enabled = signedIn && state.topic != null && !state.notificationLevelUpdating,
                                            )
                                        }
                                    }
                                }
                            }
                            if (showTopError) state.error?.let { message ->
                                item("top-error") { ErrorNotice(message, onRetry) }
                            }
                            items(headRows, key = { it.post.id }) { row -> RenderPostEntry(row) }
                            if (showOrderBar) item("order-bar") {
                                CommentOrderBar(
                                    total = replyTotal,
                                    descending = state.descending,
                                    busy = state.loading || state.refreshing,
                                    onToggle = onToggleOrder,
                                )
                            }
                            if (showPreviousGap) item("load-previous") {
                                PreviousSliceGap(
                                    hiddenCount = state.previousIds.size,
                                    loading = state.loading,
                                    onClick = triggerLoadPrevious,
                                )
                            }
                            items(bodyRows, key = { it.post.id }) { row -> RenderPostEntry(row) }
                            if (state.loading && (state.posts.isEmpty() || state.remaining.isNotEmpty())) {
                                item("loading") { IosLoadingBox() }
                            }
                            if (!showTopError) state.error?.let { message -> item("error") { ErrorNotice(message, onRetry) } }
                            if (!state.loading && state.topic != null && state.posts.isEmpty() && state.error == null) {
                                item("empty") { EmptyNotice() }
                            }
                            if (state.posts.isNotEmpty() && state.remaining.isEmpty() && !state.loading && state.error == null) {
                                item("end") {
                                    Text(
                                        text = when {
                                            state.descending -> "已显示到最早的回复"
                                            state.previousIds.isEmpty() -> "已显示全部可见楼层"
                                            else -> "已到话题末尾"
                                        },
                                        color = IosTheme.colors.secondaryLabel,
                                        style = IosTheme.type.footnote,
                                        modifier = Modifier.padding(20.dp),
                                    )
                                }
                            }
                        }
                        // 左下角放阅读进度胶囊，右下角放回复按钮，配合列表底部 76dp 留白，彻底避免遮挡最后一条评论的操作栏。
                        if (currentPostNumber != null && currentPostNumber!! > 20 && totalPosts > 20 && !firstPostVisible) {
                            ReadingProgressPill(
                                current = currentPostNumber ?: 1,
                                total = totalPosts,
                                modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 16.dp),
                            ) {
                                if (firstPostLoaded) scope.launch { list.animateScrollToItem(0) } else onReloadFromStart()
                            }
                        }
                        if (canReply) {
                            Box(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 16.dp)) {
                                ReplyFab(enabled = !writeBusy) {
                                    onOpenComposer(ComposerTarget.NewReply(topicId))
                                }
                            }
                        }
                        (noticeToast ?: state.noticeMessage)?.let { notice ->
                            Text(
                                notice,
                                style = IosTheme.type.footnote,
                                color = Color(0xFF30B85B),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.align(Alignment.BottomCenter)
                                    .padding(horizontal = 16.dp, vertical = 72.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(IosTheme.colors.card)
                                    .border(1.dp, Color(0xFF34C759).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                        if (scannedCryptoResults.isNotEmpty() && showCryptoPill) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(horizontal = 16.dp, vertical = 72.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(IosTheme.colors.card.copy(alpha = 0.95f))
                                    .border(1.dp, IosTheme.colors.accent.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                                    .clickable { showDecryptSheet = true }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("🔍", style = IosTheme.type.footnote)
                                Text(
                                    "发现 ${scannedCryptoResults.size} 处加密内容",
                                    style = IosTheme.type.footnote,
                                    color = IosTheme.colors.accent,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    "✕",
                                    style = IosTheme.type.caption,
                                    color = IosTheme.colors.secondaryLabel,
                                    modifier = Modifier
                                        .clickable { showCryptoPill = false }
                                        .padding(start = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (composer.open) {
                ComposerSheet(
                    composer = composer,
                    onDraftChange = onDraftChange,
                    onSubmit = onSubmitComposer,
                    onClose = onCloseComposer,
                    onAttach = onAttach,
                    onRemoveAttachment = onRemoveComposerAttachment,
                )
            }
            boostTarget?.let { target ->
                BoostSheet(
                    target = target,
                    draft = boostDraft,
                    onDraftChange = { boostDraft = it },
                    busy = writeBusy,
                    onSend = {
                        val toSend = boostDraft
                        boostTarget = null
                        boostDraft = ""
                        onBoost(target.postId, toSend)
                    },
                    onDismiss = {
                        boostTarget = null
                        boostDraft = ""
                    },
                )
            }
        }
    }
    picture?.let { (orig, thumb) -> ImageViewer(orig, thumb) { picture = null } }
    browserUrl?.let { url ->
        InAppBrowserSheet(url = url, onDismiss = { browserUrl = null })
    }
    if (showDecryptSheet || decryptInput != null) {
        DecryptBottomSheet(
            selectedText = decryptInput,
            scannedResults = scannedCryptoResults,
            onOpenUrl = { url ->
                showDecryptSheet = false
                decryptInput = null
                browserUrl = url
            },
            onDismiss = {
                showDecryptSheet = false
                decryptInput = null
            },
        )
    }
    if (showToc) {
        Dialog(
            onDismissRequest = { showToc = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = { showToc = false },
                    )
                    .padding(top = 96.dp, start = 20.dp, end = 20.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .fillMaxWidth()
                        .shadow(elevation = 16.dp, shape = RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(IosTheme.colors.card)
                        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    // Header: 文章目录 + 关闭 ✕
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "文章目录",
                            style = IosTheme.type.headline,
                            fontWeight = FontWeight.Bold,
                            color = IosTheme.colors.label,
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable { showToc = false },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("✕", fontSize = 14.sp, color = IosTheme.colors.secondaryLabel)
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    IosDivider()
                    Spacer(Modifier.height(4.dp))

                    // 目录项列表
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        tocEntries.forEachIndexed { index, entry ->
                            val isCurrent = entry.anchor == tocTarget || (pendingTocJump == null && index == 0)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onSetFilterUser(null)
                                        pendingTocJump = entry
                                        showToc = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (isCurrent) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(IosTheme.colors.accent, CircleShape),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    text = "${index + 1}. ${entry.title}",
                                    style = IosTheme.type.body.copy(fontSize = 14.sp),
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) IosTheme.colors.accent else IosTheme.colors.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "→",
                                    fontSize = 13.sp,
                                    color = IosTheme.colors.tertiaryLabel,
                                )
                            }
                            if (index < tocEntries.lastIndex) {
                                IosDivider(modifier = Modifier.padding(vertical = 1.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    if (authorMenuExpanded) {
        Dialog(
            onDismissRequest = { authorMenuExpanded = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = { authorMenuExpanded = false },
                    )
                    .padding(top = 96.dp, start = 20.dp, end = 20.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .fillMaxWidth()
                        .shadow(elevation = 16.dp, shape = RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(IosTheme.colors.card)
                        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    // Header: 筛选发言者 + 关闭 ✕
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "筛选发言者",
                            style = IosTheme.type.headline,
                            fontWeight = FontWeight.Bold,
                            color = IosTheme.colors.label,
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable { authorMenuExpanded = false },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("✕", fontSize = 14.sp, color = IosTheme.colors.secondaryLabel)
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    IosDivider()
                    Spacer(Modifier.height(4.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        // 首项: 全部作者
                        val isAllSelected = state.filterUserId == null
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    authorMenuExpanded = false
                                    onSetFilterUser(null)
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(IosTheme.colors.fieldBackground),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("👥", fontSize = 13.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "全部楼层",
                                style = IosTheme.type.body.copy(fontSize = 14.sp),
                                fontWeight = if (isAllSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = IosTheme.colors.label,
                                modifier = Modifier.weight(1f),
                            )
                            if (isAllSelected) {
                                Text("✓", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = IosTheme.colors.accent)
                            }
                        }

                        IosDivider(modifier = Modifier.padding(vertical = 1.dp))

                        // 各发言者项
                        authorFilterItems.forEachIndexed { idx, item ->
                            val isSelected = state.filterUserId == item.userId
                            val avatarUrl = item.avatarTemplate?.let { org.linuxdo.android.data.avatarUrlOf(it, size = 96) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        authorMenuExpanded = false
                                        onSetFilterUser(item.userId)
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // 头像
                                if (!avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = item.username,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(IosTheme.colors.fieldBackground),
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFE1F0FF)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = item.username.firstOrNull()?.uppercase() ?: "U",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = IosTheme.colors.accent,
                                        )
                                    }
                                }

                                Spacer(Modifier.width(10.dp))

                                // 用户名与楼主标识
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = item.username,
                                        style = IosTheme.type.body.copy(fontSize = 14.sp),
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) IosTheme.colors.accent else IosTheme.colors.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (item.isOp) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFFE0F2FE))
                                                .padding(horizontal = 5.dp, vertical = 1.dp),
                                        ) {
                                            Text(
                                                text = "楼主",
                                                style = IosTheme.type.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                                color = Color(0xFF0284C7),
                                            )
                                        }
                                    }
                                }

                                // 发帖数胶囊
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(IosTheme.colors.fieldBackground)
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = "${item.postCount} 帖",
                                        style = IosTheme.type.caption.copy(fontSize = 11.sp),
                                        color = IosTheme.colors.secondaryLabel,
                                    )
                                }
                            }
                            if (idx < authorFilterItems.lastIndex) {
                                IosDivider(modifier = Modifier.padding(vertical = 1.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    manageTarget?.let { post ->
        PostManageDialog(post, onDismiss = { manageTarget = null },
            onEdit = {
                manageTarget = null
                onOpenComposer(ComposerTarget.Edit(topicId, post.id, post.postNumber))
            },
            onDelete = { manageTarget = null; deleteTarget = post })
    }
    deleteTarget?.let { post ->
        DeleteConfirmDialog(post, onDismiss = { deleteTarget = null }) {
            deleteTarget = null
            onDeletePost(post.id)
        }
    }
    state.flagDialogPost?.let { post ->
        FlagDialog(
            post = post,
            submitting = post.id in state.reactingPosts,
            error = state.flagError,
            onDismiss = onCloseFlagDialog,
            onSubmit = { reasonId, message ->
                onSubmitFlag(post.id, reasonId, message)
            },
        )
    }
}

/**
 * 评论区头部栏:左侧总回复数,右侧 iOS 分段控件样式的「正序 / 倒序」胶囊。
 * 固定跟在 1 楼下方 —— 顺序只作用于评论,主贴不参与翻转。
 */
@Composable
private fun CommentOrderBar(total: Int, descending: Boolean, busy: Boolean, onToggle: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(IosTheme.colors.card)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (total > 0) "全部回复 $total" else "全部回复",
                style = IosTheme.type.subheadline,
                fontWeight = FontWeight.SemiBold,
                color = IosTheme.colors.label,
            )
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(IosTheme.colors.fieldBackground)
                    .padding(2.dp),
            ) {
                OrderSegment("正序", selected = !descending, enabled = !busy) { if (descending) onToggle() }
                OrderSegment("倒序", selected = descending, enabled = !busy) { if (!descending) onToggle() }
            }
        }
        IosDivider()
    }
}

@Composable
private fun OrderSegment(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) IosTheme.colors.card else Color.Transparent)
            .clickable(enabled = enabled && !selected, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = IosTheme.type.footnote,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) IosTheme.colors.label else IosTheme.colors.secondaryLabel,
        )
    }
}

private data class InternalTopicLink(
    val topicId: Long,
    val postNumber: Int? = null,
)
/**
 * 解析站内（linux.do）帖子链接，支持：
 * - `https://linux.do/t/{slug}/{topicId}`
 * - `https://linux.do/t/{slug}/{topicId}/{postNumber}`
 * - `https://linux.do/t/{topicId}`
 * - `https://linux.do/t/{topicId}/{postNumber}`
 */
private fun parseInternalTopicUrl(url: String): InternalTopicLink? = runCatching {
    val uri = Uri.parse(url)
    val host = uri.host?.lowercase() ?: return null
    if (host != Config.HOST && !host.endsWith(".${Config.HOST}")) return null
    val segments = uri.pathSegments ?: return null
    if (segments.size < 2 || segments[0] != "t") return null
    val secondAsId = segments[1].toLongOrNull()
    when {
        secondAsId == null -> {
            val topicId = segments.getOrNull(2)?.toLongOrNull() ?: return null
            val postNum = segments.getOrNull(3)?.toIntOrNull()?.takeIf { it > 1 }
            InternalTopicLink(topicId = topicId, postNumber = postNum)
        }
        segments.size == 2 -> InternalTopicLink(topicId = secondAsId, postNumber = null)
        segments.size >= 4 -> {
            val topicId = segments[2].toLongOrNull() ?: return null
            val postNum = segments[3].toIntOrNull()?.takeIf { it > 1 }
            InternalTopicLink(topicId = topicId, postNumber = postNum)
        }
        else -> {
            val thirdAsLong = segments[2].toLongOrNull() ?: return null
            if (thirdAsLong > secondAsId && thirdAsLong >= 1000L) {
                InternalTopicLink(topicId = thirdAsLong, postNumber = null)
            } else {
                InternalTopicLink(topicId = secondAsId, postNumber = thirdAsLong.toInt().takeIf { it > 1 })
            }
        }
    }
}.getOrNull()

/**
 * Discourse 风格的前序未加载楼层折叠条 + 骨架屏（Skeleton Placeholder）。
 * 1 楼与尾部切片之间的未加载楼层收拢在此，向上滑入或点击时展示骨架屏并按 20 楼一批向上回填。
 */
@Composable
private fun PreviousSliceGap(hiddenCount: Int, loading: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (loading) {
            repeat(2) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(IosTheme.colors.card)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(IosTheme.colors.separator),
                        )
                        Column(
                            Modifier.padding(start = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(width = 96.dp, height = 12.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(IosTheme.colors.separator),
                            )
                            Box(
                                Modifier
                                    .size(width = 64.dp, height = 10.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(IosTheme.colors.fieldBackground),
                            )
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxWidth(0.92f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(IosTheme.colors.separator),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.68f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(IosTheme.colors.fieldBackground),
                    )
                }
                IosDivider()
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(IosTheme.colors.groupedBackground)
                .clickable(enabled = !loading, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                IosActivityIndicator(diameter = 16.dp)
                Text(
                    "正在加载前序楼层（剩余 $hiddenCount 层）…",
                    style = IosTheme.type.footnote,
                    color = IosTheme.colors.secondaryLabel,
                    modifier = Modifier.padding(start = 8.dp),
                )
            } else {
                Text(
                    "↑ 中间省略 $hiddenCount 层 · 上滑或点击加载",
                    style = IosTheme.type.subheadline,
                    color = IosTheme.colors.accent,
                )
            }
        }
        IosDivider()
    }
}

/** 长按楼层头部弹出的操作面板:增加标题并对齐长按简评弹窗框体大小样式。 */
@Composable
private fun PostManageDialog(post: Post, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val isTopicRoot = post.postNumber <= 1
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(min = 280.dp, max = 340.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(IosTheme.colors.card)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (isTopicRoot) "确认编辑此话题？" else "确认编辑此评论？",
                style = IosTheme.type.headline,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (post.canEdit) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF007AFF))
                            .clickable {
                                onDismiss()
                                onEdit()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PostActionIcon(PostAction.Edit, selected = false, tint = Color.White)
                            Text("编辑", style = IosTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                        }
                    }
                }
                if (post.canDelete) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFF3B30))
                            .clickable {
                                onDismiss()
                                onDelete()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PostActionIcon(PostAction.Delete, selected = false, tint = Color.White)
                            Text("删除", style = IosTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/** 回复整个话题的入口。淡雅蓝白微光质感,与页面底色及阅读进度胶囊自然呼应。 */
@Composable
private fun ReplyFab(enabled: Boolean, onClick: () -> Unit) {
    val tintColor = if (enabled) IosTheme.colors.accent else IosTheme.colors.tertiaryLabel
    val bgBase = IosTheme.colors.card.copy(alpha = 0.95f)
    val bgTint = tintColor.copy(alpha = if (enabled) 0.12f else 0.05f)
    val borderColor = tintColor.copy(alpha = if (enabled) 0.28f else 0.12f)
    Box(
        Modifier
            .size(46.dp)
            .shadow(elevation = 3.dp, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .background(bgBase)
            .background(bgTint)
            .border(1.dp, borderColor, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = "回复这个话题" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
                val arrow = Path().apply {
                    moveTo(10f, 5f); lineTo(3f, 11f); lineTo(10f, 17f)
                    lineTo(10f, 13f); cubicTo(17f, 12f, 19f, 15f, 21f, 20f)
                    cubicTo(21f, 10f, 17f, 8f, 10f, 9f); close()
                }
                drawPath(arrow, tintColor)
            }
        }
    }
}

/** 右下角的阅读进度。兼作"回到 1 楼"的按钮,省掉一个额外悬浮控件。 */
@Composable
private fun ReadingProgressPill(current: Int, total: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val arrowColor = IosTheme.colors.accent
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(IosTheme.colors.card.copy(alpha = 0.95f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(13.dp)) {
            scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
                drawLine(arrowColor, Offset(12f, 21f), Offset(12f, 5f), 2f)
                drawLine(arrowColor, Offset(12f, 4f), Offset(5f, 11f), 2f)
                drawLine(arrowColor, Offset(12f, 4f), Offset(19f, 11f), 2f)
            }
        }
        Text("$current / $total", style = IosTheme.type.caption,
            color = IosTheme.colors.secondaryLabel, modifier = Modifier.padding(start = 5.dp))
    }
}

/** 全屏/退出全屏切换按钮图标 (四个朝外直角展开 / 四个朝内折角收起) */
@Composable
private fun FullscreenToggleIcon(isFullScreen: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            if (!isFullScreen) {
                // 四个直角朝外：全屏展开模式
                val p1 = Path().apply { moveTo(3f, 8f); lineTo(3f, 3f); lineTo(8f, 3f) }
                val p2 = Path().apply { moveTo(16f, 3f); lineTo(21f, 3f); lineTo(21f, 8f) }
                val p3 = Path().apply { moveTo(21f, 16f); lineTo(21f, 21f); lineTo(16f, 21f) }
                val p4 = Path().apply { moveTo(8f, 21f); lineTo(3f, 21f); lineTo(3f, 16f) }
                drawPath(p1, tint, style = stroke)
                drawPath(p2, tint, style = stroke)
                drawPath(p3, tint, style = stroke)
                drawPath(p4, tint, style = stroke)
            } else {
                // 四个折角朝内：恢复 1/3 模式
                val p1 = Path().apply { moveTo(3f, 8f); lineTo(8f, 8f); lineTo(8f, 3f) }
                val p2 = Path().apply { moveTo(16f, 3f); lineTo(16f, 8f); lineTo(21f, 8f) }
                val p3 = Path().apply { moveTo(21f, 16f); lineTo(16f, 16f); lineTo(16f, 21f) }
                val p4 = Path().apply { moveTo(8f, 21f); lineTo(8f, 16f); lineTo(3f, 16f) }
                drawPath(p1, tint, style = stroke)
                drawPath(p2, tint, style = stroke)
                drawPath(p3, tint, style = stroke)
                drawPath(p4, tint, style = stroke)
            }
        }
    }
}

/** 贴吧风格图片选择图标：圆角矩形外框 + 小太阳 + 起伏山峰 */
@Composable
internal fun ComposerImageIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            // 外边框圆角矩形 (3, 3) 宽高 18x18
            val rectPath = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = 3f, top = 3f, right = 21f, bottom = 21f,
                        radiusX = 4f, radiusY = 4f
                    )
                )
            }
            drawPath(rectPath, tint, style = stroke)
            // 左上角小太阳圆点
            drawCircle(tint, radius = 1.5f, center = Offset(7.5f, 7.5f))
            // 起伏山峰线条
            val mountain = Path().apply {
                moveTo(3.5f, 17f)
                lineTo(8.5f, 11.5f)
                lineTo(13f, 16f)
                lineTo(16.5f, 12f)
                lineTo(20.5f, 16.5f)
            }
            drawPath(mountain, tint, style = stroke)
        }
    }
}

/** 贴吧风格附件图标：右上折角纸张 + 内部两道横线 */
@Composable
internal fun ComposerAttachmentIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            // 折角文件外轮廓
            val doc = Path().apply {
                moveTo(6f, 3f)
                lineTo(14f, 3f)
                lineTo(19f, 8f)
                lineTo(19f, 21f)
                lineTo(6f, 21f)
                close()
            }
            drawPath(doc, tint, style = stroke)
            // 折角折痕
            val fold = Path().apply {
                moveTo(14f, 3f)
                lineTo(14f, 8f)
                lineTo(19f, 8f)
            }
            drawPath(fold, tint, style = stroke)
            // 文本横线1
            drawLine(tint, Offset(9f, 13f), Offset(15f, 13f), strokeWidth = 1.8f, cap = StrokeCap.Round)
            // 文本横线2
            drawLine(tint, Offset(9f, 17f), Offset(16f, 17f), strokeWidth = 1.8f, cap = StrokeCap.Round)
        }
    }
}

/** 贴吧风格表情图标：圆脸轮廓 + 双眼 + 微笑弧线 */
@Composable
internal fun ComposerEmojiIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawCircle(tint, radius = 9f, center = Offset(12f, 12f), style = stroke)
            drawCircle(tint, radius = 1.3f, center = Offset(8.8f, 10f))
            drawCircle(tint, radius = 1.3f, center = Offset(15.2f, 10f))
            drawArc(
                color = tint,
                startAngle = 22f,
                sweepAngle = 136f,
                useCenter = false,
                topLeft = Offset(7.8f, 10.5f),
                size = androidx.compose.ui.geometry.Size(8.4f, 6.2f),
                style = stroke,
            )
        }
    }
}

/** 粗体图标：加粗大写英文字母 B 矢量轮廓，与图片/附件/表情图标保持完全一致的视觉规范 */
@Composable
fun ComposerBoldIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            // 坚线主干
            drawLine(tint, Offset(7f, 4f), Offset(7f, 20f), strokeWidth = 2.6f, cap = StrokeCap.Round)
            // 上半弧
            val upper = Path().apply {
                moveTo(7f, 4.5f)
                lineTo(13f, 4.5f)
                cubicTo(16f, 4.5f, 17f, 6.2f, 17f, 8.2f)
                cubicTo(17f, 10.2f, 15.5f, 11.8f, 13f, 11.8f)
                lineTo(7f, 11.8f)
            }
            drawPath(upper, tint, style = stroke)
            // 下半弧
            val lower = Path().apply {
                moveTo(7f, 11.8f)
                lineTo(13.8f, 11.8f)
                cubicTo(17f, 11.8f, 18.5f, 13.8f, 18.5f, 16.2f)
                cubicTo(18.5f, 18.8f, 16.5f, 19.5f, 13.5f, 19.5f)
                lineTo(7f, 19.5f)
            }
            drawPath(lower, tint, style = stroke)
        }
    }
}

/** 矢量标签吊牌图标：横向尖角吊牌外轮廓 + 圆孔，与表情/图片/附件保持视觉规范一致 */
@Composable
fun ComposerTagIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(16.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val path = Path().apply {
                moveTo(3.2f, 12f)
                lineTo(8.2f, 6.2f)
                lineTo(20.5f, 6.2f)
                lineTo(20.5f, 17.8f)
                lineTo(8.2f, 17.8f)
                close()
            }
            drawPath(path, tint, style = stroke)
            drawCircle(tint, radius = 1.7f, center = Offset(7.8f, 12f))
        }
    }
}

/** 矢量小锁图标：用于受限/私密分类标识 */
@Composable
fun ComposerLockIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(13.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val shackle = Path().apply {
                moveTo(7f, 11f)
                lineTo(7f, 7f)
                cubicTo(7f, 3.5f, 17f, 3.5f, 17f, 7f)
                lineTo(17f, 11f)
            }
            drawPath(shackle, tint, style = stroke)
            val body = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = 4.5f, top = 10.5f, right = 19.5f, bottom = 20.5f,
                        radiusX = 3f, radiusY = 3f,
                    )
                )
            }
            drawPath(body, tint)
        }
    }
}

private data class EmojiCategory(
    val title: String,
    val icon: String,
    val emojis: List<String>,
)

private val EMOJI_CATEGORIES = listOf(
    EmojiCategory(
        title = "表情",
        icon = "😀",
        emojis = listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
            "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
            "😘", "😗", "😚", "😙", "😋", "😛", "😜", "🤪",
            "😝", "🤑", "🤗", "🫢", "🫣", "🤫", "🤔", "🫡",
            "🤐", "🤨", "😐", "😑", "😶", "🫥", "😏", "😒",
            "🙄", "😬", "😮‍💨", "🤥", "😌", "😔", "😪", "🤤",
            "😴", "😷", "🤒", "🤕", "🤢", "🤮", "🤧", "🥵",
            "🥶", "🥴", "😵", "😵‍💫", "🤯", "🤠", "🥳", "🥸",
            "😎", "🤓", "🧐", "😕", "🫤", "😟", "🙁", "☹️",
            "😮", "😯", "😲", "😳", "🥺", "🥹", "😦", "😧",
            "😨", "😰", "😥", "😢", "😭", "😱", "😖", "😣",
            "😞", "😓", "😩", "😫", "🥱", "😤", "😡", "😠",
            "🤬", "😈", "👿", "💀", "☠️", "💩", "🤡", "👹",
            "👺", "👻", "👽", "👾", "🤖", "😺", "😸", "😹",
        )
    ),
    EmojiCategory(
        title = "人物手势",
        icon = "👋",
        emojis = listOf(
            "👋", "🤚", "🖐️", "✋", "🖖", "🫱", "🫲", "🫳",
            "🫴", "🫷", "🫸", "👌", "🤌", "🤏", "✌️", "🤞",
            "🫰", "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕",
            "👇", "☝️", "🫵", "👍", "👎", "✊", "👊", "🤛",
            "🤜", "👏", "🙌", "🫶", "👐", "🤲", "🤝", "🙏",
            "✍️", "💅", "🤳", "💪", "🦾", "🦿", "🦵", "🦶",
            "👂", "🦻", "👃", "🫀", "🫁", "🧠", "👀", "👁️",
            "🧑", "👶", "👧", "🧒", "👦", "👩", "👨", "🧓",
            "👴", "👵", "👮", "🕵️", "💂", "👷", "🤴", "👸",
        )
    ),
    EmojiCategory(
        title = "动物自然",
        icon = "🐶",
        emojis = listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
            "🐻‍❄️", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵",
            "🐔", "🐧", "🐦", "🐤", "🦆", "🦅", "🦉", "🦇",
            "🐺", "🐗", "🐴", "🦄", "🐝", "🐛", "🦋", "🐌",
            "🐞", "🐜", "🪰", "🪲", "🪳", "🦟", "🦗", "🕷️",
            "🦂", "🐢", "🐍", "🦎", "🐙", "🦑", "🦐", "🦞",
            "🦀", "🐡", "🐠", "🐟", "🐬", "🐳", "🐋", "🦈",
            "🐊", "🐆", "🐅", "🐃", "🐂", "🐄", "🐎", "🐖",
            "🐏", "🐑", "🦙", "🐐", "🦌", "🐕", "🐩", "🐈",
            "🐓", "🦃", "🦚", "🦜", "🦢", "🕊️", "🐇", "🦝",
            "🌲", "🌳", "🌴", "🌵", "🌾", "🌿", "🍀", "🍁",
            "🍂", "🍃", "🌸", "🌺", "🌼", "🌻", "🌞", "🌝",
            "🌙", "⭐️", "🌟", "✨", "⚡️", "🔥", "🌈", "☀️",
            "⛅️", "☁️", "🌧️", "⛈️", "❄️", "☃️", "💨", "🌊",
        )
    ),
    EmojiCategory(
        title = "美食饮品",
        icon = "🍔",
        emojis = listOf(
            "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇",
            "🍓", "🫐", "🍈", "🍒", "🍑", "🥭", "🍍", "🥥",
            "🥝", "🍅", "🍆", "🥑", "🥦", "🥬", "🥒", "🌶️",
            "🌽", "🥕", "🧄", "🧅", "🥔", "🍠", "🥐", "🥯",
            "🍞", "🥖", "🥨", "🧀", "🥚", "🍳", "🧈", "🥞",
            "🧇", "🥓", "🥩", "🍗", "🍖", "🌭", "🍔", "🍟",
            "🍕", "🥪", "🥙", "🌮", "🌯", "🥗", "🥘", "🥫",
            "🍝", "🍜", "🍲", "🍛", "🍣", "🍱", "🥟", "🍤",
            "🍙", "🍚", "🍘", "🍢", "🍡", "🍧", "🍨", "🍦",
            "🍰", "🎂", "🍮", "🍭", "🍬", "🍫", "🍿", "🍩",
            "🍪", "🌰", "🥜", "🍯", "🥛", "☕️", "🍵", "🧃",
            "🥤", "🧋", "🍶", "🍺", "🍻", "🥂", "🍷", "🍸",
        )
    ),
    EmojiCategory(
        title = "活动旅行",
        icon = "⚽️",
        emojis = listOf(
            "⚽️", "🏀", "🏈", "⚾️", "🥎", "🎾", "🏐", "🏉",
            "🥏", "🎱", "🏓", "🏸", "🏒", "⛳️", "🏹", "🎣",
            "🥊", "🥋", "🛹", "🛼", "🎿", "🏂", "🏋️", "🤸",
            "⛹️", "🚴", "🏆", "🥇", "🥈", "🥉", "🏅", "🎖️",
            "🎫", "🎟️", "🎪", "🎨", "🎬", "🎤", "🎧", "🎼",
            "🎹", "🥁", "🎷", "🎺", "🎸", "🎻", "🎲", "🎯",
            "🎳", "🎮", "🎰", "🧩", "🚗", "🚕", "🚙", "🚌",
            "🏎️", "🚓", "🚑", "🚒", "🚐", "🛻", "🚚", "🚜",
            "🚲", "🛵", "🏍️", "🚨", "🚄", "🚅", "🚇", "✈️",
            "🛫", "🛬", "🚀", "🛸", "⛵️", "🚤", "🛳️", "🚢",
        )
    ),
    EmojiCategory(
        title = "物品符号",
        icon = "💡",
        emojis = listOf(
            "⌚️", "📱", "💻", "⌨️", "🖥️", "🖨️", "📷", "📸",
            "📹", "🎥", "📞", "☎️", "📺", "📻", "🎙️", "⏰",
            "🕰️", "⏳", "🔋", "🔌", "💡", "🔦", "🕯️", "💸",
            "💵", "💴", "💶", "💰", "💳", "💎", "⚖️", "🔧",
            "🔨", "⚒️", "🛠️", "⛏️", "🔩", "⚙️", "💣", "🔪",
            "🗡️", "⚔️", "🛡️", "⚰️", "🔮", "💈", "💊", "💉",
            "🩺", "🏷️", "🔖", "🔑", "🗝️", "🚪", "📦", "✉️",
            "📩", "📨", "📧", "💌", "📥", "📤", "📮", "📜",
            "📄", "📑", "🧾", "📊", "📈", "📉", "📁", "📂",
            "📰", "📓", "📕", "📗", "📘", "📙", "📚", "📖",
            "🔗", "📎", "✂️", "📌", "📍", "✏️", "✒️", "🔍",
            "🔎", "🔒", "🔓", "❤️", "🧡", "💛", "💚", "💙",
            "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕", "💞",
            "💓", "💗", "💖", "💘", "💝", "💯", "💢", "💥",
            "💫", "💬", "💭", "🗯️", "💤", "⚠️", "⛔️", "🚫",
            "✅", "❌", "❓", "❗️", "➕", "➖", "➗", "✖️",
            "0️⃣", "1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣",
            "8️⃣", "9️⃣", "🔟", "▶️", "⏸️", "⏹️", "🔄", "🔝",
        )
    ),
)

private val ALL_EMOJIS_FLAT = EMOJI_CATEGORIES.flatMap { it.emojis }.distinct()

@Composable
internal fun QuickEmojiPicker(
    enabled: Boolean = true,
    onSelect: (String) -> Unit,
) {
    var selectedCategoryIndex by rememberSaveable { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val isDark = IosTheme.colors.isDark
    val containerBg = if (isDark) Color(0xFF1E1E20) else IosTheme.colors.fieldBackground

    val displayedEmojis = remember(selectedCategoryIndex, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            EMOJI_CATEGORIES.getOrNull(selectedCategoryIndex)?.emojis ?: EMOJI_CATEGORIES[0].emojis
        } else {
            ALL_EMOJIS_FLAT.filter { it.contains(query) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(containerBg)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        // 顶部操作栏：搜索框与分类标签
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 搜索框
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDark) Color(0xFF2C2C2E) else Color(0x1F767680))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🔍", fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 13.sp,
                        color = IosTheme.colors.label,
                    ),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                "搜索表情...",
                                fontSize = 13.sp,
                                color = IosTheme.colors.secondaryLabel,
                            )
                        }
                        innerTextField()
                    },
                )
                if (searchQuery.isNotEmpty()) {
                    Text(
                        "✕",
                        fontSize = 11.sp,
                        color = IosTheme.colors.secondaryLabel,
                        modifier = Modifier
                            .clickable { searchQuery = "" }
                            .padding(2.dp),
                    )
                }
            }
        }

        // 分类切换 Tab（未搜索时展示）
        if (searchQuery.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                EMOJI_CATEGORIES.forEachIndexed { index, cat ->
                    val selected = index == selectedCategoryIndex
                    val tabBg = if (selected) IosTheme.colors.accent.copy(alpha = 0.15f) else Color.Transparent
                    val textColor = if (selected) IosTheme.colors.accent else IosTheme.colors.secondaryLabel
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(tabBg)
                            .clickable { selectedCategoryIndex = index }
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(cat.icon, fontSize = 12.sp)
                        Spacer(Modifier.width(3.dp))
                        Text(
                            cat.title,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = textColor,
                        )
                    }
                }
            }
        }

        // Emoji 网格展示
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (displayedEmojis.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                    Text("未找到相关表情", style = IosTheme.type.footnote, color = IosTheme.colors.secondaryLabel)
                }
            } else {
                displayedEmojis.chunked(8).forEach { rowEmojis ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        rowEmojis.forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(enabled = enabled) { onSelect(emoji) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(text = emoji, fontSize = 20.sp)
                            }
                        }
                        if (rowEmojis.size < 8) {
                            repeat(8 - rowEmojis.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 附件横向可视化预览栏。
 * 图片直接显示缩略图，并支持点击大图全屏预览；文件显示图标与名称；右上角提供删除按钮。
 */
@Composable
fun AttachmentPreviewRow(
    attachments: List<AttachedMedia>,
    uploading: Boolean,
    onRemove: (String) -> Unit,
    onPreviewImage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (media in attachments) {
            Box(
                modifier = Modifier
                    .size(if (media.isImage) 76.dp else 120.dp, 76.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(IosTheme.colors.card)
                    .border(0.8.dp, IosTheme.colors.separator.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
            ) {
                if (media.isImage) {
                    AsyncImage(
                        model = media.previewUrl,
                        contentDescription = media.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onPreviewImage(media.previewUrl) },
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ComposerAttachmentIcon(
                            tint = IosTheme.colors.accent,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = media.name,
                            style = IosTheme.type.caption.copy(fontSize = 11.sp),
                            color = IosTheme.colors.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // 右上角删除按钮
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { onRemove(media.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✕",
                        style = IosTheme.type.caption.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                }
            }
        }

        if (uploading) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(IosTheme.colors.fieldBackground)
                    .border(0.8.dp, IosTheme.colors.accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IosActivityIndicator(diameter = 16.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "上传中…",
                        style = IosTheme.type.caption.copy(fontSize = 10.sp),
                        color = IosTheme.colors.secondaryLabel,
                    )
                }
            }
        }
    }
}

/** 回复 / 编辑共用的输入弹层。页内底部抽屉设计,完全跟随宿主窗口键盘避让,支持一键全屏展开与收起。 */
@Composable
private fun ComposerSheet(
    composer: ComposerUiState,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
    onAttach: (Uri) -> Unit,
    onRemoveAttachment: (String) -> Unit = {},
) {
    val target = composer.target ?: return
    val heading = when (target) {
        is ComposerTarget.NewReply -> "发表评论"
        is ComposerTarget.ReplyTo -> "回复 @${target.username}"
        is ComposerTarget.Edit -> "编辑 #${target.postNumber}"
    }
    val focus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var isFullScreen by rememberSaveable { mutableStateOf(false) }
    var showEmojiPicker by rememberSaveable { mutableStateOf(false) }
    var previewingImage by remember { mutableStateOf<String?>(null) }
    var editorView by remember(target) { mutableStateOf<RichMarkdownEditorView?>(null) }

    // 编辑原文异步取回时只替换一次草稿。
    LaunchedEffect(target, composer.loadingDraft) {
        if (!composer.loadingDraft) {
            editorView?.setInitialMarkdown(composer.draft, composer.attachments)
        }
    }

    // 弹框打开时默认选中输入框并弹出软键盘
    LaunchedEffect(editorView, composer.loadingDraft) {
        if (!composer.loadingDraft && editorView != null) {
            kotlinx.coroutines.delay(120)
            editorView?.requestInputFocus()
        }
    }

    LaunchedEffect(composer.attachments) {
        editorView?.syncAttachments(composer.attachments)
    }

    val insertToken = { token: String ->
        editorView?.insertToken(token)
    }

    // 系统相册选择器和 SAF 文件选择器都不需要声明存储权限。
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onAttach)
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onAttach)
    }
    val busy = composer.submitting || composer.uploading

    BackHandler(enabled = true) {
        onClose()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // 背景半透明暗色遮罩,点击收起弹层
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onClose() },
                ),
        )
        // 底部抽屉面板
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    when {
                        isFullScreen -> Modifier.fillMaxHeight(0.92f)
                        showEmojiPicker -> Modifier.height(420.dp)
                        composer.attachments.isNotEmpty() || composer.uploading -> Modifier.height(380.dp)
                        else -> Modifier.height(280.dp)
                    }
                )
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(IosTheme.colors.card)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}, // 阻止点击穿透到遮罩
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 顶部标题与全屏切换栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // 左侧轻量关闭叉号
                val closeColor = IosTheme.colors.secondaryLabel
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(12.dp)) {
                        drawLine(closeColor, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = 1.8f, cap = StrokeCap.Round)
                        drawLine(closeColor, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 1.8f, cap = StrokeCap.Round)
                    }
                }

                Text(
                    heading,
                    style = IosTheme.type.headline,
                    fontWeight = FontWeight.SemiBold,
                    color = IosTheme.colors.label,
                    maxLines = 1,
                )

                // 右上角全屏/恢复 切换图标
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { isFullScreen = !isFullScreen }
                        .semantics { contentDescription = if (isFullScreen) "恢复默认高度" else "全屏显示" },
                    contentAlignment = Alignment.Center,
                ) {
                    FullscreenToggleIcon(isFullScreen = isFullScreen, tint = IosTheme.colors.label)
                }
            }

            // 中间输入区域 (自适应填满剩余空间，基于原生 EditText + ReplacementSpan 所见即所得图文混排)
            val textColorInt = IosTheme.colors.label.toArgb()
            val hintColorInt = IosTheme.colors.tertiaryLabel.toArgb()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(IosTheme.colors.fieldBackground),
            ) {
                if (composer.loadingDraft) {
                    IosLoadingBox()
                } else {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            RichMarkdownEditorView(ctx).apply {
                                updateThemeColors(textColorInt, hintColorInt)
                                setInitialMarkdown(composer.draft, composer.attachments)
                                setOnMarkdownChangeListener { newRaw -> onDraftChange(newRaw) }
                                setOnRemoveAttachmentListener { mediaId -> onRemoveAttachment(mediaId) }
                                setOnPreviewImageListener { previewUrl -> previewingImage = previewUrl }
                                editorView = this
                                post { requestInputFocus() }
                            }
                        },
                        update = { view ->
                            view.updateThemeColors(textColorInt, hintColorInt)
                        },
                    )
                }
            }

            composer.error?.let {
                Text(it, style = IosTheme.type.footnote, color = IosTheme.colors.destructive, maxLines = 1)
            }

            // 底部操作栏: 图片/附件/表情 Icon + 上传状态 + 右侧发送胶囊
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 图片 Icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !busy) {
                            pickImage.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        }
                        .semantics { contentDescription = "添加图片" },
                    contentAlignment = Alignment.Center,
                ) {
                    ComposerImageIcon(tint = if (busy) IosTheme.colors.tertiaryLabel else IosTheme.colors.label)
                }

                Spacer(Modifier.width(8.dp))

                // 附件 Icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !busy) {
                            pickFile.launch(arrayOf("*/*"))
                        }
                        .semantics { contentDescription = "添加附件" },
                    contentAlignment = Alignment.Center,
                ) {
                    ComposerAttachmentIcon(tint = if (busy) IosTheme.colors.tertiaryLabel else IosTheme.colors.label)
                }

                Spacer(Modifier.width(8.dp))

                // 表情 Icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (showEmojiPicker) IosTheme.colors.accent.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable(enabled = !busy) {
                            val next = !showEmojiPicker
                            showEmojiPicker = next
                            if (next) {
                                keyboardController?.hide()
                            } else {
                                runCatching { focus.requestFocus() }
                                keyboardController?.show()
                            }
                        }
                        .semantics { contentDescription = "选择表情" },
                    contentAlignment = Alignment.Center,
                ) {
                    ComposerEmojiIcon(
                        tint = when {
                            busy -> IosTheme.colors.tertiaryLabel
                            showEmojiPicker -> IosTheme.colors.accent
                            else -> IosTheme.colors.label
                        },
                    )
                }

                Spacer(Modifier.width(8.dp))

                // 粗体 Icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !busy) {
                            insertToken("**粗体文字**")
                        }
                        .semantics { contentDescription = "加粗文字" },
                    contentAlignment = Alignment.Center,
                ) {
                    ComposerBoldIcon(
                        tint = if (busy) IosTheme.colors.tertiaryLabel else IosTheme.colors.label,
                    )
                }

                if (composer.uploading) {
                    Spacer(Modifier.width(10.dp))
                    IosActivityIndicator(diameter = 16.dp)
                    Text(
                        "上传中…",
                        style = IosTheme.type.caption,
                        color = IosTheme.colors.secondaryLabel,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }

                Spacer(Modifier.weight(1f))

                // 发送 / 保存 胶囊按钮
                val sendText = if (target is ComposerTarget.Edit) "保存" else "发表"
                val canSubmit = composer.canSubmit
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (canSubmit) IosTheme.colors.accent else IosTheme.colors.accent.copy(alpha = 0.35f))
                        .clickable(enabled = canSubmit) {
                            editorView?.exportToMarkdown()?.let { onDraftChange(it) }
                            onSubmit()
                        }
                        .padding(horizontal = 22.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (composer.submitting) {
                            IosActivityIndicator(diameter = 14.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            sendText,
                            style = IosTheme.type.subheadline.copy(fontWeight = FontWeight.Medium),
                            color = Color.White,
                        )
                    }
                }
            }

            if (showEmojiPicker) {
                QuickEmojiPicker(enabled = !busy) { emoji ->
                    insertToken(emoji)
                }
            }
        }
    }

    LaunchedEffect(target, composer.loadingDraft) {
        if (!composer.loadingDraft) {
            runCatching { focus.requestFocus() }
            keyboardController?.show()
        }
    }

    previewingImage?.let { previewUrl ->
        ImageViewer(url = previewUrl) { previewingImage = null }
    }
}

@Composable
private fun DeleteConfirmDialog(post: Post, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    // 删掉 1 楼等于删掉整个话题,这个后果必须写在确认框里。
    val isTopicRoot = post.postNumber <= 1
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(min = 280.dp, max = 340.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(IosTheme.colors.card)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (isTopicRoot) "确认删除此话题？" else "确认删除此评论？",
                style = IosTheme.type.headline,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (isTopicRoot) {
                Text(
                    "这是 1 楼，删除后整个话题及其下所有回复都会被移除。",
                    style = IosTheme.type.footnote,
                    color = IosTheme.colors.secondaryLabel,
                    textAlign = TextAlign.Center,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(IosTheme.colors.fieldBackground)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "取消",
                        style = IosTheme.type.subheadline.copy(fontWeight = FontWeight.Medium),
                        color = IosTheme.colors.label,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFF3B30))
                        .clickable(onClick = onConfirm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "删除",
                        style = IosTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthorBadge() {
    Box(
        Modifier
            .padding(start = 5.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(IosTheme.colors.accent.copy(alpha = 0.14f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            text = "楼主",
            style = IosTheme.type.caption.copy(fontSize = 11.sp),
            fontWeight = FontWeight.Medium,
            color = IosTheme.colors.accent,
        )
    }
}

/** 未读蓝点停留多久才算"读过"并开始淡出。 */
private const val UNREAD_DOT_DWELL_MS = 1_200L

/**
 * 楼层号右侧的未读小蓝点。变为已读时淡出并收起宽度,
 * 让楼层号仍然贴着右边距,不留空位。
 */
@Composable
private fun UnreadDot(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(500)) + shrinkHorizontally(tween(500)),
    ) {
        Box(
            Modifier
                .padding(start = 4.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(IosTheme.colors.accent),
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PostRow(
    post: Post,
    isTopicAuthor: Boolean = false,
    compactBottom: Boolean = false,
    showReplyToHint: Boolean = true,
    unreadDotVisible: Boolean = false,
    unreadDotPosition: UnreadDotPosition = UnreadDotPosition.Avatar,
    onPicture: (String, String) -> Unit,
    onUnknownTag: (String) -> Unit,
    reactions: List<String>,
    emojiUrls: Map<String, String>,
    busy: Boolean,
    pending: Boolean,
    error: String?,
    onReact: (String) -> Unit,
    onOpenBoost: () -> Unit,
    onDeleteBoost: (Long) -> Unit,
    onFlagBoost: (Long, Int, String?) -> Unit = { _, _, _ -> },
    onOpenUser: (String, String?, String?, String?) -> Unit = { _, _, _, _ -> },
    onRefresh: () -> Unit,
    onSolution: () -> Unit,
    canReply: Boolean,
    onReply: () -> Unit,
    onManage: () -> Unit,
    highlighted: Boolean = false,
    replyToUsername: String? = null,
    onJumpToPost: ((Int) -> Unit)? = null,
    onToggleBookmark: () -> Unit = {},
    onCopyLink: () -> Unit = {},
    onFlag: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    signedIn: Boolean = false,
    canFlag: Boolean = !post.yours,
) {
    val blocks = remember(post) { CookedParser.parsePostCached(post, onUnknownTag) }
    val background = if (highlighted) IosTheme.colors.accent.copy(alpha = 0.12f) else IosTheme.colors.card
    val canManage = post.canEdit || post.canDelete
    Column(
        Modifier
            .fillMaxWidth()
            .background(background)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (compactBottom) 8.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 长按放在头部这一行,而不是整条楼层:正文裹在 SelectionContainer 里,
        // 长按那里是"选择文本",两个手势会抢。
        Row(
            Modifier.fillMaxWidth().then(
                if (canManage) Modifier
                    .semantics { contentDescription = "长按 #${post.postNumber} 可编辑或删除" }
                    .combinedClickable(enabled = !busy, onClick = {}, onLongClick = onManage)
                else Modifier,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val postAvatarUrl = avatarUrlOf(post.avatarTemplate, post.animatedAvatar, username = post.username)
            val staticAvatarUrl = avatarUrlOf(post.avatarTemplate, null)
            val displayName = post.displayName.ifBlank { post.username }
            Box {
                UserAvatar(
                    avatarUrl = postAvatarUrl,
                    fallbackUrl = staticAvatarUrl,
                    username = post.username,
                    modifier = Modifier.size(32.dp).clickable {
                        AvatarCache.record(post.username, post.animatedAvatar)
                        onOpenUser(post.username, postAvatarUrl, staticAvatarUrl, displayName)
                    },
                    cornerRadius = 4.dp,
                )
                if (unreadDotVisible && unreadDotPosition == UnreadDotPosition.Avatar) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 1.dp, y = (-1).dp)
                            .clip(CircleShape)
                            .background(IosTheme.colors.card)
                            .padding(1.dp)
                            .clip(CircleShape)
                            .background(IosTheme.colors.accent),
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        displayName,
                        style = IosTheme.type.subheadline,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            AvatarCache.record(post.username, post.animatedAvatar)
                            onOpenUser(post.username, postAvatarUrl, staticAvatarUrl, displayName)
                        },
                    )
                    if (!post.name.isNullOrBlank() && post.name != post.username) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "@${post.username}",
                            style = IosTheme.type.caption,
                            color = IosTheme.colors.secondaryLabel,
                            modifier = Modifier.clickable {
                                AvatarCache.record(post.username, post.animatedAvatar)
                                onOpenUser(post.username, postAvatarUrl, staticAvatarUrl, displayName)
                            },
                        )
                    }
                    if (isTopicAuthor) {
                        Spacer(Modifier.width(4.dp))
                        AuthorBadge()
                    }
                    if (showReplyToHint) {
                        val parentNum = post.effectiveReplyToPostNumber(includeTopicPost = true)
                        if (parentNum != null && parentNum > 0) {
                            Spacer(Modifier.width(6.dp))
                            val label = if (!replyToUsername.isNullOrBlank()) "⮑ 回复 @$replyToUsername #$parentNum" else "⮑ 回复 #$parentNum"
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(IosTheme.colors.accent.copy(alpha = 0.10f))
                                    .clickable(enabled = onJumpToPost != null) {
                                        onJumpToPost?.invoke(parentNum)
                                    }
                                    .padding(horizontal = 5.dp, vertical = 1.5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = label,
                                    style = IosTheme.type.caption.copy(fontSize = 11.sp),
                                    color = if (onJumpToPost != null) IosTheme.colors.accent else IosTheme.colors.secondaryLabel,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                Text(RelativeTime.format(post.createdAt), style = IosTheme.type.caption, color = IosTheme.colors.secondaryLabel)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${post.postNumber}", style = IosTheme.type.footnote, color = IosTheme.colors.secondaryLabel)
                if (unreadDotPosition == UnreadDotPosition.FloorRight) {
                    UnreadDot(unreadDotVisible)
                }
            }
        }
        if (post.acceptedAnswer) Text("✓ 解决方案", style = IosTheme.type.footnote, color = Color(0xFF30B85B))
        CookedContent(blocks, onPicture, pollPost = post)
        PostActions(
            post = post,
            reactions = reactions,
            emojiUrls = emojiUrls,
            busy = busy,
            pending = pending,
            error = error,
            onReact = onReact,
            onOpenBoost = onOpenBoost,
            onDeleteBoost = onDeleteBoost,
            onFlagBoost = onFlagBoost,
            onOpenUser = onOpenUser,
            onRefresh = onRefresh,
            onSolution = onSolution,
            canReply = canReply,
            onReply = onReply,
            onToggleBookmark = onToggleBookmark,
            onCopyLink = onCopyLink,
            onFlag = onFlag,
            onEdit = onEdit,
            onDelete = onDelete,
            signedIn = signedIn,
            canFlag = canFlag,
        )
    }
}

/**
 * 贴吧式“楼中楼”单条回复行：紧凑展示在主楼层正下方的圆角灰底容器内，
 * 二级及以上回复自动显示“A 回复 B”，支持点赞、简评、回复及长按编辑/删除。
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SubReplyRow(
    row: TreePost,
    isTopicAuthor: Boolean,
    unreadDotVisible: Boolean = false,
    unreadDotPosition: UnreadDotPosition = UnreadDotPosition.Avatar,
    onPicture: (String, String) -> Unit,
    onUnknownTag: (String) -> Unit,
    reactions: List<String>,
    emojiUrls: Map<String, String>,
    busy: Boolean,
    pending: Boolean,
    error: String?,
    onReact: (String) -> Unit,
    onOpenBoost: () -> Unit,
    onDeleteBoost: (Long) -> Unit,
    onFlagBoost: (Long, Int, String?) -> Unit = { _, _, _ -> },
    onOpenUser: (String, String?, String?, String?) -> Unit = { _, _, _, _ -> },
    onRefresh: () -> Unit,
    highlighted: Boolean,
    canReply: Boolean,
    onReply: () -> Unit,
    onManage: () -> Unit,
    selfHasMore: Boolean,
    selfLoadingReplies: Boolean,
    onLoadSelfReplies: () -> Unit,
    onToggleBookmark: () -> Unit = {},
    onCopyLink: () -> Unit = {},
    onFlag: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    signedIn: Boolean = false,
    canFlag: Boolean = !row.post.yours,
) {
    val post = row.post
    val blocks = remember(post) { CookedParser.parsePostCached(post, onUnknownTag) }
    val canManage = post.canEdit || post.canDelete
    val bg = if (highlighted) IosTheme.colors.accent.copy(alpha = 0.14f) else Color.Transparent
    Column(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().then(
                if (canManage) Modifier
                    .semantics { contentDescription = "长按 #${post.postNumber} 可编辑或删除" }
                    .combinedClickable(enabled = !busy, onClick = {}, onLongClick = onManage)
                else Modifier,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val subAvatarUrl = avatarUrlOf(post.avatarTemplate, post.animatedAvatar, username = post.username)
            val subStaticAvatarUrl = avatarUrlOf(post.avatarTemplate, null)
            val subDisplayName = post.displayName.ifBlank { post.username }
            Box {
                UserAvatar(
                    avatarUrl = subAvatarUrl,
                    fallbackUrl = subStaticAvatarUrl,
                    username = post.username,
                    modifier = Modifier.size(20.dp).clickable {
                        AvatarCache.record(post.username, post.animatedAvatar)
                        onOpenUser(post.username, subAvatarUrl, subStaticAvatarUrl, subDisplayName)
                    },
                    cornerRadius = 3.dp,
                )
                if (unreadDotVisible && unreadDotPosition == UnreadDotPosition.Avatar) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 1.dp, y = (-1).dp)
                            .clip(CircleShape)
                            .background(IosTheme.colors.card)
                            .padding(1.dp)
                            .clip(CircleShape)
                            .background(IosTheme.colors.accent),
                    )
                }
            }
            Row(
                Modifier.weight(1f).padding(start = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = subDisplayName,
                    style = IosTheme.type.footnote,
                    fontWeight = FontWeight.SemiBold,
                    color = IosTheme.colors.secondaryLabel,
                    modifier = Modifier.clickable {
                        AvatarCache.record(post.username, post.animatedAvatar)
                        onOpenUser(post.username, subAvatarUrl, subStaticAvatarUrl, subDisplayName)
                    },
                )
                if (isTopicAuthor) AuthorBadge()
                row.replyToUsername?.takeIf { it.isNotBlank() }?.let { targetUser ->
                    Text(
                        text = " 回复 ",
                        style = IosTheme.type.footnote,
                        color = IosTheme.colors.label,
                    )
                    Text(
                        text = targetUser,
                        style = IosTheme.type.footnote,
                        fontWeight = FontWeight.SemiBold,
                        color = IosTheme.colors.secondaryLabel,
                        modifier = Modifier.clickable { onOpenUser(targetUser, null, null, null) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${RelativeTime.format(post.createdAt)} · #${post.postNumber}",
                    style = IosTheme.type.caption,
                    color = IosTheme.colors.tertiaryLabel,
                )
                if (unreadDotPosition == UnreadDotPosition.FloorRight) {
                    UnreadDot(unreadDotVisible)
                }
            }
        }
        if (post.acceptedAnswer) Text("✓ 解决方案", style = IosTheme.type.caption, color = Color(0xFF30B85B))
        CookedContent(blocks, onPicture, subReply = true, pollPost = post)
        PostActions(
            post = post,
            reactions = reactions,
            emojiUrls = emojiUrls,
            busy = busy,
            pending = pending,
            error = error,
            onReact = onReact,
            onOpenBoost = onOpenBoost,
            onDeleteBoost = onDeleteBoost,
            onFlagBoost = onFlagBoost,
            onOpenUser = onOpenUser,
            onRefresh = onRefresh,
            onSolution = {},
            canReply = canReply,
            onReply = onReply,
            onToggleBookmark = onToggleBookmark,
            onCopyLink = onCopyLink,
            onFlag = onFlag,
            onEdit = onEdit,
            onDelete = onDelete,
            compact = true,
            signedIn = signedIn,
            canFlag = canFlag,
        )
        if (selfHasMore) {
            Text(
                text = if (selfLoadingReplies) "正在加载回复…" else "查看该层更多回复 >",
                style = IosTheme.type.caption,
                fontWeight = FontWeight.Medium,
                color = IosTheme.colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = !selfLoadingReplies, onClick = onLoadSelfReplies)
                    .padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
private fun PostActions(
    post: Post,
    reactions: List<String>,
    emojiUrls: Map<String, String>,
    busy: Boolean,
    pending: Boolean,
    error: String?,
    onReact: (String) -> Unit,
    onOpenBoost: () -> Unit,
    onDeleteBoost: (Long) -> Unit,
    onFlagBoost: (Long, Int, String?) -> Unit = { _, _, _ -> },
    onOpenUser: (String, String?, String?, String?) -> Unit = { _, _, _, _ -> },
    onRefresh: () -> Unit,
    onSolution: () -> Unit,
    canReply: Boolean,
    onReply: () -> Unit,
    onToggleBookmark: () -> Unit = {},
    onCopyLink: () -> Unit = {},
    onFlag: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    compact: Boolean = false,
    signedIn: Boolean = false,
    canFlag: Boolean = !post.yours,
) {
    var chooseReaction by remember { mutableStateOf(false) }
    var visibleBoosts by rememberSaveable(post.id) { mutableIntStateOf(8) }
    var deleteId by remember { mutableStateOf<Long?>(null) }
    var boostActionTarget by remember { mutableStateOf<Boost?>(null) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    if (post.reactions.any { it.count > 0 }) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            post.reactions.filter { it.count > 0 }.forEach { reaction ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ReactionIcon(reaction.id, emojiUrls)
                    Text(" ${reaction.count}", style = IosTheme.type.caption, color = IosTheme.colors.secondaryLabel)
                }
            }
        }
    }
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 只有这一条楼层在提交时才转圈。
        if (pending) {
            IosActivityIndicator(diameter = 16.dp)
            Spacer(Modifier.width(8.dp))
        }
        if (!compact && (post.canAcceptAnswer || post.canUnacceptAnswer || post.acceptedAnswer)) {
            TiebaPostActionButton(
                label = "解决方案",
                action = PostAction.Solution,
                selected = post.acceptedAnswer,
                enabled = !busy && (post.canAcceptAnswer || post.canUnacceptAnswer),
                compact = compact,
                onClick = onSolution,
            )
        }
        val selected = post.currentUserReaction?.id
        val canReact = signedIn && !busy && !post.yours && (selected == null || post.currentUserReaction?.canUndo == true)
        Box {
            TiebaPostActionButton(
                label = "点赞",
                action = PostAction.Like,
                selected = selected != null,
                enabled = canReact,
                compact = compact,
                onClick = { onReact("heart") },
                onLongClick = { chooseReaction = true },
            )
            if (chooseReaction) {
                ReactionBubblePopup(
                    reactions = reactions,
                    emojiUrls = emojiUrls,
                    busy = busy,
                    onReact = onReact,
                    onDismiss = { chooseReaction = false },
                )
            }
        }
        if (canReply) {
            TiebaPostActionButton(
                label = "回复",
                action = PostAction.Reply,
                selected = false,
                enabled = !busy,
                compact = compact,
                onClick = onReply,
            )
        }
        if (post.canBoost) {
            TiebaPostActionButton(
                label = "boost简评",
                action = PostAction.Boost,
                selected = false,
                enabled = !busy,
                compact = compact,
                onClick = onOpenBoost,
            )
        }
        var showMoreMenu by remember { mutableStateOf(false) }
        Box {
            TiebaPostActionButton(
                label = "更多",
                action = PostAction.More,
                selected = false,
                enabled = !busy,
                compact = compact,
                onClick = { showMoreMenu = true },
            )
            if (showMoreMenu) {
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false },
                    modifier = Modifier.background(IosTheme.colors.card),
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PostActionIcon(PostAction.Bookmark, post.bookmarked, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (post.bookmarked) "取消书签" else "添加书签",
                                    color = if (post.bookmarked) IosTheme.colors.accent else IosTheme.colors.label,
                                )
                            }
                        },
                        enabled = signedIn && !busy,
                        onClick = {
                            showMoreMenu = false
                            onToggleBookmark()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PostActionIcon(PostAction.CopyLink, false, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("复制链接", color = IosTheme.colors.label)
                            }
                        },
                        enabled = true,
                        onClick = {
                            showMoreMenu = false
                            onCopyLink()
                        },
                    )
                    if (signedIn && canFlag) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PostActionIcon(PostAction.Flag, false, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("举报", color = IosTheme.colors.label)
                                }
                            },
                            enabled = !busy,
                            onClick = {
                                showMoreMenu = false
                                onFlag()
                            },
                        )
                    }
                    if (post.canEdit) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PostActionIcon(PostAction.Edit, false, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("编辑", color = IosTheme.colors.label)
                                }
                            },
                            enabled = !busy,
                            onClick = {
                                showMoreMenu = false
                                onEdit()
                            },
                        )
                    }
                    if (post.canDelete) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PostActionIcon(PostAction.Delete, false, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("删除", color = IosTheme.colors.destructive)
                                }
                            },
                            enabled = !busy,
                            onClick = {
                                showMoreMenu = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
    error?.let {
        Text(it, style = IosTheme.type.footnote, color = Color(0xFFFF453A))
        IosTextAction("刷新状态", textStyle = IosTheme.type.footnote, onClick = onRefresh)
    }
    if (post.boosts.isNotEmpty()) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      val chipBg = if (compact) IosTheme.colors.card else IosTheme.colors.fieldBackground
      FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        post.boosts.take(visibleBoosts).forEach { boost ->
            key(boost.id) {
                val content = remember(boost.cooked) { CookedParser.parseCached(boost.cooked) }
                Row(Modifier.widthIn(max = 260.dp).clip(RoundedCornerShape(50))
                    .background(chipBg)
                    .combinedClickable(
                        enabled = !busy,
                        onClick = { boostActionTarget = boost },
                        onLongClick = if (boost.canDelete) ({ deleteId = boost.id }) else null,
                    )
                    .padding(horizontal = 5.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val boostAvatarUrl = avatarUrlOf(boost.user?.avatarTemplate, boost.user?.animatedAvatar, username = boost.user?.username)
                    val boostStaticAvatarUrl = avatarUrlOf(boost.user?.avatarTemplate, null)
                    UserAvatar(
                        avatarUrl = boostAvatarUrl,
                        fallbackUrl = boostStaticAvatarUrl,
                        username = boost.user?.username,
                        modifier = Modifier.size(18.dp),
                        cornerRadius = 3.dp,
                    )
                    Box(Modifier.padding(start = 4.dp, end = 3.dp)) {
                        RichParagraph(content.filterIsInstance<CookedBlock.Paragraph>().flatMap { it.runs }, compact = true)
                    }
                }
            }
        }
      }
        if (visibleBoosts < post.boosts.size) IosTextAction("更多简评 (${post.boosts.size - visibleBoosts})", textStyle = IosTheme.type.caption) { visibleBoosts += 20 }
        else if (visibleBoosts > 8) IosTextAction("收起简评", textStyle = IosTheme.type.caption) { visibleBoosts = 8 }
    }
    boostActionTarget?.let { boost ->
        BoostActionSheet(
            boost = boost,
            onDismiss = { boostActionTarget = null },
            onOpenAuthor = { username ->
                boostActionTarget = null
                AvatarCache.record(boost.user?.username, boost.user?.animatedAvatar)
                val bAvatar = avatarUrlOf(boost.user?.avatarTemplate, boost.user?.animatedAvatar, username = boost.user?.username)
                val bStatic = avatarUrlOf(boost.user?.avatarTemplate, null)
                onOpenUser(username, bAvatar, bStatic, boost.user?.name)
            },
            onCopy = { text ->
                clipboardManager.setText(AnnotatedString(text))
                Toast.makeText(context, "简评已复制", Toast.LENGTH_SHORT).show()
            },
            onFlag = { reasonId, message ->
                boostActionTarget = null
                onFlagBoost(boost.id, reasonId, message)
            },
            onDelete = {
                boostActionTarget = null
                deleteId = boost.id
            },
        )
    }
    deleteId?.let { id -> Dialog(onDismissRequest = { deleteId = null }) {
        Column(
            Modifier
                .widthIn(min = 280.dp, max = 340.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(IosTheme.colors.card)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "确认删除此简评？",
                style = IosTheme.type.headline,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(IosTheme.colors.fieldBackground)
                        .clickable { deleteId = null },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "取消",
                        style = IosTheme.type.subheadline.copy(fontWeight = FontWeight.Medium),
                        color = IosTheme.colors.label,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFF3B30))
                        .clickable { deleteId = null; onDeleteBoost(id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "删除",
                        style = IosTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                }
            }
        }
    } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoostActionSheet(
    boost: Boost,
    onDismiss: () -> Unit,
    onOpenAuthor: (String) -> Unit,
    onCopy: (String) -> Unit,
    onFlag: (Int, String?) -> Unit,
    onDelete: () -> Unit,
) {
    val author = boost.user
    val cookedText = remember(boost.cooked) {
        val doc = Jsoup.parse(boost.cooked)
        doc.select("img").forEach { img ->
            val emojiCode = img.attr("title").ifBlank { img.attr("alt") }
            img.replaceWith(org.jsoup.nodes.TextNode(emojiCode))
        }
        android.text.Html.fromHtml(doc.body().html(), android.text.Html.FROM_HTML_MODE_COMPACT)
            .toString()
            .replace("\uFFFC", "")
            .replace("\uFFFD", "")
            .trim()
    }
    var showFlagDialog by remember(boost.id) { mutableStateOf(false) }
    var selectedReason by remember(boost.id) { mutableStateOf(PostFlagReason.Spam) }
    var message by rememberSaveable(boost.id) { mutableStateOf("") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = IosTheme.colors.card,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .sheetScrollable()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val identity = remember(author) {
                UserCardData(
                    id = author?.id ?: 0L,
                    username = author?.username.orEmpty().ifBlank { "unknown" },
                    name = author?.name?.takeIf { it.isNotBlank() } ?: "简评作者",
                    avatarTemplate = author?.avatarTemplate,
                    animatedAvatar = author?.animatedAvatar,
                    trustLevel = author?.trustLevel,
                )
            }
            CompactUserIdentityHeader(
                user = identity,
                modifier = Modifier.clickable(enabled = !author?.username.isNullOrBlank()) {
                    author?.username?.let(onOpenAuthor)
                },
            )
            Text(
                cookedText.ifBlank { "（无文本内容）" },
                style = IosTheme.type.body.copy(fontSize = 17.sp, lineHeight = 25.sp),
                color = IosTheme.colors.label,
                modifier = Modifier.fillMaxWidth(),
            )
            IosDivider(startIndent = 0.dp)
            author?.username?.let { username ->
                BoostActionCard(
                    label = "查看作者资料",
                    icon = "person",
                    onClick = { onOpenAuthor(username) },
                )
            }
            BoostActionCard(
                label = "复制简评文本",
                icon = "copy",
                emphasized = true,
                showsChevron = false,
                onClick = { onCopy(cookedText) },
            )
            if (boost.canFlag) {
                BoostActionCard(
                    label = "举报此简评",
                    icon = "warning",
                    destructive = true,
                    showsChevron = false,
                    onClick = { showFlagDialog = true },
                )
            }
            if (boost.canDelete) {
                BoostActionCard(
                    label = "删除简评",
                    icon = "delete",
                    destructive = true,
                    showsChevron = false,
                    onClick = onDelete,
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }

    if (showFlagDialog) Dialog(onDismissRequest = { showFlagDialog = false }) {
        Column(
            Modifier.fillMaxWidth().widthIn(max = 380.dp).clip(RoundedCornerShape(12.dp))
                .background(IosTheme.colors.card).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("举报简评", style = IosTheme.type.headline, color = IosTheme.colors.label)
            PostFlagReason.entries.forEach { reason ->
                Row(
                    Modifier.fillMaxWidth().clickable { selectedReason = reason }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selectedReason == reason, onClick = { selectedReason = reason })
                    Column {
                        Text(reason.title, style = IosTheme.type.subheadline)
                        Text(reason.description, style = IosTheme.type.caption, color = IosTheme.colors.secondaryLabel)
                    }
                }
            }
            if (selectedReason.requiresMessage) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(500) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("补充说明") },
                    minLines = 2,
                    maxLines = 4,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showFlagDialog = false }) { Text("取消") }
                TextButton(
                    enabled = !selectedReason.requiresMessage || message.isNotBlank(),
                    onClick = {
                        showFlagDialog = false
                        onFlag(selectedReason.id, message.takeIf { selectedReason.requiresMessage })
                    },
                ) { Text("提交") }
            }
        }
    }
}

@Composable
private fun BoostActionCard(
    label: String,
    icon: String,
    onClick: () -> Unit,
    emphasized: Boolean = false,
    destructive: Boolean = false,
    showsChevron: Boolean = true,
) {
    val isDark = IosTheme.colors.isDark
    val foreground = when {
        destructive -> IosTheme.colors.destructive
        emphasized -> if (isDark) Color(0xFFD8EEFF) else Color(0xFF145A8D)
        else -> IosTheme.colors.label
    }
    val background = when {
        destructive -> if (isDark) Color(0xFF301E22) else Color(0xFFFFF0F0)
        emphasized -> if (isDark) Color(0xFF224562) else Color(0xFFE0F1FF)
        else -> if (isDark) Color(0xFF24262D) else Color(0xFFF5F7FA)
    }
    val border = when {
        destructive -> IosTheme.colors.destructive.copy(alpha = if (isDark) 0.58f else 0.38f)
        emphasized -> if (isDark) Color(0xFF5A9EC8) else Color(0xFF9CCAE8)
        else -> IosTheme.colors.separator.copy(alpha = 0.65f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoostActionGlyph(icon = icon, tint = foreground, modifier = Modifier.size(28.dp))
        Text(
            text = label,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = foreground,
            modifier = Modifier.padding(start = 16.dp).weight(1f),
        )
        if (showsChevron) {
            Text(
                text = "›",
                fontSize = 34.sp,
                lineHeight = 28.sp,
                color = IosTheme.colors.secondaryLabel,
            )
        }
    }
}

@Composable
private fun BoostActionGlyph(icon: String, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = size.minDimension * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val width = size.width
        val height = size.height
        when (icon) {
            "person" -> {
                drawCircle(tint, radius = width * 0.19f, center = Offset(width * 0.5f, height * 0.3f))
                val body = Path().apply {
                    moveTo(width * 0.17f, height * 0.84f)
                    cubicTo(width * 0.2f, height * 0.57f, width * 0.8f, height * 0.57f, width * 0.83f, height * 0.84f)
                    close()
                }
                drawPath(body, tint)
            }
            "copy" -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(width * 0.27f, height * 0.2f),
                    size = androidx.compose.ui.geometry.Size(width * 0.52f, height * 0.62f),
                    cornerRadius = CornerRadius(width * 0.08f, width * 0.08f),
                    style = stroke,
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(width * 0.39f, height * 0.1f),
                    size = androidx.compose.ui.geometry.Size(width * 0.22f, height * 0.16f),
                    cornerRadius = CornerRadius(width * 0.05f, width * 0.05f),
                    style = stroke,
                )
            }
            "warning", "delete" -> {
                drawCircle(tint, radius = width * 0.36f)
                drawLine(Color.White, Offset(width * 0.5f, height * 0.3f), Offset(width * 0.5f, height * 0.58f), strokeWidth = width * 0.1f, cap = StrokeCap.Round)
                drawCircle(Color.White, radius = width * 0.055f, center = Offset(width * 0.5f, height * 0.73f))
            }
            else -> Unit
        }
    }
}

/** 向上弹出气泡的 Shape，底部带有指向下方锚点图标的倒三角形指针 */
private class BubbleShape(
    private val cornerRadius: Dp = 22.dp,
    private val arrowWidth: Dp = 14.dp,
    private val arrowHeight: Dp = 7.dp,
    private val arrowCenterXProvider: () -> Float,
) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val arrowW = with(density) { arrowWidth.toPx() }
        val arrowH = with(density) { arrowHeight.toPx() }
        val radius = with(density) { cornerRadius.toPx() }
        val bodyHeight = maxOf(0f, size.height - arrowH)

        val rawCenterX = arrowCenterXProvider()
        val centerX = if (rawCenterX.isNaN() || rawCenterX <= 0f) size.width / 2f else rawCenterX
        val arrowLeft = (centerX - arrowW / 2f).coerceIn(radius, maxOf(radius, size.width - radius - arrowW))
        val arrowRight = arrowLeft + arrowW

        val path = Path().apply {
            moveTo(radius, 0f)
            lineTo(size.width - radius, 0f)
            arcTo(
                Rect(size.width - 2 * radius, 0f, size.width, 2 * radius),
                -90f, 90f, false
            )
            lineTo(size.width, bodyHeight - radius)
            arcTo(
                Rect(size.width - 2 * radius, bodyHeight - 2 * radius, size.width, bodyHeight),
                0f, 90f, false
            )
            lineTo(arrowRight, bodyHeight)
            lineTo(arrowLeft + arrowW / 2f, size.height)
            lineTo(arrowLeft, bodyHeight)
            lineTo(radius, bodyHeight)
            arcTo(
                Rect(0f, bodyHeight - 2 * radius, 2 * radius, bodyHeight),
                90f, 90f, false
            )
            lineTo(0f, radius)
            arcTo(
                Rect(0f, 0f, 2 * radius, 2 * radius),
                180f, 90f, false
            )
            close()
        }
        return Outline.Generic(path)
    }
}

/** 向上弹出的气泡定位计算器，保证在屏幕安全范围内，并回传小三角相对于气泡的 X 坐标 */
private class BubblePopupPositionProvider(
    private val onArrowCenterCalculated: (Float) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val anchorCenterX = anchorBounds.left + anchorBounds.width / 2
        val margin = 24
        val idealX = anchorCenterX - popupContentSize.width / 2
        val x = idealX.coerceIn(margin, maxOf(margin, windowSize.width - popupContentSize.width - margin))
        val relativeArrowX = (anchorCenterX - x).toFloat()
        onArrowCenterCalculated(relativeArrowX)

        var y = anchorBounds.top - popupContentSize.height - 6
        if (y < 40) {
            y = anchorBounds.bottom + 6
        }
        return IntOffset(x, y)
    }
}

/**
 * 长按心形点赞弹出的向上气泡反应选择条。
 * 还原贴吧/微信向上气泡风格，带指向心形的小箭头。
 * 如果一行放不下（超过 6 个），自动排成两行。
 */
@Composable
private fun ReactionBubblePopup(
    reactions: List<String>,
    emojiUrls: Map<String, String>,
    busy: Boolean,
    onReact: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var arrowCenterX by remember { mutableFloatStateOf(0f) }
    val bubbleShape = remember {
        BubbleShape(cornerRadius = 22.dp, arrowWidth = 14.dp, arrowHeight = 7.dp) { arrowCenterX }
    }
    val positionProvider = remember {
        BubblePopupPositionProvider { arrowCenterX = it }
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val allReactions = (listOf("heart") + reactions).distinct()
        // 一行最多放 6 个，如果总数 > 6 则分两行展示
        val isTwoRows = allReactions.size > 6
        val perRowCount = if (isTwoRows) ((allReactions.size + 1) / 2).coerceIn(4, 6) else allReactions.size

        Box(
            modifier = Modifier
                .shadow(elevation = 6.dp, shape = bubbleShape, clip = false)
                .clip(bubbleShape)
                .background(IosTheme.colors.card)
                .padding(bottom = 7.dp) // 预留底部小三角高度
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (isTwoRows) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    allReactions.chunked(perRowCount).forEach { rowList ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            rowList.forEach { reaction ->
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .clickable(enabled = !busy) {
                                            onDismiss()
                                            onReact(reaction)
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    ReactionIcon(reaction, emojiUrls, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    allReactions.forEach { reaction ->
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable(enabled = !busy) {
                                    onDismiss()
                                    onReact(reaction)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            ReactionIcon(reaction, emojiUrls, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 点击 boost 简评弹出的页内底部输入抽屉。
 * 紧贴软键盘上方，避免原悬浮气泡被弹出键盘遮挡或越界。
 */
@Composable
private fun BoostSheet(
    target: BoostTarget,
    draft: String,
    onDraftChange: (String) -> Unit,
    busy: Boolean,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = true) {
        if (!busy) onDismiss()
    }
    val focus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var showEmojiPicker by rememberSaveable { mutableStateOf(false) }
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(draft, selection = TextRange(draft.length)))
    }

    LaunchedEffect(draft) {
        if (draft != textFieldValue.text) {
            val newSelection = if (textFieldValue.selection.end <= draft.length) {
                textFieldValue.selection
            } else {
                TextRange(draft.length)
            }
            textFieldValue = TextFieldValue(draft, selection = newSelection)
        }
    }

    val insertToken = { token: String ->
        val currentText = textFieldValue.text
        val start = textFieldValue.selection.min.coerceIn(0, currentText.length)
        val end = textFieldValue.selection.max.coerceIn(0, currentText.length)
        val newText = currentText.replaceRange(start, end, token)
        if (newText.length <= BoostLength.RAW_INPUT_CAP) {
            val newCursor = start + token.length
            textFieldValue = TextFieldValue(newText, selection = TextRange(newCursor))
            onDraftChange(newText)
        }
    }
    val length = remember(draft) { measureBoost(draft) }
    val over = length.overVisible || length.overEmoji
    val canSend = length.submittable && !busy

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (!busy) onDismiss() },
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(IosTheme.colors.card)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "简评 #${target.postNumber} · @${target.username}",
                    style = IosTheme.type.headline,
                    fontWeight = FontWeight.SemiBold,
                    color = IosTheme.colors.label,
                    maxLines = 1,
                )
                val closeColor = IosTheme.colors.secondaryLabel
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !busy, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(11.dp)) {
                        drawLine(closeColor, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = 1.8f, cap = StrokeCap.Round)
                        drawLine(closeColor, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 1.8f, cap = StrokeCap.Round)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 76.dp, max = 120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(IosTheme.colors.fieldBackground)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        if (newValue.text.length <= BoostLength.RAW_INPUT_CAP) {
                            textFieldValue = newValue
                            if (newValue.text != draft) {
                                onDraftChange(newValue.text)
                            }
                        }
                    },
                    textStyle = IosTheme.type.body.copy(color = IosTheme.colors.label),
                    cursorBrush = SolidColor(IosTheme.colors.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                    decorationBox = { field ->
                        Box {
                            if (textFieldValue.text.isEmpty()) {
                                Text("写一句简评…", style = IosTheme.type.body, color = IosTheme.colors.tertiaryLabel)
                            }
                            field()
                        }
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (showEmojiPicker) IosTheme.colors.accent.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable(enabled = !busy) {
                                val next = !showEmojiPicker
                                showEmojiPicker = next
                                if (next) {
                                    keyboardController?.hide()
                                } else {
                                    runCatching { focus.requestFocus() }
                                    keyboardController?.show()
                                }
                            }
                            .semantics { contentDescription = "选择表情" },
                        contentAlignment = Alignment.Center,
                    ) {
                        ComposerEmojiIcon(
                            tint = when {
                                busy -> IosTheme.colors.tertiaryLabel
                                showEmojiPicker -> IosTheme.colors.accent
                                else -> IosTheme.colors.label
                            },
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = "可见 ${length.visible}/${BoostLength.MAX_VISIBLE} · 表情 ${length.emoji}/${BoostLength.MAX_EMOJI}",
                        style = IosTheme.type.caption,
                        color = if (over) IosTheme.colors.destructive else IosTheme.colors.secondaryLabel,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clickable(enabled = !busy, onClick = onDismiss)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("取消", style = IosTheme.type.subheadline, color = IosTheme.colors.secondaryLabel)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (canSend) IosTheme.colors.accent else IosTheme.colors.accent.copy(alpha = 0.35f))
                            .clickable(enabled = canSend, onClick = onSend)
                            .padding(horizontal = 20.dp, vertical = 7.dp),
                    ) {
                        Text("发送", style = IosTheme.type.subheadline.copy(fontWeight = FontWeight.Medium), color = Color.White)
                    }
                }
            }

            if (showEmojiPicker) {
                QuickEmojiPicker(enabled = !busy) { emoji ->
                    insertToken(emoji)
                }
            }
        }
    }

    LaunchedEffect(target.postId) {
        runCatching { focus.requestFocus() }
    }
}

@Composable
private fun ReactionIcon(name: String, urls: Map<String, String>, modifier: Modifier = Modifier.size(20.dp)) {
    AsyncImage(urls[name] ?: "https://linux.do/images/emoji/twemoji/$name.png?v=15", name, modifier = modifier)
}

internal enum class PostAction { Solution, Like, Boost, Reply, Edit, Delete, Bookmark, CopyLink, Flag, More }

@Composable
internal fun PostActionIcon(
    action: PostAction,
    selected: Boolean,
    tint: Color? = null,
    modifier: Modifier = Modifier.size(20.dp),
) {
    val color = tint ?: when {
        action == PostAction.Solution && selected -> Color(0xFF30B85B)
        action == PostAction.Delete -> IosTheme.colors.destructive
        action == PostAction.Bookmark && selected -> IosTheme.colors.accent
        selected -> IosTheme.colors.accent
        else -> IosTheme.colors.secondaryLabel
    }
    val cardBg = IosTheme.colors.card
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val shape = Path()
            when (action) {
                PostAction.Solution -> {
                    rotate(-45f, pivot = Offset(12f, 12f)) {
                        // 水平向左钥匙：右侧圆环头部，向左下伸出钥匙柄与钥匙齿
                        val outerRadius = 4.8f
                        val innerRadius = 2.3f
                        val headCenter = Offset(16f, 12f)
                        val keyPath = Path().apply {
                            moveTo(11.5f, 10.4f)
                            lineTo(4.5f, 10.4f)
                            arcTo(
                                Rect(3f, 10.4f, 6f, 13.6f),
                                -90f, -180f, false
                            )
                            lineTo(6.5f, 13.6f)
                            lineTo(6.5f, 16.2f)
                            lineTo(8f, 16.2f)
                            lineTo(8f, 13.6f)
                            lineTo(9.5f, 13.6f)
                            lineTo(9.5f, 16.2f)
                            lineTo(11f, 16.2f)
                            lineTo(11f, 13.6f)
                            lineTo(11.5f, 13.6f)
                        }
                        drawCircle(color, outerRadius, headCenter, style = Stroke(1.7f))
                        drawCircle(color, innerRadius, headCenter, style = Stroke(1.5f))
                        drawPath(keyPath, color, style = Stroke(1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                PostAction.Like -> {
                    shape.moveTo(12f, 21.3f)
                    shape.cubicTo(10.2f, 19.5f, 3.2f, 13.4f, 3.2f, 8.2f)
                    shape.cubicTo(3.2f, 3.5f, 9.8f, 2.2f, 12f, 6.6f)
                    shape.cubicTo(14.2f, 2.2f, 20.8f, 3.5f, 20.8f, 8.2f)
                    shape.cubicTo(20.8f, 13.4f, 13.8f, 19.5f, 12f, 21.3f)
                    shape.close()
                    if (selected) {
                        drawPath(shape, color)
                    } else {
                        drawPath(shape, color, style = Stroke(1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                PostAction.Boost -> {
                    rotate(45f, pivot = Offset(12f, 12f)) {
                        val rocket = Path().apply {
                            moveTo(12f, 3.5f)
                            cubicTo(14.8f, 7.5f, 15f, 12f, 14.5f, 14.5f)
                            lineTo(18.5f, 16.5f)
                            lineTo(18.5f, 18.5f)
                            lineTo(14f, 17f)
                            lineTo(13.2f, 18.8f)
                            lineTo(10.8f, 18.8f)
                            lineTo(10f, 17f)
                            lineTo(5.5f, 18.5f)
                            lineTo(5.5f, 16.5f)
                            lineTo(9.5f, 14.5f)
                            cubicTo(9f, 12f, 9.2f, 7.5f, 12f, 3.5f)
                            close()
                        }
                        drawPath(rocket, color)
                        drawCircle(if (selected) Color.White else cardBg, radius = 1.6f, center = Offset(12f, 9f))
                    }
                }
                // Discourse PC 原生实心左弯回复箭头 (FontAwesome fa-reply 矢量造型)
                PostAction.Reply -> {
                    shape.moveTo(3.5f, 11.5f)
                    shape.lineTo(10.5f, 5f)
                    shape.lineTo(10.5f, 8.8f)
                    shape.cubicTo(16.5f, 8.8f, 20.5f, 11.5f, 21f, 18.5f)
                    shape.cubicTo(19.2f, 15f, 15.5f, 13.5f, 10.5f, 13.8f)
                    shape.lineTo(10.5f, 18f)
                    shape.close()
                    drawPath(shape, color)
                }
                // 铅笔
                PostAction.Edit -> {
                    shape.moveTo(4f, 20f); shape.lineTo(8f, 19f); shape.lineTo(20f, 7f)
                    shape.lineTo(17f, 4f); shape.lineTo(5f, 16f); shape.close()
                    drawPath(shape, color, style = Stroke(1.7f))
                    drawLine(color, Offset(15f, 6f), Offset(18f, 9f), 1.7f)
                }
                // 垃圾桶
                PostAction.Delete -> {
                    shape.moveTo(6f, 7f); shape.lineTo(18f, 7f); shape.lineTo(17f, 21f)
                    shape.lineTo(7f, 21f); shape.close()
                    drawPath(shape, color, style = Stroke(1.7f))
                    drawLine(color, Offset(4f, 7f), Offset(20f, 7f), 1.7f)
                    drawLine(color, Offset(10f, 4f), Offset(14f, 4f), 1.7f)
                    drawLine(color, Offset(10f, 4f), Offset(10f, 7f), 1.7f)
                    drawLine(color, Offset(14f, 4f), Offset(14f, 7f), 1.7f)
                }
                // 丝带书签 (Ribbon)
                PostAction.Bookmark -> {
                    shape.moveTo(6.5f, 4.5f)
                    shape.lineTo(17.5f, 4.5f)
                    shape.lineTo(17.5f, 19.5f)
                    shape.lineTo(12f, 15.5f)
                    shape.lineTo(6.5f, 19.5f)
                    shape.close()
                    if (selected) {
                        drawPath(shape, color)
                    } else {
                        drawPath(shape, color, style = Stroke(1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                // 链接链环
                PostAction.CopyLink -> {
                    rotate(-45f, pivot = Offset(12f, 12f)) {
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(4f, 9f),
                            size = androidx.compose.ui.geometry.Size(8f, 6f),
                            cornerRadius = CornerRadius(3f, 3f),
                            style = Stroke(1.7f),
                        )
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(12f, 9f),
                            size = androidx.compose.ui.geometry.Size(8f, 6f),
                            cornerRadius = CornerRadius(3f, 3f),
                            style = Stroke(1.7f),
                        )
                        drawLine(color, Offset(9f, 12f), Offset(15f, 12f), 1.7f)
                    }
                }
                // 举报旗帜
                PostAction.Flag -> {
                    shape.moveTo(6.5f, 4.5f)
                    shape.lineTo(17.5f, 4.5f)
                    shape.lineTo(14.5f, 9f)
                    shape.lineTo(17.5f, 13.5f)
                    shape.lineTo(6.5f, 13.5f)
                    shape.close()
                    if (selected) {
                        drawPath(shape, color)
                    } else {
                        drawPath(shape, color, style = Stroke(1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    drawLine(color, Offset(6.5f, 4.5f), Offset(6.5f, 20.5f), 1.7f, cap = StrokeCap.Round)
                }
                // 更多操作 (•••)
                PostAction.More -> {
                    val r = 1.8f
                    drawCircle(color, r, Offset(5.5f, 12f))
                    drawCircle(color, r, Offset(12f, 12f))
                    drawCircle(color, r, Offset(18.5f, 12f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TiebaPostActionButton(
    label: String,
    action: PostAction,
    selected: Boolean = false,
    color: Color = IosTheme.colors.secondaryLabel,
    enabled: Boolean = true,
    compact: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val activeTint = when (action) {
        PostAction.Like -> Color(0xFFFF3B30)
        PostAction.Solution -> Color(0xFF30B85B)
        else -> IosTheme.colors.accent
    }
    val buttonTint = if (selected) activeTint else color
    val buttonBg = if (selected) activeTint.copy(alpha = 0.12f) else Color.Transparent

    Box(
        modifier = Modifier
            .size(if (compact) 30.dp else 34.dp)
            .clip(CircleShape)
            .background(buttonBg)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                }
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        PostActionIcon(
            action = action,
            selected = selected,
            tint = buttonTint,
            modifier = Modifier.size(if (compact) 18.dp else 20.dp),
        )
    }
}

@Composable
private fun CookedContent(
    blocks: List<CookedBlock>,
    onPicture: (String, String) -> Unit,
    compact: Boolean = false,
    subReply: Boolean = false,
    pollPost: Post? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact || subReply) 6.dp else 10.dp)) {
        blocks.forEach { block ->
            when (block) {
                is CookedBlock.Paragraph -> RichParagraph(block.runs, compact = compact, subReply = subReply)
                is CookedBlock.Heading -> CookedHeading(block)
                is CookedBlock.Video -> VideoCard(block)
                is CookedBlock.Onebox -> OneboxCard(block)
                is CookedBlock.Poll -> PollCard(block, pollPost?.id)
                is CookedBlock.Code -> {
                    val codeResetKey = LocalSelectionResetKey.current
                    key(codeResetKey) {
                        DecryptableSelection {
                            Text(block.text, fontFamily = FontFamily.Monospace, style = IosTheme.type.footnote,
                                modifier = Modifier.fillMaxWidth().background(
                                    if (subReply) IosTheme.colors.card else IosTheme.colors.fieldBackground,
                                ).horizontalScroll(rememberScrollState()).padding(10.dp))
                        }
                    }
                }
                is CookedBlock.Quote -> Row(
                    Modifier.fillMaxWidth().background(
                        if (subReply) IosTheme.colors.card else IosTheme.colors.fieldBackground,
                    ),
                ) {
                    Box(Modifier.width(3.dp).height(28.dp).background(IosTheme.colors.separator))
                    Box(Modifier.weight(1f).padding(10.dp)) {
                        CookedContent(block.blocks, onPicture, compact = compact, subReply = subReply, pollPost = pollPost)
                    }
                }
                is CookedBlock.Picture -> {
                    val maxPortraitHeight = if (subReply) 220.dp else 320.dp
                    val ratioModifier = if (block.width > 0 && block.height > 0) {
                        val rawRatio = block.width.toFloat() / block.height.toFloat()
                        if (rawRatio >= 0.85f) {
                            // 横图或近似方图：撑满宽度，高度由比例唯一确定，不加 heightIn 以避免与 fillMaxWidth 产生无解约束导致溢出重叠
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(rawRatio.coerceAtMost(4f))
                                .clip(RoundedCornerShape(8.dp))
                        } else {
                            // 竖屏长截图（如手机截图）：固定高度上限并按比例自适应宽度（左对齐，与网页端一致），既锁死高度防滚动抖动，又绝不越界遮挡文字
                            Modifier
                                .height(maxPortraitHeight)
                                .aspectRatio(rawRatio.coerceAtLeast(0.25f), matchHeightConstraintsFirst = true)
                                .clip(RoundedCornerShape(8.dp))
                        }
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp, max = maxPortraitHeight)
                            .clip(RoundedCornerShape(8.dp))
                    }
                    var currentUrl by remember(block.url) { mutableStateOf(block.url) }
                    var isError by remember(block.url) { mutableStateOf(false) }
                    var isLoading by remember(block.url) { mutableStateOf(true) }
                    var retryCount by remember(block.url) { mutableIntStateOf(0) }
                    var autoRetryCount by remember(block.url) { mutableIntStateOf(0) }
                    var pendingAutoRetry by remember(block.url) { mutableStateOf(false) }
                    if (pendingAutoRetry) {
                        LaunchedEffect(autoRetryCount) {
                            awaitImageRetrySlot(autoRetryCount)
                            pendingAutoRetry = false
                            autoRetryCount++
                            retryCount++
                        }
                    }

                    val context = LocalContext.current
                    val imageRequest = remember(currentUrl, retryCount) {
                        ImageRequest.Builder(context)
                            .data(currentUrl)
                            .crossfade(true)
                            .setParameter("retry", retryCount, memoryCacheKey = null)
                            .build()
                    }

                    Box(
                        modifier = ratioModifier.background(IosTheme.colors.fieldBackground),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isLoading && !isError) {
                            IosActivityIndicator(diameter = 20.dp)
                        }
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = block.description,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { onPicture(block.originalUrl, currentUrl) },
                            onLoading = {
                                isLoading = true
                                isError = false
                            },
                            onSuccess = {
                                isLoading = false
                                isError = false
                            },
                            onError = { errorState ->
                                val err = errorState.result.throwable
                                android.util.Log.w("CookedContent", "Failed to load image: $currentUrl, reason: ${err.message}")
                                if (isTransientImageError(err) && autoRetryCount < IMAGE_AUTO_RETRY_LIMIT) {
                                    // 门禁冷却 / 429 / 网络抖动：换原图只会再撞一次，原地等冷却结束后重试同一地址
                                    pendingAutoRetry = true
                                } else if (currentUrl == block.url && block.url != block.originalUrl) {
                                    // 缩略图拉取失败（如 Discourse 未生成或 404/超时），自动透明降级尝试原图！
                                    currentUrl = block.originalUrl
                                    isLoading = true
                                } else {
                                    isLoading = false
                                    isError = true
                                }
                            },
                        )
                        if (isError) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(8.dp),
                            ) {
                                Text("图片未显示", style = IosTheme.type.caption, color = IosTheme.colors.secondaryLabel)
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = "重新加载",
                                        style = IosTheme.type.caption,
                                        color = Color(0xFF007AFF),
                                        modifier = Modifier.clickable {
                                            isError = false
                                            isLoading = true
                                            currentUrl = block.url
                                            retryCount++
                                        },
                                    )
                                    Text(
                                        text = "查看大图",
                                        style = IosTheme.type.caption,
                                        color = Color(0xFF007AFF),
                                        modifier = Modifier.clickable {
                                            onPicture(block.originalUrl, currentUrl)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                is CookedBlock.ListBlock -> block.entries.forEachIndexed { index, entry ->
                    Row {
                        Text(
                            text = block.start?.let { "${it + index}. " } ?: "• ",
                            style = if (subReply) IosTheme.type.subheadline.copy(fontSize = 14.sp, lineHeight = 19.5.sp)
                                    else IosTheme.type.body.copy(fontSize = 15.5.sp, lineHeight = 22.sp),
                            modifier = Modifier.widthIn(min = 22.dp),
                        )
                        Box(Modifier.weight(1f)) {
                            CookedContent(entry, onPicture, compact = compact, subReply = subReply, pollPost = pollPost)
                        }
                    }
                }
                CookedBlock.Divider -> IosFullDivider()
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun CookedHeading(heading: CookedBlock.Heading) {
    val requester = remember(heading.anchor) { BringIntoViewRequester() }
    val target = LocalTocTarget.current
    LaunchedEffect(target, heading.anchor) {
        if (target == heading.anchor) requester.bringIntoView()
    }
    Text(
        text = heading.title,
        style = when (heading.level) {
            1 -> IosTheme.type.title3
            2 -> IosTheme.type.title3
            else -> IosTheme.type.headline
        },
        color = IosTheme.colors.label,
        modifier = Modifier.fillMaxWidth().bringIntoViewRequester(requester).padding(vertical = 4.dp),
    )
}

@Composable
private fun VideoCard(video: CookedBlock.Video) {
    val openUrl = LocalOpenExternalUrl.current
    Box(
        Modifier.fillMaxWidth().height(180.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(IosTheme.colors.fieldBackground)
            .clickable { openUrl(video.url) },
        contentAlignment = Alignment.Center,
    ) {
        video.posterUrl?.let { url ->
            AsyncImage(url, video.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Column(
            Modifier.align(Alignment.Center).clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.66f)).padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text("▶", color = Color.White, style = IosTheme.type.title3)
            Text(video.title.ifBlank { "播放视频" }, color = Color.White, style = IosTheme.type.caption)
        }
    }
}

@Composable
private fun OneboxCard(onebox: CookedBlock.Onebox) {
    val openUrl = LocalOpenExternalUrl.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(IosTheme.colors.fieldBackground)
            .clickable { openUrl(onebox.url) }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(onebox.title, style = IosTheme.type.subheadline, fontWeight = FontWeight.SemiBold, color = IosTheme.colors.label)
        onebox.description?.let {
            Text(it, style = IosTheme.type.footnote, color = IosTheme.colors.secondaryLabel, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Text(Uri.parse(onebox.url).host.orEmpty(), style = IosTheme.type.caption, color = IosTheme.colors.accent, maxLines = 1)
    }
}

@Composable
private fun PollCard(poll: CookedBlock.Poll, postId: Long?) {
    var selected by remember(poll.name, poll.userVotes) { mutableStateOf(poll.userVotes) }
    val busy = postId?.let(LocalPollBusy.current) == true
    val error = postId?.let(LocalPollError.current)
    val voter = LocalVotePoll.current
    val isMultiple = poll.type == "multiple"
    val canVote = postId != null && poll.status == "open" && poll.type in setOf("regular", "multiple") && !busy
    val showResults = when (poll.results) {
        "always" -> true
        "on_vote" -> poll.userVotes.isNotEmpty()
        "on_close" -> poll.status == "closed"
        else -> false
    }
    val totalVotes = maxOf(poll.voters, poll.options.sumOf { it.votes })

    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = IosTheme.colors.fieldBackground),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(poll.title, style = IosTheme.type.subheadline, fontWeight = FontWeight.SemiBold, color = IosTheme.colors.label, modifier = Modifier.weight(1f))
                Text(if (isMultiple) "多选" else if (poll.type == "regular") "单选" else poll.type, style = IosTheme.type.caption, color = IosTheme.colors.secondaryLabel)
            }
            poll.options.forEach { option ->
                val checked = option.id in selected
                Row(
                    Modifier.fillMaxWidth().then(
                        if (canVote) Modifier.clickable {
                            selected = if (isMultiple) {
                                if (checked) selected - option.id else selected + option.id
                            } else setOf(option.id)
                        } else Modifier,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isMultiple) Checkbox(checked = checked, onCheckedChange = { value ->
                        if (canVote) selected = if (value) selected + option.id else selected - option.id
                    }, enabled = canVote)
                    else RadioButton(selected = checked, onClick = {
                        if (canVote) selected = setOf(option.id)
                    }, enabled = canVote)
                    Column(Modifier.weight(1f).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(option.title, style = IosTheme.type.footnote, color = IosTheme.colors.label, modifier = Modifier.weight(1f))
                            if (showResults) {
                                val percent = if (totalVotes == 0) 0 else option.votes * 100 / totalVotes
                                Text("$percent%", style = IosTheme.type.caption, color = IosTheme.colors.secondaryLabel)
                            }
                        }
                        if (showResults) {
                            val fraction = if (totalVotes == 0) 0f else option.votes.toFloat() / totalVotes
                            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(IosTheme.colors.separator)) {
                                Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(IosTheme.colors.accent))
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${poll.voters} 人参与", style = IosTheme.type.caption, color = IosTheme.colors.secondaryLabel, modifier = Modifier.weight(1f))
                if (canVote) {
                    Button(enabled = selected.isNotEmpty(), onClick = { postId?.let { voter(it, poll.name, selected.toList()) } }) {
                        Text(if (busy) "提交中" else "投票")
                    }
                }
            }
            error?.let { Text(it, style = IosTheme.type.caption, color = Color(0xFFFF3B30)) }
        }
    }
}

@Composable
private fun RichParagraph(runs: List<InlineText>, compact: Boolean = false, subReply: Boolean = false) {
    val colors = IosTheme.colors
    val context = LocalContext.current
    val openTopic = LocalOpenTopic.current
    val updatedOpenTopic by rememberUpdatedState(openTopic)
    val openExternal = LocalOpenExternalUrl.current
    val updatedOpenExternal by rememberUpdatedState(openExternal)
    val scope = rememberCoroutineScope()
    val resetKey = LocalSelectionResetKey.current
    val annotated = remember(runs, colors, context, openExternal) {
        buildAnnotatedString {
            runs.forEach { run ->
                val emoji = run.emojiUrl
                if (emoji != null) {
                    appendInlineContent(emoji, run.text.ifBlank { "表情" })
                    return@forEach
                }
                withStyle(SpanStyle(
                    fontWeight = if (run.bold) FontWeight.Bold else null,
                    fontStyle = if (run.italic) FontStyle.Italic else null,
                    fontFamily = if (run.code) FontFamily.Monospace else null,
                    background = if (run.code) colors.fieldBackground else Color.Unspecified,
                )) {
                    val link = run.href
                    if (link == null) append(run.text) else withLink(LinkAnnotation.Url(
                        link, TextLinkStyles(style = SpanStyle(color = colors.accent)),
                        linkInteractionListener = {
                            if (run.isAttachment) {
                                Toast.makeText(context, "正在下载文件…", Toast.LENGTH_SHORT).show()
                                scope.launch {
                                    val result = saveMediaToPublicStorage(
                                        context = context,
                                        url = link,
                                        suggestedName = run.text.trim(),
                                        asImage = false,
                                    )
                                    Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                                }
                            } else {
                                val internalTarget = parseInternalTopicUrl(link)
                                val opener = updatedOpenTopic
                                if (internalTarget != null && opener != null) {
                                    val rawLabel = run.text.trim()
                                    val titleHint = if (rawLabel.isNotBlank() &&
                                        !rawLabel.startsWith("http://", ignoreCase = true) &&
                                        !rawLabel.startsWith("https://", ignoreCase = true)
                                    ) {
                                        rawLabel
                                    } else {
                                        "话题 #${internalTarget.topicId}"
                                    }
                                    opener(internalTarget.topicId, titleHint, internalTarget.postNumber)
                                } else {
                                    val uri = runCatching { android.net.Uri.parse(link) }.getOrNull()
                                    val host = uri?.host?.lowercase()?.trimEnd('.').orEmpty()
                                    if (host.isNotBlank() && host !in TRUSTED_FORUM_HOSTS) {
                                        openExternal(context, link)
                                    } else {
                                        updatedOpenExternal(link)
                                    }
                                }
                            }
                        },
                    )) { append(run.text) }
                }
            }
        }
    }
    val inline = runs.mapNotNull { it.emojiUrl }.distinct().associateWith { url ->
        val size = when {
            compact -> 14.sp
            subReply -> 16.5.sp
            else -> 18.5.sp
        }
        InlineTextContent(Placeholder(size, size, PlaceholderVerticalAlign.TextCenter)) {
            AsyncImage(url, "表情", modifier = Modifier.fillMaxSize())
        }
    }
    key(resetKey) {
        when {
            compact -> Text(annotated, style = IosTheme.type.caption, inlineContent = inline)
            subReply -> DecryptableSelection {
                Text(
                    annotated,
                    style = IosTheme.type.subheadline.copy(fontSize = 14.sp, lineHeight = 19.5.sp),
                    inlineContent = inline,
                )
            }
            else -> DecryptableSelection {
                Text(
                    annotated,
                    style = IosTheme.type.body.copy(fontSize = 15.5.sp, lineHeight = 22.sp),
                    inlineContent = inline,
                )
            }
        }
    }
}

data class DecodedResult(
    val rawMatched: String,
    val cleanedBase64: String,
    val plainText: String,
    val confidence: Int,
    val method: String = "Base64",
    val postNumber: Int = 1,
)

object PostCryptoScanner {
    private val COMMON_HANZI_REGEX = Regex("[\\u4e00-\\u9fa5]{2,}")
    private val HIGH_VALUE_PREFIX = Regex("^(https?://|magnet:\\?xt=|ed2k://|tg://|weixin://|ftp://|[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})", RegexOption.IGNORE_CASE)
    private val COMMON_NETDISK_REGEX = Regex("(pan\\.baidu\\.com|aliyun(drive)?|quark|115\\.com|lanzou|123pan|t\\.me/|提取码|解压码|车牌号)", RegexOption.IGNORE_CASE)
    private val CONTEXT_HINT_REGEX = Regex("(base64|b64|解码|解密|加密|神秘代码|车牌|提取码|密码|卡密|口令|key|token|pass|clinepass|cookie|福利|自取|兑换码|激活码)", RegexOption.IGNORE_CASE)
    private val BRACKET_NOISE_REGEX = Regex("(\\([^)]*[\\u4e00-\\u9fa5]+[^)]*\\))|（.*?）|【.*?】|\\[[^\\]]*[\\u4e00-\\u9fa5]+[^\\]]*\\]")
    private val DYNAMIC_RULE_REGEX = Regex("(?:去掉|删掉|删除|去除)[所有一切中间的\\s]*[\"'“‘【\\[]?([a-zA-Z0-9]{1,8})[\"'”’】\\]]?")
    private val CODE_KEYWORDS_REGEX = Regex("\\b(function|import|val|var|public|private|def|class|return|const|interface)\\b")
    // 匹配常规 Base64 以及跨换行/空格连接的 Base64 连续块
    private val MULTILINE_BASE64_REGEX = Regex("""[A-Za-z0-9+/=_-]{4,}(?:\s+[A-Za-z0-9+/=_-]{2,})+""")
    private val CANDIDATE_REGEX = Regex("""[A-Za-z0-9+/=_-]{4,}(?:[^A-Za-z0-9+/=_-]{1,4}[A-Za-z0-9+/=_-]{2,})+|[A-Za-z0-9+/=_-]{8,}""")

    fun stripBracketsAndNoise(text: String): String {
        val withoutBrackets = BRACKET_NOISE_REGEX.replace(text, "")
        return withoutBrackets.replace(Regex("[^A-Za-z0-9+/=_-]"), "")
    }

    fun scanPosts(posts: List<Post>, topicTitle: String? = null): List<DecodedResult> {
        if (posts.isEmpty()) return emptyList()
        val results = mutableListOf<DecodedResult>()
        val sortedPosts = posts.sortedBy { if (it.postNumber == 1) 0 else it.postNumber }

        for (post in sortedPosts) {
            val cooked = post.cooked.takeIf { it.isNotBlank() } ?: continue
            val doc = runCatching { Jsoup.parse(cooked) }.getOrNull() ?: continue

            // 1. 区域隔离与预处理
            doc.select("a, img").remove()
            doc.select("pre, code").forEach { el ->
                val codeText = el.text()
                if (CODE_KEYWORDS_REGEX.containsMatchIn(codeText) || codeText.lines().size > 3) {
                    el.remove()
                }
            }

            val postText = doc.text()
            val hasContextHint = CONTEXT_HINT_REGEX.containsMatchIn(postText) ||
                (topicTitle != null && CONTEXT_HINT_REGEX.containsMatchIn(topicTitle))

            val dynamicNoises = DYNAMIC_RULE_REGEX.findAll(postText)
                .map { it.groupValues[1] }
                .filter { it.isNotEmpty() }
                .toSet()

            val wholeText = doc.wholeText()

            // 收集所有候选 Base64 字串集合：
            // A. 跨换行/空格拆分的 Base64 连续块（支持换行折叠，如 76 字符换行）
            val rawCandidates = mutableSetOf<String>()
            MULTILINE_BASE64_REGEX.findAll(wholeText).forEach { match ->
                val compact = match.value.filterNot(Char::isWhitespace)
                if (compact.length >= 8) {
                    rawCandidates.add(compact)
                }
            }

            // B. 逐行提取（常规行内模式以及动态去混淆模式）
            for (line in wholeText.lines()) {
                val lineTrimmed = line.trim()
                if (lineTrimmed.length < 8) continue

                var processedLine = BRACKET_NOISE_REGEX.replace(lineTrimmed, "")
                for (noise in dynamicNoises) {
                    processedLine = processedLine.replace(noise, "")
                }

                for (match in CANDIDATE_REGEX.findAll(processedLine)) {
                    val raw = match.value
                    if (raw.startsWith("http", ignoreCase = true) || raw.all { it.isDigit() }) continue
                    rawCandidates.add(raw)
                }

                // URL 编码候选识别
                val urlEncodedRegex = Regex("(?:%[0-9a-fA-F]{2}){2,}")
                urlEncodedRegex.findAll(processedLine).forEach { match ->
                    val raw = match.value
                    val decoded = runCatching { URLDecoder.decode(raw, StandardCharsets.UTF_8.name()) }.getOrNull()
                    if (decoded != null && decoded != raw && isHumanReadableStrict(decoded)) {
                        val score = evaluatePlainText(decoded, hasContextHint, raw.length)
                        if (score >= 40) {
                            results.add(
                                DecodedResult(
                                    rawMatched = raw,
                                    cleanedBase64 = raw,
                                    plainText = decoded,
                                    confidence = score,
                                    method = "URL解码",
                                    postNumber = post.postNumber,
                                )
                            )
                        }
                    }
                }
            }

            // 对所有 Base64 候选字串进行严格 UTF-8 解密与打分
            for (raw in rawCandidates) {
                val cleaned = raw.replace(Regex("[^A-Za-z0-9+/=_-]"), "")
                if (cleaned.length < 8) continue

                // 纯 Hex 校验，若是纯 Hex 且较长，优先尝试 Hex 还原
                if (cleaned.length >= 16 && cleaned.length % 2 == 0 &&
                    cleaned.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                    val hexBytes = runCatching {
                        ByteArray(cleaned.length / 2) { i ->
                            cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                        }
                    }.getOrNull()
                    if (hexBytes != null) {
                        val decodedHex = decodeUtf8Strict(hexBytes)
                        if (decodedHex != null && isHumanReadableStrict(decodedHex)) {
                            val score = evaluatePlainText(decodedHex, hasContextHint, cleaned.length)
                            if (score >= 40) {
                                results.add(
                                    DecodedResult(
                                        rawMatched = raw,
                                        cleanedBase64 = cleaned,
                                        plainText = decodedHex,
                                        confidence = score,
                                        method = "Hex",
                                        postNumber = post.postNumber,
                                    )
                                )
                                continue
                            }
                        }
                    }
                }

                val plainText = tryStrictDecodeBase64(cleaned) ?: continue
                val score = evaluatePlainText(plainText, hasContextHint, cleaned.length)
                if (score >= 40) {
                    results.add(
                        DecodedResult(
                            rawMatched = raw,
                            cleanedBase64 = cleaned,
                            plainText = plainText,
                            confidence = score,
                            method = if (raw != cleaned) "Base64 (去混淆)" else "Base64",
                            postNumber = post.postNumber,
                        )
                    )
                }
            }
        }

        return results.distinctBy { it.plainText }
    }

    private fun tryStrictDecodeBase64(input: String): String? {
        var normalized = input.replace('-', '+').replace('_', '/').trimEnd('=')
        if (normalized.length % 4 == 1) return null
        while (normalized.length % 4 != 0) {
            normalized += "="
        }

        val decodedBytes = try {
            android.util.Base64.decode(normalized, android.util.Base64.DEFAULT)
        } catch (_: Exception) {
            return null
        }
        if (decodedBytes.size < 4) return null

        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)

        val decodedStr = try {
            decoder.decode(ByteBuffer.wrap(decodedBytes)).toString().trim()
        } catch (_: Exception) {
            return null
        }

        if (decodedStr.any { (it.code < 0x20 && it != '\n' && it != '\r' && it != '\t') || it.code == 0x7F }) {
            return null
        }
        if (decodedStr == input) return null

        return decodedStr
    }

    private fun isHumanReadableStrict(text: String): Boolean {
        if (text.isBlank() || text.contains('\uFFFD')) return false
        return !text.any { (it.code < 0x20 && it != '\n' && it != '\r' && it != '\t') || it.code == 0x7F }
    }

    fun evaluatePlainText(plain: String, hasContextHint: Boolean, cipherLength: Int = 0): Int {
        var score = 0
        if (HIGH_VALUE_PREFIX.containsMatchIn(plain)) score += 80
        if (COMMON_NETDISK_REGEX.containsMatchIn(plain)) score += 60
        if (COMMON_HANZI_REGEX.containsMatchIn(plain)) score += 50
        if (hasContextHint) score += 35

        // 识别常见 Token、API Key、JSON、账号密码等凭据特征
        val isJson = (plain.startsWith("{") && plain.endsWith("}")) || (plain.startsWith("[") && plain.endsWith("]"))
        if (isJson) score += 60

        val tokenPattern = Regex("""(?i)\b(sk-[a-zA-Z0-9]{20,}|gh[pousr]_[a-zA-Z0-9]{20,}|ey[A-Za-z0-9_-]{10,}|[a-zA-Z0-9_-]{16,}:[a-zA-Z0-9_-]+|bearer\s+|clinepass|[a-f0-9]{32,64})""")
        if (tokenPattern.containsMatchIn(plain)) score += 60

        val keyValuePattern = Regex("""(?i)\b(key|token|password|pass|secret|code|auth|cookie|session)[=:][^\s]{3,}""")
        if (keyValuePattern.containsMatchIn(plain)) score += 50

        val domainPattern = Regex("""(?i)\b[a-z0-9-]+\.(com|cn|org|net|io|me|cc|top|xyz|do)\b""")
        if (domainPattern.containsMatchIn(plain)) score += 50

        val readableCount = plain.count {
            it.isLetterOrDigit() || it in '\u4e00'..'\u9fa5' || it in ":/?.=&_-+~#%@!$,;\"'{}[]()<>"
        }
        val readableRatio = readableCount.toFloat() / plain.length

        // 可读性与密文长度加分：
        // 只要解码后为纯有效可读文本（>=85%且无控制字符），长度达标即赋予基础置信度
        if (readableRatio >= 0.85f && plain.length >= 6) {
            score += 40
            if (cipherLength >= 16 || plain.length >= 12) {
                score += 25
            }
        } else if (readableRatio < 0.65f) {
            score -= 50
        }

        return score
    }
}

private fun decodeUtf8Strict(bytes: ByteArray): String? = runCatching {
    val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    decoder.decode(ByteBuffer.wrap(bytes)).toString()
}.getOrNull()

private fun isHumanReadable(text: String): Boolean {
    if (text.isBlank() || text.contains('\uFFFD')) return false
    var printable = 0
    for (ch in text) {
        if (ch == '\n' || ch == '\r' || ch == '\t') {
            printable++
        } else {
            val type = Character.getType(ch)
            if (type != Character.CONTROL.toInt() && type != Character.SURROGATE.toInt() && type != Character.UNASSIGNED.toInt()) {
                printable++
            }
        }
    }
    return (printable.toFloat() / text.length) >= 0.85f
}

private fun decodeCandidates(source: String): List<Pair<String, String>> {
    if (source.isBlank()) return emptyList()
    val results = mutableListOf<Pair<String, String>>()
    val compact = source.filterNot(Char::isWhitespace)

    // 1. 标准 Base64
    runCatching {
        val bytes = android.util.Base64.decode(compact, android.util.Base64.DEFAULT)
        decodeUtf8Strict(bytes)
    }.getOrNull()?.takeIf { it != null && isHumanReadable(it) && it != source }?.let { results += "Base64" to it }

    // 2. 剥离中文和括号后的 Base64
    val cleaned = PostCryptoScanner.stripBracketsAndNoise(compact)
    if (cleaned != compact && cleaned.length >= 8) {
        runCatching {
            var norm = cleaned.replace('-', '+').replace('_', '/').trimEnd('=')
            while (norm.length % 4 != 0) norm += "="
            val bytes = android.util.Base64.decode(norm, android.util.Base64.DEFAULT)
            decodeUtf8Strict(bytes)
        }.getOrNull()?.takeIf { it != null && isHumanReadable(it) && it != source }?.let {
            results += "Base64 (剥离干扰后)" to it
        }
    }

    // 3. URL 编码
    runCatching { URLDecoder.decode(source, StandardCharsets.UTF_8.name()) }
        .getOrNull()?.takeIf { isHumanReadable(it) && it != source }?.let { results += "URL解码" to it }

    // 4. Hex
    val hex = compact.removePrefix("0x").removePrefix("0X").replace(":", "").replace("-", "")
    if (hex.length >= 8 && hex.length % 2 == 0 && hex.matches(Regex("[0-9a-fA-F]+"))) {
        runCatching {
            val bytes = ByteArray(hex.length / 2) { index -> hex.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
            decodeUtf8Strict(bytes)
        }.getOrNull()?.takeIf { it != null && isHumanReadable(it) }?.let { results += "Hex" to it }
    }

    // 5. Unicode
    val unicodePattern = Regex("\\\\u([0-9a-fA-F]{4})")
    val unicodeDecoded = unicodePattern.replace(source) { match -> match.groupValues[1].toInt(16).toChar().toString() }
    if (unicodeDecoded != source && isHumanReadable(unicodeDecoded)) {
        results += "Unicode" to unicodeDecoded
    }

    return results.distinctBy { it.second }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DecryptBottomSheet(
    selectedText: String? = null,
    scannedResults: List<DecodedResult> = emptyList(),
    onOpenUrl: (String) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    var selectedScannedIndex by remember(scannedResults) {
        mutableIntStateOf(0)
    }

    var source by remember(selectedText, scannedResults) {
        mutableStateOf(
            selectedText?.takeIf { it.isNotBlank() }
                ?: scannedResults.getOrNull(selectedScannedIndex)?.rawMatched.orEmpty()
        )
    }
    var noise by remember { mutableStateOf("") }
    var showWorkbench by remember(scannedResults, selectedText) {
        mutableStateOf(scannedResults.isEmpty() || !selectedText.isNullOrBlank())
    }

    val manualResults = remember(source) { decodeCandidates(source) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = IosTheme.colors.card,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 380.dp, max = 720.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("文本解码", style = IosTheme.type.headline)
                    if (scannedResults.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(IosTheme.colors.accent.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "发现 ${scannedResults.size} 处",
                                style = IosTheme.type.caption,
                                color = IosTheme.colors.accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("关闭", color = IosTheme.colors.secondaryLabel)
                }
            }

            if (scannedResults.isNotEmpty()) {
                if (scannedResults.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        scannedResults.forEachIndexed { index, res ->
                            val isSelected = index == selectedScannedIndex
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) IosTheme.colors.accent.copy(alpha = 0.18f)
                                        else IosTheme.colors.fieldBackground
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) IosTheme.colors.accent else Color.Transparent,
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        selectedScannedIndex = index
                                        source = res.rawMatched
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    "第 ${res.postNumber} 楼 (${res.confidence}分)",
                                    style = IosTheme.type.caption,
                                    color = if (isSelected) IosTheme.colors.accent else IosTheme.colors.label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }

                val currentScanned = scannedResults.getOrNull(selectedScannedIndex) ?: scannedResults.first()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(IosTheme.colors.fieldBackground)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                currentScanned.method,
                                style = IosTheme.type.subheadline,
                                fontWeight = FontWeight.Bold,
                                color = IosTheme.colors.accent
                            )
                            Text(
                                "· 第 ${currentScanned.postNumber} 楼 · 置信度 ${currentScanned.confidence}%",
                                style = IosTheme.type.caption,
                                color = IosTheme.colors.secondaryLabel
                            )
                        }
                    }

                    SelectionContainer {
                        Text(
                            text = currentScanned.plainText,
                            style = IosTheme.type.body,
                            color = IosTheme.colors.label,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(currentScanned.plainText))
                                Toast.makeText(context, "已复制明文", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("复制明文", color = IosTheme.colors.accent)
                        }

                        val plainTrimmed = currentScanned.plainText.trim()
                        if (plainTrimmed.startsWith("http://", ignoreCase = true) || plainTrimmed.startsWith("https://", ignoreCase = true)) {
                            TextButton(
                                onClick = { onOpenUrl(plainTrimmed) }
                            ) {
                                Text("打开网页", color = IosTheme.colors.accent, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        if (plainTrimmed.startsWith("magnet:?xt=", ignoreCase = true)) {
                            TextButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(plainTrimmed))
                                    Toast.makeText(context, "已复制磁力链", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("复制磁链", color = IosTheme.colors.accent)
                            }
                        }

                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            source = currentScanned.rawMatched
                            showWorkbench = true
                        }) {
                            Text("填入工作台", style = IosTheme.type.caption, color = IosTheme.colors.secondaryLabel)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showWorkbench = !showWorkbench }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "手动解码工作台 / 划词分析",
                    style = IosTheme.type.subheadline,
                    color = IosTheme.colors.secondaryLabel,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    if (showWorkbench) "收起 ▴" else "展开 ▾",
                    style = IosTheme.type.caption,
                    color = IosTheme.colors.accent
                )
            }

            if (showWorkbench) {
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("待测文本") },
                    minLines = 2,
                    maxLines = 5,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = {
                        val clip = clipboard.getText()?.text?.trim().orEmpty()
                        if (clip.isNotBlank()) {
                            source = clip
                            Toast.makeText(context, "已粘贴剪贴板", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("粘贴剪贴板") }

                    TextButton(onClick = { source = source.filterNot(Char::isWhitespace) }) { Text("去空格换行") }

                    TextButton(onClick = {
                        source = PostCryptoScanner.stripBracketsAndNoise(source)
                    }) { Text("剥离中文与括号") }

                    TextButton(
                        enabled = noise.isNotEmpty(),
                        onClick = { source = source.replace(noise, "") },
                    ) { Text("剔除指定词") }
                }

                OutlinedTextField(
                    value = noise,
                    onValueChange = { noise = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("待剔除的干扰文本 (例如 666 或 abc)") },
                    singleLine = true,
                )

                if (manualResults.isEmpty() && source.isNotBlank()) {
                    Text("暂未识别出有效明文", style = IosTheme.type.footnote, color = IosTheme.colors.secondaryLabel)
                }

                manualResults.forEach { (method, value) ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(IosTheme.colors.fieldBackground)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(method, style = IosTheme.type.subheadline, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            val valTrimmed = value.trim()
                            if (valTrimmed.startsWith("http://", ignoreCase = true) || valTrimmed.startsWith("https://", ignoreCase = true)) {
                                TextButton(onClick = { onOpenUrl(valTrimmed) }) { Text("打开网页") }
                            }
                            TextButton(onClick = {
                                clipboard.setText(AnnotatedString(value))
                                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                            }) { Text("复制") }
                        }
                        SelectionContainer {
                            Text(value, style = IosTheme.type.footnote, color = IosTheme.colors.secondaryLabel)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun ImageViewer(
    url: String,
    previewUrl: String? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var zoom by remember(url) { mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    var saving by remember(url) { mutableStateOf(false) }
    var reloadTrigger by remember(url) { mutableIntStateOf(0) }
    var showSpinner by remember(url, reloadTrigger) { mutableStateOf(false) }
    LaunchedEffect(url, reloadTrigger) {
        delay(200)
        showSpinner = true
    }
    val originalRequest = remember(url, context, reloadTrigger) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(200)
            .build()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        SideEffect {
            val win = (view.parent as? DialogWindowProvider)?.window
            win?.let {
                it.setWindowAnimations(0)
                it.attributes = it.attributes.apply { windowAnimations = 0 }
                it.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                it.statusBarColor = android.graphics.Color.TRANSPARENT
                it.navigationBarColor = android.graphics.Color.TRANSPARENT
                it.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            }
        }

        val enterAnim = remember {
            MutableTransitionState(false).apply { targetState = true }
        }
        AnimatedVisibility(
            visibleState = enterAnim,
            enter = fadeIn(animationSpec = tween(120)),
            exit = fadeOut(animationSpec = tween(100)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(0f)
                        .clipToBounds()
                        .pointerInput(url) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (zoom > 1.05f) {
                                        zoom = 1f
                                        offset = Offset.Zero
                                    } else {
                                        zoom = 2.5f
                                    }
                                },
                            )
                        }
                        .pointerInput(url) {
                            detectTransformGestures { _, pan, scale, _ ->
                                val nextZoom = (zoom * scale).coerceIn(1f, 6f)
                                zoom = nextZoom
                                offset = if (nextZoom > 1f) {
                                    offset + pan
                                } else {
                                    Offset(0f, (offset.y + pan.y).coerceAtLeast(0f))
                                }
                                if (nextZoom == 1f && offset.y > 100.dp.toPx()) onDismiss()
                            }
                        },
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = offset.x
                                translationY = offset.y
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        // 1. 底层常驻预览图：优先从内存/磁盘缓存秒显，作为无缝垫底底图，大图加载全过程及切换时永不卸载，杜绝闪屏
                        if (!previewUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(previewUrl)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        // 2. 表层高清原图：加载成功后以 200ms crossfade 平滑覆盖在预览图之上
                        SubcomposeAsyncImage(
                            model = originalRequest,
                            contentDescription = "图片预览",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            val state = painter.state
                            when (state) {
                                is AsyncImagePainter.State.Loading -> {
                                    if (showSpinner) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            IosActivityIndicator(
                                                diameter = 34.dp,
                                                color = Color.White.copy(alpha = 0.85f),
                                            )
                                        }
                                    }
                                }
                                is AsyncImagePainter.State.Error -> {
                                    // 仅在完全没有预览图垫底（即界面全黑、空无一物）时，才在中央显示重试卡片
                                    // 若已有 previewUrl 成功垫底，保持静默展示当前预览图，绝不弹窗打断与遮挡用户视线
                                    if (previewUrl.isNullOrBlank()) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color.Black.copy(alpha = 0.72f))
                                                    .border(0.6.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                                    .padding(horizontal = 20.dp, vertical = 12.dp)
                                                    .clickable { reloadTrigger++ },
                                            ) {
                                                Text("大图加载失败", color = Color.White, style = IosTheme.type.subheadline)
                                                Text("点击重新加载", color = Color(0xFF64B5F6), style = IosTheme.type.caption)
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    SubcomposeAsyncImageContent()
                                }
                            }
                        }
                    }
                }
                Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .zIndex(10f)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (saving) "保存中…" else "保存",
                    color = Color.White,
                    style = IosTheme.type.subheadline.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.38f))
                        .border(0.6.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
                        .clickable(enabled = !saving) {
                            saving = true
                            scope.launch {
                                var msg = saveMediaToPublicStorage(
                                    context = context,
                                    url = url,
                                    suggestedName = null,
                                    asImage = true,
                                )
                                // 若大图下载保存失败，且存在预览图，自动降级保存当前已缓存的预览图
                                if (msg.startsWith("保存失败") && !previewUrl.isNullOrBlank() && previewUrl != url) {
                                    msg = saveMediaToPublicStorage(
                                        context = context,
                                        url = previewUrl,
                                        suggestedName = null,
                                        asImage = true,
                                    )
                                }
                                saving = false
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Text(
                    text = "关闭",
                    color = Color.White,
                    style = IosTheme.type.subheadline.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.38f))
                        .border(0.6.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
    }
}

private suspend fun saveMediaToPublicStorage(
    context: Context,
    url: String,
    suggestedName: String?,
    asImage: Boolean,
): String {
    val app = context.applicationContext as App
    val container = app.container
    withContext(Dispatchers.Main) { container.sessionStore.syncFromWebView() }
    return withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).get().build()
            var response = container.httpClient.newCall(request).execute()
            if (response.code == 403 && response.header("cf-mitigated") != null) {
                response.close()
                val cleared = container.browser.clearChallenge("download")
                if (cleared) {
                    response = container.httpClient.newCall(request).execute()
                } else {
                    return@withContext "下载被安全校验拦截，请重试"
                }
            }
            response.use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext "下载失败 (HTTP ${resp.code})"
                }
                val body = resp.body ?: return@withContext "下载失败：响应内容为空"
                val headerMime = resp.header("Content-Type")
                    ?.substringBefore(';')
                    ?.trim()
                    ?.lowercase()
                    ?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
                val fileName = resolveDownloadFileName(
                    disposition = resp.header("Content-Disposition"),
                    finalUrl = resp.request.url.toString(),
                    suggestedName = suggestedName,
                    mimeType = headerMime,
                    asImage = asImage,
                )
                val ext = fileName.substringAfterLast('.', "").lowercase()
                val resolvedMime = headerMime
                    ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                    ?: if (asImage) "image/jpeg" else "application/octet-stream"

                val saveAsPicture = asImage && resolvedMime.startsWith("image/")
                val resolver = context.contentResolver
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val collection = if (saveAsPicture) {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    } else {
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    }
                    val relativeDir = if (saveAsPicture) {
                        Environment.DIRECTORY_PICTURES + "/LinuxDo"
                    } else {
                        Environment.DIRECTORY_DOWNLOADS + "/LinuxDo"
                    }
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, resolvedMime)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val itemUri = resolver.insert(collection, values)
                        ?: return@withContext "无法创建本地文件"
                    try {
                        resolver.openOutputStream(itemUri)?.use { out ->
                            body.byteStream().use { input -> input.copyTo(out) }
                        } ?: return@withContext "无法写入本地文件"
                        val doneValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.IS_PENDING, 0)
                        }
                        resolver.update(itemUri, doneValues, null, null)
                    } catch (e: Throwable) {
                        resolver.delete(itemUri, null, null)
                        throw e
                    }
                    if (saveAsPicture) "已保存到系统相册 (Pictures/LinuxDo)" else "已保存到系统「下载/LinuxDo」：$fileName"
                } else {
                    val baseDir = context.getExternalFilesDir(
                        if (saveAsPicture) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS,
                    ) ?: context.filesDir
                    if (!baseDir.exists()) baseDir.mkdirs()
                    val outFile = File(baseDir, fileName)
                    outFile.outputStream().use { out ->
                        body.byteStream().use { input -> input.copyTo(out) }
                    }
                    "已保存到：${outFile.absolutePath}"
                }
            }
        }.getOrElse { err ->
            "保存失败：${err.message ?: "网络异常"}"
        }
    }
}

private fun resolveDownloadFileName(
    disposition: String?,
    finalUrl: String,
    suggestedName: String?,
    mimeType: String?,
    asImage: Boolean,
): String {
    val fromDisposition = disposition?.let { header ->
        val utf8Match = Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(header)
        val stdMatch = Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(header)
        val raw = utf8Match?.groupValues?.getOrNull(1) ?: stdMatch?.groupValues?.getOrNull(1)
        raw?.let { runCatching { URLDecoder.decode(it.trim(), "UTF-8") }.getOrDefault(it.trim()) }
    }?.takeIf { it.isNotBlank() }

    val fromUrl = runCatching {
        Uri.parse(finalUrl).lastPathSegment?.trim()
    }.getOrNull()?.takeIf { it.isNotBlank() }

    val cleanSuggested = suggestedName
        ?.replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_")
        ?.trim()
        ?.takeIf { it.isNotBlank() && '.' in it && it.length <= 120 }

    var base = (fromDisposition ?: cleanSuggested ?: fromUrl ?: "linuxdo_${System.currentTimeMillis()}")
        .replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_")
        .trim()

    if ('.' !in base) {
        val ext = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?: when (mimeType) {
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                else -> if (asImage) "jpg" else "bin"
            }
        base = "$base.$ext"
    }
    return base
}

@Composable
private fun TopicNotificationSelector(
    currentLevel: TopicNotificationLevel,
    onSelectLevel: (TopicNotificationLevel) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val isDark = IosTheme.colors.isDark
    val pillBg = if (isDark) Color(0xFF2C2C2E) else IosTheme.colors.fieldBackground
    val pillTextColor = if (isDark) Color(0xFFE5E5EA) else IosTheme.colors.label

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(pillBg)
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val dotColor = when (currentLevel) {
                TopicNotificationLevel.Watching -> Color(0xFF007AFF) // 蓝
                TopicNotificationLevel.Tracking -> Color(0xFF34C759) // 绿
                TopicNotificationLevel.Regular -> Color(0xFF8E8E93)  // 灰
                TopicNotificationLevel.Muted -> Color(0xFFFF3B30)    // 红
            }
            Box(Modifier.size(6.dp).background(dotColor, CircleShape))
            Text(
                text = "${currentLevel.badgeLabel} ▾",
                style = IosTheme.type.footnote,
                color = pillTextColor,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(IosTheme.colors.card),
        ) {
            TopicNotificationLevel.entries.forEach { level ->
                val isSelected = level == currentLevel
                DropdownMenuItem(
                    modifier = if (isSelected) Modifier.background(IosTheme.colors.accent.copy(alpha = 0.12f)) else Modifier,
                    text = {
                        Column(modifier = Modifier.padding(vertical = 2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TopicNotificationIcon(level)
                                Text(
                                    level.title,
                                    style = IosTheme.type.subheadline,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) IosTheme.colors.accent else IosTheme.colors.label,
                                )
                                if (isSelected) {
                                    Text("✓", style = IosTheme.type.caption, color = IosTheme.colors.accent, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                level.description,
                                style = IosTheme.type.caption,
                                color = if (isDark) Color(0xFFB0B0B5) else IosTheme.colors.secondaryLabel,
                                maxLines = 2,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelectLevel(level)
                    },
                )
            }
        }
    }
}

@Composable
private fun FlagDialog(
    post: Post,
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (reasonId: Int, message: String?) -> Unit,
) {
    var selectedReason by remember(post.id) { mutableStateOf(PostFlagReason.Spam) }
    var customMessage by rememberSaveable(post.id) { mutableStateOf("") }

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
                "举报楼层 #${post.postNumber}",
                style = IosTheme.type.headline,
                fontWeight = FontWeight.SemiBold,
                color = IosTheme.colors.label,
            )
            Text(
                "请选择举报原因，以便社区版主尽快核实处理：",
                style = IosTheme.type.footnote,
                color = IosTheme.colors.secondaryLabel,
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
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(IosTheme.colors.accent, CircleShape)
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
                    }
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
                    Text(if (submitting) "提交中…" else "提交", style = IosTheme.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                }
            }
        }
    }
}
