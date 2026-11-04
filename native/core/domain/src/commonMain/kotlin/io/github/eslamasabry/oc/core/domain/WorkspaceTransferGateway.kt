package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PromptAdmission
import kotlinx.serialization.json.JsonObject

/** The closed file operations needed by the exclusive conversation transfer writer. */
public sealed interface WorkspaceTransferRequest {
    public data class Create(val parent: String, val name: String, val directory: Boolean) :
        WorkspaceTransferRequest

    public data class ListEntries(val path: String) : WorkspaceTransferRequest

    public data class ReadFile(val path: String, val maxBytes: Int) : WorkspaceTransferRequest

    public data class WriteFile(
        val path: String,
        val content: String,
        val expectedModifiedAt: String,
        val expectedRevision: String?,
    ) : WorkspaceTransferRequest {
        override fun toString(): String = "WorkspaceTransferRequest.WriteFile"
    }
}

/** Optional negotiated workspace writer and first-prompt dispatch of one live connection. */
public interface WorkspaceTransferGateway {
    /** Changes on any reconnect or location change. */
    public val transferEpoch: Long

    /** False when closed, disconnected, or workspace editing was not explicitly negotiated. */
    public val transferConnected: Boolean

    /** No retry; [epoch] and the pinned directory are checked again immediately before dispatch. */
    public suspend fun transferFileRequest(
        directory: String,
        epoch: Long,
        request: WorkspaceTransferRequest,
    ): Outcome<JsonObject>

    /** The daemon's durable identity, for the stored navigation link. */
    public fun transferSessionId(id: SessionId): SessionId

    /** The alias by which this same connection opens a daemon identity. */
    public fun transferNavigationId(id: SessionId): SessionId

    /**
     * First prompt on an unconsumed draft only; fences after provider preflight and before
     * creation.
     */
    public suspend fun promptTransfer(
        session: SessionId,
        prompt: OutgoingPrompt,
        beforeSend: () -> Boolean,
    ): Outcome<PromptAdmission>
}
