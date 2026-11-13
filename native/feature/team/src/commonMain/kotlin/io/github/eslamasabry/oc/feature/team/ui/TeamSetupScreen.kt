package io.github.eslamasabry.oc.feature.team.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.team.SetupPhase
import io.github.eslamasabry.oc.engine.team.SetupProblem
import io.github.eslamasabry.oc.engine.team.SetupServerState
import io.github.eslamasabry.oc.engine.team.SetupStep
import io.github.eslamasabry.oc.feature.team.presentation.SetupMark
import io.github.eslamasabry.oc.feature.team.presentation.SetupNote
import io.github.eslamasabry.oc.feature.team.presentation.SetupProof
import io.github.eslamasabry.oc.feature.team.presentation.SetupProofKind
import io.github.eslamasabry.oc.feature.team.presentation.SetupStepUi
import io.github.eslamasabry.oc.feature.team.presentation.TeamSetupActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamSetupUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.state.KitChecklist
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateSize
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.state.KitStep
import io.github.eslamasabry.oc.ui.kit.state.KitStepState
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * "Turn on AI Team on this phone" (Dart `TeamPhoneSetupScreen`): one tap, then four real steps each with its own time
 * and a mark: the reply finishes, OpenCode and the terminals stop (only after a yes, in one line), the engine proves
 * this phone safe, OpenCode comes back protected. A failure says what happened in plain words, keeps its technical text
 * under Details and offers Start again. The engine's own checks are only shown here, never decided.
 */
@Composable
public fun TeamSetupScreen(state: TeamSetupUi, actions: TeamSetupActions) {
    KitSheetHost(Modifier.fillMaxSize()) {
        KitScreen(
            topBar = {
                KitTopBar(
                    title = stringResource(Res.string.teamUiHomeTitle),
                    exit = KitTopBarExit.Back,
                    exitLabel = stringResource(Res.string.kitTopBarBack),
                    onExit = actions::back,
                )
            },
            width = KitScreenWidth.Reading,
        ) {
            LazyColumn(
                Modifier.fillMaxSize().testTag("team-setup"),
                contentPadding = PaddingValues(top = Space.S2.dp, bottom = Space.S6.dp),
                verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
            ) {
                item(key = "state") { SetupHeader(state, actions) }
                if (state.phase == SetupPhase.Done && state.protection != null) {
                    item(key = "protection") {
                        KitNotice(
                            stringResource(
                                if (state.protection == "proot") Res.string.phoneTeamProtectedProot
                                else Res.string.phoneTeamProtectedLandlock,
                            ),
                            Modifier.testTag("team-setup-protection"),
                            icon = KitIcons.shield,
                        )
                    }
                }
                if (state.phase == SetupPhase.Failed && !state.calm && state.details != null) {
                    item(key = "why") {
                        val proof = state.proof?.let { proofLine(it) }
                        KitNotice(
                            whyWords(state.problem, proof != null),
                            Modifier.testTag("team-setup-why"),
                            notes = listOfNotNull(proof),
                        )
                    }
                }
                if (state.problem != SetupProblem.NoServer && state.steps.isNotEmpty()) {
                    item(key = "steps") {
                        KitChecklist(steps(state), Modifier.testTag("team-setup-steps"))
                    }
                }
            }
        }
        KitConfirm(
            spec =
                if (state.phase == SetupPhase.Confirming) {
                    KitConfirmSpec(
                        title = stringResource(Res.string.phoneTeamStopTitle),
                        confirmLabel = stringResource(Res.string.phoneTeamStopConfirm),
                        kind = KitConfirmKind.Stop,
                        body = stringResource(Res.string.phoneTeamStopBody),
                        cancelLabel = stringResource(Res.string.phoneTeamStopCancel),
                    )
                } else {
                    null
                },
            onConfirm = { actions.answer(true) },
            onCancel = { actions.answer(false) },
        )
    }
}

@Composable
private fun SetupHeader(state: TeamSetupUi, actions: TeamSetupActions) {
    when (state.phase) {
        SetupPhase.Done ->
            KitStateView(
                KitStateKind.Finished,
                stringResource(Res.string.phoneTeamDoneTitle),
                Modifier.testTag("team-setup-ready"),
                body = stringResource(Res.string.phoneTeamDoneBody),
                primary = KitAction(stringResource(Res.string.teamProjectNew), actions::openTeam),
                size = KitStateSize.Inline,
            )
        SetupPhase.Failed -> {
            val words = problemWords(state)
            val action =
                if (state.problem == SetupProblem.NoServer) null
                else KitAction(stringResource(Res.string.teamStartAgain), actions::start, icon = KitIcons.play)
            KitStateView(
                if (state.calm) KitStateKind.Empty else KitStateKind.Error,
                words.first,
                Modifier.testTag("team-setup-failed"),
                body = words.second,
                icon = if (state.calm) KitIcons.phone else KitIcons.warning,
                primary = action,
                secondary =
                    if (state.calm) null
                    else KitAction(stringResource(Res.string.failedJobReport), actions::reportFailure, icon = KitIcons.bug),
                details = state.details,
                size = KitStateSize.Inline,
            )
        }
        SetupPhase.Idle,
        SetupPhase.Running,
        SetupPhase.Confirming ->
            KitStateView(
                KitStateKind.Working,
                stringResource(Res.string.phoneTeamSetupTitle),
                Modifier.testTag("team-setup-running"),
                icon = KitIcons.waiting,
                progress = KitProgress.Known(state.finished.toFloat() / SetupStep.entries.size),
                primary =
                    if (state.phase == SetupPhase.Idle) {
                        KitAction(stringResource(Res.string.teamIntroTurnOnPhone), actions::start, icon = KitIcons.play)
                    } else {
                        null
                    },
                size = KitStateSize.Inline,
            )
    }
}

/** The plain title and body of a failure, with exactly what state the phone is in (Dart `phoneTeamProblemCopy`). */
@Composable
private fun problemWords(state: TeamSetupUi): Pair<String, String> {
    val problem = state.problem ?: SetupProblem.Engine
    val base =
        when (problem) {
            SetupProblem.Unsafe -> Res.string.phoneTeamFailUnsafeTitle to Res.string.phoneTeamFailUnsafeBody
            SetupProblem.Engine -> Res.string.phoneTeamFailEngineTitle to Res.string.phoneTeamFailEngineBody
            SetupProblem.StopFailed -> Res.string.phoneTeamFailStopTitle to Res.string.phoneTeamFailStopBody
            SetupProblem.ServerStart -> Res.string.phoneTeamFailServerTitle to Res.string.phoneTeamFailServerBody
            SetupProblem.NotReady -> Res.string.phoneTeamFailNotReadyTitle to Res.string.phoneTeamFailNotReadyBody
            SetupProblem.Declined -> Res.string.phoneTeamFailDeclinedTitle to Res.string.phoneTeamFailDeclinedBody
            SetupProblem.NoServer -> Res.string.phoneTeamFailNoServerTitle to Res.string.phoneTeamFailNoServerBody
        }
    val server =
        if (problem == SetupProblem.Unsafe || problem == SetupProblem.Engine || problem == SetupProblem.StopFailed) {
            stringResource(
                when (state.serverState) {
                    SetupServerState.BackOn -> Res.string.phoneTeamStateBackOn
                    SetupServerState.StillOff -> Res.string.phoneTeamStateStillOff
                    SetupServerState.Untouched -> Res.string.phoneTeamStateNotStopped
                },
            )
        } else {
            null
        }
    val terminals =
        if (state.terminalsClosed && !state.calm) stringResource(Res.string.phoneTeamStateTerminalsClosed) else null
    val body = listOfNotNull(stringResource(base.second), server, terminals).joinToString(" ")
    return stringResource(base.first) to body
}

/** The one sentence under a failure that says why, before the technical code (Dart `phoneTeamDetailsText`). */
@Composable
private fun whyWords(problem: SetupProblem?, proofFound: Boolean): String =
    stringResource(
        if (proofFound) Res.string.phoneTeamWhyUnsafe
        else
            when (problem) {
                SetupProblem.Unsafe -> Res.string.phoneTeamWhyUnsafe
                SetupProblem.StopFailed -> Res.string.phoneTeamWhyStop
                SetupProblem.ServerStart -> Res.string.phoneTeamWhyServer
                SetupProblem.NotReady -> Res.string.phoneTeamWhyNotReady
                else -> Res.string.phoneTeamWhyEngine
            },
    )

/** The plain line naming where a failed safety check stopped (Dart `phoneTeamProofStepText`, issue #111). */
@Composable
private fun proofLine(proof: SetupProof): String {
    val check = proof.check ?: proof.code
    return when (proof.kind) {
        SetupProofKind.Signal -> stringResource(Res.string.phoneTeamProofSignal, proof.signal ?: 0)
        SetupProofKind.CheckSignal -> stringResource(Res.string.phoneTeamProofCheckSignal, check, proof.signal ?: 0)
        SetupProofKind.NotBlocked -> stringResource(Res.string.phoneTeamProofNotBlocked, check)
        SetupProofKind.Sandbox -> stringResource(Res.string.phoneTeamProofSandbox)
        SetupProofKind.Ubuntu -> stringResource(Res.string.phoneTeamProofUbuntu, check)
        SetupProofKind.Fixture -> stringResource(Res.string.phoneTeamProofFixture)
        SetupProofKind.NoAnswer -> stringResource(Res.string.phoneTeamProofNoAnswer)
        SetupProofKind.Proot -> stringResource(Res.string.phoneTeamProofProot, check)
        SetupProofKind.Other -> stringResource(Res.string.phoneTeamProofOther, check)
    }
}

@Composable
private fun elapsed(ms: Long): String {
    val seconds = (ms / 1_000).coerceAtLeast(0)
    return if (seconds < 60) stringResource(Res.string.teamProjectElapsedSeconds, seconds.toInt())
    else stringResource(Res.string.teamProjectElapsedMinutes, (seconds / 60).toInt())
}

/** One row per step; every mark comes from the engine's own signals. */
@Composable
private fun steps(state: TeamSetupUi): List<KitStep> = state.steps.map { step(it) }

@Composable
private fun step(step: SetupStepUi): KitStep {
    val title =
        stringResource(
            when (step.step) {
                SetupStep.Reply -> Res.string.phoneTeamStepReply
                SetupStep.Stop -> Res.string.phoneTeamStepStop
                SetupStep.Check -> Res.string.phoneTeamStepCheck
                SetupStep.Server -> Res.string.phoneTeamStepServer
            },
        )
    val words =
        when (step.note) {
            SetupNote.NotNeeded -> stringResource(Res.string.phoneTeamNotNeeded)
            SetupNote.ReplyWaiting -> stringResource(Res.string.phoneTeamReplyWaiting)
            SetupNote.StopWaiting -> stringResource(Res.string.phoneTeamStopWaiting)
            SetupNote.NotPackaged -> stringResource(Res.string.phoneTeamReasonNotPackaged)
            SetupNote.None -> null
        }
    val supporting =
        when {
            step.mark == SetupMark.Done && step.note != SetupNote.NotNeeded ->
                step.tookMs?.let { stringResource(Res.string.aiteamComponentStageTook, elapsed(it)) }
            step.mark == SetupMark.Done -> words
            step.mark == SetupMark.Working -> {
                val time = step.soFarMs?.let { stringResource(Res.string.aiteamComponentStageSoFar, elapsed(it)) }
                listOfNotNull(words, time).joinToString(" · ").ifEmpty { null }
            }
            step.mark == SetupMark.Failed -> words
            else -> null
        }
    return KitStep(
        title = title,
        state =
            when (step.mark) {
                SetupMark.Waiting -> KitStepState.Waiting
                SetupMark.Working -> KitStepState.Working
                SetupMark.Done -> KitStepState.Done
                SetupMark.Failed -> KitStepState.Failed
            },
        supporting = supporting,
        testTag = "team-setup-step-${step.step.name.lowercase()}",
    )
}
