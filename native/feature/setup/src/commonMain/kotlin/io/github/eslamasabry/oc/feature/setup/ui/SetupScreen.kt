package io.github.eslamasabry.oc.feature.setup.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.setup.*
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.words.res.*
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
import io.github.eslamasabry.oc.ui.words.res.linuxSetupSavedFailed
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
    var detailsOpen by remember { mutableStateOf(false) }
    val progress = (state.phase as? SetupPhase.Progress)?.progress
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
        bottom = if (progress == null) null else ({ ProgressAction(progress, state.working, actions) }),
    ) {
        when (val phase = state.phase) {
            SetupPhase.Loading -> Unit
            is SetupPhase.Start -> StartPage(phase.start, state.working, actions)
            is SetupPhase.Progress -> ProgressPage(phase.progress) { detailsOpen = true }
        }
        if (state.settingsMissing) KitNotice(stringResource(Res.string.keepRunningOpenFailed))
        KitSheet(
            visible = detailsOpen && progress != null,
            onDismissRequest = { detailsOpen = false },
            title = stringResource(Res.string.phoneSetupRingDetailsTitle),
        ) {
            progress?.let { p ->
                p.steps.forEach { StepRow(it) }
                p.details?.let {
                    KitDetailsFold(
                        label = stringResource(Res.string.kitDetails),
                        copyLabel = stringResource(Res.string.kitCopyDetails),
                        text = it,
                    )
                }
            }
        }
    }
}

@Composable
private fun StartPage(start: SetupStartUi, working: Boolean, actions: SetupActions) {
    KitSetupHero(
        title = stringResource(Res.string.phoneSetupStartHeadline),
        body = if (start.downloadBytes > 0) stringResource(Res.string.phoneSetupStartPromise, sizeText(start.downloadBytes))
            else stringResource(Res.string.phoneSetupStartPromiseNoSize),
        primary = KitAction(stringResource(Res.string.phoneSetupStartSetUp), onClick = actions::start, working = working),
        content = if (start.includes.isEmpty()) null else ({
            KitText(stringResource(Res.string.phoneSetupStartIncludes, joinNames(start.includes)), role = TypeRole.Secondary)
        }),
    )
}

@Composable
private fun ProgressAction(p: SetupProgressUi, working: Boolean, actions: SetupActions) {
    when {
        p.failure == SetupFailureLine.Ownership -> KitButton(stringResource(Res.string.e7SetupOpenAppSettings), actions::openAppSettings)
        p.canContinue -> KitButton(stringResource(Res.string.setupProgressViewContinue), actions::resume, working = working)
        p.state == SetupState.Done -> KitButton(stringResource(Res.string.phoneSetupStartDone), actions::back)
        p.running -> KitButton(stringResource(Res.string.phoneSetupRingPause), actions::pause, fullWidth = true, role = KitButtonRole.Secondary, working = working, icon = io.github.eslamasabry.oc.ui.kit.icon.KitIcons.pause)
    }
}

@Composable
private fun ProgressPage(p: SetupProgressUi, openDetails: () -> Unit) {
    val title = when (p.headline) {
        SetupHeadline.Done -> stringResource(Res.string.setupProgressViewDone)
        SetupHeadline.Failed -> stringResource(Res.string.setupProgressViewFailedTitle)
        SetupHeadline.Interrupted -> stringResource(Res.string.phoneSetupRingInterruptedTitle)
        SetupHeadline.Paused -> stringResource(Res.string.phoneSetupRingPausedTitle)
        SetupHeadline.Checking -> stringResource(Res.string.phoneSetupRingChecking)
        SetupHeadline.GettingStarted -> stringResource(Res.string.setupProgressViewGettingStarted)
        SetupHeadline.Starting -> stringResource(Res.string.phoneSetupStageStarting)
        SetupHeadline.Downloading -> stringResource(Res.string.phoneSetupRingDownloading, p.subject.orEmpty())
        SetupHeadline.Unpacking -> stringResource(Res.string.phoneSetupRingUnpacking, p.subject.orEmpty())
        SetupHeadline.Installing -> stringResource(Res.string.phoneSetupRingInstalling, p.subject.orEmpty())
    }
    val note = when (p.state) {
        SetupState.Done -> null
        SetupState.Failed -> p.failure?.let { failureLine(it) } ?: stringResource(Res.string.setupProgressViewFailedUnknown)
        SetupState.Interrupted, SetupState.Cancelled -> stringResource(Res.string.phoneSetupRingStoppedNote)
        else -> stringResource(Res.string.phoneSetupRingSafeToLeaveQuiet)
    }
    val done = p.steps.count { it.state == ComponentState.Done || it.state == ComponentState.Skipped }
    val trail = p.steps.mapIndexed { index, step ->
        when {
            step.state == ComponentState.Done || step.state == ComponentState.Skipped -> KitTrailMark.Done
            p.step == index + 1 && p.headline != SetupHeadline.Checking -> KitTrailMark.Current
            else -> KitTrailMark.Waiting
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = maxHeight
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = height)
                .padding(horizontal = Space.S5.dp, vertical = Space.S6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(.7f)) // NV: alignment -0.3 puts 35 percent of free space above
            KitProgressRing(
                headline = title,
                semanticsLabel = stringResource(Res.string.setupProgressViewOverallLabel),
                value = if (p.canContinue) null else p.measured,
                done = done, of = p.of,
                finished = p.state == SetupState.Done,
                halted = p.canContinue || p.state == SetupState.Failed,
                caption = p.bytes?.let { (done, total) -> stringResource(Res.string.setupProgressViewBytes, done, total) },
                trail = trail,
            )
            if (p.running && p.minutesLeft != null) {
                Spacer(Modifier.height(Space.S6.dp))
                KitText(org.jetbrains.compose.resources.pluralStringResource(Res.plurals.phoneSetupRingMinutesLeft, p.minutesLeft, p.minutesLeft), role = TypeRole.RowTitle, tone = TextTone.Secondary, textAlign = TextAlign.Center)
            }
            if (note != null) {
                Spacer(Modifier.height(if (p.running && p.minutesLeft != null) Space.S2.dp else Space.S6.dp))
                KitText(note, Modifier.widthIn(max = 320.dp), role = TypeRole.Secondary, tone = TextTone.Tertiary, textAlign = TextAlign.Center) // F: lib/ui/screens/phone_setup/phone_setup_progress_screen.dart:553
            }
            Spacer(Modifier.height(Space.S3.dp))
            KitButton(stringResource(Res.string.kitDetails), openDetails, role = KitButtonRole.Tertiary)
            Spacer(Modifier.weight(1.3f)) // NV: alignment -0.3 divides free space into .7 above and 1.3 below
        }
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
                SetupError.PersistenceFailure -> stringResource(Res.string.linuxSetupSavedFailed)
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
