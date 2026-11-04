package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.feature.chat.agentfeatures.presentation.AgentFeatureOptionUi
import io.github.eslamasabry.oc.feature.chat.agentfeatures.presentation.AgentFeatureUi
import io.github.eslamasabry.oc.feature.chat.agentfeatures.presentation.AgentFeaturesPort
import io.github.eslamasabry.oc.feature.chat.agentfeatures.presentation.AgentFeaturesSnapshot
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.protocol.paseo.gateway.AgentFeature
import io.github.eslamasabry.oc.protocol.paseo.gateway.AgentFeatureKind
import io.github.eslamasabry.oc.protocol.paseo.gateway.PaseoGateway
import io.github.eslamasabry.oc.protocol.paseo.gateway.agentFeatures
import io.github.eslamasabry.oc.protocol.paseo.gateway.agentFeaturesOwner
import io.github.eslamasabry.oc.protocol.paseo.gateway.setAgentFeature

/** Protocol binding lives in the app graph; the feature knows only the agent switch port. */
@Inject
@ContributesIntoSet(AppScope::class)
class PaseoAgentFeaturesPort(private val registry: ConnectionRegistry) : AgentFeaturesPort {
    private fun gateway(ref: ConversationRef): PaseoGateway? {
        val graph =
            registry.live.value.entries.firstOrNull { it.key == ref.profileId }?.value
                ?: return null
        val gateway = (graph as ChatGraphAccessors).chatGateway as? PaseoGateway ?: return null
        if (gateway.isClosed) return null
        return gateway
    }

    override fun available(ref: ConversationRef): Boolean = gateway(ref) != null

    private fun unavailable(): Outcome.Failed =
        Outcome.Failed(Failure.Unavailable(FeatureId("agentFeatures")))

    override suspend fun read(ref: ConversationRef): Outcome<AgentFeaturesSnapshot> {
        val gateway = gateway(ref) ?: return unavailable()
        return snapshot(ref, gateway, gateway.agentFeatures(ref.sessionId))
    }

    override suspend fun set(
        ref: ConversationRef,
        id: String,
        value: Any,
    ): Outcome<AgentFeaturesSnapshot> {
        val gateway = gateway(ref) ?: return unavailable()
        return snapshot(ref, gateway, gateway.setAgentFeature(ref.sessionId, id, value))
    }

    private fun snapshot(
        ref: ConversationRef,
        gateway: PaseoGateway,
        result: Outcome<List<AgentFeature>>,
    ): Outcome<AgentFeaturesSnapshot> {
        if (gateway(ref) !== gateway) return unavailable()
        return when (result) {
            is Outcome.Failed -> result
            is Outcome.Ok ->
                Outcome.Ok(
                    AgentFeaturesSnapshot(
                        gateway.agentFeaturesOwner(ref.sessionId),
                        result.value.map { feature ->
                            AgentFeatureUi(
                                feature.id,
                                feature.label,
                                feature.description,
                                on =
                                    if (feature.kind == AgentFeatureKind.Toggle) feature.on
                                    else null,
                                selected = feature.selected,
                                options =
                                    feature.options.map { AgentFeatureOptionUi(it.id, it.label) },
                            )
                        },
                    )
                )
        }
    }
}
