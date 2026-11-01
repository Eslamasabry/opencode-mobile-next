import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// APK 4293 (2026-10-31) built and installed without
/// io.flutter.plugins.GeneratedPluginRegistrant: a vps_test run synced its
/// worktree into the build's server folder with --delete mid-build. No plugin
/// registered, and every cold start died in libdartjni ("JNI is not
/// initialized") once path_provider reached package:jni.
const _check = 'tool/qa/apk_has_plugins.sh';
const _build = 'tool/qa/vps_build_apk.sh';
const _registrant = 'Lio/flutter/plugins/GeneratedPluginRegistrant;';

void main() {
  late Directory temp;

  setUp(() => temp = Directory.systemTemp.createTempSync('apk_plugins_'));
  tearDown(() => temp.deleteSync(recursive: true));

  /// A stand-in APK: a zip whose dex files hold the given text.
  String apk(String name, Map<String, String> dexes) {
    final dir = Directory('${temp.path}/$name')..createSync();
    for (final entry in dexes.entries) {
      File('${dir.path}/${entry.key}').writeAsStringSync(entry.value);
    }
    final out = '${temp.path}/$name.apk';
    final zipped = Process.runSync('zip', [
      '-q',
      out,
      ...dexes.keys,
    ], workingDirectory: dir.path);
    expect(zipped.exitCode, 0, reason: '${zipped.stderr}');
    return out;
  }

  ProcessResult check(String path) => Process.runSync('bash', [_check, path]);

  test('an APK with the plugin registrant passes', () {
    final ok = check(
      apk('ok', {'classes.dex': 'dex\n035 Lfoo/Bar; $_registrant Lbaz;'}),
    );
    expect(ok.exitCode, 0, reason: '${ok.stderr}');
  });

  test('a registrant in a later dex file still counts', () {
    final ok = check(
      apk('multi', {
        'classes.dex': 'dex\n035 Lfoo/Bar;',
        'classes2.dex': 'dex\n035 $_registrant',
      }),
    );
    expect(ok.exitCode, 0, reason: '${ok.stderr}');
  });

  test('an APK without the registrant fails and says why', () {
    final bad = check(
      apk('bad', {
        'classes.dex':
            'dex\n035 Lcom/github/dart_lang/jni/JniPlugin; '
            'Lio/flutter/plugins/imagepicker/ImagePickerPlugin;',
      }),
    );
    expect(bad.exitCode, 1);
    expect(bad.stderr, contains('GeneratedPluginRegistrant'));
  });

  test('a missing APK is a usage error, not a pass', () {
    expect(check('${temp.path}/none.apk').exitCode, 64);
  });

  test('vps_build_apk builds in its own server folder and checks the APK', () {
    final script = File(_build).readAsStringSync();
    // vps_test.sh syncs (with --delete) into ~/wt/<worktree name>; the build
    // must never share that folder.
    expect(script, contains(r'sdir="$name-apk"'));
    expect(script, isNot(contains(r'wt/$name/')));
    expect(script, isNot(contains(r'~/wt/$name ')));
    expect(script, contains(r'--rsync-path="flock ~/wt/.$sdir.lock rsync"'));
    expect(script, contains(r'apk_has_plugins.sh" "$final"'));
  });
}
