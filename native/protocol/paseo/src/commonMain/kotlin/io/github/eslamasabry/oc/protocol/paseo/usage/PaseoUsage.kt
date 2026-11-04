package io.github.eslamasabry.oc.protocol.paseo.usage

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.protocol.paseo.PaseoException
import io.github.eslamasabry.oc.protocol.paseo.arrayOrNull
import io.github.eslamasabry.oc.protocol.paseo.numberOrNull
import io.github.eslamasabry.oc.protocol.paseo.objectOrNull
import io.github.eslamasabry.oc.protocol.paseo.parseInstantMillis
import io.github.eslamasabry.oc.protocol.paseo.stringOrNull
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoTransport
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

/*
 * Plan usage a Paseo daemon reports for the agent providers it runs (`provider.usage.list`), a port of
 * lib/domain/provider_usage.dart and lib/paseo/provider_usage_gateway.dart (B-AGENT-189, 190). Display numbers only:
 * the daemon's free text (errors, source and plan labels, details) is never kept, and the two names that are kept
 * are cleaned and length-capped.
 */

/** One limit window: its length when its id names one (`five_hour`, `weekly`), else its cleaned label. */
public data class ProviderUsageWindow(
    val durationSeconds: Int?,
    /** Cleaned daemon label, shown only when the length is unknown. */
    val label: String,
    /** 0..100. */
    val remainingPercent: Int,
    val resetsAtMs: Long?,
)

public data class ProviderUsageReading(
    /** Lower-case id such as `claude` or `codex`. */
    val providerId: String,
    val displayName: String,
    val fetchedAtMs: Long,
    val windows: List<ProviderUsageWindow>,
    /** The daemon reported this provider as not signed in: no numbers, only the way in. */
    val needsSignIn: Boolean = false,
)

private val PROVIDER_ID = Regex("^[a-z0-9][a-z0-9_-]{0,31}$")
private val BLANKS = Regex("\\s+")
private val SIGN_IN_PROVIDERS = setOf("claude")

private fun isControl(unit: Int): Boolean =
    unit < 0x20 || unit in 0x7f..0x9f || unit in 0x200b..0x200f || unit in 0x202a..0x202e || unit in 0x2066..0x2069

/** Strips control and direction characters, collapses blanks and caps the length (in code points); null when empty. */
public fun cleanUsageText(value: JsonElement?, maxLength: Int): String? {
    val raw = value.stringOrNull() ?: return null
    val text = buildString(raw.length) { raw.forEach { append(if (isControl(it.code)) ' ' else it) } }.replace(BLANKS, " ").trim()
    if (text.isEmpty()) return null
    var points = 0
    var i = 0
    while (i < text.length) {
        if (points == maxLength) return text.substring(0, i).trimEnd()
        i += if (text[i].isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) 2 else 1
        points++
    }
    return text
}

/** Window lengths the daemon's window ids name. */
public fun usageWindowSeconds(id: String): Int? =
    when (id) {
        "five_hour", "5h" -> 5 * 3600
        "weekly", "seven_day" -> 7 * 86400
        else -> null
    }

/** Lower number means shown first when space is short: 5 hours, then weekly, then the rest. */
public fun usageWindowPriority(window: ProviderUsageWindow): Int =
    when (window.durationSeconds) {
        18000 -> 0
        604800 -> 1
        else -> 2
    }

private fun time(value: JsonElement?): Long? {
    val text = value.stringOrNull() ?: return null
    if (text.length > 64) return null
    return parseInstantMillis(text)
}

/**
 * Tolerant parse of a `provider.usage.list.response` payload. Unknown providers, statuses and fields are ignored; a
 * provider that is not `available`, or a window without a number, is skipped. Fails (UnreadableReply, no daemon text)
 * only when the payload is not a provider list. A fetch time in the future is clamped to [nowMs].
 */
public fun parseProviderUsageList(
    payload: JsonElement?,
    nowMs: Long,
    maxProviders: Int = 16,
    maxWindows: Int = 3,
): Outcome<List<ProviderUsageReading>> {
    val map = payload.objectOrNull()
    val providers = map?.get("providers").arrayOrNull()
    if (map == null || providers == null) return Outcome.Failed(Failure.UnreadableReply())
    val listFetched = time(map["fetchedAt"])
    val readings = ArrayList<ProviderUsageReading>()
    val seen = HashSet<String>()
    for (rawEntry in providers.take(maxProviders * 2)) {
        val raw = rawEntry.objectOrNull() ?: continue
        val status = raw["status"].stringOrNull()
        val id = raw["providerId"].stringOrNull()
        if (id == null || !PROVIDER_ID.matches(id)) continue
        val signedOut = id in SIGN_IN_PROVIDERS && status == "unavailable"
        if (status != "available" && !signedOut) continue
        if (!seen.add(id)) continue
        val name = cleanUsageText(raw["displayName"], 24) ?: id
        if (signedOut) {
            readings += ProviderUsageReading(id, name, time(raw["fetchedAt"]) ?: listFetched ?: nowMs, emptyList(), needsSignIn = true)
            continue
        }
        val rawWindows = raw["windows"].arrayOrNull() ?: continue
        val windows = ArrayList<ProviderUsageWindow>()
        for (itemRaw in rawWindows.take(16)) {
            val item = itemRaw.objectOrNull() ?: continue
            val windowId = item["id"].stringOrNull()
            val remaining = item["remainingPct"].numberOrNull()
            val used = item["usedPct"].numberOrNull()
            val left = remaining ?: used?.let { 100 - it }
            if (windowId == null || left == null || !left.isFinite()) continue
            val seconds = usageWindowSeconds(windowId)
            val label = cleanUsageText(item["label"], 24)
            if (seconds == null && label == null) continue
            windows += ProviderUsageWindow(seconds, label ?: "", (left.coerceIn(0.0, 100.0) + 0.5).toInt(), time(item["resetsAt"]))
        }
        if (windows.isEmpty()) continue
        val sorted = windows.sortedBy(::usageWindowPriority)
        var fetched = time(raw["fetchedAt"]) ?: listFetched ?: nowMs
        if (fetched > nowMs) fetched = nowMs
        readings += ProviderUsageReading(id, name, fetched, sorted.take(maxWindows))
        if (readings.size >= maxProviders) break
    }
    return Outcome.Ok(readings)
}

/** Reads provider usage once (Dart ProviderUsageGateway). */
public interface ProviderUsageReader {
    /** Unavailable reads are Failure.ComputerDidNotAnswer; a reply that is not a provider list is UnreadableReply. */
    public suspend fun read(): Outcome<List<ProviderUsageReading>>

    public suspend fun close()
}

/**
 * Reads plan usage from a Paseo daemon (`provider.usage.list.request`, permission daemon.read). When [owns] the
 * transport is closed by [close] (a short-lived session of its own); otherwise it is somebody else's (the phone's
 * built-in host session) and is left open. An error reply, a timeout or a dropped link is unavailable; no daemon
 * text is kept.
 */
public class PaseoProviderUsageReader(
    private val transport: PaseoTransport,
    private val clock: Clock,
    private val owns: Boolean = true,
    private val timeoutMs: Long = 10_000,
) : ProviderUsageReader {
    override suspend fun read(): Outcome<List<ProviderUsageReading>> {
        val payload: JsonObject = try {
            transport.request(REQUEST, JsonObject(emptyMap()), timeoutMs = timeoutMs)
        } catch (e: CancellationException) {
            throw e
        } catch (_: PaseoException) {
            return Outcome.Failed(Failure.ComputerDidNotAnswer())
        }
        return parseProviderUsageList(payload, clock.now().toEpochMilliseconds())
    }

    override suspend fun close() {
        if (owns) transport.close()
    }

    public companion object {
        public const val REQUEST: String = "provider.usage.list.request"
    }
}
