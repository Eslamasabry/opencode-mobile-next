// G26 — no new analyzer suppressions (STANDARDS.md PROC-3, §18.2 G26).
//
// A pure-Dart file scan, no widget pumping. It checks two things against the
// committed baseline test/analyzer_suppressions_baseline.json, which may only
// shrink.
//
// 1. Suppression comments in every Dart file of the repo (tracked or
//    untracked-but-not-ignored, from `git ls-files`), except the generated
//    SDK under packages/opencode_sdk/ and the gen-l10n output
//    lib/l10n/app_localizations*.dart. Per file it keeps the number of
//    `ignore` / `ignore_for_file` comments (matched by `_suppression` below)
//    and the multiset of suppression tokens, one token per suppressed code:
//    `ignore:avoid_print`, `ignore_for_file:type=lint`. A comma list gives
//    one token per code. A file fails when its comment count rises or when
//    it holds a token its baseline multiset does not, so widening a line
//    ignore to a whole-file ignore, adding a code to an existing comment or
//    swapping one ignore for another all fail.
//
// 2. Analyzer options files: every `analysis_options.yaml` and
//    `analysis_options.mustache` in the repo must be on the baseline's
//    allowlist. For each, the top-level `include:` must equal its baseline
//    (removing or changing it switches off a whole lint set), and the
//    `analyzer: exclude:` list, `analyzer: errors:` entries set to `ignore`
//    and `linter: rules:` entries set to `false` may not gain an entry. The
//    reader is a small block-YAML parser that fails closed: a flow mapping,
//    anchor, alias, tag, complex key or block scalar under `include:`,
//    `analyzer:` or `linter:`, a multi-line flow list, or a second YAML
//    document is reported as a problem rather than skipped.
//
// When anything drops the test still passes but prints the smaller baseline;
// commit it so the numbers only go down (PROC-13: lower only the entries for
// files you changed; G31 checks that no entry rises or appears).
//
// Regenerate the baseline (only to lower it):
//   ANALYZER_SUPPRESSIONS_WRITE=1 flutter test test/analyzer_suppressions_test.dart
// with the pinned Flutter from AGENTS.md, then run it again without the
// variable and commit the smaller numbers.
//
// Instead of adding a suppression, fix the diagnostic. This file prints with
// stdout.writeln rather than print so it needs no suppression itself.
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

const _baselinePath = 'test/analyzer_suppressions_baseline.json';

/// Generated from `contracts/` by tool/sdk/generate.sh; its own options file
/// is tracked below, its Dart sources are not scanned for comments.
const _generatedSdk = 'packages/opencode_sdk/';
// flutter gen-l10n output: its fixed header ignores are the generator's, and
// each new language adds a file with the same header.
const _generatedL10n = 'lib/l10n/app_localizations';

/// The suppression comment pattern from STANDARDS.md §18.2 G26.
final _suppression = RegExp(r'//\s*ignore(_for_file)?:');

/// One suppressed code after `ignore:`: `type=lint` or a diagnostic name,
/// optionally `plugin.code`.
final _code = RegExp(r'\s*(type\s*=\s*\w+|[\w$]+(?:\.[\w$]+)?)');
final _comma = RegExp(r'\s*,');

final _optionsFileName = RegExp(r'(^|/)analysis_options\.(yaml|mustache)$');

/// Every file in the repo, repo-relative with forward slashes, sorted:
/// tracked plus untracked files git does not ignore. Falls back to a walk
/// that skips hidden directories, build/ and symlinks when git is missing.
List<String> _repoFiles() {
  try {
    final r = Process.runSync('git', [
      'ls-files',
      '-z',
      '--cached',
      '--others',
      '--exclude-standard',
    ]);
    if (r.exitCode == 0) {
      return (r.stdout as String)
          .split('\x00')
          .where((p) => p.isNotEmpty && File(p).existsSync())
          .toSet()
          .toList()
        ..sort();
    }
  } on ProcessException {
    // Fall through to the walk.
  }
  final out = <String>[];
  void walk(Directory dir) {
    for (final e in dir.listSync(followLinks: false)) {
      final name = e.uri.pathSegments.lastWhere((s) => s.isNotEmpty);
      if (e is Directory) {
        if (name.startsWith('.') || name == 'build') continue;
        walk(e);
      } else if (e is File) {
        out.add(e.path.replaceAll(r'\', '/').replaceFirst(RegExp(r'^\./'), ''));
      }
    }
  }

  walk(Directory('.'));
  return out..sort();
}

/// Sorted suppression tokens in [text], one per suppressed code.
List<String> _tokensIn(String text) {
  final tokens = <String>[];
  for (final m in _suppression.allMatches(text)) {
    final kind = m.group(1) == null ? 'ignore' : 'ignore_for_file';
    final end = text.indexOf('\n', m.end);
    final rest = text.substring(m.end, end < 0 ? text.length : end);
    var pos = 0;
    var any = false;
    while (true) {
      final c = _code.matchAsPrefix(rest, pos);
      if (c == null) break;
      final code = c.group(1)!.replaceAll(RegExp(r'\s'), '').toLowerCase();
      tokens.add('$kind:$code');
      any = true;
      pos = c.end;
      final comma = _comma.matchAsPrefix(rest, pos);
      if (comma == null) break;
      pos = comma.end;
    }
    if (!any) tokens.add('$kind:?');
  }
  return tokens..sort();
}

/// Items of [now] left over after removing [base] as a multiset.
List<String> _extra(List<String> now, List<String> base) {
  final left = <String, int>{};
  for (final t in base) {
    left[t] = (left[t] ?? 0) + 1;
  }
  final extra = <String>[];
  for (final t in now) {
    final n = left[t] ?? 0;
    if (n > 0) {
      left[t] = n - 1;
    } else {
      extra.add(t);
    }
  }
  return extra;
}

/// Line locations of suppression comments in [path], for failure messages.
List<String> _suppressionLines(String path) {
  final lines = File(path).readAsLinesSync();
  return [
    for (var i = 0; i < lines.length; i++)
      if (_suppression.hasMatch(lines[i])) '$path:${i + 1}: ${lines[i].trim()}',
  ];
}

/// A `#` starts a YAML comment at the line start or after whitespace.
String _stripComment(String line) {
  final m = RegExp(r'(^|\s)#').firstMatch(line);
  return (m == null ? line : line.substring(0, m.start)).trimRight();
}

int _indentOf(String line) => line.length - line.trimLeft().length;

String _unquote(String s) {
  if (s.length >= 2 &&
      ((s.startsWith("'") && s.endsWith("'")) ||
          (s.startsWith('"') && s.endsWith('"')))) {
    return s.substring(1, s.length - 1);
  }
  return s;
}

List<String> _flowList(String value) => value
    .substring(1, value.length - 1)
    .split(',')
    .map((s) => _unquote(s.trim()))
    .where((s) => s.isNotEmpty)
    .toList();

const _sections = [
  'include',
  'analyzer.exclude',
  'analyzer.errors.ignore',
  'linter.rules.disabled',
];

/// Top-level keys whose subtree the reader must understand fully.
const _scopedRoots = {'include', 'analyzer', 'linter'};

/// Keys that hold a mapping or block list, never an inline scalar.
const _containers = {'analyzer', 'linter', 'analyzer.errors', 'linter.rules'};

/// Keys where a one-line flow list is read.
const _flowListKeys = {'include', 'analyzer.exclude', 'linter.rules'};

/// Reads the suppression settings of an analysis options [text] at [path]:
/// the top-level `include`, `analyzer.exclude` (list items),
/// `analyzer.errors.ignore` (diagnostics whose severity is `ignore`) and
/// `linter.rules.disabled` (rules set to `false`). Returns the settings and
/// the YAML the reader refuses to guess at (fail closed).
(Map<String, List<String>>, List<String>) _parseOptions(
  String path,
  String text,
) {
  final found = {for (final s in _sections) s: <String>[]};
  final problems = <String>[];
  // The path of keys leading to the current line, with their indents.
  final stack = <(int, String)>[];
  var seenContent = false;
  final lines = const LineSplitter().convert(text);
  for (var i = 0; i < lines.length; i++) {
    final line = _stripComment(lines[i]);
    if (line.trim().isEmpty) continue;
    final where = '$path:${i + 1}: ${line.trim()}';
    final body = line.trim();
    if (_indentOf(line) == 0 &&
        (body == '---' || body.startsWith('--- ') || body == '...')) {
      if (!seenContent && body.startsWith('---')) continue;
      problems.add('$where: a second YAML document is not read');
      continue;
    }
    if (body.startsWith('%')) {
      problems.add('$where: a YAML directive is not read');
      continue;
    }
    seenContent = true;
    final indent = _indentOf(line);
    final isItem = body == '-' || body.startsWith('- ');
    // A key closes every key at its indent or deeper; a list item belongs to
    // the nearest key at its indent or shallower (`exclude:` then `- item`
    // may share an indent).
    while (stack.isNotEmpty &&
        (isItem ? stack.last.$1 > indent : stack.last.$1 >= indent)) {
      stack.removeLast();
    }
    final parent = stack.map((e) => e.$2).join('.');
    if (body.startsWith('?')) {
      problems.add('$where: a complex YAML key is not read');
      continue;
    }
    if (line.substring(0, indent).contains('\t')) {
      problems.add('$where: a tab in the indentation is not read');
      continue;
    }

    String? key;
    String value;
    if (isItem) {
      value = body.substring(1).trim();
    } else {
      final colon = RegExp(r':(\s|$)').firstMatch(body);
      if (colon == null) {
        if (stack.isNotEmpty && _scopedRoots.contains(stack.first.$2)) {
          problems.add('$where: a line that is not a key or list item');
        }
        continue;
      }
      key = _unquote(body.substring(0, colon.start).trim());
      value = body.substring(colon.end).trim();
    }
    final keyPath = key == null
        ? parent
        : (parent.isEmpty ? key : '$parent.$key');
    final root = stack.isNotEmpty ? stack.first.$2 : key;
    final inScope = _scopedRoots.contains(root);

    if (inScope) {
      String? refuse;
      if (body.contains('{')) {
        refuse = 'a flow mapping';
      } else if (key != null && RegExp(r'^[&*!<]').hasMatch(key)) {
        refuse = 'an anchor, alias, tag or merge key';
      } else if (RegExp(r'^[&*!]').hasMatch(value)) {
        refuse = 'an anchor, alias or tag';
      } else if (RegExp(r'^[|>]').hasMatch(value)) {
        refuse = 'a block scalar';
      } else if (value.startsWith('[') &&
          (!value.endsWith(']') ||
              isItem ||
              !_flowListKeys.contains(keyPath))) {
        refuse = 'a flow list here';
      } else if (key != null &&
          _containers.contains(keyPath) &&
          value.isNotEmpty &&
          !value.startsWith('[')) {
        refuse = 'an inline value for a mapping';
      } else if (isItem && RegExp(r'''^[\w"'][^:]*:(\s|$)''').hasMatch(value)) {
        refuse = 'a mapping inside a list item';
      }
      if (refuse != null) {
        problems.add('$where: $refuse is not read (G26 fails closed)');
        continue;
      }
    }

    if (isItem) {
      final item = _unquote(value);
      if (parent == 'analyzer.exclude') found['analyzer.exclude']!.add(item);
      if (parent == 'include') found['include']!.add(item);
      // `rules:` as a list enables rules; it cannot disable one.
      continue;
    }
    if (value.isEmpty) {
      stack.add((indent, key!));
      continue;
    }
    final scalar = _unquote(value).toLowerCase();
    if (keyPath == 'include') {
      found['include']!.addAll(
        value.startsWith('[') ? _flowList(value) : [_unquote(value)],
      );
    } else if (keyPath == 'analyzer.exclude') {
      if (value.startsWith('[')) {
        found['analyzer.exclude']!.addAll(_flowList(value));
      } else {
        found['analyzer.exclude']!.add(_unquote(value));
      }
    } else if (parent == 'analyzer.errors' && scalar == 'ignore') {
      found['analyzer.errors.ignore']!.add(key!);
    } else if (parent == 'linter.rules' && scalar == 'false') {
      found['linter.rules.disabled']!.add(key!);
    }
  }
  return ({for (final e in found.entries) e.key: e.value..sort()}, problems);
}

Map<String, dynamic> _loadBaseline() {
  final file = File(_baselinePath);
  if (!file.existsSync()) return const {};
  return jsonDecode(file.readAsStringSync()) as Map<String, dynamic>;
}

String _encode(
  Map<String, int> comments,
  Map<String, List<String>> tokens,
  Map<String, Map<String, List<String>>> options,
) {
  const encoder = JsonEncoder.withIndent('  ');
  return '${encoder.convert({'suppressionComments': comments, 'suppressionTokens': tokens, 'analysisOptions': options})}\n';
}

void main() {
  final baseline = _loadBaseline();
  final writeMode = Platform.environment['ANALYZER_SUPPRESSIONS_WRITE'] == '1';
  final files = _repoFiles();

  final comments = <String, int>{};
  final tokens = <String, List<String>>{};
  for (final path in files) {
    if (!path.endsWith('.dart') ||
        path.startsWith(_generatedSdk) ||
        path.startsWith(_generatedL10n)) {
      continue;
    }
    final text = File(path).readAsStringSync();
    final n = _suppression.allMatches(text).length;
    if (n == 0) continue;
    comments[path] = n;
    tokens[path] = _tokensIn(text);
  }

  final options = <String, Map<String, List<String>>>{};
  final parseProblems = <String>[];
  for (final path in files.where(_optionsFileName.hasMatch)) {
    final (settings, problems) = _parseOptions(
      path,
      File(path).readAsStringSync(),
    );
    options[path] = settings;
    parseProblems.addAll(problems);
  }

  if (writeMode) {
    File(_baselinePath).writeAsStringSync(_encode(comments, tokens, options));
    stdout.writeln(
      'ANALYZER_SUPPRESSIONS_WRITE=1: wrote $_baselinePath '
      '(${comments.length} files, '
      '${comments.values.fold<int>(0, (a, b) => a + b)} comments, '
      '${options.length} options files)',
    );
  }

  final baseComments =
      (baseline['suppressionComments'] as Map<String, dynamic>? ?? const {})
          .map((k, v) => MapEntry(k, (v as num).toInt()));
  final baseTokens =
      (baseline['suppressionTokens'] as Map<String, dynamic>? ?? const {}).map(
        (k, v) => MapEntry(k, (v as List).cast<String>()),
      );
  final baseOptions =
      (baseline['analysisOptions'] as Map<String, dynamic>? ?? const {}).map(
        (file, sections) => MapEntry(
          file,
          (sections as Map<String, dynamic>).map(
            (k, v) => MapEntry(k, (v as List).cast<String>()),
          ),
        ),
      );

  var shrank = false;
  var failed = false;

  test('G26: no new analyzer suppression comments (PROC-3)', () {
    final problems = <String>[];
    for (final MapEntry(key: path, value: count) in comments.entries) {
      final base = baseComments[path] ?? 0;
      final gained = _extra(tokens[path]!, baseTokens[path] ?? const []);
      if (count > base || gained.isNotEmpty) {
        problems.add(
          '$path: ${count > base ? '$count suppression comment(s), baseline $base' : 'no more comments than the baseline'}'
          '${gained.isEmpty ? '' : '; new suppression(s) ${gained.join(', ')}'}:\n'
          '  ${_suppressionLines(path).join('\n  ')}',
        );
      }
      if (count < base ||
          _extra(baseTokens[path] ?? const [], tokens[path]!).isNotEmpty) {
        shrank = true;
      }
    }
    if (baseComments.keys.any((p) => !comments.containsKey(p)) ||
        baseTokens.keys.any((p) => !tokens.containsKey(p))) {
      shrank = true;
    }
    if (!writeMode && problems.isNotEmpty) failed = true;
    expect(
      writeMode ? const <String>[] : problems,
      isEmpty,
      reason:
          'PROC-3: no new analyzer suppression, and no wider or extra code on '
          'an existing one. Fix the diagnostic instead of silencing it. The '
          'baseline ($_baselinePath) only shrinks.',
    );
  });

  test('G26: no new analysis options suppression (PROC-3)', () {
    final problems = <String>[...parseProblems];
    for (final MapEntry(key: file, value: settings) in options.entries) {
      final base = baseOptions[file];
      if (base == null) {
        problems.add(
          '$file: an analysis options file not on the G26 allowlist '
          '(${baseOptions.keys.join(', ')}); it can silence a directory',
        );
        continue;
      }
      final include = settings['include']!;
      final baseInclude = base['include'] ?? const <String>[];
      if (include.join('\n') != ([...baseInclude]..sort()).join('\n')) {
        problems.add(
          '$file include changed: baseline [${baseInclude.join(', ')}], '
          'now [${include.join(', ')}]; removing or changing it switches off '
          'a lint set',
        );
      }
      for (final section in _sections.skip(1)) {
        final entries = settings[section]!;
        final baseSet = (base[section] ?? const <String>[]).toSet();
        for (final entry in entries) {
          if (!baseSet.contains(entry)) {
            problems.add('$file $section gained "$entry"');
          }
        }
        if (baseSet.any((e) => !entries.contains(e))) shrank = true;
      }
    }
    for (final MapEntry(key: file, value: base) in baseOptions.entries) {
      if (options.containsKey(file)) continue;
      if ((base['include'] ?? const []).isNotEmpty) {
        problems.add(
          '$file is gone, and with it include '
          '[${base['include']!.join(', ')}]',
        );
      } else {
        shrank = true;
      }
    }
    if (!writeMode && problems.isNotEmpty) failed = true;
    expect(
      writeMode ? const <String>[] : problems,
      isEmpty,
      reason:
          'PROC-3: no new exclude:, errors: ignore, disabled lint rule, '
          'changed include: or unlisted options file. The baseline '
          '($_baselinePath) only shrinks.',
    );
  });

  // Printed only when nothing failed, and as the per-entry minimum of the
  // baseline and today, so a printed baseline never raises an entry.
  tearDownAll(() {
    if (!shrank || failed || writeMode) return;
    final lowComments = {
      for (final MapEntry(key: p, value: n) in comments.entries)
        if (baseComments.containsKey(p))
          p: n < baseComments[p]! ? n : baseComments[p]!,
    };
    final lowTokens = <String, List<String>>{};
    for (final MapEntry(key: p, value: now) in tokens.entries) {
      final base = baseTokens[p];
      if (base == null) continue;
      final extra = _extra(now, base);
      final common = _extra(now, extra)..sort();
      if (common.isNotEmpty) lowTokens[p] = common;
    }
    final lowOptions = {
      for (final MapEntry(key: f, value: now) in options.entries)
        if (baseOptions[f] case final base?)
          f: {
            'include': [...?base['include']]..sort(),
            for (final section in _sections.skip(1))
              section: [
                for (final e in now[section]!)
                  if ((base[section] ?? const []).contains(e)) e,
              ],
          },
    };
    stdout.writeln(
      '--- G26 baseline shrank: commit as $_baselinePath ---\n'
      '${_encode(lowComments, lowTokens, lowOptions)}--- end baseline ---',
    );
  });
}
