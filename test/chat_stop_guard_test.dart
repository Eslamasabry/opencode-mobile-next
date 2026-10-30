import 'support/complete_message_history.dart';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'support/fake_server_gateway.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// A steering server (OpenCode 2) that records every prompt and every
/// interrupt the chat sends it.
class _SteeringApi extends FakeServerGateway with CompleteMessageHistory {
  _SteeringApi() : super(baseUrl: 'http://localhost');

  final prompts = <({String text, PromptDelivery? delivery})>[];
  final aborts = <String>[];

  @override
  ServerCapabilities get capabilities =>
      const ServerCapabilities(forms: true, inbox: true, promptSteering: true);

  @override
  Future<List<MessageWithParts>> messages(String id) async => [];

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
    prompts.add((text: text, delivery: delivery));
  }

  @override
  Future<void> abort(String sessionID) async => aborts.add(sessionID);
}

Future<ConnectionController> _controller(ServerGateway api) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'profile-1',
        'name': 'Test server',
        'flavor': 'v2',
        'baseUrl': 'http://localhost',
        'username': '',
      },
    ]),
    'oc.activeProfile': 'profile-1',
  });
  final preferences = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: preferences);
  await store.load();
  return ConnectionController(store)
    ..api = api
    ..status = StreamStatus.connected;
}

Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 6; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

Future<(_SteeringApi, ConnectionController)> _busyChat(
  WidgetTester tester,
) async {
  final api = _SteeringApi();
  final controller = await _controller(api);
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
    ),
  );
  await _settle(tester);
  controller.busySessions.add('session-1');
  controller.notifyListeners();
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 200));
  return (api, controller);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  testWidgets('a double tap on Send while a turn runs steers once and never '
      'stops the turn', (tester) async {
    final (api, _) = await _busyChat(tester);
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'Now finish building first',
    );
    await tester.pump();
    final at = tester.getCenter(find.byKey(const Key('chat-send-button')));

    await tester.tapAt(at);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 150));
    // The field cleared and Send became Stop under the same finger: the
    // second tap of a double tap lands on it.
    expect(find.byKey(const Key('chat-stop-button')), findsWidgets);
    await tester.tapAt(at);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 150));

    expect(api.prompts.single.delivery, PromptDelivery.steer);
    expect(api.aborts, isEmpty);
    expect(find.text('You stopped this reply.'), findsNothing);

    // A late second tap, just after the button changed role, is still not
    // a Stop.
    await tester.pump(const Duration(milliseconds: 300));
    await tester.tapAt(at);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 150));
    expect(api.aborts, isEmpty);
  });

  testWidgets('Stop works once the quiet moment after a send has passed', (
    tester,
  ) async {
    final (api, _) = await _busyChat(tester);
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'one more thing',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('chat-send-button')));
    await tester.pump();
    await tester.pump(const Duration(seconds: 2));

    await tester.tap(find.byKey(const Key('chat-stop-button')).last);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));
    expect(api.aborts, ['session-1']);
  });

  testWidgets('Stop with nothing sent stops at the first tap', (tester) async {
    final (api, _) = await _busyChat(tester);
    await tester.tap(find.byKey(const Key('chat-stop-button')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));
    expect(api.aborts, ['session-1']);
  });
}
