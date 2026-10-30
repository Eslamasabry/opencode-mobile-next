// Captures of the server switcher (vision slice, 2026-10-20,
// docs/qa/vision-server-switcher-2026-10-20/README.md): the real HomeScreen
// with the sheet opened from its server pill, the live aurora painted (still),
// real fonts and blurred shadows, no server. Phone 412x915 dp at 2x pixels,
// one full screen per image.
//
//   flutter test --concurrency=1 tool/capture/vision_server_switcher_test.dart
//   flutter test --concurrency=1 --dart-define=VISION_OUT=<dir> \
//     tool/capture/vision_server_switcher_test.dart
//
// Output: /home/eslam/Storage2/launch-footage/vision-server-switcher/<scene>.png
//
// The data is invented and plain: three saved servers. "Studio Mac" and
// "Home PC" are side connections (servers the Conversations list keeps open
// beside the one in use), so the app really holds their requests and their
// state; in the "watched" scene they are only watched by the attention
// monitor, which keeps no command and no way to reply.
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart'
    show builtinLinuxProvider;
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profile_monitor.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/desktop/shortcuts.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';

import '../../test/support/setup_capture_preferences.dart';
import '../../test/support/usage_ui_harness.dart' show usageAndroid;
import 'fixtures.dart';

const _out = String.fromEnvironment(
  'VISION_OUT',
  defaultValue: '/home/eslam/Storage2/launch-footage/vision-server-switcher',
);

/// What the other servers are to the app.
enum _Mode {
  /// Studio Mac asks to run a command; Home PC isn't answering. Both are
  /// side connections.
  side,

  /// The same two servers, only watched by the monitor.
  watched,

  /// The server in use isn't answering.
  currentDown,

  /// Allow once was tapped on Studio Mac's question.
  allowed,

  /// Studio Mac asks a question with options (it opens the chat).
  question,

  /// Only the server in use is saved.
  single,

  /// Studio Mac asks to run a command too long for its row: Open.
  longCommand,

  /// OpenCode inside the app is the server in use; Laptop is saved with
  /// nothing live about it.
  phoneCurrent,
}

enum _Scene {
  mainDark('main-dark'),
  mainLight('main-light', light: true),
  watched('watched-servers-dark', mode: _Mode.watched),
  currentDown('current-not-answering-dark', mode: _Mode.currentDown),
  allowed('allowed-undo-dark', mode: _Mode.allowed),
  question('question-opens-chat-dark', mode: _Mode.question),
  single('one-server-dark', mode: _Mode.single),
  phone('this-phone-current-dark', mode: _Mode.phoneCurrent),
  long('long-command-opens-chat-dark', mode: _Mode.longCommand),
  small('small-320-text2-dark', size: Size(320, 693), scale: 2),
  arabic('arabic-rtl-dark', locale: Locale('ar'));

  const _Scene(
    this.file, {
    this.light = false,
    this.mode = _Mode.side,
    this.size = const Size(412, 915),
    this.scale = 1,
    this.locale = const Locale('en'),
  });
  final String file;
  final bool light;
  final _Mode mode;
  final Size size;
  final double scale;
  final Locale locale;
}

final _laptop = ServerProfile(
  id: 'laptop',
  name: 'Laptop',
  baseUrl: 'http://192.168.1.20:4096',
);
final _phone = ServerProfile(
  id: 'phone',
  name: 'In-app Ubuntu',
  baseUrl: BuiltinLinux.serverUrl,
);
final _studio = ServerProfile(
  id: 'studio',
  name: 'Studio Mac',
  baseUrl: 'https://studio.example.test:4096',
);
final _homePc = ServerProfile(
  id: 'homepc',
  name: 'Home PC',
  baseUrl: 'http://192.168.1.30:4096',
);

class _Api extends CaptureApi {
  @override
  ServerCapabilities get capabilities =>
      const ServerCapabilities(permissionRequests: true, promptSteering: true);
}

/// Another server's own live connection, as the Conversations list keeps it.
class _Side extends CaptureController {
  _Side(super.store, this._profile);

  final ServerProfile _profile;

  @override
  ServerProfile? get profile => _profile;
}

/// The server in use, with its side connections and the monitor's view.
class _Main extends CaptureController {
  _Main(super.store, {required this.mode});

  final _Mode mode;
  _Side? studio;
  final retried = <String>[];
  _Monitor? _monitor;

  ChatFeedItem get _studioRow => ChatFeedItem(
    sessionID: 'ses_studio',
    title: 'Set up the landing page',
    directory: '/Users/me/site',
    projectName: 'site',
    isGit: true,
    status: ChatStatus.needsYou,
    lastActivity: DateTime.now().subtract(const Duration(minutes: 2)),
    sourceId: 'profile:studio',
    sourceLabel: 'Studio Mac',
  );

  bool get _sides =>
      mode == _Mode.side ||
      mode == _Mode.allowed ||
      mode == _Mode.question ||
      mode == _Mode.currentDown ||
      mode == _Mode.longCommand ||
      mode == _Mode.phoneCurrent;

  @override
  ProfileMonitor get profileMonitor =>
      _monitor ??= _Monitor(store, watched: mode == _Mode.watched);

  @override
  bool isProfileReadable(String id) => true;

  @override
  ChatFeedSnapshot chatFeed([ChatFeedFilter filter = ChatFeedFilter.all]) {
    final own = super.chatFeed(filter);
    if (!_sides || studio == null) return own;
    final row = _studioRow;
    return ChatFeedSnapshot(
      items: [if (chatFeedMatches(row, filter)) row, ...own.items],
      acrossProjects: own.acrossProjects,
      unreachableServers: const ['homepc'],
    );
  }

  @override
  ConnectionController? connectionForRow(ChatFeedItem item) =>
      item.sourceId == 'profile:studio' ? studio : super.connectionForRow(item);

  @override
  List<ChatListSource> get chatListSources => [
    ChatListSource(
      id: store.activeId!,
      name: 'Laptop',
      kind: ChatListSourceKind.openCode,
      shown: true,
      main: true,
      onThisPhone: false,
    ),
    if (_sides) ...[
      const ChatListSource(
        id: 'studio',
        name: 'Studio Mac',
        kind: ChatListSourceKind.openCode,
        shown: true,
        onThisPhone: false,
      ),
      const ChatListSource(
        id: 'homepc',
        name: 'Home PC',
        kind: ChatListSourceKind.openCode,
        shown: true,
        onThisPhone: false,
        unreachable: true,
      ),
    ],
  ];

  @override
  Future<void> retryChatListSource(String id) async => retried.add(id);
}

/// The attention monitor: in [watched] it has seen Studio Mac wait on the
/// person (no command) and Home PC fail, with its next check ahead.
class _Monitor extends ProfileMonitor {
  _Monitor(ProfileStore store, {required this.watched})
    : super(
        store: store,
        createGateway: (_) => throw UnimplementedError(),
        isReadable: (_) => true,
        networkWifi: () async => null,
        alert: (_, _, _, _) async => false,
        dismiss: (_) async => false,
      );

  final bool watched;

  @override
  ProfileAttentionSnapshot snapshotFor(String id) {
    if (!watched) {
      return ProfileAttentionSnapshot(
        profileID: id,
        status: ProfileMonitorStatus.disabled,
      );
    }
    final now = DateTime.now();
    return switch (id) {
      'studio' => ProfileAttentionSnapshot(
        profileID: id,
        status: ProfileMonitorStatus.current,
        complete: true,
        checkedAt: now.subtract(const Duration(seconds: 40)),
        runningCount: 0,
        requests: const [
          MonitoredRequest(
            id: 'per_1',
            sessionID: 'ses_studio',
            kind: MonitoredRequestKind.permission,
          ),
        ],
      ),
      'homepc' => ProfileAttentionSnapshot(
        profileID: id,
        status: ProfileMonitorStatus.unavailable,
        checkedAt: now.subtract(const Duration(hours: 3)),
        nextCheckAt: now.add(const Duration(minutes: 4, seconds: 20)),
      ),
      _ => ProfileAttentionSnapshot(
        profileID: id,
        status: ProfileMonitorStatus.disabled,
      ),
    };
  }
}

/// OpenCode inside the app: installed and running.
class _Linux extends BuiltinLinux {
  @override
  Future<BuiltinLinuxStatus> status() async => const BuiltinLinuxStatus(
    installed: true,
    phase: BuiltinLinuxPhase.ready,
    serverRunning: true,
    bytesUsed: 734003200,
  );
}

PermissionRequest _npmInstall() => PermissionRequest(
  id: 'per_npm',
  sessionID: 'ses_studio',
  permission: 'bash',
  patterns: const ['npm install'],
  metadata: const {
    'command': 'npm install',
    'description': 'Install the project dependencies',
  },
);

PermissionRequest _longCommand() => PermissionRequest(
  id: 'per_npm',
  sessionID: 'ses_studio',
  permission: 'bash',
  metadata: const {
    'command': 'npm run build && npx playwright test --project=chromium',
  },
);

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

Future<_Main> _controller(_Mode mode) async {
  final prefs = await setupCapturePreferences();
  final profiles = switch (mode) {
    _Mode.single => [_laptop],
    _Mode.phoneCurrent => [_phone, _laptop, _studio, _homePc],
    _ => [_laptop, _studio, _homePc],
  };
  final store = SeededProfileStore(prefs: prefs, seeded: profiles);
  final api = _Api();
  final main = _Main(store, mode: mode)
    ..api = api
    ..repository = CaptureRepository()
    ..status = mode == _Mode.currentDown
        ? StreamStatus.disconnected
        : StreamStatus.connected
    ..directory = projectDirectory
    ..sessionsById = Map.of(api.sessionsById)
    ..busySessions = Set.of(api.busy);
  if (mode == _Mode.currentDown) {
    main.lastError = 'Cannot reach http://192.168.1.20:4096';
  }
  if (main._sides) {
    final sideStore = SeededProfileStore(prefs: prefs, seeded: [_studio]);
    final studio = _Side(sideStore, _studio)
      ..api = _Api()
      ..repository = CaptureRepository()
      ..status = StreamStatus.connected
      ..directory = '/Users/me/site'
      ..sessionsById = {
        'ses_studio': Session(
          id: 'ses_studio',
          directory: '/Users/me/site',
          title: 'Set up the landing page',
        ),
      }
      ..busySessions = {};
    if (mode == _Mode.question) {
      studio.questions = {
        'que_1': PendingQuestion(
          id: 'que_1',
          sessionID: 'ses_studio',
          prompts: const [
            QuestionPrompt(
              title: 'Branch',
              question: 'Which branch should I deploy?',
              multiple: false,
              custom: true,
              choices: [
                QuestionChoice(label: 'main', description: ''),
                QuestionChoice(label: 'develop', description: ''),
              ],
            ),
          ],
        ),
      };
    } else if (mode == _Mode.longCommand) {
      studio.permissions = {'per_npm': _longCommand()};
    } else {
      studio.permissions = {'per_npm': _npmInstall()};
    }
    main.studio = studio;
  }
  return main;
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
    testWidgets(scene.file, (tester) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      // OpenCode inside the app exists only on Android.
      if (scene.mode == _Mode.phoneCurrent) usageAndroid();
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
      final controller = await _controller(scene.mode);
      final key = GlobalKey();
      await tester.pumpWidget(
        captureApp(
          home: AppShortcutScope(
            signals: AppShortcutSignals(),
            perform: (_) {},
            child: const HomeScreen(),
          ),
          boundaryKey: key,
          controller: controller,
          light: scene.light,
          aboveNavigator: (child) => Builder(
            builder: (context) {
              Widget body = ProviderScope(
                overrides: [builtinLinuxProvider.overrideWithValue(_Linux())],
                child: child,
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
      // Opened the way a person opens it: the server pill on the home.
      await tester.tap(find.byKey(const ValueKey('server-switcher-button')));
      await _settle(tester, frames: 12);
      if (scene.mode == _Mode.allowed) {
        await tester.tap(
          find.byKey(const ValueKey('server-switcher-allow-per_npm')),
        );
        await _settle(tester, frames: 4);
      }
      expect(tester.takeException(), isNull);
      await writePng(
        '$_out/${scene.file}.png',
        await capturePng(tester, key, pixelRatio: 2),
      );
      await tester.pumpWidget(const SizedBox.shrink());
      controller.studio?.dispose();
      controller.dispose();
      await tester.pump(const Duration(minutes: 2));
      debugDisableShadows = true;
      debugDefaultTargetPlatformOverride = null;
    });
  }
}
