import 'support/complete_message_history.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit_request_card.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/widgets/tool_card.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _ChatApi extends OpenCodeApi with CompleteMessageHistory {
  _ChatApi() : super(baseUrl: 'http://localhost');

  @override
  Future<List<MessageWithParts>> messages(String id) async => [];

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));
}

/// The v1 answer path the Activity sheet already uses; the inline card must
/// land on the same call with the same serialization.
class _QuestionRepository extends ProductRepository {
  final answered = <(String, List<List<String>>)>[];
  Object? answerError;

  @override
  Future<List<PendingQuestion>> listQuestions() async => const [];

  @override
  Future<void> answerQuestion(String id, List<List<String>> answers) async {
    if (answerError case final error?) {
      answerError = null;
      throw error;
    }
    answered.add((id, answers));
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

/// A chat with Claude Code through Paseo: the card names that agent.
class _ClaudeConnection extends ConnectionController {
  _ClaudeConnection(super.store);

  @override
  ServerProfile? get profile => ServerProfile(
    id: 'p1',
    name: 'Phone agents',
    baseUrl: 'ws://127.0.0.1:6767',
    backend: ServerBackend.paseo,
  );
}

Future<ConnectionController> _controller(
  _QuestionRepository repository, {
  bool claude = false,
}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  return (claude ? _ClaudeConnection(store) : ConnectionController(store))
    ..api = _ChatApi()
    ..repository = repository
    ..status = StreamStatus.connected;
}

Future<ConnectionController> _pumpChat(
  WidgetTester tester,
  _QuestionRepository repository, {
  bool claude = false,
}) async {
  final controller = await _controller(repository, claude: claude);
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
    ),
  );
  await tester.pumpAndSettle();
  return controller;
}

EventEnvelope _question({
  String id = 'question-1',
  bool multiple = false,
  bool custom = true,
  String description = 'Test environment',
  int prompts = 1,
}) => EventEnvelope(
  type: 'question.asked',
  properties: {
    'id': id,
    'sessionID': 'session-1',
    'questions': [
      for (var index = 0; index < prompts; index++)
        {
          'header': index == 0 ? 'Pick a target' : 'Prompt ${index + 1}',
          'question': index == 0
              ? 'Where should this deploy?'
              : 'Question ${index + 1}?',
          'multiple': multiple,
          'custom': custom,
          'options': [
            {'label': 'Staging', 'description': description},
            {'label': 'Production', 'description': 'Live environment'},
          ],
        },
    ],
  },
);

EventEnvelope _permission() => EventEnvelope(
  type: 'permission.asked',
  properties: {
    'id': 'request-1',
    'sessionID': 'session-1',
    'permission': 'bash',
    'patterns': ['git status'],
    'metadata': <String, Object?>{},
    'always': <String>[],
  },
);

/// An answer waits out its undo window before it is sent.
Future<void> _afterHold(WidgetTester tester) async {
  await tester.pump(const Duration(seconds: 3));
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  // chat-5: the question is the one request card (KitRequestCard.ask), with
  // the options in place for one answer and "Answer" for anything longer.
  testWidgets('a pending question renders inline with its choices', (
    tester,
  ) async {
    final repository = _QuestionRepository();
    final controller = await _pumpChat(tester, repository);

    controller.handleEventForTesting(_question());
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('question-card-question-1')),
      findsOneWidget,
    );
    final card = tester.widget<KitRequestCard>(find.byType(KitRequestCard));
    expect(card.kind, KitRequestKind.question);
    expect(find.text('Pick a target'), findsOneWidget);
    expect(find.text('Where should this deploy?'), findsOneWidget);
    expect(find.text('Staging'), findsOneWidget);
    expect(find.text('Test environment'), findsOneWidget);
    expect(find.text('Production'), findsOneWidget);
    // A typed answer rides along when the prompt accepts custom text.
    expect(find.text('Something else'), findsOneWidget);
    expect(find.byKey(const Key('question-card-more')), findsOneWidget);

    // Every option row keeps the 48 dp touch target.
    for (final index in [0, 1]) {
      final row = find.byKey(ValueKey('question-card-option-$index'));
      expect(tester.getSize(row).height, greaterThanOrEqualTo(48));
    }
  });

  testWidgets('a Claude Code chat names Claude Code as the one who asks', (
    tester,
  ) async {
    final repository = _QuestionRepository();
    final controller = await _pumpChat(tester, repository, claude: true);

    controller.handleEventForTesting(_question(prompts: 3));
    await tester.pumpAndSettle();

    final card = tester.widget<KitRequestCard>(find.byType(KitRequestCard));
    expect(card.who, 'Claude Code');
    await tester.tap(find.byKey(const Key('question-card-answer')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('question-sheet')), findsOneWidget);
    expect(
      find.textContaining(RegExp('Claude Code.*needs input')),
      findsOneWidget,
    );
  });

  testWidgets('an OpenCode chat keeps "The agent"', (tester) async {
    final repository = _QuestionRepository();
    final controller = await _pumpChat(tester, repository);

    controller.handleEventForTesting(_question());
    await tester.pumpAndSettle();

    final card = tester.widget<KitRequestCard>(find.byType(KitRequestCard));
    expect(card.who, 'The agent');
  });

  testWidgets('tapping a single-select choice answers immediately', (
    tester,
  ) async {
    final repository = _QuestionRepository();
    final controller = await _pumpChat(tester, repository);

    controller.handleEventForTesting(_question());
    await tester.pumpAndSettle();

    await tester.tap(find.text('Staging'));
    await _afterHold(tester);

    expect(repository.answered.single.$1, 'question-1');
    expect(repository.answered.single.$2, [
      ['Staging'],
    ]);
    // The answer resolved the request, so the card leaves the chat.
    expect(controller.questions, isEmpty);
    expect(
      find.byKey(const ValueKey('question-card-question-1')),
      findsNothing,
    );
  });

  testWidgets('a choice is held for 3 s with Undo; Undo sends nothing', (
    tester,
  ) async {
    final repository = _QuestionRepository();
    final controller = await _pumpChat(tester, repository);
    controller.handleEventForTesting(_question());
    await tester.pumpAndSettle();

    await tester.tap(find.text('Staging'));
    await tester.pump(const Duration(seconds: 1));
    await tester.pump(const Duration(milliseconds: 300));
    expect(repository.answered, isEmpty);
    expect(find.textContaining('Staging', findRichText: true), findsOneWidget);
    expect(find.byKey(const Key('question-card-undo')), findsOneWidget);
    expect(find.text('Production'), findsNothing);

    await tester.tap(find.byKey(const Key('question-card-undo')));
    await tester.pumpAndSettle();
    expect(find.text('Production'), findsOneWidget);
    await tester.pump(const Duration(seconds: 5));
    expect(repository.answered, isEmpty);

    await tester.tap(find.text('Production'));
    await tester.pump(const Duration(milliseconds: 2900));
    expect(repository.answered, isEmpty);
    await tester.pump(const Duration(milliseconds: 200));
    await tester.pumpAndSettle();
    expect(repository.answered.single.$2, [
      ['Production'],
    ]);
  });

  testWidgets('a typed answer sends from Something else', (tester) async {
    tester.view.physicalSize = const Size(800, 1400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final repository = _QuestionRepository();
    final controller = await _pumpChat(tester, repository);

    controller.handleEventForTesting(_question());
    await tester.pumpAndSettle();

    await tester.ensureVisible(find.text('Something else'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Something else'));
    await tester.pumpAndSettle();
    final field = find.byKey(const Key('question-card-custom-0'));
    await tester.ensureVisible(field);
    await tester.enterText(field, 'Canary');
    // A multiline answer sends from the field's own Send button.
    await tester.tap(find.byTooltip('Send answer'));
    await _afterHold(tester);

    expect(repository.answered.single.$2, [
      ['Canary'],
    ]);
  });

  testWidgets('multi-select answers in the request sheet with Send', (
    tester,
  ) async {
    final repository = _QuestionRepository();
    final controller = await _pumpChat(tester, repository);

    controller.handleEventForTesting(_question(multiple: true, custom: false));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Answer'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('question-sheet')), findsOneWidget);
    expect(find.text('Choose at least one answer.'), findsOneWidget);

    await tester.tap(find.text('Staging').last);
    await tester.pump();
    await tester.tap(find.text('Production').last);
    await tester.pump();
    // Nothing is sent by a tap on a multi-select prompt.
    expect(repository.answered, isEmpty);

    await tester.tap(find.text('Send').last);
    await _afterHold(tester);

    expect(repository.answered.single.$2, [
      ['Staging', 'Production'],
    ]);
  });

  testWidgets('a long description collapses the card to an Answer button '
      'that opens the sheet', (tester) async {
    final repository = _QuestionRepository();
    final controller = await _pumpChat(tester, repository);

    controller.handleEventForTesting(_question(description: 'x' * 160));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('question-card-answer')), findsOneWidget);
    expect(find.text('Where should this deploy?'), findsOneWidget);

    await tester.tap(find.byKey(const Key('question-card-answer')));
    await tester.pumpAndSettle();

    // The full question sheet, as a form's Answer opens the form.
    expect(find.text('OpenCode needs input'), findsOneWidget);
  });

  testWidgets('more than two prompts also collapse to the Answer button', (
    tester,
  ) async {
    final repository = _QuestionRepository();
    final controller = await _pumpChat(tester, repository);

    controller.handleEventForTesting(_question(prompts: 3));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('question-card-answer')), findsOneWidget);
    expect(find.textContaining('3 questions'), findsOneWidget);
  });

  testWidgets('Details opens the one request sheet', (tester) async {
    final repository = _QuestionRepository();
    final controller = await _pumpChat(tester, repository);

    controller.handleEventForTesting(_question());
    await tester.pumpAndSettle();

    await tester.ensureVisible(find.byKey(const Key('question-card-more')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('question-card-more')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('question-sheet')), findsOneWidget);
  });

  testWidgets('a pending permission outranks the question card', (
    tester,
  ) async {
    final repository = _QuestionRepository();
    final controller = await _pumpChat(tester, repository);

    controller.handleEventForTesting(_question());
    controller.handleEventForTesting(_permission());
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('permission-card-review')), findsOneWidget);
    expect(
      find.byKey(const ValueKey('question-card-question-1')),
      findsNothing,
    );
  });

  testWidgets('a failed answer keeps the card and says why', (tester) async {
    final repository = _QuestionRepository()
      ..answerError = ApiException('server refused the answer');
    final controller = await _pumpChat(tester, repository);

    controller.handleEventForTesting(_question());
    await tester.pumpAndSettle();

    await tester.tap(find.text('Staging'));
    await _afterHold(tester);

    expect(controller.questions, contains('question-1'));
    expect(
      find.byKey(const ValueKey('question-card-question-1')),
      findsOneWidget,
    );
    expect(find.textContaining('Not accepted'), findsOneWidget);
  });

  group('transcript tool card', () {
    Future<void> pumpTool(
      WidgetTester tester,
      Map<String, dynamic> json,
    ) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              child: ToolCard(
                toolName: 'question',
                state: ToolState.fromJson(json, toolName: 'question'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Questions'));
      // A running card animates its status; bounded pumps instead of settle.
      for (var i = 0; i < 5; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
    }

    const input = {
      'questions': [
        {
          'header': 'Pick a target',
          'question': 'Where should this deploy?',
          'options': [
            {'label': 'Staging', 'description': ''},
          ],
        },
      ],
    };

    testWidgets('reads "Answered: <label>" once the server has the answer', (
      tester,
    ) async {
      await pumpTool(tester, const {
        'status': 'completed',
        'input': input,
        'output': 'User answered.',
        'metadata': {
          'answers': [
            ['Staging'],
          ],
        },
      });

      expect(find.text('1 answered'), findsOneWidget);
      expect(find.text('Answered: Staging'), findsOneWidget);
      expect(find.text('No answer'), findsNothing);
    });

    testWidgets('still reads "No answer" while the question is open', (
      tester,
    ) async {
      await pumpTool(tester, const {
        'status': 'running',
        'input': input,
        'metadata': <String, Object?>{},
      });

      expect(find.text('1 asked'), findsOneWidget);
      expect(find.text('No answer'), findsOneWidget);
    });
  });
}
