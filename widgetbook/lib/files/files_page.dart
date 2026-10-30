// The project's Files page (vision 2026-10-20, "just the files"): the real
// ProjectHub page over a fake OpenCode 2 server that lists a small project,
// reports Git's uncommitted changes and reads one file.
import 'package:flutter/widgets.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart'
    show api2ServerCapabilities;
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/ui/screens/project_hub_screen.dart';
import 'package:widgetbook/widgetbook.dart';

import '../catalog.dart';
import '../fakes/catalog_gateway.dart';
import '../fakes/fake_app.dart';

const _project = '/root/projects/neon-breaker';

const _gameJs = '''const paddle = { x: 0, y: 0, w: 100, h: 14 };
let score = 0;

function movePaddle(pointerX) {
  paddle.x = Math.max(50, Math.min(width - 50, pointerX));
}
''';

class _Gateway extends CatalogFakeGateway {
  _Gateway({required this.empty}) : super(capabilities: api2ServerCapabilities);

  final bool empty;

  @override
  Future<List<FileNode>> listFiles([String path = '']) async {
    if (empty) return [];
    if (path == 'src') {
      return [
        for (final name in ['game.js', 'input.js', 'levels.js'])
          FileNode(name: name, path: 'src/$name', isDir: false),
      ];
    }
    return [
      FileNode(name: 'assets', path: 'assets', isDir: true),
      FileNode(name: 'src', path: 'src', isDir: true),
      for (final name in ['index.html', 'package.json', 'README.md'])
        FileNode(name: name, path: name, isDir: false),
    ];
  }

  @override
  Future<List<String>> findFile(String query) async => [
    for (final path in ['src/game.js', 'src/levels.js'])
      if (path.contains(query)) path,
  ];

  @override
  Future<FileContent> fileContent(String path) async =>
      const FileContent(_gameJs, mimeType: 'text/javascript');
}

class _Repository implements ProductRepository {
  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<VersionControlFile>> listFileStatuses() async => const [
    VersionControlFile(
      path: 'src/game.js',
      status: 'modified',
      additions: 30,
      deletions: 5,
    ),
    VersionControlFile(
      path: 'index.html',
      status: 'modified',
      additions: 4,
      deletions: 1,
    ),
  ];

  @override
  Future<List<TerminalProcess>> listTerminals() async => const [];

  @override
  Future<List<WorkspaceProject>> listProjects() async => const [];

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

enum _Scene { files, folder, reader, empty, noProject }

Widget _scene(_Scene scene) => FakeAppScene<FakeApp>(
  platform: FakePlatform.android,
  autoTap: switch (scene) {
    _Scene.folder => 'project-file-src',
    _Scene.reader => 'project-file-index.html',
    _ => null,
  },
  create: () async {
    final app = await FakeApp.create(
      connected: true,
      api: _Gateway(empty: scene == _Scene.empty),
      repository: _Repository(),
    );
    app.connection.directory = scene == _Scene.noProject ? null : _project;
    return app;
  },
  builder: (context, app) => ProjectHub(controller: app.connection, page: true),
);

CatalogComponent filesPage() => catalogComponent(
  area: 'Project',
  screen: 'ProjectHub',
  name: 'Files',
  useCases: [
    WidgetbookUseCase(name: 'Files', builder: (_) => _scene(_Scene.files)),
    WidgetbookUseCase(
      name: 'Inside a folder',
      builder: (_) => _scene(_Scene.folder),
    ),
    WidgetbookUseCase(name: 'Reader', builder: (_) => _scene(_Scene.reader)),
    WidgetbookUseCase(
      name: 'Empty folder',
      builder: (_) => _scene(_Scene.empty),
    ),
    WidgetbookUseCase(
      name: 'No project',
      builder: (_) => _scene(_Scene.noProject),
    ),
  ],
);
