package io.github.eslamasabry.oc.engine.diagnostics.exit

import kotlin.time.Instant

/** The `ApplicationExitInfo` constants the classification reads (numbers only, no Android types). */
public object ExitReason {
    public const val UNKNOWN: Int = 0
    public const val EXIT_SELF: Int = 1
    public const val SIGNALED: Int = 2
    public const val LOW_MEMORY: Int = 3
    public const val CRASH: Int = 4
    public const val CRASH_NATIVE: Int = 5
    public const val ANR: Int = 6
    public const val INITIALIZATION_FAILURE: Int = 7
    public const val PERMISSION_CHANGE: Int = 8
    public const val EXCESSIVE_RESOURCE_USAGE: Int = 9
    public const val USER_REQUESTED: Int = 10
    public const val USER_STOPPED: Int = 11
    public const val DEPENDENCY_DIED: Int = 12
    public const val OTHER: Int = 13
    public const val FREEZER: Int = 14
    public const val PACKAGE_STATE_CHANGE: Int = 15
    public const val PACKAGE_UPDATED: Int = 16

    /** The sub-reason in `ApplicationExitInfo.toString()` ("subreason=21 (FORCE STOP)"), or [SUB_UNKNOWN]. */
    public fun subReasonIn(text: String?): Int =
        text?.let { Regex("subreason=(-?\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: SUB_UNKNOWN

    // Subreasons (hidden API).
    public const val SUB_UNKNOWN: Int = -1
    public const val SUB_TOO_MANY_CACHED: Int = 2
    public const val SUB_TOO_MANY_EMPTY: Int = 3
    public const val SUB_TRIM_EMPTY: Int = 4
    public const val SUB_LARGE_CACHED: Int = 5
    public const val SUB_MEMORY_PRESSURE: Int = 6
    public const val SUB_REMOVE_LRU: Int = 16
    public const val SUB_FORCE_STOP: Int = 21
    public const val SUB_REMOVE_TASK: Int = 22
    public const val SUB_STOP_APP: Int = 23
    public const val SUB_PACKAGE_UPDATE: Int = 25

    /** `ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND`. */
    public const val IMPORTANCE_FOREGROUND: Int = 100

    /** No importance recorded (a record built without one); read as "in front", so it never announces a stop. */
    public const val IMPORTANCE_UNKNOWN: Int = 0
}

/** Why the previous process ended, in the app's terms (port of Dart `AppExitKind` + plan 11 §7.3 `MemoryLimit`). */
public enum class ExitKind {
    /** It ended itself or Android gave no reason. */
    Normal,

    /** Updated or reinstalled. */
    Update,

    /** Force-stopped while in use: the person's own stop with the app in front, or a swipe from Recents. */
    ForceStop,

    /**
     * Stopped "at the user's request" while the app was in the background (`REASON_USER_REQUESTED` or
     * `REASON_USER_STOPPED`, importance below foreground): the person in Settings, or a maker's battery manager, which
     * Android records the same way (issue #138, RedMagic and ZTE). Never a swipe from Recents or an update's stop.
     */
    BackgroundStop,

    /** Android reclaimed its memory. */
    LowMemory,

    /** Android 17's Memory Limiter (`MemoryLimiter:AnonSwap`) capped the app while it was in the background. */
    MemoryLimit,

    /** A crash or an "isn't responding". */
    Crash,

    /** Any other kill by the system. */
    Killed,
    ;

    /** Whether the person hears about it when something was running. */
    public val notable: Boolean get() = this != Normal && this != Update

    /**
     * The phone, not the person, ended the app: low memory, the memory limiter, the freezer or any other system kill.
     * A stop "at the user's request" ([ForceStop], [BackgroundStop]), an update and a normal exit are never one.
     */
    public val closedBySystem: Boolean get() = this == LowMemory || this == MemoryLimit || this == Killed

    /**
     * Whether a notice tells the person about it: a system kill or a crash in its own words, and a [BackgroundStop] in
     * a softer line (it may have been a battery manager). A stop while the app was in use ([ForceStop]) is the person's
     * own and not news to them (upgrade test 2026-11-09); it stays in diagnostics only, like an update.
     */
    public val announced: Boolean get() = closedBySystem || this == Crash || this == BackgroundStop

    /** A crash is ours to fix, not a phone setting to change (B-STATE-026). */
    public val offersKeepAlive: Boolean get() = this != Crash
}

/** Pure classification of one exit (port of Dart `classifyAppExit` and `AppExitHistoryPolicy.summary`). */
public object ExitClassifier {
    /** The marker Android 17 writes into the description of a Memory Limiter kill. */
    internal const val MEMORY_LIMITER_MARK: String = "MemoryLimiter:AnonSwap"

    private val MEMORY_SUBREASONS =
        setOf(
            ExitReason.SUB_TOO_MANY_CACHED, ExitReason.SUB_TOO_MANY_EMPTY, ExitReason.SUB_TRIM_EMPTY,
            ExitReason.SUB_LARGE_CACHED, ExitReason.SUB_MEMORY_PRESSURE, ExitReason.SUB_REMOVE_LRU,
        )

    public fun classify(
        reason: Int,
        subReason: Int,
        memoryLimited: Boolean = false,
        importance: Int = ExitReason.IMPORTANCE_UNKNOWN,
    ): ExitKind =
        when (reason) {
            ExitReason.PACKAGE_UPDATED, ExitReason.PACKAGE_STATE_CHANGE -> ExitKind.Update
            ExitReason.USER_REQUESTED, ExitReason.USER_STOPPED ->
                when {
                    // An update also ends the old process "because the user asked".
                    subReason == ExitReason.SUB_PACKAGE_UPDATE -> ExitKind.Update
                    subReason == ExitReason.SUB_REMOVE_TASK -> ExitKind.ForceStop
                    // In the background (a foreground service counts): the person, or a maker's battery manager.
                    importance > ExitReason.IMPORTANCE_FOREGROUND -> ExitKind.BackgroundStop
                    // In front, or no importance recorded: the person's own stop.
                    else -> ExitKind.ForceStop
                }
            ExitReason.LOW_MEMORY -> ExitKind.LowMemory
            ExitReason.CRASH, ExitReason.CRASH_NATIVE, ExitReason.ANR, ExitReason.INITIALIZATION_FAILURE -> ExitKind.Crash
            ExitReason.EXIT_SELF, ExitReason.UNKNOWN -> ExitKind.Normal
            else ->
                when {
                    reason == ExitReason.OTHER && memoryLimited -> ExitKind.MemoryLimit
                    subReason == ExitReason.SUB_FORCE_STOP ||
                        subReason == ExitReason.SUB_STOP_APP ||
                        subReason == ExitReason.SUB_REMOVE_TASK -> ExitKind.ForceStop
                    subReason == ExitReason.SUB_PACKAGE_UPDATE -> ExitKind.Update
                    subReason in MEMORY_SUBREASONS -> ExitKind.LowMemory
                    else -> ExitKind.Killed
                }
        }
}

/**
 * One `ApplicationExitInfo`, numbers only. The OS [description] is read ONCE here, for the Memory Limiter marker, and is
 * never stored or forwarded (plan 11 §7.3, map T21): no property holds it and [toString] prints numbers only.
 * [processName] is used to keep the main process's records and is never printed either.
 */
public class ExitRecord(
    public val reason: Int,
    public val timestampMs: Long,
    public val subReason: Int = ExitReason.SUB_UNKNOWN,
    public val status: Int = 0,
    public val importance: Int = 0,
    public val processName: String? = null,
    description: String? = null,
) {
    private val memoryLimited: Boolean = description?.contains(ExitClassifier.MEMORY_LIMITER_MARK) == true

    public val kind: ExitKind = ExitClassifier.classify(reason, subReason, memoryLimited, importance)

    /** A record without a time is no record (B-ANDR-005). */
    public val hasTime: Boolean get() = timestampMs > 0

    public val at: Instant get() = Instant.fromEpochMilliseconds(timestampMs)

    /** For the trace and diagnostics: numbers and the kind only, no package or OS text. */
    public val traceAttrs: Map<String, Any?>
        get() =
            mapOf(
                "kind" to kind.name,
                "reason" to reason,
                "subReason" to subReason,
                "importance" to importance,
                "at" to at.toString(),
            )

    override fun equals(other: Any?): Boolean =
        other is ExitRecord && other.reason == reason && other.timestampMs == timestampMs &&
            other.subReason == subReason && other.status == status && other.importance == importance &&
            other.processName == processName && other.kind == kind

    override fun hashCode(): Int = (((reason * 31 + timestampMs.hashCode()) * 31 + subReason) * 31 + importance) * 31 + kind.hashCode()

    override fun toString(): String =
        "ExitRecord(kind=$kind, reason=$reason, subReason=$subReason, status=$status, importance=$importance, at=$timestampMs)"
}
