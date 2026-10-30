// Unit chat-8, the chat screen's second half (map pages chat,
// chat-run-shell-dialog, chat-message-actions-sheet). What the person sees:
// the shell command and the rename are kit dialogs that run from inside
// and keep a failure under the field; a display toggle offers Undo; a tool
// call a permission waits on says "Waiting for you"; and on a server that
// cannot run an app action, the command launcher says why instead of
// leaving it out (P7.4).
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/codex/gateway.dart'
    show codexServerCapabilities;
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/widgets/tool_card.dart';

import 'chat_3_support.dart';
import '../support/composer_tools.dart';

class _Chat8Api extends Chat3Api {
  final shells = <String>[];
  final renames = <String>[];
  Object? shellFailure;

  @override
  Future<void> shell(
    String sessionID, {
    required String command,
    required String agent,
    ModelRef? model,
    String? variant,
  }) async {
    final failure = shellFailure;
    if (failure != null) throw failure;
    shells.add(command);
  }

  @override
  Future<void> renameSession(String id, String title) async {
    renames.add(title);
  }
}

class _CodexChat8Api extends _Chat8Api {
  @override
  ServerCapabilities get capabilities => codexServerCapabilities;
}

Future<void> _openLauncher(WidgetTester tester) async {
  await openComposerTools(tester);
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(const Key('composer-tool-commands')));
  await tester.pumpAndSettle();
}

Future<void> _runCommand(WidgetTester tester, String slash) async {
  await _openLauncher(tester);
  await tester.enterText(
    find.byKey(const Key('command-launcher-search')),
    slash,
  );
  await tester.pump();
  final row = find.byKey(Key('command-mobile-$slash'));
  await tester.ensureVisible(row);
  await tester.tap(row);
  await tester.pumpAndSettle();
}

MessageWithParts _runningTool(String callID) => MessageWithParts(
  info: MessageInfo(
    id: 'assistant-1',
    sessionID: 'session-1',
    role: 'assistant',
    time: MsgTime(created: 1),
  ),
  parts: [
    Part(
      id: 'part-$callID',
      callID: callID,
      messageID: 'assistant-1',
      type: 'tool',
      toolName: 'bash',
      toolState: ToolState.fromJson({
        'status': 'running',
        'input': {'command': 'npm test'},
      }, toolName: 'bash'),
    ),
  ],
);

EventEnvelope _asked(String callID) => EventEnvelope(
  type: 'permission.asked',
  properties: {
    'id': 'request-1',
    'sessionID': 'session-1',
    'permission': 'bash',
    'patterns': ['npm test'],
    'metadata': <String, Object?>{},
    'always': <String>[],
    'tool': {'messageID': 'assistant-1', 'callID': callID},
  },
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(chat3MockSecureStorage);

  testWidgets('Run shell command runs from the dialog and keeps a failure', (
    tester,
  ) async {
    final api = _Chat8Api()
      ..shellFailure = const ProductException('Shell is busy');
    final conn = await chat3Controller(api: api);
    addTearDown(conn.dispose);
    await pumpChat3(tester, conn);

    // Run shell command is a command in the sheet (slice-P10.1).
    await _runCommand(tester, 'shell');

    expect(find.byKey(const ValueKey('run-shell-dialog')), findsOneWidget);
    // Says what happens, and why Run waits.
    expect(
      find.text(
        'The agent runs it in this project, and its output joins the '
        'conversation.',
      ),
      findsOneWidget,
    );
    // Nothing is judged before the first try (KitDialog, slice-R2): Run
    // with an empty field says why instead of running.
    expect(find.text('Type a command to run.'), findsNothing);
    await tester.tap(find.byKey(const ValueKey('run-shell-confirm')));
    await tester.pumpAndSettle();
    expect(find.text('Type a command to run.'), findsOneWidget);

    await tester.enterText(
      find.byKey(const ValueKey('run-shell-command')),
      'npm test',
    );
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('run-shell-confirm')));
    await tester.pumpAndSettle();
    // The failure stays with the command, in the dialog.
    expect(find.byKey(const ValueKey('run-shell-dialog')), findsOneWidget);
    expect(find.textContaining('Shell is busy'), findsOneWidget);

    api.shellFailure = null;
    await tester.tap(find.byKey(const ValueKey('run-shell-confirm')));
    await tester.pumpAndSettle();
    expect(api.shells, ['npm test']);
    expect(find.byKey(const ValueKey('run-shell-dialog')), findsNothing);
  });

  testWidgets('a long shell command wraps so it reads whole; Enter runs it, '
      'Shift+Enter starts a new line', (tester) async {
    final api = _Chat8Api();
    final conn = await chat3Controller(api: api);
    addTearDown(conn.dispose);
    await pumpChat3(tester, conn);
    await _runCommand(tester, 'shell');

    final field = find.byKey(const ValueKey('run-shell-command'));
    EditableText editable() => tester.widget<EditableText>(
      find.descendant(of: field, matching: find.byType(EditableText)),
    );
    // Starts at one line and grows to four as the command wraps.
    expect(editable().minLines, 1);
    expect(editable().maxLines, 4);
    final oneLine = tester.getSize(field).height;
    const long =
        'flutter test --concurrency=1 test/revamp/chat_8_test.dart '
        '--plain-name "a long shell command wraps so it reads whole"';
    await tester.enterText(field, long);
    await tester.pumpAndSettle();
    expect(tester.getSize(field).height, greaterThan(oneLine));
    // The start of the command stays in view: nothing scrolled sideways.
    expect(find.textContaining('flutter test --concurrency'), findsWidgets);

    // Shift+Enter adds a line; it does not run anything.
    await tester.sendKeyDownEvent(LogicalKeyboardKey.shift);
    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.shift);
    await tester.pump();
    expect(api.shells, isEmpty);
    expect(editable().controller.text, '$long\n');
    await tester.enterText(field, long);
    await tester.pump();

    // Enter (the keyboard's action) runs it.
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pumpAndSettle();
    expect(api.shells, [long]);
    expect(find.byKey(const ValueKey('run-shell-dialog')), findsNothing);
  });

  testWidgets('Rename conversation is a kit dialog that says why it waits', (
    tester,
  ) async {
    final api = _Chat8Api();
    final conn = await chat3Controller(api: api);
    addTearDown(conn.dispose);
    await pumpChat3(tester, conn);

    // Rename is the conversation menu's (slice-P10.2).
    await tester.tap(find.byTooltip('Conversation menu'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('session-menu-rename')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('rename-session-dialog')), findsOneWidget);
    // An edit that leaves the title empty is judged (KitDialog, slice-R2).
    await tester.enterText(
      find.byKey(const ValueKey('rename-session-title')),
      'x',
    );
    await tester.pump();
    await tester.enterText(
      find.byKey(const ValueKey('rename-session-title')),
      '',
    );
    await tester.pump();
    expect(find.text('Type a title.'), findsOneWidget);

    await tester.enterText(
      find.byKey(const ValueKey('rename-session-title')),
      'Checkout fix',
    );
    await tester.pump();
    await tester.tap(find.widgetWithText(FilledButton, 'Rename'));
    await tester.pumpAndSettle();
    expect(api.renames, ['Checkout fix']);
    expect(find.byKey(const ValueKey('rename-session-dialog')), findsNothing);
  });

  testWidgets('a display toggle offers Undo instead of a snackbar', (
    tester,
  ) async {
    final conn = await chat3Controller(api: _Chat8Api());
    addTearDown(conn.dispose);
    await pumpChat3(tester, conn);
    final before = conn.transcriptTimestampsVisible;

    await _runCommand(tester, 'timestamps');
    expect(conn.transcriptTimestampsVisible, !before);
    expect(find.byType(SnackBar), findsNothing);
    expect(
      find.text(
        before ? 'Message timestamps hidden' : 'Message timestamps shown',
      ),
      findsOneWidget,
    );

    await tester.tap(find.text('Undo'));
    await tester.pumpAndSettle();
    expect(conn.transcriptTimestampsVisible, before);
  });

  testWidgets(
    'the tool call a permission waits on is not repeated above its card',
    (tester) async {
      final api = _Chat8Api()
        ..transcript = [
          chat3Prompt('user-1', 'Run the tests'),
          _runningTool('c1'),
        ];
      final conn = await chat3Controller(api: api);
      addTearDown(conn.dispose);
      await pumpChat3(tester, conn);

      final card = find.byType(ToolCard);
      expect(tester.widget<ToolCard>(card).waitingForYou, isFalse);
      // A shell step can be run again, through the dialog.
      expect(tester.widget<ToolCard>(card).onRerunCommand, isNotNull);

      conn.handleEventForTesting(_asked('another-call'));
      await tester.pump();
      expect(tester.widget<ToolCard>(card).waitingForYou, isFalse);

      conn.handleEventForTesting(_asked('c1'));
      await tester.pump();
      // The request card names the step; the step itself is not repeated above
      // it while it waits.
      expect(card, findsNothing);
    },
  );

  testWidgets('Codex: the launcher says why Files, Review and settings are '
      'missing', (tester) async {
    final conn = await chat3Controller(api: _CodexChat8Api());
    addTearDown(conn.dispose);
    await pumpChat3(tester, conn);
    await _openLauncher(tester);

    for (final capability in [
      'flag:fileBrowsing+terminal',
      'flag:sessionDiff',
      'flag:serverCatalog',
    ]) {
      final row = find.byKey(ValueKey('command-unavailable-$capability'));
      await tester.ensureVisible(row);
      expect(row, findsOneWidget);
    }

    // A search for one such action names it and says why.
    await tester.enterText(
      find.byKey(const Key('command-launcher-search')),
      'files',
    );
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('command-mobile-files')), findsNothing);
    expect(find.byKey(const Key('command-launcher-no-match')), findsNothing);
    final files = find.byKey(
      const ValueKey('command-unavailable-flag:fileBrowsing+terminal'),
    );
    expect(
      find.descendant(of: files, matching: find.text('Project files')),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: files,
        matching: find.textContaining("doesn't share its files"),
      ),
      findsOneWidget,
    );
  });

  testWidgets('OpenCode lists every action, with nothing to explain', (
    tester,
  ) async {
    final conn = await chat3Controller(api: _Chat8Api());
    addTearDown(conn.dispose);
    await pumpChat3(tester, conn);
    await _openLauncher(tester);
    expect(find.byKey(const Key('command-launcher-unavailable')), findsNothing);
  });
}
