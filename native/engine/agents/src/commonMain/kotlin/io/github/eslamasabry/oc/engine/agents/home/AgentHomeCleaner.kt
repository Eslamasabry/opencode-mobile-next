package io.github.eslamasabry.oc.engine.agents.home

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.AgentKeys
import io.github.eslamasabry.oc.engine.agents.OwnerId
import io.github.eslamasabry.oc.engine.agents.host.AgentHost
import io.github.eslamasabry.oc.engine.agents.host.AgentHostSecret
import io.github.eslamasabry.oc.engine.agents.ownership.AgentOwnership
import io.github.eslamasabry.oc.engine.agents.store.AgentPrefs

/** What the profile deletion must do with the agents' data of one profile. */
public sealed interface AgentHomeCleanup {
    /** Another phone profile still maps to the owner: the home stays; the sweep must keep [retainedKeys]. */
    public data class Retained(val owner: OwnerId, val retainedKeys: Set<String>) : AgentHomeCleanup

    /** The last alias left: home drained and erased, then the password and the owner's keys removed. */
    public data class Erased(val owner: OwnerId, val removedKeys: Set<String>) : AgentHomeCleanup
}

/**
 * `ProfileResourceCleaner` for the agents (plan 17 §1.5, STATE-DATA-05, B-STATE-056). Runs in `ProfileDeletion`
 * BEFORE the secret sweep. The drain comes first; a failed drain keeps the profile, the keys and the secure slot so the
 * deletion can be retried.
 */
public class AgentHomeCleaner(
    private val prefs: AgentPrefs,
    private val host: AgentHost,
    private val secret: AgentHostSecret,
    private val ownership: AgentOwnership = AgentOwnership(prefs),
) {
    public suspend fun clean(profile: ProfileId, phoneProfiles: List<ProfileId>): Outcome<AgentHomeCleanup> {
        val owner = ownership.ownerOf(profile)
        if (ownership.retainedAfterRemoving(profile, phoneProfiles)) {
            return AgentHomeCleanup.Retained(owner, ownership.retainedPreferenceKeys(profile, phoneProfiles)).ok()
        }
        when (val drained = host.deleteHome(owner)) {
            is Outcome.Failed -> return drained
            is Outcome.Ok -> Unit
        }
        when (val deleted = secret.delete(owner)) {
            is Outcome.Failed -> return deleted
            is Outcome.Ok -> Unit
        }
        val keys = ownership.ownerPreferenceKeys(owner) +
            prefs.keys(AgentKeys.PHONE_AGENT_OWNER_PREFIX).filter { prefs.string(it) == owner.raw }
        if (keys.isNotEmpty()) {
            when (val removed = prefs.commit { keys.forEach(::remove) }) {
                is Outcome.Failed -> return removed
                is Outcome.Ok -> Unit
            }
        }
        return AgentHomeCleanup.Erased(owner, keys).ok()
    }
}

/**
 * `AgentsSignInResetParticipant` (17 §1.12): stops each owner's helper (not guarded: the person asked), deletes every
 * `oc.agentHostSecret.*` including orphans, and keeps agent homes, logins and installed agents.
 */
public class AgentsSignInReset(private val host: AgentHost, private val secret: AgentHostSecret) {
    public suspend fun reset(owners: Collection<OwnerId>): Outcome<Set<String>> {
        for (owner in owners) {
            when (val stopped = host.stop(owner, guardedForUpdate = false)) {
                is Outcome.Failed -> return stopped
                is Outcome.Ok -> Unit
            }
        }
        return secret.deleteAll()
    }
}
