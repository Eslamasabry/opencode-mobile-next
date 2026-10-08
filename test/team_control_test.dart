// TEAM-202: the control gateway through the host front, idempotency keys
// persisted before send, receipts and their resolution by the host's
// events. The controller runs over a scripted gateway (no network); the
// probe runs against an in-process HTTP server; the front receipt mapping
// is exercised directly.

import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/orchestration/adapters/gascity/gascity_control.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'support/team_control_fixtures.dart';

void main() {
  // No widgets binding on purpose: the probe tests talk to an in-process
  // HTTP server, which the test binding's HttpClient stub would refuse.
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
    store = OrchestrationStore(prefs, secure: MemorySecureStorage());
    clock = DateTime.utc(2026, 9, 11, 12);
    nextKey = 0;
  });

  group('receipt lifecycle', () {
    test('the key is persisted before the gateway is called', () async {
      final gateway = ScriptedGateway();
      String? seenInStore;
      gateway.answer = (call) async {
        seenInStore = prefs.getString(storedKey(call.requestId));
        throw StateError('transport down');
      };
      final controller = await boot(gateway);
      final record = await controller.answerGate(
        'req-1',
        const GateResponse.choice('replace'),
      );
      expect(record.key, 'key-1');
      expect(gateway.calls.single.requestId, 'key-1');
      // The store held the record (as sent) when the gateway ran.
      final atSend = jsonDecode(seenInStore!) as Map<String, Object?>;
      expect(atSend['status'], 'sent');
      expect(atSend['key'], 'key-1');
      // A thrown gateway leaves it unconfirmed with the error, never resent.
      expect(record.status, MutationStatus.unconfirmed);
      expect(record.receipt?.message, contains('transport down'));
      expect(stored('key-1')?['status'], 'unconfirmed');
      expect(record.canRetry, isTrue);
    });

    test('accepted, then confirmed by the matching request.result', () async {
      final gateway = ScriptedGateway();
      gateway.answer = (call) async => MutationReceipt(
        id: call.requestId,
        status: MutationReceiptStatus.accepted,
        correlationId: 'corr-7',
        hostRequestId: 'host-1',
        upstreamStatus: 202,
      );
      final controller = await boot(gateway);
      final notified = <MutationStatus?>[];
      controller.addListener(
        () => notified.add(controller.mutation('key-1')?.status),
      );
      final record = await controller.messageAgent('gastown.mayor', 'hello');
      expect(record.status, MutationStatus.sent);
      expect(record.correlationId, 'corr-7');
      expect(controller.latestMutation(targetId: 'gastown.mayor'), record);

      // Another id does not match.
      gateway.push(const RequestResult(requestId: 'corr-other', ok: true));
      await settle();
      expect(controller.mutation('key-1')?.status, MutationStatus.sent);

      gateway.push(
        const RequestResult(
          requestId: 'corr-7',
          ok: true,
          operation: 'session.message',
          seq: 2000,
        ),
      );
      await settle();
      final done = controller.mutation('key-1')!;
      expect(done.status, MutationStatus.confirmed);
      expect(done.isSettled, isTrue);
      expect(stored('key-1')?['status'], 'confirmed');
      expect(notified, contains(MutationStatus.confirmed));
      // The timer is gone: no unconfirmed flip afterwards.
      await Future<void>.delayed(const Duration(milliseconds: 250));
      expect(controller.mutation('key-1')?.status, MutationStatus.confirmed);
    });

    test('request.failed rejects with the host error', () async {
      final gateway = ScriptedGateway();
      gateway.answer = (call) async => MutationReceipt(
        id: call.requestId,
        status: MutationReceiptStatus.accepted,
        correlationId: 'corr-8',
        upstreamStatus: 202,
      );
      final controller = await boot(gateway);
      await controller.messageAgent('gastown.mayor', 'hello');
      gateway.push(
        const RequestResult(
          requestId: 'corr-8',
          ok: false,
          operation: 'session.message',
          errorCode: 'session-refused',
          errorMessage: 'the session is gone',
        ),
      );
      await settle();
      final record = controller.mutation('key-1')!;
      expect(record.status, MutationStatus.rejected);
      expect(record.receipt?.message, 'the session is gone');
      expect(record.canRetry, isTrue);
    });

    test('a rejected receipt settles the record', () async {
      final gateway = ScriptedGateway();
      gateway.answer = (call) async =>
          MutationReceipt.rejected(call.requestId, 'session not found');
      final controller = await boot(gateway);
      final record = await controller.cancelRun('run-1');
      expect(record.status, MutationStatus.rejected);
      expect(record.receipt?.message, 'session not found');
      expect(stored('key-1')?['status'], 'rejected');
      expect(record.canRetry, isTrue);
    });

    test('502 upstream-unavailable is unconfirmed and retryable', () async {
      final gateway = ScriptedGateway();
      gateway.answer = (call) async => receiptFromFront(
        call.requestId,
        frontProblem(502, 'upstream-unavailable'),
      );
      final controller = await boot(gateway);
      final record = await controller.controlAgent(
        'gastown.mayor',
        AgentControlAction.nudge,
      );
      expect(record.status, MutationStatus.unconfirmed);
      expect(record.receipt?.status, MutationReceiptStatus.pending);
      expect(record.receipt?.retryable, isTrue);
      expect(record.receipt?.message, 'detail for upstream-unavailable');
      expect(record.canRetry, isTrue);
      // Round-trips through the store with the flag.
      final persisted = MutationRecord.fromJson(stored('key-1'))!;
      expect(persisted.receipt?.retryable, isTrue);
    });

    test('409 idempotency-mismatch is rejected', () async {
      final gateway = ScriptedGateway();
      gateway.answer = (call) async => receiptFromFront(
        call.requestId,
        frontProblem(409, 'idempotency-mismatch'),
      );
      final controller = await boot(gateway);
      final record = await controller.assignWork(
        'oc-loy',
        agentId: 'gastown.mayor',
      );
      expect(record.status, MutationStatus.rejected);
      expect(record.receipt?.retryable, isFalse);
      expect(record.receipt?.message, 'detail for idempotency-mismatch');
    });

    test('no result inside the window: unconfirmed, then a late result '
        'still confirms (stream drop and resume)', () async {
      final gateway = ScriptedGateway();
      gateway.answer = (call) async => MutationReceipt(
        id: call.requestId,
        status: MutationReceiptStatus.accepted,
        correlationId: 'corr-9',
        upstreamStatus: 202,
      );
      final controller = await boot(gateway);
      await controller.messageAgent('gastown.mayor', 'hello');
      expect(controller.mutation('key-1')?.status, MutationStatus.sent);
      await Future<void>.delayed(const Duration(milliseconds: 300));
      final timedOut = controller.mutation('key-1')!;
      expect(timedOut.status, MutationStatus.unconfirmed);
      expect(stored('key-1')?['status'], 'unconfirmed');
      expect(timedOut.canRetry, isTrue);
      // Nothing was sent again.
      expect(gateway.callCount, 1);

      // The stream comes back and replays the result.
      gateway.push(const RequestResult(requestId: 'corr-9', ok: true));
      await settle();
      expect(controller.mutation('key-1')?.status, MutationStatus.confirmed);
      expect(gateway.callCount, 1);
    });

    test('a result that arrives before the receipt still confirms', () async {
      final gateway = ScriptedGateway();
      final release = Completer<void>();
      gateway.answer = (call) async {
        await release.future;
        return MutationReceipt(
          id: call.requestId,
          status: MutationReceiptStatus.accepted,
          correlationId: 'corr-early',
          upstreamStatus: 202,
        );
      };
      final controller = await boot(gateway);
      final pending = controller.messageAgent('gastown.mayor', 'hello');
      await settle();
      gateway.push(const RequestResult(requestId: 'corr-early', ok: true));
      await settle();
      release.complete();
      final record = await pending;
      expect(record.status, MutationStatus.confirmed);
    });

    test('a synchronous 200 answer is confirmed at once', () async {
      final gateway = ScriptedGateway();
      gateway.answer = (call) async => MutationReceipt(
        id: call.requestId,
        status: MutationReceiptStatus.accepted,
        upstreamStatus: 200,
      );
      final controller = await boot(gateway);
      final record = await controller.controlAgent(
        'gastown.mayor',
        AgentControlAction.stop,
      );
      expect(record.status, MutationStatus.confirmed);
    });

    test('a gate answer is confirmed by the gate resolving', () async {
      final gateway = ScriptedGateway();
      final controller = await boot(gateway);
      final record = await controller.answerGate(
        'req-1',
        const GateResponse.confirmation(confirmed: true),
      );
      expect(record.status, MutationStatus.sent);
      expect(controller.mutationFor('req-1'), record);
      expect(controller.mutationFor('req-2'), isNull);
      gateway.push(const GateChanged(gateId: 'req-2', resolved: true));
      await settle();
      expect(controller.mutationFor('req-1')?.status, MutationStatus.sent);
      gateway.push(const GateChanged(gateId: 'req-1', resolved: true));
      await settle();
      expect(controller.mutationFor('req-1')?.status, MutationStatus.confirmed);
    });

    test('the same gate is not answered twice while sent', () async {
      final gateway = ScriptedGateway();
      final controller = await boot(gateway);
      final first = await controller.answerGate(
        'req-1',
        const GateResponse.choice('keep'),
      );
      final second = await controller.answerGate(
        'req-1',
        const GateResponse.choice('replace'),
      );
      expect(second.key, first.key);
      expect(gateway.callCount, 1);
    });

    test('cancel, agent and assign confirm on their effect events', () async {
      final gateway = ScriptedGateway();
      final controller = await boot(gateway);
      await controller.cancelRun('run-1');
      await controller.controlAgent('agent-1', AgentControlAction.restart);
      await controller.assignWork('bead-1', agentId: 'agent-2');
      gateway.push(const RunChanged(runId: 'run-1', state: RunState.cancelled));
      gateway.push(
        const SessionChanged(
          sessionId: 'sess-1',
          agentId: 'agent-1',
          change: SessionChange.woke,
        ),
      );
      gateway.push(
        const BeadChanged(beadId: 'bead-1', change: BeadChange.updated),
      );
      await settle();
      expect(
        controller.mutations.map((m) => m.status),
        everyElement(MutationStatus.confirmed),
      );
    });

    test('giveTask (TEAM-306): creates, then slings the created id; a 201 '
        'bead confirms at once and the work is fetched again', () async {
      final gateway = ScriptedGateway();
      gateway.answer = (call) async => call.verb == 'createWork'
          ? MutationReceipt(
              id: call.requestId,
              status: MutationReceiptStatus.accepted,
              raw: {'id': 'gc-77', 'status': 'open', 'title': call.target},
              upstreamStatus: 201,
            )
          : MutationReceipt(
              id: call.requestId,
              status: MutationReceiptStatus.accepted,
              correlationId: 'corr-${call.requestId}',
              upstreamStatus: 202,
            );
      final controller = await boot(gateway);
      final reads = gateway.workReads;
      final result = await controller.giveTask(
        title: 'Add a docstring',
        description: 'One line.',
        projectId: 'ocproof',
        agentId: 'ocproof/gastown.polecat',
      );
      expect(gateway.calls.map((c) => c.verb), ['createWork', 'assign']);
      expect(gateway.calls[0].target, 'Add a docstring');
      expect(gateway.calls[0].arg, 'ocproof');
      expect(gateway.calls[1].target, 'gc-77');
      expect(gateway.calls[1].arg, 'ocproof/gastown.polecat');
      expect(result.created.status, MutationStatus.confirmed);
      expect(result.created.receipt?.createdId, 'gc-77');
      expect(result.assigned?.status, MutationStatus.sent);
      expect(stored('key-1')?['request'], {
        'kind': 'createWork',
        'targetId': 'Add a docstring',
        'text': 'One line.',
        'projectId': 'ocproof',
      });
      await settle();
      expect(gateway.workReads, greaterThan(reads));
      gateway.push(
        const BeadChanged(beadId: 'gc-77', change: BeadChange.updated),
      );
      await settle();
      expect(result.assigned!.key, 'key-2');
      expect(controller.mutation('key-2')?.status, MutationStatus.confirmed);
    });

    test('giveTask: a refused create slings nothing; a create without an '
        'id waits for its bead.created event', () async {
      final gateway = ScriptedGateway();
      gateway.answer = (call) async =>
          MutationReceipt.rejected(call.requestId, 'rig nope unknown');
      final controller = await boot(gateway);
      final refused = await controller.giveTask(
        title: 'T',
        projectId: 'nope',
        agentId: 'nope/gastown.polecat',
      );
      expect(refused.created.status, MutationStatus.rejected);
      expect(refused.assigned, isNull);
      expect(gateway.calls.map((c) => c.verb), ['createWork']);

      gateway.answer = (call) async => MutationReceipt(
        id: call.requestId,
        status: MutationReceiptStatus.accepted,
        correlationId: 'corr-async',
        upstreamStatus: 202,
      );
      final pending = await controller.giveTask(
        title: 'U',
        projectId: 'ocproof',
        agentId: 'ocproof/gastown.polecat',
      );
      expect(pending.created.status, MutationStatus.sent);
      expect(pending.assigned, isNull);
      expect(gateway.calls.map((c) => c.verb), ['createWork', 'createWork']);
      gateway.push(const RequestResult(requestId: 'corr-async', ok: true));
      await settle();
      expect(
        controller.mutation(pending.created.key)?.status,
        MutationStatus.confirmed,
      );
    });

    test('createWork is refused by the controller when the host lacks '
        'controlCreateWork', () async {
      final gateway = ScriptedGateway(
        capabilities: OrchestrationCapabilities.gascityFront,
      );
      final controller = await boot(gateway);
      final record = await controller.createWork(title: 'T');
      expect(record.status, MutationStatus.rejected);
      expect(record.receipt?.message, 'the host does not allow this control');
      expect(gateway.calls, isEmpty);
    });

    test('a stop and a batch close confirm on bead.closed of the session '
        'and convoy beads (Gas City 1.4.1, proven live in TEAM-207)', () async {
      final gateway = ScriptedGateway()
        ..agentList.add(
          const OrchestrationAgent(
            id: 'ocproof/gastown.furiosa',
            name: 'ocproof/gastown.furiosa',
            state: AgentState.working,
            sessionId: 'bl-ex0',
            pool: 'ocproof/gastown.polecat',
          ),
        );
      final controller = await boot(gateway);
      final stop = await controller.controlAgent(
        'ocproof/gastown.furiosa',
        AgentControlAction.stop,
      );
      final close = await controller.cancelRun('oc-rmn');
      final nudge = await controller.controlAgent(
        'ocproof/gastown.furiosa',
        AgentControlAction.nudge,
      );
      // Unrelated closures confirm nothing.
      gateway.push(
        const BeadChanged(beadId: 'oc-v1t', change: BeadChange.closed),
      );
      gateway.push(
        const BeadChanged(beadId: 'bl-ex0', change: BeadChange.updated),
      );
      await settle();
      expect(controller.mutation(stop.key)?.status, MutationStatus.sent);
      expect(controller.mutation(close.key)?.status, MutationStatus.sent);
      final agentsReads = gateway.agentsReads;
      gateway.push(
        const BeadChanged(beadId: 'bl-ex0', change: BeadChange.closed),
      );
      gateway.push(
        const BeadChanged(beadId: 'oc-rmn', change: BeadChange.closed),
      );
      await settle();
      await settle();
      // The session bead changing refreshes the agents list as well.
      expect(gateway.agentsReads, greaterThan(agentsReads));
      expect(controller.mutation(stop.key)?.status, MutationStatus.confirmed);
      expect(controller.mutation(close.key)?.status, MutationStatus.confirmed);
      // A nudge is settled by its request.result only.
      expect(
        controller.mutation(nudge.key)?.status,
        isNot(MutationStatus.confirmed),
      );
    });

    test('controls the host does not allow are rejected locally', () async {
      final gateway = ScriptedGateway(
        capabilities: OrchestrationCapabilities.gascityRead,
      );
      final controller = await boot(gateway);
      final record = await controller.messageAgent('gastown.mayor', 'hi');
      expect(record.status, MutationStatus.rejected);
      expect(gateway.callCount, 0);
    });
  });

  group('restart and retry', () {
    test('a sent record survives a restart as unconfirmed and is never '
        're-sent', () async {
      final gateway = ScriptedGateway();
      gateway.answer = (call) async => MutationReceipt(
        id: call.requestId,
        status: MutationReceiptStatus.accepted,
        correlationId: 'corr-1',
        upstreamStatus: 202,
      );
      final controller = await boot(
        gateway,
        timeout: const Duration(seconds: 60),
      );
      await controller.answerGate('req-1', const GateResponse.choice('keep'));
      expect(stored('key-1')?['status'], 'sent');
      // The app goes away with the answer in flight.
      controller.dispose();
      await settle();

      final again = ScriptedGateway();
      final reloaded = await boot(again);
      expect(again.callCount, 0);
      final record = reloaded.mutationFor('req-1')!;
      expect(record.key, 'key-1');
      expect(record.status, MutationStatus.unconfirmed);
      expect(record.correlationId, 'corr-1');
      expect(stored('key-1')?['status'], 'unconfirmed');
      // Still never sent: a refresh or a stream event changes nothing.
      await reloaded.refresh();
      expect(again.callCount, 0);
      // The late result confirms it after the restart too.
      again.push(const RequestResult(requestId: 'corr-1', ok: true));
      await settle();
      expect(reloaded.mutationFor('req-1')?.status, MutationStatus.confirmed);
    });

    test('retryMutation sends again under a new key', () async {
      final gateway = ScriptedGateway();
      gateway.answer = (call) async =>
          MutationReceipt.rejected(call.requestId, 'busy');
      final controller = await boot(gateway);
      final first = await controller.messageAgent('gastown.mayor', 'hello');
      expect(first.status, MutationStatus.rejected);

      gateway.answer = null;
      final retry = (await controller.retryMutation(first.key))!;
      expect(retry.key, isNot(first.key));
      expect(retry.retryOf, first.key);
      expect(retry.request.text, 'hello');
      expect(retry.status, MutationStatus.sent);
      expect(gateway.calls.map((c) => c.requestId), ['key-1', 'key-2']);
      // The old record is superseded and no longer the latest one.
      expect(controller.mutation(first.key)?.retriedBy, retry.key);
      expect(controller.mutation(first.key)?.canRetry, isFalse);
      expect(controller.latestMutation(targetId: 'gastown.mayor'), retry);
      // A settled or superseded record cannot be retried.
      expect(await controller.retryMutation(first.key), isNull);
      expect(await controller.retryMutation('missing'), isNull);
    });

    test('every mutation key lives under the profile prefix', () async {
      final gateway = ScriptedGateway();
      final controller = await boot(gateway);
      await controller.cancelRun('run-1');
      expect(storedKey('key-1'), 'oc.orchestration.$profileId.mutations.key-1');
      expect(store.keysFor(profileId), contains(storedKey('key-1')));
      expect(await controller.remove(), isEmpty);
      expect(prefs.getString(storedKey('key-1')), isNull);
    });
  });
}
