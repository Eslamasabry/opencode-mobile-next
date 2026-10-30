package io.github.eslamasabry.opencode_mobile

import org.json.JSONObject

/**
 * What the Status widget says, worked out from the stored snapshot without Android: one line per
 * server, this phone's Linux, the running tasks, and the plan limit closest to running out.
 * Problems (an offline server, a plan almost used up) come first and carry the warning or bad
 * tone; the rest keep their order.
 */
internal object StatusWidgetModel {
    enum class Tone { OK, IDLE, WARN, BAD }
    enum class Kind { SERVER, LINUX, TASKS }

    /** [state]: online, offline or connecting. [count] is the running tasks. */
    data class Line(
        val kind: Kind,
        val name: String,
        val state: String,
        val tone: Tone,
        val since: Long = 0L,
        val count: Int = 0,
        val phone: Boolean = false,
    )

    data class Plan(val provider: String, val displayName: String, val seconds: Long, val label: String,
        val left: Int, val tone: Tone, val stale: Boolean)

    /** At most this much left reads as a warning; nothing left is bad. */
    const val WARN_LEFT = 20

    /** When the app last looked, if it has stopped watching (closed, or the live link ended):
     * the lines are then what it saw at that time, and the header says how long ago. */
    fun watchedAt(snapshot: JSONObject): Long? =
        if (snapshot.optBoolean("connected")) null
        else listOf(snapshot.optLong("pausedAt"), snapshot.optLong("lastSeenAt")).firstOrNull { it > 0 }

    fun lines(snapshot: JSONObject, linuxInstalled: Boolean, linuxRunning: Boolean): List<Line> {
        val out = mutableListOf<Line>()
        val servers = snapshot.optJSONArray("servers")
        for (i in 0 until (servers?.length() ?: 0)) {
            servers?.optJSONObject(i)?.let { out += server(it) }
        }
        if (linuxInstalled) {
            out += Line(Kind.LINUX, "", if (linuxRunning) "running" else "stopped",
                if (linuxRunning) Tone.OK else Tone.IDLE)
        }
        if (snapshot.has("profileID")) {
            val running = running(snapshot)
            out += Line(Kind.TASKS, "", if (running > 0) "running" else "none", Tone.IDLE, count = running)
        }
        return out.sortedBy { if (it.tone == Tone.BAD || it.tone == Tone.WARN) 0 else 1 }
    }

    private fun server(row: JSONObject): Line {
        val name = row.optString("name")
        val phone = row.optBoolean("phone")
        return when (row.optString("state")) {
            "online" -> Line(Kind.SERVER, name, "online", Tone.OK, phone = phone)
            "connecting" -> Line(Kind.SERVER, name, "connecting", Tone.IDLE, phone = phone)
            else -> Line(Kind.SERVER, name, "offline", Tone.BAD, row.optLong("since"), phone = phone)
        }
    }

    /** Running turns; one waiting on the person needs you and is not counted as running too. */
    private fun running(snapshot: JSONObject): Int {
        val sessions = snapshot.optJSONArray("sessions")
        val waiting = (0 until (sessions?.length() ?: 0)).mapNotNull { sessions?.optJSONObject(it) }.count {
            it.optBoolean("busy") && (it.optString("requestID").isNotBlank() || it.optBoolean("needsYou"))
        }
        return WidgetStatusRules.workingCount(snapshot.optInt("runningCount"), waiting)
    }

    /** How many lines fit over the plan line, in dp at font scale 1. */
    fun lineCount(effectiveHeightDp: Int, withPlan: Boolean, available: Int): Int {
        val left = effectiveHeightDp - FIXED_DP - if (withPlan) PLAN_DP else 0
        return (left / LINE_DP).coerceIn(0, available)
    }

    /** The plan limit closest to running out, from the Usage widget's rows: measured numbers
     * first, fresh before stale. Null when nothing is measured. */
    fun plan(rows: List<UsageWidgetRow>, now: Long): Plan? {
        val measured = rows.filter { !it.signInOnly }
        if (measured.isEmpty()) return null
        fun stale(r: UsageWidgetRow) = r.forceStale || r.authNeeded ||
            now - r.fetchedAt > UsageWidgetPlans.STALE_AFTER_MS || (r.resetsAt in 1..now)
        val pick = measured.sortedWith(compareBy({ stale(it) }, { it.left })).first()
        val isStale = stale(pick)
        val tone = when {
            isStale -> Tone.IDLE
            pick.left <= 0 -> Tone.BAD
            pick.left <= WARN_LEFT -> Tone.WARN
            else -> Tone.OK
        }
        return Plan(pick.provider, pick.displayName, pick.seconds, pick.label, pick.left, tone, isStale)
    }

    /** Paddings (12 + 12), the header (24) and the gap under it (6). */
    private const val FIXED_DP = 12 + 12 + 24 + 6
    /** The plan's words and its bar. */
    private const val PLAN_DP = 28
    const val LINE_DP = 48
}
