// Behaviour of screen-servers-2 (wave 2b): the setup guide, the pairing
// scanner, saved-server attention and the Server settings page, rebuilt
// from kit parts, with the map's missing actions for these pages.
import 'dart:convert';

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
import 'package:opencode_mobile/ui/screens/pairing_scanner_screen.dart';
import 'package:opencode_mobile/ui/screens/profile_monitor_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/app_iconography.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../support/profile_monitor_fixture.dart';

class _Camera implements CameraPlatform {
  _Camera(this.answer);
  CameraPermission answer;
  int asked = 0;

  @override
  Future<bool> hasCamera() async => true;

  @override
  Future<CameraPermission> requestCameraPermission() async {
    asked++;
    return answer;
  }

  @override
  Future<void> openAppSettings() async {}
}

class _Api extends FakeServerGateway {
  _Api(this.version) : super(baseUrl: 'http://203.0.113.10:4747');
  final String version;
  int checks = 0;

  @override
  Future<Health> health() async {
    checks++;
    return Health(healthy: true, version: version);
  }
}

class _Repository implements ProductRepository {
  String? upgradedTarget;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<String> upgradeServer(String target) async => upgradedTarget = target;

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Store extends ProfileStore {
  _Store({required super.prefs});

  final _profile = ServerProfile(
    id: 'server',
    name: 'Laptop',
    baseUrl: 'http://203.0.113.10:4747',
  );

  @override
  List<ServerProfile> get profiles => [_profile];

  @override
  String? get activeId => _profile.id;
}

Future<(ConnectionController, _Api)> _server({
  String healthVersion = '1.19.5',
  ProductRepository? repository,
}) async {
  SharedPreferences.setMockInitialValues({});
  final api = _Api(healthVersion);
  final controller =
      ConnectionController(_Store(prefs: await SharedPreferences.getInstance()))
        ..api = api
        ..repository = repository
        ..version = '1.18.23'
        ..status = StreamStatus.connected;
  return (controller, api);
}

Widget _app(Widget home, {Map<String, WidgetBuilder> routes = const {}}) =>
    MaterialApp(
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: home,
      routes: routes,
    );

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  setUp(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          secure,
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        ),
  );
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secure, null);
    debugPlatformCapabilities = null;
  });

  group('guide', () {
    testWidgets('step 2 has Add server, which opens Servers', (tester) async {
      await tester.pumpWidget(
        _app(
          const GuideScreen(),
          routes: {'/servers': (_) => const Text('Servers page')},
        ),
      );
      await tester.pumpAndSettle();
      final add = find.text('Add server');
      expect(add, findsOneWidget);
      await tester.ensureVisible(add);
      await tester.pumpAndSettle();
      await tester.tap(add);
      await tester.pumpAndSettle();
      expect(find.text('Servers page'), findsOneWidget);
    });

    testWidgets('step 2 names the real Servers buttons', (tester) async {
      debugPlatformCapabilities = const PlatformCapabilities.android();
      await tester.pumpWidget(_app(const GuideScreen()));
      await tester.pumpAndSettle();
      // R15: the words name Add server's own buttons and send nobody to
      // find Servers first; the Add server button stays.
      expect(
        find.text(
          'Tap Add server, then Scan code and point the camera at the QR, '
          'or Paste code.',
        ),
        findsOneWidget,
      );
      expect(find.textContaining('Open Servers'), findsNothing);
      expect(find.textContaining('open Servers'), findsNothing);
      expect(find.byKey(const ValueKey('guide-add-server')), findsOneWidget);
      expect(find.textContaining('Paste pairing code'), findsNothing);
    });

    // Sweep 3680: the heading said "Three steps" but the steps were not
    // numbered, and their icons floated in the middle of tall rows.
    testWidgets('the steps are numbered 1, 2, 3 and the number sits by the '
        'title, not mid-row', (tester) async {
      tester.view.physicalSize = const Size(412, 2400);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      debugPlatformCapabilities = const PlatformCapabilities.android();
      await tester.pumpWidget(_app(const GuideScreen()));
      await tester.pumpAndSettle();
      for (final n in ['1', '2', '3']) {
        expect(find.text(n), findsOneWidget);
      }
      final title = find.text('On your computer, run one command');
      final number = find.text('1');
      // The first step is tall (a command under its words): its number is
      // level with the title's first line.
      expect(
        (tester.getCenter(number).dy - tester.getCenter(title).dy).abs(),
        lessThan(24),
      );
    });

    testWidgets('a phone offers the no-computer path; a desktop does not', (
      tester,
    ) async {
      // Tall enough that the lazy list builds every row.
      tester.view.physicalSize = const Size(412, 2400);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final help = find.byKey(const ValueKey('guide-advanced'));
      debugPlatformCapabilities = const PlatformCapabilities.android();
      await tester.pumpWidget(_app(const GuideScreen()));
      await tester.pumpAndSettle();
      expect(help, findsOneWidget);
      expect(find.text('Use this phone instead'), findsOneWidget);

      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
      await tester.pumpWidget(_app(const GuideScreen(key: ValueKey('d'))));
      await tester.pumpAndSettle();
      expect(help, findsOneWidget);
      expect(find.text('Use this phone instead'), findsNothing);
    });
  });

  group('pairing scanner', () {
    testWidgets('denied: Allow camera is the primary and asks again', (
      tester,
    ) async {
      debugPlatformCapabilities = const PlatformCapabilities.android();
      final camera = _Camera(CameraPermission.denied);
      final previous = cameraPlatform;
      cameraPlatform = camera;
      addTearDown(() => cameraPlatform = previous);

      await tester.pumpWidget(_app(const PairingScannerScreen()));
      await tester.pumpAndSettle();
      expect(camera.asked, 1);
      final primary = find.byKey(const ValueKey('pairing-scanner-primary'));
      expect(
        find.descendant(of: primary, matching: find.text('Allow camera')),
        findsOneWidget,
      );
      expect(find.text('Paste it instead'), findsOneWidget);
      await tester.tap(primary);
      await tester.pumpAndSettle();
      expect(camera.asked, 2);
    });

    testWidgets('blocked: Open app settings, with paste as the way out', (
      tester,
    ) async {
      debugPlatformCapabilities = const PlatformCapabilities.android();
      final previous = cameraPlatform;
      cameraPlatform = _Camera(CameraPermission.permanentlyDenied);
      addTearDown(() => cameraPlatform = previous);

      await tester.pumpWidget(_app(const PairingScannerScreen()));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('pairing-scanner-blocked')),
        findsOneWidget,
      );
      await tester.tap(find.byKey(const ValueKey('pairing-scanner-secondary')));
      await tester.pumpAndSettle();
      expect(find.byType(PairingScannerScreen), findsNothing);
    });
  });

  group('server settings', () {
    testWidgets('the update question states the health-reported version', (
      tester,
    ) async {
      final repository = _Repository();
      final (controller, _) = await _server(repository: repository);
      addTearDown(controller.dispose);
      controller.handleEventForTesting(
        EventEnvelope(
          type: 'installation.update-available',
          properties: const {'version': '1.20.0'},
        ),
      );
      await tester.pumpWidget(
        _app(
          ServerPageScreen(
            controller: controller,
            profileId: controller.profile!.id,
          ),
        ),
      );
      await tester.pumpAndSettle();
      // The health row reads the probe's version, once on the page (R3);
      // the update row says what installing does instead of repeating it.
      expect(find.textContaining('1.19.5'), findsOneWidget);
      expect(
        find.text(
          "Uses OpenCode's official installer; restart the server afterwards.",
        ),
        findsOneWidget,
      );
      final tile = find.byKey(const Key('server-updates-tile'));
      await tester.ensureVisible(tile);
      await tester.pump();
      await tester.ensureVisible(tile);
      await tester.tap(tile);
      await tester.pumpAndSettle();
      expect(find.textContaining('(now 1.19.5)'), findsOneWidget);
      expect(
        find.text('The server keeps running 1.19.5 while it installs'),
        findsOneWidget,
      );
      expect(find.text('Server data stays in place'), findsOneWidget);
      await tester.tap(find.byKey(const Key('confirm-server-upgrade')));
      await tester.pumpAndSettle();
      expect(repository.upgradedTarget, '1.20.0');
      expect(find.text('Restart OpenCode to use 1.20.0'), findsOneWidget);
    });

    testWidgets('R15: the update row carries no download mark; the Linux '
        'service row says what it is for in one line', (tester) async {
      final (controller, _) = await _server(repository: _Repository());
      addTearDown(controller.dispose);
      controller.handleEventForTesting(
        EventEnvelope(
          type: 'installation.update-available',
          properties: const {'version': '1.20.0'},
        ),
      );
      await tester.pumpWidget(
        _app(
          ServerPageScreen(
            controller: controller,
            profileId: controller.profile!.id,
          ),
        ),
      );
      await tester.pumpAndSettle();
      final update = find.byKey(const Key('server-updates-tile'));
      // The server's page rows carry no leading icons, and the update row
      // no trailing download mark either.
      expect(
        find.descendant(
          of: update,
          matching: find.byIcon(AppIconography.systemDownload),
        ),
        findsNothing,
      );
      expect(
        find.text('Keep OpenCode running after you close the terminal.'),
        findsOneWidget,
      );
    });

    testWidgets('restart: the command to run, and I restarted it re-checks', (
      tester,
    ) async {
      String? copied;
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        (call) async {
          if (call.method == 'Clipboard.setData') {
            copied = (call.arguments as Map)['text'] as String?;
          }
          return null;
        },
      );
      addTearDown(
        () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
          SystemChannels.platform,
          null,
        ),
      );
      final (controller, api) = await _server();
      addTearDown(controller.dispose);
      controller.recordServerUpgradeInstalled('1.20.0');
      await tester.pumpWidget(
        _app(
          ServerPageScreen(
            controller: controller,
            profileId: controller.profile!.id,
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(api.checks, 1);
      final tile = find.byKey(const Key('server-updates-tile'));
      await tester.ensureVisible(tile);
      await tester.pump();
      await tester.ensureVisible(tile);
      await tester.tap(tile);
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('server-restart-sheet')),
        findsOneWidget,
      );
      // The app cannot know how the server was started: the sheet says to
      // restart it that way, and the helper script's command is only a
      // folded aside for servers set up with it.
      expect(find.textContaining('the way you started it'), findsOneWidget);
      expect(find.text('bash ubuntu-opencode.sh restart'), findsNothing);
      expect(find.byKey(const ValueKey('server-restart-copy')), findsNothing);
      expect(find.byKey(const ValueKey('server-restart-script')), findsNothing);
      expect(copied, isNull);
      await tester.tap(find.text('I restarted it'));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('server-restart-sheet')), findsNothing);
      expect(api.checks, 2);
    });

    testWidgets('externally managed: the commands copy in place', (
      tester,
    ) async {
      String? copied;
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        (call) async {
          if (call.method == 'Clipboard.setData') {
            copied = (call.arguments as Map)['text'] as String?;
          }
          return null;
        },
      );
      addTearDown(
        () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
          SystemChannels.platform,
          null,
        ),
      );
      final (controller, _) = await _server();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(
          ServerPageScreen(
            controller: controller,
            profileId: controller.profile!.id,
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Copy update commands for Laptop'), findsOneWidget);
      // Tapping the row itself copies: no trailing copy icon.
      expect(
        find.byKey(const ValueKey('server-update-commands-copy')),
        findsNothing,
      );
      final tile = find.byKey(const Key('server-updates-tile'));
      await tester.ensureVisible(tile);
      await tester.pump();
      await tester.ensureVisible(tile);
      await tester.tap(tile);
      await tester.pump();
      expect(copied, 'npm install -g @opencode/cli@2.1.1');
      expect(find.byType(SnackBar), findsNothing);
      await tester.pumpAndSettle();
      expect(
        find.text("Copied. Run them in a terminal on the server's computer."),
        findsOneWidget,
      );
    });

    testWidgets('the address is in Details, not on the identity row', (
      tester,
    ) async {
      final (controller, _) = await _server();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(
          ServerPageScreen(
            controller: controller,
            profileId: controller.profile!.id,
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('http://203.0.113.10:4747'), findsNothing);
      final toggle = find.byKey(const ValueKey('kit-details-toggle'));
      await tester.ensureVisible(toggle);
      await tester.pump();
      await tester.ensureVisible(toggle);
      await tester.tap(toggle);
      await tester.pumpAndSettle();
      expect(find.textContaining('203.0.113.10:4747'), findsWidgets);
    });
  });

  group('saved-server attention', () {
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

    testWidgets('another server\'s request is a needs-you row naming it', (
      tester,
    ) async {
      final controller = await watching(tester);
      await tester.pumpWidget(
        _app(Scaffold(body: ProfileMonitorInbox(controller: controller))),
      );
      await tester.pump();
      expect(find.textContaining('Server 2'), findsWidgets);
      expect(
        find.textContaining('The agent waits until you answer'),
        findsOneWidget,
      );
      await tester.pumpWidget(const SizedBox.shrink());
      controller.dispose();
    });

    testWidgets('switching names both servers and what keeps running', (
      tester,
    ) async {
      final controller = await watching(tester);
      controller.busySessions = {'busy-session'};
      await tester.pumpWidget(
        _app(
          Scaffold(
            body: Builder(
              builder: (context) => Center(
                child: GestureDetector(
                  onTap: () => openMonitoredRequest(
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
                  child: const Text('open'),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('open'));
      await tester.pumpAndSettle();
      expect(
        find.text(
          'A run is going on Server 1. Switching shows Server 2 in this '
          'app; the run on Server 1 keeps going.',
        ),
        findsOneWidget,
      );
      // The question and its button name where it goes (R2).
      expect(find.text('Switch to Server 2?'), findsOneWidget);
      expect(find.text('Switch to Server 2'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('kit-confirm-cancel')));
      await tester.pumpAndSettle();
      await tester.pumpWidget(const SizedBox.shrink());
      controller.dispose();
    });
  });
}
