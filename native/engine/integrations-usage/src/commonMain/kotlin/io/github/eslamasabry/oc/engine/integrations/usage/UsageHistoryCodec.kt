package io.github.eslamasabry.oc.engine.integrations.usage

import io.github.eslamasabry.oc.core.common.dart.DartJson
import kotlin.time.Duration
import kotlin.time.Instant

private const val HISTORY_FORMAT_VERSION: Long = 1L
private const val HISTORY_MAX_JSON_BYTES: Int = 524_288
private const val HISTORY_MAX_WINDOWS: Int = 64
private const val HISTORY_MAX_SUB_KEY_LENGTH: Int = 192
private const val HISTORY_MAX_WINDOW_ID_LENGTH: Int = 64
private const val HISTORY_TUPLE_SIZE: Int = 3
private const val HISTORY_MIN_PERCENT: Double = 0.0
private const val HISTORY_MAX_PERCENT: Double = 100.0
private const val FIVE_HOUR_SECONDS: Long = 18_000L
private const val WEEKLY_SECONDS: Long = 604_800L

internal fun providerWindowId(window: ProviderUsageWindowReading): String =
    when (window.durationSeconds) {
        FIVE_HOUR_SECONDS -> "5h"
        WEEKLY_SECONDS -> "weekly"
        else -> window.label.ifEmpty { "window" }
    }

internal fun encodeUsageHistoryConnection(windows: Map<WindowKey, List<UsageSample>>): String {
    val entries =
        windows.entries.take(HISTORY_MAX_WINDOWS).map { (key, list) ->
            linkedMapOf<String, Any?>(
                "s" to key.subscriptionKey,
                "i" to key.windowId,
                "p" to
                    list.map { sample ->
                        listOf(
                            sample.fetchedAt.toEpochMilliseconds(),
                            sample.usedPercent,
                            sample.resetsAt?.toEpochMilliseconds(),
                        )
                    },
            )
        }
    return DartJson.encode(linkedMapOf("v" to HISTORY_FORMAT_VERSION, "w" to entries))
}

internal fun decodeUsageHistoryConnection(
    raw: String?,
    maxSamples: Int,
): MutableMap<WindowKey, List<UsageSample>> {
    val result = LinkedHashMap<WindowKey, List<UsageSample>>()
    val root = raw?.takeIf { it.length <= HISTORY_MAX_JSON_BYTES }?.let(DartJson::decodeOrNull) as? Map<*, *>
    val entries = if (root != null && toExactLong(root["v"]) == HISTORY_FORMAT_VERSION) root["w"] as? List<*> else null
    entries?.take(HISTORY_MAX_WINDOWS)?.forEach { item ->
        decodeHistoryEntry(item, maxSamples)?.let { (key, list) -> result[key] = list }
    }
    return result
}

private fun decodeHistoryEntry(item: Any?, maxSamples: Int): Pair<WindowKey, List<UsageSample>>? {
    val map = item as? Map<*, *>
    val subKey = (map?.get("s") as? String)?.takeIf { it.isNotEmpty() && it.length <= HISTORY_MAX_SUB_KEY_LENGTH }
    val windowId = (map?.get("i") as? String)?.takeIf { it.isNotEmpty() && it.length <= HISTORY_MAX_WINDOW_ID_LENGTH }
    val rawPoints = map?.get("p") as? List<*>
    val parsed =
        if (subKey != null && windowId != null && rawPoints != null) {
            rawPoints.mapNotNull(::decodeHistorySample).sortedBy { it.fetchedAt }.takeLast(maxSamples)
        } else {
            emptyList()
        }
    return if (parsed.isEmpty() || subKey == null || windowId == null) null else WindowKey(subKey, windowId) to parsed
}

private fun decodeHistorySample(raw: Any?): UsageSample? {
    val tuple = (raw as? List<*>)?.takeIf { it.size >= HISTORY_TUPLE_SIZE } ?: return null
    val fetchedMs = toExactLong(tuple[0])
    val used = (tuple[1] as? Number)?.toDouble()?.takeIf { it.isFinite() }
    val resetRaw = tuple[2]
    val resetValid = resetRaw == null || toExactLong(resetRaw) != null
    return if (fetchedMs != null && used != null && resetValid) {
        UsageSample(
            fetchedAt = Instant.fromEpochMilliseconds(fetchedMs),
            usedPercent = used.coerceIn(HISTORY_MIN_PERCENT, HISTORY_MAX_PERCENT),
            resetsAt = toExactLong(resetRaw)?.let { Instant.fromEpochMilliseconds(it) },
        )
    } else {
        null
    }
}

private fun toExactLong(value: Any?): Long? =
    (value as? Number)?.takeIf { it is Int || it is Long }?.toLong()

internal fun mergeUsageHistory(
    byConnection: Map<String, Map<WindowKey, List<UsageSample>>>,
    maxSamples: Int,
    dedupeInterval: Duration,
): Map<WindowKey, List<UsageSample>> {
    val merged = LinkedHashMap<WindowKey, List<UsageSample>>()
    for (slot in byConnection.values) {
        for ((key, samples) in slot) {
            val existing = merged[key]
            merged[key] =
                if (existing == null) {
                    samples.toList()
                } else {
                    combineWindowSamples(existing, samples, maxSamples, dedupeInterval)
                }
        }
    }
    return merged
}

private fun combineWindowSamples(
    first: List<UsageSample>,
    second: List<UsageSample>,
    maxSamples: Int,
    dedupeInterval: Duration,
): List<UsageSample> {
    var combined = emptyList<UsageSample>()
    for (sample in (first + second).sortedBy { it.fetchedAt }) {
        combined = UsageHistory.appendSample(combined, sample, maxSamples, dedupeInterval)
    }
    return combined
}
