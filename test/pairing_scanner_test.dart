import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/platform/camera.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/pairing.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/pairing_scanner_screen.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _password = 'Ig9K30-es1a7CqKcImcWUQcsghzCPYPKXHCB2c8Qtco';

String pairJson({List<String> urls = const ['http://127.0.0.1:4097']}) =>
    '{"urls":${jsonEncode(urls)},"username":"opencode",'
    '"password":${jsonEncode(_password)}}';

/// A camera that answers exactly what a test wants, and records what it was
/// asked. The real one is a MethodChannel to `oc/camera`.
class _FakeCamera implements CameraPlatform {
  /// Defaults are the happy path; each test narrows to the case it is about.
  bool camera = true;
  CameraPermission permission = CameraPermission.granted;
  int settingsOpened = 0;
  int permissionRequests = 0;

  @override
  Future<bool> hasCamera() async => camera;

  @override
  Future<CameraPermission> requestCameraPermission() async {
    permissionRequests++;
    return permission;
  }

  @override
  Future<void> openAppSettings() async => settingsOpened++;
}

class _EmptyStore extends ProfileStore {
  _EmptyStore({required super.prefs});

  @override
  List<ServerProfile> get profiles => const [];
}

Future<void> pumpScanner(WidgetTester tester) async {
  await tester.pumpWidget(
    const MaterialApp(home: PairingScannerScreen()),
  );
  await tester.pumpAndSettle();
}

void main() {
  late _FakeCamera camera;

  setUp(() {
    camera = _FakeCamera();
    cameraPlatform = camera;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  tearDown(() {
    cameraPlatform = const AndroidCameraPlatform();
    debugPlatformCapabilities = null;
  });

  group('permission denial', () {
    testWidgets('a refusable denial explains and offers paste instead', (
      tester,
    ) async {
      camera.permission = CameraPermission.denied;
      await pumpScanner(tester);

      expect(
        find.byKey(const ValueKey('pairing-scanner-denied')),
        findsOneWidget,
      );
      // What the camera is for, and that there is a way through without it.
      expect(find.textContaining('opencode2 pair'), findsWidgets);
      expect(find.text('Paste it instead'), findsOneWidget);
      // Retryable, because Android will ask again.
      expect(find.text('Try again'), findsOneWidget);
      expect(camera.settingsOpened, 0);
    });

    testWidgets('retrying asks the platform again and can succeed', (
      tester,
    ) async {
      camera.permission = CameraPermission.denied;
      await pumpScanner(tester);
      expect(camera.permissionRequests, 1);

      camera.permission = CameraPermission.permanentlyDenied;
      await tester.tap(find.text('Try again'));
      await tester.pumpAndSettle();

      expect(camera.permissionRequests, 2);
      expect(
        find.byKey(const ValueKey('pairing-scanner-blocked')),
        findsOneWidget,
      );
    });

    testWidgets('a permanent denial deep-links to app settings', (
      tester,
    ) async {
      camera.permission = CameraPermission.permanentlyDenied;
      await pumpScanner(tester);

      expect(
        find.byKey(const ValueKey('pairing-scanner-blocked')),
        findsOneWidget,
      );
      // No "Try again": Android will not ask, and offering it would lie.
      expect(find.text('Try again'), findsNothing);
      expect(find.text('Paste it instead'), findsOneWidget);

      await tester.tap(find.byKey(const ValueKey('pairing-scanner-primary')));
      await tester.pumpAndSettle();
      expect(camera.settingsOpened, 1);
    });

    testWidgets('paste instead closes the scanner with no payload', (
      tester,
    ) async {
      camera.permission = CameraPermission.denied;
      PairingPayload? returned;
      var popped = false;
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (context) => TextButton(
              onPressed: () async {
                returned = await Navigator.of(context).push<PairingPayload>(
                  MaterialPageRoute<PairingPayload>(
                    builder: (_) => const PairingScannerScreen(),
                  ),
                );
                popped = true;
              },
              child: const Text('open'),
            ),
          ),
        ),
      );
      await tester.tap(find.text('open'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Paste it instead'));
      await tester.pumpAndSettle();

      expect(popped, isTrue);
      expect(returned, isNull);
    });
  });

  group('hardware', () {
    testWidgets('no camera says so and never asks for permission', (
      tester,
    ) async {
      camera.camera = false;
      await pumpScanner(tester);

      expect(
        find.byKey(const ValueKey('pairing-scanner-no-camera')),
        findsOneWidget,
      );
      expect(find.textContaining('no camera'), findsWidgets);
      // Asking for a permission the device can never use is a pointless
      // prompt.
      expect(camera.permissionRequests, 0);
    });

    testWidgets('desktop reaches no camera code even if routed here', (
      tester,
    ) async {
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
      await pumpScanner(tester);

      expect(
        find.byKey(const ValueKey('pairing-scanner-no-camera')),
        findsOneWidget,
      );
      expect(camera.permissionRequests, 0);
    });
  });

  group('the scan affordance is gated by platform', () {
    Future<void> pumpEditor(WidgetTester tester) async {
      tester.view.physicalSize = const Size(1200, 2600);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final store = _EmptyStore(prefs: prefs);
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            bootstrapProvider.overrideWithValue(AppBootstrap(store)),
            connProvider.overrideWithValue(ConnectionController(store)),
          ],
          child: const MaterialApp(home: ServersScreen()),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('welcome-connect-card')));
      await tester.pumpAndSettle();
    }

    testWidgets('Android offers scanning', (tester) async {
      debugPlatformCapabilities = const PlatformCapabilities.android();
      await pumpEditor(tester);
      expect(
        find.byKey(const ValueKey('server-pairing-scan')),
        findsOneWidget,
      );
    });

    testWidgets('desktop renders no scan affordance at all', (tester) async {
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
      await pumpEditor(tester);
      expect(find.byKey(const ValueKey('server-pairing-scan')), findsNothing);
      // Paste needs no permission and stays.
      expect(
        find.byKey(const ValueKey('server-pairing-paste')),
        findsOneWidget,
      );
    });
  });

  group('the camera seam refuses to invent answers', () {
    tearDown(() => cameraPlatform = const AndroidCameraPlatform());

    testWidgets('off Android it reports no camera and denies permission', (
      tester,
    ) async {
      cameraPlatform = const AndroidCameraPlatform();
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
      expect(await cameraPlatform.hasCamera(), isFalse);
      expect(
        await cameraPlatform.requestCameraPermission(),
        CameraPermission.permanentlyDenied,
      );
      // And opening settings is a no-op rather than a channel error.
      await cameraPlatform.openAppSettings();
    });

    testWidgets('a missing native handler reports no camera, not a false yes', (
      tester,
    ) async {
      cameraPlatform = const AndroidCameraPlatform();
      debugPlatformCapabilities = const PlatformCapabilities.android();
      // An engine with no `oc/camera` handler — a detached engine, or an
      // Android build where registration failed. The channel raises
      // MissingPluginException; claiming a camera on that would walk the user
      // into a preview that can never open. (Mocked rather than simply left
      // unhandled: an unhandled channel in a widget test never replies at
      // all, and the await hangs until the suite times out.)
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        const MethodChannel('oc/camera'),
        (call) async => throw MissingPluginException('no handler'),
      );
      addTearDown(
        () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
          const MethodChannel('oc/camera'),
          null,
        ),
      );
      expect(await cameraPlatform.hasCamera(), isFalse);
      expect(
        await cameraPlatform.requestCameraPermission(),
        CameraPermission.denied,
      );
      // And the settings deep link swallows it rather than throwing at a
      // user who is already stuck.
      await cameraPlatform.openAppSettings();
    });
  });

  group('a scanned payload takes the same path as a pasted one', () {
    test('a QR carrying a pairing code parses identically', () {
      final scanned = parsePairingPayload(pairJson());
      final pasted = parsePairingPayload('  ${pairJson()}  ');
      expect(scanned.ok, isTrue);
      expect(pasted.ok, isTrue);
      expect(scanned.payload!.urls, pasted.payload!.urls);
      expect(scanned.payload!.password, pasted.payload!.password);
    });

    test('a QR that is not a pairing code is rejected with a reason', () {
      // The overwhelmingly likely mis-scan: a wifi QR, a URL, a vCard.
      for (final raw in <String>[
        'https://example.com',
        'WIFI:S:home;T:WPA;P:hunter2;;',
        'BEGIN:VCARD\nFN:Someone\nEND:VCARD',
        '',
      ]) {
        final parsed = parsePairingPayload(raw);
        expect(parsed.ok, isFalse, reason: raw);
        expect(parsed.error, isNotNull);
        expect(parsed.error, isNot(contains(raw.isEmpty ? ' ' : raw)));
      }
    });
  });
}
