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
    /** B3 (QA wave 1): the provider's display name, so equal model names can be told apart. */
    val providerName: String = "",
)

/** B3: the models picked lately in this app run, newest first, per server (memory only, never a preference). */
public class RecentModels(private val max: Int = 5) {
    private val byProfile = HashMap<String, ArrayDeque<Pair<String, String>>>()

    public fun record(profile: String, providerId: String, modelId: String) {
        val list = byProfile.getOrPut(profile) { ArrayDeque() }
        list.remove(providerId to modelId)
        list.addFirst(providerId to modelId)
        while (list.size > max) list.removeLast()
    }

    public fun of(profile: String): List<Pair<String, String>> = byProfile[profile]?.toList().orEmpty()
}

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
    /** B3: models picked lately on this server, newest first (from [choices]). */
    val recent: List<ComposerModel> = emptyList(),
)

/** One server slash command, safe for display. */
public data class ComposerCommand(val name: String, val description: String? = null)
