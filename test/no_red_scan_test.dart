// Owner rule: nothing in the app is ever painted red. Amber means "needs you";
// a destructive act is made safe by its confirmation (and a typed name where
// it exists), never by a colour. This scan fails on any use of an error or
// danger colour, a Colors.red*, or a red-hued colour literal in lib/ui.
import 'dart:io';
import 'package:flutter/painting.dart' show Color;
import 'package:flutter_test/flutter_test.dart';

import 'support/no_red.dart';

/// path suffix -> reason. Keep this empty if at all possible.
const _allowed = <String, String>{
  'lib/ui/theme_roles.dart':
      'MarkupInk.red: the red pen of the picture markup tool is the person\'s '
      'own drawing ink on their photo, not interface colour',
  'lib/ui/theme_packs_signal.dart':
      'the Signal style\'s railway lamps and Stop key (owner-approved '
      'concept CD, 2026-10-30): red means "needs you" there, in that one '
      'chosen style only; every other style stays without red',
};

final _banned = <String, RegExp>{
  'danger role': RegExp(
    r'(?<!GenUiConfirmTone)\.danger\b|\bdanger:|dangerFill|onDangerFill',
  ),
  'Colors.red': RegExp(r'Colors\.(red|redAccent|pink|pinkAccent|deepOrange)\b'),
  'Material error colour': RegExp(
    r'(colorScheme|scheme|cs)\.(error|onError|errorContainer|onErrorContainer)\b',
  ),
};

final _hex = RegExp(r'Color\(\s*0x([0-9A-Fa-f]{8})\s*\)');

String _code(String line) {
  final i = line.indexOf('//');
  return i < 0 ? line : line.substring(0, i);
}

void main() {
  test('no red anywhere in lib/ui', () {
    final hits = <String>[];
    for (final f in Directory('lib/ui').listSync(recursive: true)) {
      if (f is! File || !f.path.endsWith('.dart')) continue;
      if (_allowed.keys.any(f.path.endsWith)) continue;
      final lines = f.readAsLinesSync();
      for (var i = 0; i < lines.length; i++) {
        final line = _code(lines[i]);
        _banned.forEach((name, re) {
          if (re.hasMatch(line)) hits.add('${f.path}:${i + 1} $name');
        });
        for (final m in _hex.allMatches(line)) {
          if (isRedHue(Color(int.parse(m.group(1)!, radix: 16)))) {
            hits.add('${f.path}:${i + 1} red literal ${m.group(0)}');
          }
        }
      }
    }
    expect(hits, isEmpty, reason: hits.join('\n'));
  });

  test('every allowlisted file still needs its entry', () {
    for (final e in _allowed.entries) {
      expect(File(e.key).existsSync(), isTrue, reason: e.value);
    }
  });

  test('theme roles have no red role', () {
    final src = File('lib/ui/theme_roles.dart').readAsStringSync();
    expect(src, isNot(contains('danger')));
  });
}
