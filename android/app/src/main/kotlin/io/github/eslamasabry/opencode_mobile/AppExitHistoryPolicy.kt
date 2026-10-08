package io.github.eslamasabry.opencode_mobile

/** Numeric metadata only: OS descriptions and trace streams are not inputs. */
internal data class AppExitMetadata(
    val processName: String?,
    val reason: Int,
    val importance: Int,
    val timestamp: Long,
    val subReason: Int = -1,
    val status: Int = 0,
)

/** Pure query/serialization boundary, shared by Android and the host harness. */
internal object AppExitHistoryPolicy {
    private const val DEFAULT_LIMIT = 10
    private const val MIN_LIMIT = 1
    private const val MAX_LIMIT = 50
    private const val MIN_SUPPORTED_API = 30
    private const val ALL_RETAINED_RECORDS = 0
    private const val MIN_TIMESTAMP = 1L
    private const val MAX_TIMESTAMP = 8640000000000000L

    private object Reason {
        const val UNKNOWN = 0
        const val EXIT_SELF = 1
        const val LOW_MEMORY = 3
        const val CRASH = 4
        const val CRASH_NATIVE = 5
        const val ANR = 6
        const val INITIALIZATION_FAILURE = 7
        const val USER_REQUESTED = 10
        const val USER_STOPPED = 11
        const val PACKAGE_STATE_CHANGE = 15
        const val PACKAGE_UPDATED = 16
    }

    private object SubReason {
        const val UNKNOWN = -1
        const val TOO_MANY_CACHED = 2
        const val TOO_MANY_EMPTY = 3
        const val TRIM_EMPTY = 4
        const val LARGE_CACHED = 5
        const val MEMORY_PRESSURE = 6
        const val REMOVE_LRU = 16
        const val FORCE_STOP = 21
        const val REMOVE_TASK = 22
        const val STOP_APP = 23
        const val PACKAGE_UPDATE = 25
    }

    fun limit(raw: Any?): Int? = when (raw) {
        null -> DEFAULT_LIMIT
        is Int, is Long -> {
            val value = (raw as Number).toLong()
            if (value in MIN_LIMIT.toLong()..MAX_LIMIT.toLong()) value.toInt() else null
        }
        else -> null
    }

    /** Numeric hidden accessor only; never parse toString/getDescription. */
    fun subReason(record: Any): Int = try {
        (record.javaClass.getMethod("getSubReason").invoke(record) as? Int) ?: SubReason.UNKNOWN
    } catch (_: Exception) {
        // Hidden API availability differs by release and manufacturer.
        SubReason.UNKNOWN
    }

    fun history(
        sdk: Int,
        packageName: String,
        limit: Int,
        read: (Int) -> Iterable<AppExitMetadata>,
    ): Map<String, Any?> = when {
        limit !in MIN_LIMIT..MAX_LIMIT -> result(false, error = "invalidLimit")
        sdk < MIN_SUPPORTED_API -> result(false)
        else -> try {
            // Android's package history is a retained ring buffer. Query all
            // retained records (maxNum=0) so secondary processes cannot crowd
            // the main process out before we filter and apply the user's cap.
            val entries = read(ALL_RETAINED_RECORDS).asSequence()
                .filter { it.processName == packageName }
                .filter { it.timestamp in MIN_TIMESTAMP..MAX_TIMESTAMP }
                .sortedByDescending { it.timestamp }
                .take(limit)
                .map { record ->
                    mapOf<String, Any?>(
                        "reason" to record.reason,
                        "importance" to record.importance,
                        "timestamp" to record.timestamp,
                        "subReason" to record.subReason,
                        "status" to record.status,
                        "description" to summary(record.reason, record.subReason),
                    )
                }.toList()
            result(true, entries)
        } catch (_: Exception) {
            // No raw exception, package name or native text reaches the wire/log.
            result(true, error = "unavailable")
        }
    }

    private fun result(
        supported: Boolean,
        entries: List<Map<String, Any?>> = emptyList(),
        error: String? = null,
    ): Map<String, Any?> = mapOf(
        "supported" to supported,
        "entries" to entries,
        "error" to error,
    )

    // Mirrors classifyAppExit's numeric Android reasons/subreasons. The Dart
    // bridge still derives the public category itself and ignores wire text.
    private fun summary(reason: Int, subReason: Int): String = when (reason) {
        Reason.PACKAGE_STATE_CHANGE, Reason.PACKAGE_UPDATED -> "App updated"
        Reason.USER_REQUESTED, Reason.USER_STOPPED ->
            if (subReason == SubReason.PACKAGE_UPDATE) "App updated" else "App stopped"
        Reason.LOW_MEMORY -> "Phone needed memory"
        Reason.CRASH, Reason.CRASH_NATIVE, Reason.ANR, Reason.INITIALIZATION_FAILURE ->
            "App stopped unexpectedly"
        Reason.UNKNOWN, Reason.EXIT_SELF -> "App closed"
        else -> when (subReason) {
            SubReason.FORCE_STOP, SubReason.REMOVE_TASK, SubReason.STOP_APP -> "App stopped"
            SubReason.PACKAGE_UPDATE -> "App updated"
            SubReason.TOO_MANY_CACHED, SubReason.TOO_MANY_EMPTY, SubReason.TRIM_EMPTY,
            SubReason.LARGE_CACHED, SubReason.MEMORY_PRESSURE, SubReason.REMOVE_LRU -> "Phone needed memory"
            else -> "Android ended the app"
        }
    }
}
