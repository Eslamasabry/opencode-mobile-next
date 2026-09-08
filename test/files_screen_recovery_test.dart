import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/files_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _FilesApi extends OpenCodeApi {
  _FilesApi(this.read, {this.list}) : super(baseUrl: 'http://localhost');

  final Future<FileContent> Function(String path) read;
  final Future<List<FileNode>> Function(String path)? list;

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => list != null
      ? list!(path)
      : [FileNode(name: 'README.md', path: 'README.md', isDir: false)];

  @override
  Future<List<String>> findFile(String query) async => const [];

  @override
  Future<FileContent> fileContent(String path) => read(path);

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};
}

class _StatusRepository implements ProductRepository {
  _StatusRepository(this.loadStatuses);

  final Future<List<VersionControlFile>> Function() loadStatuses;

  @override
  Future<List<VersionControlFile>> listFileStatuses() => loadStatuses();

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _SignalController extends ConnectionController {
  _SignalController(super.store);

  void signalScope(OpenCodeApi nextApi, ProductRepository nextRepository) {
    api = nextApi;
    repository = nextRepository;
    locationRevision++;
    notifyListeners();
  }
}

Future<ProfileStore> _store() async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ProfileStore(prefs: prefs);
}

Future<ConnectionController> _controller(OpenCodeApi api) async {
  return ConnectionController(await _store())
    ..api = api
    ..status = StreamStatus.connected;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('retry stays in the requested folder and Back returns to root', (
    tester,
  ) async {
    final requests = <String>[];
    var fail = true;
    final api = _FilesApi(
      (_) async => const FileContent('contents'),
      list: (path) async {
        requests.add(path);
        if (path.isEmpty) {
          return [FileNode(name: 'src', path: 'src', isDir: true)];
        }
        if (fail) throw const ProductException('Folder unavailable');
        return [
          FileNode(name: 'main.dart', path: 'src/main.dart', isDir: false),
        ];
      },
    );
    final controller = await _controller(api);
    final back = FilesBackController();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: FilesScreen(controller: controller, backController: back),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('src'));
    await tester.pumpAndSettle();
    expect(find.text('Folder unavailable'), findsOneWidget);
    fail = false;
    await tester.tap(find.text('Try again'));
    await tester.pumpAndSettle();
    expect(requests, ['', 'src', 'src']);
    expect(find.text('main.dart'), findsOneWidget);
    expect(back.handleBack(), isTrue);
    await tester.pumpAndSettle();
    expect(requests.last, '');
    expect(find.text('main.dart'), findsNothing);
    expect(find.text('src'), findsOneWidget);
  });

  testWidgets('empty folder refresh is actionable and keeps its location', (
    tester,
  ) async {
    var loads = 0;
    final api = _FilesApi(
      (_) async => const FileContent('contents'),
      list: (path) async {
        loads++;
        return [];
      },
    );
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: FilesScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Folder is empty'), findsOneWidget);
    await tester.drag(find.text('Folder is empty'), const Offset(0, 350));
    await tester.pumpAndSettle();
    expect(loads, 2);
    expect(find.text('Folder is empty'), findsOneWidget);
  });

  testWidgets('late file read cannot publish after a transport switch', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(320, 640));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final pending = Completer<FileContent>();
    final oldApi = _FilesApi((_) => pending.future);
    final controller = await _controller(oldApi);
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: FilesScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('README.md'));
    await tester.pump();

    final newApi = _FilesApi(
      (_) async => const FileContent('current content', mimeType: 'text/plain'),
    );
    controller.api = newApi;
    controller.locationRevision++;
    controller.notifyListeners();
    await tester.pumpAndSettle();

    expect(
      find.text('Connection changed. Close and reopen this file.'),
      findsOneWidget,
    );
    expect(find.text('Try again'), findsNothing);
    pending.complete(const FileContent('stale content'));
    await tester.pumpAndSettle();
    expect(find.text('stale content'), findsNothing);
  });

  testWidgets('failed file read exposes retry and recovers', (tester) async {
    await tester.binding.setSurfaceSize(const Size(320, 640));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    var reads = 0;
    final api = _FilesApi((_) {
      reads++;
      if (reads == 1) {
        return Future.error(
          const ProductException('File read temporarily unavailable'),
        );
      }
      return Future.value(const FileContent('recovered content'));
    });
    final controller = await _controller(api);
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: FilesScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('README.md'));
    await tester.pumpAndSettle();

    expect(find.text('File read temporarily unavailable'), findsOneWidget);
    expect(find.text('Try again'), findsOneWidget);
    await tester.tap(find.text('Try again'));
    await tester.pumpAndSettle();
    expect(find.text('recovered content'), findsOneWidget);
  });

  testWidgets('late attach read cannot cross a transport switch', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(320, 640));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final pending = Completer<FileContent>();
    final oldApi = _FilesApi((_) => pending.future);
    final controller = await _controller(oldApi);
    var attached = false;
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: FilesScreen(
            controller: controller,
            onAttachFile: (_, _) async => attached = true,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.longPress(find.text('README.md'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('file-menu-attach')));
    await tester.pump();

    final newApi = _FilesApi((_) async => const FileContent('current content'));
    controller.api = newApi;
    controller.locationRevision++;
    controller.notifyListeners();
    pending.complete(const FileContent('stale content'));
    await tester.pumpAndSettle();

    expect(attached, isFalse);
  });

  testWidgets('late file status response cannot repopulate old scope', (
    tester,
  ) async {
    final pending = Completer<List<VersionControlFile>>();
    final oldRepository = _StatusRepository(() => pending.future);
    final newRepository = _StatusRepository(
      () async => const [
        VersionControlFile(
          path: 'new.txt',
          status: 'deleted',
          additions: 0,
          deletions: 1,
        ),
      ],
    );
    final api = _FilesApi((_) async => const FileContent('unused'));
    final controller = _SignalController(await _store())
      ..api = api
      ..repository = oldRepository
      ..status = StreamStatus.connected;
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: FilesScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();
    controller.signalScope(api, newRepository);
    await tester.pumpAndSettle();
    pending.complete(const [
      VersionControlFile(
        path: 'old.txt',
        status: 'deleted',
        additions: 0,
        deletions: 1,
      ),
    ]);
    await tester.pumpAndSettle();

    expect(find.text('new.txt'), findsOneWidget);
    expect(find.text('old.txt'), findsNothing);
  });
}
