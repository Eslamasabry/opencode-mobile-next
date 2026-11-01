/// What the app tells Claude Code about the phone it runs on, so tests and
/// builds do not all start at once and heat the phone up (perf audit
/// 2026-11-01, slice S3).
///
/// Two small things are written into the agent's own Claude folder
/// (`<profile>/claude/`), where Claude Code reads them at every start:
///
/// - a marked block in `CLAUDE.md`: a short note in plain words ("this is a
///   phone: run tests one or two at a time"). Text around the block is the
///   person's and is never touched;
/// - a few cap-the-threads variables in the `env` of `settings.json`
///   (`MAKEFLAGS=-j2` and similar). Only keys the person has not set are
///   added, and only keys that still hold our value are removed again.
///
/// "Always full speed" (Keep running) removes both. Nothing here blocks or
/// kills a command: the agent can still run anything the person asks for.
library;

import 'dart:convert';
import 'dart:io';

import 'package:path_provider/path_provider.dart';

/// The global switch behind Keep running's "Always full speed".
class FullSpeedPreference {
  const FullSpeedPreference._();

  /// Not per profile: one choice for the phone.
  static const key = 'oc.fullSpeed';
}

class PhoneBudgetFiles {
  const PhoneBudgetFiles._();

  static const begin = '<!-- oc-phone-budget:begin -->';
  static const end = '<!-- oc-phone-budget:end -->';

  /// The note, in the words an agent follows best: short rules, and the
  /// way out ("if the person asks for more, do it").
  static const note =
      '$begin\n'
      '# This is a phone\n'
      '\n'
      'You run on a phone with a few cores, limited memory and a battery. '
      'Heavy work heats it up and slows everything down. Please:\n'
      '\n'
      '- Run tests and analyzers one or two at a time. Flutter: '
      '`flutter test --concurrency=1`; other tools: `-j1` or `-j2`.\n'
      '- Run one build at a time, and one test file before the whole suite.\n'
      '- Never leave watch modes or dev servers running; stop what you '
      'started when you are done.\n'
      '- Start as few background programs as you can: Android stops an '
      'app that has more than about 30 child processes.\n'
      '\n'
      'If the person asks for something heavier, do it.\n'
      '$end\n';

  /// Variables that cap how many threads common tools start. Added to the
  /// `env` of Claude Code's settings only where the person set none.
  static const env = <String, String>{
    'MAKEFLAGS': '-j2',
    'CMAKE_BUILD_PARALLEL_LEVEL': '2',
    'CARGO_BUILD_JOBS': '2',
    'GOMAXPROCS': '2',
    'OMP_NUM_THREADS': '2',
    'UV_THREADPOOL_SIZE': '2',
    'GRADLE_OPTS': '-Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false',
  };

  /// Where a profile's Claude folder is, below the app's files folder.
  static Directory claudeFolder(Directory filesDir, String profileId) =>
      Directory(
        '${filesDir.path}/linux/ubuntu/home/oc/.oc-profiles/$profileId/claude',
      );

  /// Writes (or, with [fullSpeed], removes) the note and the variables.
  /// Best effort: returns false when it did nothing (no folder yet, a link
  /// where a file belongs, unreadable settings), and never throws.
  static bool apply({
    required Directory filesDir,
    required String profileId,
    required bool fullSpeed,
  }) {
    try {
      final folder = claudeFolder(filesDir, profileId);
      // The profile's home exists once an agent was set up; do not make a
      // Claude folder for a phone that has none.
      if (!folder.parent.existsSync()) return false;
      if (FileSystemEntity.isLinkSync(folder.path) ||
          FileSystemEntity.isLinkSync(folder.parent.path)) {
        return false;
      }
      if (!folder.existsSync()) {
        if (fullSpeed) return true;
        folder.createSync();
      }
      final notes = _writeNote(File('${folder.path}/CLAUDE.md'), fullSpeed);
      final settings = _writeSettings(
        File('${folder.path}/settings.json'),
        fullSpeed,
      );
      return notes && settings;
    } on Object {
      return false;
    }
  }

  /// [apply] for every agent profile on this phone (the switch in Keep
  /// running changes the choice for all of them).
  static Future<void> applyEverywhere({required bool fullSpeed}) async {
    try {
      final files = await getApplicationSupportDirectory();
      final profiles = Directory(
        '${files.path}/linux/ubuntu/home/oc/.oc-profiles',
      );
      if (!profiles.existsSync()) return;
      for (final entry in profiles.listSync(followLinks: false)) {
        if (entry is! Directory) continue;
        final id = entry.uri.pathSegments.where((s) => s.isNotEmpty).last;
        if (!RegExp(r'^[A-Za-z0-9_-]{1,80}$').hasMatch(id)) continue;
        apply(filesDir: files, profileId: id, fullSpeed: fullSpeed);
      }
    } on Object {
      // Best effort.
    }
  }

  static bool _isPlainFileOrAbsent(File file) {
    final type = FileSystemEntity.typeSync(file.path, followLinks: false);
    return type == FileSystemEntityType.file ||
        type == FileSystemEntityType.notFound;
  }

  /// [current] with our block replaced, added at the end, or taken out.
  static String withBlock(String current, {required bool remove}) {
    final from = current.indexOf(begin);
    final to = from < 0 ? -1 : current.indexOf(end, from);
    var before = current;
    var after = '';
    if (from >= 0 && to > from) {
      before = current.substring(0, from);
      after = current.substring(to + end.length).replaceFirst('\n', '');
    }
    if (remove) {
      // The blank line that set the block apart goes with it.
      final head = before.trimRight();
      final joined = head.isEmpty ? after : '$head\n$after';
      return joined.trim().isEmpty ? '' : joined;
    }
    final lead = before.isEmpty || before.endsWith('\n\n')
        ? before
        : before.endsWith('\n')
        ? '$before\n'
        : '$before\n\n';
    return '$lead$note$after';
  }

  static bool _writeNote(File file, bool fullSpeed) {
    if (!_isPlainFileOrAbsent(file)) return false;
    final exists = file.existsSync();
    if (!exists && fullSpeed) return true;
    final current = exists ? file.readAsStringSync() : '';
    final next = withBlock(current, remove: fullSpeed);
    if (next == current) return true;
    if (next.isEmpty) {
      file.deleteSync();
      return true;
    }
    _replace(file, next);
    return true;
  }

  static bool _writeSettings(File file, bool fullSpeed) {
    if (!_isPlainFileOrAbsent(file)) return false;
    final exists = file.existsSync();
    if (!exists && fullSpeed) return true;
    Object? decoded = <String, Object?>{};
    if (exists) {
      final text = file.readAsStringSync();
      if (text.trim().isNotEmpty) {
        try {
          decoded = jsonDecode(text);
        } on FormatException {
          return false; // the person's file, in a shape we do not know
        }
      }
    }
    if (decoded is! Map<String, dynamic>) return false;
    final root = Map<String, dynamic>.from(decoded);
    final rawEnv = root['env'];
    if (rawEnv != null && rawEnv is! Map) return false;
    final envMap = <String, dynamic>{
      if (rawEnv is Map) ...Map<String, dynamic>.from(rawEnv),
    };
    var changed = false;
    for (final entry in env.entries) {
      if (fullSpeed) {
        if (envMap[entry.key] == entry.value) {
          envMap.remove(entry.key);
          changed = true;
        }
      } else if (!envMap.containsKey(entry.key)) {
        envMap[entry.key] = entry.value;
        changed = true;
      }
    }
    if (!changed) return true;
    if (envMap.isEmpty) {
      root.remove('env');
    } else {
      root['env'] = envMap;
    }
    if (root.isEmpty) {
      if (exists) file.deleteSync();
      return true;
    }
    _replace(file, '${const JsonEncoder.withIndent('  ').convert(root)}\n');
    return true;
  }

  /// Writes through a new file and renames it over [file]: a link planted
  /// at [file] is replaced, never followed.
  static void _replace(File file, String text) {
    final temporary = File('${file.path}.oc-new');
    if (FileSystemEntity.typeSync(temporary.path, followLinks: false) !=
        FileSystemEntityType.notFound) {
      temporary.deleteSync();
    }
    temporary.writeAsStringSync(text, flush: true);
    temporary.renameSync(file.path);
  }
}
