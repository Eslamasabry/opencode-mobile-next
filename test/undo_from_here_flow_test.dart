// slice-P3.7b: one "Undo from here" flow. Every door (a prompt's own menu,
// the conversation menu) lands on the same sheet (stage-revert-sheet) on
// every server: staged with review where the server allows it, an honest
// one-step confirm otherwise. The old "Revert from this prompt?" confirm is
// gone.
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'support/fake_server_gateway.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/domain/run_result.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/staged_revert_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'support/composer_tools.dart';

MessageWithParts _user(String id, String text, int created) => MessageWithParts(
  info: MessageInfo(
    id: id,
    sessionID: 'session-1',
    role: 'user',
    time: MsgTime(created: created, completed: created),
  ),
  parts: [Part(id: '$id-p', type: 'text', text: text)],
);

MessageWithParts _reply(
  String id,
  String parent,
  int created, {
  String text = 'Done.',
  List<String> edits = const [],
}) => MessageWithParts(
  info: MessageInfo(
    id: id,
    sessionID: 'session-1',
    role: 'assistant',
    parentID: parent,
    finish: 'stop',
    time: MsgTime(created: created, completed: created + 1),
  ),
  parts: [
    for (final path in edits)
      Part(
        id: '$id-$path',
        type: 'tool',
        toolName: 'edit',
        toolState: ToolState(status: 'completed', input: {'filePath': path}),
      ),
    Part(id: '$id-t', type: 'text', text: text),
  ],
);

/// msg_1 "Add a settings screen" → reply editing two files; msg_3 "Rename
/// the title" → reply editing one of them again.
List<MessageWithParts> _transcript() => [
  _user('msg_1', 'Add a settings screen', 1),
  _reply(
    'msg_2',
    'msg_1',
    2,
    text: 'Added the settings screen.',
    edits: ['lib/settings.dart', 'lib/main.dart'],
  ),
  _user('msg_3', 'Rename the title', 3),
  _reply(
    'msg_4',
    'msg_3',
    4,
    text: 'Renamed the title.',
    edits: ['lib/main.dart'],
  ),
];

class _Api extends FakeServerGateway {
  _Api() : super(baseUrl: 'http://localhost');
  final List<MessageWithParts> transcript = _transcript();
  Session value = Session(id: 'session-1', title: 'Settings work');

  @override
  Future<List<Session>> sessions() async => [value];
  @override
  Future<Map<String, String>> sessionStatuses() async => const {};
  @override
  Future<Session> session(String id) async => value;
  @override
  Future<List<MessageWithParts>> messages(String id) async => transcript;
  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? transcript : const []);
}

/// A server that undoes at once (no staged lifecycle).
class _OneStepRepository implements ProductRepository {
  _OneStepRepository(this.api);
  final _Api api;
  final reverted = <String>[];
  Object? failure;

  @override
  Future<List<CommandInfo>> listCommands() async => const [];
  @override
  Future<List<ReferenceInfo>> listReferences() async => const [];
  @override
  Future<void> revertSession(String id, String messageID) async {
    if (failure case final error?) throw error;
    reverted.add(messageID);
    api.value = api.value.copyWith(
      stagedRevert: SessionRevert(messageID: messageID, snapshot: 'snap'),
    );
  }

  @override
  Future<void> restoreSession(String id) async {
    reverted.add('restore');
    api.value = api.value.copyWith(stagedRevert: null);
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

/// A server with the staged lifecycle.
class _StagedRepository extends _OneStepRepository
    implements StagedRevertGateway {
  _StagedRepository(super.api);
  final staged = <String>[];

  @override
  Future<String?> sessionRevertPrompt(String id, String messageID) async =>
      api.transcript.firstWhere((m) => m.info.id == messageID).parts.first.text;

  @override
  Future<SessionRevert> stageSessionRevert(
    String id,
    String messageID, {
    required bool applyFiles,
  }) async {
    staged.add('$messageID:$applyFiles');
    final result = SessionRevert(
      messageID: messageID,
      snapshot: 'snap',
      files: [FileDiff(file: 'lib/main.dart', before: 'new', after: 'old')],
    );
    api.value = api.value.copyWith(stagedRevert: result);
    return result;
  }

  @override
  Future<void> commitSessionRevert(String id) async {}
  @override
  Future<void> clearSessionRevert(String id) async {}
}

Future<(ConnectionController, _Api, _OneStepRepository)> _pump(
  WidgetTester tester, {
  required bool staged,
}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final api = _Api();
  final repository = staged ? _StagedRepository(api) : _OneStepRepository(api);
  final controller = ConnectionController(ProfileStore(prefs: prefs))
    ..api = api
    ..repository = repository
    ..status = StreamStatus.connected;
  controller.sessionsById['session-1'] = api.value;
  addTearDown(controller.dispose);
  tester.view.physicalSize = const Size(420, 900);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: const ChatScreen(sessionID: 'session-1'),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return (controller, api, repository);
}

/// Door 1: the prompt's own menu (long-press).
Future<void> _undoFromPrompt(WidgetTester tester, String prompt) async {
  await tester.longPress(find.text(prompt).first);
  await tester.pumpAndSettle();
  await tester.tap(find.text('Undo from here'));
  await tester.pumpAndSettle();
}

/// Door 2: the command sheet's "Undo last prompt" (/undo, slice-P10.1).
Future<void> _undoFromMenu(WidgetTester tester) async {
  await openComposerTools(tester);
  await tester.pumpAndSettle();
  final commands = find.byKey(const Key('composer-tool-commands'));
  await tester.ensureVisible(commands);
  await tester.pumpAndSettle();
  await tester.tap(commands);
  await tester.pumpAndSettle();
  await tester.pumpAndSettle();
  await tester.enterText(
    find.byKey(const Key('command-launcher-search')),
    'undo',
  );
  await tester.pump();
  final undo = find.byKey(const Key('command-mobile-undo'));
  await tester.ensureVisible(undo);
  await tester.pumpAndSettle();
  await tester.tap(undo);
  await tester.pumpAndSettle();
}

final _sheet = find.byKey(const ValueKey('stage-revert-sheet'));
final _body = find.byKey(const ValueKey('stage-revert-body'));
final _promptRow = find.byKey(const ValueKey('stage-revert-prompt'));

String _bodyText(WidgetTester tester) => tester
    .widget<Text>(find.descendant(of: _body, matching: find.byType(Text)))
    .data!;

String _quoted(WidgetTester tester) => tester
    .widgetList<Text>(
      find.descendant(of: _promptRow, matching: find.byType(Text)),
    )
    .map((t) => t.data ?? t.textSpan?.toPlainText() ?? '')
    .join();

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('edited paths come from completed edit tools, first seen, once', () {
    final after = _transcript().skip(1);
    expect(RunResult.editedPaths(after), [
      'lib/settings.dart',
      'lib/main.dart',
    ]);
    expect(RunResult.editedPaths(after, max: 1), ['lib/settings.dart']);
    expect(RunResult.editedPaths(_transcript().skip(3)), ['lib/main.dart']);
  });

  group('a server that undoes at once', () {
    testWidgets('both doors open the same one-step sheet: prompt quoted, '
        'count said, edited files listed', (tester) async {
      final (_, _, repository) = await _pump(tester, staged: false);

      await _undoFromPrompt(tester, 'Add a settings screen');
      expect(_sheet, findsOneWidget);
      expect(find.text('Undo from this prompt?'), findsOneWidget);
      expect(_quoted(tester), 'Add a settings screen');
      expect(
        _bodyText(tester),
        'This prompt and the 3 messages after it are removed, and files go '
        'back to how they were before it. You can put them back until you '
        'send another prompt.',
      );
      expect(find.text('Files the agent edited after it'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('stage-revert-edited-lib/settings.dart')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('stage-revert-edited-lib/main.dart')),
        findsOneWidget,
      );
      // No review step and no files switch: the server undoes files anyway.
      expect(find.byKey(const ValueKey('stage-revert-files')), findsNothing);
      expect(find.text('Undo and review'), findsNothing);
      expect(find.text('Undo now'), findsOneWidget);
      // The old "Revert from this prompt?" confirm is gone.
      expect(find.text('Revert from this prompt?'), findsNothing);
      await tester.tapAt(const Offset(10, 10));
      await tester.pumpAndSettle();
      expect(_sheet, findsNothing);
      expect(repository.reverted, isEmpty);

      await _undoFromMenu(tester);
      expect(_sheet, findsOneWidget);
      expect(find.text('Undo from this prompt?'), findsOneWidget);
      expect(_quoted(tester), 'Rename the title');
      expect(
        _bodyText(tester),
        'This prompt and the message after it are removed, and files go back '
        'to how they were before it. You can put them back until you send '
        'another prompt.',
      );
      expect(
        find.byKey(const ValueKey('stage-revert-edited-lib/main.dart')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('stage-revert-edited-lib/settings.dart')),
        findsNothing,
      );
      expect(find.text('Undo now'), findsOneWidget);
      expect(repository.reverted, isEmpty);
    });

    testWidgets('"Undo now" undoes, hides the undone turns and offers the '
        'way back', (tester) async {
      final (_, _, repository) = await _pump(tester, staged: false);
      await _undoFromPrompt(tester, 'Rename the title');
      await tester.tap(find.text('Undo now'));
      await tester.pumpAndSettle();
      expect(repository.reverted, ['msg_3']);
      expect(_sheet, findsNothing);
      expect(find.text('Rename the title'), findsNothing);
      expect(find.text('Renamed the title.'), findsNothing);
      expect(find.text('Add a settings screen'), findsOneWidget);
      expect(find.text('Undone from a prompt'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('chat-status-undo-put-back')),
        findsOneWidget,
      );
      // The way back: the undone turns return.
      await tester.tap(find.byKey(const ValueKey('chat-status-undo-put-back')));
      await tester.pumpAndSettle();
      expect(repository.reverted, ['msg_3', 'restore']);
      expect(find.text('Rename the title'), findsOneWidget);
      expect(find.text('Undone from a prompt'), findsNothing);
    });

    testWidgets('a failed undo keeps the sheet open and says so', (
      tester,
    ) async {
      final (_, _, repository) = await _pump(tester, staged: false);
      repository.failure = StateError('server said no');
      await _undoFromPrompt(tester, 'Rename the title');
      await tester.tap(find.text('Undo now'));
      await tester.pumpAndSettle();
      expect(_sheet, findsOneWidget);
      expect(
        find.text(
          "That didn't finish. Check the conversation, then try again.",
        ),
        findsOneWidget,
      );
      expect(find.text('Rename the title'), findsWidgets);
    });

    testWidgets('a busy conversation keeps the door but turns the undo off', (
      tester,
    ) async {
      final (controller, _, repository) = await _pump(tester, staged: false);
      controller.busySessions.add('session-1');
      await _undoFromPrompt(tester, 'Rename the title');
      expect(_sheet, findsOneWidget);
      expect(
        find.text('Wait for the current conversation action to finish.'),
        findsWidgets,
      );
      await tester.tap(find.text('Undo now'), warnIfMissed: false);
      await tester.pumpAndSettle();
      expect(repository.reverted, isEmpty);
    });
  });

  group('a server with staged undo', () {
    testWidgets('both doors open the same staged sheet for their prompt', (
      tester,
    ) async {
      final (_, _, repository) = await _pump(tester, staged: true);

      await _undoFromPrompt(tester, 'Add a settings screen');
      expect(_sheet, findsOneWidget);
      expect(_quoted(tester), 'Add a settings screen');
      expect(
        _bodyText(tester),
        'This prompt and everything after it are hidden while you review. '
        'Nothing is final until you choose.',
      );
      expect(find.byKey(const ValueKey('stage-revert-files')), findsOneWidget);
      expect(find.text('Undo and review'), findsOneWidget);
      expect(find.text('Undo now'), findsNothing);
      await tester.tapAt(const Offset(10, 10));
      await tester.pumpAndSettle();

      await _undoFromMenu(tester);
      expect(_sheet, findsOneWidget);
      expect(_quoted(tester), 'Rename the title');
      expect(find.text('Undo and review'), findsOneWidget);
      expect((repository as _StagedRepository).staged, isEmpty);
    });

    testWidgets('undo and review stages, then the review page says exactly '
        'how many messages are hidden and would be deleted', (tester) async {
      final (_, _, repository) = await _pump(tester, staged: true);
      await _undoFromPrompt(tester, 'Add a settings screen');
      await tester.tap(find.text('Undo and review'));
      await tester.pumpAndSettle();
      expect((repository as _StagedRepository).staged, ['msg_1:true']);
      expect(find.byType(StagedRevertScreen), findsOneWidget);
      expect(
        find.text(
          'This prompt and the 3 messages after it are hidden. Nothing is '
          'final until you choose below.',
        ),
        findsOneWidget,
      );
      await tester.tap(find.byKey(const ValueKey('commit-staged-revert')));
      await tester.pumpAndSettle();
      expect(
        find.text('The hidden prompt and the 3 messages after it are deleted'),
        findsOneWidget,
      );
    });
  });
}
