import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/main.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit_nav.dart';
import 'package:opencode_mobile/ui/kit/kit_top_bar.dart';
import 'package:opencode_mobile/ui/screens/review_workspace.dart';
import 'package:opencode_mobile/ui/widgets/pickers.dart' show ModelCatalogView;
import 'package:opencode_mobile/ui/screens/chat/permission_sheet.dart'
    show showPermissionSheet;
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/ui/screens/terminal_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:xterm/xterm.dart';

import 'support/first_run_path.dart';
import 'support/server_editor.dart';
import 'support/product_ui_regression_fixtures.dart';

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

class _TerminalRepository extends LocationRepository {
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

class _DelayedTerminalRepository extends LocationRepository {
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
  Future<void> connect(
    ServerProfile profile, {
    bool redetectOnFailure = true,
  }) async {
    status = StreamStatus.connecting;
    notifyListeners();
    await ready.future;
    api = TestApi();
    repository = LocationRepository();
    version = 'test';
    status = StreamStatus.connected;
    notifyListeners();
  }
}

class _ImmediateController extends ConnectionController {
  _ImmediateController(super.store);

  @override
  Future<void> connect(
    ServerProfile profile, {
    bool redetectOnFailure = true,
  }) async {
    await store.setActiveId(profile.id);
    api = TestApi();
    repository = LocationRepository();
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

Future<ConnectionController> _controllerWithoutApi() async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: prefs));
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    ReviewWorkspace.clearCache();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

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
    expect(find.byType(KitNavBar), findsNothing);
    expect(find.byType(KitNavRail), findsNothing);

    ready.complete();
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    await tester.pump(const Duration(milliseconds: 50));
    expect(find.byType(KitNavRail), findsOneWidget);
  });

  testWidgets(
    'terminal reconnects, forwards control input, and closes channels',
    (tester) async {
      final repository = _TerminalRepository();
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: TerminalSurface(
            repository: repository,
            process: terminalProcess,
          ),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));
      expect(find.byType(TerminalView), findsOneWidget);

      await tester.tap(find.byKey(const ValueKey('terminal-surface-menu')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('terminal-accessible-mode')));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const Key('terminal-accessible-input')),
        findsOneWidget,
      );
      expect(find.bySemanticsLabel('Terminal transcript'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('terminal-surface-menu')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('terminal-accessible-mode')));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const ValueKey('terminal-key-interrupt')));
      expect(repository.channels.first.writes, contains('\x03'));

      await tester.tap(find.byKey(const ValueKey('terminal-surface-menu')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('terminal-reconnect')));
      await tester.pumpAndSettle();
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
    final controller = SignalController(ProfileStore(prefs: prefs))
      ..api = TestApi()
      ..repository = oldRepository
      ..status = StreamStatus.connected;
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: TerminalScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('terminal-new')));
    await tester.pump();
    expect(oldRepository.createCalls, 1);

    controller.signalLocation(newRepository);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(newRepository.terminalLoads, 1);

    oldRepository.createResult.complete(terminalProcess);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 20));

    expect(find.byType(TerminalSurface), findsNothing);
    expect(newRepository.terminalLoads, 1);
    expect(
      tester.getSemantics(find.byKey(const ValueKey('terminal-new'))),
      isSemantics(isEnabled: true, hasTapAction: true),
    );
  });

  testWidgets('stale terminal rename does not reload a new workspace', (
    tester,
  ) async {
    final repository = _DelayedTerminalRepository(
      processes: const [terminalProcess],
    );
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final controller = SignalController(ProfileStore(prefs: prefs))
      ..api = TestApi()
      ..repository = repository
      ..status = StreamStatus.connected;
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: TerminalScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();
    await tester.longPress(find.text('Shell'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('terminal-menu-rename')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'Renamed');
    await tester.tap(find.byKey(const ValueKey('terminal-rename-confirm')));
    await tester.pump();
    expect(repository.renameCalls, 1);

    repository.setLocation(workspace: 'new-workspace');
    controller.signalLocation(repository);
    // The confirm remains busy until the deliberately delayed write ends.
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
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
    final controller = SignalController(ProfileStore(prefs: prefs))
      ..api = TestApi()
      ..repository = oldRepository
      ..status = StreamStatus.connected;
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: TerminalScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();
    await tester.longPress(find.text('Shell'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('terminal-menu-remove')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('terminal-remove-confirm')));
    await tester.pump();
    expect(oldRepository.removeCalls, 1);

    controller.signalLocation(newRepository);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
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
    // P4.2a: an Inbox row now lands on the request's card in its chat; the
    // card's Details opens this same shared sheet, opened directly here.
    await tester.pumpWidget(const MaterialApp(home: Scaffold()));
    unawaited(
      showPermissionSheet(
        tester.element(find.byType(Scaffold)),
        permission: controller.permissions['permission-1']!,
        controller: controller,
      ),
    );
    await tester.pumpAndSettle();

    // The shared request sheet: its pinned Allow once and
    // Reject and the "Always allow" switch must all render at 280dp.
    final sheet = find.byKey(const Key('permission-sheet'));
    expect(sheet, findsOneWidget);
    expect(
      find.descendant(of: sheet, matching: find.text('Allow once')),
      findsOneWidget,
    );
    expect(
      find.descendant(of: sheet, matching: find.text('Reject')),
      findsOneWidget,
    );
    expect(find.byKey(const Key('permission-allow-always')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('model details remain scrollable on a short screen', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 360);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final repository = LocationRepository()
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
    final controller = await regressionController(repository: repository);
    controller.catalog = repository.catalog;
    controller.catalogDetailed = true;
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: ModelCatalogView(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();
    await tester.drag(find.byType(ListView), const Offset(0, -500));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Large model'));
    await tester.pumpAndSettle();

    // Details now stay under the selected model; thinking levels live in
    // the footer's menu and must remain reachable on a short screen.
    await tester.tap(find.byKey(const Key('model-picker-thinking')));
    await tester.pumpAndSettle();
    final lastVariant = find.byKey(
      const ValueKey('model-variant-model-variant-23'),
    );
    await tester.scrollUntilVisible(
      lastVariant,
      120,
      scrollable: find.byType(Scrollable).last,
    );
    await tester.pumpAndSettle();
    expect(lastVariant.hitTestable(), findsOneWidget);
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
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
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
    await openFirstRunConnect(tester);
    await openServerManualAddress(tester);

    // The connect screen is titled with the agent the person chose.
    expect(find.widgetWithText(KitTopBar, 'OpenCode'), findsOneWidget);
    expect(find.byKey(const ValueKey('server-profile-editor')), findsOneWidget);
    expect(find.byType(AlertDialog), findsNothing);
    expect(find.text('Server URL'), findsOneWidget);
    expect(store.profiles, isEmpty);
    expect(find.text('Start the server'), findsNothing);
  });
}
