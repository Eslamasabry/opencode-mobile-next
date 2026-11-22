package io.github.eslamasabry.oc.feature.phonesetup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentProgress
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentState
import io.github.eslamasabry.oc.engine.runtime.setup.SetupComponentIds
import io.github.eslamasabry.oc.engine.runtime.setup.SetupError
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgressRules
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.feature.phonesetup.presentation.BatteryOpen
import io.github.eslamasabry.oc.feature.phonesetup.presentation.BatteryStep
import io.github.eslamasabry.oc.feature.phonesetup.presentation.FirstProjectNameProblem
import io.github.eslamasabry.oc.feature.phonesetup.presentation.FirstProjectStep
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyActions
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyUi
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupMaker
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatsHomeNewChat
import io.github.eslamasabry.oc.ui.words.res.phoneSetupErrorNoInternet
import io.github.eslamasabry.oc.ui.words.res.phoneSetupNodeConnectComputer
import io.github.eslamasabry.oc.ui.words.res.phoneSetupNodeReportFailure
import io.github.eslamasabry.oc.ui.words.res.phoneSetupReadyCreateFailed
import io.github.eslamasabry.oc.ui.words.res.phoneSetupReadyCreateOpen
import io.github.eslamasabry.oc.ui.words.res.phoneSetupReadyCreating
import io.github.eslamasabry.oc.ui.words.res.phoneSetupReadyNameEmpty
import io.github.eslamasabry.oc.ui.words.res.phoneSetupReadyNameHelp
import io.github.eslamasabry.oc.ui.words.res.phoneSetupReadyNameInvalid
import io.github.eslamasabry.oc.ui.words.res.phoneSetupReadyNameLabel
import io.github.eslamasabry.oc.ui.words.res.phoneSetupReadyNameOneFolder
import io.github.eslamasabry.oc.ui.words.res.phoneSetupReadyNameTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupReadyOpenFailed
import io.github.eslamasabry.oc.ui.words.res.phoneSetupReadyOpenFolderInstead
import io.github.eslamasabry.oc.ui.words.res.phoneSetupRingChecking
import io.github.eslamasabry.oc.ui.words.res.phoneSetupRingDownloading
import io.github.eslamasabry.oc.ui.words.res.phoneSetupRingInstalling
import io.github.eslamasabry.oc.ui.words.res.phoneSetupRingPause
import io.github.eslamasabry.oc.ui.words.res.phoneSetupRingUnpacking
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStageStarting
import io.github.eslamasabry.oc.ui.words.res.productErrorTimedOut
import io.github.eslamasabry.oc.ui.words.res.productErrorUnknown
import io.github.eslamasabry.oc.ui.words.res.r40SetupRetry
import io.github.eslamasabry.oc.ui.words.res.setupBatteryBody
import io.github.eslamasabry.oc.ui.words.res.setupBatteryOpenApp
import io.github.eslamasabry.oc.ui.words.res.setupBatteryOpenAutostart
import io.github.eslamasabry.oc.ui.words.res.setupBatteryOpenFailed
import io.github.eslamasabry.oc.ui.words.res.setupBatteryOpenSamsung
import io.github.eslamasabry.oc.ui.words.res.setupBatteryStepAutostart
import io.github.eslamasabry.oc.ui.words.res.setupBatteryStepNeverSleeping
import io.github.eslamasabry.oc.ui.words.res.setupBatteryStepUnrestricted
import io.github.eslamasabry.oc.ui.words.res.setupBatteryTitle
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewBytes
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewChecking
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewContinue
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewGettingStarted
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewPercent
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewStageMeasured
import io.github.eslamasabry.oc.ui.words.res.setupProgressViewStarting
import io.github.eslamasabry.oc.ui.words.res.setupUseComputerInstead
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun JobAction(state: SetupJourneyUi, actions: SetupJourneyActions) {
    // Node.js cannot run on this phone (#117): Continue would only fail again, so the way on is a computer.
    val cannotRun = nodeCannotRun(state.progress)
    KitReveal(cannotRun) {
        KitActionBlock(
            primary =
                KitAction(
                    stringResource(Res.string.phoneSetupNodeConnectComputer),
                    actions::connectComputer,
                ),
            secondary =
                KitAction(stringResource(Res.string.phoneSetupNodeReportFailure), actions::reportFailure),
        )
    }
    if (cannotRun) return
    val timedOut =
        state.progress.state == SetupState.Failed &&
            (state.progress.error is SetupError.StepTimedOut ||
                state.progress.components.any { it.error is SetupError.StepTimedOut })
    KitReveal(timedOut) {
        KitActionBlock(
            primary = KitAction(stringResource(Res.string.r40SetupRetry), actions::continueSetup, working = state.working),
            secondary = KitAction(stringResource(Res.string.setupUseComputerInstead), actions::connectComputer),
        )
    }
    if (timedOut) return
    when {
        state.progress.state == SetupState.Running -> KitButton(
            stringResource(Res.string.phoneSetupRingPause), { actions.askStop(); actions.confirmStop() },
            fullWidth = true, role = KitButtonRole.Secondary, working = state.working,
            icon = io.github.eslamasabry.oc.ui.kit.icon.KitIcons.pause,
        )
        // Ownership recovery requires an app-settings action which this contract does not expose.
        SetupProgressRules.needsOwnershipRecovery(state.progress) -> Unit
        // F: lib/ui/screens/phone_setup/phone_setup_progress_screen.dart:351 (KitScreen.bottom stretches its button)
        state.progress.canContinue -> KitButton(
            stringResource(Res.string.setupProgressViewContinue), actions::continueSetup, working = state.working,
            icon = io.github.eslamasabry.oc.ui.kit.icon.KitIcons.play,
            fullWidth = true,
            modifier = Modifier.testTag("setup-progress-continue"),
        )
        state.progress.state == SetupState.Done && state.canMakeProject -> ReadyProject(state, actions)
        state.progress.state == SetupState.Done ->
            KitButton(stringResource(Res.string.chatsHomeNewChat), actions::done, fullWidth = true)
    }
}

/**
 * The Ready step (Flutter phone_setup_ready_screen.dart): "Name your first project", one name field starting as
 * "my-app", Create and open, and the quiet "Open a folder instead". A refused name says why under the field.
 */
@Composable
private fun ReadyProject(state: SetupJourneyUi, actions: SetupJourneyActions) {
    val name = androidx.compose.foundation.text.input.rememberTextFieldState(state.projectName)
    Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
        KitText(stringResource(Res.string.phoneSetupReadyNameTitle), role = TypeRole.Title)
        KitField(
            name,
            stringResource(Res.string.phoneSetupReadyNameLabel),
            Modifier.testTag("phone-setup-ready-name"),
            kind = KitFieldKind.Path,
            helper = stringResource(Res.string.phoneSetupReadyNameHelp),
            error =
                state.nameProblem?.let {
                    stringResource(
                        when (it) {
                            FirstProjectNameProblem.Empty -> Res.string.phoneSetupReadyNameEmpty
                            FirstProjectNameProblem.OneFolder -> Res.string.phoneSetupReadyNameOneFolder
                            FirstProjectNameProblem.Invalid -> Res.string.phoneSetupReadyNameInvalid
                        }
                    )
                },
            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            enabled = KitEnabled.of(!state.creatingProject, stringResource(Res.string.phoneSetupReadyCreating)),
            onSubmit = actions::createProject,
            onTextChanged = actions::projectName,
        )
        state.projectFailure?.let { (step, failure) ->
            val why =
                stringResource(
                    when {
                        failure is io.github.eslamasabry.oc.core.common.Failure.TimedOut -> Res.string.productErrorTimedOut
                        io.github.eslamasabry.oc.ui.kit.state.KitErrorKind.of(failure) ==
                            io.github.eslamasabry.oc.ui.kit.state.KitErrorKind.Network -> Res.string.phoneSetupErrorNoInternet
                        else -> Res.string.productErrorUnknown
                    }
                )
            KitErrorNotice(
                stringResource(
                    if (step == FirstProjectStep.Create) Res.string.phoneSetupReadyCreateFailed else Res.string.phoneSetupReadyOpenFailed,
                    why,
                ),
                Modifier.testTag("phone-setup-ready-failed"),
                failure = failure,
            )
        }
        KitActionBlock(
            primary =
                KitAction(
                    stringResource(Res.string.phoneSetupReadyCreateOpen),
                    actions::createProject,
                    working = state.creatingProject,
                ),
            tertiary =
                listOf(
                    KitAction(
                        stringResource(Res.string.phoneSetupReadyOpenFolderInstead),
                        actions::openFolderInstead,
                        enabled = KitEnabled.of(!state.creatingProject, stringResource(Res.string.phoneSetupReadyCreating)),
                    )
                ),
        )
    }
}

/**
 * Issue #138: the steps that stop this phone's battery manager closing the app, for the maker's own wording. One card;
 * the buttons open the app's settings page and, where this maker has one, its Autostart or battery screen.
 */
@Composable
internal fun BatteryCardNotice(state: SetupJourneyUi, actions: SetupJourneyActions) {
    val card = state.batteryCard ?: return
    val steps =
        card.steps.map {
            stringResource(
                when (it) {
                    BatteryStep.Unrestricted -> Res.string.setupBatteryStepUnrestricted
                    BatteryStep.Autostart -> Res.string.setupBatteryStepAutostart
                    BatteryStep.NeverSleeping -> Res.string.setupBatteryStepNeverSleeping
                }
            )
        }
    val failed = if (state.batteryOpenFailed) listOf(stringResource(Res.string.setupBatteryOpenFailed)) else emptyList()
    val app = KitLineAction(
            label = stringResource(Res.string.setupBatteryOpenApp),
            onClick = { actions.openBatterySettings(BatteryOpen.App) },
        )
    val maker =
        if (!card.hasMakerScreen) null
        else
            KitLineAction(
                label =
                    stringResource(
                        if (card.maker == SetupMaker.Samsung) Res.string.setupBatteryOpenSamsung
                        else Res.string.setupBatteryOpenAutostart
                    ),
                onClick = { actions.openBatterySettings(BatteryOpen.Maker) },
            )
    KitNotice(
        message = stringResource(Res.string.setupBatteryBody),
        modifier = Modifier.testTag("setup-battery-card"),
        title = stringResource(Res.string.setupBatteryTitle),
        tone = StatusTone.Attention,
        notes = steps + failed,
        actions = listOfNotNull(app, maker),
    )
}

/** The rows the ring counts and which one is current, with the halted step's correction, in one place. */
internal fun ringRowFacts(state: SetupJourneyUi): RingRowFacts {
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
    if (current < 0 && progress.canContinue)
        current = rows.indexOfFirst { it.state != ComponentState.Done && it.state != ComponentState.Skipped }
    return RingRowFacts(rows, done, current, rows.getOrNull(current))
}

/** True when every step is in its after-install check (the ring then says Checking). */
internal fun ringChecking(rows: List<ComponentProgress>): Boolean =
    rows.isNotEmpty() &&
        rows.all {
            it.state == ComponentState.Checking || it.state == ComponentState.Done ||
                it.state == ComponentState.Skipped
        } &&
        rows.any { it.state == ComponentState.Checking }

/** The ring's title while the job works: which stage the current row is in, in the person's words. */
@Composable
internal fun jobWorkingTitle(
    checking: Boolean,
    row: ComponentProgress?,
    subject: String,
    unpacking: String,
): String =
    when {
        checking -> stringResource(Res.string.phoneSetupRingChecking)
        row == null || row.state == ComponentState.Pending ->
            stringResource(Res.string.setupProgressViewGettingStarted)
        row.id == SetupComponentIds.START ->
            stringResource(Res.string.phoneSetupStageStarting)
        row.stage?.let {
                it == unpacking || Regex("unpack|extract", RegexOption.IGNORE_CASE).containsMatchIn(it)
            } == true -> stringResource(Res.string.phoneSetupRingUnpacking, subject)
        ((row.bytesTotal ?: 0) > (row.bytesDone ?: 0)) ||
            row.stage?.let {
                Regex("download|fetch", RegexOption.IGNORE_CASE).containsMatchIn(it)
            } == true -> stringResource(Res.string.phoneSetupRingDownloading, subject)
        else -> stringResource(Res.string.phoneSetupRingInstalling, subject)
    }

/** One step's measured share of the ring's bytes caption: "x of y" when the size is known, else its percent. */
@Composable
internal fun jobRowMeasured(component: ComponentProgress): String? =
    if ((component.bytesTotal ?: 0) > 0 && component.bytesDone != null) {
        val (received, total) = SetupProgressRules.formatBytePair(component.bytesDone!!, component.bytesTotal!!)
        stringResource(Res.string.setupProgressViewBytes, received, total)
    } else
        component.percent?.let {
            stringResource(Res.string.setupProgressViewPercent, it.coerceIn(0.0, 100.0).toInt())
        }

/** One step's stage words, or the Checking word when the whole job is in its after-install check. */
@Composable
internal fun jobRowStage(component: ComponentProgress): String? =
    component.stage?.let { setupMessageWords(it) }
        ?: if (component.state == ComponentState.Checking)
            stringResource(Res.string.setupProgressViewChecking)
        else null

/** One step's supporting line: version when done, the problem when failed, else stage and measure together. */
@Composable
internal fun jobRowSupporting(
    component: ComponentProgress,
    stage: String?,
    measured: String?,
    failureWords: String?,
): String? =
    when (component.state) {
        ComponentState.Done,
        ComponentState.Skipped -> component.version
        ComponentState.Failed -> failureWords
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

/** The plain values [ringRowFacts] hands the ring: rows, done count, current index and row. */
internal data class RingRowFacts(
    val rows: List<ComponentProgress>,
    val done: Int,
    val current: Int,
    val row: ComponentProgress?,
)
