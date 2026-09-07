import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// The Android side of launcher shortcuts cannot run under `flutter test`,
/// so the contract Dart relies on is pinned here from the sources: the
/// declared shortcuts, the channel and method names, the whitelist, the
/// single-consumption of the intent extra, and the readiness gate that decides
/// between a live push and a parked cold-start value.
void main() {
  const androidMain = 'android/app/src/main';
  final activity = File(
    '$androidMain/kotlin/io/github/eslamasabry/opencode_mobile/'
    'MainActivity.kt',
  ).readAsStringSync();
  final shortcuts = File(
    '$androidMain/res/xml/shortcuts.xml',
  ).readAsStringSync();
  final manifest = File('$androidMain/AndroidManifest.xml').readAsStringSync();

  final extraValues = RegExp(
    r'<extra\s+android:name="oc\.shortcut"\s+android:value="([^"]+)"',
  ).allMatches(shortcuts).map((m) => m.group(1)).toList();

  test('the manifest publishes the static shortcuts resource', () {
    expect(manifest, contains('android:name="android.app.shortcuts"'));
    expect(manifest, contains('android:resource="@xml/shortcuts"'));
  });

  test(
    'every declared shortcut targets MainActivity with a whitelisted id',
    () {
      expect(extraValues, unorderedEquals(['connect', 'new_task']));

      final intents = RegExp(
        r'<intent[\s\S]*?</intent>',
      ).allMatches(shortcuts).map((m) => m.group(0)!).toList();
      expect(intents, hasLength(extraValues.length));
      for (final intent in intents) {
        expect(intent, contains('android:action="android.intent.action.MAIN"'));
        expect(
          intent,
          contains(
            'android:targetClass='
            '"io.github.eslamasabry.opencode_mobile.MainActivity"',
          ),
        );
      }
    },
  );

  test('the native whitelist matches the declared shortcut ids', () {
    final whitelist = RegExp(
      r'LAUNCH_ACTIONS\s*=\s*setOf\(([^)]*)\)',
    ).firstMatch(activity);
    expect(whitelist, isNotNull);
    final ids = RegExp(
      r'"([^"]+)"',
    ).allMatches(whitelist!.group(1)!).map((m) => m.group(1)).toList();
    expect(ids, unorderedEquals(extraValues));
    expect(activity, contains('EXTRA_LAUNCH_ACTION = "oc.shortcut"'));
    expect(activity, contains('SHORTCUT_CHANNEL_NAME = "oc/shortcut"'));
  });

  test('the extra is removed before the whitelist decides', () {
    final capture = activity.indexOf('private fun captureLaunchAction');
    expect(capture, greaterThanOrEqualTo(0));
    final body = activity.substring(capture);
    final remove = body.indexOf('removeExtra(EXTRA_LAUNCH_ACTION)');
    final whitelist = body.indexOf('!in LAUNCH_ACTIONS');
    final park = body.indexOf('pendingLaunchAction = action');
    expect(remove, greaterThanOrEqualTo(0));
    expect(whitelist, greaterThan(remove));
    expect(park, greaterThan(whitelist));
  });

  test('consumeLaunchAction acknowledges readiness and drains once', () {
    final consume = activity.indexOf('"consumeLaunchAction"');
    expect(consume, greaterThanOrEqualTo(0));
    final ready = activity.indexOf('shortcutDartReady = true', consume);
    final drain = activity.indexOf('pendingLaunchAction = null', consume);
    final reply = activity.indexOf('result.success(action)', consume);
    expect(ready, greaterThan(consume));
    expect(drain, greaterThan(ready));
    expect(reply, greaterThan(drain));
  });

  test('a live tap is pushed only once Dart is listening', () {
    final newIntent = activity.indexOf('override fun onNewIntent');
    expect(newIntent, greaterThanOrEqualTo(0));
    final body = activity.substring(newIntent);
    final captured = body.indexOf('if (captureLaunchAction(intent))');
    final gate = body.indexOf(
      'if (shortcutDartReady && channel != null && action != null)',
      captured,
    );
    final push = body.indexOf('invokeMethod("launched", action)', gate);
    expect(captured, greaterThanOrEqualTo(0));
    expect(gate, greaterThan(captured));
    expect(push, greaterThan(gate));
  });

  test('engine teardown forgets the channel and its readiness', () {
    final cleanup = activity.indexOf('override fun cleanUpFlutterEngine');
    final end = activity.indexOf('super.cleanUpFlutterEngine', cleanup);
    expect(cleanup, greaterThanOrEqualTo(0));
    expect(end, greaterThan(cleanup));
    final body = activity.substring(cleanup, end);
    expect(body, contains('shortcutChannel = null'));
    expect(body, contains('shortcutDartReady = false'));
  });
}
