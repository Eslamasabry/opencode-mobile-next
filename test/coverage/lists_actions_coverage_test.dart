// Coverage ratchet for what the chats, projects and sessions lists CHANGE
// (gate 2). tool/coverage/lists_actions.mjs lists every call in this area
// that changes something, from the OpenCode 1 and 2 contracts and Paseo's
// protocol; lists_actions_ledger.json says for each one:
//   reachable: <control> -> <gateway method> [test: <file> ~ <token>]
//   covered elsewhere: <where>
//   not offered: <reason>
// A reachable entry is proven in two links: a test taps the control and sees
// the gateway method called (this file, or the named existing test), and a
// real gateway sends that method to the server over a loopback socket, where
// the call it makes is matched to the contract's operation.
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api2/client.dart';
import 'package:opencode_mobile/api2/gateway.dart';
import 'package:opencode_mobile/api2/gateway_operations.dart';
import 'package:opencode_mobile/ui/screens/chats/chats_home_screen.dart';
import 'package:opencode_mobile/ui/screens/global_sessions_screen.dart';
import 'package:opencode_mobile/ui/screens/projects_screen.dart';

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
                  'test/fixtures/coverage/lists_actions_samples.json',
                ).readAsStringSync(),
              )
              as Map)['operations']
          as List;
  final keys = [for (final o in inventory) (o as Map)['key'] as String];
  final ledger =
      (jsonDecode(
                File(
                  'test/fixtures/coverage/lists_actions_ledger.json',
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
      reason: 'calls with no entry in lists_actions_ledger.json',
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

  group('the controls', () {
    testWidgets('New conversation starts a conversation', (tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final api = ListsApi();
      final controller = await listsController(sessions: [_plain()], api: api);
      controller.lists
        ..sessions = [_plain()]
        ..projects = const [
          WorkspaceProject(
            id: 'prj_shopfront',
            name: 'Shopfront',
            directory: '/work/shopfront',
            worktrees: [],
            updatedAt: 1,
          ),
        ];
      await tester.runAsync(controller.refreshChatFeed);
      await tester.runAsync(
        () => controller.rememberLastUsedProject('/work/shopfront'),
      );
      await tester.pumpWidget(
        listsApp(
          controller,
          const ChatsHomeScreen(),
          routes: {'/chat/ses_new_1': (_) => const Text('chat opened')},
        ),
      );
      await settle(tester);
      await tester.tap(find.byKey(const ValueKey('chats-new-chat')));
      await settle(tester);
      await tester.enterText(
        find.byKey(const ValueKey('chats-new-field')),
        'Round the cart total',
      );
      await tester.pump();
      await tester.tap(find.byKey(const ValueKey('chats-new-send')));
      await settle(tester, rounds: 10);
      expect(api.createdSessions, 1);
      uiProven.add('createSession');
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(seconds: 3));
      controller.dispose();
      await tester.pump(const Duration(seconds: 3));
    });

    testWidgets('a project row menu renames the project', (tester) async {
      final controller = await listsController();
      controller.lists.projects = const [
        WorkspaceProject(
          id: 'prj_shopfront',
          name: 'Shopfront',
          directory: '/work/shopfront',
          worktrees: [],
          updatedAt: 1,
        ),
      ];
      await tester.pumpWidget(
        listsApp(
          controller,
          ProjectsScreen(controller: controller, selectedProjectID: null),
        ),
      );
      await settle(tester);
      await tester.longPress(
        find.byKey(const ValueKey('project-prj_shopfront')),
      );
      await settle(tester);
      await tester.tap(
        find.byKey(const ValueKey('rename-project-prj_shopfront')),
      );
      await settle(tester);
      await tester.enterText(
        find.byKey(const ValueKey('project-name-input')),
        'Shopfront web',
      );
      await tester.tap(find.byKey(const ValueKey('confirm-rename-project')));
      await settle(tester);
      expect(controller.lists.calls, contains('renameProject'));
      uiProven.add('renameProject');
    });

    testWidgets('a row elsewhere offers Continue here', (tester) async {
      final controller = await listsController();
      controller.directory = '/work/active';
      controller.lists.global = [
        GlobalSessionResult(
          session: Session(
            id: 'ses_2',
            title: 'Review the checkout',
            directory: '/work/other',
            workspaceID: 'wrk_remote',
            time: SessionTime(
              created: DateTime.now().millisecondsSinceEpoch - 7200000,
              updated: DateTime.now().millisecondsSinceEpoch - 3600000,
            ),
          ),
          projectName: 'Other',
        ),
      ];
      await tester.pumpWidget(
        listsApp(
          controller,
          GlobalSessionsScreen(controller: controller),
          routes: {'/chat/ses_2': (_) => const Text('stolen chat opened')},
        ),
      );
      await settle(tester);
      await tester.longPress(
        find.byKey(const ValueKey('global-session-ses_2')),
      );
      await settle(tester);
      await tester.tap(find.byKey(const ValueKey('steal-session-ses_2')));
      await settle(tester);
      await tester.tap(
        find.byKey(const ValueKey('global-sessions-move-confirm')),
      );
      await settle(tester);
      expect(controller.lists.calls, contains('stealSessionIntoWorkspace'));
      uiProven.add('stealSessionIntoWorkspace');
    });
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
      await one('OC1 POST /session', api.createSession);
      await one(
        'OC1 PATCH /project/{projectID}',
        () => repository.renameProject(
          projectID: 'prj_shopfront',
          projectDirectory: '/work/shopfront',
          name: 'Shopfront web',
        ),
      );
      await one(
        'OC1 POST /session/{sessionID}/abort',
        () => api.abort('ses_1'),
      );
      await one(
        'OC1 POST /experimental/session/{sessionID}/background',
        () => repository.backgroundSession('ses_1'),
      );
      await one(
        'OC1 POST /project/git/init',
        repository.initializeGitRepository,
      );
      await one(
        'OC1 POST /experimental/control-plane/move-session',
        () => repository.moveSession(
          'ses_1',
          directory: '/work/shopfront-review',
          moveChanges: false,
        ),
      );
      await one(
        'OC1 POST /experimental/workspace/warp',
        () => repository.warpSession(
          'ses_1',
          workspaceID: 'wrk_review',
          copyChanges: false,
        ),
      );
      await one(
        'OC1 POST /sync/steal',
        () => repository.stealSessionIntoWorkspace('ses_1'),
      );
      api.close();
    });

    test('OpenCode 2', () async {
      final client = Api2Client.connect(
        baseUrl: wire.baseUrl,
        password: 'fixture',
      );
      final gateway = Api2Gateway(client: client);
      final repository = Api2OperationsGateway(client: client);
      Future<void> one(String key, Future<Object?> Function() call) =>
          sends(key, call, protocol: 'OC2');
      await one('OC2 POST /api/session', gateway.createSession);
      await one(
        'OC2 PATCH /api/project/{projectID}',
        () => repository.renameProject(
          projectID: 'prj_shopfront',
          projectDirectory: '/work/shopfront',
          name: 'Shopfront web',
        ),
      );
      await one(
        'OC2 POST /api/session/{sessionID}/interrupt',
        () => gateway.abort('ses_1'),
      );
      await one(
        'OC2 POST /api/session/{sessionID}/background',
        () => repository.backgroundSession('ses_1'),
      );
      await one(
        'OC2 POST /api/session/{sessionID}/move',
        () => repository.moveSession(
          'ses_1',
          directory: '/work/shopfront-review',
          moveChanges: false,
        ),
      );
      await one(
        'OC2 PUT /api/session/{sessionID}/instructions/entries/{key}',
        () => repository.saveSessionNote('ses_1', 'Rerun the cart tests'),
      );
      await one(
        'OC2 DELETE /api/session/{sessionID}/instructions/entries/{key}',
        () => repository.removeSessionNote('ses_1'),
      );
      final file = jsonDecode(
        File(
          'test/fixtures/coverage/oc2_lists_samples.json',
        ).readAsStringSync(),
      );
      final transfer = (file['cases'] as List).firstWhere(
        (c) => (c as Map)['id'] == 'transfer_file',
      );
      await one('OC2 POST /api/session/import', () async {
        final document = SessionImportDocument.fromJson(
          (transfer as Map)['payload'],
        );
        return repository.importSession(
          document,
          const SessionImportDestination(directory: '/work/shopfront'),
        );
      });
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
      expect(
        sentProven,
        contains(entry.key),
        reason: '${entry.key}: no real gateway call was matched to it',
      );
    }
  });
}

Session _plain() => Session(
  id: 'ses_lists01',
  title: 'Fix the cart total rounding',
  projectID: 'prj_shopfront',
  directory: '/work/shopfront',
  time: SessionTime(
    created: DateTime.now().millisecondsSinceEpoch - 7200000,
    updated: DateTime.now().millisecondsSinceEpoch - 3600000,
  ),
);

/// Plausible answers, so the gateways get past parsing; the test reads what
/// was sent, not what came back.
Object? _canned(String protocol, WireRequest r) {
  final path = r.path.startsWith('/api') ? r.path.substring(4) : r.path;
  if (path.endsWith('/background') ||
      path.endsWith('/abort') ||
      path.endsWith('/interrupt')) {
    return null;
  }
  if (path == '/session' && r.method == 'POST') {
    return protocol == 'OC1'
        ? {
            'id': 'ses_new',
            'slug': 'new-one',
            'projectID': 'prj_shopfront',
            'directory': '/work/shopfront',
            'title': 'New conversation',
            'version': '1.4.2',
            'time': {'created': 1, 'updated': 1},
          }
        : {
            'data': {
              'id': 'ses_new',
              'projectID': 'prj_shopfront',
              'cost': 0,
              'tokens': {
                'input': 0,
                'output': 0,
                'reasoning': 0,
                'cache': {'read': 0, 'write': 0},
              },
              'time': {'created': 1, 'updated': 1},
              'location': {'directory': '/work/shopfront'},
            },
          };
  }
  if (path.startsWith('/project/') && r.method == 'PATCH') {
    return {
      'id': 'prj_shopfront',
      'worktree': '/work/shopfront',
      'name': 'Shopfront web',
      'time': {'created': 1, 'updated': 1},
      'sandboxes': <String>[],
      'canonical': '/work/shopfront',
    };
  }
  if (path == '/sync/steal') return {'sessionID': 'ses_1'};
  return null;
}
