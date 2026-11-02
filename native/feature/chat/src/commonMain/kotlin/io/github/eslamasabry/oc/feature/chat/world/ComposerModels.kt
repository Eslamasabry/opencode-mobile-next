package io.github.eslamasabry.oc.feature.chat.world

import io.github.eslamasabry.oc.core.common.Failure

/** Safe catalog projection: never retain raw provider configuration in presentation. */
public data class ComposerModel(
    val providerId: String,
    val modelId: String,
    val label: String,
    val supportsAttachments: Boolean,
    /** The model's thinking levels (catalog variant ids, disabled ones left out). */
    val variants: List<String> = emptyList(),
)

/** A primary agent the conversation can run as (hidden agents and subagents are left out). */
public data class ComposerAgent(val id: String, val description: String? = null)

public data class ComposerModels(
    val choices: List<ComposerModel> = emptyList(),
    val selected: ComposerModel? = null,
    val loading: Boolean = false,
    val failure: Failure? = null,
    /** The session's thinking level; "" is the model's default. */
    val variant: String = "",
    val agents: List<ComposerAgent> = emptyList(),
    /** The session's agent; null = the server's default. */
    val agent: String? = null,
    /** The session names no model: the server default applies. */
    val usesDefault: Boolean = false,
)

/** One server slash command, safe for display. */
public data class ComposerCommand(val name: String, val description: String? = null)
