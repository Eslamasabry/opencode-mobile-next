package io.github.eslamasabry.oc.feature.team.presentation

import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.engine.team.TeamConfig
import io.github.eslamasabry.oc.engine.team.TeamProvider
import io.github.eslamasabry.oc.engine.team.fleet.AgentState
import io.github.eslamasabry.oc.engine.team.fleet.FleetAgent
import io.github.eslamasabry.oc.engine.team.fleet.FleetAgentAction
import io.github.eslamasabry.oc.engine.team.fleet.FleetCapabilities
import io.github.eslamasabry.oc.engine.team.fleet.FleetFailure
import io.github.eslamasabry.oc.engine.team.fleet.FleetPhase
import io.github.eslamasabry.oc.engine.team.fleet.FleetSnapshot
import io.github.eslamasabry.oc.engine.team.fleet.FleetState
import io.github.eslamasabry.oc.engine.team.fleet.FleetWork
import io.github.eslamasabry.oc.engine.team.fleet.GateKind
import io.github.eslamasabry.oc.engine.team.fleet.ReceiptStatus
import io.github.eslamasabry.oc.engine.team.fleet.RunState
import io.github.eslamasabry.oc.engine.team.fleet.WorkState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

// ---- the agents of a computer's team (Dart team_agents_screen.dart + agent_screen.dart over OrchestrationController)

/** What an agent does for the team, in plain words (Dart `TeamAgentRole`); Gas City's own names stay in Details. */
public enum class FleetAgentRole { Worker, Reviewer, Planner, Supervisor, Helper, Other }

/** What a supervisor looks after, to tell two of them apart (Dart `teamAgentLooksAfter*`). */
public enum class FleetLooksAfter { Team, Watchdog, Workers }

/**
 * An agent's name for the person (Dart `teamAgentTitles`): its role, with its own name when the host gives one besides
 * the role, then, where several share the same words, its project, what it looks after and finally its number in the list.
 */
public data class FleetAgentTitle(
    val role: FleetAgentRole,
    val nickname: String? = null,
    val project: String? = null,
    val looksAfter: FleetLooksAfter? = null,
    val number: Int? = null,
)

/** Where an agent stands on the list, most urgent first (Dart `TeamAgentStanding`). */
public enum class FleetStanding { NeedsYou, Crashed, Working, Idle, Unknown, Asleep, Paused }

/** One agent's row on the list. */
public data class FleetAgentRow(
    val id: String,
    val title: FleetAgentTitle,
    val state: AgentState,
    val standing: FleetStanding,
    /** Switched off on purpose (not just asleep). */
    val paused: Boolean,
    /** What it works on, the task's own title; only while it works. */
    val task: String?,
    /** Since when it works (its session, else its last activity). */
    val sinceMs: Long?,
    val lastActivityMs: Long?,
    /** The computer cannot start it (its model is not set up, say). */
    val unavailable: Boolean,
) {
    val key: String
        get() = "agent:$id"
}

/** The Agents page of a computer's team. */
public data class FleetAgentsUi(
    val kind: FleetHomeKind = FleetHomeKind.Starting,
    val failure: FleetFailure? = null,
    val place: FleetPlace = FleetPlace.Computer,
    val notAnswering: Boolean = false,
    val stale: Boolean = false,
    val readAtMs: Long? = null,
    val rows: ImmutableList<FleetAgentRow> = persistentListOf(),
)

/** What went wrong with an agent, said once above its status (Dart `agent_screen.dart` `_list` notices, in its order). */
public enum class FleetAgentNotice { None, DidNotStart, StopStuck, Unavailable, Stopped, Crashed }

/** Why the computer cannot run it, in the words Flutter gives (Dart `teamAgentUnavailableWords`). */
public enum class FleetUnavailable { Model, Other }

/** What holds the agent's task up (Dart `_workHold`). */
public sealed interface FleetHold {
    public data object Blocked : FleetHold

    public data class Steps(val open: Int) : FleetHold

    public data object Nothing : FleetHold
}

public data class FleetAgentWork(val id: String, val title: String, val hold: FleetHold)

/** The question that waits on the person, on this agent's row. Answering is the gate sheet's. */
public data class FleetAgentGate(val id: String, val title: String)

/** Which controls the page offers: each only when the computer takes it (Dart `_controls`, TEAM-204). */
public data class FleetAgentControls(
    val pause: Boolean = false,
    val nudge: Boolean = false,
    val restart: Boolean = false,
    val stop: Boolean = false,
    val forceStop: Boolean = false,
    /** The stopped notice's own action: start it again (restart when it crashed). */
    val start: FleetAgentAction? = null,
)

/** What the person last sent to this agent from this page, and what came back. */
public data class FleetAgentSent(val action: FleetAgentAction, val status: ReceiptStatus, val message: String? = null, val retryable: Boolean = false)

/** The live output's state (Dart `AgentOutputTail`). */
public enum class FleetOutputState { Unavailable, Connecting, Live, Ended }

/** What the page was given of the agent's live output; the view model owns the stream. */
public data class FleetOutputFeed(val text: String = "", val ended: Boolean = false)

public data class FleetOutputUi(
    val state: FleetOutputState = FleetOutputState.Unavailable,
    /** The newest blocks of what the agent said and did, oldest first. */
    val blocks: ImmutableList<FleetBlock> = persistentListOf(),
    /** The newest lines of the raw output, redacted, for Technical details. */
    val raw: Redacted? = null,
)

/** The labels of Technical details, in Flutter's order. */
public enum class FleetFactKind { AgentId, SessionId, SessionName, Provider, Model, Harness, WorkDir, Branch, WorkId, LastCommand, HostSays, RawState, Pool, Pack }

public data class FleetFact(val kind: FleetFactKind, val value: Redacted)

public enum class FleetAgentKind { Starting, Failed, Missing, Ready }

/** The agent page. */
public data class FleetAgentUi(
    val kind: FleetAgentKind = FleetAgentKind.Starting,
    val failure: FleetFailure? = null,
    val place: FleetPlace = FleetPlace.Computer,
    val notAnswering: Boolean = false,
    val stale: Boolean = false,
    val readAtMs: Long? = null,
    val id: String = "",
    /** The agent's own handle: control labels name it ("Pause fox"). */
    val name: String = "",
    val title: FleetAgentTitle = FleetAgentTitle(FleetAgentRole.Other),
    val state: AgentState = AgentState.Unknown,
    val paused: Boolean = false,
    val work: FleetAgentWork? = null,
    val gate: FleetAgentGate? = null,
    val notice: FleetAgentNotice = FleetAgentNotice.None,
    val unavailable: FleetUnavailable? = null,
    /** Dart `teamContextRecyclePercent`: the context is nearly full. */
    val recycling: Boolean = false,
    val contextPercent: Int? = null,
    val sessionStartedAtMs: Long? = null,
    val lastActivityMs: Long? = null,
    val lastStep: FleetStep? = null,
    /** "gpt-x from openai": the model's own name and who serves it. */
    val model: String? = null,
    val modelFrom: String? = null,
    val controls: FleetAgentControls = FleetAgentControls(),
    /** The computer takes none of this page's controls (read only). */
    val controlsElsewhere: Boolean = false,
    val busy: Boolean = false,
    val sent: FleetAgentSent? = null,
    val output: FleetOutputUi = FleetOutputUi(),
    val facts: ImmutableList<FleetFact> = persistentListOf(),
    /** The agent is the one the stop-confirm names: its task, when it has one. */
    val stopTask: String? = null,
)

/** Pure mapping from a computer's agents to their pages (tested without Compose). */
public object FleetAgentsPresent {
    /** Dart `teamContextRecyclePercent`. */
    public const val RECYCLE_PERCENT: Int = 90

    /** The newest blocks shown on the page; the rest is in the raw output under Details. */
    public const val BLOCKS_SHOWN: Int = 6

    private const val RAW_LINES = 60
    private const val PARSED_CHARS = 60_000

    private fun tail(value: String): String = value.lowercase().substringAfterLast('/').substringAfterLast('.').replace(Regex("-\\d+$"), "")

    /** Dart `teamAgentKind`: the last part of the pool (or name) without an instance number. */
    public fun kind(agent: FleetAgent): String = tail(agent.pool ?: agent.name)

    /** Dart `teamAgentRole`. */
    public fun role(agent: FleetAgent): FleetAgentRole =
        when (kind(agent)) {
            "polecat", "polecats" -> FleetAgentRole.Worker
            "refinery" -> FleetAgentRole.Reviewer
            "mayor" -> FleetAgentRole.Planner
            "witness", "deacon", "boot" -> FleetAgentRole.Supervisor
            "dog", "dogs" -> FleetAgentRole.Helper
            else -> FleetAgentRole.Other
        }

    /** Dart `teamAgentPaused`: switched off on purpose. */
    public fun paused(agent: FleetAgent): Boolean = agent.suspended || agent.rawState?.trim()?.lowercase() in setOf("suspended", "paused")

    /** Dart `teamAgentIsLive`, over the engine's state (which already weighs the session first). */
    public fun live(agent: FleetAgent): Boolean = agent.state != AgentState.Stopped

    /** Dart `teamAgentStanding`. */
    public fun standing(agent: FleetAgent): FleetStanding =
        if (!live(agent)) (if (paused(agent)) FleetStanding.Paused else FleetStanding.Asleep)
        else
            when (agent.state) {
                AgentState.Waiting, AgentState.Blocked -> FleetStanding.NeedsYou
                AgentState.Crashed -> FleetStanding.Crashed
                AgentState.Working -> FleetStanding.Working
                AgentState.Idle -> FleetStanding.Idle
                AgentState.Stopped -> FleetStanding.Asleep
                AgentState.Unknown -> FleetStanding.Unknown
            }

    /** Dart `teamCompareAgentsByUrgency`: urgency, then the newest activity, then the name so the order is stable. */
    public val byUrgency: Comparator<FleetAgent> =
        Comparator { a, b ->
            val rank = standing(a).ordinal.compareTo(standing(b).ordinal)
            if (rank != 0) return@Comparator rank
            val at = a.lastActivityMs
            val bt = b.lastActivityMs
            when {
                at != null && bt != null && at != bt -> bt.compareTo(at)
                at != null && bt == null -> -1
                at == null && bt != null -> 1
                else -> a.name.compareTo(b.name)
            }
        }

    /** Dart `teamAgentNickname`: "furiosa" for `ocproof/gastown.furiosa` in the `gastown.polecat` pool; null when it only repeats the role. */
    public fun nickname(agent: FleetAgent): String? {
        val pool = agent.pool?.takeIf { it.isNotBlank() } ?: return null
        fun tailOf(value: String) = value.substringAfterLast('/').substringAfterLast('.').trim()
        val name = tailOf(agent.name)
        val role = tailOf(pool).replace(Regex("-\\d+$"), "").lowercase()
        if (name.isEmpty() || name.lowercase().startsWith(role)) return null
        return name
    }

    /** Dart `teamAgentTitles`: the title of each of [agents] by id, none twice. */
    public fun titles(agents: List<FleetAgent>): Map<String, FleetAgentTitle> {
        val titles = LinkedHashMap<String, FleetAgentTitle>()
        for (agent in agents) titles[agent.id] = FleetAgentTitle(role(agent), nickname(agent))

        fun shared(): List<List<FleetAgent>> = agents.groupBy { titles.getValue(it.id) }.values.filter { it.size > 1 }

        fun qualify(change: (FleetAgentTitle, FleetAgent) -> FleetAgentTitle?) {
            for (group in shared()) {
                for (agent in group) {
                    val next = change(titles.getValue(agent.id), agent) ?: continue
                    titles[agent.id] = next
                }
            }
        }
        // Where the title tells them apart: the project (the name before its last "/"), then what a supervisor looks after.
        qualify { title, agent ->
            val slash = agent.name.lastIndexOf('/')
            if (slash <= 0) null else title.copy(project = agent.name.substring(0, slash))
        }
        qualify { title, agent ->
            when (kind(agent)) {
                "deacon" -> title.copy(looksAfter = FleetLooksAfter.Team)
                "boot" -> title.copy(looksAfter = FleetLooksAfter.Watchdog)
                "witness" -> title.copy(looksAfter = FleetLooksAfter.Workers)
                else -> null
            }
        }
        for (group in shared()) group.forEachIndexed { index, agent -> titles[agent.id] = titles.getValue(agent.id).copy(number = index + 1) }
        return titles
    }

    private fun place(config: TeamConfig?, fleet: FleetState) = FleetPresent.place(config, fleet.host?.url)

    private fun taskOf(snapshot: FleetSnapshot, agent: FleetAgent): FleetWork? = agent.currentWorkId?.let { id -> snapshot.work.firstOrNull { it.id == id } }

    private fun taskTitle(snapshot: FleetSnapshot, work: FleetWork): String {
        val run = work.runId?.let { id -> snapshot.runs.firstOrNull { it.id == id } }
        return (run?.title ?: work.title).trim()
    }

    /** The agents most urgent first (Dart `TeamAgentsScreen`, over the computer's own agents instead of roles). */
    public fun list(config: TeamConfig?, fleet: FleetState, nowMs: Long): FleetAgentsUi {
        val place = place(config, fleet)
        if (config?.provider == TeamProvider.Fixture) return FleetAgentsUi(FleetHomeKind.Demo, place = place)
        if (fleet.phase == FleetPhase.Failed && !fleet.hasData) {
            return FleetAgentsUi(FleetHomeKind.Failed, failure = fleet.failure, place = place, notAnswering = true)
        }
        if (!fleet.hasData) return FleetAgentsUi(FleetHomeKind.Starting, place = place)
        val snapshot = fleet.snapshot
        val stale = fleet.phase == FleetPhase.Failed || fleet.stale(nowMs)
        val titles = titles(snapshot.agents)
        val rows =
            snapshot.agents.sortedWith(byUrgency).map { agent ->
                val working = agent.state == AgentState.Working
                val work = if (working) taskOf(snapshot, agent) else null
                FleetAgentRow(
                    id = agent.id,
                    title = titles.getValue(agent.id),
                    state = agent.state,
                    standing = standing(agent),
                    paused = paused(agent),
                    task = work?.let { taskTitle(snapshot, it) }?.takeIf { it.isNotEmpty() },
                    sinceMs = agent.sessionStartedAtMs ?: agent.lastActivityMs,
                    lastActivityMs = agent.lastActivityMs,
                    unavailable = agent.unavailableReason != null,
                )
            }
        return FleetAgentsUi(FleetHomeKind.Ready, place = place, notAnswering = stale, stale = stale, readAtMs = fleet.refreshedAtMs, rows = rows.toImmutableList())
    }

    /** Dart `teamAgentUnavailableWords`: its model is the problem, or something else on the computer. */
    public fun unavailable(agent: FleetAgent): FleetUnavailable? {
        val reason = agent.unavailableReason?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        val model = Regex("provider|model|sign(ed)?[ -]?in|log(ged)?[ -]?in|api[ -]?key|credential|auth|token")
        return if (model.containsMatchIn(reason)) FleetUnavailable.Model else FleetUnavailable.Other
    }

    /** "gpt-x from openai": the model's own name, then who serves it (Dart `_modelWords`). */
    private fun modelFrom(agent: FleetAgent, model: String): Pair<String, String?> {
        val slash = model.indexOf('/')
        val name = if (slash < 0) model else model.substring(slash + 1)
        val provider = (if (slash > 0) model.substring(0, slash) else agent.provider)?.trim()
        if (provider.isNullOrEmpty() || name.isEmpty()) return model to null
        return name to provider
    }

    /** Whether this agent's last stop did not end it: the stop went unanswered or was refused and the host still shows it running. */
    private fun stopDidNotWork(agent: FleetAgent, caps: FleetCapabilities, sent: FleetAgentSent?): Boolean {
        if (!caps.controlAgentKill) return false
        if (agent.state == AgentState.Stopped || agent.state == AgentState.Crashed) return false
        return sent != null && sent.action == FleetAgentAction.Stop && (sent.status == ReceiptStatus.Pending || sent.status == ReceiptStatus.Rejected)
    }

    /** One agent's page (Dart `AgentScreen`): where it stands, what went wrong, its controls and its live output. */
    public fun agent(
        config: TeamConfig?,
        fleet: FleetState,
        agentId: String,
        nowMs: Long,
        redact: (String) -> Redacted,
        sent: FleetAgentSent? = null,
        busy: Boolean = false,
        feed: FleetOutputFeed = FleetOutputFeed(),
    ): FleetAgentUi {
        val place = place(config, fleet)
        if (config?.provider == TeamProvider.Fixture) return FleetAgentUi(FleetAgentKind.Missing, place = place, id = agentId)
        if (fleet.phase == FleetPhase.Failed && !fleet.hasData) {
            return FleetAgentUi(FleetAgentKind.Failed, failure = fleet.failure, place = place, notAnswering = true, id = agentId)
        }
        if (!fleet.hasData) return FleetAgentUi(FleetAgentKind.Starting, place = place, id = agentId)
        val snapshot = fleet.snapshot
        val stale = fleet.phase == FleetPhase.Failed || fleet.stale(nowMs)
        val agent = snapshot.agents.firstOrNull { it.id == agentId || it.sessionId == agentId }
            ?: return FleetAgentUi(FleetAgentKind.Missing, place = place, notAnswering = stale, stale = stale, readAtMs = fleet.refreshedAtMs, id = agentId)
        val caps = fleet.capabilities
        val work = snapshot.work.firstOrNull { it.id == agent.currentWorkId }
        val gate =
            snapshot.gates.firstOrNull { g ->
                g.kind != GateKind.ReviewReady &&
                    (g.agentId == agent.id || (agent.sessionId != null && g.agentId == agent.sessionId) || (work != null && g.workId == work.id))
            }
        val stopped = agent.state == AgentState.Stopped || agent.state == AgentState.Crashed
        val unavailable = unavailable(agent)
        val didNotStart =
            !live(agent) && role(agent) == FleetAgentRole.Worker && snapshot.visibleRuns.any { it.state == RunState.Waiting }
        val notice =
            when {
                didNotStart -> FleetAgentNotice.DidNotStart
                stopDidNotWork(agent, caps, sent) -> FleetAgentNotice.StopStuck
                unavailable != null -> FleetAgentNotice.Unavailable
                agent.state == AgentState.Crashed -> FleetAgentNotice.Crashed
                agent.state == AgentState.Stopped -> FleetAgentNotice.Stopped
                else -> FleetAgentNotice.None
            }
        val output = output(agent, caps, feed, redact)
        val lastStep = if (output.blocks.isEmpty()) null else FleetTranscript.lastStep(output.blocks)
        val (modelName, from) = agent.model?.trim()?.takeIf { it.isNotEmpty() }?.let { modelFrom(agent, it) } ?: (null to null)
        return FleetAgentUi(
            kind = FleetAgentKind.Ready,
            place = place,
            notAnswering = stale,
            stale = stale,
            readAtMs = fleet.refreshedAtMs,
            id = agent.id,
            name = agent.name,
            title = titles(snapshot.agents).getValue(agent.id),
            state = agent.state,
            paused = paused(agent),
            work = work?.let { FleetAgentWork(it.id, it.title, hold(it, snapshot.work)) },
            gate = gate?.let { FleetAgentGate(it.id, it.title) },
            notice = notice,
            unavailable = unavailable,
            recycling = (agent.contextPercent ?: 0) >= RECYCLE_PERCENT,
            contextPercent = agent.contextPercent,
            sessionStartedAtMs = agent.sessionStartedAtMs,
            lastActivityMs = agent.lastActivityMs,
            lastStep = lastStep,
            model = modelName,
            modelFrom = from,
            controls =
                FleetAgentControls(
                    pause = caps.controlAgent && !stopped,
                    nudge = caps.controlMessage,
                    restart = caps.controlAgent,
                    stop = caps.controlAgent && !stopped,
                    forceStop = caps.controlAgentKill && !stopped,
                    start = if (caps.controlAgent && stopped) (if (agent.state == AgentState.Crashed) FleetAgentAction.Restart else FleetAgentAction.Resume) else null,
                ),
            controlsElsewhere = !caps.controlAgent && !caps.controlMessage,
            busy = busy,
            sent = sent,
            output = output,
            facts =
                (facts(agent, work, FleetTranscript.commandLine(lastStep), redact) +
                        // What the computer said when it refused the last control: technical, so only here.
                        listOfNotNull(sent?.takeIf { it.status == ReceiptStatus.Rejected }?.message?.takeIf { it.isNotBlank() }?.let { FleetFact(FleetFactKind.HostSays, redact(it)) }))
                    .toImmutableList(),
            stopTask = work?.title,
        )
    }

    /** Dart `_workHold`: only a dependency the host lists as still open counts. */
    private fun hold(work: FleetWork, all: List<FleetWork>): FleetHold {
        if (work.isBlocked) return FleetHold.Blocked
        val ids = work.dependsOn.toSet()
        val open = all.count { it.id in ids && it.state != WorkState.Completed && it.state != WorkState.Cancelled }
        return if (open > 0) FleetHold.Steps(open) else FleetHold.Nothing
    }

    private fun output(agent: FleetAgent, caps: FleetCapabilities, feed: FleetOutputFeed, redact: (String) -> Redacted): FleetOutputUi {
        if (!caps.agentOutput || agent.sessionId == null) return FleetOutputUi(FleetOutputState.Unavailable)
        if (feed.text.isEmpty()) return FleetOutputUi(if (feed.ended) FleetOutputState.Ended else FleetOutputState.Connecting)
        val parsed = FleetTranscript.parse(feed.text.takeLast(PARSED_CHARS))
        val raw = feed.text.lineSequence().toList().takeLast(RAW_LINES).joinToString("\n")
        return FleetOutputUi(
            state = if (feed.ended) FleetOutputState.Ended else FleetOutputState.Live,
            blocks = parsed.takeLast(BLOCKS_SHOWN).toImmutableList(),
            raw = redact(raw),
        )
    }

    private fun facts(agent: FleetAgent, work: FleetWork?, lastCommand: String?, redact: (String) -> Redacted): List<FleetFact> {
        val out = ArrayList<FleetFact>()

        fun add(kind: FleetFactKind, value: String?) {
            if (!value.isNullOrBlank()) out += FleetFact(kind, redact(value))
        }
        add(FleetFactKind.AgentId, agent.id)
        add(FleetFactKind.SessionId, agent.sessionId)
        add(FleetFactKind.SessionName, agent.sessionName)
        add(FleetFactKind.Provider, agent.provider)
        add(FleetFactKind.Model, agent.model)
        add(FleetFactKind.Harness, agent.harness)
        add(FleetFactKind.WorkDir, agent.workDir)
        add(FleetFactKind.Branch, agent.branch)
        add(FleetFactKind.WorkId, work?.id)
        add(FleetFactKind.LastCommand, lastCommand)
        add(FleetFactKind.HostSays, agent.unavailableReason)
        add(FleetFactKind.RawState, agent.rawState)
        add(FleetFactKind.Pool, agent.pool)
        add(FleetFactKind.Pack, agent.pack)
        return out
    }
}
