package io.github.eslamasabry.oc.feature.chat.composer.presentation

import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels

/** One group of the model picker: [providerName] null = the "Recent" group. */
public data class ModelSection(
    val providerName: String?,
    val rows: List<ModelPickRow>,
    val favorites: Boolean = false,
)

/** One model row; [current] marks the conversation's model. */
public data class ModelPickRow(val model: ComposerModel, val current: Boolean)

/**
 * QA wave 1 B3: the picker is a list grouped by provider (the provider's name is the group label),
 * recent picks first, the current model marked, and a search that matches the model name, its id or
 * the provider. Pure, so the order is tested without Compose.
 */
public object ModelPicker {
    /** Server default is offered as the list's first choice once a model is known, and not while searching. */
    public fun offersServerDefault(models: ComposerModels, query: String = ""): Boolean =
        models.selected != null && query.isBlank()

    /** The thinking levels on the chosen model's one line: "" (the model's default) first, then its own levels. */
    public fun thinkingLevels(models: ComposerModels): List<String> =
        models.selected?.variants?.takeIf { it.isNotEmpty() }?.let { listOf("") + it } ?: emptyList()

    public fun sections(models: ComposerModels, query: String = ""): List<ModelSection> {
        val q = query.trim().lowercase()
        val current = if (models.usesDefault) null else models.selected
        fun matches(m: ComposerModel) =
            q.isEmpty() ||
                q in m.label.lowercase() ||
                q in m.modelId.lowercase() ||
                q in m.providerName.lowercase() ||
                q in m.providerId.lowercase()
        fun row(m: ComposerModel) =
            ModelPickRow(
                m,
                current != null &&
                    m.providerId == current.providerId &&
                    m.modelId == current.modelId,
            )
        val out = ArrayList<ModelSection>()
        // Each model shows once (owner 2026-11-06): a favourite or a recent pick is not repeated in its provider's
        // group below.
        fun key(m: ComposerModel) = m.providerId + "/" + m.modelId
        val listed = HashSet<String>()
        val favorites = models.favorites.filter(::matches).filter { listed.add(key(it)) }
        if (favorites.isNotEmpty())
            out += ModelSection(null, favorites.map(::row), favorites = true)
        val recent = models.recent.filter(::matches).filter { listed.add(key(it)) }
        if (recent.isNotEmpty()) out += ModelSection(null, recent.map(::row))
        models.choices
            .filter(::matches)
            .filter { key(it) !in listed }
            .groupBy { it.providerId }
            .map { (_, list) -> list }
            .sortedWith(
                // The current model's provider first, then by name.
                compareBy<List<ComposerModel>>({ list ->
                        list.none { current != null && it.providerId == current.providerId }
                    })
                    .thenBy {
                        it.first().providerName.ifEmpty { it.first().providerId }.lowercase()
                    }
            )
            .forEach { list ->
                val name = list.first().providerName.ifEmpty { list.first().providerId }
                // B-NAV-034: the current model leads its provider's group, so it is on screen when
                // the picker opens.
                val sorted =
                    list.sortedWith(
                        compareBy<ComposerModel>({ !row(it).current }, { it.label.lowercase() })
                    )
                out += ModelSection(name, sorted.map(::row))
            }
        return out
    }
}
