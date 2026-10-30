import '../domain/git/git_models.dart';
import '../domain/git/git_problem.dart';
import '../domain/git/git_repository.dart';
import '../domain/git/github_device_flow.dart';
import '../l10n/app_localizations.dart';
import '../state/git_workspace.dart';

// The Git page's words for the typed states of lib/domain/git and
// lib/state/git_page_controller.dart. All copy is app-authored; Git's own
// text (GitFailure.details) belongs under Details only.

/// The sentence for [problem]. [offersGitHubSignIn] picks the sign-in advice
/// for [GitProblem.authRequired] (sign in here, or set up Git there).
String gitProblemText(
  GitProblem problem,
  AppLocalizations l10n, {
  bool offersGitHubSignIn = false,
}) => switch (problem) {
  GitProblem.notARepository => l10n.gitProblemNotARepository,
  GitProblem.gitMissing => l10n.gitProblemGitMissing,
  GitProblem.folderMissing => l10n.gitProblemFolderMissing,
  GitProblem.environmentUnavailable => l10n.gitProblemEnvironmentUnavailable,
  GitProblem.serverCannotRunCommands => l10n.gitProblemServerCannotRunCommands,
  GitProblem.outputTooLarge => l10n.gitProblemOutputTooLarge,
  GitProblem.timedOut => l10n.gitProblemTimedOut,
  GitProblem.unsafeRepository => l10n.gitProblemUnsafeRepository,
  GitProblem.repositoryLocked => l10n.gitProblemRepositoryLocked,
  GitProblem.noUpstream => l10n.gitProblemNoUpstream,
  GitProblem.noRemote => l10n.gitProblemNoRemote,
  GitProblem.detachedHead => l10n.gitProblemDetachedHead,
  GitProblem.cannotFastForward => l10n.gitProblemCannotFastForward,
  GitProblem.pushRejected => l10n.gitProblemPushRejected,
  GitProblem.localChangesBlock => l10n.gitProblemLocalChangesBlock,
  GitProblem.authRequired =>
    offersGitHubSignIn
        ? l10n.gitProblemAuthRequiredSignIn
        : l10n.gitProblemAuthRequired,
  GitProblem.gitHubTokenRejected => l10n.gitProblemGitHubTokenRejected,
  GitProblem.noPermission => l10n.gitProblemNoPermission,
  GitProblem.network => l10n.gitProblemNetwork,
  GitProblem.remoteNotFound => l10n.gitProblemRemoteNotFound,
  GitProblem.nothingSelected => l10n.gitProblemNothingSelected,
  GitProblem.emptyMessage => l10n.gitProblemEmptyMessage,
  GitProblem.nothingToCommit => l10n.gitProblemNothingToCommit,
  GitProblem.identityMissing => l10n.gitProblemIdentityMissing,
  GitProblem.signingFailed => l10n.gitProblemSigningFailed,
  GitProblem.hookRejected => l10n.gitProblemHookRejected,
  GitProblem.invalidBranchName => l10n.gitProblemInvalidBranchName,
  GitProblem.branchExists => l10n.gitProblemBranchExists,
  GitProblem.branchNotFound => l10n.gitProblemBranchNotFound,
  GitProblem.conflicts => l10n.gitProblemConflicts,
  GitProblem.operationInProgress => l10n.gitProblemOperationInProgress,
  GitProblem.cannotDiscardConflict => l10n.gitProblemCannotDiscardConflict,
  GitProblem.invalidRemoteName => l10n.gitProblemInvalidRemoteName,
  GitProblem.invalidRemoteUrl => l10n.gitProblemInvalidRemoteUrl,
  GitProblem.remoteAddressHasPassword =>
    l10n.gitProblemRemoteAddressHasPassword,
  GitProblem.remoteExists => l10n.gitProblemRemoteExists,
  GitProblem.invalidFolderName => l10n.gitProblemInvalidFolderName,
  GitProblem.folderExists => l10n.gitProblemFolderExists,
  GitProblem.unknown => l10n.gitProblemUnknown,
};

String gitUnavailableText(GitUnavailableReason reason, AppLocalizations l10n) =>
    switch (reason) {
      GitUnavailableReason.notConnected => l10n.gitUnavailableNotConnected,
      GitUnavailableReason.noProject => l10n.gitUnavailableNoProject,
      GitUnavailableReason.serverCannotRunCommands =>
        l10n.gitUnavailableServerCannotRun,
    };

/// The busy line while [action] runs.
String gitBusyText(GitAction action, AppLocalizations l10n) => switch (action) {
  GitAction.refresh => l10n.gitBusyRefresh,
  GitAction.pull => l10n.gitBusyPull,
  GitAction.push => l10n.gitBusyPush,
  GitAction.commit => l10n.gitBusyCommit,
  GitAction.switchBranch => l10n.gitBusySwitchBranch,
  GitAction.createBranch => l10n.gitBusyCreateBranch,
  GitAction.discard => l10n.gitBusyDiscard,
  GitAction.setIdentity => l10n.gitBusySaveIdentity,
  GitAction.addRemote => l10n.gitBusyAddRemote,
  GitAction.clone => l10n.gitBusyClone,
};

/// The button label for [action].
String gitActionLabel(GitAction action, AppLocalizations l10n) =>
    switch (action) {
      GitAction.refresh => l10n.gitActionRefresh,
      GitAction.pull => l10n.gitActionPull,
      GitAction.push => l10n.gitActionPush,
      GitAction.commit => l10n.gitActionCommit,
      GitAction.switchBranch => l10n.gitActionSwitchBranch,
      GitAction.createBranch => l10n.gitActionCreateBranch,
      GitAction.discard => l10n.gitActionDiscard,
      GitAction.setIdentity => l10n.gitActionSaveIdentity,
      GitAction.addRemote => l10n.gitActionAddRemote,
      GitAction.clone => l10n.gitActionClone,
    };

/// The status line for a finished action: what worked, or the problem.
String gitResultText(
  GitActionResult result,
  AppLocalizations l10n, {
  bool offersGitHubSignIn = false,
}) {
  final failure = result.failure;
  if (failure != null) {
    return gitProblemText(
      failure.problem,
      l10n,
      offersGitHubSignIn: offersGitHubSignIn,
    );
  }
  return switch (result.note!) {
    GitActionNote.upToDate => l10n.gitDoneUpToDate,
    GitActionNote.pulled =>
      result.count == null
          ? l10n.gitDonePulled
          : l10n.gitDoneGotN(result.count!),
    GitActionNote.pushed ||
    GitActionNote.pushedAndTracking => l10n.gitDonePushed,
    GitActionNote.committed =>
      result.count == null
          ? l10n.gitDoneSavedPlain
          : l10n.gitDoneSaved(result.count!),
    GitActionNote.switched => l10n.gitDoneSwitched,
    GitActionNote.created => l10n.gitDoneCreated,
    GitActionNote.discarded => l10n.gitDoneDiscarded,
    GitActionNote.identitySaved => l10n.gitDoneIdentitySaved,
    GitActionNote.remoteAdded => l10n.gitDoneRemoteAdded,
    GitActionNote.cloned => l10n.gitDoneCloned(result.clonedTo ?? ''),
  };
}

/// The short kind label of a changed file row.
String gitChangeLabel(GitFileChange file, AppLocalizations l10n) {
  if (file.isConflicted) return l10n.gitChangeConflicted;
  return switch (file.kind) {
    GitChangeKind.added => l10n.gitChangeAdded,
    GitChangeKind.deleted => l10n.gitChangeDeleted,
    GitChangeKind.renamed => l10n.gitChangeRenamed,
    GitChangeKind.copied => l10n.gitChangeCopied,
    GitChangeKind.typeChanged => l10n.gitChangeTypeChanged,
    GitChangeKind.untracked => l10n.gitChangeUntracked,
    GitChangeKind.modified ||
    GitChangeKind.ignored ||
    null => l10n.gitChangeModified,
  };
}

/// The banner for a half-done operation; null when there is none.
String? gitOperationText(GitOperation operation, AppLocalizations l10n) =>
    switch (operation) {
      GitOperation.none => null,
      GitOperation.merge => l10n.gitOperationMerge,
      GitOperation.rebase => l10n.gitOperationRebase,
      GitOperation.cherryPick => l10n.gitOperationCherryPick,
      GitOperation.revert => l10n.gitOperationRevert,
    };

String gitHubSignInProblemText(
  GitHubSignInProblem problem,
  AppLocalizations l10n,
) => switch (problem) {
  GitHubSignInProblem.expired => l10n.gitHubProblemExpired,
  GitHubSignInProblem.denied => l10n.gitHubProblemDenied,
  GitHubSignInProblem.deviceFlowDisabled => l10n.gitHubProblemDisabled,
  GitHubSignInProblem.network => l10n.gitHubProblemNetwork,
  GitHubSignInProblem.tokenRejected => l10n.gitHubProblemTokenRejected,
  GitHubSignInProblem.unexpected ||
  GitHubSignInProblem.cancelled => l10n.gitHubProblemUnexpected,
};

/// Where a remote lives in plain words: "GitHub", else the host of its
/// address ("gitlab.com"), else its name. The address itself and the exact
/// `origin/main` stay under Details.
String gitPlaceName(GitRemote? remote, AppLocalizations l10n) {
  if (remote == null) return '';
  if (remote.isGitHub) return l10n.gitPlaceGitHub;
  final address = remote.address;
  if (address != null) {
    // `https://host/...`, `ssh://git@host/...` or `git@host:path`.
    final scp = RegExp(r'^[^@/\s]+@([^:/\s]+):').firstMatch(address);
    final host = scp?.group(1) ?? Uri.tryParse(address)?.host;
    if (host != null && host.isNotEmpty) return host;
  }
  return remote.name;
}
