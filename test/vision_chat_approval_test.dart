// Chat with a question (vision slice, 2026-10-20): the card names the action
// and its target as a question, shows exactly what the request runs or
// touches (only what the request says), counts how long it has waited, names
// the standing grant after what it saves; while it waits the composer reads
// "Waiting for you" with no Stop.
import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat/permission_sheet.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart';

const _dir = '/root/projects/neon-breaker';
const _sid = 'ses_neon';

final _l10n = lookupAppLocalizations(const Locale('en'));

/// [text] with the bidi isolates and word joiners the card adds removed.
String _plain(String text) =>
    text.replaceAll(RegExp('[\u2066-\u2069\u2060]'), '');

PermissionAsk _ask(PermissionRequest request, {String? project}) =>
    permissionAsk(
      request,
      l10n: _l10n,
      fallback: 'fallback',
      project: project,
      directory: _dir,
    );

class _Api extends CaptureApi {
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

Future<CaptureController> _open(
  WidgetTester tester,
  PermissionRequest request,
) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final now = DateTime.now().millisecondsSinceEpoch;
  final api = _Api()
    ..sessionsById = {
      _sid: Session(id: _sid, title: 'Neon brick breaker', directory: _dir),
    }
    ..busy = {_sid}
    ..messagesHandler = (_) async => [
      MessageWithParts(
        info: messageInfo('msg_user', 'user', created: now - 20000),
        parts: [
          Part(
            id: 'part_prompt',
            messageID: 'msg_user',
            type: 'text',
            text: 'Make the game smoother.',
          ),
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
    ..permissions = {request.id: request};
  addTearDown(controller.dispose);
  withClock(
    Clock.fixed(clock.now().subtract(const Duration(seconds: 20))),
    () => requestFirstSeen(controller, request.id),
  );
  await tester.pumpWidget(
    captureApp(
      home: const ChatScreen(sessionID: _sid),
      boundaryKey: GlobalKey(),
      controller: controller,
    ),
  );
  for (var i = 0; i < 10; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
  return controller;
}

final _npm = PermissionRequest(
  id: 'per_npm',
  sessionID: _sid,
  // OpenCode 2 stable names its shell tool `shell`.
  permission: 'shell',
  patterns: const ['npm install'],
  always: const ['npm install *'],
);

void main() {
  group('the ask, from the request alone', () {
    test('a short shell command: its words and the project', () {
      final ask = _ask(_npm, project: 'neon-breaker');
      expect(_plain(ask.title), 'Run npm install in neon-breaker?');
      expect(ask.targets.single.text, 'npm install');
      expect(ask.targets.single.change, isNull);
      expect(_plain(ask.alwaysLabel!), 'Always allow npm install');
      expect(ask.alwaysWhat, 'npm install');
    });

    test('a long command reads by the family its pattern saves', () {
      final ask = _ask(
        PermissionRequest(
          id: 'p',
          sessionID: _sid,
          permission: 'bash',
          patterns: const ['npm install --save-dev vite @types/node eslint'],
          metadata: const {
            'command': 'npm install --save-dev vite @types/node eslint',
          },
          always: const ['npm install *'],
        ),
        project: 'neon-breaker',
      );
      expect(_plain(ask.title), 'Run npm install in neon-breaker?');
      // The block shows the whole command, never a guess at its files.
      expect(
        ask.targets.single.text,
        'npm install --save-dev vite @types/node eslint',
      );
    });

    test('a long command with no family: "a command"', () {
      final ask = _ask(
        PermissionRequest(
          id: 'p',
          sessionID: _sid,
          permission: 'shell',
          patterns: const ['find . -name "*.tmp" -mtime +3 -exec rm {} +'],
          always: const ['find . -name "*.tmp" -mtime +3 -exec rm {} +'],
        ),
        project: 'neon-breaker',
      );
      expect(_plain(ask.title), 'Run a command in neon-breaker?');
      expect(ask.alwaysLabel, _l10n.chatRequestAlwaysAllowCommands);
    });

    test('OpenCode 2 apply_patch: every path, no size it does not send', () {
      final paths = [
        'src/game.js',
        'src/levels.js',
        'src/paddle.js',
        'src/sounds.js',
        'index.html',
      ];
      final request = PermissionRequest(
        id: 'p',
        sessionID: _sid,
        permission: 'edit',
        patterns: paths,
        always: const ['*'],
      );
      final ask = _ask(request);
      expect(_plain(ask.title), 'Edit 5 files?');
      expect([for (final t in ask.targets) t.text], paths);
      expect(ask.targets.every((t) => t.change == null), isTrue);
      expect(ask.alwaysLabel, 'Always allow edits');
      // The one-line summary (lists, sheets) names them all too.
      expect(permissionSummary(request), paths.join(' · '));
    });

    test("OpenCode 1 edit: the file's name and its diff's size", () {
      final ask = _ask(
        PermissionRequest(
          id: 'p',
          sessionID: _sid,
          permission: 'edit',
          patterns: const ['notes.txt'],
          always: const ['*'],
          metadata: const {
            'filepath': '$_dir/notes.txt',
            'diff':
                '--- notes.txt\n+++ notes.txt\n@@ -1,2 +1,3 @@\n-a\n+b\n+c\n d\n',
          },
        ),
      );
      expect(_plain(ask.title), 'Edit notes.txt?');
      expect(ask.targets.single.text, 'notes.txt');
      expect(ask.targets.single.change, '+2 −1');
    });

    test('a path the agent names absolutely reads from the project', () {
      final ask = _ask(
        PermissionRequest(
          id: 'p',
          sessionID: _sid,
          permission: 'edit',
          patterns: const ['$_dir/src/game.js'],
          metadata: const {'filePath': '$_dir/src/game.js'},
        ),
      );
      expect(ask.targets.single.text, 'src/game.js');
      // No "*" pattern: the grant keeps the card's own words.
      expect(ask.alwaysLabel, isNull);
    });

    test('Delete only when the request says every file is deleted', () {
      final ask = _ask(
        PermissionRequest(
          id: 'p',
          sessionID: _sid,
          permission: 'edit',
          patterns: const ['build/a.js', 'build/b.css'],
          always: const ['*'],
          metadata: const {
            'files': [
              {
                'relativePath': 'build/a.js',
                'type': 'delete',
                'additions': 0,
                'deletions': 40,
              },
              {
                'relativePath': 'build/b.css',
                'type': 'delete',
                'additions': 0,
                'deletions': 12,
              },
            ],
          },
        ),
      );
      expect(_plain(ask.title), 'Delete 2 files?');
      expect([for (final t in ask.targets) t.change], ['+0 −40', '+0 −12']);
    });
  });

  group('in the chat', () {
    setUpAll(loadCaptureFonts);

    testWidgets('the card asks, counts and names its grant', (tester) async {
      await _open(tester, _npm);
      expect(
        find.byWidgetPredicate(
          (w) =>
              w is Text &&
              _plain(w.data ?? '') == 'Run npm install in neon-breaker?',
        ),
        findsOneWidget,
      );
      expect(find.text('Needs you · 21 s'), findsOneWidget);
      await tester.pump(const Duration(seconds: 1));
      expect(find.text('Needs you · 22 s'), findsOneWidget);
      expect(find.text('Use Shell'), findsNothing);
      expect(
        find.byWidgetPredicate(
          (w) =>
              w is Text && _plain(w.data ?? '') == 'Always allow npm install',
        ),
        findsOneWidget,
      );
      expect(find.byKey(const Key('permission-card-review')), findsOneWidget);
    });

    testWidgets(
      'the composer waits with the card, and works again once answered',
      (tester) async {
        await _open(tester, _npm);
        expect(find.text('Waiting for you'), findsOneWidget);
        // Stop fades out of the chips row while the card owns the composer.
        await tester.pump(const Duration(milliseconds: 400));
        await tester.pump(const Duration(milliseconds: 400));
        expect(find.byKey(const Key('chat-stop-button')), findsNothing);
        await tester.tap(find.byKey(const Key('permission-card-allow')));
        await tester.pump(const Duration(milliseconds: 300));
        // Held for its Undo window: it no longer waits on the person.
        expect(find.text('Waiting for you'), findsNothing);
        await tester.pump(const Duration(milliseconds: 400));
        expect(find.byKey(const Key('chat-stop-button')), findsOneWidget);
        for (var i = 0; i < 8; i++) {
          await tester.pump(const Duration(seconds: 1));
        }
      },
    );

    testWidgets('five files: three in place, the rest counted', (tester) async {
      await _open(
        tester,
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
        ),
      );
      expect(find.text('Edit 5 files?'), findsOneWidget);
      expect(find.text('src/paddle.js'), findsOneWidget);
      expect(find.text('src/sounds.js'), findsNothing);
      expect(find.text('+2 more'), findsOneWidget);
      expect(find.text('Always allow edits'), findsOneWidget);
    });
  });
}
