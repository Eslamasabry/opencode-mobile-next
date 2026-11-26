package io.github.eslamasabry.oc.feature.phonesetup.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentProgress
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentState
import io.github.eslamasabry.oc.engine.runtime.setup.SetupComponentIds
import io.github.eslamasabry.oc.engine.runtime.setup.SetupError
import io.github.eslamasabry.oc.engine.runtime.setup.SetupPreflight
import io.github.eslamasabry.oc.engine.runtime.setup.SetupPreflightResult
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgress
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgressRules
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyActions
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyUi
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupRingCost
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.setup.KitProgressRing
import io.github.eslamasabry.oc.ui.kit.setup.KitTrailMark
import io.github.eslamasabry.oc.ui.kit.state.KitChecklistStep
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStepState
import io.github.eslamasabry.oc.ui.kit.wait.KitWaitKind
import io.github.eslamasabry.oc.ui.kit.wait.KitWaitMarkHere
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.inAppServerOwnershipRecoveryBody
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.linuxSetupSavedFailed
import io.github.eslamasabry.oc.ui.words.res.phoneServerPortBusy
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorCannotStart
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorChecksum
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorInstall
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorInterrupted
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorNoInternet
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorNoSpace
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorNodeCannotRun
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorOffline
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorOpenCodeNoProgram
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorOpenCodeNoStart
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorOpenCodeWontRun
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorPackageRepair
import io.github.eslamasabry.oc.ui.words.res.phoneSetupEssentialsShort
import io.github.eslamasabry.oc.ui.words.res.phoneSetupPreflightLowMemoryBody
import io.github.eslamasabry.oc.ui.words.res.phoneSetupPreflightLowMemoryHeadline
import io.github.eslamasabry.oc.ui.words.res.phoneSetupPreflightLowSpaceBody
import io.github.eslamasabry.oc.ui.words.res.phoneSetupPreflightLowSpaceHeadline
import io.github.eslamasabry.oc.ui.words.res.phoneSetupPreflightMayBeSlow
import io.github.eslamasabry.oc.ui.words.res.phoneSetupPreflightOpenStorage
import io.github.eslamasabry.oc.ui.words.res.phoneSetupPreflightUnsupportedBody
import io.github.eslamasabry.oc.ui.words.res.phoneSetupPreflightUnsupportedHeadline
import io.github.eslamasabry.oc.ui.words.res.phoneSetupRingDetailsTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupRingInterruptedTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupRingPausedTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupRingSafeToLeave
import io.github.eslamasabry.oc.ui.words.res.phoneSetupRingSafeToLeaveQuiet
import io.github.eslamasabry.oc.ui.words.res.phoneSetupRingStoppedNote
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStageUnpackingLinux
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewBytes
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewDone
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewFailedAt
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewFailedDuring
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewFailedStep
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewFailedTitle
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewFailedUnknown
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewNoInternet
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewOverallLabel
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewPercent
import io.github.eslamasabry.oc.ui.words.res.setupRbStatusUnreadable
import io.github.eslamasabry.oc.ui.words.res.setupRbToolsUncheckable
import io.github.eslamasabry.oc.ui.words.res.setupStepTimedOut
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

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
    val facts = ringRowFacts(state)
    val rows = facts.rows
    val row = facts.row
    val halted = progress.canContinue
    val checking = ringChecking(rows)
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
        ProgressRingCall(progress, rows, RingWords(title, caption, measured, facts.done, facts.current, checking))
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

/** The words and counts the progress ring and its lines are drawn from, built once in [RingBodyPane]. */
internal class RingWords(
    val title: String,
    val caption: String?,
    val measured: Float?,
    val done: Int,
    val current: Int,
    val checking: Boolean,
)

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
        else -> jobWorkingTitle(checking, row, subject, unpacking)
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
    val measured = jobRowMeasured(component)
    val stage = jobRowStage(component)
    val failureWords =
        if (component.state == ComponentState.Failed) runtimeProblem(component.error ?: progress.error) else null
    val supporting = jobRowSupporting(component, stage, measured, failureWords)
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
        is SetupError.StartStepFailed -> stringResource(Res.string.phoneSetupErrorCannotStart)
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

@Composable
internal fun Preflight(state: SetupJourneyUi, actions: SetupJourneyActions) {
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
internal fun wideJourneyParts(
    state: SetupJourneyUi,
    rows: List<ComponentProgress>,
): List<WidePartItem> =
    rows.map { component ->
        val fallback =
            state.options?.components?.firstOrNull { it.id == component.id }?.title ?: component.id
        val total = component.bytesTotal
        val done = component.bytesDone
        val pct = component.percent?.takeIf { component.state == ComponentState.Running }
        val progressNote =
            when {
                total != null && total > 0 && done != null -> {
                    val (received, whole) = SetupProgressRules.formatBytePair(done, total)
                    stringResource(Res.string.setupProgressViewBytes, received, whole)
                }
                pct != null ->
                    stringResource(
                        Res.string.setupProgressViewPercent,
                        pct.coerceIn(0.0, JOURNEY_PERCENT_MAX).roundToInt(),
                    )
                else -> null
            }
        val status =
            when (component.state) {
                ComponentState.Done, ComponentState.Skipped -> WidePartStatus.Done
                ComponentState.Running, ComponentState.Checking -> WidePartStatus.Working
                ComponentState.Failed -> WidePartStatus.Failed
                ComponentState.Pending -> WidePartStatus.Waiting
            }
        WidePartItem(component.id, itemTitle(component.id, fallback), status, progressNote)
    }

private const val JOURNEY_PERCENT_MAX = 100.0
