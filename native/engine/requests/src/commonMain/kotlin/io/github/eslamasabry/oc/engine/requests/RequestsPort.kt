package io.github.eslamasabry.oc.engine.requests

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.domain.events.GatewayEvents
import io.github.eslamasabry.oc.core.model.PendingQuestion
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.core.model.PermissionRequest
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

/**
 * The part of one live connection the requests engine uses (plan 09 §5.1). The connection graph binds it with
 * [ServerGateway.asRequestsPort]; tests script it directly.
 */
public interface RequestsPort {
    public val events: GatewayEvents

    public val capabilities: StateFlow<ServerCapabilities>

    /** Dart `prepareActionTransport`: every reply waits for a woken link first (B-PROTO-048). */
    public suspend fun awaitActionTransport(): Outcome<Unit>

    public suspend fun pendingPermissions(): Outcome<ImmutableList<PermissionRequest>>

    public suspend fun replyPermission(session: SessionId, request: RequestId, reply: PermissionReply, note: String?): Outcome<Unit>

    public suspend fun pendingQuestions(): Outcome<ImmutableList<PendingQuestion>>

    public suspend fun answerQuestion(session: SessionId, request: RequestId, answers: ImmutableList<ImmutableList<String>>): Outcome<Unit>

    public suspend fun rejectQuestion(session: SessionId, request: RequestId): Outcome<Unit>
}

/** The one adapter from a connected gateway; nothing here branches on the backend. */
public fun ServerGateway.asRequestsPort(): RequestsPort =
    object : RequestsPort {
        private val gateway = this@asRequestsPort
        override val events: GatewayEvents get() = gateway.events
        override val capabilities: StateFlow<ServerCapabilities> get() = gateway.capabilities

        override suspend fun awaitActionTransport(): Outcome<Unit> = gateway.awaitActionTransport()

        override suspend fun pendingPermissions(): Outcome<ImmutableList<PermissionRequest>> = gateway.pendingPermissions()

        override suspend fun replyPermission(session: SessionId, request: RequestId, reply: PermissionReply, note: String?): Outcome<Unit> =
            gateway.replyPermission(session, request, reply, note)

        override suspend fun pendingQuestions(): Outcome<ImmutableList<PendingQuestion>> = gateway.pendingQuestions()

        override suspend fun answerQuestion(
            session: SessionId,
            request: RequestId,
            answers: ImmutableList<ImmutableList<String>>,
        ): Outcome<Unit> = gateway.answerQuestion(session, request, answers)

        override suspend fun rejectQuestion(session: SessionId, request: RequestId): Outcome<Unit> = gateway.rejectQuestion(session, request)
    }
