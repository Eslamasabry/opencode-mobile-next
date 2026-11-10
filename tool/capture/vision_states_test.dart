// Captures of the offline and empty states (vision slice, 2026-10-20,
// docs/qa/vision-states-2026-10-20/README.md): the real HomeScreen,
// ChatScreen, Search, Projects and AI Team home, opened the way a person
// opens them, the live aurora painted (still), real fonts and blurred
// shadows, no server. Phone 412x915 dp at 2x pixels, one full screen per
// image.
//
//   flutter test --concurrency=1 tool/capture/vision_states_test.dart
//   flutter test --concurrency=1 --dart-define=VISION_OUT=<dir> \
//     --dart-define=VISION_ONLY=empty-dark tool/capture/vision_states_test.dart
//
// Output: /home/eslam/Storage2/launch-footage/vision-states/<scene>.png
//
// The data is invented and plain: "Laptop" is the server in use; offline,
// its link dropped and its own retry timer tries again in 12 s.
import 'dart:async';
import 'dart:io';

import 'package:clock/clock.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart'
    show builtinLinuxProvider;
import 'package:opencode_mobile/domain/connection_status.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/desktop/shortcuts.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/projects_screen.dart';
import 'package:opencode_mobile/ui/screens/team/team_home_screen.dart';
import 'package:opencode_mobile/ui/widgets/app_connection_status.dart'
    show AppConditionsScope;
import 'package:opencode_mobile/ui/widgets/connection_status_banner.dart'
    show connectionKitStatus;

import '../../test/support/setup_capture_preferences.dart';
import '../../test/support/team_chat_fixture.dart' show bootTeam, teamClock;
import 'fixtures.dart';

const _out = String.fromEnvironment(
  'VISION_OUT',
  defaultValue: '/home/eslam/Storage2/launch-footage/vision-states',
);

/// Only these scenes (comma separated), when given.
const _only = String.fromEnvironment('VISION_ONLY');

enum _Screen { home, chat, search, projects, team }

enum _Scene {
  emptyDark('empty-dark', phone: true),
  emptyLight('empty-light', light: true, phone: true),
  offlineChatDark('offline-chat-dark', screen: _Screen.chat, offline: true),
  offlineChatLight(
    'offline-chat-light',
    screen: _Screen.chat,
    offline: true,
    light: true,
  ),
  offlineHome('offline-home-dark', offline: true, sessions: true),
  nothingRunning('nothing-running-dark', sessions: true, running: true),
  search('search-no-results-dark', screen: _Screen.search, sessions: true),
  projects('projects-empty-dark', screen: _Screen.projects),
  team('team-empty-dark', screen: _Screen.team),
  small('small-320-text2-dark', phone: true, size: Size(320, 693), scale: 2),
  smallOffline(
    'small-320-text2-offline-chat-dark',
    screen: _Screen.chat,
    offline: true,
    size: Size(320, 693),
    scale: 2,
  ),
  arabic('arabic-rtl-dark', phone: true, locale: Locale('ar')),
  arabicOffline(
    'arabic-rtl-offline-chat-dark',
    screen: _Screen.chat,
    offline: true,
    locale: Locale('ar'),
  );

  const _Scene(
    this.file, {
    this.screen = _Screen.home,
    this.light = false,
    this.offline = false,
    this.sessions = false,
    this.running = false,
    this.phone = false,
    this.size = const Size(412, 915),
    this.scale = 1,
    this.locale = const Locale('en'),
  });
  final String file;
  final _Screen screen;
  final bool light;
  final bool offline;
  final bool sessions;
  final bool running;

  /// The only server is this phone's own (a loopback address), so no
  /// computer is saved.
  final bool phone;
  final Size size;
  final double scale;
  final Locale locale;
}

final _laptop = ServerProfile(
  id: 'laptop',
  name: 'Laptop',
  baseUrl: 'http://192.168.1.20:4096',
);

final _thisPhone = ServerProfile(
  id: 'phone',
  name: 'This phone',
  baseUrl: 'http://127.0.0.1:4096',
);

class _Api extends CaptureApi {
  _Api({required bool sessions}) {
    if (!sessions) sessionsById = {};
    busy = {};
  }

  @override
  ServerCapabilities get capabilities => const ServerCapabilities(
    permissionRequests: true,
    promptSteering: true,
    projectManagement: true,
  );

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await messages(id) : const []);
}

class _Repository extends CaptureRepository {
  @override
  Future<List<WorkspaceProject>> listProjects() async => [];
}

/// The server in use; [offline] its link dropped, its own retry timer set.
class _Controller extends CaptureController {
  _Controller(super.store, {required this.offline})
    : _retryAt = clock.now().add(const Duration(milliseconds: 12400));

  final bool offline;
  final DateTime _retryAt;

  @override
  ConnectionStatusSnapshot get connectionStatus => offline
      ? ConnectionStatusSnapshot(
          phase: ConnectionStatusPhase.notAnswering,
          profileId: _laptop.id,
          serverName: _laptop.name,
          since: DateTime.now().subtract(const Duration(seconds: 40)),
          nextAttemptAt: _retryAt,
        )
      : super.connectionStatus;

  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async =>
      repository;
}

class _Linux extends BuiltinLinux {
  @override
  Future<BuiltinLinuxStatus> status() async =>
      const BuiltinLinuxStatus(installed: false, phase: BuiltinLinuxPhase.idle);
}

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

Future<void> _settle(WidgetTester tester, {int frames = 10}) async {
  for (var i = 0; i < frames; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

List<MessageWithParts> _transcript() {
  final now = DateTime.now().millisecondsSinceEpoch;
  Part text(String id, String message, String words) =>
      Part(id: id, messageID: message, type: 'text', text: words);
  return [
    MessageWithParts(
      info: messageInfo('msg_user', 'user', created: now - 600000),
      parts: [
        text(
          'p1',
          'msg_user',
          'Make the checkout test stop failing on slow phones.',
        ),
      ],
    ),
    MessageWithParts(
      info: messageInfo(
        'msg_reply',
        'assistant',
        created: now - 590000,
        completed: now - 420000,
      ),
      parts: [
        text(
          'p2',
          'msg_reply',
          'The test waited a fixed 2 seconds for the payment sheet. It now '
              'waits for the sheet itself, so a slow phone no longer fails it.',
        ),
      ],
    ),
  ];
}

Future<_Controller> _controller(_Scene scene) async {
  final prefs = await setupCapturePreferences();
  final store = SeededProfileStore(
    prefs: prefs,
    seeded: [scene.phone ? _thisPhone : _laptop],
  );
  final api = _Api(sessions: scene.sessions || scene.screen == _Screen.chat)
    ..messagesHandler = (_) async => _transcript();
  return _Controller(store, offline: scene.offline)
    ..api = api
    ..repository = _Repository()
    ..status = scene.offline
        ? StreamStatus.reconnecting
        : StreamStatus.connected
    ..directory = projectDirectory
    ..sessionsById = Map.of(api.sessionsById)
    ..busySessions = {};
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadCaptureFonts();
    // Arabic falls back to Noto, as on a device.
    final arabic = FontLoader('Noto Sans Arabic');
    for (final face in ['Regular', 'Bold']) {
      final bytes = File(
        'test/fixtures/fonts/NotoSansArabic-$face.ttf',
      ).readAsBytesSync();
      arabic.addFont(Future.value(ByteData.sublistView(bytes)));
    }
    await arabic.load();
    final sans = FontLoader('sans-serif');
    for (final face in ['Regular', 'Medium', 'Bold']) {
      final bytes = File(
        'tool/capture/fonts/Roboto-$face.ttf',
      ).readAsBytesSync();
      sans.addFont(Future.value(ByteData.sublistView(bytes)));
    }
    await sans.load();
  });
  setUp(() {
    KitLiveBackground.paintWithoutLoops = true;
    KitMotion.loops = false;
  });
  tearDown(() {
    KitLiveBackground.paintWithoutLoops = false;
    KitLiveBackground.frozenSeconds = null;
  });

  for (final scene in _Scene.values) {
    if (_only.isNotEmpty && !_only.split(',').contains(scene.file)) continue;
    testWidgets(scene.file, (tester) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      // Blurred shadows as a phone draws them (the test binding flattens
      // them); put back before the test ends.
      debugDisableShadows = false;
      _mockSecureStorage(tester);
      tester.view.physicalSize = scene.size * 2;
      tester.view.devicePixelRatio = 2;
      tester.view.padding = const FakeViewPadding(top: 64, bottom: 48);
      addTearDown(tester.view.reset);
      if (scene.locale.languageCode != 'en') {
        tester.platformDispatcher.localesTestValue = [scene.locale];
        addTearDown(tester.platformDispatcher.clearLocalesTestValue);
      }
      KitLiveBackground.frozenSeconds = 21;
      final controller = await _controller(scene);
      final key = GlobalKey();
      final navigator = GlobalKey<NavigatorState>();
      // Search as the shell opens it: the phone's Search button, a few of
      // the app's own commands.
      void perform(Intent intent) {
        if (intent is! OpenCommandPaletteIntent) return;
        unawaited(
          showCommandPalette(navigator.currentContext!, [
            for (final (label, icon) in [
              ('New conversation', AppIconography.add),
              ('Inbox', AppIconography.inbox),
              ('Files', AppIconography.files),
              ('Settings', AppIconography.settings),
            ])
              DesktopCommand(label: label, icon: icon, onInvoke: () {}),
          ]),
        );
      }

      final Widget home = switch (scene.screen) {
        _Screen.chat => const ChatScreen(sessionID: checkoutSessionID),
        _Screen.projects => ProjectsScreen(
          controller: controller,
          selectedProjectID: null,
        ),
        _Screen.team => TeamHomeScreen(
          controller: (await tester.runAsync(
            () => bootTeam(runs: const [], work: const [], gates: const []),
          ))!.$1,
          now: () => teamClock,
        ),
        _ => AppShortcutScope(
          signals: AppShortcutSignals(),
          perform: perform,
          child: const HomeScreen(),
        ),
      };
      await tester.pumpWidget(
        captureApp(
          home: home,
          boundaryKey: key,
          navigatorKey: navigator,
          controller: controller,
          light: scene.light,
          aboveNavigator: (child) => Builder(
            builder: (context) {
              // The app-wide connection line, as main.dart hosts it above
              // the navigator.
              Widget body = ProviderScope(
                overrides: [builtinLinuxProvider.overrideWithValue(_Linux())],
                child: ListenableBuilder(
                  listenable: controller,
                  builder: (context, _) => AppConditionsScope(
                    conditions: [connectionKitStatus(context, controller)],
                    child: child,
                  ),
                ),
              );
              if (scene.locale.languageCode != 'en') {
                body = Theme(
                  data: AppTheme.forLocale(Theme.of(context), scene.locale),
                  child: body,
                );
              }
              if (scene.scale != 1) {
                body = MediaQuery(
                  data: MediaQuery.of(
                    context,
                  ).copyWith(textScaler: TextScaler.linear(scene.scale)),
                  child: body,
                );
              }
              return body;
            },
          ),
        ),
      );
      await _settle(tester);
      if (scene.running) {
        final chip = find.byKey(const ValueKey('chats-filter-running'));
        await tester.ensureVisible(chip);
        await tester.tap(chip);
        await _settle(tester);
      }
      if (scene.screen == _Screen.search) {
        await tester.tap(find.byKey(const ValueKey('home-shell-search')));
        await _settle(tester);
        await tester.enterText(
          find.descendant(
            of: find.byKey(const ValueKey('command-palette-query')),
            matching: find.byType(EditableText),
          ),
          'deploy staging',
        );
        await _settle(tester);
      }
      expect(tester.takeException(), isNull);
      await writePng(
        '$_out/${scene.file}.png',
        await capturePng(tester, key, pixelRatio: 2),
      );
      await tester.pumpWidget(const SizedBox.shrink());
      controller.dispose();
      await tester.pump(const Duration(minutes: 2));
      debugDisableShadows = true;
      debugDefaultTargetPlatformOverride = null;
    });
  }
}
