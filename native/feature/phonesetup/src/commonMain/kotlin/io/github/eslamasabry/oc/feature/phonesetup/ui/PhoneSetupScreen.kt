package io.github.eslamasabry.oc.feature.phonesetup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.linuxsetup.ComponentState
import io.github.eslamasabry.oc.engine.linuxsetup.SetupComponentIds
import io.github.eslamasabry.oc.engine.linuxsetup.SetupPreflightResult
import io.github.eslamasabry.oc.engine.linuxsetup.SetupProblem
import io.github.eslamasabry.oc.engine.linuxsetup.SetupProgress
import io.github.eslamasabry.oc.engine.linuxsetup.SetupState
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneSetupActions
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneSetupUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import io.github.eslamasabry.oc.ui.kit.state.KitProgressTone
import io.github.eslamasabry.oc.ui.kit.state.KitProgressView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.diagnosticsUnavailable
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitReportProblem
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorCannotStart
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorChecksum
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorInstall
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorInterrupted
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorNoInternet
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorNoSpace
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorOffline
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorOpenCodeNoProgram
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorOpenCodeNoStart
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorOpenCodeWontRun
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorPackageRepair
import io.github.eslamasabry.oc.ui.words.res.phoneSetupEssentialsTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupLinuxTitle
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
import io.github.eslamasabry.oc.ui.words.res.phoneSetupProgressTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupReadyTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartChecking
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartGigabytes
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartHeadline
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartMegabytes
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartOpen
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartPromise
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartPromiseNoSize
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartReadyBody
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartScreenTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartSetUp
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartTitle
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewCancel
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewCancelled
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewContinue
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewFailedTitle
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewFailedUnknown
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewGettingStarted
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewInterrupted
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewOverallLabel
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.compose.resources.stringResource

/**
 * PhoneSetupRoute.Setup (Flutter phone_setup_start_screen.dart + phone_setup_progress_screen.dart):
 * the start shows what is installed and the preflight (B-LINUX-209..211), then the job's weighted
 * bar with its stage and an honest ETA (B-LINUX-201), and a failure as one plain line with Details
 * (B-LINUX-203, B-SEC-166), Continue and Stop.
 */
@Composable
public fun PhoneSetupScreen(state: PhoneSetupUi, actions: PhoneSetupActions) {
    val progress = state.progress
    KitScreen(
        topBar = {
            KitTopBar(
                title =
                    stringResource(
                        if (progress == null) Res.string.phoneSetupStartScreenTitle
                        else Res.string.phoneSetupProgressTitle
                    ),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
        loading = state.checking,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            if (progress == null) Start(state, actions) else Job(progress, state, actions)
        }
    }
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

@Composable
private fun Start(state: PhoneSetupUi, actions: PhoneSetupActions) {
    KitText(stringResource(Res.string.phoneSetupStartHeadline))
    if (state.checking) {
        KitText(stringResource(Res.string.phoneSetupStartChecking))
        return
    }
    val size = sizeWords(state.downloadBytes)
    KitText(
        if (state.downloadBytes > 0) stringResource(Res.string.phoneSetupStartPromise, size)
        else stringResource(Res.string.phoneSetupStartPromiseNoSize)
    )
    when (val preflight = state.preflight) {
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
    for (item in state.items) KitRow(title = itemTitle(item.id, item.title))
    if (state.preflight?.supported != false) {
        KitButton(
            stringResource(Res.string.phoneSetupStartSetUp),
            actions::setUp,
            working = state.starting,
        )
    }
}

@Composable
private fun Job(progress: SetupProgress, state: PhoneSetupUi, actions: PhoneSetupActions) {
    val current = progress.components.firstOrNull { it.id == progress.current }
    val stage =
        current?.stage
            ?: if (progress.state == SetupState.Running)
                stringResource(Res.string.setupProgressViewGettingStarted)
            else null
    val bar =
        KitProgress.Known(
            value = progress.overall.toFloat().coerceIn(0f, 1f),
            eta = progress.etaSeconds?.seconds,
        )
    KitProgressView(
        bar,
        semanticsLabel = stringResource(Res.string.setupProgressViewOverallLabel),
        tone =
            when (progress.state) {
                SetupState.Failed -> KitProgressTone.Failed
                SetupState.Running,
                SetupState.Done -> KitProgressTone.Moving
                else -> KitProgressTone.Stopped
            },
    )
    KitText("${(progress.overall * 100).roundToInt().coerceIn(0, 100)}%")
    stage?.let { KitText(it) }
    when (progress.state) {
        SetupState.Running -> {
            for (component in progress.components) {
                KitRow(
                    title =
                        itemTitle(
                            component.id,
                            state.items.firstOrNull { it.id == component.id }?.title
                                ?: component.id,
                        ),
                    supporting = rowState(component.state),
                )
            }
            KitButton(
                stringResource(Res.string.setupProgressViewCancel),
                actions::askStop,
                role = KitButtonRole.Secondary,
            )
        }
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
            if (progress.state == SetupState.Failed)
                KitButton(
                    stringResource(Res.string.kitReportProblem),
                    actions::reportFailure,
                    role = KitButtonRole.Secondary,
                )
            KitButton(
                stringResource(Res.string.setupProgressViewContinue),
                actions::continueSetup,
                working = state.starting,
            )
        }
        SetupState.Idle -> Unit
    }
}

@Composable
private fun rowState(state: ComponentState): String? =
    when (state) {
        ComponentState.Done,
        ComponentState.Skipped -> "✓"
        ComponentState.Running,
        ComponentState.Checking -> "…"
        ComponentState.Failed -> "!"
        ComponentState.Pending -> null
    }

/**
 * A component's name in the person's language; Python, Node.js and OpenCode are names and stay as
 * they are.
 */
@Composable
internal fun itemTitle(id: String, fallback: String): String =
    when (id) {
        SetupComponentIds.LINUX -> stringResource(Res.string.phoneSetupLinuxTitle)
        SetupComponentIds.ESSENTIALS -> stringResource(Res.string.phoneSetupEssentialsTitle)
        SetupComponentIds.START -> stringResource(Res.string.phoneSetupStartTitle)
        else -> fallback
    }

/**
 * The job's problem in plain words (app_en.arb phoneSetupError*); the technical reason stays under
 * Details.
 */
@Composable
internal fun problemWords(problem: SetupProblem): String =
    when (problem) {
        is SetupProblem.NoSpace -> stringResource(Res.string.phoneSetupErrorNoSpace, problem.name)
        SetupProblem.Interrupted -> stringResource(Res.string.phoneSetupErrorInterrupted)
        is SetupProblem.Offline -> stringResource(Res.string.phoneSetupErrorOffline, problem.name)
        SetupProblem.NoInternet -> stringResource(Res.string.phoneSetupErrorNoInternet)
        is SetupProblem.Checksum -> stringResource(Res.string.phoneSetupErrorChecksum, problem.name)
        is SetupProblem.Install -> stringResource(Res.string.phoneSetupErrorInstall, problem.name)
        SetupProblem.PackageRepair -> stringResource(Res.string.phoneSetupErrorPackageRepair)
        SetupProblem.CannotStart,
        SetupProblem.RuntimeOwnershipUnknown ->
            stringResource(Res.string.phoneSetupErrorCannotStart)
        SetupProblem.OpenCodeNoProgram ->
            stringResource(Res.string.phoneSetupErrorOpenCodeNoProgram)
        SetupProblem.OpenCodeWontRun -> stringResource(Res.string.phoneSetupErrorOpenCodeWontRun)
        SetupProblem.OpenCodeNoStart -> stringResource(Res.string.phoneSetupErrorOpenCodeNoStart)
        is SetupProblem.StartFailed -> stringResource(Res.string.setupProgressViewFailedUnknown)
    }

/** "~350 MB" / "1.2 GB" (decimal units, as the Flutter start screen). */
@Composable
internal fun sizeWords(bytes: Long): String {
    val mb = bytes / 1_000_000.0
    return if (mb >= 1000) {
        stringResource(
            Res.string.phoneSetupStartGigabytes,
            ((mb / 100).roundToInt() / 10.0).toString(),
        )
    } else {
        stringResource(
            Res.string.phoneSetupStartMegabytes,
            mb.roundToInt().coerceAtLeast(1).toString(),
        )
    }
}
