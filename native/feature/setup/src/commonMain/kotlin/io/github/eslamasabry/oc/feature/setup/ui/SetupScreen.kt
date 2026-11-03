package io.github.eslamasabry.oc.feature.setup.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentState
import io.github.eslamasabry.oc.engine.runtime.setup.SetupError
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.feature.setup.presentation.SetupActions
import io.github.eslamasabry.oc.feature.setup.presentation.SetupFailureLine
import io.github.eslamasabry.oc.feature.setup.presentation.SetupHeadline
import io.github.eslamasabry.oc.feature.setup.presentation.SetupPhase
import io.github.eslamasabry.oc.feature.setup.presentation.SetupProgressUi
import io.github.eslamasabry.oc.feature.setup.presentation.SetupStartUi
import io.github.eslamasabry.oc.feature.setup.presentation.SetupStepUi
import io.github.eslamasabry.oc.feature.setup.presentation.SetupUi
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.e7SetupOpenAppSettings
import io.github.eslamasabry.oc.ui.words.res.inAppServerOwnershipRecoveryBody
import io.github.eslamasabry.oc.ui.words.res.keepRunningOpenFailed
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
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
import io.github.eslamasabry.oc.ui.words.res.phoneSetupNotificationDone
import io.github.eslamasabry.oc.ui.words.res.phoneSetupRingChecking
import io.github.eslamasabry.oc.ui.words.res.phoneSetupRingDetailsTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupRingDownloading
import io.github.eslamasabry.oc.ui.words.res.phoneSetupRingInstalling
import io.github.eslamasabry.oc.ui.words.res.phoneSetupRingInterruptedTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupRingPause
import io.github.eslamasabry.oc.ui.words.res.phoneSetupRingPausedTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupRingSafeToLeaveQuiet
import io.github.eslamasabry.oc.ui.words.res.phoneSetupRingStoppedNote
import io.github.eslamasabry.oc.ui.words.res.phoneSetupRingUnpacking
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStageStarting
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartDone
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartGigabytes
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartHeadline
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartIncludes
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartInstalled
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartListPair
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartListSeparator
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartMegabytes
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartPromise
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartPromiseNoSize
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartScreenTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartSetUp
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewBytes
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewChecking
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewContinue
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewDone
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewFailedAt
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewFailedDuring
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewFailedStep
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewFailedTitle
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewFailedUnknown
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewGettingStarted
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewPercent
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewStageMeasured
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewStarting
import io.github.eslamasabry.oc.ui.words.res.setupRbStatusUnreadable
import io.github.eslamasabry.oc.ui.words.res.setupRbToolsUncheckable
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.minutes
import org.jetbrains.compose.resources.stringResource

/**
 * SetupRoute.Phone (port of phone_setup_start_screen.dart's fresh state and phone_setup_progress_screen.dart): the
 * start page while no job exists, else the live job — what it is doing now ("Downloading Node.js", Step 4 of 6 ·
 * 18 of 58 MB · ~3 min), Pause while it runs, Continue setup after it stopped, a plain failure line, the steps and the
 * log under Details. Back leaves; the job keeps going in the background. Kit parts only (K1).
 */
@Composable
public fun SetupScreen(state: SetupUi, actions: SetupActions) {
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.phoneSetupStartScreenTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
        loading = state.phase == SetupPhase.Loading,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            when (val phase = state.phase) {
                SetupPhase.Loading -> Unit
                is SetupPhase.Start -> StartPage(phase.start, state.working, actions)
                is SetupPhase.Progress -> ProgressPage(phase.progress, state.working, actions)
            }
            if (state.settingsMissing) KitNotice(stringResource(Res.string.keepRunningOpenFailed))
        }
    }
}

@Composable
private fun StartPage(start: SetupStartUi, working: Boolean, actions: SetupActions) {
    KitStateView(
        kind = KitStateKind.Calm,
        title = stringResource(Res.string.phoneSetupStartHeadline),
        body =
            if (start.downloadBytes > 0) stringResource(Res.string.phoneSetupStartPromise, sizeText(start.downloadBytes))
            else stringResource(Res.string.phoneSetupStartPromiseNoSize),
        primary = KitAction(stringResource(Res.string.phoneSetupStartSetUp), onClick = actions::start, working = working),
    )
    if (start.includes.isNotEmpty()) {
        KitGroupNote(stringResource(Res.string.phoneSetupStartIncludes, joinNames(start.includes)))
    }
}

@Composable
private fun ProgressPage(p: SetupProgressUi, working: Boolean, actions: SetupActions) {
    val kind =
        when (p.state) {
            SetupState.Done -> KitStateKind.Finished
            SetupState.Failed -> KitStateKind.Error
            SetupState.Interrupted, SetupState.Cancelled -> KitStateKind.Calm
            SetupState.Running, SetupState.Idle -> KitStateKind.Working
        }
    val subject = p.subject.orEmpty()
    val title =
        when (p.headline) {
            SetupHeadline.Done -> stringResource(Res.string.setupProgressViewDone)
            SetupHeadline.Failed -> stringResource(Res.string.setupProgressViewFailedTitle)
            SetupHeadline.Interrupted -> stringResource(Res.string.phoneSetupRingInterruptedTitle)
            SetupHeadline.Paused -> stringResource(Res.string.phoneSetupRingPausedTitle)
            SetupHeadline.Checking -> stringResource(Res.string.phoneSetupRingChecking)
            SetupHeadline.GettingStarted -> stringResource(Res.string.setupProgressViewGettingStarted)
            SetupHeadline.Starting -> stringResource(Res.string.phoneSetupStageStarting)
            SetupHeadline.Downloading -> stringResource(Res.string.phoneSetupRingDownloading, subject)
            SetupHeadline.Unpacking -> stringResource(Res.string.phoneSetupRingUnpacking, subject)
            SetupHeadline.Installing -> stringResource(Res.string.phoneSetupRingInstalling, subject)
        }
    val body =
        when (p.state) {
            SetupState.Done -> stringResource(Res.string.phoneSetupNotificationDone)
            SetupState.Failed -> p.failure?.let { failureLine(it) } ?: stringResource(Res.string.setupProgressViewFailedUnknown)
            SetupState.Interrupted, SetupState.Cancelled -> stringResource(Res.string.phoneSetupRingStoppedNote)
            SetupState.Running, SetupState.Idle -> stringResource(Res.string.phoneSetupRingSafeToLeaveQuiet)
        }
    val step = p.step
    val bytes = p.bytes?.let { (done, total) -> stringResource(Res.string.setupProgressViewBytes, done, total) }
    val progress =
        when {
            p.state == SetupState.Done -> KitProgress.Known(1f)
            step != null && step in 1..p.of ->
                KitProgress.Staged(
                    step = step,
                    of = p.of,
                    label = p.steps.getOrNull(step - 1)?.title.orEmpty(),
                    stepValue = p.measured,
                    caption = bytes,
                    eta = p.minutesLeft?.minutes,
                )
            p.running -> KitProgress.Waiting(bytes)
            else -> null
        }
    val primary =
        when {
            p.failure == SetupFailureLine.Ownership ->
                KitAction(stringResource(Res.string.e7SetupOpenAppSettings), onClick = actions::openAppSettings)
            p.canContinue -> KitAction(stringResource(Res.string.setupProgressViewContinue), onClick = actions::resume, working = working)
            p.state == SetupState.Done -> KitAction(stringResource(Res.string.phoneSetupStartDone), onClick = actions::back)
            else -> null
        }
    val secondary =
        if (p.running) KitAction(stringResource(Res.string.phoneSetupRingPause), onClick = actions::pause, working = working) else null
    KitStateView(kind = kind, title = title, body = body, progress = progress, primary = primary, secondary = secondary)
    if (p.steps.isNotEmpty()) {
        KitRowGroup(label = stringResource(Res.string.phoneSetupRingDetailsTitle)) {
            p.steps.forEach { StepRow(it) }
        }
    }
    p.details?.let { details ->
        KitDetailsFold(
            label = stringResource(Res.string.kitDetails),
            copyLabel = stringResource(Res.string.kitCopyDetails),
            text = details,
            initiallyExpanded = false,
        )
    }
}

@Composable
private fun StepRow(step: SetupStepUi) {
    val finished = step.state == ComponentState.Done || step.state == ComponentState.Skipped
    val measured =
        step.bytes?.let { (done, total) -> stringResource(Res.string.setupProgressViewBytes, done, total) }
            ?: step.percent?.let { stringResource(Res.string.setupProgressViewPercent, it) }
    val stage = step.stage ?: if (step.state == ComponentState.Checking) stringResource(Res.string.setupProgressViewChecking) else null
    val supporting =
        when (step.state) {
            ComponentState.Running, ComponentState.Checking ->
                when {
                    measured != null && stage != null -> stringResource(Res.string.setupProgressViewStageMeasured, stage, measured)
                    else -> measured ?: stage ?: stringResource(Res.string.setupProgressViewStarting)
                }
            else -> null
        }
    KitRow(
        title = step.title,
        supporting = supporting,
        supportingMaxLines = 2,
        trailing =
            if (finished) KitRowTrailing.Value(step.version ?: stringResource(Res.string.phoneSetupStartInstalled), chevron = false)
            else KitRowTrailing.None,
    )
}

@Composable
private fun failureLine(line: SetupFailureLine): String =
    when (line) {
        SetupFailureLine.Ownership -> stringResource(Res.string.inAppServerOwnershipRecoveryBody)
        is SetupFailureLine.During -> stringResource(Res.string.setupProgressViewFailedDuring, line.stage)
        SetupFailureLine.Step -> stringResource(Res.string.setupProgressViewFailedStep)
        is SetupFailureLine.At -> stringResource(Res.string.setupProgressViewFailedAt, line.title)
        SetupFailureLine.Unknown -> stringResource(Res.string.setupProgressViewFailedUnknown)
        is SetupFailureLine.Error ->
            when (val e = line.error) {
                SetupError.OwnershipUnknown -> stringResource(Res.string.inAppServerOwnershipRecoveryBody)
                SetupError.StatusUnreadable -> stringResource(Res.string.setupRbStatusUnreadable)
                SetupError.ToolsUncheckable -> stringResource(Res.string.setupRbToolsUncheckable)
                SetupError.PackageRepair -> stringResource(Res.string.phoneSetupErrorPackageRepair)
                SetupError.CannotStart -> stringResource(Res.string.phoneSetupErrorCannotStart)
                SetupError.OpenCodeNoProgram -> stringResource(Res.string.phoneSetupErrorOpenCodeNoProgram)
                SetupError.OpenCodeWontRun -> stringResource(Res.string.phoneSetupErrorOpenCodeWontRun)
                SetupError.OpenCodeNoStart -> stringResource(Res.string.phoneSetupErrorOpenCodeNoStart)
                SetupError.Interrupted -> stringResource(Res.string.phoneSetupErrorInterrupted)
                SetupError.NoInternet -> stringResource(Res.string.phoneSetupErrorNoInternet)
                is SetupError.NoSpace -> stringResource(Res.string.phoneSetupErrorNoSpace, e.title)
                is SetupError.Offline -> stringResource(Res.string.phoneSetupErrorOffline, e.title)
                is SetupError.Checksum -> stringResource(Res.string.phoneSetupErrorChecksum, e.title)
                is SetupError.Install -> stringResource(Res.string.phoneSetupErrorInstall, e.title)
                SetupError.HostMismatch, SetupError.HostRefused, SetupError.Unexpected ->
                    stringResource(Res.string.setupProgressViewFailedUnknown)
            }
    }

/** "208 MB" or "1.2 GB", in decimal units like the store listing (Flutter `setupSizeText`). */
@Composable
private fun sizeText(bytes: Long): String {
    val megabytes = bytes / 1_000_000.0
    if (megabytes >= 1000) {
        val tenths = (megabytes / 100).roundToLong()
        return stringResource(Res.string.phoneSetupStartGigabytes, "${tenths / 10}.${tenths % 10}")
    }
    return stringResource(Res.string.phoneSetupStartMegabytes, maxOf(1L, megabytes.roundToLong()).toString())
}

/** "Git and SSH, Python and Node.js" with the locale's own separator and "and" (Flutter `joinSetupNames`). */
@Composable
private fun joinNames(names: List<String>): String {
    if (names.size == 1) return names.single()
    val head = names.dropLast(1).joinToString(stringResource(Res.string.phoneSetupStartListSeparator))
    return stringResource(Res.string.phoneSetupStartListPair, head, names.last())
}
