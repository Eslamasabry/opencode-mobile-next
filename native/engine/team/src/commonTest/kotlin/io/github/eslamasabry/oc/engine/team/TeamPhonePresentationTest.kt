package io.github.eslamasabry.oc.engine.team

import io.github.eslamasabry.oc.core.common.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class TeamPhonePresentationTest {
    private val all = TeamAction.all() + TeamCommand.EXECUTION_ACTIONS

    private fun project(
        status: String,
        planning: TeamPlanningState? = null,
        tasks: List<TeamTask> = emptyList(),
        approved: Boolean = false,
    ) = TeamProject(id = "p", name = "p", status = status, planningState = planning, tasks = tasks, planApproved = approved, simulated = false)

    @Test
    fun `B-186 a plan cut off by an OpenCode restart reads as paused so Resume shows`() {
        val planning = TeamPlanningState("j", "interrupted", "serverRestarted", "2026-11-09T10:00:00Z")
        val out = TeamPhonePresentation.present(project("interrupted", planning), all)
        assertEquals("paused", out.status)
        val line = out.timeline.single { it.kind == "planningCheckpoint" }
        assertEquals("Planning was interrupted. Resume to check its existing session (serverRestarted).", line.text)
    }

    @Test
    fun `B-186 without a Resume command a restart cut-off reads as failed`() {
        val planning = TeamPlanningState("j", "interrupted", "serverRestarted", "")
        val out = TeamPhonePresentation.present(project("interrupted", planning), all - TeamAction.RESUME_PROJECT - TeamAction.RETRY_PLAN)
        assertEquals("failed", out.status)
    }

    @Test
    fun `B-186 an approved plan whose only step was cut off by a restart is paused and resumable`() {
        val task = TeamTask(id = "t", status = "interrupted", reason = "serverRestarted", changedAt = "2026-11-09T10:00:00Z")
        val out = TeamPhonePresentation.present(project("running", tasks = listOf(task), approved = true), all)
        assertEquals("paused", out.status)
        assertEquals("paused", out.tasks.single().status)
        val line = out.timeline.single { it.kind == "taskCheckpoint" }
        assertEquals("Work was interrupted. Resume to check its existing session (serverRestarted).", line.text)
    }

    @Test
    fun `B-186 an interrupted step the person cannot resume needs review`() {
        val task = TeamTask(id = "t", status = "interrupted", reason = "recoveryNeedsReview")
        val out = TeamPhonePresentation.present(project("running", tasks = listOf(task), approved = true), all)
        assertEquals("failed", out.status)
        assertEquals("review", out.tasks.single().status)
    }

    @Test
    fun `B-186 an unreadable checker answer becomes an ask to resume`() {
        val task = TeamTask(id = "t", status = "interrupted", reason = "structuredOutputInvalid")
        val out = TeamPhonePresentation.present(project("running", tasks = listOf(task), approved = true), all)
        assertEquals("interrupted", out.requests.single().kind)
        assertEquals("t", out.requests.single().taskId)
    }

    @Test
    fun `B-186 the engine's raw task and plan words become the ones the pages use`() {
        val tasks = listOf(TeamTask(id = "a", status = "checked"), TeamTask(id = "b", status = "needsFix"), TeamTask(id = "c", status = "merging"))
        val out = TeamPhonePresentation.present(project("needsPlanApproval", tasks = tasks), all)
        assertEquals("plan", out.status)
        assertEquals(listOf("verified", "review", "running"), out.tasks.map { it.status })
        assertEquals("checkerFindings", out.tasks[1].reason)
    }

    @Test
    fun `B-186 a failed plan the person can retry is planFailed`() {
        val planning = TeamPlanningState("j", "failed", "invalidPlan", "")
        assertEquals("planFailed", TeamPhonePresentation.present(project("failed", planning), all).status)
    }

    @Test
    fun `B-186 the demo workspace is left as it is`() {
        val demo = TeamWorkspace(simulated = true, projects = listOf(project("needsPlanApproval").copy(simulated = true)))
        assertEquals("needsPlanApproval", TeamPhonePresentation.present(demo, all).projects.single().status)
    }

    @Test
    fun `B-186 a refresh hands the pages the presented workspace`() = runTest {
        val planning = TeamPlanningState("j", "interrupted", "serverRestarted", "")
        val raw = TeamWorkspace(revision = 3, simulated = false, projects = listOf(project("interrupted", planning)))
        val host = FakeTeamHost(reads = ArrayDeque(listOf(Outcome.Ok(raw))))
        val engine = TeamEngine(host)
        engine.refresh("p")
        assertEquals("paused", engine.state("p").value.workspace?.projects?.single()?.status)
        assertTrue(TeamPhonePresentation.resumableInterruption("serverRestarted"))
    }
}
