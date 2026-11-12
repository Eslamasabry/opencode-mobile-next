package io.github.eslamasabry.oc.feature.chat.inbox.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.InboxItemId
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.delivery.queue.QueueEviction
import io.github.eslamasabry.oc.engine.delivery.queue.QueuedPrompt
import io.github.eslamasabry.oc.core.domain.CorrelatedPromptGateway
import io.github.eslamasabry.oc.core.domain.facet
import io.github.eslamasabry.oc.core.model.InboxDelivery
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.delivery.live.InboxProjection
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

public data class ChatInboxRow(
    val id: String,
    val text: String,
    val local: Boolean,
    val dispatched: Boolean = false,
    val sending: Boolean = false,
    val delivery: InboxDelivery? = null,
) {
    override fun toString(): String =
        "ChatInboxRow(local=$local, dispatched=$dispatched, length=${text.length})"
}

public data class InboxRestore(val id: Long, val text: String) {
    override fun toString(): String = "InboxRestore(id=$id, length=${text.length})"
}

/** C3: how many queued drafts the queue dropped on its own (too old / over the limits). Counts only, never text. */
public data class QueueEvictedNotice(val tooOld: Int, val overLimit: Int) {
    public val total: Int
        get() = tooOld + overLimit
}

/** S7: drafts queued under another server, with the name to show and the server they wait on. */
public data class OtherServerDrafts(val source: ProfileId, val count: Int)

public data class ChatInboxUi(
    val rows: List<ChatInboxRow> = emptyList(),
    val canSteer: Boolean = false,
    val busy: Boolean = false,
    val failed: Boolean = false,
    val restore: InboxRestore? = null,
    /** C3: shown once, until the person dismisses it. */
    val evicted: QueueEvictedNotice? = null,
    /** S7: drafts waiting for other servers (this chat's server is the destination of a move). */
    val others: List<OtherServerDrafts> = emptyList(),
    /** S7: this chat's server name, the destination in "Move N waiting prompts to ...". */
    val serverName: String = "",
) {
    public val othersTotal: Int
        get() = others.sumOf { it.count }
}

/** Pure reads of the queue for the chat's banners (testable without a connection). */
public object QueueBanners {
    /** Oversized drafts count with the over-the-limit ones: one reason in plain words. */
    public fun evicted(eviction: QueueEviction?): QueueEvictedNotice? =
        eviction
            ?.let { QueueEvictedNotice(it.expired, it.overflowed + it.oversized) }
            ?.takeIf { it.total > 0 }

    /** Drafts that wait under servers other than [profile], grouped by server, largest first. */
    public fun others(entries: List<QueuedPrompt>, profile: ProfileId): List<OtherServerDrafts> =
        entries
            .filter { it.profileId != profile }
            .groupingBy { it.profileId }
            .eachCount()
            .map { (id, count) -> OtherServerDrafts(id, count) }
            .sortedByDescending { it.count }
}

public interface ChatInboxActions {
    public fun cancel(row: ChatInboxRow)

    public fun steer(row: ChatInboxRow)

    public fun check(row: ChatInboxRow)

    /**
     * N-48: the person's Send again on a send that may already have arrived (they were warned it can repeat). The
     * queue's own resend first; when a receipt still binds the entry to its first dispatch, a fresh copy goes out
     * under a new message id and the stuck one leaves.
     */
    public fun sendAgain(row: ChatInboxRow) {}

    /** N-48: the person's Discard on a send that may already have arrived: it leaves the queue and stops blocking. */
    public fun discard(row: ChatInboxRow) {}

    /** A device send not yet dispatched leaves the queue and returns to the composer. */
    public fun editLocal(row: ChatInboxRow)

    public fun restoreDone(id: Long)

    /** C3: the person has read (or dismissed) the "discarded N queued drafts" notice. */
    public fun evictionSeen() {}
}

/** Reads the connection's existing projection; this screen never duplicates event subscriptions. */
@AssistedInject
public class ChatInboxViewModel(
    @Assisted private val route: ChatRoute,
    private val registry: ConnectionRegistry,
) : ViewModel(), ChatInboxActions {
    private val mutable = MutableStateFlow(ChatInboxUi())
    public val state: StateFlow<ChatInboxUi> = mutable
    private var nextRestore = 0L

    init {
        viewModelScope.launch {
            // Only this profile's graph matters; a pending restore survives a reconnect.
            registry.live
                .map { it[route.ref.profileId] }
                .distinctUntilChanged()
                .collectLatest { graph ->
                val services = graph as? ChatGraphAccessors
                mutable.value =
                    ChatInboxUi(restore = mutable.value.restore, evicted = mutable.value.evicted)
                if (services == null) return@collectLatest
                val gateway = services.chatGateway
                val delivery = services.chatDelivery
                val queue = delivery.queue
                coroutineScope {
                    launch {
                        if (gateway.capabilities.value.inbox)
                            delivery.inbox.refresh(route.ref.sessionId)
                    }
                    combine(
                            delivery.inbox.items,
                            gateway.capabilities,
                            queue?.state
                                ?: MutableStateFlow(
                                    io.github.eslamasabry.oc.engine.delivery.queue.QueueState()
                                ),
                        ) { inbox, caps, local ->
                            val remote =
                                if (!caps.inbox) emptyList()
                                else
                                    inbox[route.ref.sessionId].orEmpty().map { item ->
                                        ChatInboxRow(
                                            item.id.raw,
                                            InboxProjection.promptText(item).orEmpty(),
                                            false,
                                            delivery = item.delivery,
                                        )
                                    }
                            val device =
                                local.entries
                                    .filter {
                                        it.ref == route.ref &&
                                            it.id !in queue?.onlineInFlight().orEmpty()
                                    }
                                    .map { item ->
                                        ChatInboxRow(
                                            item.id,
                                            item.text,
                                            true,
                                            item.dispatched,
                                            item.id in local.sending ||
                                                item.id in local.acceptedUnrecorded,
                                        )
                                    }
                            Derived(
                                remote + device,
                                caps.promptSteering,
                                QueueBanners.evicted(local.eviction),
                                QueueBanners.others(local.entries, route.ref.profileId),
                            )
                        }
                        .collect { derived ->
                            mutable.value =
                                mutable.value.copy(
                                    rows = derived.rows,
                                    canSteer = derived.steer,
                                    evicted = derived.evicted ?: mutable.value.evicted,
                                    others = derived.others,
                                    serverName = graph?.profile?.name.orEmpty(),
                                )
                            // C3: the queue shows an eviction once; taking it clears the engine's copy.
                            if (derived.evicted != null || queue?.state?.value?.eviction != null)
                                queue?.takeEviction()
                        }
                }
            }
        }
    }

    private class Derived(
        val rows: List<ChatInboxRow>,
        val steer: Boolean,
        val evicted: QueueEvictedNotice?,
        val others: List<OtherServerDrafts>,
    )

    private fun action(row: ChatInboxRow, command: suspend (ChatGraphAccessors) -> Boolean) {
        if (mutable.value.busy || row !in mutable.value.rows || row.sending) return
        val graph = registry.graphFor(route.ref) ?: return
        val services = graph as? ChatGraphAccessors ?: return
        val revision = services.chatGateway.scopeRevision
        mutable.value = mutable.value.copy(busy = true, failed = false)
        viewModelScope.launch {
            if (
                registry.graphFor(route.ref) !== graph ||
                    services.chatGateway.scopeRevision != revision
            ) {
                if (registry.graphFor(route.ref) === graph)
                    mutable.value = mutable.value.copy(busy = false)
                return@launch
            }
            val success = command(services)
            if (registry.graphFor(route.ref) === graph)
                mutable.value =
                    mutable.value.copy(
                        busy = false,
                        failed = !success && services.chatGateway.scopeRevision == revision,
                    )
        }
    }

    override fun cancel(row: ChatInboxRow) {
        if (row.local || row.dispatched) return
        action(row) { services ->
            val graph = registry.graphFor(route.ref)
            val revision = services.chatGateway.scopeRevision
            val result =
                services.chatDelivery.inbox.cancel(route.ref.sessionId, InboxItemId(row.id))
            if (result !is Outcome.Ok) return@action false
            val text = result.value
            if (
                registry.graphFor(route.ref) === graph &&
                    services.chatGateway.scopeRevision == revision &&
                    text != null
            ) {
                nextRestore += 1
                mutable.value = mutable.value.copy(restore = InboxRestore(nextRestore, text))
            }
            true
        }
    }

    override fun steer(row: ChatInboxRow) {
        if (row.local || !mutable.value.canSteer || row.delivery == InboxDelivery.Steer) return
        action(row) { services ->
            services.chatDelivery.inbox.setDelivery(
                route.ref.sessionId,
                InboxItemId(row.id),
                InboxDelivery.Steer,
            ) is Outcome.Ok
        }
    }

    override fun check(row: ChatInboxRow) {
        if (!row.local || !row.dispatched) return
        action(row) { services -> services.chatDelivery.queue?.checkReceipt(row.id) == true }
    }

    override fun sendAgain(row: ChatInboxRow) {
        if (!row.local || !row.dispatched) return
        action(row) { services ->
            val queue = services.chatDelivery.queue ?: return@action false
            val entry = queue.entries().firstOrNull { it.id == row.id } ?: return@action true
            val again = queue.resend(row.id)
            if (again is Outcome.Ok && again.value) return@action true
            if (queue.entries().none { it.id == row.id }) return@action true
            val messageId =
                services.chatGateway.facet<CorrelatedPromptGateway>()?.createPromptMessageId()
                    ?: return@action false
            val fresh =
                io.github.eslamasabry.oc.engine.delivery.queue.QueuedPrompt(
                    id = messageId.raw,
                    profileId = entry.profileId,
                    sessionId = entry.sessionId,
                    text = entry.text,
                    attachments = entry.attachments,
                    mentions = entry.mentions,
                    modelProviderId = entry.modelProviderId,
                    modelId = entry.modelId,
                    agent = entry.agent,
                    variant = entry.variant,
                    createdAt = kotlin.time.Clock.System.now().toEpochMilliseconds(),
                )
            val queued = queue.queuePrompt(fresh)
            if (queued !is Outcome.Ok || queued.value != null) return@action false
            if (queue.remove(row.id) !is Outcome.Ok) return@action false
            queue.flush()
            true
        }
    }

    override fun discard(row: ChatInboxRow) {
        if (!row.local || !row.dispatched) return
        action(row) { services ->
            val queue = services.chatDelivery.queue ?: return@action false
            val removed = queue.remove(row.id)
            removed is Outcome.Ok && removed.value
        }
    }

    override fun editLocal(row: ChatInboxRow) {
        if (!row.local || row.dispatched) return
        action(row) { services ->
            val queue = services.chatDelivery.queue ?: return@action false
            val revision = services.chatGateway.scopeRevision
            val entry = queue.entries().firstOrNull { it.id == row.id }
            if (entry == null || entry.dispatched || queue.sending(row.id)) return@action false
            val removed = queue.remove(row.id)
            if (removed !is Outcome.Ok || !removed.value) return@action false
            if (services.chatGateway.scopeRevision == revision) {
                nextRestore += 1
                mutable.value = mutable.value.copy(restore = InboxRestore(nextRestore, entry.text))
            }
            true
        }
    }

    override fun evictionSeen() {
        mutable.value = mutable.value.copy(evicted = null)
    }

    override fun restoreDone(id: Long) {
        if (mutable.value.restore?.id == id) mutable.value = mutable.value.copy(restore = null)
    }

    @AssistedFactory
    @ContributesIntoMap(AppScope::class)
    @ManualViewModelAssistedFactoryKey
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ChatRoute): ChatInboxViewModel
    }
}
