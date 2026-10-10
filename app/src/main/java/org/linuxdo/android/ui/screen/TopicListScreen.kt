package org.linuxdo.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
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
import coil.compose.AsyncImage
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt
import org.jsoup.Jsoup
import org.linuxdo.android.data.Category
import org.linuxdo.android.data.Topic
import org.linuxdo.android.data.HomeFeedType
import org.linuxdo.android.data.TopicListItem
import org.linuxdo.android.ui.design.IosActivityIndicator
import org.linuxdo.android.ui.design.IosDivider
import org.linuxdo.android.ui.design.IosLargeTitleScaffold
import org.linuxdo.android.ui.design.IosLoadingBox
import org.linuxdo.android.ui.design.IosMetrics
import org.linuxdo.android.ui.design.IosTheme
import org.linuxdo.android.ui.format.RelativeTime

data class TopicListUiState(
    val items: List<TopicListItem> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
    val initialLoading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
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
) {
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()

    // 距底部还有 4 项时就开始取下一页,滚到底时数据通常已经就位。
    // 必须要求列表已有真实数据:空列表时占位项也会让 lastVisible 命中阈值,
    // 配合"失败不更新 page"就会变成每秒重打同一页的死循环。
    val nearEnd by remember {
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

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier,
        indicator = {
            val fraction = pullState.distanceFraction
            if (state.refreshing || fraction > 0f) {
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
            customLargeTitle = if (currentFeed != null && onFeedSelect != null) {
                {
                    HomeFeedTitleBar(
                        currentFeed = currentFeed,
                        onFeedSelect = onFeedSelect,
                        hasUnread = hasUnread,
                        trailing = trailing,
                    )
                }
            } else null,
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

            itemsIndexed(state.items, key = { _, item -> item.topic.id }) { index, item ->
                Column(Modifier.background(IosTheme.colors.card)) {
                    TopicRow(item) { onOpenTopic(item.topic) }
                    if (index < state.items.lastIndex) {
                        IosDivider(startIndent = IosMetrics.screenPadding)
                    }
                }
            }

            if (state.loadingMore) {
                item(key = "load-more") { IosLoadingBox() }
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
@Composable
internal fun TopicRow(item: TopicListItem, onClick: () -> Unit) {
    val topic = item.topic
    val excerpt = remember(topic.id, topic.excerpt) {
        topic.excerpt?.let { Jsoup.parseBodyFragment(it).text().trim() }?.takeIf { it.isNotEmpty() }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                AsyncImage(
                    model = item.avatarUrl,
                    contentDescription = item.authorUsername,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(IosTheme.colors.fieldBackground),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = item.authorUsername ?: "匿名",
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
            item.category?.let {
                Spacer(Modifier.width(8.dp))
                CategoryTag(it)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Text(
                    text = titleWithBadges(topic, IosTheme.colors.accent),
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
                        maxLines = 2,
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
            Spacer(Modifier.width(10.dp))
            MetaStat(formatCount(topic.views), textWidth = 34.dp) { ViewGlyph(it) }
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
private fun titleWithBadges(topic: Topic, accent: Color) = buildAnnotatedString {
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
    append(topic.title)
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
    hasUnread: Boolean,
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
                    Box(contentAlignment = Alignment.TopEnd) {
                        Text(
                            text = feed.label,
                            fontSize = if (selected) 21.sp else 16.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) IosTheme.colors.label else IosTheme.colors.secondaryLabel,
                        )
                        if (feed == HomeFeedType.Unread && hasUnread) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 1.dp, end = (-7).dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF3B30)),
                            )
                        }
                    }
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
