// Before/after renders for the front-door backlog items of 2026-10-07:
// FB3 (the welcome's recommended phone choice) and FB2 (the step line on
// phone setup screens A and B). 412x915 dp, dark theme, real fonts.
//
//   flutter test --concurrency=1 --dart-define=FRONT_DOOR_CAPTURE=before \
//     tool/capture/front_door_test.dart     # on the commit before the change
//   flutter test --concurrency=1 tool/capture/front_door_test.dart
//
// Output: docs/qa/fb3-2026-10-07/<prefix>-welcome.png and
// docs/qa/fb2-2026-10-07/<prefix>-<scene>.png
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';

import '../../test/support/phone_setup_scenes.dart';
import 'fixtures.dart';

const _prefix = String.fromEnvironment(
  'FRONT_DOOR_CAPTURE',
  defaultValue: 'after',
);

void _mockSecureStorage(WidgetTester tester) {
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
    secure,
    (call) async => call.method == 'readAll' ? <String, String>{} : null,
  );
  addTearDown(
    () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      secure,
      null,
    ),
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  final scenes = <(String, SetupScene)>[
    (
      'docs/qa/fb3-2026-10-07/$_prefix-welcome.png',
      SetupScene('welcome', home: () => const ServersScreen()),
    ),
    for (final name in ['setup_start', 'setup_progress_running'])
      (
        'docs/qa/fb2-2026-10-07/$_prefix-${name.replaceAll('_', '-')}.png',
        setupScenes.firstWhere((scene) => scene.name == name),
      ),
  ];

  for (final (path, scene) in scenes) {
    testWidgets(path, (tester) async {
      _mockSecureStorage(tester);
      final boundary = GlobalKey();
      try {
        await pumpSetupScene(tester, scene, boundary: boundary);
        expect(tester.takeException(), isNull);
        await writePng(path, await capturePng(tester, boundary, pixelRatio: 1));
      } finally {
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pump();
      }
    });
  }
}
