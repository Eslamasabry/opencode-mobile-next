// Coverage ratchet for what files, changes, review, worktrees and undo CHANGE
// (gate 2). tool/coverage/files_actions.mjs lists every call in this area
// that changes something, from the OpenCode 1 and 2 contracts and Paseo's
// protocol; files_actions_ledger.json says for each one:
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

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
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
                  'test/fixtures/coverage/files_actions_samples.json',
                ).readAsStringSync(),
              )
              as Map)['operations']
          as List;
  final keys = [for (final o in inventory) (o as Map)['key'] as String];
  final ledger =
      (jsonDecode(
                File(
                  'test/fixtures/coverage/files_actions_ledger.json',
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

  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });

  test('every change in this area has a ledger decision', () {
    expect(
      keys.toSet().difference(ledger.keys.toSet()),
      isEmpty,
      reason: 'calls with no entry in files_actions_ledger.json',
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
        'OC1 POST /experimental/worktree',
        () => repository.createWorktree(
          projectDirectory: '/work/shopfront',
          name: 'shopfront-retry',
        ),
      );
      await one(
        'OC1 DELETE /experimental/worktree',
        () => repository.removeWorktree(
          projectDirectory: '/work/shopfront',
          directory: '/work/shopfront-retry',
        ),
      );
      await one(
        'OC1 POST /experimental/worktree/reset',
        () => repository.resetWorktree(
          projectDirectory: '/work/shopfront',
          directory: '/work/shopfront-retry',
        ),
      );
      await one(
        'OC1 POST /session/{sessionID}/revert',
        () => repository.revertSession('ses_1', 'msg_1'),
      );
      await one(
        'OC1 POST /session/{sessionID}/unrevert',
        () => repository.restoreSession('ses_1'),
      );
      await one(
        'OC1 POST /experimental/workspace',
        () => repository.createManagedWorkspace(
          projectDirectory: '/work/shopfront',
          type: 'worktree',
        ),
      );
      await one(
        'OC1 POST /experimental/workspace/sync-list',
        () => repository.syncWorkspaceList(projectDirectory: '/work/shopfront'),
      );
      await one(
        'OC1 DELETE /experimental/workspace/{id}',
        () => repository.removeManagedWorkspace(
          projectDirectory: '/work/shopfront',
          id: 'wrk_review',
        ),
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
        'OC2 POST /api/session/{sessionID}/revert/stage',
        () => repository.stageSessionRevert('ses_1', 'msg_1', applyFiles: true),
      );
      await one(
        'OC2 POST /api/session/{sessionID}/revert/clear',
        () => repository.clearSessionRevert('ses_1'),
      );
      await one(
        'OC2 POST /api/session/{sessionID}/revert/commit',
        () => repository.commitSessionRevert('ses_1'),
      );
      await one(
        'OC2 DELETE /api/worktree/{projectID}',
        () => repository.removeWorktree(
          projectDirectory: '/work/shopfront',
          directory: '/work/shopfront-retry',
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
Object? _canned(String protocol, WireRequest r) {
  final path = r.path.startsWith('/api') ? r.path.substring(4) : r.path;
  if (path == '/location') {
    return {
      'directory': '/work/shopfront',
      'project': {
        'id': 'prj_shopfront',
        'directory': '/work/shopfront',
        'canonical': '/work/shopfront',
      },
    };
  }
  return null;
}
