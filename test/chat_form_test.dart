import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart'
    show api2ServerCapabilities;
import 'package:opencode_mobile/domain/server_gateway.dart'
    show ServerCapabilities;
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _FormChatApi extends OpenCodeApi {
  _FormChatApi() : super(baseUrl: 'http://localhost');

  // Forms are a v2 capability, and the chat now gates the renderer and the
  // inline card on it, so a fake that serves forms must report it.
  @override
  ServerCapabilities get capabilities => api2ServerCapabilities;

  final formReplies = <(String, String, Map<String, dynamic>)>[];
  final formCancels = <(String, String)>[];
  Object? formError;

  @override
  Future<List<MessageWithParts>> messages(String id) async => [];

  @override
  Future<void> replyForm(
    String sessionID,
    String formID,
    Map<String, dynamic> answer,
  ) async {
    if (formError case final error?) {
      formError = null;
      throw error;
    }
    formReplies.add((sessionID, formID, answer));
  }

  @override
  Future<void> cancelForm(String sessionID, String formID) async {
    formCancels.add((sessionID, formID));
  }
}

Future<ConnectionController> _controller(_FormChatApi api) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: prefs))
    ..api = api
    ..status = StreamStatus.connected;
}

EventEnvelope _formCreated({
  String id = 'frm_1',
  String sessionID = 'session-1',
}) => EventEnvelope(
  type: 'form.v2.created',
  properties: {
    'form': {
      'id': id,
      'sessionID': sessionID,
      'title': 'Connect to Sentry',
      'fields': [
        {
          'key': 'env',
          'type': 'string',
          'title': 'Environment',
          'required': true,
          'options': [
            {'value': 'prod', 'label': 'Production'},
            {'value': 'stage', 'label': 'Staging'},
          ],
        },
        {'key': 'confirm', 'type': 'boolean', 'title': 'Confirm'},
      ],
    },
  },
);

Future<ConnectionController> _pumpChat(
  WidgetTester tester,
  _FormChatApi api,
) async {
  final controller = await _controller(api);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
    ),
  );
  await tester.pumpAndSettle();
  return controller;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('a form for the open session auto-presents the renderer and '
      'shows the inline card', (tester) async {
    final api = _FormChatApi();
    final controller = await _pumpChat(tester, api);
    addTearDown(controller.dispose);

    controller.handleEventForTesting(_formCreated());
    await tester.pumpAndSettle();

    // The renderer auto-opened as a sheet.
    expect(find.byKey(const Key('form-sheet')), findsOneWidget);
    expect(find.byKey(const Key('form-title')), findsOneWidget);

    // Dismissing it leaves the inline attention card as the reopen path —
    // the same form does not nag by auto-presenting again.
    await tester.tapAt(const Offset(10, 10));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('form-sheet')), findsNothing);
    expect(
      find.byKey(const ValueKey('form-request-card-frm_1')),
      findsOneWidget,
    );
    expect(find.text('2 questions'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('form-request-answer-frm_1')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('form-sheet')), findsOneWidget);
  });

  testWidgets('answering the form replies through the gateway and clears the '
      'card', (tester) async {
    final api = _FormChatApi();
    final controller = await _pumpChat(tester, api);
    addTearDown(controller.dispose);

    controller.handleEventForTesting(_formCreated());
    await tester.pumpAndSettle();

    await tester.tap(find.text('Production'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('form-submit')));
    await tester.pumpAndSettle();

    final reply = api.formReplies.single;
    expect(reply.$1, 'session-1');
    expect(reply.$2, 'frm_1');
    expect(reply.$3, {'env': 'prod', 'confirm': false});
    expect(find.byKey(const Key('form-sheet')), findsNothing);
    expect(
      find.byKey(const ValueKey('form-request-card-frm_1')),
      findsNothing,
    );
  });

  testWidgets('a 400 invalid answer keeps the form open with the banner', (
    tester,
  ) async {
    final api = _FormChatApi()
      ..formError = ApiException(
        'Invalid option for form field: env',
        statusCode: 400,
        errorTag: 'FormInvalidAnswerError',
      );
    final controller = await _pumpChat(tester, api);
    addTearDown(controller.dispose);

    controller.handleEventForTesting(_formCreated());
    await tester.pumpAndSettle();
    await tester.tap(find.text('Production'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('form-submit')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('form-sheet')), findsOneWidget);
    expect(find.byKey(const Key('form-error-banner')), findsOneWidget);
    expect(find.textContaining('Invalid option'), findsOneWidget);
    expect(controller.forms, contains('frm_1'));
  });

  testWidgets('a 409 already-settled reply toasts and closes', (tester) async {
    final api = _FormChatApi()
      ..formError = ApiException(
        'Form already settled',
        statusCode: 409,
        errorTag: 'FormAlreadySettledError',
      );
    final controller = await _pumpChat(tester, api);
    addTearDown(controller.dispose);

    controller.handleEventForTesting(_formCreated());
    await tester.pumpAndSettle();
    await tester.tap(find.text('Production'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('form-submit')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('form-sheet')), findsNothing);
    expect(find.text('Already answered elsewhere'), findsOneWidget);
    expect(controller.forms, isEmpty);
  });

  testWidgets('dismissing the form cancels it after confirmation', (
    tester,
  ) async {
    final api = _FormChatApi();
    final controller = await _pumpChat(tester, api);
    addTearDown(controller.dispose);

    controller.handleEventForTesting(_formCreated());
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('form-cancel')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('form-dismiss-confirm-button')));
    await tester.pumpAndSettle();

    expect(api.formCancels.single, ('session-1', 'frm_1'));
    expect(find.byKey(const Key('form-sheet')), findsNothing);
    expect(controller.forms, isEmpty);
  });

  testWidgets('a form for another session shows no card in this chat', (
    tester,
  ) async {
    final api = _FormChatApi();
    final controller = await _pumpChat(tester, api);
    addTearDown(controller.dispose);

    controller.handleEventForTesting(
      _formCreated(id: 'frm_other', sessionID: 'session-2'),
    );
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('form-sheet')), findsNothing);
    expect(
      find.byKey(const ValueKey('form-request-card-frm_other')),
      findsNothing,
    );
  });
}
