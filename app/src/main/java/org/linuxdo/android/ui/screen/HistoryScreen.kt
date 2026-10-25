package org.linuxdo.android.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.linuxdo.android.data.ReadingHistoryEntry
import org.linuxdo.android.data.avatarUrlOf
import org.linuxdo.android.ui.design.IosDivider
import org.linuxdo.android.ui.design.IosLoadingBox
import org.linuxdo.android.ui.design.IosLargeTitleScaffold
import org.linuxdo.android.ui.design.IosTheme
import org.linuxdo.android.ui.design.avatarShape
import org.linuxdo.android.ui.format.RelativeTime

@Composable
fun HistoryScreen(
    entries: List<ReadingHistoryEntry>,
    query: String,
    loading: Boolean,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onOpenTopic: (Long, String, Int?) -> Unit,
    onBack: () -> Unit,
) {
    val groups = remember(entries, query) {
        val filtered = entries.filter { entry ->
            query.isBlank() || entry.title.contains(query.trim(), ignoreCase = true) || entry.topicId.toString().contains(query.trim())
        }
        filtered.groupBy { dayLabel(it.lastSeenAt) }
    }

    IosLargeTitleScaffold(
        title = "浏览历史",
        showLargeTitle = false,
        onBack = onBack,
        backLabel = "返回",
    ) {
        item("history-search") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(IosTheme.colors.fieldBackground)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HistorySearchIcon(
                        tint = IosTheme.colors.secondaryLabel,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = IosTheme.type.body.copy(color = IosTheme.colors.label, fontSize = 14.sp),
                        cursorBrush = SolidColor(IosTheme.colors.accent),
                        modifier = Modifier.weight(1f),
                        decorationBox = { field ->
                            Box {
                                if (query.isEmpty()) {
                                    Text(
                                        "搜索标题或话题编号",
                                        style = IosTheme.type.body.copy(fontSize = 14.sp),
                                        color = IosTheme.colors.secondaryLabel,
                                    )
                                }
                                field()
                            }
                        },
                    )
                    if (query.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(IosTheme.colors.secondaryLabel.copy(alpha = 0.2f))
                                .clickable { onQueryChange("") },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("✕", fontSize = 10.sp, color = IosTheme.colors.secondaryLabel)
                        }
                    }
                }
                if (entries.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = onClear,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "清空",
                            color = IosTheme.colors.secondaryLabel,
                            style = IosTheme.type.subheadline,
                        )
                    }
                }
            }
        }
        if (loading && entries.isEmpty()) {
            item("history-loading") { IosLoadingBox() }
        } else if (groups.isEmpty()) {
            item("history-empty") {
                Text(
                    if (entries.isEmpty()) "暂无浏览历史" else "没有匹配的话题",
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    style = IosTheme.type.footnote,
                    color = IosTheme.colors.secondaryLabel,
                )
            }
        }
        groups.forEach { (label, dayEntries) ->
            item("history-day-$label") {
                Text(
                    label,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    style = IosTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = IosTheme.colors.secondaryLabel,
                )
            }
            items(dayEntries, key = { it.topicId }) { entry ->
                HistoryRow(entry = entry, query = query) {
                    onOpenTopic(
                        entry.topicId,
                        entry.title,
                        entry.lastSeenPostNumber.takeIf { it > 1 },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    entry: ReadingHistoryEntry,
    query: String,
    onClick: () -> Unit,
) {
    val needle = query.trim()
    val accent = IosTheme.colors.accent
    val titleText = remember(entry.title, needle, accent) {
        if (needle.isEmpty()) {
            AnnotatedString(entry.title)
        } else {
            buildAnnotatedString {
                val raw = entry.title
                var start = raw.indexOf(needle, ignoreCase = true)
                var cursor = 0
                while (start >= 0) {
                    append(raw.substring(cursor, start))
                    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) {
                        append(raw.substring(start, start + needle.length))
                    }
                    cursor = start + needle.length
                    start = raw.indexOf(needle, cursor, ignoreCase = true)
                }
                append(raw.substring(cursor))
            }
        }
    }

    val avatarUrl = remember(entry.avatarTemplate) {
        entry.avatarTemplate?.let { avatarUrlOf(it, size = 96) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(IosTheme.colors.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 第一行：作者头像 + 作者名 / 编号 + 右侧分类药丸
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = entry.authorUsername,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(avatarShape(entry.authorUsername, avatarUrl, 3.5.dp))
                            .background(IosTheme.colors.fieldBackground),
                    )
                    Spacer(Modifier.width(6.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(IosTheme.colors.accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = (entry.authorUsername?.firstOrNull()?.uppercase() ?: "#"),
                            style = IosTheme.type.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = IosTheme.colors.accent,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }

                Text(
                    text = entry.authorUsername ?: "#${entry.topicId}",
                    style = IosTheme.type.footnote,
                    color = IosTheme.colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (!entry.categoryName.isNullOrBlank()) {
                val catColor = parseHexColor(entry.categoryColor) ?: IosTheme.colors.accent
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(catColor.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(catColor),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = entry.categoryName,
                        style = IosTheme.type.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                        color = catColor,
                        maxLines = 1,
                    )
                }
            }
        }

        // 第二行：标题 (对齐默认列表与搜索样式)
        Text(
            text = titleText,
            style = IosTheme.type.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp),
            color = IosTheme.colors.label,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // 第三行：底部元信息 (读到第 X 楼、回复数、时间、ID 药丸)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (entry.lastSeenPostNumber > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(IosTheme.colors.accent.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "读到第 ${entry.lastSeenPostNumber} 楼",
                            style = IosTheme.type.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                            color = IosTheme.colors.accent,
                        )
                    }
                }

                if (entry.totalReplies > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HistoryReplyGlyph(IosTheme.colors.tertiaryLabel)
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "${entry.totalReplies}",
                            style = IosTheme.type.caption.copy(fontSize = 11.sp),
                            color = IosTheme.colors.secondaryLabel,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    HistoryClockGlyph(IosTheme.colors.tertiaryLabel)
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = RelativeTime.format(Instant.ofEpochMilli(entry.lastSeenAt).toString()),
                        style = IosTheme.type.caption.copy(fontSize = 11.sp),
                        color = IosTheme.colors.secondaryLabel,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            if (!entry.authorUsername.isNullOrBlank()) {
                Text(
                    text = "#${entry.topicId}",
                    style = IosTheme.type.caption.copy(fontSize = 10.sp),
                    color = IosTheme.colors.tertiaryLabel,
                )
            }
        }
    }
    IosDivider(startIndent = 16.dp)
}

@Composable
private fun HistoryReplyGlyph(color: Color) {
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
private fun HistoryClockGlyph(color: Color) {
    Canvas(Modifier.size(10.dp)) {
        val stroke = Stroke(width = size.width * 0.12f, cap = StrokeCap.Round)
        drawCircle(color, radius = size.width * 0.44f, style = stroke)
        drawLine(
            color,
            start = Offset(size.width / 2f, size.height / 2f),
            end = Offset(size.width / 2f, size.height * 0.22f),
            strokeWidth = size.width * 0.12f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color,
            start = Offset(size.width / 2f, size.height / 2f),
            end = Offset(size.width * 0.72f, size.height / 2f),
            strokeWidth = size.width * 0.12f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun HistorySearchIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawCircle(tint, radius = 6.5f, center = Offset(10.5f, 10.5f), style = stroke)
            drawLine(tint, Offset(15.5f, 15.5f), Offset(21f, 21f), strokeWidth = 2.2f, cap = StrokeCap.Round)
        }
    }
}

private fun parseHexColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val clean = hex.removePrefix("#")
    val value = clean.toLongOrNull(16) ?: return null
    return if (clean.length == 6) Color(0xFF000000 or value) else Color(value)
}

private fun dayLabel(timestamp: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    return when (date) {
        LocalDate.now(zone) -> "今天"
        LocalDate.now(zone).minusDays(1) -> "昨天"
        else -> date.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
    }
}

