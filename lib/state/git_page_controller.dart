import 'package:flutter/foundation.dart';

import '../domain/git/git_models.dart';
import '../domain/git/git_problem.dart';
import '../domain/git/git_repository.dart';
import 'git_workspace.dart';
import 'github_account.dart';

/// What the Git page body shows.
sealed class GitPageView {
  const GitPageView();
}

/// First read in flight (no snapshot yet).
class GitPageLoading extends GitPageView {
  const GitPageLoading();
}

/// The connection cannot run Git ([reason] picks the words).
class GitPageUnavailable extends GitPageView {
  const GitPageUnavailable(this.reason);
  final GitUnavailableReason reason;
}

/// The folder is not a repository (offer to initialise elsewhere; not here).
class GitPageNotRepository extends GitPageView {
  const GitPageNotRepository();
}

class GitPageReady extends GitPageView {
  const GitPageReady(this.snapshot);
  final GitSnapshot snapshot;
}

/// The read failed before anything was shown.
class GitPageFailed extends GitPageView {
  const GitPageFailed(this.failure);
  final GitFailure failure;
}

/// What the page may offer, from where the project lives and what the last
/// snapshot says. The UI enables buttons from these, never from the flavor.
class GitPageAbilities {
  const GitPageAbilities({
    this.canPull = false,
    this.canPush = false,
    this.canCommit = false,
    this.canSwitchBranch = false,
    this.offersGitHubSignIn = false,
    this.usesOwnGitSignIn = false,
  });

  final bool canPull;
  final bool canPush;
  final bool canCommit;
  final bool canSwitchBranch;

  /// Show GitHub sign-in (Ubuntu inside the app only).
  final bool offersGitHubSignIn;

  /// Pushes use the environment's own sign-in (Termux, servers): say so.
  final bool usesOwnGitSignIn;
}

/// Where the quiet check of the remote stands (a `git fetch` when the sheet
/// opens).
enum GitCheck {
  /// Not started, or nothing to check (no remote branch to follow).
  idle,
  checking,
  done,

  /// Could not reach the remote. Not an error to show: the row says so
  /// quietly and one tap tries again.
  failed,
}

/// State of the Git page for one project. One action runs at a time; every
/// write refreshes the snapshot afterwards.
class GitPageController extends ChangeNotifier {
  GitPageController({
    required GitWorkspaceResolution resolution,
    this.account,
    GitRepository Function(GitWorkspace workspace)? repository,
    this.logLimit = 30,
  }) : workspace = resolution.workspace,
       _repository = resolution.workspace == null
           ? null
           : (repository ?? _defaultRepository)(resolution.workspace!),
       _view = resolution.workspace == null
           ? GitPageUnavailable(resolution.reason!)
           : const GitPageLoading();

  static GitRepository _defaultRepository(GitWorkspace workspace) =>
      GitRepository(
        shell: workspace.shell,
        directory: workspace.directory,
        credentials: workspace.credentials,
      );

  final GitWorkspace? workspace;

  /// The GitHub sign-in of this profile; used only where
  /// [GitWorkspace.offersGitHubSignIn].
  final GitHubAccountController? account;
  final int logLimit;
  final GitRepository? _repository;

  GitPageView _view;
  GitPageView get view => _view;

  /// The last snapshot read, kept while a refresh or action runs.
  GitSnapshot? get snapshot => switch (_view) {
    GitPageReady(:final snapshot) => snapshot,
    _ => null,
  };

  GitAction? _busy;

  /// The action running now; null when idle.
  GitAction? get busy => _busy;

  GitActionResult? _lastResult;

  /// The outcome of the last write, for a status line; cleared by
  /// [clearLastResult] or the next action.
  GitActionResult? get lastResult => _lastResult;

  bool _savedBeforeSend = false;

  /// True after [commitAndPush] saved and then tried to send: a failed send
  /// in [lastResult] then says the save is kept. Cleared with [lastResult].
  bool get savedBeforeSend => _savedBeforeSend;

  GitCheck _check = GitCheck.idle;
  GitCheck get check => _check;

  bool _disposed = false;

  void _changed() {
    if (!_disposed) notifyListeners();
  }

  GitPageAbilities get abilities {
    final ws = workspace;
    final snap = snapshot;
    if (ws == null || snap == null) {
      return GitPageAbilities(
        offersGitHubSignIn: ws?.offersGitHubSignIn ?? false,
        usesOwnGitSignIn: ws?.usesOwnGitSignIn ?? false,
      );
    }
    final branch = snap.status.branch;
    final halfDone = snap.operation != GitOperation.none;
    return GitPageAbilities(
      canPull: !branch.detached && branch.hasUpstream && !halfDone,
      canPush: !branch.detached && !branch.unborn && snap.hasRemote,
      canCommit: snap.status.files.isNotEmpty && !snap.status.hasConflicts,
      canSwitchBranch: !halfDone,
      offersGitHubSignIn: ws.offersGitHubSignIn,
      usesOwnGitSignIn: ws.usesOwnGitSignIn,
    );
  }

  /// Reads the repository again. Keeps the old snapshot on screen while it
  /// runs; a failure after a good read is reported in [lastResult].
  Future<void> refresh() async {
    final repo = _repository;
    if (repo == null || _busy != null) return;
    _busy = GitAction.refresh;
    _changed();
    try {
      await _read(repo, reportFailure: true);
    } finally {
      _busy = null;
      _changed();
    }
  }

  Future<void> _read(GitRepository repo, {required bool reportFailure}) async {
    final result = await repo.snapshot(logLimit: logLimit);
    if (_disposed) return;
    switch (result) {
      case GitSnapshotReady(:final snapshot):
        _view = GitPageReady(snapshot);
      case GitSnapshotNotRepository():
        _view = const GitPageNotRepository();
      case GitSnapshotFailed(:final failure):
        if (_view is! GitPageReady) {
          _view = GitPageFailed(failure);
        } else if (reportFailure) {
          // Keep the last good snapshot on screen; say the refresh failed.
          _lastResult = GitActionResult.failure(GitAction.refresh, failure);
        }
    }
  }

  /// Looks at the remote quietly and re-reads, so "behind" is true. Does not
  /// show as a running action and never reports a failure as one: [check]
  /// says [GitCheck.failed] and the row says so in a neutral line.
  Future<void> checkForUpdates() async {
    final repo = _repository;
    final snap = snapshot;
    if (repo == null || snap == null || _check == GitCheck.checking) return;
    if (!snap.status.branch.hasUpstream) {
      _check = GitCheck.idle;
      _changed();
      return;
    }
    _check = GitCheck.checking;
    _changed();
    try {
      final result = await repo.fetch(gitHubToken: await _token());
      if (_disposed) return;
      if (!result.ok) {
        _check = GitCheck.failed;
      } else {
        _check = GitCheck.done;
        if (_busy == null) await _read(repo, reportFailure: false);
      }
    } catch (_) {
      _check = GitCheck.failed;
    }
    _changed();
  }

  Future<GitActionResult?> pull() => _act(GitAction.pull, (repo) async {
    final result = await repo.pull(gitHubToken: await _token());
    if (result.ok) _check = GitCheck.done;
    return result;
  });

  Future<GitActionResult?> push() => _act(
    GitAction.push,
    (repo) async => repo.push(gitHubToken: await _token()),
  );

  /// Commits exactly [paths] with [message].
  Future<GitActionResult?> commit(List<String> paths, String message) => _act(
    GitAction.commit,
    (repo) => repo.commit(paths: paths, message: message),
  );

  /// Saves exactly [paths] with [message], then sends to the remote: the
  /// one-tap "Save 2 files and send". A failed save stops there and is the
  /// result; after a good save the send's outcome is the result and
  /// [savedBeforeSend] is true. [busy] reads commit, then push.
  Future<GitActionResult?> commitAndPush(
    List<String> paths,
    String message,
  ) async {
    final repo = _repository;
    if (repo == null || _busy != null) return null;
    _busy = GitAction.commit;
    _lastResult = null;
    _savedBeforeSend = false;
    _changed();
    GitActionResult result;
    try {
      result = await repo.commit(paths: paths, message: message);
      if (result.ok && !_disposed) {
        _savedBeforeSend = true;
        _busy = GitAction.push;
        _changed();
        result = await repo.push(gitHubToken: await _token());
        if (result.problem == GitProblem.gitHubTokenRejected) {
          await account?.tokenRejected();
        }
      }
      _lastResult = result;
      if (!_disposed) await _read(repo, reportFailure: false);
    } catch (_) {
      result = GitActionResult.failure(
        _busy ?? GitAction.commit,
        const GitFailure(GitProblem.unknown),
      );
      _lastResult = result;
    } finally {
      _busy = null;
      _changed();
    }
    return result;
  }

  Future<GitActionResult?> switchBranch(String name) =>
      _act(GitAction.switchBranch, (repo) => repo.switchBranch(name));

  Future<GitActionResult?> createBranch(String name) =>
      _act(GitAction.createBranch, (repo) => repo.createBranch(name));

  Future<GitActionResult?> discard(GitFileChange file) =>
      _act(GitAction.discard, (repo) => repo.discard(file));

  /// Saves the commit name and email for this repository (answer to
  /// [GitProblem.identityMissing]).
  Future<GitActionResult?> setIdentity(String name, String email) => _act(
    GitAction.setIdentity,
    (repo) => repo.setIdentity(name: name, email: email),
  );

  /// Adds the remote [name] pointing at [url].
  Future<GitActionResult?> addRemote(String name, String url) =>
      _act(GitAction.addRemote, (repo) => repo.addRemote(name: name, url: url));

  /// Clones [url] into a new folder [folderName] beside this project. The
  /// GitHub sign-in is used where this environment offers it.
  Future<GitActionResult?> clone(String url, String folderName) => _act(
    GitAction.clone,
    (repo) async => repo.clone(
      url: url,
      folderName: folderName,
      gitHubToken: await _token(),
    ),
  );

  void clearLastResult() {
    if (_lastResult == null) return;
    _lastResult = null;
    _savedBeforeSend = false;
    _changed();
  }

  Future<String?> _token() async {
    if (!(workspace?.offersGitHubSignIn ?? false)) return null;
    return account?.tokenForGit();
  }

  /// Runs one write, records its result, then re-reads. Returns null when
  /// another action is running or there is no workspace.
  Future<GitActionResult?> _act(
    GitAction action,
    Future<GitActionResult> Function(GitRepository repo) run,
  ) async {
    final repo = _repository;
    if (repo == null || _busy != null) return null;
    _busy = action;
    _lastResult = null;
    _savedBeforeSend = false;
    _changed();
    GitActionResult result;
    try {
      result = await run(repo);
      if (result.problem == GitProblem.gitHubTokenRejected) {
        await account?.tokenRejected();
      }
      _lastResult = result;
      if (!_disposed) await _read(repo, reportFailure: false);
    } catch (_) {
      result = GitActionResult.failure(
        action,
        const GitFailure(GitProblem.unknown),
      );
      _lastResult = result;
    } finally {
      _busy = null;
      _changed();
    }
    return result;
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
