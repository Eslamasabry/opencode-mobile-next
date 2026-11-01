package io.github.eslamasabry.opencode_mobile

import android.content.SharedPreferences
import org.json.JSONObject

/** Admission marker only: it never adopts or kills a process recovered from disk. */
internal class ScheduledPromptPhoneLease(private val prefs: SharedPreferences) {
    data class Clock(val boot: Int, val elapsed: Long, val wall: Long)

    fun begin(profile: String, token: String, budget: Long, clock: Clock) {
        require(ScheduledPromptPolicy.validId(profile) && token.isNotEmpty())
        // Include launch slack, timeout's second rounding and its TERM/KILL grace.
        val duration = budget.coerceIn(1L, ScheduledPromptPolicy.DART_BUDGET_MS) + LAUNCH_SLACK_MS
        val record = JSONObject().put("token", token).put("boot", clock.boot)
            .put("startedElapsed", clock.elapsed).put("deadlineElapsed", clock.elapsed + duration)
            .put("deadlineWall", clock.wall + duration)
        check(prefs.edit().putString(PREFIX + profile, record.toString()).commit()) { "unavailable" }
    }

    fun active(clock: Clock): Boolean = prefs.all.entries.any { (key, value) ->
        key.startsWith(PREFIX) && value is String && activeRecord(JSONObject(value), clock)
    }

    fun release(token: String) {
        val edit = prefs.edit()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(PREFIX) && value is String && JSONObject(value).optString("token") == token)
                edit.remove(key)
        }
        check(edit.commit()) { "unavailable" }
    }

    companion object {
        private const val PREFIX = "flutter.oc.schedulePhoneLease."
        private const val LAUNCH_SLACK_MS = 66_000L
        private const val MAX_LEASE_MS = ScheduledPromptPolicy.DART_BUDGET_MS + LAUNCH_SLACK_MS

        fun activeRecord(record: JSONObject, clock: Clock): Boolean {
            val boot = record.getInt("boot")
            val rebooted = boot >= 0 && clock.boot >= 0 && boot != clock.boot
            return !rebooted && withinWindow(record, clock)
        }

        private fun withinWindow(record: JSONObject, clock: Clock): Boolean {
            val start = record.getLong("startedElapsed")
            val end = record.getLong("deadlineElapsed")
            val duration = end - start
            require(start >= 0 && duration in 1..MAX_LEASE_MS)
            // On the same boot, moving wall time forward or back cannot expose
            // a still-running guest. This also covers an unavailable boot count.
            if (clock.elapsed >= start) return clock.elapsed < end
            // Elapsed time reset implies a reboot. If boot count is unavailable,
            // conservatively retain the marker within both finite bounds.
            return clock.elapsed < duration && clock.wall < record.getLong("deadlineWall")
        }
    }
}
