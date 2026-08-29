import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/main.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/files_screen.dart';
import 'package:opencode_mobile/ui/screens/library_screen.dart';
import 'package:opencode_mobile/ui/screens/requests_screen.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/ui/screens/terminal_screen.dart';
import 'package:opencode_mobile/ui/widgets/file_preview.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:xterm/xterm.dart';

class _TestApi extends OpenCodeApi {
  _TestApi({this.files, this.findFiles, this.contents = const {}})
    : super(baseUrl: 'http://localhost');

  final Future<List<FileNode>> Function(String path)? files;
  final Future<List<String>> Function(String query)? findFiles;
  final Map<String, FileContent> contents;

  @override
  Future<List<FileNode>> listFiles([String path = '']) async =>
      files?.call(path) ?? const [];

  @override
  Future<List<String>> findFile(String query) async =>
      findFiles?.call(query) ?? const [];

  @override
  Future<FileContent> fileContent(String path) async => contents[path]!;

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};
}

class _LocationRepository
    implements ProductRepository, LocationAwareProductRepository {
  int _revision = 0;
  int terminalLoads = 0;
  CatalogSnapshot catalog = const CatalogSnapshot(
    providers: [],
    models: [],
    agents: [],
  );

  @override
  int get locationRevision => _revision;

  @override
  void setLocation({String? directory, String? workspace}) {
    _revision++;
  }

  @override
  Future<List<WorkspaceProject>> listProjects() async => const [];

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => const [];

  @override
  Future<List<TerminalProcess>> listTerminals() async {
    terminalLoads++;
    return const [];
  }

  @override
  Future<CatalogSnapshot> loadCatalog() async => catalog;

  @override
  Future<List<VersionControlFile>> listFileStatuses() async => const [];

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _FileStatusRepository extends _LocationRepository {
  List<VersionControlFile> statuses = const [];
  List<FileDiff> diffs = const [];
  Object? statusError;
  int statusLoads = 0;
  int diffLoads = 0;

  @override
  Future<List<VersionControlFile>> listFileStatuses() async {
    statusLoads++;
    if (statusError case final error?) throw error;
    return statuses;
  }

  @override
  Future<List<FileDiff>> listVcsDiffs(VcsDiffMode mode) async {
    diffLoads++;
    return diffs;
  }
}

class _SymbolRepository extends _LocationRepository {
  final queries = <String>[];
  List<WorkspaceSymbol> symbols = const [];
  Object? symbolError;

  @override
  Future<List<WorkspaceSymbol>> findWorkspaceSymbols(String query) async {
    queries.add(query);
    if (symbolError case final error?) throw error;
    return symbols;
  }
}

class _MemoryChannel implements TerminalChannel {
  final controller = StreamController<String>();
  final writes = <String>[];
  bool closed = false;

  @override
  Stream<String> get output => controller.stream;

  @override
  int? get cursor => null;

  @override
  void write(String value) => writes.add(value);

  @override
  Future<void> close() async {
    if (closed) return;
    closed = true;
    unawaited(controller.close());
  }
}

class _SignalController extends ConnectionController {
  _SignalController(super.store);

  void signalLocation(ProductRepository value) {
    repository = value;
    locationRevision++;
    notifyListeners();
  }
}

class _TerminalRepository extends _LocationRepository {
  final channels = <_MemoryChannel>[];
  int resizeCalls = 0;

  @override
  Future<TerminalChannel> connectTerminal(String id, {int? cursor}) async {
    final channel = _MemoryChannel();
    channels.add(channel);
    return channel;
  }

  @override
  Future<void> resizeTerminal(
    String id, {
    required int rows,
    required int cols,
  }) async {
    resizeCalls++;
  }
}

class _DelayedTerminalRepository extends _LocationRepository {
  _DelayedTerminalRepository({required this.processes});

  final List<TerminalProcess> processes;
  final createResult = Completer<TerminalProcess>();
  final renameResult = Completer<void>();
  final removeResult = Completer<void>();
  int createCalls = 0;
  int renameCalls = 0;
  int removeCalls = 0;

  @override
  Future<List<TerminalProcess>> listTerminals() async {
    terminalLoads++;
    return processes;
  }

  @override
  Future<TerminalProcess> createTerminal({String? title}) {
    createCalls++;
    return createResult.future;
  }

  @override
  Future<void> renameTerminal(String id, String title) {
    renameCalls++;
    return renameResult.future;
  }

  @override
  Future<void> removeTerminal(String id) {
    removeCalls++;
    return removeResult.future;
  }
}

class _ReconnectController extends ConnectionController {
  _ReconnectController(super.store, this.ready);

  final Completer<void> ready;

  @override
  Future<void> connect(ServerProfile profile) async {
    status = StreamStatus.connecting;
    notifyListeners();
    await ready.future;
    api = _TestApi();
    repository = _LocationRepository();
    version = 'test';
    status = StreamStatus.connected;
    notifyListeners();
  }
}

class _ImmediateController extends ConnectionController {
  _ImmediateController(super.store);

  @override
  Future<void> connect(ServerProfile profile) async {
    await store.setActiveId(profile.id);
    api = _TestApi();
    repository = _LocationRepository();
    version = 'test';
    status = StreamStatus.connected;
    notifyListeners();
  }
}

class _MemoryProfileStore extends ProfileStore {
  _MemoryProfileStore({required super.prefs, required this.profile});

  final ServerProfile profile;
  String? selectedID;

  @override
  List<ServerProfile> get profiles => [profile];

  @override
  String? get activeId => selectedID;

  @override
  Future<void> setActiveId(String? id) async {
    selectedID = id;
  }
}

Future<(ProfileStore, ServerProfile)> _storeWithProfile() async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final profile = ServerProfile(
    id: 'server-1',
    name: 'Saved server',
    baseUrl: 'http://localhost:4096',
  );
  final store = _MemoryProfileStore(prefs: prefs, profile: profile);
  await store.setActiveId(profile.id);
  return (store, profile);
}

Future<ConnectionController> _controller({
  OpenCodeApi? api,
  ProductRepository? repository,
}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: prefs))
    ..api = api ?? _TestApi()
    ..repository = repository
    ..status = StreamStatus.connected;
}

Future<ConnectionController> _controllerWithoutApi() async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: prefs));
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const terminalProcess = TerminalProcess(
    id: 'pty-1',
    title: 'Shell',
    command: 'bash',
    arguments: [],
    directory: '/work',
    running: true,
    pid: 42,
  );

  testWidgets('persisted startup waits for reconnect before loading tabs', (
    tester,
  ) async {
    final (store, _) = await _storeWithProfile();
    final ready = Completer<void>();
    final controller = _ReconnectController(store, ready);
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          bootstrapProvider.overrideWithValue(AppBootstrap(store)),
          connProvider.overrideWithValue(controller),
        ],
        child: const OcApp(),
      ),
    );
    await tester.pump();

    expect(find.text('Connecting to Saved server'), findsOneWidget);
    expect(find.byType(NavigationBar), findsNothing);
    expect(find.byType(NavigationRail), findsNothing);

    ready.complete();
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.pump(const Duration(milliseconds: 50));
    expect(find.byType(NavigationRail), findsOneWidget);
  });

  testWidgets(
    'newer file request wins and location invalidates retained files',
    (tester) async {
      final first = Completer<List<FileNode>>();
      final second = Completer<List<FileNode>>();
      var call = 0;
      final api = _TestApi(
        files: (_) {
          call++;
          if (call == 1) return first.future;
          if (call == 2) return second.future;
          return Future.value(const <FileNode>[]);
        },
      );
      final repository = _LocationRepository();
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final controller = _SignalController(ProfileStore(prefs: prefs))
        ..api = api
        ..repository = repository
        ..status = StreamStatus.connected;
      addTearDown(controller.dispose);

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(body: FilesScreen(controller: controller)),
        ),
      );
      await tester.pump();
      repository.setLocation(directory: '/new');
      controller.signalLocation(repository);
      await tester.pump();
      expect(call, 2);
      second.complete([
        FileNode(name: 'new.txt', path: 'new.txt', isDir: false),
      ]);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 20));
      expect(find.text('new.txt'), findsOneWidget);

      first.complete([
        FileNode(name: 'old.txt', path: 'old.txt', isDir: false),
      ]);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 20));
      expect(find.text('new.txt'), findsOneWidget);
      expect(find.text('old.txt'), findsNothing);
    },
  );

  testWidgets('nested files use project-relative API paths and breadcrumbs', (
    tester,
  ) async {
    final requestedPaths = <String>[];
    final api = _TestApi(
      files: (path) async {
        requestedPaths.add(path);
        return switch (path) {
          '' => [FileNode(name: 'lib', path: '/lib', isDir: true)],
          'lib' => [FileNode(name: 'ui', path: 'lib/ui', isDir: true)],
          'lib/ui' => [
            FileNode(
              name: 'screen.dart',
              path: 'lib/ui/screen.dart',
              isDir: false,
            ),
          ],
          _ => const <FileNode>[],
        };
      },
    );
    final controller = await _controller(
      api: api,
      repository: _LocationRepository(),
    );
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: FilesScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('lib'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('ui'));
    await tester.pumpAndSettle();

    expect(requestedPaths, ['', 'lib', 'lib/ui']);
    expect(find.widgetWithText(ActionChip, '/'), findsOneWidget);
    expect(find.text('/lib/ui'), findsNothing);

    await tester.tap(find.widgetWithText(ActionChip, 'lib'));
    await tester.pumpAndSettle();
    expect(requestedPaths.last, 'lib');
  });

  testWidgets(
    'files show exact changes and aggregate nested changes without crowding',
    (tester) async {
      final api = _TestApi(
        files: (path) async => switch (path) {
          '' => [
            FileNode(name: 'lib', path: 'lib', isDir: true),
            FileNode(name: 'README.md', path: 'README.md', isDir: false),
          ],
          'lib' => [
            FileNode(name: 'main.dart', path: 'lib/main.dart', isDir: false),
          ],
          _ => const <FileNode>[],
        },
      );
      final repository = _FileStatusRepository()
        ..statuses = const [
          VersionControlFile(
            path: '/README.md',
            status: 'modified',
            additions: 8,
            deletions: 2,
          ),
          VersionControlFile(
            path: 'lib/main.dart',
            status: 'added',
            additions: 34,
            deletions: 0,
          ),
          VersionControlFile(
            path: 'gone.txt',
            status: 'deleted',
            additions: 0,
            deletions: 12,
          ),
        ]
        ..diffs = [
          FileDiff(
            file: 'lib/main.dart',
            patch: '@@ -0,0 +1 @@\n+library change',
            additions: 1,
            deletions: 0,
          ),
          FileDiff(
            file: 'README.md',
            patch: '@@ -1 +1 @@\n-old readme\n+new readme',
            additions: 1,
            deletions: 1,
          ),
          FileDiff(
            file: 'gone.txt',
            patch: '@@ -1 +0,0 @@\n-deleted text',
            additions: 0,
            deletions: 1,
            status: 'deleted',
          ),
        ];
      final controller = await _controller(api: api, repository: repository);
      addTearDown(controller.dispose);
      tester.view.physicalSize = const Size(320, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (context) => MediaQuery(
              data: MediaQuery.of(
                context,
              ).copyWith(textScaler: const TextScaler.linear(2)),
              child: Scaffold(body: FilesScreen(controller: controller)),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byKey(const ValueKey('folder-change-count')), findsOneWidget);
      expect(find.text('1 changed file'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('review-file-change-README.md')),
        findsOneWidget,
      );
      expect(find.text('Modified · +8 −2'), findsOneWidget);
      expect(find.text('gone.txt'), findsOneWidget);
      expect(find.text('Deleted · −12'), findsOneWidget);
      expect(
        tester
            .widget<ListTile>(
              find.byKey(const ValueKey('project-file-gone.txt')),
            )
            .onTap,
        isNull,
      );
      expect(
        find.byKey(const ValueKey('review-file-change-gone.txt')),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);

      await tester.tap(
        find.byKey(const ValueKey('review-file-change-README.md')),
      );
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('review-workspace')), findsOneWidget);
      expect(find.byKey(const Key('review-scope-picker')), findsNothing);
      expect(find.text('+new readme'), findsOneWidget);
      expect(find.text('+library change'), findsNothing);
      expect(repository.diffLoads, 1);
      expect(tester.takeException(), isNull);

      await tester.pageBack();
      await tester.pumpAndSettle();

      await tester.tap(find.text('lib'));
      await tester.pumpAndSettle();

      expect(
        find.byKey(const ValueKey('review-file-change-lib/main.dart')),
        findsOneWidget,
      );
      expect(find.text('Added · +34'), findsOneWidget);
      expect(repository.statusLoads, greaterThanOrEqualTo(2));
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('file review comments return to the active chat callback', (
    tester,
  ) async {
    final api = _TestApi(
      files: (_) async => [
        FileNode(name: 'README.md', path: 'README.md', isDir: false),
      ],
    );
    final repository = _FileStatusRepository()
      ..statuses = const [
        VersionControlFile(
          path: 'README.md',
          status: 'modified',
          additions: 1,
          deletions: 1,
        ),
      ]
      ..diffs = [
        FileDiff(
          file: 'README.md',
          patch: '@@ -1 +1 @@\n-old copy\n+new copy',
          additions: 1,
          deletions: 1,
        ),
      ];
    final controller = await _controller(api: api, repository: repository);
    addTearDown(controller.dispose);
    String? reviewPrompt;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: FilesScreen(
            controller: controller,
            onReviewPrompt: (value) => reviewPrompt = value,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(
      find.byKey(const ValueKey('review-file-change-README.md')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Ask about file'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('review-comment-field')),
      'Keep this wording precise.',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('review-add-to-prompt')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('review-workspace')), findsNothing);
    expect(reviewPrompt, contains('Review `README.md`'));
    expect(reviewPrompt, contains('Keep this wording precise.'));
    expect(
      find.text('Review comment added. Return to the chat to continue.'),
      findsOneWidget,
    );
  });

  testWidgets('top-level file review copies its comment for another chat', (
    tester,
  ) async {
    String? copiedText;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'Clipboard.setData') {
          copiedText = (call.arguments as Map)['text'] as String?;
        }
        return null;
      },
    );
    addTearDown(
      () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        null,
      ),
    );
    final api = _TestApi(
      files: (_) async => [
        FileNode(name: 'README.md', path: 'README.md', isDir: false),
      ],
    );
    final repository = _FileStatusRepository()
      ..statuses = const [
        VersionControlFile(
          path: 'README.md',
          status: 'modified',
          additions: 1,
          deletions: 1,
        ),
      ]
      ..diffs = [
        FileDiff(
          file: 'README.md',
          patch: '@@ -1 +1 @@\n-old copy\n+new copy',
          additions: 1,
          deletions: 1,
        ),
      ];
    final controller = await _controller(api: api, repository: repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: FilesScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const ValueKey('review-file-change-README.md')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Ask about file'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('review-comment-field')),
      'Use the approved wording.',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('review-add-to-prompt')));
    await tester.pumpAndSettle();

    expect(copiedText, contains('Review `README.md`'));
    expect(copiedText, contains('Use the approved wording.'));
    expect(
      find.text('Review comment copied. Paste it into a chat.'),
      findsOneWidget,
    );
  });

  testWidgets('file status failure stays scoped and retries independently', (
    tester,
  ) async {
    final api = _TestApi(
      files: (_) async => [
        FileNode(name: 'README.md', path: 'README.md', isDir: false),
      ],
    );
    final repository = _FileStatusRepository()
      ..statusError = const ProductException('Old server');
    final controller = await _controller(api: api, repository: repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: FilesScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('README.md'), findsOneWidget);
    expect(find.byKey(const ValueKey('file-status-notice')), findsOneWidget);
    expect(
      find.text('File change indicators are unavailable on this server.'),
      findsOneWidget,
    );

    repository
      ..statusError = null
      ..statuses = const [
        VersionControlFile(
          path: 'README.md',
          status: 'modified',
          additions: 1,
          deletions: 0,
        ),
      ];
    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('file-status-notice')), findsNothing);
    expect(
      find.byKey(const ValueKey('review-file-change-README.md')),
      findsOneWidget,
    );
    expect(repository.statusLoads, 2);
  });

  testWidgets('foreground refresh reloads the current file directory', (
    tester,
  ) async {
    var refreshed = false;
    final requestedPaths = <String>[];
    final api = _TestApi(
      files: (path) async {
        requestedPaths.add(path);
        if (path.isEmpty) {
          return [FileNode(name: 'lib', path: 'lib', isDir: true)];
        }
        return [
          FileNode(
            name: refreshed ? 'after.dart' : 'before.dart',
            path: 'lib/${refreshed ? 'after.dart' : 'before.dart'}',
            isDir: false,
          ),
        ];
      },
    );
    final controller = await _controller(
      api: api,
      repository: _LocationRepository(),
    );
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: FilesScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('lib'));
    await tester.pumpAndSettle();
    expect(find.text('before.dart'), findsOneWidget);

    refreshed = true;
    controller.signalDataRefreshForTesting();
    await tester.pumpAndSettle();

    expect(requestedPaths.last, 'lib');
    expect(find.text('after.dart'), findsOneWidget);
    expect(find.text('before.dart'), findsNothing);
  });

  testWidgets('file search clear control has an accessible tooltip', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    final controller = await _controller(repository: _LocationRepository());
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: FilesScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), 'query');
    await tester.pump();

    expect(find.byTooltip('Clear file search'), findsOneWidget);
    expect(find.bySemanticsLabel(RegExp('Clear file search')), findsOneWidget);
    semantics.dispose();
  });

  testWidgets('clearing file search restores the directory it started in', (
    tester,
  ) async {
    final requestedPaths = <String>[];
    final api = _TestApi(
      files: (path) async {
        requestedPaths.add(path);
        return switch (path) {
          '' => [FileNode(name: 'lib', path: 'lib', isDir: true)],
          'lib' => [
            FileNode(name: 'src', path: 'lib/src', isDir: true),
            FileNode(name: 'local.dart', path: 'lib/local.dart', isDir: false),
          ],
          _ => const <FileNode>[],
        };
      },
      findFiles: (_) async => const ['test/result.dart'],
    );
    final controller = await _controller(
      api: api,
      repository: _LocationRepository(),
    );
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: FilesScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('lib'));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), 'result');
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();
    expect(find.text('result.dart'), findsOneWidget);

    await tester.tap(find.byTooltip('Clear file search'));
    await tester.pumpAndSettle();

    expect(requestedPaths.last, 'lib');
    expect(find.text('local.dart'), findsOneWidget);
    expect(find.text('result.dart'), findsNothing);
  });

  testWidgets('unsupported binary files show preview metadata', (tester) async {
    final api = _TestApi(
      files: (_) async => [
        FileNode(name: 'image.bin', path: 'image.bin', isDir: false),
      ],
      contents: const {
        'image.bin': FileContent(
          'AAEC',
          type: 'binary',
          encoding: 'base64',
          mimeType: 'application/octet-stream',
        ),
      },
    );
    final controller = await _controller(
      api: api,
      repository: _LocationRepository(),
    );
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: FilesScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('image.bin'));
    await tester.pumpAndSettle();

    expect(find.text('Preview unavailable'), findsOneWidget);
    expect(find.textContaining('application/octet-stream'), findsOneWidget);
    expect(find.text('AAEC'), findsNothing);
  });

  testWidgets('image files open as a zoomable preview', (tester) async {
    final api = _TestApi(
      files: (_) async => [
        FileNode(name: 'pixel.png', path: 'pixel.png', isDir: false),
      ],
      contents: const {
        'pixel.png': FileContent(
          'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2ZKgAAAAASUVORK5CYII=',
          type: 'binary',
          encoding: 'base64',
        ),
      },
    );
    final controller = await _controller(
      api: api,
      repository: _LocationRepository(),
    );
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: FilesScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('pixel.png'));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('file-preview-image')), findsOneWidget);
    expect(find.text('Pinch to zoom'), findsOneWidget);
    expect(find.byType(InteractiveViewer), findsOneWidget);
    expect(find.byKey(const Key('project-file-download')), findsOneWidget);
    expect(find.byKey(const Key('project-file-attach')), findsNothing);
  });

  testWidgets('chat file viewer can attach the original project file', (
    tester,
  ) async {
    const path = 'docs/review.md';
    const body = '# Review\n\nAdd a focused follow-up comment.';
    final api = _TestApi(
      files: (_) async => [
        FileNode(name: 'review.md', path: path, isDir: false),
      ],
      contents: const {path: FileContent(body, mimeType: 'text/markdown')},
    );
    final controller = await _controller(
      api: api,
      repository: _LocationRepository(),
    );
    addTearDown(controller.dispose);
    String? attachedPath;
    FilePreviewData? attachedData;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: FilesScreen(
            controller: controller,
            onAttachFile: (path, data) async {
              attachedPath = path;
              attachedData = data;
            },
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('review.md'));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('project-file-download')), findsOneWidget);
    expect(find.byKey(const Key('project-file-attach')), findsOneWidget);
    await tester.tap(find.byKey(const Key('project-file-attach')));
    await tester.pumpAndSettle();

    expect(attachedPath, path);
    expect(attachedData?.name, 'review.md');
    expect(attachedData?.mimeType, 'text/markdown');
    expect(attachedData?.text, body);
    expect(
      find.text('review.md attached. Return to the chat to add your comment.'),
      findsOneWidget,
    );
  });

  testWidgets('symbol search opens the exact source line in file preview', (
    tester,
  ) async {
    final source = List.generate(
      70,
      (index) => index == 41
          ? 'class ProjectHealthScreen extends StatefulWidget {'
          : '// line ${index + 1}',
    ).join('\n');
    final api = _TestApi(
      files: (_) async => const [],
      contents: {
        'lib/ui/project_health_screen.dart': FileContent(
          source,
          type: 'text',
          mimeType: 'text/plain',
        ),
      },
    );
    final repository = _SymbolRepository()
      ..symbols = const [
        WorkspaceSymbol(
          name: 'ProjectHealthScreen',
          kind: 5,
          path: 'lib/ui/project_health_screen.dart',
          line: 42,
          column: 7,
        ),
      ];
    final controller = await _controller(api: api, repository: repository);
    addTearDown(controller.dispose);
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: const TextScaler.linear(2)),
            child: Scaffold(body: FilesScreen(controller: controller)),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Symbols'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'ProjectHealth');
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();

    expect(repository.queries, ['ProjectHealth']);
    expect(find.text('ProjectHealthScreen'), findsOneWidget);
    expect(
      find.textContaining('lib/ui/project_health_screen.dart:42:7'),
      findsOneWidget,
    );

    await tester.tap(find.text('ProjectHealthScreen'));
    await tester.pumpAndSettle();

    expect(find.textContaining('Line 42'), findsOneWidget);
    expect(
      find.byKey(const Key('file-preview-focused-source')),
      findsOneWidget,
    );
    expect(find.byKey(const Key('file-preview-target-line')), findsOneWidget);
    expect(
      find.text('class ProjectHealthScreen extends StatefulWidget {'),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('symbol search debounces typing and explains empty results', (
    tester,
  ) async {
    final api = _TestApi(files: (_) async => const []);
    final repository = _SymbolRepository();
    final controller = await _controller(api: api, repository: repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: FilesScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Symbols'));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), 'MissingSymbol');
    await tester.pump(const Duration(milliseconds: 349));
    expect(repository.queries, isEmpty);
    await tester.pump(const Duration(milliseconds: 1));
    await tester.pumpAndSettle();

    expect(repository.queries, ['MissingSymbol']);
    expect(find.text('No symbols found'), findsOneWidget);
    expect(
      find.textContaining('do not support workspace-wide symbol search'),
      findsOneWidget,
    );
  });

  testWidgets('unavailable symbol search leaves file browsing intact', (
    tester,
  ) async {
    final api = _TestApi(
      files: (_) async => [
        FileNode(name: 'README.md', path: 'README.md', isDir: false),
      ],
    );
    final repository = _SymbolRepository()
      ..symbolError = const ProductException(
        'Symbol search is unavailable on this server',
      );
    final controller = await _controller(api: api, repository: repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: FilesScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('README.md'), findsOneWidget);

    await tester.tap(find.text('Symbols'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'Missing');
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();
    expect(
      find.text('Symbol search is unavailable on this server'),
      findsOneWidget,
    );

    await tester.tap(find.text('Files'));
    await tester.pumpAndSettle();
    expect(find.text('README.md'), findsOneWidget);
  });

  testWidgets(
    'terminal reconnects, forwards control input, and closes channels',
    (tester) async {
      final repository = _TerminalRepository();
      await tester.pumpWidget(
        MaterialApp(
          home: TerminalSurface(
            repository: repository,
            process: terminalProcess,
          ),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));
      expect(find.byType(TerminalView), findsOneWidget);

      await tester.tap(find.byKey(const Key('terminal-accessible-mode')));
      await tester.pump();
      expect(
        find.byKey(const Key('terminal-accessible-input')),
        findsOneWidget,
      );
      expect(find.bySemanticsLabel('Terminal transcript'), findsOneWidget);
      await tester.tap(find.byKey(const Key('terminal-accessible-mode')));
      await tester.pump();

      await tester.tap(find.text('Ctrl-C'));
      expect(repository.channels.first.writes, contains('\x03'));

      final reconnect = tester.widget<IconButton>(
        find.byKey(const Key('terminal-reconnect')),
      );
      await tester.runAsync(() async {
        reconnect.onPressed!();
        await Future<void>.delayed(const Duration(milliseconds: 20));
      });
      await tester.pump();
      expect(repository.channels, hasLength(2));
      expect(repository.channels.first.closed, isTrue);

      await tester.pumpWidget(const MaterialApp(home: SizedBox()));
      await tester.pump();
      expect(repository.channels.last.closed, isTrue);
    },
  );

  testWidgets('stale terminal create does not open in a new repository', (
    tester,
  ) async {
    final oldRepository = _DelayedTerminalRepository(processes: const []);
    final newRepository = _TerminalRepository();
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final controller = _SignalController(ProfileStore(prefs: prefs))
      ..api = _TestApi()
      ..repository = oldRepository
      ..status = StreamStatus.connected;
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: TerminalScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FloatingActionButton, 'Terminal'));
    await tester.pump();
    expect(oldRepository.createCalls, 1);

    controller.signalLocation(newRepository);
    await tester.pumpAndSettle();
    expect(newRepository.terminalLoads, 1);

    oldRepository.createResult.complete(terminalProcess);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 20));

    expect(find.byType(TerminalSurface), findsNothing);
    expect(newRepository.terminalLoads, 1);
    final createButton = tester.widget<FloatingActionButton>(
      find.byType(FloatingActionButton),
    );
    expect(createButton.onPressed, isNotNull);
  });

  testWidgets('stale terminal rename does not reload a new workspace', (
    tester,
  ) async {
    final repository = _DelayedTerminalRepository(
      processes: const [terminalProcess],
    );
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final controller = _SignalController(ProfileStore(prefs: prefs))
      ..api = _TestApi()
      ..repository = repository
      ..status = StreamStatus.connected;
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: TerminalScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Terminal actions'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Rename'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'Renamed');
    await tester.tap(find.text('Save'));
    await tester.pump();
    expect(repository.renameCalls, 1);

    repository.setLocation(workspace: 'new-workspace');
    controller.signalLocation(repository);
    await tester.pumpAndSettle();
    expect(repository.terminalLoads, 2);

    repository.renameResult.complete();
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 20));
    expect(repository.terminalLoads, 2);
  });

  testWidgets('stale terminal remove does not reload a new repository', (
    tester,
  ) async {
    final oldRepository = _DelayedTerminalRepository(
      processes: const [terminalProcess],
    );
    final newRepository = _TerminalRepository();
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final controller = _SignalController(ProfileStore(prefs: prefs))
      ..api = _TestApi()
      ..repository = oldRepository
      ..status = StreamStatus.connected;
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: TerminalScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Terminal actions'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Stop'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Stop'));
    await tester.pump();
    expect(oldRepository.removeCalls, 1);

    controller.signalLocation(newRepository);
    await tester.pumpAndSettle();
    expect(newRepository.terminalLoads, 1);

    oldRepository.removeResult.complete();
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 20));
    expect(newRepository.terminalLoads, 1);
  });

  testWidgets('permission actions wrap on a narrow screen', (tester) async {
    tester.view.physicalSize = const Size(280, 700);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controllerWithoutApi();
    controller.permissions = {
      'permission-1': PermissionRequest(
        id: 'permission-1',
        sessionID: 'session-1',
        permission: 'bash',
        patterns: const ['long command pattern'],
      ),
    };
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(home: RequestsScreen(controller: controller)),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Run a shell command'));
    await tester.pumpAndSettle();

    // The tile now opens the shared permission sheet; its triad is stacked
    // full-width, so a 280dp screen must still render all three actions.
    expect(find.byKey(const Key('permission-sheet')), findsOneWidget);
    expect(find.byKey(const Key('permission-allow-once')), findsOneWidget);
    expect(find.byKey(const Key('permission-allow-always')), findsOneWidget);
    expect(find.byKey(const Key('permission-reject')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('model details remain scrollable on a short screen', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 360);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final repository = _LocationRepository()
      ..catalog = CatalogSnapshot(
        providers: const [],
        agents: const [],
        models: [
          CatalogModel(
            id: 'model',
            providerID: 'provider',
            name: 'Large model',
            enabled: true,
            status: 'active',
            contextLimit: 100000,
            outputLimit: 10000,
            reasoning: true,
            attachments: true,
            tools: true,
            variants: List.generate(
              24,
              (index) => CatalogVariant(id: 'variant-$index'),
            ),
          ),
        ],
      );
    final controller = await _controller(repository: repository);
    controller.catalog = repository.catalog;
    controller.catalogDetailed = true;
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(home: CatalogScreen(controller: controller)),
    );
    await tester.pumpAndSettle();
    await tester.drag(find.byType(ListView), const Offset(0, -500));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Large model'));
    await tester.pumpAndSettle();

    expect(find.byType(SingleChildScrollView), findsWidgets);
    expect(tester.takeException(), isNull);
  });

  testWidgets('server switch removes the previous home stack', (tester) async {
    final (store, profile) = await _storeWithProfile();
    final controller = _ImmediateController(store);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          bootstrapProvider.overrideWithValue(AppBootstrap(store)),
          connProvider.overrideWithValue(controller),
        ],
        child: MaterialApp(
          routes: {'/home': (_) => const Scaffold(body: Text('New home'))},
          home: Builder(
            builder: (context) => Scaffold(
              body: Column(
                children: [
                  const Text('Old home'),
                  FilledButton(
                    onPressed: () => Navigator.of(context).push(
                      MaterialPageRoute<void>(
                        builder: (_) => const ServersScreen(),
                      ),
                    ),
                    child: const Text('Servers'),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Servers'));
    await tester.pumpAndSettle();
    await tester.tap(find.text(profile.name));
    await tester.pumpAndSettle();

    expect(find.text('New home'), findsOneWidget);
    expect(find.text('Old home'), findsNothing);
  });

  testWidgets('remote quick add opens an editor without saving a fake host', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final store = ProfileStore(prefs: prefs);
    await store.load();
    final controller = ConnectionController(store);
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          bootstrapProvider.overrideWithValue(AppBootstrap(store)),
          connProvider.overrideWithValue(controller),
        ],
        child: const MaterialApp(home: ServersScreen()),
      ),
    );
    await tester.tap(find.byKey(const ValueKey('welcome-connect-card')));
    await tester.pumpAndSettle();

    expect(find.text('Add server'), findsOneWidget);
    expect(find.byKey(const ValueKey('server-profile-editor')), findsOneWidget);
    expect(find.byType(AlertDialog), findsNothing);
    expect(find.text('Server URL'), findsOneWidget);
    expect(store.profiles, isEmpty);
    expect(find.text('Start the server'), findsNothing);
  });
}
