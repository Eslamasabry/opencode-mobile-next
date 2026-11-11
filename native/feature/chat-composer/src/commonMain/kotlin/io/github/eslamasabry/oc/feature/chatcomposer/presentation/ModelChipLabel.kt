package io.github.eslamasabry.oc.feature.chatcomposer.presentation

import io.github.eslamasabry.oc.core.model.ModelDisplayName
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * A readable stand-in for a model's name made from its id alone, for the moment before the catalog arrives: port of
 * Dart `modelNameFromId` (`lib/domain/model_display_name.dart`, B-KIT-057), kept in one home, `:core:model`
 * [ModelDisplayName]. "claude-sonnet-5" reads "Sonnet 5", "claude-opus-4-1-20250805" reads "Opus 4.1".
 */
object ModelNames {
    fun fromId(modelId: String): String = ModelDisplayName.fromId(modelId)
}

/** The model chip's rules (plan 09 §10.3; B-CHAT-066, B-CHAT-067). Never `provider/model` or a raw id. */
object ModelChipLabel {
    private val NEW_FOR = 30.days

    fun label(facts: ModelFacts): ModelLabel {
        val id = facts.modelId?.takeIf { it.isNotBlank() }
        if (id == null) {
            return when {
                !facts.signedIn -> ModelLabel.NoModelSignedIn
                !facts.serverDefaultName.isNullOrBlank() -> ModelLabel.Named(facts.serverDefaultName.trim())
                else -> ModelLabel.Loading
            }
        }
        facts.catalogName?.trim()?.takeIf { it.isNotEmpty() && !facts.catalogLoading }?.let { return ModelLabel.Named(it) }
        // While the models are read (or when the catalog lacks the model), a name seen before, else one made from the id.
        val known = facts.knownName?.trim()?.takeIf { it.isNotEmpty() }
        return ModelLabel.Named(known ?: ModelNames.fromId(id))
    }

    /** Released in the last 30 days; a date in the future is wrong, not new. */
    fun isNew(released: Instant?, now: Instant): Boolean {
        if (released == null) return false
        val age = now - released
        return !age.isNegative() && age < NEW_FOR
    }

    fun effort(id: String): EffortWord =
        when (id.trim().lowercase()) {
            "none" -> EffortWord.Known.None
            "minimal" -> EffortWord.Known.Minimal
            "low" -> EffortWord.Known.Low
            "medium" -> EffortWord.Known.Medium
            "high" -> EffortWord.Known.High
            "xhigh" -> EffortWord.Known.ExtraHigh
            "max" -> EffortWord.Known.Max
            else -> EffortWord.Other(id)
        }

    fun chip(facts: ModelFacts, now: Instant): ComposerChip.Model =
        ComposerChip.Model(
            label = label(facts),
            isNew = facts.modelId != null && isNew(facts.released, now),
            effort = facts.effort?.takeIf { facts.effortIsChoice && it.isNotBlank() }?.let(::effort),
        )
}
