package io.github.eslamasabry.oc.engine.lifecycle

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

// Port of lib/platform/app_exit.dart: why the previous process ended, in the words the app speaks about it.

public enum class AppExitKind {
    Normal,
    Update,
    ForceStop,
    LowMemory,
    Crash,
    Killed,
    ;

    /** Whether the person should hear about it when something was running. */
    public val notable: Boolean get() = this != Normal && this != Update
}

/** The `ApplicationExitInfo` constants the classification reads. */
public object AndroidExitReason {
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
}

/** One `ApplicationExitInfo` of the app's main process. [description] is native text and is never shown or reported. */
public data class AppExitRecord(
    val reason: Int,
    val timestampMillis: Long,
    val subReason: Int = -1,
    val status: Int = 0,
    val importance: Int = 0,
    val description: String = "",
) {
    val kind: AppExitKind get() = classifyAppExit(this)

    override fun toString(): String = "AppExitRecord(kind=$kind, reason=$reason, subReason=$subReason)"
}

public fun classifyAppExit(record: AppExitRecord): AppExitKind {
    val sub = record.subReason
    return when (record.reason) {
        AndroidExitReason.PACKAGE_UPDATED, AndroidExitReason.PACKAGE_STATE_CHANGE -> AppExitKind.Update
        AndroidExitReason.USER_REQUESTED, AndroidExitReason.USER_STOPPED ->
            if (sub == AndroidExitReason.SUB_PACKAGE_UPDATE) AppExitKind.Update else AppExitKind.ForceStop
        AndroidExitReason.LOW_MEMORY -> AppExitKind.LowMemory
        AndroidExitReason.CRASH, AndroidExitReason.CRASH_NATIVE, AndroidExitReason.ANR,
        AndroidExitReason.INITIALIZATION_FAILURE,
        -> AppExitKind.Crash
        AndroidExitReason.EXIT_SELF, AndroidExitReason.UNKNOWN -> AppExitKind.Normal
        else ->
            when (sub) {
                AndroidExitReason.SUB_FORCE_STOP, AndroidExitReason.SUB_STOP_APP, AndroidExitReason.SUB_REMOVE_TASK ->
                    AppExitKind.ForceStop
                AndroidExitReason.SUB_PACKAGE_UPDATE -> AppExitKind.Update
                AndroidExitReason.SUB_TOO_MANY_CACHED, AndroidExitReason.SUB_TOO_MANY_EMPTY,
                AndroidExitReason.SUB_TRIM_EMPTY, AndroidExitReason.SUB_LARGE_CACHED,
                AndroidExitReason.SUB_MEMORY_PRESSURE, AndroidExitReason.SUB_REMOVE_LRU,
                -> AppExitKind.LowMemory
                else -> AppExitKind.Killed
            }
    }
}

/** What the app learns at start about the process before this one. */
public data class AppLaunchReport(
    val exit: AppExitRecord? = null,
    val previousServices: ImmutableList<String> = persistentListOf(),
)
