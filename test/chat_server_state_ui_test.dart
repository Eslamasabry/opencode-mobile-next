import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/domain/server_gateway.dart'
    show
        BackgroundWorkCapabilities,
        BackgroundWorkGateway,
        PromptDelivery,
        ShellJob,
        ShellJobGateway,
        ShellOutputPage;
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/review_handoff.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/library_screen.dart'
    show IntegrationsScreen;
import 'package:shared_preferences/shared_preferences.dart';

/// Covers the chat UI built on the widened server data: the retry banner,
/// typed assistant error cards, the length footer, and session-row chips.
class _Api extends OpenCodeApi implements BackgroundWorkGateway {
  _Api() : super(baseUrl: 'http://localhost');

  List<MessageWithParts> messagesResult = const [];
  int abortCalls = 0;
  int backgroundCalls = 0;
  BackgroundWorkCapabilities backgroundCapabilities =
      const BackgroundWorkCapabilities(subagents: false);
  final List<String> prompts = [];

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<Map<String, SessionRetryState>> sessionRetryStates() async => const {};

  @override
  Future<List<MessageWithParts>> messages(String id) async =>
      List.of(messagesResult);

  @override
  Future<Session> session(String id) async => Session(id: id);

  @override
  Future<List<FileDiff>> diff(String id) async => const [];

  @override
  Future<List<Todo>> todos(String id) async => const [];

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => const [];

  @override
  Future<void> abort(String sessionID) async {
    abortCalls += 1;
  }

  @override
  Future<BackgroundWorkCapabilities> loadBackgroundWorkCapabilities() async =>
      backgroundCapabilities;

  @override
  Future<bool> moveSessionWorkToBackground(String sessionID) async {
    backgroundCalls += 1;
    return true;
  }

  @override
  Future<void> promptAsync(
    String sessionID, {
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
    List<PromptAgentMention> agentMentions = const [],
    PromptDelivery? delivery,
  }) async {
    prompts.add(text);
  }
}

class _ShellApi extends _Api implements ShellJobGateway {
  List<ShellJob> shellJobs = const [];
  int stopCalls = 0;
  Duration? lastTimeout;

  @override
  Future<List<ShellJob>> listShellJobs() async => List.of(shellJobs);

  @override
  Future<ShellJob> getShellJob(String id) async =>
      shellJobs.singleWhere((shell) => shell.id == id);

  @override
  Future<ShellOutputPage> readShellOutput(
    String id, {
    int? cursor,
    int? limit,
  }) async => const ShellOutputPage(
    output: 'tests passing\n',
    cursor: 14,
    size: 14,
    truncated: false,
  );

  @override
  Future<ShellJob> updateShellTimeout(String id, Duration? timeout) async {
    lastTimeout = timeout;
    return getShellJob(id);
  }

  @override
  Future<void> stopShellJob(String id) async {
    stopCalls += 1;
    shellJobs = shellJobs.where((shell) => shell.id != id).toList();
  }
}

MessageWithParts _assistant(
  String id, {
  String? errorText,
  MessageErrorKind? errorKind,
  String? finish,
  List<Part> parts = const [],
}) => MessageWithParts(
  info: MessageInfo(
    id: id,
    sessionID: 'session-1',
    role: 'assistant',
    errorText: errorText,
    errorKind: errorKind,
    finish: finish,
    time: MsgTime(created: 10, completed: 11),
  ),
  parts: parts,
);

MessageWithParts _user(String id) => MessageWithParts(
  info: MessageInfo(
    id: id,
    sessionID: 'session-1',
    role: 'user',
    time: MsgTime(created: 1, completed: 2),
  ),
  parts: [Part(id: '$id-text', messageID: id, type: 'text', text: 'Hi')],
);

Future<ConnectionController> _controller(_Api api) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final controller = ConnectionController(ProfileStore(prefs: prefs))
    ..api = api
    ..status = StreamStatus.connected;
  addTearDown(controller.dispose);
  return controller;
}

Future<ConnectionController> _pumpChat(WidgetTester tester, _Api api) async {
  final controller = await _controller(api);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: MaterialApp(
        home: ChatScreen(
          sessionID: 'session-1',
          handoffStore: ReviewHandoffStore(),
        ),
      ),
    ),
  );
  await tester.pump();
  await tester.pump();
  return controller;
}

void _status(ConnectionController controller, Object status) {
  controller.handleEventForTesting(
    EventEnvelope(
      type: 'session.status',
      properties: {'sessionID': 'session-1', 'status': status},
    ),
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('retry banner', () {
    test('headline names the attempt and counts down to next', () {
      final now = DateTime(2026, 9, 2, 12);
      expect(
        retryBannerHeadline(
          SessionRetryState(
            attempt: 2,
            next: now.add(const Duration(seconds: 42)),
          ),
          now: now,
        ),
        'Rate limited. Retrying 2 in 0:42',
      );
      expect(
        retryBannerHeadline(
          SessionRetryState(
            attempt: 5,
            next: now.add(const Duration(seconds: 65)),
          ),
          now: now,
        ),
        'Rate limited. Retrying 5 in 1:05',
      );
      expect(
        retryBannerHeadline(
          SessionRetryState(
            attempt: 1,
            next: now.subtract(const Duration(seconds: 3)),
          ),
          now: now,
        ),
        'Rate limited. Retrying 1 in 0:00',
      );
      expect(
        retryBannerHeadline(const SessionRetryState(attempt: 0), now: now),
        'Rate limited. Retrying…',
      );
    });

    testWidgets('appears with a live countdown and Stop, then clears', (
      tester,
    ) async {
      final api = _Api();
      final controller = await _pumpChat(tester, api);
      expect(find.byKey(const ValueKey('retry-banner')), findsNothing);

      final next = DateTime.now().add(const Duration(seconds: 42));
      _status(controller, {
        'type': 'retry',
        'attempt': 2,
        'message': 'Rate limit exceeded, backing off',
        'next': next.millisecondsSinceEpoch,
      });
      await tester.pump();
      await tester.pump();

      expect(find.byKey(const ValueKey('retry-banner')), findsOneWidget);
      expect(
        find.textContaining(RegExp(r'Rate limited\. Retrying 2 in 0:4[12]')),
        findsOneWidget,
      );
      expect(find.text('Rate limit exceeded, backing off'), findsOneWidget);

      // The one-second ticker keeps rebuilding the banner (the countdown text
      // itself reads the wall clock, which the test clock does not move).
      await tester.pump(const Duration(seconds: 3));
      await tester.pump();
      expect(find.byKey(const ValueKey('retry-banner')), findsOneWidget);

      await tester.tap(find.byKey(const Key('retry-banner-stop')));
      await tester.pump();
      await tester.pump();
      expect(api.abortCalls, 1);

      _status(controller, 'idle');
      await tester.pump();
      // Let the attention region's exit animation finish.
      await tester.pump(const Duration(milliseconds: 400));
      expect(find.byKey(const ValueKey('retry-banner')), findsNothing);
    });

    testWidgets('sessions tab marks a retrying session', (tester) async {
      final api = _Api();
      final controller = await _controller(api);
      controller.sessionsById['session-1'] = Session(
        id: 'session-1',
        title: 'Retrying chat',
        time: SessionTime(created: 1, updated: 2),
      );
      controller.retryStates['session-1'] = const SessionRetryState(attempt: 3);
      controller.busySessions.add('session-1');
      await tester.pumpWidget(
        ProviderScope(
          overrides: [connProvider.overrideWithValue(controller)],
          child: MaterialApp(
            home: Scaffold(body: SessionsTab(controller: controller)),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(
        find.byKey(const Key('session-retrying-session-1')),
        findsOneWidget,
      );
      expect(find.text('Retrying'), findsOneWidget);
      expect(find.byType(CircularProgressIndicator), findsNothing);
    });
  });

  group('session row usage chips', () {
    Future<void> pumpRows(
      WidgetTester tester,
      Session session, {
      double textScale = 1,
    }) async {
      final controller = await _controller(_Api());
      controller.sessionsById[session.id] = session;
      await tester.pumpWidget(
        ProviderScope(
          overrides: [connProvider.overrideWithValue(controller)],
          child: MediaQuery(
            data: MediaQueryData(textScaler: TextScaler.linear(textScale)),
            child: MaterialApp(
              home: Scaffold(body: SessionsTab(controller: controller)),
            ),
          ),
        ),
      );
      // The compaction pill spins forever, so settle by hand.
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 600));
    }

    testWidgets('show cost, diff summary and compaction when reported', (
      tester,
    ) async {
      await pumpRows(
        tester,
        Session(
          id: 's1',
          title: 'Usage',
          time: SessionTime(created: 1, updated: 2),
          cost: 0.4249,
          summary: const SessionDiffSummary(
            additions: 120,
            deletions: 34,
            files: 6,
          ),
          compactingSince: DateTime.now(),
        ),
      );
      expect(find.byKey(const Key('session-cost-s1')), findsOneWidget);
      expect(find.text(r'$0.42'), findsOneWidget);
      expect(find.byKey(const Key('session-diff-s1')), findsOneWidget);
      expect(find.textContaining('6 files'), findsOneWidget);
      expect(find.byKey(const Key('session-compacting-s1')), findsOneWidget);
      expect(find.text('Compacting…'), findsOneWidget);
    });

    testWidgets('hide zero cost and empty summaries', (tester) async {
      await pumpRows(
        tester,
        Session(
          id: 's2',
          title: 'Quiet',
          time: SessionTime(created: 1, updated: 2),
          cost: 0,
          summary: const SessionDiffSummary(),
        ),
      );
      expect(find.byKey(const Key('session-cost-s2')), findsNothing);
      expect(find.byKey(const Key('session-diff-s2')), findsNothing);
    });

    testWidgets('drop usage chips at 2x text scale, keep state chips', (
      tester,
    ) async {
      await pumpRows(
        tester,
        Session(
          id: 's3',
          title: 'Large text',
          time: SessionTime(created: 1, updated: 2),
          cost: 1.5,
          summary: const SessionDiffSummary(additions: 1, files: 1),
          compactingSince: DateTime.now(),
        ),
        textScale: 2,
      );
      expect(find.byKey(const Key('session-cost-s3')), findsNothing);
      expect(find.byKey(const Key('session-diff-s3')), findsNothing);
      expect(find.byKey(const Key('session-compacting-s3')), findsOneWidget);
    });
  });

  group('assistant error cards', () {
    testWidgets('context overflow offers Compact session', (tester) async {
      final api = _Api()
        ..messagesResult = [
          _user('u1'),
          _assistant(
            'a1',
            errorText: 'Context window exceeded',
            errorKind: MessageErrorKind.contextOverflow,
          ),
        ];
      await _pumpChat(tester, api);
      await tester.pump(const Duration(milliseconds: 200));
      expect(
        find.byKey(const Key('error-card-context-overflow')),
        findsOneWidget,
      );
      expect(find.text('Context window exceeded'), findsOneWidget);
      await tester.tap(find.byKey(const Key('error-action-compact')));
      await tester.pump();
      // No model is selected in this harness, so the existing compaction
      // path reports its precondition: proof the button reaches it.
      expect(
        find.textContaining('Select a model before compacting'),
        findsOneWidget,
      );
    });

    testWidgets('provider auth opens the providers screen', (tester) async {
      final api = _Api()
        ..messagesResult = [
          _user('u1'),
          _assistant(
            'a1',
            errorText: 'Invalid API key',
            errorKind: MessageErrorKind.providerAuth,
          ),
        ];
      await _pumpChat(tester, api);
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('error-card-provider-auth')), findsOneWidget);
      await tester.tap(find.byKey(const Key('error-action-providers')));
      await tester.pumpAndSettle();
      expect(find.byType(IntegrationsScreen), findsOneWidget);
    });

    testWidgets('output length offers Continue, which sends "Continue"', (
      tester,
    ) async {
      final api = _Api()
        ..messagesResult = [
          _user('u1'),
          _assistant(
            'a1',
            errorText: 'Output too long',
            errorKind: MessageErrorKind.outputLength,
            finish: 'length',
          ),
        ];
      await _pumpChat(tester, api);
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('error-card-output-length')), findsOneWidget);
      // The typed error already says it; the footer is for plain cut-offs.
      expect(find.byKey(const Key('message-length-footer')), findsNothing);
      await tester.tap(find.byKey(const Key('error-action-continue')));
      await tester.pumpAndSettle();
      expect(api.prompts, ['Continue']);
    });

    testWidgets('aborted turns read as a muted Stopped row', (tester) async {
      final api = _Api()
        ..messagesResult = [
          _user('u1'),
          _assistant(
            'a1',
            errorText: 'aborted',
            errorKind: MessageErrorKind.aborted,
          ),
        ];
      await _pumpChat(tester, api);
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('message-stopped')), findsOneWidget);
      expect(find.text('Stopped'), findsOneWidget);
      expect(find.text('aborted'), findsNothing);
      expect(find.byType(TextButton), findsNothing);
    });

    testWidgets('model not found shows one line, Details, and Choose model', (
      tester,
    ) async {
      const raw =
          'ProviderModelNotFoundError: Model not found: openai/gpt-5.6-sol. '
          'Did you mean: gpt-5.6-sol, gpt-5.6-sol-pro?\n'
          '    at <anonymous> (/\$bunfs/root/chunk-gt0nh583.js:439:95275)\n'
          '    at SessionPrompt.getModel (/\$bunfs/root/chunk.js:1096:11500)';
      final api = _Api()
        ..messagesResult = [
          _user('u1'),
          _assistant('a1', errorText: raw, errorKind: MessageErrorKind.unknown),
        ];
      await _pumpChat(tester, api);
      await tester.pumpAndSettle();
      expect(
        find.byKey(const Key('error-card-model-not-found')),
        findsOneWidget,
      );
      expect(
        find.text(
          'Model not found: openai/gpt-5.6-sol. Did you mean: gpt-5.6-sol, '
          'gpt-5.6-sol-pro?',
        ),
        findsOneWidget,
      );
      expect(find.textContaining('at <anonymous>'), findsNothing);
      await tester.tap(find.byKey(const Key('error-action-details')));
      await tester.pumpAndSettle();
      expect(find.textContaining('at <anonymous>'), findsOneWidget);
      await tester.tap(find.text('Close'));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const Key('error-action-choose-model')),
        findsOneWidget,
      );
    });

    testWidgets(
      'unknown errors keep the plain red row; length finish adds a footer',
      (tester) async {
        final api = _Api()
          ..messagesResult = [
            _user('u1'),
            _assistant(
              'a1',
              errorText: 'Something odd',
              errorKind: MessageErrorKind.unknown,
            ),
            _assistant(
              'a2',
              finish: 'length',
              parts: [
                Part(
                  id: 'a2-text',
                  messageID: 'a2',
                  type: 'text',
                  text: 'Partial answer',
                ),
              ],
            ),
          ];
        await _pumpChat(tester, api);
        await tester.pumpAndSettle();
        expect(find.text('Something odd'), findsOneWidget);
        expect(find.byKey(const Key('message-length-footer')), findsOneWidget);
        expect(
          find.text('Answer was cut off by the length limit'),
          findsOneWidget,
        );
      },
    );
  });

  group('background work', () {
    testWidgets('offers a touch action only for a running foreground task', (
      tester,
    ) async {
      final api = _Api()
        ..backgroundCapabilities = const BackgroundWorkCapabilities(
          subagents: true,
        )
        ..messagesResult = [
          _assistant(
            'a1',
            parts: [
              Part(
                id: 'task-1',
                messageID: 'a1',
                type: 'tool',
                toolName: 'task',
                toolState: ToolState(
                  status: 'running',
                  input: const {'description': 'Audit the project'},
                ),
              ),
            ],
          ),
        ];
      await _pumpChat(tester, api);
      await tester.pump(const Duration(milliseconds: 200));

      expect(find.text('Background subagents'), findsOneWidget);
      await tester.tap(find.byKey(const Key('move-work-to-background')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));
      expect(api.backgroundCalls, 1);
      expect(find.text('Running work moved to background.'), findsOneWidget);
    });

    testWidgets('hides promotion for an already-background task', (
      tester,
    ) async {
      final api = _Api()
        ..backgroundCapabilities = const BackgroundWorkCapabilities(
          subagents: true,
        )
        ..messagesResult = [
          _assistant(
            'a1',
            parts: [
              Part(
                id: 'task-1',
                messageID: 'a1',
                type: 'tool',
                toolName: 'task',
                toolState: ToolState(
                  status: 'running',
                  metadata: const {'background': true},
                ),
              ),
            ],
          ),
        ];
      await _pumpChat(tester, api);
      await tester.pump(const Duration(milliseconds: 200));
      expect(find.byKey(const Key('move-work-to-background')), findsNothing);
    });

    testWidgets('Ctrl+B is contextual and does not fire without eligibility', (
      tester,
    ) async {
      final api = _Api()
        ..backgroundCapabilities = const BackgroundWorkCapabilities(
          subagents: true,
        );
      await _pumpChat(tester, api);
      await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
      await tester.sendKeyEvent(LogicalKeyboardKey.keyB);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
      expect(api.backgroundCalls, 0);
    });

    testWidgets('Ctrl+B promotes eligible foreground work', (tester) async {
      final api = _Api()
        ..backgroundCapabilities = const BackgroundWorkCapabilities(
          subagents: true,
        )
        ..messagesResult = [
          _assistant(
            'a1',
            parts: [
              Part(
                id: 'task-1',
                messageID: 'a1',
                type: 'tool',
                toolName: 'task',
                toolState: ToolState(status: 'running'),
              ),
            ],
          ),
        ];
      await _pumpChat(tester, api);
      await tester.pump(const Duration(milliseconds: 200));
      await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
      await tester.sendKeyEvent(LogicalKeyboardKey.keyB);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));
      expect(api.backgroundCalls, 1);
    });

    testWidgets('manages OpenCode 2 shells from the Running work sheet', (
      tester,
    ) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(800, 1000);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);
      final api = _ShellApi()
        ..backgroundCapabilities = const BackgroundWorkCapabilities(
          subagents: true,
          shells: true,
          shellManagement: true,
        )
        ..shellJobs = const [
          ShellJob(
            id: 'sh_01',
            status: 'running',
            command: 'flutter test',
            directory: '/repo',
            startedAt: 1,
            metadata: {'sessionID': 'session-1'},
          ),
        ];
      await _pumpChat(tester, api);
      await tester.pump(const Duration(milliseconds: 200));

      expect(find.text('1 running'), findsOneWidget);
      await tester.tap(find.byKey(const Key('open-running-work')));
      await tester.pumpAndSettle();
      expect(find.text('Running work'), findsOneWidget);
      expect(find.text('flutter test'), findsOneWidget);

      final shellRow = find.byKey(const Key('running-work-shell-sh_01'));
      await tester.ensureVisible(shellRow);
      await tester.tap(shellRow);
      await tester.pumpAndSettle();
      expect(find.textContaining('tests passing'), findsOneWidget);
      await tester.tap(find.byKey(const Key('stop-shell-job')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Stop command').last);
      await tester.pump(const Duration(milliseconds: 200));
      expect(api.stopCalls, 1);
    });
  });
}
