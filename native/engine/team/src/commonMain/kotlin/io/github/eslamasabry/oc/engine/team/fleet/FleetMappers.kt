package io.github.eslamasabry.oc.engine.team.fleet

import io.github.eslamasabry.oc.engine.team.TeamHostMode

/*
 * Gas City → product models (Dart gascity_mappers.dart). The state tables are Dart's, row for row; the comments name
 * the Dart function each piece ports.
 */

/** Cross-resource facts a bead's state depends on (Dart `GcWorkContext`). */
internal data class WorkContext(
    val readyIds: Set<String> = emptySet(),
    val waitingSessions: Set<String> = emptySet(),
    val needsInputSessions: Set<String> = emptySet(),
    val convoyByBead: Map<String, String> = emptyMap(),
) {
    fun sessionIn(set: Set<String>, bead: WireBead): Boolean =
        set.isNotEmpty() && listOf(bead.sessionId, bead.sessionName, bead.meta("session_id")).any { it != null && it in set }

    companion object {
        fun from(
            ready: List<WireBead> = emptyList(),
            waits: List<WireWait> = emptyList(),
            pending: List<WirePending> = emptyList(),
            convoys: List<WireConvoy> = emptyList(),
        ): WorkContext {
            val waiting = mutableSetOf<String>()
            waits.filter { it.active }.forEach { w -> w.sessionId?.let(waiting::add); w.sessionName?.let(waiting::add) }
            val byBead = LinkedHashMap<String, String>()
            convoys.forEach { c -> c.trackedIds.forEach { byBead.getOrPut(it) { c.id } } }
            return WorkContext(
                readyIds = ready.map { it.id }.toSet(),
                waitingSessions = waiting,
                needsInputSessions = pending.mapNotNull { it.sessionId }.toSet(),
                convoyByBead = byBead,
            )
        }
    }
}

internal object FleetMap {
    /** Dart `isRefineryName`. */
    fun isRefinery(name: String): Boolean = name.trim().lowercase().endsWith("refinery")

    /** Dart `_rigOf`: the rig of `rig/agent`. */
    fun rigOf(identity: String?): String? {
        if (identity == null) return null
        val slash = identity.indexOf('/')
        return if (slash <= 0) null else identity.substring(0, slash)
    }

    // ---- work

    /** Dart `mapBead`. */
    fun bead(bead: WireBead, context: WorkContext = WorkContext()): FleetWork {
        val state =
            WorkState.derive(
                status = bead.status,
                isBlocked = bead.isBlocked,
                isReady = bead.id in context.readyIds,
                sessionWaiting = context.sessionIn(context.waitingSessions, bead),
                needsInput = context.sessionIn(context.needsInputSessions, bead),
                needsReview = bead.needsReview || bead.assignee?.let(::isRefinery) == true,
                lastError = bead.lastError,
                closedReason = bead.closedReason,
            )
        return FleetWork(
            id = bead.id,
            title = bead.title.ifEmpty { bead.id },
            state = state,
            rawState = bead.status,
            projectId = bead.meta("rig") ?: rigOf(bead.routedTo ?: bead.assignee),
            runId = context.convoyByBead[bead.id],
            parentId = bead.parent,
            assignee = bead.assignee ?: bead.routedTo,
            assigneeOwn = bead.assignee,
            sessionId = bead.sessionId,
            isBlocked = bead.isBlocked,
            labels = bead.labels,
            dependsOn = bead.dependsOn,
            closedReason = bead.closedReason,
            description = bead.description,
            branch = bead.meta("branch"),
            mergeResult = bead.meta("merge_result"),
            createdAtMs = bead.createdAtMs,
            updatedAtMs = bead.updatedAtMs,
        )
    }

    /** Dart `mapBeads` without the host's own beads. */
    fun beads(beads: List<WireBead>, context: WorkContext): List<FleetWork> = beads.filter { !it.internal }.map { bead(it, context) }

    /** Dart `workHandedToMerge`: an open item whose own assignee is a rig's refinery. */
    fun handedToMerge(item: FleetWork): Boolean =
        when (item.state) {
            WorkState.Completed, WorkState.Cancelled, WorkState.Failed, WorkState.Blocked, WorkState.NeedsInput -> false
            else -> item.assigneeOwn?.trim()?.takeIf { it.isNotEmpty() }?.let(::isRefinery) == true
        }

    // ---- runs

    private val autoConvoyTitle = Regex("^sling-[A-Za-z0-9_.-]+$")

    /** Dart `batchTitle`. */
    fun batchTitle(convoyTitle: String, tracked: List<FleetWork>): String? {
        val own = convoyTitle.trim()
        if (own.isNotEmpty() && !autoConvoyTitle.matches(own)) return own
        val titles = tracked.map { it.title.trim() }.filter { it.isNotEmpty() }
        return when {
            titles.isEmpty() -> own.ifEmpty { null }
            titles.size == 1 -> titles.single()
            else -> "${titles.first()} + ${titles.size - 1} more"
        }
    }

    /** Dart `_workStarted`: an agent picked it up (its own assignee or a live session). */
    private fun started(item: FleetWork): Boolean = !item.assigneeOwn.isNullOrEmpty() || !item.sessionId.isNullOrEmpty()

    /** Dart `_runStateFromWork`. */
    fun runStateFromWork(tracked: List<FleetWork>): RunState {
        if (tracked.isEmpty()) return RunState.Waiting
        var anyBlocked = false
        var anyWorking = false
        var allCompleted = true
        var allCancelled = true
        var allDone = true
        for (w in tracked) {
            if (handedToMerge(w)) {
                allCompleted = false
                allCancelled = false
                allDone = false
                continue
            }
            when (w.state) {
                WorkState.Failed -> return RunState.Failed
                WorkState.Blocked, WorkState.NeedsInput -> anyBlocked = true
                WorkState.Working, WorkState.Review, WorkState.Waiting -> anyWorking = true
                WorkState.Queued, WorkState.Ready, WorkState.Unknown -> if (started(w)) anyWorking = true
                WorkState.Completed, WorkState.Cancelled -> Unit
            }
            if (w.state != WorkState.Completed) allCompleted = false
            if (w.state != WorkState.Cancelled) allCancelled = false
            if (w.state != WorkState.Completed && w.state != WorkState.Cancelled) allDone = false
        }
        return when {
            anyBlocked -> RunState.Blocked
            anyWorking -> RunState.Working
            allCompleted -> RunState.Completed
            allCancelled -> RunState.Cancelled
            allDone -> RunState.Completed
            else -> RunState.Waiting
        }
    }

    /** Dart `mapConvoy`. */
    fun convoy(convoy: WireConvoy, work: Map<String, FleetWork>, context: WorkContext, closedAtMs: Long? = null): FleetRun {
        val head = convoy.bead
        val children = convoy.children.associateBy { it.id }
        val tracked = ArrayList<FleetWork>()
        for (id in convoy.trackedIds) {
            val item = work[id]
            if (item != null) tracked += item else children[id]?.let { tracked += bead(it, context) }
        }
        if (tracked.isEmpty()) convoy.children.forEach { tracked += bead(it, context) }
        val completed = tracked.count { it.state == WorkState.Completed }
        val state =
            if (head.isClosed) {
                if (head.closedReason?.lowercase()?.contains("cancel") == true) RunState.Cancelled else RunState.Completed
            } else {
                runStateFromWork(tracked)
            }
        val failure = tracked.firstOrNull { it.state == WorkState.Failed }
        val finishedAt = if (head.isClosed) closedAtMs ?: head.updatedAtMs else null
        return FleetRun(
            id = head.id,
            title = batchTitle(head.title, tracked) ?: head.id,
            state = state,
            rawState = head.status,
            kind = RunKind.Batch,
            projectId = head.meta("rig"),
            stepCount = convoy.progressTotal ?: tracked.size,
            completedSteps = convoy.progressClosed ?: completed,
            lastError = failure?.let { "Work item ${it.id} failed" },
            startedAtMs = head.createdAtMs,
            updatedAtMs = head.updatedAtMs ?: finishedAt,
            finishedAtMs = finishedAt,
            merged = state == RunState.Completed && tracked.isNotEmpty() && tracked.all { it.mergeResult == "merged" },
        )
    }

    /** Dart `runStateFromGc`. */
    fun runState(status: String?): RunState =
        when (status?.trim()?.lowercase()) {
            "canceling", "cancelling", "skipped" -> RunState.Cancelled
            else -> RunState.fromProvider(status)
        }

    private val upkeepFormula = Regex("^mol-([a-z0-9_.-]+-)?patrol$|^mol-shutdown-dance$|^mol-digest-generate$")

    /** Dart `isUpkeepRun`. */
    fun upkeep(run: WireRun): Boolean {
        for (name in listOf(run.title, run.formula)) {
            val n = name?.trim()?.lowercase() ?: continue
            if (n.isEmpty()) continue
            if (upkeepFormula.matches(n) || n.startsWith("order:") || n.startsWith("nudge:")) return true
        }
        if (run.target?.trim()?.lowercase() == "workflow") {
            val scoped = !run.scopeKind.isNullOrEmpty() || !run.scopeRef.isNullOrEmpty()
            if (!scoped && !run.tracksWork) return true
        }
        return false
    }

    /** Dart `mapRun`. */
    fun run(run: WireRun): FleetRun {
        var state = runState(run.status)
        if (state == RunState.Unknown && run.lastError != null) state = RunState.Failed
        return FleetRun(
            id = run.runId,
            title = run.title.ifEmpty { run.formula ?: run.runId },
            state = state,
            rawState = run.status,
            kind = RunKind.Formula,
            projectId = if (run.scopeKind == "rig") run.scopeRef else null,
            formula = run.formula,
            lastError = run.lastError,
            startedAtMs = run.startedAtMs,
            updatedAtMs = run.updatedAtMs,
            upkeep = upkeep(run),
        )
    }

    // ---- agents

    private val dogSlot = Regex("(^|[./])bd\\.dog-\\d+$")

    /** Dart `isAgentEntry`: the host's empty slots and idle helpers are not agents. */
    fun isAgentEntry(agent: WireAgent, session: WireSession?): Boolean {
        val state = agent.state?.trim()?.lowercase()
        val hasSession = agent.hasSession || session != null
        val running = agent.running || session?.running == true
        val helper = agent.pack == "core" || agent.pack == "bd" || "control-dispatcher" in agent.name || dogSlot.containsMatchIn(agent.name)
        if (helper && !running) return false
        if (agent.pool != null && !hasSession && !running && (state == null || state == "stopped" || state == "suspended")) return false
        return true
    }

    /** Dart `_agentState`. */
    fun agentState(
        agentState: String?,
        sessionState: String?,
        running: Boolean,
        suspended: Boolean,
        unavailable: Boolean,
        needsInput: Boolean,
        waiting: Boolean,
    ): AgentState {
        val a = AgentState.fromProvider(agentState)
        val s = AgentState.fromProvider(sessionState)
        if (a == AgentState.Crashed || s == AgentState.Crashed || unavailable) return AgentState.Crashed
        if (suspended || s == AgentState.Stopped) return AgentState.Stopped
        // A running session is the fact; the agent word ("stopped" through a tmux backend a phone never has) yields.
        if (a == AgentState.Stopped && !(running && s == AgentState.Working)) return AgentState.Stopped
        if (!running && agentState != null && a != AgentState.Working) return AgentState.Stopped
        if (!running && agentState == null && sessionState == null) return AgentState.Stopped
        if (needsInput) return AgentState.Waiting
        if (waiting) return AgentState.Blocked
        if (a == AgentState.Idle) return AgentState.Idle
        if (a == AgentState.Working) return AgentState.Working
        if (a == AgentState.Waiting || a == AgentState.Blocked) return a
        if (s == AgentState.Working && running) return AgentState.Working
        if (s != AgentState.Unknown) return s
        if (a != AgentState.Unknown) return a
        if (agentState == null && sessionState == null && running) return AgentState.Working
        return AgentState.Unknown
    }

    private fun branchOf(metadata: Map<String, String>?): String? =
        metadata?.let { m -> listOf("branch", "gc.branch", "worktree_branch").firstNotNullOfOrNull { m[it]?.trim()?.takeIf(String::isNotEmpty) } }

    /** Dart `mapAgents`: `/agents` joined with `/sessions`; sessions no agent claimed are pool instances. */
    fun agents(
        agents: List<WireAgent>,
        sessions: List<WireSession>,
        pending: List<WirePending>,
        waits: List<WireWait>,
    ): List<FleetAgent> {
        val context = WorkContext.from(pending = pending, waits = waits)
        fun matches(set: Set<String>, keys: List<String?>) = set.isNotEmpty() && keys.any { it != null && it in set }
        val byName = LinkedHashMap<String, WireSession>()
        val byAlias = LinkedHashMap<String, WireSession>()
        sessions.forEach { s ->
            s.sessionName?.let { byName.getOrPut(it) { s } }
            s.alias?.let { byAlias.getOrPut(it) { s } }
        }
        val claimed = mutableSetOf<String>()
        val out = ArrayList<FleetAgent>()
        for (agent in agents) {
            val session = agent.sessionName?.let { byName[it] } ?: byAlias[agent.name]
            session?.let { claimed += it.id }
            if (!isAgentEntry(agent, session)) continue
            val keys = listOf(session?.id, session?.sessionName, agent.sessionName, agent.name)
            out +=
                FleetAgent(
                    id = agent.name,
                    name = agent.name,
                    state =
                        agentState(
                            agentState = agent.state,
                            sessionState = session?.state,
                            running = agent.running || session?.running == true,
                            suspended = agent.suspended,
                            unavailable = !agent.available && agent.unavailableReason != null,
                            needsInput = matches(context.needsInputSessions, keys),
                            waiting = matches(context.waitingSessions, keys),
                        ),
                    rawState = agent.state ?: session?.state,
                    sessionId = session?.id,
                    sessionName = agent.sessionName ?: session?.sessionName,
                    pool = agent.pool ?: session?.takeIf { it.isPoolInstance }?.template,
                    pack = agent.pack,
                    provider = agent.provider ?: session?.provider,
                    model = agent.model ?: session?.model,
                    harness = agent.displayName ?: session?.displayName,
                    currentWorkId = agent.activeBead ?: session?.activeBead,
                    lastActivityMs = agent.sessionLastActivityMs ?: session?.lastActiveMs,
                    contextPercent = (agent.contextPct ?: session?.contextPct)?.coerceIn(0, 100),
                    workDir = session?.workDir,
                    branch = branchOf(session?.metadata),
                    sessionStartedAtMs = session?.createdAtMs,
                    suspended = agent.suspended || agent.state?.trim()?.lowercase() == "suspended",
                    unavailableReason = if (agent.available) null else agent.unavailableReason,
                )
        }
        for (s in sessions) {
            if (s.id in claimed) continue
            val keys = listOf(s.id, s.sessionName, s.alias)
            out +=
                FleetAgent(
                    id = s.id,
                    name = s.alias ?: s.title ?: s.template ?: s.id,
                    state =
                        agentState(
                            agentState = null,
                            sessionState = s.state,
                            running = s.running,
                            suspended = false,
                            unavailable = false,
                            needsInput = matches(context.needsInputSessions, keys),
                            waiting = matches(context.waitingSessions, keys),
                        ),
                    rawState = s.state,
                    sessionId = s.id,
                    sessionName = s.sessionName,
                    pool = if (s.isPoolInstance) s.template else null,
                    provider = s.provider,
                    model = s.model,
                    harness = s.displayName,
                    currentWorkId = s.activeBead,
                    lastActivityMs = s.lastActiveMs,
                    contextPercent = s.contextPct?.coerceIn(0, 100),
                    workDir = s.workDir,
                    branch = branchOf(s.metadata),
                    sessionStartedAtMs = s.createdAtMs,
                    hasAgentEntry = false,
                )
        }
        return out
    }

    // ---- gates

    /** Dart `_gateTitle`: the host's words when it sent none of its own. */
    private fun gateTitle(kind: GateKind, raw: String?): String =
        when (kind) {
            GateKind.Choice -> "Choose an option"
            GateKind.Confirmation -> "Confirm to continue"
            GateKind.FreeText -> "Input needed"
            GateKind.GateBead -> "Gate"
            GateKind.RunFailed -> "Run failed"
            GateKind.ReviewReady -> "Ready for review"
            GateKind.Unknown -> raw ?: "Needs attention"
        }

    /** Dart `mapGates`: pending interactions, then gate and review beads, then failed runs (not the host's chores). */
    fun gates(pending: List<WirePending>, beads: List<WireBead>, runs: List<FleetRun>): List<FleetGate> {
        val out = ArrayList<FleetGate>()
        for (p in pending) {
            val kind = GateKind.fromProvider(p.kind)
            out += FleetGate(p.requestId, kind, p.prompt ?: gateTitle(kind, p.kind), p.kind, p.prompt, agentId = p.sessionId, choices = p.options, createdAtMs = p.createdAtMs)
        }
        for (b in beads) {
            if (b.isClosed) continue
            val agent = b.assignee ?: b.routedTo
            if (b.isGate) {
                out += FleetGate("bead:${b.id}", GateKind.GateBead, b.title.ifEmpty { b.id }, b.issueType, b.description, workId = b.id, agentId = agent, createdAtMs = b.createdAtMs)
            } else if (b.needsReview) {
                out +=
                    FleetGate(
                        "review:${b.id}", GateKind.ReviewReady, b.title.ifEmpty { b.id }, "needs-review", b.description,
                        workId = b.id, agentId = agent, createdAtMs = b.updatedAtMs ?: b.createdAtMs,
                    )
            }
        }
        for (r in runs) {
            if (r.state != RunState.Failed || r.upkeep) continue
            out += FleetGate("run:${r.id}", GateKind.RunFailed, r.title, r.rawState, r.lastError, runId = r.id, createdAtMs = r.updatedAtMs ?: r.startedAtMs)
        }
        return out
    }

    // ---- rigs, usage, orders, host

    /** Dart `mapRigs`. */
    fun rigs(status: WireStatus): List<FleetProject> =
        status.rigs.filter { it.name.isNotEmpty() }.map { FleetProject(it.name, it.name, it.path, it.suspended) }

    /** Dart `mapUsage` + `mapStatusCounts`, the figures a page shows. Every figure is the host's estimate. */
    fun usage(usage: Json, status: WireStatus?): FleetUsage {
        val available = J.bool(usage, "available") == true && J.text(usage, "source") != "unavailable"
        val today = if (available && J.hasMap(usage, "today")) J.field(usage, "today") else null
        val cost = today?.let { J.double(it, "cost_usd_estimate") }?.takeIf { it.isFinite() && it >= 0 }
        return FleetUsage(
            activeAgents = status?.let { it.agentsRunning ?: it.running },
            runsInProgress = status?.workInProgress,
            workOpen = status?.workOpen,
            workReady = status?.workReady,
            workInProgress = status?.workInProgress,
            inputTokensToday = today?.let { J.long(it, "input_tokens") }?.takeIf { it >= 0 },
            outputTokensToday = today?.let { J.long(it, "output_tokens") }?.takeIf { it >= 0 },
            costUsdToday = cost,
            estimated = true,
        )
    }

    /** Dart `mapOrders`. */
    fun orders(orders: List<WireOrder>): List<FleetJob> =
        orders.filter { it.name.isNotEmpty() }.map { o ->
            FleetJob(
                id = o.scopedName ?: o.name,
                name = o.name,
                enabled = o.enabled,
                trigger =
                    when (o.trigger?.trim()?.lowercase()) {
                        "cron" -> FleetJobTrigger.Cron
                        "cooldown", "interval" -> FleetJobTrigger.Interval
                        "event" -> FleetJobTrigger.Event
                        "manual" -> FleetJobTrigger.Manual
                        "webhook" -> FleetJobTrigger.Webhook
                        "condition" -> FleetJobTrigger.Condition
                        else -> FleetJobTrigger.Other
                    },
                projectId = o.rig,
                schedule = o.schedule,
                interval = o.interval,
                onEvent = o.on,
                description = o.description,
            )
        }

    /** Dart `mapHostIdentity`: `/health` first, then `/status`. */
    fun host(url: String, hostMode: TeamHostMode, health: WireHealth?, status: WireStatus?, front: Boolean): FleetHost =
        FleetHost(url, hostMode, city = health?.city ?: status?.name, version = health?.version ?: status?.version, front = front)
}

/** Dart `mapEvent`'s session types: each one changes the agents. */
private val SESSION_TYPES =
    setOf(
        "session.woke", "session.stopped", "session.crashed", "session.idle_killed", "session.max_age_killed",
        "session.quarantined", "session.suspended",
    )

/** What a stream frame makes stale (Dart `_onEvent`'s dirty scopes). */
public enum class FleetScope { Projects, Runs, Work, Agents, Gates, Usage }

/** One frame, read (Dart `mapStreamFrame` + `mapEvent`, the part that decides what to read again). */
internal sealed interface FleetSignal {
    val seq: Long?

    /** A heartbeat: the stream is alive. */
    data class Heartbeat(override val seq: Long?) : FleetSignal

    /** [scopes] changed; [beadId] is set for a bead change (a session bead also changes the agents). */
    data class Changed(val scopes: Set<FleetScope>, val beadId: String? = null, override val seq: Long? = null) : FleetSignal

    /** Something the team pages do not read (activity, results, unknown types). */
    data class Other(override val seq: Long?) : FleetSignal

    companion object {
        fun of(frame: FleetFrame): FleetSignal {
            // Dart `OrchestrationSseClient._decode`: malformed data is skipped; the name defaults from the data's shape.
            val body = (io.github.eslamasabry.oc.core.common.dart.DartJson.decodeOrNull(frame.data) as? Map<*, *>)?.let(J::map) ?: return Other(null)
            val frameSeq = frame.id?.trim()?.toLongOrNull() ?: J.long(body, "seq")
            val name =
                frame.event
                    ?: when {
                        body.size == 1 && body.containsKey("timestamp") -> "heartbeat"
                        body.containsKey("type") -> "event"
                        else -> "message"
                    }
            when (name) {
                "heartbeat" -> return Heartbeat(frameSeq)
                "pending", "pending_cleared" -> return Changed(setOf(FleetScope.Gates), seq = frameSeq)
                "event", "tagged_event" -> Unit
                else -> return Other(frameSeq)
            }
            val event = WireEvent.parse(body)
            val seq = event.seq ?: frameSeq
            val type = event.type
            if (type == "heartbeat") return Heartbeat(seq)
            val payloadBead = if (J.hasMap(event.payload, "bead")) J.field(event.payload, "bead") else null
            val beadId = event.subject ?: payloadBead?.let { J.text(it, "id") }
            return when {
                type == "bead.created" || type == "bead.updated" || type == "bead.closed" || type == "bead.deleted" ->
                    if (beadId != null) Changed(setOf(FleetScope.Work, FleetScope.Runs, FleetScope.Gates), beadId, seq) else Other(seq)
                type in SESSION_TYPES && (event.sessionId ?: J.text(event.payload, "session_id") ?: event.subject) != null ->
                    Changed(setOf(FleetScope.Agents), seq = seq)
                (type == "convoy.created" || type == "convoy.closed" || event.family == "run") && (event.runId ?: event.subject) != null ->
                    Changed(setOf(FleetScope.Runs), seq = seq)
                else -> Other(seq)
            }
        }
    }
}
