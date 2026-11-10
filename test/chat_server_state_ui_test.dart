import 'support/complete_message_history.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'support/fake_server_gateway.dart';
import 'support/fake_event_channel.dart';
import 'package:opencode_mobile/domain/server_gateway.dart'
    show CatalogModel, CatalogSnapshot, PromptDelivery;
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/review_handoff.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/library_screen.dart'
    show IntegrationsScreen;
import 'package:shared_preferences/shared_preferences.dart';

/// Covers the chat UI built on the widened server data: the retry banner,
/// typed assistant error cards, the length footer, and session-row chips.
/// A controller whose catalog stays what the test set (no server read).
class _CatalogController extends ConnectionController {
  _CatalogController(super.store);

  @override
  Future<void> refreshCatalog() async {}
}

CatalogModel _model(String providerID, String id, String name) => CatalogModel(
  id: id,
  providerID: providerID,
  name: name,
  enabled: true,
  status: 'active',
  contextLimit: 200000,
  outputLimit: 32000,
  reasoning: false,
  attachments: false,
  tools: true,
  variants: const [],
);

class _Api extends FakeServerGateway with CompleteMessageHistory {
  _Api() : super(baseUrl: 'http://localhost');

  List<MessageWithParts> messagesResult = const [];
  int abortCalls = 0;
  final List<String> prompts = [];
  final List<ModelRef?> promptModels = [];

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
    promptModels.add(model);
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

Future<ConnectionController> _controller(
  _Api api, {
  List<CatalogModel>? models,
}) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles':
        '[{"id":"profile","name":"Test","baseUrl":"http://localhost"}]',
    'oc.activeProfile': 'profile',
  });
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(secure, (_) async => null);
  addTearDown(() => messenger.setMockMethodCallHandler(secure, null));
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.load();
  final controller =
      (models == null ? ConnectionController(store) : _CatalogController(store))
        ..api = api
        ..status = StreamStatus.connected;
  if (models != null) {
    controller.catalog = CatalogSnapshot(
      providers: const [],
      models: models,
      agents: const [],
    );
  }
  addTearDown(controller.dispose);
  return controller;
}

Future<ConnectionController> _pumpChat(
  WidgetTester tester,
  _Api api, {
  List<CatalogModel>? models,
}) async {
  final controller = await _controller(api, models: models);
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
    test('a retry is only called a rate limit when it is one', () {
      final now = DateTime(2026, 9, 2, 12);
      // The server retries for many reasons; your screenshot's was a dropped
      // socket under a "Rate limited" title.
      expect(
        retryBannerHeadline(
          SessionRetryState(
            attempt: 2,
            next: now.add(const Duration(seconds: 42)),
            message: 'ECONNRESET: The socket connection was closed',
          ),
          now: now,
        ),
        'Retrying 2 in 0:42',
      );
      expect(
        retryBannerHeadline(
          SessionRetryState(attempt: 1, message: 'Overloaded'),
          now: now,
        ),
        'Retrying 1…',
      );
      expect(
        retryBannerHeadline(
          SessionRetryState(
            attempt: 3,
            next: now.add(const Duration(seconds: 5)),
            message: '429 Too Many Requests',
          ),
          now: now,
        ),
        'Rate limited. Retrying 3 in 0:05',
      );
    });

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
        // Past due: no clock stuck at 0:00.
        'Rate limited. Retrying 1…',
      );
      expect(
        retryBannerHeadline(const SessionRetryState(attempt: 0), now: now),
        'Rate limited. Retrying…',
      );
    });

    testWidgets('appears with a live countdown and Stop, then clears', (
      tester,
    ) async {
      // Stop sits on the running turn's live line, so the turn has a prompt.
      final api = _Api()..messagesResult = [_user('u1')];
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
      // The title already says it is a rate limit; the server's sentence
      // saying so again is not repeated under it.
      expect(find.text('Rate limit exceeded, backing off'), findsNothing);

      // The one-second ticker keeps rebuilding the banner (the countdown text
      // itself reads the wall clock, which the test clock does not move).
      await tester.pump(const Duration(seconds: 3));
      await tester.pump();
      expect(find.byKey(const ValueKey('retry-banner')), findsOneWidget);

      await tester.tap(find.byKey(const Key('chat-stop-button')));
      await tester.pump();
      await tester.pump();
      expect(api.abortCalls, 1);

      _status(controller, 'idle');
      await tester.pump();
      // Let the attention region's exit animation finish.
      await tester.pump(const Duration(milliseconds: 400));
      expect(find.byKey(const ValueKey('retry-banner')), findsNothing);
    });
  });

  group('a prompt that got no answer', () {
    const modelMissing =
        'ProviderModelNotFoundError: Model not found: openai/gpt-5.6-sol. '
        'Did you mean: gpt-5.6-sol, gpt-5.6-sol-pro?\n'
        '    at <anonymous> (/\$bunfs/root/chunk.js:439:95275)';

    testWidgets('the server\'s suggested model is one tap: switch and resend', (
      tester,
    ) async {
      final api = _Api()
        ..messagesResult = [
          _user('u1'),
          _assistant(
            'a1',
            errorText: modelMissing,
            errorKind: MessageErrorKind.unknown,
          ),
        ];
      final controller = await _pumpChat(
        tester,
        api,
        models: [_model('openai', 'gpt-5.6-sol-pro', 'GPT-5.6 Sol Pro')],
      );
      await tester.pumpAndSettle();
      // The prompt itself says it got no answer, right above the error.
      expect(find.byKey(const ValueKey('prompt-not-answered-u1')), findsOne);
      expect(find.text('Not answered'), findsOneWidget);
      final use = find.byKey(const Key('error-action-use-suggestion'));
      expect(use, findsOneWidget);
      expect(find.text('Use GPT-5.6 Sol Pro and resend'), findsOneWidget);
      await tester.tap(use);
      await tester.pumpAndSettle();
      expect(api.prompts, ['Hi']);
      expect(api.promptModels.single?.providerID, 'openai');
      expect(api.promptModels.single?.modelID, 'gpt-5.6-sol-pro');
      expect(
        controller.modelForSession('session-1')?.modelID,
        'gpt-5.6-sol-pro',
      );
    });

    testWidgets('a prompt the server refused before any step: the status '
        'line offers the suggested model and resends', (tester) async {
      final api = _Api()..messagesResult = [_user('u1')];
      final controller = await _pumpChat(
        tester,
        api,
        models: [_model('openai', 'gpt-5.6-sol-pro', 'GPT-5.6 Sol Pro')],
      );
      await tester.pumpAndSettle();
      expect(find.text('Not answered'), findsNothing);
      controller.handleEventForTesting(
        EventEnvelope(
          type: 'session.error',
          properties: {
            'sessionID': 'session-1',
            'error': {
              'name': 'ProviderModelNotFoundError',
              'data': {'message': modelMissing},
            },
          },
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Not answered'), findsOneWidget);
      final use = find.byKey(const ValueKey('prompt-error-use-suggestion'));
      expect(use, findsOneWidget);
      await tester.tap(use);
      await tester.pumpAndSettle();
      expect(api.prompts, ['Hi']);
      expect(api.promptModels.single?.modelID, 'gpt-5.6-sol-pro');
    });

    testWidgets('without a model the server has, Choose model stays', (
      tester,
    ) async {
      final api = _Api()
        ..messagesResult = [
          _user('u1'),
          _assistant(
            'a1',
            errorText: modelMissing,
            errorKind: MessageErrorKind.unknown,
          ),
        ];
      await _pumpChat(tester, api, models: const []);
      await tester.pumpAndSettle();
      expect(
        find.byKey(const Key('error-action-use-suggestion')),
        findsNothing,
      );
      expect(
        find.byKey(const Key('error-action-choose-model')),
        findsOneWidget,
      );
    });

    testWidgets(
      'any other failure offers Send again, which resends the prompt',
      (tester) async {
        final api = _Api()
          ..messagesResult = [
            _user('u1'),
            _assistant(
              'a1',
              errorText: 'The provider is overloaded. Please retry.',
              errorKind: MessageErrorKind.unknown,
            ),
          ];
        await _pumpChat(tester, api);
        await tester.pumpAndSettle();
        expect(find.text('Not answered'), findsOneWidget);
        await tester.tap(find.byKey(const Key('error-action-resend')));
        await tester.pumpAndSettle();
        expect(api.prompts, ['Hi']);
      },
    );

    testWidgets('a provider failure on the newest reply offers a quiet '
        'Continue with another agent that opens the sheet, never by itself', (
      tester,
    ) async {
      // A prompt with a file cannot simply be sent again, so nothing else
      // fixes this failure and the way on is inline.
      final prompt = _user('u1');
      prompt.parts.add(Part(id: 'u1-f', messageID: 'u1', type: 'file'));
      final api = _Api()
        ..messagesResult = [
          prompt,
          _assistant(
            'a1',
            errorText: 'The provider is overloaded. Please retry.',
            errorKind: MessageErrorKind.unknown,
          ),
        ];
      await _pumpChat(tester, api);
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('continue-with-sheet')), findsNothing);
      await tester.tap(find.byKey(const Key('error-action-continue-with')));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('continue-with-sheet')), findsOneWidget);
    });

    testWidgets('no Continue with another agent for an older failure, a '
        'content-filter refusal or a healthy reply', (tester) async {
      final api = _Api()
        ..messagesResult = [
          _user('u1'),
          _assistant(
            'a1',
            errorText: 'Something odd',
            errorKind: MessageErrorKind.unknown,
          ),
          _user('u2'),
          _assistant(
            'a2',
            parts: [
              Part(id: 'a2-t', messageID: 'a2', type: 'text', text: 'Done.'),
            ],
          ),
        ];
      await _pumpChat(tester, api);
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('error-action-continue-with')), findsNothing);
      final filtered = _Api()
        ..messagesResult = [
          _user('u1'),
          _assistant(
            'a1',
            errorText: 'Blocked by the content filter.',
            errorKind: MessageErrorKind.contentFilter,
          ),
        ];
      await _pumpChat(tester, filtered);
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('error-action-continue-with')), findsNothing);
    });

    testWidgets('a turn that ended with nothing and no error says so and '
        'offers Send again', (tester) async {
      final api = _Api()..messagesResult = [_user('u1'), _assistant('a1')];
      await _pumpChat(tester, api);
      await tester.pumpAndSettle();
      expect(find.text('No reply came back'), findsOneWidget);
      // One way to say it: not also "Not answered".
      expect(find.text('Not answered'), findsNothing);
      await tester.tap(find.byKey(const Key('no-reply-send-again')));
      await tester.pumpAndSettle();
      expect(api.prompts, ['Hi']);
    });

    testWidgets('an answered turn, and an older failure, offer no resend', (
      tester,
    ) async {
      final api = _Api()
        ..messagesResult = [
          _user('u1'),
          _assistant(
            'a1',
            errorText: 'Something odd',
            errorKind: MessageErrorKind.unknown,
          ),
          _user('u2'),
          _assistant(
            'a2',
            parts: [
              Part(id: 'a2-t', messageID: 'a2', type: 'text', text: 'Done.'),
            ],
          ),
        ];
      await _pumpChat(tester, api);
      await tester.pumpAndSettle();
      expect(find.text('Not answered'), findsNothing);
      expect(find.byKey(const Key('error-action-resend')), findsNothing);
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
      await tester.pumpAndSettle();
      expect(
        find.byKey(const Key('error-card-context-overflow')),
        findsOneWidget,
      );
      // Words, not the server's text (that is under Error details).
      expect(
        find.text('This conversation is too long for the model.'),
        findsOneWidget,
      );
      expect(find.text('Context window exceeded'), findsNothing);
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
      final message = find.byKey(const Key('message-stopped'));
      expect(message, findsOneWidget);
      // The turn's end line says it in words; the raw reason stays out.
      // Nobody pressed Stop in this app, so it does not say "You".
      expect(
        find.text('This reply was stopped before it finished.'),
        findsOneWidget,
      );
      expect(find.text('You stopped this reply.'), findsNothing);
      expect(find.text('aborted'), findsNothing);
      expect(
        find.descendant(of: message, matching: find.byType(TextButton)),
        findsNothing,
      );
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
      // One line in words; the server's text only under Details.
      expect(find.text("The server doesn't have this model."), findsOneWidget);
      expect(find.textContaining('Did you mean'), findsNothing);
      expect(find.textContaining('at <anonymous>'), findsNothing);
      await tester.tap(find.byKey(const Key('error-action-details')));
      await tester.pumpAndSettle();
      expect(find.textContaining('at <anonymous>'), findsOneWidget);
      expect(find.textContaining('Did you mean'), findsOneWidget);
      await tester.tap(find.byTooltip('Close'));
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
        // An error the app does not know reads in plain words; the
        // server's text is under Error details.
        expect(
          find.text('The agent stopped because of an error.'),
          findsOneWidget,
        );
        expect(find.text('Something odd'), findsNothing);
        expect(find.byKey(const Key('message-length-footer')), findsOneWidget);
        expect(
          find.text('Answer was cut off by the length limit'),
          findsOneWidget,
        );
      },
    );
  });
}
