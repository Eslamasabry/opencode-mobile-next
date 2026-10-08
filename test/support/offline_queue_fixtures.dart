import 'complete_message_history.dart';
import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/domain/server_gateway.dart'
    show PromptDelivery, ServerGateway;
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/offline_queue.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

class QueueController extends ConnectionController {
  QueueController(super.store);
  Completer<ServerGateway?>? pendingTransport;
  Future<void>? selectionWait;

  @override
  Future<void> waitForSessionSelection(
    String sessionID, {
    ServerGateway? expectedApi,
  }) =>
      selectionWait ??
      super.waitForSessionSelection(sessionID, expectedApi: expectedApi);

  @override
  Future<ServerGateway?> prepareActionTransport() =>
      pendingTransport?.future ?? super.prepareActionTransport();
}

/// Preferences whose queue-key writes follow a script. Each write to the
/// offline queue consumes the next planned outcome — `true` accepts,
/// `false` refuses, a future defers the answer — and an exhausted plan
/// answers [defaultOutcome]. Refused writes leave the stored value as it
/// was, the way a full disk does. Other keys are untouched.
///
/// [onDisk] reads the platform store directly, bypassing the
/// SharedPreferences in-memory cache, so a test can ask what a process
/// death at that instant would have left behind.
class ScriptedQueueStore extends InMemorySharedPreferencesStore {
  ScriptedQueueStore(super.data) : super.withData();

  final List<FutureOr<bool>> plan = [];
  bool defaultOutcome = true;

  /// Queue writes that have reached the store, whether or not they have
  /// been answered yet — how a test knows a held write is now pending.
  int queueWritesRequested = 0;

  bool _isQueue(String key) => key.endsWith('oc.offlineQueue');

  Future<bool> _admit() async {
    queueWritesRequested += 1;
    return plan.isEmpty ? defaultOutcome : await plan.removeAt(0);
  }

  @override
  Future<bool> setValue(String type, String key, Object value) async {
    if (!_isQueue(key)) return super.setValue(type, key, value);
    return await _admit() && await super.setValue(type, key, value);
  }

  @override
  Future<bool> remove(String key) async {
    if (!_isQueue(key)) return super.remove(key);
    return await _admit() && await super.remove(key);
  }

  Future<List<QueuedPrompt>> onDisk() async {
    final raw = (await getAll())['flutter.oc.offlineQueue'];
    if (raw is! String || raw.isEmpty) return const [];
    return [
      for (final entry in jsonDecode(raw) as List)
        QueuedPrompt.fromJson(entry)!,
    ];
  }
}

/// Puts a [ScriptedQueueStore] holding the current preferences behind the
/// plugin for the rest of the test. Call after the controller is built so
/// its setup writes go through the ordinary store.
Future<ScriptedQueueStore> scriptedDisk() async {
  final original = SharedPreferencesStorePlatform.instance;
  final disk = ScriptedQueueStore(await original.getAll());
  SharedPreferencesStorePlatform.instance = disk;
  addTearDown(() => SharedPreferencesStorePlatform.instance = original);
  return disk;
}

class FakeApi extends OpenCodeApi with CompleteMessageHistory {
  FakeApi() : super(baseUrl: 'http://localhost');

  final List<({String sessionID, String text, ModelRef? model})> prompts = [];

  /// Per-attempt plan consumed from the front: null means success, an error
  /// object is thrown. An empty plan means every attempt succeeds.
  final List<Object?> promptPlan = [];
  Future<void> Function()? beforePrompt;

  /// What the flush's staged-revert preflight sees on OpenCode 2 servers.
  bool sessionReverted = false;
  Object? sessionError;

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<List<MessageWithParts>> messages(String id) async => [];

  @override
  Future<Session> session(String id) async {
    if (sessionError != null) throw sessionError!;
    return Session(id: id, reverted: sessionReverted);
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
    await beforePrompt?.call();
    if (promptPlan.isNotEmpty) {
      final planned = promptPlan.removeAt(0);
      if (planned != null) throw planned;
    }
    prompts.add((sessionID: sessionID, text: text, model: model));
  }
}

Future<QueueController> queueController(
  FakeApi api, {
  StreamStatus status = StreamStatus.connected,
  String? flavor,
  Object? queue,
  bool secondProfile = false,
}) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'profile-1',
        'name': 'Test server',
        'baseUrl': 'http://localhost',
        'username': '',
        'flavor': ?flavor,
      },
      if (secondProfile)
        {
          'id': 'profile-2',
          'name': 'Other server',
          'baseUrl': 'http://other.localhost',
          'username': '',
        },
    ]),
    'oc.activeProfile': 'profile-1',
    'oc.offlineQueue': ?queue,
  });
  return restartQueueController(api, status: status);
}

/// Builds a controller the way a fresh process would: every store reloads
/// from whatever the platform preferences hold right now.
Future<QueueController> restartQueueController(
  FakeApi api, {
  StreamStatus status = StreamStatus.connected,
}) async {
  SharedPreferences.resetStatic();
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.load();
  final controller = QueueController(store)
    ..api = api
    ..status = status;
  return controller;
}

const attachment = PromptAttachment(
  mime: 'text/plain',
  filename: 'notes.txt',
  url: 'data:text/plain;base64,bm90ZXM=',
);

QueuedPrompt queuedEntry(
  String id, {
  String profileID = 'profile-1',
  String sessionID = 'session-1',
  String text = 'queued text',
  String? error,
  List<PromptAttachment> attachments = const [],
  List<PromptAgentMention> mentions = const [],
  int? dispatchedAt,
}) => QueuedPrompt(
  id: id,
  profileID: profileID,
  sessionID: sessionID,
  text: text,
  attachments: attachments,
  mentions: mentions,
  createdAt: 1,
  error: error,
  dispatchedAt: dispatchedAt,
);

Future<void> pumpChat(WidgetTester tester, ConnectionController conn) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(conn)],
      child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
    ),
  );
  await tester.pump();
  await tester.pump();
}

/// Lets a flush started by the controller itself (not awaited by the test)
/// run to the point where [done] holds, failing rather than hanging when
/// it never does.
Future<void> settle(bool Function() done) async {
  for (var i = 0; i < 200; i++) {
    if (done()) return;
    await Future<void>.delayed(Duration.zero);
  }
  fail('the background flush never reached the expected state');
}

/// The widget-test counterpart of [settle]: pumps frames until [done].
Future<void> settleWidgets(WidgetTester tester, bool Function() done) async {
  for (var i = 0; i < 50; i++) {
    if (done()) return;
    await tester.pump();
  }
  fail('the background flush never reached the expected state');
}

/// Opens the queued item's menu (each waiting message carries its own).
Future<void> openQueuedMenu(WidgetTester tester, {int index = 0}) async {
  await tester.tap(find.byKey(ValueKey('queued-send-$index')));
  await tester.pumpAndSettle();
}

/// Chooses [key] from the queued item's menu.
Future<void> openQueuedAction(
  WidgetTester tester,
  String key, {
  int index = 0,
}) async {
  await openQueuedMenu(tester, index: index);
  await tester.tap(find.byKey(ValueKey(key)));
  await tester.pumpAndSettle();
}

/// A send in flight: its menu offers no resend, and Edit and Discard do
/// nothing, so no action can pull the draft out from under the request.
Future<void> expectQueuedActionsInert(
  WidgetTester tester,
  ConnectionController controller,
) async {
  await openQueuedMenu(tester);
  expect(find.byKey(const ValueKey('queued-action-resend')), findsNothing);
  for (final key in ['queued-action-edit', 'queued-action-discard']) {
    expect(find.byKey(ValueKey(key)), findsOneWidget);
    await tester.tap(find.byKey(ValueKey(key)), warnIfMissed: false);
    await tester.pump();
    expect(find.text('Discard queued draft?'), findsNothing);
    expect(controller.queuedPromptCount, 1);
  }
  await tester.sendKeyEvent(LogicalKeyboardKey.escape);
  await tester.pumpAndSettle();
}
