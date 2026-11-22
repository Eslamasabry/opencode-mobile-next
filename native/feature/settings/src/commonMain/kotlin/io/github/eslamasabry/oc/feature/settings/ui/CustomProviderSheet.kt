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
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.nlibChanged
import io.github.eslamasabry.oc.ui.words.res.nlibCopy
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
import io.github.eslamasabry.oc.ui.words.res.nlibCustomLoadedBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomLoadedTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomModelInvalid
import io.github.eslamasabry.oc.ui.words.res.nlibCustomModels
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
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRestart
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRestartFailedBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRestartFailedTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomSave
import io.github.eslamasabry.oc.ui.words.res.nlibCustomSavedBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomSavedTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomUnavailableBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomUnavailableTitle
import io.github.eslamasabry.oc.ui.words.res.nlibDetails
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
        else -> CustomProviderResult(state, ui, actions)
    }
}

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
        val (title, body) = resultWords(ui.stage, ui.name)
        KitNotice(body, title = title, tone = resultTone(ui.stage))
        // A restart the page could not ask for (the server changed meanwhile): the declaration stays saved.
        ui.refusal?.let { KitNotice(refusalWords(it)) }
        ui.detail?.let { CustomProviderDetails(it) }
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

/** The stable technical label (an exit status or a failure label), only inside Details. */
@Composable
private fun CustomProviderDetails(detail: String) {
    KitDetailsFold(
        stringResource(Res.string.nlibDetails),
        stringResource(Res.string.nlibCopy),
        notes = listOf(detail),
    )
}

/** The result notice's title (naming the provider [name]) and body for [stage]. */
@Composable
private fun resultWords(stage: CustomProviderStage, name: String): Pair<String, String> =
    when (stage) {
        CustomProviderStage.Loaded ->
            stringResource(Res.string.nlibCustomLoadedTitle, name) to stringResource(Res.string.nlibCustomLoadedBody)
        CustomProviderStage.RestartFailed ->
            stringResource(Res.string.nlibCustomRestartFailedTitle, name) to
                stringResource(Res.string.nlibCustomRestartFailedBody)
        CustomProviderStage.NotLoadedAfterRestart ->
            stringResource(Res.string.nlibCustomNotLoadedTitle, name) to stringResource(Res.string.nlibCustomNotLoadedBody)
        else -> stringResource(Res.string.nlibCustomSavedTitle, name) to stringResource(Res.string.nlibCustomSavedBody)
    }

private fun resultTone(stage: CustomProviderStage): StatusTone =
    when (stage) {
        CustomProviderStage.Loaded -> StatusTone.Ok
        CustomProviderStage.Restarting -> StatusTone.Progress
        CustomProviderStage.RestartFailed,
        CustomProviderStage.NotLoadedAfterRestart -> StatusTone.Attention
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
            CustomProviderRefusal.Failed -> Res.string.nlibCustomRefusedFailed
        }
    )
