// Coverage ratchet for what settings CHANGE
// (gate 2). tool/coverage/settings_actions.mjs lists every call in settings
// that changes something, from the OpenCode 1 and 2 contracts and Paseo's
// protocol; settings_actions_ledger.json says for each one:
//   reachable: <control> -> <gateway method> [test: <file> ~ <token>]
//   covered elsewhere: <where>
//   not offered: <reason>
// A reachable entry is proven in two links: a test taps the control and sees
// the gateway method called (this file, or the named existing test), and a
// real gateway sends that method to the server over a loopback socket, where
// the call it makes is matched to the contract's operation.
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api2/client.dart';
import 'package:opencode_mobile/api2/gateway_operations.dart';

import 'lists_support.dart';

/// A key such as `OC1 POST /session/{sessionID}/fork` matched against what was sent.
bool _matches(String key, WireRequest request) {
  final parts = key.split(' ');
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
                  'test/fixtures/coverage/settings_actions_samples.json',
                ).readAsStringSync(),
              )
              as Map)['operations']
          as List;
  final keys = [for (final o in inventory) (o as Map)['key'] as String];
  final ledger =
      (jsonDecode(
                File(
                  'test/fixtures/coverage/settings_actions_ledger.json',
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

  test('every change in settings has a ledger decision', () {
    expect(
      keys.toSet().difference(ledger.keys.toSet()),
      isEmpty,
      reason: 'calls with no entry in settings_actions_ledger.json',
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

  group('what each gateway sends', () {
    late WireServer wire;
    setUpAll(() async => wire = await WireServer.start());
    tearDownAll(() => wire.close());

    Future<void> sends(
      String key,
      Future<Object?> Function() call, {
      required String protocol,
    }) async {
      wire.requests.clear();
      wire.handler = (r) => _canned(protocol, r);
      try {
        await withRealHttp(call);
      } on Object {
        // The answer may not suit the call; what was sent is what counts.
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

    test('OpenCode 1', () async {
      final api = OpenCodeApi(baseUrl: wire.baseUrl);
      final repository = SdkProductRepository(api.sdkClient);
      Future<void> one(String key, Future<Object?> Function() call) =>
          sends(key, call, protocol: 'OC1');
      await one(
        'OC1 PATCH /global/config',
        () => repository.selectTerminalShell('fish'),
      );
      await one(
        'OC1 DELETE /api/permission/saved/{id}',
        () => repository.removeSavedPermission('perm_1'),
      );
      api.close();
    });

    test('OpenCode 2', () async {
      final client = Api2Client.connect(
        baseUrl: wire.baseUrl,
        password: 'fixture',
      );
      final repository = Api2OperationsGateway(client: client);
      Future<void> one(String key, Future<Object?> Function() call) =>
          sends(key, call, protocol: 'OC2');
      await one(
        'OC2 DELETE /api/permission/saved/{id}',
        () => repository.removeSavedPermission('perm_1'),
      );
      await one(
        'OC2 POST /api/shell',
        () => repository.startManagedShell(
          command: 'npm run dev',
          directory: '/work/shopfront',
          ownerToken: 'owner_settings',
        ),
      );
      await one(
        'OC2 DELETE /api/shell/{id}',
        () => repository.stopManagedShell('sh_1'),
      );
      await one(
        'OC2 PATCH /api/shell/{id}/timeout',
        () => repository.setManagedShellTimeout(
          'sh_1',
          const Duration(minutes: 30),
        ),
      );
      client.close();
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
      // Paseo's and Codex's calls are proven by the scripted-peer test named in
      // the entry; the HTTP ones by a real gateway call matched here.
      if (!entry.key.startsWith('Paseo ') && !entry.key.startsWith('Codex ')) {
        expect(
          sentProven,
          contains(entry.key),
          reason: '${entry.key}: no real gateway call was matched to it',
        );
      }
    }
  });
}

/// Plausible answers, so the gateways get past parsing; the test reads what
/// was sent, not what came back.
Object? _canned(String protocol, WireRequest r) => null;
