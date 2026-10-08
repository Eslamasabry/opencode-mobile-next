// Golden renders of the shell while this phone switches its own server from
// OpenCode 2 to OpenCode 1: the old connection reconnecting, then refusing
// its old password. At 412x915, dark and light, with the app's real fonts.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/runtime_switch_golden_test.dart
// and look at every changed image before committing it.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/connection_status.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;
import '../support/runtime_switch_shell.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final (scene, phase, target) in [
      ('reconnecting', ConnectionStatusPhase.reconnecting, phoneOne),
      ('password', ConnectionStatusPhase.credentialsRequired, phoneOne),
      // Not a switch: the same server restarting, a plain reconnect.
      ('restart', ConnectionStatusPhase.reconnecting, phoneTwo),
    ]) {
      testWidgets('the shell during a $scene of this phone\'s server, $mode', (
        tester,
      ) async {
        tester.view.physicalSize = const Size(412, 915);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        final shell = await RuntimeSwitchShell.create();
        final boundary = GlobalKey();
        await tester.pumpWidget(
          shell.app(
            theme: captureTheme(light: light),
            boundaryKey: boundary,
          ),
        );
        await tester.pump();
        unawaited(shell.starter.start(target));
        shell.controller.show(phase);
        await tester.pump();
        await tester.pump(const Duration(seconds: 1));
        expect(tester.takeException(), isNull);
        await expectLater(
          find.byKey(boundary),
          matchesGoldenFile('runtime_switch_${scene}_$mode.png'),
        );
        shell.linux.finish();
        await tester.pump();
        await tester.pumpWidget(const SizedBox.shrink());
        shell.dispose();
      });
    }
  }
}
