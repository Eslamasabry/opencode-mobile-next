package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.AgentFeature
import io.github.eslamasabry.oc.core.model.HostUpdatePhase
import io.github.eslamasabry.oc.core.model.HostUpdateResult
import io.github.eslamasabry.oc.core.model.ImportableConversation
import io.github.eslamasabry.oc.core.model.ImportableConversations
import io.github.eslamasabry.oc.core.model.Session
import kotlinx.collections.immutable.ImmutableList
import kotlinx.serialization.json.JsonPrimitive

public interface AgentFeaturesGateway {
    public fun providerIdForSession(session: SessionId): String?

    public suspend fun agentFeatures(session: SessionId): Outcome<ImmutableList<AgentFeature>>

    public suspend fun setAgentFeature(
        session: SessionId,
        featureId: String,
        value: JsonPrimitive,
    ): Outcome<ImmutableList<AgentFeature>>
}

public interface ReadOnlySessionsGateway {
    public fun isReadOnlySession(session: SessionId): Boolean
}

public interface HostDaemonUpdateGateway {
    public suspend fun updateHostDaemon(
        onProgress: (HostUpdatePhase) -> Unit
    ): Outcome<HostUpdateResult>
}

public interface ProviderConversationImportGateway {
    public val providerImportDirectory: String

    public suspend fun importableConversations(): Outcome<ImportableConversations>

    public suspend fun importConversation(conversation: ImportableConversation): Outcome<Session>
}
