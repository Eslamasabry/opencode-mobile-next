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

class _QueueController extends ConnectionController {
  _QueueController(super.store);
  Completer<ServerGateway?>? pendingTransport;

  @override
  Future<ServerGateway?> prepareActionTransport() =>
      pendingTransport?.future ?? super.prepareActionTransport();
}

class _RefusingQueueStore extends InMemorySharedPreferencesStore {
  _RefusingQueueStore(super.data) : super.withData();
  bool _blocks(String key) => key.endsWith('oc.offlineQueue');
  @override
  Future<bool> setValue(String type, String key, Object value) async =>
      _blocks(key) ? false : super.setValue(type, key, value);
  @override
  Future<bool> remove(String key) async =>
      _blocks(key) ? false : super.remove(key);
}

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
    PromptDelivery? delivery,
  }) async {
    if (promptPlan.isNotEmpty) {
      final planned = promptPlan.removeAt(0);
      if (planned != null) throw planned;
    }
    prompts.add((sessionID: sessionID, text: text, model: model));
  }
}

Future<_QueueController> _controller(
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
  final controller = _QueueController(store)
    ..api = api
    ..status = status;
  return controller;
}

QueuedPrompt _entry(
  String id, {
  String profileID = 'profile-1',
  String sessionID = 'session-1',
  String text = 'queued text',
  String? error,
  List<PromptAttachment> attachments = const [],
}) => QueuedPrompt(
  id: id,
  profileID: profileID,
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

  test('failed queue writes preserve the existing queued prompts', () async {
    final controller = await _controller(_FakeApi());
    addTearDown(controller.dispose);
    await controller.queuePrompt(_entry('saved'));
    final platform = SharedPreferencesStorePlatform.instance;
    SharedPreferencesStorePlatform.instance = _RefusingQueueStore(
      await platform.getAll(),
    );
    addTearDown(() => SharedPreferencesStorePlatform.instance = platform);
    await expectLater(
      controller.queuePrompt(_entry('new')),
      throwsA(isA<OfflineQueueWriteException>()),
    );
    await expectLater(
      controller.removeQueuedPrompt('saved'),
      throwsA(isA<OfflineQueueWriteException>()),
    );
    expect(controller.queuedPromptsFor('session-1').map((entry) => entry.id), [
      'saved',
    ]);
  });

  test(
    'a queued send rechecks its location after transport preparation',
    () async {
      final api = _FakeApi();
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(_entry('saved'));
      controller.pendingTransport = Completer<ServerGateway?>();
      final flush = controller.flushOfflineQueue();
      controller.directory = '/another-project';
      controller.pendingTransport!.complete(api);
      await flush;
      expect(api.prompts, isEmpty);
      expect(controller.queuedPromptCount, 1);
    },
  );

  test('a draft discarded while transport wakes is never sent', () async {
    final api = _FakeApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await controller.queuePrompt(_entry('discarded'));
    controller.pendingTransport = Completer<ServerGateway?>();
    final flush = controller.flushOfflineQueue();
    await controller.removeQueuedPrompt('discarded');
    controller.pendingTransport!.complete(api);
    await flush;
    expect(api.prompts, isEmpty);
    expect(controller.queuedPromptCount, 0);
  });

  test('flush sends queued prompts oldest first', () async {
    final api = _FakeApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await controller.queuePrompt(
      _entry(
        'q1',
        text: 'first',
        attachments: const [
          PromptAttachment(
            mime: 'text/plain',
            filename: 'notes.txt',
            url: 'data:text/plain;base64,bm90ZXM=',
          ),
        ],
      ),
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

  test('a flush cycle reports how many drafts it sent', () async {
    final api = _FakeApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await controller.queuePrompt(_entry('q1', text: 'first'));
    await controller.queuePrompt(_entry('q2', text: 'second'));

    final before = controller.offlineFlushRevision;
    await controller.flushOfflineQueue();
    expect(controller.offlineFlushRevision, before + 1);
    expect(controller.lastFlushedPromptCount, 2);
    expect(controller.lastFlushSkippedForOtherProfiles, 0);

    // A flush that delivers nothing announces nothing.
    await controller.flushOfflineQueue();
    expect(controller.offlineFlushRevision, before + 1);
  });

  test('drafts for other profiles stay queued and are counted', () async {
    final api = _FakeApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await controller.queuePrompt(_entry('mine', text: 'active server'));
    await controller.queuePrompt(
      _entry(
        'other',
        profileID: 'profile-2',
        sessionID: 'session-9',
        text: 'other server',
      ),
    );

    await controller.flushOfflineQueue();

    expect(api.prompts.map((p) => p.text).toList(), ['active server']);
    expect(controller.queuedPromptCount, 0);
    expect(controller.queuedPromptCountForOtherProfiles, 1);
    expect(controller.lastFlushedPromptCount, 1);
    expect(controller.lastFlushSkippedForOtherProfiles, 1);
    // The skipped draft persists for its own profile's next connection.
    final prefs = await SharedPreferences.getInstance();
    expect(OfflineQueueStore(prefs: prefs).load().single.id, 'other');
  });

  testWidgets('a completed flush surfaces a sent confirmation', (tester) async {
    final api = _FakeApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await controller.queuePrompt(_entry('q1', text: 'first'));
    await controller.queuePrompt(
      _entry(
        'other',
        profileID: 'profile-2',
        sessionID: 'session-9',
        text: 'other server',
      ),
    );
    await _pumpChat(tester, controller);

    await controller.flushOfflineQueue();
    await tester.pump();
    await tester.pump();

    expect(
      find.text('Sent 1 queued prompt · 1 draft waiting for other servers'),
      findsOneWidget,
    );
  });

  testWidgets('the offline banner counts drafts waiting for other servers', (
    tester,
  ) async {
    final api = _FakeApi();
    final controller = await _controller(
      api,
      status: StreamStatus.disconnected,
    );
    addTearDown(controller.dispose);
    await controller.queuePrompt(_entry('mine', text: 'active server'));
    await controller.queuePrompt(
      _entry(
        'other',
        profileID: 'profile-2',
        sessionID: 'session-9',
        text: 'other server',
      ),
    );
    await _pumpChat(tester, controller);

    expect(
      find.textContaining(
        '1 draft queued to send on reconnect. '
        '1 draft waiting for other servers.',
      ),
      findsOneWidget,
    );
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

    await tester.tap(find.byKey(const ValueKey('queued-action-discard')));
    await tester.pumpAndSettle();

    expect(find.text('Discard queued draft?'), findsOneWidget);
    await tester.tap(find.widgetWithText(FilledButton, 'Discard draft'));
    await tester.pumpAndSettle();

    expect(controller.queuedPromptCount, 0);
    expect(find.byKey(const ValueKey('queued-send-0')), findsNothing);
  });

  group('queue limits', () {
    QueuedPrompt sized(
      String id, {
      required int bytes,
      required int createdAt,
    }) => QueuedPrompt(
      id: id,
      profileID: 'profile-1',
      sessionID: 'session-1',
      text: 'x' * bytes,
      createdAt: createdAt,
    );

    final now = DateTime.utc(2026, 8, 29);
    int daysAgo(int days) =>
        now.subtract(Duration(days: days)).millisecondsSinceEpoch;

    test('entries past the TTL are dropped with a notice', () {
      final result = OfflineQueueStore.enforceLimits([
        sized('stale', bytes: 10, createdAt: daysAgo(15)),
        sized('fresh', bytes: 10, createdAt: daysAgo(1)),
      ], now: now);

      expect(result.kept.map((entry) => entry.id), ['fresh']);
      expect(result.expired, 1);
      expect(result.notice, contains('1 too old to send'));
      expect(result.notice, contains('Discarded 1 queued draft'));
    });

    test('an unreadable timestamp is kept rather than deleted', () {
      // A missing createdAt decodes to zero. Unknown age is not evidence of
      // staleness, and the user's prompt gets the benefit of the doubt.
      final result = OfflineQueueStore.enforceLimits([
        sized('unknown', bytes: 10, createdAt: 0),
        sized('placeholder', bytes: 10, createdAt: 1),
      ], now: now);

      expect(result.kept, hasLength(2));
      expect(result.expired, 0);
      expect(result.notice, isNull);
    });

    test('the entry cap evicts the oldest first', () {
      final result = OfflineQueueStore.enforceLimits([
        for (var i = 0; i < OfflineQueueStore.maxEntries + 3; i++)
          sized('q$i', bytes: 10, createdAt: daysAgo(1) + i),
      ], now: now);

      expect(result.kept, hasLength(OfflineQueueStore.maxEntries));
      expect(result.overflowed, 3);
      expect(result.kept.first.id, 'q3', reason: 'the oldest three go');
      expect(result.notice, contains('3 to stay within the queue limit'));
    });

    test('the byte quota evicts the oldest until it fits', () {
      final big = OfflineQueueStore.maxTotalBytes ~/ 2;
      final result = OfflineQueueStore.enforceLimits([
        sized('old', bytes: big, createdAt: daysAgo(3)),
        sized('mid', bytes: big, createdAt: daysAgo(2)),
        sized('new', bytes: big, createdAt: daysAgo(1)),
      ], now: now);

      expect(result.kept.map((entry) => entry.id), ['mid', 'new']);
      expect(result.oversized, 1);
      expect(
        result.kept.fold(0, (sum, entry) => sum + entry.payloadBytes),
        lessThanOrEqualTo(OfflineQueueStore.maxTotalBytes),
      );
    });

    test('a single oversized entry is never evicted into nothing', () {
      // The per-entry cap already refused anything larger; emptying the queue
      // here would delete the only draft the user has.
      final result = OfflineQueueStore.enforceLimits([
        sized(
          'only',
          bytes: OfflineQueueStore.maxTotalBytes + 10,
          createdAt: daysAgo(1),
        ),
      ], now: now);

      expect(result.kept, hasLength(1));
      expect(result.oversized, 0);
    });

    test('queuing past the entry cap evicts and reports it', () async {
      final controller = await _controller(
        _FakeApi(),
        status: StreamStatus.disconnected,
      );
      addTearDown(controller.dispose);
      for (var i = 0; i < OfflineQueueStore.maxEntries; i++) {
        expect(
          await controller.queuePrompt(
            sized('q$i', bytes: 10, createdAt: daysAgo(1) + i),
          ),
          isTrue,
        );
      }
      expect(controller.takeQueueEvictionNotice(), isNull);

      expect(
        await controller.queuePrompt(
          sized('newest', bytes: 10, createdAt: daysAgo(0)),
        ),
        isTrue,
      );

      expect(controller.queuedPromptCount, OfflineQueueStore.maxEntries);
      expect(
        controller.queuedPromptsFor('session-1').map((entry) => entry.id),
        contains('newest'),
      );
      final notice = controller.takeQueueEvictionNotice();
      expect(notice, contains('1 to stay within the queue limit'));
      // One-shot: the next read has nothing left to say.
      expect(controller.takeQueueEvictionNotice(), isNull);
    });

    test('a stale queue is trimmed and rewritten on first read', () async {
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
        'oc.offlineQueue': jsonEncode([
          {
            'id': 'ancient',
            'profileID': 'profile-1',
            'sessionID': 'session-1',
            'text': 'written a month ago',
            'createdAt': DateTime.now()
                .subtract(const Duration(days: 40))
                .millisecondsSinceEpoch,
          },
          {
            'id': 'recent',
            'profileID': 'profile-1',
            'sessionID': 'session-1',
            'text': 'written today',
            'createdAt': DateTime.now().millisecondsSinceEpoch,
          },
        ]),
      });
      final prefs = await SharedPreferences.getInstance();
      final store = ProfileStore(prefs: prefs);
      await store.load();
      final controller = ConnectionController(store);
      addTearDown(controller.dispose);

      expect(
        controller.queuedPromptsFor('session-1').map((entry) => entry.id),
        ['recent'],
      );
      expect(controller.takeQueueEvictionNotice(), contains('too old to send'));
      // Written back, so the next start does not re-evict the same entry.
      await Future<void>.delayed(Duration.zero);
      expect(prefs.getString('oc.offlineQueue'), isNot(contains('ancient')));
    });

    test('bulk clears drop everything and report the sizes', () async {
      final controller = await _controller(
        _FakeApi(),
        status: StreamStatus.disconnected,
      );
      addTearDown(controller.dispose);
      await controller.queuePrompt(_entry('q1'));
      await controller.saveSessionDraft('session-1', 'unsent text');

      expect(controller.totalQueuedPromptCount, 1);
      expect(controller.totalSessionDraftCount, 1);
      expect(controller.queuedPromptBytes, greaterThan(0));
      expect(controller.sessionDraftBytes, greaterThan(0));

      expect(await controller.clearAllQueuedPrompts(), isTrue);
      expect(await controller.clearAllSessionDrafts(), isTrue);
      expect(controller.totalQueuedPromptCount, 0);
      expect(controller.totalSessionDraftCount, 0);
      expect(controller.queuedPromptBytes, 0);
      expect(controller.sessionDraftBytes, 0);
    });
  });
}
