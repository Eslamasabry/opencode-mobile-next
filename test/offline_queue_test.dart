import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/offline_queue.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _FakeApi extends OpenCodeApi {
  _FakeApi() : super(baseUrl: 'http://localhost');

  final List<({String sessionID, String text, ModelRef? model})> prompts = [];

  /// Per-attempt plan consumed from the front: null means success, an error
  /// object is thrown. An empty plan means every attempt succeeds.
  final List<Object?> promptPlan = [];

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<List<MessageWithParts>> messages(String id) async => [];

  @override
  Future<Session> session(String id) async => Session(id: id);

  @override
  Future<void> promptAsync(
    String sessionID, {
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
    List<PromptAgentMention> agentMentions = const [],
  }) async {
    if (promptPlan.isNotEmpty) {
      final planned = promptPlan.removeAt(0);
      if (planned != null) throw planned;
    }
    prompts.add((sessionID: sessionID, text: text, model: model));
  }
}

Future<ConnectionController> _controller(
  _FakeApi api, {
  StreamStatus status = StreamStatus.connected,
}) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'profile-1',
        'name': 'Test server',
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
    ..status = status;
  return controller;
}

QueuedPrompt _entry(
  String id, {
  String sessionID = 'session-1',
  String text = 'queued text',
  String? error,
  List<PromptAttachment> attachments = const [],
}) => QueuedPrompt(
  id: id,
  profileID: 'profile-1',
  sessionID: sessionID,
  text: text,
  attachments: attachments,
  createdAt: 1,
  error: error,
);

Future<void> _pumpChat(WidgetTester tester, ConnectionController conn) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(conn)],
      child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
    ),
  );
  await tester.pump();
  await tester.pump();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    // ProfileStore.load restores passwords through flutter_secure_storage,
    // whose unmocked platform channel never answers inside testWidgets (in
    // plain tests it throws MissingPluginException, which load catches).
    // Answer reads with null so widget tests that load a stored profile
    // cannot hang.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  test('queued prompts persist and reload with attachments intact', () async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final store = OfflineQueueStore(prefs: prefs);
    final entries = [
      _entry(
        'q1',
        attachments: const [
          PromptAttachment(
            mime: 'text/plain',
            filename: 'notes.txt',
            url: 'data:text/plain;base64,bm90ZXM=',
          ),
        ],
      ),
      _entry('q2', text: 'second', error: 'declared failure'),
    ];
    expect(await store.save(entries), isTrue);

    final reloaded = OfflineQueueStore(prefs: prefs).load();
    expect(reloaded, hasLength(2));
    expect(reloaded.first.id, 'q1');
    expect(reloaded.first.attachments.single.filename, 'notes.txt');
    expect(
      reloaded.first.attachments.single.url,
      'data:text/plain;base64,bm90ZXM=',
    );
    expect(reloaded.last.error, 'declared failure');
  });

  test('oversized drafts are rejected instead of queued', () async {
    final api = _FakeApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);

    final oversized = _entry(
      'big',
      attachments: [
        PromptAttachment(
          mime: 'application/octet-stream',
          filename: 'huge.bin',
          url: 'x' * (OfflineQueueStore.maxEntryBytes + 1),
        ),
      ],
    );
    expect(await controller.queuePrompt(oversized), isFalse);
    expect(controller.queuedPromptCount, 0);

    expect(await controller.queuePrompt(_entry('ok')), isTrue);
    expect(controller.queuedPromptCount, 1);
  });

  test('flush sends queued prompts oldest first', () async {
    final api = _FakeApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await controller.queuePrompt(
      _entry('q1', text: 'first', attachments: const [
        PromptAttachment(
          mime: 'text/plain',
          filename: 'notes.txt',
          url: 'data:text/plain;base64,bm90ZXM=',
        ),
      ]),
    );
    await controller.queuePrompt(_entry('q2', text: 'second'));
    await controller.queuePrompt(_entry('q3', text: 'third'));

    await controller.flushOfflineQueue();

    expect(api.prompts.map((p) => p.text).toList(), [
      'first',
      'second',
      'third',
    ]);
    expect(controller.queuedPromptCount, 0);
    // Persistence reflects the drained queue.
    final prefs = await SharedPreferences.getInstance();
    expect(OfflineQueueStore(prefs: prefs).load(), isEmpty);
  });

  test('a declared failure keeps its entry with the error and later '
      'connectivity loss stops the flush', () async {
    final api = _FakeApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await controller.queuePrompt(_entry('q1', text: 'first'));
    await controller.queuePrompt(_entry('q2', text: 'second'));

    // First attempt: declared failure -> entry keeps error, flush continues.
    api.promptPlan.add(ApiException('Bad model', statusCode: 400));
    await controller.flushOfflineQueue();
    expect(api.prompts.map((p) => p.text).toList(), ['second']);
    expect(controller.queuedPromptCount, 1);
    expect(
      controller.queuedPromptsFor('session-1').single.error,
      contains('Bad model'),
    );

    // Next flush: connectivity failure -> entry stays, flush stops.
    api.promptPlan.add(ApiException('socket closed'));
    await controller.flushOfflineQueue();
    expect(controller.queuedPromptCount, 1);
    expect(api.prompts, hasLength(1));

    // Server back: the remaining entry delivers.
    await controller.flushOfflineQueue();
    expect(controller.queuedPromptCount, 0);
    expect(api.prompts.map((p) => p.text).toList(), ['second', 'first']);
  });

  testWidgets('sending while disconnected queues a visible draft', (
    tester,
  ) async {
    final api = _FakeApi();
    final controller = await _controller(
      api,
      status: StreamStatus.disconnected,
    );
    addTearDown(controller.dispose);
    await _pumpChat(tester, controller);

    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'offline draft',
    );
    await tester.pump();
    await tester.tap(find.byTooltip('Send'));
    await tester.pump();
    await tester.pump();

    expect(api.prompts, isEmpty);
    expect(find.byKey(const ValueKey('queued-send-0')), findsOneWidget);
    expect(find.text('Queued — will send when reconnected'), findsWidgets);
    expect(controller.queuedPromptCount, 1);
    expect(
      tester.widget<TextField>(find.byType(TextField)).controller?.text,
      isEmpty,
    );
  });

  testWidgets('a queued draft can be edited back into the composer', (
    tester,
  ) async {
    final api = _FakeApi();
    final controller = await _controller(
      api,
      status: StreamStatus.disconnected,
    );
    addTearDown(controller.dispose);
    await controller.queuePrompt(_entry('q1', text: 'edit me'));
    await _pumpChat(tester, controller);

    await tester.tap(find.byKey(const ValueKey('queued-send-0')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('queued-action-edit')));
    await tester.pumpAndSettle();

    expect(controller.queuedPromptCount, 0);
    expect(find.byKey(const ValueKey('queued-send-0')), findsNothing);
    expect(
      tester.widget<TextField>(find.byType(TextField)).controller?.text,
      'edit me',
    );
  });

  testWidgets('discarding a queued draft confirms first', (tester) async {
    final api = _FakeApi();
    final controller = await _controller(
      api,
      status: StreamStatus.disconnected,
    );
    addTearDown(controller.dispose);
    await controller.queuePrompt(_entry('q1', text: 'discard me'));
    await _pumpChat(tester, controller);

    await tester.tap(find.byKey(const ValueKey('queued-send-0')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('queued-action-discard')));
    await tester.pumpAndSettle();

    expect(find.text('Discard queued draft?'), findsOneWidget);
    await tester.tap(find.widgetWithText(FilledButton, 'Discard draft'));
    await tester.pumpAndSettle();

    expect(controller.queuedPromptCount, 0);
    expect(find.byKey(const ValueKey('queued-send-0')), findsNothing);
  });
}
