import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/domain/run_result.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/run_result_screen.dart';
import 'package:opencode_mobile/ui/widgets/run_result_view.dart';
import 'package:opencode_mobile/ui/widgets/tool_card.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Repository implements ProductRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

/// Serves scripted pages: page index → items, with a cursor to the next.
class _Gateway implements ServerGateway {
  _Gateway(this.pages, {this.failure});
  final List<List<MessageWithParts>> pages;
  final Object? failure;
  final requestedCursors = <String?>[];
  Completer<void>? barrier;

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async {
    requestedCursors.add(cursor);
    await barrier?.future;
    if (failure != null) throw failure!;
    final index = cursor == null ? 0 : int.parse(cursor);
    final hasMore = index + 1 < pages.length;
    return ServerPage(
      items: pages[index],
      nextCursor: hasMore ? '${index + 1}' : null,
    );
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Controller extends ConnectionController {
  _Controller(super.store);
  ServerGateway? transport;
  ServerProfile? selectedProfile;
  @override
  ServerProfile? get profile => selectedProfile ?? super.profile;

  @override
  Future<ServerGateway?> prepareActionTransport() async => transport;
}

MessageWithParts _msg(
  String id, {
  required String role,
  required int created,
  int? completed,
  String? finish,
  String? error,
  List<Part> parts = const [],
}) => MessageWithParts(
  info: MessageInfo(
    id: id,
    sessionID: 'ses_1',
    role: role,
    agent: role == 'assistant' ? 'build' : null,
    modelID: role == 'assistant' ? 'gpt-5' : null,
    time: MsgTime(created: created, completed: completed),
    errorText: error,
    finish: finish,
  ),
  parts: parts,
);

Part _tool(
  String id,
  String name, {
  String status = 'completed',
  Map<String, dynamic> input = const {},
  Map<String, dynamic>? metadata,
  String output = '',
  bool pruned = false,
}) => Part(
  id: id,
  callID: id,
  type: 'tool',
  toolName: name,
  toolState: ToolState(
    status: status,
    input: input,
    output: output,
    metadata: metadata,
    pruned: pruned,
  ),
);

final _run = [
  _msg(
    'a-final',
    role: 'assistant',
    created: 40,
    completed: 41,
    finish: 'stop',
    parts: [
      _tool(
        'c-test',
        'bash',
        input: {'command': 'flutter test test/run_result_test.dart'},
        metadata: {'exit': 0},
        output: '00:03 +12: All tests passed!',
      ),
    ],
  ),
  _msg(
    'a-edit',
    role: 'assistant',
    created: 30,
    completed: 31,
    finish: 'tool-calls',
    parts: [
      _tool('e1', 'edit', input: {'filePath': 'lib/domain/run_result.dart'}),
      _tool('c-ls', 'bash', input: {'command': 'ls lib'}),
    ],
  ),
  _msg('u-prompt', role: 'user', created: 20),
  _msg(
    'a-previous',
    role: 'assistant',
    created: 10,
    completed: 11,
    finish: 'stop',
    parts: [
      _tool('e-old', 'edit', input: {'filePath': 'lib/old.dart'}),
    ],
  ),
];

Future<_Controller> _controller(ServerGateway? transport) async {
  SharedPreferences.setMockInitialValues({});
  final preferences = await SharedPreferences.getInstance();
  final controller = _Controller(ProfileStore(prefs: preferences))
    ..repository = _Repository()
    ..status = StreamStatus.connected
    ..transport = transport;
  controller.sessionsById = {
    'ses_1': Session(
      id: 'ses_1',
      title: 'Add run results',
      directory: '/work/oc',
      time: SessionTime(created: 1, updated: 2),
    ),
  };
  return controller;
}

Widget _app(Widget home, {Map<String, WidgetBuilder> routes = const {}}) =>
    MaterialApp(
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: home,
      routes: routes,
    );

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('loadRunHistory', () {
    test('walks older pages only until the user boundary is loaded', () async {
      final gateway = _Gateway([
        [_run[0]],
        [_run[1]],
        [_run[2]],
        [_run[3]],
      ]);
      final loaded = await loadRunHistory(
        gateway,
        'ses_1',
        isCurrent: () => true,
      );
      expect(gateway.requestedCursors, [null, '1', '2']);
      expect(loaded.messages.map((m) => m.info.id), [
        'u-prompt',
        'a-edit',
        'a-final',
      ]);
      expect(loaded.complete, isFalse);
      final result = RunResult.fromMessages('ses_1', loaded.messages)!;
      expect(result.boundaryKnown, isTrue);
      expect(result.stepCount, 2);
    });

    test('stops at the page cap and reports the history as partial', () async {
      final gateway = _Gateway([
        for (var i = 0; i < 8; i++)
          [
            _msg(
              'a$i',
              role: 'assistant',
              created: 100 - i,
              completed: 101 - i,
              finish: 'stop',
            ),
          ],
        [_run[2]],
      ]);
      final loaded = await loadRunHistory(
        gateway,
        'ses_1',
        isCurrent: () => true,
      );
      expect(gateway.requestedCursors.length, RunResultScreen.maxPages);
      expect(loaded.complete, isFalse);
      final result = RunResult.fromMessages(
        'ses_1',
        loaded.messages,
        historyComplete: loaded.complete,
      )!;
      expect(result.boundaryKnown, isFalse);
    });

    test('an exhausted history counts as a known boundary', () async {
      final gateway = _Gateway([
        [
          _msg(
            'a0',
            role: 'assistant',
            created: 1,
            completed: 2,
            finish: 'stop',
          ),
        ],
      ]);
      final loaded = await loadRunHistory(
        gateway,
        'ses_1',
        isCurrent: () => true,
      );
      expect(loaded.complete, isTrue);
      expect(
        RunResult.fromMessages(
          'ses_1',
          loaded.messages,
          historyComplete: loaded.complete,
        )!.boundaryKnown,
        isTrue,
      );
    });
  });

  group('RunResultView', () {
    testWidgets('shows server facts, source labels and explicit unknowns', (
      tester,
    ) async {
      final result = RunResult.fromMessages('ses_1', _run)!;
      await tester.pumpWidget(
        _app(
          Scaffold(
            body: RunResultView(
              result: result,
              observedLive: false,
              onOpenConversation: () {},
              sessionTitle: 'Add run results',
            ),
          ),
        ),
      );
      expect(find.text('Add run results'), findsOneWidget);
      expect(find.text('Run …a-edit'), findsOneWidget);
      expect(find.text('2 assistant steps'), findsOneWidget);
      expect(find.text('build · gpt-5'), findsOneWidget);
      expect(find.text('Completed'), findsOneWidget);
      expect(find.text('Provider finish reason: stop'), findsOneWidget);
      expect(find.byKey(const Key('run-result-history')), findsOneWidget);
      expect(find.byKey(const Key('run-result-partial')), findsNothing);
      // Only this run's edit, never the previous run's.
      expect(find.text('lib/domain/run_result.dart'), findsOneWidget);
      expect(find.text('lib/old.dart'), findsNothing);
      expect(find.text('Edited'), findsOneWidget);
      // Commands: recorded exit code vs explicit unknown, textual test label.
      expect(
        find.text('flutter test test/run_result_test.dart'),
        findsOneWidget,
      );
      expect(
        find.text(
          'Exit code 0 · Looks like a test command (from the command text only)',
        ),
        findsOneWidget,
      );
      expect(find.text('ls lib'), findsOneWidget);
      expect(find.text('Exit code not recorded'), findsOneWidget);
      await tester.scrollUntilVisible(
        find.byKey(const Key('run-result-open-conversation')),
        200,
        scrollable: find.byType(Scrollable).first,
      );
      expect(
        find.byKey(const Key('run-result-open-conversation')),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('opens the recorded tool output without re-fetching', (
      tester,
    ) async {
      final result = RunResult.fromMessages('ses_1', _run)!;
      await tester.pumpWidget(
        _app(
          Scaffold(
            body: RunResultView(
              result: result,
              observedLive: true,
              onOpenConversation: () {},
            ),
          ),
        ),
      );
      expect(find.byKey(const Key('run-result-observed')), findsOneWidget);
      await tester.tap(find.text('flutter test test/run_result_test.dart'));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('run-result-output-sheet')), findsOneWidget);
      expect(find.text('Recorded tool output'), findsOneWidget);
      expect(find.byType(ToolCard), findsOneWidget);
      // Expand the card the way the transcript does and read the record.
      await tester.tap(find.byType(ToolCard));
      await tester.pumpAndSettle();
      expect(find.textContaining('All tests passed!'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('no tool calls reads as no evidence, and partial history is '
        'flagged', (tester) async {
      final result = RunResult.fromMessages('ses_1', [
        _msg(
          'a-only',
          role: 'assistant',
          created: 5,
          completed: 6,
          error: 'ProviderError: quota exceeded',
        ),
      ])!;
      await tester.pumpWidget(
        _app(
          Scaffold(
            body: RunResultView(
              result: result,
              observedLive: false,
              onOpenConversation: () {},
            ),
          ),
        ),
      );
      expect(find.byKey(const Key('run-result-partial')), findsOneWidget);
      expect(find.text('At least 1 assistant step loaded'), findsOneWidget);
      expect(find.text('Failed'), findsOneWidget);
      expect(find.text('quota exceeded'), findsOneWidget);
      expect(find.byKey(const Key('run-result-no-tools')), findsOneWidget);
      expect(find.byKey(const Key('run-result-no-files')), findsNothing);
      expect(find.text('The provider gave no finish reason.'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  });

  for (final loading in [false, true]) {
    for (final change in ['profile', 'connection', 'location']) {
      testWidgets(
        'same-controller $change change permanently invalidates ${loading ? 'in-flight' : 'loaded'} results',
        (tester) async {
          final gateway = _Gateway([_run]);
          if (loading) gateway.barrier = Completer<void>();
          final c = await _controller(gateway);
          addTearDown(c.dispose);
          var opened = false;
          await tester.pumpWidget(
            _app(
              RunResultScreen(controller: c, sessionID: 'ses_1'),
              routes: {
                '/chat/ses_1': (_) {
                  opened = true;
                  return const Scaffold();
                },
              },
            ),
          );
          await tester.pump();
          if (!loading) {
            await tester.pumpAndSettle();
            expect(find.text('Completed'), findsOneWidget);
          }
          if (change == 'profile') {
            c.selectedProfile = ServerProfile(
              id: 'b',
              name: 'B',
              baseUrl: 'http://b',
            );
          } else if (change == 'connection') {
            c.connectionRevision++;
          } else {
            c.locationRevision++;
            c.directory = '/different';
          }
          c.notifyListeners();
          await tester.pump();
          gateway.barrier?.complete();
          await tester.pumpAndSettle();
          expect(
            find.byKey(const Key('run-result-scope-changed')),
            findsOneWidget,
          );
          expect(find.byType(RunResultView), findsNothing);
          expect(
            find.byKey(const Key('run-result-open-conversation')),
            findsNothing,
          );
          expect(find.text('Retry'), findsNothing);
          final replacement = _Gateway([_run]);
          c.transport = replacement;
          await tester
              .widget<RefreshIndicator>(find.byType(RefreshIndicator))
              .onRefresh();
          await tester.pumpAndSettle();
          expect(replacement.requestedCursors, isEmpty);
          expect(opened, isFalse);
          expect(tester.takeException(), isNull);
        },
      );
    }
  }

  test(
    'equal timestamps spanning older pages retain the user boundary and step order',
    () async {
      final gateway = _Gateway([
        [
          _msg(
            'a-last',
            role: 'assistant',
            created: 100,
            completed: 120,
            finish: 'stop',
          ),
        ],
        [
          _msg('z-user', role: 'user', created: 100),
          _msg('x-first', role: 'assistant', created: 100, completed: 110),
        ],
      ]);
      final loaded = await loadRunHistory(
        gateway,
        'ses_1',
        isCurrent: () => true,
      );
      final result = RunResult.fromMessages('ses_1', loaded.messages)!;
      expect(result.userMessageID, 'z-user');
      expect(result.lastStepID, 'a-last');
      expect(result.stepCount, 2);
    },
  );

  group('RunResultScreen', () {
    testWidgets('loads history, binds observation to the exact step, opens '
        'the conversation', (tester) async {
      final controller = await _controller(_Gateway([_run]));
      addTearDown(controller.dispose);
      var openedChat = false;
      await tester.pumpWidget(
        _app(
          RunResultScreen(controller: controller, sessionID: 'ses_1'),
          routes: {
            '/chat/ses_1': (_) {
              openedChat = true;
              return const Scaffold(body: Text('chat'));
            },
          },
        ),
      );
      expect(find.byKey(const Key('run-result-loading')), findsOneWidget);
      await tester.pumpAndSettle();
      expect(find.text('Run results'), findsOneWidget);
      expect(find.text('Completed'), findsOneWidget);
      expect(find.byKey(const Key('run-result-history')), findsOneWidget);

      // A live completion for a DIFFERENT message must not count.
      controller.observedCompletedMessageIDs.add('a-edit');
      controller.notifyListeners();
      await tester.pump();
      expect(find.byKey(const Key('run-result-history')), findsOneWidget);
      controller.observedCompletedMessageIDs.add('a-final');
      controller.notifyListeners();
      await tester.pump();
      expect(find.byKey(const Key('run-result-observed')), findsOneWidget);

      await tester.scrollUntilVisible(
        find.byKey(const Key('run-result-open-conversation')),
        200,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.tap(find.byKey(const Key('run-result-open-conversation')));
      await tester.pumpAndSettle();
      expect(openedChat, isTrue);
    });

    testWidgets('empty and error states are explicit and retryable', (
      tester,
    ) async {
      final controller = await _controller(
        _Gateway([
          [_msg('u-new', role: 'user', created: 50), ..._run],
        ]),
      );
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(RunResultScreen(controller: controller, sessionID: 'ses_1')),
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('run-result-empty')), findsOneWidget);

      final failing = _Gateway([], failure: const ProductException('offline'));
      final failedController = await _controller(failing);
      addTearDown(failedController.dispose);
      await tester.pumpWidget(
        _app(
          RunResultScreen(
            key: UniqueKey(),
            controller: failedController,
            sessionID: 'ses_1',
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('run-result-error-state')), findsOneWidget);
      expect(find.text('offline'), findsOneWidget);
      failedController.transport = _Gateway([_run]);
      await tester.tap(find.text('Retry'));
      await tester.pumpAndSettle();
      expect(find.text('Completed'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  });
}
