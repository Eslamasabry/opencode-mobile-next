package io.github.eslamasabry.oc.engine.agents.host

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.engine.agents.OwnerId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentPins

/**
 * Starting the helper the one way (AGENTS-BACKENDS-10/11): the password from [AgentHostSecret] (stdin only, never in
 * the config, argv, env or a log), the rendered [PaseoConfig] and the pinned Paseo version. [AgentHost.ensureRunning]
 * returns only after `GET /api/health` answers, so a returned [HelperRunning] means ready (B-AGENT-125).
 */
public class AgentHelper(
    private val host: AgentHost,
    private val secret: AgentHostSecret,
    private val pins: AgentPins = AgentPins.builtIn,
    private val catalog: AgentCatalog = AgentCatalog.builtIn,
    private val port: Int = PaseoConfig.STABLE_PORT,
    /** Closes and reopens the transient `"<owner>.agents"` graph so no socket keeps an old credential (04 §12.1). */
    private val reopenAgentsGraph: suspend (OwnerId) -> Unit = {},
) {
    public suspend fun ensureRunning(
        owner: OwnerId,
        restartIfVersionChanged: Boolean = false,
        idleResume: IdleResume? = null,
    ): Outcome<HelperRunning> {
        if (!host.available) return AgentHostProblem.AgentUnavailable.asFailure().failed()
        val password = when (val s = secret.get(owner)) {
            is Outcome.Failed -> return s
            is Outcome.Ok -> s.value
        }
        return host.ensureRunning(owner, start(password, restartIfVersionChanged, idleResume = idleResume))
    }

    /**
     * Rotation (17 §1.12): stop guarded for update (refuses while a turn or a permission reply runs; the caller retries
     * when idle), commit a new value, start with it, then reopen the agents graph. Never on a timer.
     */
    public suspend fun rotate(owner: OwnerId): Outcome<HelperRunning> {
        when (val stopped = host.stop(owner, guardedForUpdate = true)) {
            is Outcome.Failed -> return stopped
            is Outcome.Ok -> Unit
        }
        val password = when (val s = secret.replace(owner)) {
            is Outcome.Failed -> return s
            is Outcome.Ok -> s.value
        }
        val running = host.ensureRunning(owner, start(password, restartIfVersionChanged = true))
        if (running is Outcome.Ok) reopenAgentsGraph(owner)
        return running
    }

    internal fun start(
        password: Secret,
        restartIfVersionChanged: Boolean,
        forceRestart: Boolean = false,
        idleResume: IdleResume? = null,
    ): HelperStart =
        HelperStart(
            password = password,
            config = PaseoConfig.render(catalog, port),
            expectedHelperVersion = pins.paseo.version,
            restartIfVersionChanged = restartIfVersionChanged,
            forceRestart = forceRestart,
            idleResume = idleResume,
        )
}
