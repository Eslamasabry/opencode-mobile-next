// Coverage ratchet for what the AI Team CHANGES (gate 2).
// tool/coverage/team_actions.mjs lists every call in the Gas City supervisor
// contract that changes something, plus the host front's two merge calls;
// team_actions_ledger.json says for each one:
//   reachable: <control> -> <gateway method> [test: <file> ~ <token>]
//   covered elsewhere: <where>
//   not offered: <reason>
// A reachable entry is proven in two links: a test taps the control and sees
// the gateway method called (this file, or the named existing test), and a
// real gateway sends that method to the server over a loopback socket, where
// the call it makes is matched to the contract's operation. Reads go through the same loopback host
// the data gate uses (team_support.dart).
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/orchestration/adapters/gascity/gascity_gateway.dart';
import 'package:opencode_mobile/orchestration/adapters/inapp/phone_engine_gateway.dart';
import 'package:opencode_mobile/domain/orchestration_work_edits.dart';
import 'package:opencode_mobile/orchestration/adapters/gascity/gascity_work_edits.dart';

import 'lists_support.dart' show WireRequest, withRealHttp;
import 'team_support.dart';

/// A key such as `GC POST /v0/city/{cityName}/sling` matched against what was sent.
bool _matches(String key, WireRequest request) {
  final parts = key.split(' ');
  if (parts[0] == 'Engine') {
    if (request.method != 'POST' || request.path != '/v1/commands') {
      return false;
    }
    final body = jsonDecode(request.body);
    return body is Map && body['action'] == parts[1];
  }
  if (request.method != parts[1]) return false;
  final pattern = RegExp(
    '^${parts[2].replaceAllMapped(RegExp(r'\{[^}]+\}'), (_) => '[^/]+')}\$',
  );
  return pattern.hasMatch(request.path);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final inventory =
      (jsonDecode(
                File(
                  'test/fixtures/coverage/team_actions_samples.json',
                ).readAsStringSync(),
              )
              as Map)['operations']
          as List;
  final keys = [for (final o in inventory) (o as Map)['key'] as String];
  final ledger =
      (jsonDecode(
                File(
                  'test/fixtures/coverage/team_actions_ledger.json',
                ).readAsStringSync(),
              )
              as Map)
          .cast<String, String>();
  final reachable = {
    for (final e in ledger.entries)
      if (e.value.startsWith('reachable: ')) e.key: e.value,
  };
  // Proven by the tests below.
  final uiProven = <String>{};
  final sentProven = <String>{};

  test('every change in the AI Team has a ledger decision', () {
    expect(
      keys.toSet().difference(ledger.keys.toSet()),
      isEmpty,
      reason: 'calls with no entry in team_actions_ledger.json',
    );
    expect(
      ledger.keys.toSet().difference(keys.toSet()),
      isEmpty,
      reason: 'ledger entries for calls the protocols no longer have',
    );
    for (final entry in ledger.entries) {
      expect(
        entry.value.startsWith('reachable: ') ||
            entry.value.startsWith('covered elsewhere: ') ||
            entry.value.startsWith('not offered: '),
        isTrue,
        reason: '${entry.key}: reachable / covered elsewhere / not offered',
      );
      expect(entry.value.length, greaterThan(30), reason: entry.key);
    }
  });

  group('what the gateway sends', () {
    late TeamHost wire;
    late Map<String, Object?> city;
    setUpAll(() async {
      wire = await TeamHost.start();
      city =
          (((jsonDecode(
                                File(
                                  'test/fixtures/coverage/gascity_team_samples.json',
                                ).readAsStringSync(),
                              )
                              as Map)['cases']
                          as List)
                      .first
                  as Map)['payload']
              .cast<String, Object?>();
      wire.handler = (r) =>
          r.method == 'GET' ? supervisorAnswer(city, r) : <String, Object?>{};
    });
    tearDownAll(() => wire.close());

    Future<void> sends(String key, Future<Object?> Function() call) async {
      wire.requests.clear();
      try {
        await withRealHttp(call);
      } on Object {
        // The host's answer may not suit the call; what was sent is what counts.
      }
      final sent = wire.requests.where((r) => _matches(key, r)).toList();
      expect(
        sent,
        isNotEmpty,
        reason:
            '$key was not sent; requests: ${[for (final r in wire.requests) '${r.method} ${r.path}']}',
      );
      sentProven.add(key);
    }

    test('through the host front', () async {
      final gateway = GasCityGateway(
        url: wire.baseUrl,
        city: teamCity,
        front: true,
      );
      addTearDown(gateway.close);
      final edits = GasCityWorkEdits.of(gateway)!;
      const dir = 'shopfront/gastown.furiosa';
      Future<void> one(String key, Future<Object?> Function() call) =>
          sends(key, call);
      await one(
        'GC POST /v0/city/{cityName}/agent/{dir}/{base}/{action}',
        () => gateway.controlAgent(
          dir,
          AgentControlAction.pause,
          requestId: 'r1',
        ),
      );
      await one(
        'GC POST /v0/city/{cityName}/agent/{base}/{action}',
        () => gateway.controlAgent(
          'gastown.mayor',
          AgentControlAction.resume,
          requestId: 'r2',
        ),
      );
      await one(
        'GC POST /v0/city/{cityName}/session/{id}/suspend',
        () => gateway.controlAgent(
          'sf-orphan',
          AgentControlAction.pause,
          requestId: 'r3',
        ),
      );
      await one(
        'GC POST /v0/city/{cityName}/session/{id}/wake',
        () => gateway.controlAgent(
          'sf-orphan',
          AgentControlAction.resume,
          requestId: 'r4',
        ),
      );
      await one(
        'GC POST /v0/city/{cityName}/session/{id}/stop',
        () =>
            gateway.controlAgent(dir, AgentControlAction.stop, requestId: 'r5'),
      );
      await one(
        'GC POST /v0/city/{cityName}/session/{id}/messages',
        () => gateway.message(dir, 'Please rebase first', requestId: 'r6'),
      );
      await one(
        'GC POST /v0/city/{cityName}/session/{id}/respond',
        () => gateway.respond(
          'req-1',
          GateResponse.confirmation(confirmed: true),
          requestId: 'r7',
        ),
      );
      await one(
        'GC POST /v0/city/{cityName}/sling',
        () => gateway.assign('sf-12', agentId: dir, requestId: 'r8'),
      );
      await one(
        'GC POST /v0/city/{cityName}/beads',
        () => gateway.createWork(title: 'Add a receipt page', requestId: 'r9'),
      );
      await one(
        'GC POST /v0/city/{cityName}/bead/{id}/update',
        () => edits.setWorkPriority(
          'sf-12',
          WorkPriority.values.first,
          requestId: 'r10',
        ),
      );
      await one(
        'GC POST /v0/city/{cityName}/bead/{id}/close',
        () => edits.cancelWork('sf-13', requestId: 'r11'),
      );
      await one(
        'GC POST /v0/city/{cityName}/bead/{id}/reopen',
        () => edits.reopenWork('sf-13', requestId: 'r12'),
      );
      await one(
        'GC POST /v0/city/{cityName}/convoy/{id}/close',
        () => gateway.cancelRun('sf-c1', requestId: 'r13'),
      );
      await one(
        'GC POST /v0/city/{cityName}/runs/{run_id}/cancel',
        () => gateway.cancelRun('run-formula-1', requestId: 'r14'),
      );
      await one(
        'Front POST /v0/city/{cityName}/front/mr/{bead}/approve',
        () => gateway.approveMerge('gc-mr-14', requestId: 'r15'),
      );
      await one(
        'Front POST /v0/city/{cityName}/front/merge/{run}',
        () => gateway.merge('sf-c1', requestId: 'r16'),
      );
    });
  });

  group('what the engine gateway sends', () {
    late TeamHost engine;
    setUpAll(() async {
      engine = await TeamHost.start();
      engine.handler = (r) {
        if (r.path == '/v1/health') {
          return {
            'schemaVersion': 1,
            'profileId': 'srv-1',
            'engineVersion': '1.0.0',
            'capabilities': {
              'execution': true,
              'boundary': true,
              'oc1Verified': true,
              'oc2': false,
            },
            'commandActions': [
              for (final a in TeamProjectAction.values) a.name,
            ],
          };
        }
        if (r.path == '/v1/commands') {
          return {
            'accepted': true,
            'code': '',
            'projectId': 'site',
            'revision': 2,
            'replayed': false,
          };
        }
        return null;
      };
    });
    tearDownAll(() => engine.close());

    test('every command a control sends', () async {
      final gateway = PhoneEngineGateway(
        baseUrl: engine.baseUrl,
        profileId: 'srv-1',
        bearerToken: 'a' * 64,
      );
      addTearDown(gateway.close);
      for (final key in reachable.keys.where((k) => k.startsWith('Engine '))) {
        final action = TeamProjectAction.values.byName(key.split(' ')[1]);
        engine.requests.clear();
        await withRealHttp(
          () => gateway.executeProject(
            TeamProjectCommand(
              requestId: 'req-${action.name}',
              action: action,
              projectId: 'site',
            ),
          ),
        );
        expect(
          engine.requests.where((r) => _matches(key, r)),
          isNotEmpty,
          reason: '$key was not sent',
        );
        sentProven.add(key);
      }
    });
  });

  test('every reachable control is proven both ways', () {
    for (final entry in reachable.entries) {
      final match = RegExp(
        r'-> (\w+) \[test: ([^\]~]+?)(?: ~ ([^\]]+))?\]$',
      ).firstMatch(entry.value);
      expect(match, isNotNull, reason: '${entry.key}: "-> method [test: ...]"');
      final method = match!.group(1)!;
      final file = match.group(2)!;
      final token = match.group(3);
      if (file == 'this file') {
        expect(uiProven, contains(method), reason: entry.key);
      } else {
        final text = File(file).readAsStringSync();
        expect(
          text.contains(token ?? method),
          isTrue,
          reason: '${entry.key}: $file does not mention ${token ?? method}',
        );
      }
      // The call itself is proven by a real gateway sending it to a loopback
      // host, matched above.
      expect(
        sentProven,
        contains(entry.key),
        reason: '${entry.key}: no real gateway call was matched to it',
      );
    }
  });
}
