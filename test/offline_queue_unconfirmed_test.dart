import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/offline_queue.dart';
import 'package:opencode_mobile/ui/kit/kit.dart' show KitUndo;
import 'package:shared_preferences/shared_preferences.dart';
import 'support/offline_queue_fixtures.dart';

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

  group('unconfirmed sends', () {
    test('the dispatch marker survives snapshots, errors, and disk', () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final store = OfflineQueueStore(prefs: prefs);
      final marked = queuedEntry(
        'marked',
        attachments: const [attachment],
        dispatchedAt: 1700000000000,
      );

      expect(marked.dispatched, isTrue);
      expect(marked.withError('later failure').dispatchedAt, 1700000000000);
      expect(marked.withError('later failure').error, 'later failure');
      expect(marked.withDispatchedAt(null).dispatchedAt, isNull);
      expect(marked.withDispatchedAt(null).attachments, hasLength(1));
      expect(queuedEntry('plain').dispatched, isFalse);

      expect(await store.save([marked, queuedEntry('plain')]), isTrue);
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
      final api = FakeApi();
      final controller = await queueController(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(queuedEntry('q1', text: 'first'));
      await controller.queuePrompt(queuedEntry('q2', text: 'second'));
      final disk = await scriptedDisk();

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
        final api = FakeApi();
        final controller = await queueController(api);
        addTearDown(controller.dispose);
        await controller.queuePrompt(queuedEntry('q1', text: 'first'));
        await controller.queuePrompt(queuedEntry('q2', text: 'second'));
        final revision = controller.offlineFlushRevision;
        final disk = await scriptedDisk()
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
      final api = FakeApi();
      var controller = await queueController(api);
      addTearDown(() => controller.dispose());
      await controller.queuePrompt(
        queuedEntry('q1', text: 'first', attachments: const [attachment]),
      );
      await controller.queuePrompt(queuedEntry('q2', text: 'second'));
      final disk = await scriptedDisk();
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
      controller = await restartQueueController(api);
      await controller.flushOfflineQueue();

      expect(api.prompts.map((p) => p.text).toList(), ['first', 'second']);
      queued = controller.queuedPromptsFor('session-1');
      expect(queued.map((e) => e.id), ['q1']);
      expect(queued.single.dispatchedAt, isNotNull);
      expect(controller.queuedPromptAcceptedUnrecorded('q1'), isFalse);
      held = await disk.onDisk();
      expect(held.map((e) => e.id), ['q1']);
      expect(held.single.attachments.single.url, attachment.url);
    });

    testWidgets('an accepted send the device could not record explains '
        'itself and offers no resend', (tester) async {
      final api = FakeApi();
      final controller = await queueController(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(queuedEntry('q1', text: 'landed'));
      await pumpChat(tester, controller);
      final disk = await scriptedDisk();
      disk.plan.addAll([true, false]);

      await controller.flushOfflineQueue();
      await tester.pumpAndSettle();

      expect(api.prompts, hasLength(1));
      // It says the server has it, and offers no resend (a certain
      // duplicate): not on the bubble, not in its menu.
      expect(find.text('Reached the server'), findsOneWidget);
      expect(find.byKey(const ValueKey('queued-bubble-resend')), findsNothing);
      await openQueuedMenu(tester);
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
      final api = FakeApi();
      final controller = await queueController(
        api,
        queue: jsonEncode([
          {
            'id': 'accepted',
            'profileID': 'profile-1',
            'sessionID': 'session-1',
            'text': 'may have landed',
            'attachments': [
              {
                'mime': attachment.mime,
                'filename': attachment.filename,
                'url': attachment.url,
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
        final api = FakeApi();
        final controller = await queueController(api);
        addTearDown(controller.dispose);
        await controller.queuePrompt(
          queuedEntry('q1', text: 'first', attachments: const [attachment]),
        );
        final disk = await scriptedDisk();

        api.promptPlan.add(failure);
        await controller.flushOfflineQueue();
        expect(api.prompts, isEmpty, reason: '$failure');
        var queued = controller.queuedPromptsFor('session-1').single;
        expect(queued.dispatchedAt, isNotNull, reason: '$failure');
        expect(queued.attachments.single.filename, 'notes.txt');
        final held = (await disk.onDisk()).single;
        expect(held.dispatchedAt, isNotNull, reason: '$failure');
        expect(held.attachments.single.url, attachment.url);

        // Server healthy again: still no automatic resend, in this process
        // or the next.
        await controller.flushOfflineQueue();
        expect(api.prompts, isEmpty, reason: '$failure');
        final restarted = await restartQueueController(api);
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
        final api = FakeApi();
        final controller = await queueController(api);
        addTearDown(controller.dispose);
        await controller.queuePrompt(queuedEntry('q1', text: 'first'));
        await controller.queuePrompt(queuedEntry('q2', text: 'second'));

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
      final api = FakeApi();
      final controller = await queueController(api, flavor: 'v2');
      addTearDown(controller.dispose);
      expect(controller.supportsStagedRevert, isTrue);
      await controller.queuePrompt(queuedEntry('q1', text: 'first'));
      await controller.queuePrompt(queuedEntry('q2', text: 'second'));
      final disk = await scriptedDisk();

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
      final api = FakeApi();
      final controller = await queueController(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(
        queuedEntry('q1', text: 'first', dispatchedAt: 1700000000000),
      );
      final disk = await scriptedDisk()
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
      final api = FakeApi();
      final controller = await queueController(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(
        queuedEntry(
          'q1',
          text: 'did this land?',
          attachments: const [attachment],
          dispatchedAt: 1700000000000,
        ),
      );
      await pumpChat(tester, controller);
      await controller.flushOfflineQueue();
      await tester.pump();

      expect(api.prompts, isEmpty);
      expect(find.byKey(const ValueKey('queued-send-0')), findsOneWidget);
      expect(find.text('Not confirmed yet'), findsOneWidget);
      expect(find.text('Waiting to send'), findsNothing);
      await openQueuedMenu(tester);
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
      final api = FakeApi();
      final controller = await queueController(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(
        queuedEntry(
          'q1',
          text: 'did this land?',
          error: 'socket closed',
          dispatchedAt: 1700000000000,
        ),
      );
      await pumpChat(tester, controller);

      expect(
        find.textContaining(RegExp(r'^Not confirmed yet: .*socket closed')),
        findsOneWidget,
      );
      expect(find.textContaining('Failed:'), findsNothing);
    });

    testWidgets('resending asks first and only sends on confirmation', (
      tester,
    ) async {
      final api = FakeApi();
      final controller = await queueController(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(
        queuedEntry(
          'q1',
          text: 'send me twice maybe',
          attachments: const [attachment],
          dispatchedAt: 1700000000000,
        ),
      );
      await pumpChat(tester, controller);

      await openQueuedAction(tester, 'queued-action-resend');
      expect(find.text('Send this draft again?'), findsOneWidget);
      await tester.tap(find.widgetWithText(FilledButton, 'Keep for review'));
      await tester.pumpAndSettle();
      expect(api.prompts, isEmpty);
      expect(
        controller.queuedPromptsFor('session-1').single.dispatchedAt,
        1700000000000,
      );

      await openQueuedAction(tester, 'queued-action-resend');
      await tester.tap(find.widgetWithText(FilledButton, 'Send again'));
      await tester.pumpAndSettle();
      await settleWidgets(tester, () => controller.queuedPromptCount == 0);

      expect(api.prompts.map((p) => p.text).toList(), ['send me twice maybe']);
      expect(find.byKey(const ValueKey('queued-send-0')), findsNothing);
      final prefs = await SharedPreferences.getInstance();
      expect(OfflineQueueStore(prefs: prefs).load(), isEmpty);
    });

    testWidgets('a refused resend keeps the entry in review', (tester) async {
      final api = FakeApi();
      final controller = await queueController(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(
        queuedEntry('q1', text: 'stuck', dispatchedAt: 1700000000000),
      );
      await pumpChat(tester, controller);
      (await scriptedDisk()).defaultOutcome = false;

      await openQueuedAction(tester, 'queued-action-resend');
      await tester.tap(find.widgetWithText(FilledButton, 'Send again'));
      await tester.pumpAndSettle();

      expect(api.prompts, isEmpty);
      expect(
        find.textContaining('Could not save the queued draft'),
        findsOneWidget,
      );
      expect(find.textContaining('Not confirmed yet'), findsOneWidget);
      expect(
        controller.queuedPromptsFor('session-1').single.dispatchedAt,
        1700000000000,
      );
    });

    testWidgets('editing an unconfirmed send restores text and attachments '
        'to the composer without sending', (tester) async {
      final api = FakeApi();
      final controller = await queueController(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(
        queuedEntry(
          'q1',
          text: 'edit me',
          attachments: const [attachment],
          dispatchedAt: 1700000000000,
        ),
      );
      await pumpChat(tester, controller);

      await openQueuedAction(tester, 'queued-action-edit');

      expect(controller.queuedPromptCount, 0);
      expect(find.byKey(const ValueKey('queued-send-0')), findsNothing);
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
      expect(find.text('Returned to your draft'), findsOneWidget);
      await tester.pump(KitUndo.window);
      await tester.pumpAndSettle();
    });

    testWidgets('editing a never-sent draft carries no duplicate warning', (
      tester,
    ) async {
      final api = FakeApi();
      final controller = await queueController(
        api,
        status: StreamStatus.disconnected,
      );
      addTearDown(controller.dispose);
      await controller.queuePrompt(queuedEntry('q1', text: 'plain draft'));
      await pumpChat(tester, controller);

      await openQueuedAction(tester, 'queued-action-edit');

      expect(controller.queuedPromptCount, 0);
      expect(
        find.byKey(const Key('queued-edit-unconfirmed-note')),
        findsNothing,
      );
      expect(find.textContaining('may already have reached'), findsNothing);
      // Let the Undo window close.
      await tester.pump(KitUndo.window);
    });

    testWidgets('editing an unconfirmed send whose removal is refused '
        'leaves it queued and the composer untouched', (tester) async {
      final api = FakeApi();
      final controller = await queueController(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(
        queuedEntry(
          'q1',
          text: 'edit me',
          attachments: const [attachment],
          dispatchedAt: 1700000000000,
        ),
      );
      await pumpChat(tester, controller);
      final disk = await scriptedDisk()
        ..defaultOutcome = false;

      await openQueuedAction(tester, 'queued-action-edit');

      expect(
        find.textContaining('Could not remove this draft'),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('queued-send-0')), findsOneWidget);
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
      final api = FakeApi();
      final controller = await queueController(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(
        queuedEntry('q1', text: 'discard me', dispatchedAt: 1700000000000),
      );
      await pumpChat(tester, controller);
      final disk = await scriptedDisk()
        ..defaultOutcome = false;

      await openQueuedAction(tester, 'queued-action-discard');
      await tester.tap(find.widgetWithText(FilledButton, 'Discard draft'));
      await tester.pumpAndSettle();

      expect(
        find.textContaining('Could not remove this draft'),
        findsOneWidget,
      );
      expect(controller.queuedPromptCount, 1);
      expect(find.byKey(const ValueKey('queued-send-0')), findsOneWidget);
      expect((await disk.onDisk()).single.dispatchedAt, 1700000000000);

      // The failure is a kit alert; dismiss it before returning to the item.
      await tester.tap(find.text('Close'));
      await tester.pumpAndSettle();

      // Storage back: the discard goes through and nothing is ever sent.
      disk.defaultOutcome = true;
      await openQueuedAction(tester, 'queued-action-discard');
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
      final api = FakeApi();
      final controller = await queueController(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(queuedEntry('q1', text: 'on the wire'));
      final held = Completer<void>();
      api.beforePrompt = () => held.future;
      await pumpChat(tester, controller);

      final flush = controller.flushOfflineQueue();
      await settleWidgets(tester, () => controller.queuedPromptSending('q1'));
      await tester.pump();

      expect(find.text('Sending…'), findsOneWidget);
      // No action can pull the draft out from under a request in flight.
      await expectQueuedActionsInert(tester, controller);

      held.complete();
      await flush;
      await tester.pumpAndSettle();
      expect(controller.queuedPromptSending('q1'), isFalse);
      expect(api.prompts.map((p) => p.text).toList(), ['on the wire']);
      expect(find.byKey(const ValueKey('queued-send-0')), findsNothing);
    });
  });
}
