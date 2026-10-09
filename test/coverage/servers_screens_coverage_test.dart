// The servers area's whole screens, drawn for the contact sheet (the look
// gate): the list in its states, the welcome, host management, the
// Tailscale and pairing steps. Not a ledger: it checks the words a person
// needs are on each screen and writes build/coverage/srvscene_*.png.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/platform/camera.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/host_management_screen.dart';
import 'package:opencode_mobile/ui/screens/pairing_scanner_screen.dart';
import 'package:opencode_mobile/ui/screens/tailscale_setup_screen.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'paseo_coverage_support.dart';
import 'servers_support.dart';

class _NoCamera implements CameraPlatform {
  @override
  Future<bool> hasCamera() async => false;

  @override
  Future<CameraPermission> requestCameraPermission() async =>
      CameraPermission.granted;

  @override
  Future<void> openAppSettings() async {}
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });
  Future<GlobalKey> pump(
    WidgetTester tester,
    List<ServerProfile> profiles, {
    Widget? home,
    bool connected = false,
  }) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    mockNoTermux();
    final (store, controller) = await serversState(profiles: profiles);
    if (connected) {
      controller
        ..api = OpenCodeApi(baseUrl: profiles.first.baseUrl)
        ..status = StreamStatus.connected;
    }
    addTearDown(controller.dispose);
    final boundary = GlobalKey();
    await tester.pumpWidget(
      home == null
          ? serversApp(boundary, store, controller)
          : serversApp(boundary, store, controller, home: home),
    );
    await frames(tester, 10);
    return boundary;
  }

  testWidgets('the list: connected, password to enter again, others', (
    tester,
  ) async {
    final boundary = await pump(tester, [
      ServerProfile(
        id: 'a',
        name: 'Studio OpenCode',
        baseUrl: 'https://studio.example.net:4096',
        serverVersion: '1.14.52',
      ),
      ServerProfile(
        id: 'b',
        name: 'Lab OpenCode',
        baseUrl: 'https://lab.example.net:4097',
        flavor: ServerFlavor.v2,
        serverVersion: '2.0.10',
        requiresPasswordReentry: true,
      ),
      ServerProfile(
        id: 'c',
        name: 'Build box',
        baseUrl: 'ws://build.example.net:6767',
        backend: ServerBackend.paseo,
        codexDirectory: '/work/shop',
      ),
      ServerProfile(
        id: 'd',
        name: 'Codex desk',
        baseUrl: 'wss://codex.example.net',
        backend: ServerBackend.codex,
        codexDirectory: '/work/api',
        requiresCodexTokenReentry: true,
      ),
    ], connected: true);
    await writeCasePng(tester, boundary, 'srvscene_list');
    final text = flat(screenText(tester).join('\n'));
    expect(text, contains('Connected'));
    expect(text, contains("Can't read the saved password"));
    expect(text, contains('Claude Code or Pi'));
    expect(text, contains('Add server'));
  });

  testWidgets('the welcome, with no server saved', (tester) async {
    final boundary = await pump(tester, const []);
    await writeCasePng(tester, boundary, 'srvscene_welcome');
    expect(
      flat(screenText(tester).join('\n')),
      contains('Where does your coding agent run?'),
    );
  });

  testWidgets('host management for a remote server', (tester) async {
    final (store, controller) = await serversState(
      profiles: [
        ServerProfile(
          id: 'a',
          name: 'Studio OpenCode',
          baseUrl: 'https://studio.example.net:4096',
        ),
      ],
    );
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    addTearDown(controller.dispose);
    final boundary = GlobalKey();
    await tester.pumpWidget(
      serversApp(
        boundary,
        store,
        controller,
        home: HostManagementScreen(controller: controller),
      ),
    );
    await frames(tester, 10);
    await writeCasePng(tester, boundary, 'srvscene_host');
    expect(flat(screenText(tester).join('\n')), contains('Studio OpenCode'));
  });

  testWidgets('Tailscale: the steps to reach a server', (tester) async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('oc/tailscale'),
          (call) async => call.method == 'check' ? 'installed' : true,
        );
    addTearDown(
      () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(const MethodChannel('oc/tailscale'), null),
    );
    final boundary = await pump(
      tester,
      const [],
      home: const TailscaleSetupScreen(),
    );
    await writeCasePng(tester, boundary, 'srvscene_tailscale');
    expect(screenText(tester), isNotEmpty);
  });

  testWidgets('pairing scanner on a phone with no camera', (tester) async {
    final old = cameraPlatform;
    cameraPlatform = _NoCamera();
    addTearDown(() => cameraPlatform = old);
    final boundary = await pump(
      tester,
      const [],
      home: const PairingScannerScreen(),
    );
    await writeCasePng(tester, boundary, 'srvscene_scanner');
    expect(
      find.byKey(const ValueKey('pairing-scanner-no-camera')),
      findsOneWidget,
    );
  });
}
