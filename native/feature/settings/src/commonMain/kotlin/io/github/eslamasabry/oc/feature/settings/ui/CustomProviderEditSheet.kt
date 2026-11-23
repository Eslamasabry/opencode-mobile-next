package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.engine.integrations.catalog.CustomProviderField
import io.github.eslamasabry.oc.engine.integrations.catalog.DeclaredProvider
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
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentsDone
import io.github.eslamasabry.oc.ui.words.res.appUpdateNotNow
import io.github.eslamasabry.oc.ui.words.res.editor40SaveChanges
import io.github.eslamasabry.oc.ui.words.res.nlibCustomAddress
import io.github.eslamasabry.oc.ui.words.res.nlibCustomAddressHelper
import io.github.eslamasabry.oc.ui.words.res.nlibCustomEdit
import io.github.eslamasabry.oc.ui.words.res.nlibCustomEditDoneTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomEditSavedBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomIdFixed
import io.github.eslamasabry.oc.ui.words.res.nlibCustomLoadedBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomModels
import io.github.eslamasabry.oc.ui.words.res.nlibCustomModelsHelper
import io.github.eslamasabry.oc.ui.words.res.nlibCustomName
import io.github.eslamasabry.oc.ui.words.res.nlibCustomNameHint
import io.github.eslamasabry.oc.ui.words.res.nlibCustomNotLoadedBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomNotLoadedTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRestart
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRestartFailedBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRestartFailedTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomSavedTitle
import org.jetbrains.compose.resources.stringResource

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
