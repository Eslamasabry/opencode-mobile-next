package io.github.eslamasabry.opencode_mobile

import io.github.eslamasabry.opencode_mobile.StatusWidgetModel.Tone
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusWidgetModelTest {
    private val now = 1_800_000_000_000L

    private fun snapshot(connected: Boolean = true) = JSONObject().put("profileID", "p1")
        .put("connected", connected).put("runningCount", 1).put("servers", JSONArray(listOf(
            JSONObject().put("name", "This phone").put("state", "online"),
            JSONObject().put("name", "Laptop").put("state", "offline").put("since", now - 60_000),
        )))

    @Test fun anOfflineServerComesFirstInTheBadTone() {
        val lines = StatusWidgetModel.lines(snapshot(), linuxInstalled = true, linuxRunning = false)
        assertEquals("Laptop", lines.first().name)
        assertEquals(Tone.BAD, lines.first().tone)
        assertEquals(listOf(StatusWidgetModel.Kind.SERVER, StatusWidgetModel.Kind.SERVER,
            StatusWidgetModel.Kind.LINUX, StatusWidgetModel.Kind.TASKS), lines.map { it.kind })
        assertEquals("stopped", lines[2].state)
    }

    @Test fun whenTheAppStoppedWatchingTheHeaderSaysSince() {
        val paused = snapshot(connected = false).put("pausedAt", now - 300_000)
        assertEquals(now - 300_000, StatusWidgetModel.watchedAt(paused))
        assertNull(StatusWidgetModel.watchedAt(snapshot()))
        // A plain disconnect with no pause is just offline: nothing to date.
        assertNull(StatusWidgetModel.watchedAt(snapshot(connected = false)))
    }

    @Test fun aTurnWaitingOnThePersonIsNotCountedAsRunning() {
        val waiting = snapshot().put("runningCount", 2).put("sessions", JSONArray(listOf(
            JSONObject().put("id", "s1").put("busy", true).put("requestID", "r1"),
            JSONObject().put("id", "s2").put("busy", true),
        )))
        val tasks = StatusWidgetModel.lines(waiting, false, false).single { it.kind == StatusWidgetModel.Kind.TASKS }
        assertEquals(1, tasks.count)
    }

    @Test fun linuxShowsOnlyWhenInstalled() {
        val lines = StatusWidgetModel.lines(snapshot(), linuxInstalled = false, linuxRunning = false)
        assertEquals(0, lines.count { it.kind == StatusWidgetModel.Kind.LINUX })
    }

    @Test fun thePlanIsTheFreshLimitClosestToRunningOut() {
        fun row(left: Int, fetched: Long = now - 60_000) =
            UsageWidgetRow("codex", "", "", 18_000L, "", left, now + 3_600_000, fetched, false)
        val plan = StatusWidgetModel.plan(listOf(row(62), row(8), row(3, fetched = now - 86_400_000)), now)!!
        assertEquals(8, plan.left)
        assertEquals(Tone.WARN, plan.tone)
        assertEquals(Tone.OK, StatusWidgetModel.plan(listOf(row(62)), now)!!.tone)
        assertEquals(Tone.BAD, StatusWidgetModel.plan(listOf(row(0)), now)!!.tone)
        assertNull(StatusWidgetModel.plan(listOf(row(0).copy(signInOnly = true)), now))
    }

    @Test fun linesFitTheHeightOverThePlan() {
        // Every line is a 48dp touch target, so a 2x2 shows two and a taller cell four.
        assertEquals(2, StatusWidgetModel.lineCount(200, true, 6))
        assertEquals(4, StatusWidgetModel.lineCount(300, true, 6))
        assertEquals(1, StatusWidgetModel.lineCount(300, true, 1))
        assertEquals(0, StatusWidgetModel.lineCount(60, true, 4))
    }
}
