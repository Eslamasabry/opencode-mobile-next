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
 * Where the add-, edit- or remove-a-provider sheet is (pull request 142, DESIGN §3). "Saved" and "loaded" are never
 * one stage: [SavedNotLoaded], [RestartFailed] and [NotLoadedAfterRestart] all mean the declaration is in the
 * settings file and the server does not list it yet; [RemovedNotRestarted] means the entry is gone and the server
 * still lists it.
 */
public enum class CustomProviderStage {
    Form,
    Saving,
    Loaded,
    SavedNotLoaded,
    Restarting,
    RestartFailed,
    NotLoadedAfterRestart,
    /** The edit form: the id fixed, the other fields filled from the declared entry. */
    Editing,
    /** A remove is running (its confirmation's answer stays working); a refusal keeps this stage and its reason. */
    Removing,
    /** The entry left the settings file; the server still lists it until OpenCode restarts. */
    RemovedNotRestarted,
    /** The saved key is gone but the entry stayed: a second remove finishes the job. */
    RemovePartial,
    /** The id is already an app-written entry: Save asks before anything is written (4A). */
    AskReplace,
    /** Removed, and the restarted server no longer lists it. */
    Removed,
}

/** Which flow owns the sheet: the words of a state differ by flow (DESIGN §3). */
public enum class CustomProviderMode {
    Add,
    Edit,
    Remove,
}

/** The add-, edit- or remove-a-provider sheet's state. What the person types stays in the sheet's own fields. */
public data class CustomProviderUi(
    val stage: CustomProviderStage = CustomProviderStage.Form,
    val mode: CustomProviderMode = CustomProviderMode.Add,
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

    /**
     * True when this state owns the provider sheet of [id]: a remove, from its question's answer to its end, or a
     * restart started from that sheet. The provider sheet then shows the flow's own words.
     */
    public fun ownsProviderSheet(id: String): Boolean =
        providerId == id &&
            (mode == CustomProviderMode.Remove || stage == CustomProviderStage.Restarting)

    /** Clears a stale refused-remove state when a provider sheet is reopened; in-progress or waiting states stay. */
    public fun reopened(): CustomProviderUi = this

    /** The stage an engine outcome leads to. A refusal during a restart keeps the saved stage of its flow. */
    public fun after(outcome: CustomProviderOutcome): CustomProviderUi =
        when (outcome) {
            CustomProviderOutcome.Loaded -> settled(CustomProviderStage.Loaded)
            CustomProviderOutcome.SavedNotLoaded -> settled(CustomProviderStage.SavedNotLoaded)
            CustomProviderOutcome.NeedsReplaceAnswer -> settled(CustomProviderStage.AskReplace)
            CustomProviderOutcome.RemovedNotRestarted -> settled(CustomProviderStage.RemovedNotRestarted)
            CustomProviderOutcome.Removed -> settled(CustomProviderStage.Removed)
            CustomProviderOutcome.NotLoadedAfterRestart -> settled(CustomProviderStage.NotLoadedAfterRestart)
            CustomProviderOutcome.NotGoneAfterRestart -> goneAfterRestart()
            is CustomProviderOutcome.RestartFailed ->
                settled(savedOrRemoved(CustomProviderStage.RestartFailed)).copy(detail = outcome.detail)
            is CustomProviderOutcome.Invalid ->
                copy(
                    stage =
                        if (mode == CustomProviderMode.Edit) CustomProviderStage.Editing
                        else CustomProviderStage.Form,
                    problems = outcome.problems.toImmutableList(),
                    refusal = null,
                )
            is CustomProviderOutcome.RemovePartial ->
                copy(
                    stage = CustomProviderStage.RemovePartial,
                    problems = persistentListOf(),
                    refusal = null,
                    detail = outcome.detail,
                )
            is CustomProviderOutcome.Refused ->
                copy(
                    stage = refusedStage(),
                    problems = persistentListOf(),
                    refusal = outcome.reason,
                    detail = outcome.detail,
                )
        }

    /**
     * A remove whose server still lists it: the entry IS gone from the settings, so the sheet says that and
     * offers the restart again (DESIGN §3, "removed, waiting for the restart").
     */
    private fun goneAfterRestart(): CustomProviderUi =
        if (mode == CustomProviderMode.Remove) settled(CustomProviderStage.RemovedNotRestarted)
        else settled(CustomProviderStage.NotLoadedAfterRestart)

    /**
     * A restart-time outcome in a remove keeps the removed-waiting stage; an add or an edit keeps its own
     * [fallback] saved stage.
     */
    private fun savedOrRemoved(fallback: CustomProviderStage): CustomProviderStage =
        if (mode == CustomProviderMode.Remove) CustomProviderStage.RemovedNotRestarted else fallback

    /** A refusal keeps the stage its flow was in, so the sheet keeps the flow's own words. */
    private fun refusedStage(): CustomProviderStage =
        when (stage) {
            CustomProviderStage.Restarting -> savedOrRemoved(CustomProviderStage.SavedNotLoaded)
            // A remove's refusals keep their stage: the sheet shows the remove's own words.
            CustomProviderStage.Removing -> CustomProviderStage.Removing
            else ->
                if (mode == CustomProviderMode.Edit) CustomProviderStage.Editing
                else CustomProviderStage.Form
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
