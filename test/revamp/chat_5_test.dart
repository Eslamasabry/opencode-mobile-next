// Behaviour of unit chat-5 (chat requests on the one card): a permission is
// answered in place with one tap and leaves a receipt, a refusal says so and
// gives the answers back, Details opens the one request sheet where "Always
// allow" states its scope before anything is sent, an OpenCode 1 question
// and an OpenCode 2 form share the one card, form answers survive closing
// and a restart, and the server-wide approval switch asks before it turns
// on.
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart'
    show api2ServerCapabilities;
import 'package:opencode_mobile/domain/form_request.dart' show Api2FormInfo;
import 'package:opencode_mobile/state/automation_policy.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit_request_card.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/widgets/form_renderer.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../support/complete_message_history.dart';
import 'chat_3_support.dart' show chat3MockSecureStorage;

class _Api extends OpenCodeApi with CompleteMessageHistory {
  _Api({this.forms = false}) : super(baseUrl: 'http://localhost');

  final bool forms;
  final replies = <(String, String)>[];
  final pendingFormInventory = <String, Api2FormInfo>{};
  bool failReplies = false;

  void publishForm(ConnectionController controller, EventEnvelope event) {
    final form = Api2FormInfo.fromJson(
      Map<String, dynamic>.from(event.properties['form'] as Map),
    )!;
    pendingFormInventory[form.id] = form;
    controller.handleEventForTesting(event);
  }

  @override
  Future<List<Api2FormInfo>> pendingForms() async =>
      pendingFormInventory.values.toList();

  @override
  ServerCapabilities get capabilities =>
      forms ? api2ServerCapabilities : super.capabilities;

  @override
  Future<List<MessageWithParts>> messages(String id) async => [];

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));

  @override
  Future<void> respondPermission(
    String requestID,
    String reply, {
    String? legacySessionID,
    String? legacyPermissionID,
    String? message,
  }) async {
    replies.add((requestID, reply));
    if (failReplies) throw ApiException('The server refused the reply');
  }

  @override
  Future<void> replyForm(
    String sessionID,
    String formID,
    Map<String, dynamic> answer,
  ) async {
    pendingFormInventory.remove(formID);
  }
}

class _Questions extends ProductRepository {
  final answered = <(String, List<List<String>>)>[];

  @override
  Future<List<PendingQuestion>> listQuestions() async => const [];

  @override
  Future<void> answerQuestion(String id, List<List<String>> answers) async {
    answered.add((id, answers));
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<ConnectionController> _controller(
  _Api api, {
  _Questions? questions,
}) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'profile-1',
        'name': 'Laptop',
        'baseUrl': 'http://localhost',
        'username': '',
      },
    ]),
    'oc.activeProfile': 'profile-1',
  });
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.load();
  final controller = ConnectionController(store)
    ..api = api
    ..directory = '/work/app'
    ..status = StreamStatus.connected;
  if (questions != null) controller.repository = questions;
  return controller;
}

Future<void> _pumpChat(WidgetTester tester, ConnectionController conn) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(conn)],
      child: MaterialApp(
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(disableAnimations: true),
          child: child!,
        ),
        home: const ChatScreen(sessionID: 'session-1'),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

EventEnvelope _permission({String id = 'request-1'}) => EventEnvelope(
  type: 'permission.asked',
  properties: {
    'id': id,
    'sessionID': 'session-1',
    'permission': 'bash',
    'patterns': ['flutter test test/checkout_test.dart'],
    'metadata': <String, Object?>{},
    'always': ['flutter test *'],
  },
);

EventEnvelope _question() => EventEnvelope(
  type: 'question.asked',
  properties: {
    'id': 'question-1',
    'sessionID': 'session-1',
    'questions': [
      {
        'header': 'Pick a target',
        'question': 'Where should this deploy?',
        'multiple': false,
        'custom': true,
        'options': [
          {'label': 'Staging', 'description': 'Test environment'},
          {'label': 'Production', 'description': 'Live environment'},
        ],
      },
    ],
  },
);

EventEnvelope _form() => EventEnvelope(
  type: 'form.v2.created',
  properties: {
    'form': {
      'id': 'frm_1',
      'sessionID': 'session-1',
      'title': 'Connect to Sentry',
      'fields': [
        {'key': 'note', 'type': 'string', 'title': 'Note for the agent'},
      ],
    },
  },
);

KitRequestCard _card(WidgetTester tester) =>
    tester.widget<KitRequestCard>(find.byType(KitRequestCard));

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(chat3MockSecureStorage);

  testWidgets('Allow once answers a permission in place with one tap', (
    tester,
  ) async {
    final api = _Api();
    final conn = await _controller(api);
    addTearDown(conn.dispose);
    await _pumpChat(tester, conn);
    conn.handleEventForTesting(_permission());
    await tester.pumpAndSettle();

    final card = _card(tester);
    expect(card.kind, KitRequestKind.permission);
    expect(card.who, isNotNull, reason: 'the v2 card, never the legacy one');
    expect(find.text('Run a shell command'), findsOneWidget);
    expect(find.byKey(const Key('permission-sheet')), findsNothing);

    await tester.tap(find.byKey(const Key('permission-card-allow')));
    await tester.pump(const Duration(seconds: 3));
    await tester.pumpAndSettle();

    expect(api.replies, [('request-1', 'once')]);
    expect(find.byType(KitRequestCard), findsNothing);
  });

  testWidgets('a refused answer says so and gives the answers back', (
    tester,
  ) async {
    final api = _Api()..failReplies = true;
    final conn = await _controller(api);
    addTearDown(conn.dispose);
    await _pumpChat(tester, conn);
    conn.handleEventForTesting(_permission());
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('permission-card-reject')));
    await tester.pump(const Duration(seconds: 3));
    await tester.pumpAndSettle();

    expect(api.replies, [('request-1', 'reject')]);
    expect(find.textContaining('Not accepted'), findsOneWidget);
    expect(find.byKey(const Key('permission-card-allow')), findsOneWidget);
    expect(_card(tester).phase, KitRequestPhase.waiting);
  });

  testWidgets('Details opens the one request sheet; Always allow states its '
      'scope before anything is sent', (tester) async {
    // The card now carries Always allow, so it is taller than the default
    // 600 dp test window leaves room for above the composer.
    tester.view.physicalSize = const Size(800, 1400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final api = _Api();
    final conn = await _controller(api);
    addTearDown(conn.dispose);
    await _pumpChat(tester, conn);
    conn.handleEventForTesting(_permission());
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('permission-card-review')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('permission-sheet')), findsOneWidget);

    await tester.tap(find.byKey(const Key('permission-allow-always')));
    await tester.pumpAndSettle();
    expect(find.textContaining('From now on'), findsOneWidget);
    expect(api.replies, isEmpty);

    await tester.ensureVisible(find.text('Turn on'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Turn on'));
    await tester.pump(const Duration(seconds: 3));
    await tester.pumpAndSettle();
    expect(api.replies, [('request-1', 'always')]);
    expect(find.byKey(const Key('permission-sheet')), findsNothing);
  });

  testWidgets('an OpenCode 1 question sends on a tap from the one card', (
    tester,
  ) async {
    final questions = _Questions();
    final conn = await _controller(_Api(), questions: questions);
    addTearDown(conn.dispose);
    await _pumpChat(tester, conn);
    conn.handleEventForTesting(_question());
    await tester.pumpAndSettle();

    expect(_card(tester).kind, KitRequestKind.question);
    expect(find.text('Pick a target'), findsOneWidget);
    await tester.tap(find.text('Staging'));
    await tester.pump(const Duration(seconds: 3));
    await tester.pumpAndSettle();

    final (id, answers) = questions.answered.single;
    expect(id, 'question-1');
    expect(answers, [
      ['Staging'],
    ]);
  });

  testWidgets('an OpenCode 2 form is the same card, and its answers survive '
      'closing and a restart', (tester) async {
    final api = _Api(forms: true);
    final conn = await _controller(api);
    addTearDown(conn.dispose);
    addTearDown(debugForgetFormAnswers);
    await _pumpChat(tester, conn);
    api.publishForm(conn, _form());
    await tester.pumpAndSettle();

    final card = _card(tester);
    expect(card.kind, KitRequestKind.form);
    expect(card.who, isNotNull);

    Future<void> open() async {
      await tester.tap(find.byKey(const ValueKey('form-request-answer-frm_1')));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('form-sheet')), findsOneWidget);
    }

    Future<void> close() async {
      Navigator.of(tester.element(find.byKey(const Key('form-sheet')))).pop();
      await tester.pumpAndSettle();
    }

    await open();
    await tester.enterText(
      find.descendant(
        of: find.byKey(const Key('form-sheet')),
        matching: find.byType(EditableText),
      ),
      'Use the EU region',
    );
    await tester.pumpAndSettle();
    await close();

    // A restart forgets what the app held in memory; the draft is kept.
    debugForgetFormAnswers();
    await open();
    expect(find.text('Use the EU region'), findsOneWidget);
  });

  testWidgets('the server-wide approval choice states its scope before it '
      'turns on', (tester) async {
    final conn = await _controller(_Api());
    addTearDown(conn.dispose);
    // The automatic modes are offered while the automation policy allows.
    await AutomationPolicyController.forProfile(
      conn.store.prefs,
      'profile-1',
    ).setSupervision(AutomationSupervision.balanced);
    // Tall enough that the sheet builds every section at once.
    tester.view.physicalSize = const Size(412, 2400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      MaterialApp(
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(disableAnimations: true),
          child: child!,
        ),
        home: Builder(
          builder: (context) => Scaffold(
            body: Center(
              child: TextButton(
                onPressed: () => showSessionApprovalsSheet(
                  context,
                  controller: conn,
                  sessionID: 'session-1',
                ),
                child: const Text('open'),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('session-approvals-sheet')), findsOneWidget);
    // The scope is said once, in the one-sentence confirm that follows the
    // choice; nothing on the sheet contradicts it.
    expect(find.textContaining('New conversations always ask'), findsNothing);

    await tester.tap(find.byKey(const Key('approvals-mode-everything')));
    await tester.pumpAndSettle();
    expect(conn.approvesEverything, isFalse);
    expect(find.textContaining('without asking'), findsOneWidget);

    await tester.tap(find.text('Approve everything').last);
    await tester.pumpAndSettle();
    expect(conn.approvesEverything, isTrue);
  });
}
