import 'dart:async';
import 'dart:convert';

import '../builtin/builtin_folders.dart';
import '../domain/workspace_paths.dart';
import 'bridge.dart';

/// Runs one script in Termux (`bash -s`) and returns its result.
typedef TermuxScriptRunner =
    Future<TermuxCommandResult> Function(String script, Duration timeout);

/// Folders of the OpenCode server this app runs in Termux, for the folder
/// browser: a short read-only script runs inside Termux's Ubuntu
/// (`proot-distro login opencode-ubuntu`) through [TermuxBridge.run], the
/// channel the app already uses for its own Termux features. It never
/// changes anything, never follows a link, and prints one line per child
/// folder.
///
/// The path reaches the script base64-encoded as an argument, never as
/// shell text: whatever a folder is called (quotes, spaces, `$(...)`,
/// newlines), it cannot become part of a command.
class TermuxFolders {
  TermuxFolders({TermuxScriptRunner? run, this.timeout = defaultTimeout})
    : _run = run ?? _bridgeRun;

  final TermuxScriptRunner _run;

  /// How long a listing may take in all, Termux's start included (a round
  /// trip is 1–2 s; a cold proot login can take several).
  final Duration timeout;

  static const defaultTimeout = Duration(seconds: 25);

  /// The script's own bound inside Termux, shorter than [defaultTimeout] so
  /// the script's answer ("timeout") normally arrives before the app gives up.
  static const scriptSeconds = 15;

  static Future<TermuxCommandResult> _bridgeRun(
    String script,
    Duration timeout,
  ) => TermuxBridge.run(script, timeout: timeout);

  Future<List<FolderEntry>> list(String path) async {
    final ubuntuPath = _checked(path);
    final TermuxCommandResult result;
    try {
      result = await _run(listScript(ubuntuPath), timeout).timeout(timeout);
    } on TimeoutException {
      throw const FolderListException(FolderListProblem.timedOut);
    } on TermuxBridgeException catch (error) {
      throw FolderListException(FolderListProblem.failed, error.message);
    }
    return parseListing(ubuntuPath, result.stdout);
  }

  /// Makes [path] (and any missing folder above it) inside Termux's Ubuntu.
  /// `created` is false for a folder that was already there.
  Future<({String path, bool created})> create(String path) async {
    final ubuntuPath = _checked(path);
    final problem = workspaceDirectoryProblem(ubuntuPath);
    if (problem != null) {
      throw FolderListException(FolderListProblem.invalid, problem);
    }
    final TermuxCommandResult result;
    try {
      result = await _run(createScript(ubuntuPath), timeout).timeout(timeout);
    } on TimeoutException {
      throw const FolderListException(FolderListProblem.timedOut);
    } on TermuxBridgeException catch (error) {
      throw FolderListException(FolderListProblem.failed, error.message);
    }
    final lines = const LineSplitter().convert(result.stdout);
    if (lines.contains('oc-folder-created')) {
      return (path: ubuntuPath, created: true);
    }
    if (lines.contains('oc-folder-exists')) {
      return (path: ubuntuPath, created: false);
    }
    if (lines.contains('oc-folder-linked')) {
      throw FolderListException(FolderListProblem.linked, ubuntuPath);
    }
    if (lines.contains('oc-folders-timeout')) {
      throw const FolderListException(FolderListProblem.timedOut);
    }
    throw FolderListException(
      FolderListProblem.failed,
      _describe(result, 'The folder was not made.'),
    );
  }

  static String _checked(String path) {
    final value = BuiltinRootfsFolders.normalize(path);
    if (value == null || value.codeUnits.any((unit) => unit < 0x20)) {
      throw FolderListException(FolderListProblem.invalid, path);
    }
    return value;
  }

  static String _encode(String path) => base64.encode(utf8.encode(path));

  /// Decodes `$1` into `$path` exactly (the `x` keeps a trailing newline
  /// that command substitution would drop), then refuses anything but a
  /// plain absolute path.
  static const _decodePath = r'''
path=$(printf %s "$1" | base64 -d 2>/dev/null; printf x) || { echo oc-folders-invalid; exit 0; }
path=${path%x}
nl="
"
tab=$(printf "\t")
case "$path" in /*) ;; *) echo oc-folders-invalid; exit 0 ;; esac
case "$path" in *"$nl"*|*"$tab"*) echo oc-folders-invalid; exit 0 ;; esac
''';

  /// Runs inside Ubuntu as `sh -c '<this>' -- <base64 path>`. It holds no
  /// single quote, so it sits inside the outer script's single quotes as is.
  static const listInnerScript =
      'set -u\n$_decodePath'
      r'''
cur=""
rest=${path#/}
while [ -n "$rest" ]; do
  part=${rest%%/*}
  case "$rest" in */*) rest=${rest#*/} ;; *) rest="" ;; esac
  case "$part" in ""|.) continue ;; ..) echo oc-folders-invalid; exit 0 ;; esac
  cur="$cur/$part"
  if [ -L "$cur" ]; then echo oc-folders-linked; exit 0; fi
  if [ ! -d "$cur" ]; then echo oc-folders-missing; exit 0; fi
done
dir=${cur:-/}
if [ ! -r "$dir" ] || [ ! -x "$dir" ]; then echo oc-folders-denied; exit 0; fi
echo oc-folders-ok
prefix=${dir%/}
for entry in "$prefix"/*; do
  if [ -L "$entry" ] || [ ! -d "$entry" ]; then continue; fi
  name=${entry##*/}
  case "$name" in *"$nl"*|*"$tab"*) continue ;; esac
  if [ "$dir" = / ]; then
    case "$name" in dev|proc|sys) continue ;; esac
  fi
  git=-
  if [ -e "$entry/.git" ] || [ -L "$entry/.git" ]; then git=g; fi
  printf "oc-dir\t%s\t%s\n" "$git" "$name"
done
''';

  /// Makes the folder with `mkdir -p`, refusing a link in its place. A new one becomes a git project with one empty
  /// first commit, as the in-app server's new projects are: AI Team refuses
  /// a folder that is not a git repository, so a project made while turning
  /// it on failed at once (found with issue #112). Best effort: the folder
  /// is made either way, and an existing one is never touched.
  static const createInnerScript =
      'set -u\n$_decodePath'
      r'''
if [ -L "$path" ]; then echo oc-folder-linked; exit 0; fi
if [ -d "$path" ]; then echo oc-folder-exists; exit 0; fi
mkdir -p -- "$path" && [ -d "$path" ] || exit 0
if command -v git >/dev/null 2>&1 && git init -q "$path" 2>/dev/null; then
  gname=$(git config user.name || echo "AI Team")
  gmail=$(git config user.email || echo "aiteam@localhost")
  (cd "$path" && git -c user.name="$gname" -c user.email="$gmail" commit -q --allow-empty -m "First commit") >/dev/null 2>&1 || true
fi
echo oc-folder-created
''';

  /// The script Termux runs (`bash -s`) to list [path]'s folders.
  static String listScript(String path) => _outer(listInnerScript, path);

  /// The script Termux runs (`bash -s`) to make [path].
  static String createScript(String path) => _outer(createInnerScript, path);

  static String _outer(String inner, String path) {
    assert(!inner.contains("'"));
    // Base64 is letters, digits, + / and =: safe inside single quotes.
    final encoded = _encode(path);
    return '''
set -u
status=0
timeout -k 2s ${scriptSeconds}s proot-distro login opencode-ubuntu -- sh -c '
$inner' -- '$encoded' || status=\$?
case "\$status" in
  0) ;;
  124|137) echo oc-folders-timeout ;;
  *) exit "\$status" ;;
esac
''';
  }

  /// The folders in [stdout] of [listScript] for [path]. Anything that is
  /// not one of the script's own lines (a proot warning) is ignored; a
  /// listing without its `oc-folders-ok` line is an error.
  static List<FolderEntry> parseListing(String path, String stdout) {
    final lines = const LineSplitter().convert(stdout);
    FolderListProblem? problem;
    var ok = false;
    final entries = <FolderEntry>[];
    for (final line in lines) {
      switch (line.trim()) {
        case 'oc-folders-ok':
          ok = true;
          continue;
        case 'oc-folders-missing':
          problem ??= FolderListProblem.missing;
          continue;
        case 'oc-folders-linked':
          problem ??= FolderListProblem.linked;
          continue;
        case 'oc-folders-denied':
          problem ??= FolderListProblem.denied;
          continue;
        case 'oc-folders-invalid':
          problem ??= FolderListProblem.invalid;
          continue;
        case 'oc-folders-timeout':
          problem ??= FolderListProblem.timedOut;
          continue;
      }
      if (!ok || !line.startsWith('oc-dir\t')) continue;
      final parts = line.split('\t');
      if (parts.length != 3) continue;
      final (flag, name) = (parts[1], parts[2]);
      if (flag != 'g' && flag != '-') continue;
      if (name.isEmpty ||
          name == '.' ||
          name == '..' ||
          name.startsWith('.') ||
          name.contains('/') ||
          name.codeUnits.any((unit) => unit < 0x20 || unit == 0x7f)) {
        continue;
      }
      if (path == '/' && const {'dev', 'proc', 'sys'}.contains(name)) {
        continue;
      }
      entries.add(
        FolderEntry(
          name: name,
          path: path == '/' ? '/$name' : '$path/$name',
          isGit: flag == 'g',
        ),
      );
    }
    if (problem == FolderListProblem.missing &&
        path == managedProjectsDirectory) {
      // Nothing made yet: the projects folder appears with the first one.
      return const [];
    }
    if (problem != null) throw FolderListException(problem, path);
    if (!ok) {
      throw FolderListException(
        FolderListProblem.failed,
        stdout.trim().isEmpty ? 'Termux gave no answer.' : stdout.trim(),
      );
    }
    entries.sort(
      (a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()),
    );
    return entries;
  }

  static String _describe(TermuxCommandResult result, String fallback) {
    final text = [
      result.stdout.trim(),
      result.stderr.trim(),
    ].where((part) => part.isNotEmpty).join('\n');
    return text.isEmpty ? fallback : text;
  }
}
