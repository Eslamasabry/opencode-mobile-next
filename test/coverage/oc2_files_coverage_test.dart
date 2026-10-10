// Coverage ratchet for what an OpenCode 2 server sends the Files page, the
// Changes review and the worktree page (see paseo_coverage_support.dart for
// the rules). Each case is the wire body of the endpoints, served by a
// loopback server and parsed by the app's real OpenCode 2 gateways.
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

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'files_support.dart';
import 'lists_support.dart';
import 'paseo_coverage_support.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late WireServer wire;
  final fetched = <String, Map<String, Object?>>{};
  final family = CoverageFamily('oc2_files', prefix: '');
  const location = {
    'directory': '/work/shopfront',
    'project': {
      'id': 'prj_shopfront',
      'directory': '/work/shopfront',
      'canonical': '/work/shopfront',
    },
  };

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
        final body = variant['payload'];
        wire.handler = (r) => _route(variant, body, r, location);
        try {
          final id = variant['id'] as String;
          fetched[id] = switch (variant['kind']) {
            'fs' => {'nodes': await gateway.listFiles('lib')},
            'changes' => {
              'health': await repository.loadVersionControlHealth(),
              'status': await repository.listFileStatuses(),
              'working': await repository.listVcsDiffs(VcsDiffMode.workingTree),
            },
            'undo' => {
              'revert': await repository.stageSessionRevert(
                'ses_files01',
                'msg_files_r1',
                applyFiles: true,
              ),
            },
            _ => <String, Object?>{},
          };
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
    testWidgets('opencode 2 files · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final data = fetched[id]!;
      final api = FilesApi(api2ServerCapabilities);
      final repository = FilesRepository();
      final controller = await listsController(
        api: api,
        repository: repository,
      );
      final screens = ListsScreens(
        tester,
        controller,
        GlobalKey(),
        'oc2-files-$id',
      );
      switch (variant['kind']) {
        case 'fs':
          api.nodes = data['nodes'] as List<FileNode>;
          await screens.filesPage();
        case 'changes':
          api.nodes = [FileNode(name: 'lib', path: 'lib', isDir: true)];
          repository
            ..statuses = data['status'] as List<VersionControlFile>
            ..health = data['health'] as VersionControlHealth
            ..workingDiffs = data['working'] as List<FileDiff>;
          await screens.filesPage();
          await screens.healthPage();
          await screens.review(working: data['working'] as List<FileDiff>);
        case 'undo':
          final revert = data['revert'] as SessionRevert;
          final session = Session(
            id: 'ses_files01',
            title: 'Fix the cart total rounding',
            directory: '/work/shopfront',
            stagedRevert: revert,
            reverted: true,
          );
          controller.sessionsById[session.id] = session;
          repository.staged = revert;
          await screens.undoPage(session.id);
          // Both ways out ask first; what they ask is read too.
          for (final action in [
            'Delete the hidden messages',
            'Put everything back',
          ]) {
            await tapText(tester, action);
            screens.seen.add(readText(tester));
            await tester.tapAt(const Offset(4, 4));
            await settle(tester);
          }
          // A dangerous way out never asks without naming the conversation.
          expect(
            flat(screens.text),
            contains('Delete messages in “Fix the cart total rounding”?'),
          );
          expect(
            flat(screens.text),
            contains('Restore “Fix the cart total rounding”?'),
          );
        case 'worktree':
          // Creating a worktree is not offered on OpenCode 2 (the server asks
          // for a strategy and a folder the app has no way to choose), so the
          // page lists worktrees only and the created folder never shows.
          await screens.worktreesPage();
      }
      File(
        'build/coverage/oc2files_$id.txt',
      ).writeAsStringSync(flat(screens.text));
      final problems = checkCase(family, variant, screens.text);
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(seconds: 3));
      controller.dispose();
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screens.text)}');
    });
  }
}

Object? _route(Map variant, Object? body, WireRequest r, Map location) {
  final path = r.path.startsWith('/api') ? r.path.substring(4) : r.path;
  final b = body is Map ? body : const {};
  switch (variant['kind']) {
    case 'fs':
      if (path == '/fs/list') return {'location': location, 'data': body};
    case 'changes':
      if (path == '/vcs') return {'location': location, 'data': b['info']};
      if (path == '/vcs/status') {
        return {'location': location, 'data': b['status']};
      }
      if (path == '/vcs/diff') return {'location': location, 'data': b['diff']};
    case 'undo':
      if (path.endsWith('/revert/stage')) return {'data': body};
    case 'worktree':
      if (path == '/location') return location;
      if (path.startsWith('/worktree/')) return body;
  }
  return null;
}
