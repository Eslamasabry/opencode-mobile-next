// Captures of the conversation screen in its four moments (chat fidelity
// round, 2026-10-20): idle, working, a question waiting and a finished turn,
// next to concept C (working) and concept A (question). The real ChatScreen
// at 412x915 dp, 2x pixels, real fonts, blurred shadows, no server.
//
//   TZ=UTC flutter test --concurrency=1 tool/capture/chat_fidelity_test.dart
//   TZ=UTC flutter test --concurrency=1 --dart-define=FIDELITY_OUT=<dir> \
//     tool/capture/chat_fidelity_test.dart

import 'package:clock/clock.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/session_auto_approval.dart';
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
  'FIDELITY_OUT',
  defaultValue: '/home/eslam/Storage2/launch-footage/chat-fidelity',
);

const _dir = '/root/projects/neon-breaker';
const _sid = 'ses_neon';

enum _Moment { idle, working, question, done }

enum _Scene {
  idleDark('idle-dark', moment: _Moment.idle),
  idleLight('idle-light', moment: _Moment.idle, light: true),
  workingDark('working-dark'),
  workingLight('working-light', light: true),
  questionDark('question-dark', moment: _Moment.question),
  doneDark('done-dark', moment: _Moment.done),
  plusIdleDark('plus-idle-dark', moment: _Moment.idle, plus: true),
  plusIdleLight(
    'plus-idle-light',
    moment: _Moment.idle,
    light: true,
    plus: true,
  ),
  plusWorkingDark('plus-working-dark', plus: true),
  plusWorkingLight('plus-working-light', light: true, plus: true),
  small('small-320-text2-dark', size: Size(320, 693), scale: 2),
  plusSmall(
    'plus-small-320-text2-dark',
    moment: _Moment.idle,
    size: Size(320, 693),
    scale: 2,
    plus: true,
  ),
  arabic('arabic-rtl-dark', locale: Locale('ar'));

  const _Scene(
    this.file, {
    this.moment = _Moment.working,
    this.light = false,
    this.size = const Size(412, 915),
    this.scale = 1,
    this.locale = const Locale('en'),
    this.plus = false,
  });
  final String file;

  /// "+" tapped: its tray open.
  final bool plus;
  final _Moment moment;
  final bool light;
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

Part _tool(String id, String name, String status, Map<String, dynamic> in_) =>
    Part(
      id: id,
      messageID: 'msg_reply',
      callID: id,
      type: 'tool',
      toolName: name,
      toolState: ToolState.fromJson({
        'status': status,
        'input': in_,
        if (status == 'completed') 'output': '',
        if (status == 'completed') 'title': name,
      }, toolName: name),
    );

Part _text(String id, String text, {String message = 'msg_reply'}) =>
    Part(id: id, messageID: message, type: 'text', text: text);

/// The prompt, the reply's parts and the waiting request, per moment.
(String, List<Part>, PermissionRequest?) _turnFor(_Moment moment) =>
    switch (moment) {
      _Moment.idle => (
        'What would make the game feel smoother?',
        [
          _text(
            'part_words',
            'Three things would help most: a steady frame rate, a short '
                'trail behind the ball, and a little screen shake when a '
                'brick breaks.',
          ),
        ],
        null,
      ),
      _Moment.working => (
        'Build a neon brick breaker I can play with my thumb.',
        [
          _text(
            'part_words',
            'On it. Adding glowing bricks and smooth paddle controls.',
          ),
          _tool('write', 'write', 'running', {'filePath': '$_dir/index.html'}),
        ],
        null,
      ),
      _Moment.question => (
        'Make the game feel smoother.',
        [
          _text(
            'part_words',
            'I found a few ways to smooth out the gameplay. First I need to '
                'install the game\'s dependencies.',
          ),
          _tool('call_npm', 'shell', 'running', {'command': 'npm install'}),
        ],
        PermissionRequest(
          id: 'per_npm',
          sessionID: _sid,
          permission: 'shell',
          patterns: const ['npm install'],
          always: const ['npm install *'],
          tool: PermissionTool(messageID: 'msg_reply', callID: 'call_npm'),
        ),
      ),
      _Moment.done => (
        'Add a combo counter and some screen shake.',
        [
          _text('part_plan', 'I will add both and check the game still runs.'),
          _tool('read', 'read', 'completed', {'filePath': '$_dir/index.html'}),
          _tool('edit', 'edit', 'completed', {'filePath': '$_dir/index.html'}),
          _tool('edit2', 'edit', 'completed', {'filePath': '$_dir/game.js'}),
          _tool('bash', 'bash', 'completed', {'command': 'npm test'}),
          _text(
            'part_words',
            'Added a combo counter and a short screen shake on every hit. '
                'The tests pass.',
          ),
        ],
        null,
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
      final moment = scene.moment;
      final working = moment == _Moment.working || moment == _Moment.question;
      final (prompt, parts, request) = _turnFor(moment);
      final api = _Api()
        ..sessionsById = {
          _sid: Session(
            id: _sid,
            title: 'Neon brick breaker',
            directory: _dir,
            time: SessionTime(created: now - 60000, updated: now - 20000),
          ),
        }
        ..busy = {if (working) _sid}
        ..messagesHandler = (_) async => [
          MessageWithParts(
            info: messageInfo('msg_user', 'user', created: now - 14000),
            parts: [_text('part_prompt', prompt, message: 'msg_user')],
          ),
          MessageWithParts(
            info: messageInfo(
              'msg_reply',
              'assistant',
              created: now - 12000,
              completed: working ? null : now - 2000,
            ),
            parts: parts,
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
        ..permissions = {?request?.id: ?request};
      addTearDown(controller.dispose);
      // Auto-approve on everywhere but the question (a request that waits
      // is one Auto-approve did not answer).
      if (moment != _Moment.question) {
        await controller.setSessionAutoApproval(
          _sid,
          const SessionAutoApproval(mode: AutoApprovalMode.autoOnce),
        );
      }
      final key = GlobalKey();
      if (request != null) {
        withClock(
          Clock.fixed(clock.now().subtract(const Duration(seconds: 20))),
          () => requestFirstSeen(controller, request.id),
        );
      }
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
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 200)),
      );
      for (var i = 0; i < 12; i++) {
        await tester.pump(const Duration(milliseconds: 120));
      }
      if (scene.plus) {
        await tester.tap(find.byKey(const Key('composer-tools-button')));
        for (var i = 0; i < 4; i++) {
          await tester.pump(const Duration(milliseconds: 120));
        }
      }
      expect(tester.takeException(), isNull);
      await writePng(
        '$_out/${scene.file}.png',
        await capturePng(tester, key, pixelRatio: 2),
      );
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(seconds: 1));
      debugDisableShadows = true;
      debugDefaultTargetPlatformOverride = null;
    });
  }
}
