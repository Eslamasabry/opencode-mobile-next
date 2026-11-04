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
public class RecentModels(private val max: Int = 5, private val backing: RecentModelsBacking? = null) {
    private val byProfile = HashMap<String, ArrayDeque<Pair<String, String>>>()

    public fun record(profile: String, providerId: String, modelId: String) {
        val list = listOf(profile, keep = true)
        list.remove(providerId to modelId)
        list.addFirst(providerId to modelId)
        while (list.size > max) list.removeLast()
        backing?.save(profile, list.toList())
    }

    public fun of(profile: String): List<Pair<String, String>> = listOf(profile, keep = false).toList()

    /** The saved list is read once per server; a store that cannot answer yet is asked again next time. */
    private fun listOf(profile: String, keep: Boolean): ArrayDeque<Pair<String, String>> {
        byProfile[profile]?.let { return it }
        if (backing == null) return byProfile.getOrPut(profile) { ArrayDeque() }
        val saved = backing.load(profile)
        // Not readable yet: a read asks again later; a pick starts this run's list (and saves it).
        if (saved == null) return if (keep) ArrayDeque<Pair<String, String>>().also { byProfile[profile] = it } else ArrayDeque()
        return ArrayDeque(saved.distinct().take(max)).also { byProfile[profile] = it }
    }
}

/**
 * B-STATE-032: where the recent picks persist across restarts, per server (the app binds `:engine:agents`'
 * `ModelStore`, the Flutter `oc.modelLibrary.<profileId>` format). [load] answers null while the store cannot be read
 * yet; [save] never blocks the pick. No binding keeps the picks for this app run only.
 */
public interface RecentModelsBacking {
    /** Newest first as (providerId, modelId). */
    public fun load(profile: String): List<Pair<String, String>>?

    public fun save(profile: String, recent: List<Pair<String, String>>)
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
