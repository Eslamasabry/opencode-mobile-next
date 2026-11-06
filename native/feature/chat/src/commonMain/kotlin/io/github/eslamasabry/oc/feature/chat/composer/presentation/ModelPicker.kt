package io.github.eslamasabry.oc.feature.chat.composer.presentation

import io.github.eslamasabry.oc.feature.chat.world.ComposerModels

/**
 * The chat's own rules around the kit model sheet (N-51: the list's order is `KitModelOrder`, shared with New
 * conversation). Pure, so they are tested without Compose.
 */
public object ModelPicker {
    /** Server default is offered as the list's first choice once a model is known (the sheet hides it while searching). */
    public fun offersServerDefault(models: ComposerModels, query: String = ""): Boolean =
        models.selected != null && query.isBlank()

    /** The thinking levels on the chosen model's one line: "" (the model's default) first, then its own levels. */
    public fun thinkingLevels(models: ComposerModels): List<String> =
        models.selected?.variants?.takeIf { it.isNotEmpty() }?.let { listOf("") + it } ?: emptyList()
}
