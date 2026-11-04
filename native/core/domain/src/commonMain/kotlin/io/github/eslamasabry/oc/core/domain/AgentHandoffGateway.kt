package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ModelRef

/**
 * A negotiated record writer and explicitly fenced first-prompt start. Shared-filesystem proof
 * belongs to the host.
 */
public interface AgentHandoffGateway {
    public val handoffFilesReady: Boolean

    public fun durableHandoffSessionId(session: SessionId): SessionId

    public fun handoffNavigationSessionId(session: SessionId): SessionId

    public suspend fun writeHandoffRecord(
        directory: String,
        recordId: String,
        markdown: String,
    ): Outcome<String>

    public suspend fun promptHandoff(
        session: SessionId,
        text: String,
        model: ModelRef,
        agent: String?,
        variant: String?,
        beforeSend: () -> Boolean,
    ): Outcome<Unit>
}
