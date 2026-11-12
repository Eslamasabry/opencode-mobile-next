package io.github.eslamasabry.oc.engine.team

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TeamAlertTrackerTest {
    private fun ws(tasks: List<TeamTask> = emptyList(), requests: List<TeamRequest> = emptyList()) =
        TeamWorkspace(projects = listOf(TeamProject(id = "p", tasks = tasks, requests = requests)))

    @Test
    fun `G1 the first workspace is only the baseline`() {
        val t = TeamAlertTracker("prof")
        val first = t.observe(ws(listOf(TeamTask("a", status = "failed")), listOf(TeamRequest("q"))))
        assertTrue(first.isEmpty)
        assertTrue(t.observe(ws(listOf(TeamTask("a", status = "failed")), listOf(TeamRequest("q")))).isEmpty)
    }

    @Test
    fun `G1 a new question is a decision, new findings a review, each once`() {
        val t = TeamAlertTracker("prof")
        t.observe(ws(listOf(TeamTask("a", status = "running"))))
        val diff = t.observe(ws(listOf(TeamTask("a")), listOf(TeamRequest("q", "question"), TeamRequest("f", "findings"))))
        assertEquals(listOf(TeamAlertKind.Decision, TeamAlertKind.Review), diff.alerts.map { it.kind })
        assertTrue(t.observe(ws(listOf(TeamTask("a")), listOf(TeamRequest("q", "question"), TeamRequest("f", "findings")))).isEmpty)
        val answered = t.observe(ws(listOf(TeamTask("a")), listOf(TeamRequest("f", "findings"))))
        assertEquals(listOf("team:prof:gate:q"), answered.settled)
    }

    @Test
    fun `G1 a running task that fails is news once, a finished project is a completed run`() {
        val t = TeamAlertTracker("prof")
        t.observe(ws(listOf(TeamTask("a", status = "running"), TeamTask("b", status = "running"))))
        val failed = t.observe(ws(listOf(TeamTask("a", status = "failed"), TeamTask("b", status = "running"))))
        assertEquals(listOf(TeamAlertKind.RunFailed), failed.alerts.map { it.kind })
        assertTrue(t.observe(ws(listOf(TeamTask("a", status = "failed"), TeamTask("b", status = "running")))).isEmpty)
        t.observe(ws(listOf(TeamTask("a", status = "done"), TeamTask("b", status = "running"))))
        val done = t.observe(ws(listOf(TeamTask("a", status = "done"), TeamTask("b", status = "merged"))))
        assertEquals(listOf(TeamAlertKind.Completed), done.alerts.map { it.kind })
    }
}
