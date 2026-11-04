package io.github.eslamasabry.oc.android.background

/**
 * Why the background connection stopped (Flutter `BackgroundPauseReason`). [wire] is the fixed category the native
 * receipt stores and the Flutter enum name, kept verbatim so a migrated receipt reads the same.
 */
public enum class PauseReason(public val wire: String) {
    None("none"),
    TimeLimit("timeLimit"),
    BatteryRestricted("batteryRestricted"),
    UserStopped("userStopped"),
    Interrupted("interrupted"),

    /** Android ended the app to update it (exit reason PACKAGE_UPDATED). */
    AppUpdated("appUpdated"),

    /** The phone restarted while the connection was running. */
    DeviceRestarted("deviceRestarted"),
    ;

    public companion object {
        /** Unknown text never becomes a reason (and is never repeated). */
        public fun fromWire(value: Any?): PauseReason? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * The native service receipt, independent of any selected server (Flutter `BackgroundPauseState`). [atMs] is UTC epoch
 * milliseconds. `supported = false` means the phone cannot tell (or the receipt is unreadable): never a pause, never a
 * permission to start.
 */
public data class PauseState(
    val supported: Boolean,
    val active: Boolean = false,
    val paused: Boolean = false,
    val reason: PauseReason = PauseReason.None,
    val atMs: Long? = null,
    val canResume: Boolean = false,
) {
    public companion object {
        public val Unsupported: PauseState = PauseState(supported = false)

        /** Supported, not running and not paused (an intentional opt-out). */
        public val Off: PauseState = PauseState(supported = true)

        public val Running: PauseState = PauseState(supported = true, active = true)

        /** Year 10000 in epoch ms: a later stamp is not a time. */
        private const val MAX_AT_MS = 253_402_300_800_000L

        /**
         * Strict structural parsing of a receipt map (`supported`, `active`, `paused`, `reason`, `at`, `canResume`):
         * a wrong type, an unknown category with a pause, or a pause without a valid time is [Unsupported]. Arbitrary
         * values are never turned into text.
         */
        public fun fromWire(value: Map<String, Any?>?): PauseState {
            if (value == null || value["supported"] != true) return Unsupported
            val active = value["active"] as? Boolean ?: return Unsupported
            val pausedFlag = value["paused"] as? Boolean ?: return Unsupported
            val canResume = value["canResume"] as? Boolean ?: return Unsupported
            val reason = PauseReason.fromWire(value["reason"]) ?: PauseReason.None
            val stamp = value["at"]
            val at = (stamp as? Long ?: (stamp as? Int)?.toLong())?.takeIf { it in 1 until MAX_AT_MS }
            val paused = !active && pausedFlag
            if (paused && (reason == PauseReason.None || at == null)) return Unsupported
            return PauseState(
                supported = true,
                active = active,
                paused = paused,
                reason = if (paused) reason else PauseReason.None,
                atMs = if (paused) at else null,
                canResume = paused && canResume,
            )
        }
    }
}

/** Why a visible Resume did not run the connection again (Flutter `DiagnosticsError.unavailable/busy/resumeFailed`). */
public enum class ResumeError { Unavailable, Busy, ResumeFailed }

public data class ResumeResult(val state: PauseState, val error: ResumeError? = null)
