package io.github.eslamasabry.oc.feature.git.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.engine.git.GitActionNote
import io.github.eslamasabry.oc.engine.git.GitActionResult
import io.github.eslamasabry.oc.engine.git.GitBusy
import io.github.eslamasabry.oc.engine.git.GitChangeKind
import io.github.eslamasabry.oc.engine.git.GitFileChange
import io.github.eslamasabry.oc.engine.git.GitHubSignInProblem
import io.github.eslamasabry.oc.engine.git.GitOperation
import io.github.eslamasabry.oc.engine.git.GitProblem
import io.github.eslamasabry.oc.engine.git.GitUnavailableReason
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// The Git page's words for the engine's typed states (port of lib/ui/git_words.dart). All copy is app-authored and
// lives in :ui:words (English + Arabic); Git's own text belongs under Details only.

@Composable
internal fun gitProblemText(problem: GitProblem, offersGitHubSignIn: Boolean = false): String =
    stringResource(
        when (problem) {
            GitProblem.NotARepository -> Res.string.gitProblemNotARepository
            GitProblem.GitMissing -> Res.string.gitProblemGitMissing
            GitProblem.FolderMissing -> Res.string.gitProblemFolderMissing
            GitProblem.EnvironmentUnavailable -> Res.string.gitProblemEnvironmentUnavailable
            GitProblem.ServerCannotRunCommands -> Res.string.gitProblemServerCannotRunCommands
            GitProblem.OutputTooLarge -> Res.string.gitProblemOutputTooLarge
            GitProblem.TimedOut -> Res.string.gitProblemTimedOut
            GitProblem.UnsafeRepository -> Res.string.gitProblemUnsafeRepository
            GitProblem.RepositoryLocked -> Res.string.gitProblemRepositoryLocked
            GitProblem.NoUpstream -> Res.string.gitProblemNoUpstream
            GitProblem.NoRemote -> Res.string.gitProblemNoRemote
            GitProblem.DetachedHead -> Res.string.gitProblemDetachedHead
            GitProblem.CannotFastForward -> Res.string.gitProblemCannotFastForward
            GitProblem.PushRejected -> Res.string.gitProblemPushRejected
            GitProblem.LocalChangesBlock -> Res.string.gitProblemLocalChangesBlock
            GitProblem.AuthRequired ->
                if (offersGitHubSignIn) Res.string.gitProblemAuthRequiredSignIn else Res.string.gitProblemAuthRequired
            GitProblem.GitHubTokenRejected -> Res.string.gitProblemGitHubTokenRejected
            GitProblem.NoPermission -> Res.string.gitProblemNoPermission
            GitProblem.Network -> Res.string.gitProblemNetwork
            GitProblem.RemoteNotFound -> Res.string.gitProblemRemoteNotFound
            GitProblem.NothingSelected -> Res.string.gitProblemNothingSelected
            GitProblem.EmptyMessage -> Res.string.gitProblemEmptyMessage
            GitProblem.NothingToCommit -> Res.string.gitProblemNothingToCommit
            GitProblem.IdentityMissing -> Res.string.gitProblemIdentityMissing
            GitProblem.SigningFailed -> Res.string.gitProblemSigningFailed
            GitProblem.HookRejected -> Res.string.gitProblemHookRejected
            GitProblem.InvalidBranchName -> Res.string.gitProblemInvalidBranchName
            GitProblem.BranchExists -> Res.string.gitProblemBranchExists
            GitProblem.BranchNotFound -> Res.string.gitProblemBranchNotFound
            GitProblem.InvalidRemoteName -> Res.string.gitProblemInvalidRemoteName
            GitProblem.InvalidRemoteUrl -> Res.string.gitProblemInvalidRemoteUrl
            GitProblem.RemoteAddressHasPassword -> Res.string.gitProblemRemoteAddressHasPassword
            GitProblem.RemoteExists -> Res.string.gitProblemRemoteExists
            GitProblem.InvalidFolderName -> Res.string.gitProblemInvalidFolderName
            GitProblem.FolderExists -> Res.string.gitProblemFolderExists
            GitProblem.Conflicts -> Res.string.gitProblemConflicts
            GitProblem.OperationInProgress -> Res.string.gitProblemOperationInProgress
            GitProblem.CannotDiscardConflict -> Res.string.gitProblemCannotDiscardConflict
            GitProblem.Unknown -> Res.string.gitProblemUnknown
        },
    )

@Composable
internal fun gitUnavailableText(reason: GitUnavailableReason): String =
    stringResource(
        when (reason) {
            GitUnavailableReason.NotConnected -> Res.string.gitUnavailableNotConnected
            GitUnavailableReason.NoProject -> Res.string.gitUnavailableNoProject
            GitUnavailableReason.ServerCannotRunCommands -> Res.string.gitUnavailableServerCannotRun
        },
    )

/** The busy line while an action runs. */
@Composable
internal fun gitBusyText(busy: GitBusy): String =
    stringResource(
        when (busy) {
            GitBusy.Refresh -> Res.string.gitBusyRefresh
            GitBusy.Pull -> Res.string.gitBusyPull
            GitBusy.Push, GitBusy.SaveThenSend -> Res.string.gitBusyPush
            GitBusy.Commit -> Res.string.gitBusyCommit
            GitBusy.SwitchBranch -> Res.string.gitBusySwitchBranch
            GitBusy.CreateBranch -> Res.string.gitBusyCreateBranch
            GitBusy.Discard -> Res.string.gitBusyDiscard
            GitBusy.SetIdentity -> Res.string.gitBusySaveIdentity
        },
    )

/** What a finished action that worked says. */
@Composable
internal fun gitDoneText(result: GitActionResult): String {
    val count = result.count
    return when (result.note) {
        GitActionNote.UpToDate -> stringResource(Res.string.gitDoneUpToDate)
        GitActionNote.Pulled ->
            if (count == null) stringResource(Res.string.gitDonePulled) else pluralStringResource(Res.plurals.gitDoneGotN, count, count)
        GitActionNote.Pushed, GitActionNote.PushedAndTracking -> stringResource(Res.string.gitDonePushed)
        GitActionNote.Committed ->
            if (count == null) stringResource(Res.string.gitDoneSavedPlain) else pluralStringResource(Res.plurals.gitDoneSaved, count, count)
        GitActionNote.Switched -> stringResource(Res.string.gitDoneSwitched)
        GitActionNote.Created -> stringResource(Res.string.gitDoneCreated)
        GitActionNote.Discarded -> stringResource(Res.string.gitDoneDiscarded)
        GitActionNote.IdentitySaved -> stringResource(Res.string.gitDoneIdentitySaved)
        null -> ""
    }
}

/** The short kind label of a changed file row. */
@Composable
internal fun gitChangeLabel(file: GitFileChange): String =
    stringResource(
        if (file.isConflicted) {
            Res.string.gitChangeConflicted
        } else {
            when (file.kind) {
                GitChangeKind.Added -> Res.string.gitChangeAdded
                GitChangeKind.Deleted -> Res.string.gitChangeDeleted
                GitChangeKind.Renamed -> Res.string.gitChangeRenamed
                GitChangeKind.Copied -> Res.string.gitChangeCopied
                GitChangeKind.TypeChanged -> Res.string.gitChangeTypeChanged
                GitChangeKind.Untracked -> Res.string.gitChangeUntracked
                GitChangeKind.Modified, GitChangeKind.Ignored, null -> Res.string.gitChangeModified
            }
        },
    )

/** The banner for a half-done operation; null when there is none. */
@Composable
internal fun gitOperationText(operation: GitOperation): String? =
    when (operation) {
        GitOperation.None -> null
        GitOperation.Merge -> stringResource(Res.string.gitOperationMerge)
        GitOperation.Rebase -> stringResource(Res.string.gitOperationRebase)
        GitOperation.CherryPick -> stringResource(Res.string.gitOperationCherryPick)
        GitOperation.Revert -> stringResource(Res.string.gitOperationRevert)
    }

@Composable
internal fun gitHubProblemText(problem: GitHubSignInProblem): String =
    stringResource(
        when (problem) {
            GitHubSignInProblem.Expired -> Res.string.gitHubProblemExpired
            GitHubSignInProblem.Denied -> Res.string.gitHubProblemDenied
            GitHubSignInProblem.DeviceFlowDisabled -> Res.string.gitHubProblemDisabled
            GitHubSignInProblem.Network -> Res.string.gitHubProblemNetwork
            GitHubSignInProblem.TokenRejected -> Res.string.gitHubProblemTokenRejected
            GitHubSignInProblem.Unexpected, GitHubSignInProblem.Cancelled -> Res.string.gitHubProblemUnexpected
        },
    )

/** `+3 −1`, or nothing when the counts are unknown. Digits are not words: no translation needed. */
internal fun gitCounts(file: GitFileChange): String? {
    val added = file.totalAdded
    val deleted = file.totalDeleted
    if (added == null && deleted == null) return null
    return "+${added ?: 0} −${deleted ?: 0}"
}
