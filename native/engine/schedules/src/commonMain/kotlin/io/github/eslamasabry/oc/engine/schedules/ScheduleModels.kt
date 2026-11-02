package io.github.eslamasabry.oc.engine.schedules

/*
 * Port of lib/scheduling/models.dart. The device scheduler stores only user-authored prompts and target identifiers;
 * credentials are resolved from the profile immediately before each dispatch. Times are epoch milliseconds; the JSON
 * form is byte-compatible with the Flutter store (ScheduleCodec), so a Flutter install's schedules load unchanged.
 */

public enum class RecurrenceKind(public val wire: String) {
    Once("once"),
    Daily("daily"),
    Weekdays("weekdays"),
    Interval("interval"),
    ;

    public companion object {
        public fun of(wire: String): RecurrenceKind? = entries.firstOrNull { it.wire == wire }
    }
}

public enum class MissedRunPolicy(public val wire: String) {
    CatchUp("catchUp"),
    Skip("skip"),
    ;

    public companion object {
        public fun of(wire: String): MissedRunPolicy? = entries.firstOrNull { it.wire == wire }
    }
}

public enum class RunStatus(public val wire: String) {
    Running("running"),
    Ran("ran"),
    Failed("failed"),
    Skipped("skipped"),
    ;

    public companion object {
        public fun of(wire: String): RunStatus? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * [atMs] is the absolute once time or the interval anchor; daily and weekday schedules use the device's local
 * wall-clock [hour]:[minute]. [hours] is the interval period.
 */
public data class Recurrence(
    val kind: RecurrenceKind,
    val atMs: Long? = null,
    val hour: Int = 8,
    val minute: Int = 0,
    val hours: Int = 1,
) {
    public val valid: Boolean
        get() =
            hour in 0..23 && minute in 0..59 && hours in 1..168 &&
                !((kind == RecurrenceKind.Once || kind == RecurrenceKind.Interval) && atMs == null)

    public companion object {
        public fun once(atMs: Long): Recurrence = Recurrence(RecurrenceKind.Once, atMs = atMs)

        public fun daily(hour: Int, minute: Int): Recurrence = Recurrence(RecurrenceKind.Daily, hour = hour, minute = minute)

        public fun weekdays(hour: Int, minute: Int): Recurrence =
            Recurrence(RecurrenceKind.Weekdays, hour = hour, minute = minute)

        public fun interval(hours: Int, anchorMs: Long): Recurrence =
            Recurrence(RecurrenceKind.Interval, atMs = anchorMs, hours = hours)
    }
}

public data class ScheduledPrompt(
    val id: String,
    val profileId: String,
    val profileFingerprint: String,
    val prompt: String,
    val projectPath: String,
    val recurrence: Recurrence,
    val createdAtMs: Long,
    val workspace: String? = null,
    /** Null creates a new conversation for each occurrence. */
    val sessionId: String? = null,
    val agent: String? = null,
    val modelProviderId: String? = null,
    val modelId: String? = null,
    val missedPolicy: MissedRunPolicy = MissedRunPolicy.CatchUp,
    val enabled: Boolean = true,
    val nextRunAtMs: Long? = null,
    val revision: Int = 1,
) {
    public val valid: Boolean
        get() =
            ScheduleIds.valid(id) && ScheduleIds.valid(profileId) &&
                profileFingerprint.isNotEmpty() && profileFingerprint.length <= 256 &&
                prompt.isNotBlank() && prompt.length <= MAX_PROMPT &&
                projectPath.isNotBlank() && projectPath.length <= 4096 &&
                revision >= 1 && (sessionId == null || sessionId.isNotEmpty()) &&
                ((modelProviderId == null) == (modelId == null)) && recurrence.valid

    public companion object {
        public const val MAX_PROMPT: Int = 32_000
    }
}

/**
 * One occurrence's record. [title] and [summary] are the conversation title and the reply's first line, kept only
 * for the "finished" notification: never persisted. [errorCode] is a fixed code, never server text.
 */
public data class ScheduledRun(
    val id: String,
    val scheduleId: String,
    val scheduledAtMs: Long,
    val startedAtMs: Long,
    val status: RunStatus,
    val late: Boolean = false,
    val finishedAtMs: Long? = null,
    val sessionId: String? = null,
    val errorCode: String? = null,
    val title: String? = null,
    val summary: String? = null,
) {
    public val valid: Boolean
        get() =
            id.isNotEmpty() && id.length <= 512 && ScheduleIds.valid(scheduleId) &&
                (errorCode == null || ERROR_CODE.matches(errorCode))

    /** The stored form: the notification-only fields dropped. */
    public fun stored(): ScheduledRun = copy(title = null, summary = null)

    public companion object {
        public val ERROR_CODE: Regex = Regex("^[a-z][a-z0-9_]{0,79}$")
    }
}

public data class ScheduleDigest(
    val enabled: Boolean = false,
    val hour: Int = 8,
    val minute: Int = 0,
    val lastDeliveredAtMs: Long? = null,
) {
    public val valid: Boolean get() = hour in 0..23 && minute in 0..59
}

public object ScheduleIds {
    private val identifier = Regex("^[a-zA-Z0-9_-]{1,128}$")

    public fun valid(value: String): Boolean = identifier.matches(value)

    /** The durable occurrence identity: `id:revision:dueMs`, never reused after an uncertain dispatch. */
    public fun occurrence(scheduleId: String, revision: Int, scheduledAtMs: Long): String =
        "$scheduleId:$revision:$scheduledAtMs"

    public fun occurrence(schedule: ScheduledPrompt, scheduledAtMs: Long): String =
        occurrence(schedule.id, schedule.revision, scheduledAtMs)

    /** The digest pseudo-schedule id the OS scheduler carries (Flutter `__digest`). */
    public const val DIGEST: String = "__digest"
}
