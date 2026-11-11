package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.core.model.ModelDisplayName

/**
 * A reply footer's model words ("Sonnet 4"), made from the model id the way the composer's chip does: Dart
 * `modelNameFromId` (`lib/domain/model_display_name.dart`, B-KIT-057), from its one home in `:core:model`.
 * "claude-sonnet-4" reads "Sonnet 4", "claude-opus-4-1-20250805" reads "Opus 4.1", "gpt-6-sol" reads "GPT-6 Sol".
 */
internal object ReplyModelNames {
    fun fromId(modelId: String): String = ModelDisplayName.fromId(modelId)
}
