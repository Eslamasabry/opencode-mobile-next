package io.github.eslamasabry.oc.engine.attention.monitor

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** What one monitored server's row says (port of Flutter `ProfileMonitorStatus`). Never a raw error. */
public enum class MonitorStatus { Disabled, Waiting, Checking, Current, Unavailable, WifiRequired, Paused }

/** [CheckIn] is the app's own reminder that a session has been observed busy past the rule; not a server request. */
public enum class MonitoredRequestKind(public val wire: String) {
    Permission("permission"),
    Question("question"),
    Form("form"),
    CheckIn("checkIn"),
    ;

    public companion object {
        public fun fromWire(value: String?): MonitoredRequestKind? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * One server's monitoring rule (port of `ProfileNotifyRules`). Off by default; [checkInAfterMinutes] null never reminds.
 * Quiet hours are local wall-clock minutes after midnight and may wrap midnight.
 */
public data class MonitorNotifyRules(
    val enabled: Boolean = false,
    val notifications: Boolean = true,
    val wifiOnly: Boolean = false,
    val quietStart: Int? = null,
    val quietEnd: Int? = null,
    val checkInAfterMinutes: Int? = null,
) {
    val checkInAfter: Duration? get() = checkInAfterMinutes?.minutes

    /** [minuteOfDay] is the local wall-clock minute (0..1439). Equal start and end means always quiet. */
    public fun quietAt(minuteOfDay: Int): Boolean {
        val start = quietStart ?: return false
        val end = quietEnd ?: return false
        return start == end || if (start < end) minuteOfDay in start until end else minuteOfDay >= start || minuteOfDay < end
    }

    public fun toJson(): String =
        buildJsonObject {
            put("enabled", enabled)
            put("notifications", notifications)
            put("wifiOnly", wifiOnly)
            put("quietStart", quietStart)
            put("quietEnd", quietEnd)
            put("checkInAfterMinutes", checkInAfterMinutes)
        }.toString()

    public companion object {
        /** The durations the settings screen offers. */
        public val CHECK_IN_CHOICES: List<Int> = listOf(15, 30, 60, 120)
        public const val DEFAULT_CHECK_IN_MINUTES: Int = 30

        /** Junk or older payloads read as off for every field they do not carry correctly. */
        public fun fromJson(raw: String): MonitorNotifyRules? {
            val value = parseObject(raw) ?: return null
            fun minute(key: String): Int? = value.intOrNullAt(key)?.takeIf { it in 0 until 1440 }
            val checkIn = value.intOrNullAt("checkInAfterMinutes")
            return MonitorNotifyRules(
                enabled = value.boolAt("enabled") == true,
                notifications = value.boolAt("notifications") != false,
                wifiOnly = value.boolAt("wifiOnly") == true,
                quietStart = minute("quietStart"),
                quietEnd = minute("quietEnd"),
                checkInAfterMinutes = checkIn?.takeIf { it in 1..(24 * 60) },
            )
        }
    }
}

/**
 * One stretch of polls during which a session was seen busy (port of `ObservedBusyInterval`). An observation, not the
 * server's record of a run. [title] is shown in the row but never persisted.
 */
public data class ObservedBusyInterval(
    val sessionId: String,
    val firstObservedBusyAt: Instant,
    val lastObservedBusyAt: Instant,
    val title: String? = null,
    val directory: String? = null,
    val workspace: String? = null,
    /** Persisted before native dispatch, independently of notification dismissal. */
    val reminderClaimed: Boolean = false,
) {
    /** Stable within one interval; a later interval for the same session gets a new id. */
    val id: String get() = "busy-${firstObservedBusyAt.toEpochMilliseconds()}"

    val observedFor: Duration get() = lastObservedBusyAt - firstObservedBusyAt

    public fun isDue(rules: MonitorNotifyRules): Boolean {
        val after = rules.checkInAfter ?: return false
        return observedFor >= after
    }

    public fun observedAgainAt(at: Instant, title: String?, directory: String?, workspace: String?): ObservedBusyInterval =
        copy(
            lastObservedBusyAt = at,
            title = title ?: this.title,
            directory = directory ?: this.directory,
            workspace = workspace ?: this.workspace,
        )

    public fun claimReminder(): ObservedBusyInterval = copy(reminderClaimed = true)

    public fun toRequest(): MonitoredRequest =
        MonitoredRequest(id, sessionId, MonitoredRequestKind.CheckIn, title, directory, workspace)

    internal fun toJson(): JsonObject =
        buildJsonObject {
            put("sessionID", sessionId)
            put("firstObservedBusyAt", firstObservedBusyAt.toEpochMilliseconds())
            put("lastObservedBusyAt", lastObservedBusyAt.toEpochMilliseconds())
            put("directory", directory)
            put("workspace", workspace)
            put("reminderClaimed", reminderClaimed)
        }

    internal companion object {
        fun fromJson(value: JsonElement): ObservedBusyInterval? {
            val o = value as? JsonObject ?: return null
            val sessionId = o.stringAt("sessionID").orEmpty()
            val first = o.longAt("firstObservedBusyAt")
            val last = o.longAt("lastObservedBusyAt")
            if (sessionId.isEmpty() || first == null || last == null || last < first) return null
            return ObservedBusyInterval(
                sessionId = sessionId,
                firstObservedBusyAt = Instant.fromEpochMilliseconds(first),
                lastObservedBusyAt = Instant.fromEpochMilliseconds(last),
                title = o.stringAt("title"),
                directory = o.stringAt("directory"),
                workspace = o.stringAt("workspace"),
                reminderClaimed = o.boolAt("reminderClaimed") == true,
            )
        }

        fun listToJson(items: Collection<ObservedBusyInterval>): String = JsonArray(items.map { it.toJson() }).toString()
    }
}

/** One request waiting on the person on a monitored server, or a check-in reminder. */
public data class MonitoredRequest(
    val id: String,
    val sessionId: String,
    val kind: MonitoredRequestKind,
    val title: String? = null,
    val directory: String? = null,
    val workspace: String? = null,
) {
    val identity: String get() = "${kind.wire}:$sessionId:$id"
}

public enum class MonitorAttentionKind { FailedRun, TeamGate }

public enum class MonitorWorkPhase { Failed, NeedsYou }

/**
 * A supplemental failed-run or team observation (port of `AttentionObservation` as the monitor uses it). Keeps its
 * typed card target and receipt time; partial reads keep the original [observedAt] and mark it not fresh.
 */
public data class MonitorAttentionItem(
    val id: String,
    val kind: MonitorAttentionKind,
    val phase: MonitorWorkPhase,
    val observedAt: Instant,
    val isFresh: Boolean = true,
    val sessionId: String? = null,
    val requestId: String? = null,
    val taskId: String? = null,
    val runId: String? = null,
    val title: String? = null,
    val directory: String? = null,
    val workspace: String? = null,
) {
    val identity: String
        get() =
            JsonArray(listOf(kind.name, sessionId, requestId ?: id, directory, workspace).map { JsonPrimitive(it) })
                .toString()
}

/** What an attention enrichment read returned (port of `MonitorAttentionDetails`). */
public data class MonitorAttentionDetails(
    val items: List<MonitorAttentionItem>,
    val complete: Boolean,
    val checkedSessionIds: Set<String> = emptySet(),
    val teamComplete: Boolean = false,
)

/** One monitored server's latest picture. Holds no credential and no raw error (port of `ProfileAttentionSnapshot`). */
public data class ProfileAttentionSnapshot(
    val profileId: String,
    val status: MonitorStatus,
    val checkedAt: Instant? = null,
    val directory: String? = null,
    val workspace: String? = null,
    val requests: ImmutableList<MonitoredRequest> = persistentListOf(),
    val busyIntervals: ImmutableList<ObservedBusyInterval> = persistentListOf(),
    val attention: ImmutableList<MonitorAttentionItem> = persistentListOf(),
    val attentionComplete: Boolean = false,
    val runningCount: Int? = null,
    val complete: Boolean = false,
    val nextCheckAt: Instant? = null,
) {
    val isCurrent: Boolean get() = status == MonitorStatus.Current && complete
    val pendingCount: Int? get() = if (isCurrent) requests.size else null

    /** The intervals [rules] would remind about right now. */
    public fun dueCheckIns(rules: MonitorNotifyRules): List<ObservedBusyInterval> = busyIntervals.filter { it.isDue(rules) }
}

/** A durable, opaque alert route. Its scope deliberately excludes credentials. */
public data class MonitoredRoute(
    val profileId: String,
    val requestId: String,
    val sessionId: String,
    val kind: MonitoredRequestKind,
    val createdAt: Instant,
    val serverUrl: String,
    val sourceIdentity: String,
    val directory: String? = null,
    val workspace: String? = null,
) {
    internal fun toJson(): JsonObject =
        buildJsonObject {
            put("requestID", requestId)
            put("sessionID", sessionId)
            put("kind", kind.wire)
            put("createdAt", createdAt.toEpochMilliseconds())
            put("serverUrl", serverUrl)
            put("sourceIdentity", sourceIdentity)
            put("directory", directory)
            put("workspace", workspace)
        }

    internal companion object {
        fun fromJson(profileId: String, value: JsonElement?): MonitoredRoute? {
            val o = value as? JsonObject ?: return null
            return MonitoredRoute(
                profileId = profileId,
                requestId = o.stringAt("requestID") ?: return null,
                sessionId = o.stringAt("sessionID") ?: return null,
                kind = MonitoredRequestKind.fromWire(o.stringAt("kind")) ?: return null,
                createdAt = Instant.fromEpochMilliseconds(o.longAt("createdAt") ?: return null),
                serverUrl = o.stringAt("serverUrl") ?: return null,
                sourceIdentity = o.stringAt("sourceIdentity").orEmpty(),
                directory = o.stringAt("directory"),
                workspace = o.stringAt("workspace"),
            )
        }
    }
}

internal val monitorJson: Json = Json { ignoreUnknownKeys = true }

internal fun parseObject(raw: String): JsonObject? =
    try {
        monitorJson.parseToJsonElement(raw) as? JsonObject
    } catch (_: IllegalArgumentException) {
        null
    }

internal fun parseArray(raw: String): JsonArray? =
    try {
        monitorJson.parseToJsonElement(raw) as? JsonArray
    } catch (_: IllegalArgumentException) {
        null
    }

internal fun JsonObject.prim(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }

internal fun JsonObject.stringAt(key: String): String? = prim(key)?.takeIf { it.isString }?.content

internal fun JsonObject.boolAt(key: String): Boolean? = prim(key)?.takeUnless { it.isString }?.booleanOrNull

internal fun JsonObject.longAt(key: String): Long? = prim(key)?.takeUnless { it.isString }?.let { if (it.content.contains('.')) null else it.longOrNull }

internal fun JsonObject.intOrNullAt(key: String): Int? = prim(key)?.takeUnless { it.isString }?.let { if (it.content.contains('.')) null else it.intOrNull }
