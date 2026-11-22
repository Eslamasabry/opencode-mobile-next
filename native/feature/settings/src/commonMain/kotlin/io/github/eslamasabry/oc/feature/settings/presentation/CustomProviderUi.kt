package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.core.model.IntegrationInfo
import io.github.eslamasabry.oc.engine.integrations.catalog.CustomProviderField
import io.github.eslamasabry.oc.engine.integrations.catalog.CustomProviderOutcome
import io.github.eslamasabry.oc.engine.integrations.catalog.CustomProviderProblem
import io.github.eslamasabry.oc.engine.integrations.catalog.CustomProviderRefusal
import io.github.eslamasabry.oc.engine.integrations.catalog.LibraryInventory
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Where the add-a-provider sheet is (pull request 142). "Saved" and "loaded" are never one stage: [SavedNotLoaded],
 * [RestartFailed] and [NotLoadedAfterRestart] all mean the declaration is in the settings file and the server does not
 * list it yet.
 */
public enum class CustomProviderStage {
    Form,
    Saving,
    Loaded,
    SavedNotLoaded,
    Restarting,
    RestartFailed,
    NotLoadedAfterRestart,
}

/** The add-a-provider sheet's state. What the person types stays in the sheet's own fields. */
public data class CustomProviderUi(
    val stage: CustomProviderStage = CustomProviderStage.Form,
    /** Filled only by Save: an untouched form is never red. */
    val problems: ImmutableList<CustomProviderProblem> = persistentListOf(),
    /** Why nothing was saved (on the form), or why a restart was not asked for (after a save). */
    val refusal: CustomProviderRefusal? = null,
    /** A stable technical label for the Details fold (`Exit 23`, a failure label); never server text. */
    val detail: String? = null,
    val name: String = "",
    val providerId: String = "",
) {
    /** The first problem of [field], or null: one message under each field. */
    public fun problem(field: CustomProviderField): CustomProviderProblem? = problems.firstOrNull { it.field == field }

    /** The stage an engine outcome leads to. A refusal during a restart keeps the saved stage. */
    public fun after(outcome: CustomProviderOutcome): CustomProviderUi =
        when (outcome) {
            CustomProviderOutcome.Loaded -> settled(CustomProviderStage.Loaded)
            CustomProviderOutcome.SavedNotLoaded -> settled(CustomProviderStage.SavedNotLoaded)
            CustomProviderOutcome.NotLoadedAfterRestart -> settled(CustomProviderStage.NotLoadedAfterRestart)
            is CustomProviderOutcome.RestartFailed ->
                settled(CustomProviderStage.RestartFailed).copy(detail = outcome.detail)
            is CustomProviderOutcome.Invalid ->
                copy(stage = CustomProviderStage.Form, problems = outcome.problems.toImmutableList(), refusal = null)
            is CustomProviderOutcome.Refused ->
                copy(
                    stage =
                        if (stage == CustomProviderStage.Restarting) CustomProviderStage.SavedNotLoaded
                        else CustomProviderStage.Form,
                    problems = persistentListOf(),
                    refusal = outcome.reason,
                    detail = outcome.detail,
                )
        }

    private fun settled(next: CustomProviderStage): CustomProviderUi =
        copy(stage = next, problems = persistentListOf(), refusal = null, detail = null)
}

/**
 * The ids the page already shows: the form refuses them before Save reaches the server (which checks again). One
 * row is left out (issue 152): listed with no connection and no catalog entry — only the settings file can still
 * hold it, and the engine's save-time check decides there.
 */
internal fun shownProviderIds(inventory: LibraryInventory): Set<String> {
    val catalog = inventory.catalog

    fun replaceable(provider: IntegrationInfo): Boolean =
        provider.connections.isEmpty() &&
            catalog != null &&
            catalog.providers.none { it.id == provider.id || it.integrationId == provider.id }

    return buildSet {
        inventory.providers.forEach { if (!replaceable(it)) add(it.id) }
        catalog?.providers?.forEach { provider ->
            add(provider.id)
            provider.integrationId?.let { add(it) }
        }
    }
}
