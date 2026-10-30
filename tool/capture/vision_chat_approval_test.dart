// Captures of the chat while the agent waits for an answer (vision slice
// "Chat with a question", 2026-10-20): the real ChatScreen at 412x915 dp,
// 2x pixels, real fonts, blurred shadows, no server.
//
//   TZ=UTC flutter test --concurrency=1 tool/capture/vision_chat_approval_test.dart
//   TZ=UTC flutter test --concurrency=1 --dart-define=VISION_OUT=<dir> \
//     tool/capture/vision_chat_approval_test.dart
//
// Every request below has the shape its server really sends (see
// docs/qa/vision-chat-approval-2026-10-20/README.md): OpenCode 2's shell ask
// (`resources` = the command, `save` = its prefix pattern), OpenCode 1's
// edit (the path and the diff in metadata), OpenCode 2's apply_patch
// (`resources` = the paths, no diff).

import 'package:clock/clock.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chat/permission_sheet.dart'
    show requestFirstSeen;
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

import '../../test/goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'fixtures.dart';

const _out = String.fromEnvironment(
  'VISION_OUT',
  defaultValue: '/home/eslam/Storage2/launch-footage/vision-chat-approval',
);

const _dir = '/root/projects/neon-breaker';
const _sid = 'ses_neon';

enum _Ask { command, edit, files, question }

enum _Scene {
  commandDark('command-dark'),
  commandLight('command-light', light: true),
  editDark('edit-one-file-dark', ask: _Ask.edit),
  filesDark('edit-five-files-dark', ask: _Ask.files),
  questionDark('question-dark', ask: _Ask.question),
  allowedDark('allowed-undo-dark', tapAllow: true),
  small('small-320-text2-dark', size: Size(320, 693), scale: 2),
  arabic('arabic-rtl-dark', locale: Locale('ar'));

  const _Scene(
    this.file, {
    this.light = false,
    this.ask = _Ask.command,
    this.tapAllow = false,
    this.size = const Size(412, 915),
    this.scale = 1,
    this.locale = const Locale('en'),
  });
  final String file;
  final bool light;
  final _Ask ask;
  final bool tapAllow;
  final Size size;
  final double scale;
  final Locale locale;
}

class _Api extends CaptureApi {
  // What OpenCode 2 advertises: it asks before acting, keeps "always"
  // grants and steers a send made while a reply runs.
  @override
  ServerCapabilities get capabilities => const ServerCapabilities(
    permissionRequests: true,
    persistentPermissionGrants: true,
    promptSteering: true,
  );

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await messages(id) : const []);
}

Part _tool(String id, String name, Map<String, dynamic> input) => Part(
  id: id,
  messageID: 'msg_reply',
  callID: id,
  type: 'tool',
  toolName: name,
  toolState: ToolState.fromJson({
    'status': 'running',
    'input': input,
  }, toolName: name),
);

/// The words, the tool call that asks, and the request, per kind.
(String, String, Part, PermissionRequest?) _askFor(_Ask ask) => switch (ask) {
  _Ask.question => (
    'Add a high score to the game.',
    'There are two ways to keep the score.',
    _tool('call_q', 'question', const {}),
    null,
  ),
  _Ask.command => (
    'Make the game feel smoother.',
    'I found a few ways to smooth out the gameplay. First I need to '
        'install the game\'s dependencies.',
    _tool('call_npm', 'shell', {'command': 'npm install'}),
    // OpenCode 2 (2.0.x): action `shell`, the command as its resource, the
    // command's prefix as the pattern "Always allow" saves.
    PermissionRequest(
      id: 'per_npm',
      sessionID: _sid,
      permission: 'shell',
      patterns: const ['npm install'],
      always: const ['npm install *'],
      tool: PermissionTool(messageID: 'msg_reply', callID: 'call_npm'),
    ),
  ),
  _Ask.edit => (
    'Keep a note of what changed.',
    'I will add today\'s changes to the notes file.',
    _tool('call_edit', 'edit', {'filePath': '$_dir/notes.txt'}),
    // OpenCode 1: the relative path as its pattern, the path and the diff
    // in metadata, "*" as what Always allow saves.
    PermissionRequest(
      id: 'per_edit',
      sessionID: _sid,
      permission: 'edit',
      patterns: const ['notes.txt'],
      always: const ['*'],
      metadata: const {
        'filepath': '$_dir/notes.txt',
        'diff':
            '--- notes.txt\n+++ notes.txt\n@@ -1,3 +1,6 @@\n # Notes\n'
            '-Paddle speed: 4\n+Paddle speed: 6\n+Ball trail: on\n'
            '+Screen shake on hit\n+Combo counter\n Levels: 12\n',
      },
      tool: PermissionTool(messageID: 'msg_reply', callID: 'call_edit'),
    ),
  ),
  _Ask.files => (
    'Split the game into smaller files.',
    'I will move the levels, the paddle and the sounds into their own files.',
    _tool('call_patch', 'apply_patch', const {}),
    // OpenCode 2 apply_patch: every path as a resource, no diff.
    PermissionRequest(
      id: 'per_patch',
      sessionID: _sid,
      permission: 'edit',
      patterns: const [
        'src/game.js',
        'src/levels.js',
        'src/paddle.js',
        'src/sounds.js',
        'index.html',
      ],
      always: const ['*'],
      tool: PermissionTool(messageID: 'msg_reply', callID: 'call_patch'),
    ),
  ),
};

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
  });

  for (final scene in _Scene.values) {
    testWidgets(scene.file, (tester) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      // Blurred shadows as a phone draws them; put back before the end.
      debugDisableShadows = false;
      tester.view.physicalSize = scene.size * 2;
      tester.view.devicePixelRatio = 2;
      addTearDown(tester.view.reset);
      KitLiveBackground.frozenSeconds = 21;
      SharedPreferencesStorePlatform.instance =
          InMemorySharedPreferencesStore.empty();
      final prefs = await SharedPreferences.getInstance();
      await prefs.clear();
      final now = DateTime.now().millisecondsSinceEpoch;
      final (prompt, words, call, request) = _askFor(scene.ask);
      final api = _Api()
        ..sessionsById = {
          _sid: Session(
            id: _sid,
            title: 'Neon brick breaker',
            directory: _dir,
            time: SessionTime(created: now - 60000, updated: now - 20000),
          ),
        }
        ..busy = {_sid}
        ..messagesHandler = (_) async => [
          MessageWithParts(
            info: messageInfo('msg_user', 'user', created: now - 40000),
            parts: [
              Part(
                id: 'part_prompt',
                messageID: 'msg_user',
                type: 'text',
                text: prompt,
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
                text: words,
              ),
              call,
            ],
          ),
        ];
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
        ..busySessions = Set.of(api.busy)
        ..permissions = {?request?.id: ?request}
        ..questions = {
          if (scene.ask == _Ask.question)
            'que_score': const PendingQuestion(
              id: 'que_score',
              sessionID: _sid,
              prompts: [
                QuestionPrompt(
                  title: 'High score',
                  question: 'Where should the high score be kept?',
                  multiple: false,
                  custom: true,
                  choices: [
                    QuestionChoice(
                      label: 'On this device',
                      description: 'Saved in the browser',
                    ),
                    QuestionChoice(
                      label: 'Online',
                      description: 'Needs a small server',
                    ),
                  ],
                ),
              ],
            ),
        };
      addTearDown(controller.dispose);
      final key = GlobalKey();
      // The request arrived 20 s before the shot.
      withClock(
        Clock.fixed(clock.now().subtract(const Duration(seconds: 20))),
        () => requestFirstSeen(controller, request?.id ?? 'que_score'),
      );
      {
        await tester.pumpWidget(
          captureApp(
            home: const ChatScreen(sessionID: _sid),
            boundaryKey: key,
            controller: controller,
            light: scene.light,
            aboveNavigator: (child) => Builder(
              builder: (context) => MediaQuery(
                data: MediaQuery.of(
                  context,
                ).copyWith(textScaler: TextScaler.linear(scene.scale)),
                child: Localizations.override(
                  context: context,
                  locale: scene.locale,
                  // The app's theme for the language (Arabic fonts).
                  child: Theme(
                    data: AppTheme.forLocale(Theme.of(context), scene.locale),
                    child: child,
                  ),
                ),
              ),
            ),
          ),
        );
        await tester.pump();
      }
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 200)),
      );
      for (var i = 0; i < 12; i++) {
        await tester.pump(const Duration(milliseconds: 120));
      }
      if (scene.tapAllow) {
        await tester.tap(find.byKey(const Key('permission-card-allow')));
        for (var i = 0; i < 4; i++) {
          await tester.pump(const Duration(milliseconds: 120));
        }
      }
      expect(tester.takeException(), isNull);
      await writePng(
        '$_out/${scene.file}.png',
        await capturePng(tester, key, pixelRatio: 2),
      );
      // Let a held answer go out while the chat is still open.
      for (var i = 0; i < 8; i++) {
        await tester.pump(const Duration(seconds: 1));
      }
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(seconds: 1));
      debugDisableShadows = true;
      debugDefaultTargetPlatformOverride = null;
    });
  }
}
