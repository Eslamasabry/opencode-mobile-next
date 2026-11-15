package io.github.eslamasabry.oc.feature.agents.hub

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.agents.providers.OwnProviderProblem
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.flow.StateFlow

/**
 * "Use your own provider" for a built-in phone agent (issue 147): an endpoint, a key and an optional model, used by the
 * agent instead of a sign-in to its plan. The app binds it where the phone's Linux exists; elsewhere none is bound and
 * the page offers nothing. The key is never part of any state here: it goes from the sheet's field to [save] and from
 * there only to the secure store and the agent's own settings in the phone's Linux.
 */
public interface PhoneOwnProviders {
    /** Saved providers per agent id (no key). */
    public val state: StateFlow<ImmutableMap<String, OwnProviderView>>

    /** Agents that take an own provider (ids). */
    public val supported: Set<String>

    /** Checks, saves and hands the provider to the agent. Refused values come back as [OwnProviderSave.Refused]. */
    public suspend fun save(agent: String, endpoint: String, key: String, model: String): Outcome<OwnProviderSave>

    /** Forgets the agent's own provider; it signs in to its plan again after this. */
    public suspend fun remove(agent: String): Outcome<Unit>
}

public data class OwnProviderView(val endpoint: String, val model: String?)

public sealed interface OwnProviderSave {
    public data object Saved : OwnProviderSave

    public data class Refused(val problem: OwnProviderProblem) : OwnProviderSave
}

@dev.zacsweers.metro.ContributesTo(dev.zacsweers.metro.AppScope::class)
public interface PhoneOwnProviderBindings {
    @dev.zacsweers.metro.Multibinds(allowEmpty = true)
    public val phoneOwnProviders: Set<PhoneOwnProviders>
}
