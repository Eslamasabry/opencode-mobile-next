package io.github.eslamasabry.oc.feature.chat.relations.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.engine.agents.resume.SessionResumeCli
import io.github.eslamasabry.oc.engine.agents.resume.SessionResumeCommand
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.settings.pins.SessionPinStore
import io.github.eslamasabry.oc.engine.requests.AnswerPhase
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.SessionRelationsRoute
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The parent and its delegated conversations, scoped to the graph and location which opened this page.
 * Reconnects refetch; graph/location replacement refuses old row actions. Opening, pinning and stopping
 * re-read both the target identity and its membership immediately before the action.
 */
@AssistedInject
public class SessionRelationsViewModel(
    @Assisted private val route: SessionRelationsRoute,
    private val registry: ConnectionRegistry,
    private val nav: AppNavigator,
    private val pins: SessionRelationsPins,
    dispatchers: AppDispatchers,
    private val clock: Clock,
) : ViewModel(), SessionRelationsActions {
    private val graph = registry.graphFor(route.ref)
    private val services = graph as? ChatGraphAccessors
    private val gateway = services?.chatGateway
    private val place = gateway?.location?.value
    private val pinScope = SessionPinStore.scope(place?.directory, place?.workspace)
    private val lane = dispatchers.serial("session-relations")
    private val operations = Mutex()
    private val mutable = MutableStateFlow(SessionRelationsUi())
    public val state: StateFlow<SessionRelationsUi> = mutable.asStateFlow()
    private var parent: Session? = null
    private var children: List<Session> = emptyList()
    private var running: Set<SessionId> = emptySet()
    private var needsYou: Set<SessionId> = emptySet()
    private var closed = false
    private var clipId = 0L
    private var refreshing = false
    private var refreshAgain = false

    init {
        viewModelScope.launch {
            registry.live.collect {
                if (!isCurrent()) stale()
            }
        }
        if (gateway != null && graph != null && services != null) {
            viewModelScope.launch {
                gateway.location.collect {
                    if (!isCurrent()) stale()
                }
            }
            viewModelScope.launch {
                services.chatRequests.engine.pending.collect { asks ->
                    needsYou = asks.filter { it.phase == AnswerPhase.Waiting || it.phase is AnswerPhase.NotAccepted }.map { it.sessionId }.toSet()
                    publish()
                }
            }
            viewModelScope.launch {
                gateway.events.project.collect { envelope ->
                    when (envelope.event) {
                        GatewayEvent.Connected,
                        is GatewayEvent.SessionChanged,
                        is GatewayEvent.SessionUpserted,
                        is GatewayEvent.SessionDeleted,
                        is GatewayEvent.RunStarted,
                        is GatewayEvent.RunEnded,
                        is GatewayEvent.RunRetrying -> refresh()
                        else -> Unit
                    }
                }
            }
        }
        refresh()
    }

    private fun isCurrent(): Boolean = !closed && !state.value.stale && graph != null && registry.graphFor(route.ref) === graph && gateway?.location?.value == place

    private fun staleFailure(): Failure = Failure.Refused(RefusalReason.StaleScope)

    private fun stale() {
        mutable.update { it.copy(stale = true, loading = false, working = false, menu = null, stopping = null, resume = null, failure = staleFailure()) }
    }

    /** All calls belong to the captured connection; replacing its graph cancels that generation. */
    private suspend fun <T> command(block: suspend () -> Outcome<T>): Outcome<T> {
        val owner = graph ?: return staleFailure().failed()
        return owner.scope.async(lane) {
            operations.withLock {
                if (!isCurrent()) staleFailure().failed()
                else block().let { if (isCurrent()) it else staleFailure().failed() }
            }
        }.await()
    }

    override fun refresh() {
        if (state.value.stale) return
        if (refreshing) { refreshAgain = true; return }
        refreshing = true
        viewModelScope.launch {
            mutable.update { it.copy(loading = true, failure = null) }
            val result = command {
                val live = gateway ?: return@command staleFailure().failed()
                val ops = services?.chatOperations ?: return@command staleFailure().failed()
                if (!live.capabilities.value.subagentSessions) return@command Failure.Unavailable(FeatureId("subagentSessions")).failed()
                val ready = live.awaitActionTransport()
                if (ready is Outcome.Failed) return@command ready
                if (!isCurrent()) return@command staleFailure().failed()
                val current = live.session(route.ref.sessionId)
                if (current is Outcome.Failed) return@command current
                val selected = (current as Outcome.Ok).value
                val parentId = selected.parentId ?: selected.id
                val root = if (parentId == selected.id) current else live.session(parentId)
                if (root is Outcome.Failed) return@command root
                if (!isCurrent()) return@command staleFailure().failed()
                val listed = ops.sessionChildren(parentId)
                if (listed is Outcome.Failed) return@command listed
                val statuses = live.sessionStatuses()
                if (statuses is Outcome.Failed) return@command statuses
                if (!isCurrent()) return@command staleFailure().failed()
                val rootValue = (root as Outcome.Ok).value
                val listedValue = (listed as Outcome.Ok).value
                if (rootValue.id != parentId || listedValue.any { it.parentId != parentId }) return@command Failure.UnreadableReply().failed()
                parent = rootValue
                children = listedValue.distinctBy { it.id }
                running = (statuses as Outcome.Ok).value.filterValues { it == SessionStatus.Busy || it == SessionStatus.Retry }.keys
                Outcome.Ok(Unit)
            }
            when (result) {
                is Outcome.Ok -> { mutable.update { it.copy(loading = false, failure = null) }; publish() }
                is Outcome.Failed -> mutable.update { it.copy(loading = false, failure = result.failure) }
            }
            refreshing = false
            if (refreshAgain) { refreshAgain = false; refresh() }
        }
    }

    private fun row(session: Session, isParent: Boolean): RelationRow = RelationRow(
        session.id,
        RelationTitles.present(session.title),
        isParent,
        session.id == route.ref.sessionId,
        when {
            session.id in needsYou -> RelationStatus.NeedsYou
            session.id in running -> RelationStatus.Working
            isParent -> RelationStatus.Idle
            else -> RelationStatus.Done
        },
        session.time?.createdMs,
        pins.isPinned(route.ref.profileId, pinScope, session.id.raw),
        session.time?.createdMs?.let { ((clock.now().toEpochMilliseconds() - it) / 60_000).coerceAtLeast(0) },
    )

    private fun publish() {
        if (!isCurrent() || state.value.stale) return
        val childRows = children.map { row(it, false) }.sortedWith(compareBy<RelationRow> {
            when (it.status) { RelationStatus.NeedsYou -> 0; RelationStatus.Working -> 1; else -> 2 }
        }.thenByDescending { it.createdMs ?: 0 })
        mutable.update {
            it.copy(parent = parent?.let { p -> row(p, true) }, children = childRows.toImmutableList(),
                canPin = pins.available(route.ref.profileId),
                canResume = gateway?.capabilities?.value?.cliSessionResume == true)
        }
    }

    private fun listed(id: SessionId): Session? = parent?.takeIf { it.id == id } ?: children.firstOrNull { it.id == id }

    /** The fresh server read must still name the same folder, workspace, project and family. */
    private suspend fun checked(id: SessionId): Outcome<Session> {
        val old = listed(id) ?: return staleFailure().failed()
        val live = gateway ?: return staleFailure().failed()
        if (!live.capabilities.value.subagentSessions) return Failure.Unavailable(FeatureId("subagentSessions")).failed()
        when (val ready = live.awaitActionTransport()) { is Outcome.Failed -> return ready; is Outcome.Ok -> Unit }
        if (!isCurrent()) return staleFailure().failed()
        val current = live.session(id)
        if (current is Outcome.Failed) return current
        val next = (current as Outcome.Ok).value
        if (!isCurrent() || next.id != old.id || next.directory != old.directory || next.workspaceId != old.workspaceId || next.projectId != old.projectId || next.parentId != old.parentId) return staleFailure().failed()
        val origin = live.session(route.ref.sessionId)
        if (origin is Outcome.Failed) return origin
        val owner = (origin as Outcome.Ok).value
        val expectedParent = parent?.id ?: return staleFailure().failed()
        if (!isCurrent() || (owner.parentId ?: owner.id) != expectedParent || (next.id != expectedParent && next.parentId != expectedParent)) return staleFailure().failed()
        return next.let { Outcome.Ok(it) }
    }

    private fun act(block: suspend () -> Outcome<Unit>) {
        if (state.value.working || state.value.stale) return
        mutable.update { it.copy(working = true, failure = null) }
        viewModelScope.launch {
            val result = command(block)
            mutable.update { it.copy(working = false, failure = (result as? Outcome.Failed)?.failure) }
            if (result is Outcome.Ok) publish()
        }
    }

    override fun open(id: SessionId) {
        if (id == route.ref.sessionId || state.value.working || state.value.stale) return
        mutable.update { it.copy(working = true, failure = null) }
        viewModelScope.launch {
            val result = command { checked(id) }
            mutable.update { it.copy(working = false, failure = (result as? Outcome.Failed)?.failure) }
            if (result is Outcome.Ok && isCurrent()) {
                nav.back()
                nav.openChat(ConversationRef(route.ref.profileId, id))
            }
        }
    }

    override fun showMenu(id: SessionId) {
        if (state.value.working || state.value.stale) return
        val target = (listOfNotNull(state.value.parent) + state.value.children).firstOrNull { it.id == id } ?: return
        mutable.update { it.copy(menu = target, failure = null) }
    }
    override fun dismissMenu() { if (!state.value.working) mutable.update { it.copy(menu = null) } }
    override fun togglePin() {
        val target = state.value.menu ?: return
        act {
            val current = checked(target.id)
            if (current is Outcome.Failed) return@act current
            if (!pins.available(route.ref.profileId) || !isCurrent()) return@act staleFailure().failed()
            pins.setPinned(route.ref.profileId, pinScope, target.id.raw, !target.pinned).also {
                if (it is Outcome.Ok) mutable.update { s -> s.copy(menu = null) }
            }
        }
    }
    override fun showResume() {
        val target = state.value.menu ?: return
        act {
            if (gateway?.capabilities?.value?.cliSessionResume != true) return@act Failure.Unavailable(FeatureId("cliSessionResume")).failed()
            when (val current = checked(target.id)) {
                is Outcome.Failed -> current
                is Outcome.Ok -> {
                    val s = current.value
                    val command = SessionResumeCommand.build(SessionResumeCli.OpenCode2, s.id.raw, s.directory, s.workspaceId?.raw)
                    mutable.update { it.copy(menu = null, resume = command) }
                    Outcome.Ok(Unit)
                }
            }
        }
    }
    override fun dismissResume() { mutable.update { it.copy(resume = null) } }
    override fun copyResume() { state.value.resume?.command?.let { command -> mutable.update { it.copy(clip = RelationClip(++clipId, command)) } } }
    override fun clipDone(id: Long) { mutable.update { if (it.clip?.id == id) it.copy(clip = null) else it } }
    override fun requestStop() {
        val target = state.value.menu?.takeIf { !it.parent && it.status == RelationStatus.Working } ?: return
        mutable.update { it.copy(menu = null, stopping = target, failure = null) }
    }
    override fun cancelStop() { if (!state.value.working) mutable.update { it.copy(stopping = null, failure = null) } }
    override fun confirmStop() {
        val target = state.value.stopping ?: return
        act {
            val current = checked(target.id)
            if (current is Outcome.Failed) return@act current
            if (target.parent || !isCurrent()) return@act staleFailure().failed()
            val delivery = services?.chatDelivery?.engine ?: return@act Failure.StorageRefused().failed()
            delivery.stop(ConversationRef(route.ref.profileId, target.id)).also {
                if (it is Outcome.Ok) mutable.update { s -> s.copy(stopping = null) }
            }
        }
    }
    override fun back() { nav.back() }
    override fun onCleared() { closed = true }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: SessionRelationsRoute): SessionRelationsViewModel
    }
}
