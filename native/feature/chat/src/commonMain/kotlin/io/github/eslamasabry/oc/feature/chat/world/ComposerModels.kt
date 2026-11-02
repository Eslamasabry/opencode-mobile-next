package io.github.eslamasabry.oc.feature.chat.world

import io.github.eslamasabry.oc.core.common.Failure

/** Safe catalog projection: never retain raw provider configuration in presentation. */
public data class ComposerModel(
    val providerId: String,
    val modelId: String,
    val label: String,
    val supportsAttachments: Boolean,
)

public data class ComposerModels(
    val choices: List<ComposerModel> = emptyList(),
    val selected: ComposerModel? = null,
    val loading: Boolean = false,
    val failure: Failure? = null,
)
