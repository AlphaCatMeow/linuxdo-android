package org.linuxdo.android.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import org.linuxdo.android.ui.LoginUiState
import org.linuxdo.android.ui.design.IosActivityIndicator
import org.linuxdo.android.ui.design.IosTheme

private val GradientStart = Color(0xFFD6E6FF)
private val GradientMid   = Color(0xFFECE3FF)
private val GradientEnd   = Color(0xFFF7F8FD)
private val AccentOrange  = Color(0xFFFF7A00)
private val AccentOrange2 = Color(0xFFFF9526)
private val AccentBlue    = Color(0xFF3F69A8)
private val FieldBorder   = Color(0xFFE2E7F2)
private val FieldBg       = Color(0xFFF9FAFD)
private val TabActive     = Color(0xFF1E2238)
private val TabInactive   = Color(0xFF8E98B0)


/** 原生登录页面，按设计图实现双 Tab（账号密码 / 验证码）。 */
@Composable
fun LoginScreen(
    state: LoginUiState,
    onPasswordLogin: (login: String, password: String, secondFactorToken: String?, secondFactorMethod: Int, rememberPassword: Boolean) -> Unit,
    onSendEmailCode: (email: String) -> Unit,
    onEmailCodeLogin: (email: String, token: String, secondFactorToken: String?, secondFactorMethod: Int) -> Unit,
    onDismissError: () -> Unit,
    onResetSecondFactor: () -> Unit = {},
    onOpenWebLogin: (login: String?, password: String?, secondFactorToken: String?, secondFactorMethod: Int, rememberPassword: Boolean) -> Unit = { _, _, _, _, _ -> },
    onStartWebAuth: () -> Unit = {},
    onDismissRiskControl: () -> Unit = {},
    onCancelWebAuth: () -> Unit = {},
    onOpenSavedAccounts: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
) {
    if (onCancel != null) {
        BackHandler(onBack = onCancel)
    }

    // Tab 选择：0=账号密码，1=验证码
    var tab by rememberSaveable { mutableStateOf(0) }
    var showForgotDialog by rememberSaveable { mutableStateOf(false) }

    // 账号密码 Tab 的状态
    var rememberPassword by rememberSaveable { mutableStateOf(state.rememberPassword) }
    var loginText by rememberSaveable { mutableStateOf(state.savedLogin) }
    var passwordText by rememberSaveable { mutableStateOf(state.savedPassword) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    // 验证码 Tab 的状态
    var emailText by rememberSaveable { mutableStateOf("") }
    var codeText by rememberSaveable { mutableStateOf("") }

    // 两步验证 (2FA) 状态：1=TOTP 6位动态口令，2=备用恢复码
    var secondFactorText by rememberSaveable { mutableStateOf("") }
    var secondFactorMethod by rememberSaveable { mutableStateOf(1) }
    val secondFactorFocus = remember { FocusRequester() }

    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val backEdgeWidth = with(density) { 24.dp.toPx() }
    val backGestureDistance = with(density) { 80.dp.toPx() }

    LaunchedEffect(state.secondFactorRequired) {
        if (state.secondFactorRequired) {
            runCatching { secondFactorFocus.requestFocus() }
        } else {
            secondFactorText = ""
            secondFactorMethod = 1
        }
    }

    // 错误自动消除
    LaunchedEffect(state.error) {
        if (state.error != null) { /* 由对话框展示 */ }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GradientStart, GradientMid, GradientEnd)))
            .clickable(indication = null, interactionSource = remember {
                androidx.compose.foundation.interaction.MutableInteractionSource()
            }) { focusManager.clearFocus() }
            .pointerInput(onCancel) {
                val cancel = onCancel ?: return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.position.x > backEdgeWidth) return@awaitEachGesture

                    var travelled = 0f
                    val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                        travelled += over
                        change.consume()
                    } ?: return@awaitEachGesture

                    horizontalDrag(start.id) { change ->
                        travelled += change.positionChange().x
                        change.consume()
                    }
                    if (travelled >= backGestureDistance) cancel()
                }
            }
    ) {
        if (onCancel != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .zIndex(1f)
                    .padding(start = 20.dp, top = 20.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.85f))
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "取消",
                    tint = Color(0xFF1E2238),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        onOpenSavedAccounts?.let { openSavedAccounts ->
            Text(
                text = "已存账号",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = AccentBlue,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .zIndex(1f)
                    .padding(end = 20.dp, top = 27.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = openSavedAccounts)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(52.dp))

            // Logo
            AppIconBadge()

            Spacer(Modifier.height(20.dp))

            // 标题
            Text(
                "欢迎登录",
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E2238),
            )
            Text(
                "Welcome back",
                fontSize = 13.sp,
                color = Color(0xFF8E98B0),
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(24.dp))

            // ── 登录主要卡片容器 ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(24.dp),
                        spotColor = Color(0x14000000),
                        ambientColor = Color(0x0A000000),
                    )
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFF0F4FC), RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Tab 切换
                LoginTabBar(selected = tab, onSelect = {
                    if (tab != it) {
                        tab = it
                        secondFactorText = ""
                        onResetSecondFactor()
                    }
                })

                Spacer(Modifier.height(22.dp))

                if (tab == 0) {
                    // ── 账号密码登录 ──
                    LoginField(
                        value = loginText,
                        onValueChange = {
                            loginText = it
                            if (state.secondFactorRequired) onResetSecondFactor()
                        },
                        placeholder = "请输入用户名/手机号/邮箱",
                        leadingIcon = { UserIcon() },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )

                    Spacer(Modifier.height(14.dp))

                    LoginField(
                        value = passwordText,
                        onValueChange = {
                            passwordText = it
                            if (state.secondFactorRequired) onResetSecondFactor()
                        },
                        placeholder = "请输入密码",
                        leadingIcon = { LockIcon() },
                        trailingContent = {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { passwordVisible = !passwordVisible }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (passwordVisible) "隐藏" else "显示",
                                    fontSize = 13.sp,
                                    color = if (passwordVisible) AccentOrange else Color(0xFF8E98B0),
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = if (state.secondFactorRequired) ImeAction.Next else ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            keyboard?.hide(); focusManager.clearFocus()
                            if (loginText.isNotBlank() && passwordText.isNotBlank()) {
                                onPasswordLogin(
                                    loginText.trim(),
                                    passwordText,
                                    secondFactorText.takeIf { state.secondFactorRequired },
                                    secondFactorMethod,
                                    rememberPassword,
                                )
                            }
                        }),
                    )

                    if (state.secondFactorRequired) {
                        Spacer(Modifier.height(14.dp))
                        SecondFactorSection(
                            value = secondFactorText,
                            onValueChange = { secondFactorText = it },
                            method = secondFactorMethod,
                            onMethodChange = {
                                secondFactorMethod = it
                                secondFactorText = ""
                            },
                            focusRequester = secondFactorFocus,
                            onDone = {
                                keyboard?.hide(); focusManager.clearFocus()
                                if (loginText.isNotBlank() && passwordText.isNotBlank() && secondFactorText.isNotBlank()) {
                                    onPasswordLogin(
                                        loginText.trim(),
                                        passwordText,
                                        secondFactorText.trim(),
                                        secondFactorMethod,
                                        rememberPassword,
                                    )
                                }
                            },
                        )
                    }

                    // 记住密码与忘记密码
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { rememberPassword = !rememberPassword }
                                .padding(vertical = 4.dp, horizontal = 2.dp),
                        ) {
                            RememberCheckBox(checked = rememberPassword)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "记住密码",
                                fontSize = 13.sp,
                                color = Color(0xFF1E2238),
                            )
                        }

                        Text(
                            "忘记密码？",
                            fontSize = 13.sp,
                            color = Color(0xFF5A6E8C),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showForgotDialog = true }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    val canSubmitPwd = loginText.isNotBlank() &&
                        passwordText.isNotBlank() &&
                        (!state.secondFactorRequired || secondFactorText.isNotBlank()) &&
                        !state.loading

                    LoginButton(
                        text = if (state.secondFactorRequired) "验证并登录" else "登录",
                        loading = state.loading,
                        enabled = canSubmitPwd,
                        onClick = {
                            keyboard?.hide(); focusManager.clearFocus()
                            onPasswordLogin(
                                loginText.trim(),
                                passwordText,
                                secondFactorText.trim().takeIf { state.secondFactorRequired },
                                secondFactorMethod,
                                rememberPassword,
                            )
                        },
                    )
                } else {
                    // ── 验证码登录 ──
                    LoginField(
                        value = emailText,
                        onValueChange = { emailText = it },
                        placeholder = "请输入邮箱",
                        leadingIcon = { EmailIcon() },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                    )

                    Spacer(Modifier.height(14.dp))

                    // 验证码行：输入框 + 获取按钮
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LoginField(
                            value = codeText,
                            onValueChange = {
                                codeText = it
                                if (state.secondFactorRequired) onResetSecondFactor()
                            },
                            placeholder = "粘贴验证码或完整登录链接",
                            leadingIcon = { ShieldIcon() },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                imeAction = if (state.secondFactorRequired) ImeAction.Next else ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = {
                                keyboard?.hide(); focusManager.clearFocus()
                            }),
                        )

                        Spacer(Modifier.width(10.dp))

                        // 获取验证码按钮
                        SendCodeButton(
                            loading = state.sendingCode,
                            enabled = emailText.isNotBlank() && !state.sendingCode,
                            onClick = {
                                keyboard?.hide(); focusManager.clearFocus()
                                onSendEmailCode(emailText.trim())
                            },
                        )
                    }

                    if (state.secondFactorRequired) {
                        Spacer(Modifier.height(14.dp))
                        SecondFactorSection(
                            value = secondFactorText,
                            onValueChange = { secondFactorText = it },
                            method = secondFactorMethod,
                            onMethodChange = {
                                secondFactorMethod = it
                                secondFactorText = ""
                            },
                            focusRequester = secondFactorFocus,
                            onDone = {
                                keyboard?.hide(); focusManager.clearFocus()
                                if (emailText.isNotBlank() && codeText.isNotBlank() && secondFactorText.isNotBlank()) {
                                    onEmailCodeLogin(
                                        emailText.trim(),
                                        codeText.trim(),
                                        secondFactorText.trim(),
                                        secondFactorMethod,
                                    )
                                }
                            },
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // 提示文字
                    Text(
                        "验证码将通过邮件发送。可直接粘贴邮件里的完整登录链接，或其中 /session/email-login/ 后面的 token",
                        fontSize = 12.sp,
                        color = Color(0xFF8E98B0),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(24.dp))

                    val canSubmitEmail = emailText.isNotBlank() &&
                        codeText.isNotBlank() &&
                        (!state.secondFactorRequired || secondFactorText.isNotBlank()) &&
                        !state.loading

                    LoginButton(
                        text = if (state.secondFactorRequired) "验证并登录" else "登录",
                        loading = state.loading,
                        enabled = canSubmitEmail,
                        onClick = {
                            keyboard?.hide(); focusManager.clearFocus()
                            onEmailCodeLogin(
                                emailText.trim(),
                                codeText.trim(),
                                secondFactorText.trim().takeIf { state.secondFactorRequired },
                                secondFactorMethod,
                            )
                        },
                    )
                }

                Spacer(Modifier.height(16.dp))

                // ── 网页授权登录按钮 ──
                WebAuthButton(
                    loading = state.webAuthLoading,
                    enabled = !state.loading && !state.webAuthLoading,
                    onClick = {
                        keyboard?.hide()
                        focusManager.clearFocus()
                        onStartWebAuth()
                    },
                )
            }

            Spacer(Modifier.height(36.dp))
        }
    }

    // 忘记密码提示对话框
    if (showForgotDialog) {
        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            title = { Text("忘记密码") },
            text = { Text("请在 web 端执行忘记密码操作。\n\n访问 linux.do 网页版，使用忘记密码功能重置后再回来登录。") },
            confirmButton = {
                TextButton(onClick = { showForgotDialog = false }) { Text("知道了") }
            },
        )
    }

    // 错误提示对话框
    if (state.error != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text(if (state.isRiskOrCaptchaBlocked) "安全验证提示" else "登录失败") },
            text = { Text(state.error) },
            confirmButton = {
                if (state.isRiskOrCaptchaBlocked) {
                    TextButton(onClick = {
                        onDismissError()
                        val u = if (tab == 0) loginText.trim() else emailText.trim()
                        val p = if (tab == 0) passwordText else null
                        val sf = secondFactorText.trim().takeIf { state.secondFactorRequired && it.isNotBlank() }
                        onOpenWebLogin(u, p, sf, secondFactorMethod, rememberPassword)
                    }) {
                        Text("进入安全验证登录", fontWeight = FontWeight.Bold, color = AccentOrange)
                    }
                } else {
                    TextButton(onClick = onDismissError) { Text("确定") }
                }
            },
            dismissButton = if (state.isRiskOrCaptchaBlocked) {
                { TextButton(onClick = onDismissError) { Text("取消") } }
            } else null,
        )
    }

    // 邮件已发送提示
    if (state.emailSent) {
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text("邮件已发送") },
            text = { Text("登录邮件已发送至您的邮箱。\n\n可打开邮件直接复制整条登录链接，粘贴到验证码输入框后点击登录（也可以只粘贴 /session/email-login/ 后面的 token）。") },
            confirmButton = {
                TextButton(onClick = onDismissError) { Text("知道了") }
            },
        )
    }

    // L站安全风控提示对话框（403）
    if (state.showRiskControlDialog) {
        AlertDialog(
            onDismissRequest = onDismissRiskControl,
            title = { Text("安全验证未通过") },
            text = {
                Text(
                    "已尝试在应用内完成人机验证，但站点仍在拦截当前请求。\n\n" +
                        "可稍后重试，或改用网页授权认证完成登录。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDismissRiskControl()
                    onStartWebAuth()
                }) {
                    Text("网页授权认证", color = AccentOrange, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRiskControl) {
                    Text("取消")
                }
            },
        )
    }

    // 网页授权进行中提示
    if (state.webAuthLoading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("网页授权认证") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    IosActivityIndicator(diameter = 28.dp, color = AccentOrange)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        state.webAuthMessage ?: "正在进行网页授权认证...",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onCancelWebAuth) {
                    Text("取消")
                }
            },
        )
    }
}

// ── 两步验证 (2FA) 输入区域 ──────────────────────────────────────────

@Composable
private fun SecondFactorSection(
    value: String,
    onValueChange: (String) -> Unit,
    method: Int,
    onMethodChange: (Int) -> Unit,
    focusRequester: FocusRequester,
    onDone: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FieldBg)
            .border(1.dp, FieldBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (method == 1) "两步验证 · 动态口令 (TOTP)" else "两步验证 · 备用恢复码",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1E2238),
            )
            Text(
                if (method == 1) "改用备用码" else "改用动态口令",
                fontSize = 12.sp,
                color = AccentOrange,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onMethodChange(if (method == 1) 2 else 1) }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        LoginField(
            value = value,
            onValueChange = onValueChange,
            placeholder = if (method == 1) "请输入 6 位身份验证器验证码" else "请输入备用恢复码",
            leadingIcon = { ShieldIcon() },
            modifier = Modifier.focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (method == 1) KeyboardType.NumberPassword else KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
        )
    }
}

// ── Tab 切换栏 ──────────────────────────────────────────────────────

@Composable
private fun LoginTabBar(selected: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf("账号密码登录", "验证码登录")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        tabs.forEachIndexed { index, label ->
            val isActive = selected == index
            val color by animateColorAsState(
                targetValue = if (isActive) Color(0xFF1E2238) else Color(0xFF8E98B0),
                animationSpec = tween(200),
                label = "tab-color-$index",
            )
            Column(
                Modifier
                    .weight(1f)
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    ) { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = label,
                    fontSize = 16.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = color,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .fillMaxWidth(if (isActive) 0.62f else 0f),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(
                                    Brush.horizontalGradient(listOf(AccentOrange, AccentOrange2)),
                                    RoundedCornerShape(1.5.dp),
                                ),
                        )
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(AccentOrange, CircleShape)
                                .border(1.5.dp, Color.White, CircleShape),
                        )
                    }
                }
            }
        }
    }
}

// ── 输入框 ─────────────────────────────────────────────────────────

@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        placeholder = {
            Text(placeholder, fontSize = 14.sp, color = Color(0xFFB4BFD2))
        },
        leadingIcon = { Box(Modifier.padding(start = 4.dp)) { leadingIcon() } },
        trailingIcon = trailingContent?.let { { it() } },
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = FieldBg,
            focusedContainerColor = Color.White,
            unfocusedBorderColor = FieldBorder,
            focusedBorderColor = AccentOrange.copy(alpha = 0.6f),
            cursorColor = AccentOrange,
        ),
    )
}

// ── 登录按钮 ───────────────────────────────────────────────────────

@Composable
private fun LoginButton(
    text: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .shadow(
                elevation = if (enabled) 10.dp else 0.dp,
                shape = RoundedCornerShape(25.dp),
                ambientColor = Color(0x33FF7A00),
                spotColor = Color(0x55FF7A00),
            )
            .clip(RoundedCornerShape(25.dp))
            .background(
                if (enabled)
                    Brush.horizontalGradient(listOf(AccentOrange, AccentOrange2))
                else
                    Brush.horizontalGradient(listOf(Color(0xFFCDD5E8), Color(0xFFCDD5E8))),
            )
            .then(if (enabled && !loading) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            IosActivityIndicator(diameter = 22.dp, color = Color.White)
        } else {
            Text(
                text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

// ── 获取验证码按钮 ─────────────────────────────────────────────────

@Composable
private fun SendCodeButton(
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .height(52.dp)
            .width(96.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (enabled)
                    Brush.horizontalGradient(listOf(AccentOrange, AccentOrange2))
                else
                    Brush.horizontalGradient(listOf(Color(0xFFCDD5E8), Color(0xFFCDD5E8))),
            )
            .then(if (enabled && !loading) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            IosActivityIndicator(diameter = 18.dp, color = Color.White)
        } else {
            Text(
                "获取验证码",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── App 图标徽章（复用 ic_launcher_foreground 路径） ───────────────

@Composable
private fun AppIconBadge() {
    Box(
        Modifier
            .size(84.dp)
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color(0x18000000),
                spotColor = Color(0x14000000),
            )
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        // 绘制 ic_launcher_foreground 的两条路径（黑色 L 主体 + 亮黄几何折角）
        Canvas(Modifier.size(56.dp)) {
            val scaleX = size.width / 108f
            val scaleY = size.height / 108f
            scale(scaleX, scaleY, Offset.Zero) {
                // 黑色 L 形主体
                val p1 = Path().apply {
                    moveTo(34f, 26.5f)
                    quadraticTo(34f, 24f, 36.5f, 24f)
                    lineTo(45.2f, 24f)
                    quadraticTo(47.7f, 24f, 47.7f, 26.5f)
                    lineTo(47.7f, 63f)
                    lineTo(71.5f, 63f)
                    lineTo(50f, 84f)
                    lineTo(36.5f, 84f)
                    quadraticTo(34f, 84f, 34f, 81.5f)
                    close()
                }
                drawPath(p1, Color(0xFF151517))
                // 亮黄折角
                val p2 = Path().apply {
                    moveTo(55.25f, 84f)
                    lineTo(79.45f, 59.8f)
                    quadraticTo(81.25f, 58f, 81.25f, 61.3f)
                    lineTo(81.25f, 73.5f)
                    quadraticTo(81.25f, 84f, 70.5f, 84f)
                    close()
                }
                drawPath(p2, Color(0xFFFAB50F))

                /* 备用原版蓝底折角路径：
                val p1Old = Path().apply {
                    moveTo(34f, 27f); cubicTo(34f, 24f, 37f, 24f, 37f, 24f)
                    lineTo(44f, 24f); cubicTo(47f, 24f, 47f, 27f, 47f, 27f)
                    lineTo(47f, 63f); lineTo(70f, 63f); lineTo(49f, 84f)
                    lineTo(37f, 84f); cubicTo(34f, 84f, 34f, 81f, 34f, 81f); close()
                }
                val p2Old = Path().apply {
                    moveTo(54f, 84f); lineTo(76f, 62f)
                    cubicTo(78f, 61f, 78f, 64f, 78f, 64f)
                    lineTo(78f, 81f); cubicTo(78f, 84f, 75f, 84f, 75f, 84f); close()
                }
                */
            }
        }
    }
}

// ── 小图标（Canvas 手绘，不依赖 Material Icons） ──────────────────

/** 用户图标 */
@Composable
private fun UserIcon() {
    Canvas(Modifier.size(18.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        drawCircle(Color(0xFFBBC4D6), radius = size.width * 0.22f, center = Offset(cx, cy * 0.7f))
        drawArc(
            color = Color(0xFFBBC4D6),
            startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(size.width * 0.1f, cy * 0.9f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.8f, size.height * 0.6f),
            style = androidx.compose.ui.graphics.drawscope.Fill,
        )
    }
}

/** 锁图标 */
@Composable
private fun LockIcon() {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width; val h = size.height
        // 锁体
        drawRoundRect(
            color = Color(0xFFBBC4D6),
            topLeft = Offset(w * 0.15f, h * 0.45f),
            size = androidx.compose.ui.geometry.Size(w * 0.7f, h * 0.5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f),
        )
        // 锁孔圆圈（白色镂空效果）
        drawCircle(Color.White, w * 0.1f, Offset(w / 2f, h * 0.67f))
        // 锁梁（弧形）- 用矩形框模拟
        drawArc(
            color = Color(0xFFBBC4D6),
            startAngle = 200f, sweepAngle = -220f, useCenter = false,
            topLeft = Offset(w * 0.25f, h * 0.08f),
            size = androidx.compose.ui.geometry.Size(w * 0.5f, h * 0.5f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.13f),

        )
    }
}

/** 邮件图标 */
@Composable
private fun EmailIcon() {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width; val h = size.height
        drawRoundRect(
            color = Color(0xFFBBC4D6),
            topLeft = Offset(w * 0.05f, h * 0.2f),
            size = androidx.compose.ui.geometry.Size(w * 0.9f, h * 0.6f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.1f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.1f),
        )
        // 信封折角
        val path = Path().apply {
            moveTo(w * 0.05f, h * 0.25f)
            lineTo(w * 0.5f, h * 0.55f)
            lineTo(w * 0.95f, h * 0.25f)
        }
        drawPath(path, Color(0xFFBBC4D6), style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.1f))

    }
}

/** 盾牌图标（验证码） */
@Composable
private fun ShieldIcon() {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width; val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.05f)
            lineTo(w * 0.95f, h * 0.25f)
            lineTo(w * 0.95f, h * 0.6f)
            cubicTo(w * 0.95f, h * 0.85f, w * 0.5f, h * 0.97f, w * 0.5f, h * 0.97f)
            cubicTo(w * 0.5f, h * 0.97f, w * 0.05f, h * 0.85f, w * 0.05f, h * 0.6f)
            lineTo(w * 0.05f, h * 0.25f)
            close()
        }
        drawPath(path, Color(0xFFBBC4D6))
    }
}

/** 眼睛图标：open=true 时眼睛睁开（可见），open=false 时加一条斜线（隐藏） */
@Composable
private fun EyeIcon(open: Boolean) {
    val color = if (open) AccentBlue else Color(0xFFBBC4D6)
    Canvas(Modifier.size(22.dp)) {
        val w = size.width; val h = size.height
        val cx = w / 2f; val cy = h / 2f

        // 外轮廓：眼睛形状
        val eyePath = Path().apply {
            moveTo(w * 0.05f, cy)
            cubicTo(cx * 0.5f, h * 0.25f, cx * 1.5f, h * 0.25f, w * 0.95f, cy)
            cubicTo(cx * 1.5f, h * 0.75f, cx * 0.5f, h * 0.75f, w * 0.05f, cy)
            close()
        }
        drawPath(eyePath, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.08f))

        // 瞳孔
        drawCircle(color, radius = w * 0.14f, center = Offset(cx, cy))

        // 关闭态：斜线穿过
        if (!open) {
            val slashPath = Path().apply {
                moveTo(w * 0.2f, h * 0.2f)
                lineTo(w * 0.8f, h * 0.8f)
            }
            drawPath(
                slashPath,
                Color(0xFFBBC4D6),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = w * 0.09f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                ),
            )
        }
    }
}

// ── 网页授权登录相关组件 ──────────────────────────────────────────

/** 地球/网页图标（Canvas 手绘，对齐设计图） */
@Composable
private fun GlobeIcon(
    modifier: Modifier = Modifier.size(19.dp),
    color: Color = AccentBlue,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val strokeW = 1.6.dp.toPx()
        val radius = (w.coerceAtMost(h) / 2f) - strokeW / 2f
        val cx = w / 2f
        val cy = h / 2f

        // 外圆
        drawCircle(
            color = color,
            radius = radius,
            center = Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW),
        )
        // 赤道水平线
        drawLine(
            color = color,
            start = Offset(cx - radius, cy),
            end = Offset(cx + radius, cy),
            strokeWidth = strokeW,
        )
        // 本初子午线（垂直中线）
        drawLine(
            color = color,
            start = Offset(cx, cy - radius),
            end = Offset(cx, cy + radius),
            strokeWidth = strokeW,
        )
        // 经线弧（椭圆）
        drawOval(
            color = color,
            topLeft = Offset(cx - radius * 0.52f, cy - radius),
            size = androidx.compose.ui.geometry.Size(radius * 1.04f, radius * 2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW),
        )
    }
}

/** 网页授权登录药丸按钮（白底蓝深灰边，对齐设计图） */
@Composable
private fun WebAuthButton(
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (enabled) Color(0xFF2C4C7E) else Color(0xFF9AA5BB)
    Box(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .border(
                width = 1.dp,
                color = if (enabled) Color(0xFF6B8AB8).copy(alpha = 0.6f) else Color(0xFFDDE3F0),
                shape = RoundedCornerShape(25.dp),
            )
            .background(Color.White)
            .then(if (enabled && !loading) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IosActivityIndicator(diameter = 18.dp, color = contentColor)
                Spacer(Modifier.width(8.dp))
                Text(
                    "正在授权...",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor,
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                GlobeIcon(color = if (enabled) contentColor else Color(0xFFBBC4D6))
                Spacer(Modifier.width(8.dp))
                Text(
                    "网页授权登录",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor,
                )
            }
        }
    }
}

@Composable
private fun RememberCheckBox(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (checked) AccentOrange else Color.White)
            .border(
                width = 1.2.dp,
                color = if (checked) AccentOrange else Color(0xFFCCD5E4),
                shape = RoundedCornerShape(4.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Canvas(modifier = Modifier.size(11.dp)) {
                val path = Path().apply {
                    moveTo(size.width * 0.15f, size.height * 0.52f)
                    lineTo(size.width * 0.42f, size.height * 0.82f)
                    lineTo(size.width * 0.88f, size.height * 0.22f)
                }
                drawPath(
                    path = path,
                    color = Color.White,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round,
                    ),
                )
            }
        }
    }
}




