// Golden renders for slice-qa-phone-setup (emulator QA 2026-09-28): B2 —
// a nominal 2 GB phone (1,972 MB) may set up with a plain "may be slow"
// note, and a 1,700 MB phone is refused with the way forward; B6 — a saved
// server's "not answering" line over the phone's own setup is one line with
// its Reconnect behind More. Phone and a wide window; light, the app's real
// fonts, DPR 1.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/slice_qa_phone_setup_golden_test.dart
// and look at every changed image before committing it.

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';

import '../../tool/capture/fixtures.dart' show loadCaptureFonts;
import '../support/phone_setup_scenes.dart' show sceneJob, sceneNodeDownloading;
import 'screen_phone_1_fixtures.dart';
import 'slice_qa_phone_setup_fixtures.dart';

Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 12; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

Future<void> _shot(
  WidgetTester tester,
  String name, {
  required Widget home,
  Size size = phoneSize,
  SetupProgress? progress,
}) async {
  final boundary = GlobalKey();
  debugDefaultTargetPlatformOverride = TargetPlatform.android; // ARCH-11
  try {
    await pumpPhone(
      tester,
      home: home,
      size: size,
      light: true,
      boundary: boundary,
      progress: progress,
    );
    await _settle(tester);
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('goldens/$name.png'),
    );
  } finally {
    debugDefaultTargetPlatformOverride = null;
    await unmountPhone(tester);
  }
}

String _name(String base, Size size) =>
    size == phoneSize ? '${base}_light' : '${base}_1280x800_light';

void main() {
  setUpAll(loadCaptureFonts);
  setUp(() {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(
      const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
      (_) async => null,
    );
    addTearDown(
      () => messenger.setMockMethodCallHandler(
        const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
        null,
      ),
    );
  });

  for (final size in [phoneSize, wideSize]) {
    testWidgets('B2: a 1,972 MB phone may set up, noted · $size', (
      tester,
    ) async {
      await _shot(
        tester,
        _name('qa_phone_setup_start_2gb', size),
        home: startOnPhone(memoryMb: 1972),
        size: size,
      );
    });

    testWidgets('B6: another server not answering over setup · $size', (
      tester,
    ) async {
      await _shot(
        tester,
        _name('qa_phone_setup_progress_other_server', size),
        home: underAppConditions([
          otherServerNotAnswering(),
        ], progressOnPhone()),
        size: size,
        progress: sceneJob(
          eta: 300,
          done: {'linux'},
          current: sceneNodeDownloading,
        ),
      );
    });
  }

  testWidgets('B2: a 1,700 MB phone is refused with the way forward', (
    tester,
  ) async {
    await _shot(
      tester,
      _name('qa_phone_setup_start_low_memory', phoneSize),
      home: startOnPhone(memoryMb: 1700),
    );
  });

  testWidgets('B6: the start page under the other server\'s line', (
    tester,
  ) async {
    await _shot(
      tester,
      _name('qa_phone_setup_start_other_server', phoneSize),
      home: underAppConditions([otherServerNotAnswering()], startOnPhone()),
    );
  });
}
