import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/activity_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Api extends OpenCodeApi {
  _Api() : super(baseUrl: 'http://localhost');
  int replies = 0;

  @override
  Future<void> respondPermission(
    String requestID,
    String reply, {
    String? legacySessionID,
    String? legacyPermissionID,
    String? message,
  }) async {
    replies++;
  }
}

class _Repository extends ProductRepository {
  bool fail = false;
  Completer<void>? pending;
  int rejects = 0;
  List<List<String>>? answers;

  @override
  Future<void> answerQuestion(String id, List<List<String>> answers) async {
    this.answers = answers;
    if (fail) throw ApiException('Temporarily unavailable');
    await pending?.future;
  }

  @override
  Future<void> rejectQuestion(String id) async {
    rejects++;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

PermissionRequest _permission() => PermissionRequest(
  id: 'permission',
  sessionID: 'session',
  permission: 'edit',
  patterns: ['lib/main.dart'],
  always: ['lib/*'],
  metadata: {
    'diff': '--- a/lib/main.dart\n+++ b/lib/main.dart\n@@ -1 +1 @@\n-old\n+new',
  },
);

PendingQuestion _question() => const PendingQuestion(
  id: 'question',
  sessionID: 'session',
  prompts: [
    QuestionPrompt(
      title: 'Choose target',
      question: 'Where should this deploy?',
      multiple: false,
      custom: true,
      choices: [QuestionChoice(label: 'Staging', description: 'Test first')],
    ),
  ],
);

Future<
  ({
    ConnectionController controller,
    _Api api,
    _Repository repository,
    GlobalKey<NavigatorState> navigator,
  })
>
_open(WidgetTester tester, {bool question = false}) async {
  SharedPreferences.setMockInitialValues({});
  final api = _Api();
  final repository = _Repository();
  final controller =
      ConnectionController(
          ProfileStore(prefs: await SharedPreferences.getInstance()),
        )
        ..api = api
        ..repository = repository;
  controller.permissions['permission'] = _permission();
  controller.questions['question'] = _question();
  addTearDown(controller.dispose);
  final navigator = GlobalKey<NavigatorState>();
  await tester.pumpWidget(
    MaterialApp(
      navigatorKey: navigator,
      home: Scaffold(
        body: question
            ? ActivityQuestionTile(
                question: _question(),
                controller: controller,
              )
            : ActivityPermissionTile(
                permission: _permission(),
                controller: controller,
              ),
      ),
    ),
  );
  await tester.tap(find.byType(ListTile));
  await tester.pumpAndSettle();
  return (
    controller: controller,
    api: api,
    repository: repository,
    navigator: navigator,
  );
}

void _resolved(ConnectionController controller, {bool question = false}) {
  controller.handleEventForTesting(
    EventEnvelope(
      type: question ? 'question.replied' : 'permission.replied',
      properties: {'requestID': question ? 'question' : 'permission'},
    ),
  );
}

void main() {
  testWidgets(
    'remote permission resolution removes its confirmation but preserves unrelated routes',
    (tester) async {
      final h = await _open(tester);
      await tester.tap(find.byKey(const Key('permission-allow-always')));
      await tester.pumpAndSettle();
      expect(find.text('Confirm broader access'), findsOneWidget);
      h.navigator.currentState!.push(
        MaterialPageRoute<void>(
          builder: (_) => const Scaffold(body: Text('Unrelated screen')),
        ),
      );
      await tester.pumpAndSettle();
      _resolved(h.controller);
      await tester.pumpAndSettle();
      expect(find.text('Unrelated screen'), findsOneWidget);
      h.navigator.currentState!.pop();
      await tester.pumpAndSettle();
      expect(find.text('Confirm broader access'), findsNothing);
      expect(find.byKey(const Key('permission-sheet')), findsNothing);
      expect(h.api.replies, 0);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('remote resolution closes the permission full diff', (
    tester,
  ) async {
    final h = await _open(tester);
    await tester.tap(find.byKey(const Key('permission-see-full-diff')));
    await tester.pumpAndSettle();
    _resolved(h.controller);
    await tester.pumpAndSettle();
    expect(h.navigator.currentState!.canPop(), isFalse);
    expect(h.api.replies, 0);
  });

  testWidgets('remote question resolution closes the dismissal confirmation', (
    tester,
  ) async {
    final h = await _open(tester, question: true);
    await tester.tap(find.widgetWithText(TextButton, 'Dismiss'));
    await tester.pumpAndSettle();
    expect(find.text('Dismiss this request?'), findsOneWidget);
    _resolved(h.controller, question: true);
    await tester.pumpAndSettle();
    expect(h.navigator.currentState!.canPop(), isFalse);
    expect(h.repository.rejects, 0);
    expect(tester.takeException(), isNull);
  });

  for (final question in [false, true]) {
    testWidgets(
      'scope replacement closes ${question ? 'question' : 'permission'} with a reused ID',
      (tester) async {
        final h = await _open(tester, question: question);
        h.controller.locationRevision++;
        // A real controller notification, with the same pending ID and content.
        h.controller.handleEventForTesting(
          EventEnvelope(
            type: 'session.status',
            properties: {
              'sessionID': 'session',
              'status': {'type': 'idle'},
            },
          ),
        );
        await tester.pumpAndSettle();
        expect(h.navigator.currentState!.canPop(), isFalse);
        expect(h.controller.permissions, contains('permission'));
        expect(h.controller.questions, contains('question'));
      },
    );
  }

  testWidgets(
    'equivalent hydration and a recoverable error preserve the question draft',
    (tester) async {
      final h = await _open(tester, question: true);
      await tester.enterText(find.byType(TextField), 'Canary');
      h.controller.handleEventForTesting(
        EventEnvelope(
          type: 'question.asked',
          properties: {
            'id': 'question',
            'sessionID': 'session',
            'questions': [
              {
                'header': 'Choose target',
                'question': 'Where should this deploy?',
                'multiple': false,
                'custom': true,
                'options': [
                  {'label': 'Staging', 'description': 'Test first'},
                ],
              },
            ],
          },
        ),
      );
      await tester.pump();
      h.repository.fail = true;
      await tester.tap(find.widgetWithText(FilledButton, 'Send answers'));
      await tester.pumpAndSettle();
      expect(find.textContaining('Temporarily unavailable'), findsOneWidget);
      expect(
        tester.widget<TextField>(find.byType(TextField)).controller!.text,
        'Canary',
      );
      h.repository.fail = false;
      await tester.tap(find.widgetWithText(FilledButton, 'Send answers'));
      await tester.pumpAndSettle();
      expect(h.repository.answers, [
        ['Canary'],
      ]);
      expect(h.navigator.currentState!.canPop(), isFalse);
    },
  );

  testWidgets(
    'question success under another screen removes only its own sheet',
    (tester) async {
      final h = await _open(tester, question: true);
      final pending = Completer<void>();
      h.repository.pending = pending;
      await tester.tap(find.text('Staging'));
      await tester.pump();
      await tester.tap(find.widgetWithText(FilledButton, 'Send answers'));
      await tester.pump();
      h.navigator.currentState!.push(
        MaterialPageRoute<void>(
          builder: (_) => const Scaffold(body: Text('Unrelated screen')),
        ),
      );
      await tester.pump(const Duration(milliseconds: 400));
      pending.complete();
      await tester.pumpAndSettle();
      expect(find.text('Unrelated screen'), findsOneWidget);
      h.navigator.currentState!.pop();
      await tester.pumpAndSettle();
      expect(h.navigator.currentState!.canPop(), isFalse);
      expect(find.text('OpenCode needs input'), findsNothing);
    },
  );
}
