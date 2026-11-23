package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.engine.integrations.catalog.CustomProviderDraft
import io.github.eslamasabry.oc.engine.integrations.catalog.CustomProviderField
import io.github.eslamasabry.oc.engine.integrations.catalog.CustomProviderProblem
import io.github.eslamasabry.oc.engine.integrations.catalog.CustomProviderRefusal
import io.github.eslamasabry.oc.feature.settings.presentation.CustomProviderStage
import io.github.eslamasabry.oc.feature.settings.presentation.CustomProviderUi
import io.github.eslamasabry.oc.feature.settings.presentation.LibraryActions
import io.github.eslamasabry.oc.feature.settings.presentation.LibrarySheet
import io.github.eslamasabry.oc.feature.settings.presentation.LibraryUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitConfirmKeepEditing
import io.github.eslamasabry.oc.ui.words.res.nlibCustomAdd
import io.github.eslamasabry.oc.ui.words.res.nlibCustomAddKey
import io.github.eslamasabry.oc.ui.words.res.nlibCustomAddress
import io.github.eslamasabry.oc.ui.words.res.nlibCustomAddressCredentials
import io.github.eslamasabry.oc.ui.words.res.nlibCustomAddressHelper
import io.github.eslamasabry.oc.ui.words.res.nlibCustomAddressInvalid
import io.github.eslamasabry.oc.ui.words.res.nlibCustomId
import io.github.eslamasabry.oc.ui.words.res.nlibCustomIdHelper
import io.github.eslamasabry.oc.ui.words.res.nlibCustomIdInvalid
import io.github.eslamasabry.oc.ui.words.res.nlibCustomIdMissing
import io.github.eslamasabry.oc.ui.words.res.nlibCustomIdTaken
import io.github.eslamasabry.oc.ui.words.res.nlibCustomIntro
import io.github.eslamasabry.oc.ui.words.res.nlibCustomModelInvalid
import io.github.eslamasabry.oc.ui.words.res.nlibCustomModels
import io.github.eslamasabry.oc.ui.words.res.nlibCustomModelsHelper
import io.github.eslamasabry.oc.ui.words.res.nlibCustomModelsMissing
import io.github.eslamasabry.oc.ui.words.res.nlibCustomModelsTooMany
import io.github.eslamasabry.oc.ui.words.res.nlibCustomName
import io.github.eslamasabry.oc.ui.words.res.nlibCustomNameHint
import io.github.eslamasabry.oc.ui.words.res.nlibCustomNameInvalid
import io.github.eslamasabry.oc.ui.words.res.nlibCustomNameMissing
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRefusedTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomReplace
import io.github.eslamasabry.oc.ui.words.res.nlibCustomReplaceBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomReplaceKey
import io.github.eslamasabry.oc.ui.words.res.nlibCustomReplaceModels
import io.github.eslamasabry.oc.ui.words.res.nlibCustomReplaceQuestion
import io.github.eslamasabry.oc.ui.words.res.nlibCustomReplaceRestart
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRestart
import io.github.eslamasabry.oc.ui.words.res.nlibCustomSave
import io.github.eslamasabry.oc.ui.words.res.nlibCustomUnavailableBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomUnavailableTitle
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
internal fun RefusalNotice(refusal: CustomProviderRefusal, detail: String?) {
    KitNotice(
        refusalWords(refusal),
        title = stringResource(Res.string.nlibCustomRefusedTitle),
        tone = StatusTone.Failure,
    )
    detail?.let { CustomProviderDetails(it) }
}

@Composable
internal fun problemWords(problem: CustomProviderProblem?): String? =
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
