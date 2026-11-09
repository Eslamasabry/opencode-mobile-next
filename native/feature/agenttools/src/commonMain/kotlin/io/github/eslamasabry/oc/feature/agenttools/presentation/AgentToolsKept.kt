package io.github.eslamasabry.oc.feature.agenttools.presentation

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.AgentFeature
import io.github.eslamasabry.oc.core.model.AgentFeatureKind
import io.github.eslamasabry.oc.core.model.AgentFeatureOption
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable

/**
 * Cache-first (owner rule, 2026-11-09): Agent tools opens on the feature switches and model choices it last showed,
 * then the live read replaces them in place. Bound by the app to its last-known store; without a binding it loads as
 * before.
 */
public interface AgentToolsLastKnown {
    public fun peek(profile: ProfileId, session: SessionId?): KeptAgentTools?

    public suspend fun read(profile: ProfileId, session: SessionId?): KeptAgentTools?

    public suspend fun keep(profile: ProfileId, session: SessionId?, kept: KeptAgentTools)
}

/** The labels and current choices the person saw (already redacted for display). Capped; nothing else is kept. */
@Serializable
public data class KeptAgentTools(val features: List<Feature> = emptyList(), val models: List<Model> = emptyList()) {
    @Serializable
    public data class Option(val id: String, val label: String)

    @Serializable
    public data class Feature(
        val id: String,
        val label: String,
        val description: String? = null,
        val choice: Boolean = false,
        val on: Boolean = false,
        val selected: String? = null,
        val options: List<Option> = emptyList(),
    )

    @Serializable
    public data class Model(val id: String, val label: String, val selected: Boolean = false)

    public fun toFeatures(): ImmutableList<AgentFeature> =
        features
            .map {
                AgentFeature(
                    it.id,
                    it.label,
                    it.description,
                    if (it.choice) AgentFeatureKind.Choice else AgentFeatureKind.Toggle,
                    it.on,
                    it.selected,
                    it.options.map { o -> AgentFeatureOption(o.id, o.label) }.toImmutableList(),
                )
            }
            .toImmutableList()

    public fun toModels(): ImmutableList<AgentModelChoice> =
        models.map { AgentModelChoice(it.id, it.label, it.selected) }.toImmutableList()

    public companion object {
        public const val MAX_FEATURES: Int = 40
        public const val MAX_OPTIONS: Int = 20
        public const val MAX_MODELS: Int = 80
        private const val MAX_TEXT = 200

        public fun of(features: List<AgentFeature>, models: List<AgentModelChoice>): KeptAgentTools =
            KeptAgentTools(
                features.take(MAX_FEATURES).map {
                    Feature(
                        it.id.take(MAX_TEXT),
                        it.label.take(MAX_TEXT),
                        it.description?.take(MAX_TEXT),
                        it.kind == AgentFeatureKind.Choice,
                        it.on,
                        it.selected?.take(MAX_TEXT),
                        it.options.take(MAX_OPTIONS).map { o -> Option(o.id.take(MAX_TEXT), o.label.take(MAX_TEXT)) },
                    )
                },
                models.take(MAX_MODELS).map { Model(it.id.take(MAX_TEXT), it.label.take(MAX_TEXT), it.selected) },
            )
    }
}

/** True when the page has rows to draw while the live read runs; the skeleton is only for a first-ever read. */
public fun AgentToolsUi.hasRows(): Boolean = features.isNotEmpty() || models.isNotEmpty()

/** The state a reopened page starts from: the kept rows draw at once, the live read is still running. */
public fun AgentToolsUi.seeded(kept: KeptAgentTools?): AgentToolsUi =
    if (kept == null || hasRows()) this else copy(features = kept.toFeatures(), models = kept.toModels())
