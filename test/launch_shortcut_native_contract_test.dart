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
  final channel = File(
    '$androidMain/kotlin/io/github/eslamasabry/opencode_mobile/LaunchShortcutChannel.kt',
  ).readAsStringSync();
  final launches = File(
    '$androidMain/kotlin/io/github/eslamasabry/opencode_mobile/LaunchActivityBridge.kt',
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
    // The resource is chosen per build: the preview variant, installed
    // beside the stable app, needs shortcuts that open its own package.
    expect(manifest, contains(r'android:resource="${appShortcuts}"'));
    final gradle = File('android/app/build.gradle.kts').readAsStringSync();
    expect(
      gradle,
      contains(
        'manifestPlaceholders["appShortcuts"] =\n'
        '            if (ocPreview) "@xml/shortcuts_preview" else '
        '"@xml/shortcuts"',
      ),
    );
  });

  test('the preview shortcuts differ only in the package they open', () {
    final preview = File(
      '$androidMain/res/xml/shortcuts_preview.xml',
    ).readAsStringSync();
    expect(
      preview,
      contains(
        'android:targetPackage="io.github.eslamasabry.opencode_mobile.preview"',
      ),
    );
    expect(
      preview.replaceAll('opencode_mobile.preview"', 'opencode_mobile"'),
      shortcuts,
    );
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
    ).firstMatch(channel);
    expect(whitelist, isNotNull);
    final ids = RegExp(
      r'"([^"]+)"',
    ).allMatches(whitelist!.group(1)!).map((m) => m.group(1)).toList();
    // The static shortcut ids plus the Quick Settings tile's action, which
    // reaches MainActivity through the same extra (see
    // launch_surfaces_native_contract_test.dart for the tile half), and the
    // phone setup notifications' two actions (SetupService.kt), and the
    // Status widget's built-in Linux line.
    expect(
      ids,
      unorderedEquals([
        ...extraValues,
        'activity',
        'usage',
        'usage_settings',
        'background_settings',
        'phone_setup',
        'phone_setup_done',
        'linux',
      ]),
    );
    expect(activity, contains('EXTRA_LAUNCH_ACTION = "oc.shortcut"'));
    expect(channel, contains('MethodChannel(messenger, "oc/shortcut")'));
  });

  test('the extra is removed before the whitelist decides', () {
    final capture = channel.indexOf('fun captureAction(intent: Intent?)');
    expect(capture, greaterThanOrEqualTo(0));
    final body = channel.substring(capture);
    final remove = body.indexOf(
      'removeExtra(MainActivity.EXTRA_LAUNCH_ACTION)',
    );
    final whitelist = body.indexOf(
      'action in LAUNCH_ACTIONS || USAGE_TARGET.matches(action)',
    );
    final park = body.indexOf('if (accepted) pendingAction = action');
    expect(remove, greaterThanOrEqualTo(0));
    expect(whitelist, greaterThan(remove));
    expect(park, greaterThan(whitelist));
  });

  test('consumeLaunchAction acknowledges readiness and drains once', () {
    final consume = channel.indexOf('"consumeLaunchAction"');
    expect(consume, greaterThanOrEqualTo(0));
    final ready = channel.indexOf('dartReady = true', consume);
    final drain = channel.indexOf('pendingAction = null', consume);
    final reply = channel.indexOf('result.success(action)', consume);
    expect(ready, greaterThan(consume));
    expect(drain, greaterThan(ready));
    expect(reply, greaterThan(drain));
  });

  test('a live tap is pushed only once Dart is listening', () {
    expect(activity, contains('launches.configure(messenger, intent)'));
    expect(activity, contains('launches.onNewIntent(intent)'));
    expect(launches, contains('shortcuts.configure(messenger)'));
    expect(launches, contains('shortcuts.onNewIntent(intent)'));
    final newIntent = channel.indexOf('fun onNewIntent');
    expect(newIntent, greaterThanOrEqualTo(0));
    final captured = channel.indexOf(
      'if (captureAction(intent)) dispatchAction()',
      newIntent,
    );
    final dispatch = channel.indexOf('private fun dispatchAction()');
    final gate = channel.indexOf(
      'if (dartReady && currentChannel != null && action != null)',
      dispatch,
    );
    final push = channel.indexOf('invokeMethod("launched", action)', gate);
    expect(captured, greaterThan(newIntent));
    expect(dispatch, greaterThanOrEqualTo(0));
    expect(gate, greaterThan(dispatch));
    expect(push, greaterThan(gate));
  });

  test('engine teardown forgets the channel and its readiness', () {
    final cleanup = activity.indexOf('override fun cleanUpFlutterEngine');
    final end = activity.indexOf('super.cleanUpFlutterEngine', cleanup);
    expect(cleanup, greaterThanOrEqualTo(0));
    expect(end, greaterThan(cleanup));
    final body = activity.substring(cleanup, end);
    expect(body, contains('launches.reset()'));
    expect(launches, contains('shortcuts.reset()'));
    final reset = channel.indexOf('fun reset()');
    final resetEnd = channel.indexOf('fun onNewIntent', reset);
    expect(reset, isNonNegative);
    expect(resetEnd, greaterThan(reset));
    final resetBody = channel.substring(reset, resetEnd);
    expect(resetBody, contains('channel = null'));
    expect(resetBody, contains('dartReady = false'));
  });
}
