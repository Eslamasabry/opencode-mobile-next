// Interrupting actions ask before they act (UX plan rule 6). Each test proves
// both halves: nothing happens when the sheet is cancelled or dismissed, and
// the action runs once it is confirmed.
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Api extends OpenCodeApi {
  _Api({this.sessionList = const []}) : super(baseUrl: 'http://localhost');

  final List<Session> sessionList;

  @override
  Future<List<Session>> sessions() async => sessionList;

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => [];
}

class _Repository implements ProductRepository {
  final unshared = <String>[];

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<void> unshareSession(String id) async => unshared.add(id);

  @override
  Future<List<WorkspaceProject>> listProjects() async => const [
    WorkspaceProject(
      id: 'p1',
      name: 'p1',
      directory: '/tmp/p1',
      worktrees: [],
      updatedAt: 1,
    ),
  ];

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
  _Store({required super.prefs});

  final _profile = ServerProfile(
    id: 'local',
    name: 'Studio box',
    baseUrl: 'http://localhost:4096',
  );

  @override
  List<ServerProfile> get profiles => [_profile];

  @override
  String? get activeId => _profile.id;
}

class _Controller extends ConnectionController {
  _Controller(super.store);

  int disconnects = 0;

  @override
  Future<void> disconnect({bool keepActive = false, bool silent = false}) {
    disconnects += 1;
    return super.disconnect(keepActive: keepActive, silent: silent);
  }
}

Future<_Controller> _controller({
  List<Session> sessions = const [],
  _Repository? repository,
}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return _Controller(_Store(prefs: prefs))
    ..api = _Api(sessionList: sessions)
    ..repository = repository ?? _Repository()
    ..status = StreamStatus.connected;
}

Widget _app(Widget home, {ConnectionController? provided}) {
  final app = MaterialApp(
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    routes: {'/servers': (_) => const Scaffold(body: Text('servers-route'))},
    home: home,
  );
  if (provided == null) return app;
  return ProviderScope(
    overrides: [connProvider.overrideWithValue(provided)],
    child: app,
  );
}

const _sheet = ValueKey('disconnect-confirm-sheet');
const _confirm = ValueKey('confirm-disconnect');

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Disconnect', () {
    // The shell's Disconnect lives in the server switcher, which stays open
    // after a "no" so the person can pick something else.
    Future<void> openSwitcherDisconnect(WidgetTester tester) async {
      final disconnect = find.byKey(
        const ValueKey('server-switcher-disconnect'),
      );
      final current = find.byKey(const ValueKey('server-switcher-current'));
      if (current.evaluate().isEmpty) {
        await tester.tap(find.byKey(const ValueKey('server-switcher-button')));
        await tester.pumpAndSettle();
      }
      // Disconnect acts on the current server through its row menu.
      await tester.longPress(current);
      await tester.pumpAndSettle();
      await tester.ensureVisible(disconnect);
      await tester.tap(disconnect);
      await tester.pumpAndSettle();
    }

    testWidgets('server switcher asks first and stays put on cancel', (
      tester,
    ) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(const HomeScreen(), provided: controller));
      await tester.pumpAndSettle();

      await openSwitcherDisconnect(tester);
      expect(find.byKey(_sheet), findsOneWidget);
      expect(find.text('Disconnect from Studio box?'), findsOneWidget);
      // One sentence on what happens; nothing waits, so no count and never
      // "No queued prompts" (settings-disconnect-sheet).
      expect(find.textContaining('The server keeps running'), findsOneWidget);
      expect(find.textContaining('queued'), findsNothing);
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(controller.disconnects, 0);
      expect(controller.status, StreamStatus.connected);
      expect(find.text('servers-route'), findsNothing);

      // Dismissing by the scrim is also a "no".
      await openSwitcherDisconnect(tester);
      await tester.tapAt(const Offset(10, 10));
      await tester.pumpAndSettle();
      expect(find.byKey(_sheet), findsNothing);
      expect(controller.disconnects, 0);
    });

    testWidgets('server switcher disconnects once confirmed', (tester) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(const HomeScreen(), provided: controller));
      await tester.pumpAndSettle();

      await openSwitcherDisconnect(tester);
      await tester.tap(find.byKey(_confirm));
      await tester.pumpAndSettle();
      expect(controller.disconnects, 1);
      expect(find.text('servers-route'), findsOneWidget);
    });

    // Disconnect lives on the server's own page (Settings > This server).
    Future<void> tapServerPageDisconnect(WidgetTester tester) async {
      final button = find.byKey(const ValueKey('server-disconnect'));
      await tester.scrollUntilVisible(
        button,
        200,
        scrollable: find.byType(Scrollable).first,
      );
      // Wholly in view, whatever the length of the words above it.
      await tester.ensureVisible(button);
      await tester.pumpAndSettle();
      await tester.tap(button);
      await tester.pumpAndSettle();
    }

    testWidgets('the server page shows the same sheet and honours cancel', (
      tester,
    ) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(ServerSettingsScreen(controller: controller)),
      );
      await tester.pumpAndSettle();

      await tapServerPageDisconnect(tester);
      expect(find.byKey(_sheet), findsOneWidget);
      expect(find.text('Disconnect from Studio box?'), findsOneWidget);
      // One sentence on what happens; nothing waits, so no count and never
      // "No queued prompts" (settings-disconnect-sheet).
      expect(find.textContaining('The server keeps running'), findsOneWidget);
      expect(find.textContaining('queued'), findsNothing);
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(controller.disconnects, 0);
      expect(find.text('servers-route'), findsNothing);
    });

    testWidgets('the server page disconnects once confirmed', (tester) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(ServerSettingsScreen(controller: controller)),
      );
      await tester.pumpAndSettle();

      await tapServerPageDisconnect(tester);
      await tester.tap(find.byKey(_confirm));
      await tester.pumpAndSettle();
      expect(controller.disconnects, 1);
      expect(find.text('servers-route'), findsOneWidget);
    });
  });
}
