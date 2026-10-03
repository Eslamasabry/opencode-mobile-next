// Golden renders of the screens migrated to the design kit
// (design-standard §8): the connection states, at 412x915, dark and light,
// with the app's real fonts.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/work_tab_golden_test.dart
// and look at every changed image before committing it.

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/ui/kit/kit_motion.dart';
import 'package:opencode_mobile/ui/widgets/app_connection_status.dart';
import 'package:opencode_mobile/ui/widgets/saved_server_connection_card.dart';

import '../../tool/capture/fixtures.dart' show captureApp, loadCaptureFonts;
import '../support/work_tab_fixture.dart';

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

Future<void> _golden(
  WidgetTester tester,
  String name, {
  required bool light,
  required WorkController controller,
  required Widget home,
  Future<void> Function()? before,
}) async {
  _mockSecureStorage(tester);
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final boundary = GlobalKey();
  final navigatorKey = GlobalKey<NavigatorState>();
  try {
    await tester.pumpWidget(
      captureApp(
        navigatorKey: navigatorKey,
        // The app's status slot as main.dart hosts it above every page:
        // since the one controller-owned connection status (3d64653c) the
        // Work tab's connection line comes from here, not from the page.
        home: AppConnectionStatusScope(
          controller: controller,
          navigatorKey: navigatorKey,
          child: home,
        ),
        boundaryKey: boundary,
        controller: controller,
        light: light,
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    if (before != null) {
      await before();
      // A state that changed during `before` arrives with a short fade
      // (design standard §10); capture it once it has arrived.
      await tester.pump(KitMotion.standard);
    }
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('${name}_${light ? 'light' : 'dark'}.png'),
    );
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
    await tester.pump();
  }
}

Widget _card({
  String? error,
  bool starting = false,
  bool notAnswering = false,
  VoidCallback? onStart,
}) => Scaffold(
  body: SafeArea(
    child: SavedServerConnectionCard(
      profileName: 'This device (Termux)',
      baseUrl: 'http://127.0.0.1:4096',
      error: error,
      attempts: 1,
      supportsTermux: true,
      onChangeServer: () {},
      onRetry: () {},
      onOpenTermuxSetup: () {},
      onStartPhoneServer: onStart,
      startingPhoneServer: starting,
      notAnswering: notAnswering,
    ),
  ),
);

/// Long enough for a state's drawing to finish drawing itself in
/// (KitMotion.entrance), so the golden shows the finished frame.
const _drawn = Duration(seconds: 1);

const _refused =
    'Cannot reach http://127.0.0.1:4096: Connection refused (errno = 111)';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';

    testWidgets('connection · connecting · $mode', (tester) async {
      await _golden(
        tester,
        'connection_connecting',
        light: light,
        controller: await workController(status: StreamStatus.connecting),
        home: _card(onStart: () {}),
        before: () => tester.pump(_drawn),
      );
    });

    testWidgets('connection · not answering · $mode', (tester) async {
      await _golden(
        tester,
        'connection_not_answering',
        light: light,
        controller: await workController(status: StreamStatus.connecting),
        // The controller's 8 s wait ran out (the card keeps no clock).
        home: _card(onStart: () {}, notAnswering: true),
        before: () => tester.pump(_drawn),
      );
    });

    testWidgets('connection · stopped · $mode', (tester) async {
      await _golden(
        tester,
        'connection_stopped',
        light: light,
        controller: await workController(status: StreamStatus.disconnected),
        home: _card(error: _refused, onStart: () {}),
        before: () => tester.pump(_drawn),
      );
    });

    testWidgets('connection · starting · $mode', (tester) async {
      await _golden(
        tester,
        'connection_starting',
        light: light,
        controller: await workController(status: StreamStatus.disconnected),
        // The last attempt's error is still there while it starts: the
        // screen must say "Starting", not "stopped".
        home: _card(error: _refused, starting: true, onStart: () {}),
        before: () => tester.pump(_drawn),
      );
    });

    testWidgets('connection · failed (remote) · $mode', (tester) async {
      await _golden(
        tester,
        'connection_failed',
        light: light,
        controller: await workController(status: StreamStatus.disconnected),
        home: Scaffold(
          body: SafeArea(
            child: SavedServerConnectionCard(
              profileName: 'Laptop',
              baseUrl: 'http://100.64.0.7:4096',
              error: 'Cannot reach http://100.64.0.7:4096: timed out',
              attempts: 2,
              supportsTermux: true,
              onChangeServer: () {},
              onRetry: () {},
            ),
          ),
        ),
      );
    });
  }
}
