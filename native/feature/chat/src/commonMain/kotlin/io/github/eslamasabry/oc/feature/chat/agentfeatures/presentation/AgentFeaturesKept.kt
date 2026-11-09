package io.github.eslamasabry.oc.feature.chat.agentfeatures.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import kotlinx.serialization.Serializable

/**
 * Cache-first (owner rule, 2026-11-09): the agent's feature list opens on what it last showed, then the live read
 * replaces it in place. The app binds this to its last-known store; without a binding the list loads as before.
 */
public interface AgentFeaturesLastKnown {
    public fun peek(ref: ConversationRef): KeptAgentFeatures?

    public suspend fun read(ref: ConversationRef): KeptAgentFeatures?

    public suspend fun keep(ref: ConversationRef, kept: KeptAgentFeatures)
}

/** The feature labels, descriptions and current choices the person saw. Display facts only, capped. */
@Serializable
public data class KeptAgentFeatures(val owner: String? = null, val features: List<Feature> = emptyList()) {
    @Serializable
    public data class Option(val id: String, val label: String)

    @Serializable
    public data class Feature(
        val id: String,
        val label: String,
        val description: String? = null,
        val on: Boolean? = null,
        val selected: String? = null,
        val options: List<Option> = emptyList(),
    )

    public fun toSnapshot(): AgentFeaturesSnapshot =
        AgentFeaturesSnapshot(
            owner,
            features.map { f ->
                AgentFeatureUi(f.id, f.label, f.description, f.on, f.selected, f.options.map { AgentFeatureOptionUi(it.id, it.label) })
            },
        )

    public companion object {
        public const val MAX_FEATURES: Int = 60
        public const val MAX_OPTIONS: Int = 20
        private const val MAX_TEXT = 300

        public fun of(snapshot: AgentFeaturesSnapshot): KeptAgentFeatures =
            KeptAgentFeatures(
                snapshot.owner?.take(MAX_TEXT),
                snapshot.features.take(MAX_FEATURES).map { f ->
                    Feature(
                        f.id.take(MAX_TEXT),
                        f.label.take(MAX_TEXT),
                        f.description?.take(MAX_TEXT),
                        f.on,
                        f.selected?.take(MAX_TEXT),
                        f.options.take(MAX_OPTIONS).map { Option(it.id.take(MAX_TEXT), it.label.take(MAX_TEXT)) },
                    )
                },
            )
    }
}

/** What the sheet draws in its body. A skeleton is only for a first-ever read; kept rows are never swapped out. */
public enum class AgentFeaturesBody { Failure, Skeleton, Empty, Rows }

public fun AgentFeaturesUi.body(): AgentFeaturesBody =
    when {
        failure != null -> AgentFeaturesBody.Failure
        snapshot == null && loading -> AgentFeaturesBody.Skeleton
        snapshot?.features.isNullOrEmpty() -> AgentFeaturesBody.Empty
        else -> AgentFeaturesBody.Rows
    }

/** The state a reopened sheet starts from: the kept list is the snapshot, nothing is loading yet. */
public fun AgentFeaturesUi.seeded(kept: KeptAgentFeatures?): AgentFeaturesUi =
    if (kept == null || snapshot != null) this else copy(snapshot = kept.toSnapshot())

/** A failed READ with a list on screen stays quiet (the kept list remains); a failed change or a first read shows. */
public fun AgentFeaturesUi.readFailed(failure: Failure, wasRead: Boolean): AgentFeaturesUi =
    copy(loading = false, failure = if (wasRead && snapshot != null) null else failure)
