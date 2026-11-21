package io.github.eslamasabry.oc.engine.agents.install

import io.github.eslamasabry.oc.engine.agents.AgentId

/**
 * The yes on a sheet that said mobile data and showed the size: consent for EXACTLY the updates that sheet named
 * (agent and version), spent when that agent's install starts. Nothing else gains it: a later, different update asks
 * again.
 */
public class MobileConsent(private val offer: () -> AgentUpdateOffer?) {
    @kotlin.concurrent.Volatile private var yes = emptyMap<AgentId, String>()

    /** What the last answered sheet named, id to version; empty when it did not say mobile data. */
    internal fun record(named: Map<AgentId, String>) {
        yes = named
    }

    /** The person's yes on Home's line for exactly these (agent, version) pairs; earlier yes stay. */
    public fun grant(named: Map<AgentId, String>) {
    }

    /** True while [id]'s update, as the offer lists it now, is one the sheet named and it has not started yet. */
    public fun covers(id: AgentId): Boolean {
        val row = offer()?.items?.firstOrNull { it.id == id } ?: return false
        return row.choice == AgentUpdateChoice.Update && yes[id] == row.toVersion
    }

    /** [id]'s install started: its yes is spent. */
    public fun spend(id: AgentId) {
        yes = yes - id
    }
}
