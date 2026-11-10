// Captures of the teaching system, "Stop after this step" made
// discoverable, and the bigger Settings gear (2026-10-21). The real screens
// at 412x915 dp, 2x pixels, dark, real fonts, blurred shadows, no server.
// One full phone screen per image.
//
//   TZ=UTC flutter test --concurrency=1 tool/capture/stopgear_test.dart
//   ... --dart-define=VISION_PASS=pass2 (file prefix)
//
// Output: /home/eslam/vision-assets/out/stopgear/renders/<pass>-<scene>.png
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart'
    show builtinLinuxProvider;
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_iconography.dart';
import 'package:opencode_mobile/ui/desktop/shortcuts.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/settings/tips_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/teaching/teaching.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

import '../../test/goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'fixtures.dart';

const _out = String.fromEnvironment(
  'VISION_OUT',
  defaultValue: '/home/eslam/vision-assets/out/stopgear/renders',
);

/// The pass the images belong to ("pass1"), the file name's prefix.
const _pass = String.fromEnvironment('VISION_PASS', defaultValue: 'pass1');

/// Only these scenes (comma separated), when given.
const _only = String.fromEnvironment('VISION_ONLY');

const _dir = '/root/projects/settings-demo';
const _sid = 'ses_tea';

bool _wanted(String scene) => _only.isEmpty || _only.split(',').contains(scene);

String _file(String scene) => '$_out/$_pass-$scene-dark.png';

class _Api extends CaptureApi {
  _Api({this.inbox = false});

  final bool inbox;

  @override
  ServerCapabilities get capabilities => ServerCapabilities(
    permissionRequests: true,
    persistentPermissionGrants: true,
    promptSteering: true,
    inbox: inbox,
  );

  /// The stop note, once "Stop after this step" was sent.
  bool noteWaits = false;

  @override
  Future<List<Api2InboxItem>> inboxItems(String sessionID) async => [
    if (noteWaits)
      Api2InboxItem(
        id: 'msg_note',
        sessionID: sessionID,
        type: 'user',
        payload: const {
          'text':
              'Please stop after the step you are on now and wait for me '
              'before doing anything else.',
        },
        delivery: Api2Delivery.steer,
      ),
  ];

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await messages(id) : const []);
}

Future<void> _frames(WidgetTester tester, [int n = 12]) async {
  await tester.runAsync(
    () => Future<void>.delayed(const Duration(milliseconds: 200)),
  );
  for (var i = 0; i < n; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

Future<SharedPreferences> _prefs() async {
  SharedPreferencesStorePlatform.instance =
      InMemorySharedPreferencesStore.empty();
  final prefs = await SharedPreferences.getInstance();
  await prefs.clear();
  // Past the first reply: tips may show.
  await prefs.setBool('oc.nudges.firstReplySeen', true);
  return prefs;
}

CaptureController _controller(SharedPreferences prefs, _Api api) {
  final controller = CaptureController(
    SeededProfileStore(
      prefs: prefs,
      seeded: [
        ServerProfile(
          id: 'laptop',
          name: 'Laptop',
          baseUrl: 'http://192.168.1.20:4096',
        ),
      ],
    ),
  );
  controller
    ..api = api
    ..repository = CaptureRepository()
    ..status = StreamStatus.connected
    ..directory = _dir
    ..sessionsById = Map.of(api.sessionsById)
    ..busySessions = Set.of(api.busy);
  return controller;
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

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadKitGalleryFonts);
  setUp(() {
    KitLiveBackground.paintWithoutLoops = true;
    KitMotion.loops = false;
  });
  tearDown(() {
    KitLiveBackground.paintWithoutLoops = false;
    KitLiveBackground.frozenSeconds = null;
    KitMotion.loops = true;
  });

  void phone(WidgetTester tester) {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    debugDisableShadows = false;
    tester.view.physicalSize = const Size(412, 915) * 2;
    tester.view.devicePixelRatio = 2;
    tester.view.padding = const FakeViewPadding(top: 64, bottom: 48);
    addTearDown(tester.view.reset);
    KitLiveBackground.frozenSeconds = 21;
  }

  Future<void> done(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(seconds: 5));
    debugDisableShadows = true;
    debugDefaultTargetPlatformOverride = null;
  }

  Future<void> shoot(WidgetTester tester, GlobalKey key, String scene) async {
    expect(tester.takeException(), isNull);
    await writePng(_file(scene), await capturePng(tester, key, pixelRatio: 2));
  }

  // ---- Help › Tips and the Appearance tutorial ---------------------------

  for (final scene in ['tips-page', 'tips-replay']) {
    if (!_wanted(scene)) continue;
    testWidgets(scene, (tester) async {
      phone(tester);
      final prefs = await _prefs();
      final controller = _controller(prefs, _Api());
      addTearDown(controller.dispose);
      final key = GlobalKey();
      await tester.pumpWidget(
        captureApp(
          home: TipsScreen(controller: controller),
          boundaryKey: key,
          controller: controller,
        ),
      );
      await _frames(tester);
      if (scene == 'tips-replay') {
        await tester.tap(
          find.byKey(const ValueKey('tips-show-me-${TeachingIds.sendLater}')),
        );
        await _frames(tester, 4);
      }
      await shoot(tester, key, scene);
      await done(tester);
    });
  }

  for (final scene in ['tour-offer', 'tour-step']) {
    if (!_wanted(scene)) continue;
    testWidgets(scene, (tester) async {
      phone(tester);
      final prefs = await _prefs();
      final controller = _controller(prefs, _Api());
      addTearDown(controller.dispose);
      final key = GlobalKey();
      await tester.pumpWidget(
        captureApp(
          home: AppearanceSettingsScreen(controller: controller),
          boundaryKey: key,
          controller: controller,
        ),
      );
      await _frames(tester);
      if (scene == 'tour-step') {
        await tester.tap(find.byKey(const ValueKey('kit-tour-offer-start')));
        await _frames(tester, 6);
        await tester.tap(find.byKey(const ValueKey('kit-tour-next')));
        await _frames(tester, 6);
      }
      await shoot(tester, key, scene);
      await done(tester);
    });
  }

  // ---- Stop after this step ----------------------------------------------

  Session session(int now) => Session(
    id: _sid,
    title: 'Create and read hello.txt tea notes',
    directory: _dir,
    time: SessionTime(created: now - 60000, updated: now - 20000),
  );

  List<MessageWithParts> running(int now) => [
    MessageWithParts(
      info: messageInfo('msg_user', 'user', created: now - 42000),
      parts: [
        Part(
          id: 'part_prompt',
          messageID: 'msg_user',
          type: 'text',
          text:
              'Create a file hello.txt with two lines about tea, then read '
              'it back',
        ),
      ],
    ),
    MessageWithParts(
      info: messageInfo('msg_reply', 'assistant', created: now - 30000),
      parts: [
        Part(
          id: 'part_words',
          messageID: 'msg_reply',
          type: 'text',
          text: 'Writing the two lines now, then I will read it back.',
        ),
        Part(
          id: 'call_edit',
          messageID: 'msg_reply',
          callID: 'call_edit',
          type: 'tool',
          toolName: 'edit',
          toolState: ToolState.fromJson({
            'status': 'running',
            'input': {'filePath': '$_dir/hello.txt'},
          }, toolName: 'edit'),
        ),
      ],
    ),
  ];

  for (final scene in ['stop-tip', 'steps-view', 'pending']) {
    if (!_wanted(scene)) continue;
    testWidgets(scene, (tester) async {
      phone(tester);
      final prefs = await _prefs();
      final now = DateTime.now().millisecondsSinceEpoch;
      final api = _Api(inbox: scene == 'pending')
        ..sessionsById = {_sid: session(now)}
        ..busy = {_sid}
        ..messagesHandler = (_) async => running(now);
      final controller = _controller(prefs, api);
      addTearDown(controller.dispose);
      final key = GlobalKey();
      await tester.pumpWidget(
        captureApp(
          home: const ChatScreen(sessionID: _sid),
          boundaryKey: key,
          controller: controller,
        ),
      );
      await _frames(tester, 4);
      if (scene != 'stop-tip') {
        // The tip has had its moment; the person tapped it away.
        controller.tips.markUsed(TeachingIds.stopAfterStep);
        await _frames(tester, 4);
        await tester.tap(find.byKey(const ValueKey('kit-turn-live-tap')));
        await _frames(tester, 6);
      }
      if (scene == 'pending') {
        await tester.tap(find.byKey(const Key('live-stop-after-step')));
        await _frames(tester, 4);
        api.noteWaits = true;
        await tester.runAsync(() => controller.refreshInbox(_sid));
        // Shown steps fold back after the choice, as a person would.
        await tester.tap(find.byKey(const ValueKey('kit-turn-live-tap')));
        await _frames(tester, 6);
      }
      await shoot(tester, key, scene);
      await done(tester);
    });
  }

  // ---- The Settings gear -------------------------------------------------

  for (final scene in ['gear-rest', 'gear-turn']) {
    if (!_wanted(scene)) continue;
    testWidgets(scene, (tester) async {
      phone(tester);
      _mockSecureStorage(tester);
      final prefs = await _prefs();
      final now = DateTime.now().millisecondsSinceEpoch;
      final api = _Api()
        ..sessionsById = {_sid: session(now)}
        ..busy = {};
      final controller = _controller(prefs, api);
      addTearDown(controller.dispose);
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
          aboveNavigator: (child) => ProviderScope(
            overrides: [builtinLinuxProvider.overrideWithValue(_Linux())],
            child: child,
          ),
        ),
      );
      await _frames(tester);
      if (scene == 'gear-turn') {
        final gear = find.byWidgetPredicate(
          (w) => w is KitIconButton && w.icon == AppIconography.settingsGear,
        );
        await tester.tap(gear.first);
        await tester.pump();
        // Early in the 200 ms turn, before Settings has faded in over it.
        await tester.pump(const Duration(milliseconds: 60));
      }
      await shoot(tester, key, scene);
      await done(tester);
    });
  }
}
