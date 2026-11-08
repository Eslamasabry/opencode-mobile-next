// Owner decision (2026-11-07): the Termux app integration is gone. Built-in
// Linux is the only engine on the phone; a saved setup from Termux gets one
// plain notice (TermuxRetiredNotice) and nothing else. These checks fail if
// any part of the old path comes back.
//
// What stays on purpose and is not checked here: the terminal emulator
// library compiled in under android/app/src/main/java/com/termux/terminal
// and the proot/talloc .so files built from Termux's package recipes. They
// run built-in Linux; they are not the Termux app.
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// The one copy that may still name Termux, and its keys.
const _noticeKeys = {'termuxRetiredNotice'};

Iterable<File> _files(String root, bool Function(String path) keep) sync* {
  final dir = Directory(root);
  if (!dir.existsSync()) return;
  for (final entity in dir.listSync(recursive: true, followLinks: false)) {
    if (entity is File && keep(entity.path)) yield entity;
  }
}

void main() {
  test('the app asks for no Termux permission and calls no RUN_COMMAND', () {
    final hits = <String>[];
    for (final file in _files(
      'android/app/src/main',
      (path) =>
          path.endsWith('.xml') ||
          path.endsWith('.kt') ||
          path.endsWith('.java') ||
          path.endsWith('.kts'),
    )) {
      final text = file.readAsStringSync();
      for (final banned in ['com.termux.permission', 'RUN_COMMAND']) {
        if (text.contains(banned)) hits.add('${file.path}: $banned');
      }
    }
    expect(hits, isEmpty);
    final manifest = File(
      'android/app/src/main/AndroidManifest.xml',
    ).readAsStringSync();
    expect(manifest, isNot(contains('<package android:name="com.termux"')));
  });

  test('lib/termux is gone and nothing imports it or its channel', () {
    expect(Directory('lib/termux').existsSync(), isFalse);
    final hits = <String>[];
    for (final file in _files('lib', (path) => path.endsWith('.dart'))) {
      final text = file.readAsStringSync();
      if (RegExp(r'''import\s+['"][^'"]*termux/''').hasMatch(text)) {
        hits.add('${file.path}: imports termux/');
      }
      if (text.contains("'oc/termux'") || text.contains('"oc/termux"')) {
        hits.add('${file.path}: oc/termux channel');
      }
    }
    expect(hits, isEmpty);
  });

  test('no copy names Termux except the one notice', () {
    final arbs = _files('lib/l10n', (path) => path.endsWith('.arb')).toList();
    expect(arbs, isNotEmpty);
    final hits = <String>[];
    for (final file in arbs) {
      final messages =
          jsonDecode(file.readAsStringSync()) as Map<String, dynamic>;
      for (final MapEntry(:key, :value) in messages.entries) {
        if (key.startsWith('@') || value is! String) continue;
        if (_noticeKeys.contains(key)) continue;
        if (value.toLowerCase().contains('termux')) {
          hits.add('${file.path}: $key');
        }
      }
    }
    expect(hits, isEmpty);
    // The notice itself is there in English.
    final english =
        jsonDecode(File('lib/l10n/app_en.arb').readAsStringSync())
            as Map<String, dynamic>;
    expect(
      english['termuxRetiredNotice'],
      startsWith('Termux is no longer supported.'),
    );
  });
}
