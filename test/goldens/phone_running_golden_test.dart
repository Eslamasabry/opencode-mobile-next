// Golden renders of This phone › Running on this phone: an ordinary moment
// (OpenCode running tests, one Claude Code conversation, a terminal), the
// hot phone of 2026-10-27 (six processes a dead Claude Code conversation
// left behind, spinning, and a heavy Flutter test), and 29 processes, near
// Android's limit of 32. Phone width 412x915, dark and light, with the
// app's real fonts.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/phone_running_golden_test.dart
// and look at every changed image before committing it.
//
// RUNNING_SAMPLES=<dir> also writes each scene at 3x to <dir> for the owner.
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/phone_processes.dart';

import '../../tool/capture/fixtures.dart' show loadCaptureFonts;
import '../support/running_on_phone_fakes.dart';

enum _Scene {
  normal('normal'),
  hot('left_behind'),
  nearLimit('near_limit');

  const _Scene(this.name);
  final String name;

  List<PhoneProcess> get processes => switch (this) {
    _Scene.normal => normalProcesses(),
    _Scene.hot => hotProcesses(),
    _Scene.nearLimit => nearLimitProcesses(),
  };
}

Future<void> _shot(
  WidgetTester tester,
  _Scene scene, {
  required bool light,
}) async {
  final boundary = GlobalKey();
  debugDefaultTargetPlatformOverride = TargetPlatform.android; // ARCH-11
  try {
    final processes = FakePhoneProcesses(
      PhoneProcessSnapshot(processes: scene.processes),
    );
    final clock = FakeClock();
    // Three minutes of readings already: what stays busy is heavy.
    final watch = watchWithHistory(
      processes,
      clock,
      history: const Duration(minutes: 3),
    );
    await pumpRunning(tester, watch, light: light, boundary: boundary);
    expect(tester.takeException(), isNull);
    final theme = light ? 'light' : 'dark';
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('phone_running_${scene.name}_$theme.png'),
    );
    final samples = Platform.environment['RUNNING_SAMPLES'];
    if (samples != null) {
      await tester.runAsync(() async {
        final render =
            boundary.currentContext!.findRenderObject()!
                as RenderRepaintBoundary;
        final image = await render.toImage(pixelRatio: 3);
        final png = await image.toByteData(format: ui.ImageByteFormat.png);
        final file = File('$samples/running_${scene.name}_$theme.png');
        await file.parent.create(recursive: true);
        await file.writeAsBytes(png!.buffer.asUint8List());
      });
    }
  } finally {
    debugDefaultTargetPlatformOverride = null;
    await unmountRunning(tester);
  }
}

void main() {
  setUpAll(loadCaptureFonts);

  for (final scene in _Scene.values) {
    for (final light in [false, true]) {
      testWidgets('${scene.name} (${light ? 'light' : 'dark'})', (
        tester,
      ) async {
        await _shot(tester, scene, light: light);
      });
    }
  }
}
