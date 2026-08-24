import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
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
import 'package:shared_preferences/shared_preferences.dart';
import 'package:xterm/xterm.dart';

class _TestApi extends OpenCodeApi {
  _TestApi({this.files, this.contents = const {}})
    : super(baseUrl: 'http://localhost');

  final Future<List<FileNode>> Function(String path)? files;
  final Map<String, FileContent> contents;

  @override
  Future<List<FileNode>> listFiles([String path = '']) async =>
      files?.call(path) ?? const [];

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
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _MemoryChannel implements TerminalChannel {
  final controller = StreamController<String>();
  final writes = <String>[];
  bool closed = false;

  @override
  Stream<String> get output => controller.stream;

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
  Future<TerminalChannel> connectTerminal(String id) async {
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

  testWidgets('binary files render metadata instead of base64 source', (
    tester,
  ) async {
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

    expect(find.text('Binary file'), findsOneWidget);
    expect(find.textContaining('application/octet-stream'), findsOneWidget);
    expect(find.text('AAEC'), findsNothing);
  });

  testWidgets(
    'terminal reconnects, forwards control input, and closes channels',
    (tester) async {
      final repository = _TerminalRepository();
      const process = TerminalProcess(
        id: 'pty-1',
        title: 'Shell',
        command: 'bash',
        arguments: [],
        directory: '/work',
        running: true,
        pid: 42,
      );
      await tester.pumpWidget(
        MaterialApp(
          home: TerminalSurface(repository: repository, process: process),
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

    expect(find.text('Reject'), findsOneWidget);
    expect(find.text('Always'), findsOneWidget);
    expect(find.text('Allow once'), findsOneWidget);
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
            variants: List.generate(24, (index) => 'variant-$index'),
          ),
        ],
      );
    final controller = await _controller(repository: repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(home: CatalogScreen(controller: controller)),
    );
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
}
