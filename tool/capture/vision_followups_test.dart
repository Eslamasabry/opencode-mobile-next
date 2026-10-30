// Captures of the vision follow-ups (owner's emulator dry run of build 2567,
// 2026-10-21): the real screens at 412x915 dp, 2x pixels, dark, real fonts,
// blurred shadows, no server. One full phone screen per image.
//
//   TZ=UTC flutter test --concurrency=1 tool/capture/vision_followups_test.dart
//   TZ=UTC flutter test --concurrency=1 --dart-define=VISION_OUT=<dir> \
//     --dart-define=VISION_ONLY=chat-waiting-dark tool/capture/vision_followups_test.dart
//
// Output: /home/eslam/Storage2/launch-footage/vision-followups/<scene>.png
import 'dart:async';

import 'package:clock/clock.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/phrase_chips.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/desktop/shortcuts.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chat/permission_sheet.dart'
    show requestFirstSeen;
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/chats/chats_host.dart'
    show chatsHostProvider;
import 'package:opencode_mobile/ui/screens/chats/new_chat_screen.dart'
    show showNewChat;
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

import '../../test/goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../../test/support/agents_fakes.dart';
import '../../test/support/chats_fakes.dart';
import 'fixtures.dart';

const _out = String.fromEnvironment(
  'VISION_OUT',
  defaultValue: '/home/eslam/Storage2/launch-footage/vision-followups',
);

/// Only these scenes (comma separated), when given.
const _only = String.fromEnvironment('VISION_ONLY');

const _dir = '/root/projects/settings-demo';
const _sid = 'ses_tea';

bool _wanted(String scene) => _only.isEmpty || _only.split(',').contains(scene);

class _Api extends CaptureApi {
  _Api({this.schedules = false});

  final bool schedules;

  // What OpenCode 2 advertises: it asks before acting and keeps "always"
  // grants; [schedules] adds scheduled prompts.
  @override
  ServerCapabilities get capabilities => ServerCapabilities(
    permissionRequests: true,
    persistentPermissionGrants: true,
    promptSteering: true,
    scheduledPrompts: schedules,
  );

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await messages(id) : const []);
}

MessageWithParts _prompt(int now, String text) => MessageWithParts(
  info: messageInfo('msg_user', 'user', created: now - 40000),
  parts: [
    Part(id: 'part_prompt', messageID: 'msg_user', type: 'text', text: text),
  ],
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
  return prefs;
}

/// The chat's controller on the app's own server on this phone.
CaptureController _phoneController(SharedPreferences prefs, _Api api) {
  final controller = CaptureController(
    SeededProfileStore(
      prefs: prefs,
      seeded: [
        ServerProfile(
          id: 'phone',
          name: 'On this phone',
          baseUrl: BuiltinLinux.serverUrl,
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
    await tester.pump(const Duration(seconds: 1));
    debugDisableShadows = true;
    debugDefaultTargetPlatformOverride = null;
  }

  Session session(int now) => Session(
    id: _sid,
    title: 'Create and read hello.txt tea notes',
    directory: _dir,
    time: SessionTime(created: now - 60000, updated: now - 20000),
  );

  // Item 4: the dry run's "Thinking…" over the waiting card. The card is the
  // state; the thought in hand stays out of the way until it is answered.
  if (_wanted('chat-waiting-dark')) {
    testWidgets('chat-waiting-dark', (tester) async {
      phone(tester);
      final prefs = await _prefs();
      final now = DateTime.now().millisecondsSinceEpoch;
      final api = _Api()
        ..sessionsById = {_sid: session(now)}
        ..busy = {_sid}
        ..messagesHandler = (_) async => [
          _prompt(
            now,
            'Create a file hello.txt with two lines about tea, then read it '
            'back',
          ),
          MessageWithParts(
            info: messageInfo('msg_reply', 'assistant', created: now - 30000),
            parts: [
              Part(
                id: 'part_think',
                messageID: 'msg_reply',
                type: 'reasoning',
                text: 'Two short lines about tea, then read the file back.',
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
      final request = PermissionRequest(
        id: 'per_edit',
        sessionID: _sid,
        permission: 'edit',
        patterns: const ['hello.txt'],
        always: const ['*'],
        tool: PermissionTool(messageID: 'msg_reply', callID: 'call_edit'),
      );
      final controller = _phoneController(prefs, api)
        ..permissions = {request.id: request};
      addTearDown(controller.dispose);
      withClock(
        Clock.fixed(clock.now().subtract(const Duration(seconds: 17))),
        () => requestFirstSeen(controller, request.id),
      );
      final key = GlobalKey();
      await tester.pumpWidget(
        captureApp(
          home: const ChatScreen(sessionID: _sid),
          boundaryKey: key,
          controller: controller,
        ),
      );
      await _frames(tester);
      expect(find.text('Thinking…'), findsNothing);
      expect(tester.takeException(), isNull);
      await writePng(
        '$_out/chat-waiting-dark.png',
        await capturePng(tester, key, pixelRatio: 2),
      );
      await done(tester);
    });
  }

  // Item 1 (the row above the box): learned phrases that run past the edge
  // fade there instead of ending mid-word.
  // Item 7: a long-press on Send offers "Send later or repeat".
  for (final scene in ['chat-phrases-dark', 'chat-send-later-dark']) {
    if (!_wanted(scene)) continue;
    testWidgets(scene, (tester) async {
      phone(tester);
      final prefs = await _prefs();
      final store = PhraseChipStore.forProfile(prefs, 'phone');
      for (final phrase in [
        'Run the tests again',
        'Explain what changed',
        'Make it look calmer',
        'Commit and push',
      ]) {
        for (var i = 0; i < 3; i++) {
          await store.record(phrase, PhraseMoment.idle);
        }
      }
      final now = DateTime.now().millisecondsSinceEpoch;
      final api = _Api(schedules: true)
        ..sessionsById = {_sid: session(now)}
        ..busy = {}
        ..messagesHandler = (_) async => [
          _prompt(
            now,
            'Create a file hello.txt with two lines about tea, then read it '
            'back',
          ),
          MessageWithParts(
            info: messageInfo(
              'msg_reply',
              'assistant',
              created: now - 30000,
              completed: now - 20000,
            ),
            parts: [
              Part(
                id: 'part_words',
                messageID: 'msg_reply',
                type: 'text',
                text:
                    'hello.txt now says:\n\n> Green tea before noon.\n> '
                    'Black tea after dark.',
              ),
            ],
          ),
        ];
      final controller = _phoneController(prefs, api);
      addTearDown(controller.dispose);
      final key = GlobalKey();
      await tester.pumpWidget(
        captureApp(
          home: const ChatScreen(sessionID: _sid),
          boundaryKey: key,
          controller: controller,
        ),
      );
      await _frames(tester);
      if (scene == 'chat-send-later-dark') {
        await tester.enterText(
          find.byKey(const Key('chat-composer-field')),
          'Check the tea notes every morning',
        );
        await _frames(tester, 4);
        await tester.longPress(find.byKey(const Key('chat-send-button')));
        await _frames(tester, 6);
        expect(find.byKey(const Key('composer-send-later')), findsOneWidget);
      } else {
        expect(
          find.byKey(const ValueKey('phrase-chip-Commit and push')),
          findsOneWidget,
        );
      }
      expect(tester.takeException(), isNull);
      await writePng(
        '$_out/$scene.png',
        await capturePng(tester, key, pixelRatio: 2),
      );
      await done(tester);
    });
  }

  // Items 1 and 2: the Conversations filter chips run past the edge and
  // fade there; the phone's own server is "On this phone" everywhere.
  if (_wanted('home-chips-dark')) {
    testWidgets('home-chips-dark', (tester) async {
      phone(tester);
      _mockSecureStorage(tester);
      final prefs = await _prefs();
      final now = DateTime(2026, 10, 21, 10);
      final source = FakeChatFeedSource(
        items: [
          chat(
            'a',
            'Create bread notes in notes.txt',
            at: now.subtract(const Duration(hours: 8)),
            dir: _dir,
            project: 'settings-demo',
          ),
          chat(
            'b',
            'Settings page dark mode toggle',
            at: now.subtract(const Duration(hours: 13)),
            dir: _dir,
            project: 'settings-demo',
          ),
          chat(
            'c',
            'Neon brick breaker mobile game build',
            at: now.subtract(const Duration(hours: 26)),
            dir: '/root/projects/neon-game',
            project: 'neon-game',
          ),
          chat(
            'd',
            'Writing theme.js tests',
            at: now.subtract(const Duration(hours: 30)),
            dir: _dir,
            project: 'settings-demo',
          ),
        ],
        projects: [project('settings-demo', chats: 3, kind: 'node')],
      );
      final host = _NamedHost(source)
        ..listSources = FakeListSources(const [
          ChatListSource(
            id: 'phone',
            name: 'On this phone',
            kind: ChatListSourceKind.openCode,
            shown: true,
            main: true,
          ),
          ChatListSource(
            id: 'claude',
            name: 'Claude Code',
            kind: ChatListSourceKind.openCode,
            shown: true,
          ),
        ]);
      final controller = _phoneController(prefs, _Api());
      addTearDown(controller.dispose);
      final key = GlobalKey();
      await withClock(Clock.fixed(now), () async {
        await tester.pumpWidget(
          captureApp(
            home: ProviderScope(
              overrides: [chatsHostProvider.overrideWithValue(host)],
              child: AppShortcutScope(
                signals: AppShortcutSignals(),
                perform: (_) {},
                child: const HomeScreen(),
              ),
            ),
            boundaryKey: key,
            controller: controller,
          ),
        );
        await _frames(tester);
      });
      expect(find.text('In-app Ubuntu'), findsNothing);
      expect(tester.takeException(), isNull);
      await writePng(
        '$_out/home-chips-dark.png',
        await capturePng(tester, key, pixelRatio: 2),
      );
      await done(tester);
    });
  }

  // Item 3: the OpenCode mark on the new conversation's agent card.
  if (_wanted('new-conversation-dark')) {
    testWidgets('new-conversation-dark', (tester) async {
      phone(tester);
      final host =
          FakeChatsHost(
              FakeChatFeedSource(
                projects: [project('settings-demo', kind: 'node')],
                lastUsed: _dir,
              ),
            )
            ..phoneAgents = FakePhoneAgentsSource(
              rows: [agentRowFor('claude', FakeAgentStage.notInstalled)],
            );
      final boundary = GlobalKey();
      await tester.pumpWidget(
        RepaintBoundary(
          key: boundary,
          child: chatsApp(host, const SizedBox.expand(key: ValueKey('opener'))),
        ),
      );
      unawaited(
        showNewChat(tester.element(find.byKey(const ValueKey('opener')))),
      );
      await tester.pump();
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 200)),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await writePng(
        '$_out/new-conversation-dark.png',
        await capturePng(tester, boundary, pixelRatio: 2),
      );
      await done(tester);
    });
  }
}

/// Names connections the way the app does (the fake says "OpenCode").
class _NamedHost extends FakeChatsHost {
  _NamedHost(super.source);

  @override
  String connectionName(BuildContext context, String id, String fallback) =>
      listSources?.chatListSources
          .where((source) => source.id == id)
          .firstOrNull
          ?.name ??
      fallback;
}
