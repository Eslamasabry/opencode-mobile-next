// Repairing a project whose Git objects are links to files Git can't reach
// (2026-10-18). Ubuntu run under proot with --link2symlink stores every hard
// link (each Git object Git ever stored) as a link to an absolute path. Such
// links break when the project folder moves (renamed, moved out of Ubuntu by
// an older install) and, for projects brought over from Termux's Ubuntu,
// point into Termux's private storage, which the app can't read. Git then
// says `bad object HEAD` and no agent can start in the project.
//
// The repair runs on the app's side of the files (no proot, so links look
// like links): each object link is replaced by a plain file holding the
// bytes it leads to, found next to the link when the folder moved, or asked
// of Termux when the bytes are in its Ubuntu. Bytes from Termux are checked
// against the object's own name before they are used. Git then checks the
// project inside Ubuntu.
import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:isolate';
import 'dart:math';

import 'package:crypto/crypto.dart';

import '../termux/bridge.dart';
import 'builtin_folders.dart' show BuiltinRootfsFolders;
import 'builtin_linux.dart';

/// Where the bytes behind one object link are.
enum GitLinkPlace {
  /// On the app's side: the link resolves, or its file sits next to it.
  here,

  /// In Termux's Ubuntu (proot-distro's `.l2s` store).
  termux,

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

  /// [GitLinkPlace.here]: the file holding the bytes. [GitLinkPlace.termux]:
  /// the path in Termux.
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

  /// A file in a Termux proot-distro container's `.l2s` store: the only
  /// Termux paths the repair asks for.
  static final termuxStore = RegExp(
    r'^(?!.*/\.\.(?:/|$))'
    r'/data/(?:data|user/0)/com\.termux/files/usr/var/lib/proot-distro/'
    r'(?:containers/[A-Za-z0-9._-]+/rootfs|installed-rootfs/[A-Za-z0-9._-]+)'
    r'/\.l2s/\.l2s\.[A-Za-z0-9._-]+$',
  );

  static bool _inTermux(String path) =>
      path.startsWith('/data/data/com.termux/') ||
      path.startsWith('/data/user/0/com.termux/');

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
        if (_inTermux(next)) {
          return GitObjectLink(
            path: link,
            relative: relative,
            target: first,
            place: termuxStore.hasMatch(next)
                ? GitLinkPlace.termux
                : GitLinkPlace.missing,
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
enum GitRepairStage { looking, restoring, askingTermux, checking }

class GitRepairProgress {
  const GitRepairProgress(this.stage, {this.done = 0, this.total = 0});

  final GitRepairStage stage;
  final int done;
  final int total;
}

/// Why Termux could not hand over the files.
enum GitRepairTermux {
  /// Termux is not on this phone (any more): its files went with it.
  notInstalled,

  /// The app may not run commands in Termux yet.
  notAllowed,

  /// Termux did not answer.
  noAnswer,
}

enum GitRepairResult {
  /// Every link is a file again and Git can read the project.
  repaired,

  /// Nothing was missing and Git can read the project.
  healthy,

  /// Some files are nowhere on this phone, or Git still refuses.
  cannotRepair,

  /// The files are in Termux, which could not hand them over now.
  termux,

  /// The folder is not a Git project the repair can reach.
  notRepository,
}

class GitRepairOutcome {
  const GitRepairOutcome(
    this.result, {
    this.restored = 0,
    this.missing = const [],
    this.termux,
    this.details = '',
  });

  final GitRepairResult result;

  /// Links now plain files.
  final int restored;

  /// Object files still missing, as paths inside the Git folder.
  final List<String> missing;

  final GitRepairTermux? termux;

  /// Technical text for Details only.
  final String details;
}

/// Git's own verdict on a repository.
class GitCheck {
  const GitCheck({required this.ok, this.output = ''});

  final bool ok;
  final String output;
}

/// What the repair sheet can do: run the repair, and the Termux steps a
/// person may need on the way.
class GitRepairActions {
  const GitRepairActions({
    required this.run,
    required this.allowTermux,
    required this.openTermux,
  });

  /// One repair run, reporting how it goes.
  final Future<GitRepairOutcome> Function(
    void Function(GitRepairProgress progress) progress,
  )
  run;

  /// Asks Android for Termux's "Run commands" permission; true when given.
  final Future<bool> Function() allowTermux;

  final Future<void> Function() openTermux;

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
      allowTermux: () async {
        try {
          return await TermuxBridge.requestRunCommandAccess() == 'granted';
        } catch (_) {
          return false;
        }
      },
      openTermux: () async {
        await TermuxBridge.openTermux();
      },
    );
  }
}

/// Files fetched from Termux: index into the asked list -> the received file.
abstract interface class TermuxGitFiles {
  /// Null when Termux can be asked; else why not.
  Future<GitRepairTermux?> available();

  Future<Map<int, File>> fetch(
    List<String> paths,
    Directory into, {
    void Function(int done)? progress,
  });
}

/// The repair of one project in the built-in Ubuntu.
class GitLinkRepair {
  GitLinkRepair({
    required this.projects,
    required this.staging,
    required this.termux,
    required this.check,
  });

  /// The app's side of `/root/projects`.
  final Directory projects;

  /// Where fetched files wait (same disk as [projects]).
  final Directory staging;

  final TermuxGitFiles termux;

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
      staging: Directory('${projects.parent.path}/git-repair'),
      termux: BridgeTermuxGitFiles(),
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
          'Termux ${scan.of(GitLinkPlace.termux).length}, '
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

    final inTermux = scan.of(GitLinkPlace.termux).toList();
    GitRepairTermux? termuxProblem;
    if (inTermux.isNotEmpty) {
      termuxProblem = await termux.available();
      if (termuxProblem == GitRepairTermux.notInstalled) {
        missing.addAll(inTermux.map((link) => link.relative));
        notes.add('Termux is not installed; its files are gone.');
        termuxProblem = null;
      } else if (termuxProblem == null) {
        say(GitRepairStage.askingTermux, 0, inTermux.length);
        final job = Directory(
          '${staging.path}/${DateTime.now().microsecondsSinceEpoch}',
        );
        try {
          final received = await termux.fetch(
            [for (final link in inTermux) link.file!],
            job,
            progress: (done) =>
                say(GitRepairStage.askingTermux, done, inTermux.length),
          );
          for (final (index, link) in inTermux.indexed) {
            final file = received[index];
            if (file != null &&
                await GitObjectLinks.verify(link.relative, file)) {
              try {
                GitObjectLinks.replace(link.path, file.path, move: true);
                restored++;
                continue;
              } catch (_) {}
            }
            missing.add(link.relative);
          }
        } on TermuxBridgeException catch (error) {
          termuxProblem = GitRepairTermux.noAnswer;
          // The code only: a message could carry the script's text.
          notes.add('Termux: ${error.code}');
        } catch (error) {
          termuxProblem = GitRepairTermux.noAnswer;
          notes.add('Termux: ${error.runtimeType}');
        } finally {
          try {
            if (job.existsSync()) job.deleteSync(recursive: true);
          } catch (_) {}
        }
      }
    }

    if (termuxProblem != null) {
      return GitRepairOutcome(
        GitRepairResult.termux,
        restored: restored,
        termux: termuxProblem,
        details: notes.join('\n'),
      );
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

/// Fetches files from Termux over an authenticated loopback socket: Termux
/// uploads each asked file (only paths in a proot-distro `.l2s` store) with
/// curl; the app writes them to its staging folder. Nothing else crosses.
class BridgeTermuxGitFiles implements TermuxGitFiles {
  BridgeTermuxGitFiles({
    Future<TermuxCommandResult> Function(String script, Duration timeout)? run,
    Future<TermuxCapabilities> Function()? capabilities,
  }) : _run =
           run ??
           ((script, timeout) => TermuxBridge.run(script, timeout: timeout)),
       _capabilities = capabilities ?? TermuxBridge.capabilities;

  final Future<TermuxCommandResult> Function(String script, Duration timeout)
  _run;
  final Future<TermuxCapabilities> Function() _capabilities;

  static const batch = 150;
  static const maxFileBytes = 2 * 1024 * 1024 * 1024;

  @override
  Future<GitRepairTermux?> available() async {
    final TermuxCapabilities found;
    try {
      found = await _capabilities();
    } catch (_) {
      return GitRepairTermux.noAnswer;
    }
    if (!found.installed) return GitRepairTermux.notInstalled;
    if (!found.permissionGranted) return GitRepairTermux.notAllowed;
    return null;
  }

  @override
  Future<Map<int, File>> fetch(
    List<String> paths,
    Directory into, {
    void Function(int done)? progress,
  }) async {
    await into.create(recursive: true);
    final token = base64Url
        .encode(List<int>.generate(32, (_) => Random.secure().nextInt(256)))
        .replaceAll('=', '');
    final received = <int, File>{};
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    server.listen((request) async {
      final match = RegExp(r'^/f/(\d{1,6})$').firstMatch(request.uri.path);
      final index = match == null ? null : int.parse(match[1]!);
      if (request.method != 'PUT' ||
          index == null ||
          index >= paths.length ||
          request.headers.value(HttpHeaders.authorizationHeader) !=
              'Bearer $token') {
        await _respond(request, HttpStatus.forbidden);
        return;
      }
      final file = File('${into.path}/$index');
      try {
        final sink = file.openWrite();
        var bytes = 0;
        await for (final chunk in request) {
          bytes += chunk.length;
          if (bytes > maxFileBytes) throw const FormatException();
          sink.add(chunk);
        }
        await sink.close();
        received[index] = file;
        await _respond(request, HttpStatus.noContent);
      } catch (_) {
        await _respond(request, HttpStatus.badRequest);
      }
    });
    try {
      var done = 0;
      for (var start = 0; start < paths.length; start += batch) {
        final end = min(start + batch, paths.length);
        final lines = <String>[
          for (var i = start; i < end; i++)
            if (GitObjectLinks.termuxStore.hasMatch(paths[i])) '$i|${paths[i]}',
        ];
        if (lines.isNotEmpty) {
          await _run(
            script(port: server.port, token: token, lines: lines),
            const Duration(minutes: 5),
          );
        }
        done = end;
        progress?.call(done);
      }
    } finally {
      await server.close(force: true);
    }
    return received;
  }

  /// The Termux side: each line is `index|path`; a path outside a
  /// proot-distro `.l2s` store, or not a readable file, is skipped.
  static String script({
    required int port,
    required String token,
    required List<String> lines,
  }) =>
      '''
# oc-git-repair
export LC_ALL=C
oc_curl_cfg=\$(mktemp "\${TMPDIR:-\$HOME}/.oc-curl.XXXXXX") || exit 1
trap 'rm -f "\$oc_curl_cfg"' EXIT
# The token reaches curl as a private config file, never as an argument.
printf 'header = "Authorization: Bearer %s"\\n' '$token' > "\$oc_curl_cfg"
while IFS='|' read -r index path; do
  case "\$path" in
    /data/data/com.termux/files/usr/var/lib/proot-distro/*/.l2s/.l2s.*|/data/user/0/com.termux/files/usr/var/lib/proot-distro/*/.l2s/.l2s.*) ;;
    *) continue ;;
  esac
  case "\$path" in *..*/*|*/..*) continue ;; esac
  [ -f "\$path" ] && [ -r "\$path" ] || continue
  curl --silent --fail --max-time 120 --connect-timeout 3 -T "\$path" \\
    -K "\$oc_curl_cfg" "http://127.0.0.1:$port/f/\$index" >/dev/null 2>&1 || true
done <<'OC_GIT_REPAIR_LIST'
${lines.join('\n')}
OC_GIT_REPAIR_LIST
printf 'done\\n'
''';

  static Future<void> _respond(HttpRequest request, int status) async {
    try {
      request.response.statusCode = status;
      request.response.persistentConnection = false;
      await request.response.close();
    } catch (_) {}
  }
}
