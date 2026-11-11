package io.github.eslamasabry.oc.feature.team

import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.team.TeamConfig
import io.github.eslamasabry.oc.engine.team.TeamProvider
import io.github.eslamasabry.oc.engine.team.fleet.AgentState
import io.github.eslamasabry.oc.engine.team.fleet.FleetAgent
import io.github.eslamasabry.oc.engine.team.fleet.FleetAgentAction
import io.github.eslamasabry.oc.engine.team.fleet.FleetCapabilities
import io.github.eslamasabry.oc.engine.team.fleet.FleetGate
import io.github.eslamasabry.oc.engine.team.fleet.FleetPhase
import io.github.eslamasabry.oc.engine.team.fleet.FleetRun
import io.github.eslamasabry.oc.engine.team.fleet.FleetSnapshot
import io.github.eslamasabry.oc.engine.team.fleet.FleetState
import io.github.eslamasabry.oc.engine.team.fleet.FleetStream
import io.github.eslamasabry.oc.engine.team.fleet.FleetWork
import io.github.eslamasabry.oc.engine.team.fleet.GateKind
import io.github.eslamasabry.oc.engine.team.fleet.ReceiptStatus
import io.github.eslamasabry.oc.engine.team.fleet.RunState
import io.github.eslamasabry.oc.engine.team.fleet.WorkState
import io.github.eslamasabry.oc.feature.team.presentation.FleetAgentKind
import io.github.eslamasabry.oc.feature.team.presentation.FleetAgentNotice
import io.github.eslamasabry.oc.feature.team.presentation.FleetAgentRole
import io.github.eslamasabry.oc.feature.team.presentation.FleetAgentSent
import io.github.eslamasabry.oc.feature.team.presentation.FleetAgentsPresent
import io.github.eslamasabry.oc.feature.team.presentation.FleetBlock
import io.github.eslamasabry.oc.feature.team.presentation.FleetFactKind
import io.github.eslamasabry.oc.feature.team.presentation.FleetHold
import io.github.eslamasabry.oc.feature.team.presentation.FleetHomeKind
import io.github.eslamasabry.oc.feature.team.presentation.FleetLooksAfter
import io.github.eslamasabry.oc.feature.team.presentation.FleetOutputFeed
import io.github.eslamasabry.oc.feature.team.presentation.FleetOutputState
import io.github.eslamasabry.oc.feature.team.presentation.FleetStanding
import io.github.eslamasabry.oc.feature.team.presentation.FleetStepKind
import io.github.eslamasabry.oc.feature.team.presentation.FleetTranscript
import io.github.eslamasabry.oc.feature.team.presentation.FleetUnavailable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FleetAgentsPresentTest {
    private val now = 1_800_000_000_000L
    private val config = TeamConfig(TeamProvider.GasCity, "http://dev-pc.tail1234.ts.net:8372", city = "main")
    private val redact: (String) -> Redacted = Redactor()::details

    private fun ready(snapshot: FleetSnapshot, capabilities: FleetCapabilities = FleetCapabilities.FRONT) =
        FleetState(phase = FleetPhase.Ready, capabilities = capabilities, snapshot = snapshot, hasData = true, stream = FleetStream.Live, refreshedAtMs = now)

    private fun agent(id: String, state: AgentState = AgentState.Idle, pool: String? = "gastown.polecat", session: String? = "s-$id", activity: Long? = null, work: String? = null) =
        FleetAgent(id = id, name = id, state = state, sessionId = session, pool = pool, lastActivityMs = activity, currentWorkId = work)

    private fun page(agent: FleetAgent, caps: FleetCapabilities = FleetCapabilities.FRONT, snapshot: FleetSnapshot = FleetSnapshot(agents = listOf(agent)), sent: FleetAgentSent? = null, feed: FleetOutputFeed = FleetOutputFeed()) =
        FleetAgentsPresent.agent(config, ready(snapshot, caps), agent.id, now, redact, sent, feed = feed)

    @Test
    fun a_role_is_read_from_the_pool_without_its_instance_number() {
        assertEquals(FleetAgentRole.Worker, FleetAgentsPresent.role(agent("a", pool = "demo/gastown.polecat-3")))
        assertEquals(FleetAgentRole.Reviewer, FleetAgentsPresent.role(agent("a", pool = "gastown.refinery")))
        assertEquals(FleetAgentRole.Planner, FleetAgentsPresent.role(agent("a", pool = "gastown.mayor")))
        assertEquals(FleetAgentRole.Supervisor, FleetAgentsPresent.role(agent("a", pool = "gastown.witness")))
        assertEquals(FleetAgentRole.Other, FleetAgentsPresent.role(agent("a", pool = null)))
    }

    @Test
    fun the_list_puts_what_needs_you_first_then_working_then_asleep_with_the_newest_activity_first() {
        val snapshot =
            FleetSnapshot(
                agents =
                    listOf(
                        agent("asleep", AgentState.Stopped),
                        agent("old", AgentState.Working, activity = now - 600_000),
                        agent("new", AgentState.Working, activity = now - 1_000),
                        agent("asks", AgentState.Waiting),
                        agent("broken", AgentState.Crashed),
                    ),
            )
        val ui = FleetAgentsPresent.list(config, ready(snapshot), now)
        assertEquals(FleetHomeKind.Ready, ui.kind)
        assertEquals(listOf("asks", "broken", "new", "old", "asleep"), ui.rows.map { it.id })
        assertEquals(FleetStanding.NeedsYou, ui.rows.first().standing)
    }

    @Test
    fun a_stopped_agent_switched_off_on_purpose_is_paused_not_just_asleep() {
        val paused = FleetAgent("p", "p", AgentState.Stopped, suspended = true)
        val asleep = FleetAgent("s", "s", AgentState.Stopped)
        assertEquals(FleetStanding.Paused, FleetAgentsPresent.standing(paused))
        assertEquals(FleetStanding.Asleep, FleetAgentsPresent.standing(asleep))
        val ui = FleetAgentsPresent.list(config, ready(FleetSnapshot(agents = listOf(paused, asleep))), now)
        assertEquals(listOf("s", "p"), ui.rows.map { it.id })
        assertTrue(ui.rows.last().paused)
    }

    @Test
    fun a_working_row_names_the_task_it_works_on() {
        val snapshot =
            FleetSnapshot(
                runs = listOf(FleetRun("r-1", "Sync engine", RunState.Working)),
                work = listOf(FleetWork("w-1", "Write the diff", WorkState.Working, runId = "r-1")),
                agents = listOf(agent("fox", AgentState.Working, work = "w-1")),
            )
        assertEquals("Sync engine", FleetAgentsPresent.list(config, ready(snapshot), now).rows.single().task)
    }

    @Test
    fun a_nickname_is_shown_only_when_it_is_more_than_the_role() {
        assertEquals("furiosa", FleetAgentsPresent.nickname(FleetAgent("ocproof/gastown.furiosa", "ocproof/gastown.furiosa", AgentState.Idle, pool = "gastown.polecat")))
        assertNull(FleetAgentsPresent.nickname(FleetAgent("gastown.mayor", "gastown.mayor", AgentState.Idle, pool = "gastown.mayor")))
        assertNull(FleetAgentsPresent.nickname(agent("x", pool = null)))
    }

    @Test
    fun two_agents_with_the_same_words_are_told_apart_and_none_title_is_shared() {
        val agents =
            listOf(
                FleetAgent("demo/gastown.witness", "demo/gastown.witness", AgentState.Idle, pool = "gastown.witness"),
                FleetAgent("shop/gastown.witness", "shop/gastown.witness", AgentState.Idle, pool = "gastown.witness"),
                FleetAgent("gastown.deacon", "gastown.deacon", AgentState.Idle, pool = "gastown.deacon"),
                FleetAgent("a/gastown.boot", "a/gastown.boot", AgentState.Idle, pool = "gastown.boot"),
                FleetAgent("gastown.boot", "gastown.boot", AgentState.Idle, pool = "gastown.boot"),
                FleetAgent("gastown.polecat-1", "gastown.polecat-1", AgentState.Idle, pool = "gastown.polecat"),
                FleetAgent("gastown.polecat-2", "gastown.polecat-2", AgentState.Idle, pool = "gastown.polecat"),
            )
        val titles = FleetAgentsPresent.titles(agents)
        assertEquals("demo", titles.getValue("demo/gastown.witness").project)
        assertEquals("shop", titles.getValue("shop/gastown.witness").project)
        assertNull(titles.getValue("gastown.deacon").project)
        assertEquals(1, titles.getValue("gastown.polecat-1").number)
        assertEquals(2, titles.getValue("gastown.polecat-2").number)
        assertEquals(agents.size, titles.values.toSet().size)
    }

    @Test
    fun agents_still_alike_after_their_project_say_what_they_look_after_then_take_a_number() {
        // Names that only repeat the role (no nickname of their own), with no project to tell them apart.
        val deacons = listOf("gastown.deacon-1", "gastown.deacon-2").map { FleetAgent(it, it, AgentState.Idle, pool = "gastown.deacon") }
        val titles = FleetAgentsPresent.titles(deacons)
        assertEquals(FleetLooksAfter.Team, titles.getValue("gastown.deacon-1").looksAfter)
        assertEquals(listOf(1, 2), listOf(titles.getValue("gastown.deacon-1").number, titles.getValue("gastown.deacon-2").number))
    }

    @Test
    fun the_page_offers_each_control_only_when_the_computer_takes_it() {
        val running = agent("fox", AgentState.Working)
        val full = page(running).controls
        assertTrue(full.pause && full.nudge && full.restart && full.stop && full.forceStop)
        val readOnly = page(running, FleetCapabilities.READ)
        assertFalse(readOnly.controls.pause || readOnly.controls.nudge || readOnly.controls.stop || readOnly.controls.forceStop)
        assertTrue(readOnly.controlsElsewhere)
        // A host that takes controls but no hard kill (Flutter's loopback set has the kill, so it is made up here).
        assertFalse(page(running, FleetCapabilities.LOOPBACK.copy(controlAgentKill = false)).controls.forceStop)
    }

    @Test
    fun a_stopped_agent_cannot_be_paused_or_stopped_but_can_be_started_again() {
        val ui = page(agent("fox", AgentState.Stopped))
        assertFalse(ui.controls.pause || ui.controls.stop || ui.controls.forceStop)
        assertEquals(FleetAgentAction.Resume, ui.controls.start)
        assertEquals(FleetAgentNotice.Stopped, ui.notice)
        val crashed = page(agent("fox", AgentState.Crashed))
        assertEquals(FleetAgentAction.Restart, crashed.controls.start)
        assertEquals(FleetAgentNotice.Crashed, crashed.notice)
    }

    @Test
    fun a_stop_the_host_refused_while_the_agent_still_runs_offers_force_stop() {
        val running = agent("fox", AgentState.Working)
        val stuck = page(running, sent = FleetAgentSent(FleetAgentAction.Stop, ReceiptStatus.Rejected, "no"))
        assertEquals(FleetAgentNotice.StopStuck, stuck.notice)
        assertTrue(stuck.controls.forceStop)
        val unanswered = page(running, sent = FleetAgentSent(FleetAgentAction.Stop, ReceiptStatus.Pending))
        assertEquals(FleetAgentNotice.StopStuck, unanswered.notice)
        // No hard kill on this host: the plain stop is all there is, so the page does not promise one.
        assertEquals(FleetAgentNotice.None, page(running, FleetCapabilities.LOOPBACK.copy(controlAgentKill = false), sent = FleetAgentSent(FleetAgentAction.Stop, ReceiptStatus.Rejected)).notice)
        assertEquals(FleetAgentNotice.None, page(running, sent = FleetAgentSent(FleetAgentAction.Pause, ReceiptStatus.Rejected)).notice)
    }

    @Test
    fun a_worker_that_is_not_running_while_a_task_waits_did_not_start() {
        val snapshot = FleetSnapshot(runs = listOf(FleetRun("r", "Fix login", RunState.Waiting)), agents = listOf(agent("fox", AgentState.Stopped)))
        assertEquals(FleetAgentNotice.DidNotStart, page(snapshot.agents.single(), snapshot = snapshot).notice)
        // A reviewer is not the worker a waiting task needs.
        val reviewer = agent("rev", AgentState.Stopped, pool = "gastown.refinery")
        assertEquals(FleetAgentNotice.Stopped, page(reviewer, snapshot = snapshot.copy(agents = listOf(reviewer))).notice)
    }

    @Test
    fun an_agent_that_cannot_start_says_whether_its_model_is_the_reason() {
        val model = agent("fox", AgentState.Crashed).copy(unavailableReason = "provider not signed in")
        val other = agent("fox", AgentState.Crashed).copy(unavailableReason = "disk full")
        assertEquals(FleetUnavailable.Model, FleetAgentsPresent.unavailable(model))
        assertEquals(FleetUnavailable.Other, FleetAgentsPresent.unavailable(other))
        assertEquals(FleetAgentNotice.Unavailable, page(model).notice)
        // What the computer said stays under Details.
        assertTrue(page(model).facts.any { it.kind == FleetFactKind.HostSays })
    }

    @Test
    fun the_hold_counts_only_dependencies_the_host_lists_as_open() {
        val snapshot =
            FleetSnapshot(
                work =
                    listOf(
                        FleetWork("a", "Done step", WorkState.Completed),
                        FleetWork("b", "Open step", WorkState.Working),
                        FleetWork("c", "This one", WorkState.Waiting, dependsOn = listOf("a", "b", "gone")),
                        FleetWork("d", "Free one", WorkState.Working, dependsOn = listOf("a")),
                        FleetWork("e", "Stuck one", WorkState.Blocked, isBlocked = true),
                    ),
                agents = listOf(agent("x", AgentState.Working, work = "c"), agent("y", AgentState.Working, work = "d"), agent("z", AgentState.Working, work = "e")),
            )
        fun hold(id: String) = FleetAgentsPresent.agent(config, ready(snapshot), id, now, redact).work?.hold
        assertEquals(FleetHold.Steps(1), hold("x"))
        assertEquals(FleetHold.Nothing, hold("y"))
        assertEquals(FleetHold.Blocked, hold("z"))
    }

    @Test
    fun a_question_that_waits_on_the_agent_is_on_its_page() {
        val gate = FleetGate("g", GateKind.Choice, "Keep drafts in SQLite?", agentId = "s-fox")
        val ui = page(agent("fox", AgentState.Waiting), snapshot = FleetSnapshot(gates = listOf(gate), agents = listOf(agent("fox", AgentState.Waiting))))
        assertEquals("Keep drafts in SQLite?", ui.gate?.title)
        // A review that is ready only informs.
        val review = gate.copy(kind = GateKind.ReviewReady)
        assertNull(page(agent("fox"), snapshot = FleetSnapshot(gates = listOf(review), agents = listOf(agent("fox")))).gate)
    }

    @Test
    fun a_model_is_its_own_name_and_who_serves_it() {
        val ui = page(agent("fox").copy(model = "openai/gpt-x"))
        assertEquals("gpt-x", ui.model)
        assertEquals("openai", ui.modelFrom)
        assertNull(page(agent("fox").copy(model = "gpt-x")).modelFrom)
    }

    @Test
    fun an_agent_the_host_no_longer_lists_is_missing_and_a_team_not_found_says_so() {
        assertEquals(FleetAgentKind.Missing, FleetAgentsPresent.agent(config, ready(FleetSnapshot()), "gone", now, redact).kind)
        assertEquals(FleetAgentKind.Starting, FleetAgentsPresent.agent(config, FleetState(phase = FleetPhase.Probing), "x", now, redact).kind)
        assertEquals(FleetAgentKind.Failed, FleetAgentsPresent.agent(config, FleetState(phase = FleetPhase.Failed), "x", now, redact).kind)
    }

    @Test
    fun the_output_waits_then_reads_then_ends_and_is_unavailable_without_a_session_or_the_capability() {
        val fox = agent("fox", AgentState.Working)
        assertEquals(FleetOutputState.Connecting, page(fox).output.state)
        val text = "[tool: bash]\n[tool: flutter test]\nAll tests passed\n"
        val live = page(fox, feed = FleetOutputFeed(text))
        assertEquals(FleetOutputState.Live, live.output.state)
        assertEquals(FleetStepKind.Test, live.lastStep?.kind)
        assertTrue(live.facts.any { it.kind == FleetFactKind.LastCommand })
        assertEquals(FleetOutputState.Ended, page(fox, feed = FleetOutputFeed(text, ended = true)).output.state)
        assertEquals(FleetOutputState.Ended, page(fox, feed = FleetOutputFeed("", ended = true)).output.state)
        assertEquals(FleetOutputState.Unavailable, page(agent("fox", session = null)).output.state)
        assertEquals(FleetOutputState.Unavailable, page(fox, FleetCapabilities.NONE).output.state)
    }

    @Test
    fun the_output_keeps_only_the_newest_blocks_for_the_page() {
        val text = (1..20).joinToString("\n") { "[tool: bash]\nStep $it is going on now.\n[tool: echo $it]\nline $it" }
        val ui = page(agent("fox", AgentState.Working), feed = FleetOutputFeed(text)).output
        assertTrue(ui.blocks.size <= FleetAgentsPresent.BLOCKS_SHOWN)
        assertNotNull(ui.raw)
    }

    @Test
    fun the_transcript_splits_prose_from_steps_and_a_repeated_command_counts_once() {
        val text = "Looking at it.\n[tool: read]\n[tool: lib/a.dart]\nfile text\n[tool: edit]\n[tool: lib/b.dart]\n[tool: lib/b.dart]\nmore text\n"
        val blocks = FleetTranscript.parse(text)
        assertTrue(blocks.first() is FleetBlock.Prose)
        val steps = blocks.filterIsInstance<FleetBlock.Steps>().flatMap { it.steps }
        assertEquals(2, steps.size)
        assertEquals(FleetStepKind.Read, steps.first().kind)
        assertEquals(FleetStepKind.Edit, steps.last().kind)
        assertEquals("more text", steps.last().output)
    }
}
