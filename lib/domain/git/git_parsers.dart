/// Parsers for Git's machine-readable output. Pure functions over strings;
/// each one is fed fixtures copied from real `git` runs in the tests.
library;

import 'git_models.dart';

/// The `log` format [parseGitLog] reads: fields split by US (0x1f), commits
/// split by NUL (`git log -z`).
const gitLogFormat = '%H%x1f%h%x1f%an%x1f%ae%x1f%at%x1f%P%x1f%D%x1f%s';

/// The `for-each-ref` format [parseGitBranches] reads (`%1f` is US).
const gitBranchFormat =
    '%(refname)%1f%(objectname:short)%1f%(upstream:short)%1f'
    '%(upstream:track,nobracket)%1f%(HEAD)%1f%(committerdate:unix)%1f'
    '%(contents:subject)';

/// `git status --porcelain=v2 --branch -z`.
GitStatus parseGitStatus(String output) {
  final records = output.split('\x00');
  String? head;
  String? commit;
  String? upstream;
  var ahead = 0;
  var behind = 0;
  final files = <GitFileChange>[];
  for (var i = 0; i < records.length; i++) {
    final record = records[i];
    if (record.isEmpty) continue;
    if (record.startsWith('# ')) {
      final header = record.substring(2);
      final space = header.indexOf(' ');
      if (space < 0) continue;
      final key = header.substring(0, space);
      final value = header.substring(space + 1);
      switch (key) {
        case 'branch.oid':
          commit = value == '(initial)' ? null : value;
        case 'branch.head':
          head = value == '(detached)' ? null : value;
        case 'branch.upstream':
          upstream = value;
        case 'branch.ab':
          final parts = value.split(' ');
          for (final part in parts) {
            if (part.startsWith('+')) {
              ahead = int.tryParse(part.substring(1)) ?? 0;
            }
            if (part.startsWith('-')) {
              behind = int.tryParse(part.substring(1)) ?? 0;
            }
          }
      }
      continue;
    }
    switch (record[0]) {
      case '1':
        // 1 XY sub mH mI mW hH hI path
        final fields = _splitFields(record, 9);
        if (fields == null) continue;
        files.add(
          GitFileChange(
            path: fields[8],
            staged: _kind(fields[1][0]),
            unstaged: _kind(fields[1][1]),
            submodule: fields[2].startsWith('S'),
          ),
        );
      case '2':
        // 2 XY sub mH mI mW hH hI Xscore path, then origPath as the next record.
        final fields = _splitFields(record, 10);
        if (fields == null) continue;
        final original = i + 1 < records.length ? records[++i] : null;
        files.add(
          GitFileChange(
            path: fields[9],
            originalPath: original == null || original.isEmpty
                ? null
                : original,
            staged: _kind(fields[1][0]),
            unstaged: _kind(fields[1][1]),
            submodule: fields[2].startsWith('S'),
          ),
        );
      case 'u':
        // u XY sub m1 m2 m3 mW h1 h2 h3 path
        final fields = _splitFields(record, 11);
        if (fields == null) continue;
        files.add(
          GitFileChange(
            path: fields[10],
            conflict: _conflict(fields[1]),
            submodule: fields[2].startsWith('S'),
          ),
        );
      case '?':
        if (record.length > 2) {
          files.add(
            GitFileChange(
              path: record.substring(2),
              unstaged: GitChangeKind.untracked,
            ),
          );
        }
      case '!':
        if (record.length > 2) {
          files.add(
            GitFileChange(
              path: record.substring(2),
              unstaged: GitChangeKind.ignored,
            ),
          );
        }
    }
  }
  return GitStatus(
    branch: GitBranchState(
      head: head,
      commit: commit,
      upstream: upstream,
      ahead: ahead,
      behind: behind,
    ),
    files: List.unmodifiable(files),
  );
}

/// [record] split on its first `count - 1` spaces, so the last field (a
/// path) keeps its own spaces. Null when the record is short.
List<String>? _splitFields(String record, int count) {
  final fields = <String>[];
  var start = 0;
  for (var n = 0; n < count - 1; n++) {
    final space = record.indexOf(' ', start);
    if (space < 0) return null;
    fields.add(record.substring(start, space));
    start = space + 1;
  }
  fields.add(record.substring(start));
  if (fields[1].length != 2 || fields.last.isEmpty) return null;
  return fields;
}

GitChangeKind? _kind(String code) => switch (code) {
  'M' => GitChangeKind.modified,
  'A' => GitChangeKind.added,
  'D' => GitChangeKind.deleted,
  'R' => GitChangeKind.renamed,
  'C' => GitChangeKind.copied,
  'T' => GitChangeKind.typeChanged,
  _ => null,
};

GitConflictKind _conflict(String xy) => switch (xy) {
  'DD' => GitConflictKind.bothDeleted,
  'AU' => GitConflictKind.addedByUs,
  'UD' => GitConflictKind.deletedByThem,
  'UA' => GitConflictKind.addedByThem,
  'DU' => GitConflictKind.deletedByUs,
  'AA' => GitConflictKind.bothAdded,
  _ => GitConflictKind.bothModified,
};

/// One `git diff --numstat -z` entry. Counts are null for binary files.
class GitNumstat {
  const GitNumstat({
    required this.path,
    this.originalPath,
    this.added,
    this.deleted,
  });

  final String path;
  final String? originalPath;
  final int? added;
  final int? deleted;

  bool get binary => added == null && deleted == null;
}

/// `git diff [--cached] --numstat -z`: `A\tD\tpath\0`, or for a rename
/// `A\tD\t\0old\0new\0`.
List<GitNumstat> parseGitNumstat(String output) {
  final records = output.split('\x00');
  final entries = <GitNumstat>[];
  for (var i = 0; i < records.length; i++) {
    final record = records[i];
    if (record.isEmpty) continue;
    final first = record.indexOf('\t');
    final second = first < 0 ? -1 : record.indexOf('\t', first + 1);
    if (second < 0) continue;
    final added = int.tryParse(record.substring(0, first));
    final deleted = int.tryParse(record.substring(first + 1, second));
    final path = record.substring(second + 1);
    if (path.isNotEmpty) {
      entries.add(GitNumstat(path: path, added: added, deleted: deleted));
    } else if (i + 2 < records.length) {
      final original = records[++i];
      final renamed = records[++i];
      entries.add(
        GitNumstat(
          path: renamed,
          originalPath: original,
          added: added,
          deleted: deleted,
        ),
      );
    }
  }
  return entries;
}

/// [status] files with line counts from the two numstat runs.
GitStatus withGitNumstat(
  GitStatus status, {
  List<GitNumstat> unstaged = const [],
  List<GitNumstat> staged = const [],
}) {
  final work = {for (final e in unstaged) e.path: e};
  final index = {for (final e in staged) e.path: e};
  return GitStatus(
    branch: status.branch,
    files: List.unmodifiable([
      for (final file in status.files)
        file.withCounts(
          addedLines: work[file.path]?.added,
          deletedLines: work[file.path]?.deleted,
          stagedAddedLines: index[file.path]?.added,
          stagedDeletedLines: index[file.path]?.deleted,
          binary:
              (work[file.path]?.binary ?? false) ||
              (index[file.path]?.binary ?? false),
        ),
    ]),
  );
}

/// `git log -z --format=<gitLogFormat>`.
List<GitCommit> parseGitLog(String output) {
  final commits = <GitCommit>[];
  for (final record in output.split('\x00')) {
    final trimmed = record.startsWith('\n') ? record.substring(1) : record;
    if (trimmed.isEmpty) continue;
    final fields = trimmed.split('\x1f');
    if (fields.length < 8) continue;
    final seconds = int.tryParse(fields[4]);
    commits.add(
      GitCommit(
        id: fields[0],
        shortId: fields[1],
        authorName: fields[2],
        authorEmail: fields[3],
        time: DateTime.fromMillisecondsSinceEpoch(
          (seconds ?? 0) * 1000,
          isUtc: true,
        ),
        parents: fields[5].isEmpty ? const [] : fields[5].split(' '),
        refs: fields[6].isEmpty ? const [] : fields[6].split(', '),
        // A subject never holds US; join defensively anyway.
        subject: fields.sublist(7).join('\x1f').trimRight(),
      ),
    );
  }
  return commits;
}

/// `git for-each-ref --format=<gitBranchFormat> refs/heads refs/remotes`.
/// A remote's symbolic `HEAD` (`origin/HEAD`) is skipped.
GitBranches parseGitBranches(String output) {
  final local = <GitBranch>[];
  final remote = <GitBranch>[];
  for (final line in output.split('\n')) {
    if (line.isEmpty) continue;
    final fields = line.split('\x1f');
    if (fields.length < 7) continue;
    final ref = fields[0];
    final track = _parseTrack(fields[3]);
    final seconds = int.tryParse(fields[5]);
    final time = seconds == null
        ? null
        : DateTime.fromMillisecondsSinceEpoch(seconds * 1000, isUtc: true);
    final subject = fields.sublist(6).join('\x1f');
    if (ref.startsWith('refs/heads/')) {
      local.add(
        GitBranch(
          name: ref.substring('refs/heads/'.length),
          remote: false,
          commit: fields[1].isEmpty ? null : fields[1],
          upstream: fields[2].isEmpty ? null : fields[2],
          ahead: track.ahead,
          behind: track.behind,
          upstreamGone: track.gone,
          current: fields[4] == '*',
          time: time,
          subject: subject.isEmpty ? null : subject,
        ),
      );
    } else if (ref.startsWith('refs/remotes/')) {
      final name = ref.substring('refs/remotes/'.length);
      final slash = name.indexOf('/');
      if (slash <= 0 || name.endsWith('/HEAD')) continue;
      remote.add(
        GitBranch(
          name: name,
          remote: true,
          remoteName: name.substring(0, slash),
          commit: fields[1].isEmpty ? null : fields[1],
          time: time,
          subject: subject.isEmpty ? null : subject,
        ),
      );
    }
  }
  return GitBranches(
    local: List.unmodifiable(local),
    remote: List.unmodifiable(remote),
  );
}

({int ahead, int behind, bool gone}) _parseTrack(String value) {
  if (value == 'gone') return (ahead: 0, behind: 0, gone: true);
  var ahead = 0;
  var behind = 0;
  for (final part in value.split(', ')) {
    if (part.startsWith('ahead ')) ahead = int.tryParse(part.substring(6)) ?? 0;
    if (part.startsWith('behind ')) {
      behind = int.tryParse(part.substring(7)) ?? 0;
    }
  }
  return (ahead: ahead, behind: behind, gone: false);
}

/// The marker files the snapshot script prints, one per line, for the
/// operation left half-done (see `GitScripts.operationProbe`).
GitOperation parseGitOperation(String output) {
  final markers = output.split('\n').map((l) => l.trim()).toSet();
  if (markers.contains('rebase-merge') ||
      markers.contains('rebase-apply') ||
      markers.contains('REBASE_HEAD')) {
    return GitOperation.rebase;
  }
  if (markers.contains('MERGE_HEAD')) return GitOperation.merge;
  if (markers.contains('CHERRY_PICK_HEAD')) return GitOperation.cherryPick;
  if (markers.contains('REVERT_HEAD')) return GitOperation.revert;
  return GitOperation.none;
}

/// `git config --get-regexp '^remote\..*\.url$'` lines, whose addresses the
/// script has already stripped of user info. Only the name and the kind of
/// host survive parsing; the address itself is dropped.
List<GitRemote> parseGitRemotes(String output) {
  final remotes = <String, GitRemote>{};
  for (final line in output.split('\n')) {
    final space = line.indexOf(' ');
    if (space < 0) continue;
    final key = line.substring(0, space);
    final url = line.substring(space + 1).trim();
    if (!key.startsWith('remote.') || !key.endsWith('.url')) continue;
    final name = key.substring('remote.'.length, key.length - '.url'.length);
    if (name.isEmpty || remotes.containsKey(name)) continue;
    remotes[name] = GitRemote(
      name: name,
      kind: gitRemoteKind(url),
      address: url.isEmpty ? null : stripGitUserInfo(url),
    );
  }
  return List.unmodifiable(remotes.values);
}

/// [url] without the user info of a `scheme://user:secret@host` address.
/// An scp-style `git@host:path` keeps its user name: it is not a secret.
String stripGitUserInfo(String url) =>
    url.replaceFirst(RegExp(r'://[^/@\s]*@'), '://');

GitRemoteKind gitRemoteKind(String url) {
  final value = url.trim().toLowerCase();
  if (value.startsWith('git@github.com:') ||
      value.startsWith('ssh://git@github.com/') ||
      value.startsWith('ssh://github.com/')) {
    return GitRemoteKind.gitHubSsh;
  }
  final uri = Uri.tryParse(value);
  if (uri != null &&
      uri.scheme == 'https' &&
      (uri.host == 'github.com' || uri.host == 'www.github.com')) {
    return GitRemoteKind.gitHubHttps;
  }
  return GitRemoteKind.other;
}

/// The two lines `printf '%s\n%s\n' name email` printed.
GitIdentity parseGitIdentity(String output) {
  final lines = output.split('\n');
  return GitIdentity(
    name: lines.isNotEmpty ? lines[0].trim() : '',
    email: lines.length > 1 ? lines[1].trim() : '',
  );
}
