package io.github.eslamasabry.oc.engine.schedules

/** Where tapping a scheduled run's notification leads (port of lib/scheduling/open_route.dart). */
public sealed interface ScheduledOpen {
    public val profileId: String

    /** The conversation the run used ("started", "skipped · busy", or a failed run that reached it). */
    public data class Conversation(override val profileId: String, val scheduleId: String, val sessionId: String) : ScheduledOpen

    /** The schedule's list and history: the morning summary, or a run that never reached a conversation. */
    public data class History(override val profileId: String, val scheduleId: String?) : ScheduledOpen

    public companion object {
        /**
         * Resolves a notification's routing data against what this phone stores. Null when the schedule is gone or its
         * saved server changed: private routing never falls back to another target.
         */
        public fun resolve(
            profileId: String,
            scheduleId: String,
            sessionId: String?,
            currentFingerprint: String?,
            schedules: List<ScheduledPrompt>,
            history: List<ScheduledRun>,
        ): ScheduledOpen? {
            val digest = scheduleId == ScheduleIds.DIGEST || scheduleId.isEmpty()
            val schedule = if (digest) schedules.firstOrNull() else schedules.firstOrNull { it.id == scheduleId }
            if (schedule == null || currentFingerprint == null || schedule.profileId != profileId ||
                schedule.profileFingerprint != currentFingerprint
            ) {
                return null
            }
            if (digest) return History(profileId, null)
            val known = !sessionId.isNullOrEmpty() && history.any { it.sessionId == sessionId && it.scheduleId == schedule.id }
            return if (known) Conversation(profileId, schedule.id, sessionId) else History(profileId, schedule.id)
        }
    }
}
