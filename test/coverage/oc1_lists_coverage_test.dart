// Coverage ratchet for what an OpenCode 1 server sends the chats, projects
// and sessions lists (see paseo_coverage_support.dart for the rules). Each
// case is the wire body of one endpoint, served by a loopback server and
// parsed by the app's real client; the screens that list it are drawn and
// their text is checked against the ledger.
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'lists_support.dart';
import 'paseo_coverage_support.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late WireServer wire;
  final fetched = <String, Map<String, Object?>>{};
  final family = CoverageFamily('oc1_lists', prefix: '');

  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
    wire = await WireServer.start();
    await withRealHttp(() async {
      final api = OpenCodeApi(baseUrl: wire.baseUrl);
      final repository = SdkProductRepository(api.sdkClient);
      for (final variant in family.cases) {
        final body = shiftTimes(variant['payload']);
        wire.handler = (r) => _route(variant, body, r);
        try {
          await _fetch(variant, body, api, repository, fetched);
        } on ProductException catch (error) {
          fail('${variant['id']}: ${error.message}: ${error.cause}');
        }
      }
    });
  });
  tearDownAll(() => wire.close());

  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('opencode 1 list · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final data = fetched[id]!;
      final boundary = GlobalKey();
      late ListsController controller;
      late ListsScreens screens;
      Future<void> start(ListsController made) async {
        controller = made;
        screens = ListsScreens(tester, controller, boundary, 'oc1-$id');
      }

      switch (variant['route']) {
        case 'session':
          final listed = (data['listed'] as List<Session>).single;
          final detail = data['detail'] as Session;
          await start(await listsController(sessions: [listed]));
          controller.lists
            ..sessions = [detail]
            ..global = [GlobalSessionResult(session: detail)];
          await screens.conversation(
            detail,
            archived: variant['archived'] == true,
          );
        case 'global':
          final page = data['page'] as ServerPage<GlobalSessionResult>;
          await start(await listsController());
          controller.lists.global = page.items;
          await screens.home();
          await screens.finder(archived: variant['archived'] == true);
        case 'status':
          final api = ListsApi()
            ..statuses = data['statuses'] as Map<String, String>
            ..retries = data['retries'] as Map<String, SessionRetryState>;
          await start(
            await listsController(
              sessions: [_plain()],
              api: api,
              repository: ListsRepository()..sessions = [_plain()],
            ),
          );
          controller.retryStates = Map.of(api.retries);
          await screens.home();
        case 'project':
          await start(await listsController(sessions: [_plain()]));
          controller.lists
            ..projects = data['projects'] as List<WorkspaceProject>
            ..sessions = [_plain()];
          await screens.home(then: screens.projectSheet);
          await screens.projects();
        case 'directories':
          await start(await listsController(sessions: [_plain()]));
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
      }
      final seen = [screens.text];
      File(
        'build/coverage/oc1list_$id.txt',
      ).writeAsStringSync(seen.map(flat).join('\n\n'));
      final problems = checkCase(family, variant, seen.join('\n'));
      expect(
        problems,
        isEmpty,
        reason: 'screen text:\n${flat(seen.join('\n'))}',
      );
    });
  }
}

/// A conversation in the shopfront folder, for cases that are not about it.
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
  OpenCodeApi api,
  SdkProductRepository repository,
  Map<String, Map<String, Object?>> fetched,
) async {
  final id = variant['id'] as String;
  switch (variant['route']) {
    case 'session':
      final sessionID = (body as Map)['id'] as String;
      fetched[id] = {
        'listed': await api.sessions(),
        'detail': await repository.getSessionDetails(sessionID),
        'children': await repository.listSessionChildren(sessionID),
      };
    case 'global':
      fetched[id] = {'page': await repository.listGlobalSessions()};
    case 'status':
      fetched[id] = {
        'statuses': await api.sessionStatuses(),
        'retries': await api.sessionRetryStates(),
      };
    case 'project':
      fetched[id] = {'projects': await repository.listProjects()};
    case 'directories':
      fetched[id] = {
        'directories': await repository.listProjectDirectories('prj_shopfront'),
      };
  }
}

Object? _route(Map variant, Object? body, WireRequest r) {
  final path = r.path;
  switch (variant['route']) {
    case 'session':
      final id = (body as Map)['id'];
      if (path == '/session') return [body];
      if (path == '/session/$id') return body;
      if (path == '/session/$id/children') return <Object>[];
    case 'global':
      if (path == '/experimental/session') return [body];
    case 'status':
      if (path == '/session/status') return {'ses_lists01': body};
    case 'project':
      if (path == '/project') return [body];
    case 'directories':
      if (path.endsWith('/directories')) return body;
  }
  return null;
}
