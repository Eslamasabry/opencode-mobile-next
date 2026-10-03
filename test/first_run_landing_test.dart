import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/first_run.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// After the first successful connect of a new device the shell stays on
/// Chats; it never opens
/// an empty conversation. A returning person lands as before.

class _Api extends OpenCodeApi {
  _Api() : super(baseUrl: 'http://localhost');

  @override
  Future<List<Session>> sessions() async => [];

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => [];
}

class _Repository implements ProductRepository {
  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<WorkspaceProject>> listProjects() async => [];

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => [];

  @override
  Future<List<TerminalProcess>> listTerminals() async => [];

  @override
  Future<CatalogSnapshot> loadCatalog() async =>
      const CatalogSnapshot(providers: [], models: [], agents: []);

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Store extends ProfileStore {
  _Store({required super.prefs, this.seeded = const []});

  final List<ServerProfile> seeded;

  @override
  List<ServerProfile> get profiles => seeded;

  @override
  String? get activeId => seeded.isEmpty ? null : seeded.first.id;
}

class _Connection extends ConnectionController {
  _Connection(super.store, {this.needsProject = false});

  bool needsProject;
  int created = 0;
  bool failCreate = false;

  @override
  bool get workspaceChoiceRequired => needsProject;

  @override
  Future<Session> createSession() async {
    created++;
    if (failCreate) throw StateError('create refused');
    return Session(id: 'first-$created');
  }

  @override
  Future<void> refreshSessions() async {}

  void projectChosen() {
    needsProject = false;
    notifyListeners();
  }
}

final _server = ServerProfile(
  id: 'work',
  name: 'Workstation',
  baseUrl: 'http://localhost:4096',
);

Future<_Connection> _connection(
  Map<String, Object> prefs, {
  bool needsProject = false,
}) async {
  SharedPreferences.setMockInitialValues(prefs);
  final store = _Store(
    prefs: await SharedPreferences.getInstance(),
    seeded: [_server],
  );
  return _Connection(store, needsProject: needsProject)
    ..api = _Api()
    ..repository = _Repository()
    ..status = StreamStatus.connected;
}

final _opened = <(String, Object?)>[];

Widget _shell(ConnectionController controller) => ProviderScope(
  overrides: [connProvider.overrideWithValue(controller)],
  child: MaterialApp(
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    onGenerateRoute: (settings) {
      if (settings.name?.startsWith('/chat/') != true) return null;
      _opened.add((settings.name!, settings.arguments));
      return MaterialPageRoute<void>(
        settings: settings,
        builder: (_) => Scaffold(
          appBar: AppBar(),
          body: Text('conversation ${settings.name}'),
        ),
      );
    },
    home: const HomeScreen(),
  ),
);

Future<void> _settle(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 400));
  await tester.pump(const Duration(milliseconds: 400));
}

String _tab(WidgetTester tester) {
  final nav = tester.widget<KitNav>(find.byType(KitNav));
  return nav.destinations[nav.selected].label;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() {
    _opened.clear();
  });

  void phone(WidgetTester tester) {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
  }

  test('only a device that started on the welcome is new', () async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final firstRun = FirstRun(prefs);
    expect(firstRun.landingPending, isFalse);

    // An upgraded build opens on saved servers: returning, for good.
    await firstRun.observeServers(hasServers: true);
    expect(firstRun.landingPending, isFalse);
    await firstRun.observeServers(hasServers: false);
    expect(firstRun.landingPending, isFalse);

    SharedPreferences.setMockInitialValues({});
    final fresh = FirstRun(await SharedPreferences.getInstance());
    await fresh.observeServers(hasServers: false);
    expect(fresh.landingPending, isTrue);
    expect(fresh.notifyAskPending, isFalse);
    await fresh.markLanded();
    expect(fresh.landingPending, isFalse);
    expect(fresh.notifyAskPending, isTrue);
    // Removing every server later does not make the person new again.
    await fresh.observeServers(hasServers: false);
    expect(fresh.landingPending, isFalse);
  });

  testWidgets('the welcome arms first run; saved servers do not', (
    tester,
  ) async {
    for (final seeded in [false, true]) {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final store = _Store(prefs: prefs, seeded: seeded ? [_server] : []);
      final controller = ConnectionController(store);
      await tester.pumpWidget(
        ProviderScope(
          key: UniqueKey(),
          overrides: [
            bootstrapProvider.overrideWithValue(AppBootstrap(store)),
            connProvider.overrideWithValue(controller),
          ],
          child: const MaterialApp(home: ServersScreen()),
        ),
      );
      await tester.pump();
      expect(FirstRun(prefs).landingPending, !seeded, reason: '$seeded');
      await tester.pumpWidget(const SizedBox());
      controller.dispose();
    }
  });

  testWidgets('the first connect lands on Chats, no conversation opens', (
    tester,
  ) async {
    phone(tester);
    final controller = await _connection({FirstRun.stateKey: 'armed'});
    addTearDown(controller.dispose);
    await tester.pumpWidget(_shell(controller));
    await _settle(tester);

    expect(controller.created, 0);
    expect(_opened, isEmpty);
    expect(_tab(tester), 'Chats');
    expect(FirstRun(controller.store.prefs).landingPending, isFalse);
    expect(FirstRun(controller.store.prefs).notifyAskPending, isTrue);
  });

  for (final state in <String?>[null, 'done']) {
    testWidgets('a returning person lands on Chats (state: $state)', (
      tester,
    ) async {
      phone(tester);
      final controller = await _connection({FirstRun.stateKey: ?state});
      addTearDown(controller.dispose);
      await tester.pumpWidget(_shell(controller));
      await _settle(tester);

      expect(controller.created, 0);
      expect(_opened, isEmpty);
      expect(_tab(tester), 'Chats');
      // Someone who never saw the welcome is recorded as returning, so
      // removing their servers later does not restart first run.
      expect(controller.store.prefs.getString(FirstRun.stateKey), 'done');
      expect(FirstRun(controller.store.prefs).notifyAskPending, isFalse);
    });
  }

  testWidgets('a returning person with something waiting lands on Chats', (
    tester,
  ) async {
    phone(tester);
    final controller = await _connection({FirstRun.stateKey: 'done'});
    addTearDown(controller.dispose);
    controller.permissions = {
      'perm-1': PermissionRequest(
        id: 'perm-1',
        sessionID: 'session-1',
        permission: 'edit',
        patterns: const ['lib/main.dart'],
      ),
    };
    await tester.pumpWidget(_shell(controller));
    await _settle(tester);

    // The waiting request is the badge on Chats; nothing moves the person.
    expect(_tab(tester), 'Chats');
    expect(controller.created, 0);
  });
}
