/// Typed state of one Git repository, as the Git page shows it.
library;

/// What happened to a file on one side (index or working tree).
enum GitChangeKind {
  added,
  modified,
  deleted,
  renamed,
  copied,
  typeChanged,
  untracked,
  ignored,
}

/// Which side of an unmerged path did what (`git status` `u` entries).
enum GitConflictKind {
  bothModified,
  bothAdded,
  bothDeleted,
  addedByUs,
  addedByThem,
  deletedByUs,
  deletedByThem,
}

/// One path with changes. [staged] is the index side, [unstaged] the working
/// tree; an untracked file has only [unstaged] = [GitChangeKind.untracked].
class GitFileChange {
  const GitFileChange({
    required this.path,
    this.originalPath,
    this.staged,
    this.unstaged,
    this.conflict,
    this.submodule = false,
    this.addedLines,
    this.deletedLines,
    this.stagedAddedLines,
    this.stagedDeletedLines,
    this.binary = false,
  });

  /// Repository-relative path (the new name of a rename).
  final String path;

  /// The old name of a rename or the source of a copy.
  final String? originalPath;
  final GitChangeKind? staged;
  final GitChangeKind? unstaged;

  /// Non-null while the path is unmerged.
  final GitConflictKind? conflict;
  final bool submodule;

  /// Working-tree line counts (`git diff --numstat`); null when unknown.
  final int? addedLines;
  final int? deletedLines;

  /// Index line counts (`git diff --cached --numstat`); null when unknown.
  final int? stagedAddedLines;
  final int? stagedDeletedLines;

  /// Git reported `-` for the counts: the file is binary.
  final bool binary;

  bool get isConflicted => conflict != null;
  bool get isUntracked => unstaged == GitChangeKind.untracked;

  /// The one kind a row shows: conflict first, then the index side, then the
  /// working tree.
  GitChangeKind? get kind => staged ?? unstaged;

  /// Lines added across both sides, when either is known.
  int? get totalAdded => _sum(addedLines, stagedAddedLines);
  int? get totalDeleted => _sum(deletedLines, stagedDeletedLines);

  static int? _sum(int? a, int? b) =>
      a == null && b == null ? null : (a ?? 0) + (b ?? 0);

  GitFileChange withCounts({
    int? addedLines,
    int? deletedLines,
    int? stagedAddedLines,
    int? stagedDeletedLines,
    bool? binary,
  }) => GitFileChange(
    path: path,
    originalPath: originalPath,
    staged: staged,
    unstaged: unstaged,
    conflict: conflict,
    submodule: submodule,
    addedLines: addedLines ?? this.addedLines,
    deletedLines: deletedLines ?? this.deletedLines,
    stagedAddedLines: stagedAddedLines ?? this.stagedAddedLines,
    stagedDeletedLines: stagedDeletedLines ?? this.stagedDeletedLines,
    binary: binary ?? this.binary,
  );
}

/// The branch line of `git status --porcelain=v2 --branch`.
class GitBranchState {
  const GitBranchState({
    this.head,
    this.commit,
    this.upstream,
    this.ahead = 0,
    this.behind = 0,
  });

  /// Current branch name; null when HEAD is detached.
  final String? head;

  /// HEAD's commit id; null before the first commit.
  final String? commit;

  /// `origin/main`; null when the branch tracks nothing.
  final String? upstream;
  final int ahead;
  final int behind;

  bool get detached => head == null;
  bool get unborn => commit == null;
  bool get hasUpstream => upstream != null;
}

/// `git status` parsed.
class GitStatus {
  const GitStatus({required this.branch, this.files = const []});

  final GitBranchState branch;
  final List<GitFileChange> files;

  Iterable<GitFileChange> get conflicts => files.where((f) => f.isConflicted);
  bool get hasConflicts => files.any((f) => f.isConflicted);
  bool get clean => files.isEmpty;
}

/// One commit of `git log`.
class GitCommit {
  const GitCommit({
    required this.id,
    required this.shortId,
    required this.authorName,
    required this.authorEmail,
    required this.time,
    required this.subject,
    this.parents = const [],
    this.refs = const [],
  });

  final String id;
  final String shortId;
  final String authorName;
  final String authorEmail;
  final DateTime time;
  final String subject;
  final List<String> parents;

  /// Decorations: `HEAD -> main`, `origin/main`, `tag: v1`.
  final List<String> refs;

  bool get isMerge => parents.length > 1;
}

/// One local or remote-tracking branch.
class GitBranch {
  const GitBranch({
    required this.name,
    required this.remote,
    this.remoteName,
    this.commit,
    this.upstream,
    this.ahead = 0,
    this.behind = 0,
    this.upstreamGone = false,
    this.current = false,
    this.time,
    this.subject,
  });

  /// `main` for a local branch, `origin/main` for a remote one.
  final String name;
  final bool remote;

  /// `origin` for `origin/main`; null for local branches.
  final String? remoteName;
  final String? commit;
  final String? upstream;
  final int ahead;
  final int behind;

  /// The upstream it tracked was deleted on the remote.
  final bool upstreamGone;
  final bool current;
  final DateTime? time;
  final String? subject;

  /// The name a local branch made from this remote one would get
  /// (`origin/feature/x` → `feature/x`).
  String get localName =>
      remote && remoteName != null && name.startsWith('$remoteName/')
      ? name.substring(remoteName!.length + 1)
      : name;
}

class GitBranches {
  const GitBranches({this.local = const [], this.remote = const []});

  final List<GitBranch> local;
  final List<GitBranch> remote;

  GitBranch? get current {
    for (final branch in local) {
      if (branch.current) return branch;
    }
    return null;
  }

  /// Remote branches with no local branch of the same short name yet.
  Iterable<GitBranch> get remoteOnly {
    final names = {for (final b in local) b.name};
    return remote.where((b) => !names.contains(b.localName));
  }
}

/// A merge, rebase, cherry-pick or revert left half-done in the repository.
enum GitOperation { none, merge, rebase, cherryPick, revert }

/// Where a remote points: GitHub over HTTPS or SSH, or somewhere else.
enum GitRemoteKind {
  /// `https://github.com/...`
  gitHubHttps,

  /// `git@github.com:...` or `ssh://git@github.com/...`
  gitHubSsh,

  /// Any other host or protocol.
  other,
}

class GitRemote {
  const GitRemote({required this.name, required this.kind, this.address});

  final String name;
  final GitRemoteKind kind;

  /// Where the remote points, as `git remote -v` would print it, with any
  /// user info removed (`https://user:token@host/...` becomes
  /// `https://host/...`) so a credential never reaches the screen. Null when
  /// unknown.
  final String? address;

  bool get isGitHub => kind != GitRemoteKind.other;
}

/// The name and email Git would put on a commit made here (the repository's
/// own setting, else the device's).
class GitIdentity {
  const GitIdentity({this.name = '', this.email = ''});

  final String name;
  final String email;

  /// Missing, or a placeholder an agent or installer left on the device
  /// (`aiteam@localhost`): the person should say who they are before the
  /// first save, so a push does not show a stranger as the author.
  bool get needsSetup {
    final mail = email.trim().toLowerCase();
    return name.trim().isEmpty ||
        mail.isEmpty ||
        !mail.contains('@') ||
        mail.endsWith('@localhost') ||
        mail.endsWith('@localhost.localdomain');
  }
}

/// Everything the page shows, read in one round trip.
class GitSnapshot {
  const GitSnapshot({
    required this.status,
    this.commits = const [],
    this.branches = const GitBranches(),
    this.operation = GitOperation.none,
    this.remotes = const [],
    this.identity,
  });

  /// Null when it could not be read: nothing is asked then.
  final GitIdentity? identity;
  final GitStatus status;
  final List<GitCommit> commits;
  final GitBranches branches;
  final GitOperation operation;
  final List<GitRemote> remotes;

  bool get hasRemote => remotes.isNotEmpty;

  /// The remote a first push would go to: `origin`, else the only one.
  GitRemote? get pushRemote {
    for (final remote in remotes) {
      if (remote.name == 'origin') return remote;
    }
    return remotes.length == 1 ? remotes.single : null;
  }
}
