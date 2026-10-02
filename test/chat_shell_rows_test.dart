// Shell steps in a chat: the step line reads as one clean command, the
// output block keeps its status line at normal kit spacing, nothing paints
// across "Run this command again", and the top bar's "Collapse all steps"
// shows in any chat while a step is open.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart' show KitTopBar;
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/widgets/tool_card.dart';

import '../tool/capture/fixtures.dart';
import 'support/setup_capture_preferences.dart';

const _command =
    'mkdir -p /root/projects/space-and-time && ls -la /root/projects';

ToolState _shell() => ToolState.fromJson({
  'status': 'completed',
  'input': {'command': _command, 'workdir': '/root'},
  'output': 'total 14\ndrwx------. 4 root root 3452 Oct 2 06:15 .',
  'metadata': {'exit': 0},
}, toolName: 'bash');

Future<void> _pumpCard(WidgetTester tester) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Scaffold(
        body: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            ToolCard(toolName: 'bash', state: _shell(), onRerunCommand: (_) {}),
          ],
        ),
      ),
    ),
  );
  await tester.tap(find.text('Shell'));
  await tester.pumpAndSettle();
}

class _Api extends CaptureApi {
  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await messages(id) : const []);
}

void main() {
  testWidgets('the step title is the command, cut once at the end', (
    tester,
  ) async {
    await _pumpCard(tester);
    final row = find.byKey(const Key('tool-shell-command'));
    expect(row, findsOneWidget);
    // The line's own text: the whole command (the cut is the renderer's),
    // and the last path never appears as a second, separate piece.
    final line = find.byWidgetPredicate(
      (w) =>
          w is Text &&
          w.data == _command &&
          w.overflow == TextOverflow.ellipsis,
    );
    expect(line, findsOneWidget);
    expect(find.text('/root/projects'), findsNothing);
  });

  testWidgets('Run this command again has no decoration and no bar over it', (
    tester,
  ) async {
    await _pumpCard(tester);
    final label = find.text('Run this command again');
    expect(label, findsOneWidget);
    final style = tester.widget<Text>(label).style;
    final effective = DefaultTextStyle.of(
      tester.element(label),
    ).style.merge(style);
    expect(effective.decoration ?? TextDecoration.none, TextDecoration.none);
    // Every scroll bar belongs to a code block and is clipped to it, so
    // none can paint across the rows beside it.
    for (final bar in find.byType(Scrollbar).evaluate()) {
      expect(
        find.ancestor(
          of: find.byWidget(bar.widget),
          matching: find.byType(ClipRect),
        ),
        findsWidgets,
      );
    }
    final rerun = tester.getRect(find.byKey(const Key('tool-shell-rerun')));
    for (final bar in find.byType(Scrollbar).evaluate()) {
      final box = (bar.renderObject! as RenderBox);
      final rect = box.localToGlobal(Offset.zero) & box.size;
      expect(rect.overlaps(rerun), isFalse);
    }
  });

  testWidgets('the output block keeps its status line close to the top', (
    tester,
  ) async {
    await _pumpCard(tester);
    final block = tester.getRect(find.byKey(const Key('tool-shell-output')));
    final status = tester.getRect(find.text('Passed · exit code 0'));
    // Normal kit spacing: the 48 dp controls' own room, not 16 dp more.
    expect(status.top - block.top, lessThan(24));
  });

  testWidgets(
    'Collapse all steps shows in a normal chat while a step is open',
    (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final prefs = await setupCapturePreferences();
      final api = _Api()
        ..busy = {}
        ..messagesHandler = (_) async => [
          MessageWithParts(
            info: MessageInfo(
              id: 'tools',
              sessionID: checkoutSessionID,
              role: 'assistant',
            ),
            parts: [
              for (final id in ['read', 'test'])
                Part(
                  id: id,
                  callID: id,
                  type: 'tool',
                  toolName: 'bash',
                  toolState: ToolState.fromJson({
                    'status': 'completed',
                    'input': {
                      'command': id == 'read' ? 'cat a.ts' : 'npm test',
                    },
                    'output': 'ok',
                  }, toolName: 'bash'),
                ),
            ],
          ),
        ];
      final controller = await captureController(prefs: prefs, api: api);
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        captureApp(
          home: const ChatScreen(sessionID: checkoutSessionID),
          boundaryKey: GlobalKey(),
          controller: controller,
        ),
      );
      for (var frame = 0; frame < 8; frame++) {
        await tester.pump(const Duration(milliseconds: 120));
      }
      final button = find.descendant(
        of: find.byType(KitTopBar),
        matching: find.byKey(const Key('chat-collapse-all')),
      );
      expect(button, findsNothing);
      await tester.tap(find.byKey(const Key('work-group-header')));
      await tester.pump(const Duration(milliseconds: 300));
      expect(button, findsOneWidget);
      await tester.tap(button);
      await tester.pump(const Duration(milliseconds: 300));
      expect(button, findsNothing);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
    },
  );
}
