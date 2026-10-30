// The phone shell without a bottom bar (docs/qa/no-bottom-nav-2026-10-20):
// Conversations is the only home, the gear opens Settings, a project's Files
// open as a page, and every door that used to pick a tab lands on the new
// place. Tablets and PCs keep the rail with its three destinations.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart' show StreamStatus;
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/domain/run_result.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/desktop/shortcuts.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chats/chats_home_screen.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/project_hub_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/fake_server_gateway.dart';

class _Api extends FakeServerGateway {
  _Api() : super(baseUrl: 'http://localhost');

  @override
  Future<List<Session>> sessions() async => [];

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => [
    FileNode(name: 'README.md', path: 'README.md', isDir: false),
  ];

  @override
  Future<List<String>> findFile(String query) async => [];
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
  _Store({required super.prefs});

  @override
  List<ServerProfile> get profiles => [
    ServerProfile(id: 'local', name: 'Laptop', baseUrl: 'http://localhost'),
  ];

  @override
  String? get activeId => 'local';
}

Future<ConnectionController> _controller() async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(_Store(prefs: prefs))
    ..api = _Api()
    ..repository = _Repository()
    ..status = StreamStatus.connected
    ..directory = '/srv/app';
}

/// The shell with the shortcut bus the app installs, so search results and
/// shortcuts can reach it.
Future<AppShortcutSignals> _pump(
  WidgetTester tester,
  ConnectionController controller, {
  Size size = const Size(412, 915),
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final signals = AppShortcutSignals();
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: AppShortcutScope(
          signals: signals,
          perform: (_) {},
          child: const HomeScreen(),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return signals;
}

Finder get _filesPage => find.byKey(const ValueKey('project-files-page'));

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() {
    // ProfileStore reads secure storage; without a handler it waits forever.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        );
  });

  group('phone', () {
    testWidgets('no navigation bar: the home is Conversations with a gear', (
      tester,
    ) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await _pump(tester, controller);
      expect(find.byType(KitNavRail), findsNothing);
      expect(find.byKey(const ValueKey('home-shell-tab-files')), findsNothing);
      expect(find.byType(ChatsHomeScreen), findsOneWidget);
      expect(find.byKey(const ValueKey('home-shell-settings')), findsOneWidget);
      expect(find.byKey(const ValueKey('home-shell-search')), findsOneWidget);
    });

    testWidgets('the gear opens Settings as a page and Back returns home', (
      tester,
    ) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await _pump(tester, controller);
      await tester.tap(find.byKey(const ValueKey('home-shell-settings')));
      await tester.pumpAndSettle();
      expect(find.byType(SettingsScreen), findsOneWidget);
      expect(find.byType(ChatsHomeScreen), findsNothing);
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(find.byType(SettingsScreen), findsNothing);
      expect(find.byType(ChatsHomeScreen), findsOneWidget);
    });

    testWidgets('Ctrl/Cmd+2 and search "Files" open the Files page', (
      tester,
    ) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      final signals = await _pump(tester, controller);
      expect(signals.dispatch(const SelectDestinationIntent(1)), isTrue);
      await tester.pumpAndSettle();
      expect(_filesPage, findsOneWidget);
      // The page is the project's files (vision 2026-10-20).
      expect(
        find.byKey(const ValueKey('project-files-browser')),
        findsOneWidget,
      );
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(_filesPage, findsNothing);
      expect(find.byType(ChatsHomeScreen), findsOneWidget);
    });

    testWidgets('Ctrl/Cmd+3 and search "Settings" open the Settings page', (
      tester,
    ) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      final signals = await _pump(tester, controller);
      expect(signals.dispatch(const SelectDestinationIntent(2)), isTrue);
      await tester.pumpAndSettle();
      expect(find.byType(SettingsScreen), findsOneWidget);
    });

    testWidgets('Ctrl/Cmd+1 lands on the home', (tester) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      final signals = await _pump(tester, controller);
      expect(signals.dispatch(const SelectDestinationIntent(0)), isTrue);
      await tester.pumpAndSettle();
      expect(find.byType(ChatsHomeScreen), findsOneWidget);
    });

    testWidgets('search "Files" (the browser) opens the Files page on it', (
      tester,
    ) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      final signals = await _pump(tester, controller);
      expect(
        signals.dispatch(const OpenProjectToolIntent(ProjectTool.files)),
        isTrue,
      );
      await tester.pumpAndSettle();
      // The Files page is the browser itself now: one page, one Back.
      expect(_filesPage, findsOneWidget);
      expect(
        find.byKey(const ValueKey('project-files-browser')),
        findsOneWidget,
      );
      expect(find.text('README.md'), findsOneWidget);
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(find.byType(ChatsHomeScreen), findsOneWidget);
    });

    testWidgets('Back on the home still asks for a second Back to exit', (
      tester,
    ) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      var exits = 0;
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        (call) async {
          if (call.method == 'SystemNavigator.pop') exits++;
          return null;
        },
      );
      addTearDown(
        () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
          SystemChannels.platform,
          null,
        ),
      );
      await _pump(tester, controller);
      await tester.binding.handlePopRoute();
      await tester.pump();
      expect(exits, 0);
      await tester.binding.handlePopRoute();
      await tester.pump();
      expect(exits, 1);
      await tester.pump(const Duration(seconds: 3));
    });
  });

  testWidgets('a PC keeps the rail with Files, and no gear', (tester) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    final signals = await _pump(
      tester,
      controller,
      size: const Size(1280, 800),
    );
    expect(find.byType(KitNavRail), findsOneWidget);
    expect(find.byKey(const ValueKey('home-shell-tab-files')), findsOneWidget);
    expect(find.byKey(const ValueKey('home-shell-settings')), findsNothing);
    // Ctrl/Cmd+2 picks the destination in place, as before.
    expect(signals.dispatch(const SelectDestinationIntent(1)), isTrue);
    await tester.pumpAndSettle();
    expect(_filesPage, findsNothing);
    expect(find.byKey(const ValueKey('project-files-browser')), findsOneWidget);
  });

  group('changed files of a turn', () {
    Part edit(String id, String path, Map<String, dynamic>? diff) => Part(
      id: id,
      messageID: 'm',
      type: 'tool',
      toolName: 'edit',
      toolState: ToolState.fromJson({
        'status': 'completed',
        'input': {'filePath': path},
        'metadata': {'filediff': ?diff},
      }, toolName: 'edit'),
    );
    MessageWithParts reply(List<Part> parts) => MessageWithParts(
      info: MessageInfo(id: 'm', sessionID: 's', role: 'assistant'),
      parts: parts,
    );

    test('sums the counts the tools reported, per file, in order', () {
      final files = RunResult.editedFileStats([
        reply([
          edit('a', '/p/game.js', {'additions': 20, 'deletions': 5}),
          edit('b', '/p/style.css', {'additions': 4, 'deletions': 0}),
          edit('c', '/p/game.js', {'additions': 10, 'deletions': 0}),
        ]),
      ]);
      expect(files.map((file) => file.name), ['game.js', 'style.css']);
      expect(files.first.added, 30);
      expect(files.first.removed, 5);
      expect(files.last.added, 4);
    });

    test('a change without numbers leaves the file without numbers', () {
      final files = RunResult.editedFileStats([
        reply([
          edit('a', '/p/game.js', {'additions': 20, 'deletions': 5}),
          edit('b', '/p/game.js', null),
        ]),
      ]);
      expect(files.single.added, isNull);
      expect(files.single.removed, isNull);
    });
  });
}
