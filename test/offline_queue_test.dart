import 'support/complete_message_history.dart';
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

class _RefusingQueueStore extends InMemorySharedPreferencesStore {
  _RefusingQueueStore(super.data) : super.withData();
  bool _blocks(String key) => key.endsWith('oc.offlineQueue');
  Completer<void>? gate;
  bool allowRemove = false;
  @override
  Future<bool> setValue(String type, String key, Object value) async {
    if (_blocks(key)) {
      await gate?.future;
      return false;
    }
    return super.setValue(type, key, value);
  }

  @override
  Future<bool> remove(String key) async =>
      _blocks(key) && !allowRemove ? false : super.remove(key);
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
class _ScriptedQueueStore extends InMemorySharedPreferencesStore {
  _ScriptedQueueStore(super.data) : super.withData();

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

/// Puts a [_ScriptedQueueStore] holding the current preferences behind the
/// plugin for the rest of the test. Call after the controller is built so
/// its setup writes go through the ordinary store.
Future<_ScriptedQueueStore> _scriptedDisk() async {
  final original = SharedPreferencesStorePlatform.instance;
  final disk = _ScriptedQueueStore(await original.getAll());
  SharedPreferencesStorePlatform.instance = disk;
  addTearDown(() => SharedPreferencesStorePlatform.instance = original);
  return disk;
}

class _FakeApi extends OpenCodeApi with CompleteMessageHistory {
  _FakeApi() : super(baseUrl: 'http://localhost');

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

Future<_QueueController> _controller(
  _FakeApi api, {
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
  return _restart(api, status: status);
}

/// Builds a controller the way a fresh process would: every store reloads
/// from whatever the platform preferences hold right now.
Future<_QueueController> _restart(
  _FakeApi api, {
  StreamStatus status = StreamStatus.connected,
}) async {
  SharedPreferences.resetStatic();
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.load();
  final controller = _QueueController(store)
    ..api = api
    ..status = status;
  return controller;
}

const _attachment = PromptAttachment(
  mime: 'text/plain',
  filename: 'notes.txt',
  url: 'data:text/plain;base64,bm90ZXM=',
);

QueuedPrompt _entry(
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

/// Lets a flush started by the controller itself (not awaited by the test)
/// run to the point where [done] holds, failing rather than hanging when
/// it never does.
Future<void> _settle(bool Function() done) async {
  for (var i = 0; i < 200; i++) {
    if (done()) return;
    await Future<void>.delayed(Duration.zero);
  }
  fail('the background flush never reached the expected state');
}

/// A queue action aimed at an entry that is on the wire, or whose
/// bookkeeping has not yet been persisted, is declined: false, and the
/// entry stays exactly as it is.
Future<void> _expectInFlightRefusal(Future<bool> action) async =>
    expect(await action, isFalse);

/// The widget-test counterpart of [_settle]: pumps frames until [done].
Future<void> _settleWidgets(WidgetTester tester, bool Function() done) async {
  for (var i = 0; i < 50; i++) {
    if (done()) return;
    await tester.pump();
  }
  fail('the background flush never reached the expected state');
}

Future<void> _openQueuedAction(WidgetTester tester, String key) async {
  await tester.tap(find.byKey(ValueKey(key)));
  await tester.pumpAndSettle();
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
    // Profile deletion clears the home-screen widget through this channel.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('oc/background'),
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

  test(
    'corrupt persisted queue fails closed until explicitly cleared',
    () async {
      final raw = jsonEncode([
        {
          'id': 'valid',
          'profileID': 'profile-1',
          'sessionID': 'session-1',
          'text': 'keep me',
          'createdAt': 1,
        },
        {'id': 'missing-session'},
      ]);
      SharedPreferences.setMockInitialValues({'oc.offlineQueue': raw});
      final prefs = await SharedPreferences.getInstance();
      final store = OfflineQueueStore(prefs: prefs);
      expect(store.load(), isEmpty);
      expect(store.readable, isFalse);
      expect(await store.save([_entry('replacement')]), isFalse);
      expect(prefs.getString('oc.offlineQueue'), raw);
      expect(await store.save(const []), isTrue);
      expect(store.load(), isEmpty);
    },
  );

  test(
    'wrongly typed persisted queue fails closed until explicitly cleared',
    () async {
      SharedPreferences.setMockInitialValues({'oc.offlineQueue': 42});
      final prefs = await SharedPreferences.getInstance();
      final store = OfflineQueueStore(prefs: prefs);

      expect(store.load(), isEmpty);
      expect(store.readable, isFalse);
      expect(store.storedBytes(), 0);
      expect(await store.save([_entry('replacement')]), isFalse);
      expect(await store.save(const []), isTrue);
      expect(store.load(), isEmpty);
      expect(store.readable, isTrue);
    },
  );

  test('queue save snapshots caller attachments and mentions', () async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final store = OfflineQueueStore(prefs: prefs);
    final attachments = <PromptAttachment>[
      const PromptAttachment(
        mime: 'text/plain',
        filename: 'first.txt',
        url: 'data:text/plain;base64,Zmlyc3Q=',
      ),
    ];
    final mentions = <PromptAgentMention>[
      const PromptAgentMention(name: 'agent', value: 'build', start: 0, end: 6),
    ];
    final entry = _entry(
      'snapshot',
      attachments: attachments,
      mentions: mentions,
    );
    final save = store.save([entry]);
    attachments.add(
      const PromptAttachment(
        mime: 'text/plain',
        filename: 'mutated.txt',
        url: 'data:text/plain;base64,bXV0YXRlZA==',
      ),
    );
    mentions.add(
      const PromptAgentMention(name: 'agent', value: 'plan', start: 0, end: 4),
    );
    expect(
      () => entry.attachments.add(attachments.last),
      throwsUnsupportedError,
    );
    expect(() => entry.mentions.add(mentions.last), throwsUnsupportedError);
    expect(await save, isTrue);
    final restored = OfflineQueueStore(prefs: prefs).load().single;
    expect(restored.attachments, hasLength(1));
    expect(restored.mentions, hasLength(1));
  });

  test('queue remove follows an in-flight stale save', () async {
    SharedPreferences.setMockInitialValues({});
    await SharedPreferences.getInstance();
    final originalPlatform = SharedPreferencesStorePlatform.instance;
    final disk = _RefusingQueueStore(await originalPlatform.getAll());
    disk.gate = Completer<void>();
    SharedPreferencesStorePlatform.instance = disk;
    SharedPreferences.resetStatic();
    addTearDown(
      () => SharedPreferencesStorePlatform.instance = originalPlatform,
    );
    final store = OfflineQueueStore(
      prefs: await SharedPreferences.getInstance(),
    );
    final entry = _entry('stale');
    final stale = store.save([entry]);
    await Future<void>.delayed(Duration.zero);
    final remove = store.save([]);
    disk.allowRemove = true;
    disk.gate!.complete();
    expect(await stale, isFalse);
    expect(await remove, isTrue);
    expect(store.load(), isEmpty);
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

  test('failures after dispatch park the entry for review — a declared '
      'error lets the batch continue, a connectivity loss stops it, and '
      'only an explicit resend delivers', () async {
    final api = _FakeApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await controller.queuePrompt(_entry('q1', text: 'first'));
    await controller.queuePrompt(_entry('q2', text: 'second'));
    await controller.queuePrompt(_entry('q3', text: 'third'));

    // The server answers the first prompt with an error and the socket
    // drops on the second. Neither proves the prompt was not enqueued
    // first, so both keep their dispatch marker with the failure inline.
    // The declared error lets the flush move on; the dropped socket ends
    // it, leaving the third untouched.
    api.promptPlan.addAll([
      ApiException('Bad model', statusCode: 400),
      ApiException('socket closed'),
    ]);
    await controller.flushOfflineQueue();
    expect(api.prompts, isEmpty);
    var queued = controller.queuedPromptsFor('session-1');
    expect(queued.map((e) => e.id), ['q1', 'q2', 'q3']);
    expect(queued[0].dispatchedAt, isNotNull);
    expect(queued[0].error, contains('Bad model'));
    expect(queued[1].dispatchedAt, isNotNull);
    expect(queued[1].error, contains('socket closed'));
    expect(queued[2].dispatchedAt, isNull);
    expect(queued[2].error, isNull);

    // Server back: the untouched draft goes; the unconfirmed ones do not.
    await controller.flushOfflineQueue();
    expect(api.prompts.map((p) => p.text).toList(), ['third']);
    queued = controller.queuedPromptsFor('session-1');
    expect(queued.map((e) => e.id), ['q1', 'q2']);

    // Only the user's explicit answer sends one again, and only that one.
    expect(await controller.resendQueuedPrompt('q2'), isTrue);
    await _settle(() => controller.queuedPromptCount == 1);
    expect(api.prompts.map((p) => p.text).toList(), ['third', 'second']);
    expect(controller.queuedPromptsFor('session-1').single.id, 'q1');
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

  testWidgets(
    'selection failure restores an undispatched draft without queuing',
    (tester) async {
      final api = _FakeApi();
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      final pending = Completer<void>();
      controller.selectionWait = pending.future;
      await _pumpChat(tester, controller);
      await tester.enterText(
        find.byKey(const Key('chat-composer-field')),
        'Keep this draft',
      );
      await tester.pump();
      await tester.tap(find.byTooltip('Send'));
      await tester.pump();
      pending.completeError(ApiException('selection timed out'));
      await tester.pump();
      await tester.pump();
      expect(api.prompts, isEmpty);
      expect(controller.queuedPromptsFor('session-1'), isEmpty);
      expect(
        tester
            .widget<TextField>(find.byKey(const Key('chat-composer-field')))
            .controller!
            .text,
        'Keep this draft',
      );
    },
  );

  testWidgets('a failed dispatched prompt queues its captured selection', (
    tester,
  ) async {
    final api = _FakeApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    final pending = Completer<void>();
    final started = Completer<void>();
    api.beforePrompt = () {
      started.complete();
      return pending.future;
    };
    api.promptPlan.add(ApiException('disconnected'));
    controller.selectedModel = ModelRef(providerID: 'p', modelID: 'original');
    controller.selectedAgent = 'build';
    await _pumpChat(tester, controller);
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'Keep my choice',
    );
    await tester.pump();
    await tester.tap(find.byTooltip('Send'));
    await tester.pump();
    expect(started.isCompleted, isTrue);
    controller.selectedModel = ModelRef(providerID: 'p', modelID: 'later');
    controller.selectedAgent = 'plan';
    pending.complete();
    await tester.pump();
    await tester.pump();
    final queued = controller.queuedPromptsFor('session-1').single;
    expect(queued.model!.wireName, 'p/original');
    expect(queued.agent, 'build');
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

  group('unconfirmed sends', () {
    test('the dispatch marker survives snapshots, errors, and disk', () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final store = OfflineQueueStore(prefs: prefs);
      final marked = _entry(
        'marked',
        attachments: const [_attachment],
        dispatchedAt: 1700000000000,
      );

      expect(marked.dispatched, isTrue);
      expect(marked.withError('later failure').dispatchedAt, 1700000000000);
      expect(marked.withError('later failure').error, 'later failure');
      expect(marked.withDispatchedAt(null).dispatchedAt, isNull);
      expect(marked.withDispatchedAt(null).attachments, hasLength(1));
      expect(_entry('plain').dispatched, isFalse);

      expect(await store.save([marked, _entry('plain')]), isTrue);
      final reloaded = OfflineQueueStore(prefs: prefs).load();
      expect(reloaded.first.dispatchedAt, 1700000000000);
      expect(reloaded.first.attachments.single.filename, 'notes.txt');
      expect(reloaded.last.dispatchedAt, isNull);
      // An older build's entry has no marker and decodes as never sent.
      expect(
        QueuedPrompt.fromJson({
          'id': 'legacy',
          'profileID': 'profile-1',
          'sessionID': 'session-1',
          'text': 'old',
          'createdAt': 1,
        })!.dispatchedAt,
        isNull,
      );
    });

    test('the marker reaches disk before the prompt leaves, and the '
        'accepted entry is removed from disk on its own', () async {
      final api = _FakeApi();
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(_entry('q1', text: 'first'));
      await controller.queuePrompt(_entry('q2', text: 'second'));
      final disk = await _scriptedDisk();

      final heldAtSend = <List<QueuedPrompt>>[];
      api.beforePrompt = () async => heldAtSend.add(await disk.onDisk());
      await controller.flushOfflineQueue();

      expect(api.prompts.map((p) => p.text).toList(), ['first', 'second']);
      // When the first prompt was on the wire, the device already held its
      // marker, and only its marker.
      expect(heldAtSend.first.map((e) => e.id), ['q1', 'q2']);
      expect(heldAtSend.first.first.dispatchedAt, isNotNull);
      expect(heldAtSend.first.last.dispatchedAt, isNull);
      // By the second send, the first entry was already gone from disk: its
      // removal was persisted on its own, not deferred to the end.
      expect(heldAtSend.last.map((e) => e.id), ['q2']);
      expect(heldAtSend.last.single.dispatchedAt, isNotNull);
      expect(await disk.onDisk(), isEmpty);
      expect(controller.queuedPromptCount, 0);
      expect(controller.lastFlushedPromptCount, 2);
    });

    test(
      'storage refusing the dispatch marker keeps the prompt unsent',
      () async {
        final api = _FakeApi();
        final controller = await _controller(api);
        addTearDown(controller.dispose);
        await controller.queuePrompt(_entry('q1', text: 'first'));
        await controller.queuePrompt(_entry('q2', text: 'second'));
        final revision = controller.offlineFlushRevision;
        final disk = await _scriptedDisk()
          ..defaultOutcome = false;

        await controller.flushOfflineQueue();

        // Nothing left the device: without a durable marker, a send could not
        // be told apart from a never-sent draft after a crash.
        expect(api.prompts, isEmpty);
        expect(controller.offlineFlushRevision, revision);
        final queued = controller.queuedPromptsFor('session-1');
        expect(queued.map((e) => e.id), ['q1', 'q2']);
        expect(queued.map((e) => e.dispatchedAt), [null, null]);
        expect(queued.map((e) => e.error), [null, null]);
        expect((await disk.onDisk()).map((e) => e.dispatchedAt).toList(), [
          null,
          null,
        ]);

        // Once storage recovers, the same drafts flush normally.
        disk.defaultOutcome = true;
        await controller.flushOfflineQueue();
        expect(api.prompts.map((p) => p.text).toList(), ['first', 'second']);
        expect(controller.queuedPromptCount, 0);
      },
    );

    test('an accepted send whose removal is refused stays marked, stops the '
        'batch, and is never resent after a restart', () async {
      final api = _FakeApi();
      var controller = await _controller(api);
      addTearDown(() => controller.dispose());
      await controller.queuePrompt(
        _entry('q1', text: 'first', attachments: const [_attachment]),
      );
      await controller.queuePrompt(_entry('q2', text: 'second'));
      final disk = await _scriptedDisk();
      // Marker write for q1 lands; the removal after acceptance does not.
      disk.plan.addAll([true, false]);
      final revision = controller.offlineFlushRevision;

      await controller.flushOfflineQueue();

      expect(api.prompts.map((p) => p.text).toList(), ['first']);
      expect(controller.offlineFlushRevision, revision);
      var queued = controller.queuedPromptsFor('session-1');
      expect(queued.map((e) => e.id), ['q1', 'q2']);
      expect(queued.first.dispatchedAt, isNotNull);
      // The device knows this one was accepted, says so, and will not
      // offer to send it again.
      expect(controller.queuedPromptAcceptedUnrecorded('q1'), isTrue);
      expect(queued.first.error, contains('accepted this prompt'));
      expect(queued.first.attachments.single.filename, 'notes.txt');
      expect(queued.last.dispatchedAt, isNull);
      expect(await controller.resendQueuedPrompt('q1'), isFalse);
      expect(api.prompts, hasLength(1));
      var held = await disk.onDisk();
      expect(held.map((e) => e.id), ['q1', 'q2']);
      expect(held.first.dispatchedAt, isNotNull);

      // Restart: the disk still says q1 was dispatched. It must not go
      // again, while the untouched q2 flushes as usual. The acceptance
      // itself was never recorded, so after a restart it reads as an
      // ordinary unconfirmed send.
      controller.dispose();
      controller = await _restart(api);
      await controller.flushOfflineQueue();

      expect(api.prompts.map((p) => p.text).toList(), ['first', 'second']);
      queued = controller.queuedPromptsFor('session-1');
      expect(queued.map((e) => e.id), ['q1']);
      expect(queued.single.dispatchedAt, isNotNull);
      expect(controller.queuedPromptAcceptedUnrecorded('q1'), isFalse);
      held = await disk.onDisk();
      expect(held.map((e) => e.id), ['q1']);
      expect(held.single.attachments.single.url, _attachment.url);
    });

    testWidgets('an accepted send the device could not record explains '
        'itself and offers no resend', (tester) async {
      final api = _FakeApi();
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(_entry('q1', text: 'landed'));
      await _pumpChat(tester, controller);
      final disk = await _scriptedDisk();
      disk.plan.addAll([true, false]);

      await controller.flushOfflineQueue();
      await tester.pumpAndSettle();

      expect(api.prompts, hasLength(1));
      expect(
        find.textContaining('OpenCode accepted this prompt'),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('queued-action-resend')), findsNothing);
      expect(find.byKey(const ValueKey('queued-action-edit')), findsOneWidget);
      expect(
        find.byKey(const ValueKey('queued-action-discard')),
        findsOneWidget,
      );
    });

    test('a process death between acceptance and the queue commit leaves an '
        'entry the next start shows for review instead of resending', () async {
      // Exactly what the device holds after the marker write, the server's
      // acceptance, and a kill before the removal could be written.
      final api = _FakeApi();
      final controller = await _controller(
        api,
        queue: jsonEncode([
          {
            'id': 'accepted',
            'profileID': 'profile-1',
            'sessionID': 'session-1',
            'text': 'may have landed',
            'attachments': [
              {
                'mime': _attachment.mime,
                'filename': _attachment.filename,
                'url': _attachment.url,
              },
            ],
            'createdAt': 1,
            'dispatchedAt': 1700000000000,
          },
          {
            'id': 'untouched',
            'profileID': 'profile-1',
            'sessionID': 'session-1',
            'text': 'never left',
            'createdAt': 2,
          },
        ]),
      );
      addTearDown(controller.dispose);

      expect(controller.queuedPromptCount, 2);
      expect(controller.queuedPromptReviewCount, 1);
      await controller.flushOfflineQueue();

      expect(api.prompts.map((p) => p.text).toList(), ['never left']);
      final review = controller.queuedPromptsFor('session-1').single;
      expect(review.id, 'accepted');
      expect(review.dispatchedAt, 1700000000000);
      expect(review.attachments.single.filename, 'notes.txt');
      expect(controller.queuedPromptReviewCount, 1);
      final prefs = await SharedPreferences.getInstance();
      final stored = OfflineQueueStore(prefs: prefs).load().single;
      expect(stored.id, 'accepted');
      expect(stored.dispatchedAt, 1700000000000);
    });

    test('transport uncertainty after dispatch keeps the marker and is '
        'never retried on its own', () async {
      // A status code says who answered, not whether the prompt was
      // enqueued before the answer, so a declared 4xx is as uncertain here
      // as a dropped socket.
      for (final failure in <Object>[
        ApiException('socket closed'),
        TimeoutException('no response'),
        ApiException('gateway timeout', statusCode: 504),
        ApiException('slow down', statusCode: 429),
        ApiException('Bad model', statusCode: 400),
      ]) {
        final api = _FakeApi();
        final controller = await _controller(api);
        addTearDown(controller.dispose);
        await controller.queuePrompt(
          _entry('q1', text: 'first', attachments: const [_attachment]),
        );
        final disk = await _scriptedDisk();

        api.promptPlan.add(failure);
        await controller.flushOfflineQueue();
        expect(api.prompts, isEmpty, reason: '$failure');
        var queued = controller.queuedPromptsFor('session-1').single;
        expect(queued.dispatchedAt, isNotNull, reason: '$failure');
        expect(queued.attachments.single.filename, 'notes.txt');
        final held = (await disk.onDisk()).single;
        expect(held.dispatchedAt, isNotNull, reason: '$failure');
        expect(held.attachments.single.url, _attachment.url);

        // Server healthy again: still no automatic resend, in this process
        // or the next.
        await controller.flushOfflineQueue();
        expect(api.prompts, isEmpty, reason: '$failure');
        final restarted = await _restart(api);
        addTearDown(restarted.dispose);
        await restarted.flushOfflineQueue();
        expect(api.prompts, isEmpty, reason: '$failure');
        queued = restarted.queuedPromptsFor('session-1').single;
        expect(queued.dispatchedAt, isNotNull, reason: '$failure');
      }
    });

    test(
      'a status-bearing uncertain failure lets the batch continue',
      () async {
        final api = _FakeApi();
        final controller = await _controller(api);
        addTearDown(controller.dispose);
        await controller.queuePrompt(_entry('q1', text: 'first'));
        await controller.queuePrompt(_entry('q2', text: 'second'));

        api.promptPlan.add(ApiException('internal error', statusCode: 500));
        await controller.flushOfflineQueue();

        expect(api.prompts.map((p) => p.text).toList(), ['second']);
        final parked = controller.queuedPromptsFor('session-1').single;
        expect(parked.id, 'q1');
        expect(parked.dispatchedAt, isNotNull);
        expect(parked.error, contains('internal error'));
      },
    );

    test('a failure before dispatch leaves the entry unmarked and '
        'retryable', () async {
      final api = _FakeApi();
      final controller = await _controller(api, flavor: 'v2');
      addTearDown(controller.dispose);
      expect(controller.supportsStagedRevert, isTrue);
      await controller.queuePrompt(_entry('q1', text: 'first'));
      await controller.queuePrompt(_entry('q2', text: 'second'));
      final disk = await _scriptedDisk();

      // The staged-revert preflight refuses before anything is sent.
      api.sessionReverted = true;
      await controller.flushOfflineQueue();
      expect(api.prompts, isEmpty);
      var queued = controller.queuedPromptsFor('session-1');
      expect(queued.map((e) => e.dispatchedAt), [null, null]);
      expect(queued.first.error, contains('staged revert'));
      expect((await disk.onDisk()).map((e) => e.dispatchedAt).toList(), [
        null,
        null,
      ]);

      // A connectivity failure during the preflight stops the flush and
      // marks nothing.
      api.sessionReverted = false;
      api.sessionError = ApiException('socket closed');
      await controller.flushOfflineQueue();
      expect(api.prompts, isEmpty);
      queued = controller.queuedPromptsFor('session-1');
      expect(queued.map((e) => e.dispatchedAt), [null, null]);

      // Preflight clean: both go, oldest first, without user action.
      api.sessionError = null;
      await controller.flushOfflineQueue();
      expect(api.prompts.map((p) => p.text).toList(), ['first', 'second']);
      expect(controller.queuedPromptCount, 0);
    });

    test('a resend whose marker clear is refused sends nothing', () async {
      final api = _FakeApi();
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(
        _entry('q1', text: 'first', dispatchedAt: 1700000000000),
      );
      final disk = await _scriptedDisk()
        ..defaultOutcome = false;

      await expectLater(
        controller.resendQueuedPrompt('q1'),
        throwsA(isA<OfflineQueueWriteException>()),
      );
      await controller.flushOfflineQueue();

      expect(api.prompts, isEmpty);
      expect(
        controller.queuedPromptsFor('session-1').single.dispatchedAt,
        1700000000000,
      );
      expect((await disk.onDisk()).single.dispatchedAt, 1700000000000);
    });

    testWidgets('an unconfirmed send is shown for review, not resent', (
      tester,
    ) async {
      final api = _FakeApi();
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(
        _entry(
          'q1',
          text: 'did this land?',
          attachments: const [_attachment],
          dispatchedAt: 1700000000000,
        ),
      );
      await _pumpChat(tester, controller);
      await controller.flushOfflineQueue();
      await tester.pump();

      expect(api.prompts, isEmpty);
      expect(find.byKey(const ValueKey('queued-send-0')), findsOneWidget);
      expect(
        find.text('Delivery unconfirmed — review before resending'),
        findsOneWidget,
      );
      expect(find.text('Queued — will send when reconnected'), findsNothing);
      expect(
        find.byKey(const ValueKey('queued-action-resend')),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('queued-action-edit')), findsOneWidget);
      expect(
        find.byKey(const ValueKey('queued-action-discard')),
        findsOneWidget,
      );
    });

    testWidgets('an unconfirmed send with a failure shows the failure', (
      tester,
    ) async {
      final api = _FakeApi();
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(
        _entry(
          'q1',
          text: 'did this land?',
          error: 'socket closed',
          dispatchedAt: 1700000000000,
        ),
      );
      await _pumpChat(tester, controller);

      expect(find.text('Delivery unconfirmed: socket closed'), findsOneWidget);
      expect(find.textContaining('Failed:'), findsNothing);
    });

    testWidgets('resending asks first and only sends on confirmation', (
      tester,
    ) async {
      final api = _FakeApi();
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(
        _entry(
          'q1',
          text: 'send me twice maybe',
          attachments: const [_attachment],
          dispatchedAt: 1700000000000,
        ),
      );
      await _pumpChat(tester, controller);

      await _openQueuedAction(tester, 'queued-action-resend');
      expect(find.text('Send this draft again?'), findsOneWidget);
      await tester.tap(find.widgetWithText(TextButton, 'Keep for review'));
      await tester.pumpAndSettle();
      expect(api.prompts, isEmpty);
      expect(
        controller.queuedPromptsFor('session-1').single.dispatchedAt,
        1700000000000,
      );

      await _openQueuedAction(tester, 'queued-action-resend');
      await tester.tap(find.widgetWithText(FilledButton, 'Send again'));
      await tester.pumpAndSettle();
      await _settleWidgets(tester, () => controller.queuedPromptCount == 0);

      expect(api.prompts.map((p) => p.text).toList(), ['send me twice maybe']);
      expect(find.byKey(const ValueKey('queued-send-0')), findsNothing);
      final prefs = await SharedPreferences.getInstance();
      expect(OfflineQueueStore(prefs: prefs).load(), isEmpty);
    });

    testWidgets('a refused resend keeps the entry in review', (tester) async {
      final api = _FakeApi();
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(
        _entry('q1', text: 'stuck', dispatchedAt: 1700000000000),
      );
      await _pumpChat(tester, controller);
      (await _scriptedDisk()).defaultOutcome = false;

      await _openQueuedAction(tester, 'queued-action-resend');
      await tester.tap(find.widgetWithText(FilledButton, 'Send again'));
      await tester.pumpAndSettle();

      expect(api.prompts, isEmpty);
      expect(
        find.textContaining('Could not save the queued draft'),
        findsOneWidget,
      );
      expect(
        find.text('Delivery unconfirmed — review before resending'),
        findsOneWidget,
      );
      expect(
        controller.queuedPromptsFor('session-1').single.dispatchedAt,
        1700000000000,
      );
    });

    testWidgets('editing an unconfirmed send restores text and attachments '
        'to the composer without sending', (tester) async {
      final api = _FakeApi();
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(
        _entry(
          'q1',
          text: 'edit me',
          attachments: const [_attachment],
          dispatchedAt: 1700000000000,
        ),
      );
      await _pumpChat(tester, controller);

      await _openQueuedAction(tester, 'queued-action-edit');

      expect(controller.queuedPromptCount, 0);
      expect(find.byKey(const ValueKey('queued-send-0')), findsNothing);
      expect(
        tester
            .widget<TextField>(find.byKey(const Key('chat-composer-field')))
            .controller
            ?.text,
        'edit me',
      );
      expect(find.text('notes.txt'), findsOneWidget);
      // The composer says why sending this again is a decision.
      expect(
        find.byKey(const Key('queued-edit-unconfirmed-note')),
        findsOneWidget,
      );
      expect(
        find.text(
          'It may already have reached OpenCode. Sending again can duplicate '
          'it.',
        ),
        findsOneWidget,
      );
      // Neither the edit nor a later flush sends anything by itself.
      await controller.flushOfflineQueue();
      await tester.pump();
      expect(api.prompts, isEmpty);
      final prefs = await SharedPreferences.getInstance();
      expect(OfflineQueueStore(prefs: prefs).load(), isEmpty);
    });

    testWidgets('editing a never-sent draft carries no duplicate warning', (
      tester,
    ) async {
      final api = _FakeApi();
      final controller = await _controller(
        api,
        status: StreamStatus.disconnected,
      );
      addTearDown(controller.dispose);
      await controller.queuePrompt(_entry('q1', text: 'plain draft'));
      await _pumpChat(tester, controller);

      await _openQueuedAction(tester, 'queued-action-edit');

      expect(controller.queuedPromptCount, 0);
      expect(
        find.byKey(const Key('queued-edit-unconfirmed-note')),
        findsNothing,
      );
      expect(find.textContaining('may already have reached'), findsNothing);
    });

    testWidgets('editing an unconfirmed send whose removal is refused '
        'leaves it queued and the composer untouched', (tester) async {
      final api = _FakeApi();
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(
        _entry(
          'q1',
          text: 'edit me',
          attachments: const [_attachment],
          dispatchedAt: 1700000000000,
        ),
      );
      await _pumpChat(tester, controller);
      final disk = await _scriptedDisk()
        ..defaultOutcome = false;

      await _openQueuedAction(tester, 'queued-action-edit');

      expect(
        find.textContaining('Could not remove this draft'),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('queued-send-0')), findsOneWidget);
      expect(
        tester
            .widget<TextField>(find.byKey(const Key('chat-composer-field')))
            .controller
            ?.text,
        isEmpty,
      );
      expect(find.text('notes.txt'), findsNothing);
      expect(api.prompts, isEmpty);
      final held = (await disk.onDisk()).single;
      expect(held.dispatchedAt, 1700000000000);
      expect(held.attachments.single.filename, 'notes.txt');
    });

    testWidgets('discarding an unconfirmed send honors a refused deletion', (
      tester,
    ) async {
      final api = _FakeApi();
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(
        _entry('q1', text: 'discard me', dispatchedAt: 1700000000000),
      );
      await _pumpChat(tester, controller);
      final disk = await _scriptedDisk()
        ..defaultOutcome = false;

      await _openQueuedAction(tester, 'queued-action-discard');
      await tester.tap(find.widgetWithText(FilledButton, 'Discard draft'));
      await tester.pumpAndSettle();

      expect(
        find.textContaining('Could not remove this draft'),
        findsOneWidget,
      );
      expect(controller.queuedPromptCount, 1);
      expect(find.byKey(const ValueKey('queued-send-0')), findsOneWidget);
      expect((await disk.onDisk()).single.dispatchedAt, 1700000000000);

      // Storage back: the discard goes through and nothing is ever sent.
      disk.defaultOutcome = true;
      await _openQueuedAction(tester, 'queued-action-discard');
      await tester.tap(find.widgetWithText(FilledButton, 'Discard draft'));
      await tester.pumpAndSettle();
      expect(controller.queuedPromptCount, 0);
      expect(await disk.onDisk(), isEmpty);
      await controller.flushOfflineQueue();
      expect(api.prompts, isEmpty);
    });

    testWidgets('a send in flight shows as sending with actions disabled', (
      tester,
    ) async {
      final api = _FakeApi();
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(_entry('q1', text: 'on the wire'));
      final held = Completer<void>();
      api.beforePrompt = () => held.future;
      await _pumpChat(tester, controller);

      final flush = controller.flushOfflineQueue();
      await _settleWidgets(tester, () => controller.queuedPromptSending('q1'));
      await tester.pump();

      expect(find.text('Sending…'), findsOneWidget);
      // No action can pull the draft out from under a request in flight.
      final actions = tester.widgetList<IconButton>(
        find.descendant(
          of: find.byKey(const ValueKey('queued-send-0')),
          matching: find.byType(IconButton),
        ),
      );
      expect(actions, isNotEmpty);
      expect(actions.where((button) => button.onPressed != null), isEmpty);

      held.complete();
      await flush;
      await tester.pumpAndSettle();
      expect(controller.queuedPromptSending('q1'), isFalse);
      expect(api.prompts.map((p) => p.text).toList(), ['on the wire']);
      expect(find.byKey(const ValueKey('queued-send-0')), findsNothing);
    });
  });

  group('unconfirmed sends under concurrency', () {
    test('removing another server while the marker write is pending keeps '
        'the marker and never resurrects the removed entries', () async {
      final api = _FakeApi();
      final controller = await _controller(api, secondProfile: true);
      addTearDown(controller.dispose);
      await controller.queuePrompt(
        _entry(
          'other',
          profileID: 'profile-2',
          sessionID: 'session-9',
          text: 'other server secret',
          attachments: const [_attachment],
        ),
      );
      await controller.queuePrompt(_entry('mine', text: 'mine'));
      final disk = await _scriptedDisk();
      final markerWrite = Completer<bool>();
      disk.plan.add(markerWrite.future);
      final sendGate = Completer<void>();
      api.beforePrompt = () => sendGate.future;

      final flush = controller.flushOfflineQueue();
      await _settle(() => disk.queueWritesRequested == 1);
      expect(controller.queuedPromptSending('mine'), isTrue);

      // The marker write is waiting on storage when the user removes the
      // other server. Its sweep must line up behind the marker, not read
      // the queue from before it.
      final deletion = controller.deleteProfileAndLocalData('profile-2');
      for (var i = 0; i < 20; i++) {
        await Future<void>.delayed(Duration.zero);
      }
      markerWrite.complete(true);
      final result = await deletion;

      expect(result.failures, isEmpty);
      expect(result.removedQueuedPrompts, 1);
      expect(controller.queuedPromptCountForProfile('profile-2'), 0);
      final surviving = controller.queuedPromptsFor('session-1').single;
      expect(surviving.id, 'mine');
      expect(surviving.dispatchedAt, isNotNull);
      var held = await disk.onDisk();
      expect(held.map((e) => e.id), ['mine']);
      expect(held.single.dispatchedAt, isNotNull);

      // The request was on the wire the whole time. Its acceptance removes
      // only its own entry; the removed server's data does not come back.
      sendGate.complete();
      await flush;
      expect(api.prompts.map((p) => p.text).toList(), ['mine']);
      expect(controller.totalQueuedPromptCount, 0);
      held = await disk.onDisk();
      expect(held, isEmpty);
      final raw = (await disk.getAll())['flutter.oc.offlineQueue'];
      expect(raw?.toString() ?? '', isNot(contains(_attachment.url)));
    });

    test(
      'the sending guard holds until the accepted removal is persisted',
      () async {
        final api = _FakeApi();
        final controller = await _controller(api);
        addTearDown(controller.dispose);
        await controller.queuePrompt(_entry('mine', text: 'mine'));
        final disk = await _scriptedDisk();
        final removalWrite = Completer<bool>();
        disk.plan.addAll([true, removalWrite.future]);

        final flush = controller.flushOfflineQueue();
        await _settle(() => disk.queueWritesRequested == 2);

        // Accepted by the server; the removal is still waiting on storage.
        // Until it lands the entry is neither reviewable nor editable.
        expect(api.prompts, hasLength(1));
        expect(controller.queuedPromptSending('mine'), isTrue);
        expect(
          controller.queuedPromptsFor('session-1').single.dispatchedAt,
          isNotNull,
        );

        removalWrite.complete(true);
        await flush;
        expect(controller.queuedPromptSending('mine'), isFalse);
        expect(controller.queuedPromptCount, 0);
        expect(await disk.onDisk(), isEmpty);
      },
    );

    test('the sending guard holds until a post-dispatch failure is '
        'recorded', () async {
      final api = _FakeApi();
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(_entry('mine', text: 'mine'));
      final disk = await _scriptedDisk();
      final errorWrite = Completer<bool>();
      disk.plan.addAll([true, errorWrite.future]);
      api.promptPlan.add(ApiException('socket closed'));

      final flush = controller.flushOfflineQueue();
      await _settle(() => disk.queueWritesRequested == 2);

      expect(controller.queuedPromptSending('mine'), isTrue);
      expect(controller.queuedPromptCount, 1);

      errorWrite.complete(true);
      await flush;
      expect(controller.queuedPromptSending('mine'), isFalse);
      final parked = controller.queuedPromptsFor('session-1').single;
      expect(parked.dispatchedAt, isNotNull);
      expect(parked.error, contains('socket closed'));
      // Settled: the user's discard now goes through.
      await controller.removeQueuedPrompt('mine');
      expect(controller.queuedPromptCount, 0);
      expect(await disk.onDisk(), isEmpty);
    });

    test('an entry on the wire cannot be removed or resent', () async {
      final api = _FakeApi();
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(
        _entry('mine', text: 'mine', attachments: const [_attachment]),
      );
      final disk = await _scriptedDisk();
      final sendGate = Completer<void>();
      api.beforePrompt = () => sendGate.future;

      final flush = controller.flushOfflineQueue();
      // Marker persisted, request held on the wire.
      await _settle(
        () => controller.queuedPromptsFor('session-1').single.dispatched,
      );
      expect(controller.queuedPromptSending('mine'), isTrue);

      await _expectInFlightRefusal(controller.removeQueuedPrompt('mine'));
      await _expectInFlightRefusal(controller.resendQueuedPrompt('mine'));
      final retained = controller.queuedPromptsFor('session-1').single;
      expect(retained.text, 'mine');
      expect(retained.attachments.single.filename, 'notes.txt');
      expect(retained.dispatchedAt, isNotNull);
      final held = (await disk.onDisk()).single;
      expect(held.dispatchedAt, isNotNull);
      expect(held.attachments.single.url, _attachment.url);

      sendGate.complete();
      await flush;
      expect(api.prompts.map((p) => p.text).toList(), ['mine']);
      expect(controller.queuedPromptCount, 0);
      expect(await disk.onDisk(), isEmpty);
    });

    testWidgets('an accepted send stays "Sending…" until its removal is '
        'recorded, even when the screen rebuilds', (tester) async {
      final api = _FakeApi();
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(_entry('mine', text: 'on the wire'));
      await _pumpChat(tester, controller);
      final disk = await _scriptedDisk();
      final removalWrite = Completer<bool>();
      disk.plan.addAll([true, removalWrite.future]);

      final flush = controller.flushOfflineQueue();
      await _settleWidgets(tester, () => disk.queueWritesRequested == 2);
      expect(api.prompts, hasLength(1));
      // Any unrelated change rebuilds the strip in this window.
      controller.notifyListeners();
      await tester.pump();

      expect(find.text('Sending…'), findsOneWidget);
      expect(find.textContaining('Delivery unconfirmed'), findsNothing);
      expect(find.byKey(const ValueKey('queued-action-resend')), findsNothing);
      final actions = tester.widgetList<IconButton>(
        find.descendant(
          of: find.byKey(const ValueKey('queued-send-0')),
          matching: find.byType(IconButton),
        ),
      );
      expect(actions, isNotEmpty);
      expect(actions.where((button) => button.onPressed != null), isEmpty);

      removalWrite.complete(true);
      await flush;
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('queued-send-0')), findsNothing);
      expect(api.prompts, hasLength(1));
    });

    testWidgets('a discard confirmed after a reconnect put the draft on the '
        'wire is refused, and the draft is delivered once', (tester) async {
      final api = _FakeApi();
      final controller = await _controller(
        api,
        status: StreamStatus.disconnected,
      );
      addTearDown(controller.dispose);
      await controller.queuePrompt(
        _entry('mine', text: 'discard me?', attachments: const [_attachment]),
      );
      await _pumpChat(tester, controller);
      final disk = await _scriptedDisk();
      final sendGate = Completer<void>();
      api.beforePrompt = () => sendGate.future;

      // The sheet is open, reading "has not been sent"...
      await _openQueuedAction(tester, 'queued-action-discard');
      expect(find.text('Discard queued draft?'), findsOneWidget);

      // ...when the connection returns and the flush dispatches the draft.
      controller.status = StreamStatus.connected;
      final flush = controller.flushOfflineQueue();
      await _settleWidgets(tester, () => disk.queueWritesRequested == 1);
      await _settleWidgets(
        tester,
        () => controller.queuedPromptsFor('session-1').single.dispatched,
      );

      await tester.tap(find.widgetWithText(FilledButton, 'Discard draft'));
      await tester.pumpAndSettle();

      // The stale confirmation is not acted on: the sheet comes back with
      // the copy that matches the draft's real state.
      expect(
        find.text(
          'Its earlier send was never confirmed; it may already be in the '
          'session.',
        ),
        findsOneWidget,
      );
      expect(
        find.widgetWithText(TextButton, 'Keep for review'),
        findsOneWidget,
      );
      expect(controller.queuedPromptCount, 1);

      // Confirming even that cannot delete a prompt on the wire.
      await tester.tap(find.widgetWithText(FilledButton, 'Discard draft'));
      await tester.pumpAndSettle();
      expect(find.text('Discard queued draft?'), findsNothing);
      final retained = controller.queuedPromptsFor('session-1').single;
      expect(retained.text, 'discard me?');
      expect(retained.attachments.single.filename, 'notes.txt');
      expect(retained.dispatchedAt, isNotNull);
      expect((await disk.onDisk()).single.dispatchedAt, isNotNull);
      expect(find.byKey(const ValueKey('queued-send-0')), findsOneWidget);

      sendGate.complete();
      await flush;
      await tester.pumpAndSettle();
      expect(api.prompts.map((p) => p.text).toList(), ['discard me?']);
      expect(controller.queuedPromptCount, 0);
      expect(await disk.onDisk(), isEmpty);
    });
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
