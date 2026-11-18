package io.github.eslamasabry.oc.engine.agents.install

import io.github.eslamasabry.oc.engine.agents.AgentId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

/**
 * Whether the phone's agents should move to the versions this app pins, by themselves (the agents' counterpart of
 * `OpenCodeAutoUpdateRule`). Only when an installed agent, or the shared agent helper, is behind the pin, and never
 * under running work, over another setup job, on a half-finished switch or install, or on metered data (an agent
 * download is hundreds of megabytes). One automatic try per app run and pinned set; a failure is left to the manual
 * install on the Agents page.
 */
public object AgentAutoUpdateRule {
    /**
     * The installed agent whose install brings the phone to the pins, or null when nothing is behind.
     *
     * [installed] are the installed agents in catalog order, [behind] the installed agents whose copy is not their
     * pinned version, [helperBehind] whether the agent helper (Paseo) is not the pinned one. An agent that is behind is
     * updated first; when only the helper is behind, any installed agent's install brings it (the install job also
     * checks the helper and Node.js and skips what is in place).
     */
    public fun target(installed: List<AgentId>, behind: Set<AgentId>, helperBehind: Boolean): AgentId? =
        installed.firstOrNull { it in behind } ?: if (helperBehind) installed.firstOrNull() else null

    @Suppress("LongParameterList")
    public fun shouldUpdate(
        target: AgentId?,
        busy: Boolean,
        setupRunning: Boolean,
        switchPending: Boolean,
        installProblem: Boolean,
        metered: Boolean,
        attemptedThisRun: Boolean,
    ): Boolean {
        if (target == null) return false
        if (attemptedThisRun || busy || setupRunning || metered) return false
        if (switchPending || installProblem) return false
        return true
    }
}

/** What the auto-updater reads about the phone's agents. */
public interface AgentUpdateHost {
    /** Reads the agents again (installed versions); it may be older than the work that just ended. */
    public suspend fun refresh()

    /**
     * No kept answer can be trusted yet: the first look after an app update, or nothing was ever read. The updater
     * then asks for ONE [read] (while the phone is idle) before it decides, so it never depends on the person having
     * opened the Agents page.
     */
    public val readDue: Boolean

    /**
     * One read of the installed agents' versions (and whether the helper's install is in place at its pin), the cheap
     * read the Agents page uses, without starting the helper. Records that it ran, so [readDue] turns false.
     */
    public suspend fun read()

    /** The installed agents in catalog order; empty when none is installed or nothing is known yet. */
    public val installed: List<AgentId>

    /**
     * The installed agents whose copy is not their pin, from the last read: another version, or (issue 147) the same
     * version installed from another artifact or running another program ([AgentInstallRecord]).
     */
    public val behind: Set<AgentId>

    /**
     * The part of [behind] that is the same version installed again ([AgentInstallRecord]): its install replaces the
     * folder a running copy of that agent works from, so it waits until the agents' work is idle, like a helper update.
     */
    public val reinstall: Set<AgentId> get() = emptySet()

    /** The helper runs, or is installed, at a version other than the pin. False when unknown. */
    public val helperBehind: Boolean

    /**
     * An agent whose automatic update was started in an earlier run of the app and never finished (the app was
     * stopped or crashed mid-install): its install is run again, once per run. Null when none.
     */
    public val resume: AgentId? get() = null

    /** A line naming the pinned versions (agents and helper): one automatic try is spent per value. */
    public val pinKey: String

    public val switchPending: Boolean
    public val installProblem: Boolean

    /** The active network charges for data (or cannot be read: then treated as metered). */
    public val metered: Boolean
}

/** The setup engine's face the auto-updater uses: the same job as the Agents page's Install. */
public interface AgentUpdateJobs {
    public val running: Boolean
    public val failed: Boolean

    /**
     * Runs the install of [agent] (which also brings the helper and Node.js to their pins). True when the job started;
     * false when it was refused before it started (work running, not enough room, another job), which leaves the
     * automatic try unspent.
     */
    public suspend fun update(agent: AgentId): Boolean
}

/** What the automatic agent update last decided, for the Agents page's status line and the problem report. */
public enum class AgentUpdateStep {
    /** Nothing looked yet this run. */
    NotLooked,

    /** The one read of the installed agents' versions runs. */
    Reading,

    /** The read could not finish; it is not tried again until the app opens again. */
    ReadFailed,

    /** Every installed agent and the helper are on their pins (or nothing is installed). */
    NothingBehind,

    /** An agent is behind but the agents' own work is running (the binding bounds a stale reading of it). */
    WaitingAgentsBusy,

    /** A setup or install job runs. */
    WaitingSetup,

    /** A phone server move is half finished. */
    WaitingSwitch,

    /** The network charges for data. */
    WaitingMetered,

    /** The install of [AgentUpdateDecision.agent] was started. */
    Updating,

    /** The install of [AgentUpdateDecision.agent] ended; the line stays until the next decision. */
    Updated,

    /** The install was refused (no room, not installable here): the manual Install says why. */
    Refused,

    /**
     * The install of [AgentUpdateDecision.agent] was started and ended without finishing (or ran past its bound): the
     * wait for it is over and Home says so with Try again. Kept until the next decision.
     */
    Failed,
}

public data class AgentUpdateDecision(val step: AgentUpdateStep, val agent: AgentId? = null) {
    /** One plain line for the redacted problem report; no names of people, paths or addresses. */
    public fun line(): String =
        when (step) {
            AgentUpdateStep.NotLooked -> "agent update: not looked yet"
            AgentUpdateStep.Reading -> "agent update: reading installed agents"
            AgentUpdateStep.ReadFailed -> "agent update: read failed, not retried this run"
            AgentUpdateStep.NothingBehind -> "agent update: nothing behind"
            AgentUpdateStep.WaitingAgentsBusy -> "agent update: waiting, agents busy"
            AgentUpdateStep.WaitingSetup -> "agent update: waiting, a setup job runs"
            AgentUpdateStep.WaitingSwitch -> "agent update: waiting, a server move is pending"
            AgentUpdateStep.WaitingMetered -> "agent update: waiting, network is metered"
            AgentUpdateStep.Updating -> "agent update: started ${agent?.raw ?: "an agent"}"
            AgentUpdateStep.Updated -> "agent update: finished ${agent?.raw ?: "an agent"}"
            AgentUpdateStep.Refused -> "agent update: install refused for ${agent?.raw ?: "an agent"}"
            AgentUpdateStep.Failed -> "agent update: did not finish for ${agent?.raw ?: "an agent"}"
        }
}

/**
 * Moves the installed agents and the helper to this app's pins by itself, once per agent and pinned set. The binding
 * calls [changed] at start and whenever the host's state or the work changes; a call while one is deciding returns at
 * once. The job it starts is the Agents page's own install, so the row, the tile and the composer show it as they
 * show any install. [decision] says what it is doing or waiting for.
 *
 * The one read of the installed versions needs no idle phone (it only asks each agent its version); only the install
 * waits for [workBusy] to be false.
 */
public class AgentAutoUpdater(
    private val host: AgentUpdateHost,
    private val jobs: AgentUpdateJobs,
    /** One line per change of what the updater sees: which condition holds it back (no personal data). */
    private val log: ((String) -> Unit)? = null,
    /** A monotonic clock in milliseconds, for the spacing of a retry; the default never spaces one. */
    private val nowMs: () -> Long = { 0L },
    private val workBusy: () -> Boolean,
) {
    private var lastLine: String? = null
    private val attempted = mutableSetOf<String>()
    private val failures = mutableMapOf<String, Int>()
    private val retryNotBefore = mutableMapOf<String, Long>()
    private val checking = Mutex()
    private var readTries = 0
    private var readRetryAt = 0L
    private var busyUntil = 0L
    private var busyStreak = 0
    private var meteredAllowed = false

    /**
     * The person chose "Download now" on mobile data: the next install try runs on this network, once (spent when the
     * install starts; a try refused before it started keeps it).
     */
    public fun allowMeteredOnce() {
        meteredAllowed = true
    }

    private fun metered(): Boolean = host.metered && !meteredAllowed
    private val state = MutableStateFlow(AgentUpdateDecision(AgentUpdateStep.NotLooked))

    public val decision: StateFlow<AgentUpdateDecision> = state.asStateFlow()

    public suspend fun changed() {
        if (!checking.tryLock()) return
        try {
            // Refused for busy (work running, or another install job holds the one gate): the try is kept, and the
            // next look is after the backoff, never at once.
            if (nowMs() < busyUntil) return
            val readAgain = readTries < MAX_READS && nowMs().let { it > 0L && it >= readRetryAt }
            if (host.readDue && (readTries == 0 || readAgain)) {
                // The read starts a process per installed agent: not under a setup job or a half-finished move; one
                // that cannot finish is tried again a few times, spaced (never silent: Home tells the hold).
                when {
                    jobs.running -> return hold(AgentUpdateStep.WaitingSetup)
                    host.switchPending -> return hold(AgentUpdateStep.WaitingSwitch)
                }
                readTries++
                publish(AgentUpdateDecision(AgentUpdateStep.Reading))
                host.read()
                if (host.readDue) {
                    readRetryAt = nowMs() + READ_RETRY_MS
                    publish(AgentUpdateDecision(AgentUpdateStep.ReadFailed))
                    return
                }
            }
            val key = host.pinKey
            if (decide(key, explain = true) == null) return
            host.refresh()
            val target = decide(key) ?: return
            // A try refused before the job started is not spent: the next look tries again. A started one is spent for
            // that agent and for the helper (its install brings the helper too); another agent behind is next.
            if (jobs.update(target)) {
                attempted += "$key/${target.raw}"
                attempted += "$key/$HELPER"
                meteredAllowed = false
                busyStreak = 0
                publish(AgentUpdateDecision(AgentUpdateStep.Updating, target))
            } else {
                busyStreak++
                busyUntil = busyDeadline(nowMs(), busyStreak)
                note("refused before it started (busy); waiting, the try is kept")
                publish(AgentUpdateDecision(AgentUpdateStep.Refused, target))
            }
        } finally {
            checking.unlock()
        }
    }

    /** The binding saw the install of [agent] end (Done): the line says it was updated until the next decision. */
    public fun finished(agent: AgentId) {
        publish(AgentUpdateDecision(AgentUpdateStep.Updated, agent))
    }

    /**
     * The install of [agent] moved to [step] outside the updater's own looks.
     * [AgentUpdateStep.Failed]: it ended without finishing, ran past its bound, was refused or never started. A failed
     * try never marks the agent current: it is given back (the next look sees it behind again) up to [MAX_RETRIES]
     * more times, spaced, then it is left to the person's Try again.
     * [AgentUpdateStep.Updating]: the person's Try again started it, so every earlier failure is forgotten and the
     * other agents that are behind are tried in their turn.
     */
    public fun settle(agent: AgentId, step: AgentUpdateStep) {
        val key = host.pinKey
        val mine = "$key/${agent.raw}"
        val helper = "$key/$HELPER"
        if (step == AgentUpdateStep.Failed) {
            val n = (failures[mine] ?: 0) + 1
            failures[mine] = n
            if (n <= MAX_RETRIES) {
                attempted -= mine
                attempted -= helper
                retryNotBefore[mine] = nowMs() + RETRY_SPACING_MS
            }
        } else if (step == AgentUpdateStep.Updating) {
            attempted.clear()
            failures.clear()
            retryNotBefore.clear()
            attempted += mine
            attempted += helper
        }
        publish(AgentUpdateDecision(step, agent))
    }

    private fun hold(step: AgentUpdateStep) {
        note("a read of the installed agents is due; waiting: $step")
        publish(AgentUpdateDecision(step))
    }

    private fun publish(decision: AgentUpdateDecision) {
        if (state.value == decision) return
        state.value = decision
        note(decision.line())
    }

    private companion object {
        const val HELPER = "helper"
        const val MAX_RETRIES = 2
        const val RETRY_SPACING_MS = 600_000L
    }

    private fun note(line: String) {
        if (line == lastLine) return
        lastLine = line
        log?.invoke(line)
    }

    /** The agent to install now, or null while something holds the update back (or nothing is behind). */
    private fun decide(key: String, explain: Boolean = false): AgentId? {
        // One automatic try per agent (and per helper) and pinned set: an agent that was tried is not asked again, and
        // does not keep another agent that is behind from being updated.
        val now = nowMs()
        val behind =
            host.behind.filterTo(mutableSetOf()) {
                "$key/${it.raw}" !in attempted && now >= (retryNotBefore["$key/${it.raw}"] ?: 0L)
            }
        val helperBehind = host.helperBehind && "$key/$HELPER" !in attempted
        // An update the app was stopped in the middle of runs again first (the install skips what is in place).
        val resume = host.resume?.takeIf { "$key/${it.raw}" !in attempted }
        val target = resume ?: AgentAutoUpdateRule.target(host.installed, behind, helperBehind)
        // Only an install that replaces or restarts the helper waits for the agents to be idle. An agent-only update
        // (the helper already on its pin) replaces that agent's own files and link; another agent's conversation keeps
        // running on its own copy, so it never waits for it.
        // A reinstall of the same version replaces the folder that agent's running copy uses: it waits as well.
        val busy = target != null && (host.helperBehind || target in host.reinstall) && workBusy()
        val running = jobs.running
        val problem = host.installProblem || jobs.failed
        val go =
            AgentAutoUpdateRule.shouldUpdate(
                target = target,
                busy = busy,
                setupRunning = running,
                switchPending = host.switchPending,
                installProblem = problem,
                metered = metered(),
                attemptedThisRun = false,
            )
        if (explain) {
            publishDecide(target, busy, running, problem)
            note(
                "behind=${behind.size} helperBehind=$helperBehind busy=$busy setupRunning=$running " +
                    "switchPending=${host.switchPending} installProblem=$problem metered=${host.metered} " +
                    "tried=${attempted.size} -> ${if (go) "update" else "hold"}",
            )
        }
        return if (go) target else null
    }

    private fun publishDecide(target: AgentId?, busy: Boolean, running: Boolean, problem: Boolean) {
        val step =
            when {
                // The install it started runs on: keep saying so until that job ends.
                target == null ->
                    when {
                        state.value.step == AgentUpdateStep.Updating && running -> return
                        state.value.step == AgentUpdateStep.Updated -> return
                        state.value.step == AgentUpdateStep.Failed -> return
                        else -> AgentUpdateStep.NothingBehind
                    }
                running -> AgentUpdateStep.WaitingSetup
                host.switchPending -> AgentUpdateStep.WaitingSwitch
                problem -> AgentUpdateStep.WaitingSetup
                metered() -> AgentUpdateStep.WaitingMetered
                busy -> AgentUpdateStep.WaitingAgentsBusy
                else -> return
            }
        publish(AgentUpdateDecision(step, target))
    }
}

private const val MAX_READS = 3
private const val READ_RETRY_MS = 300_000L
private const val BUSY_BACKOFF_MS = 30_000L
private const val BUSY_BACKOFF_MAX_MS = 120_000L
private const val BUSY_DOUBLINGS = 3

/** When a busy-refused try looks again: 30 s, doubling, at most 2 min. No clock (0) means no backoff. */
private fun busyDeadline(now: Long, streak: Int): Long =
    if (now == 0L) {
        0L
    } else {
        now + minOf(BUSY_BACKOFF_MS shl (streak - 1).coerceAtMost(BUSY_DOUBLINGS), BUSY_BACKOFF_MAX_MS)
    }
