package io.github.eslamasabry.oc.core.net.stream

import io.github.eslamasabry.oc.core.common.pace.Visibility
import kotlin.random.Random

/**
 * How long an event stream waits before its next try: the one reconnect schedule of the app (plan 04 §6.2, N4-D6,
 * extended by plan 13 amendment A2; PB-51, PB-53). Every retry wait in the app takes its value from [delayMs].
 *
 * `attempt` = failures so far (1 after the first failure). With [preIncrement] the exponent is the attempt itself
 * (OpenCode 2: first wait 1 s); without it the exponent is `attempt - 1` (Gas City: first wait 0.5 s).
 */
public data class BackoffPolicy(
    val baseMs: Long,
    val capMs: Long,
    val preIncrement: Boolean,
    /** Multiplier range, e.g. 0.5..1.5; null = no jitter. */
    val jitter: ClosedFloatingPointRange<Double>?,
    val resetAfterStableMs: Long = 30_000,
    /** From this many failures the status says Disconnected instead of Reconnecting; retrying continues. */
    val disconnectedLabelAfter: Int = 6,
    /** No byte for this long counts as a drop (N4-D7: 3 × the 15 s heartbeat). */
    val stallTimeoutMs: Long,
    /** A2: out of sight, tries up to this attempt use the normal formula; from the next one on, [hiddenRangeMs]. */
    val hiddenAfterAttempt: Int = 2,
    /** A2: out of sight, each later wait is a uniform draw from this range (jittered, so wakes never line up). */
    val hiddenRangeMs: LongRange = 60_000L..120_000L,
) {
    init {
        require(baseMs > 0 && capMs >= baseMs) { "0 < baseMs ≤ capMs" }
        require(hiddenAfterAttempt >= 0) { "hiddenAfterAttempt ≥ 0" }
        require(!hiddenRangeMs.isEmpty()) { "hiddenRangeMs is not empty" }
        require(hiddenRangeMs.first >= HIDDEN_MIN_MS) { "PB-51: ≤ 1 hidden attempt per 60 s" }
    }

    /** 04's formula, unchanged, for [Visibility.Foreground]. */
    public fun delayMs(attempt: Int, random: Random): Long {
        val exponent = (if (preIncrement) attempt else attempt - 1).coerceIn(0, MAX_EXPONENT)
        val raw = (baseMs shl exponent).coerceIn(baseMs, capMs)
        val range = jitter ?: return raw
        val factor = range.start + random.nextDouble() * (range.endInclusive - range.start)
        return (raw * factor).toLong().coerceAtMost(capMs)
    }

    /**
     * A2. Foreground → [delayMs] (attempt, random). HiddenWatching (HiddenLive / HiddenReplyWatch) → the same for
     * attempts ≤ [hiddenAfterAttempt], then a uniform draw from [hiddenRangeMs]. HiddenIdle → null: the stream is
     * cancelled (`Suspended`, 04 §6.4) and nothing is dialled (PB-53).
     */
    public fun delayMs(attempt: Int, random: Random, visibility: Visibility): Long? =
        when (visibility) {
            Visibility.Foreground -> delayMs(attempt, random)
            Visibility.HiddenWatching ->
                if (attempt <= hiddenAfterAttempt) {
                    delayMs(attempt, random)
                } else {
                    hiddenRangeMs.first + random.nextLong(hiddenRangeMs.last - hiddenRangeMs.first + 1)
                }
            Visibility.HiddenIdle -> null
        }

    public companion object {
        private const val MAX_EXPONENT = 16
        private const val HIDDEN_MIN_MS = 60_000L

        /** Flutter `sse2.dart` + `reconnect_backoff.dart`: 1, 2, 4, 8, 16 s cap, no jitter; hidden: 1, 2, then 60–120 s. */
        public val OpenCode2: BackoffPolicy = BackoffPolicy(500, 16_000, true, null, 30_000, 6, 45_000)

        /** Gas City (after the switch): `min(0.5 s × 2^n, 30 s) × U[0.5, 1.5)`, capped at 30 s; same hidden pace. */
        public val GasCity: BackoffPolicy =
            BackoffPolicy(500, 30_000, false, 0.5..1.5, 30_000, Int.MAX_VALUE, 90_000)
    }
}
