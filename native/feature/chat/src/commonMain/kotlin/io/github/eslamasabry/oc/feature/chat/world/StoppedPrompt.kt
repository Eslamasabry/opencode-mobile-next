package io.github.eslamasabry.oc.feature.chat.world

import io.github.eslamasabry.oc.core.model.PromptAttachment

/** One model of the catalog, by its provider and id (B-169). */
public data class ModelKey(val providerId: String, val modelId: String) {
    public companion object {
        public fun of(model: ComposerModel): ModelKey = ModelKey(model.providerId, model.modelId)
    }
}

/**
 * The prompt that started a stopped turn, read back from the transcript (design §4): what Switch model sends again.
 * [toolSteps] counts the tool steps the stopped reply already ran (edits, commands).
 */
public data class StoppedPrompt(
    val turnId: String,
    val text: String,
    val attachments: List<PromptAttachment> = emptyList(),
    val agent: String? = null,
    /** The model the stopped reply ran on. */
    val model: ModelKey? = null,
    val toolSteps: Int = 0,
    /** The conversation's size in tokens after this turn, when known (the context guard). */
    val contextTokens: Long? = null,
) {
    /** Files that can no longer be read and so cannot ride along (design §4.3). */
    public val unreadable: List<PromptAttachment>
        get() = attachments.filter { it.url.isBlank() }
}
