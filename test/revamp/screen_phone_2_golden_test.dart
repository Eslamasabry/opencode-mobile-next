// Golden renders of screen-phone-2's pages (wave 2b), rebuilt from kit
// parts: phone setup progress (wide window; the phone renders are
// test/goldens/setup_progress_*.png) and its "Stop setup?" sheet. Phone 412x915 and one wide window (1280x800), dark and
// light (owner decision 2026-09-27: no Arabic), with the app's real fonts at
// DPR 1.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/screen_phone_2_golden_test.dart
// and look at every changed image before committing it.

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../tool/capture/fixtures.dart' show loadCaptureFonts;
import '../support/phone_setup_scenes.dart';

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

String _name(String shot, Size size, bool light) => [
  shot,
  if (size != _phone) '${size.width.toInt()}x${size.height.toInt()}',
  light ? 'light' : 'dark',
].join('_');

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

Future<void> _progress(
  WidgetTester tester,
  String shot, {
  required bool light,
  Size size = _phone,
  Future<void> Function()? then,
}) async {
  _mockSecureStorage(tester);
  final boundary = GlobalKey();
  final scene = setupScenes.firstWhere(
    (s) => s.name == 'setup_progress_running',
  );
  debugDefaultTargetPlatformOverride = TargetPlatform.android; // ARCH-11
  try {
    await pumpSetupScene(tester, scene, boundary: boundary, light: light);
    if (size != _phone) {
      tester.view.physicalSize = size;
      for (var i = 0; i < 5; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
    }
    if (then != null) await then();
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('goldens/${_name(shot, size, light)}.png'),
    );
  } finally {
    debugDefaultTargetPlatformOverride = null;
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump();
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';

    testWidgets('setup progress wide · $mode', (tester) async {
      await _progress(
        tester,
        'phone_setup_progress_running',
        light: light,
        size: _wide,
      );
    });
    testWidgets('setup progress details sheet · $mode', (tester) async {
      await _progress(
        tester,
        'phone_setup_progress_details_sheet',
        light: light,
        then: () async {
          final details = find.byKey(const Key('setup-progress-details'));
          await tester.ensureVisible(details);
          await tester.pump();
          await tester.tap(details);
          for (var i = 0; i < 8; i++) {
            await tester.pump(const Duration(milliseconds: 100));
          }
        },
      );
    });
  }
}
