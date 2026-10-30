// Owner report 2026-10-27: a Claude Code turn ended with "The owner build is
// compiling … Once it's done I'll install it" while the build ran on in the
// background, and the chat looked finished. Now, once the turn has ended, a
// quiet line under the last turn says what still runs; it goes away when the
// task's notice lands, and the notice row stays as it was.
//
// Owner report 2026-10-29: a tap on "Still running in the background · grep ·
// 8 min" only scrolled the chat a little; the step that started it was folded
// in the turn's work line, and nothing said what ran. Now the line opens to
// the task's name, time and command, and the row opens the folded step and
// marks it for a moment.
import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/background_tasks.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';

import '../tool/capture/fixtures.dart';
import 'support/setup_capture_preferences.dart';

/// The line as a screen reader hears it: its words in full, never cut.
Finder _says(String words) =>
    find.bySemanticsLabel(RegExp(RegExp.escape(words)));

const _callId = 'toolu_01BuildOwner';
const _lineKey = ValueKey('chat-background-work');
const _loop =
    'until grep -q "BUILD SUCCESSFUL" /tmp/claude-1000/tasks/b1x2y3.output; '
    'do sleep 30; done';

class _Api extends CaptureApi {
  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await messages(id) : const []);
}

/// The owner's turn: the prompt, the build started in the background, and
/// Claude's last words. The turn has ended.
List<MessageWithParts> ownerTurn(
  int startedAt, {
  String command = 'bash tool/build_tv_release.sh --owner',
  bool afterAnotherStep = false,
}) {
  MessageInfo info(String id, String role, int at) => MessageInfo(
    id: id,
    sessionID: checkoutSessionID,
    role: role,
    providerID: role == 'assistant' ? 'claude' : null,
    time: MsgTime(created: at, completed: at + 1000),
  );
  return [
    MessageWithParts(
      info: info('u1', 'user', startedAt),
      parts: [
        Part(
          id: 'u1:0',
          messageID: 'u1',
          type: 'text',
          text:
              'Where is the newly added movies and series section? Also can '
              'you make this a premium user.',
        ),
      ],
    ),
    MessageWithParts(
      info: info(_callId, 'assistant', startedAt + 4000),
      parts: [
        // With a step before it, the two fold into one work line.
        if (afterAnotherStep)
          Part(
            id: '$_callId:read',
            messageID: _callId,
            callID: 'toolu_00Read',
            type: 'tool',
            toolName: 'read',
            toolState: ToolState.fromJson({
              'status': 'completed',
              'input': {'filePath': 'tool/build_tv_release.sh'},
              'output': 'set -e',
            }, toolName: 'read'),
          ),
        Part(
          id: '$_callId:0',
          messageID: _callId,
          callID: _callId,
          type: 'tool',
          toolName: 'bash',
          toolState: ToolState.fromJson({
            'status': 'completed',
            'input': {'command': command},
            'output':
                'Command running in background with ID: b1x2y3. Output is '
                'being written to: /tmp/claude-1000/tasks/b1x2y3.output',
            'metadata': {
              backgroundTaskMetadataKey: {'id': 'b1x2y3'},
            },
          }, toolName: 'bash'),
        ),
      ],
    ),
    MessageWithParts(
      info: info('a1', 'assistant', startedAt + 5000),
      parts: [
        Part(
          id: 'a1:0',
          messageID: 'a1',
          type: 'text',
          text:
              'The owner build is compiling: the fast release version with '
              "Premium built in. Once it's done I'll install it and then "
              'check your Home screen for the new movies/series shelves.',
        ),
      ],
    ),
  ];
}

Future<CaptureController> pumpOwnerChat(
  WidgetTester tester, {
  required int startedAt,
  GlobalKey? boundaryKey,
  String? command,
  bool afterAnotherStep = false,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final prefs = await setupCapturePreferences();
  final api = _Api()
    ..busy = {}
    ..messagesHandler = (_) async => command == null
        ? ownerTurn(startedAt, afterAnotherStep: afterAnotherStep)
        : ownerTurn(
            startedAt,
            command: command,
            afterAnotherStep: afterAnotherStep,
          );
  final controller = await captureController(prefs: prefs, api: api);
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    captureApp(
      home: const ChatScreen(sessionID: checkoutSessionID),
      boundaryKey: boundaryKey ?? GlobalKey(),
      controller: controller,
    ),
  );
  for (var frame = 0; frame < 8; frame++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
  return controller;
}

void main() {
  testWidgets('the line shows after the turn ends and goes when the notice '
      'lands', (tester) async {
    // The test's clock, which the line's timer reads.
    final startedAt = clock
        .now()
        .subtract(const Duration(minutes: 2, seconds: 30))
        .millisecondsSinceEpoch;
    final controller = await pumpOwnerChat(tester, startedAt: startedAt);

    expect(find.byKey(_lineKey), findsOneWidget);
    expect(
      _says('Still running in the background · build_tv_release.sh · 2 min'),
      findsOneWidget,
    );
    // One line, under the newest row (Claude's last words).
    final words = tester.getRect(find.textContaining('shelves.'));
    expect(tester.getRect(find.byKey(_lineKey)).top, greaterThan(words.top));

    final now = DateTime.now().millisecondsSinceEpoch;
    controller
      ..handleEventForTesting(
        captureEvent('message.updated', {
          'info': {
            'id': 'task_notification_b1x2y3',
            'sessionID': checkoutSessionID,
            'role': 'assistant',
            'providerID': 'claude',
            'time': {'created': now, 'completed': now},
          },
        }),
      )
      ..handleEventForTesting(
        captureEvent('message.part.updated', {
          'sessionID': checkoutSessionID,
          'part': partJson(
            id: 'task_notification_b1x2y3:0',
            messageID: 'task_notification_b1x2y3',
            type: 'tool',
            tool: 'task_notification',
            state: {
              'status': 'completed',
              'input': {
                'summary':
                    'Background command "Build the owner APK" completed '
                    '(exit code 0)',
                'status': 'completed',
                'toolUseId': _callId,
                'taskId': 'b1x2y3',
              },
              'output': '',
            },
          ),
        }),
      );
    await tester.pump();
    // It fades out (the old line is still drawn this frame).
    expect(find.byKey(_lineKey), findsOneWidget);
    await tester.pump(const Duration(milliseconds: 300));
    expect(
      find.textContaining('Still running in the background'),
      findsNothing,
    );
    expect(find.byKey(_lineKey), findsNothing);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('a tap on the task opens its folded step and marks it', (
    tester,
  ) async {
    final startedAt = clock
        .now()
        .subtract(const Duration(minutes: 8, seconds: 10))
        .millisecondsSinceEpoch;
    await pumpOwnerChat(
      tester,
      startedAt: startedAt,
      command: _loop,
      afterAnotherStep: true,
    );
    expect(
      _says('Still running in the background · Waiting for grep · 8 min'),
      findsOneWidget,
    );
    // The step that started it is folded in the turn's work line.
    const stepRow = Key('embedded-tool-row');
    expect(find.byKey(stepRow), findsNothing);

    final rowCommand = find.descendant(
      of: find.byType(KitBackgroundWorkLine),
      matching: find.text(_loop),
    );
    expect(rowCommand, findsNothing);
    await tester.tap(find.byKey(_lineKey));
    for (var frame = 0; frame < 4; frame++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
    expect(rowCommand, findsOneWidget);

    await tester.tap(find.text('Waiting for grep · 8 min'));
    for (var frame = 0; frame < 8; frame++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
    const band = ValueKey('step:$_callId:0');
    final step = find.descendant(
      of: find.byKey(band),
      matching: find.byKey(stepRow),
    );
    expect(step, findsOneWidget);
    expect(
      tester.widget<KitAnimatedBox>(find.byKey(band)).level,
      KitSurfaceLevel.surface2,
    );
    // The list settled on the step, well clear of the composer.
    expect(tester.getRect(step).top, inInclusiveRange(0, 500));
    // The whole turn is not marked as well: the mark is the step's.
    expect(
      find.byWidgetPredicate(
        (widget) => widget is KitTurn && widget.highlighted,
      ),
      findsNothing,
    );

    // The mark fades; the work line stays open.
    await tester.pump(const Duration(seconds: 2));
    await tester.pump(const Duration(milliseconds: 300));
    expect(tester.widget<KitAnimatedBox>(find.byKey(band)).level, isNull);
    expect(step, findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('a step not folded is marked in place', (tester) async {
    final startedAt = clock
        .now()
        .subtract(const Duration(minutes: 2, seconds: 30))
        .millisecondsSinceEpoch;
    await pumpOwnerChat(tester, startedAt: startedAt);
    await tester.tap(find.byKey(_lineKey));
    for (var frame = 0; frame < 4; frame++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
    await tester.tap(find.text('build_tv_release.sh · 2 min'));
    for (var frame = 0; frame < 8; frame++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
    expect(
      tester
          .widget<KitAnimatedBox>(find.byKey(const ValueKey('step:$_callId:0')))
          .level,
      KitSurfaceLevel.surface2,
    );
    await tester.pump(const Duration(seconds: 3));
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('work started over six hours ago is not claimed', (tester) async {
    final startedAt = DateTime.now()
        .subtract(const Duration(hours: 6, minutes: 1))
        .millisecondsSinceEpoch;
    await pumpOwnerChat(tester, startedAt: startedAt);
    expect(
      find.textContaining('Still running in the background'),
      findsNothing,
    );
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
