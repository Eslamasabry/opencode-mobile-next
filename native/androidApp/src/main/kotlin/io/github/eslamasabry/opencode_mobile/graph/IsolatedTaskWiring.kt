package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.domain.facet
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.engine.agents.worktree.IsolatedTaskLaunch
import io.github.eslamasabry.oc.engine.agents.worktree.IsolatedTaskSendResult
import io.github.eslamasabry.oc.engine.agents.worktree.IsolatedTaskTarget
import io.github.eslamasabry.oc.engine.composer.drafts.ConversationDrafts
import io.github.eslamasabry.oc.engine.composer.drafts.DraftLocation
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.delivery.SendOutcome
import io.github.eslamasabry.oc.engine.delivery.SendPhase
import io.github.eslamasabry.oc.engine.delivery.SendRequest
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.feature.projects.presentation.IsolatedTaskHost
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Captures graph and location once; every mutation checks them again. Nothing changes the shared
 * location.
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<IsolatedTaskHost>())
class AndroidIsolatedTaskHost(
    private val registry: ConnectionRegistry,
    private val stores: PreviewStores,
    private val drafts: Set<ConversationDrafts>,
    private val redactor: io.github.eslamasabry.oc.core.security.Redactor,
) : IsolatedTaskHost {
    override fun redactTechnical(value: String): io.github.eslamasabry.oc.core.common.Redacted =
        redactor.details(value)

    private fun key(profile: ProfileId): String = "oc.draft.isolated.task.${profile.raw}"

    private fun stale(): Outcome.Failed = Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))

    override suspend fun draft(profile: ProfileId): Outcome<String> {
        if (stores.profiles.profile(profile) == null) return stale()
        return when (val raw = stores.prefs.get(key(profile))) {
            null -> Outcome.Ok("")
            is PrefValue.Str -> Outcome.Ok(raw.value)
            else -> Outcome.Failed(Failure.StorageRefused())
        }
    }

    override suspend fun saveDraft(profile: ProfileId, text: String): Outcome<Unit> {
        if (stores.profiles.profile(profile) == null) return stale()
        return stores.prefs.commit(stores.lanes.forProfile(profile)) {
            if (text.isEmpty()) remove(key(profile)) else putString(key(profile), text)
        }
    }

    override suspend fun open(
        scope: CoroutineScope,
        profile: ProfileId,
        directory: String,
        model: ModelRef?,
        agent: String?,
    ): Outcome<IsolatedTaskLaunch> {
        val graph = registry.live.value[profile] ?: return stale()
        val gateway = graph.session.server
        val operations = gateway.facet<ServerOperationsGateway>() ?: return stale()
        val at = gateway.location.value
        if (!gateway.capabilities.value.worktreeCreate) return stale()
        fun current(): Boolean =
            registry.live.value[profile] === graph &&
                gateway.location.value == at &&
                stores.profiles.profile(profile) != null
        val project =
            when (val projects = operations.listProjects()) {
                is Outcome.Failed -> return projects
                is Outcome.Ok ->
                    projects.value.firstOrNull {
                        it.directory.trimEnd('/') == directory.trimEnd('/')
                    } ?: return stale()
            }
        if (!current()) return stale()
        val target =
            object : IsolatedTaskTarget {
                override val gateway = gateway
                override val operations = operations

                override fun isCurrent(): Boolean = current()

                private var putBack: SendOutcome.PutBack? = null

                override suspend fun send(
                    ref: ConversationRef,
                    task: String,
                ): IsolatedTaskSendResult {
                    if (!current()) return IsolatedTaskSendResult.NotSent(stale().failure)
                    val engine =
                        (graph as? ChatGraphAccessors)?.chatDelivery?.engine
                            ?: return IsolatedTaskSendResult.NotSent(stale().failure)
                    val id =
                        when (
                            val sent =
                                engine.send(
                                    SendRequest(
                                        ref,
                                        task,
                                        model = model?.let(::ModelSelection),
                                        agent = agent,
                                        promptOnly = true,
                                    )
                                )
                        ) {
                            is Outcome.Failed -> return IsolatedTaskSendResult.NotSent(sent.failure)
                            is Outcome.Ok -> sent.value
                        }
                    return withTimeoutOrNull(45_000) {
                        combine(engine.pending, engine.outcomes, registry.live) {
                                pending,
                                outcomes,
                                _ ->
                                val outcome = outcomes.firstOrNull {
                                    it.ref == ref &&
                                        when (it) {
                                            is SendOutcome.Accepted -> it.localId == id
                                            is SendOutcome.PutBack -> it.localId == id
                                            is SendOutcome.Unconfirmed -> it.localId == id
                                            is SendOutcome.Queued -> false
                                        }
                                }
                                when {
                                    outcome is SendOutcome.Accepted ||
                                        pending.any {
                                            it.localId == id && it.phase == SendPhase.Accepted
                                        } -> IsolatedTaskSendResult.Accepted
                                    outcome is SendOutcome.PutBack -> {
                                        putBack = outcome
                                        IsolatedTaskSendResult.NotSent(outcome.reason)
                                    }
                                    outcome is SendOutcome.Unconfirmed ->
                                        IsolatedTaskSendResult.Unconfirmed(
                                            outcome.reason
                                                ?: Failure.Refused(RefusalReason.DispatchUncertain)
                                        )
                                    !current() ->
                                        IsolatedTaskSendResult.Unconfirmed(stale().failure)
                                    else -> null
                                }
                            }
                            .first { it != null }!!
                    }
                        ?: IsolatedTaskSendResult.Unconfirmed(
                            Failure.Refused(RefusalReason.DispatchUncertain)
                        )
                }

                override suspend fun keepDraft(ref: ConversationRef, task: String): Outcome<Unit> {
                    if (stores.profiles.profile(profile) == null) return stale()
                    val owner =
                        drafts.singleOrNull() ?: return Outcome.Failed(Failure.StorageRefused())
                    val handle = owner.open(ref)
                    try {
                        val session =
                            when (val loaded = gateway.session(ref.sessionId)) {
                                is Outcome.Failed -> return loaded
                                is Outcome.Ok -> loaded.value
                            }
                        handle.bindLocation(
                            DraftLocation(session.directory, session.workspaceId?.raw)
                        )
                        val ready =
                            withTimeoutOrNull(5_000) {
                                handle.state.first { it.ready || it.failure != null }
                            } ?: return Outcome.Failed(Failure.StorageRefused())
                        if (!ready.ready || ready.text.isNotEmpty())
                            return Outcome.Failed(ready.failure ?: Failure.StorageRefused())
                        handle.changed(task)
                        val saved = handle.flush()
                        if (saved is Outcome.Ok)
                            putBack?.let {
                                (graph as? ChatGraphAccessors)
                                    ?.chatDelivery
                                    ?.engine
                                    ?.acknowledge(it.id)
                                putBack = null
                            }
                        return saved
                    } finally {
                        handle.close()
                    }
                }

                override suspend fun clearTaskDraft(task: String): Outcome<Unit> {
                    if (!current()) return stale()
                    val newKey =
                        "oc.draft.new-chat.${Base64.UrlSafe.encode(directory.encodeToByteArray()).trimEnd('=')}.${profile.raw}"
                    return stores.prefs.commit(stores.lanes.forProfile(profile)) {
                        if (stores.prefs.string(key(profile))?.trim() == task) remove(key(profile))
                        if (stores.prefs.string(newKey)?.trim() == task) remove(newKey)
                    }
                }
            }
        return Outcome.Ok(IsolatedTaskLaunch(scope, profile, project, target))
    }
}
