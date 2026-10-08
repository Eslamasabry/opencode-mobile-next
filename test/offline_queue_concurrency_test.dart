import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/offline_queue.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'support/offline_queue_fixtures.dart';

/// A queue action aimed at an entry that is on the wire, or whose
/// bookkeeping has not yet been persisted, is declined: false, and the
/// entry stays exactly as it is.
Future<void> _expectInFlightRefusal(Future<bool> action) async =>
    expect(await action, isFalse);

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

  group('unconfirmed sends under concurrency', () {
    test('removing another server while the marker write is pending keeps '
        'the marker and never resurrects the removed entries', () async {
      final api = FakeApi();
      final controller = await queueController(api, secondProfile: true);
      addTearDown(controller.dispose);
      await controller.queuePrompt(
        queuedEntry(
          'other',
          profileID: 'profile-2',
          sessionID: 'session-9',
          text: 'other server secret',
          attachments: const [attachment],
        ),
      );
      await controller.queuePrompt(queuedEntry('mine', text: 'mine'));
      final disk = await scriptedDisk();
      final markerWrite = Completer<bool>();
      disk.plan.add(markerWrite.future);
      final sendGate = Completer<void>();
      api.beforePrompt = () => sendGate.future;

      final flush = controller.flushOfflineQueue();
      await settle(() => disk.queueWritesRequested == 1);
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
      expect(raw?.toString() ?? '', isNot(contains(attachment.url)));
    });

    test(
      'the sending guard holds until the accepted removal is persisted',
      () async {
        final api = FakeApi();
        final controller = await queueController(api);
        addTearDown(controller.dispose);
        await controller.queuePrompt(queuedEntry('mine', text: 'mine'));
        final disk = await scriptedDisk();
        final removalWrite = Completer<bool>();
        disk.plan.addAll([true, removalWrite.future]);

        final flush = controller.flushOfflineQueue();
        await settle(() => disk.queueWritesRequested == 2);

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
      final api = FakeApi();
      final controller = await queueController(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(queuedEntry('mine', text: 'mine'));
      final disk = await scriptedDisk();
      final errorWrite = Completer<bool>();
      disk.plan.addAll([true, errorWrite.future]);
      api.promptPlan.add(ApiException('socket closed'));

      final flush = controller.flushOfflineQueue();
      await settle(() => disk.queueWritesRequested == 2);

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
      final api = FakeApi();
      final controller = await queueController(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(
        queuedEntry('mine', text: 'mine', attachments: const [attachment]),
      );
      final disk = await scriptedDisk();
      final sendGate = Completer<void>();
      api.beforePrompt = () => sendGate.future;

      final flush = controller.flushOfflineQueue();
      // Marker persisted, request held on the wire.
      await settle(
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
      expect(held.attachments.single.url, attachment.url);

      sendGate.complete();
      await flush;
      expect(api.prompts.map((p) => p.text).toList(), ['mine']);
      expect(controller.queuedPromptCount, 0);
      expect(await disk.onDisk(), isEmpty);
    });

    testWidgets('an accepted send stays "Sending…" until its removal is '
        'recorded, even when the screen rebuilds', (tester) async {
      final api = FakeApi();
      final controller = await queueController(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(queuedEntry('mine', text: 'on the wire'));
      await pumpChat(tester, controller);
      final disk = await scriptedDisk();
      final removalWrite = Completer<bool>();
      disk.plan.addAll([true, removalWrite.future]);

      final flush = controller.flushOfflineQueue();
      await settleWidgets(tester, () => disk.queueWritesRequested == 2);
      expect(api.prompts, hasLength(1));
      // Any unrelated change rebuilds the strip in this window.
      controller.notifyListeners();
      await tester.pump();

      expect(find.text('Sending…'), findsOneWidget);
      expect(find.textContaining('Delivery unconfirmed'), findsNothing);
      await expectQueuedActionsInert(tester, controller);

      removalWrite.complete(true);
      await flush;
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('queued-send-0')), findsNothing);
      expect(api.prompts, hasLength(1));
    });

    testWidgets('a discard confirmed after a reconnect put the draft on the '
        'wire is refused, and the draft is delivered once', (tester) async {
      final api = FakeApi();
      final controller = await queueController(
        api,
        status: StreamStatus.disconnected,
      );
      addTearDown(controller.dispose);
      await controller.queuePrompt(
        queuedEntry(
          'mine',
          text: 'discard me?',
          attachments: const [attachment],
        ),
      );
      await pumpChat(tester, controller);
      final disk = await scriptedDisk();
      final sendGate = Completer<void>();
      api.beforePrompt = () => sendGate.future;

      // The sheet is open, reading "has not been sent"...
      await openQueuedAction(tester, 'queued-action-discard');
      expect(find.text('Discard queued draft?'), findsOneWidget);

      // ...when the connection returns and the flush dispatches the draft.
      controller.status = StreamStatus.connected;
      final flush = controller.flushOfflineQueue();
      await settleWidgets(tester, () => disk.queueWritesRequested == 1);
      await settleWidgets(
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
          'conversation.',
        ),
        findsOneWidget,
      );
      expect(
        find.widgetWithText(FilledButton, 'Keep for review'),
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
      final controller = await queueController(
        FakeApi(),
        status: StreamStatus.disconnected,
      );
      addTearDown(controller.dispose);
      // The controller trims with the real clock, so these entries are
      // stamped relative to now (not the pinned `now` above) to stay inside
      // the TTL whenever the suite runs.
      final wallNow = DateTime.now().toUtc();
      int recent(int days) =>
          wallNow.subtract(Duration(days: days)).millisecondsSinceEpoch;
      for (var i = 0; i < OfflineQueueStore.maxEntries; i++) {
        expect(
          await controller.queuePrompt(
            sized('q$i', bytes: 10, createdAt: recent(1) + i),
          ),
          isTrue,
        );
      }
      expect(controller.takeQueueEvictionNotice(), isNull);

      expect(
        await controller.queuePrompt(
          sized('newest', bytes: 10, createdAt: recent(0)),
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
      final controller = await queueController(
        FakeApi(),
        status: StreamStatus.disconnected,
      );
      addTearDown(controller.dispose);
      await controller.queuePrompt(queuedEntry('q1'));
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
