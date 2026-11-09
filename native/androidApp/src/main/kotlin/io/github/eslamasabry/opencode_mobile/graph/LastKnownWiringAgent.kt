package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.data.cache.CacheShelf
import io.github.eslamasabry.oc.core.data.cache.LastKnown
import io.github.eslamasabry.oc.feature.agenttools.presentation.AgentToolsLastKnown
import io.github.eslamasabry.oc.feature.agenttools.presentation.KeptAgentTools
import io.github.eslamasabry.oc.feature.chat.agentfeatures.presentation.AgentFeaturesLastKnown
import io.github.eslamasabry.oc.feature.chat.agentfeatures.presentation.KeptAgentFeatures
import io.github.eslamasabry.oc.feature.chat.context.ActiveContextLastKnown
import io.github.eslamasabry.oc.feature.chat.context.KeptActiveContext
import io.github.eslamasabry.oc.feature.servers.account.presentation.AgentAccountLastKnown
import io.github.eslamasabry.oc.feature.servers.account.presentation.KeptAgentAccount

/*
 * Cache-first (owner rule, 2026-11-09) for the agent pages: the agent's features, its tools page, the active-context
 * inspector and the account page keep what they last showed, per profile and server (ProfileShelf), and draw it first.
 * The whole profile's part goes with the profile (LastKnownDeletion).
 */

/** The agent features list of a conversation; 20 conversations, 32 KB each at most. */
@ContributesIntoSet(AppScope::class, binding = binding<AgentFeaturesLastKnown>())
@SingleIn(AppScope::class)
@Inject
class AppAgentFeaturesLastKnown(store: LastKnown, stores: PreviewStores) : AgentFeaturesLastKnown {
    private val lists = ProfileShelf(store, stores, FEATURES)

    override fun peek(ref: ConversationRef): KeptAgentFeatures? = lists.peek(ref.profileId, ref.sessionId.raw)

    override suspend fun read(ref: ConversationRef): KeptAgentFeatures? = lists.read(ref.profileId, ref.sessionId.raw)

    override suspend fun keep(ref: ConversationRef, kept: KeptAgentFeatures) =
        lists.keep(ref.profileId, ref.sessionId.raw, kept)

    companion object {
        val FEATURES = CacheShelf("agentFeatures", KeptAgentFeatures.serializer(), maxBytes = 32 * 1024, maxEntries = 20)
    }
}

/** The active-context message list of a conversation (prose cut short, no tool output); 10 conversations, 192 KB each. */
@ContributesIntoSet(AppScope::class, binding = binding<ActiveContextLastKnown>())
@SingleIn(AppScope::class)
@Inject
class AppActiveContextLastKnown(store: LastKnown, stores: PreviewStores) : ActiveContextLastKnown {
    private val lists = ProfileShelf(store, stores, CONTEXT)

    override fun peek(ref: ConversationRef): KeptActiveContext? = lists.peek(ref.profileId, ref.sessionId.raw)

    override suspend fun read(ref: ConversationRef): KeptActiveContext? = lists.read(ref.profileId, ref.sessionId.raw)

    override suspend fun keep(ref: ConversationRef, kept: KeptActiveContext) =
        lists.keep(ref.profileId, ref.sessionId.raw, kept)

    companion object {
        val CONTEXT = CacheShelf("activeContext", KeptActiveContext.serializer(), maxBytes = 192 * 1024, maxEntries = 10)
    }
}

/** The Agent tools page's features and models, per profile and (optional) conversation; 20 entries, 48 KB each. */
@ContributesIntoSet(AppScope::class, binding = binding<AgentToolsLastKnown>())
@SingleIn(AppScope::class)
@Inject
class AppAgentToolsLastKnown(store: LastKnown, stores: PreviewStores) : AgentToolsLastKnown {
    private val pages = ProfileShelf(store, stores, TOOLS)

    private fun key(session: SessionId?) = session?.raw ?: "@profile"

    override fun peek(profile: ProfileId, session: SessionId?): KeptAgentTools? = pages.peek(profile, key(session))

    override suspend fun read(profile: ProfileId, session: SessionId?): KeptAgentTools? =
        pages.read(profile, key(session))

    override suspend fun keep(profile: ProfileId, session: SessionId?, kept: KeptAgentTools) =
        pages.keep(profile, key(session), kept)

    companion object {
        val TOOLS = CacheShelf("agentTools", KeptAgentTools.serializer(), maxBytes = 48 * 1024, maxEntries = 20)
    }
}

/** The agent account page (sign-in kind, plan, allowance, usage) per profile and server; 16 KB. */
@ContributesIntoSet(AppScope::class, binding = binding<AgentAccountLastKnown>())
@SingleIn(AppScope::class)
@Inject
class AppAgentAccountLastKnown(store: LastKnown, stores: PreviewStores) : AgentAccountLastKnown {
    private val accounts = ProfileShelf(store, stores, ACCOUNT)

    override fun peek(profile: ProfileId): KeptAgentAccount? = accounts.peek(profile)

    override suspend fun read(profile: ProfileId): KeptAgentAccount? = accounts.read(profile)

    override suspend fun keep(profile: ProfileId, kept: KeptAgentAccount) = accounts.keep(profile, value = kept)

    companion object {
        val ACCOUNT = CacheShelf("agentAccount", KeptAgentAccount.serializer(), maxBytes = 16 * 1024, maxEntries = 1)
    }
}
