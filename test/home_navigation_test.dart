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
import 'package:opencode_mobile/ui/screens/activity_screen.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _ShellApi extends OpenCodeApi {
  _ShellApi() : super(baseUrl: 'http://localhost');

  @override
  Future<List<Session>> sessions() async => [];

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => [];
}

class _ShellRepository implements ProductRepository {
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

class _ShellProfileStore extends ProfileStore {
  final ServerProfile? profile;

  _ShellProfileStore({required super.prefs, this.profile});

  @override
  List<ServerProfile> get profiles => [?profile];

  @override
  String? get activeId => profile?.id;
}

Future<ConnectionController> _controller({String? profileName}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final store = _ShellProfileStore(
    prefs: prefs,
    profile: profileName == null
        ? null
        : ServerProfile(
            id: 'local',
            name: profileName,
            baseUrl: 'http://localhost:4096',
          ),
  );
  return ConnectionController(store)
    ..api = _ShellApi()
    ..repository = _ShellRepository()
    ..status = StreamStatus.connected;
}

Future<void> _pumpShell(
  WidgetTester tester,
  ConnectionController controller,
) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: const MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: HomeScreen(),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('phone shell uses product bottom navigation', (tester) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller();
    addTearDown(controller.dispose);

    await _pumpShell(tester, controller);

    expect(find.byType(NavigationBar), findsOneWidget);
    expect(find.byType(NavigationRail), findsNothing);
    expect(find.text('Workspace'), findsWidgets);
    expect(find.text('Files'), findsOneWidget);
    // Audit §5: Activity took Terminal's navigation slot.
    expect(find.text('Activity'), findsWidgets);
    expect(find.text('Terminal'), findsNothing);
    expect(find.text('More'), findsOneWidget);
    expect(find.text('API'), findsNothing);
    expect(find.text('Guide'), findsNothing);
  });

  testWidgets('one pending badge, on the Activity destination', (tester) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller();
    addTearDown(controller.dispose);

    await _pumpShell(tester, controller);
    expect(find.byType(Badge), findsNothing);

    controller.permissions = {
      'perm-1': PermissionRequest(
        id: 'perm-1',
        sessionID: 'session-1',
        permission: 'edit',
        patterns: const ['lib/main.dart'],
      ),
    };
    controller.notifyListeners();
    await tester.pump();

    // UX-P0-01: exactly one global badge, and the duplicate app-bar entry
    // points are gone.
    final badge = find.byKey(const ValueKey('activity-pending-badge'));
    expect(badge, findsOneWidget);
    expect(find.byType(Badge), findsOneWidget);
    expect(
      find.descendant(of: find.byType(NavigationBar), matching: badge),
      findsOneWidget,
    );
    expect(find.byTooltip('Mission Control'), findsNothing);
    expect(find.byTooltip('Pending requests'), findsNothing);
    // The tune/model action stays per the audit.
    expect(find.byTooltip('Model / agent'), findsOneWidget);
  });

  testWidgets('the Activity tab shows cross-session sections', (tester) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller();
    addTearDown(controller.dispose);

    await _pumpShell(tester, controller);
    await tester.tap(find.byIcon(Icons.notifications_outlined));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));

    expect(find.byType(ActivityScreen), findsOneWidget);
    expect(
      tester.widget<Text>(find.byKey(const ValueKey('current-tab-title'))).data,
      'Activity',
    );
    expect(find.text('Nothing needs attention'), findsWidgets);
  });

  testWidgets('failed reconnect keeps the product shell and location visible', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller(profileName: 'This device (Termux)')
      ..api = null
      ..repository = null
      ..status = StreamStatus.disconnected
      ..lastError = 'Endpoint is unavailable';
    addTearDown(controller.dispose);

    await _pumpShell(tester, controller);

    expect(find.byType(HomeScreen), findsOneWidget);
    expect(find.byType(NavigationBar), findsOneWidget);
    expect(
      find.byKey(const ValueKey('connection-status-banner')),
      findsOneWidget,
    );
    expect(find.text('Connection lost'), findsOneWidget);
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('connection-status-banner')),
        matching: find.text('Try again'),
      ),
      findsOneWidget,
    );
    // The raw error and the secondary action live behind Details.
    await tester.tap(find.byKey(const ValueKey('connection-banner-details')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.textContaining('Endpoint is unavailable'), findsOneWidget);
    expect(find.text('Change server'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
    controller.dispose();
  });

  testWidgets('recovery banner fits a 320dp phone with 2x text', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller(profileName: 'This device (Termux)')
      ..api = null
      ..repository = null
      ..status = StreamStatus.disconnected
      ..lastError = 'Endpoint is unavailable';
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Builder(
            builder: (context) => MediaQuery(
              data: MediaQuery.of(
                context,
              ).copyWith(textScaler: const TextScaler.linear(2)),
              child: const HomeScreen(),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.descendant(
        of: find.byKey(const ValueKey('connection-status-banner')),
        matching: find.text('Try again'),
      ),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('connection-banner-details')),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
    controller.dispose();
  });

  testWidgets('automatic SSE reconnect still offers a manual retry', (
    tester,
  ) async {
    final controller = await _controller(profileName: 'This device (Termux)')
      ..status = StreamStatus.reconnecting;
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(controller)],
        child: const MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: HomeScreen(),
        ),
      ),
    );
    await tester.pump();

    final retry = tester.widget<TextButton>(
      find.widgetWithText(TextButton, 'Try again'),
    );
    expect(retry.onPressed, isNotNull);
    await tester.tap(find.byKey(const ValueKey('connection-banner-details')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.text('Change server'), findsOneWidget);
  });

  testWidgets('phone header separates long local server and workspace labels', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(411, 891);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller(profileName: 'This device (Termux)');
    addTearDown(controller.dispose);

    await _pumpShell(tester, controller);

    final profile = find.byKey(const ValueKey('server-profile-title'));
    final tab = find.byKey(const ValueKey('current-tab-title'));
    expect(profile, findsOneWidget);
    expect(tab, findsOneWidget);
    expect(tester.getRect(profile).bottom, lessThan(tester.getRect(tab).top));
    expect(
      tester.getSemantics(profile).label,
      contains('Server: This device (Termux)'),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('tablet shell switches to navigation rail', (tester) async {
    tester.view.physicalSize = const Size(1024, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller();
    addTearDown(controller.dispose);

    await _pumpShell(tester, controller);

    expect(find.byType(NavigationRail), findsOneWidget);
    expect(find.byType(NavigationBar), findsNothing);
    final profile = tester.getRect(
      find.byKey(const ValueKey('server-profile-title')),
    );
    final tab = tester.getRect(find.byKey(const ValueKey('current-tab-title')));
    expect((profile.center.dy - tab.center.dy).abs(), lessThan(2));
  });
}
