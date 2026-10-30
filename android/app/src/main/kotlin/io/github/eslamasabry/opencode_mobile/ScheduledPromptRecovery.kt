package io.github.eslamasabry.opencode_mobile

import java.time.Instant
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

/** Atomic envelope repair after a worker dies, without creating another prompt. */
internal object ScheduledPromptRecovery {
    private const val LATE_AFTER_MS = 900_000L
    private const val MAX_RUNS = 500
    private const val MAX_INTERVAL_HOURS = 168L
    private const val HOUR_MS = 3_600_000L

    /** The schedule occurrence being repaired. */
    data class Target(val id: String, val revision: Long, val due: Long)

    fun repair(state: JSONObject, target: Target, now: Long, error: String, zone: ZoneId): Boolean {
        require(state.getInt("schemaVersion") == 1)
        val schedule = find(state.getJSONArray("schedules"), target.id) ?: return false
        recordFailure(state.getJSONArray("runs"), target, now, error)
        if (schedule.getLong("revision") == target.revision && !schedule.isNull("nextRunAt") &&
            Instant.parse(schedule.getString("nextRunAt")).toEpochMilli() == target.due) {
            val next = next(schedule.getJSONObject("recurrence"), now, zone)
            schedule.put("nextRunAt", next?.let { Instant.ofEpochMilli(it).toString() } ?: JSONObject.NULL)
        }
        return true
    }

    private fun find(rows: JSONArray, id: String): JSONObject? =
        (0 until rows.length()).map { rows.getJSONObject(it) }.firstOrNull { it.getString("id") == id }

    private fun recordFailure(runs: JSONArray, target: Target, now: Long, error: String) {
        val occurrence = ScheduledPromptPolicy.occurrence(target.id, target.revision, target.due)
        val existing = find(runs, occurrence)
        if (existing != null && existing.optString("status") != "running") return
        val run = existing ?: newRun(occurrence, target, now)
        run.put("status", "failed").put("errorCode", error).put("finishedAt", Instant.ofEpochMilli(now).toString())
        if (existing == null) runs.put(run)
        while (runs.length() > MAX_RUNS) runs.remove(0)
    }

    private fun newRun(occurrence: String, target: Target, now: Long): JSONObject = JSONObject()
        .put("id", occurrence).put("scheduleId", target.id)
        .put("scheduledAt", Instant.ofEpochMilli(target.due).toString())
        .put("startedAt", Instant.ofEpochMilli(now).toString())
        .put("late", now - target.due > LATE_AFTER_MS)

    fun next(recurrence: JSONObject, now: Long, zone: ZoneId): Long? = when (recurrence.getString("kind")) {
        "once" -> null
        "daily" -> ScheduledPromptPolicy.nextDaily(
            recurrence.getInt("hour"), recurrence.getInt("minute"), now, zone,
        )
        "weekdays" -> ScheduledPromptPolicy.nextWeekday(
            recurrence.getInt("hour"), recurrence.getInt("minute"), now, zone,
        )
        "interval" -> nextInterval(recurrence, now)
        else -> error("invalid_schedule")
    }

    private fun nextInterval(recurrence: JSONObject, now: Long): Long {
        val hours = recurrence.getLong("hours")
        require(hours in 1..MAX_INTERVAL_HOURS)
        val period = hours * HOUR_MS
        val anchor = Instant.parse(recurrence.getString("at")).toEpochMilli()
        if (anchor > now) return anchor
        return Math.addExact(anchor, Math.multiplyExact((now - anchor) / period + 1, period))
    }
}
