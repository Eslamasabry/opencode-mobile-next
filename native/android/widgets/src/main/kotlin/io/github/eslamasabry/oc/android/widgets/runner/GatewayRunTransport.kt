package io.github.eslamasabry.oc.android.widgets.runner

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts

/**
 * [WidgetRunTransport] over the protocol-neutral gateway of one private, cold connection. Only reads the runners
 * need and two mutations (an exact permission answer and stop). [release] closes the connection's own scope after
 * the gateway; the person's live connection is never touched.
 */
public class GatewayRunTransport(
    private val gateway: ServerGateway,
    private val release: suspend () -> Unit = {},
) : WidgetRunTransport {
    private val lock = Any()
    private var closed = false

    override suspend fun health(): Outcome<Boolean> = gateway.health().then { it.healthy }

    override suspend fun pendingPermissions(): Outcome<List<WidgetPendingAsk>> =
        gateway.pendingPermissions().then { list -> list.map { WidgetPendingAsk(it.id.raw, it.sessionId.raw) } }

    override suspend fun messages(session: String): Outcome<List<MessageWithParts>> =
        gateway.messagePage(SessionId(session)).then { it.items }

    override suspend fun sessionStatuses(): Outcome<Map<String, String>> =
        gateway.sessionStatuses().then { statuses -> statuses.entries.associate { (id, status) -> id.raw to status.raw } }

    override suspend fun answerPermission(session: String, request: String, allowOnce: Boolean): Outcome<Unit> =
        gateway.replyPermission(
            SessionId(session),
            RequestId(request),
            if (allowOnce) PermissionReply.Once else PermissionReply.Reject,
        )

    override suspend fun stop(session: String): Outcome<Unit> = gateway.stop(SessionId(session)).then {}

    override suspend fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
        }
        try {
            gateway.close()
        } finally {
            release()
        }
    }
}

/** core:common's inline `map` cannot be inlined here (this module targets an older JVM bytecode level). */
private fun <T, R> Outcome<T>.then(transform: (T) -> R): Outcome<R> =
    when (this) {
        is Outcome.Ok -> Outcome.Ok(transform(value))
        is Outcome.Failed -> this
    }
