// Before/after renders of slice A of the motion and illustration pass
// (docs/design/motion-and-illustration-2026-09-25.md): setup start, the
// progress screen with its Details (the live log) open, setup ready, the
// connection's starting / connecting / not answering / stopped states and
// On this phone stopped. 412x915 dp, real fonts, dark (and light with
// MOTION_LIGHT=true).
//
//   flutter test --dart-define=MOTION_CAPTURE=before tool/capture/motion_setup_test.dart
//   flutter test tool/capture/motion_setup_test.dart
//
// Output: docs/qa/motion-setup-2026-09-25/<before|after>-<nn>-<state>[-light].png
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/widgets/saved_server_connection_card.dart';

import '../../test/support/phone_setup_scenes.dart';
import '../../test/support/work_tab_fixture.dart';
import 'fixtures.dart';

const _prefix = String.fromEnvironment('MOTION_CAPTURE', defaultValue: 'after');
const _light = bool.fromEnvironment('MOTION_LIGHT');
const _out = 'docs/qa/motion-setup-2026-09-25';

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

String _file(int number, String name) {
  final n = '$number'.padLeft(2, '0');
  return '$_out/$_prefix-$n-$name${_light ? '-light' : ''}.png';
}

Widget _card({String? error, bool starting = false}) => Scaffold(
  body: SafeArea(
    child: SavedServerConnectionCard(
      profileName: 'This device (Termux)',
      baseUrl: 'http://127.0.0.1:4096',
      error: error,
      attempts: 1,
      onChangeServer: () {},
      onRetry: () {},
      onStartPhoneServer: () {},
      startingPhoneServer: starting,
    ),
  ),
);

const _refused =
    'Cannot reach http://127.0.0.1:4096: Connection refused (errno = 111)';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  const setupNames = [
    'setup_start',
    'setup_start_progress',
    'setup_progress_running',
    'setup_progress_log',
    'setup_ready',
  ];
  for (final (index, name) in setupNames.indexed) {
    testWidgets('${index + 1} $name', (tester) async {
      _mockSecureStorage(tester);
      final scene = setupScenes.firstWhere((s) => s.name == name);
      final boundary = GlobalKey();
      try {
        await pumpSetupScene(tester, scene, boundary: boundary, light: _light);
        expect(tester.takeException(), isNull);
        await writePng(
          _file(index + 1, name.replaceAll('_', '-')),
          await capturePng(tester, boundary, pixelRatio: 1),
        );
      } finally {
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pump();
      }
    });
  }

  final cards = <(String, Widget, Duration)>[
    ('connection-starting', _card(error: _refused, starting: true), .zero),
    ('connection-connecting', _card(), .zero),
    ('connection-not-answering', _card(), const Duration(seconds: 9)),
    ('connection-stopped', _card(error: _refused), .zero),
  ];
  for (final (index, (name, home, wait)) in cards.indexed) {
    final number = setupNames.length + index + 1;
    testWidgets('$number $name', (tester) async {
      _mockSecureStorage(tester);
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final controller = await workController(
        status: StreamStatus.disconnected,
      );
      final boundary = GlobalKey();
      try {
        await tester.pumpWidget(
          captureApp(
            home: home,
            boundaryKey: boundary,
            controller: controller,
            light: _light,
          ),
        );
        for (var i = 0; i < 15; i++) {
          await tester.pump(const Duration(milliseconds: 100));
        }
        if (wait > Duration.zero) {
          await tester.pump(wait);
          for (var i = 0; i < 15; i++) {
            await tester.pump(const Duration(milliseconds: 100));
          }
        }
        expect(tester.takeException(), isNull);
        await writePng(
          _file(number, name),
          await capturePng(tester, boundary, pixelRatio: 1),
        );
      } finally {
        await tester.pumpWidget(const SizedBox.shrink());
        controller.dispose();
        await tester.pump();
      }
    });
  }
}
