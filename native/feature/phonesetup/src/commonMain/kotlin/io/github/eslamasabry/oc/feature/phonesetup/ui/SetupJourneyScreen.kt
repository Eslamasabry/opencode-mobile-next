package io.github.eslamasabry.oc.feature.phonesetup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.engine.runtime.setup.*
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyActions
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.words.res.*
import kotlin.math.ceil
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.compose.resources.stringResource

/** One durable setup, with dependency-aware first-install and add-tools choices. */
@Composable
public fun SetupJourneyScreen(state: SetupJourneyUi, actions: SetupJourneyActions) {
    LifecycleStartEffect(actions) {
        actions.retry()
        onStopOrDispose {}
    }
    val job = state.progress
    val idle = job.state == SetupState.Idle
    val title =
        when {
            state.customizing && state.adding -> Res.string.phoneSetupStartAddTitle
            state.customizing -> Res.string.phoneSetupStartCustomizeTitle
            idle -> Res.string.phoneSetupStartScreenTitle
            else -> Res.string.phoneSetupProgressTitle
        }
    KitScreen(
        topBar = {
            KitTopBar(
                stringResource(title),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
        loading = state.checking,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            if (state.customizing || idle) {
                Choices(state, actions)
            } else {
                Job(state, actions)
            }
            if (state.failure != null && state.confirmStop == null && !state.confirmRemoveVoice) {
                KitErrorNotice(
                    message = stringResource(Res.string.r40SetupCouldNotCheck),
                    retry = KitLineAction(stringResource(Res.string.r40SetupRetry), actions::retry),
                )
            }
        }
        KitSheet(
            visible = state.confirmStop != null,
            onDismissRequest = actions::cancelConfirm,
            title = stringResource(Res.string.phoneSetupProgressStopTitle),
            dismissible = !state.working,
            primary =
                KitAction(
                    stringResource(Res.string.phoneSetupProgressStopConfirm),
                    actions::confirmStop,
                    working = state.working,
                ),
            secondary =
                KitAction(
                    stringResource(Res.string.phoneSetupProgressKeepGoing),
                    actions::cancelConfirm,
                ),
        ) {
            KitText(stringResource(Res.string.phoneSetupProgressStopMessage))
            if (state.failure != null)
                KitErrorNotice(stringResource(Res.string.r40SetupActionFailed))
        }
        KitSheet(
            visible = state.confirmRemoveVoice,
            onDismissRequest = actions::cancelConfirm,
            title = stringResource(Res.string.r40SetupRemoveVoiceTitle),
            dismissible = !state.working,
            primary =
                KitAction(
                    stringResource(Res.string.r40SetupRemoveVoice),
                    actions::confirmRemoveVoice,
                    destructive = true,
                    working = state.working,
                ),
            secondary =
                KitAction(stringResource(Res.string.r40SetupKeepVoice), actions::cancelConfirm),
        ) {
            KitText(stringResource(Res.string.r40SetupRemoveVoiceBody))
            state.options
                ?.components
                ?.firstOrNull { it.id == SetupComponentIds.VOICE }
                ?.installedBytes
                ?.let {
                    KitText(journeySize(it))
                }
            if (state.failure != null)
                KitErrorNotice(stringResource(Res.string.r40SetupRemoveVoiceFailed))
        }
    }
}

@Composable
private fun Choices(state: SetupJourneyUi, actions: SetupJourneyActions) {
    if (state.checking) {
        KitText(stringResource(Res.string.phoneSetupStartChecking))
        return
    }
    val options = state.options ?: return
    val choices = options.components.filter { !it.jobStep && (!state.adding || !it.required) }
    if (!state.customizing) KitText(stringResource(Res.string.phoneSetupStartHeadline))
    for (component in choices) {
        val installed = component.id in options.installed
        val required = component.required
        val included = required || installed || component.id in state.chosen
        val supporting =
            when {
                installed -> stringResource(Res.string.phoneSetupStartInstalled)
                required -> stringResource(Res.string.r40SetupRequired)
                else -> component.summary ?: component.why
            }
        if (state.customizing) {
            KitSwitchRow(
                title = itemTitle(component.id, component.title),
                checked = included,
                supporting = supporting,
                locked = installed || required,
                enabled = KitEnabled.of(!state.working, stringResource(Res.string.r40SetupWorking)),
                modifier = Modifier.testTag("phone-setup-customize-${component.id}"),
                onCheckedChange = { actions.toggle(component.id, it) },
            )
            if (installed && component.id == SetupComponentIds.VOICE) {
                KitButton(
                    stringResource(Res.string.r40SetupRemoveVoice),
                    actions::askRemoveVoice,
                    role = KitButtonRole.Tertiary,
                    enabled =
                        KitEnabled.of(!state.working, stringResource(Res.string.r40SetupWorking)),
                )
            }
        } else if (included)
            KitRow(itemTitle(component.id, component.title), supporting = supporting)
    }
    KitText(
        if (state.adding && state.install.isEmpty())
            stringResource(Res.string.phoneSetupStartNothingChosen)
        else
            stringResource(
                Res.string.r40SetupTotals,
                journeySize(state.bytes),
                Numbers.format(ceil(state.seconds / 60.0).toLong().coerceAtLeast(1)),
            ),
        Modifier.testTag("phone-setup-customize-totals"),
    )
    Preflight(state, actions)
    if (state.storageUnopened) KitErrorNotice(stringResource(Res.string.r40SetupStorageUnopened))
    val disabled =
        when {
            state.failure != null -> stringResource(Res.string.r40SetupCouldNotCheck)
            state.adding && state.install.isEmpty() ->
                stringResource(Res.string.phoneSetupStartNothingChosen)
            state.preflight?.supported == false -> stringResource(Res.string.r40SetupCheckDevice)
            else -> stringResource(Res.string.r40SetupWorking)
        }
    KitButton(
        stringResource(
            when {
                state.customizing && state.adding -> Res.string.phoneSetupStartAdd
                state.customizing -> Res.string.phoneSetupStartDone
                else -> Res.string.phoneSetupStartSetUp
            }
        ),
        if (state.customizing) actions::accept else actions::start,
        modifier = Modifier.testTag("phone-setup-customize-done"),
        enabled = KitEnabled.of(state.canConfirm && state.failure == null, disabled),
        working = state.working,
    )
    if (!state.customizing)
        KitButton(
            stringResource(Res.string.phoneSetupStartCustomize),
            actions::customize,
            role = KitButtonRole.Secondary,
        )
}

@Composable
private fun Preflight(state: SetupJourneyUi, actions: SetupJourneyActions) {
    when (val check = state.preflight) {
        is SetupPreflightResult.UnsupportedAbi ->
            KitNotice(
                title = stringResource(Res.string.phoneSetupPreflightUnsupportedHeadline),
                message =
                    stringResource(
                        Res.string.phoneSetupPreflightUnsupportedBody,
                        check.reportedAbi,
                    ),
                tone = StatusTone.Failure,
            )
        is SetupPreflightResult.LowMemory ->
            KitNotice(
                title = stringResource(Res.string.phoneSetupPreflightLowMemoryHeadline),
                message =
                    stringResource(
                        Res.string.phoneSetupPreflightLowMemoryBody,
                        Numbers.format(SetupPreflight.MINIMUM_MEMORY_MB),
                        Numbers.format(check.totalMemoryMb),
                    ),
                tone = StatusTone.Failure,
            )
        is SetupPreflightResult.LowSpace ->
            KitNotice(
                title = stringResource(Res.string.phoneSetupPreflightLowSpaceHeadline),
                message =
                    stringResource(
                        Res.string.phoneSetupPreflightLowSpaceBody,
                        journeySize(check.bytesToFree),
                    ),
                tone = StatusTone.Failure,
                actions =
                    listOf(
                        KitLineAction(
                            stringResource(Res.string.phoneSetupPreflightOpenStorage),
                            actions::openStorage,
                        )
                    ),
            )
        is SetupPreflightResult.MayBeSlow ->
            KitNotice(
                stringResource(
                    Res.string.phoneSetupPreflightMayBeSlow,
                    Numbers.format(check.totalMemoryMb),
                )
            )
        else -> Unit
    }
}

@Composable
private fun Job(state: SetupJourneyUi, actions: SetupJourneyActions) {
    val progress = state.progress
    KitProgressView(
        progress =
            KitProgress.Known(
                progress.overall.toFloat().coerceIn(0f, 1f),
                eta = progress.etaSeconds?.seconds,
            ),
        semanticsLabel = stringResource(Res.string.setupProgressViewOverallLabel),
        tone =
            when (progress.state) {
                SetupState.Failed -> KitProgressTone.Failed
                SetupState.Running,
                SetupState.Done -> KitProgressTone.Moving
                else -> KitProgressTone.Stopped
            },
    )
    KitText(
        stringResource(
            Res.string.r40SetupPercent,
            Numbers.format(
                (progress.overall * 100)
                    .toInt()
                    .coerceIn(0, if (progress.state == SetupState.Done) 100 else 99)
            ),
        )
    )
    for (component in progress.components) {
        val title =
            state.options?.components?.firstOrNull { it.id == component.id }?.title ?: component.id
        val label =
            when (component.state) {
                ComponentState.Done -> Res.string.r40SetupComponentDone
                ComponentState.Skipped -> Res.string.r40SetupComponentInstalled
                ComponentState.Checking -> Res.string.r40SetupComponentChecking
                ComponentState.Running -> Res.string.r40SetupComponentRunning
                ComponentState.Failed -> Res.string.r40SetupComponentFailed
                ComponentState.Pending -> Res.string.r40SetupComponentPending
            }
        KitRow(
            itemTitle(component.id, title),
            supporting = component.stage ?: stringResource(label),
        )
        val done = component.bytesDone
        val total = component.bytesTotal
        if (done != null && total != null && total > 0)
            KitText(
                stringResource(
                    Res.string.setupProgressViewBytes,
                    journeySize(done),
                    journeySize(total),
                )
            )
    }
    when (progress.state) {
        SetupState.Running ->
            KitButton(
                stringResource(Res.string.setupProgressViewCancel),
                actions::askStop,
                role = KitButtonRole.Secondary,
            )
        SetupState.Done -> {
            KitNotice(
                title = stringResource(Res.string.phoneSetupReadyTitle),
                message = stringResource(Res.string.phoneSetupStartReadyBody),
                tone = StatusTone.Ok,
            )
            KitButton(stringResource(Res.string.phoneSetupStartOpen), actions::done)
        }
        SetupState.Failed,
        SetupState.Interrupted,
        SetupState.Cancelled -> {
            KitErrorNotice(
                message =
                    when (progress.state) {
                        SetupState.Interrupted ->
                            stringResource(Res.string.setupProgressViewInterrupted)
                        SetupState.Cancelled ->
                            stringResource(Res.string.setupProgressViewCancelled)
                        else -> runtimeProblem(progress.error)
                    },
                title = stringResource(Res.string.setupProgressViewFailedTitle),
                details = state.details,
                detailsLabel = stringResource(Res.string.kitDetails),
                copyLabel = stringResource(Res.string.kitCopyDetails),
            )
            KitButton(
                stringResource(Res.string.setupProgressViewContinue),
                actions::continueSetup,
                working = state.working,
            )
        }
        SetupState.Idle -> Unit
    }
}

@Composable
private fun runtimeProblem(error: SetupError?): String =
    when (error) {
        is SetupError.NoSpace -> stringResource(Res.string.phoneSetupErrorNoSpace, error.title)
        is SetupError.Offline -> stringResource(Res.string.phoneSetupErrorOffline, error.title)
        is SetupError.Checksum -> stringResource(Res.string.phoneSetupErrorChecksum, error.title)
        is SetupError.Install -> stringResource(Res.string.phoneSetupErrorInstall, error.title)
        SetupError.NoInternet -> stringResource(Res.string.phoneSetupErrorNoInternet)
        SetupError.PackageRepair -> stringResource(Res.string.phoneSetupErrorPackageRepair)
        SetupError.CannotStart,
        SetupError.OwnershipUnknown -> stringResource(Res.string.phoneSetupErrorCannotStart)
        SetupError.OpenCodeNoProgram -> stringResource(Res.string.phoneSetupErrorOpenCodeNoProgram)
        SetupError.OpenCodeWontRun -> stringResource(Res.string.phoneSetupErrorOpenCodeWontRun)
        SetupError.OpenCodeNoStart -> stringResource(Res.string.phoneSetupErrorOpenCodeNoStart)
        else -> stringResource(Res.string.setupProgressViewFailedUnknown)
    }

@Composable
internal fun journeySize(bytes: Long): String =
    if (bytes >= 1_000_000_000)
        stringResource(
            Res.string.phoneSetupStartGigabytes,
            Numbers.decimal(bytes / 1_000_000_000.0, 1),
        )
    else
        stringResource(
            Res.string.phoneSetupStartMegabytes,
            Numbers.format(ceil(bytes / 1_000_000.0).toLong()),
        )
