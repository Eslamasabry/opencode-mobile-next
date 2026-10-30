/// What went wrong with a Git action, in categories the page has words for.
///
/// Git's own text is a message from one programmer to another. The page shows
/// the localized sentence for a [GitProblem] (lib/ui/git_words.dart) and keeps
/// Git's text, scrubbed of secrets, for Details only ([GitFailure.details]).
/// Commands run with `LC_ALL=C`, so the English patterns below hold.
library;

enum GitProblem {
  // ----- The environment -----
  /// The folder is not inside a Git repository.
  notARepository,

  /// Git is not installed where the project lives.
  gitMissing,

  /// The project folder does not exist there.
  folderMissing,

  /// The command bridge is not ready (Ubuntu stopped, Termux unreachable,
  /// server disconnected).
  environmentUnavailable,

  /// The connected server cannot run commands.
  serverCannotRunCommands,

  /// The answer was cut off (the in-app bridge keeps the last 64 KB).
  outputTooLarge,

  /// The command did not finish in time.
  timedOut,

  /// Git refuses the folder because another user owns it
  /// ("detected dubious ownership").
  unsafeRepository,

  /// Another Git process holds `index.lock`.
  repositoryLocked,

  // ----- Branch and remote -----
  /// The branch tracks no remote branch (pull).
  noUpstream,

  /// The repository has no remote to push to.
  noRemote,

  /// HEAD is not on a branch.
  detachedHead,

  /// Local and remote both have new commits (pull --ff-only).
  cannotFastForward,

  /// The remote has commits this branch lacks (push rejected).
  pushRejected,

  /// Local edits would be overwritten (pull or switch).
  localChangesBlock,

  // ----- Sign-in and network -----
  /// The remote wants a sign-in the environment does not have.
  authRequired,

  /// The remote rejected the GitHub sign-in this app handed over.
  gitHubTokenRejected,

  /// Signed in, but the account may not write to that repository.
  noPermission,

  /// The host could not be reached.
  network,

  /// The remote repository does not exist (or is hidden from this account).
  remoteNotFound,

  // ----- Commit -----
  nothingSelected,
  emptyMessage,
  nothingToCommit,

  /// Git has no name and email to sign the commit with.
  identityMissing,

  /// A configured commit signature (GPG/SSH) could not be made.
  signingFailed,

  /// A commit hook refused the commit.
  hookRejected,

  // ----- Branches -----
  invalidBranchName,
  branchExists,
  branchNotFound,

  // ----- Remotes and clone -----
  /// The remote's name has characters Git's remote names do not allow.
  invalidRemoteName,

  /// The address is not one the app accepts (an https, ssh, git or file
  /// address, an `user@host:path` address or a folder path).
  invalidRemoteUrl,

  /// The address carries a password or token. The app refuses it so the
  /// secret is not saved in the repository's settings.
  remoteAddressHasPassword,

  /// A remote with that name is already set up.
  remoteExists,

  /// The folder name for a clone has characters or dots it cannot have.
  invalidFolderName,

  /// The folder to clone into is already there and not empty.
  folderExists,

  // ----- Conflicts and half-done operations -----
  /// Unmerged files block the action.
  conflicts,

  /// A merge or rebase is half-done.
  operationInProgress,

  /// Discarding an unmerged file is not offered.
  cannotDiscardConflict,

  /// Git failed in a way the page has no words for.
  unknown,
}

/// A failed Git action: the category and Git's own text for Details.
class GitFailure {
  const GitFailure(this.problem, {this.details = ''});

  final GitProblem problem;

  /// Git's stderr (or the bridge's description), secrets already masked.
  /// Shown only under Details, never as the message.
  final String details;

  @override
  String toString() => 'GitFailure(${problem.name})';
}

/// The category for [stderr] of a failed command, or null when nothing
/// matches (the caller then falls back to its own default).
///
/// [usedGitHubToken] turns an authentication failure into
/// [GitProblem.gitHubTokenRejected].
GitProblem? classifyGitError(String stderr, {bool usedGitHubToken = false}) {
  final text = stderr.toLowerCase();
  bool has(String s) => text.contains(s);

  if (has('not a git repository')) return GitProblem.notARepository;
  if (has('dubious ownership') || has('safe.directory')) {
    return GitProblem.unsafeRepository;
  }
  if (has('index.lock') && (has('file exists') || has('unable to create'))) {
    return GitProblem.repositoryLocked;
  }
  if (has('you have unmerged paths') ||
      has('unmerged files') ||
      has('fix conflicts') ||
      has('is unmerged') ||
      has('you need to resolve your current index first')) {
    return GitProblem.conflicts;
  }
  if (has('cannot do a partial commit during a merge') ||
      has('you have not concluded your merge') ||
      has('rebase in progress') ||
      has('cherry-pick is already in progress') ||
      has('a cherry-pick or revert is already in progress')) {
    return GitProblem.operationInProgress;
  }
  if (has('would be overwritten by') ||
      has('please commit your changes or stash them')) {
    return GitProblem.localChangesBlock;
  }
  if (has('not possible to fast-forward') ||
      has('diverging branches can\'t be fast-forwarded') ||
      has('have diverged')) {
    return GitProblem.cannotFastForward;
  }
  if (has('[rejected]') ||
      has('[remote rejected]') && has('non-fast-forward') ||
      has('updates were rejected') ||
      has('failed to push some refs') && has('fetch first')) {
    return GitProblem.pushRejected;
  }
  if (has('no upstream configured') ||
      has('no tracking information') ||
      has('has no upstream branch')) {
    return GitProblem.noUpstream;
  }
  if (has('head does not point to a branch') ||
      has('you are not currently on a branch')) {
    return GitProblem.detachedHead;
  }
  if (has('no configured push destination') ||
      has('does not appear to be a git repository') &&
          !has('repository not found')) {
    return GitProblem.noRemote;
  }
  if (has('repository not found') ||
      has('the requested url returned error: 404')) {
    return GitProblem.remoteNotFound;
  }
  if (has('permission to') && has('denied') ||
      has('the requested url returned error: 403') ||
      has('error: 403')) {
    return GitProblem.noPermission;
  }
  if (has('authentication failed') ||
      has('invalid username or password') ||
      has('the requested url returned error: 401') ||
      has('could not read username') ||
      has('could not read password') ||
      has('terminal prompts disabled') ||
      has('permission denied (publickey') ||
      has('host key verification failed')) {
    return usedGitHubToken &&
            !has('permission denied (publickey') &&
            !has('host key verification failed')
        ? GitProblem.gitHubTokenRejected
        : GitProblem.authRequired;
  }
  if (has('could not resolve host') ||
      has('could not resolve hostname') ||
      has('connection timed out') ||
      has('connection refused') ||
      has('network is unreachable') ||
      has('failed to connect to') ||
      has('unable to access') ||
      has('operation timed out') ||
      has('ssl certificate problem') ||
      has('could not read from remote repository')) {
    return GitProblem.network;
  }
  if (has('please tell me who you are') ||
      has('author identity unknown') ||
      has('unable to auto-detect email address') ||
      has('empty ident name')) {
    return GitProblem.identityMissing;
  }
  if (has('gpg failed to sign') ||
      has('failed to write commit object') && has('sign') ||
      has('error: cannot run gpg') ||
      has('ssh-keygen') && has('sign')) {
    return GitProblem.signingFailed;
  }
  if (has('nothing to commit') ||
      has('nothing added to commit') ||
      has('no changes added to commit')) {
    return GitProblem.nothingToCommit;
  }
  if (has('hook') && (has('exited') || has('declined') || has('failed'))) {
    return GitProblem.hookRejected;
  }
  if (has('remote') && has('already exists')) return GitProblem.remoteExists;
  if (has('destination path') && has('already exists')) {
    return GitProblem.folderExists;
  }
  if (has('already exists') && has('branch')) return GitProblem.branchExists;
  if (has('is not a valid branch name') || has('not a valid branch name')) {
    return GitProblem.invalidBranchName;
  }
  if (has('invalid reference') ||
      has('did not match any file(s) known to git') && has('switch') ||
      has('a branch is expected')) {
    return GitProblem.branchNotFound;
  }
  return null;
}

/// True when [stderr] says the branch has no commits yet (`git log` on an
/// unborn branch), which is an empty history rather than a failure.
bool gitSaysNoCommitsYet(String stderr) {
  final text = stderr.toLowerCase();
  return text.contains('does not have any commits yet') ||
      text.contains('bad default revision') ||
      text.contains("ambiguous argument 'head'");
}

/// [text] with every one of [secrets] masked, for Details.
String scrubGitText(String text, Iterable<String> secrets) {
  var out = text;
  for (final secret in secrets) {
    if (secret.isEmpty) continue;
    out = out.replaceAll(secret, '•••');
  }
  // A remote address with user info (`https://user:token@host`) never
  // reaches Details either.
  return out.replaceAll(RegExp(r'://[^/\s@]+@'), '://•••@');
}
