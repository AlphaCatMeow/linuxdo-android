package org.linuxdo.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.linuxdo.android.data.AccountSession
import org.linuxdo.android.data.AccountManager
import org.linuxdo.android.data.avatarUrlOf
import org.linuxdo.android.ui.AccountUiState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.linuxdo.android.ui.design.IosActivityIndicator
import org.linuxdo.android.ui.design.IosTheme
import org.linuxdo.android.ui.design.sheetScrollable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSwitchBottomSheet(
    state: AccountUiState,
    onDismiss: () -> Unit,
    onSwitch: (Long) -> Unit,
    onRemoveAccount: (Long) -> Unit = {},
    onAddAccount: () -> Unit,
) {
    var accountPendingRemoval by remember { mutableStateOf<AccountSession?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = IosTheme.colors.groupedBackground,
        dragHandle = null,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .sheetScrollable()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 顶栏: 微信风格左侧 ✕ 关闭按钮
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = IosTheme.colors.label,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (state.switching) {
                    IosActivityIndicator(diameter = 20.dp, color = Color(0xFF07C160))
                }
            }

            // 标题: 居中大标题 "轻触头像以切换账号"
            Text(
                text = "轻触头像以切换账号",
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = IosTheme.colors.label,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            // 账号卡片列表
            state.accounts.forEach { account ->
                WeChatAccountCard(
                    account = account,
                    selected = account.userId == state.activeUserId,
                    enabled = !state.switching,
                    credentialsUsable = account.hasUsableCredentials,
                    onClick = { onSwitch(account.userId) },
                    onRemove = { accountPendingRemoval = account },
                )
            }

            // 添加账号卡片（最多限制 3 个账号）
            val reachLimit = state.accounts.size >= AccountManager.MAX_ACCOUNTS
            WeChatAddAccountCard(
                enabled = !state.switching,
                isFull = reachLimit,
                maxCount = AccountManager.MAX_ACCOUNTS,
                onClick = onAddAccount,
            )

            state.error?.let {
                Text(
                    text = it,
                    style = IosTheme.type.footnote,
                    color = Color(0xFFFF3B30),
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    accountPendingRemoval?.let { target ->
        DestructiveConfirmDialog(
            title = "移除账号",
            confirmLabel = "移除",
            onDismiss = { accountPendingRemoval = null },
            onConfirm = {
                val userId = target.userId
                accountPendingRemoval = null
                onRemoveAccount(userId)
            },
        )
    }
}

@Composable
private fun WeChatAccountCard(
    account: AccountSession,
    selected: Boolean,
    enabled: Boolean,
    credentialsUsable: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (credentialsUsable) IosTheme.colors.card else IosTheme.colors.fieldBackground)
            .alpha(if (credentialsUsable) 1f else 0.62f)
            .clickable(enabled = enabled && credentialsUsable && !selected, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 头像: 微信大圆角方块风格
        val avatarUrl = account.avatarTemplate?.let { avatarUrlOf(it, 120) }
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = account.username,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(IosTheme.colors.fieldBackground),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE5E5EA)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = account.username.firstOrNull()?.uppercase().orEmpty(),
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF007AFF),
                    ),
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        // 中间昵称与用户名/等级
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = account.name?.takeIf { it.isNotBlank() } ?: account.username,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = IosTheme.colors.label,
                ),
            )
            Text(
                text = buildString {
                    append(account.username)
                    account.trustLevel?.let { append(" · Lv.").append(it) }
                },
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 14.sp,
                    color = IosTheme.colors.secondaryLabel,
                ),
            )
            if (!credentialsUsable) {
                Text(
                    text = "凭据缺失，需要重新登录",
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp,
                        color = IosTheme.colors.destructive,
                    ),
                )
            }
        }

        // 右侧状态: 微信绿色 "当前使用" / 移除操作
        if (!credentialsUsable) {
            Text(
                text = "需要重新登录",
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = IosTheme.colors.destructive,
                ),
            )
        } else if (selected) {
            Text(
                text = "当前使用",
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF07C160),
                ),
            )
        } else {
            TextButton(onClick = onRemove, enabled = enabled) {
                Text(
                    text = "移除",
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp,
                        color = Color(0xFFFF3B30),
                    ),
                )
            }
        }
    }
}

@Composable
private fun WeChatAddAccountCard(
    enabled: Boolean,
    isFull: Boolean = false,
    maxCount: Int = AccountManager.MAX_ACCOUNTS,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(IosTheme.colors.card)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 微信风格浅灰带框虚框 + 号图标
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    1.dp,
                    if (isFull) Color(0xFFE5E5EA) else Color(0xFFD1D1D6),
                    RoundedCornerShape(8.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "添加",
                tint = if (isFull) Color(0xFFC7C7CC) else Color(0xFF8E8E93),
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "添加账号",
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isFull) IosTheme.colors.secondaryLabel else IosTheme.colors.label,
                ),
            )
            if (isFull) {
                Text(
                    text = "已达上限 ($maxCount/$maxCount)，请先移除不常用账号",
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp,
                        color = Color(0xFFFF9500),
                    ),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
