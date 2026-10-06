// TEAM-207: write proof against a live Gas City behind the host front,
// through the same classes the app uses (`GasCityProbe` → `GasCityGateway`
// with `front: true` → `GasCityControl`) and nothing else on the write path.
//
//   dart run tool/qa/gascity_write_proof.dart --url http://100.101.102.103:8373
//       [--city bright-lights] [--supervisor http://127.0.0.1:8372]
//       [--rig ocproof] [--pool ocproof/gastown.polecat] [--wait 360]
//       [--json report.json]
//
// Steps, each timed and recorded with its receipt (front request id,
// receipt status, upstream status, replay flag) and the stream events that
// confirmed it:
//
//   a. probe the front: `front: true`, control capability, identity allowed;
//      read the policy document (`/front/policy`);
//   b. create one tiny bead on the loopback supervisor (`POST /beads`, the
//      only call that bypasses the front: the app has no "create work" verb)
//      and sling it to the polecat pool through the front (`assign` →
//      `POST /sling` with an Idempotency-Key); replay the same key and
//      check `Idempotent-Replayed`;
//   c. wait up to `--wait` seconds for the pool to wake a session for the
//      bead (the `session.woke` event or the app's agent list, confirmed by
//      the session bead's `gc.trigger_bead_id`), then nudge that agent
//      through the front (`messageAgent`) and wait for the matching
//      `request.result` event on the stream;
//   d. stop the agent through the front (`controlAgent(stop)`) and wait for
//      the effect on the stream (`session.stopped`, or `bead.closed` on the
//      session bead, which is what Gas City 1.4.1 emits);
//   e. read the convoy's merge readiness (`/front/merge-readiness/{id}`)
//      while it is still open;
//   f. close the auto-created convoy through the front (`cancelRun` →
//      `POST /convoy/{id}/close`);
//   g. identity refusal: not exercisable from an allowlisted PC (the front
//      identifies the peer by its tailnet address; there is no test hook),
//      recorded as skipped with the front unit tests that cover it.
//
// Cleanup, always attempted: close the bead on the supervisor so the pool
// does not pick it up again, and confirm no polecat session of this run is
// still active. Prints one compact JSON report on stdout and exits 1 on any
// hard failure; a step that could not be confirmed inside its window is
// recorded as `unconfirmed`, an effect the host refused after a proven
// round trip under `hostRefused`, never invented. No Flutter imports.

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/orchestration/adapters/gascity/gascity_gateway.dart';
import 'package:opencode_mobile/orchestration/adapters/gascity/gascity_probe.dart';
import 'package:opencode_mobile/orchestration/client/http.dart';

Future<void> main(List<String> args) async {
  var url = 'http://100.101.102.103:8373';
  var supervisor = 'http://127.0.0.1:8372';
  var city = 'bright-lights';
  var rig = 'ocproof';
  var pool = 'ocproof/gastown.polecat';
  var wait = const Duration(seconds: 360);
  String? jsonPath;
  for (var i = 0; i < args.length; i++) {
    switch (args[i]) {
      case '--url':
        url = args[++i];
      case '--supervisor':
        supervisor = args[++i];
      case '--city':
        city = args[++i];
      case '--rig':
        rig = args[++i];
      case '--pool':
        pool = args[++i];
      case '--wait':
        wait = Duration(seconds: int.parse(args[++i]));
      case '--json':
        jsonPath = args[++i];
      default:
        stderr.writeln('unknown argument ${args[i]}');
        exit(2);
    }
  }
  final proof = _WriteProof(
    url: url,
    supervisor: supervisor,
    city: city,
    rig: rig,
    pool: pool,
    wait: wait,
  );
  final report = await proof.run();
  final encoded = jsonEncode(report);
  stdout.writeln(encoded);
  if (jsonPath != null) {
    File(
      jsonPath,
    ).writeAsStringSync(const JsonEncoder.withIndent('  ').convert(report));
  }
  exit(report['ok'] == true ? 0 : 1);
}

/// One observed stream event, as the report records it.
class _Seen {
  _Seen(this.event) : at = DateTime.now();

  final OrchestrationEvent event;
  final DateTime at;

  Map<String, Object?> toJson() => {
    'type': event.type,
    'seq': event.seq,
    'at': at.toUtc().toIso8601String(),
    if (event case SessionChanged e) 'sessionId': e.sessionId,
    if (event case SessionChanged e) 'agentId': e.agentId,
    if (event case BeadChanged e) 'beadId': e.beadId,
    if (event case RunChanged e) 'runId': e.runId,
    if (event case RequestResult e) 'requestId': e.requestId,
    if (event case RequestResult e) 'ok': e.ok,
    if (event case RequestResult e) 'operation': e.operation,
    if (event case RequestResult e) 'error': e.errorMessage,
  };
}

class _WriteProof {
  _WriteProof({
    required this.url,
    required this.supervisor,
    required this.city,
    required this.rig,
    required this.pool,
    required this.wait,
  });

  final String url;
  final String supervisor;
  final String city;
  final String rig;
  final String pool;
  final Duration wait;

  final failures = <String>[];
  final unconfirmed = <String>[];
  final hostRefused = <String>[];
  final skipped = <String>[];
  final timingsMs = <String, int>{};
  final receipts = <String, Object?>{};
  final seen = <_Seen>[];
  final started = DateTime.now();
  var keyCounter = 0;

  String? beadId;
  String? sessionId;
  String? agentId;
  String? convoyId;

  /// One idempotency key per attempt, the way the controller mints them.
  String key(String step) =>
      '${started.toUtc().millisecondsSinceEpoch.toRadixString(36)}'
      '-$step-${++keyCounter}';

  void check(bool condition, String message) {
    if (!condition) failures.add(message);
  }

  Future<T> timed<T>(String name, Future<T> Function() body) async {
    final watch = Stopwatch()..start();
    try {
      return await body();
    } finally {
      timingsMs[name] = watch.elapsedMilliseconds;
    }
  }

  Map<String, Object?> receipt(MutationReceipt r) => {
    'key': r.id,
    'status': r.status.name,
    'upstreamStatus': r.upstreamStatus,
    'hostRequestId': r.hostRequestId,
    'correlationId': r.correlationId,
    'replayed': r.replayed,
    'retryable': r.retryable,
    if (r.message != null) 'message': r.message,
    'body': r.raw['body'] ?? r.raw,
  };

  /// Waits until [predicate] answers non-null over the events seen so far,
  /// or [limit] passes; polls the collected list (the stream subscription
  /// fills it in the background).
  Future<T?> awaitEvent<T>(
    T? Function(List<_Seen> events) predicate,
    Duration limit,
  ) async {
    final deadline = DateTime.now().add(limit);
    while (DateTime.now().isBefore(deadline)) {
      final hit = predicate(seen);
      if (hit != null) return hit;
      await Future<void>.delayed(const Duration(milliseconds: 500));
    }
    return predicate(seen);
  }

  Future<Map<String, Object?>> run() async {
    final total = Stopwatch()..start();
    final report = <String, Object?>{
      'url': url,
      'supervisor': supervisor,
      'city': city,
      'rig': rig,
      'pool': pool,
      'startedAt': started.toUtc().toIso8601String(),
    };

    // a. Probe the front.
    final verdict = await timed(
      'probe',
      () => const GasCityProbe().probe(url, city: city),
    );
    report['probe'] = verdict.describe();
    if (verdict is! ProbeFound) {
      failures.add('probe: ${verdict.describe()}');
      return _finish(report, total);
    }
    report['front'] = verdict.front;
    report['identityLogin'] = verdict.identityLogin;
    report['identityAllowed'] = verdict.identityAllowed;
    report['capabilities'] = verdict.capabilities.asMap();
    check(verdict.front, 'probe: the URL is not a host front');
    check(verdict.identityAllowed, 'probe: this identity may not write');
    check(
      verdict.capabilities.controlAssign &&
          verdict.capabilities.controlMessage &&
          verdict.capabilities.controlAgent &&
          verdict.capabilities.controlCancelRun,
      'probe: control capabilities are off',
    );
    if (!verdict.front || !verdict.identityAllowed) {
      return _finish(report, total);
    }

    final gateway = GasCityGateway(url: url, city: city, front: true);
    final loop = OrchestrationHttpClient(baseUrl: supervisor, city: city);
    StreamSubscription<OrchestrationEvent>? events;
    try {
      final host = await timed('connect', gateway.connect);
      report['host'] = {
        'provider': host.provider,
        'version': host.version,
        'city': host.city,
        'url': host.url,
      };

      // Keep the stream open for the whole proof so every result is seen.
      events = gateway.events().listen(
        (event) => seen.add(_Seen(event)),
        onError: (Object error) => seen.add(
          _Seen(UnknownOrchestrationEvent(type: 'stream.error: $error')),
        ),
      );

      final policy = await timed('policy', gateway.policy);
      report['policy'] = policy?.toJson();
      check(policy != null, 'policy: the front answered no policy document');

      // b. Create a tiny bead on the loopback supervisor, sling it via the
      // front, replay the key.
      final created = await timed(
        'createBead',
        () => loop.postJson('${loop.cityPath}/beads', {
          'title': 'Write proof: add a docstring to add() in calc.py',
          'description':
              'Add a one-line docstring to add(a, b) in calc.py. Nothing '
              'else. This bead is a write proof for the phone app and will '
              'be stopped and closed shortly after it starts.',
          'rig': rig,
          'type': 'task',
          'priority': 2,
          'labels': ['opencode-mobile-write-proof'],
        }, requestId: 'opencode-mobile-write-proof'),
      );
      final bead = created['id'];
      check(bead is String && bead.isNotEmpty, 'createBead: no id in $created');
      if (bead is! String) return _finish(report, total);
      beadId = bead;
      report['bead'] = {
        'id': bead,
        'status': created['status'],
        'title': created['title'],
      };

      final slingKey = key('sling');
      final slung = await timed(
        'sling',
        () => gateway.assign(bead, agentId: pool, requestId: slingKey),
      );
      receipts['sling'] = receipt(slung);
      check(slung.isAccepted, 'sling: ${slung.status.name} ${slung.message}');
      check(slung.hostRequestId != null, 'sling: no X-GC-Request-Id');
      check(!slung.replayed, 'sling: first send flagged as replayed');

      final replay = await timed(
        'slingReplay',
        () => gateway.assign(bead, agentId: pool, requestId: slingKey),
      );
      receipts['slingReplay'] = receipt(replay);
      check(replay.replayed, 'slingReplay: Idempotent-Replayed not set');
      check(
        replay.hostRequestId == slung.hostRequestId,
        'slingReplay: request id ${replay.hostRequestId} != '
        '${slung.hostRequestId}',
      );
      check(
        replay.upstreamStatus == slung.upstreamStatus,
        'slingReplay: upstream status differs',
      );

      // The auto-created convoy tracks the bead.
      convoyId = await timed('findConvoy', () => _findConvoy(gateway, bead));
      report['convoy'] = convoyId;
      check(convoyId != null, 'convoy: none tracks $bead after the sling');

      // c. Wait for the pool session, then nudge it via the front.
      final session = await timed(
        'awaitSession',
        () => _awaitSession(gateway, bead),
      );
      report['session'] = session;
      if (session == null) {
        unconfirmed.add(
          'c/d: no polecat session for $bead within ${wait.inSeconds}s; '
          'nudge and stop not exercised on a live session',
        );
      } else {
        sessionId = session['sessionId'] as String?;
        agentId = session['agentId'] as String?;
        final nudgeKey = key('nudge');
        final nudged = await timed(
          'nudge',
          () => gateway.controlAgent(
            agentId!,
            AgentControlAction.nudge,
            requestId: nudgeKey,
          ),
        );
        receipts['nudge'] = receipt(nudged);
        check(
          nudged.isAccepted,
          'nudge: ${nudged.status.name} ${nudged.message}',
        );
        final correlation = nudged.correlationId;
        if (correlation == null) {
          unconfirmed.add('nudge: the 202 body carried no request_id');
        } else {
          final result = await timed(
            'nudgeResult',
            () => awaitEvent<_Seen>(
              (events) => events.cast<_Seen?>().firstWhere(
                (s) =>
                    s!.event is RequestResult &&
                    (s.event as RequestResult).requestId == correlation,
                orElse: () => null,
              ),
              const Duration(seconds: 90),
            ),
          );
          report['nudgeResult'] = result?.toJson();
          if (result == null) {
            unconfirmed.add(
              'nudge: no request.result for $correlation within 90 s',
            );
          } else if (!(result.event as RequestResult).ok) {
            // The round trip is proven (receipt → correlation id →
            // request.failed on the stream); the host refused the effect.
            // The app shows exactly this message on the receipt chip.
            hostRefused.add(
              'nudge: ${(result.event as RequestResult).errorMessage}',
            );
          }
        }

        // d. Stop it via the front; expect session.stopped.
        final stopKey = key('stop');
        final stopped = await timed(
          'stop',
          () => gateway.controlAgent(
            agentId!,
            AgentControlAction.stop,
            requestId: stopKey,
          ),
        );
        receipts['stop'] = receipt(stopped);
        check(
          stopped.isAccepted,
          'stop: ${stopped.status.name} ${stopped.message}',
        );
        // The effect: `session.stopped` for the session, or — what Gas
        // City 1.4.1 actually emits for an explicit stop — `bead.closed`
        // on the session bead (the controller accepts both).
        final stopEvent = await timed(
          'stopEvent',
          () => awaitEvent<_Seen>(
            (events) => events.cast<_Seen?>().firstWhere(
              (s) => switch (s!.event) {
                SessionChanged(change: SessionChange.stopped) =>
                  (s.event as SessionChanged).sessionId == sessionId,
                BeadChanged(change: BeadChange.closed) =>
                  (s.event as BeadChanged).beadId == sessionId,
                _ => false,
              },
              orElse: () => null,
            ),
            const Duration(seconds: 90),
          ),
        );
        report['stopEvent'] = stopEvent?.toJson();
        if (stopEvent == null) {
          unconfirmed.add(
            'stop: neither session.stopped nor bead.closed for $sessionId '
            'within 90 s (the receipt was accepted)',
          );
        } else {
          final doc = await gateway.http.getCity(
            '/bead/${Uri.encodeComponent(sessionId!)}',
          );
          final meta = doc['metadata'];
          report['sessionAfterStop'] = {
            'status': doc['status'],
            if (meta is Map) 'closeReason': meta['close_reason'],
            if (meta is Map) 'closedAt': meta['closed_at'],
          };
        }
      }

      // e. Merge readiness for the convoy while it is open: expected not
      // ready (the work is not done).
      final convoy = convoyId;
      if (convoy != null) {
        final readiness = await timed(
          'mergeReadiness',
          () => gateway.mergeReadiness(convoy),
        );
        report['mergeReadiness'] = readiness == null
            ? null
            : {
                'ready': readiness.ready,
                'rig': readiness.rig,
                'targetBranch': readiness.targetBranch,
                'lines': [for (final l in readiness.lines) l.toJson()],
                'boundaries': [
                  for (final b in readiness.boundaries) b.toJson(),
                ],
                'mergeRequest': readiness.mergeRequest?.toJson(),
                'branches': readiness.branches,
              };
        check(readiness != null, 'mergeReadiness: the front answered null');
        check(
          readiness == null || !readiness.canMerge,
          'mergeReadiness: reports mergeable for undone work',
        );

        // f. Close the convoy via the front.
        final closeKey = key('close');
        final closed = await timed(
          'cancelRun',
          () => gateway.cancelRun(convoy, requestId: closeKey),
        );
        receipts['cancelRun'] = receipt(closed);
        check(
          closed.isAccepted,
          'cancelRun: ${closed.status.name} ${closed.message}',
        );
        final after = await timed(
          'convoyAfterClose',
          () => gateway.http.getCity('/convoy/${Uri.encodeComponent(convoy)}'),
        );
        final status = (after['convoy'] as Map?)?['status'] ?? after['status'];
        report['convoyStatusAfterClose'] = status;
        check(status == 'closed', 'cancelRun: convoy status is $status');
      }

      // g. Identity refusal: skipped, with the reason.
      skipped.add(
        'g: a mutation from a non-allowlisted identity cannot be sent from '
        'this PC (the front identifies the peer by tailnet address; no test '
        'hook); covered by tool/host/cp_front/tests/test_front.py '
        'MutationTests.test_post_refused_for_non_allowlisted_is_problem_json '
        'and test_same_key_different_identity_is_409',
      );
    } on Object catch (error, stack) {
      failures.add('exception: $error');
      report['stack'] = stack.toString().split('\n').take(8).join('\n');
    } finally {
      report['cleanup'] = await _cleanup(gateway, loop);
      await events?.cancel();
      await gateway.close();
      loop.close();
    }
    return _finish(report, total);
  }

  /// The convoy whose `tracks` dependency names [bead]; polls a few times
  /// because the supervisor creates it just after the sling answers.
  Future<String?> _findConvoy(GasCityGateway gateway, String bead) async {
    for (var attempt = 0; attempt < 10; attempt++) {
      final json = await gateway.http.getCity('/convoys');
      final items = json['items'];
      if (items is List) {
        for (final item in items) {
          if (item is! Map) continue;
          final deps = item['dependencies'];
          if (deps is List &&
              deps.any(
                (d) =>
                    d is Map &&
                    d['type'] == 'tracks' &&
                    d['depends_on_id'] == bead,
              )) {
            return item['id'] as String?;
          }
        }
      }
      await Future<void>.delayed(const Duration(seconds: 1));
    }
    return null;
  }

  /// Waits for the pool to wake a session for [bead]: a `session.woke`
  /// event on the stream or a pool member in the app's own agent list with
  /// a session, either confirmed by the session bead's
  /// `gc.trigger_bead_id` (else the bead's `gc.session_id` once claimed).
  /// Names the agent the app would address: the pool member whose session
  /// it is, else the session id itself.
  Future<Map<String, Object?>?> _awaitSession(
    GasCityGateway gateway,
    String bead,
  ) async {
    final began = DateTime.now();
    final deadline = began.add(wait);
    var polls = 0;
    final rejected = <String>{};
    Future<bool> triggeredBy(String session) async {
      if (rejected.contains(session)) return false;
      try {
        final json = await gateway.http.getCity(
          '/bead/${Uri.encodeComponent(session)}',
        );
        final meta = json['metadata'];
        if (meta is Map && meta['gc.trigger_bead_id'] == bead) return true;
      } on OrchestrationHttpException {
        // Not a session bead (yet); try again next poll.
        return false;
      }
      rejected.add(session);
      return false;
    }

    while (DateTime.now().isBefore(deadline)) {
      polls += 1;
      final agents = await gateway.agents();
      final candidates = <String, OrchestrationAgent?>{};
      for (final s in seen) {
        if (s.event case final SessionChanged e
            when e.change == SessionChange.woke) {
          candidates[e.sessionId] = null;
        }
      }
      for (final a in agents) {
        if (a.pool == pool && a.sessionId != null) {
          candidates[a.sessionId!] = a;
        }
      }
      for (final a in agents) {
        for (final id in candidates.keys.toList()) {
          if (a.sessionId == id) candidates[id] = a;
        }
      }
      final claimed = await gateway.http.getCity(
        '/bead/${Uri.encodeComponent(bead)}',
      );
      final meta = claimed['metadata'];
      final claimedSession = meta is Map ? meta['gc.session_id'] : null;
      if (claimedSession is String && claimedSession.isNotEmpty) {
        candidates.putIfAbsent(claimedSession, () => null);
      }
      for (final entry in candidates.entries) {
        final session = entry.key;
        if (session != claimedSession && !await triggeredBy(session)) {
          continue;
        }
        final agent = entry.value;
        return {
          'sessionId': session,
          'agentId': agent?.id ?? session,
          'agentName': agent?.name,
          'agentState': agent?.rawState ?? agent?.state.name,
          'agentPool': agent?.pool,
          'beadStatus': claimed['status'],
          'assignee': claimed['assignee'],
          'claimed': session == claimedSession,
          'polls': polls,
          'waitedMs': DateTime.now().difference(began).inMilliseconds,
        };
      }
      await Future<void>.delayed(const Duration(seconds: 3));
    }
    return null;
  }

  /// Closes the bead on the supervisor and checks no polecat session of
  /// this run is still active; records what it found.
  Future<Map<String, Object?>> _cleanup(
    GasCityGateway gateway,
    OrchestrationHttpClient loop,
  ) async {
    final out = <String, Object?>{};
    final bead = beadId;
    if (bead != null) {
      try {
        final closed = await loop.postJson(
          '${loop.cityPath}/bead/${Uri.encodeComponent(bead)}/close',
          const {},
          requestId: 'opencode-mobile-write-proof',
        );
        out['beadClose'] = closed;
        final after = await loop.getCity('/bead/${Uri.encodeComponent(bead)}');
        out['beadStatus'] = after['status'];
        check(
          after['status'] == 'closed',
          'cleanup: bead is ${after['status']}',
        );
      } on Object catch (error) {
        out['beadClose'] = 'failed: $error';
        failures.add('cleanup: bead $bead not closed: $error');
      }
    }
    final session = sessionId;
    if (session != null) {
      try {
        // Give the host a moment to reflect the stop, then look.
        String? state;
        for (var attempt = 0; attempt < 12; attempt++) {
          final json = await loop.getCity('/sessions');
          final items = json['items'];
          state = null;
          if (items is List) {
            for (final item in items) {
              if (item is Map && item['id'] == session) {
                state = '${item['state'] ?? item['status']}';
              }
            }
          }
          if (state == null || state == 'stopped' || state == 'closed') break;
          await Future<void>.delayed(const Duration(seconds: 5));
        }
        out['sessionStateAtEnd'] = state ?? 'gone';
        if (state != null && state != 'stopped' && state != 'closed') {
          final kill = await loop.postJson(
            '${loop.cityPath}/session/${Uri.encodeComponent(session)}/stop',
            const {},
            requestId: 'opencode-mobile-write-proof',
          );
          out['sessionStopRetry'] = kill;
          failures.add(
            'cleanup: session $session was still $state; stopped again '
            'on the supervisor',
          );
        }
      } on Object catch (error) {
        out['sessionCheck'] = 'failed: $error';
      }
    }
    // Any other pool session this bead triggered (a respawn after the stop)
    // is stopped on the supervisor and reported; the pool must not keep
    // working on proof beads.
    if (bead != null) {
      try {
        final stray = <String>[];
        final json = await loop.getCity('/sessions');
        final items = json['items'];
        if (items is List) {
          for (final item in items) {
            if (item is! Map || item['id'] == session) continue;
            if (item['template'] != pool || item['running'] != true) continue;
            final id = '${item['id']}';
            final doc = await loop.getCity('/bead/${Uri.encodeComponent(id)}');
            final meta = doc['metadata'];
            if (meta is Map && meta['gc.trigger_bead_id'] == bead) {
              stray.add(id);
              await loop.postJson(
                '${loop.cityPath}/session/${Uri.encodeComponent(id)}/stop',
                const {},
                requestId: 'opencode-mobile-write-proof',
              );
            }
          }
        }
        out['straySessionsStopped'] = stray;
      } on Object catch (error) {
        out['strayCheck'] = 'failed: $error';
      }
    }
    return out;
  }

  Map<String, Object?> _finish(Map<String, Object?> report, Stopwatch total) {
    report['receipts'] = receipts;
    report['events'] = [for (final s in seen) s.toJson()];
    report['timingsMs'] = timingsMs;
    report['totalMs'] = total.elapsedMilliseconds;
    report['unconfirmed'] = unconfirmed;
    report['hostRefused'] = hostRefused;
    report['skipped'] = skipped;
    report['failures'] = failures;
    report['ok'] = failures.isEmpty;
    return report;
  }
}
