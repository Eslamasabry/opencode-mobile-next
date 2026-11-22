package io.github.eslamasabry.oc.feature.phonesetup.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentProgress
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentState
import io.github.eslamasabry.oc.engine.runtime.setup.SetupComponentIds
import io.github.eslamasabry.oc.engine.runtime.setup.SetupError
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgress
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgressRules
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.feature.phonesetup.presentation.RingCostPart
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyActions
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyUi
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupRingCost
import io.github.eslamasabry.oc.ui.design.layout.Layout
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.setup.KitTrailMark
import io.github.eslamasabry.oc.ui.kit.state.KitChecklistStep
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitProgressRing
import io.github.eslamasabry.oc.ui.kit.state.KitStepState
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.wait.KitWaitKind
import io.github.eslamasabry.oc.ui.kit.wait.KitWaitMarkHere
import io.github.eslamasabry.oc.ui.words.res.Res
import org.jetbrains.compose.resources.stringResource

/** The words and counts the progress ring and its lines are drawn from, built once in [RingBodyPane]. */
internal class RingWords(
    val title: String,
    val caption: String?,
    val measured: Float?,
    val done: Int,
    val current: Int,
    val checking: Boolean,
)

/** The ring body of the setup job: the words and marks it needs, computed in one place. */
@Composable
internal fun RingBodyPane(
    state: SetupJourneyUi,
    actions: SetupJourneyActions,
    memory: JourneyProgressMemory,
    wide: Boolean,
    onOpenDetails: () -> Unit,
) {
    val progress = state.progress
    val rows = progress.components.ifEmpty { state.install.map { ComponentProgress(it.id, ComponentState.Pending) } }
    val done = rows.count { it.state == ComponentState.Done || it.state == ComponentState.Skipped }
    var current =
        rows.indexOfFirst {
                it.state == ComponentState.Running ||
                    it.state == ComponentState.Checking ||
                    it.state == ComponentState.Failed
            }
            .let { found ->
                if (found >= 0) found
                else
                    rows.indexOfFirst {
                        it.id == progress.current && it.state != ComponentState.Done &&
                            it.state != ComponentState.Skipped
                    }
            }
    val halted = progress.canContinue
    if (current < 0 && halted)
        current = rows.indexOfFirst { it.state != ComponentState.Done && it.state != ComponentState.Skipped }
    val row = rows.getOrNull(current)
    val checking =
        rows.isNotEmpty() &&
            rows.all {
                it.state == ComponentState.Checking || it.state == ComponentState.Done ||
                    it.state == ComponentState.Skipped
            } &&
            rows.any { it.state == ComponentState.Checking }
    // One bar for the whole setup: it only moves forward across steps and stages. A step's own download arc used to
    // be the ring, so it read 100% when the download ended and 29% when the unpacking began.
    val measuredSteps = if (!halted && memory.measured(row) != null) memory.overall(progress) else null
    // When the downloads have a known size the ring follows the bytes, so it and the line under it are one measure.
    val measured = measuredSteps?.let { steps -> memory.followBytes(rows, state.bytes) ?: steps }
    val eta = memory.eta(progress, state.options?.components.orEmpty())
    val title = jobTitle(progress, checking, row, state)
    val note = jobNote(progress, state)
    // The download as the home page promised it: all the steps' bytes, not this step's own (0 of 30 MB for a
    // download the home page called 208 MB).
    val caption = wholeBytesCaption(memory, rows, measured, row, state)
    val topGap = if (wide) Space.S5.dp else Space.S6.dp
    Column(
        Modifier.fillMaxWidth()
            .testTag("setup-progress-body")
            .then(if (wide) Modifier else Modifier.padding(horizontal = Space.S5.dp, vertical = Space.S6.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProgressRingCall(progress, rows, RingWords(title, caption, measured, done, current, checking))
        val running = progress.state == SetupState.Running || progress.state == SetupState.Idle
        val costParts =
            if (!running) emptyList()
            else
                SetupRingCost.parts(
                    eta, lastStep = rows.none { it.state == ComponentState.Pending },
                    downloading = caption != null, transport = state.transport,
                )
        if (costParts.isNotEmpty()) JobCostLine(costParts, topGap)
        JobBeatAndNote(progress, note, wide, topGap, costParts.isEmpty())
        RingTailCards(state, actions, wide, onOpenDetails)
    }
}

/** The ring itself with its step trail, as [RingBodyPane] hands it over. */
@Composable
private fun ProgressRingCall(progress: SetupProgress, rows: List<ComponentProgress>, words: RingWords) {
    KitProgressRing(
        words.title,
        stringResource(Res.string.setupProgressViewOverallLabel),
        value = words.measured,
        done = words.done,
        of = rows.size,
        finished = progress.state == SetupState.Done,
        halted = progress.canContinue,
        caption = words.caption,
        trail =
            rows.mapIndexed { index, step ->
                when {
                    step.state == ComponentState.Done || step.state == ComponentState.Skipped -> KitTrailMark.Done
                    index == words.current && !words.checking -> KitTrailMark.Current
                    else -> KitTrailMark.Waiting
                }
            },
    )
}

/** The whole-setup ring's stage line, from the current row's state and stage words. */
@Composable
private fun jobTitle(
    progress: SetupProgress,
    checking: Boolean,
    row: ComponentProgress?,
    state: SetupJourneyUi,
): String {
    val subject =
        row?.let { currentRow ->
                if (currentRow.id == SetupComponentIds.ESSENTIALS)
                    stringResource(Res.string.phoneSetupEssentialsShort)
                else
                    itemTitle(
                        currentRow.id,
                        state.options
                            ?.components
                            ?.firstOrNull { it.id == currentRow.id }
                            ?.shortTitle ?: currentRow.id,
                    )
            }
            .orEmpty()
    val unpacking = stringResource(Res.string.phoneSetupStageUnpackingLinux)
    return when (progress.state) {
        SetupState.Done -> stringResource(Res.string.setupProgressViewDone)
        SetupState.Failed -> stringResource(Res.string.setupProgressViewFailedTitle)
        SetupState.Interrupted -> stringResource(Res.string.phoneSetupRingInterruptedTitle)
        SetupState.Cancelled -> stringResource(Res.string.phoneSetupRingPausedTitle)
        else ->
            when {
                checking -> stringResource(Res.string.phoneSetupRingChecking)
                row == null || row.state == ComponentState.Pending ->
                    stringResource(Res.string.setupProgressViewGettingStarted)
                row.id == SetupComponentIds.START ->
                    stringResource(Res.string.phoneSetupStageStarting)
                row.stage?.let {
                    it == unpacking ||
                        Regex("unpack|extract", RegexOption.IGNORE_CASE).containsMatchIn(it)
                } == true -> stringResource(Res.string.phoneSetupRingUnpacking, subject)
                ((row.bytesTotal ?: 0) > (row.bytesDone ?: 0)) ||
                    row.stage?.let {
                        Regex("download|fetch", RegexOption.IGNORE_CASE).containsMatchIn(it)
                    } == true -> stringResource(Res.string.phoneSetupRingDownloading, subject)
                else -> stringResource(Res.string.phoneSetupRingInstalling, subject)
            }
    }
}

/** The line under the ring: what happened, in the person's words. */
@Composable
private fun jobNote(progress: SetupProgress, state: SetupJourneyUi): String? =
    when (progress.state) {
        // Flutter: the promise of a notification only when the app may post one.
        SetupState.Running,
        SetupState.Idle ->
            stringResource(
                if (state.canNotify) Res.string.phoneSetupRingSafeToLeave else Res.string.phoneSetupRingSafeToLeaveQuiet
            )
        SetupState.Failed,
        SetupState.Interrupted ->
            state.systemKill?.let { killWords(it) }
                ?: if (progress.state == SetupState.Failed) journeyFailure(state)
                else stringResource(Res.string.phoneSetupRingStoppedNote)
        SetupState.Cancelled -> stringResource(Res.string.phoneSetupRingStoppedNote)
        SetupState.Done -> null
    }

/** The ring's byte caption when the whole download's size is known. */
@Composable
private fun wholeBytesCaption(
    memory: JourneyProgressMemory,
    rows: List<ComponentProgress>,
    measured: Float?,
    row: ComponentProgress?,
    state: SetupJourneyUi,
): String? {
    val wholeBytes = if (measured != null && row?.bytesDone != null) memory.bytes(rows, state.bytes) else null
    if (wholeBytes == null) return null
    val (received, total) = SetupProgressRules.formatBytePair(wholeBytes.first, wholeBytes.second)
    return stringResource(
        Res.string.setupProgressViewBytes,
        "\u2066$received\u2069",
        "\u2066$total\u2069",
    )
}

/** The cost line under the ring (eta, last step, network), when there is one to say. */
@Composable
private fun JobCostLine(costParts: List<RingCostPart>, topGap: Dp) {
    Spacer(Modifier.height(topGap))
    // F: lib/ui/screens/phone_setup/phone_setup_progress_screen.dart:284 (_costLine)
    val cost =
        costParts
            .map { part ->
                when (part) {
                    RingCostPart.UnderMinute -> stringResource(Res.string.phoneSetupRingUnderMinute)
                    is RingCostPart.Minutes ->
                        org.jetbrains.compose.resources.pluralStringResource(
                            Res.plurals.phoneSetupRingMinutesLeft,
                            part.minutes,
                            part.minutes,
                        )
                    RingCostPart.Wifi -> stringResource(Res.string.phoneSetupRingWifi)
                    RingCostPart.MobileData -> stringResource(Res.string.phoneSetupRingMobileData)
                }
            }
            .joinToString(" · ")
    KitText(
        cost,
        Modifier.testTag("phone-setup-progress-cost"),
        role = TypeRole.RowTitle,
        tone = TextTone.Secondary,
        textAlign = TextAlign.Center,
    )
}

/** The step clock line and the note under the ring, each only when it has something to say. */
@Composable
private fun JobBeatAndNote(progress: SetupProgress, note: String?, wide: Boolean, topGap: Dp, costEmpty: Boolean) {
    val clock = progress.stepElapsedSeconds
    val running = progress.state == SetupState.Running || progress.state == SetupState.Idle
    if (running && clock != null) {
        // Phase 0: a long step shows its own clock and what it is doing, or a still-working line.
        Spacer(Modifier.height(if (costEmpty) topGap else Space.S2.dp))
        val doing = progress.activity?.let { activityWords(it) }
        // The clock is the current step's own: the words say so.
        val stepClock = stringResource(Res.string.setupStepClock, elapsedClock(clock))
        KitText(
            if (doing != null) {
                stringResource(Res.string.setupHeartbeatLine, doing, stepClock)
            } else {
                stepClock
            },
            Modifier.testTag("phone-setup-progress-heartbeat"),
            role = TypeRole.RowTitle,
            tone = TextTone.Secondary,
            textAlign = TextAlign.Center,
        )
        if ((progress.quietSeconds ?: 0) >= QUIET_SECONDS) {
            Spacer(Modifier.height(Space.S1.dp))
            KitText(
                stringResource(Res.string.setupHeartbeatQuiet),
                role = TypeRole.Secondary,
                tone = TextTone.Tertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
    if (note != null) {
        Spacer(Modifier.height(if (costEmpty && clock == null) topGap else Space.S2.dp))
        KitText(
            note,
            if (wide) Modifier.widthIn(max = Layout.CONTROL_MAX_WIDTH.dp) else Modifier.widthIn(max = 320.dp),
            role = TypeRole.Secondary,
            tone = TextTone.Tertiary,
            textAlign = TextAlign.Center,
        ) // F: lib/ui/screens/phone_setup/phone_setup_progress_screen.dart:553
    }
}

private const val QUIET_SECONDS = 30

/** The details sheet of the setup job: one line per step, then the technical details fold. */
@Composable
internal fun JobDetailsSheet(
    state: SetupJourneyUi,
    rows: List<ComponentProgress>,
    progress: SetupProgress,
    open: Boolean,
    onClose: () -> Unit,
) {
    KitSheet(
        visible = open,
        onDismissRequest = onClose,
        title = stringResource(Res.string.phoneSetupRingDetailsTitle),
    ) {
        Column(Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, component ->
                JobDetailsRow(component, index, rows.size, state, progress)
            }
            KitDetailsFold(
                label = stringResource(Res.string.kitDetails),
                copyLabel = stringResource(Res.string.kitCopyDetails),
                text = state.details,
            )
        }
    }
}

/** One step of the details sheet: its measured share, its stage, and its checklist row. */
@Composable
private fun JobDetailsRow(
    component: ComponentProgress,
    index: Int,
    stepCount: Int,
    state: SetupJourneyUi,
    progress: SetupProgress,
) {
            val measured =
                if ((component.bytesTotal ?: 0) > 0 && component.bytesDone != null) {
                    val (received, total) =
                        SetupProgressRules.formatBytePair(component.bytesDone!!, component.bytesTotal!!)
                    stringResource(Res.string.setupProgressViewBytes, received, total)
                } else
                    component.percent?.let {
                        stringResource(Res.string.setupProgressViewPercent, it.coerceIn(0.0, 100.0).toInt())
                    }
            val stage =
                component.stage?.let { setupMessageWords(it) }
                    ?: if (component.state == ComponentState.Checking)
                        stringResource(Res.string.setupProgressViewChecking)
                    else null
            val supporting =
                when (component.state) {
                    ComponentState.Done,
                    ComponentState.Skipped -> component.version
                    ComponentState.Failed -> runtimeProblem(component.error ?: progress.error)
                    ComponentState.Pending -> null
                    else ->
                        when {
                            stage != null && measured != null ->
                                stringResource(Res.string.setupProgressViewStageMeasured, stage, measured)
                            else ->
                                measured
                                    ?: stage
                                    ?: stringResource(Res.string.setupProgressViewStarting)
                        }
                }
            // A download whose share is known: the Downloading mark in the working arc's place.
            val running = component.state == ComponentState.Running
            val share = component.fraction?.toFloat()?.coerceIn(0f, 1f)?.takeIf { running }
            KitWaitMarkHere(KitWaitKind.Downloading.takeIf { share != null }, share ?: 0f) {
            KitChecklistStep(
                itemTitle(
                    component.id,
                    state.options?.components?.firstOrNull { it.id == component.id }?.title
                        ?: component.id,
                ),
                state =
                    when (component.state) {
                        ComponentState.Done,
                        ComponentState.Skipped -> KitStepState.Done
                        ComponentState.Running,
                        ComponentState.Checking -> KitStepState.Working
                        ComponentState.Failed -> KitStepState.Failed
                        ComponentState.Pending -> KitStepState.Waiting
                    },
                index = index + 1,
                total = stepCount,
                supporting = supporting,
            )
            }
}

/** The job answer uses Flutter's ownership/network/known-reason/stage fallback order. */
@Composable
private fun journeyFailure(state: SetupJourneyUi): String {
    val progress = state.progress
    val failed = progress.components.filter { it.state == ComponentState.Failed }
    val errors = listOfNotNull(progress.error) + failed.mapNotNull { it.error }
    if (SetupError.OwnershipUnknown in errors)
        return stringResource(Res.string.inAppServerOwnershipRecoveryBody)
    if (errors.any { it == SetupError.NoInternet || it is SetupError.Offline })
        return stringResource(Res.string.setupProgressViewNoInternet)
    val row = failed.firstOrNull()
    val error = row?.error ?: progress.error
    if (
        error != null &&
            error !in setOf(SetupError.Unexpected, SetupError.HostRefused, SetupError.HostMismatch)
    )
        return runtimeProblem(error)
    // F: lib/ui/widgets/setup_progress_view.dart:144-167
    if (row != null) {
        return row.stage?.let {
            stringResource(Res.string.setupProgressViewFailedDuring, setupMessageWords(it))
        } ?: stringResource(Res.string.setupProgressViewFailedStep)
    }
    val current =
        progress.current ?: return stringResource(Res.string.setupProgressViewFailedUnknown)
    val component = state.options?.components?.firstOrNull { it.id == current }
    val title =
        if (current == SetupComponentIds.ESSENTIALS)
            stringResource(Res.string.phoneSetupEssentialsShort)
        else itemTitle(current, component?.shortTitle ?: current)
    return stringResource(Res.string.setupProgressViewFailedAt, title)
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
        is SetupError.PortBusy -> stringResource(Res.string.phoneServerPortBusy, error.port.toString())
        SetupError.CannotStart -> stringResource(Res.string.phoneSetupErrorCannotStart)
        SetupError.OwnershipUnknown -> stringResource(Res.string.inAppServerOwnershipRecoveryBody)
        SetupError.StatusUnreadable -> stringResource(Res.string.setupRbStatusUnreadable)
        SetupError.ToolsUncheckable -> stringResource(Res.string.setupRbToolsUncheckable)
        SetupError.PersistenceFailure -> stringResource(Res.string.linuxSetupSavedFailed)
        SetupError.Interrupted -> stringResource(Res.string.phoneSetupErrorInterrupted)
        is SetupError.StepTimedOut -> stringResource(Res.string.setupStepTimedOut, error.title)
        SetupError.OpenCodeNoProgram -> stringResource(Res.string.phoneSetupErrorOpenCodeNoProgram)
        SetupError.OpenCodeWontRun -> stringResource(Res.string.phoneSetupErrorOpenCodeWontRun)
        SetupError.OpenCodeNoStart -> stringResource(Res.string.phoneSetupErrorOpenCodeNoStart)
        SetupError.NodeCannotRun -> stringResource(Res.string.phoneSetupErrorNodeCannotRun)
        else -> stringResource(Res.string.setupProgressViewFailedUnknown)
    }
