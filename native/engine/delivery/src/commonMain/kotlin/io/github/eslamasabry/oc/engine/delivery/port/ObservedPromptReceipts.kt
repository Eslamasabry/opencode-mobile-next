package io.github.eslamasabry.oc.engine.delivery.port

import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.engine.delivery.receipts.DispatchScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Metadata only, scoped to this connection's identity; collecting never reads history or resends a
 * prompt.
 */
public class ObservedPromptReceipts {
    private data class Key(val scope: DispatchScope, val session: String, val receipt: String)

    private val keys = MutableStateFlow<List<Key>>(emptyList())

    public fun observe(scope: DispatchScope, event: GatewayEvent.MessageUpserted) {
        val echo = event.userEcho ?: return
        val message = event.message
        if (
            message.role != Role.User ||
                message.sessionId.isEmpty() ||
                echo.clientMessageId.raw.isEmpty() ||
                echo.parts.isEmpty() ||
                echo.parts.any {
                    it.synthetic || it.sessionId != message.sessionId || it.messageId != message.id
                }
        )
            return
        val key = Key(scope, message.sessionId, echo.clientMessageId.raw)
        keys.update { previous -> (previous.filterNot { it == key } + key).takeLast(512) }
    }

    public fun contains(scope: DispatchScope, session: SessionId, receipt: MessageId): Boolean =
        Key(scope, session.raw, receipt.raw) in keys.value
}
