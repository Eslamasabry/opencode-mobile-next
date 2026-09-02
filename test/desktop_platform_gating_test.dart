import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/background/widget_snapshot.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/guide_screen.dart';
import 'package:opencode_mobile/ui/screens/host_management_screen.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/screens/termux_setup_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _FakeApi extends OpenCodeApi {
  _FakeApi() : super(baseUrl: 'http://localhost');

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<List<MessageWithParts>> messages(String id) async => [];

  @override
  Future<Session> session(String id) async => Session(id: id);

  @override
  Future<Health> health() async => Health(healthy: true);
}

Future<ConnectionController> _chatController() async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'profile-1',
        'name': 'Test server',
        'baseUrl': 'http://localhost',
        'username': '',
      },
    ]),
    'oc.activeProfile': 'profile-1',
  });
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.load();
  return ConnectionController(store)
    ..api = _FakeApi()
    ..status = StreamStatus.connected;
}

/// Presents saved profiles without touching the real secure-storage channel,
/// which is unmocked in widget tests and would hang a real upsert.
class _SeededStore extends ProfileStore {
  _SeededStore({required super.prefs, required this.seeded});

  final List<ServerProfile> seeded;

  @override
  List<ServerProfile> get profiles => List.unmodifiable(seeded);
}

Future<(ProfileStore, ConnectionController)> _emptyState() async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.load();
  return (store, ConnectionController(store));
}

Future<(ProfileStore, ConnectionController)> _seededState() async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final store = _SeededStore(
    prefs: prefs,
    seeded: [
      ServerProfile(
        id: 'server-1',
        name: 'Workstation',
        baseUrl: 'https://box.example:4096',
        username: '',
        password: '',
      ),
    ],
  );
  return (store, ConnectionController(store));
}

Widget _servers(ProfileStore store, ConnectionController controller) =>
    ProviderScope(
      overrides: [
        bootstrapProvider.overrideWithValue(AppBootstrap(store)),
        connProvider.overrideWithValue(controller),
      ],
      child: const MaterialApp(home: ServersScreen()),
    );

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    // ProfileStore reaches for secure storage; an unmocked channel hangs.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  tearDown(() => debugPlatformCapabilities = null);

  void onDesktop() =>
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();

  group('the first-run welcome', () {
    testWidgets('offers the Termux path on Android', (tester) async {
      final (store, controller) = await _emptyState();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_servers(store, controller));

      expect(find.byKey(const ValueKey('welcome-termux-card')), findsOneWidget);
      expect(find.text('Run OpenCode on this phone'), findsOneWidget);
      expect(find.text('A two-minute guide to both paths'), findsOneWidget);
    });

    testWidgets('never mentions Termux on desktop', (tester) async {
      onDesktop();
      final (store, controller) = await _emptyState();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_servers(store, controller));

      expect(find.byKey(const ValueKey('welcome-termux-card')), findsNothing);
      expect(find.textContaining('Termux'), findsNothing);
      expect(find.textContaining('phone'), findsNothing);
      // The remaining paths are intact — this is a gate, not a deletion.
      expect(
        find.byKey(const ValueKey('welcome-connect-card')),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('welcome-guide-card')), findsOneWidget);
    });
  });

  group('the servers quick-add list', () {
    testWidgets('offers on-device setup on Android', (tester) async {
      final (store, controller) = await _seededState();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_servers(store, controller));

      expect(
        find.byKey(const ValueKey('quick-add-termux-card')),
        findsOneWidget,
      );
      expect(find.text('On-device (Termux)'), findsOneWidget);
    });

    testWidgets('offers only the remote path on desktop', (tester) async {
      onDesktop();
      final (store, controller) = await _seededState();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_servers(store, controller));

      expect(find.byKey(const ValueKey('quick-add-termux-card')), findsNothing);
      expect(find.text('On-device (Termux)'), findsNothing);
      expect(find.text('Another computer'), findsOneWidget);
    });
  });

  group('the setup guide', () {
    // The guide is a lazy ListView; a tall surface builds all of it so
    // "findsNothing" means absent rather than merely unbuilt.
    Future<void> pumpGuide(WidgetTester tester) async {
      await tester.binding.setSurfaceSize(const Size(800, 3000));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await tester.pumpWidget(const MaterialApp(home: GuideScreen()));
      // The three-step story is always visible; every other path lives
      // behind the Advanced disclosure, so open it before asserting.
      await tester.tap(find.byKey(const ValueKey('guide-advanced')));
      await tester.pumpAndSettle();
    }

    testWidgets('documents both paths on Android', (tester) async {
      await pumpGuide(tester);

      expect(find.byKey(const ValueKey('guide-pair-command')), findsOneWidget);
      expect(
        find.byKey(const ValueKey('guide-termux-section')),
        findsOneWidget,
      );
      expect(find.textContaining('Android Keystore'), findsOneWidget);
      expect(
        find.text('Scan the QR or paste the code in this app'),
        findsOneWidget,
      );
    });

    testWidgets('drops the Termux path on desktop', (tester) async {
      onDesktop();
      await pumpGuide(tester);

      expect(find.byKey(const ValueKey('guide-termux-section')), findsNothing);
      expect(find.textContaining('Termux'), findsNothing);
      expect(find.textContaining('Android Keystore'), findsNothing);
      // The remote-server instructions, the only path a desktop user has,
      // are still there and no longer numbered as one of two.
      expect(find.text('Paste the code in this app'), findsOneWidget);
      expect(find.text('OLDER SERVERS WITHOUT PAIRING'), findsOneWidget);
      expect(find.textContaining('libsecret'), findsOneWidget);
    });
  });

  group('the Termux setup screen', () {
    testWidgets('says so plainly if it is ever reached on desktop', (
      tester,
    ) async {
      onDesktop();
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final store = ProfileStore(prefs: prefs);
      await store.load();
      final controller = ConnectionController(store);
      addTearDown(controller.dispose);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            bootstrapProvider.overrideWithValue(AppBootstrap(store)),
            connProvider.overrideWithValue(controller),
          ],
          child: const MaterialApp(home: TermuxSetupScreen()),
        ),
      );
      await tester.pump();

      expect(find.byKey(const Key('termux-setup-unsupported')), findsOneWidget);
      expect(find.text('On-device setup is Android only'), findsOneWidget);
      // No step list, so nothing invites a tap that cannot work.
      expect(find.text('Get Termux'), findsNothing);
      expect(tester.takeException(), isNull);
    });
  });

  group('the composer prompt tools', () {
    Future<void> pumpChat(WidgetTester tester) async {
      final controller = await _chatController();
      addTearDown(controller.dispose);
      await tester.binding.setSurfaceSize(const Size(400, 800));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await tester.pumpWidget(
        ProviderScope(
          overrides: [connProvider.overrideWithValue(controller)],
          child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
        ),
      );
      await tester.pumpAndSettle();
    }

    Future<void> openTools(WidgetTester tester) async {
      await tester.tap(find.byKey(const Key('composer-tools-button')));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('composer-tools-sheet')), findsOneWidget);
    }

    testWidgets('offer voice input on Android', (tester) async {
      await pumpChat(tester);
      expect(
        find.byTooltip('Add. Hold to attach a file'),
        findsOneWidget,
      );
      await openTools(tester);
      expect(find.byKey(const Key('composer-tool-voice')), findsOneWidget);
    });

    testWidgets('drop voice input on desktop', (tester) async {
      onDesktop();
      await pumpChat(tester);
      // The collapsed button no longer advertises a tool that is not there.
      expect(find.byTooltip('Add. Hold to attach a file'), findsOneWidget);
      await openTools(tester);
      expect(find.byKey(const Key('composer-tool-voice')), findsNothing);
      expect(find.text('Voice input'), findsNothing);
      // Commands and Attach are untouched.
      expect(find.byKey(const Key('composer-tool-commands')), findsOneWidget);
      expect(find.byKey(const Key('composer-tool-attach')), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  });

  group('the settings hub', () {
    Future<void> pumpSettings(WidgetTester tester) async {
      final controller = await _chatController();
      addTearDown(controller.dispose);
      await tester.binding.setSurfaceSize(const Size(500, 1400));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await tester.pumpWidget(
        ProviderScope(
          overrides: [connProvider.overrideWithValue(controller)],
          child: MaterialApp(home: SettingsScreen(controller: controller)),
        ),
      );
      await tester.pumpAndSettle();
    }

    testWidgets('shows the background category on Android', (tester) async {
      await pumpSettings(tester);
      expect(
        find.byKey(const ValueKey('settings-category-background')),
        findsOneWidget,
      );
    });

    testWidgets('hides the foreground-service category on desktop', (
      tester,
    ) async {
      onDesktop();
      await pumpSettings(tester);
      expect(
        find.byKey(const ValueKey('settings-category-background')),
        findsNothing,
      );
      expect(find.text('Notifications & background'), findsNothing);
      // The rest of the hub is untouched.
      expect(
        find.byKey(const ValueKey('settings-category-privacy')),
        findsOneWidget,
      );
    });
  });

  group('Ubuntu host management', () {
    Future<void> pumpHost(WidgetTester tester) async {
      final controller = await _chatController();
      addTearDown(controller.dispose);
      await tester.binding.setSurfaceSize(const Size(500, 2000));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await tester.pumpWidget(
        MaterialApp(home: HostManagementScreen(controller: controller)),
      );
      await tester.pumpAndSettle();
    }

    testWidgets('offers the USB port forward on Android', (tester) async {
      await pumpHost(tester);
      expect(find.byKey(const Key('host-command-adb-reverse')), findsOneWidget);
    });

    testWidgets('drops the adb command on desktop', (tester) async {
      onDesktop();
      await pumpHost(tester);
      expect(find.byKey(const Key('host-command-adb-reverse')), findsNothing);
      expect(find.textContaining('adb reverse'), findsNothing);
      // The host commands that do apply are untouched.
      expect(find.text('Service status'), findsOneWidget);
    });
  });

  group('the background live controller', () {
    Future<BackgroundLiveController> build({
      required List<String> calls,
    }) async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      return BackgroundLiveController(
        preferences: prefs,
        invoke: (method, [arguments]) async {
          calls.add(method);
          return <String, dynamic>{
            'active': true,
            'enabled': true,
            'notificationGranted': true,
          };
        },
      );
    }

    test('talks to the service on Android', () async {
      final calls = <String>[];
      final controller = await build(calls: calls);
      addTearDown(controller.dispose);
      await controller.restore();
      expect(calls, ['getStatus']);
      expect(controller.active, isTrue);
    });

    test('never reaches the channel on desktop', () async {
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
      final calls = <String>[];
      final controller = await build(calls: calls);
      addTearDown(controller.dispose);

      await controller.restore();
      await controller.refreshStatus();
      expect(await controller.setEnabled(true), isFalse);
      expect(
        await controller.showCodingAlert(
          kind: CodingAlertKind.complete,
          sessionID: 'session-1',
          key: 'k',
        ),
        isFalse,
      );
      expect(await controller.dismissCodingAlert('k'), isFalse);
      expect(await controller.consumeCodingAlertOpen(), isNull);

      expect(calls, isEmpty);
      expect(controller.active, isFalse);
      // No error is parked either: nothing failed, the feature is simply not
      // part of this build.
      expect(controller.lastError, isNull);
    });
  });

  group('the home-screen widget snapshot', () {
    Future<(WidgetSessionSnapshot, List<int>)> build() async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final refreshes = <int>[];
      return (
        WidgetSessionSnapshot(
          prefs: prefs,
          refreshNative: () async => refreshes.add(1),
        ),
        refreshes,
      );
    }

    test('writes and redraws on Android', () async {
      final (snapshot, refreshes) = await build();
      await snapshot.update(
        sessions: [Session(id: 'a')],
        busySessions: const {},
        connected: true,
        profileID: 'p',
      );
      expect(refreshes, hasLength(1));
    });

    test('writes nothing on desktop, where there is no widget', () async {
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
      final (snapshot, refreshes) = await build();
      await snapshot.update(
        sessions: [Session(id: 'a')],
        busySessions: const {},
        connected: true,
        profileID: 'p',
      );
      expect(refreshes, isEmpty);
      final prefs = await SharedPreferences.getInstance();
      expect(prefs.getString(WidgetSessionSnapshot.prefsKey), isNull);
    });
  });
}
