import 'dart:convert';
import 'dart:async';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/domain/command_receipts.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/offline_queue.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'support/complete_message_history.dart';

class _ReceiptApi extends OpenCodeApi with CompleteMessageHistory {
  _ReceiptApi() : super(baseUrl: 'http://localhost');
  bool lostResponse = true;
  bool visible = true;
  bool supported = true;
  Completer<void>? lookupGate;
  Completer<void>? lookupEntered;
  int sends = 0;
  final ids = <String>[];
  @override
  ServerCapabilities get capabilities =>
      supported ? ServerCapabilities.allV1 : const ServerCapabilities();
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
    if (lostResponse) throw ApiException('Connection ended');
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
    lookupEntered?.complete();
    await lookupGate?.future;
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

QueuedPrompt _prompt() => const QueuedPrompt(
  id: 'q1',
  profileID: 'profile-1',
  sessionID: 'ses_one',
  text: 'private prompt',
  createdAt: 1,
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() {
    SharedPreferences.setMockInitialValues({
      'oc.profiles': jsonEncode([
        {
          'id': 'profile-1',
          'name': 'Test',
          'baseUrl': 'http://localhost',
          'username': '',
        },
      ]),
      'oc.activeProfile': 'profile-1',
    });
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('oc/background'),
          (_) async => null,
        );
  });
  test(
    'lost response then receipt retry confirms without another send',
    () async {
      final api = _ReceiptApi();
      final c = await _controller(api);
      addTearDown(c.dispose);
      await c.queuePrompt(_prompt());
      await c.flushOfflineQueue();
      expect(api.sends, 1);
      expect(c.queuedPromptReviewCount, 1);
      expect(
        c.commandReceiptsFor('ses_one').single.state,
        CommandReceiptState.uncertain,
      );
      expect(await c.resendQueuedPrompt('q1'), isTrue);
      expect(api.sends, 1);
      expect(c.queuedPromptCount, 0);
      expect(
        c.commandReceiptsFor('ses_one').single.state,
        CommandReceiptState.confirmed,
      );
      final prefs = await SharedPreferences.getInstance();
      expect(
        prefs.getString('oc.pendingCommands.profile-1'),
        isNot(contains('private prompt')),
      );
    },
  );
  test(
    'restart with pending command reconciles on flush without send',
    () async {
      final api = _ReceiptApi();
      final before = await _controller(api);
      await before.queuePrompt(_prompt());
      await before.flushOfflineQueue();
      before.dispose();
      SharedPreferences.resetStatic();
      final reconnected = _ReceiptApi()
        ..ids.addAll(api.ids)
        ..sends = api.sends;
      final after = await _controller(reconnected);
      addTearDown(after.dispose);
      expect(after.queuedPromptReviewCount, 1);
      await after.flushOfflineQueue();
      expect(after.queuedPromptCount, 0);
      expect(reconnected.sends, 1);
    },
  );
  test('absent receipt and capability loss never authorize resend', () async {
    final api = _ReceiptApi()..visible = false;
    final c = await _controller(api);
    addTearDown(c.dispose);
    await c.queuePrompt(_prompt());
    await c.flushOfflineQueue();
    expect(await c.resendQueuedPrompt('q1'), isFalse);
    expect(api.sends, 1);
    api.supported = false;
    expect(await c.resendQueuedPrompt('q1'), isFalse);
    await c.flushOfflineQueue();
    expect(api.sends, 1);
    expect(c.queuedPromptReviewCount, 1);
  });
  test(
    'unsupported gateway keeps normal queue dispatch and no journal',
    () async {
      final api = _ReceiptApi()..supported = false;
      final c = await _controller(api);
      addTearDown(c.dispose);
      await c.queuePrompt(_prompt());
      await c.flushOfflineQueue();
      expect(api.sends, 1);
      expect(c.queuedPromptCount, 0);
      final prefs = await SharedPreferences.getInstance();
      expect(prefs.containsKey('oc.pendingCommands.profile-1'), isFalse);
    },
  );
  test('late receipt lookup keeps a replacement unmarked draft', () async {
    final api = _ReceiptApi();
    final c = await _controller(api);
    addTearDown(c.dispose);
    await c.queuePrompt(_prompt());
    await c.flushOfflineQueue();
    api.lookupGate = Completer<void>();
    api.lookupEntered = Completer<void>();
    final check = c.checkQueuedPromptReceipt('q1');
    await api.lookupEntered!.future;
    expect(await c.removeQueuedPrompt('q1'), isTrue);
    await c.queuePrompt(_prompt());
    api.lookupGate!.complete();
    expect(await check, isFalse);
    expect(c.queuedPromptCount, 1);
    expect(c.queuedPromptReviewCount, 0);
    expect(api.sends, 1);
  });
  test('profile preference sweep deletes and closes journal', () async {
    final api = _ReceiptApi();
    final c = await _controller(api);
    addTearDown(c.dispose);
    await c.queuePrompt(_prompt());
    await c.flushOfflineQueue();
    expect(
      c.store.profileScopedPreferenceKeys('profile-1'),
      contains('oc.pendingCommands.profile-1'),
    );
    expect(await c.store.removeScopedPreferences('profile-1'), isEmpty);
    expect(c.store.prefs.containsKey('oc.pendingCommands.profile-1'), isFalse);
    expect(
      () => c.commandReceiptsFor('ses_one'),
      throwsA(isA<CommandReceiptException>()),
    );
  });
}
