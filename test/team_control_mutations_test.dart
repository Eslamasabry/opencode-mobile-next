// TEAM-202 (second half): the MutationStore, the front receipt mapping
// (receiptFromFront), the merge mutations (TEAM-205) and the probe and
// discovery runs against an in-process HTTP server. The receipt lifecycle
// and the restart and retry runs are in team_control_test.dart.

import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/orchestration/adapters/gascity/gascity_control.dart';
import 'package:opencode_mobile/orchestration/adapters/gascity/gascity_gateway.dart';
import 'package:opencode_mobile/orchestration/adapters/gascity/gascity_probe.dart';
import 'package:opencode_mobile/orchestration/client/http.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/ui/widgets/team_host_form.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'support/team_control_fixtures.dart';

/// Serves a scripted supervisor (and optionally its front) in-process for
/// the probe tests.
class _Host {
  _Host._(this.server);

  final HttpServer server;
  bool front = true;
  bool allowed = true;
  final requests = <String>[];

  String get url => 'http://127.0.0.1:${server.port}';

  static Future<_Host> start() async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    final host = _Host._(server);
    server.listen(host._handle);
    return host;
  }

  Future<void> close() => server.close(force: true);

  void _handle(HttpRequest request) {
    final path = request.uri.path;
    requests.add(path);
    Object? body;
    var status = 200;
    if (path == frontWellKnownPath) {
      if (!front) {
        status = 404;
        body = {
          'type': 'urn:gascity:error:not-found',
          'title': 'Not Found',
          'status': 404,
        };
      } else {
        body = {
          'provider': 'gascity',
          'supervisorUrl': url,
          'city': 'bright-lights',
          'front': true,
          'version': '1.4.1',
          'capabilities': {'read': true, 'control': true, 'merge': false},
          'identity': {'login': 'you@example.com', 'allowed': allowed},
        };
      }
    } else if (path == '/health' || path == '/v0/city/bright-lights/health') {
      body = {'status': 'ok', 'version': '1.4.1', 'city': 'bright-lights'};
    } else if (path == '/v0/cities') {
      body = {
        'items': [
          {'name': 'bright-lights', 'running': true, 'status': 'running'},
        ],
      };
    } else if (path == '/v0/city/bright-lights/status') {
      body = {'name': 'bright-lights', 'version': '1.4.1'};
    } else {
      status = 404;
      body = {
        'type': 'urn:gascity:error:not-found',
        'title': 'Not Found',
        'status': 404,
      };
    }
    request.response
      ..statusCode = status
      ..headers.contentType = ContentType.json
      ..headers.set('X-GC-Request-Id', 'r-${requests.length}')
      ..write(jsonEncode(body));
    request.response.close();
  }
}

/// A merge gateway whose readiness read throws.
class _FailingMergeGateway extends MergeGateway {
  @override
  Future<MergeReadiness?> mergeReadiness(String runId) async {
    throw StateError('front down');
  }
}

/// An in-process front answering the three merge routes for the
/// GasCityControl route test.
class _MergeHost {
  _MergeHost._(this.server);

  final HttpServer server;
  final requests = <String>[];
  final keys = <String?>[];

  String get url => 'http://127.0.0.1:${server.port}';

  static Future<_MergeHost> start() async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    final host = _MergeHost._(server);
    server.listen(host._handle);
    return host;
  }

  Future<void> close() => server.close(force: true);

  Future<void> _handle(HttpRequest request) async {
    final path = request.uri.path;
    requests.add('${request.method} $path');
    keys.add(request.headers.value('Idempotency-Key'));
    await request.drain<void>();
    Object? body;
    var status = 200;
    var contentType = ContentType.json;
    const prefix = '/v0/city/bright-lights/front';
    Map<String, Object?> receipt(
      String id,
      int upstream,
      Map<String, Object?> inner,
    ) => {
      'request_id': id,
      'status': upstream < 300 ? 'accepted' : 'rejected',
      'upstream_status': upstream,
      'body': inner,
      'idempotency_key': request.headers.value('Idempotency-Key'),
    };
    if (path == '$prefix/merge-readiness/oc-xru') {
      body = {
        'ready': true,
        'runId': 'oc-xru',
        'rig': 'ocproof',
        'targetBranch': 'main',
        'lines': [
          {'key': 'work', 'ok': true, 'detail': '1/1 work items'},
          {'key': 'tests', 'ok': true, 'detail': 'not configured on the host'},
          {'key': 'build', 'ok': true, 'detail': 'not configured on the host'},
          {'key': 'review', 'ok': true, 'detail': 'no review requested'},
          {'key': 'conflicts', 'ok': true, 'detail': 'merges cleanly'},
          {'key': 'acceptance', 'ok': true, 'detail': 'not reported'},
        ],
        'files': 1,
        'additions': 3,
        'deletions': 0,
        'changes': [
          {'path': 'calc.py', 'additions': 3, 'deletions': 0},
        ],
        'boundaries': [
          {
            'key': 'require_approval',
            'satisfied': false,
            'text': 'Never merge without approval',
          },
        ],
        'mergeRequest': {'id': 'gc-mr-14', 'title': 'sling-oc-loy'},
        'mergeCommit': null,
        'branches': ['polecat/oc-loy'],
      };
    } else if (path == '$prefix/merge-readiness/unknown') {
      status = 404;
      contentType = ContentType('application', 'problem+json');
      body = {
        'type': 'urn:opencode-mobile:front:run-not-found',
        'title': 'Not Found',
        'status': 404,
        'code': 'run-not-found',
      };
    } else if (path == '$prefix/merge-readiness/no-rig') {
      status = 422;
      contentType = ContentType('application', 'problem+json');
      body = {
        'type': 'urn:opencode-mobile:front:merge-unavailable',
        'title': 'Unprocessable Entity',
        'status': 422,
        'code': 'merge-unavailable',
      };
    } else if (path == '$prefix/mr/gc-mr-14/approve') {
      body = receipt('front-approve', 200, {
        'id': 'gc-mr-14',
        'metadata': {'review.approved_by': 'you@example.com'},
      });
    } else if (path == '$prefix/merge/oc-xru') {
      status = 403;
      body = receipt('front-merge', 403, {
        'type': 'urn:opencode-mobile:front:boundary',
        'title': 'Forbidden',
        'status': 403,
        'code': 'boundary',
        'boundary': 'require_approval',
        'detail': 'Never merge without approval',
      });
    } else if (path == '$prefix/merge/oc-ok') {
      body = receipt('front-merge-2', 200, {
        'status': 'merged',
        'mergeCommit': 'c02e375abc',
        'branch': 'main',
        'alreadyMerged': false,
      });
    } else {
      status = 404;
      body = {'type': 'urn:gascity:error:not-found', 'status': 404};
    }
    request.response
      ..statusCode = status
      ..headers.contentType = contentType
      ..headers.set('X-GC-Request-Id', 'r-${requests.length}')
      ..write(jsonEncode(body));
    await request.response.close();
  }
}

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

  group('MutationStore', () {
    test('records round-trip and prune keeps unsettled ones', () async {
      final records = [
        for (var i = 0; i < 5; i++)
          MutationRecord(
            key: 'k$i',
            request: MutationRequest.respond(
              'g$i',
              const GateResponse.text('yes'),
            ),
            createdAt: clock.add(Duration(minutes: i)),
            status: i == 1 ? MutationStatus.sent : MutationStatus.confirmed,
            receipt: MutationReceipt(
              id: 'k$i',
              status: MutationReceiptStatus.accepted,
              correlationId: 'c$i',
              hostRequestId: 'h$i',
              upstreamStatus: 202,
              replayed: i == 2,
            ),
          ),
      ];
      final mutations = MutationStore(prefs, keep: 2);
      for (final record in records) {
        await mutations.save(profileId, record);
      }
      final read = mutations.read(profileId);
      expect(read.map((r) => r.key), ['k0', 'k1', 'k2', 'k3', 'k4']);
      expect(read[2].receipt?.replayed, isTrue);
      expect(read[1].request.response?.text, 'yes');
      expect(mutations.get(profileId, 'k3')?.correlationId, 'c3');
      await mutations.prune(profileId);
      // Three settled ones went (oldest first); the sent one stayed.
      expect(mutations.read(profileId).map((r) => r.key), ['k1', 'k4']);
      // Garbage is skipped, not thrown.
      await prefs.setString(MutationStore.keyFor(profileId, 'bad'), '{');
      expect(mutations.read(profileId).length, 2);
    });

    test('requests of every kind round-trip', () {
      final requests = [
        MutationRequest.respond('g', const GateResponse.choice('a')),
        MutationRequest.respond(
          'g',
          const GateResponse.confirmation(confirmed: false),
        ),
        MutationRequest.message('a', 'hi'),
        MutationRequest.controlAgent('a', AgentControlAction.pause),
        MutationRequest.cancelRun('r'),
        MutationRequest.assign('w', agentId: 'a'),
        MutationRequest.createWork(
          title: 'Add a docstring',
          description: 'One line.',
          projectId: 'ocproof',
        ),
        MutationRequest.createWork(title: 'Bare'),
      ];
      for (final request in requests) {
        final back = MutationRequest.fromJson(
          jsonDecode(jsonEncode(request.toJson())),
        )!;
        expect(back.toJson(), request.toJson());
      }
      expect(requests[1].response?.confirmed, isFalse);
      expect(requests[2].response, isNull);
      // TEAM-306: the title is the target (no id yet), the details the
      // text and the rig its own field.
      final create = requests[6];
      expect(create.kind, MutationKind.createWork);
      expect(create.targetId, 'Add a docstring');
      expect(create.text, 'One line.');
      expect(create.projectId, 'ocproof');
      expect(create.toJson(), {
        'kind': 'createWork',
        'targetId': 'Add a docstring',
        'text': 'One line.',
        'projectId': 'ocproof',
      });
      expect(requests[7].toJson(), {'kind': 'createWork', 'targetId': 'Bare'});
      final record = MutationRecord(
        key: 'k',
        request: create,
        createdAt: DateTime.utc(2026, 9, 12),
        status: MutationStatus.confirmed,
        receipt: const MutationReceipt(
          id: 'k',
          status: MutationReceiptStatus.accepted,
          raw: {'id': 'gc-77', 'status': 'open'},
          upstreamStatus: 201,
        ),
      );
      final back = MutationRecord.fromJson(
        jsonDecode(jsonEncode(record.toJson())),
      )!;
      expect(back.request.projectId, 'ocproof');
      expect(back.receipt?.createdId, 'gc-77');
    });
  });

  group('front receipts (receiptFromFront)', () {
    test('accepted receipt with a correlation id', () {
      final receipt = receiptFromFront(
        'k',
        const OrchestrationHttpResponse(
          statusCode: 202,
          requestId: 'host-9',
          body: {
            'request_id': 'host-9',
            'status': 'accepted',
            'upstream_status': 202,
            'body': {
              'status': 'accepted',
              'request_id': 'req-abc',
              'event_cursor': '1443',
            },
            'idempotency_key': 'k',
          },
        ),
      );
      expect(receipt.status, MutationReceiptStatus.accepted);
      expect(receipt.correlationId, 'req-abc');
      expect(receipt.hostRequestId, 'host-9');
      expect(receipt.upstreamStatus, 202);
      expect(receipt.replayed, isFalse);
    });

    test('rejected receipt carries the supervisor problem', () {
      final receipt = receiptFromFront(
        'k',
        const OrchestrationHttpResponse(
          statusCode: 404,
          replayed: true,
          body: {
            'request_id': 'host-2',
            'status': 'rejected',
            'upstream_status': 404,
            'body': {
              'type': 'urn:gascity:error:session-not-found',
              'title': 'Session Not Found',
              'status': 404,
              'detail': 'session nope not found',
            },
            'idempotency_key': 'k',
          },
        ),
      );
      expect(receipt.status, MutationReceiptStatus.rejected);
      expect(receipt.message, 'session nope not found');
      expect(receipt.replayed, isTrue);
      expect(receipt.correlationId, isNull);
    });

    test('front problems: 502 pending+retryable, 409 and 403 rejected', () {
      final down = receiptFromFront(
        'k',
        frontProblem(502, 'upstream-unavailable'),
      );
      expect(down.status, MutationReceiptStatus.pending);
      expect(down.retryable, isTrue);
      final mismatch = receiptFromFront(
        'k',
        frontProblem(409, 'idempotency-mismatch'),
      );
      expect(mismatch.status, MutationReceiptStatus.rejected);
      expect(mismatch.retryable, isFalse);
      final forbidden = receiptFromFront(
        'k',
        frontProblem(403, 'identity-not-allowed'),
      );
      expect(forbidden.status, MutationReceiptStatus.rejected);
      expect(forbidden.message, 'detail for identity-not-allowed');
    });

    test('a bare supervisor answer maps too', () {
      final ok = receiptFromFront(
        'k',
        const OrchestrationHttpResponse(
          statusCode: 200,
          body: {'status': 'ok', 'id': 'bl-1'},
        ),
      );
      expect(ok.status, MutationReceiptStatus.accepted);
      expect(ok.upstreamStatus, 200);
      final bad = receiptFromFront(
        'k',
        const OrchestrationHttpResponse(
          statusCode: 422,
          body: {
            'type': 'urn:gascity:error:invalid-request',
            'title': 'Unprocessable',
            'status': 422,
            'detail': 'action is required',
          },
        ),
      );
      expect(bad.status, MutationReceiptStatus.rejected);
      expect(bad.message, 'action is required');
    });

    test('respondAction and receipts serialise', () {
      expect(respondAction(const GateResponse.choice('keep')), 'keep');
      expect(
        respondAction(const GateResponse.confirmation(confirmed: true)),
        'allow',
      );
      expect(
        respondAction(const GateResponse.confirmation(confirmed: false)),
        'deny',
      );
      expect(respondAction(const GateResponse.text('why')), 'text');
      const receipt = MutationReceipt(
        id: 'k',
        status: MutationReceiptStatus.pending,
        message: 'm',
        retryable: true,
        replayed: true,
        hostRequestId: 'h',
        correlationId: 'c',
        upstreamStatus: 502,
        raw: {'a': 1},
      );
      final back = MutationReceipt.fromJson(
        jsonDecode(jsonEncode(receipt.toJson())),
      )!;
      expect(back.toJson(), receipt.toJson());
      expect(MutationReceipt.fromJson('nope'), isNull);
    });
  });

  group('merge mutations (TEAM-205)', () {
    test('approve and merge go through mutate with a persisted key', () async {
      final gateway = MergeGateway();
      final controller = await boot(gateway);
      gateway.answer = (call) async {
        expect(stored(call.requestId)?['status'], 'sent');
        return MutationReceipt(
          id: call.requestId,
          status: MutationReceiptStatus.accepted,
          upstreamStatus: 200,
          raw: const {
            'body': {'status': 'merged', 'mergeCommit': 'c02e375'},
          },
        );
      };
      final approved = await controller.approveMergeRequest(
        'gc-mr-14',
        runId: 'oc-xru',
      );
      expect(approved.kind, MutationKind.approveMerge);
      expect(approved.targetId, 'gc-mr-14');
      expect(approved.status, MutationStatus.confirmed);
      final merged = await controller.mergeRun('oc-xru');
      expect(merged.kind, MutationKind.merge);
      expect(merged.status, MutationStatus.confirmed);
      expect(
        merged.receipt?.raw['body'],
        containsPair('mergeCommit', 'c02e375'),
      );
      expect(gateway.calls.map((c) => c.verb), ['approveMerge', 'merge']);
      expect(gateway.calls.map((c) => c.requestId), ['key-1', 'key-2']);
      expect(stored('key-2')?['status'], 'confirmed');
      // Both helpers read the readiness again after the host answered.
      await settle();
      expect(gateway.readinessReads, 2);
    });

    test('a 202 approval is confirmed by the request bead changing', () async {
      final gateway = MergeGateway();
      final controller = await boot(gateway);
      final record = await controller.approveMergeRequest('gc-mr-14');
      expect(record.status, MutationStatus.sent);
      gateway.push(
        const BeadChanged(beadId: 'other', change: BeadChange.updated),
      );
      await settle();
      expect(controller.mutation('key-1')?.status, MutationStatus.sent);
      gateway.push(
        const BeadChanged(beadId: 'gc-mr-14', change: BeadChange.updated),
      );
      await settle();
      expect(controller.mutation('key-1')?.status, MutationStatus.confirmed);
    });

    test(
      'a host refusal on a boundary settles the record as rejected',
      () async {
        final gateway = MergeGateway();
        final controller = await boot(gateway);
        gateway.answer = (call) async => MutationReceipt(
          id: call.requestId,
          status: MutationReceiptStatus.rejected,
          message: 'Never merge without approval',
          upstreamStatus: 403,
          raw: const {
            'status': 'rejected',
            'upstream_status': 403,
            'body': {'code': 'boundary', 'boundary': 'require_approval'},
          },
        );
        final record = await controller.mergeRun('oc-xru');
        expect(record.status, MutationStatus.rejected);
        expect(record.receipt?.message, 'Never merge without approval');
        expect(gateway.callCount, 1);
      },
    );

    test(
      'without merge roles on the adapter the write is rejected locally',
      () async {
        final gateway = ScriptedGateway();
        final controller = await boot(gateway);
        expect(controller.capabilities.mergeReadiness, isTrue);
        final record = await controller.mergeRun('oc-xru');
        expect(record.status, MutationStatus.rejected);
        expect(record.receipt?.message, 'the host does not allow this control');
        expect(gateway.callCount, 0);
        expect(await controller.mergeReadiness('oc-xru'), isNull);
      },
    );

    test('without the capability the write is rejected locally', () async {
      final gateway = MergeGateway()
        ..readiness = const MergeReadiness(runId: 'oc-xru', ready: true);
      final controller = await boot(gateway);
      // The default 202 answer waits for the host's result; the write went.
      final record = await controller.mutate(MutationRequest.merge('oc-xru'));
      expect(record.status, MutationStatus.sent);
      expect(gateway.callCount, 1);
      // gascityRead has no merge roles even when the adapter would.
      final readOnly = MergeGateway(
        capabilities: OrchestrationCapabilities.gascityRead,
      );
      final other = await boot(readOnly);
      final refused = await other.mergeRun('oc-xru');
      expect(refused.status, MutationStatus.rejected);
      expect(readOnly.callCount, 0);
      expect(await other.mergeReadiness('oc-xru'), isNull);
      expect(readOnly.readinessReads, 0);
    });

    test('readiness is cached per run, shared while loading, refetched on '
        'refresh and forgotten on stop', () async {
      final gateway = MergeGateway()
        ..readiness = const MergeReadiness(
          runId: 'oc-xru',
          ready: false,
          lines: [MergeReadinessLine(key: 'tests', ok: false, detail: 'x')],
        );
      final controller = await boot(gateway);
      expect(controller.mergeReadinessFor('oc-xru'), isNull);
      final first = controller.mergeReadiness('oc-xru');
      expect(controller.mergeReadinessLoading('oc-xru'), isTrue);
      final second = controller.mergeReadiness('oc-xru');
      expect(identical(first, second), isTrue);
      expect((await first)?.firstMissing?.key, 'tests');
      expect(gateway.readinessReads, 1);
      expect(controller.mergeReadinessLoading('oc-xru'), isFalse);
      expect(await controller.mergeReadiness('oc-xru'), isNotNull);
      expect(gateway.readinessReads, 1);
      await controller.refresh();
      await settle();
      expect(gateway.readinessReads, 2);
      expect(controller.mergeReadinessFor('oc-xru')?.ready, isFalse);
      await controller.stop();
      expect(controller.mergeReadinessFor('oc-xru'), isNull);
    });

    test(
      'a failing readiness read is kept as the error, never thrown',
      () async {
        final controller = await boot(_FailingMergeGateway());
        expect(await controller.mergeReadiness('oc-xru'), isNull);
        expect(controller.mergeReadinessError('oc-xru'), isA<StateError>());
        expect(controller.mergeReadinessFor('oc-xru'), isNull);
      },
    );

    test('GasCityControl routes: readiness GET, approve and merge POST '
        'through the front', () async {
      final host = await _MergeHost.start();
      addTearDown(host.close);
      final gateway = GasCityGateway(
        url: host.url,
        city: 'bright-lights',
        front: true,
      );
      addTearDown(gateway.close);
      expect(gateway.capabilities.mergeReadiness, isTrue);
      expect(gateway.capabilities.changes, isTrue);
      expect(gateway.capabilities.verification, isTrue);

      final readiness = await gateway.mergeReadiness('oc-xru');
      expect(readiness?.ready, isTrue);
      expect(readiness?.targetBranch, 'main');
      expect(readiness?.mergeRequest?.id, 'gc-mr-14');
      expect(readiness?.lines.map((l) => l.key), contains('conflicts'));
      expect(
        host.requests.last,
        'GET /v0/city/bright-lights/front/merge-readiness/oc-xru',
      );
      expect(await gateway.mergeReadiness('unknown'), isNull);
      expect(await gateway.mergeReadiness('no-rig'), isNull);

      final approved = await gateway.approveMerge(
        'gc-mr-14',
        requestId: 'k-approve',
      );
      expect(approved.status, MutationReceiptStatus.accepted);
      expect(approved.upstreamStatus, 200);
      expect(approved.hostRequestId, 'front-approve');
      expect(
        host.requests.last,
        'POST /v0/city/bright-lights/front/mr/gc-mr-14/approve',
      );
      expect(host.keys.last, 'k-approve');

      final refused = await gateway.merge('oc-xru', requestId: 'k-merge');
      expect(refused.status, MutationReceiptStatus.rejected);
      expect(refused.upstreamStatus, 403);
      expect(refused.message, 'Never merge without approval');
      expect(refused.raw['body'], containsPair('boundary', 'require_approval'));
      expect(
        host.requests.last,
        'POST /v0/city/bright-lights/front/merge/oc-xru',
      );
      expect(host.keys.last, 'k-merge');

      final merged = await gateway.merge('oc-ok', requestId: 'k-merge-2');
      expect(merged.status, MutationReceiptStatus.accepted);
      expect(merged.upstreamStatus, 200);
      expect(merged.raw['body'], containsPair('mergeCommit', 'c02e375abc'));

      // A bare supervisor has no merge roles at all.
      final bare = GasCityGateway(url: host.url, city: 'bright-lights');
      addTearDown(bare.close);
      expect(bare.capabilities.mergeReadiness, isFalse);
      expect(await bare.mergeReadiness('oc-xru'), isNull);
      final rejected = await bare.merge('oc-xru', requestId: 'k-bare');
      expect(rejected.status, MutationReceiptStatus.rejected);
      expect(rejected.message, 'front required');
    });
  });

  group('probe and discovery', () {
    test('finds a front and yields gascityFront', () async {
      final host = await _Host.start();
      addTearDown(host.close);
      final verdict = await const GasCityProbe().probe(host.url);
      expect(verdict, isA<ProbeFound>());
      final found = verdict as ProbeFound;
      expect(found.front, isTrue);
      expect(found.identityLogin, 'you@example.com');
      expect(found.identityAllowed, isTrue);
      expect(found.readOnly, isFalse);
      expect(found.capabilities.controlRespond, isTrue);
      expect(
        found.capabilities.asMap(),
        OrchestrationCapabilities.gascityFront.asMap(),
      );
      expect(found.host.url, host.url);
      expect(found.city, 'bright-lights');
      expect(found.describe(), contains('via front'));
      // The well-known route was asked first.
      expect(host.requests.first, frontWellKnownPath);
      // The config a found front turns into carries controls.
      final config = teamConfigFromVerdict(found, url: host.url, city: '');
      expect(config.front, isTrue);
      expect(config.url, host.url);
    });

    test('a front that does not allow this device is read-only', () async {
      final host = await _Host.start()
        ..allowed = false;
      addTearDown(host.close);
      final found = await const GasCityProbe().probe(host.url) as ProbeFound;
      expect(found.front, isTrue);
      expect(found.identityAllowed, isFalse);
      expect(found.readOnly, isTrue);
      expect(found.capabilities.anyControl, isFalse);
    });

    test('no well-known document: the bare supervisor probe decides', () async {
      final host = await _Host.start()
        ..front = false;
      addTearDown(host.close);
      final found = await const GasCityProbe().probe(host.url) as ProbeFound;
      expect(found.front, isFalse);
      expect(found.readOnly, isTrue);
      expect(
        found.capabilities.asMap(),
        OrchestrationCapabilities.gascityRead.asMap(),
      );
      expect(host.requests.first, frontWellKnownPath);
      expect(host.requests, contains('/health'));
    });

    test('the gateway factory builds a front gateway from a found front', () {
      final found = ProbeFound(
        host: const OrchestrationHostIdentity(
          provider: 'gascity',
          url: 'http://100.100.1.2:8373',
          hostMode: OrchestrationHostMode.computer,
        ),
        city: 'bright-lights',
        front: true,
        identityAllowed: true,
        capabilities: OrchestrationCapabilities.gascityFront,
      );
      final withFront =
          OrchestrationController.defaultGatewayFactory(frontConfig(), found)
              as GasCityGateway;
      addTearDown(withFront.close);
      expect(withFront.front, isTrue);
      expect(withFront.capabilities.controlRespond, isTrue);
      expect(withFront.url, 'http://100.100.1.2:8373');
      final readOnly =
          OrchestrationController.defaultGatewayFactory(
                frontConfig(),
                ProbeFound(host: found.host, city: 'bright-lights'),
              )
              as GasCityGateway;
      addTearDown(readOnly.close);
      expect(readOnly.front, isFalse);
      expect(readOnly.capabilities.anyControl, isFalse);
    });

    test('discovery tries the front port before the supervisor port', () {
      expect(teamDiscoveryUrlsFor('http://100.100.1.2:4096'), [
        'http://100.100.1.2:8373',
        'http://100.100.1.2:8372',
      ]);
      expect(teamDiscoveryUrlsFor('https://pc.tail1234.ts.net'), [
        'http://pc.tail1234.ts.net:8373',
        'http://pc.tail1234.ts.net:8372',
      ]);
      expect(teamDiscoveryUrlsFor('https://server.example:4096'), isEmpty);
      expect(teamDiscoveryUrlsFor('not a url'), isEmpty);
      expect(teamHostFrontPort, 8373);
    });
  });
}
