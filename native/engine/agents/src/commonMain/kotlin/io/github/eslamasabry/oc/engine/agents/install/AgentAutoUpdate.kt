package io.github.eslamasabry.oc.engine.agents.install

import io.github.eslamasabry.oc.engine.agents.AgentId
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

    /** The installed agents in catalog order; empty when none is installed or nothing is known yet. */
    public val installed: List<AgentId>

    /** The installed agents whose copy is not their pinned version, from the last read. */
    public val behind: Set<AgentId>

    /** The helper runs, or is installed, at a version other than the pin. False when unknown. */
    public val helperBehind: Boolean

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

/**
 * Moves the installed agents and the helper to this app's pins by itself, once per app run and pinned set. The
 * binding calls [changed] at start and whenever the host's state or the work changes; a call while one is deciding
 * returns at once. The job it starts is the Agents page's own install, so the row, the tile and the composer show it
 * as they show any install.
 */
public class AgentAutoUpdater(
    private val host: AgentUpdateHost,
    private val jobs: AgentUpdateJobs,
    /** One line per change of what the updater sees: which condition holds it back (no personal data). */
    private val log: ((String) -> Unit)? = null,
    private val workBusy: () -> Boolean,
) {
    private var lastLine: String? = null
    private val attempted = mutableSetOf<String>()
    private val checking = Mutex()

    public suspend fun changed() {
        if (!checking.tryLock()) return
        try {
            val key = host.pinKey
            if (decide(key, explain = true) == null) return
            host.refresh()
            val target = decide(key) ?: return
            // A try refused before the job started is not spent: the next look tries again.
            if (jobs.update(target)) attempted += key else note("refused before it started; will try again")
        } finally {
            checking.unlock()
        }
    }

    private fun note(line: String) {
        if (line == lastLine) return
        lastLine = line
        log?.invoke(line)
    }

    /** The agent to install now, or null while something holds the update back. */
    private fun decide(key: String, explain: Boolean = false): AgentId? {
        val target = AgentAutoUpdateRule.target(host.installed, host.behind, host.helperBehind)
        val busy = workBusy()
        val running = jobs.running
        val problem = host.installProblem || jobs.failed
        val tried = key in attempted
        val go =
            AgentAutoUpdateRule.shouldUpdate(
                target = target,
                busy = busy,
                setupRunning = running,
                switchPending = host.switchPending,
                installProblem = problem,
                metered = host.metered,
                attemptedThisRun = tried,
            )
        if (explain) {
            note(
                "behind=${host.behind.size} helperBehind=${host.helperBehind} busy=$busy setupRunning=$running " +
                    "switchPending=${host.switchPending} installProblem=$problem metered=${host.metered} " +
                    "attempted=$tried -> ${if (go) "update" else "hold"}",
            )
        }
        return if (go) target else null
    }
}
