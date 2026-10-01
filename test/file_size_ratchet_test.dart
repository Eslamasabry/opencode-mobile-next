// Ratchet against oversized source files (owner, 2026-10-02: "Why do we have
// a file that's 8600 lines? Break it down immediately.").
//
// Every lib/**/*.dart file holds at most [_maxLines] lines, except the
// offenders recorded in [_baseline] at their size when this gate landed.
// Those may only shrink: the test fails when a listed file grows, or when a
// file not in the baseline passes [_maxLines]. Split the file instead of
// raising a number (part files with `extension ... on _State` for code that
// needs the State's private fields, own files for real widgets; see
// lib/ui/screens/chat/ for the pattern).
//
// The chat screen's library (lib/ui/screens/chat_screen.dart and every part
// in lib/ui/screens/chat/) was split to at most [_chatMaxLines] lines a file
// and stays there.
//
// When a listed file shrinks, the test passes and prints the smaller number:
// lower its entry in [_baseline] (remove it once it is at or under
// [_maxLines]). Generated localization output (lib/l10n/app_localizations*)
// is not counted.
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

const _maxLines = 1500;
const _chatMaxLines = 800;

/// path -> line count when the gate landed (2026-10-02). Only goes down.
const _baseline = <String, int>{
  'lib/api/product_repository.dart': 2708,
  'lib/api2/gateway_operations.dart': 1849,
  'lib/api2/models.dart': 1647,
  'lib/api/models.dart': 1630,
  'lib/domain/server_gateway.dart': 1522,
};

bool _generated(String path) =>
    RegExp(r'^lib/l10n/app_localizations(_\w+)?\.dart$').hasMatch(path);

bool _chatLibrary(String path) =>
    path == 'lib/ui/screens/chat_screen.dart' ||
    path.startsWith('lib/ui/screens/chat/');

/// path -> line count for every counted Dart file under lib/.
Map<String, int> _lineCounts() => {
  for (final file in Directory(
    'lib',
  ).listSync(recursive: true).whereType<File>())
    if (file.path.endsWith('.dart'))
      if (file.path.replaceAll('\\', '/') case final path
          when !_generated(path))
        path: file.readAsLinesSync().length,
};

void main() {
  test('no lib file grows past its line budget', () {
    final counts = _lineCounts();
    final problems = <String>[];
    final shrunk = <String>[];
    for (final MapEntry(key: path, value: lines) in counts.entries) {
      final recorded = _baseline[path];
      if (recorded == null) {
        if (lines > _maxLines) {
          problems.add(
            '$path has $lines lines (limit $_maxLines). Split it into '
            'smaller files; do not add it to the baseline.',
          );
        }
      } else if (lines > recorded) {
        problems.add(
          '$path grew from $recorded to $lines lines. Baselined files may '
          'only shrink: move code out instead.',
        );
      } else if (lines < recorded) {
        shrunk.add(
          lines <= _maxLines
              ? '$path: $recorded -> $lines (now within $_maxLines: remove '
                    'its entry)'
              : '$path: $recorded -> $lines',
        );
      }
      if (_chatLibrary(path) && lines > _chatMaxLines) {
        problems.add(
          '$path has $lines lines; the chat screen library keeps every file '
          'at or under $_chatMaxLines. Move the code into its own part file.',
        );
      }
    }
    for (final path in _baseline.keys) {
      if (!counts.containsKey(path)) {
        shrunk.add('$path: gone (remove its entry)');
      }
    }
    if (shrunk.isNotEmpty) {
      stdout.writeln(
        'file_size_ratchet: files shrank; lower their entries in '
        '_baseline in test/file_size_ratchet_test.dart:\n  '
        '${shrunk.join('\n  ')}',
      );
    }
    expect(problems, isEmpty, reason: problems.join('\n'));
  });

  test('the baseline only lists files over the limit', () {
    for (final MapEntry(key: path, value: lines) in _baseline.entries) {
      expect(
        lines,
        greaterThan(_maxLines),
        reason: '$path is within $_maxLines lines: remove it from _baseline',
      );
    }
  });
}
