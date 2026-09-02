import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/activity_screen.dart';
import 'package:opencode_mobile/ui/widgets/question_options.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _QuestionRepository extends ProductRepository {
  _QuestionRepository(this.questions);

  final List<PendingQuestion> questions;
  String? answeredID;
  List<List<String>>? answers;

  @override
  Future<List<PendingQuestion>> listQuestions() async => questions;

  @override
  Future<void> answerQuestion(String id, List<List<String>> answers) async {
    answeredID = id;
    this.answers = answers;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<ConnectionController> _controller(_QuestionRepository repository) async {
  SharedPreferences.setMockInitialValues({});
  final preferences = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: preferences))
    ..repository = repository
    ..status = StreamStatus.connected;
}

PendingQuestion _question({required bool multiple}) => PendingQuestion(
  id: multiple ? 'multiple-question' : 'single-question',
  sessionID: 'session-1',
  prompts: [
    QuestionPrompt(
      title: multiple ? 'Pick targets' : 'Pick a target',
      question: 'Where should this deploy?',
      multiple: multiple,
      custom: true,
      choices: const [
        QuestionChoice(label: 'Staging', description: 'Test environment'),
        QuestionChoice(label: 'Production', description: 'Live environment'),
      ],
    ),
  ],
);

Future<void> _openQuestion(
  WidgetTester tester,
  PendingQuestion question,
  _QuestionRepository repository,
) async {
  final controller = await _controller(repository);
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    MaterialApp(home: ActivityScreen(controller: controller)),
  );
  await tester.pumpAndSettle();
  await tester.tap(find.text(question.prompts.single.title));
  await tester.pumpAndSettle();
}

/// Labels of the option rows currently drawn as selected — the sheet and
/// the inline chat card share [QuestionOptionRow], so this is the one truth.
List<String> _selectedLabels(WidgetTester tester) => [
  for (final row in tester.widgetList<QuestionOptionRow>(
    find.byType(QuestionOptionRow),
  ))
    if (row.selected) row.choice.label,
];

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'custom single answer clears the selected radio and serializes once',
    (tester) async {
      final question = _question(multiple: false);
      final repository = _QuestionRepository([question]);
      await _openQuestion(tester, question, repository);

      await tester.tap(find.text('Staging'));
      await tester.pump();
      expect(_selectedLabels(tester), ['Staging']);

      await tester.enterText(find.byType(TextField), 'Canary');
      await tester.pump();
      expect(_selectedLabels(tester), isEmpty);

      await tester.tap(find.widgetWithText(FilledButton, 'Send answers'));
      await tester.pumpAndSettle();
      expect(repository.answeredID, question.id);
      expect(repository.answers, [
        ['Canary'],
      ]);
    },
  );

  testWidgets(
    'choosing a single radio clears custom text and serializes once',
    (tester) async {
      final question = _question(multiple: false);
      final repository = _QuestionRepository([question]);
      await _openQuestion(tester, question, repository);

      await tester.enterText(find.byType(TextField), 'Canary');
      await tester.pump();
      await tester.tap(find.text('Production'));
      await tester.pump();

      final customField = tester.widget<TextField>(find.byType(TextField));
      expect(customField.controller!.text, isEmpty);
      expect(_selectedLabels(tester), ['Production']);

      await tester.tap(find.widgetWithText(FilledButton, 'Send answers'));
      await tester.pumpAndSettle();
      expect(repository.answers, [
        ['Production'],
      ]);
    },
  );

  testWidgets('multiple choice preserves selected and custom answers', (
    tester,
  ) async {
    final question = _question(multiple: true);
    final repository = _QuestionRepository([question]);
    await _openQuestion(tester, question, repository);

    await tester.tap(find.text('Staging'));
    await tester.enterText(find.byType(TextField), 'Canary');
    await tester.pump();
    await tester.tap(find.widgetWithText(FilledButton, 'Send answers'));
    await tester.pumpAndSettle();

    expect(repository.answers, [
      ['Staging', 'Canary'],
    ]);
  });

  testWidgets('question controls remain reachable with keyboard inset', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(360, 320));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final semantics = tester.ensureSemantics();
    final question = _question(multiple: false);
    final repository = _QuestionRepository([question]);

    try {
      await _openQuestion(tester, question, repository);
      await tester.enterText(find.byType(TextField), 'Canary');
      tester.view.viewInsets = const FakeViewPadding(bottom: 96);
      addTearDown(tester.view.resetViewInsets);
      await tester.pump();

      expect(find.bySemanticsLabel(RegExp('Your answer')), findsOneWidget);
      expect(find.widgetWithText(TextButton, 'Dismiss'), findsOneWidget);
      expect(find.widgetWithText(FilledButton, 'Send answers'), findsOneWidget);
      expect(tester.takeException(), isNull);
    } finally {
      semantics.dispose();
    }
  });
}
