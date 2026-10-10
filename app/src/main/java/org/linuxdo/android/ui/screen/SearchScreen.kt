package org.linuxdo.android.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import org.jsoup.Jsoup
import org.linuxdo.android.data.Topic
import org.linuxdo.android.data.TopicListItem
import org.linuxdo.android.data.SearchOrder
import org.linuxdo.android.ui.SearchUiState
import org.linuxdo.android.ui.design.*

@Composable
fun SearchScreen(
    state: SearchUiState,
    onQuery: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpenTopic: (Topic) -> Unit,
    onOrder: (SearchOrder) -> Unit,
    active: Boolean = true,
    scrollToTopRequest: Int = 0,
    trailing: @Composable () -> Unit = {},
) {
    val list = rememberLazyListState()
    ScrollToTopOnRequest(list, scrollToTopRequest)
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val nearEnd by remember { derivedStateOf {
        (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= list.layoutInfo.totalItemsCount - 3
    } }
    LaunchedEffect(active, nearEnd, state.results) {
        if (active && nearEnd && state.results.items.isNotEmpty() && state.results.hasMore &&
            !state.results.loadingMore && !state.results.initialLoading && state.results.error == null) onLoadMore()
    }
    var previousQuery by rememberSaveable { mutableStateOf(state.query) }
    var previousOrder by rememberSaveable { mutableStateOf(state.order) }
    LaunchedEffect(state.query, state.order) {
        if (previousQuery != state.query || previousOrder != state.order) {
            previousQuery = state.query
            previousOrder = state.order
            list.scrollToItem(0)
        }
    }

    Column(Modifier.fillMaxSize().background(IosTheme.colors.groupedBackground)) {
        // 顶部大标题栏
        IosNavBar(title = "搜索", trailing = trailing)

        // 搜索输入栏 (胶囊式圆角矩形 + 放大镜 Icon + 右侧取消)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(IosTheme.colors.fieldBackground)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchLoupeIcon(
                    tint = IosTheme.colors.secondaryLabel,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = state.query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = IosTheme.type.body.copy(color = IosTheme.colors.label),
                    cursorBrush = SolidColor(IosTheme.colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused },
                    decorationBox = { field ->
                        Box {
                            if (state.query.isEmpty()) {
                                Text(
                                    "输入关键词查找话题",
                                    style = IosTheme.type.body,
                                    color = IosTheme.colors.secondaryLabel,
                                )
                            }
                            field()
                        }
                    },
                )
                if (state.query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(IosTheme.colors.secondaryLabel.copy(alpha = 0.2f))
                            .clickable { onQuery("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✕", fontSize = 10.sp, color = IosTheme.colors.secondaryLabel)
                    }
                }
            }
            Text(
                text = "取消",
                style = IosTheme.type.body,
                color = IosTheme.colors.accent,
                modifier = Modifier
                    .clickable {
                        onQuery("")
                        focusManager.clearFocus()
                    }
                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
            )
        }

        // 筛选标签栏 (横向滚动胶囊，第一项为当前排序)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "排序依据",
                style = IosTheme.type.footnote,
                color = IosTheme.colors.secondaryLabel,
            )
            // 当前排序胶囊 (高亮浅蓝底 + 蓝色文字与箭头)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(IosTheme.colors.accent.copy(alpha = 0.12f))
                    .clickable {
                        focusManager.clearFocus()
                        showSort = true
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = state.order.label,
                    style = IosTheme.type.footnote,
                    fontWeight = FontWeight.Medium,
                    color = IosTheme.colors.accent,
                )
                Text(
                    text = "⌄",
                    style = IosTheme.type.footnote,
                    fontWeight = FontWeight.SemiBold,
                    color = IosTheme.colors.accent,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // 搜索结果卡片列表
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            if (state.results.initialLoading) item("loading") { IosLoadingBox() }
            state.results.error?.let { message ->
                item("error") { ErrorNotice(message) { onQuery(state.query) } }
            }
            if (state.query.isNotBlank() && !state.results.initialLoading && state.results.error == null && state.results.items.isEmpty()) {
                item("empty") { EmptyNotice() }
            }
            items(state.results.items, key = { it.topic.id }) { item ->
                SearchResultCard(
                    item = item,
                    excerptHtml = state.excerpts[item.topic.id],
                    query = state.query,
                    onClick = {
                        focusManager.clearFocus()
                        onOpenTopic(item.topic)
                    },
                )
            }
            if (state.results.loadingMore) item("more") { IosLoadingBox() }
        }
    }

    // 底部“搜索排序”抽屉 (BottomSheet 样式)
    if (showSort) {
        Dialog(
            onDismissRequest = { showSort = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                // 半透明背景遮罩
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showSort = false },
                        ),
                )

                // 抽屉面板
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
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 顶部灰色横条把手
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(IosTheme.colors.separator),
                    )

                    Spacer(Modifier.height(12.dp))

                    // 抽屉头部标题与关闭按钮
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "搜索排序",
                            style = IosTheme.type.title3,
                            fontWeight = FontWeight.Bold,
                            color = IosTheme.colors.label,
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(IosTheme.colors.fieldBackground)
                                .clickable { showSort = false },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("✕", fontSize = 12.sp, color = IosTheme.colors.secondaryLabel)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // 排序选项列表
                    SearchOrder.entries.forEach { order ->
                        val isSelected = state.order == order
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) IosTheme.colors.accent.copy(alpha = 0.12f)
                                    else Color.Transparent,
                                )
                                .clickable {
                                    onOrder(order)
                                    showSort = false
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SearchOrderIcon(order, isSelected)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = order.label,
                                style = IosTheme.type.body,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = IosTheme.colors.label,
                                modifier = Modifier.weight(1f),
                            )
                            if (isSelected) {
                                Text(
                                    text = "✓",
                                    color = IosTheme.colors.accent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                )
                            }
                        }
                        if (order != SearchOrder.entries.last()) {
                            IosDivider(modifier = Modifier.padding(horizontal = 8.dp))
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // 底部取消按钮
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showSort = false }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "取消",
                            style = IosTheme.type.body,
                            color = IosTheme.colors.label,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 社区/贴吧风格搜索卡片：
 * 头部作者信息、加粗标题、左侧高亮摘要 + 右侧缩略图、底部操作栏（回复、赞、火箭boost）
 */
@Composable
private fun SearchResultCard(
    item: TopicListItem,
    excerptHtml: String?,
    query: String,
    onClick: () -> Unit,
) {
    val topic = item.topic
    val thumbUrl = remember(topic.imageUrl, excerptHtml) {
        topic.imageUrl ?: extractFirstImage(excerptHtml)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(IosTheme.colors.card)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 第一行：作者头像 + 昵称 + 类别标签
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = item.avatarUrl,
                contentDescription = item.authorUsername,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(IosTheme.colors.fieldBackground),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = item.authorUsername ?: "匿名用户",
                style = IosTheme.type.footnote,
                color = IosTheme.colors.secondaryLabel,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            item.category?.let { cat ->
                Spacer(Modifier.width(8.dp))
                CategoryTag(cat)
            }
        }

        // 第二行：标题
        Text(
            text = topic.title,
            style = IosTheme.type.headline,
            fontWeight = FontWeight.Bold,
            color = IosTheme.colors.label,
            maxLines = 2,
        )

        // 第三行：左侧摘要 + 右侧缩略图
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.weight(1f)) {
                SearchExcerpt(excerptHtml, query)
            }
            if (!thumbUrl.isNullOrBlank()) {
                AsyncImage(
                    model = thumbUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = 84.dp, height = 58.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(IosTheme.colors.fieldBackground),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        // 第四行：贴吧风格操作栏（回复、赞、火箭boost）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 回复
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CardReplyIcon(tint = IosTheme.colors.secondaryLabel)
                val replyCount = (topic.postsCount - 1).coerceAtLeast(0)
                Text(
                    text = if (replyCount > 0) "回复 $replyCount" else "回复",
                    style = IosTheme.type.caption,
                    color = IosTheme.colors.secondaryLabel,
                )
            }
            // 赞
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CardLikeIcon(tint = IosTheme.colors.secondaryLabel)
                Text(
                    text = if (topic.likeCount > 0) "赞 ${topic.likeCount}" else "赞",
                    style = IosTheme.type.caption,
                    color = IosTheme.colors.secondaryLabel,
                )
            }
            // 火箭boost
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CardBoostIcon(tint = IosTheme.colors.secondaryLabel)
                Text(
                    text = "火箭boost",
                    style = IosTheme.type.caption,
                    color = IosTheme.colors.secondaryLabel,
                )
            }
        }
    }
}

/** 搜索匹配摘要文本并高亮关键词 */
@Composable
private fun SearchExcerpt(html: String?, query: String) {
    val accent = IosTheme.colors.accent
    val text = remember(html, query, accent) {
        val plain = html?.let { Jsoup.parseBodyFragment(it).text() }.orEmpty()
        buildAnnotatedString {
            append(plain)
            val needle = query.trim()
            if (needle.isNotEmpty()) {
                var start = plain.indexOf(needle, ignoreCase = true)
                while (start >= 0) {
                    addStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold), start, start + needle.length)
                    start = plain.indexOf(needle, start + needle.length, ignoreCase = true)
                }
            }
        }
    }
    if (text.isNotEmpty()) {
        Text(
            text = text,
            style = IosTheme.type.subheadline,
            color = IosTheme.colors.secondaryLabel,
            maxLines = 3,
        )
    }
}

/** 提取 HTML 文本中的第一张图片 URL */
private fun extractFirstImage(html: String?): String? {
    if (html.isNullOrBlank()) return null
    return runCatching {
        val doc = Jsoup.parseBodyFragment(html)
        doc.select("img").firstOrNull()?.attr("src")?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

/** 搜索放大镜图标 */
@Composable
private fun SearchLoupeIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawCircle(tint, radius = 6.5f, center = Offset(10.5f, 10.5f), style = stroke)
            drawLine(tint, Offset(15.5f, 15.5f), Offset(21f, 21f), strokeWidth = 2.2f, cap = StrokeCap.Round)
        }
    }
}

/** 排序抽屉中各选项的专属贴吧/社区风格图标 */
@Composable
private fun SearchOrderIcon(order: SearchOrder, isSelected: Boolean) {
    val tint = if (isSelected) IosTheme.colors.accent else IosTheme.colors.secondaryLabel
    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
                val stroke = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                when (order) {
                    SearchOrder.Relevance -> {
                        // 爱心图标 (选中时实心蓝底微心，或心形)
                        if (isSelected) {
                            drawCircle(tint, radius = 10f, center = Offset(12f, 12f))
                            val heart = Path().apply {
                                moveTo(12f, 16.5f)
                                cubicTo(10.5f, 15f, 6f, 11f, 6f, 8f)
                                cubicTo(6f, 5.5f, 10f, 4.5f, 12f, 8f)
                                cubicTo(14f, 4.5f, 18f, 5.5f, 18f, 8f)
                                cubicTo(18f, 11f, 13.5f, 15f, 12f, 16.5f)
                                close()
                            }
                            drawPath(heart, Color.White)
                        } else {
                            val heart = Path().apply {
                                moveTo(12f, 19f)
                                cubicTo(10f, 17f, 4f, 12f, 4f, 7.5f)
                                cubicTo(4f, 4.5f, 9.5f, 3.5f, 12f, 8f)
                                cubicTo(14.5f, 3.5f, 20f, 4.5f, 20f, 7.5f)
                                cubicTo(20f, 12f, 14f, 17f, 12f, 19f)
                                close()
                            }
                            drawPath(heart, tint, style = stroke)
                        }
                    }
                    SearchOrder.Latest -> {
                        // 时钟图标 🕒
                        drawCircle(tint, radius = 9f, center = Offset(12f, 12f), style = stroke)
                        drawLine(tint, Offset(12f, 12f), Offset(12f, 6.5f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                        drawLine(tint, Offset(12f, 12f), Offset(16.5f, 12f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                    }
                    SearchOrder.Likes -> {
                        // 点赞大拇指 👍
                        val thumb = Path().apply {
                            moveTo(7f, 19f); lineTo(3f, 19f); lineTo(3f, 10f); lineTo(7f, 10f)
                            moveTo(7f, 11f); lineTo(10.5f, 4f); cubicTo(12f, 4f, 13f, 5.5f, 12.5f, 7.5f)
                            lineTo(11.5f, 10f); lineTo(18.5f, 10f); cubicTo(20f, 10f, 21f, 11.5f, 20f, 13f)
                            lineTo(18.5f, 18f); cubicTo(18f, 19f, 17f, 19f, 16f, 19f); lineTo(7f, 19f)
                        }
                        drawPath(thumb, tint, style = stroke)
                    }
                    SearchOrder.Views -> {
                        // 眼睛浏览 👁
                        val eye = Path().apply {
                            moveTo(3f, 12f)
                            cubicTo(6.5f, 6.5f, 17.5f, 6.5f, 21f, 12f)
                            cubicTo(17.5f, 17.5f, 6.5f, 17.5f, 3f, 12f)
                            close()
                        }
                        drawPath(eye, tint, style = stroke)
                        drawCircle(tint, radius = 2.8f, center = Offset(12f, 12f))
                    }
                    SearchOrder.LatestTopic -> {
                        // 对话气泡 💬
                        val bubble = Path().apply {
                            moveTo(4f, 6f)
                            lineTo(20f, 6f)
                            cubicTo(21f, 6f, 21.5f, 7f, 21.5f, 8f)
                            lineTo(21.5f, 15f)
                            cubicTo(21.5f, 16f, 21f, 17f, 20f, 17f)
                            lineTo(9f, 17f)
                            lineTo(5f, 20.5f)
                            lineTo(5f, 17f)
                            lineTo(4f, 17f)
                            cubicTo(3f, 17f, 2.5f, 16f, 2.5f, 15f)
                            lineTo(2.5f, 8f)
                            cubicTo(2.5f, 7f, 3f, 6f, 4f, 6f)
                            close()
                        }
                        drawPath(bubble, tint, style = stroke)
                    }
                    SearchOrder.Read -> {
                        // 打开的书本 📖
                        val book = Path().apply {
                            moveTo(12f, 6f)
                            cubicTo(9f, 4.5f, 4f, 5f, 3f, 6f); lineTo(3f, 18f)
                            cubicTo(4f, 17f, 9f, 16.5f, 12f, 18f)
                            cubicTo(15f, 16.5f, 20f, 17f, 21f, 18f); lineTo(21f, 6f)
                            cubicTo(20f, 5f, 15f, 4.5f, 12f, 6f)
                        }
                        drawPath(book, tint, style = stroke)
                        drawLine(tint, Offset(12f, 6f), Offset(12f, 18f), strokeWidth = 1.8f)
                    }
                }
            }
        }
    }
}

/** 卡片底部贴吧回复线框图标 */
@Composable
private fun CardReplyIcon(tint: Color) {
    Canvas(Modifier.size(14.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val bubble = Path().apply {
                moveTo(4f, 6f); lineTo(20f, 6f); lineTo(20f, 16f); lineTo(9f, 16f)
                lineTo(5f, 20f); lineTo(5f, 16f); lineTo(4f, 16f); close()
            }
            drawPath(bubble, tint, style = stroke)
        }
    }
}

/** 卡片底部贴吧点赞大拇指线框图标 */
@Composable
private fun CardLikeIcon(tint: Color) {
    Canvas(Modifier.size(14.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val thumb = Path().apply {
                moveTo(7f, 19f); lineTo(3f, 19f); lineTo(3f, 10f); lineTo(7f, 10f)
                moveTo(7f, 11f); lineTo(10f, 4.5f); lineTo(12f, 4.5f); lineTo(11f, 10f); lineTo(18.5f, 10f)
                lineTo(17f, 19f); lineTo(7f, 19f)
            }
            drawPath(thumb, tint, style = stroke)
        }
    }
}

/** 卡片底部火箭 boost 线框图标 */
@Composable
private fun CardBoostIcon(tint: Color) {
    Canvas(Modifier.size(14.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val rocket = Path().apply {
                moveTo(9f, 15f); lineTo(9f, 10f)
                cubicTo(12f, 5f, 16f, 3f, 21f, 3f)
                cubicTo(21f, 8f, 19f, 12f, 14f, 15f)
                close()
                moveTo(9f, 10f); lineTo(5f, 10f); lineTo(3f, 15f); lineTo(9f, 15f)
                moveTo(14f, 15f); lineTo(14f, 19f); lineTo(9f, 21f); lineTo(9f, 15f)
            }
            drawPath(rocket, tint, style = stroke)
            drawCircle(tint, 1.8f, Offset(16f, 8f), style = Stroke(1.4f))
            drawLine(tint, Offset(6f, 18f), Offset(3f, 21f), 1.6f)
        }
    }
}

