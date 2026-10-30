// Captures of the Inbox (vision slice, 2026-10-20,
// docs/qa/vision-inbox-2026-10-20/README.md): the real HomeScreen, the
// Inbox opened the way a person opens it (the "Needs you" chip on
// Conversations), the live aurora painted (still), real fonts and blurred
// shadows, no server. Phone 412x915 dp at 2x pixels, one full screen per
// image.
//
//   flutter test --concurrency=1 tool/capture/vision_inbox_test.dart
//   flutter test --concurrency=1 --dart-define=VISION_OUT=<dir> \
//     --dart-define=VISION_ONLY=main-dark tool/capture/vision_inbox_test.dart
//
// Output: /home/eslam/Storage2/launch-footage/vision-inbox/<scene>.png
//
// The data is invented and plain: the server in use ("Laptop") waits on a
// command and a question; "Studio Mac" is a side connection (a server the
// Conversations list keeps open beside it) that waits on a command; "Home
// PC" is only watched by the attention monitor, which keeps no command and
// no way to reply.
import 'dart:io';

import 'package:clock/clock.dart';

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
import 'package:opencode_mobile/ui/screens/chat/permission_sheet.dart'
    show requestFirstSeen;
import 'package:opencode_mobile/ui/screens/home_screen.dart';

import '../../test/support/setup_capture_preferences.dart';
import 'fixtures.dart';

const _out = String.fromEnvironment(
  'VISION_OUT',
  defaultValue: '/home/eslam/Storage2/launch-footage/vision-inbox',
);

/// Only this scene, when given (a pass renders the main one first).
const _only = String.fromEnvironment('VISION_ONLY');

/// Also capture Conversations before the Inbox opens, as home-SCENE.png.
const _home = bool.fromEnvironment('VISION_HOME');

/// What waits on the person.
enum _Mode {
  /// Three servers, four things waiting.
  waiting,

  /// Allow once was tapped on the first row: held, with Undo.
  allowed,

  /// Nothing waits; two conversations finished and were not opened yet.
  empty,

  /// Nothing waits on the servers the app can read; Studio Mac isn't
  /// answering.
  emptyNotAnswering,

  /// Only the server in use, one command waiting.
  single,
}

enum _Scene {
  mainDark('main-dark'),
  mainLight('main-light', light: true),
  allowed('allowed-undo-dark', mode: _Mode.allowed),
  empty('empty-finished-dark', mode: _Mode.empty),
  emptyLight('empty-finished-light', mode: _Mode.empty, light: true),
  notAnswering('empty-not-answering-dark', mode: _Mode.emptyNotAnswering),
  single('one-server-dark', mode: _Mode.single),
  small('small-320-text2-dark', size: Size(320, 693), scale: 2),
  arabic('arabic-rtl-dark', locale: Locale('ar'));

  const _Scene(
    this.file, {
    this.light = false,
    this.mode = _Mode.waiting,
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

const _landingID = 'ses_landing';

int _ago(Duration age) => DateTime.now().subtract(age).millisecondsSinceEpoch;

Session _session(String id, String title, Duration age, {String? directory}) {
  final at = _ago(age);
  return Session(
    id: id,
    title: title,
    directory: directory ?? projectDirectory,
    time: SessionTime(created: at - 20 * 60 * 1000, updated: at),
  );
}

class _Api extends CaptureApi {
  _Api() {
    sessionsById = {
      checkoutSessionID: _session(
        checkoutSessionID,
        'Fix flaky checkout test',
        const Duration(minutes: 22),
      ),
      darkModeSessionID: _session(
        darkModeSessionID,
        'Add dark mode to settings',
        const Duration(minutes: 6),
      ),
      paymentsSessionID: _session(
        paymentsSessionID,
        'Explain the payments module',
        const Duration(hours: 2),
      ),
      ciSessionID: _session(
        ciSessionID,
        'Speed up the CI pipeline',
        const Duration(minutes: 40),
      ),
    };
    busy = {};
  }

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

/// The server in use, with its side connection and the monitor's view.
class _Main extends CaptureController {
  _Main(super.store, {required this.mode});

  final _Mode mode;
  _Side? studio;
  _Monitor? _monitor;

  bool get _sides => mode != _Mode.single;

  ChatFeedItem get _studioRow => ChatFeedItem(
    sessionID: _landingID,
    title: 'Set up the landing page',
    directory: '/Users/me/site',
    projectName: 'site',
    isGit: true,
    status: mode == _Mode.waiting || mode == _Mode.allowed
        ? ChatStatus.needsYou
        : ChatStatus.idle,
    lastActivity: DateTime.now().subtract(const Duration(minutes: 48)),
    sourceId: 'profile:studio',
    sourceLabel: 'Studio Mac',
  );

  @override
  ProfileMonitor get profileMonitor => _monitor ??= _Monitor(
    store,
    watched: mode == _Mode.waiting || mode == _Mode.allowed,
  );

  @override
  bool isProfileReadable(String id) => true;

  @override
  bool finishedUnseen(String sessionID) =>
      (mode == _Mode.empty || mode == _Mode.emptyNotAnswering) &&
      (sessionID == darkModeSessionID || sessionID == ciSessionID);

  @override
  ChatFeedSnapshot chatFeed([ChatFeedFilter filter = ChatFeedFilter.all]) {
    final own = super.chatFeed(filter);
    if (!_sides || studio == null) return own;
    final down = mode == _Mode.emptyNotAnswering;
    final row = _studioRow;
    return ChatFeedSnapshot(
      items: [if (!down && chatFeedMatches(row, filter)) row, ...own.items],
      acrossProjects: own.acrossProjects,
      unreachableServers: down ? const ['studio'] : const [],
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
    if (_sides)
      ChatListSource(
        id: 'studio',
        name: 'Studio Mac',
        kind: ChatListSourceKind.openCode,
        shown: true,
        onThisPhone: false,
        unreachable: mode == _Mode.emptyNotAnswering,
      ),
  ];

  @override
  Future<void> retryChatListSource(String id) async {}
}

/// The attention monitor: in [watched] it has seen Home PC wait on the
/// person (no command, no reply path).
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
    if (!watched || id != 'homepc') {
      return ProfileAttentionSnapshot(
        profileID: id,
        status: ProfileMonitorStatus.disabled,
      );
    }
    return ProfileAttentionSnapshot(
      profileID: id,
      status: ProfileMonitorStatus.current,
      complete: true,
      checkedAt: DateTime.now().subtract(const Duration(seconds: 40)),
      runningCount: 0,
      requests: const [
        MonitoredRequest(
          id: 'per_docs',
          sessionID: 'ses_docs',
          kind: MonitoredRequestKind.permission,
          title: 'Update the API docs',
          directory: '/home/me/api',
        ),
      ],
    );
  }
}

class _Linux extends BuiltinLinux {
  @override
  Future<BuiltinLinuxStatus> status() async =>
      const BuiltinLinuxStatus(installed: false, phase: BuiltinLinuxPhase.idle);
}

PermissionRequest _bash(String id, String sessionID, String command) =>
    PermissionRequest(
      id: id,
      sessionID: sessionID,
      permission: 'bash',
      patterns: [command],
      metadata: {'command': command},
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
  final profiles = mode == _Mode.single
      ? [_laptop]
      : [_laptop, _studio, _homePc];
  final store = SeededProfileStore(prefs: prefs, seeded: profiles);
  final api = _Api();
  final main = _Main(store, mode: mode)
    ..api = api
    ..repository = CaptureRepository()
    ..status = StreamStatus.connected
    ..directory = projectDirectory
    ..sessionsById = Map.of(api.sessionsById)
    ..busySessions = {};
  final waiting =
      mode == _Mode.waiting || mode == _Mode.allowed || mode == _Mode.single;
  if (waiting) {
    main.permissions = {
      'per_test': _bash('per_test', checkoutSessionID, checkoutCommand),
    };
  }
  if (mode == _Mode.waiting || mode == _Mode.allowed) {
    main.questions = {
      'que_branch': PendingQuestion(
        id: 'que_branch',
        sessionID: darkModeSessionID,
        prompts: const [
          QuestionPrompt(
            title: 'Branch',
            question: 'Which branch should I use?',
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
  }
  if (main._sides) {
    final sideStore = SeededProfileStore(prefs: prefs, seeded: [_studio]);
    final studio = _Side(sideStore, _studio)
      ..api = _Api()
      ..repository = CaptureRepository()
      ..status = StreamStatus.connected
      ..directory = '/Users/me/site'
      ..sessionsById = {
        _landingID: _session(
          _landingID,
          'Set up the landing page',
          const Duration(minutes: 48),
          directory: '/Users/me/site',
        ),
      }
      ..busySessions = {};
    if (mode == _Mode.waiting || mode == _Mode.allowed) {
      studio.permissions = {
        'per_npm': _bash('per_npm', _landingID, 'npm install'),
      };
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
      final controller = await _controller(scene.mode);
      // First seen on this phone a few minutes ago: the rows' "waiting
      // 4 min" (the phone's own clock; no request carries its ask time).
      withClock(
        Clock.fixed(clock.now().subtract(const Duration(minutes: 4))),
        () {
          for (final owner in [controller, ?controller.studio]) {
            for (final id in owner.permissions.keys) {
              requestFirstSeen(owner, id);
            }
          }
        },
      );
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
      // Conversations first, with its amber "2 need you" line.
      if (_home) {
        await writePng(
          '$_out/home-${scene.file}.png',
          await capturePng(tester, key, pixelRatio: 2),
        );
      }
      // Opened the way a person opens it: the amber "2 need you" line under
      // the Conversations title.
      final chip = find.byKey(const ValueKey('chats-needs-you'));
      await tester.ensureVisible(chip);
      await tester.tap(chip);
      await _settle(tester, frames: 12);
      if (scene.mode == _Mode.allowed) {
        await tester.tap(find.byKey(const Key('permission-card-allow')).first);
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
