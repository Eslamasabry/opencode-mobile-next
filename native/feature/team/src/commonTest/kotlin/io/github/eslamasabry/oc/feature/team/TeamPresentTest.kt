package io.github.eslamasabry.oc.feature.team

import io.github.eslamasabry.oc.engine.team.TeamBudget
import io.github.eslamasabry.oc.engine.team.TeamCriterionResult
import io.github.eslamasabry.oc.engine.team.TeamFinding
import io.github.eslamasabry.oc.engine.team.TeamMessage
import io.github.eslamasabry.oc.engine.team.TeamMilestone
import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.engine.team.TeamProject
import io.github.eslamasabry.oc.engine.team.TeamProjectPhase
import io.github.eslamasabry.oc.engine.team.TeamProjectSettings
import io.github.eslamasabry.oc.engine.team.TeamRepo
import io.github.eslamasabry.oc.engine.team.TeamRequest
import io.github.eslamasabry.oc.engine.team.TeamRole
import io.github.eslamasabry.oc.engine.team.TeamServer
import io.github.eslamasabry.oc.engine.team.TeamSpec
import io.github.eslamasabry.oc.engine.team.TeamState
import io.github.eslamasabry.oc.engine.team.TeamTask
import io.github.eslamasabry.oc.engine.team.TeamWorkspace
import io.github.eslamasabry.oc.feature.team.presentation.BoardColumn
import io.github.eslamasabry.oc.feature.team.presentation.Headline
import io.github.eslamasabry.oc.feature.team.presentation.HomeRow
import io.github.eslamasabry.oc.feature.team.presentation.MilestoneStatus
import io.github.eslamasabry.oc.feature.team.presentation.AskAnswer
import io.github.eslamasabry.oc.feature.team.presentation.EditorKind
import io.github.eslamasabry.oc.feature.team.presentation.StatusWord
import io.github.eslamasabry.oc.feature.team.presentation.TaskSpeaker
import io.github.eslamasabry.oc.feature.team.presentation.TaskMark
import io.github.eslamasabry.oc.feature.team.presentation.TeamMark
import io.github.eslamasabry.oc.feature.team.presentation.TeamPresent
import io.github.eslamasabry.oc.feature.team.presentation.TeamRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal const val NOW = 1_793_606_400_000L // 2026-11-02T08:00:00Z

internal fun shop(): TeamProject =
    TeamProject(
        id = "shop",
        name = "Shop",
        status = "running",
        simulated = false,
        settings = TeamProjectSettings(mode = "parallel", maxLanes = 2, budget = TeamBudget(daily = 5.0)),
        repos = listOf(TeamRepo("r1", "web"), TeamRepo("r2", "api")),
        specDraft =
            TeamSpec(
                goal = "Sell shoes",
                milestones = listOf(TeamMilestone("m1", "Cart", accepted = true), TeamMilestone("m2", "Pay")),
            ),
        specVersions = listOf(TeamSpec(approvedAt = "2026-11-02T07:00:00.000Z")),
        phases = listOf(TeamProjectPhase("ph1", "m1"), TeamProjectPhase("ph2", "m2")),
        tasks =
            listOf(
                TeamTask("t1", "Cart API", "ph1", "backend", "r2", "s1", "merged"),
                TeamTask("t2", "Pay form", "ph2", "frontend", "r1", "s1", "running", changedAt = "2026-11-02T07:58:00Z"),
                TeamTask("t3", "Receipts", "ph2", "backend", "r2", "s2", "queued", dependsOn = listOf("t1")),
                TeamTask("t4", "Refunds", "ph2", "backend", "r2", "s2", "queued", dependsOn = listOf("t2")),
                TeamTask("t5", "Review pay", "ph2", "frontend", "r1", "s1", "review"),
            ),
        requests = listOf(TeamRequest("q1", "question", "Which card provider?", taskId = "t2")),
        spentToday = 1.5,
        usageReported = true,
        updatedAt = "2026-11-02T07:55:00Z",
    )

internal fun team(vararg projects: TeamProject) =
    TeamWorkspace(
        revision = 1,
        simulated = false,
        projects = projects.toList(),
        servers = listOf(TeamServer("s1", "This phone", phone = true), TeamServer("s2", "Desk", online = false)),
        roles = listOf(TeamRole("backend", "Backend"), TeamRole("frontend", "Frontend")),
    )

class TeamPresentTest {
    @Test
    fun `B-TEAM-020 the team page lists what needs you first, each project's requests above it`() {
        val done = TeamProject(id = "old", name = "Old", status = "done", simulated = false)
        val paused = TeamProject(id = "p", name = "Paused", status = "paused", simulated = false)
        val ui = TeamPresent.home(TeamState(TeamPhase.Ready, workspace = team(done, paused, shop())), NOW)
        assertEquals(
            listOf("request:shop:q1", "project:shop", "project:p", "project:old"),
            ui.rows.map { it.key },
        )
        val row = ui.rows[1] as HomeRow.Project
        assertEquals(1.5, row.spentToday)
        assertEquals(5.0, row.dailyLimit)
        assertEquals(TeamMark.Running, row.mark)
        assertEquals(Headline.Milestone(2, 2, 1), row.headline)
    }

    @Test
    fun `B-TEAM-021 last-known projects read as stale and an unlimited budget has no daily limit`() {
        val p = shop().copy(settings = TeamProjectSettings(budget = TeamBudget(unlimited = true, daily = 9.0)), usageReported = false)
        val ui = TeamPresent.home(TeamState(TeamPhase.Ready, workspace = team(p), stale = true), NOW)
        val row = ui.rows.last() as HomeRow.Project
        assertEquals(TeamMark.Stale, row.mark)
        assertNull(row.spentToday)
        assertNull(row.dailyLimit)
    }

    @Test
    fun `B-TEAM-022 headlines follow Dart's planning, progress and milestone words`() {
        val planning = TeamProject(status = "spec", updatedAt = "2026-11-02T07:57:00Z")
        assertEquals(Headline.Planning(3), TeamRules.headline(planning, NOW))
        assertEquals(Headline.Status(StatusWord.PlanWaiting), TeamRules.headline(TeamProject(status = "plan"), NOW))
        val loose = TeamProject(status = "running", tasks = listOf(TeamTask(status = "done"), TeamTask(status = "merged")))
        assertEquals(Headline.AllTasksDone(2), TeamRules.headline(loose, NOW))
        val busy = loose.copy(tasks = loose.tasks + TeamTask(status = "running"))
        assertEquals(Headline.Progress(2, 3, 1), TeamRules.headline(busy, NOW))
        val accepted = shop().copy(specDraft = TeamSpec(milestones = listOf(TeamMilestone("m", accepted = true))))
        assertEquals(Headline.MilestonesDone(1), TeamRules.headline(accepted, NOW))
    }

    @Test
    fun `B-TEAM-023 the project shows its ask, goal, milestones and lanes as Flutter's overview`() {
        val view = TeamPresent.project(TeamState(TeamPhase.Ready, workspace = team(shop())), "shop", NOW).project!!
        val ask = view.needsYou.single()
        assertEquals("Frontend", ask.roleName)
        assertEquals("This phone", ask.serverName)
        assertEquals(1, ask.blocks) // t4 waits on t2
        assertEquals(2, view.goal.milestones)
        assertEquals(2, view.goal.repos)
        assertEquals(NOW - 3_600_000, view.goal.approvedAtMs)
        assertEquals(MilestoneStatus.Done, view.milestones[0].status)
        assertEquals(MilestoneStatus.Tasks(0, 4), view.milestones[1].status)
        assertEquals(TeamMark.Running, view.milestones[1].mark)
        // One running; t3 is ready (t1 merged) and takes the one free lane; t4 waits on t2.
        assertEquals(listOf("t2", "t3"), view.lanes.map { it.taskId })
        assertEquals(true, view.lanes[1].serverOffline)
        assertEquals(1, view.busy)
        assertEquals(2, view.laneTotal)
    }

    @Test
    fun `B-TEAM-024 a project that is gone reads as nothing to show`() {
        assertNull(TeamPresent.project(TeamState(TeamPhase.Ready, workspace = team()), "shop", NOW).project)
    }

    @Test
    fun `B-TEAM-025 the board sorts tasks into Flutter's five lanes and opens where the work is`() {
        val ui = TeamPresent.board(TeamState(TeamPhase.Ready, workspace = team(shop())), "shop", TeamPresent.BoardFilters())
        val byId = ui.cards.associateBy { it.id }
        assertEquals(BoardColumn.Done, byId.getValue("t1").column)
        assertEquals(BoardColumn.Working, byId.getValue("t2").column)
        assertEquals(BoardColumn.Backlog, byId.getValue("t3").column) // has dependencies
        assertEquals(BoardColumn.Review, byId.getValue("t5").column)
        assertEquals(TaskMark.NeedsYou, byId.getValue("t5").mark)
        assertEquals(BoardColumn.Backlog, ui.column)
        assertEquals(listOf("r1", "r2"), ui.repos.map { it.id })
    }

    @Test
    fun `B-TEAM-026 board filters narrow by milestone, repo and server and keep a chosen lane`() {
        val state = TeamState(TeamPhase.Ready, workspace = team(shop()))
        val cart = TeamPresent.board(state, "shop", TeamPresent.BoardFilters(milestone = "m1"))
        assertEquals(listOf("t1"), cart.cards.map { it.id })
        assertEquals(BoardColumn.Done, cart.column)
        val web = TeamPresent.board(state, "shop", TeamPresent.BoardFilters(repo = "r1", server = "s1", column = BoardColumn.Done))
        assertEquals(listOf("t2", "t5"), web.cards.map { it.id })
        assertEquals(BoardColumn.Done, web.column)
    }

    @Test
    fun `B-TEAM-027 money reads as whole dollars or cents`() {
        assertEquals("$5", TeamRules.money(5.0))
        assertEquals("$1.50", TeamRules.money(1.5))
        assertEquals("$0.05", TeamRules.money(0.05))
    }

    @Test
    fun `B-TEAM-028 the menu, milestone accept and ask answers follow Dart's overview`() {
        val merged =
            shop().copy(
                status = "paused",
                tasks = shop().tasks.map { if (it.phaseId == "ph2") it.copy(status = "merged") else it },
                phases = listOf(TeamProjectPhase("ph1", "m1", accepted = true), TeamProjectPhase("ph2", "m2", accepted = true)),
                requests =
                    listOf(
                        TeamRequest("a", "milestone", "Accept Pay?", phaseId = "m2"),
                        TeamRequest("b", "spec", "Approve the spec"),
                        TeamRequest("c", "interrupted", "Resume Pay", taskId = "t2"),
                    ),
            )
        val view = TeamPresent.project(TeamState(TeamPhase.Ready, workspace = team(merged)), "shop", NOW).project!!
        assertEquals(true, view.milestones[1].canAccept)
        assertEquals(false, view.milestones[0].canAccept) // already accepted
        assertEquals(true, view.menu.resume)
        assertEquals(false, view.menu.pause)
        assertEquals(
            listOf(AskAnswer.AcceptMilestone("shop", "m2", view.needsYou[0].milestoneText), AskAnswer.Editor(EditorKind.Spec), AskAnswer.Resume("t2")),
            view.needsYou.map { it.answer },
        )
        assertEquals("Pay", view.needsYou[0].milestoneText)
        val home = TeamPresent.home(TeamState(TeamPhase.Ready, workspace = team(merged.copy(status = "interrupted"))), NOW)
        assertEquals("interrupted:shop", home.rows.first().key)
    }

    @Test
    fun `B-TEAM-029 a task's conversation shows its lines, its asks, results and findings`() {
        val p =
            shop().let { p ->
                p.copy(
                    tasks =
                        p.tasks.map {
                            if (it.id != "t2") it
                            else
                                it.copy(
                                    messages =
                                        listOf(
                                            TeamMessage("1", "person", "Use Stripe"),
                                            TeamMessage("2", "team", "Build the form"),
                                            TeamMessage("3", "frontend", "Form ready"),
                                        ),
                                    findings = listOf(TeamFinding("f", "minor", text = "Label missing", status = "fixed")),
                                    criterionResults = listOf(TeamCriterionResult("pays", "met")),
                                )
                        }
                )
            }
        val ui = TeamPresent.task(TeamState(TeamPhase.Ready, workspace = team(p)), "shop", "t2", NOW)
        val t = ui.task!!
        assertEquals(listOf(TaskSpeaker.Person, TaskSpeaker.Instructions, TaskSpeaker.Worker), t.lines.map { it.speaker })
        assertEquals(listOf("q1"), t.asks.map { it.id })
        assertEquals("Frontend", t.roleName)
        assertEquals(false, t.findings.single().open)
        assertEquals("met", t.criteria.single().status)
        assertNull(TeamPresent.task(TeamState(TeamPhase.Ready, workspace = team(p)), "shop", "gone", NOW).task)
    }

    @Test
    fun `B-TEAM-136 an interrupted project's menu offers Pause beside Resume, as Flutter's overview does`() {
        val interrupted = shop().copy(status = "interrupted")
        val menu = TeamPresent.project(TeamState(TeamPhase.Ready, workspace = team(interrupted)), "shop", NOW).project!!.menu
        assertEquals(true, menu.pause)
        assertEquals(false, menu.resume)
    }
}
