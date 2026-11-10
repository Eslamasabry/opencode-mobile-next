// Repairing a project whose Git objects are links to files Git can't reach
// (2026-10-18). Ubuntu run under proot with --link2symlink stores every hard
// link (each Git object Git ever stored) as a link to an absolute path. Such
// links break when the project folder moves (renamed, moved out of Ubuntu by
// an older install). Git then says `bad object HEAD` and no agent can start
// in the project.
//
// The repair runs on the app's side of the files (no proot, so links look
// like links): each object link is replaced by a plain file holding the
// bytes it leads to, found next to the link when the folder moved. Links
// into another app's private storage (projects copied over from Termux's
// Ubuntu) can't be followed and count as missing. Git then checks the
// project inside Ubuntu.
import 'dart:async';
import 'dart:io';
import 'dart:isolate';

import 'package:crypto/crypto.dart';

import 'builtin_folders.dart' show BuiltinRootfsFolders;
import 'builtin_linux.dart';

/// Where the bytes behind one object link are.
enum GitLinkPlace {
  /// On the app's side: the link resolves, or its file sits next to it.
  here,

  /// Nowhere the app can reach.
  missing,
}

/// One link standing in for a Git object file.
class GitObjectLink {
  const GitObjectLink({
    required this.path,
    required this.relative,
    required this.target,
    required this.place,
    this.file,
  });

  /// The link, on the app's side.
  final String path;

  /// Its path inside the repository's Git folder (`objects/ab/cdef…`).
  final String relative;

  /// What the link says (the first hop).
  final String target;

  final GitLinkPlace place;

  /// [GitLinkPlace.here]: the file holding the bytes.
  final String? file;
}

/// What [GitObjectLinks.scan] found in one repository.
class GitLinkScan {
  const GitLinkScan(this.links);

  final List<GitObjectLink> links;

  Iterable<GitObjectLink> of(GitLinkPlace place) =>
      links.where((link) => link.place == place);
}

/// The scan and the checks; pure file work, unit-tested on real links.
abstract final class GitObjectLinks {
  /// proot's name for the files behind its links.
  static const l2sPrefix = '.l2s.';

  /// The Git folder of the repository at [repository] (its `.git`, or the
  /// folder a `.git` file names), or null when it has none.
  ///
  /// [host] turns a path the `.git` file names (written inside Ubuntu) into
  /// the app's side.
  static String? gitFolder(
    String repository, {
    String Function(String path)? host,
  }) {
    final dotGit = '$repository/.git';
    final kind = FileSystemEntity.typeSync(dotGit, followLinks: false);
    if (kind == FileSystemEntityType.directory) return dotGit;
    if (kind != FileSystemEntityType.file) return null;
    try {
      final line = File(
        dotGit,
      ).readAsLinesSync().firstWhere((line) => line.startsWith('gitdir:'));
      final named = line.substring('gitdir:'.length).trim();
      final folder = named.startsWith('/')
          ? (host?.call(named) ?? named)
          : '$repository/$named';
      // A worktree's objects live in the main repository's folder.
      final common = File('$folder/commondir');
      if (common.existsSync()) {
        final value = common.readAsStringSync().trim();
        return value.startsWith('/')
            ? (host?.call(value) ?? value)
            : '$folder/$value';
      }
      return folder;
    } catch (_) {
      return null;
    }
  }

  /// Termux's private storage, where links of projects copied over from its
  /// Ubuntu point: the app can't read it.
  static bool _inOtherApp(String path) =>
      path.startsWith('/data/data/com.termux/') ||
      path.startsWith('/data/user/0/com.termux/');

  /// Every link under an `objects` folder of [gitFolder] (proot's own `.l2s.`
  /// files left out), with where its bytes are. Links are never followed
  /// as folders.
  static GitLinkScan scan(String gitFolder) {
    final links = <GitObjectLink>[];
    void walk(String folder, String relative) {
      final List<FileSystemEntity> children;
      try {
        children = Directory(folder).listSync(followLinks: false);
      } on FileSystemException {
        return;
      }
      for (final child in children) {
        final name = child.path.substring(child.path.lastIndexOf('/') + 1);
        final inside = relative.isEmpty ? name : '$relative/$name';
        if (child is Directory) {
          walk(child.path, inside);
        } else if (child is Link &&
            !name.startsWith(l2sPrefix) &&
            ('/$inside').contains('/objects/')) {
          links.add(_classify(child.path, inside));
        }
      }
    }

    walk(gitFolder, '');
    links.sort((a, b) => a.relative.compareTo(b.relative));
    return GitLinkScan(links);
  }

  /// Follows [link] hop by hop. A hop whose absolute target is gone is
  /// looked for under the same name next to the link, where proot made it
  /// before the folder moved.
  static GitObjectLink _classify(String link, String relative) {
    String first = '';
    var current = link;
    for (var hop = 0; hop < 8; hop++) {
      final String target;
      try {
        target = Link(current).targetSync();
      } on FileSystemException {
        break;
      }
      if (hop == 0) first = target;
      final folder = current.substring(0, current.lastIndexOf('/'));
      var next = target.startsWith('/') ? target : '$folder/$target';
      var kind = FileSystemEntity.typeSync(next, followLinks: false);
      if (kind == FileSystemEntityType.notFound) {
        if (_inOtherApp(next)) {
          // Another app's private storage (Termux's Ubuntu): out of reach.
          return GitObjectLink(
            path: link,
            relative: relative,
            target: first,
            place: GitLinkPlace.missing,
            file: next,
          );
        }
        final name = target.substring(target.lastIndexOf('/') + 1);
        final beside = '$folder/$name';
        if (beside != current) {
          next = beside;
          kind = FileSystemEntity.typeSync(next, followLinks: false);
        }
      }
      if (kind == FileSystemEntityType.link) {
        current = next;
        continue;
      }
      if (kind == FileSystemEntityType.file) {
        return GitObjectLink(
          path: link,
          relative: relative,
          target: first,
          place: GitLinkPlace.here,
          file: next,
        );
      }
      break;
    }
    return GitObjectLink(
      path: link,
      relative: relative,
      target: first,
      place: GitLinkPlace.missing,
    );
  }

  /// Whether [file] holds what an object file at [relative] must hold: a
  /// loose object whose inflated bytes hash to its name, or a pack, index or
  /// reverse index whose trailing checksum matches its contents. Anything
  /// else can't be checked and is refused.
  static Future<bool> verify(String relative, File file) async {
    try {
      final loose = RegExp(
        r'(?:^|/)objects/([0-9a-f]{2})/([0-9a-f]{38}|[0-9a-f]{62})$',
      ).firstMatch(relative);
      if (loose != null) {
        final id = '${loose[1]}${loose[2]}';
        if (await file.length() > 256 * 1024 * 1024) return false;
        final inflated = zlib.decode(await file.readAsBytes());
        final digest = id.length == 40
            ? sha1.convert(inflated)
            : sha256.convert(inflated);
        return digest.toString() == id;
      }
      final pack = RegExp(
        r'(?:^|/)objects/pack/pack-([0-9a-f]{40}|[0-9a-f]{64})\.(?:pack|idx|rev)$',
      ).firstMatch(relative);
      if (pack != null) {
        final size = pack[1]!.length == 40 ? 20 : 32;
        final length = await file.length();
        if (length <= size) return false;
        final hash = size == 20 ? sha1 : sha256;
        final digest = await hash.bind(file.openRead(0, length - size)).first;
        final trailer = <int>[];
        await for (final chunk in file.openRead(length - size)) {
          trailer.addAll(chunk);
        }
        return _same(digest.bytes, trailer);
      }
      return false;
    } catch (_) {
      return false;
    }
  }

  static bool _same(List<int> a, List<int> b) {
    if (a.length != b.length) return false;
    for (var i = 0; i < a.length; i++) {
      if (a[i] != b[i]) return false;
    }
    return true;
  }

  /// Puts a plain file holding [bytes] where the link at [link] is. With
  /// [move], [bytes] is renamed into place (it must be on the same disk).
  static void replace(String link, String bytes, {bool move = false}) {
    final folder = link.substring(0, link.lastIndexOf('/'));
    final name = link.substring(link.lastIndexOf('/') + 1);
    final temporary = '$folder/.oc-repair-$name.tmp';
    if (move) {
      File(bytes).renameSync(temporary);
    } else {
      File(bytes).copySync(temporary);
    }
    Link(link).deleteSync();
    File(temporary).renameSync(link);
  }
}

/// How the repair is going, for its progress line.
enum GitRepairStage { looking, restoring, checking }

class GitRepairProgress {
  const GitRepairProgress(this.stage, {this.done = 0, this.total = 0});

  final GitRepairStage stage;
  final int done;
  final int total;
}

enum GitRepairResult {
  /// Every link is a file again and Git can read the project.
  repaired,

  /// Nothing was missing and Git can read the project.
  healthy,

  /// Some files are nowhere on this phone, or Git still refuses.
  cannotRepair,

  /// The folder is not a Git project the repair can reach.
  notRepository,
}

class GitRepairOutcome {
  const GitRepairOutcome(
    this.result, {
    this.restored = 0,
    this.missing = const [],
    this.details = '',
  });

  final GitRepairResult result;

  /// Links now plain files.
  final int restored;

  /// Object files still missing, as paths inside the Git folder.
  final List<String> missing;

  /// Technical text for Details only.
  final String details;
}

/// Git's own verdict on a repository.
class GitCheck {
  const GitCheck({required this.ok, this.output = ''});

  final bool ok;
  final String output;
}

/// What the repair sheet can do: run the repair.
class GitRepairActions {
  const GitRepairActions({required this.run});

  /// One repair run, reporting how it goes.
  final Future<GitRepairOutcome> Function(
    void Function(GitRepairProgress progress) progress,
  )
  run;

  /// The repair of [directory] in this phone's built-in Ubuntu; null where
  /// it can't reach (another computer's folder, no built-in Ubuntu).
  static GitRepairActions? forThisPhone(String directory) {
    if (!BuiltinLinux.supported || !GitLinkRepair.reaches(directory)) {
      return null;
    }
    return GitRepairActions(
      run: (progress) async {
        final repair = await GitLinkRepair.forThisPhone();
        if (repair == null) {
          return const GitRepairOutcome(GitRepairResult.notRepository);
        }
        return repair.repair(directory, progress: progress);
      },
    );
  }
}

/// The repair of one project in the built-in Ubuntu.
class GitLinkRepair {
  GitLinkRepair({required this.projects, required this.check});

  /// The app's side of `/root/projects`.
  final Directory projects;

  /// Runs Git's check on a repository, given its path inside Ubuntu.
  final Future<GitCheck> Function(String ubuntuPath) check;

  static const ubuntuProjects = BuiltinLinux.projectsDir;

  /// The repair for this phone's built-in Ubuntu; null where there is none.
  static Future<GitLinkRepair?> forThisPhone() async {
    if (!BuiltinLinux.supported) return null;
    final projects = await BuiltinRootfsFolders.locateProjectStorage();
    if (projects == null) return null;
    final linux = BuiltinLinux();
    return GitLinkRepair(
      projects: projects,
      check: (path) => builtinGitCheck(linux, path),
    );
  }

  /// Whether [directory] (a path inside Ubuntu) is a project the repair
  /// can reach.
  static bool reaches(String directory) {
    final parts = directory.split('/');
    return directory.startsWith('$ubuntuProjects/') &&
        parts.length > 3 &&
        !parts.skip(3).any((part) => part.isEmpty || part == '..');
  }

  Future<GitRepairOutcome> repair(
    String directory, {
    void Function(GitRepairProgress)? progress,
  }) async {
    void say(GitRepairStage stage, [int done = 0, int total = 0]) =>
        progress?.call(GitRepairProgress(stage, done: done, total: total));
    if (!reaches(directory)) {
      return const GitRepairOutcome(GitRepairResult.notRepository);
    }
    say(GitRepairStage.looking);
    final top = projects.path;
    final found = await _locateApart(top, directory);
    if (found == null) {
      return const GitRepairOutcome(GitRepairResult.notRepository);
    }
    final (:repository, :gitFolder) = found;
    final scan = await _scanApart(gitFolder);
    final ubuntuRepository =
        '$ubuntuProjects${repository.substring(top.length)}';
    var restored = 0;
    final missing = <String>[
      for (final link in scan.of(GitLinkPlace.missing)) link.relative,
    ];
    final notes = <String>[
      'Links found: ${scan.links.length} '
          '(here ${scan.of(GitLinkPlace.here).length}, '
          'missing ${missing.length})',
    ];

    final here = scan.of(GitLinkPlace.here).toList();
    for (final (index, link) in here.indexed) {
      say(GitRepairStage.restoring, index, here.length);
      try {
        GitObjectLinks.replace(link.path, link.file!);
        restored++;
      } catch (_) {
        missing.add(link.relative);
      }
    }

    say(GitRepairStage.checking);
    final verdict = await check(ubuntuRepository);
    final details = [
      ...notes,
      if (missing.isNotEmpty)
        'Missing (${missing.length}):\n${missing.take(20).join('\n')}',
      if (verdict.output.trim().isNotEmpty) 'git: ${verdict.output.trim()}',
    ].join('\n');
    if (missing.isNotEmpty || !verdict.ok) {
      return GitRepairOutcome(
        GitRepairResult.cannotRepair,
        restored: restored,
        missing: missing,
        details: details,
      );
    }
    return GitRepairOutcome(
      restored == 0 ? GitRepairResult.healthy : GitRepairResult.repaired,
      restored: restored,
      details: details,
    );
  }

  // The file walks run in another isolate. Their closures are made here, in
  // a scope holding only strings: made inside [repair] they would share its
  // context, which holds the caller's progress callback (a widget's State),
  // and the isolate message would be refused (ArgumentError, emulator
  // 2026-10-18).
  static Future<({String repository, String gitFolder})?> _locateApart(
    String top,
    String directory,
  ) => Isolate.run(() => _locate(top, directory));

  static Future<GitLinkScan> _scanApart(String gitFolder) =>
      Isolate.run(() => GitObjectLinks.scan(gitFolder));

  /// The repository holding [directory]: the nearest folder at or above it,
  /// inside the projects folder, with a Git folder of its own.
  static ({String repository, String gitFolder})? _locate(
    String top,
    String directory,
  ) {
    var current = '$top${directory.substring(ubuntuProjects.length)}';
    while (current.length > top.length) {
      final kind = FileSystemEntity.typeSync(current, followLinks: false);
      if (kind == FileSystemEntityType.directory) {
        final git = GitObjectLinks.gitFolder(
          current,
          host: (path) => path.startsWith('$ubuntuProjects/')
              ? '$top${path.substring(ubuntuProjects.length)}'
              : path,
        );
        if (git != null) return (repository: current, gitFolder: git);
      }
      current = current.substring(0, current.lastIndexOf('/'));
    }
    return null;
  }
}

/// Git's check of [path] inside the built-in Ubuntu: every object reachable
/// and `git status` (what agents run first) working.
Future<GitCheck> builtinGitCheck(BuiltinLinux linux, String path) async {
  if (!GitLinkRepair.reaches(path) || path.contains("'")) {
    return const GitCheck(ok: false);
  }
  try {
    final result = await linux.run('''
cd -- '$path' 2>/dev/null || exit 3
out=\$(git fsck --connectivity-only --no-progress 2>&1); code=\$?
printf '%s\\n' "\$out" | grep -v '^warning: garbage found' | tail -n 20
[ "\$code" -eq 0 ] || exit 4
git status --porcelain >/dev/null 2>&1 || { git status --porcelain 2>&1 | tail -n 5; exit 5; }
''', timeout: const Duration(minutes: 5));
    return GitCheck(ok: result.ok, output: result.output);
  } catch (error) {
    return GitCheck(ok: false, output: '${error.runtimeType}');
  }
}
