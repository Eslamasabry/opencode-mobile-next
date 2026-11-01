package io.github.eslamasabry.oc.core.net.stream

import kotlin.random.Random

/** Out of sight, after [quickTries] failures at the normal pace, each wait is uniform in [minMs]..[maxMs] (PB-51). */
public data class HiddenPace(val quickTries: Int, val minMs: Long, val maxMs: Long)

/**
 * How long an event stream waits before its next try (plan 04 §6.2, N4-D6; plan 13 PB-51).
 *
 * [delayMs] takes `attempt` = failures so far (1 after the first failure). With [preIncrement] the exponent is the
 * attempt itself (OpenCode 2: first wait 1 s); without it the exponent is `attempt - 1` (Gas City: first wait 0.5 s).
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
    val hidden: HiddenPace? = null,
) {
    public fun delayMs(attempt: Int, hidden: Boolean, random: Random): Long {
        val pace = this.hidden
        if (hidden && pace != null && attempt > pace.quickTries) {
            return pace.minMs + random.nextLong(pace.maxMs - pace.minMs + 1)
        }
        val exponent = (if (preIncrement) attempt else attempt - 1).coerceIn(0, MAX_EXPONENT)
        val raw = (baseMs shl exponent).coerceIn(baseMs, capMs)
        val range = jitter ?: return raw
        val factor = range.start + random.nextDouble() * (range.endInclusive - range.start)
        return (raw * factor).toLong().coerceAtMost(capMs)
    }

    public companion object {
        private const val MAX_EXPONENT = 16

        /** Flutter `sse2.dart` + `reconnect_backoff.dart`: 1, 2, 4, 8, 16 s cap, no jitter; hidden: 2 quick tries then 60–120 s. */
        public val OpenCode2: BackoffPolicy =
            BackoffPolicy(
                baseMs = 500, capMs = 16_000, preIncrement = true, jitter = null, stallTimeoutMs = 45_000,
                hidden = HiddenPace(quickTries = 2, minMs = 60_000, maxMs = 120_000),
            )

        /** Gas City (after the switch): `min(0.5 s × 2^n, 30 s) × U[0.5, 1.5)`, capped again at 30 s. */
        public val GasCity: BackoffPolicy =
            BackoffPolicy(
                baseMs = 500, capMs = 30_000, preIncrement = false, jitter = 0.5..1.5,
                disconnectedLabelAfter = Int.MAX_VALUE, stallTimeoutMs = 90_000,
                hidden = HiddenPace(quickTries = 2, minMs = 60_000, maxMs = 120_000),
            )
    }
}
