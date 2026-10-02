// Receipt states on a queued prompt, in the conversation it belongs to
// (docs/design/command-receipts-contract.md): uncertain in plain words, a
// Try again that only looks, the confirmed / still-uncertain outcomes,
// recovery after a restart, and storage-full stopping the send.
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/domain/command_receipts.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/offline_queue.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/complete_message_history.dart';

class _ReceiptApi extends OpenCodeApi with CompleteMessageHistory {
  _ReceiptApi() : super(baseUrl: 'http://localhost');
  bool visible = false;
  int sends = 0;
  int lookups = 0;
  final ids = <String>[];

  @override
  ServerCapabilities get capabilities => ServerCapabilities.allV1;

  @override
  Future<Session> session(String id) async => Session(id: id);

  @override
  Future<void> promptWithMessageID(
    String sessionID, {
    required String messageID,
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
    List<PromptAgentMention> agentMentions = const [],
    PromptDelivery? delivery,
    void Function()? beforeSend,
  }) async {
    sends++;
    ids.add(messageID);
    // The response is lost: the send's outcome is unknown.
    throw ApiException('Connection ended');
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
    sends++;
  }

  @override
  Future<List<MessageWithParts>> messages(String id) async {
    lookups++;
    return visible
        ? [
            for (final messageID in ids)
              MessageWithParts(
                info: MessageInfo(
                  id: messageID,
                  sessionID: id,
                  role: 'user',
                  time: MsgTime(created: 1),
                ),
                parts: const [],
              ),
          ]
        : const [];
  }
}

class _Controller extends ConnectionController {
  _Controller(super.store);
  @override
  Future<ServerGateway?> prepareActionTransport() async => api;
}

Future<_Controller> _controller(_ReceiptApi api) async {
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.load();
  return _Controller(store)
    ..api = api
    ..status = StreamStatus.connected;
}

const _prompt = QueuedPrompt(
  id: 'q1',
  profileID: 'profile-1',
  sessionID: 'ses_one',
  text: 'run the migration',
  createdAt: 1,
);

Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 6; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

Future<void> _pumpChat(WidgetTester tester, ConnectionController c) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(c)],
      child: const MaterialApp(home: ChatScreen(sessionID: 'ses_one')),
    ),
  );
  await _settle(tester);
}

Map<String, Object> _seed() => {
  'oc.profiles': jsonEncode([
    {
      'id': 'profile-1',
      'name': 'Test',
      'baseUrl': 'http://localhost',
      'username': '',
    },
  ]),
  'oc.activeProfile': 'profile-1',
};

const _uncertainWords = "We couldn't confirm your message arrived.";

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues(_seed());
    for (final channel in [
      'plugins.it_nomads.com/flutter_secure_storage',
      'oc/background',
    ]) {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(MethodChannel(channel), (_) async => null);
    }
  });

  Future<(_ReceiptApi, _Controller)> lost(WidgetTester tester) async {
    final api = _ReceiptApi();
    final c = await _controller(api);
    addTearDown(c.dispose);
    await c.queuePrompt(_prompt);
    await c.flushOfflineQueue();
    expect(api.sends, 1);
    await _pumpChat(tester, c);
    return (api, c);
  }

  testWidgets('uncertain: plain words, Details hold the technical part, the '
      'only action checks', (tester) async {
    await lost(tester);
    expect(find.text('run the migration'), findsOneWidget);
    expect(find.text(_uncertainWords), findsOneWidget);
    // No raw error as copy, and no resend offered.
    expect(find.textContaining('Connection ended'), findsNothing);
    expect(find.text('Send this message again'), findsNothing);
    final check = find.byKey(const ValueKey('queued-bubble-check'));
    expect(check, findsOneWidget);
    expect(
      find.descendant(of: check, matching: find.text('Try again')),
      findsOneWidget,
    );
    // Technical detail is folded; opening it shows ids and the note, never
    // the message body twice.
    expect(find.text('Receipt ID'), findsNothing);
    await tester.tap(find.text('Details'));
    await _settle(tester);
    expect(find.text('Receipt ID'), findsOneWidget);
    expect(find.text('Command ID'), findsOneWidget);
    expect(
      find.text('The connection ended before we could confirm receipt.'),
      findsOneWidget,
    );
    expect(find.text('run the migration'), findsOneWidget);
  });

  testWidgets('Try again looks up and never sends; still uncertain stays '
      'with when it was last checked', (tester) async {
    final (api, c) = await lost(tester);
    expect(find.textContaining('Last checked'), findsNothing);
    final lookupsBefore = api.lookups;
    await tester.tap(find.byKey(const ValueKey('queued-bubble-check')));
    await _settle(tester);
    expect(api.lookups, greaterThan(lookupsBefore));
    expect(api.sends, 1);
    expect(c.queuedPromptReviewCount, 1);
    expect(find.textContaining(_uncertainWords), findsOneWidget);
    expect(find.textContaining('Last checked'), findsOneWidget);
    expect(find.textContaining('Nothing was sent again'), findsOneWidget);
    expect(find.text('run the migration'), findsOneWidget);
    await tester.pump(const Duration(seconds: 4));
  });

  testWidgets('confirmed: the entry leaves the bubble, nothing is resent', (
    tester,
  ) async {
    final (api, c) = await lost(tester);
    api.visible = true;
    await tester.tap(find.byKey(const ValueKey('queued-bubble-check')));
    await _settle(tester);
    expect(api.sends, 1);
    expect(c.queuedPromptCount, 0);
    expect(find.byKey(const ValueKey('queued-bubble-check')), findsNothing);
    expect(find.text(_uncertainWords), findsNothing);
    expect(find.text('Message received.'), findsOneWidget);
    await tester.pump(const Duration(seconds: 4));
  });

  testWidgets('after a restart the same conversation shows the uncertain '
      'message and Try again confirms it', (tester) async {
    final api = _ReceiptApi();
    final before = await _controller(api);
    await before.queuePrompt(_prompt);
    await before.flushOfflineQueue();
    before.dispose();
    SharedPreferences.resetStatic();
    final fresh = _ReceiptApi()
      ..ids.addAll(api.ids)
      ..sends = api.sends
      ..visible = true;
    final after = await _controller(fresh);
    addTearDown(after.dispose);
    await _pumpChat(tester, after);
    expect(find.text(_uncertainWords), findsOneWidget);
    expect(find.text('run the migration'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('queued-bubble-check')));
    await _settle(tester);
    expect(fresh.sends, 1);
    expect(after.queuedPromptCount, 0);
    expect(find.text('Message received.'), findsOneWidget);
    await tester.pump(const Duration(seconds: 4));
  });

  testWidgets('another conversation never shows this receipt', (tester) async {
    final (_, c) = await lost(tester);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(c)],
        child: const MaterialApp(home: ChatScreen(sessionID: 'ses_other')),
      ),
    );
    await _settle(tester);
    expect(find.text(_uncertainWords), findsNothing);
    expect(find.text('run the migration'), findsNothing);
  });

  testWidgets('storage full stops the send and says so in plain words', (
    tester,
  ) async {
    final scope = 'a' * 64;
    final full = [
      for (var i = 0; i < 1000; i++)
        CommandReceipt(
          commandID: 'queue:old$i',
          receiptID: 'msg_old$i',
          sessionID: 'ses_old',
          tabID: 'ses_old',
          scope: scope,
          createdAt: 1,
          state: CommandReceiptState.confirmed,
        ).toJson(),
    ];
    SharedPreferences.setMockInitialValues({
      ..._seed(),
      'oc.pendingCommands.profile-1': jsonEncode({
        'schemaVersion': 1,
        'commands': full,
      }),
    });
    final api = _ReceiptApi();
    final c = await _controller(api);
    addTearDown(c.dispose);
    await c.queuePrompt(_prompt);
    await c.flushOfflineQueue();
    // The draft never left.
    expect(api.sends, 0);
    await _pumpChat(tester, c);
    expect(find.text('run the migration'), findsOneWidget);
    expect(find.textContaining("can't safely record sends"), findsOneWidget);
    expect(find.textContaining('Could not safely record'), findsNothing);
    expect(find.text('Send this message again'), findsNothing);
    expect(find.byKey(const ValueKey('queued-bubble-check')), findsNothing);
  });
}
