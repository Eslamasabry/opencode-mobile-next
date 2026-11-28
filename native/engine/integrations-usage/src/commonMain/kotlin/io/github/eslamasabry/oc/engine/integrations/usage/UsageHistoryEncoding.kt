package io.github.eslamasabry.oc.engine.integrations.usage

import io.github.eslamasabry.oc.core.common.dart.DartJson

/**
 * Encodes one connection's history as a record [decodeUsageHistoryConnection] always accepts: when the
 * full record is longer than the read limit, every window keeps fewer of its newest samples, and only
 * if one sample per window is still too long are the least recently updated windows left out.
 */
internal fun encodeUsageHistoryConnection(windows: Map<WindowKey, List<UsageSample>>): String {
    var maxWindows = HISTORY_MAX_WINDOWS
    var maxSamples = UsageHistory.MAX_SAMPLES_PER_WINDOW
    var record = encodeHistoryRecord(windows, maxWindows, maxSamples)
    while (record.length > HISTORY_MAX_JSON_BYTES && maxSamples > 1) {
        maxSamples = shrunkHistoryLimit(maxSamples, record.length, floor = 1)
        record = encodeHistoryRecord(windows, maxWindows, maxSamples)
    }
    while (record.length > HISTORY_MAX_JSON_BYTES && maxWindows > 0) {
        maxWindows = shrunkHistoryLimit(maxWindows, record.length, floor = 0)
        record = encodeHistoryRecord(windows, maxWindows, maxSamples)
    }
    return record
}

/** [limit] scaled down by how far [length] is over the read limit: at least one lower, never under [floor]. */
private fun shrunkHistoryLimit(limit: Int, length: Int, floor: Int): Int =
    minOf(limit - 1, (limit.toLong() * HISTORY_MAX_JSON_BYTES / length).toInt()).coerceAtLeast(floor)

private fun encodeHistoryRecord(
    windows: Map<WindowKey, List<UsageSample>>,
    maxWindows: Int,
    maxSamples: Int,
): String {
    val bounded =
        if (windows.size <= maxWindows) {
            windows
        } else {
            LinkedHashMap(windows).also { trimConnectionWindows(it, maxWindows) }
        }
    val entries =
        bounded.entries.map { (key, list) ->
            linkedMapOf<String, Any?>(
                "s" to UsageHistory.opaqueSubscriptionKey(key.subscriptionKey),
                "i" to key.windowId,
                "p" to
                    list.takeLast(maxSamples).map { sample ->
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
