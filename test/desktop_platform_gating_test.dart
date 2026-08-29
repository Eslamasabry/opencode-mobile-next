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
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/guide_screen.dart';
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
      expect(find.byKey(const ValueKey('welcome-connect-card')), findsOneWidget);
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
      expect(find.text('Remote machine (LAN)'), findsOneWidget);
    });
  });

  group('the setup guide', () {
    // The guide is a lazy ListView; a tall surface builds all of it so
    // "findsNothing" means absent rather than merely unbuilt.
    Future<void> pumpGuide(WidgetTester tester) async {
      await tester.binding.setSurfaceSize(const Size(800, 3000));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await tester.pumpWidget(const MaterialApp(home: GuideScreen()));
    }

    testWidgets('documents both paths on Android', (tester) async {
      await pumpGuide(tester);

      expect(
        find.byKey(const ValueKey('guide-termux-section')),
        findsOneWidget,
      );
      expect(find.textContaining('Android Keystore'), findsOneWidget);
      expect(find.text('1 · REMOTE MACHINE'), findsOneWidget);
    });

    testWidgets('drops the Termux path on desktop', (tester) async {
      onDesktop();
      await pumpGuide(tester);

      expect(find.byKey(const ValueKey('guide-termux-section')), findsNothing);
      expect(find.textContaining('Termux'), findsNothing);
      expect(find.textContaining('Android Keystore'), findsNothing);
      // The remote-server instructions, the only path a desktop user has,
      // are still there and no longer numbered as one of two.
      expect(find.text('RUNNING THE SERVER'), findsOneWidget);
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

      expect(
        find.byKey(const Key('termux-setup-unsupported')),
        findsOneWidget,
      );
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
        find.byTooltip('Prompt tools: commands, attach, voice'),
        findsOneWidget,
      );
      await openTools(tester);
      expect(find.byKey(const Key('composer-tool-voice')), findsOneWidget);
    });

    testWidgets('drop voice input on desktop', (tester) async {
      onDesktop();
      await pumpChat(tester);
      // The collapsed button no longer advertises a tool that is not there.
      expect(find.byTooltip('Prompt tools: commands, attach'), findsOneWidget);
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

    testWidgets(
      'hides the foreground-service category on desktop',
      (tester) async {
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
      },
    );
  });
}
