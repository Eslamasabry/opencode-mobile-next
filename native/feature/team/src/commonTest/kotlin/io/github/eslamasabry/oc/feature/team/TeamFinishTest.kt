package io.github.eslamasabry.oc.feature.team

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.team.TeamAction
import io.github.eslamasabry.oc.engine.team.TeamCommandResult
import io.github.eslamasabry.oc.engine.team.TeamEngine
import io.github.eslamasabry.oc.engine.team.TeamFinding
import io.github.eslamasabry.oc.engine.team.TeamMergeItem
import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.engine.team.TeamPlanningState
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
import io.github.eslamasabry.oc.engine.team.TeamTimelineEvent
import io.github.eslamasabry.oc.engine.team.TeamWorkspace
import io.github.eslamasabry.oc.feature.team.presentation.NoTeamFailureReports
import io.github.eslamasabry.oc.feature.team.presentation.TeamFailureReports
import io.github.eslamasabry.oc.feature.team.presentation.AskAnswer
import io.github.eslamasabry.oc.feature.team.presentation.EditorKind
import io.github.eslamasabry.oc.feature.team.presentation.EditorProblem
import io.github.eslamasabry.oc.feature.team.presentation.ProjectConfirm
import io.github.eslamasabry.oc.feature.team.presentation.TaskAskAction
import io.github.eslamasabry.oc.feature.team.presentation.TaskConfirm
import io.github.eslamasabry.oc.feature.team.presentation.TeamEditorRules
import io.github.eslamasabry.oc.feature.team.presentation.TeamEditorViewModel
import io.github.eslamasabry.oc.feature.team.presentation.TeamHomeViewModel
import io.github.eslamasabry.oc.feature.team.presentation.TeamPresent
import io.github.eslamasabry.oc.feature.team.presentation.TeamProjectViewModel
import io.github.eslamasabry.oc.feature.team.presentation.TeamServersViewModel
import io.github.eslamasabry.oc.feature.team.presentation.TeamTaskViewModel
import io.github.eslamasabry.oc.feature.team.presentation.TeamTimelinePresent
import io.github.eslamasabry.oc.feature.team.presentation.TimelineFilter
import io.github.eslamasabry.oc.ui.routes.TeamRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone

/** The rest of Flutter's team screens on the phone engine: start, edit, gate, merge, promote, steer each task. */
@OptIn(ExperimentalCoroutinesApi::class)
class TeamFinishTest {
    private val dispatcher = StandardTestDispatcher()
    private val pid = ProfileId("p1")

    @BeforeTest
    fun main() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun reset() = Dispatchers.resetMain()

    private fun TestScope.watch(flow: StateFlow<*>) = backgroundScope.launch { flow.collect {} }

    private fun editor(host: FakeHost, kind: EditorKind, projectId: String = "", nav: FakeNav = FakeNav()) =
        TeamEditorViewModel(TeamRoute.Editor(pid, kind.wire, projectId), TeamEngine(host), nav, FIXED_CLOCK)

    // ---- 1. start a run

    @Test
    fun `B-TEAM-100 New project sends the typed goal, repo and settings as one createProject, then clears its draft`() =
        runTest(dispatcher) {
            val host = FakeHost(team())
            val nav = FakeNav()
            val vm = editor(host, EditorKind.Create, nav = nav)
            watch(vm.state)
            runCurrent()
            assertEquals(EditorProblem.Required, vm.state.value.creationError)
            vm.setText("goal", "Make sync work\nwith retries")
            vm.setText("repoName", "app")
            vm.setText("repoPath", "/home/me/app")
            vm.chooseServer("s1")
            runCurrent()
            // The draft is kept while typing (Dart `_persist`).
            assertNotNull(host.drafts)
            assertNull(vm.state.value.creationError)
            vm.save()
            runCurrent()
            val sent = host.commands.single()
            assertEquals(TeamAction.CREATE_PROJECT, sent.action)
            assertEquals("Make sync work", sent.name)
            assertEquals("Make sync work\nwith retries", sent.spec!!.goal)
            assertEquals("single", sent.settings!!.mode)
            assertEquals(5.0, sent.settings!!.budget.daily)
            assertEquals(listOf("app" to "/home/me/app"), sent.repos.map { it.name to it.path })
            assertEquals("s1", sent.repos.single().serverId)
            assertTrue(sent.repos.single().id.startsWith("edit-"))
            assertNull(host.drafts)
            assertEquals(1, nav.backs)
        }

    @Test
    fun `B-TEAM-101 Quick task needs a role and a server, and Plan first off starts it at once`() =
        runTest(dispatcher) {
            val host = FakeHost(team())
            val vm = editor(host, EditorKind.Quick)
            watch(vm.state)
            runCurrent()
            vm.setText("goal", "Fix the login button")
            vm.addRepo()
            runCurrent()
            assertEquals(EditorProblem.RepoRequired, vm.state.value.problem)
            vm.setText("repoName", "web")
            vm.setText("repoPath", "/srv/web")
            vm.chooseServer("s1")
            vm.addRepo()
            runCurrent()
            assertEquals(1, vm.state.value.form.repos.size)
            assertEquals("", vm.state.value.form.text("repoName"))
            assertEquals(EditorProblem.ChooseRoleServer, vm.state.value.creationError)
            vm.chooseRole("frontend")
            vm.setPlanFirst(false)
            vm.save()
            runCurrent()
            val sent = host.commands.single()
            assertEquals(TeamAction.CREATE_QUICK_TASK, sent.action)
            assertEquals("frontend", sent.roleId)
            assertEquals(true, sent.confirmed)
        }

    @Test
    fun `B-TEAM-102 a refused start keeps the form with the code, and a moved project says it changed`() =
        runTest(dispatcher) {
            val host = FakeHost(team())
            host.answer = TeamCommandResult(accepted = false, code = "repository_empty")
            val nav = FakeNav()
            val vm = editor(host, EditorKind.Create, nav = nav)
            watch(vm.state)
            runCurrent()
            vm.setText("goal", "g")
            vm.setText("repoName", "a")
            vm.setText("repoPath", "/a")
            vm.chooseServer("s1")
            vm.save()
            runCurrent()
            assertEquals("repository_empty", vm.state.value.refusal?.code)
            assertEquals(TeamAction.CREATE_PROJECT, vm.state.value.refusal?.action)
            assertEquals("g", vm.state.value.form.value("goal"))
            assertEquals(0, nav.backs)
            assertNotNull(host.drafts)
            host.answer = TeamCommandResult(accepted = false, code = "staleRevision")
            vm.save()
            runCurrent()
            assertNull(vm.state.value.refusal)
            assertEquals(EditorProblem.ChangedElsewhere, vm.state.value.problem)
        }

    @Test
    fun `B-TEAM-103 a draft left in the editor comes back on reopen, with Start fresh`() =
        runTest(dispatcher) {
            val host = FakeHost(team())
            val first = editor(host, EditorKind.Create)
            watch(first.state)
            runCurrent()
            first.setText("goal", "Half typed")
            runCurrent()
            val again = editor(host, EditorKind.Create)
            watch(again.state)
            runCurrent()
            assertEquals("Half typed", again.state.value.form.text("goal"))
            assertTrue(again.state.value.draftRestored)
            again.startFresh()
            runCurrent()
            assertEquals("", again.state.value.form.text("goal"))
            assertNull(host.drafts)
        }

    @Test
    fun `B-TEAM-104 the editors check settings as Dart does`() {
        val (base, _) = TeamEditorRules.initial(EditorKind.Settings, team(), null) { "x" }
        val parallel = base.copy(settings = base.settings.copy(mode = "parallel")).with("lanes", "40")
        assertEquals(EditorProblem.ChooseBudget, TeamEditorRules.settingsError(base.copy(settings = base.settings.copy(mode = "single"))))
        assertEquals(EditorProblem.ChooseModeBudget, TeamEditorRules.settingsError(base))
        assertEquals(EditorProblem.PositiveLanes, TeamEditorRules.settingsError(parallel.copy(budgetChoice = "unlimited")))
        val limited = parallel.with("lanes", "3").copy(budgetChoice = "limited").with("daily", "0").with("total", "9")
        assertEquals(EditorProblem.PositiveBudget, TeamEditorRules.settingsError(limited))
        val rounds = limited.with("daily", "2").with("rounds", "7")
        assertEquals(EditorProblem.FixRoundsRange, TeamEditorRules.settingsError(rounds))
        assertNull(TeamEditorRules.settingsError(rounds.with("rounds", "2")))
        assertEquals(3, TeamEditorRules.editedSettings(rounds).maxLanes)
        val long = "x".repeat(50)
        assertEquals("x".repeat(40) + "…", TeamEditorRules.projectName(base.with("goal", long)))
    }

    // ---- the spec and the plan

    @Test
    fun `B-TEAM-105 a first spec drafts one milestone from the goal, and approving saves the draft first`() =
        runTest(dispatcher) {
            val project = TeamProject(id = "a", name = "A", status = "spec", revision = 3, simulated = false, specDraft = TeamSpec(goal = "Ship the shop\nwith care"))
            val host = FakeHost(team(project))
            host.answer = TeamCommandResult(accepted = true, revision = 4)
            val vm = editor(host, EditorKind.Spec, "a")
            watch(vm.state)
            runCurrent()
            assertTrue(vm.state.value.starterSeeded)
            val m = vm.state.value.form.milestones.single()
            assertEquals("Ship the shop", m.title)
            vm.save()
            runCurrent()
            assertEquals(listOf(TeamAction.SAVE_SPEC_DRAFT, TeamAction.APPROVE_SPEC), host.commands.map { it.action })
            assertEquals(3L, host.commands[0].expectedRevision)
            assertEquals(4L, host.commands[1].expectedRevision)
            assertEquals(listOf("Ship the shop"), host.commands[1].spec!!.milestones.single().criteria)
        }

    @Test
    fun `B-TEAM-106 the plan editor sends the edited tasks and phases with Approve plan`() =
        runTest(dispatcher) {
            val plan =
                TeamProject(
                    id = "a",
                    status = "plan",
                    simulated = false,
                    phases = listOf(TeamProjectPhase("ph", "m", "Build")),
                    tasks = listOf(TeamTask("t1", "Old", "ph", criteria = listOf("c")), TeamTask("t2", "Two", "ph")),
                )
            val host = FakeHost(team(plan))
            val vm = editor(host, EditorKind.Plan, "a")
            watch(vm.state)
            runCurrent()
            vm.setText("task-t1", "New title")
            vm.toggleDependency("t2", "t1")
            vm.setRisky("ph", true)
            vm.save()
            runCurrent()
            val sent = host.commands.single()
            assertEquals(TeamAction.APPROVE_PLAN, sent.action)
            assertEquals("New title", sent.tasks!![0].title)
            assertEquals(listOf("t1"), sent.tasks!![1].dependsOn)
            assertTrue(sent.phases!!.single().risky)
        }

    // ---- 2. roles and the team's defaults

    @Test
    fun `B-TEAM-107 a role is saved whole and the list returns, and Team defaults sends updateDefaults`() =
        runTest(dispatcher) {
            val host = FakeHost(team())
            val roles = editor(host, EditorKind.Roles)
            watch(roles.state)
            runCurrent()
            assertEquals(listOf("planner", "backend", "frontend"), roles.state.value.roles.map { it.id })
            roles.editRole("frontend")
            roles.setText("roleInstructions", "Write tidy UI")
            roles.save()
            runCurrent()
            val saved = host.commands.single()
            assertEquals(TeamAction.SAVE_ROLE, saved.action)
            assertEquals("Frontend", saved.role!!.name)
            assertEquals("Write tidy UI", saved.role!!.instructions)
            assertNull(roles.state.value.form.role)
            val defaults = editor(host, EditorKind.Defaults)
            watch(defaults.state)
            runCurrent()
            defaults.setMode("single")
            defaults.setBudget("unlimited")
            defaults.save()
            runCurrent()
            assertEquals(TeamAction.UPDATE_DEFAULTS, host.commands.last().action)
            assertTrue(host.commands.last().settings!!.budget.unlimited)
        }

    // ---- 3. the merge gate

    @Test
    fun `B-TEAM-108 merging into dev and promoting to main each ask first and pin what the person saw`() =
        runTest(dispatcher) {
            val repo = TeamRepo("r", "app", devCommit = "def5678abc", mainCommit = "abc1234def")
            val ready =
                TeamProject(
                    id = "a",
                    name = "A",
                    status = "running",
                    revision = 9,
                    simulated = false,
                    planApproved = true,
                    repos = listOf(repo),
                    tasks = listOf(TeamTask("t", "T", repoId = "r", status = "merged")),
                    mergeQueue = listOf(TeamMergeItem("m", "t", "r", "merged", checksPassed = true), TeamMergeItem("m2", "t", "r", "queued")),
                )
            val host = FakeHost(team(ready))
            val vm = TeamProjectViewModel(TeamRoute.Project(pid, "a"), TeamEngine(host), FakeNav(), FIXED_CLOCK)
            watch(vm.state)
            runCurrent()
            val view = vm.state.value.project!!
            assertEquals(1, view.merges.single().waiting)
            assertTrue(view.merges.single().canMerge)
            // A queued item keeps promotion off until everything merged.
            assertTrue(view.promotions.isEmpty())
            vm.ask(ProjectConfirm.Merge("r"))
            runCurrent()
            assertTrue(host.commands.isEmpty())
            vm.confirm()
            runCurrent()
            assertEquals(TeamAction.PROCESS_MERGE_QUEUE, host.commands.single().action)
            assertEquals(true, host.commands.single().confirmed)
            host.workspace = team(ready.copy(mergeQueue = ready.mergeQueue.take(1)))
            vm.retry()
            runCurrent()
            assertEquals("r", vm.state.value.project!!.promotions.single().repoId)
            vm.ask(ProjectConfirm.Promote("r"))
            vm.confirm()
            runCurrent()
            val promote = host.commands.last()
            assertEquals(TeamAction.PROMOTE, promote.action)
            assertEquals("def5678abc", promote.expectedDevCommit)
            assertEquals("abc1234def", promote.expectedMainCommit)
        }

    @Test
    fun `B-TEAM-109 an engine that cannot run work hides the gated actions and says why once`() {
        val p =
            TeamProject(
                id = "a",
                status = "interrupted",
                simulated = false,
                repos = listOf(TeamRepo("r", "app")),
                mergeQueue = listOf(TeamMergeItem("m", "t", "r", "queued")),
                requests = listOf(TeamRequest("i", "interrupted", "Resume?", taskId = "t")),
                tasks = listOf(TeamTask("t", "T", repoId = "r", status = "interrupted")),
            )
        val blocked = TeamState(TeamPhase.Ready, workspace = team(p), health = BLOCKED_HEALTH)
        val home = TeamPresent.home(blocked, NOW)
        assertTrue(home.blocked)
        assertFalse(home.canStart)
        assertTrue(home.rows.none { it.key.startsWith("interrupted:") })
        val view = TeamPresent.project(blocked, "a", NOW).project!!
        assertTrue(view.blocked)
        assertFalse(view.merges.single().canMerge)
        assertFalse(view.needsYou.single().actionable)
        val ready = TeamPresent.home(blocked.copy(health = READY_HEALTH), NOW)
        assertTrue(ready.canStart)
        assertEquals("landlock", ready.protection)
    }

    @Test
    fun `B-TEAM-110 Delete project asks first and the page closes once it is gone`() =
        runTest(dispatcher) {
            val host = FakeHost(team(TeamProject(id = "a", name = "A", simulated = false)))
            val nav = FakeNav()
            val vm = TeamProjectViewModel(TeamRoute.Project(pid, "a"), TeamEngine(host), nav, FIXED_CLOCK)
            watch(vm.state)
            runCurrent()
            assertTrue(vm.state.value.project!!.canDelete)
            vm.ask(ProjectConfirm.Delete)
            runCurrent()
            assertEquals(ProjectConfirm.Delete, vm.state.value.confirming)
            vm.confirm()
            runCurrent()
            assertEquals(TeamAction.DELETE_PROJECT, host.commands.single().action)
            assertTrue(host.commands.single().confirmed)
            assertEquals(1, nav.backs)
        }

    @Test
    fun `B-TEAM-111 a failed plan says why and offers its way on, and the digest lists what is still true`() {
        val p =
            TeamProject(
                id = "a",
                status = "planFailed",
                simulated = false,
                planningState = TeamPlanningState(reason = "modelNotConfigured"),
                timeline =
                    listOf(
                        TeamTimelineEvent("1", "planner", "Planning stopped (modelNotConfigured)", at = "2026-11-02T07:00:00Z"),
                        TeamTimelineEvent("2", "planner", "Planning again", at = "2026-11-02T07:10:00Z"),
                        TeamTimelineEvent("3", "decision", "Use Stripe", actor = "person", at = "2026-11-02T07:20:00Z"),
                    ),
            )
        val view = TeamPresent.project(TeamState(TeamPhase.Ready, workspace = team(p), health = READY_HEALTH), "a", NOW).project!!
        val failed = view.planFailed!!
        assertTrue(failed.canRetry)
        assertTrue(failed.needsModel)
        assertFalse(failed.resumable)
        // The planner's two rows fold into the newest.
        assertEquals(listOf("Use Stripe", "Planning again"), view.digest!!.items.map { it.text })
        assertTrue(view.decisions.single().byPerson)
    }

    // ---- the task's own controls

    @Test
    fun `B-TEAM-112 findings are ticked, fixed, ignored with a reason, and checked again`() =
        runTest(dispatcher) {
            val t =
                TeamTask(
                    "t",
                    "T",
                    status = "review",
                    findings = listOf(TeamFinding("f1", "critical", text = "Leak"), TeamFinding("f2", "minor", text = "Name"), TeamFinding("f3", status = "fixed")),
                )
            val p = TeamProject(id = "a", simulated = false, tasks = listOf(t), requests = listOf(TeamRequest("q", "findings", "Review", taskId = "t")))
            val host = FakeHost(team(p))
            val vm = TeamTaskViewModel(TeamRoute.Task(pid, "a", "t"), TeamEngine(host), FakeNav(), FIXED_CLOCK, NoTeamFailureReports())
            watch(vm.state)
            runCurrent()
            assertEquals(listOf(TaskAskAction.ReviewFindings), vm.state.value.task!!.asks.single().actions)
            vm.selectOpenFindings()
            runCurrent()
            assertEquals(setOf("f1", "f2"), vm.state.value.selected)
            vm.fixSelected()
            runCurrent()
            assertEquals(TeamAction.FIX_FINDINGS, host.commands.last().action)
            assertEquals(listOf("f1", "f2"), host.commands.last().findingIds)
            vm.toggleFinding("f2", false)
            vm.ignoreSelected("  not a bug ")
            runCurrent()
            assertEquals(TeamAction.IGNORE_FINDING, host.commands.last().action)
            assertEquals(listOf("f1"), host.commands.last().findingIds)
            assertEquals("not a bug", host.commands.last().text)
            vm.recheck()
            runCurrent()
            assertEquals(TeamAction.RECHECK_TASK, host.commands.last().action)
            assertEquals(1, vm.state.value.task!!.critical)
        }

    @Test
    fun `B-TEAM-113 a running task pauses and stops (asked first), a failed one starts again, a conflict resolves`() =
        runTest(dispatcher) {
            val running = TeamTask("t", "Pay form", status = "running", repoId = "r")
            val p =
                TeamProject(
                    id = "a",
                    simulated = false,
                    repos = listOf(TeamRepo("r", "app")),
                    tasks = listOf(running),
                    mergeQueue = listOf(TeamMergeItem("mq", "t", "r", "conflict", reason = "Waiting for your conflict resolution")),
                    requests = listOf(TeamRequest("c", "conflict", "Conflict", taskId = "t")),
                )
            val host = FakeHost(team(p))
            val vm = TeamTaskViewModel(TeamRoute.Task(pid, "a", "t"), TeamEngine(host), FakeNav(), FIXED_CLOCK, NoTeamFailureReports())
            watch(vm.state)
            runCurrent()
            val controls = vm.state.value.task!!.controls
            assertTrue(controls.pause && controls.stop)
            assertFalse(controls.restart)
            assertEquals(3, vm.state.value.task!!.asks.single().actions.size)
            vm.pause()
            vm.ask(TaskConfirm.Stop)
            runCurrent()
            assertEquals(listOf(TeamAction.PAUSE_TASK), host.commands.map { it.action })
            vm.confirm()
            runCurrent()
            assertEquals(TeamAction.STOP_TASK, host.commands.last().action)
            assertTrue(host.commands.last().confirmed)
            vm.resolve("mq", "agent")
            runCurrent()
            assertEquals("agent", host.commands.last().text)
            assertEquals("mq", host.commands.last().targetId)
            host.workspace = team(p.copy(tasks = listOf(running.copy(status = "failed")), requests = emptyList()))
            vm.retry()
            runCurrent()
            assertTrue(vm.state.value.task!!.controls.restart)
            vm.restart()
            runCurrent()
            assertEquals(TeamAction.RESTART_TASK, host.commands.last().action)
        }

    @Test
    fun `B-TEAM-114 a phase whose tasks all merged is accepted from its task`() =
        runTest(dispatcher) {
            val p =
                TeamProject(
                    id = "a",
                    simulated = false,
                    phases = listOf(TeamProjectPhase("ph", "m", "Build")),
                    tasks = listOf(TeamTask("t", "T", "ph", status = "merged")),
                )
            val host = FakeHost(team(p))
            val vm = TeamTaskViewModel(TeamRoute.Task(pid, "a", "t"), TeamEngine(host), FakeNav(), FIXED_CLOCK, NoTeamFailureReports())
            watch(vm.state)
            runCurrent()
            assertTrue(vm.state.value.task!!.phase!!.canAccept)
            vm.acceptPhase("ph")
            runCurrent()
            assertEquals(TeamAction.ACCEPT_PHASE, host.commands.single().action)
            assertEquals("ph", host.commands.single().targetId)
        }

    // ---- servers, timeline, answering from the list

    @Test
    fun `B-TEAM-115 a move asks for a note and, when the branch cannot follow, restarts it there`() =
        runTest(dispatcher) {
            val p = TeamProject(id = "a", simulated = false, tasks = listOf(TeamTask("t", "T", serverId = "s1", status = "running")))
            val host = FakeHost(team(p))
            host.answer = TeamCommandResult(accepted = false, code = "branchUnavailable")
            val vm = TeamServersViewModel(TeamRoute.Servers(pid, "a"), TeamEngine(host), FakeNav(), FIXED_CLOCK)
            watch(vm.state)
            runCurrent()
            assertTrue(vm.state.value.servers.first().tasks.single().movable)
            vm.chooseMove("t", "s2")
            vm.moveWithNote("over to the desk")
            runCurrent()
            assertEquals("over to the desk", host.commands.single().text)
            assertTrue(vm.state.value.moving!!.restartElsewhere)
            host.answer = TeamCommandResult(accepted = true)
            vm.restartElsewhere(true)
            runCurrent()
            assertEquals(TeamAction.MOVE_TASK, host.commands.last().action)
            assertTrue(host.commands.last().confirmed)
            assertEquals("s2", host.commands.last().serverId)
            vm.saveLanes("s1", 3)
            runCurrent()
            assertEquals(TeamAction.UPDATE_SERVER, host.commands.last().action)
            assertEquals(3, host.commands.last().server!!.laneCap)
        }

    @Test
    fun `B-TEAM-116 the timeline lists by day, newest first, folding repeats and filtering problems`() {
        val p =
            TeamProject(
                id = "a",
                timeline =
                    listOf(
                        TeamTimelineEvent("1", "job", "Started", actor = "worker", at = "2026-11-01T09:00:00Z", taskId = "t"),
                        TeamTimelineEvent("2", "failed", "Check failed", actor = "worker", at = "2026-11-02T07:00:00Z", taskId = "t"),
                        TeamTimelineEvent("3", "failed", "Check failed", actor = "worker", at = "2026-11-02T07:01:00Z", taskId = "t"),
                    ),
            )
        val state = TeamState(TeamPhase.Ready, workspace = team(p))
        val all = TeamTimelinePresent.present(state, "a", TimelineFilter.All, NOW, TimeZone.UTC)
        assertEquals(listOf("2026-11-02", "2026-11-01"), all.days.map { it.day })
        assertEquals(2, all.days[0].rows.single().count)
        val problems = TeamTimelinePresent.present(state, "a", TimelineFilter.Problems, NOW, TimeZone.UTC)
        assertEquals(1, problems.days.size)
    }

    @Test
    fun `B-TEAM-117 the team page answers each kind of request the way Flutter does`() =
        runTest(dispatcher) {
            val p =
                shop().copy(
                    requests =
                        listOf(
                            TeamRequest("q1", "question", "Which provider?", taskId = "t2"),
                            TeamRequest("b", "budget", "Raise the limit?"),
                            TeamRequest("m", "milestone", "Accept Pay?", phaseId = "m2"),
                        )
                )
            val host = FakeHost(team(p))
            val nav = FakeNav()
            val vm = TeamHomeViewModel(TeamRoute.Home(pid), TeamEngine(host), nav, FIXED_CLOCK)
            watch(vm.state)
            runCurrent()
            val answers = vm.state.value.rows.filterIsInstance<io.github.eslamasabry.oc.feature.team.presentation.HomeRow.Request>().map { it.answer }
            assertEquals(AskAnswer.Text, answers[0])
            assertEquals(AskAnswer.Editor(EditorKind.Settings), answers[1])
            val accept = answers[2] as AskAnswer.AcceptMilestone
            vm.answer("shop", "q1", " Stripe ")
            vm.askAccept("shop", accept)
            runCurrent()
            assertEquals(accept, vm.state.value.accepting)
            vm.confirmAccept()
            runCurrent()
            assertEquals(listOf(TeamAction.ANSWER_REQUEST, TeamAction.ACCEPT_MILESTONE), host.commands.map { it.action })
            assertEquals("Stripe", host.commands[0].text)
            vm.openEditor(EditorKind.Quick)
            assertEquals(TeamRoute.Editor(pid, "quick", ""), nav.opened.last())
        }

    private fun team(vararg projects: TeamProject): TeamWorkspace =
        TeamWorkspace(
            revision = 1,
            simulated = false,
            projects = projects.toList(),
            servers = listOf(TeamServer("s1", "This phone", phone = true), TeamServer("s2", "Desk")),
            roles = listOf(TeamRole("backend", "Backend"), TeamRole("frontend", "Frontend"), TeamRole("planner", "Planner", readOnly = true)),
            defaultSettings = TeamProjectSettings(),
        )

    @Test
    fun `G5 only a failed task offers and sends Report this failure, with its own id and title`() =
        runTest(dispatcher) {
            val sent = mutableListOf<Pair<String, String>>()
            val reports = TeamFailureReports { _, id, title, _, _ -> sent += id to title }
            val p =
                TeamProject(
                    id = "a",
                    simulated = false,
                    tasks = listOf(TeamTask("bad", "Pay form", status = "failed"), TeamTask("ok", "Cart", status = "running")),
                )
            val host = FakeHost(team(p))
            val bad = TeamTaskViewModel(TeamRoute.Task(pid, "a", "bad"), TeamEngine(host), FakeNav(), FIXED_CLOCK, reports)
            val fine = TeamTaskViewModel(TeamRoute.Task(pid, "a", "ok"), TeamEngine(host), FakeNav(), FIXED_CLOCK, reports)
            watch(bad.state)
            watch(fine.state)
            runCurrent()
            assertTrue(bad.state.value.task!!.controls.report)
            assertFalse(fine.state.value.task!!.controls.report)
            fine.reportFailure()
            assertTrue(sent.isEmpty())
            bad.reportFailure()
            assertEquals(listOf("bad" to "Pay form"), sent)
        }
}
