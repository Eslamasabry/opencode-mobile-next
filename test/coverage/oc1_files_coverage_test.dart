// Coverage ratchet for what an OpenCode 1 server sends the Files page, the
// Changes review, and the worktree and workspace pages. Each case is the wire
// body of the endpoints, served by a loopback server and parsed by the app's
// real OpenCode 1 client; the screens are drawn from what it parsed (see
// paseo_coverage_support.dart for the rules).
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'files_support.dart';
import 'lists_support.dart';
import 'paseo_coverage_support.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late WireServer wire;
  final fetched = <String, Map<String, Object?>>{};
  final family = CoverageFamily('oc1_files', prefix: '');

  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
    wire = await WireServer.start();
    await withRealHttp(() async {
      final api = OpenCodeApi(baseUrl: wire.baseUrl);
      final repository = SdkProductRepository(api.sdkClient);
      for (final variant in family.cases) {
        final body = variant['payload'];
        wire.handler = (r) => _route(variant, body, r);
        try {
          fetched[variant['id'] as String] = await _fetch(
            variant,
            api,
            repository,
          );
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
    testWidgets('opencode 1 files · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final data = fetched[id]!;
      final api = FilesApi();
      final repository = FilesRepository();
      final controller = await listsController(
        api: api,
        repository: repository,
      );
      final screens = ListsScreens(
        tester,
        controller,
        GlobalKey(),
        'oc1-files-$id',
      );
      switch (variant['kind']) {
        case 'tree':
          api.nodes = data['nodes'] as List<FileNode>;
          await screens.filesPage();
        case 'file':
          final path = variant['path'] as String;
          api.nodes = [
            FileNode(name: path.split('/').last, path: path, isDir: false),
          ];
          api.contents = {path: data['content'] as FileContent};
          await screens.filesPage(
            then: () async {
              await tester.tap(find.byKey(ValueKey('project-file-$path')));
            },
          );
        case 'find':
          api.nodes = [FileNode(name: 'lib', path: 'lib', isDir: true)];
          api.found = data['files'] as List<String>;
          repository.symbols = data['symbols'] as List<WorkspaceSymbol>;
          await screens.filesPage(
            then: () async {
              await tester.enterText(
                find.byKey(const ValueKey('files-search-field')),
                'cart',
              );
              await tester.pump(const Duration(seconds: 1));
            },
          );
          await tester.tap(find.byKey(const ValueKey('file-surface-selector')));
          await settle(tester);
          await tapText(tester, 'Symbols');
          await tester.enterText(
            find.byKey(const ValueKey('files-search-field')),
            'Cart',
          );
          await tester.pump(const Duration(seconds: 1));
          await settle(tester);
          screens.seen.add(readText(tester));
        case 'changes':
          api.nodes = [FileNode(name: 'lib', path: 'lib', isDir: true)];
          repository
            ..statuses = data['status'] as List<VersionControlFile>
            ..health = data['health'] as VersionControlHealth
            ..workingDiffs = data['working'] as List<FileDiff>;
          await screens.filesPage();
          await screens.healthPage();
          await screens.review(
            session: data['session'] as List<FileDiff>,
            working: data['working'] as List<FileDiff>,
          );
          await tapText(tester, 'Uncommitted');
          screens.seen.add(readText(tester));
        case 'worktrees':
          repository
            ..worktrees = data['worktrees'] as List<WorktreeInfo>
            ..created = data['created'] as WorktreeInfo;
          await screens.worktreesPage(
            then: () async {
              await tapText(tester, 'New worktree');
              await tester.enterText(
                find.byKey(const ValueKey('worktree-name-field')),
                'shopfront-retry',
              );
              await tester.tap(
                find.byKey(const ValueKey('confirm-create-worktree')),
              );
            },
          );
        case 'workspaces':
          repository
            ..managed = data['managed'] as List<WorkspaceInfo>
            ..adapters = data['adapters'] as List<WorkspaceAdapterInfo>;
          await screens.workspacesPage();
      }
      File(
        'build/coverage/oc1files_$id.txt',
      ).writeAsStringSync(flat(screens.text));
      final problems = checkCase(family, variant, screens.text);
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(seconds: 3));
      controller.dispose();
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screens.text)}');
    });
  }
}

Future<Map<String, Object?>> _fetch(
  Map variant,
  OpenCodeApi api,
  SdkProductRepository repository,
) async {
  switch (variant['kind']) {
    case 'tree':
      return {'nodes': await api.listFiles('lib')};
    case 'file':
      return {'content': await api.fileContent(variant['path'] as String)};
    case 'find':
      return {
        'files': await api.findFile('cart'),
        'text': await api.findText('round'),
        'symbols': await repository.findWorkspaceSymbols('Cart'),
      };
    case 'changes':
      return {
        'health': await repository.loadVersionControlHealth(),
        'status': await repository.listFileStatuses(),
        'working': await repository.listVcsDiffs(VcsDiffMode.workingTree),
        'session': await api.diff('ses_files01'),
      };
    case 'worktrees':
      return {
        'worktrees': await repository.listWorktrees(
          projectDirectory: '/work/shopfront',
        ),
        'created': await repository.createWorktree(
          projectDirectory: '/work/shopfront',
          name: 'shopfront-retry',
        ),
      };
    case 'workspaces':
      return {
        'managed': await repository.listManagedWorkspaces(
          projectDirectory: '/work/shopfront',
        ),
        'adapters': await repository.listWorkspaceAdapters(
          projectDirectory: '/work/shopfront',
        ),
      };
  }
  return {};
}

Object? _route(Map variant, Object? body, WireRequest r) {
  final b = body is Map ? body : const {};
  final path = r.path;
  switch (variant['kind']) {
    case 'tree':
      if (path == '/file') return body;
    case 'file':
      if (path == '/file/content') return body;
    case 'find':
      if (path == '/find/file') return b['files'];
      if (path == '/find') return b['text'];
      if (path == '/find/symbol') return b['symbols'];
    case 'changes':
      if (path == '/vcs') return b['info'];
      if (path == '/vcs/status') return b['status'];
      if (path == '/vcs/diff') return b['working'];
      if (path.endsWith('/diff')) return b['session'];
      if (path == '/project/current') return null;
    case 'worktrees':
      if (path == '/experimental/worktree') {
        return r.method == 'GET' ? b['list'] : b['created'];
      }
    case 'workspaces':
      if (path == '/experimental/workspace') return b['list'];
      if (path == '/experimental/workspace/status') return b['statuses'];
      if (path == '/experimental/workspace/adapter') return b['adapters'];
  }
  return null;
}
