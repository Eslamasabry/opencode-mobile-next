import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/widgets/question_options.dart';
import 'package:opencode_mobile/ui/widgets/tool_card.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _ChatApi extends OpenCodeApi {
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

Future<ConnectionController> _controller(_QuestionRepository repository) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: prefs))
    ..api = _ChatApi()
    ..repository = repository
    ..status = StreamStatus.connected;
}

Future<ConnectionController> _pumpChat(
  WidgetTester tester,
  _QuestionRepository repository,
) async {
  final controller = await _controller(repository);
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

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

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
    expect(find.text('Pick a target'), findsOneWidget);
    expect(find.text('Where should this deploy?'), findsOneWidget);
    expect(find.text('Staging'), findsOneWidget);
    expect(find.text('Test environment'), findsOneWidget);
    expect(find.text('Production'), findsOneWidget);
    expect(find.byType(QuestionOptionRow), findsNWidgets(2));
    // The free-text field rides along when the prompt accepts custom text.
    expect(
      find.byKey(const ValueKey('question-card-custom-0')),
      findsOneWidget,
    );
    // Single-select: a tap is the answer, so no Send button competes with it.
    expect(find.byKey(const Key('question-card-send')), findsNothing);
    expect(find.byKey(const Key('question-card-more')), findsOneWidget);

    // Every option row keeps the 48 dp touch target.
    for (final element in find.byType(QuestionOptionRow).evaluate()) {
      expect(
        tester.getSize(find.byWidget(element.widget)).height,
        greaterThanOrEqualTo(48),
      );
    }
  });

  testWidgets('tapping a single-select choice answers immediately', (
    tester,
  ) async {
    final repository = _QuestionRepository();
    final controller = await _pumpChat(tester, repository);

    controller.handleEventForTesting(_question());
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('question-option-Staging')));
    await tester.pumpAndSettle();

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

  testWidgets('a typed custom answer sends through the Send button', (
    tester,
  ) async {
    final repository = _QuestionRepository();
    final controller = await _pumpChat(tester, repository);

    controller.handleEventForTesting(_question());
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byKey(const ValueKey('question-card-custom-0')),
      'Canary',
    );
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('question-card-send')), findsOneWidget);
    await tester.tap(find.byKey(const Key('question-card-send')));
    await tester.pumpAndSettle();

    expect(repository.answered.single.$2, [
      ['Canary'],
    ]);
  });

  testWidgets('multi-select collects choices behind Send', (tester) async {
    final repository = _QuestionRepository();
    final controller = await _pumpChat(tester, repository);

    controller.handleEventForTesting(_question(multiple: true));
    await tester.pumpAndSettle();

    final send = find.byKey(const Key('question-card-send'));
    expect(send, findsOneWidget);
    expect(tester.widget<FilledButton>(send).onPressed, isNull);

    await tester.tap(find.byKey(const ValueKey('question-option-Staging')));
    await tester.pump();
    // Nothing is sent by a tap on a multi-select prompt.
    expect(repository.answered, isEmpty);
    await tester.tap(find.byKey(const ValueKey('question-option-Production')));
    await tester.pump();
    expect(tester.widget<FilledButton>(send).onPressed, isNotNull);

    await tester.tap(send);
    await tester.pumpAndSettle();

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
    expect(find.byType(QuestionOptionRow), findsNothing);
    expect(find.text('Where should this deploy?'), findsOneWidget);

    await tester.tap(find.byKey(const Key('question-card-answer')));
    await tester.pumpAndSettle();

    // The Activity sheet, with the same option rows.
    expect(find.text('OpenCode needs input'), findsOneWidget);
    expect(find.widgetWithText(FilledButton, 'Send answers'), findsOneWidget);
    expect(find.byType(QuestionOptionRow), findsNWidgets(2));
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

  testWidgets('More opens the full sheet', (tester) async {
    final repository = _QuestionRepository();
    final controller = await _pumpChat(tester, repository);

    controller.handleEventForTesting(_question());
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('question-card-more')));
    await tester.pumpAndSettle();

    expect(find.widgetWithText(FilledButton, 'Send answers'), findsOneWidget);
  });

  testWidgets('a pending permission outranks the question card', (
    tester,
  ) async {
    final repository = _QuestionRepository();
    final controller = await _pumpChat(tester, repository);

    controller.handleEventForTesting(_question());
    controller.handleEventForTesting(_permission());
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('permission-card-allow-once')), findsOneWidget);
    expect(
      find.byKey(const ValueKey('question-card-question-1')),
      findsNothing,
    );
  });

  testWidgets('a failed answer keeps the card and reports the error', (
    tester,
  ) async {
    final repository = _QuestionRepository()
      ..answerError = ApiException('server refused the answer');
    final controller = await _pumpChat(tester, repository);

    controller.handleEventForTesting(_question());
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('question-option-Staging')));
    await tester.pumpAndSettle();

    expect(controller.questions, contains('question-1'));
    expect(
      find.byKey(const ValueKey('question-card-question-1')),
      findsOneWidget,
    );
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
