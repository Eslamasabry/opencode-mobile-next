// Golden renders of screen-servers-2's pages (wave 2b): the setup guide,
// Run as a Linux service, the pairing scanner's states, the Inbox's
// saved-server rows with the switch question, and the Server settings page
// with its restart sheet and update question, rebuilt from kit parts.
// Phone 412x915 and one wide window (1280x800), dark and light (owner
// decision 2026-09-27: no Arabic), with the app's real fonts at DPR 1.
// profile-monitor itself is merge-into:servers (slice-P4.2b), so it gets no
// golden of its own (MAP-1).
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/screen_servers_2_golden_test.dart
// and look at every changed image before committing it.
import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import '../support/fake_server_gateway.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import '../support/fake_event_channel.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/camera.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profile_monitor.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/guide_screen.dart';
import 'package:opencode_mobile/ui/screens/host_management_screen.dart';
import 'package:opencode_mobile/ui/screens/pairing_scanner_screen.dart';
import 'package:opencode_mobile/ui/screens/profile_monitor_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;
import '../support/profile_monitor_fixture.dart';

class _Camera implements CameraPlatform {
  _Camera(this.answer, {this.hold = false});
  final CameraPermission answer;

  /// Never answers: the starting state.
  final bool hold;

  @override
  Future<bool> hasCamera() async => true;

  @override
  Future<CameraPermission> requestCameraPermission() =>
      hold ? Completer<CameraPermission>().future : Future.value(answer);

  @override
  Future<void> openAppSettings() async {}
}

class _NoCamera extends _Camera {
  _NoCamera() : super(CameraPermission.denied);

  @override
  Future<bool> hasCamera() async => false;
}

class _Api extends FakeServerGateway {
  _Api({this.fails = false, this.limited = false})
    : super(baseUrl: 'http://192.168.1.20:4096');
  final bool fails;
  final bool limited;

  @override
  ServerCapabilities get capabilities => limited
      ? const ServerCapabilities(
          remoteUpgrade: false,
          setupConfigRead: true,
          pluginInventory: true,
        )
      : super.capabilities;

  @override
  Future<Health> health() async {
    if (fails) throw const ProductException('The server did not answer.');
    return Health(healthy: true, version: '1.19.5');
  }
}

class _Repository implements ProductRepository {
  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<String> upgradeServer(String target) async => target;

  /// The shell row reads this; unanswered it would show its failure line.
  @override
  Future<TerminalShellSettings> loadTerminalShellSettings() async =>
      const TerminalShellSettings(selected: '', options: []);

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Store extends ProfileStore {
  _Store({required super.prefs});

  final _profile = ServerProfile(
    id: 'laptop',
    name: 'Laptop',
    baseUrl: 'http://192.168.1.20:4096',
    username: 'opencode',
    password: 'fixture-only',
  );

  @override
  List<ServerProfile> get profiles => [_profile];

  @override
  String? get activeId => _profile.id;
}

Future<ConnectionController> _server({
  bool fails = false,
  bool limited = false,
}) async {
  SharedPreferences.setMockInitialValues({});
  return ConnectionController(
      _Store(prefs: await SharedPreferences.getInstance()),
    )
    ..api = _Api(fails: fails, limited: limited)
    ..repository = _Repository()
    ..version = '1.19.5'
    ..status = StreamStatus.connected;
}

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

String _name(String shot, Size size, bool light) => [
  shot,
  if (size != _phone) '${size.width.toInt()}x${size.height.toInt()}',
  light ? 'light' : 'dark',
].join('_');

/// Pumps [home] (a whole page, or a host for [open]), settles, and compares
/// the whole window. [settle] false pumps a fixed time instead, for a state
/// that waits on purpose.
Future<void> _shot(
  WidgetTester tester,
  String shot, {
  required bool light,
  required Widget home,
  Size size = _phone,
  FutureOr<void> Function(BuildContext context)? open,
  Future<void> Function()? act,
  bool settle = true,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  final boundary = GlobalKey();
  try {
    late BuildContext context;
    await tester.pumpWidget(
      RepaintBoundary(
        key: boundary,
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: captureTheme(light: light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(disableAnimations: true),
            child: child!,
          ),
          home: Builder(
            builder: (inner) {
              context = inner;
              return home;
            },
          ),
        ),
      ),
    );
    if (open != null) {
      await tester.pumpAndSettle();
      unawaited(Future.sync(() => open(context)));
    }
    if (act != null) {
      await tester.pumpAndSettle();
      await act();
    }
    if (settle) {
      await tester.pumpAndSettle();
    } else {
      await tester.pump(const Duration(milliseconds: 500));
    }
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('goldens/${_name(shot, size, light)}.png'),
    );
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    debugDefaultTargetPlatformOverride = null;
  }
}

/// Scrolls the page so [target] is on screen (the page is longer than the
/// window, and a state's subject can sit below the fold).
Future<void> _reveal(WidgetTester tester, Finder target) async {
  await tester.ensureVisible(target);
  await tester.pump();
  await tester.ensureVisible(target);
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  setUp(() {
    debugPlatformCapabilities = const PlatformCapabilities.android();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          secure,
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        );
  });
  tearDown(() {
    debugPlatformCapabilities = null;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secure, null);
  });

  for (final light in [false, true]) {
    final tone = light ? 'light' : 'dark';

    group('guide ($tone)', () {
      for (final size in [_phone, _wide]) {
        testWidgets('loaded ${size.width.toInt()}', (tester) async {
          await _shot(
            tester,
            'servers_guide_loaded',
            light: light,
            size: size,
            home: const GuideScreen(),
          );
        });
      }
    });

    group('host management ($tone)', () {
      for (final size in [_phone, _wide]) {
        testWidgets('loaded ${size.width.toInt()}', (tester) async {
          final controller = await _server();
          addTearDown(controller.dispose);
          await _shot(
            tester,
            'servers_host_management_loaded',
            light: light,
            size: size,
            home: HostManagementScreen(controller: controller),
          );
        });
      }
    });

    group('pairing scanner ($tone)', () {
      Future<void> scanner(
        WidgetTester tester,
        String state,
        CameraPlatform camera, {
        Size size = _phone,
        bool settle = true,
      }) async {
        final previous = cameraPlatform;
        cameraPlatform = camera;
        addTearDown(() => cameraPlatform = previous);
        await _shot(
          tester,
          'servers_pairing_scanner_$state',
          light: light,
          size: size,
          settle: settle,
          home: const PairingScannerScreen(),
        );
      }

      testWidgets('starting', (tester) async {
        await scanner(
          tester,
          'starting',
          _Camera(CameraPermission.granted, hold: true),
          settle: false,
        );
      });
      testWidgets('denied', (tester) async {
        await scanner(tester, 'denied', _Camera(CameraPermission.denied));
      });
      testWidgets('denied wide', (tester) async {
        await scanner(
          tester,
          'denied',
          _Camera(CameraPermission.denied),
          size: _wide,
        );
      });
      testWidgets('blocked', (tester) async {
        await scanner(
          tester,
          'blocked',
          _Camera(CameraPermission.permanentlyDenied),
        );
      });
      testWidgets('no camera', (tester) async {
        await scanner(tester, 'no_camera', _NoCamera());
      });
    });

    group('saved-server attention ($tone)', () {
      Future<ConnectionController> watching(WidgetTester tester) async {
        final store = await monitorStore();
        await store.setActiveId('profile-1');
        await store.prefs.setString(
          ProfileMonitor.rulesKey('profile-2'),
          jsonEncode(const ProfileNotifyRules(enabled: true).toJson()),
        );
        final controller = ConnectionController(
          store,
          monitorGatewayFactory: (_) => (
            gateway: MonitorTestGateway(requests: [request(1)]),
            operations: MonitorTestOperations(),
          ),
        );
        await tester.runAsync(controller.profileMonitor.refresh);
        return controller;
      }

      testWidgets('embedded inbox pending', (tester) async {
        final controller = await watching(tester);
        await _shot(
          tester,
          'servers_embedded_profile_monitor_inbox_pending',
          light: light,
          home: Scaffold(
            body: SafeArea(
              child: ListView(
                children: [ProfileMonitorInbox(controller: controller)],
              ),
            ),
          ),
        );
        controller.dispose();
      });

      testWidgets('switch server question', (tester) async {
        final controller = await watching(tester);
        controller.busySessions = {'busy-session'};
        await _shot(
          tester,
          'servers_profile_monitor_switch_server_dialog_confirm',
          light: light,
          home: const Scaffold(body: SizedBox.expand()),
          open: (context) => openMonitoredRequest(
            context,
            controller,
            MonitoredRoute(
              profileID: 'profile-2',
              requestID: 'request-1',
              sessionID: 'same-session',
              kind: MonitoredRequestKind.permission,
              createdAt: DateTime(2026),
              serverUrl: 'https://server2.example',
              sourceIdentity: 'fixture',
            ),
          ),
        );
        controller.dispose();
      });
    });

    group('server settings ($tone)', () {
      for (final size in [_phone, _wide]) {
        testWidgets('loaded ${size.width.toInt()}', (tester) async {
          final controller = await _server();
          addTearDown(controller.dispose);
          await _shot(
            tester,
            'servers_server_settings_loaded',
            light: light,
            size: size,
            home: ServerPageScreen(
              controller: controller,
              profileId: controller.profile!.id,
            ),
          );
        });
      }

      testWidgets('loaded lower half', (tester) async {
        final controller = await _server();
        addTearDown(controller.dispose);
        await _shot(
          tester,
          'servers_server_settings_lower',
          light: light,
          home: ServerPageScreen(
            controller: controller,
            profileId: controller.profile!.id,
          ),
          act: () async {
            await tester.drag(
              find.byKey(const ValueKey('server-page-list')),
              const Offset(0, -2000),
            );
            await tester.pumpAndSettle();
          },
        );
      });

      testWidgets('update available', (tester) async {
        final controller = await _server();
        addTearDown(controller.dispose);
        controller.handleEventForTesting(
          EventEnvelope(
            type: 'installation.update-available',
            properties: const {'version': '1.20.0'},
          ),
        );
        await _shot(
          tester,
          'servers_server_settings_update_available',
          light: light,
          home: ServerPageScreen(
            controller: controller,
            profileId: controller.profile!.id,
          ),
          act: () =>
              _reveal(tester, find.byKey(const Key('server-updates-tile'))),
        );
      });

      testWidgets('health failed', (tester) async {
        final controller = await _server(fails: true);
        addTearDown(controller.dispose);
        await _shot(
          tester,
          'servers_server_settings_health_failed',
          light: light,
          home: ServerPageScreen(
            controller: controller,
            profileId: controller.profile!.id,
          ),
        );
      });

      testWidgets('updates gated', (tester) async {
        final controller = await _server(limited: true);
        addTearDown(controller.dispose);
        await _shot(
          tester,
          'servers_server_settings_updates_gated',
          light: light,
          home: ServerPageScreen(
            controller: controller,
            profileId: controller.profile!.id,
          ),
          act: () => _reveal(
            tester,
            find.byKey(const ValueKey('gated-remote-upgrade')),
          ),
        );
      });

      testWidgets('upgrade sheet', (tester) async {
        final controller = await _server();
        addTearDown(controller.dispose);
        controller.handleEventForTesting(
          EventEnvelope(
            type: 'installation.update-available',
            properties: const {'version': '1.20.0'},
          ),
        );
        await _shot(
          tester,
          'servers_server_settings_upgrade_sheet_confirm',
          light: light,
          home: ServerPageScreen(
            controller: controller,
            profileId: controller.profile!.id,
          ),
          act: () async {
            final tile = find.byKey(const Key('server-updates-tile'));
            await _reveal(tester, tile);
            await tester.tap(tile);
          },
        );
      });

      testWidgets('restart sheet', (tester) async {
        final controller = await _server();
        addTearDown(controller.dispose);
        controller.recordServerUpgradeInstalled('1.20.0');
        await _shot(
          tester,
          'servers_server_settings_restart_dialog_notice',
          light: light,
          home: ServerPageScreen(
            controller: controller,
            profileId: controller.profile!.id,
          ),
          act: () async {
            final tile = find.byKey(const Key('server-updates-tile'));
            await _reveal(tester, tile);
            await tester.tap(tile);
          },
        );
      });
    });
  }
}
