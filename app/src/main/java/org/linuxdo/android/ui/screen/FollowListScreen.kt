package org.linuxdo.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import org.linuxdo.android.data.BlockedUser
import org.linuxdo.android.data.FollowUser
import org.linuxdo.android.data.avatarUrlOf
import org.linuxdo.android.ui.FollowUiState
import org.linuxdo.android.ui.design.IosDivider
import org.linuxdo.android.ui.design.IosLoadingBox
import org.linuxdo.android.ui.design.IosLargeTitleScaffold
import org.linuxdo.android.ui.design.IosTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowListScreen(
    state: FollowUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenUser: (String) -> Unit,
    onUnfollow: (String) -> Unit = {},
    onUnfollowBatch: (List<String>) -> Unit = {},
) {
    var isBatchMode by remember { mutableStateOf(false) }
    var selectedUsernames by remember { mutableStateOf(setOf<String>()) }
    var singleTargetForDismiss by remember { mutableStateOf<FollowUser?>(null) }
    var showBatchConfirmDialog by remember { mutableStateOf(false) }

    IosLargeTitleScaffold(
        title = "我的关注",
        showLargeTitle = false,
        onBack = onBack,
        backLabel = "返回",
        trailing = {
            if (state.users.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            isBatchMode = !isBatchMode
                            if (!isBatchMode) selectedUsernames = emptySet()
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isBatchMode) "完成" else "管理",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = IosTheme.colors.accent,
                    )
                }
            }
        },
    ) {
        when {
            state.loading && state.users.isEmpty() -> item("loading") {
                IosLoadingBox()
            }
            state.error != null && state.users.isEmpty() -> item("error") {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error, style = IosTheme.type.footnote, color = IosTheme.colors.secondaryLabel)
                    Text("重试", modifier = Modifier.clickable(onClick = onRetry), color = IosTheme.colors.accent)
                }
            }
            state.users.isEmpty() -> item("empty") {
                Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
                    Text("还没有关注的人", color = IosTheme.colors.secondaryLabel)
                }
            }
            else -> {
                items(state.users, key = { it.username }) { user ->
                    val isSelected = user.username in selectedUsernames
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                singleTargetForDismiss = user
                                false
                            } else false
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        enableDismissFromEndToStart = !isBatchMode,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFFFF3B30))
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Text(
                                    text = "取消关注",
                                    color = Color.White,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        },
                    ) {
                        UserRowItem(
                            username = user.username,
                            name = user.name,
                            avatarTemplate = user.avatarTemplate,
                            trustLevel = user.trustLevel,
                            isBatchMode = isBatchMode,
                            isSelected = isSelected,
                            onToggleSelect = {
                                selectedUsernames = if (isSelected) {
                                    selectedUsernames - user.username
                                } else {
                                    selectedUsernames + user.username
                                }
                            },
                            onClick = {
                                if (isBatchMode) {
                                    selectedUsernames = if (isSelected) {
                                        selectedUsernames - user.username
                                    } else {
                                        selectedUsernames + user.username
                                    }
                                } else {
                                    onOpenUser(user.username)
                                }
                            },
                        )
                    }
                }

                if (isBatchMode) {
                    item("bottom-spacer") {
                        Spacer(Modifier.height(80.dp))
                    }
                }
                if (state.loading) {
                    item("loading-more") { IosLoadingBox() }
                }
            }
        }
    }

    // 底部批量操作栏
    if (isBatchMode && state.users.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IosTheme.colors.card)
                    .border(0.6.dp, IosTheme.colors.separator)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val allSelected = selectedUsernames.size == state.users.size
                    // 全选 / 取消全选
                    Text(
                        text = if (allSelected) "取消全选" else "全选",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = IosTheme.colors.accent,
                        modifier = Modifier
                            .clickable {
                                selectedUsernames = if (allSelected) emptySet() else state.users.map { it.username }.toSet()
                            }
                            .padding(8.dp),
                    )

                    // 计数与批量移除按钮
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "已选 ${selectedUsernames.size} 人",
                            fontSize = 13.5.sp,
                            color = IosTheme.colors.secondaryLabel,
                        )

                        val enabled = selectedUsernames.isNotEmpty()
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (enabled) Color(0xFFFF3B30) else Color(0xFFFF3B30).copy(alpha = 0.35f))
                                .clickable(enabled = enabled) {
                                    showBatchConfirmDialog = true
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "批量移除",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }

    // 单个移除二次确认弹框 (严格对齐通知消息“一键已读”样式)
    singleTargetForDismiss?.let { target ->
        DestructiveConfirmDialog(
            title = "确定取消对 @${target.username} 的关注吗？",
            onDismiss = { singleTargetForDismiss = null },
            onConfirm = {
                val u = target.username
                singleTargetForDismiss = null
                onUnfollow(u)
            },
        )
    }

    // 批量移除二次确认弹框 (严格对齐通知消息“一键已读”样式)
    if (showBatchConfirmDialog && selectedUsernames.isNotEmpty()) {
        DestructiveConfirmDialog(
            title = "确定将选中的 ${selectedUsernames.size} 位用户移出关注列表吗？",
            onDismiss = { showBatchConfirmDialog = false },
            onConfirm = {
                val targets = selectedUsernames.toList()
                showBatchConfirmDialog = false
                isBatchMode = false
                selectedUsernames = emptySet()
                onUnfollowBatch(targets)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlacklistScreen(
    users: List<BlockedUser>,
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRemoveBatch: (List<String>) -> Unit,
) {
    var isBatchMode by remember { mutableStateOf(false) }
    var selectedUsernames by remember { mutableStateOf(setOf<String>()) }
    var singleTargetForDismiss by remember { mutableStateOf<BlockedUser?>(null) }
    var showBatchConfirmDialog by remember { mutableStateOf(false) }

    IosLargeTitleScaffold(
        title = "本地拉黑用户",
        showLargeTitle = false,
        onBack = onBack,
        backLabel = "返回",
        trailing = {
            if (users.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            isBatchMode = !isBatchMode
                            if (!isBatchMode) selectedUsernames = emptySet()
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isBatchMode) "完成" else "管理",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = IosTheme.colors.accent,
                    )
                }
            }
        },
    ) {
        if (users.isEmpty()) {
            item("empty") {
                Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
                    Text("本地黑名单暂无被拉黑的用户", color = IosTheme.colors.secondaryLabel)
                }
            }
        } else {
            items(users, key = { it.username }) { user ->
                val isSelected = user.username in selectedUsernames
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            singleTargetForDismiss = user
                            false
                        } else false
                    }
                )

                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    enableDismissFromEndToStart = !isBatchMode,
                    backgroundContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFFF3B30))
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Text(
                                text = "移除黑名单",
                                color = Color.White,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    },
                ) {
                    UserRowItem(
                        username = user.username,
                        name = user.name,
                        avatarTemplate = user.avatarTemplate,
                        trustLevel = user.trustLevel,
                        isBatchMode = isBatchMode,
                        isSelected = isSelected,
                        onToggleSelect = {
                            selectedUsernames = if (isSelected) {
                                selectedUsernames - user.username
                            } else {
                                selectedUsernames + user.username
                            }
                        },
                        onClick = {
                            if (isBatchMode) {
                                selectedUsernames = if (isSelected) {
                                    selectedUsernames - user.username
                                } else {
                                    selectedUsernames + user.username
                                }
                            } else {
                                onOpenUser(user.username)
                            }
                        },
                    )
                }
            }

            if (isBatchMode) {
                item("bottom-spacer") {
                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }

    // 底部批量操作栏
    if (isBatchMode && users.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IosTheme.colors.card)
                    .border(0.6.dp, IosTheme.colors.separator)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val allSelected = selectedUsernames.size == users.size
                    // 全选 / 取消全选
                    Text(
                        text = if (allSelected) "取消全选" else "全选",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = IosTheme.colors.accent,
                        modifier = Modifier
                            .clickable {
                                selectedUsernames = if (allSelected) emptySet() else users.map { it.username }.toSet()
                            }
                            .padding(8.dp),
                    )

                    // 计数与批量移除按钮
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "已选 ${selectedUsernames.size} 人",
                            fontSize = 13.5.sp,
                            color = IosTheme.colors.secondaryLabel,
                        )

                        val enabled = selectedUsernames.isNotEmpty()
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (enabled) Color(0xFFFF3B30) else Color(0xFFFF3B30).copy(alpha = 0.35f))
                                .clickable(enabled = enabled) {
                                    showBatchConfirmDialog = true
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "批量移除",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }

    // 单个移除二次确认弹框 (严格对齐通知消息“一键已读”样式)
    singleTargetForDismiss?.let { target ->
        DestructiveConfirmDialog(
            title = "确定将 @${target.username} 移出黑名单吗？",
            onDismiss = { singleTargetForDismiss = null },
            onConfirm = {
                val u = target.username
                singleTargetForDismiss = null
                onRemove(u)
            },
        )
    }

    // 批量移除二次确认弹框 (严格对齐通知消息“一键已读”样式)
    if (showBatchConfirmDialog && selectedUsernames.isNotEmpty()) {
        DestructiveConfirmDialog(
            title = "确定将选中的 ${selectedUsernames.size} 位用户移出黑名单吗？",
            onDismiss = { showBatchConfirmDialog = false },
            onConfirm = {
                val targets = selectedUsernames.toList()
                showBatchConfirmDialog = false
                isBatchMode = false
                selectedUsernames = emptySet()
                onRemoveBatch(targets)
            },
        )
    }
}

/**
 * 关注列表与黑名单列表统一行样式（每行一人）
 */
@Composable
private fun UserRowItem(
    username: String,
    name: String?,
    avatarTemplate: String?,
    trustLevel: Int?,
    isBatchMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
) {
    val avatar = avatarUrlOf(avatarTemplate)
    Row(
        Modifier
            .fillMaxWidth()
            .background(IosTheme.colors.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isBatchMode) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(
                        1.5.dp,
                        if (isSelected) IosTheme.colors.accent else IosTheme.colors.tertiaryLabel,
                        CircleShape
                    )
                    .background(if (isSelected) IosTheme.colors.accent else Color.Transparent)
                    .clickable(onClick = onToggleSelect),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
        }

        AsyncImage(
            model = avatar,
            contentDescription = username,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(IosTheme.colors.fieldBackground),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                name?.takeIf { it.isNotBlank() } ?: username,
                style = IosTheme.type.subheadline,
                color = IosTheme.colors.label,
                fontWeight = FontWeight.Medium,
            )
            Text("@$username", style = IosTheme.type.caption, color = IosTheme.colors.secondaryLabel)
        }
        trustLevel?.let {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(IosTheme.colors.accent.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("Lv.$it", style = IosTheme.type.caption.copy(fontSize = 11.sp), color = IosTheme.colors.accent, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    IosDivider()
}

/**
 * 严格对齐通知消息“一键已读”弹框的高保真二次确认弹窗
 * (18dp 圆角卡片、居中 16.5sp 粗体标题、左灰取消右红确定双胶囊按键)
 */
@Composable
fun DestructiveConfirmDialog(
    title: String,
    confirmLabel: String = "确定",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(IosTheme.colors.card)
                .padding(horizontal = 20.dp, vertical = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = IosTheme.colors.label,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 取消按钮
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (IosTheme.colors.isDark) Color(0xFF2C2C2E) else Color(0xFFF4F5F7))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "取消",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = IosTheme.colors.label,
                        )
                    }
                    // 确定按钮
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFF3B30))
                            .clickable(onClick = onConfirm),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = confirmLabel,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}
