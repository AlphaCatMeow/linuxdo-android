package org.linuxdo.android.data

import kotlinx.serialization.Serializable
import org.jsoup.Jsoup

@Serializable
data class TrustRingMetric(
    val label: String,
    val current: Int,
    val max: Int,
    val isMet: Boolean,
)

@Serializable
data class TrustBarMetric(
    val label: String,
    val current: Int,
    val max: Int,
    val progress: Float,
    val isMet: Boolean,
)

@Serializable
data class TrustQuotaMetric(
    val label: String,
    val value: String,
    val usedSlots: Int,
    val totalSlots: Int = 5,
    val isMet: Boolean,
)

@Serializable
data class TrustVetoMetric(
    val label: String,
    val desc: String,
    val value: String,
    val isMet: Boolean,
)

@Serializable
data class TrustReport(
    val title: String,
    val statusLabel: String,
    val isStatusMet: Boolean,
    val subtitle: String,
    val periodDays: Int,
    val rings: List<TrustRingMetric>,
    val bars: List<TrustBarMetric>,
    val quotas: List<TrustQuotaMetric>,
    val vetos: List<TrustVetoMetric>,
    val footerHint: String = "",
    val statusNotice: String = "",
    val emptyState: Boolean = false,
) {
    /**
     * 各项指标综合达成的平滑进度 (0f ~ 1f)。
     * 涵盖活跃程度(rings)、互动参与(bars)、合规记录(quotas)和一票否决(vetos)。
     * 单项达成率上限 1.0f，全部项求算术平均。若未达到 [isStatusMet]，最高上限为 0.99f。
     */
    val overallProgress: Float
        get() {
            if (isStatusMet) return 1f
            val items = mutableListOf<Float>()
            rings.forEach { ring ->
                val p = if (ring.max > 0) (ring.current.toFloat() / ring.max).coerceIn(0f, 1f)
                else if (ring.isMet) 1f else 0f
                items.add(p)
            }
            bars.forEach { bar ->
                val p = if (bar.progress > 0f) bar.progress.coerceIn(0f, 1f)
                else if (bar.max > 0) (bar.current.toFloat() / bar.max).coerceIn(0f, 1f)
                else if (bar.isMet) 1f else 0f
                items.add(p)
            }
            quotas.forEach { quota ->
                items.add(if (quota.isMet) 1f else 0f)
            }
            vetos.forEach { veto ->
                items.add(if (veto.isMet) 1f else 0f)
            }
            if (items.isEmpty()) return 0f
            return items.average().toFloat().coerceIn(0f, 0.99f)
        }

    /** 综合进度整数百分比 (0 ~ 100)。未达到时上限为 99%。 */
    val overallPercent: Int
        get() = if (isStatusMet) 100 else (overallProgress * 100f).toInt().coerceIn(0, 99)

    /** 已达标指标项总数 */
    val metMetricsCount: Int
        get() = rings.count { it.isMet } + bars.count { it.isMet } + quotas.count { it.isMet } + vetos.count { it.isMet }

    /** 待完成指标项总数 */
    val unmetMetricsCount: Int
        get() = totalMetricsCount - metMetricsCount

    /** 考评总指标项数 */
    val totalMetricsCount: Int
        get() = rings.size + bars.size + quotas.size + vetos.size

    companion object {
        fun fromHtml(html: String): TrustReport? {
            val document = Jsoup.parse(html)
            val card = document.selectFirst("div.card") ?: return null
            val subtitle = card.selectFirst(".card-subtitle")?.text()?.trim().orEmpty()
            val period = PERIOD.find(subtitle)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 100
            val isEmpty = card.hasClass("empty-state")

            // 1. 活跃程度环形指标 (Rings)
            val rings = card.select(".tl3-ring").mapNotNull { element ->
                val label = element.selectFirst(".tl3-ring-label")?.text()?.trim().orEmpty()
                if (label.isEmpty()) return@mapNotNull null
                val circle = element.selectFirst(".tl3-ring-circle")
                val style = circle?.attr("style").orEmpty()
                val value = cssValue(style, "val") ?: element.text().let(::firstNumber)
                val target = cssValue(style, "max") ?: 1
                val isMet = circle?.hasClass("met") == true || value >= target
                TrustRingMetric(label = label, current = value, max = target, isMet = isMet)
            }

            // 2. 互动参与条形指标 (Bars)
            val bars = card.select(".tl3-bar-item").mapNotNull { element ->
                val label = element.selectFirst(".tl3-bar-label")?.text()?.trim().orEmpty()
                if (label.isEmpty()) return@mapNotNull null
                val numberText = element.selectFirst(".tl3-bar-nums")?.text().orEmpty()
                val fill = element.selectFirst(".tl3-bar-fill")
                val style = fill?.attr("style").orEmpty()
                val value = firstNumber(numberText).takeIf { numberText.isNotBlank() }
                    ?: cssValue(style, "val")
                    ?: element.text().let(::firstNumber)
                val target = cssValue(style, "max") ?: 1
                val isMet = fill?.hasClass("met") == true || value >= target
                val progress = if (target > 0) (value.toFloat() / target).coerceIn(0f, 1f) else 0f
                TrustBarMetric(label = label, current = value, max = target, progress = progress, isMet = isMet)
            }

            // 3. 合规记录槽位指标 (Quotas: 被举报帖子、举报用户)
            val quotas = card.select(".tl3-quota-card").mapNotNull { element ->
                val label = element.selectFirst(".tl3-quota-label")?.text()?.trim().orEmpty()
                if (label.isEmpty()) return@mapNotNull null
                val nums = element.selectFirst(".tl3-quota-nums")?.text()?.trim().orEmpty()
                val isMet = !element.hasClass("unmet")
                val usedSlots = element.select(".tl3-slot.used").size
                TrustQuotaMetric(label = label, value = nums, usedSlots = usedSlots, totalSlots = 5, isMet = isMet)
            }

            // 4. 一票否决指标 (Vetos: 被禁言、被封禁)
            val vetos = card.select(".tl3-veto-item").mapNotNull { element ->
                val isMet = !element.hasClass("unmet")
                val targetFace = if (isMet) element.selectFirst(".tl3-veto-front") else element.selectFirst(".tl3-veto-back")
                val face = targetFace ?: element
                val label = face.selectFirst(".tl3-veto-label")?.text()?.trim().orEmpty()
                if (label.isEmpty()) return@mapNotNull null
                val desc = face.selectFirst(".tl3-veto-desc")?.text()?.trim().orEmpty()
                val value = face.selectFirst(".tl3-veto-value")?.text()?.trim().orEmpty().ifBlank { "0" }
                TrustVetoMetric(label = label, desc = desc, value = value, isMet = isMet)
            }

            // 5. 顶栏状态与底部提示
            val badge = card.selectFirst(".badge")
            val badgeText = badge?.text()?.trim().orEmpty()
            val isStatusMet = badge?.hasClass("badge-success") == true || badgeText.contains("已达到")

            val hintEl = card.selectFirst(".text-hint")
            val footerHint = hintEl?.text()?.trim().orEmpty()

            val statusEl = card.selectFirst(".status-met, .status-unmet")
            val statusNotice = statusEl?.text()?.trim().orEmpty()

            return TrustReport(
                title = card.selectFirst("h2.card-title")?.text()?.trim().orEmpty().ifBlank { "信任级别 3 的要求" },
                statusLabel = badgeText.ifBlank { if (isStatusMet) "已达到" else "未达到" },
                isStatusMet = isStatusMet,
                subtitle = subtitle,
                periodDays = period,
                rings = rings,
                bars = bars,
                quotas = quotas,
                vetos = vetos,
                footerHint = footerHint,
                statusNotice = statusNotice,
                emptyState = isEmpty,
            )
        }

        private fun firstNumber(value: String): Int =
            NUMBER.find(value.replace(",", ""))?.value?.toIntOrNull() ?: 0

        private fun cssValue(style: String, name: String): Int? =
            CSS_VALUE.findAll(style).firstOrNull { it.groupValues.getOrNull(1) == name }
                ?.groupValues?.getOrNull(2)?.toFloatOrNull()?.toInt()

        private val PERIOD = Regex("(\\d+)\\s*(?:天|days)", RegexOption.IGNORE_CASE)
        private val NUMBER = Regex("-?\\d+")
        private val CSS_VALUE = Regex("--([a-z]+)\\s*:\\s*([0-9.]+)")
    }
}
