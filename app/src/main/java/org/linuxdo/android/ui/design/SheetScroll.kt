package org.linuxdo.android.ui.design

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 底部抽屉内容的统一修饰符。
 *
 * ModalBottomSheet 自身不滚动:内容一旦超过 sheet 的高度上限就被裁掉,底部的按钮会直接消失。
 * 所有放进抽屉的根 Column 都必须带上它,保证任何屏幕尺寸下最后一行操作都够得着。
 * 640dp 是留出 drag handle 与系统栏后的安全上限,内容更短时按实际高度收缩。
 */
@Composable
fun Modifier.sheetScrollable(): Modifier =
    heightIn(max = 640.dp).verticalScroll(rememberScrollState())
