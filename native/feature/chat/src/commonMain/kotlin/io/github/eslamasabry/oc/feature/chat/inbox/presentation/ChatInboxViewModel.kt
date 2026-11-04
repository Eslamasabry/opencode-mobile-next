package io.github.eslamasabry.oc.feature.chat.inbox.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.InboxItemId
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

public data class ChatInboxUi(
    val rows: List<ChatInboxRow> = emptyList(),
    val canSteer: Boolean = false,
    val busy: Boolean = false,
    val failed: Boolean = false,
    val restore: InboxRestore? = null,
)

public interface ChatInboxActions {
    public fun cancel(row: ChatInboxRow)

    public fun steer(row: ChatInboxRow)

    public fun check(row: ChatInboxRow)

    public fun restoreDone(id: Long)
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
            registry.live.collectLatest { live ->
                val graph = live[route.ref.profileId]
                val services = graph as? ChatGraphAccessors
                mutable.value = ChatInboxUi()
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
                            (remote + device) to caps.promptSteering
                        }
                        .collect { (rows, steer) ->
                            mutable.value = mutable.value.copy(rows = rows, canSteer = steer)
                        }
                }
            }
        }
    }

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
