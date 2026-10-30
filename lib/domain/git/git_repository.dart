/// The Git page's operations on one repository, over any [GitShell].
///
/// Reads come back as typed models; writes come back as a [GitActionResult]
/// whose failure is a [GitProblem] with Git's text kept for Details. Nothing
/// here throws for a Git failure: "not a repository", "no upstream" and
/// conflicts are states the page shows, not exceptions.
library;

import 'git_credentials.dart';
import 'git_models.dart';
import 'git_parsers.dart';
import 'git_problem.dart';
import 'git_script.dart';
import 'git_shell.dart';

/// Every action the page offers; also the page's busy state.
enum GitAction {
  refresh,
  pull,
  push,
  commit,
  switchBranch,
  createBranch,
  discard,
  setIdentity,
  addRemote,
  clone,
}

/// Something worth saying about an action that worked.
enum GitActionNote {
  /// Nothing to pull or push.
  upToDate,

  /// Pulled new commits.
  pulled,

  /// Pushed commits.
  pushed,

  /// First push: the branch now tracks the remote one.
  pushedAndTracking,

  /// A new commit was made ([GitActionResult.commit] is its short id).
  committed,
  switched,
  created,
  discarded,
  identitySaved,

  /// A remote was added.
  remoteAdded,

  /// A repository was cloned ([GitActionResult.clonedTo] is its folder).
  cloned,
}

class GitActionResult {
  const GitActionResult.success(
    this.action,
    this.note, {
    this.commit,
    this.clonedTo,
    this.count,
    this.target,
  }) : failure = null;

  const GitActionResult.failure(this.action, GitFailure this.failure)
    : note = null,
      commit = null,
      clonedTo = null,
      count = null,
      target = null;

  final GitAction action;
  final GitActionNote? note;
  final GitFailure? failure;

  /// Short id of the commit a commit action made.
  final String? commit;

  /// The folder a clone made, as the project's shell sees it.
  final String? clonedTo;

  /// How many files a commit saved, or how many new commits a pull brought.
  final int? count;

  /// The remote branch a push went to or a pull came from (`origin/main`).
  final String? target;

  bool get ok => failure == null;
  GitProblem? get problem => failure?.problem;

  @override
  String toString() =>
      'GitActionResult(${action.name}, ${note?.name ?? failure?.problem.name})';
}

/// What [GitRepository.snapshot] found.
sealed class GitSnapshotResult {
  const GitSnapshotResult();
}

class GitSnapshotReady extends GitSnapshotResult {
  const GitSnapshotReady(this.snapshot);
  final GitSnapshot snapshot;
}

/// The folder is not inside a repository (a state, not an error).
class GitSnapshotNotRepository extends GitSnapshotResult {
  const GitSnapshotNotRepository();
}

class GitSnapshotFailed extends GitSnapshotResult {
  const GitSnapshotFailed(this.failure);
  final GitFailure failure;
}

/// True when [name] is a branch name Git accepts
/// (`git check-ref-format --branch`), checked before any command runs.
bool isValidGitBranchName(String name) {
  if (name.isEmpty || name.length > 250) return false;
  if (name == '@' || name.startsWith('-') || name.startsWith('/')) {
    return false;
  }
  if (name.endsWith('/') || name.endsWith('.')) return false;
  if (name.contains('..') || name.contains('//') || name.contains('@{')) {
    return false;
  }
  for (final rune in name.runes) {
    if (rune < 0x20 || rune == 0x7f) return false;
    if (' ~^:?*[\\'.contains(String.fromCharCode(rune))) return false;
  }
  for (final part in name.split('/')) {
    if (part.isEmpty || part.startsWith('.') || part.endsWith('.lock')) {
      return false;
    }
  }
  return true;
}

/// True when [name] can name a remote (`origin`, `my-fork.2`): letters,
/// digits, `.`, `_` and `-`, not starting with `-` or `.`.
bool isValidGitRemoteName(String name) =>
    RegExp(r'^[A-Za-z0-9_][A-Za-z0-9._-]{0,99}$').hasMatch(name);

/// True when [name] can be the folder a clone makes beside the project.
bool isValidGitFolderName(String name) =>
    RegExp(r'^[A-Za-z0-9_][A-Za-z0-9._-]{0,99}$').hasMatch(name) &&
    !name.endsWith('.lock');

/// Why [url] cannot be a remote's address, or null when it can. Checked
/// before any command runs: the app accepts `https://`, `http://`, `ssh://`,
/// `git://` and `file://` addresses, `user@host:path` and a folder path, and
/// refuses one that carries a password or token.
GitProblem? gitRemoteAddressProblem(String url) {
  final value = url.trim();
  if (value.isEmpty || value.startsWith('-')) {
    return GitProblem.invalidRemoteUrl;
  }
  if (RegExp(r'[\s\x00-\x1f\x7f]').hasMatch(value)) {
    return GitProblem.invalidRemoteUrl;
  }
  final scheme = RegExp(r'^([A-Za-z][A-Za-z0-9+.-]*)://').firstMatch(value);
  if (scheme != null) {
    final name = scheme.group(1)!.toLowerCase();
    if (!const {'https', 'http', 'ssh', 'git', 'file'}.contains(name)) {
      return GitProblem.invalidRemoteUrl;
    }
    final uri = Uri.tryParse(value);
    if (uri == null) return GitProblem.invalidRemoteUrl;
    if (name != 'file' && uri.host.isEmpty) return GitProblem.invalidRemoteUrl;
    if (uri.userInfo.contains(':') &&
        (name == 'https' || name == 'http' || name == 'git')) {
      return GitProblem.remoteAddressHasPassword;
    }
    return null;
  }
  if (value.startsWith('/')) return null;
  if (RegExp(r'^[A-Za-z0-9._-]+@[A-Za-z0-9.-]+:[^:]').hasMatch(value)) {
    return null;
  }
  return GitProblem.invalidRemoteUrl;
}

/// The folder name a clone of [url] would get: the last part of the
/// address without `.git` (`https://github.com/o/app.git` is `app`). Empty
/// when none can be made.
String suggestGitCloneFolder(String url) {
  var value = url.trim();
  while (value.endsWith('/')) {
    value = value.substring(0, value.length - 1);
  }
  final cut = value.lastIndexOf(RegExp(r'[/:]'));
  var name = cut < 0 ? value : value.substring(cut + 1);
  if (name.endsWith('.git')) name = name.substring(0, name.length - 4);
  return isValidGitFolderName(name) ? name : '';
}

/// The folder holding [path] (`/` for a top-level folder): where a clone
/// beside the project lands.
String gitParentFolder(String path) {
  var value = path;
  while (value.length > 1 && value.endsWith('/')) {
    value = value.substring(0, value.length - 1);
  }
  final cut = value.lastIndexOf('/');
  if (cut < 0) return '.';
  return cut == 0 ? '/' : value.substring(0, cut);
}

class GitRepository {
  GitRepository({
    required this.shell,
    required this.directory,
    this.credentials,
    this.readTimeout = const Duration(seconds: 45),
    this.networkTimeout = const Duration(minutes: 3),
  });

  final GitShell shell;

  /// The project folder as [shell] sees it.
  final String directory;

  /// Where a GitHub token can be placed for one command; null where the app
  /// must not hand one over (Termux, remote servers).
  final GitCredentialDrop? credentials;
  final Duration readTimeout;
  final Duration networkTimeout;

  bool get canUseGitHubToken => credentials != null;

  static const _git = ['git', '--no-pager', '-c', 'color.ui=false'];

  // ---------------------------------------------------------------- reads

  /// Status with line counts, recent commits, branches, remotes and any
  /// half-done operation, in one round trip.
  Future<GitSnapshotResult> snapshot({int logLimit = 30}) async {
    final script = GitScript(
      directory: directory,
      steps: [
        GitStep('status', [
          ..._git,
          'status',
          '--porcelain=v2',
          '--branch',
          '-z',
          '--untracked-files=normal',
        ], stopOnFailure: true),
        GitStep('unstaged', [..._git, 'diff', '--numstat', '-z']),
        GitStep('staged', [..._git, 'diff', '--cached', '--numstat', '-z']),
        GitStep('log', [
          ..._git,
          'log',
          '-n',
          '${logLimit.clamp(1, 200)}',
          '-z',
          '--format=$gitLogFormat',
        ]),
        GitStep('branches', [
          ..._git,
          'for-each-ref',
          '--format=$gitBranchFormat',
          'refs/heads',
          'refs/remotes',
        ]),
        GitStep('operation', ['sh', '-c', _operationProbe]),
        GitStep('remotes', ['sh', '-c', _remotesProbe]),
        GitStep('identity', ['sh', '-c', _identityProbe], optional: true),
      ],
    );
    final GitEnvelope envelope;
    try {
      envelope = await _run(script, readTimeout);
    } on _EnvelopeFailure catch (failure) {
      return GitSnapshotFailed(failure.failure);
    }
    final status = envelope['status']!;
    if (!status.ok) {
      final problem = classifyGitError(status.stderr);
      if (problem == GitProblem.notARepository) {
        return const GitSnapshotNotRepository();
      }
      return GitSnapshotFailed(
        GitFailure(problem ?? GitProblem.unknown, details: status.stderr),
      );
    }
    final parsed = withGitNumstat(
      parseGitStatus(status.stdout),
      unstaged: _okOut(envelope['unstaged'], parseGitNumstat) ?? const [],
      staged: _okOut(envelope['staged'], parseGitNumstat) ?? const [],
    );
    return GitSnapshotReady(
      GitSnapshot(
        status: parsed,
        commits: _okOut(envelope['log'], parseGitLog) ?? const [],
        branches:
            _okOut(envelope['branches'], parseGitBranches) ??
            const GitBranches(),
        operation:
            _okOut(envelope['operation'], parseGitOperation) ??
            GitOperation.none,
        remotes: _okOut(envelope['remotes'], parseGitRemotes) ?? const [],
        identity: _okOut(envelope['identity'], parseGitIdentity),
      ),
    );
  }

  static T? _okOut<T>(GitStepResult? step, T Function(String) parse) =>
      step == null || !step.ok ? null : parse(step.stdout);

  /// Prints the marker files of a half-done merge, rebase or cherry-pick.
  static const _operationProbe =
      r'g=$(git rev-parse --git-dir) || exit 1; '
      'for f in MERGE_HEAD REBASE_HEAD CHERRY_PICK_HEAD REVERT_HEAD '
      r'rebase-merge rebase-apply; do test -e "$g/$f" && echo "$f"; done; '
      'exit 0';

  /// The name and email a commit here would carry, one per line.
  static const _identityProbe =
      'printf \'%s\\n%s\\n\' "\$(git config user.name)" '
      '"\$(git config user.email)"; exit 0';

  /// Remote names and addresses with any user info removed before printing
  /// (an address can hold a token: `https://user:token@host/...`).
  static const _remotesProbe =
      r"git config --get-regexp '^remote\..*\.url$' | "
      r"sed -e 's#://[^/@]*@#://#'; exit 0";

  // --------------------------------------------------------------- writes

  /// `git pull --ff-only`. A branch without upstream is [GitProblem.noUpstream];
  /// diverged history is [GitProblem.cannotFastForward].
  Future<GitActionResult> pull({String? gitHubToken}) =>
      _withToken(GitAction.pull, gitHubToken, (auth) async {
        final envelope = await _run(
          GitScript(
            directory: directory,
            environment: auth.environment,
            removeOnExit: auth.removeOnExit,
            steps: [
              GitStep('head', [
                ..._git,
                'symbolic-ref',
                '--short',
                '-q',
                'HEAD',
              ], stopOnFailure: true),
              GitStep('upstream', [
                ..._git,
                'rev-parse',
                '--abbrev-ref',
                '--symbolic-full-name',
                '@{u}',
              ], stopOnFailure: true),
              GitStep('pull', [
                ..._git,
                ...auth.options,
                'pull',
                '--ff-only',
                '--no-rebase',
              ]),
              GitStep('count', [
                ..._git,
                'rev-list',
                '--count',
                'ORIG_HEAD..HEAD',
              ], optional: true),
            ],
          ),
          networkTimeout,
        );
        final head = envelope['head']!;
        if (!head.ok) {
          return _fail(
            GitAction.pull,
            classifyGitError(head.stderr) ?? GitProblem.detachedHead,
            head,
            auth,
          );
        }
        final upstream = envelope['upstream']!;
        if (!upstream.ok) {
          return _fail(
            GitAction.pull,
            classifyGitError(upstream.stderr) == GitProblem.notARepository
                ? GitProblem.notARepository
                : GitProblem.noUpstream,
            upstream,
            auth,
          );
        }
        final pull = envelope['pull']!;
        if (!pull.ok) return _fail(GitAction.pull, null, pull, auth);
        final upToDate = '${pull.stdout}\n${pull.stderr}'
            .toLowerCase()
            .contains('already up to date');
        return GitActionResult.success(
          GitAction.pull,
          upToDate ? GitActionNote.upToDate : GitActionNote.pulled,
          count: upToDate
              ? null
              : int.tryParse((envelope['count']?.stdout ?? '').trim()),
          target: upstream.stdout.trim(),
        );
      });

  /// `git fetch`: reads what is new on the remote without changing any file
  /// here. A branch with no remote is [GitProblem.noUpstream].
  Future<GitActionResult> fetch({String? gitHubToken}) => _withToken(
    GitAction.refresh,
    gitHubToken,
    (auth) async {
      final envelope = await _run(
        GitScript(
          directory: directory,
          environment: auth.environment,
          removeOnExit: auth.removeOnExit,
          steps: [
            GitStep('upstream', [
              ..._git,
              'rev-parse',
              '--abbrev-ref',
              '--symbolic-full-name',
              '@{u}',
            ], stopOnFailure: true),
            GitStep('fetch', [..._git, ...auth.options, 'fetch', '--quiet']),
          ],
        ),
        networkTimeout,
      );
      final upstream = envelope['upstream']!;
      if (!upstream.ok) {
        return _fail(GitAction.refresh, GitProblem.noUpstream, upstream, auth);
      }
      final step = envelope['fetch']!;
      if (!step.ok) return _fail(GitAction.refresh, null, step, auth);
      return const GitActionResult.success(
        GitAction.refresh,
        GitActionNote.upToDate,
      );
    },
  );

  /// `git push`, or `git push --set-upstream <remote> <branch>` the first
  /// time. The remote is `origin`, else the only remote.
  Future<GitActionResult> push({String? gitHubToken}) => _withToken(
    GitAction.push,
    gitHubToken,
    (auth) async {
      final plan = await _run(
        GitScript(
          directory: directory,
          steps: [
            GitStep('head', [
              ..._git,
              'symbolic-ref',
              '--short',
              '-q',
              'HEAD',
            ], stopOnFailure: true),
            GitStep('upstream', [
              ..._git,
              'rev-parse',
              '--abbrev-ref',
              '--symbolic-full-name',
              '@{u}',
            ]),
            GitStep('remotes', [..._git, 'remote']),
          ],
        ),
        readTimeout,
      );
      final head = plan['head']!;
      if (!head.ok) {
        return _fail(
          GitAction.push,
          classifyGitError(head.stderr) ?? GitProblem.detachedHead,
          head,
          auth,
        );
      }
      final branch = head.stdout.trim();
      final hasUpstream = plan['upstream']?.ok ?? false;
      final List<String> pushArgs;
      String? target;
      if (hasUpstream) {
        target = (plan['upstream']?.stdout ?? '').trim();
        pushArgs = const ['push'];
      } else {
        final remotes = (plan['remotes']?.stdout ?? '')
            .split('\n')
            .map((r) => r.trim())
            .where((r) => r.isNotEmpty)
            .toList();
        final remote = remotes.contains('origin')
            ? 'origin'
            : remotes.length == 1
            ? remotes.single
            : null;
        if (remote == null) {
          return GitActionResult.failure(
            GitAction.push,
            GitFailure(
              GitProblem.noRemote,
              details: remotes.isEmpty ? '' : 'Remotes: ${remotes.join(', ')}',
            ),
          );
        }
        pushArgs = ['push', '--set-upstream', remote, branch];
        target = '$remote/$branch';
      }
      final envelope = await _run(
        GitScript(
          directory: directory,
          environment: auth.environment,
          removeOnExit: auth.removeOnExit,
          steps: [
            GitStep('push', [..._git, ...auth.options, ...pushArgs]),
          ],
        ),
        networkTimeout,
      );
      final push = envelope['push']!;
      if (!push.ok) return _fail(GitAction.push, null, push, auth);
      final text = '${push.stdout}\n${push.stderr}'.toLowerCase();
      return GitActionResult.success(
        GitAction.push,
        text.contains('everything up-to-date')
            ? GitActionNote.upToDate
            : hasUpstream
            ? GitActionNote.pushed
            : GitActionNote.pushedAndTracking,
        target: target,
      );
    },
  );

  /// Commits exactly [paths] (staging them first, untracked ones included)
  /// with [message]; other staged changes stay staged and uncommitted.
  Future<GitActionResult> commit({
    required List<String> paths,
    required String message,
  }) async {
    final selected = paths.where((p) => p.isNotEmpty).toSet().toList();
    if (selected.isEmpty) {
      return const GitActionResult.failure(
        GitAction.commit,
        GitFailure(GitProblem.nothingSelected),
      );
    }
    if (message.trim().isEmpty) {
      return const GitActionResult.failure(
        GitAction.commit,
        GitFailure(GitProblem.emptyMessage),
      );
    }
    return _guard(GitAction.commit, () async {
      final envelope = await _run(
        GitScript(
          directory: directory,
          steps: [
            GitStep('add', [
              ..._git,
              'add',
              '-A',
              '--',
              ...selected,
            ], stopOnFailure: true),
            GitStep('commit', [
              ..._git,
              'commit',
              '-m',
              message,
              '--',
              ...selected,
            ], stopOnFailure: true),
            GitStep('id', [..._git, 'rev-parse', '--short', 'HEAD']),
            GitStep('files', [
              ..._git,
              'diff-tree',
              '--root',
              '--no-commit-id',
              '--name-only',
              '-r',
              '-M',
              'HEAD',
            ], optional: true),
          ],
        ),
        readTimeout,
      );
      for (final name in ['add', 'commit']) {
        final step = envelope[name]!;
        if (!step.ok) {
          // `git commit` prints "nothing to commit" on stdout.
          final text = '${step.stderr}\n${step.stdout}';
          return GitActionResult.failure(
            GitAction.commit,
            GitFailure(
              classifyGitError(text) ?? GitProblem.unknown,
              details: text.trim(),
            ),
          );
        }
      }
      return GitActionResult.success(
        GitAction.commit,
        GitActionNote.committed,
        commit: envelope['id']?.stdout.trim(),
        count: envelope['files'] == null || !envelope['files']!.ok
            ? null
            : envelope['files']!.stdout
                  .split('\n')
                  .where((l) => l.trim().isNotEmpty)
                  .length,
      );
    });
  }

  /// Switches to [name]. A name only a remote has (`feature/x` from
  /// `origin/feature/x`) creates a local branch tracking it.
  Future<GitActionResult> switchBranch(String name) async {
    if (!isValidGitBranchName(name)) {
      return const GitActionResult.failure(
        GitAction.switchBranch,
        GitFailure(GitProblem.invalidBranchName),
      );
    }
    return _single(
      GitAction.switchBranch,
      [..._git, 'switch', name],
      GitActionNote.switched,
      fallback: GitProblem.branchNotFound,
    );
  }

  /// Creates [name] from the current commit and switches to it.
  Future<GitActionResult> createBranch(String name) async {
    if (!isValidGitBranchName(name)) {
      return const GitActionResult.failure(
        GitAction.createBranch,
        GitFailure(GitProblem.invalidBranchName),
      );
    }
    return _single(GitAction.createBranch, [
      ..._git,
      'switch',
      '-c',
      name,
    ], GitActionNote.created);
  }

  /// Throws away every change to [file], staged and unstaged. Untracked
  /// files are deleted; new files are removed from the index and the disk;
  /// a rename is undone. Unmerged files are refused.
  Future<GitActionResult> discard(GitFileChange file) async {
    if (file.isConflicted) {
      return const GitActionResult.failure(
        GitAction.discard,
        GitFailure(GitProblem.cannotDiscardConflict),
      );
    }
    final List<GitStep> steps;
    if (file.isUntracked) {
      steps = [
        GitStep('discard', [..._git, 'clean', '-f', '-q', '--', file.path]),
      ];
    } else if (file.staged == GitChangeKind.added ||
        file.staged == GitChangeKind.copied) {
      steps = [
        GitStep('discard', [..._git, 'rm', '-f', '-q', '--', file.path]),
      ];
    } else if (file.staged == GitChangeKind.renamed &&
        file.originalPath != null) {
      steps = [
        GitStep('remove', [
          ..._git,
          'rm',
          '-f',
          '-q',
          '--',
          file.path,
        ], stopOnFailure: true),
        GitStep('discard', [
          ..._git,
          'restore',
          '--staged',
          '--worktree',
          '--',
          file.originalPath!,
        ]),
      ];
    } else {
      steps = [
        GitStep('discard', [
          ..._git,
          'restore',
          '--staged',
          '--worktree',
          '--',
          file.path,
        ]),
      ];
    }
    return _guard(GitAction.discard, () async {
      final envelope = await _run(
        GitScript(directory: directory, steps: steps),
        readTimeout,
      );
      for (final step in steps) {
        final result = envelope[step.name];
        if (result != null && !result.ok) {
          return GitActionResult.failure(
            GitAction.discard,
            GitFailure(
              classifyGitError(result.stderr) ?? GitProblem.unknown,
              details: result.stderr.trim(),
            ),
          );
        }
      }
      return const GitActionResult.success(
        GitAction.discard,
        GitActionNote.discarded,
      );
    });
  }

  /// Saves the name and email commits are signed with, for this repository
  /// only (`git config user.name/user.email`, no `--global`).
  Future<GitActionResult> setIdentity({
    required String name,
    required String email,
  }) async {
    if (name.trim().isEmpty || !email.contains('@')) {
      return const GitActionResult.failure(
        GitAction.setIdentity,
        GitFailure(GitProblem.identityMissing),
      );
    }
    return _guard(GitAction.setIdentity, () async {
      final envelope = await _run(
        GitScript(
          directory: directory,
          steps: [
            GitStep('name', [
              ..._git,
              'config',
              'user.name',
              name.trim(),
            ], stopOnFailure: true),
            GitStep('email', [..._git, 'config', 'user.email', email.trim()]),
          ],
        ),
        readTimeout,
      );
      for (final key in ['name', 'email']) {
        final step = envelope[key];
        if (step == null || !step.ok) {
          return GitActionResult.failure(
            GitAction.setIdentity,
            GitFailure(
              classifyGitError(step?.stderr ?? '') ?? GitProblem.unknown,
              details: step?.stderr.trim() ?? '',
            ),
          );
        }
      }
      return const GitActionResult.success(
        GitAction.setIdentity,
        GitActionNote.identitySaved,
      );
    });
  }

  /// `git remote add <name> <url>`. The address is checked first and never
  /// may carry a password.
  Future<GitActionResult> addRemote({
    required String name,
    required String url,
  }) async {
    final cleanName = name.trim();
    final cleanUrl = url.trim();
    if (!isValidGitRemoteName(cleanName)) {
      return const GitActionResult.failure(
        GitAction.addRemote,
        GitFailure(GitProblem.invalidRemoteName),
      );
    }
    final bad = gitRemoteAddressProblem(cleanUrl);
    if (bad != null) {
      return GitActionResult.failure(GitAction.addRemote, GitFailure(bad));
    }
    return _single(GitAction.addRemote, [
      ..._git,
      'remote',
      'add',
      cleanName,
      cleanUrl,
    ], GitActionNote.remoteAdded);
  }

  /// Clones [url] into a new folder [folderName] beside this project (in its
  /// parent folder). A GitHub sign-in, when given, is used for the clone; the
  /// saved remote keeps the address as typed.
  Future<GitActionResult> clone({
    required String url,
    required String folderName,
    String? gitHubToken,
  }) async {
    final cleanUrl = url.trim();
    final bad = gitRemoteAddressProblem(cleanUrl);
    if (bad != null) {
      return GitActionResult.failure(GitAction.clone, GitFailure(bad));
    }
    final folder = folderName.trim();
    if (!isValidGitFolderName(folder)) {
      return const GitActionResult.failure(
        GitAction.clone,
        GitFailure(GitProblem.invalidFolderName),
      );
    }
    final parent = gitParentFolder(directory);
    return _withToken(GitAction.clone, gitHubToken, (auth) async {
      final envelope = await _run(
        GitScript(
          directory: parent,
          environment: auth.environment,
          removeOnExit: auth.removeOnExit,
          steps: [
            GitStep('clone', [
              ..._git,
              ...auth.options,
              'clone',
              '--',
              cleanUrl,
              folder,
            ]),
          ],
        ),
        networkTimeout,
      );
      final step = envelope['clone']!;
      if (!step.ok) return _fail(GitAction.clone, null, step, auth);
      return GitActionResult.success(
        GitAction.clone,
        GitActionNote.cloned,
        clonedTo: parent.endsWith('/') ? '$parent$folder' : '$parent/$folder',
      );
    });
  }

  // -------------------------------------------------------------- helpers

  Future<GitActionResult> _single(
    GitAction action,
    List<String> argv,
    GitActionNote note, {
    GitProblem fallback = GitProblem.unknown,
  }) => _guard(action, () async {
    final envelope = await _run(
      GitScript(directory: directory, steps: [GitStep('run', argv)]),
      readTimeout,
    );
    final step = envelope['run']!;
    if (step.ok) return GitActionResult.success(action, note);
    return GitActionResult.failure(
      action,
      GitFailure(
        classifyGitError(step.stderr) ?? fallback,
        details: step.stderr.trim(),
      ),
    );
  });

  Future<GitActionResult> _guard(
    GitAction action,
    Future<GitActionResult> Function() body,
  ) async {
    try {
      return await body();
    } on _EnvelopeFailure catch (failure) {
      return GitActionResult.failure(action, failure.failure);
    }
  }

  /// Runs [body] with a GitHub token placed for one command when one is
  /// given and this environment may receive it; removes the file afterwards
  /// whatever happened.
  Future<GitActionResult> _withToken(
    GitAction action,
    String? token,
    Future<GitActionResult> Function(_Auth auth) body,
  ) async {
    final drop = credentials;
    if (token == null || token.isEmpty || drop == null) {
      return _guard(action, () => body(const _Auth.none()));
    }
    GitSecretFile? file;
    try {
      file = await drop.place(token);
      final placed = file;
      return await _guard(
        action,
        () => body(
          _Auth(
            options: gitHubCredentialOptions(),
            environment: {gitTokenFileVariable: placed.shellPath},
            removeOnExit: [placed.shellPath],
            secrets: [token],
          ),
        ),
      );
    } on Object catch (_) {
      if (file == null) {
        // The file could not be made private: do not push with it.
        return GitActionResult.failure(
          action,
          const GitFailure(GitProblem.environmentUnavailable),
        );
      }
      rethrow;
    } finally {
      if (file != null) {
        try {
          await drop.remove(file);
        } catch (_) {}
      }
    }
  }

  GitActionResult _fail(
    GitAction action,
    GitProblem? problem,
    GitStepResult step,
    _Auth auth,
  ) {
    final details = scrubGitText(
      '${step.stderr}\n${step.stdout}'.trim(),
      auth.secrets,
    );
    return GitActionResult.failure(
      action,
      GitFailure(
        problem ??
            classifyGitError(details, usedGitHubToken: auth.usesToken) ??
            GitProblem.unknown,
        details: details,
      ),
    );
  }

  Future<GitEnvelope> _run(GitScript script, Duration timeout) async {
    final String output;
    try {
      output = await shell.run(script.render(), timeout: timeout);
    } on GitShellException catch (error) {
      throw _EnvelopeFailure(
        GitFailure(switch (error.failure) {
          GitShellFailure.unavailable => GitProblem.environmentUnavailable,
          GitShellFailure.unsupported => GitProblem.serverCannotRunCommands,
          GitShellFailure.timedOut => GitProblem.timedOut,
        }, details: error.details),
      );
    }
    final envelope = GitEnvelope.parse(output);
    final problem = envelope.problem;
    if (problem != null) {
      throw _EnvelopeFailure(
        GitFailure(switch (problem) {
          GitEnvelopeProblem.folderMissing => GitProblem.folderMissing,
          GitEnvelopeProblem.gitMissing => GitProblem.gitMissing,
          GitEnvelopeProblem.noTemp => GitProblem.environmentUnavailable,
          GitEnvelopeProblem.truncated => GitProblem.outputTooLarge,
        }),
      );
    }
    // Every stopOnFailure step must have reported; a step that ran must
    // have its result. A missing required step means a cut-off answer.
    for (final step in script.steps) {
      if (envelope[step.name] != null || step.optional) continue;
      final earlierStopped = script.steps
          .takeWhile((s) => s != step)
          .any((s) => s.stopOnFailure && envelope[s.name]?.ok == false);
      if (!earlierStopped) {
        throw const _EnvelopeFailure(GitFailure(GitProblem.outputTooLarge));
      }
    }
    return envelope;
  }
}

class _Auth {
  const _Auth({
    required this.options,
    required this.environment,
    required this.removeOnExit,
    required this.secrets,
  });

  const _Auth.none()
    : options = const [],
      environment = const {},
      removeOnExit = const [],
      secrets = const [];

  final List<String> options;
  final Map<String, String> environment;
  final List<String> removeOnExit;
  final List<String> secrets;

  bool get usesToken => secrets.isNotEmpty;
}

class _EnvelopeFailure implements Exception {
  const _EnvelopeFailure(this.failure);
  final GitFailure failure;
}
