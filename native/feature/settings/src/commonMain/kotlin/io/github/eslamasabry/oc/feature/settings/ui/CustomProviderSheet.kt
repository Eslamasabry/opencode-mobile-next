package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.core.model.IntegrationInfo
import io.github.eslamasabry.oc.engine.integrations.catalog.CustomProviderDraft
import io.github.eslamasabry.oc.engine.integrations.catalog.CustomProviderField
import io.github.eslamasabry.oc.engine.integrations.catalog.CustomProviderProblem
import io.github.eslamasabry.oc.engine.integrations.catalog.CustomProviderRefusal
import io.github.eslamasabry.oc.engine.integrations.catalog.CustomProviderRestartPhase
import io.github.eslamasabry.oc.engine.integrations.catalog.DeclaredProvider
import io.github.eslamasabry.oc.feature.settings.presentation.CustomProviderMode
import io.github.eslamasabry.oc.feature.settings.presentation.CustomProviderStage
import io.github.eslamasabry.oc.feature.settings.presentation.CustomProviderUi
import io.github.eslamasabry.oc.feature.settings.presentation.LibraryActions
import io.github.eslamasabry.oc.feature.settings.presentation.LibrarySheet
import io.github.eslamasabry.oc.feature.settings.presentation.LibraryUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStepProgress
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentsDone
import io.github.eslamasabry.oc.ui.words.res.appUpdateNotNow
import io.github.eslamasabry.oc.ui.words.res.editor40SaveChanges
import io.github.eslamasabry.oc.ui.words.res.filesOpenPhone
import io.github.eslamasabry.oc.ui.words.res.kitConfirmKeepEditing
import io.github.eslamasabry.oc.ui.words.res.kitTourStep
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import io.github.eslamasabry.oc.ui.words.res.nlibChanged
import io.github.eslamasabry.oc.ui.words.res.nlibCopy
import io.github.eslamasabry.oc.ui.words.res.nlibCustomAdd
import io.github.eslamasabry.oc.ui.words.res.nlibCustomAddKey
import io.github.eslamasabry.oc.ui.words.res.nlibCustomAddress
import io.github.eslamasabry.oc.ui.words.res.nlibCustomAddressCredentials
import io.github.eslamasabry.oc.ui.words.res.nlibCustomAddressHelper
import io.github.eslamasabry.oc.ui.words.res.nlibCustomAddressInvalid
import io.github.eslamasabry.oc.ui.words.res.nlibCustomChangedByHand
import io.github.eslamasabry.oc.ui.words.res.nlibCustomEdit
import io.github.eslamasabry.oc.ui.words.res.nlibCustomEditDoneTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomEditHint
import io.github.eslamasabry.oc.ui.words.res.nlibCustomEditSavedBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomId
import io.github.eslamasabry.oc.ui.words.res.nlibCustomIdFixed
import io.github.eslamasabry.oc.ui.words.res.nlibCustomIdHelper
import io.github.eslamasabry.oc.ui.words.res.nlibCustomIdInvalid
import io.github.eslamasabry.oc.ui.words.res.nlibCustomIdMissing
import io.github.eslamasabry.oc.ui.words.res.nlibCustomIdTaken
import io.github.eslamasabry.oc.ui.words.res.nlibCustomIntro
import io.github.eslamasabry.oc.ui.words.res.nlibCustomLoadedBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomLoadedTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomModelInvalid
import io.github.eslamasabry.oc.ui.words.res.nlibCustomModels
import io.github.eslamasabry.oc.ui.words.res.nlibCustomModelsCount
import io.github.eslamasabry.oc.ui.words.res.nlibCustomModelsHelper
import io.github.eslamasabry.oc.ui.words.res.nlibCustomModelsMissing
import io.github.eslamasabry.oc.ui.words.res.nlibCustomModelsTooMany
import io.github.eslamasabry.oc.ui.words.res.nlibCustomName
import io.github.eslamasabry.oc.ui.words.res.nlibCustomNameHint
import io.github.eslamasabry.oc.ui.words.res.nlibCustomNameInvalid
import io.github.eslamasabry.oc.ui.words.res.nlibCustomNameMissing
import io.github.eslamasabry.oc.ui.words.res.nlibCustomNotLoadedBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomNotLoadedTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRefusedBusy
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRefusedCheck
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRefusedDeclared
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRefusedFailed
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRefusedHandKept
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRefusedNotSetUp
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRefusedTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRefusedUnreadable
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRefusedUnsafe
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRemove
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRemoveBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRemoveKey
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRemoveModels
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRemoveNoServer
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRemoveNotGoneBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRemoveNotGoneTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRemoveNothingTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRemovePartBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRemovePartTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRemoveQuestion
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRemoveRestart
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRemoveRestartFailedBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRemoveRestartFailedTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRemovedBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRemovedTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRemovedWaitTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomReplace
import io.github.eslamasabry.oc.ui.words.res.nlibCustomReplaceBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomReplaceKey
import io.github.eslamasabry.oc.ui.words.res.nlibCustomReplaceModels
import io.github.eslamasabry.oc.ui.words.res.nlibCustomReplaceQuestion
import io.github.eslamasabry.oc.ui.words.res.nlibCustomReplaceRestart
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRestart
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRestartFailedBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRestartFailedTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRestartingBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRestartingTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomSave
import io.github.eslamasabry.oc.ui.words.res.nlibCustomSavedBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomSavedTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomStepCheck
import io.github.eslamasabry.oc.ui.words.res.nlibCustomStepCheckGone
import io.github.eslamasabry.oc.ui.words.res.nlibCustomStepStart
import io.github.eslamasabry.oc.ui.words.res.nlibCustomStepStop
import io.github.eslamasabry.oc.ui.words.res.nlibCustomStepsLabel
import io.github.eslamasabry.oc.ui.words.res.nlibCustomUnavailableBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomUnavailableTitle
import io.github.eslamasabry.oc.ui.words.res.nlibDetails
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Add a provider (ported from pull request 142): the form, then where the save ended. On a server whose settings the
 * app does not own (any server but this phone's own OpenCode) it explains why instead of offering a form that cannot
 * save. Every part is a kit part; every word is from `ui/words`; technical detail only in the Details fold.
 */
@Composable
internal fun CustomProviderSheet(state: LibraryUi, actions: LibraryActions) {
    val fields =
        CustomProviderFields(
            name = rememberTextFieldState(),
            id = rememberTextFieldState(),
            address = rememberTextFieldState(),
            models = rememberTextFieldState(),
        )
    val ui = state.customProvider
    when {
        state.inventory.capabilities?.customProviders != true ->
            KitSheet(
                true,
                { actions.show(null) },
                stringResource(Res.string.nlibCustomAdd),
                subtitle = stringResource(Res.string.nlibCustomUnavailableTitle),
            ) {
                KitText(stringResource(Res.string.nlibCustomUnavailableBody))
            }
        ui.stage == CustomProviderStage.Form || ui.stage == CustomProviderStage.Saving ->
            CustomProviderForm(fields, ui, state.working, actions)
        // The id is already an app-written entry (4A): the typed values stay in the fields, and nothing is
        // written before the answer.
        ui.stage == CustomProviderStage.AskReplace -> {
            CustomProviderForm(fields, ui, working = false, actions)
            KitConfirm(
                replaceSpec(ui),
                { actions.replaceProvider(fields.draft()) },
                { actions.keepEditingProvider() },
            )
        }
        else -> CustomProviderResult(state, ui, actions)
    }
}

/** The Replace question of the add sheet (4A): what a confirmed answer overwrites and what stays. */
@Composable
private fun replaceSpec(ui: CustomProviderUi): KitConfirmSpec =
    KitConfirmSpec(
        title = stringResource(Res.string.nlibCustomReplaceQuestion, ui.name),
        confirmLabel = stringResource(Res.string.nlibCustomReplace),
        kind = KitConfirmKind.Neutral,
        body = stringResource(Res.string.nlibCustomReplaceBody, ui.providerId),
        consequences =
            listOf(
                stringResource(Res.string.nlibCustomReplaceModels),
                stringResource(Res.string.nlibCustomReplaceKey),
                stringResource(Res.string.nlibCustomReplaceRestart),
            ),
        cancelLabel = stringResource(Res.string.kitConfirmKeepEditing),
    )

/** The four fields; they outlive a refused save, so nothing typed is lost. */
internal class CustomProviderFields(
    val name: TextFieldState,
    val id: TextFieldState,
    val address: TextFieldState,
    val models: TextFieldState,
) {
    fun draft(): CustomProviderDraft =
        CustomProviderDraft(
            name = name.text.toString(),
            id = id.text.toString(),
            address = address.text.toString(),
            models = models.text.toString(),
        )

    val typed: Boolean
        get() = listOf(name, id, address, models).any { it.text.isNotBlank() }
}

@Composable
private fun CustomProviderForm(
    fields: CustomProviderFields,
    ui: CustomProviderUi,
    working: Boolean,
    actions: LibraryActions,
) {
    KitSheet(
        true,
        { actions.show(null) },
        stringResource(Res.string.nlibCustomAdd),
        dirty = fields.typed,
        dismissible = !working,
        primary =
            KitAction(
                stringResource(Res.string.nlibCustomSave),
                { actions.addProvider(fields.draft()) },
                working = ui.stage == CustomProviderStage.Saving,
            ),
    ) {
        KitText(stringResource(Res.string.nlibCustomIntro))
        ui.refusal?.let { RefusalNotice(it, ui.detail) }
        KitField(
            fields.name,
            stringResource(Res.string.nlibCustomName),
            Modifier.testTag("custom-provider-name"),
            placeholder = stringResource(Res.string.nlibCustomNameHint),
            error = problemWords(ui.problem(CustomProviderField.Name)),
        )
        KitField(
            fields.id,
            stringResource(Res.string.nlibCustomId),
            Modifier.testTag("custom-provider-id"),
            kind = KitFieldKind.Mono,
            helper = stringResource(Res.string.nlibCustomIdHelper),
            error = problemWords(ui.problem(CustomProviderField.Id)),
        )
        KitField(
            fields.address,
            stringResource(Res.string.nlibCustomAddress),
            Modifier.testTag("custom-provider-address"),
            kind = KitFieldKind.Url,
            helper = stringResource(Res.string.nlibCustomAddressHelper),
            error = problemWords(ui.problem(CustomProviderField.Address)),
        )
        KitField(
            fields.models,
            stringResource(Res.string.nlibCustomModels),
            Modifier.testTag("custom-provider-models"),
            kind = KitFieldKind.Multiline,
            helper = stringResource(Res.string.nlibCustomModelsHelper),
            error = problemWords(ui.problem(CustomProviderField.Models)),
        )
    }
}

/** Saved: loaded, waiting for a restart, restarting, a failed restart, or a restart that did not load it. */
@Composable
private fun CustomProviderResult(state: LibraryUi, ui: CustomProviderUi, actions: LibraryActions) {
    val loaded = ui.stage == CustomProviderStage.Loaded
    val row = state.inventory.providers.firstOrNull { it.id == ui.providerId }
    val restart =
        KitAction(
            stringResource(Res.string.nlibCustomRestart),
            { actions.restartForProvider(ui.providerId) },
            working = ui.stage == CustomProviderStage.Restarting,
        )
    val addKey =
        row?.let { provider ->
            KitAction(
                stringResource(Res.string.nlibCustomAddKey, ui.name),
                {
                    val only =
                        soleMethodToConnect(provider, state.inventory.capabilities?.integrationCommandAuth == true)
                    actions.show(
                        if (only != null) LibrarySheet.Method(provider, only) else LibrarySheet.Provider(provider)
                    )
                },
            )
        }
    KitSheet(
        true,
        { actions.show(null) },
        stringResource(Res.string.nlibCustomAdd),
        dismissible = !state.working,
        primary = if (loaded) addKey else restart,
    ) {
        if (ui.stage == CustomProviderStage.Restarting) {
            CustomProviderRestarting(ui.name, expectGone = false, state.inventory.customRestart)
        } else {
            val (title, body) = resultWords(ui.stage, ui.name)
            KitNotice(body, title = title, tone = resultTone(ui.stage))
            // A restart the page could not ask for (the server changed meanwhile): the declaration stays saved.
            ui.refusal?.let { KitNotice(refusalWords(it)) }
            ui.detail?.let { CustomProviderDetails(it) }
        }
    }
}

@Composable
private fun RefusalNotice(refusal: CustomProviderRefusal, detail: String?) {
    KitNotice(
        refusalWords(refusal),
        title = stringResource(Res.string.nlibCustomRefusedTitle),
        tone = StatusTone.Failure,
    )
    detail?.let { CustomProviderDetails(it) }
}

/**
 * The named steps of a custom-provider restart (DESIGN 5A with 5B's named step): waiting is not failing, so the
 * sheet names the step it is on and never an error before the 60th check. `restart()` is the phone host's one call
 * (stop, then the manual start), so the app reports two honest phases: restarting, with stop and start in flight,
 * and checking. The step label is heard by TalkBack only.
 */
@Composable
internal fun CustomProviderRestarting(
    name: String,
    expectGone: Boolean,
    phase: CustomProviderRestartPhase?,
) {
    KitNotice(
        stringResource(Res.string.nlibCustomRestartingBody),
        title = stringResource(Res.string.nlibCustomRestartingTitle),
        tone = StatusTone.Progress,
    )
    KitText(
        stringResource(Res.string.nlibCustomStepsLabel),
        role = TypeRole.Secondary,
        tone = TextTone.Secondary,
    )
    val checking = phase == CustomProviderRestartPhase.Checking
    val done = if (checking) 2 else 1
    KitStepProgress(RESTART_STEPS, done, label = stringResource(Res.string.kitTourStep, done + 1, RESTART_STEPS))
    val steps =
        listOf(
            stringResource(Res.string.nlibCustomStepStop),
            stringResource(Res.string.nlibCustomStepStart),
            stringResource(
                if (expectGone) Res.string.nlibCustomStepCheckGone else Res.string.nlibCustomStepCheck,
                name,
            ),
        )
    steps.forEachIndexed { index, step ->
        val now = if (checking) index == 2 else index < 2
        KitText(step, tone = if (now) TextTone.Primary else TextTone.Tertiary)
    }
}

/**
 * Edit of a provider this app declared (DESIGN 2A): the same fields as Add with the id fixed and its reason said
 * once, the values the settings file holds filled in; then where the edit ended, with the restart it asks for.
 */
@Composable
internal fun EditProviderSheet(sheet: LibrarySheet.EditProvider, state: LibraryUi, actions: LibraryActions) {
    val ui = state.customProvider
    val entry = state.inventory.declared.firstOrNull { it.id == sheet.id && it.appShaped }
    when {
        ui.stage == CustomProviderStage.Editing || ui.stage == CustomProviderStage.Saving ->
            EditProviderForm(sheet.id, entry, ui, state.working, actions)
        ui.stage == CustomProviderStage.Restarting ->
            KitSheet(
                true,
                { actions.show(null) },
                stringResource(Res.string.nlibCustomEdit),
                dismissible = !state.working,
            ) {
                CustomProviderRestarting(ui.name, expectGone = false, state.inventory.customRestart)
            }
        else -> EditProviderResult(ui, state, actions)
    }
}

@Composable
private fun EditProviderForm(
    id: String,
    entry: DeclaredProvider?,
    ui: CustomProviderUi,
    working: Boolean,
    actions: LibraryActions,
) {
    val fields =
        CustomProviderFields(
            name = rememberTextFieldState(entry?.name.orEmpty()),
            id = rememberTextFieldState(id),
            address = rememberTextFieldState(entry?.baseUrl.orEmpty()),
            models = rememberTextFieldState(entry?.models.orEmpty().joinToString("\n")),
        )
    KitSheet(
        true,
        { actions.show(null) },
        stringResource(Res.string.nlibCustomEdit),
        dirty = fields.typed,
        dismissible = !working,
        primary =
            KitAction(
                stringResource(Res.string.editor40SaveChanges),
                { actions.saveProviderEdit(fields.draft()) },
                working = ui.stage == CustomProviderStage.Saving,
            ),
    ) {
        // The id is the key the file holds and the prefix of every model id: fixed, with its reason.
        KitText(id, role = TypeRole.Mono)
        KitText(
            stringResource(Res.string.nlibCustomIdFixed),
            role = TypeRole.Secondary,
            tone = TextTone.Secondary,
        )
        ui.refusal?.let { RefusalNotice(it, ui.detail) }
        EditProviderValueFields(fields, ui)
    }
}

/** The three editable values of an edit: the name, the address and the models, one under each field. */
@Composable
private fun EditProviderValueFields(fields: CustomProviderFields, ui: CustomProviderUi) {
    KitField(
        fields.name,
        stringResource(Res.string.nlibCustomName),
        Modifier.testTag("custom-provider-name"),
        placeholder = stringResource(Res.string.nlibCustomNameHint),
        error = problemWords(ui.problem(CustomProviderField.Name)),
    )
    KitField(
        fields.address,
        stringResource(Res.string.nlibCustomAddress),
        Modifier.testTag("custom-provider-address"),
        kind = KitFieldKind.Url,
        helper = stringResource(Res.string.nlibCustomAddressHelper),
        error = problemWords(ui.problem(CustomProviderField.Address)),
    )
    KitField(
        fields.models,
        stringResource(Res.string.nlibCustomModels),
        Modifier.testTag("custom-provider-models"),
        kind = KitFieldKind.Multiline,
        helper = stringResource(Res.string.nlibCustomModelsHelper),
        error = problemWords(ui.problem(CustomProviderField.Models)),
    )
}

/** Saved, loaded, a failed restart or one that did not load it: the edit's end states (DESIGN §3). */
@Composable
private fun EditProviderResult(ui: CustomProviderUi, state: LibraryUi, actions: LibraryActions) {
    val done = ui.stage == CustomProviderStage.Loaded
    KitSheet(
        true,
        { actions.show(null) },
        stringResource(Res.string.nlibCustomEdit),
        dismissible = !state.working,
        primary =
            if (done)
                KitAction(stringResource(Res.string.agentsDone), { actions.show(null) })
            else
                KitAction(
                    stringResource(Res.string.nlibCustomRestart),
                    { actions.restartForProvider(ui.providerId) },
                ),
        secondary =
            if (ui.stage == CustomProviderStage.SavedNotLoaded)
                KitAction(stringResource(Res.string.appUpdateNotNow), { actions.show(null) })
            else null,
    ) {
        val (title, body) = editResultWords(ui.stage, ui.name)
        KitNotice(body, title = title, tone = editResultTone(ui.stage))
        ui.detail?.let { CustomProviderDetails(it) }
    }
}

@Composable
private fun editResultWords(stage: CustomProviderStage, name: String): Pair<String, String> =
    when (stage) {
        CustomProviderStage.Loaded ->
            stringResource(Res.string.nlibCustomEditDoneTitle, name) to
                stringResource(Res.string.nlibCustomLoadedBody)
        CustomProviderStage.RestartFailed ->
            stringResource(Res.string.nlibCustomRestartFailedTitle, name) to
                stringResource(Res.string.nlibCustomRestartFailedBody)
        CustomProviderStage.NotLoadedAfterRestart ->
            stringResource(Res.string.nlibCustomNotLoadedTitle, name) to
                stringResource(Res.string.nlibCustomNotLoadedBody)
        else ->
            stringResource(Res.string.nlibCustomSavedTitle, name) to
                stringResource(Res.string.nlibCustomEditSavedBody)
    }

private fun editResultTone(stage: CustomProviderStage): StatusTone =
    when (stage) {
        CustomProviderStage.Loaded -> StatusTone.Ok
        CustomProviderStage.RestartFailed,
        CustomProviderStage.NotLoadedAfterRestart -> StatusTone.Attention
        else -> StatusTone.Neutral
    }

/**
 * The provider sheet while a remove (or the restart it asked for) owns it (DESIGN §3): the flow's own words and
 * answers, never the provider's rows. A refusal during the remove keeps the stage it refused in.
 */
@Composable
internal fun ProviderFlowSheet(
    provider: IntegrationInfo,
    flow: CustomProviderUi,
    state: LibraryUi,
    actions: LibraryActions,
) {
    val name = flow.name.ifEmpty { provider.name }
    val tryAgain =
        KitAction(stringResource(Res.string.kitTryAgain), { actions.removeProvider(provider) })
    when (flow.stage) {
        CustomProviderStage.Restarting ->
            KitSheet(true, { actions.show(null) }, name, dismissible = !state.working) {
                CustomProviderRestarting(name, flow.mode == CustomProviderMode.Remove, state.inventory.customRestart)
            }
        CustomProviderStage.RemovePartial -> RemovePartialSheet(name, flow, tryAgain, actions)
        CustomProviderStage.RemovedNotRestarted -> RemoveWaitRestartSheet(provider, name, flow, state, actions)
        CustomProviderStage.Removed -> RemoveDoneSheet(name, actions)
        CustomProviderStage.Removing -> RemoveRefusedSheet(name, flow, tryAgain, actions)
        // A restart from a stranded entry's sheet or a remove that ended: the flow's own words, Details and actions.
        else -> FlowEndedSheet(name, flow, state, actions)
    }
}

/** The remove's key went but its entry stayed: a second remove finishes the job. */
@Composable
private fun RemovePartialSheet(
    name: String,
    flow: CustomProviderUi,
    tryAgain: KitAction,
    actions: LibraryActions,
) {
    KitSheet(true, { actions.show(null) }, name, primary = tryAgain) {
        KitNotice(
            stringResource(Res.string.nlibCustomRemovePartBody),
            title = stringResource(Res.string.nlibCustomRemovePartTitle, name),
            tone = StatusTone.Attention,
        )
        flow.detail?.let { CustomProviderDetails(it) }
    }
}

/** Removed from the settings; the server still lists it until OpenCode restarts (the person's choice). */
@Composable
private fun RemoveWaitRestartSheet(
    provider: IntegrationInfo,
    name: String,
    flow: CustomProviderUi,
    state: LibraryUi,
    actions: LibraryActions,
) {
    KitSheet(
        true,
        { actions.show(null) },
        name,
        dismissible = !state.working,
        primary =
            KitAction(
                stringResource(Res.string.nlibCustomRestart),
                { actions.restartForProvider(provider.id) },
            ),
        secondary = KitAction(stringResource(Res.string.appUpdateNotNow), { actions.show(null) }),
    ) {
        KitNotice(
            stringResource(Res.string.nlibCustomEditSavedBody),
            title = stringResource(Res.string.nlibCustomRemovedWaitTitle, name),
            tone = StatusTone.Neutral,
        )
        flow.refusal?.let { KitNotice(refusalWords(it)) }
        flow.detail?.let { CustomProviderDetails(it) }
    }
}

/** Removed, and the restarted server no longer lists it. */
@Composable
private fun RemoveDoneSheet(name: String, actions: LibraryActions) {
    KitSheet(
        true,
        { actions.show(null) },
        name,
        primary = KitAction(stringResource(Res.string.agentsDone), { actions.show(null) }),
    ) {
        KitNotice(
            stringResource(Res.string.nlibCustomRemovedBody),
            title = stringResource(Res.string.nlibCustomRemovedTitle, name),
            tone = StatusTone.Ok,
        )
    }
}

/**
 * A restart started from a stranded entry's sheet or a remove that ended: the flow's own words, its Details fold,
 * and either Restart again or Add key once the server lists it.
 */
@Composable
private fun FlowEndedSheet(
    name: String,
    flow: CustomProviderUi,
    state: LibraryUi,
    actions: LibraryActions,
) {
    val loaded = flow.stage == CustomProviderStage.Loaded
    val row = state.inventory.providers.firstOrNull { it.id == flow.providerId }
    val primary =
        if (loaded)
            row?.let { provider ->
                KitAction(
                    stringResource(Res.string.nlibCustomAddKey, name),
                    {
                        val only =
                            soleMethodToConnect(
                                provider,
                                state.inventory.capabilities?.integrationCommandAuth == true,
                            )
                        actions.show(
                            if (only != null) LibrarySheet.Method(provider, only)
                            else LibrarySheet.Provider(provider)
                        )
                    },
                )
            }
        else
            KitAction(
                stringResource(Res.string.nlibCustomRestart),
                { actions.restartForProvider(flow.providerId) },
                working = state.working,
            )
    val (title, body) = resultWords(flow.stage, name, flow.mode)
    KitSheet(true, { actions.show(null) }, name, dismissible = !state.working, primary = primary) {
        KitNotice(body, title = title, tone = resultTone(flow.stage))
        flow.refusal?.let { KitNotice(refusalWords(it)) }
        flow.detail?.let { CustomProviderDetails(it) }
    }
}

/** The remove was refused with the server not answering: nothing was removed, and the way forward is named. */
@Composable
private fun RemoveRefusedSheet(
    name: String,
    flow: CustomProviderUi,
    tryAgain: KitAction,
    actions: LibraryActions,
) {
    val serverSilent = flow.refusal == CustomProviderRefusal.CouldNotCheck
    KitSheet(
        true,
        { actions.show(null) },
        name,
        primary = tryAgain,
        secondary =
            if (serverSilent) KitAction(stringResource(Res.string.filesOpenPhone), actions::phoneTools) else null,
    ) {
        KitNotice(
            if (serverSilent) stringResource(Res.string.nlibCustomRemoveNoServer)
            else refusalWords(flow.refusal ?: CustomProviderRefusal.Failed),
            title = stringResource(Res.string.nlibCustomRemoveNothingTitle),
            tone = if (serverSilent) StatusTone.Attention else StatusTone.Failure,
        )
        flow.detail?.let { CustomProviderDetails(it) }
    }
}

/** The stable technical label (an exit status or a failure label), only inside Details. */
@Composable
private fun CustomProviderDetails(detail: String) {
    KitDetailsFold(
        stringResource(Res.string.nlibDetails),
        stringResource(Res.string.nlibCopy),
        notes = listOf(detail),
    )
}

/** The result notice's title (naming the provider [name]) and body for [stage] in [mode]. */
@Composable
private fun resultWords(
    stage: CustomProviderStage,
    name: String,
    mode: CustomProviderMode = CustomProviderMode.Add,
): Pair<String, String> =
    when {
        mode == CustomProviderMode.Remove && stage == CustomProviderStage.NotGoneAfterRestart ->
            stringResource(Res.string.nlibCustomRemoveNotGoneTitle, name) to
                stringResource(Res.string.nlibCustomRemoveNotGoneBody)
        mode == CustomProviderMode.Remove ->
            stringResource(Res.string.nlibCustomRemoveRestartFailedTitle, name) to
                stringResource(Res.string.nlibCustomRemoveRestartFailedBody)
        stage == CustomProviderStage.Loaded ->
            stringResource(Res.string.nlibCustomLoadedTitle, name) to
                stringResource(Res.string.nlibCustomLoadedBody)
        stage == CustomProviderStage.RestartFailed ->
            stringResource(Res.string.nlibCustomRestartFailedTitle, name) to
                stringResource(Res.string.nlibCustomRestartFailedBody)
        stage == CustomProviderStage.NotLoadedAfterRestart ->
            stringResource(Res.string.nlibCustomNotLoadedTitle, name) to
                stringResource(Res.string.nlibCustomNotLoadedBody)
        else ->
            stringResource(Res.string.nlibCustomSavedTitle, name) to
                stringResource(Res.string.nlibCustomSavedBody)
    }

private fun resultTone(stage: CustomProviderStage): StatusTone =
    when (stage) {
        CustomProviderStage.Loaded -> StatusTone.Ok
        CustomProviderStage.Restarting -> StatusTone.Progress
        CustomProviderStage.RestartFailed,
        CustomProviderStage.NotLoadedAfterRestart,
        CustomProviderStage.NotGoneAfterRestart -> StatusTone.Attention
        else -> StatusTone.Neutral
    }

@Composable
private fun problemWords(problem: CustomProviderProblem?): String? =
    problem?.let {
        stringResource(
            when (it) {
                CustomProviderProblem.NameMissing -> Res.string.nlibCustomNameMissing
                CustomProviderProblem.NameInvalid -> Res.string.nlibCustomNameInvalid
                CustomProviderProblem.IdMissing -> Res.string.nlibCustomIdMissing
                CustomProviderProblem.IdInvalid -> Res.string.nlibCustomIdInvalid
                CustomProviderProblem.IdTaken -> Res.string.nlibCustomIdTaken
                CustomProviderProblem.AddressInvalid -> Res.string.nlibCustomAddressInvalid
                CustomProviderProblem.AddressHasCredentials -> Res.string.nlibCustomAddressCredentials
                CustomProviderProblem.ModelsMissing -> Res.string.nlibCustomModelsMissing
                CustomProviderProblem.ModelInvalid -> Res.string.nlibCustomModelInvalid
                CustomProviderProblem.TooManyModels -> Res.string.nlibCustomModelsTooMany
            }
        )
    }

@Composable
private fun refusalWords(refusal: CustomProviderRefusal): String =
    stringResource(
        when (refusal) {
            CustomProviderRefusal.Unavailable -> Res.string.nlibCustomUnavailableBody
            CustomProviderRefusal.Changed -> Res.string.nlibChanged
            CustomProviderRefusal.CouldNotCheck -> Res.string.nlibCustomRefusedCheck
            CustomProviderRefusal.NotSetUp -> Res.string.nlibCustomRefusedNotSetUp
            CustomProviderRefusal.Unreadable -> Res.string.nlibCustomRefusedUnreadable
            CustomProviderRefusal.HandManaged -> Res.string.nlibCustomRefusedHandKept
            CustomProviderRefusal.Unsafe -> Res.string.nlibCustomRefusedUnsafe
            CustomProviderRefusal.Busy -> Res.string.nlibCustomRefusedBusy
            CustomProviderRefusal.AlreadyDeclared -> Res.string.nlibCustomRefusedDeclared
            CustomProviderRefusal.ChangedByHand -> Res.string.nlibCustomChangedByHand
            CustomProviderRefusal.Failed -> Res.string.nlibCustomRefusedFailed
        }
    )

/** A custom-provider restart is three steps: stop, start, check. */
private const val RESTART_STEPS = 3
