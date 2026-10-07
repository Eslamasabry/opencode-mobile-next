// Behaviour of slice-P3.11a's merges outside the chat library: each merged
// pair has one landing, and these tests assert the surviving one.
//
// - The Work folder row opens the project sheet with that folder under an
//   open Details (workspace-directory-details-dialog merged into
//   workspace-context-sheet).
// - A Work row's menu opens Conversation context
//   (workspace-session-details-sheet merged into session-context).
// - The question sheet pins Send to its foot and enables it as the person
//   answers (question-sheet-dismiss already confirms through KitConfirm).
//
// The other merged pairs are asserted in the existing tests of their files:
// test/session_command_handoff_test.dart (continue on computer),
// test/revamp/screen_library_3_test.dart (server sign-in, forget, accounts),
// test/revamp/screen_usage_2_test.dart and test/provider_quota_screen_test.dart
// (quota monitoring in place), test/project_hub_test.dart (Manage project's
// tools on the Project tab) and test/session_context_screen_test.dart.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

final _en = lookupAppLocalizations(const Locale('en'));

class _Repository implements ProductRepository {
  int listProjectsCalls = 0;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<WorkspaceProject>> listProjects() async {
    listProjectsCalls++;
    return [];
  }

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => [];

  @override
  Future<List<TerminalProcess>> listTerminals() async => [];

  @override
  Future<CatalogSnapshot> loadCatalog() async =>
      const CatalogSnapshot(providers: [], models: [], agents: []);

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void _phone(WidgetTester tester) {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

class _Answers extends ConnectionController {
  _Answers(super.store);

  List<List<String>>? answers;

  @override
  Future<void> refreshPendingQuestions() async {}

  @override
  Future<void> answerQuestion(
    String id,
    List<List<String>> answers, {
    PendingRequestIdentity? expectedRequest,
  }) async {
    this.answers = answers;
  }
}

const _question = PendingQuestion(
  id: 'q-1',
  sessionID: 'ses-1',
  prompts: [
    QuestionPrompt(
      title: 'Target',
      question: 'Where should this deploy?',
      multiple: false,
      custom: false,
      choices: [
        QuestionChoice(label: 'Staging', description: 'Test first'),
        QuestionChoice(label: 'Production', description: 'Live'),
      ],
    ),
  ],
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('question sheet', () {
    Future<_Answers> open(
      WidgetTester tester, {
      PendingQuestion question = _question,
    }) async {
      _phone(tester);
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final controller = _Answers(ProfileStore(prefs: prefs))
        ..repository = _Repository()
        ..status = StreamStatus.connected;
      controller.questions = {'q-1': question};
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Builder(
            builder: (context) => Scaffold(
              body: Center(
                child: KitButton.primary(
                  label: 'Open',
                  onPressed: () =>
                      showQuestionSheet(context, controller, question),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      return controller;
    }

    Finder send() => find.byKey(const ValueKey('question-send'));

    testWidgets('Send is pinned to the sheet\'s foot, outside the scrolling '
        'answers', (tester) async {
      await open(tester);
      expect(send(), findsOneWidget);
      expect(
        find.descendant(of: find.byType(Scrollable), matching: send()),
        findsNothing,
      );
      // The answers scroll; Send stays in view without scrolling.
      expect(send().hitTestable(), findsOneWidget);
    });

    testWidgets('Send enables as the person answers, and sends', (
      tester,
    ) async {
      final controller = await open(tester);
      expect(find.text(_en.activityAnswerEveryQuestion), findsOneWidget);
      await tester.tap(send());
      await tester.pump();
      expect(controller.answers, isNull);

      await tester.tap(find.text('Staging'));
      await tester.pump();
      expect(find.text(_en.activityAnswerEveryQuestion), findsNothing);

      await tester.tap(send());
      await tester.pumpAndSettle();
      expect(controller.answers, [
        ['Staging'],
      ]);
    });

    testWidgets('an optional prompt says so and Send accepts it empty; a '
        'required one still needs an answer', (tester) async {
      final controller = await open(
        tester,
        question: const PendingQuestion(
          id: 'q-1',
          sessionID: 'ses-1',
          prompts: [
            QuestionPrompt(
              title: 'Target',
              question: 'Where should this deploy?',
              multiple: false,
              custom: false,
              choices: [QuestionChoice(label: 'Staging', description: '')],
            ),
            QuestionPrompt(
              title: 'Notes',
              question: 'Anything else?',
              multiple: false,
              custom: false,
              optional: true,
              choices: [QuestionChoice(label: 'Hurry', description: '')],
            ),
          ],
        ),
      );
      expect(find.text(_en.activityQuestionOptional), findsOneWidget);
      // The required prompt still blocks Send.
      expect(find.text(_en.activityAnswerEveryQuestion), findsOneWidget);
      await tester.tap(find.text('Staging'));
      await tester.pump();
      expect(find.text(_en.activityAnswerEveryQuestion), findsNothing);
      await tester.tap(send());
      await tester.pumpAndSettle();
      expect(controller.answers, [
        ['Staging'],
        <String>[],
      ]);
    });
  });
}
