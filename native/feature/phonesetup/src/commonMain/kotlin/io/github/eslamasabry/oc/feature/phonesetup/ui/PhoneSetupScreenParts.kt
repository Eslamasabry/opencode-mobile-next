package io.github.eslamasabry.oc.feature.phonesetup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.linuxsetup.ComponentProgress
import io.github.eslamasabry.oc.engine.linuxsetup.ComponentState
import io.github.eslamasabry.oc.engine.linuxsetup.SetupPreflightResult
import io.github.eslamasabry.oc.engine.linuxsetup.SetupProblem
import io.github.eslamasabry.oc.engine.linuxsetup.SetupProgress
import io.github.eslamasabry.oc.engine.linuxsetup.SetupState
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneSetupActions
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneSetupUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.layout.Layout
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import io.github.eslamasabry.oc.ui.kit.state.KitProgressTone
import io.github.eslamasabry.oc.ui.kit.state.KitProgressView
import io.github.eslamasabry.oc.ui.kit.setup.KitSetupPhoneMood
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatsHomeNewChat
import io.github.eslamasabry.oc.ui.words.res.diagnosticsUnavailable
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitReportProblem
import io.github.eslamasabry.oc.ui.words.res.phoneSetupNodeConnectComputer
import io.github.eslamasabry.oc.ui.words.res.phoneSetupNodeReportFailure
import io.github.eslamasabry.oc.ui.words.res.phoneSetupPreflightLowMemoryBody
import io.github.eslamasabry.oc.ui.words.res.phoneSetupPreflightLowMemoryHeadline
import io.github.eslamasabry.oc.ui.words.res.phoneSetupPreflightLowSpaceBody
import io.github.eslamasabry.oc.ui.words.res.phoneSetupPreflightLowSpaceHeadline
import io.github.eslamasabry.oc.ui.words.res.phoneSetupPreflightMayBeSlow
import io.github.eslamasabry.oc.ui.words.res.phoneSetupPreflightOpenStorage
import io.github.eslamasabry.oc.ui.words.res.phoneSetupPreflightUnsupportedBody
import io.github.eslamasabry.oc.ui.words.res.phoneSetupPreflightUnsupportedHeadline
import io.github.eslamasabry.oc.ui.words.res.phoneSetupProgressKeepGoing
import io.github.eslamasabry.oc.ui.words.res.phoneSetupProgressStopConfirm
import io.github.eslamasabry.oc.ui.words.res.phoneSetupProgressStopMessage
import io.github.eslamasabry.oc.ui.words.res.phoneSetupProgressStopTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupReadyTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupRingSafeToLeaveQuiet
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartReadyBody
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartSetUp
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewBytes
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewCancel
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewCancelled
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewContinue
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewFailedTitle
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewFailedUnknown
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewInterrupted
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewOverallLabel
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewPercent
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/** The stop confirm of the setup screens: Stop with its reason, Keep going as the way back. */
@Composable
internal fun SetupStopConfirm(state: PhoneSetupUi, actions: PhoneSetupActions) {
    KitConfirm(
        spec =
            if (state.confirmingStop) {
                KitConfirmSpec(
                    title = stringResource(Res.string.phoneSetupProgressStopTitle),
                    confirmLabel = stringResource(Res.string.phoneSetupProgressStopConfirm),
                    kind = KitConfirmKind.Stop,
                    body = stringResource(Res.string.phoneSetupProgressStopMessage),
                    cancelLabel = stringResource(Res.string.phoneSetupProgressKeepGoing),
                )
            } else {
                null
            },
        onConfirm = actions::stop,
        onCancel = actions::keepGoing,
    )
}

/** The preflight notices of the start page: what the phone itself says about the setup to come. */
@Composable
internal fun StartPreflightNotices(preflight: SetupPreflightResult?, actions: PhoneSetupActions) {
    when (preflight) {
        is SetupPreflightResult.UnsupportedAbi ->
            KitNotice(
                title = stringResource(Res.string.phoneSetupPreflightUnsupportedHeadline),
                message =
                    stringResource(
                        Res.string.phoneSetupPreflightUnsupportedBody,
                        preflight.reportedAbi,
                    ),
                tone = StatusTone.Failure,
            )
        is SetupPreflightResult.LowMemory ->
            KitNotice(
                title = stringResource(Res.string.phoneSetupPreflightLowMemoryHeadline),
                message =
                    stringResource(
                        Res.string.phoneSetupPreflightLowMemoryBody,
                        "${io.github.eslamasabry.oc.engine.linuxsetup.SetupPreflight.MINIMUM_MEMORY_MB}",
                        "${preflight.totalMemoryMb}",
                    ),
                tone = StatusTone.Failure,
            )
        is SetupPreflightResult.LowSpace ->
            KitNotice(
                title = stringResource(Res.string.phoneSetupPreflightLowSpaceHeadline),
                message =
                    stringResource(
                        Res.string.phoneSetupPreflightLowSpaceBody,
                        sizeWords(preflight.bytesToFree),
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
                    "${preflight.totalMemoryMb}",
                )
            )
        else -> Unit
    }
}

/** The foot of the start page: the installed-parts list (narrow) and the Set up button. */
@Composable
internal fun StartControls(state: PhoneSetupUi, actions: PhoneSetupActions, wide: Boolean) {
    if (!wide) {
        for (item in state.items) KitRow(title = itemTitle(item.id, item.title))
    }
    if (state.preflight?.supported != false) {
        if (wide) {
            Box(Modifier.padding(top = Space.S4.dp).widthIn(max = Layout.CONTROL_MAX_WIDTH.dp)) {
                KitButton(
                    stringResource(Res.string.phoneSetupStartSetUp),
                    actions::setUp,
                    working = state.starting,
                    fullWidth = true,
                    hero = true,
                )
            }
        } else {
            KitButton(
                stringResource(Res.string.phoneSetupStartSetUp),
                actions::setUp,
                working = state.starting,
            )
        }
    }
}

/** The job's header on a wide window: the artwork bay, the stage line, the percent and the bar. */
@Composable
internal fun SetupJobWideHeader(
    progress: SetupProgress,
    stage: String?,
    bar: KitProgress.Known,
    tone: KitProgressTone,
    pct: Int,
) {
    WideArtworkBay(
        when (progress.state) {
            SetupState.Running -> KitSetupPhoneMood.Starting
            SetupState.Done -> KitSetupPhoneMood.Celebration
            else -> KitSetupPhoneMood.Stopped
        }
    )
    Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
        val headline =
            stage?.let { setupMessageWords(it) }
                ?: if (progress.state == SetupState.Done) stringResource(Res.string.phoneSetupReadyTitle) else null
        val pctRole = if (progress.state == SetupState.Running) TypeRole.Title else TypeRole.Headline
        val pctTone =
            if (progress.state == SetupState.Running || progress.state == SetupState.Done) {
                TextTone.Primary
            } else {
                TextTone.Secondary
            }
        if (headline != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                KitText(headline, Modifier.weight(1f), role = TypeRole.Title)
                KitText("$pct%", role = pctRole, tone = pctTone)
            }
        } else {
            KitText("$pct%", role = pctRole, tone = pctTone)
        }
        KitProgressView(
            bar,
            semanticsLabel = stringResource(Res.string.setupProgressViewOverallLabel),
            tone = tone,
        )
    }
}

/** The controls of a running job: the safe-to-leave line and the cancel (wide), the step list (narrow). */
@Composable
internal fun SetupRunningControls(
    progress: SetupProgress,
    state: PhoneSetupUi,
    actions: PhoneSetupActions,
    wide: Boolean,
) {
    if (wide) {
        KitText(
            stringResource(Res.string.phoneSetupRingSafeToLeaveQuiet),
            role = TypeRole.Secondary,
            tone = TextTone.Secondary,
        )
        Box(Modifier.padding(top = Space.S4.dp)) {
            KitButton(
                stringResource(Res.string.setupProgressViewCancel),
                actions::askStop,
                role = KitButtonRole.Secondary,
                expand = false,
            )
        }
    } else {
        for (component in progress.components) {
            KitRow(
                title =
                    itemTitle(
                        component.id,
                        state.items.firstOrNull { it.id == component.id }?.title
                            ?: component.id,
                    ),
                state = rowState(component.state),
            )
        }
        KitButton(
            stringResource(Res.string.setupProgressViewCancel),
            actions::askStop,
            role = KitButtonRole.Secondary,
        )
    }
}

/** The controls of a finished setup: the ready line and the one next step. */
@Composable
internal fun SetupDoneControls(actions: PhoneSetupActions, wide: Boolean) {
    if (wide) {
        KitText(stringResource(Res.string.phoneSetupStartReadyBody), tone = TextTone.Secondary)
        Box(Modifier.padding(top = Space.S4.dp).widthIn(max = Layout.CONTROL_MAX_WIDTH.dp)) {
            KitButton(
                stringResource(Res.string.chatsHomeNewChat),
                actions::done,
                fullWidth = true,
                hero = true,
            )
        }
    } else {
        KitNotice(
            title = stringResource(Res.string.phoneSetupReadyTitle),
            message = stringResource(Res.string.phoneSetupStartReadyBody),
            tone = StatusTone.Ok,
        )
        // B-109: the screen's one next step is the kit's action block (full width on a phone, at the end from
        // medium up), never a narrow button left at the start edge.
        KitActionBlock(primary = KitAction(stringResource(Res.string.chatsHomeNewChat), actions::done))
    }
}

/** The panel of a failed, interrupted or cancelled setup: the error, its way on, and the actions under it. */
@Composable
internal fun SetupFailedPanel(
    progress: SetupProgress,
    state: PhoneSetupUi,
    actions: PhoneSetupActions,
    wide: Boolean,
) {
    val message =
        when (progress.state) {
            SetupState.Interrupted ->
                stringResource(Res.string.setupProgressViewInterrupted)
            SetupState.Cancelled -> stringResource(Res.string.setupProgressViewCancelled)
            else ->
                progress.error?.let { problemWords(it) }
                    ?: stringResource(Res.string.setupProgressViewFailedUnknown)
        }
    KitErrorNotice(
        message = message,
        title = stringResource(Res.string.setupProgressViewFailedTitle),
        details = state.details,
        detailsLabel = stringResource(Res.string.kitDetails),
        copyLabel = stringResource(Res.string.kitCopyDetails),
    )
    if (state.reportFailed) KitNotice(stringResource(Res.string.diagnosticsUnavailable))
    // Node.js cannot run on this phone (#117): Continue would only fail again; a computer is the way on.
    val cannotRun =
        progress.state == SetupState.Failed && progress.error == SetupProblem.NodeCannotRun
    val primary =
        if (cannotRun) {
            KitAction(stringResource(Res.string.phoneSetupNodeConnectComputer), actions::connectComputer)
        } else {
            KitAction(
                stringResource(Res.string.setupProgressViewContinue),
                actions::continueSetup,
                working = state.starting,
            )
        }
    val secondary =
        when {
            cannotRun ->
                KitAction(stringResource(Res.string.phoneSetupNodeReportFailure), actions::reportFailure)
            progress.state == SetupState.Failed ->
                KitAction(stringResource(Res.string.kitReportProblem), actions::reportFailure)
            else -> null
        }
    KitReveal(cannotRun) {
        FailedActionRow(primary = primary, secondary = secondary, wide = wide)
    }
    if (!cannotRun) FailedActionRow(primary = primary, secondary = secondary, wide = wide)
}

/** One part of the wide parts panel: its status mark and, when downloading, its byte or percent note. */
@Composable
internal fun widePartEntry(fallback: String, comp: ComponentProgress): WidePartItem {
    val total = comp.bytesTotal
    val done = comp.bytesDone
    val pct = comp.percent?.takeIf { comp.state == ComponentState.Running }
    val progressNote =
        when {
            total != null && total > 0 && done != null ->
                stringResource(Res.string.setupProgressViewBytes, sizeWords(done), sizeWords(total))
            pct != null ->
                stringResource(
                    Res.string.setupProgressViewPercent,
                    pct.coerceIn(0.0, PERCENT_MAX.toDouble()).roundToInt(),
                )
            else -> null
        }
    val status =
        when (comp.state) {
            ComponentState.Done, ComponentState.Skipped -> WidePartStatus.Done
            ComponentState.Running, ComponentState.Checking -> WidePartStatus.Working
            ComponentState.Failed -> WidePartStatus.Failed
            ComponentState.Pending -> WidePartStatus.Waiting
        }
    return WidePartItem(comp.id, itemTitle(comp.id, fallback), status, progressNote)
}
