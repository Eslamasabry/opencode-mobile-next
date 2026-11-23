package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.core.model.IntegrationInfo
import io.github.eslamasabry.oc.engine.integrations.catalog.CustomProviderRefusal
import io.github.eslamasabry.oc.feature.settings.presentation.CustomProviderMode
import io.github.eslamasabry.oc.feature.settings.presentation.CustomProviderStage
import io.github.eslamasabry.oc.feature.settings.presentation.CustomProviderUi
import io.github.eslamasabry.oc.feature.settings.presentation.LibraryActions
import io.github.eslamasabry.oc.feature.settings.presentation.LibrarySheet
import io.github.eslamasabry.oc.feature.settings.presentation.LibraryUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentsDone
import io.github.eslamasabry.oc.ui.words.res.appUpdateNotNow
import io.github.eslamasabry.oc.ui.words.res.filesOpenPhone
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import io.github.eslamasabry.oc.ui.words.res.nlibCustomAddKey
import io.github.eslamasabry.oc.ui.words.res.nlibCustomEditSavedBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRemoveNoServer
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRemoveNothingTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRemovePartBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRemovePartTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRemovedBody
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRemovedTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRemovedWaitTitle
import io.github.eslamasabry.oc.ui.words.res.nlibCustomRestart
import org.jetbrains.compose.resources.stringResource

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
