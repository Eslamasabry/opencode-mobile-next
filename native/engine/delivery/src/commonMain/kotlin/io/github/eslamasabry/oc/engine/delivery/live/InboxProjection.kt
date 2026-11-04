package io.github.eslamasabry.oc.engine.delivery.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.InboxItemId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.InboxGateway
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.InboxChange
import io.github.eslamasabry.oc.core.model.InboxDelivery
import io.github.eslamasabry.oc.core.model.InboxItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The OpenCode 2 inbox of pending sends, per session (Dart `ConnectionController` inbox state, row B-PROTO-045). The
 * event stream is volatile, so an enqueued or re-routed item is reconciled by refetch, never replayed from the event;
 * delivered and cancelled items drop locally. Cancelling hands the prompt text back to the composer; a cancel the
 * server answers 409 (already delivered) drops the item and still fails. Changing the delivery mode updates the server
 * first, then the visible item.
 */
public class InboxProjection(private val gateway: InboxGateway) {
    private val state = MutableStateFlow<Map<SessionId, List<InboxItem>>>(emptyMap())
    public val items: StateFlow<Map<SessionId, List<InboxItem>>> = state.asStateFlow()

    public fun itemsFor(session: SessionId): List<InboxItem> = state.value[session].orEmpty()

    public suspend fun onEvent(event: GatewayEvent.InboxChanged) {
        when (event.change) {
            InboxChange.Enqueued, InboxChange.DeliveryChanged -> refresh(event.sessionId)
            InboxChange.Delivered, InboxChange.Cancelled -> drop(event.sessionId, event.inboxId)
        }
    }

    /** REST is the truth: the session's list is replaced, stale local items included. */
    public suspend fun refresh(session: SessionId): Outcome<Unit> =
        when (val listed = gateway.inbox(session)) {
            is Outcome.Failed -> listed
            is Outcome.Ok -> {
                state.update { it + (session to listed.value.sortedBy { item -> item.createdAtMs ?: 0L }) }
                Unit.ok()
            }
        }

    /** Answers the prompt text to put back in the composer. */
    public suspend fun cancel(session: SessionId, item: InboxItemId): Outcome<String?> {
        val text = itemsFor(session).firstOrNull { it.id == item }?.let(::promptText)
        return when (val cancelled = gateway.cancelInboxItem(session, item)) {
            is Outcome.Ok -> {
                drop(session, item)
                text.ok()
            }
            is Outcome.Failed -> {
                if (cancelled.failure is Failure.Conflict) drop(session, item) // already delivered: it is not pending
                cancelled
            }
        }
    }

    public suspend fun setDelivery(session: SessionId, item: InboxItemId, delivery: InboxDelivery): Outcome<Unit> =
        when (val set = gateway.setInboxDelivery(session, item, delivery)) {
            is Outcome.Failed -> set
            is Outcome.Ok -> {
                state.update { all ->
                    all + (session to all[session].orEmpty().map { if (it.id == item) it.copy(delivery = delivery) else it })
                }
                Unit.ok()
            }
        }

    private fun drop(session: SessionId, item: InboxItemId) {
        state.update { all -> all + (session to all[session].orEmpty().filterNot { it.id == item }) }
    }

    public companion object {
        /** The queued prompt's own words (`payload.text`); never any other server field. */
        public fun promptText(item: InboxItem): String? = (item.payload["text"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    }
}
