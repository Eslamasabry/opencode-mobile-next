package io.github.eslamasabry.oc.engine.transcript.turnfacts

import io.github.eslamasabry.oc.core.model.transcript.ToolState

/**
 * B-CHAT-109, Dart `formatToolDuration`: a tool's wall-clock run time as the card shows it, before words. Tenths
 * of a second under a minute ("0.8s", "12.4s"), whole minutes and zero-padded seconds past it ("1m 05s").
 */
public sealed interface ToolDuration {
    /** `chatUiDurationSeconds` (strings_common.xml, "%1$ss"); [tenths] is its argument, e.g. "12.4". */
    public data class Seconds(val tenths: String) : ToolDuration

    /** `chatUiDurationMinutesSeconds` (strings_common.xml, "%1$sm %2$ss"); [seconds] is zero-padded, e.g. "05". */
    public data class MinutesSeconds(val minutes: Long, val seconds: String) : ToolDuration
}

/** The duration of [ms] milliseconds; a negative one reads as zero (Dart clamps it). */
public fun toolDuration(ms: Long): ToolDuration {
    val clamped = if (ms < 0) 0L else ms
    val minutes = clamped / 60_000
    if (minutes >= 1) return ToolDuration.MinutesSeconds(minutes, ((clamped / 1000) % 60).toString().padStart(2, '0'))
    return ToolDuration.Seconds(tenthsOfSeconds(clamped))
}

/**
 * What the English resources render (`"12.4s"`, `"1m 05s"`), for tests and parity checks only; a screen maps
 * [toolDuration] through the resources named on [ToolDuration] so other languages get their own words.
 */
public fun toolDurationLabel(ms: Long): String =
    when (val duration = toolDuration(ms)) {
        is ToolDuration.Seconds -> "${duration.tenths}s"
        is ToolDuration.MinutesSeconds -> "${duration.minutes}m ${duration.seconds}s"
    }

/** Dart `ToolState.duration`: null until both times are known; a negative span is zero. */
public fun toolDurationMs(state: ToolState): Long? {
    val start = state.startedAtMs ?: return null
    val end = state.completedAtMs ?: return null
    return (end - start).coerceAtLeast(0)
}

/**
 * Dart `(ms / 1000).toStringAsFixed(1)` for 0 <= ms < 60 000. Dart rounds the exact binary value of the double, so
 * a tie (ms ending in 50) rounds up only when `ms / 1000.0` lands on or above the decimal half (an exact half rounds
 * up, as double-conversion's ToFixed does); that is decided exactly from the double's bits.
 */
private fun tenthsOfSeconds(ms: Long): String {
    var tenths = ms / 100
    val rest = ms % 100
    if (rest > 50 || (rest == 50L && doubleIsAtOrAbove(ms))) tenths++
    return "${tenths / 10}.${tenths % 10}"
}

/** Whether the double nearest `ms / 1000` is at or above the exact `ms / 1000` (0 < ms < 60 000). */
private fun doubleIsAtOrAbove(ms: Long): Boolean {
    val bits = (ms / 1000.0).toRawBits()
    val exponent = ((bits shr 52) and 0x7FF).toInt()
    val mantissa = (bits and 0xFFFFFFFFFFFFFL) or (1L shl 52)
    // value = mantissa * 2^(exponent - 1075); compare mantissa * 1000 with ms * 2^(1075 - exponent).
    val shift = 1075 - exponent
    val left = mantissa * 1000
    val right = ms shl shift
    return left >= right
}
