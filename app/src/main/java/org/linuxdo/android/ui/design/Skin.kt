package org.linuxdo.android.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 沉浸式多维度主题预设枚举
 */
enum class SkinPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val iconEmoji: String,
    val tagline: String,
) {
    CLASSIC(
        id = "classic",
        title = "原汁果味",
        subtitle = "极简冷灰 · 经典纯粹",
        category = "经典极客",
        iconEmoji = "🍏",
        tagline = "大繁至简，纯粹专注社区阅读",
    ),
    SPONGEBOB(
        id = "spongebob",
        title = "比奇堡奇遇",
        subtitle = "波普明黄 · 美漫元气",
        category = "潮玩波普",
        iconEmoji = "🧽",
        tagline = "“准备好了吗孩子们？” 拥抱菠萝屋的明媚暖阳",
    ),
    XIANXIA(
        id = "xianxia",
        title = "苍穹问道",
        subtitle = "宋瓷天青 · 新中式水墨",
        category = "水墨东方",
        iconEmoji = "🗡️",
        tagline = "秋水共长天一色，徽墨宣纸，雅致求索",
    ),
    SWEET_CANDY(
        id = "sweet_candy",
        title = "甜心草莓糖",
        subtitle = "柔雾软萌 · 奶芙甜愈",
        category = "梦幻治愈",
        iconEmoji = "🎀",
        tagline = "满杯生椰草莓，糯米团软圆角，温柔每个日常",
    ),
    CYBER_NEON(
        id = "cyber_neon",
        title = "赛博幻境",
        subtitle = "矩阵电光 · 未来深邃",
        category = "极客赛博",
        iconEmoji = "🌌",
        tagline = "深邃真空与霓虹电光交织，属于极客的暗夜代码",
    ),
    FOREST_TEA(
        id = "forest_tea",
        title = "空山新雨",
        subtitle = "莫兰迪绿 · 温润护眼",
        category = "自然护眼",
        iconEmoji = "🍵",
        tagline = "竹径通幽，松石茶青，长时间阅读舒适不刺眼",
    );

    companion object {
        fun fromId(id: String?): SkinPreset =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SPONGEBOB
    }
}

/**
 * 细分造型令牌：细化主卡片、内嵌容器及徽章圆角，避免草莓糖(28dp)与比奇堡(20dp)下小标签圆角过度形变
 */
@Immutable
data class SkinShapes(
    val cardCorner: Dp,
    val innerCorner: Dp,
    val tagCorner: Dp,
    val cardBorderWidth: Dp,
    val cardBorderColor: Color,
)

/**
 * 高性能硬件加速阴影令牌：使用原生 spotColor/ambientColor，避免掉帧
 */
@Immutable
data class SkinShadow(
    val elevation: Dp = 0.dp,
    val spotColor: Color = Color.Transparent,
    val ambientColor: Color = Color.Transparent,
)

/**
 * 主题全维度设计令牌集合：包含色彩体系、几何形态（细分圆角）、边框风格及专属物象资产
 */
@Immutable
data class AppSkin(
    val preset: SkinPreset,
    val colors: IosColors,
    val shapes: SkinShapes,
    val shadow: SkinShadow = SkinShadow(),
    val accentGradient: List<Color>,
    val badgeBackground: Color,
    val badgeText: Color,
    val palettePreview: List<Color>,
    val icons: SkinIcons = resolveSkinIcons(preset),
) {
    // 快捷委托属性，对齐调用方习惯
    val cardCorner: Dp get() = shapes.cardCorner
    val innerCorner: Dp get() = shapes.innerCorner
    val tagCorner: Dp get() = shapes.tagCorner
    val cardBorderWidth: Dp get() = shapes.cardBorderWidth
    val cardBorderColor: Color get() = shapes.cardBorderColor
}

val LocalAppSkin = staticCompositionLocalOf { resolveAppSkin(SkinPreset.SPONGEBOB, isDark = false) }

/**
 * 根据预设与明暗模式计算出完整的沉浸式皮肤令牌
 */
fun resolveAppSkin(preset: SkinPreset, isDark: Boolean): AppSkin {
    return when (preset) {
        SkinPreset.CLASSIC -> if (isDark) ClassicDarkSkin else ClassicLightSkin
        SkinPreset.SPONGEBOB -> if (isDark) SpongebobDarkSkin else SpongebobLightSkin
        SkinPreset.XIANXIA -> if (isDark) XianxiaDarkSkin else XianxiaLightSkin
        SkinPreset.SWEET_CANDY -> if (isDark) SweetCandyDarkSkin else SweetCandyLightSkin
        SkinPreset.CYBER_NEON -> if (isDark) CyberNeonDarkSkin else CyberNeonLightSkin
        SkinPreset.FOREST_TEA -> if (isDark) ForestTeaDarkSkin else ForestTeaLightSkin
    }
}

// ─────────────────────────────────────────────────────────────
// 1. 经典果味 (Apple Minimal)
// ─────────────────────────────────────────────────────────────
private val ClassicLightSkin = AppSkin(
    preset = SkinPreset.CLASSIC,
    colors = LightIosColors,
    shapes = SkinShapes(
        cardCorner = 10.dp,
        innerCorner = 6.dp,
        tagCorner = 4.dp,
        cardBorderWidth = 0.dp,
        cardBorderColor = Color.Transparent,
    ),
    shadow = SkinShadow(),
    accentGradient = listOf(Color(0xFF007AFF), Color(0xFF5856D6)),
    badgeBackground = Color(0x1F007AFF),
    badgeText = Color(0xFF007AFF),
    palettePreview = listOf(Color(0xFF007AFF), Color(0xFFFFFFFF), Color(0xFFF2F2F7)),
)

private val ClassicDarkSkin = AppSkin(
    preset = SkinPreset.CLASSIC,
    colors = DarkIosColors,
    shapes = SkinShapes(
        cardCorner = 10.dp,
        innerCorner = 6.dp,
        tagCorner = 4.dp,
        cardBorderWidth = 0.dp,
        cardBorderColor = Color.Transparent,
    ),
    shadow = SkinShadow(),
    accentGradient = listOf(Color(0xFF0A84FF), Color(0xFF5E5CE6)),
    badgeBackground = Color(0x2E0A84FF),
    badgeText = Color(0xFF0A84FF),
    palettePreview = listOf(Color(0xFF0A84FF), Color(0xFF1C1C1E), Color(0xFF000000)),
)

// ─────────────────────────────────────────────────────────────
// 2. 比奇堡奇遇 (Bikini Pop - 菠萝暖阳 · 美漫活力)
// ─────────────────────────────────────────────────────────────
private val SpongebobLightColors = IosColors(
    accent = Color(0xFFE67E22), // 菠萝橙黄
    label = Color(0xFF263238), // 深海炭黑
    secondaryLabel = Color(0xFF607D8B),
    tertiaryLabel = Color(0xFF90A4AE),
    spinner = Color(0xFFF39C12),
    separator = Color(0xFFEDE3D1),
    groupedBackground = Color(0xFFFAF5EC), // 贝壳暖砂白
    card = Color(0xFFFFFFFF), // 纯净海绵白
    pressedHighlight = Color(0xFFF5EEDB),
    fieldBackground = Color(0x1FEB9816),
    codeBackground = Color(0xFFF8F3EA),
    destructive = Color(0xFFE74C3C), // 蟹堡王艳红
    isDark = false,
)

private val SpongebobDarkColors = IosColors(
    accent = Color(0xFFFFB300), // 水母金辉
    label = Color(0xFFF3F7FA),
    secondaryLabel = Color(0xFF94A9B8),
    tertiaryLabel = Color(0xFF5C7282),
    spinner = Color(0xFFFFC107),
    separator = Color(0xFF1F3244),
    groupedBackground = Color(0xFF0B141E), // 深海幽蓝夜底
    card = Color(0xFF132232), // 潜水艇舱体深蓝
    pressedHighlight = Color(0xFF1D3247),
    fieldBackground = Color(0x28FFB300),
    codeBackground = Color(0xFF101B27),
    destructive = Color(0xFFFF5252),
    isDark = true,
)

private val SpongebobLightSkin = AppSkin(
    preset = SkinPreset.SPONGEBOB,
    colors = SpongebobLightColors,
    shapes = SkinShapes(
        cardCorner = 20.dp, // 美漫波普大圆角
        innerCorner = 10.dp, // 内部代码/引用块圆角
        tagCorner = 6.dp, // 小徽章细化圆角，防止形变
        cardBorderWidth = 1.2.dp, // 美漫手绘质感微描边
        cardBorderColor = Color(0xFFEADFC9),
    ),
    shadow = SkinShadow(),
    accentGradient = listOf(Color(0xFFFFB703), Color(0xFFFB8500)),
    badgeBackground = Color(0xFFFFEDD5),
    badgeText = Color(0xFFC2410C),
    palettePreview = listOf(Color(0xFFFB8500), Color(0xFFFFB703), Color(0xFFFAF5EC)),
)

private val SpongebobDarkSkin = AppSkin(
    preset = SkinPreset.SPONGEBOB,
    colors = SpongebobDarkColors,
    shapes = SkinShapes(
        cardCorner = 20.dp,
        innerCorner = 10.dp,
        tagCorner = 6.dp,
        cardBorderWidth = 1.2.dp,
        cardBorderColor = Color(0xFF223A52),
    ),
    shadow = SkinShadow(),
    accentGradient = listOf(Color(0xFFFFC107), Color(0xFFFF8F00)),
    badgeBackground = Color(0x33FFB300),
    badgeText = Color(0xFFFFD54F),
    palettePreview = listOf(Color(0xFFFFC107), Color(0xFF132232), Color(0xFF0B141E)),
)

// ─────────────────────────────────────────────────────────────
// 3. 苍穹问道 (Xianxia Ink - 宋瓷天青 · 新中式宣纸墨韵)
// ─────────────────────────────────────────────────────────────
private val XianxiaLightColors = IosColors(
    accent = Color(0xFF2B5B84), // 宋瓷黛蓝
    label = Color(0xFF212529), // 徽州焦墨
    secondaryLabel = Color(0xFF59656F), // 远山青灰
    tertiaryLabel = Color(0xFF8C98A4),
    spinner = Color(0xFF2B5B84),
    separator = Color(0xFFE5DFD5),
    groupedBackground = Color(0xFFF7F4EB), // 宋代温润素绢宣纸底色
    card = Color(0xFFFFFFFF), // 月白
    pressedHighlight = Color(0xFFECE6D8),
    fieldBackground = Color(0x142B5B84),
    codeBackground = Color(0xFFF3EEE3),
    destructive = Color(0xFFB9382B), // 丹砂印泥朱红
    isDark = false,
)

private val XianxiaDarkColors = IosColors(
    accent = Color(0xFF68A3D5), // 冷月苍青
    label = Color(0xFFECEFF1),
    secondaryLabel = Color(0xFF8FA4B5),
    tertiaryLabel = Color(0xFF556877),
    spinner = Color(0xFF68A3D5),
    separator = Color(0xFF252B33),
    groupedBackground = Color(0xFF0F1318), // 玄铁深墨黑
    card = Color(0xFF171D24), // 青墨玉底
    pressedHighlight = Color(0xFF222B35),
    fieldBackground = Color(0x2968A3D5),
    codeBackground = Color(0xFF13181F),
    destructive = Color(0xFFE55347),
    isDark = true,
)

private val XianxiaLightSkin = AppSkin(
    preset = SkinPreset.XIANXIA,
    colors = XianxiaLightColors,
    shapes = SkinShapes(
        cardCorner = 8.dp, // 画卷雅致微圆角，方正规整有骨气
        innerCorner = 4.dp,
        tagCorner = 3.dp,
        cardBorderWidth = 0.8.dp, // 宣纸边骨极细微线
        cardBorderColor = Color(0xFFE4DDD1),
    ),
    shadow = SkinShadow(),
    accentGradient = listOf(Color(0xFF2B5B84), Color(0xFF4A7C59)),
    badgeBackground = Color(0xFFE5EFF7),
    badgeText = Color(0xFF1E4263),
    palettePreview = listOf(Color(0xFF2B5B84), Color(0xFFB9382B), Color(0xFFF7F4EB)),
)

private val XianxiaDarkSkin = AppSkin(
    preset = SkinPreset.XIANXIA,
    colors = XianxiaDarkColors,
    shapes = SkinShapes(
        cardCorner = 8.dp,
        innerCorner = 4.dp,
        tagCorner = 3.dp,
        cardBorderWidth = 0.8.dp,
        cardBorderColor = Color(0xFF25303D),
    ),
    shadow = SkinShadow(),
    accentGradient = listOf(Color(0xFF68A3D5), Color(0xFF58B368)),
    badgeBackground = Color(0x2E68A3D5),
    badgeText = Color(0xFF98CAEF),
    palettePreview = listOf(Color(0xFF68A3D5), Color(0xFF171D24), Color(0xFF0F1318)),
)

// ─────────────────────────────────────────────────────────────
// 4. 甜心草莓糖 (Sweet Candy - 柔雾软萌 · 奶芙甜愈)
// ─────────────────────────────────────────────────────────────
private val SweetCandyLightColors = IosColors(
    accent = Color(0xFFFA527F), // 草莓鲜果粉
    label = Color(0xFF382329), // 浓郁生巧褐黑
    secondaryLabel = Color(0xFF8A6C75),
    tertiaryLabel = Color(0xFFBCA7AD),
    spinner = Color(0xFFFA527F),
    separator = Color(0xFFFFDFE8),
    groupedBackground = Color(0xFFFFF6F8), // 白桃乌龙粉白底
    card = Color(0xFFFFFFFF), // 纯白奶霜
    pressedHighlight = Color(0xFFFFECF1),
    fieldBackground = Color(0x1AFA527F),
    codeBackground = Color(0xFFFFF0F4),
    destructive = Color(0xFFFF3366),
    isDark = false,
)

private val SweetCandyDarkColors = IosColors(
    accent = Color(0xFFFF6994), // 霓虹草莓粉
    label = Color(0xFFFFF0F3),
    secondaryLabel = Color(0xFFB89BA3),
    tertiaryLabel = Color(0xFF755B63),
    spinner = Color(0xFFFF6994),
    separator = Color(0xFF3B202B),
    groupedBackground = Color(0xFF180E13), // 黑糖可可深夜底
    card = Color(0xFF24151C), // 红丝绒蛋糕暗色
    pressedHighlight = Color(0xFF361E2A),
    fieldBackground = Color(0x33FF6994),
    codeBackground = Color(0xFF1D1016),
    destructive = Color(0xFFFF4D79),
    isDark = true,
)

private val SweetCandyLightSkin = AppSkin(
    preset = SkinPreset.SWEET_CANDY,
    colors = SweetCandyLightColors,
    shapes = SkinShapes(
        cardCorner = 28.dp, // 28dp 软萌糯米团大超圆角
        innerCorner = 12.dp, // 内部代码/引用块 12dp 适度圆角
        tagCorner = 6.dp, // 徽章精致小圆角，绝不变形
        cardBorderWidth = 1.0.dp,
        cardBorderColor = Color(0xFFFFE0E8),
    ),
    // 硬件加速柔光粉紫投影，绝不卡顿
    shadow = SkinShadow(
        elevation = 3.dp,
        spotColor = Color(0x28FA527F),
        ambientColor = Color(0x14FA527F),
    ),
    accentGradient = listOf(Color(0xFFFF758C), Color(0xFFFF7EB3)),
    badgeBackground = Color(0xFFFFEBF1),
    badgeText = Color(0xFFD62258),
    palettePreview = listOf(Color(0xFFFA527F), Color(0xFFFFB3C6), Color(0xFFFFF6F8)),
)

private val SweetCandyDarkSkin = AppSkin(
    preset = SkinPreset.SWEET_CANDY,
    colors = SweetCandyDarkColors,
    shapes = SkinShapes(
        cardCorner = 28.dp,
        innerCorner = 12.dp,
        tagCorner = 6.dp,
        cardBorderWidth = 1.0.dp,
        cardBorderColor = Color(0xFF422230),
    ),
    shadow = SkinShadow(
        elevation = 2.dp,
        spotColor = Color(0x33FF6994),
        ambientColor = Color(0x1AFF6994),
    ),
    accentGradient = listOf(Color(0xFFFF6994), Color(0xFFE056FD)),
    badgeBackground = Color(0x33FF6994),
    badgeText = Color(0xFFFF9BB9),
    palettePreview = listOf(Color(0xFFFF6994), Color(0xFF24151C), Color(0xFF180E13)),
)

// ─────────────────────────────────────────────────────────────
// 5. 赛博幻境 (Cyber Neon - 矩阵电光 · 未来深邃)
// ─────────────────────────────────────────────────────────────
private val CyberNeonLightColors = IosColors(
    accent = Color(0xFF009688), // 赛博碧青
    label = Color(0xFF1A2130),
    secondaryLabel = Color(0xFF5C6B73),
    tertiaryLabel = Color(0xFF8F9BA2),
    spinner = Color(0xFF009688),
    separator = Color(0xFFD9E2EC),
    groupedBackground = Color(0xFFF0F4F8), // 冰川冷青白
    card = Color(0xFFFFFFFF),
    pressedHighlight = Color(0xFFE2EBF2),
    fieldBackground = Color(0x1A009688),
    codeBackground = Color(0xFFEAF0F6),
    destructive = Color(0xFFE63946),
    isDark = false,
)

private val CyberNeonDarkColors = IosColors(
    accent = Color(0xFF00F5D4), // 矩阵荧光霓虹青
    label = Color(0xFFF1F5F9),
    secondaryLabel = Color(0xFF94A3B8),
    tertiaryLabel = Color(0xFF475569),
    spinner = Color(0xFF00F5D4),
    separator = Color(0xFF1E293B),
    groupedBackground = Color(0xFF090D16), // 深邃太空暗夜黑
    card = Color(0xFF101726), // 悬浮黑曜舱
    pressedHighlight = Color(0xFF1A2338),
    fieldBackground = Color(0x2900F5D4),
    codeBackground = Color(0xFF0B101B),
    destructive = Color(0xFFFF3366),
    isDark = true,
)

private val CyberNeonLightSkin = AppSkin(
    preset = SkinPreset.CYBER_NEON,
    colors = CyberNeonLightColors,
    shapes = SkinShapes(
        cardCorner = 12.dp,
        innerCorner = 6.dp,
        tagCorner = 4.dp,
        cardBorderWidth = 1.0.dp,
        cardBorderColor = Color(0xFFCBD5E1),
    ),
    shadow = SkinShadow(),
    accentGradient = listOf(Color(0xFF00B4D8), Color(0xFF7209B7)),
    badgeBackground = Color(0xFFE0F2FE),
    badgeText = Color(0xFF0369A1),
    palettePreview = listOf(Color(0xFF009688), Color(0xFF7209B7), Color(0xFFF0F4F8)),
)

private val CyberNeonDarkSkin = AppSkin(
    preset = SkinPreset.CYBER_NEON,
    colors = CyberNeonDarkColors,
    shapes = SkinShapes(
        cardCorner = 12.dp,
        innerCorner = 6.dp,
        tagCorner = 4.dp,
        cardBorderWidth = 1.0.dp,
        cardBorderColor = Color(0xFF1E293B),
    ),
    shadow = SkinShadow(
        elevation = 2.dp,
        spotColor = Color(0x3300F5D4),
        ambientColor = Color(0x1900F5D4),
    ),
    accentGradient = listOf(Color(0xFF00F5D4), Color(0xFF7B2CBF)),
    badgeBackground = Color(0x2E00F5D4),
    badgeText = Color(0xFF5EEAD4),
    palettePreview = listOf(Color(0xFF00F5D4), Color(0xFF7B2CBF), Color(0xFF090D16)),
)

// ─────────────────────────────────────────────────────────────
// 6. 空山新雨 (Forest Tea - 莫兰迪绿 · 温润护眼)
// ─────────────────────────────────────────────────────────────
private val ForestTeaLightColors = IosColors(
    accent = Color(0xFF2D6A4F), // 莫兰迪竹青
    label = Color(0xFF1B2A20),
    secondaryLabel = Color(0xFF526E5D),
    tertiaryLabel = Color(0xFF869E90),
    spinner = Color(0xFF2D6A4F),
    separator = Color(0xFFE2EBE4),
    groupedBackground = Color(0xFFF2F7F3), // 晨露草木白底
    card = Color(0xFFFFFFFF),
    pressedHighlight = Color(0xFFE5EDE7),
    fieldBackground = Color(0x1A2D6A4F),
    codeBackground = Color(0xFFECF3EE),
    destructive = Color(0xFFBA3C34),
    isDark = false,
)

private val ForestTeaDarkColors = IosColors(
    accent = Color(0xFF52B788), // 浅叶晨绿
    label = Color(0xFFEDF6F0),
    secondaryLabel = Color(0xFF8FA899),
    tertiaryLabel = Color(0xFF536A5D),
    spinner = Color(0xFF52B788),
    separator = Color(0xFF1D2E24),
    groupedBackground = Color(0xFF0D1611), // 松针幽夜底
    card = Color(0xFF15221B), // 墨绿深林卡片
    pressedHighlight = Color(0xFF1E3127),
    fieldBackground = Color(0x2952B788),
    codeBackground = Color(0xFF101B15),
    destructive = Color(0xFFE55D54),
    isDark = true,
)

private val ForestTeaLightSkin = AppSkin(
    preset = SkinPreset.FOREST_TEA,
    colors = ForestTeaLightColors,
    shapes = SkinShapes(
        cardCorner = 14.dp,
        innerCorner = 7.dp,
        tagCorner = 5.dp,
        cardBorderWidth = 0.8.dp,
        cardBorderColor = Color(0xFFDAE5DC),
    ),
    shadow = SkinShadow(),
    accentGradient = listOf(Color(0xFF40916C), Color(0xFF1B4332)),
    badgeBackground = Color(0xFFE3EFE7),
    badgeText = Color(0xFF1B4332),
    palettePreview = listOf(Color(0xFF2D6A4F), Color(0xFF74C69D), Color(0xFFF2F7F3)),
)

private val ForestTeaDarkSkin = AppSkin(
    preset = SkinPreset.FOREST_TEA,
    colors = ForestTeaDarkColors,
    shapes = SkinShapes(
        cardCorner = 14.dp,
        innerCorner = 7.dp,
        tagCorner = 5.dp,
        cardBorderWidth = 0.8.dp,
        cardBorderColor = Color(0xFF22362B),
    ),
    shadow = SkinShadow(),
    accentGradient = listOf(Color(0xFF52B788), Color(0xFF2D6A4F)),
    badgeBackground = Color(0x2E52B788),
    badgeText = Color(0xFF74C69D),
    palettePreview = listOf(Color(0xFF52B788), Color(0xFF15221B), Color(0xFF0D1611)),
)

// ─────────────────────────────────────────────────────────────
// 主题物象资产体系 (Skin Icons Asset System)
// 包含底栏4图标（最新/分类/搜索/我的）、发帖FAB、回复FAB
// ─────────────────────────────────────────────────────────────

@Immutable
interface SkinIcons {
    fun drawTabLatest(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color = Color.Transparent)
    fun drawTabCategories(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color = Color.Transparent)
    fun drawTabSearch(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color = Color.Transparent)
    fun drawTabNotifications(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color = Color.Transparent) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val stroke = if (isSelected) 2.2f else 1.8f
            val envPath = Path().apply {
                moveTo(2.5f, 5.5f)
                lineTo(21.5f, 5.5f)
                lineTo(21.5f, 18.5f)
                lineTo(2.5f, 18.5f)
                close()
            }
            if (isSelected) {
                drawPath(envPath, color)
                drawLine(Color.White, Offset(3.5f, 6.5f), Offset(12f, 13f), stroke, StrokeCap.Round)
                drawLine(Color.White, Offset(20.5f, 6.5f), Offset(12f, 13f), stroke, StrokeCap.Round)
            } else {
                drawPath(envPath, color, style = Stroke(stroke, cap = StrokeCap.Round))
                drawLine(color, Offset(3.5f, 6.5f), Offset(12f, 13f), stroke, StrokeCap.Round)
                drawLine(color, Offset(20.5f, 6.5f), Offset(12f, 13f), stroke, StrokeCap.Round)
            }
        }
    }
    fun drawTabProfile(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color = Color.Transparent)
    fun drawCreateTopic(scope: DrawScope, color: Color, surfaceColor: Color = Color.Transparent)
    fun drawReplyFab(scope: DrawScope, color: Color, surfaceColor: Color = Color.Transparent)
    fun drawAssistiveHub(scope: DrawScope, color: Color, surfaceColor: Color = Color.Transparent)
    fun drawThemeBannerArt(scope: DrawScope, isDark: Boolean)
}

fun resolveSkinIcons(preset: SkinPreset): SkinIcons {
    return when (preset) {
        SkinPreset.CLASSIC -> ClassicSkinIcons
        SkinPreset.SPONGEBOB -> SpongebobSkinIcons
        SkinPreset.XIANXIA -> XianxiaSkinIcons
        SkinPreset.SWEET_CANDY -> SweetCandySkinIcons
        SkinPreset.CYBER_NEON -> CyberNeonSkinIcons
        SkinPreset.FOREST_TEA -> ForestTeaSkinIcons
    }
}

// ─────────────────────────────────────────────────────────────
// 1. 经典果味物象（Apple 极简纯粹几何）
// ─────────────────────────────────────────────────────────────
object ClassicSkinIcons : SkinIcons {
    override fun drawTabLatest(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val stroke = if (isSelected) 2.2f else 1.8f
            repeat(3) { index ->
                val y = 6f + index * 6f
                drawLine(color, Offset(3.5f, y), Offset(20.5f, y), stroke, StrokeCap.Round)
            }
        }
    }

    override fun drawTabCategories(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val stroke = if (isSelected) 2.2f else 1.8f
            repeat(4) { index ->
                val x = 3.5f + (index % 2) * 9.5f
                val y = 3.5f + (index / 2) * 9.5f
                if (isSelected) {
                    drawRoundRect(color, Offset(x, y), Size(7.5f, 7.5f), CornerRadius(2f, 2f))
                } else {
                    drawRoundRect(color, Offset(x, y), Size(7.5f, 7.5f), CornerRadius(2f, 2f), style = Stroke(stroke))
                }
            }
        }
    }

    override fun drawTabSearch(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val stroke = if (isSelected) 2.2f else 1.8f
            drawCircle(color, 6.5f, Offset(10f, 10f), style = Stroke(stroke))
            drawLine(color, Offset(14.8f, 14.8f), Offset(20.5f, 20.5f), stroke * 1.15f, StrokeCap.Round)
        }
    }

    override fun drawTabProfile(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val stroke = if (isSelected) 2.2f else 1.8f
            if (isSelected) {
                drawCircle(color, 4.5f, Offset(12f, 7f))
                val body = Path().apply {
                    moveTo(4f, 21f)
                    cubicTo(4f, 15f, 8f, 13f, 12f, 13f)
                    cubicTo(16f, 13f, 20f, 15f, 20f, 21f)
                    close()
                }
                drawPath(body, color)
            } else {
                drawCircle(color, 4.5f, Offset(12f, 7f), style = Stroke(stroke))
                drawArc(color, 180f, 180f, false, Offset(4f, 13f), Size(16f, 16f), style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
    }

    override fun drawCreateTopic(scope: DrawScope, color: Color, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val pen = Path().apply {
                moveTo(4f, 20f); lineTo(8.5f, 19f); lineTo(19.5f, 8f); lineTo(16f, 4.5f); lineTo(5f, 15.5f); close()
            }
            drawPath(pen, color, style = Stroke(1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawLine(color, Offset(13.5f, 7f), Offset(17f, 10.5f), 1.8f, StrokeCap.Round)
            drawLine(color, Offset(12.5f, 20f), Offset(20f, 20f), 1.9f, StrokeCap.Round)
        }
    }

    override fun drawReplyFab(scope: DrawScope, color: Color, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val arrow = Path().apply {
                moveTo(10f, 5f); lineTo(3f, 11f); lineTo(10f, 17f)
                lineTo(10f, 13f); cubicTo(17f, 12f, 19f, 15f, 21f, 20f)
                cubicTo(21f, 10f, 17f, 8f, 10f, 9f); close()
            }
            drawPath(arrow, color)
        }
    }

    override fun drawAssistiveHub(scope: DrawScope, color: Color, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            drawLine(color, Offset(5f, 7f), Offset(19f, 7f), 2.2f, StrokeCap.Round)
            drawLine(color, Offset(5f, 12f), Offset(19f, 12f), 2.2f, StrokeCap.Round)
            drawLine(color, Offset(5f, 17f), Offset(19f, 17f), 2.2f, StrokeCap.Round)
        }
    }

    override fun drawThemeBannerArt(scope: DrawScope, isDark: Boolean) {
        val bg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
        val accent = if (isDark) Color(0xFF0A84FF) else Color(0xFF007AFF)
        scope.drawRect(bg)
        val wave = Path().apply {
            moveTo(0f, scope.size.height * 0.4f)
            cubicTo(scope.size.width * 0.3f, scope.size.height * 0.8f, scope.size.width * 0.7f, scope.size.height * 0.1f, scope.size.width, scope.size.height * 0.6f)
            lineTo(scope.size.width, scope.size.height); lineTo(0f, scope.size.height); close()
        }
        scope.drawPath(wave, accent.copy(alpha = 0.12f))
    }
}

// ─────────────────────────────────────────────────────────────
// 2. 比奇堡奇遇物象（水母、菠萝屋、潜水镜、小蜗、美味蟹黄堡）
// ─────────────────────────────────────────────────────────────
object SpongebobSkinIcons : SkinIcons {
    override fun drawTabLatest(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val stroke = if (isSelected) 2.0f else 1.8f
            val contrast = if (surfaceColor != Color.Transparent && surfaceColor != Color.Unspecified) surfaceColor else Color.White
            // 水母半球形伞盖
            val cap = Path().apply {
                moveTo(4.5f, 13.5f)
                cubicTo(4.5f, 5f, 19.5f, 5f, 19.5f, 13.5f)
                // 底部裙边波浪
                cubicTo(18f, 15f, 16f, 12.5f, 14.5f, 14f)
                cubicTo(13f, 15.5f, 11f, 12.5f, 9.5f, 14f)
                cubicTo(8f, 15.5f, 6f, 12.5f, 4.5f, 13.5f)
                close()
            }
            if (isSelected) {
                drawPath(cap, color)
                // 水母圆斑反光
                drawCircle(contrast, 1.2f, Offset(9.5f, 8.5f))
                drawCircle(contrast, 1.0f, Offset(14.5f, 8f))
            } else {
                drawPath(cap, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(color, 0.8f, Offset(9.5f, 8.5f))
                drawCircle(color, 0.8f, Offset(14.5f, 8f))
            }
            // 3条飘逸的蜿蜒触须
            val t1 = Path().apply { moveTo(7.5f, 14.5f); cubicTo(6f, 17.5f, 9f, 19.5f, 7.5f, 22.5f) }
            val t2 = Path().apply { moveTo(12f, 15f); cubicTo(13.5f, 17.5f, 10.5f, 20f, 12f, 23f) }
            val t3 = Path().apply { moveTo(16.5f, 14.5f); cubicTo(15f, 17.5f, 18f, 19.5f, 16.5f, 22.5f) }
            drawPath(t1, color, style = Stroke(stroke * 0.85f, cap = StrokeCap.Round))
            drawPath(t2, color, style = Stroke(stroke * 0.85f, cap = StrokeCap.Round))
            drawPath(t3, color, style = Stroke(stroke * 0.85f, cap = StrokeCap.Round))
        }
    }

    override fun drawTabCategories(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val stroke = if (isSelected) 2.0f else 1.8f
            val contrast = if (surfaceColor != Color.Transparent && surfaceColor != Color.Unspecified) surfaceColor else Color.White
            // 顶部三片绿叶
            val leafCenter = Path().apply { moveTo(12f, 7.5f); lineTo(12f, 2f) }
            val leafLeft = Path().apply { moveTo(12f, 7.5f); cubicTo(9.5f, 5.5f, 7.5f, 4f, 6f, 4.5f) }
            val leafRight = Path().apply { moveTo(12f, 7.5f); cubicTo(14.5f, 5.5f, 16.5f, 4f, 18f, 4.5f) }
            drawPath(leafCenter, color, style = Stroke(stroke * 0.9f, cap = StrokeCap.Round))
            drawPath(leafLeft, color, style = Stroke(stroke * 0.9f, cap = StrokeCap.Round))
            drawPath(leafRight, color, style = Stroke(stroke * 0.9f, cap = StrokeCap.Round))

            // 菠萝屋主体轮廓
            val body = Path().apply {
                moveTo(6.5f, 10.5f)
                cubicTo(5f, 13.5f, 5f, 18f, 8f, 21.5f)
                cubicTo(10f, 23f, 14f, 23f, 16f, 21.5f)
                cubicTo(19f, 18f, 19f, 13.5f, 17.5f, 10.5f)
                cubicTo(16f, 8.5f, 8f, 8.5f, 6.5f, 10.5f)
                close()
            }
            if (isSelected) {
                drawPath(body, color)
                // 舷窗反白
                drawCircle(contrast, 2.3f, Offset(12f, 13.5f))
                drawCircle(color, 1.2f, Offset(12f, 13.5f))
                // 门洞反白
                val door = Path().apply {
                    moveTo(10f, 22.5f); lineTo(10f, 18.5f); cubicTo(10f, 17f, 14f, 17f, 14f, 18.5f); lineTo(14f, 22.5f)
                }
                drawPath(door, contrast, style = Stroke(1.3f, cap = StrokeCap.Round))
            } else {
                drawPath(body, color, style = Stroke(stroke, join = StrokeJoin.Round))
                // 菱形斜网纹
                drawLine(color, Offset(7.5f, 12f), Offset(16.5f, 19.5f), stroke * 0.7f, StrokeCap.Round)
                drawLine(color, Offset(16.5f, 12f), Offset(7.5f, 19.5f), stroke * 0.7f, StrokeCap.Round)
                drawCircle(color, 2f, Offset(12f, 14.5f), style = Stroke(stroke * 0.8f))
            }
        }
    }

    override fun drawTabSearch(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val stroke = if (isSelected) 2.0f else 1.8f
            val contrast = if (surfaceColor != Color.Transparent && surfaceColor != Color.Unspecified) surfaceColor else Color.White
            // 双目潜水镜两个大圆镜片
            val leftLens = Path().apply { addOval(Rect(2.5f, 7.5f, 10.5f, 16.5f)) }
            val rightLens = Path().apply { addOval(Rect(12.5f, 7.5f, 20.5f, 16.5f)) }
            if (isSelected) {
                drawPath(leftLens, color)
                drawPath(rightLens, color)
                // 镜片高光切线
                drawLine(contrast, Offset(4.8f, 10f), Offset(8.2f, 14f), 1.3f, StrokeCap.Round)
                drawLine(contrast, Offset(14.8f, 10f), Offset(18.2f, 14f), 1.3f, StrokeCap.Round)
            } else {
                drawPath(leftLens, color, style = Stroke(stroke))
                drawPath(rightLens, color, style = Stroke(stroke))
                drawLine(color, Offset(4.8f, 10.5f), Offset(7.8f, 13.5f), stroke * 0.75f, StrokeCap.Round)
                drawLine(color, Offset(14.8f, 10.5f), Offset(17.8f, 13.5f), stroke * 0.75f, StrokeCap.Round)
            }
            // 镜桥连杆与两端绑带
            drawLine(color, Offset(10.5f, 12f), Offset(12.5f, 12f), stroke * 1.1f, StrokeCap.Round)
            drawLine(color, Offset(1f, 12f), Offset(2.5f, 12f), stroke * 0.85f, StrokeCap.Round)
            drawLine(color, Offset(20.5f, 12f), Offset(22f, 12f), stroke * 0.85f, StrokeCap.Round)
            // 咕噜咕噜冒出的深海小气泡（右上角）
            drawCircle(color, 1.8f, Offset(18.5f, 4.5f), style = Stroke(stroke * 0.8f))
            drawCircle(color, 1.1f, Offset(14.5f, 2.5f), style = Stroke(stroke * 0.7f))
        }
    }

    override fun drawTabProfile(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val stroke = if (isSelected) 2.0f else 1.8f
            val contrast = if (surfaceColor != Color.Transparent && surfaceColor != Color.Unspecified) surfaceColor else Color.White
            // 小蜗圆圆的大螺壳
            val shellCenter = Offset(14f, 14.5f)
            val shellRadius = 6.2f
            if (isSelected) {
                drawCircle(color, shellRadius, shellCenter)
                // 螺壳内部螺旋纹反白
                val swirl = Path().apply {
                    moveTo(14f, 14.5f)
                    cubicTo(12.5f, 13f, 12.5f, 16f, 14.5f, 16.5f)
                    cubicTo(16.5f, 17f, 17.5f, 14.5f, 16f, 12.5f)
                }
                drawPath(swirl, contrast, style = Stroke(1.2f, cap = StrokeCap.Round))
            } else {
                drawCircle(color, shellRadius, shellCenter, style = Stroke(stroke))
                val swirl = Path().apply {
                    moveTo(14f, 14.5f)
                    cubicTo(12.5f, 13f, 12.5f, 16f, 14.5f, 16.5f)
                    cubicTo(16.5f, 17f, 17.5f, 14.5f, 16f, 12.5f)
                }
                drawPath(swirl, color, style = Stroke(stroke * 0.75f, cap = StrokeCap.Round))
            }
            // 蜗牛腹足底板
            val foot = Path().apply {
                moveTo(3.5f, 19.5f)
                cubicTo(2.5f, 19.5f, 3f, 17f, 6f, 16.5f)
                lineTo(9f, 16.5f)
            }
            drawPath(foot, color, style = Stroke(stroke, cap = StrokeCap.Round))
            drawLine(color, Offset(3.5f, 19.5f), Offset(18.5f, 19.5f), stroke, StrokeCap.Round)
            // 两只探出的长眼睛触须
            drawLine(color, Offset(5.5f, 16.5f), Offset(4f, 8f), stroke * 0.85f, StrokeCap.Round)
            drawLine(color, Offset(8f, 16.5f), Offset(7.5f, 6.5f), stroke * 0.85f, StrokeCap.Round)
            // 眼球与呆萌小瞳孔
            drawCircle(color, 2f, Offset(4f, 7.5f))
            drawCircle(color, 2f, Offset(7.5f, 6f))
            drawCircle(contrast, 0.8f, Offset(3.6f, 7.2f))
            drawCircle(contrast, 0.8f, Offset(7.1f, 5.7f))
        }
    }

    override fun drawCreateTopic(scope: DrawScope, color: Color, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            // 派大星萌系海星造型
            val star = Path().apply {
                moveTo(12f, 3f)
                lineTo(14.5f, 8.5f); lineTo(20.5f, 9.5f); lineTo(16f, 13.5f)
                lineTo(17.5f, 19.5f); lineTo(12f, 16.5f); lineTo(6.5f, 19.5f)
                lineTo(8f, 13.5f); lineTo(3.5f, 9.5f); lineTo(9.5f, 8.5f)
                close()
            }
            drawPath(star, color, style = Stroke(2.0f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(color, 1.2f, Offset(10.5f, 10.5f))
            drawCircle(color, 1.2f, Offset(13.5f, 10.5f))
        }
    }

    override fun drawReplyFab(scope: DrawScope, color: Color, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val contrast = if (surfaceColor != Color.Transparent && surfaceColor != Color.Unspecified) surfaceColor else Color.White
            // 顶层面包胚
            val bunTop = Path().apply {
                moveTo(4f, 9.5f)
                cubicTo(4f, 4.5f, 20f, 4.5f, 20f, 9.5f)
                close()
            }
            drawPath(bunTop, color)
            // 芝麻小颗粒反白
            drawCircle(contrast, 0.6f, Offset(9f, 7.5f))
            drawCircle(contrast, 0.6f, Offset(13.5f, 7f))
            drawCircle(contrast, 0.6f, Offset(16f, 8.5f))
            // 波浪生菜褶皱
            val lettuce = Path().apply {
                moveTo(3.5f, 11.5f)
                cubicTo(5.5f, 10f, 7.5f, 12.5f, 9.5f, 11.5f)
                cubicTo(11.5f, 10f, 13.5f, 12.5f, 15.5f, 11.5f)
                cubicTo(17.5f, 10f, 19.5f, 12.5f, 20.5f, 11.5f)
            }
            drawPath(lettuce, color, style = Stroke(1.5f, cap = StrokeCap.Round))
            // 芝士三角切片
            val cheese = Path().apply {
                moveTo(6f, 13f); lineTo(13f, 13f); lineTo(9.5f, 15.5f); close()
            }
            drawPath(cheese, color)
            // 肉饼层
            drawRoundRect(color, Offset(4.5f, 14f), Size(15f, 2.5f), CornerRadius(1.2f, 1.2f))
            // 底层面包胚
            val bunBottom = Path().apply {
                moveTo(5f, 17.5f); lineTo(19f, 17.5f); cubicTo(19f, 20.5f, 5f, 20.5f, 5f, 17.5f); close()
            }
            drawPath(bunBottom, color)
        }
    }

    override fun drawAssistiveHub(scope: DrawScope, color: Color, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val star = Path().apply {
                moveTo(12f, 3.5f)
                lineTo(14.5f, 9f); lineTo(20.5f, 9.8f); lineTo(16f, 14f)
                lineTo(17.2f, 20f); lineTo(12f, 17f); lineTo(6.8f, 20f)
                lineTo(8f, 14f); lineTo(3.5f, 9.8f); lineTo(9.5f, 9f)
                close()
            }
            drawPath(star, color)
            val contrast = if (surfaceColor != Color.Transparent && surfaceColor != Color.Unspecified) surfaceColor else Color(0xFFFB8500)
            drawCircle(contrast, 1f, Offset(10.5f, 11f))
            drawCircle(contrast, 1f, Offset(13.5f, 11f))
        }
    }

    override fun drawThemeBannerArt(scope: DrawScope, isDark: Boolean) {
        val oceanTop = if (isDark) Color(0xFF0D1B2A) else Color(0xFF48CAE4)
        val oceanBottom = if (isDark) Color(0xFF1B263B) else Color(0xFF0077B6)
        scope.drawRect(
            androidx.compose.ui.graphics.Brush.verticalGradient(listOf(oceanTop, oceanBottom))
        )
        val ray = Path().apply {
            moveTo(scope.size.width * 0.2f, 0f)
            lineTo(scope.size.width * 0.45f, scope.size.height)
            lineTo(scope.size.width * 0.6f, scope.size.height)
            lineTo(scope.size.width * 0.3f, 0f)
            close()
        }
        scope.drawPath(ray, Color.White.copy(alpha = 0.12f))
        val bubbleColor = Color.White.copy(alpha = 0.35f)
        scope.drawCircle(bubbleColor, 6f, Offset(scope.size.width * 0.18f, scope.size.height * 0.6f), style = Stroke(1.5f))
        scope.drawCircle(bubbleColor, 10f, Offset(scope.size.width * 0.75f, scope.size.height * 0.35f), style = Stroke(1.8f))
        scope.drawCircle(bubbleColor, 4f, Offset(scope.size.width * 0.82f, scope.size.height * 0.2f), style = Stroke(1.2f))
        val jf = Path().apply {
            moveTo(scope.size.width * 0.5f - 8f, scope.size.height * 0.45f)
            cubicTo(scope.size.width * 0.5f - 8f, scope.size.height * 0.3f, scope.size.width * 0.5f + 8f, scope.size.height * 0.3f, scope.size.width * 0.5f + 8f, scope.size.height * 0.45f)
            close()
        }
        scope.drawPath(jf, Color(0xFFFFD166).copy(alpha = 0.4f))
    }
}

// ─────────────────────────────────────────────────────────────
// 3. 苍穹问道物象（青芒飞剑、炼丹神鼎、寻龙罗盘、紫金仙葫芦、传音玉符）
// ─────────────────────────────────────────────────────────────
object XianxiaSkinIcons : SkinIcons {
    override fun drawTabLatest(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val stroke = if (isSelected) 2.0f else 1.8f
            val contrast = if (surfaceColor != Color.Transparent && surfaceColor != Color.Unspecified) surfaceColor else Color.White
            // 剑刃（右上 45 度凌厉破空）
            val blade = Path().apply {
                moveTo(19.5f, 4.5f)
                lineTo(18f, 7.5f); lineTo(9.5f, 16f); lineTo(8f, 14.5f); lineTo(16.5f, 6f)
                close()
            }
            if (isSelected) {
                drawPath(blade, color)
                drawLine(contrast, Offset(19f, 5f), Offset(8.8f, 15.2f), 1f, StrokeCap.Round)
            } else {
                drawPath(blade, color, style = Stroke(stroke))
                drawLine(color, Offset(18.5f, 5.5f), Offset(9f, 15f), stroke * 0.7f, StrokeCap.Round)
            }
            // 剑格（护手横向短杠）
            drawLine(color, Offset(6.5f, 13.5f), Offset(11.5f, 18.5f), stroke * 1.3f, StrokeCap.Round)
            // 剑柄
            drawLine(color, Offset(8.5f, 16f), Offset(5.5f, 19f), stroke * 1.1f, StrokeCap.Round)
            // 飘逸流苏剑穗
            val tassel = Path().apply {
                moveTo(5.5f, 19f); cubicTo(3.5f, 20.5f, 3f, 22.5f, 5f, 23f)
            }
            drawPath(tassel, color, style = Stroke(stroke * 0.8f, cap = StrokeCap.Round))
        }
    }

    override fun drawTabCategories(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val stroke = if (isSelected) 2.0f else 1.8f
            val contrast = if (surfaceColor != Color.Transparent && surfaceColor != Color.Unspecified) surfaceColor else Color.White
            // 鼎口
            drawRoundRect(color, Offset(5f, 8f), Size(14f, 2.5f), CornerRadius(1f, 1f))
            // 鼎身圆腹
            val cauldronBody = Path().apply {
                moveTo(6f, 10.5f)
                cubicTo(5f, 15f, 7f, 18.5f, 12f, 18.5f)
                cubicTo(17f, 18.5f, 19f, 15f, 18f, 10.5f)
                close()
            }
            if (isSelected) {
                drawPath(cauldronBody, color)
                drawCircle(contrast, 1.5f, Offset(12f, 14.5f))
            } else {
                drawPath(cauldronBody, color, style = Stroke(stroke))
            }
            // 左右双立耳
            val earLeft = Path().apply { moveTo(5.5f, 9.5f); lineTo(3.5f, 9.5f); lineTo(3.5f, 13f); lineTo(5.5f, 13f) }
            val earRight = Path().apply { moveTo(18.5f, 9.5f); lineTo(20.5f, 9.5f); lineTo(20.5f, 13f); lineTo(18.5f, 13f) }
            drawPath(earLeft, color, style = Stroke(stroke * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(earRight, color, style = Stroke(stroke * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            // 鼎双足
            drawLine(color, Offset(7.5f, 18f), Offset(6f, 22f), stroke * 1.1f, StrokeCap.Round)
            drawLine(color, Offset(16.5f, 18f), Offset(18f, 22f), stroke * 1.1f, StrokeCap.Round)
            // 升腾仙气轻烟
            val smoke = Path().apply {
                moveTo(10f, 6.5f); cubicTo(11f, 4.5f, 9.5f, 3f, 10.5f, 1.5f)
                moveTo(14f, 6.5f); cubicTo(13f, 4.5f, 14.5f, 3f, 13.5f, 1.5f)
            }
            drawPath(smoke, color, style = Stroke(stroke * 0.75f, cap = StrokeCap.Round))
        }
    }

    override fun drawTabSearch(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val stroke = if (isSelected) 2.0f else 1.8f
            // 八卦外天池圆环
            drawCircle(color, 8.5f, Offset(12f, 12f), style = Stroke(stroke))
            // 四正方位符印刻度
            drawLine(color, Offset(12f, 4f), Offset(12f, 6f), stroke * 0.8f, StrokeCap.Round)
            drawLine(color, Offset(12f, 18f), Offset(12f, 20f), stroke * 0.8f, StrokeCap.Round)
            drawLine(color, Offset(4f, 12f), Offset(6f, 12f), stroke * 0.8f, StrokeCap.Round)
            drawLine(color, Offset(18f, 12f), Offset(20f, 12f), stroke * 0.8f, StrokeCap.Round)
            // 乾坤南北磁针
            val needleNorth = Path().apply {
                moveTo(12f, 6.5f); lineTo(10.2f, 12f); lineTo(13.8f, 12f); close()
            }
            val needleSouth = Path().apply {
                moveTo(12f, 17.5f); lineTo(10.2f, 12f); lineTo(13.8f, 12f); close()
            }
            drawPath(needleNorth, color)
            drawPath(needleSouth, color, style = Stroke(stroke * 0.8f))
            drawCircle(color, 1.2f, Offset(12f, 12f))
        }
    }

    override fun drawTabProfile(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val stroke = if (isSelected) 2.0f else 1.8f
            val contrast = if (surfaceColor != Color.Transparent && surfaceColor != Color.Unspecified) surfaceColor else Color.White
            // 葫芦顶部木塞
            drawRoundRect(color, Offset(10.5f, 2.5f), Size(3f, 2f), CornerRadius(0.8f, 0.8f))
            // 上小下大的圆润仙葫身
            val topCenter = Offset(12f, 8f)
            val bottomCenter = Offset(12f, 16f)
            if (isSelected) {
                drawCircle(color, 3.8f, topCenter)
                drawCircle(color, 5.8f, bottomCenter)
                drawLine(contrast, Offset(9.5f, 11f), Offset(14.5f, 11f), 1.2f, StrokeCap.Round)
                drawCircle(contrast, 1.2f, Offset(10.5f, 15f))
            } else {
                drawCircle(color, 3.8f, topCenter, style = Stroke(stroke))
                drawCircle(color, 5.8f, bottomCenter, style = Stroke(stroke))
                drawLine(color, Offset(9f, 11f), Offset(15f, 11f), stroke * 1.2f, StrokeCap.Round)
                drawLine(color, Offset(13.5f, 11f), Offset(15.5f, 14f), stroke * 0.8f, StrokeCap.Round)
            }
        }
    }

    override fun drawCreateTopic(scope: DrawScope, color: Color, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            // 狼毫修仙神笔
            val brush = Path().apply {
                moveTo(18f, 4f); lineTo(20f, 6f); lineTo(9f, 17f); lineTo(6f, 18f); lineTo(7f, 15f); close()
            }
            drawPath(brush, color, style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            // 笔尖悬墨灵芒
            val ink = Path().apply {
                moveTo(6f, 18f); cubicTo(4.5f, 19.5f, 4f, 21f, 5.5f, 21.5f); cubicTo(7f, 21f, 6.5f, 19.5f, 6f, 18f)
            }
            drawPath(ink, color)
        }
    }

    override fun drawReplyFab(scope: DrawScope, color: Color, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            // 传音道符牌
            val rect = Path().apply {
                addRoundRect(RoundRect(Rect(6f, 3.5f, 18f, 18f), CornerRadius(2f, 2f)))
            }
            drawPath(rect, color, style = Stroke(1.8f))
            // 符牌内太极仙篆
            val cloudRune = Path().apply {
                moveTo(9f, 7f); cubicTo(11f, 5.5f, 13f, 5.5f, 15f, 7f)
                moveTo(12f, 7f); lineTo(12f, 11f)
                moveTo(9.5f, 9.5f); lineTo(14.5f, 9.5f)
                moveTo(12f, 11f); cubicTo(10f, 12f, 14f, 14f, 12f, 15.5f)
            }
            drawPath(cloudRune, color, style = Stroke(1.3f, cap = StrokeCap.Round))
            // 符尾仙穗
            drawLine(color, Offset(9f, 18f), Offset(7.5f, 22f), 1.3f, StrokeCap.Round)
            drawLine(color, Offset(15f, 18f), Offset(16.5f, 22f), 1.3f, StrokeCap.Round)
        }
    }

    override fun drawAssistiveHub(scope: DrawScope, color: Color, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            drawCircle(color, 8.5f, Offset(12f, 12f), style = Stroke(1.8f))
            val taiji = Path().apply {
                moveTo(12f, 3.5f)
                cubicTo(16.5f, 3.5f, 16.5f, 12f, 12f, 12f)
                cubicTo(7.5f, 12f, 7.5f, 20.5f, 12f, 20.5f)
                cubicTo(16.7f, 20.5f, 20.5f, 16.7f, 20.5f, 12f)
                cubicTo(20.5f, 7.3f, 16.7f, 3.5f, 12f, 3.5f)
                close()
            }
            drawPath(taiji, color)
            val contrast = if (surfaceColor != Color.Transparent && surfaceColor != Color.Unspecified) surfaceColor else Color(0xFF264653)
            drawCircle(contrast, 1.4f, Offset(12f, 7.8f))
            drawCircle(color, 1.4f, Offset(12f, 16.2f))
        }
    }

    override fun drawThemeBannerArt(scope: DrawScope, isDark: Boolean) {
        val sky = if (isDark) Color(0xFF1A1F1E) else Color(0xFFEAF4F4)
        val mountainBase = if (isDark) Color(0xFF264653) else Color(0xFF6B9080)
        scope.drawRect(sky)
        val m1 = Path().apply {
            moveTo(0f, scope.size.height * 0.7f)
            cubicTo(scope.size.width * 0.25f, scope.size.height * 0.35f, scope.size.width * 0.5f, scope.size.height * 0.65f, scope.size.width * 0.75f, scope.size.height * 0.3f)
            cubicTo(scope.size.width * 0.85f, scope.size.height * 0.45f, scope.size.width * 0.95f, scope.size.height * 0.4f, scope.size.width, scope.size.height * 0.55f)
            lineTo(scope.size.width, scope.size.height); lineTo(0f, scope.size.height); close()
        }
        scope.drawPath(m1, mountainBase.copy(alpha = 0.25f))
        val m2 = Path().apply {
            moveTo(0f, scope.size.height * 0.85f)
            cubicTo(scope.size.width * 0.15f, scope.size.height * 0.55f, scope.size.width * 0.4f, scope.size.height * 0.8f, scope.size.width * 0.65f, scope.size.height * 0.5f)
            lineTo(scope.size.width, scope.size.height); lineTo(0f, scope.size.height); close()
        }
        scope.drawPath(m2, mountainBase.copy(alpha = 0.45f))
        scope.drawLine(Color(0xFFE9C46A), Offset(scope.size.width * 0.15f, scope.size.height * 0.3f), Offset(scope.size.width * 0.35f, scope.size.height * 0.15f), 1.6f, StrokeCap.Round)
    }
}

// ─────────────────────────────────────────────────────────────
// 4. 甜心草莓糖物象（草莓圣代杯、梦幻马卡龙、心心棒棒糖、软萌蝴蝶结、草莓爱心泡泡）
// ─────────────────────────────────────────────────────────────
object SweetCandySkinIcons : SkinIcons {
    override fun drawTabLatest(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val stroke = if (isSelected) 2.0f else 1.8f
            // 下方甜点杯身
            val cup = Path().apply {
                moveTo(6f, 12f)
                cubicTo(7f, 17f, 10.5f, 18.5f, 11f, 19f)
                lineTo(11f, 21.5f)
                lineTo(8f, 21.5f); lineTo(16f, 21.5f); lineTo(13f, 21.5f)
                lineTo(13f, 19f)
                cubicTo(13.5f, 18.5f, 17f, 17f, 18f, 12f)
                close()
            }
            if (isSelected) {
                drawPath(cup, color)
            } else {
                drawPath(cup, color, style = Stroke(stroke, join = StrokeJoin.Round))
            }
            // 奶油旋顶波浪
            val cream = Path().apply {
                moveTo(5.5f, 12f)
                cubicTo(7.5f, 9.5f, 10f, 10f, 12f, 8.5f)
                cubicTo(14f, 10f, 16.5f, 9.5f, 18.5f, 12f)
            }
            drawPath(cream, color, style = Stroke(stroke, cap = StrokeCap.Round))
            // 顶端小心形红草莓
            val strawberry = Path().apply {
                moveTo(12f, 8.5f)
                cubicTo(10f, 6.5f, 10.5f, 4f, 12f, 3f)
                cubicTo(13.5f, 4f, 14f, 6.5f, 12f, 8.5f)
                close()
            }
            drawPath(strawberry, color)
        }
    }

    override fun drawTabCategories(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val stroke = if (isSelected) 2.0f else 1.8f
            val contrast = if (surfaceColor != Color.Transparent && surfaceColor != Color.Unspecified) surfaceColor else Color.White
            // 顶层马卡龙饼干壳
            val topBun = Path().apply {
                moveTo(4f, 10f); cubicTo(4f, 5.5f, 20f, 5.5f, 20f, 10f); close()
            }
            // 底层马卡龙饼干壳
            val bottomBun = Path().apply {
                moveTo(4f, 14.5f); cubicTo(4f, 19f, 20f, 19f, 20f, 14.5f); close()
            }
            if (isSelected) {
                drawPath(topBun, color)
                drawPath(bottomBun, color)
                drawRoundRect(contrast, Offset(3.5f, 11f), Size(17f, 2.5f), CornerRadius(1.2f, 1.2f))
            } else {
                drawPath(topBun, color, style = Stroke(stroke))
                drawPath(bottomBun, color, style = Stroke(stroke))
                val creamWave = Path().apply {
                    moveTo(4.5f, 12.2f)
                    cubicTo(7f, 11f, 9f, 13.5f, 12f, 12.2f)
                    cubicTo(15f, 11f, 17f, 13.5f, 19.5f, 12.2f)
                }
                drawPath(creamWave, color, style = Stroke(stroke * 0.9f, cap = StrokeCap.Round))
            }
        }
    }

    override fun drawTabSearch(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val stroke = if (isSelected) 2.0f else 1.8f
            val contrast = if (surfaceColor != Color.Transparent && surfaceColor != Color.Unspecified) surfaceColor else Color.White
            // 饱满爱心棒棒糖
            val heart = Path().apply {
                moveTo(12f, 6.5f)
                cubicTo(12f, 3.5f, 7.5f, 3f, 6.5f, 6f)
                cubicTo(5.5f, 9f, 10f, 12.5f, 12f, 14.5f)
                cubicTo(14f, 12.5f, 18.5f, 9f, 17.5f, 6f)
                cubicTo(16.5f, 3f, 12f, 3.5f, 12f, 6.5f)
                close()
            }
            if (isSelected) {
                drawPath(heart, color)
                drawCircle(contrast, 1.2f, Offset(9.5f, 6.5f))
            } else {
                drawPath(heart, color, style = Stroke(stroke))
                val swirl = Path().apply {
                    moveTo(9.5f, 6.5f); cubicTo(10f, 5.5f, 13.5f, 6f, 13f, 8f)
                }
                drawPath(swirl, color, style = Stroke(stroke * 0.75f, cap = StrokeCap.Round))
            }
            // 细长糖棍
            drawLine(color, Offset(12f, 14.5f), Offset(12f, 22f), stroke * 1.1f, StrokeCap.Round)
        }
    }

    override fun drawTabProfile(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val stroke = if (isSelected) 2.0f else 1.8f
            val contrast = if (surfaceColor != Color.Transparent && surfaceColor != Color.Unspecified) surfaceColor else Color.White
            // 软萌蝴蝶结左右双翼
            val leftWing = Path().apply {
                moveTo(11f, 10.5f); cubicTo(7f, 6f, 3f, 7f, 3.5f, 11.5f); cubicTo(3f, 16f, 7f, 17f, 11f, 12.5f); close()
            }
            val rightWing = Path().apply {
                moveTo(13f, 10.5f); cubicTo(17f, 6f, 21f, 7f, 20.5f, 11.5f); cubicTo(21f, 16f, 17f, 17f, 13f, 12.5f); close()
            }
            if (isSelected) {
                drawPath(leftWing, color)
                drawPath(rightWing, color)
                drawCircle(contrast, 2.4f, Offset(12f, 11.5f))
                drawCircle(color, 1.6f, Offset(12f, 11.5f))
            } else {
                drawPath(leftWing, color, style = Stroke(stroke, join = StrokeJoin.Round))
                drawPath(rightWing, color, style = Stroke(stroke, join = StrokeJoin.Round))
                drawCircle(color, 2f, Offset(12f, 11.5f))
            }
            // 垂下的两根波浪飘带
            val ribbonLeft = Path().apply { moveTo(10.5f, 13f); cubicTo(9f, 17f, 6f, 18.5f, 5.5f, 21.5f) }
            val ribbonRight = Path().apply { moveTo(13.5f, 13f); cubicTo(15f, 17f, 18f, 18.5f, 18.5f, 21.5f) }
            drawPath(ribbonLeft, color, style = Stroke(stroke, cap = StrokeCap.Round))
            drawPath(ribbonRight, color, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }

    override fun drawCreateTopic(scope: DrawScope, color: Color, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            // 星愿草莓魔法棒
            drawLine(color, Offset(6f, 18f), Offset(15f, 9f), 2.0f, StrokeCap.Round)
            val star = Path().apply {
                moveTo(17f, 4f)
                lineTo(18.5f, 7.5f); lineTo(22f, 8f); lineTo(19.5f, 10.5f)
                lineTo(20f, 14f); lineTo(17f, 12f); lineTo(14f, 14f)
                lineTo(14.5f, 10.5f); lineTo(12f, 8f); lineTo(15.5f, 7.5f)
                close()
            }
            drawPath(star, color)
        }
    }

    override fun drawReplyFab(scope: DrawScope, color: Color, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val contrast = if (surfaceColor != Color.Transparent && surfaceColor != Color.Unspecified) surfaceColor else Color.White
            // 草莓爱心气泡
            val heartOuter = Path().apply {
                moveTo(12f, 6f)
                cubicTo(12f, 2.5f, 6.5f, 2f, 5.5f, 6f)
                cubicTo(4.5f, 10f, 10f, 14.5f, 12f, 17.5f)
                cubicTo(14f, 14.5f, 19.5f, 10f, 18.5f, 6f)
                cubicTo(17.5f, 2f, 12f, 2.5f, 12f, 6f)
                close()
            }
            drawPath(heartOuter, color)
            val heartInner = Path().apply {
                moveTo(12f, 8.5f)
                cubicTo(12f, 6.5f, 8.5f, 6f, 8f, 8.5f)
                cubicTo(7.5f, 11f, 11f, 13.5f, 12f, 15f)
                cubicTo(13f, 13.5f, 16.5f, 11f, 16f, 8.5f)
                cubicTo(15.5f, 6f, 12f, 6.5f, 12f, 8.5f)
                close()
            }
            drawPath(heartInner, contrast)
            drawCircle(contrast, 1.2f, Offset(16f, 17f))
        }
    }

    override fun drawAssistiveHub(scope: DrawScope, color: Color, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val heart = Path().apply {
                moveTo(12f, 7.5f)
                cubicTo(12f, 4f, 6.5f, 3.5f, 5.5f, 7.5f)
                cubicTo(4.5f, 11.5f, 10f, 16f, 12f, 18.5f)
                cubicTo(14f, 16.5f, 19.5f, 11.5f, 18.5f, 7.5f)
                cubicTo(17.5f, 3.5f, 12f, 4f, 12f, 7.5f)
                close()
            }
            drawPath(heart, color)
            val contrast = if (surfaceColor != Color.Transparent && surfaceColor != Color.Unspecified) surfaceColor else Color(0xFFFF4D6D)
            drawCircle(contrast, 1.2f, Offset(8.8f, 8.5f))
        }
    }

    override fun drawThemeBannerArt(scope: DrawScope, isDark: Boolean) {
        val candyTop = if (isDark) Color(0xFF2B1B22) else Color(0xFFFFCCD5)
        val candyBottom = if (isDark) Color(0xFF3B1F2B) else Color(0xFFFFB3C1)
        scope.drawRect(
            androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(candyTop, candyBottom))
        )
        val cream = Path().apply {
            moveTo(0f, scope.size.height * 0.6f)
            cubicTo(scope.size.width * 0.25f, scope.size.height * 0.4f, scope.size.width * 0.5f, scope.size.height * 0.75f, scope.size.width * 0.75f, scope.size.height * 0.5f)
            cubicTo(scope.size.width * 0.9f, scope.size.height * 0.6f, scope.size.width, scope.size.height * 0.45f, scope.size.width, scope.size.height * 0.45f)
            lineTo(scope.size.width, scope.size.height); lineTo(0f, scope.size.height); close()
        }
        scope.drawPath(cream, Color.White.copy(alpha = if (isDark) 0.08f else 0.45f))
        val heart = Path().apply {
            val cx = scope.size.width * 0.8f
            val cy = scope.size.height * 0.35f
            moveTo(cx, cy)
            cubicTo(cx - 6f, cy - 8f, cx - 12f, cy + 2f, cx, cy + 10f)
            cubicTo(cx + 12f, cy + 2f, cx + 6f, cy - 8f, cx, cy)
            close()
        }
        scope.drawPath(heart, Color(0xFFFF4D6D).copy(alpha = 0.4f))
        scope.drawCircle(Color(0xFFFF758F).copy(alpha = 0.5f), 3.5f, Offset(scope.size.width * 0.25f, scope.size.height * 0.4f))
        scope.drawCircle(Color(0xFFFF758F).copy(alpha = 0.35f), 2f, Offset(scope.size.width * 0.32f, scope.size.height * 0.25f))
    }
}

// ─────────────────────────────────────────────────────────────
// 5. 赛博幻境物象（矩阵终端、全息方块、赛博雷达、机械护目镜、量子信标）
// ─────────────────────────────────────────────────────────────
object CyberNeonSkinIcons : SkinIcons {
    override fun drawTabLatest(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val stroke = if (isSelected) 2.0f else 1.8f
            drawRoundRect(color, Offset(3.5f, 4.5f), Size(17f, 15f), CornerRadius(2.5f, 2.5f), style = Stroke(stroke))
            // 终端提示符 >_
            val prompt = Path().apply {
                moveTo(7f, 9f); lineTo(10f, 12f); lineTo(7f, 15f)
            }
            drawPath(prompt, color, style = Stroke(stroke * 1.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawLine(color, Offset(12f, 15f), Offset(16.5f, 15f), stroke * 1.1f, StrokeCap.Round)
        }
    }

    override fun drawTabCategories(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val stroke = if (isSelected) 2.0f else 1.8f
            // 3D 等轴测全息立方体
            val center = Offset(12f, 12f)
            val pTop = Offset(12f, 4f)
            val pBottom = Offset(12f, 20f)
            val pLeftTop = Offset(4.5f, 8f)
            val pLeftBottom = Offset(4.5f, 16f)
            val pRightTop = Offset(19.5f, 8f)
            val pRightBottom = Offset(19.5f, 16f)

            // 外轮廓
            val cube = Path().apply {
                moveTo(pTop.x, pTop.y)
                lineTo(pRightTop.x, pRightTop.y)
                lineTo(pRightBottom.x, pRightBottom.y)
                lineTo(pBottom.x, pBottom.y)
                lineTo(pLeftBottom.x, pLeftBottom.y)
                lineTo(pLeftTop.x, pLeftTop.y)
                close()
            }
            drawPath(cube, color, style = Stroke(stroke, join = StrokeJoin.Round))
            // 内部汇聚三棱
            drawLine(color, center, pTop, stroke * 0.9f)
            drawLine(color, center, pLeftBottom, stroke * 0.9f)
            drawLine(color, center, pRightBottom, stroke * 0.9f)
            if (isSelected) {
                drawCircle(color, 2f, center)
            }
        }
    }

    override fun drawTabSearch(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val stroke = if (isSelected) 2.0f else 1.8f
            // 雷达扫描同心环
            drawCircle(color, 8.5f, Offset(12f, 12f), style = Stroke(stroke))
            drawCircle(color, 4.5f, Offset(12f, 12f), style = Stroke(stroke * 0.75f))
            // 扫描瞄准十字线
            drawLine(color, Offset(12f, 3.5f), Offset(12f, 20.5f), stroke * 0.75f)
            drawLine(color, Offset(3.5f, 12f), Offset(20.5f, 12f), stroke * 0.75f)
            // 目标扫描亮点
            drawCircle(color, 1.8f, Offset(15.5f, 8.5f))
        }
    }

    override fun drawTabProfile(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val stroke = if (isSelected) 2.0f else 1.8f
            // 战术目镜 HUD 外框
            val visor = Path().apply {
                moveTo(3.5f, 8f); lineTo(20.5f, 8f); lineTo(18.5f, 16f); lineTo(14f, 18f); lineTo(10f, 18f); lineTo(5.5f, 16f); close()
            }
            if (isSelected) {
                drawPath(visor, color)
                drawLine(Color.Black, Offset(6f, 12f), Offset(18f, 12f), 1.5f, StrokeCap.Round)
            } else {
                drawPath(visor, color, style = Stroke(stroke, join = StrokeJoin.Round))
                drawLine(color, Offset(6f, 12f), Offset(18f, 12f), stroke * 0.8f, StrokeCap.Round)
            }
        }
    }

    override fun drawCreateTopic(scope: DrawScope, color: Color, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            // 光子军刀
            drawLine(color, Offset(5f, 19f), Offset(9f, 15f), 3.0f, StrokeCap.Square)
            drawLine(color, Offset(9f, 15f), Offset(20f, 4f), 1.8f, StrokeCap.Round)
            drawCircle(color, 1.2f, Offset(20f, 4f))
        }
    }

    override fun drawReplyFab(scope: DrawScope, color: Color, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            // 六边形量子脉冲信标
            val hex = Path().apply {
                moveTo(12f, 4f); lineTo(19f, 8f); lineTo(19f, 16f); lineTo(12f, 20f); lineTo(5f, 16f); lineTo(5f, 8f); close()
            }
            drawPath(hex, color, style = Stroke(1.8f, join = StrokeJoin.Round))
            drawCircle(color, 2.5f, Offset(12f, 12f))
        }
    }

    override fun drawAssistiveHub(scope: DrawScope, color: Color, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val hex = Path().apply {
                moveTo(12f, 3.5f); lineTo(19.5f, 7.8f); lineTo(19.5f, 16.2f)
                lineTo(12f, 20.5f); lineTo(4.5f, 16.2f); lineTo(4.5f, 7.8f); close()
            }
            drawPath(hex, color, style = Stroke(2.0f, join = StrokeJoin.Round))
            drawCircle(color, 2.5f, Offset(12f, 12f))
        }
    }

    override fun drawThemeBannerArt(scope: DrawScope, isDark: Boolean) {
        scope.drawRect(Color(0xFF0B0C10))
        val horizonY = scope.size.height * 0.45f
        val gridColor = Color(0xFF00F5D4).copy(alpha = 0.25f)
        repeat(4) { i ->
            val y = horizonY + (i + 1) * (scope.size.height - horizonY) / 4f
            scope.drawLine(gridColor, Offset(0f, y), Offset(scope.size.width, y), 1f)
        }
        repeat(7) { i ->
            val x = (i + 1) * scope.size.width / 8f
            scope.drawLine(gridColor, Offset(scope.size.width * 0.5f, horizonY), Offset(x, scope.size.height), 1f)
        }
        val pulseColor = Color(0xFF7B2CBF).copy(alpha = 0.4f)
        scope.drawRect(pulseColor, Offset(scope.size.width * 0.4f, horizonY - 14f), Size(12f, 14f))
        scope.drawRect(pulseColor, Offset(scope.size.width * 0.45f, horizonY - 22f), Size(16f, 22f))
        scope.drawRect(pulseColor, Offset(scope.size.width * 0.53f, horizonY - 18f), Size(14f, 18f))
    }
}

// ─────────────────────────────────────────────────────────────
// 6. 空山新雨物象（初展嫩芽、竹韵茶盘、雨落涟漪、温润茶盏、翠竹信笺）
// ─────────────────────────────────────────────────────────────
object ForestTeaSkinIcons : SkinIcons {
    override fun drawTabLatest(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val stroke = if (isSelected) 2.0f else 1.8f
            // 茎干
            drawLine(color, Offset(12f, 21f), Offset(12f, 7f), stroke, StrokeCap.Round)
            // 左嫩叶
            val leafLeft = Path().apply {
                moveTo(12f, 13f); cubicTo(8f, 13f, 6f, 9f, 7f, 6.5f); cubicTo(10f, 6.5f, 11.5f, 9.5f, 12f, 13f); close()
            }
            // 右嫩叶
            val leafRight = Path().apply {
                moveTo(12f, 10f); cubicTo(15f, 10f, 18f, 7f, 17f, 4.5f); cubicTo(14f, 4.5f, 12.5f, 7f, 12f, 10f); close()
            }
            if (isSelected) {
                drawPath(leafLeft, color)
                drawPath(leafRight, color)
            } else {
                drawPath(leafLeft, color, style = Stroke(stroke))
                drawPath(leafRight, color, style = Stroke(stroke))
            }
        }
    }

    override fun drawTabCategories(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val stroke = if (isSelected) 2.0f else 1.8f
            // 竹托外盘
            drawRoundRect(color, Offset(3.5f, 3.5f), Size(17f, 17f), CornerRadius(3f, 3f), style = Stroke(stroke))
            // 十字竹节隔板
            drawLine(color, Offset(12f, 3.5f), Offset(12f, 20.5f), stroke * 0.9f)
            drawLine(color, Offset(3.5f, 12f), Offset(20.5f, 12f), stroke * 0.9f)
            if (isSelected) {
                drawCircle(color, 1.8f, Offset(7.5f, 7.5f))
                drawCircle(color, 1.8f, Offset(16.5f, 16.5f))
            }
        }
    }

    override fun drawTabSearch(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val stroke = if (isSelected) 2.0f else 1.8f
            // 雨滴滴入泛起的水纹涟漪
            drawArc(color, 0f, 360f, false, Offset(3.5f, 8f), Size(17f, 10f), style = Stroke(stroke))
            drawArc(color, 0f, 360f, false, Offset(7.5f, 10.5f), Size(9f, 5f), style = Stroke(stroke * 0.75f))
            // 核心清晨水滴
            val droplet = Path().apply {
                moveTo(12f, 3f); cubicTo(10f, 6.5f, 10f, 8.5f, 12f, 9.5f); cubicTo(14f, 8.5f, 14f, 6.5f, 12f, 3f); close()
            }
            drawPath(droplet, color)
        }
    }

    override fun drawTabProfile(scope: DrawScope, color: Color, isSelected: Boolean, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val stroke = if (isSelected) 2.0f else 1.8f
            // 温润茶盏
            val cup = Path().apply {
                moveTo(4.5f, 11f); lineTo(19.5f, 11f); cubicTo(19f, 19f, 5f, 19f, 4.5f, 11f); close()
            }
            if (isSelected) {
                drawPath(cup, color)
            } else {
                drawPath(cup, color, style = Stroke(stroke, join = StrokeJoin.Round))
            }
            // 盏底托垫
            drawLine(color, Offset(8f, 20.5f), Offset(16f, 20.5f), stroke * 1.2f, StrokeCap.Round)
            // 袅袅茶烟
            val steam = Path().apply {
                moveTo(10f, 8.5f); cubicTo(11f, 6.5f, 9.5f, 4.5f, 10.5f, 3f)
                moveTo(14f, 8.5f); cubicTo(13f, 6.5f, 14.5f, 4.5f, 13.5f, 3f)
            }
            drawPath(steam, color, style = Stroke(stroke * 0.75f, cap = StrokeCap.Round))
        }
    }

    override fun drawCreateTopic(scope: DrawScope, color: Color, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            // 青竹毛笔
            drawLine(color, Offset(5f, 19f), Offset(17f, 7f), 2.2f, StrokeCap.Round)
            val tip = Path().apply {
                moveTo(17f, 7f); lineTo(19.5f, 4.5f); lineTo(20.5f, 7.5f); lineTo(18f, 8f); close()
            }
            drawPath(tip, color)
        }
    }

    override fun drawReplyFab(scope: DrawScope, color: Color, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            // 翠竹折叠信笺
            val letter = Path().apply {
                moveTo(4f, 7f); lineTo(20f, 7f); lineTo(20f, 18f); lineTo(4f, 18f); close()
            }
            drawPath(letter, color, style = Stroke(1.8f, join = StrokeJoin.Round))
            val fold = Path().apply {
                moveTo(4f, 7f); lineTo(12f, 13f); lineTo(20f, 7f)
            }
            drawPath(fold, color, style = Stroke(1.4f, join = StrokeJoin.Round))
        }
    }

    override fun drawAssistiveHub(scope: DrawScope, color: Color, surfaceColor: Color) {
        scope.scale(scope.size.width / 24f, scope.size.height / 24f, pivot = Offset.Zero) {
            val leaf1 = Path().apply {
                moveTo(12f, 14f); cubicTo(7f, 14f, 5f, 9f, 6f, 6f); cubicTo(10f, 6f, 11.5f, 10f, 12f, 14f); close()
            }
            val leaf2 = Path().apply {
                moveTo(12f, 11f); cubicTo(16f, 11f, 19f, 7f, 18f, 4.5f); cubicTo(14.5f, 4.5f, 12.5f, 7.5f, 12f, 11f); close()
            }
            drawPath(leaf1, color)
            drawPath(leaf2, color)
            drawLine(color, Offset(12f, 20f), Offset(12f, 9f), 1.8f, StrokeCap.Round)
        }
    }

    override fun drawThemeBannerArt(scope: DrawScope, isDark: Boolean) {
        val forestTop = if (isDark) Color(0xFF132A13) else Color(0xFFD8F3DC)
        val forestBottom = if (isDark) Color(0xFF1F3A24) else Color(0xFFB7E4C7)
        scope.drawRect(
            androidx.compose.ui.graphics.Brush.verticalGradient(listOf(forestTop, forestBottom))
        )
        val bambooColor = (if (isDark) Color(0xFF52B788) else Color(0xFF40916C)).copy(alpha = 0.35f)
        scope.drawLine(bambooColor, Offset(scope.size.width * 0.78f, scope.size.height), Offset(scope.size.width * 0.78f, 0f), 3.5f)
        scope.drawLine(bambooColor, Offset(scope.size.width * 0.86f, scope.size.height), Offset(scope.size.width * 0.86f, 0f), 2.5f)
        val rainColor = (if (isDark) Color.White else Color(0xFF2D6A4F)).copy(alpha = 0.18f)
        repeat(6) { i ->
            val startX = (i + 1) * scope.size.width / 7f
            scope.drawLine(rainColor, Offset(startX, 10f), Offset(startX - 15f, 35f), 1.2f, StrokeCap.Round)
        }
    }
}
