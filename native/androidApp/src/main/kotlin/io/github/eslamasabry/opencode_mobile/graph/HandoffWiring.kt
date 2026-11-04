package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.scope.*
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.agents.handoff.*
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.*
import io.github.eslamasabry.oc.feature.chat.header.world.ChatHandoffs
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * One app-scoped owner for linked conversation metadata, with immediate deletion admission fencing.
 */
@SingleIn(AppScope::class)
@Inject
class HandoffLinkOwner(
    private val stores: PreviewStores,
    redactor: Redactor,
    private val scope: AppCoroutineScope,
) {
    val links = HandoffLinks(KvAgentPrefs(stores.prefs, stores.lanes.app), redactor)
    private val lock = Any()
    @Volatile private var closed: Set<ProfileId> = emptySet()

    fun admits(profile: ProfileId): Boolean =
        profile !in closed && stores.profiles.profile(profile) != null

    fun stop(profile: ProfileId) {
        synchronized(lock) { closed = closed + profile }
    }

    suspend fun drain(profile: ProfileId): Outcome<Unit> {
        links.closeProfile(profile.raw)
        return Unit.ok()
    }

    fun reopen(profile: ProfileId) {
        scope.launch {
            links.cancelDeletion(profile.raw)
            if (stores.profiles.profile(profile) != null)
                synchronized(lock) { closed = closed - profile }
        }
    }
}

/**
 * A transfer starts another agent on this exact live remote project, never by matching an address
 * or folder.
 */
@ContributesIntoSet(AppScope::class, binding = binding<ChatHandoffs>())
@Inject
class AppChatHandoffs(
    private val registry: ConnectionRegistry,
    private val owner: HandoffLinkOwner,
    private val redactor: Redactor,
) : ChatHandoffs {
    override fun open(
        ref: ConversationRef,
        model: ModelRef,
        label: String,
        active: () -> Boolean,
    ): HandoffEngine? {
        val graph = registry.graphFor(ref) ?: return null
        val gateway = (graph as ChatGraphAccessors).chatGateway
        if (!gateway.capabilities.value.agentHandoffFiles || !owner.admits(ref.profileId))
            return null
        val directory = gateway.location.value.directory ?: return null
        val current = {
            active() && owner.admits(ref.profileId) && registry.graphFor(ref) === graph
        }
        val adapter = ConnectedHandoffGateway(gateway, current, redactor)
        if (adapter.isClosed) return null
        // Both sides are this identical live gateway, so the app can attest to a shared filesystem
        // without
        // inferring identity from URLs, matching path strings, server prose or another saved
        // connection.
        val scope =
            HandoffScope(
                ref.profileId.raw,
                directory,
                gateway.location.value.workspace,
                UUID.randomUUID().toString(),
            )
        return HandoffEngine(
            adapter,
            scope,
            ref.sessionId.raw,
            HandoffTarget(adapter, scope, HandoffModelRef(model.providerId, model.modelId), label),
            owner.links,
            current,
            { UUID.randomUUID().toString() },
            redactor,
        )
    }
}

@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
@Inject
class HandoffProfileCloser(private val ownerStore: HandoffLinkOwner) : ProfileCloser {
    override val owner: String = ":engine:agents"

    override fun stopAdmission(profile: ProfileId) = ownerStore.stop(profile)

    override suspend fun drain(profile: ProfileId): Outcome<Unit> = ownerStore.drain(profile)

    override fun reopen(profile: ProfileId) = ownerStore.reopen(profile)
}

@ContributesIntoSet(AppScope::class, binding = binding<ProfileResourceCleaner>())
@Inject
class HandoffProfileCleaner(private val linksOwner: HandoffLinkOwner) : ProfileResourceCleaner {
    override val owner: String = ":engine:agents"
    override val order: Int = DeletionStep.DRAFTS
    override val guardsSweep: Boolean = true

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> =
        when (val removed = linksOwner.links.removeProfile(profileId.raw)) {
            is Outcome.Failed -> removed
            is Outcome.Ok ->
                when (removed.value) {
                    HandoffLinkSave.Saved -> 0.ok()
                    is HandoffLinkSave.Refused -> Failure.StorageRefused().failed()
                }
        }
}

object HandoffKeys {
    fun register(scope: ProfileScope) =
        scope.register(
            KeyShape(
                HandoffLinks.PREFIX,
                KeyScope.Profile,
                PrefType.Str,
                ":engine:agents",
                "Linked conversation metadata, never transcript content",
            )
        )
}
