// Coverage ratchet for what an OpenCode 2 server sends the chats, projects
// and sessions lists (see paseo_coverage_support.dart for the rules). Each
// case is the wire body of one endpoint, served by a loopback server and
// parsed by the app's real OpenCode 2 gateways; the screens that list it are
// drawn and their text is checked against the ledger.
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api2/client.dart';
import 'package:opencode_mobile/api2/gateway.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart'
    show api2ServerCapabilities;
import 'package:opencode_mobile/api2/gateway_operations.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/screens/session_import_screen.dart';
import 'package:opencode_mobile/ui/screens/session_note_screen.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'lists_support.dart';
import 'paseo_coverage_support.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late WireServer wire;
  final fetched = <String, Map<String, Object?>>{};
  final family = CoverageFamily('oc2_lists', prefix: '');

  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
    wire = await WireServer.start();
    await withRealHttp(() async {
      final client = Api2Client.connect(
        baseUrl: wire.baseUrl,
        password: 'fixture',
      );
      final gateway = Api2Gateway(client: client);
      final repository = Api2OperationsGateway(client: client);
      for (final variant in family.cases) {
        final body = shiftTimes(variant['payload']);
        wire.handler = (r) => _route(variant, body, r);
        try {
          await _fetch(variant, body, gateway, repository, fetched, wire);
        } on ProductException catch (error) {
          fail('${variant['id']}: ${error.message}: ${error.cause}');
        }
      }
      client.close();
    });
  });
  tearDownAll(() => wire.close());

  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('opencode 2 list · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final data = fetched[id] ?? const <String, Object?>{};
      final boundary = GlobalKey();
      late ListsController controller;
      late ListsScreens screens;
      Future<void> start(ListsController made) async {
        controller = made;
        screens = ListsScreens(tester, controller, boundary, 'oc2-$id');
      }

      switch (variant['route']) {
        case 'session':
          final listed = (data['listed'] as List<Session>).single;
          final detail = data['detail'] as Session;
          await start(
            await listsController(
              sessions: [listed],
              api: ListsApi(api2ServerCapabilities)..sessionsById.clear(),
            ),
          );
          controller.lists
            ..sessions = [detail]
            ..global = [GlobalSessionResult(session: detail)];
          await screens.conversation(
            detail,
            archived: variant['archived'] == true,
          );
        case 'page':
          final page = data['page'] as ServerPage<GlobalSessionResult>;
          expect(page.nextCursor, 'cur_next_b2');
          await start(
            await listsController(api: ListsApi(api2ServerCapabilities)),
          );
          controller.lists.global = page.items;
          await screens.home();
          await screens.finder();
        case 'active':
          final api = ListsApi(api2ServerCapabilities)
            ..statuses = data['statuses'] as Map<String, String>;
          await start(await listsController(sessions: [_plain()], api: api));
          await screens.home();
          expect(screens.text, contains('Running'));
        case 'project':
          await start(
            await listsController(
              sessions: [_plain()],
              api: ListsApi(api2ServerCapabilities),
            ),
          );
          controller.lists
            ..projects = data['projects'] as List<WorkspaceProject>
            ..sessions = [_plain()];
          await screens.home(then: screens.projectSheet);
          await screens.projects();
        case 'worktrees':
          await start(
            await listsController(
              sessions: [_plain()],
              api: ListsApi(api2ServerCapabilities),
            ),
          );
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
            ]
            ..directories = data['directories'] as List<ProjectDirectoryInfo>;
          await screens.destination('ses_lists01');
        case 'notes':
          await start(
            await listsController(
              sessions: [_plain()],
              api: ListsApi(api2ServerCapabilities),
            ),
          );
          controller.lists.note = data['note'] as String?;
          await screens.show(
            SessionNoteScreen(controller: controller, sessionID: 'ses_lists01'),
          );
          expect(
            screens.text,
            isNot(contains('Prefer tabs in generated code')),
            reason: 'another tool\'s instruction entry is never shown',
          );
        case 'transfer':
          await start(
            await listsController(
              sessions: [_plain()],
              api: ListsApi(api2ServerCapabilities),
            ),
          );
          final bytes = utf8.encode(jsonEncode(variant['payload']));
          await screens.show(
            SessionImportScreen(
              controller: controller,
              pickFile: () async => SessionImportFile(
                name: 'cart-total.json',
                length: () async => bytes.length,
                read: () => Stream.value(bytes),
              ),
            ),
            then: () async {
              await tester.tap(find.text('Choose JSON file'));
            },
          );
      }
      File(
        'build/coverage/oc2list_$id.txt',
      ).writeAsStringSync(flat(screens.text));
      final problems = checkCase(family, variant, screens.text);
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screens.text)}');
    });
  }
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

Future<void> _fetch(
  Map variant,
  Object? body,
  Api2Gateway gateway,
  Api2OperationsGateway repository,
  Map<String, Map<String, Object?>> fetched,
  WireServer wire,
) async {
  final id = variant['id'] as String;
  switch (variant['route']) {
    case 'session':
      final sessionID = (body as Map)['id'] as String;
      fetched[id] = {
        'listed': await gateway.sessions(),
        'detail': await repository.getSessionDetails(sessionID),
      };
    case 'page':
      fetched[id] = {'page': await repository.listGlobalSessions(limit: 1)};
      // The next page is asked for with the cursor the server gave.
      await repository.listGlobalSessions(limit: 1, cursor: 'cur_next_b2');
      expect(
        wire.requests.last.uri.queryParameters['cursor'],
        'cur_next_b2',
        reason: 'older conversations load with the server\'s own cursor',
      );
    case 'active':
      fetched[id] = {'statuses': await gateway.sessionStatuses()};
    case 'project':
      fetched[id] = {'projects': await repository.listProjects()};
    case 'worktrees':
      fetched[id] = {
        'directories': await repository.listProjectDirectories('prj_shopfront'),
      };
    case 'notes':
      fetched[id] = {'note': await repository.loadSessionNote('ses_lists01')};
  }
}

Object? _route(Map variant, Object? body, WireRequest r) {
  final path = r.path.startsWith('/api') ? r.path.substring(4) : r.path;
  switch (variant['route']) {
    case 'session':
      final id = (body as Map)['id'];
      if (path == '/session') {
        return {
          'data': [body],
          'cursor': {},
        };
      }
      if (path == '/session/$id') return {'data': body};
    case 'page':
      if (path == '/session') return body;
    case 'active':
      if (path == '/session/active') {
        return {
          'data': {'ses_lists01': body},
        };
      }
    case 'project':
      if (path == '/project') return [body];
    case 'worktrees':
      if (path.startsWith('/worktree/')) return body;
    case 'notes':
      if (path.endsWith('/instructions/entries')) return body;
  }
  return null;
}
