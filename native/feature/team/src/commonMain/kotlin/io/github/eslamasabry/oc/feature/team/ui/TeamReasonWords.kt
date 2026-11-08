package io.github.eslamasabry.oc.feature.team.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.team.presentation.Did
import io.github.eslamasabry.oc.feature.team.presentation.EditorProblem
import io.github.eslamasabry.oc.feature.team.presentation.Reason
import io.github.eslamasabry.oc.feature.team.presentation.TeamReasons
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A refusal as the person reads it (Dart `TeamRefusal`): the sentence, the way forward, and the code for details. */
internal data class RefusalWords(val message: String, val next: String, val code: String)

private fun Reason.res(): Pair<StringResource, StringResource> =
    when (this) {
        Reason.RepositoryEmpty -> Res.string.teamRefusalRepositoryEmpty to Res.string.teamRefusalRepositoryEmptyNext
        Reason.RepoPathInvalid -> Res.string.teamRefusalRepoPathInvalid to Res.string.teamRefusalRepoPathInvalidNext
        Reason.RepositoryLink -> Res.string.teamRefusalRepositoryLink to Res.string.teamRefusalRepositoryLinkNext
        Reason.RepositoryDamaged -> Res.string.teamRefusalRepositoryDamaged to Res.string.teamRefusalRepositoryDamagedNext
        Reason.PlanTaskName -> Res.string.teamRefusalPlanTaskName to Res.string.teamRefusalPlanTaskNameNext
        Reason.PlanPhase -> Res.string.teamRefusalPlanPhase to Res.string.teamRefusalPlanPhaseNext
        Reason.UnsupportedCommand -> Res.string.teamRefusalUnsupportedCommand to Res.string.teamRefusalUnsupportedCommandNext
        Reason.BoundaryUnverified -> Res.string.teamRefusalBoundaryUnverified to Res.string.teamRefusalBoundaryUnverifiedNext
        Reason.ProtocolUnverified -> Res.string.teamRefusalProtocolUnverified to Res.string.teamRefusalProtocolUnverifiedNext
        Reason.EngineUnavailable -> Res.string.teamRefusalEngineUnavailable to Res.string.teamRefusalEngineUnavailableNext
        Reason.TransportUncertain -> Res.string.teamRefusalTransportUncertain to Res.string.teamRefusalTransportUncertainNext
        Reason.Busy -> Res.string.teamRefusalBusy to Res.string.teamRefusalBusyNext
        Reason.SaveFailed -> Res.string.teamRefusalSaveFailed to Res.string.teamRefusalSaveFailedNext
        Reason.ReadOnly -> Res.string.teamRefusalReadOnly to Res.string.teamRefusalReadOnlyNext
        Reason.Closed -> Res.string.teamRefusalClosed to Res.string.teamRefusalClosedNext
        Reason.CommandRefused -> Res.string.teamRefusalCommandRefused to Res.string.teamRefusalCommandRefusedNext
        Reason.ImportFailed -> Res.string.teamRefusalImportFailed to Res.string.teamRefusalImportFailedNext
        Reason.PayloadInvalid -> Res.string.teamRefusalPayloadInvalid to Res.string.teamRefusalPayloadInvalidNext
        Reason.SchemaUnsupported -> Res.string.teamRefusalSchemaUnsupported to Res.string.teamRefusalSchemaUnsupportedNext
        Reason.EngineClosed -> Res.string.teamRefusalEngineClosed to Res.string.teamRefusalEngineClosedNext
        Reason.SessionFailed -> Res.string.teamRefusalSessionFailed to Res.string.teamRefusalSessionFailedNext
        Reason.ModelNotConfigured -> Res.string.teamRefusalModelNotConfigured to Res.string.teamRefusalModelNotConfiguredNext
        Reason.ModelUnavailable -> Res.string.teamRefusalModelUnavailable to Res.string.teamRefusalModelUnavailableNext
        Reason.AuthFailed -> Res.string.teamRefusalAuthFailed to Res.string.teamRefusalAuthFailedNext
        Reason.CloneFailed -> Res.string.teamRefusalCloneFailed to Res.string.teamRefusalCloneFailedNext
        Reason.SessionUncertain -> Res.string.teamRefusalSessionUncertain to Res.string.teamRefusalSessionUncertainNext
        Reason.PlanInvalid -> Res.string.teamRefusalPlanInvalid to Res.string.teamRefusalPlanInvalidNext
        Reason.CheckerUnreadable -> Res.string.teamRefusalCheckerUnreadable to Res.string.teamRefusalCheckerUnreadableNext
        Reason.NeedsAnswer -> Res.string.teamRefusalNeedsAnswer to Res.string.teamRefusalNeedsAnswerNext
        Reason.RecoveryReview -> Res.string.teamRefusalRecoveryReview to Res.string.teamRefusalRecoveryReviewNext
        Reason.AppStopped -> Res.string.teamRefusalAppStopped to Res.string.teamRefusalAppStoppedNext
        Reason.ChatBusy -> Res.string.teamRefusalChatBusy to Res.string.teamRefusalChatBusyNext
        Reason.BudgetReached -> Res.string.teamRefusalBudgetReached to Res.string.teamRefusalBudgetReachedNext
    }

@Composable
internal fun reasonMessage(reason: Reason): String = stringResource(reason.res().first)

/** Dart `teamReasonFor`: null for an unknown code (the caller keeps the engine's own sentence). */
@Composable
internal fun reasonWords(code: String): RefusalWords? =
    TeamReasons.reason(code)?.let { RefusalWords(stringResource(it.res().first), stringResource(it.res().second), code) }

/** Dart `teamRefusalFor`: an unknown code reads "The team couldn't <what you asked>" and keeps the code in details. */
@Composable
internal fun refusalWords(action: String, code: String): RefusalWords =
    reasonWords(code)
        ?: RefusalWords(
            stringResource(
                Res.string.teamRefusalUnknown,
                stringResource(
                    when (TeamReasons.did(action)) {
                        Did.Plan -> Res.string.teamRefusalDidPlan
                        Did.Quick -> Res.string.teamRefusalDidQuick
                        Did.Approve -> Res.string.teamRefusalDidApprove
                        Did.Spec -> Res.string.teamRefusalDidSpec
                        Did.Promote -> Res.string.teamRefusalDidPromote
                        Did.Stop -> Res.string.teamRefusalDidStop
                        Did.Pause -> Res.string.teamRefusalDidPause
                        Did.Resume -> Res.string.teamRefusalDidResume
                        Did.Save -> Res.string.teamRefusalDidSave
                    }
                ),
            ),
            stringResource(Res.string.teamRefusalUnknownNext),
            code,
        )

/** Dart `teamTimelineWords`: a known code becomes its sentence, an unknown one is cut off. */
@Composable
internal fun timelineWords(text: String): String {
    val (plain, reason) = TeamReasons.timelineText(text)
    return reason?.let { reasonMessage(it) } ?: plain
}

@Composable
internal fun problemWords(problem: EditorProblem): String =
    stringResource(
        when (problem) {
            EditorProblem.ChooseModeBudget -> Res.string.teamProjectEditorChooseModeBudget
            EditorProblem.ChooseMode -> Res.string.teamProjectEditorChooseMode
            EditorProblem.PositiveLanes -> Res.string.teamProjectEditorPositiveLanes
            EditorProblem.ChooseBudget -> Res.string.teamProjectEditorChooseBudget
            EditorProblem.PositiveBudget -> Res.string.teamProjectEditorPositiveBudget
            EditorProblem.FixRoundsRange -> Res.string.teamProjectEditorFixRoundsRange
            EditorProblem.PositiveTokens -> Res.string.teamProjectEditorPositiveTokens
            EditorProblem.Required -> Res.string.teamProjectEditorRequired
            EditorProblem.GoalRequired -> Res.string.teamProjectEditorGoalRequired
            EditorProblem.RepoIncomplete -> Res.string.teamProjectEditorRepoIncomplete
            EditorProblem.RepoMissing -> Res.string.teamProjectEditorRepoMissing
            EditorProblem.ChooseRoleServer -> Res.string.teamProjectEditorChooseRoleServer
            EditorProblem.RepoRequired -> Res.string.teamProjectEditorRepoRequired
            EditorProblem.SpecRequired -> Res.string.teamProjectEditorSpecRequired
            EditorProblem.ChangeRequired -> Res.string.teamProjectEditorChangeRequired
            EditorProblem.RoleRequired -> Res.string.teamProjectEditorRoleRequired
            EditorProblem.DraftFailed -> Res.string.teamProjectEditorDraftFailed
            EditorProblem.DraftClearFailed -> Res.string.teamProjectEditorDraftClearFailed
            EditorProblem.ChangedElsewhere -> Res.string.teamProjectEditorChangedElsewhere
        }
    )
