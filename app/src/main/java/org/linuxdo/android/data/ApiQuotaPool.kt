package org.linuxdo.android.data

import android.os.SystemClock

/**
 * 后台自动请求的独立计量池。
 *
 * 每个池子各自记录发车时间、429 惩罚与连击次数，互不牵连：未读轮询撞了限流，
 * 不会让摘要预取的惩罚期一起翻倍，反之亦然。
 *
 * 但 linux.do / Cloudflare 是按 IP + Cookie 合并计数的，客户端拆池只能控节奏，不能扩额度。
 * 所以这里只做"准入"，总闸仍是 [TopicRepository] 里的全局 429 冷却与读写双轨调度器，两层叠加：
 * - 池级：最小间隔 + 前台让行 + 撞墙后指数惩罚（后台请求不排队，直接跳过本轮）
 * - 全局：任何一次 429 都冻结所有自动请求，前台也一并退让
 */
enum class ApiQuotaPool(
    val label: String,
    /** 两次发车的最小间隔。0 表示不限（由全局读轨道兜底）。 */
    private val minIntervalMs: Long,
    /** 用户刚有前台请求或刚发过写请求时，本轮让出，避免贴脸叠加。 */
    private val yieldToForeground: Boolean,
    /** 撞到 429 后的基础惩罚时长，连续撞墙按 2^n 递增。 */
    private val penaltyBaseMs: Long,
) {
    /** 前台未读角标 / 通知分类轮询。 */
    UNREAD_POLL("未读轮询", 15_000L, true, 30_000L),

    /** 列表顶部"有 N 条新话题"的静默探测。 */
    INCOMING_PROBE("新话题探测", 15_000L, true, 40_000L),

    /** 列表首楼摘要的静默预取。前台活跃时让行并保持最小间隔，避免与前台抢占额度触发 429。 */
    EXCERPT_PREFETCH("摘要预取", 600L, true, 45_000L),
    ;

    private var lastAcquiredAt = 0L
    private var penaltyUntil = 0L
    private var strikes = 0

    /**
     * 申请一次发车。返回 false 时调用方应静默跳过本轮，不要抛异常、不要排队。
     * [force] 只跳过最小间隔与前台让行（用户主动触发），惩罚期与全局冷却仍然生效。
     */
    fun tryAcquire(force: Boolean = false): Boolean {
        if (TopicRepository.isRateLimitCoolingDown()) return false
        val now = SystemClock.elapsedRealtime()
        synchronized(this) {
            if (now < penaltyUntil) return false
            if (!force) {
                if (minIntervalMs > 0L && now - lastAcquiredAt < minIntervalMs) return false
                if (yieldToForeground && now - lastActivityAt() < FOREGROUND_QUIET_MS) return false
            }
            lastAcquiredAt = now
            return true
        }
    }

    /** 本池请求成功：清掉连击，惩罚期自然结束。 */
    fun onSuccess() {
        synchronized(this) { strikes = 0 }
    }

    /** 距离本池下次可发车还要等多久（含全局冷却），供轮询循环自适应休眠。 */
    fun remainingWaitMs(): Long {
        val now = SystemClock.elapsedRealtime()
        val poolWait = synchronized(this) {
            maxOf(penaltyUntil - now, lastAcquiredAt + minIntervalMs - now)
        }
        return maxOf(poolWait, TopicRepository.rateLimitRemainingMs(), 0L)
    }

    private fun penalize(globalCooldownMs: Long, cfChallenge: Boolean) {
        val now = SystemClock.elapsedRealtime()
        synchronized(this) {
            // 同一惩罚窗口的并发 429 只续期，不重复升级，否则首轮就会被顶到上限。
            val continuingPenalty = now < penaltyUntil
            val strikeLevel = if (continuingPenalty) (strikes - 1).coerceAtLeast(0) else strikes
            val factor = 1L shl strikeLevel.coerceAtMost(MAX_STRIKE_SHIFT)
            val base = if (cfChallenge) penaltyBaseMs * 2 else penaltyBaseMs
            val penalty = (base * factor).coerceAtMost(MAX_PENALTY_MS)
            // 惩罚期至少要盖过全局冷却，否则冷却一结束所有后台池会同一秒齐射
            penaltyUntil = maxOf(penaltyUntil, now + maxOf(penalty, globalCooldownMs))
            if (!continuingPenalty) strikes = (strikes + 1).coerceAtMost(MAX_STRIKE_SHIFT)
        }
    }

    fun penaltyRemainingMs(): Long = synchronized(this) {
        (penaltyUntil - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    }

    companion object {
        /** 前台请求 / 写请求之后，后台池需要让出的静默窗口。 */
        const val FOREGROUND_QUIET_MS = 3_000L
        private const val MAX_STRIKE_SHIFT = 3
        private const val MAX_PENALTY_MS = 1 * 60_000L

        @Volatile private var lastForegroundAt = 0L

        /** 用户直接触发的 GET 发出时调用。 */
        fun markForeground() {
            lastForegroundAt = SystemClock.elapsedRealtime()
        }

        private fun lastActivityAt(): Long = maxOf(lastForegroundAt, TopicRepository.lastPostRequestAt)

        /**
         * 服务端任意一次 429 都会进到这里：服务端是全局计数的，撞墙说明总量超了，
         * 所有后台池都要退让；被 CF 质询页拦下（cf-mitigated=challenge）时惩罚加倍。
         */
        fun onServerRateLimited(globalCooldownMs: Long, cfChallenge: Boolean) {
            entries.forEach { it.penalize(globalCooldownMs, cfChallenge) }
        }
    }
}
