import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart' show ServerGateway;
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/automation_policy.dart';
import 'package:opencode_mobile/domain/while_away.dart';
import 'package:opencode_mobile/state/offline_queue.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';
import 'support/offline_queue_fixtures.dart';

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
      queuedEntry(
        'q1',
        attachments: const [
          PromptAttachment(
            mime: 'text/plain',
            filename: 'notes.txt',
            url: 'data:text/plain;base64,bm90ZXM=',
          ),
        ],
      ),
      queuedEntry('q2', text: 'second', error: 'declared failure'),
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
      expect(await store.save([queuedEntry('replacement')]), isFalse);
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
      expect(await store.save([queuedEntry('replacement')]), isFalse);
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
    final entry = queuedEntry(
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
    final entry = queuedEntry('stale');
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
    final api = FakeApi();
    final controller = await queueController(api);
    addTearDown(controller.dispose);

    final oversized = queuedEntry(
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

    expect(await controller.queuePrompt(queuedEntry('ok')), isTrue);
    expect(controller.queuedPromptCount, 1);
  });

  test('failed queue writes preserve the existing queued prompts', () async {
    final controller = await queueController(FakeApi());
    addTearDown(controller.dispose);
    await controller.queuePrompt(queuedEntry('saved'));
    final platform = SharedPreferencesStorePlatform.instance;
    SharedPreferencesStorePlatform.instance = _RefusingQueueStore(
      await platform.getAll(),
    );
    addTearDown(() => SharedPreferencesStorePlatform.instance = platform);
    await expectLater(
      controller.queuePrompt(queuedEntry('new')),
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
      final api = FakeApi();
      final controller = await queueController(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(queuedEntry('saved'));
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
    final api = FakeApi();
    final controller = await queueController(api);
    addTearDown(controller.dispose);
    await controller.queuePrompt(queuedEntry('discarded'));
    controller.pendingTransport = Completer<ServerGateway?>();
    final flush = controller.flushOfflineQueue();
    await controller.removeQueuedPrompt('discarded');
    controller.pendingTransport!.complete(api);
    await flush;
    expect(api.prompts, isEmpty);
    expect(controller.queuedPromptCount, 0);
  });

  test(
    'policy gates queue dispatch and logs only confirmed delivery',
    () async {
      final api = FakeApi();
      final controller = await queueController(api);
      addTearDown(controller.dispose);
      final policy = AutomationPolicyController.forProfile(
        controller.store.prefs,
        'profile-1',
      );
      await controller.queuePrompt(queuedEntry('policy-queue'));
      await policy.setBehavior(AutomationBehavior.reconcileQueuedSends, false);
      await controller.flushOfflineQueue();
      expect(api.prompts, isEmpty);
      expect(
        controller.queuedPromptsFor('session-1').single.dispatched,
        isFalse,
      );
      expect(controller.automaticActsHere, isEmpty);
      await policy.setBehavior(AutomationBehavior.reconcileQueuedSends, true);
      final delivered = Completer<void>();
      api.beforePrompt = () => delivered.future;
      final flush = controller.flushOfflineQueue();
      await Future<void>.delayed(Duration.zero);
      expect(controller.automaticActsHere, isEmpty);
      delivered.complete();
      await flush;
      await Future<void>.delayed(Duration.zero);
      expect(api.prompts, hasLength(1));
      expect(
        controller.automaticActsHere.single.kind,
        AutomaticActKind.queuedSend,
      );
    },
  );

  test('policy disabled during queue transport wait sends nothing', () async {
    final api = FakeApi();
    final controller = await queueController(api);
    addTearDown(controller.dispose);
    await controller.queuePrompt(queuedEntry('policy-wait'));
    controller.pendingTransport = Completer<ServerGateway?>();
    final flush = controller.flushOfflineQueue();
    await AutomationPolicyController.forProfile(
      controller.store.prefs,
      'profile-1',
    ).setBehavior(AutomationBehavior.reconcileQueuedSends, false);
    controller.pendingTransport!.complete(api);
    await flush;
    expect(api.prompts, isEmpty);
    expect(controller.queuedPromptsFor('session-1').single.dispatched, isFalse);
    expect(controller.automaticActsHere, isEmpty);
  });

  test(
    'explicit resend works with policy off and is not an automatic act',
    () async {
      final api = FakeApi();
      final controller = await queueController(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(queuedEntry('manual', dispatchedAt: 1));
      await controller.queuePrompt(queuedEntry('automatic'));
      await AutomationPolicyController.forProfile(
        controller.store.prefs,
        'profile-1',
      ).setBehavior(AutomationBehavior.reconcileQueuedSends, false);
      expect(await controller.resendQueuedPrompt('manual'), isTrue);
      await settle(
        () => api.prompts.isNotEmpty && controller.queuedPromptCount == 1,
      );
      expect(api.prompts, hasLength(1));
      expect(controller.queuedPromptsFor('session-1').single.id, 'automatic');
      expect(controller.automaticActsHere, isEmpty);
    },
  );

  test('unconfirmed queue delivery has no automatic act', () async {
    final api = FakeApi()..promptPlan.add(ApiException('unavailable'));
    final controller = await queueController(api);
    addTearDown(controller.dispose);
    await controller.queuePrompt(queuedEntry('policy-failure'));
    await controller.flushOfflineQueue();
    expect(controller.automaticActsHere, isEmpty);
  });

  test('flush sends queued prompts oldest first', () async {
    final api = FakeApi();
    final controller = await queueController(api);
    addTearDown(controller.dispose);
    await controller.queuePrompt(
      queuedEntry(
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
    await controller.queuePrompt(queuedEntry('q2', text: 'second'));
    await controller.queuePrompt(queuedEntry('q3', text: 'third'));

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
    final api = FakeApi();
    final controller = await queueController(api);
    addTearDown(controller.dispose);
    await controller.queuePrompt(queuedEntry('q1', text: 'first'));
    await controller.queuePrompt(queuedEntry('q2', text: 'second'));
    await controller.queuePrompt(queuedEntry('q3', text: 'third'));

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
    await settle(() => controller.queuedPromptCount == 1);
    expect(api.prompts.map((p) => p.text).toList(), ['third', 'second']);
    expect(controller.queuedPromptsFor('session-1').single.id, 'q1');
  });

  test('a flush cycle reports how many drafts it sent', () async {
    final api = FakeApi();
    final controller = await queueController(api);
    addTearDown(controller.dispose);
    await controller.queuePrompt(queuedEntry('q1', text: 'first'));
    await controller.queuePrompt(queuedEntry('q2', text: 'second'));

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
    final api = FakeApi();
    final controller = await queueController(api);
    addTearDown(controller.dispose);
    await controller.queuePrompt(queuedEntry('mine', text: 'active server'));
    await controller.queuePrompt(
      queuedEntry(
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
    final api = FakeApi();
    final controller = await queueController(api);
    addTearDown(controller.dispose);
    await controller.queuePrompt(queuedEntry('q1', text: 'first'));
    await controller.queuePrompt(
      queuedEntry(
        'other',
        profileID: 'profile-2',
        sessionID: 'session-9',
        text: 'other server',
      ),
    );
    await pumpChat(tester, controller);

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
    final api = FakeApi();
    final controller = await queueController(
      api,
      status: StreamStatus.disconnected,
    );
    addTearDown(controller.dispose);
    await controller.queuePrompt(queuedEntry('mine', text: 'active server'));
    await controller.queuePrompt(
      queuedEntry(
        'other',
        profileID: 'profile-2',
        sessionID: 'session-9',
        text: 'other server',
      ),
    );
    await pumpChat(tester, controller);

    expect(
      find.textContaining(
        '1 draft queued to send on reconnect. '
        '1 draft waiting for other servers.',
      ),
      findsOneWidget,
    );
  });

  // slice-queue-move hook (slice-chat-speed-fixes): connected to a server
  // that keeps a queue, the chat says how many drafts wait for another
  // server and offers to move them here, naming this server.
  testWidgets('drafts waiting for another server can be moved here', (
    tester,
  ) async {
    final api = FakeApi();
    final controller = await queueController(api, secondProfile: true);
    addTearDown(controller.dispose);
    await controller.queuePrompt(
      queuedEntry(
        'other',
        profileID: 'profile-2',
        sessionID: 'session-9',
        text: 'other server',
      ),
    );
    await pumpChat(tester, controller);

    expect(
      find.textContaining('1 draft waiting for other servers.'),
      findsOneWidget,
    );
    final move = find.byKey(
      const ValueKey('chat-status-move-queued-profile-2'),
    );
    expect(move, findsOneWidget);
    expect(find.text('Move 1 waiting prompt to Test server'), findsOneWidget);
    await tester.tap(move);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.byKey(const ValueKey('queued-move-sheet')), findsOneWidget);
  });

  testWidgets('sending while disconnected queues a visible draft', (
    tester,
  ) async {
    final api = FakeApi();
    final controller = await queueController(
      api,
      status: StreamStatus.disconnected,
    );
    addTearDown(controller.dispose);
    await pumpChat(tester, controller);

    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'offline draft',
    );
    await tester.pump();
    await tester.tap(find.byTooltip('Send when back online'));
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
      final api = FakeApi();
      final controller = await queueController(api);
      addTearDown(controller.dispose);
      final pending = Completer<void>();
      controller.selectionWait = pending.future;
      await pumpChat(tester, controller);
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
            .widget<EditableText>(
              find.descendant(
                of: find.byKey(const Key('chat-composer-field')),
                matching: find.byType(EditableText),
              ),
            )
            .controller
            .text,
        'Keep this draft',
      );
    },
  );

  testWidgets('a failed dispatched prompt queues its captured selection', (
    tester,
  ) async {
    final api = FakeApi();
    final controller = await queueController(api);
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
    await pumpChat(tester, controller);
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
    final api = FakeApi();
    final controller = await queueController(
      api,
      status: StreamStatus.disconnected,
    );
    addTearDown(controller.dispose);
    await controller.queuePrompt(queuedEntry('q1', text: 'edit me'));
    await pumpChat(tester, controller);

    await openQueuedAction(tester, 'queued-action-edit');

    expect(controller.queuedPromptCount, 0);
    expect(find.byKey(const ValueKey('queued-send-0')), findsNothing);
    expect(
      tester.widget<TextField>(find.byType(TextField)).controller?.text,
      'edit me',
    );

    // P4.3: "Returned to your draft · Undo"; Undo queues it again.
    expect(find.text('Returned to your draft'), findsOneWidget);
    await tester.tap(find.text('Undo'));
    await tester.pumpAndSettle();
    expect(controller.queuedPromptCount, 1);
    expect(
      tester.widget<TextField>(find.byType(TextField)).controller?.text,
      isEmpty,
    );
  });

  testWidgets('discarding a queued draft confirms first', (tester) async {
    final api = FakeApi();
    final controller = await queueController(
      api,
      status: StreamStatus.disconnected,
    );
    addTearDown(controller.dispose);
    await controller.queuePrompt(queuedEntry('q1', text: 'discard me'));
    await pumpChat(tester, controller);

    await openQueuedAction(tester, 'queued-action-discard');

    expect(find.text('Discard queued draft?'), findsOneWidget);
    await tester.tap(find.widgetWithText(FilledButton, 'Discard draft'));
    await tester.pumpAndSettle();

    expect(controller.queuedPromptCount, 0);
    expect(find.byKey(const ValueKey('queued-send-0')), findsNothing);
  });
}
