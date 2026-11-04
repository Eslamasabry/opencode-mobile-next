package io.github.eslamasabry.oc.feature.agents.hub

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Complete read-only history, transformed into a masked bounded record; no destination write or
 * prompt dispatch.
 */
public interface AgentHandoffDrafts {
    public val sources: StateFlow<ImmutableList<AgentDraftSource>>

    public suspend fun conversations(
        profileId: ProfileId
    ): Outcome<ImmutableList<AgentDraftConversation>>

    public suspend fun prepare(
        profileId: ProfileId,
        sessionId: SessionId,
    ): Outcome<AgentDraftRecord>

    /** Copies only the already masked record the person reviewed. */
    public fun copy(markdown: String): Boolean
}

public data class AgentDraftSource(val id: ProfileId, val name: String)

public data class AgentDraftConversation(val id: SessionId, val title: String)

/** In-memory review content only. Its string representation never discloses conversation text. */
public class AgentDraftRecord(
    public val markdown: String,
    public val omittedMessages: Int,
    public val redacted: Boolean,
    public val byteLength: Int,
) {
    override fun toString(): String =
        "AgentDraftRecord(bytes=$byteLength, omitted=$omittedMessages)"
}

/**
 * Desktop capability is absent until connected product runtimes are introduced; Android replaces
 * this binding.
 */
@Inject
@ContributesBinding(AppScope::class)
public class NoAgentHandoffDrafts : AgentHandoffDrafts {
    override val sources: StateFlow<ImmutableList<AgentDraftSource>> =
        MutableStateFlow(persistentListOf())

    override suspend fun conversations(
        profileId: ProfileId
    ): Outcome<ImmutableList<AgentDraftConversation>> =
        Outcome.Failed(Failure.Unavailable(FeatureId("handoffHistory")))

    override suspend fun prepare(
        profileId: ProfileId,
        sessionId: SessionId,
    ): Outcome<AgentDraftRecord> = Outcome.Failed(Failure.Unavailable(FeatureId("handoffHistory")))

    override fun copy(markdown: String): Boolean = false
}
