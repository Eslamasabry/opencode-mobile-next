package io.github.eslamasabry.oc.engine.schedules

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.ServerGateway

/**
 * A private transport for one run. [close] releases exactly what this run opened (a gateway the app already holds
 * for the profile is borrowed, not closed).
 */
public class ScheduleConnection(
    public val gateway: ServerGateway,
    /** The run started this phone runtime, so its lease must last until fresh output completes. */
    public val startedPhoneServer: Boolean = false,
    private val release: suspend () -> Unit = {},
) {
    private val closing = kotlinx.coroutines.sync.Mutex()
    private var closed = false

    public suspend fun close() {
        closing.lock()
        try {
            if (closed) return
            closed = true
            release()
        } finally {
            closing.unlock()
        }
    }
}

/** The saved servers, as schedules need them. Implemented by the app wiring over the profile store. */
public interface ScheduleProfiles {
    /** Every saved profile id (for re-arming after boot, update or first open). */
    public suspend fun ids(): List<String>

    /**
     * The identity of the profile's saved server, without its display name: a schedule made for one server never
     * runs against an edited address, account or trust choice. Null when the profile is gone.
     */
    public suspend fun fingerprint(profileId: String): String?

    /**
     * The Flutter-era fingerprint of the profile as it is saved now (Flutter `scheduledProfileFingerprint`: SHA-256 of
     * the Dart `toJson()` without `name`). Only a schedule whose stored legacy fingerprint equals this is migrated; null
     * (unknown) migrates nothing, so a schedule made for another server stays refused (B-AGENT-218).
     */
    public suspend fun legacyFingerprint(profileId: String): String? = null

    /** Opens a gateway for the profile pinned to [projectPath]. Credentials are resolved inside, never stored. */
    public suspend fun open(profileId: String, projectPath: String, workspace: String?): Outcome<ScheduleConnection>
}

/** What the OS scheduler can do here (Flutter `SchedulePlatformStatus`). */
public data class SchedulePlatform(
    val supported: Boolean = false,
    val notificationsAllowed: Boolean = false,
)

/** The OS scheduler (WorkManager on Android). Replace is total: entries absent from [entries] are cancelled. */
public interface ScheduleAlarms {
    public fun status(): SchedulePlatform

    public fun replace(profileId: String, entries: List<ArmedEntry>)

    /** Starts one run of a saved schedule now, through the same background worker a timed run uses. */
    public fun runNow(profileId: String, scheduleId: String, revision: Int)

    /** Cancels everything armed for a removed profile. */
    public fun cancelProfile(profileId: String)
}

/** The run's visible notices (started, then finished in the same place). */
public interface ScheduleNotices {
    public fun started(entry: ArmedEntry)

    public fun finished(entry: ArmedEntry, report: RunReport)

    /** Removes the started notice when nothing is worth showing (a quiet skip). */
    public fun clear(entry: ArmedEntry)
}

/** What a finished run reports to the notification (never persisted beyond the run record). */
public data class RunReport(
    val status: String,
    val errorCode: String? = null,
    val sessionId: String? = null,
    val late: Boolean = false,
    val title: String? = null,
    val summary: String? = null,
    val ran: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
) {
    public val digest: Boolean get() = status == "digest"

    /** Flutter's quiet skips: nothing to tell the person. */
    public val quiet: Boolean
        get() =
            (status == "skipped" && errorCode in setOf("stale_target", "not_due", "disabled")) ||
                (digest && ran + failed + skipped == 0)
}
