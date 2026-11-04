package io.github.eslamasabry.oc.engine.integrations.usage

import kotlin.math.roundToInt
import kotlin.time.Instant

// Paseo's `provider.usage.list.response` (and this phone's agent host, which speaks the same list), ported from Flutter
// lib/domain/provider_usage.dart. Display numbers only: plan, source, balances, details and error text are dropped.

public data class ProviderUsageWindowReading(
    /** The window's length when its id names one (`five_hour`, `weekly`). */
    val durationSeconds: Long?,
    /** Cleaned daemon label, shown only when the length is unknown. */
    val label: String,
    val remainingPercent: Int,
    val resetsAt: Instant?,
)

public data class ProviderUsageReading(
    /** Lower-case id such as `claude` or `codex`. */
    val providerId: String,
    val displayName: String,
    val fetchedAt: Instant,
    val windows: List<ProviderUsageWindowReading>,
    /** The daemon reported this provider as not signed in: no numbers, only the way in (the widget offers Sign in). */
    val needsSignIn: Boolean = false,
)

/** The payload was not a provider list. Carries no payload text. */
public class ProviderUsageListInvalid : Exception("not a provider usage list")

private val providerIdPattern = Regex("^[a-z0-9][a-z0-9_-]{0,31}$")

private fun isControl(unit: Int): Boolean =
    unit < 0x20 || unit in 0x7f..0x9f || unit in 0x200b..0x200f || unit in 0x202a..0x202e || unit in 0x2066..0x2069

/** Strips control and direction characters, collapses blanks and caps the length. Null when nothing printable is left. */
public fun cleanUsageText(value: Any?, maxLength: Int): String? {
    if (value !is String) return null
    val replaced = buildString(value.length) { for (c in value) append(if (isControl(c.code)) ' ' else c) }
    val text = replaced.replace(Regex("\\s+"), " ").trim()
    if (text.isEmpty()) return null
    val points = text.codePointCount()
    if (points <= maxLength) return text
    return text.takeCodePoints(maxLength).trimEnd()
}

private fun String.codePointCount(): Int {
    var n = 0
    var i = 0
    while (i < length) {
        i += if (this[i].isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate()) 2 else 1
        n++
    }
    return n
}

private fun String.takeCodePoints(count: Int): String {
    var n = 0
    var i = 0
    while (i < length && n < count) {
        i += if (this[i].isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate()) 2 else 1
        n++
    }
    return substring(0, i)
}

/** Window lengths the daemon's window ids name. */
public fun usageWindowSeconds(id: String): Long? =
    when (id) {
        "five_hour", "5h" -> 5L * 3600
        "weekly", "seven_day" -> 7L * 86_400
        else -> null
    }

private fun windowPriority(w: ProviderUsageWindowReading): Int =
    when (w.durationSeconds) {
        18_000L -> 0
        604_800L -> 1
        else -> 2
    }

private fun time(value: Any?): Instant? {
    if (value !is String || value.length > 64) return null
    return try {
        Instant.parse(value)
    } catch (e: IllegalArgumentException) {
        null
    }
}

/** Providers the app signs in through the daemon: only they turn `unavailable` into "Sign in". */
private val daemonSignInProviders = setOf("claude")

/**
 * Tolerant parse of a provider usage list. Unknown providers, statuses and fields are ignored; a provider that is not
 * `available`, or a window without a number, is skipped. Throws [ProviderUsageListInvalid] only when [payload] is not a
 * provider list.
 */
public fun parseProviderUsageList(payload: Any?, now: Instant, maxProviders: Int = 16, maxWindows: Int = 3): List<ProviderUsageReading> {
    if (payload !is Map<*, *> || payload["providers"] !is List<*>) throw ProviderUsageListInvalid()
    val listFetched = time(payload["fetchedAt"])
    val readings = ArrayList<ProviderUsageReading>()
    val seen = HashSet<String>()
    for (raw in (payload["providers"] as List<*>).take(maxProviders * 2)) {
        if (raw !is Map<*, *>) continue
        val status = raw["status"]
        val id = raw["providerId"] as? String ?: continue
        if (!providerIdPattern.matches(id)) continue
        val signedOut = id in daemonSignInProviders && status == "unavailable"
        if (status != "available" && !signedOut) continue
        if (!seen.add(id)) continue
        if (signedOut) {
            readings +=
                ProviderUsageReading(
                    providerId = id,
                    displayName = cleanUsageText(raw["displayName"], 24) ?: id,
                    fetchedAt = time(raw["fetchedAt"]) ?: listFetched ?: now,
                    windows = emptyList(),
                    needsSignIn = true,
                )
            continue
        }
        val rawWindows = raw["windows"] as? List<*> ?: continue
        val windows = ArrayList<ProviderUsageWindowReading>()
        for (item in rawWindows.take(16)) {
            if (item !is Map<*, *>) continue
            val windowId = item["id"] as? String ?: continue
            val remaining = item["remainingPct"] as? Number
            val used = item["usedPct"] as? Number
            val left = remaining?.toDouble() ?: used?.let { 100 - it.toDouble() } ?: continue
            if (!left.isFinite()) continue
            val seconds = usageWindowSeconds(windowId)
            val label = cleanUsageText(item["label"], 24)
            if (seconds == null && label == null) continue
            windows +=
                ProviderUsageWindowReading(
                    durationSeconds = seconds,
                    label = label ?: "",
                    remainingPercent = left.coerceIn(0.0, 100.0).roundToInt(),
                    resetsAt = time(item["resetsAt"]),
                )
        }
        if (windows.isEmpty()) continue
        windows.sortBy { windowPriority(it) }
        var fetched = time(raw["fetchedAt"]) ?: listFetched ?: now
        if (fetched > now) fetched = now
        readings +=
            ProviderUsageReading(
                providerId = id,
                displayName = cleanUsageText(raw["displayName"], 24) ?: id,
                fetchedAt = fetched,
                windows = windows.take(maxWindows),
            )
        if (readings.size >= maxProviders) break
    }
    return readings
}
