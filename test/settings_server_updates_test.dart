import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/app_diagnostics_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/screens/saved_permissions_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _HealthyApi extends OpenCodeApi {
  _HealthyApi() : super(baseUrl: 'http://127.0.0.1:4096');

  @override
  Future<Health> health() async => Health(healthy: true, version: '1.18.23');
}

class _MemoryProfileStore extends ProfileStore {
  _MemoryProfileStore({required super.prefs, required this.savedProfile});

  final ServerProfile savedProfile;
  String? selectedID;

  @override
  List<ServerProfile> get profiles => [savedProfile];

  @override
  String? get activeId => selectedID;

  @override
  Future<void> setActiveId(String? id) async => selectedID = id;
}

class _EmptyPermissionRepository implements ProductRepository {
  TerminalShellSettings shellSettings = const TerminalShellSettings(
    selected: 'bash',
    options: [
      TerminalShellOption(path: '/bin/bash', name: 'bash', acceptable: true),
      TerminalShellOption(
        path: '/usr/bin/fish',
        name: 'fish',
        acceptable: false,
      ),
    ],
  );
  Object? shellError;
  Object? shellSelectError;
  int shellLoadCalls = 0;
  int shellSelectCalls = 0;
  String? selectedShell;
  String? upgradedTarget;
  Object? upgradeError;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<String> upgradeServer(String target) async {
    upgradedTarget = target;
    if (upgradeError case final error?) throw error;
    return target;
  }

  @override
  Future<TerminalShellSettings> loadTerminalShellSettings() async {
    shellLoadCalls++;
    if (shellError case final error?) throw error;
    return shellSettings;
  }

  @override
  Future<void> selectTerminalShell(String value) async {
    shellSelectCalls++;
    if (shellSelectError case final error?) throw error;
    selectedShell = value;
    shellSettings = TerminalShellSettings(
      selected: value,
      options: shellSettings.options,
    );
  }

  @override
  Future<List<SavedPermission>> listSavedPermissions() async => const [];

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<ConnectionController> _controllerFor(
  String baseUrl, {
  ProductRepository? repository,
}) async {
  SharedPreferences.setMockInitialValues({});
  final profile = ServerProfile(
    id: 'server',
    name: 'OpenCode server',
    baseUrl: baseUrl,
  );
  final store = _MemoryProfileStore(
    prefs: await SharedPreferences.getInstance(),
    savedProfile: profile,
  );
  await store.setActiveId(profile.id);
  return ConnectionController(store)
    ..api = _HealthyApi()
    ..repository = repository
    ..version = '1.18.23'
    ..status = StreamStatus.connected;
}

/// The hub-and-spoke Settings places every section one level deep; open the
/// category that owns the rows a test asserts on.
Future<void> _openCategory(WidgetTester tester, String key) async {
  final row = find.byKey(ValueKey(key));
  await tester.ensureVisible(row);
  await tester.tap(row);
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('settings exposes process-local app diagnostics', (tester) async {
    final controller = await _controllerFor(
      'http://127.0.0.1:4096',
      repository: _EmptyPermissionRepository(),
    );
    addTearDown(controller.dispose);
    controller.diagnostics.record(
      StateError('handled failure'),
      null,
      source: 'flutter',
    );

    await tester.pumpWidget(
      MaterialApp(home: SettingsScreen(controller: controller)),
    );
    await tester.pumpAndSettle();
    await _openCategory(tester, 'settings-category-diagnostics');

    await tester.scrollUntilVisible(
      find.byKey(const Key('app-diagnostics-entry')),
      320,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text('1 handled error kept in memory'), findsOneWidget);
    await tester.tap(find.byKey(const Key('app-diagnostics-entry')));
    await tester.pumpAndSettle();

    expect(find.byType(AppDiagnosticsScreen), findsOneWidget);
  });

  testWidgets('managed local profile opens the in-app updater', (tester) async {
    final controller = await _controllerFor('http://127.0.0.1:4096');
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        home: SettingsScreen(controller: controller),
        routes: {
          '/termux-setup': (_) => const Scaffold(body: Text('Managed updater')),
        },
      ),
    );
    await tester.pumpAndSettle();
    await _openCategory(tester, 'settings-category-server');

    expect(find.text('Update managed OpenCode'), findsOneWidget);
    await tester.ensureVisible(find.byKey(const Key('server-updates-tile')));
    await tester.tap(find.byKey(const Key('server-updates-tile')));
    await tester.pumpAndSettle();
    expect(find.text('Managed updater'), findsOneWidget);
  });

  testWidgets('remote profile clearly remains externally managed', (
    tester,
  ) async {
    final controller = await _controllerFor('http://100.64.0.10:4747');
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(home: SettingsScreen(controller: controller)),
    );
    await tester.pumpAndSettle();
    await _openCategory(tester, 'settings-category-server');

    expect(find.text('Server updates are managed externally'), findsOneWidget);
    expect(
      find.textContaining('official upgrade and model-refresh commands'),
      findsOneWidget,
    );
  });

  testWidgets('remote update event offers the exact generated upgrade', (
    tester,
  ) async {
    final repository = _EmptyPermissionRepository();
    final controller = await _controllerFor(
      'http://100.64.0.10:4747',
      repository: repository,
    );
    addTearDown(controller.dispose);
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'installation.update-available',
        properties: const {'version': '1.19.0'},
      ),
    );

    await tester.pumpWidget(
      MaterialApp(home: SettingsScreen(controller: controller)),
    );
    await tester.pumpAndSettle();
    await _openCategory(tester, 'settings-category-server');

    expect(find.text('Update OpenCode to 1.19.0'), findsOneWidget);
    expect(find.textContaining('Current server: 1.18.23'), findsOneWidget);
    await tester.tap(find.byKey(const Key('server-updates-tile')));
    await tester.pumpAndSettle();

    expect(find.text('Update remote OpenCode?'), findsOneWidget);
    expect(
      find.textContaining('must be restarted on its host'),
      findsOneWidget,
    );
    await tester.tap(find.byKey(const Key('confirm-server-upgrade')));
    await tester.pumpAndSettle();

    expect(repository.upgradedTarget, '1.19.0');
    expect(controller.availableServerVersion, isNull);
    expect(controller.installedServerVersion, '1.19.0');
    expect(find.text('Restart OpenCode to use 1.19.0'), findsOneWidget);
    expect(
      find.textContaining('Restart its server process to use it'),
      findsOneWidget,
    );
  });

  testWidgets('failed remote upgrade remains visible and retryable', (
    tester,
  ) async {
    final repository = _EmptyPermissionRepository()
      ..upgradeError = const ProductException('Unknown installation method');
    final controller = await _controllerFor(
      'http://100.64.0.10:4747',
      repository: repository,
    );
    addTearDown(controller.dispose);
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'installation.update-available',
        properties: const {'version': '1.19.0'},
      ),
    );

    await tester.pumpWidget(
      MaterialApp(home: SettingsScreen(controller: controller)),
    );
    await tester.pumpAndSettle();
    await _openCategory(tester, 'settings-category-server');
    await tester.scrollUntilVisible(
      find.byKey(const Key('server-updates-tile')),
      120,
      scrollable: find.byType(Scrollable).first,
    );
    tester
        .widget<ListTile>(find.byKey(const Key('server-updates-tile')))
        .onTap!();
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('confirm-server-upgrade')));
    await tester.pumpAndSettle();

    expect(find.textContaining('Unknown installation method'), findsOneWidget);
    expect(controller.availableServerVersion, '1.19.0');
    expect(controller.installedServerVersion, isNull);
    expect(
      tester
          .widget<ListTile>(find.byKey(const Key('server-updates-tile')))
          .onTap,
      isNotNull,
    );
  });

  testWidgets('remote update confirmation fits a compact large-text phone', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 480);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controllerFor(
      'http://100.64.0.10:4747',
      repository: _EmptyPermissionRepository(),
    );
    addTearDown(controller.dispose);
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'installation.update-available',
        properties: const {'version': '1.19.0'},
      ),
    );

    await tester.pumpWidget(
      MaterialApp(
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: const TextScaler.linear(2)),
          child: child!,
        ),
        home: SettingsScreen(controller: controller),
      ),
    );
    await tester.pumpAndSettle();
    await _openCategory(tester, 'settings-category-server');
    await tester.scrollUntilVisible(
      find.byKey(const Key('server-updates-tile')),
      120,
      scrollable: find.byType(Scrollable).first,
    );
    tester
        .widget<ListTile>(find.byKey(const Key('server-updates-tile')))
        .onTap!();
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.text('Update remote OpenCode?'), findsOneWidget);
    expect(find.byKey(const Key('confirm-server-upgrade')), findsOneWidget);
  });

  testWidgets('settings exposes current-project always allowed actions', (
    tester,
  ) async {
    final controller = await _controllerFor(
      'http://127.0.0.1:4096',
      repository: _EmptyPermissionRepository(),
    );
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(home: SettingsScreen(controller: controller)),
    );
    await tester.pumpAndSettle();
    await _openCategory(tester, 'settings-category-privacy');

    final entry = find.byKey(const ValueKey('saved-permissions-entry'));
    await tester.drag(find.byType(ListView), const Offset(0, -500));
    await tester.pumpAndSettle();
    await tester.ensureVisible(entry);
    expect(find.text('Always allowed actions'), findsOneWidget);
    await tester.tap(entry);
    await tester.pumpAndSettle();

    expect(find.byType(SavedPermissionsScreen), findsOneWidget);
    expect(find.text('No always allowed actions'), findsOneWidget);
  });

  testWidgets('settings exposes the persisted native appearance choices', (
    tester,
  ) async {
    final controller = await _controllerFor('http://127.0.0.1:4096');
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(home: SettingsScreen(controller: controller)),
    );
    await tester.pumpAndSettle();
    await _openCategory(tester, 'settings-category-appearance');

    final entry = find.byKey(const ValueKey('appearance-settings-entry'));
    await tester.scrollUntilVisible(
      entry,
      200,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text('Dark'), findsOneWidget);
    await tester.tap(entry);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('appearance-system')));
    await tester.pumpAndSettle();

    expect(controller.appearance.value, AppAppearance.system);
    expect(find.text('Follow Android'), findsOneWidget);
  });

  testWidgets(
    'settings selects a server shell through the current repository',
    (tester) async {
      final initial = _EmptyPermissionRepository();
      final replacement = _EmptyPermissionRepository();
      final controller = await _controllerFor(
        'http://127.0.0.1:4096',
        repository: initial,
      );
      addTearDown(controller.dispose);

      await tester.pumpWidget(
        MaterialApp(home: SettingsScreen(controller: controller)),
      );
      await tester.pumpAndSettle();
      await _openCategory(tester, 'settings-category-coding');
      final entry = find.byKey(const ValueKey('default-shell-settings-entry'));
      await tester.scrollUntilVisible(
        entry,
        200,
        scrollable: find.byType(Scrollable).first,
      );
      expect(find.text('bash'), findsOneWidget);
      await tester.tap(entry);
      await tester.pumpAndSettle();

      expect(find.text('Automatic (server default)'), findsOneWidget);
      expect(find.text('fish'), findsOneWidget);
      expect(
        find.textContaining(
          'Terminal only; OpenCode uses a compatible fallback',
        ),
        findsOneWidget,
      );

      controller.repository = replacement;
      await tester.tap(
        find.byKey(const ValueKey('server-shell-/usr/bin/fish')),
      );
      await tester.pumpAndSettle();

      expect(initial.shellSelectCalls, 0);
      expect(replacement.shellSelectCalls, 1);
      expect(replacement.selectedShell, 'fish');
      expect(find.text('Default shell updated'), findsOneWidget);
      expect(find.text('fish'), findsOneWidget);
    },
  );

  testWidgets('shell failure remains scoped and can be retried', (
    tester,
  ) async {
    final repository = _EmptyPermissionRepository()
      ..shellError = const ProductException('Shell endpoint unavailable');
    final controller = await _controllerFor(
      'http://127.0.0.1:4096',
      repository: repository,
    );
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(home: SettingsScreen(controller: controller)),
    );
    await tester.pumpAndSettle();
    await _openCategory(tester, 'settings-category-coding');
    final entry = find.byKey(const ValueKey('default-shell-settings-entry'));
    await tester.scrollUntilVisible(
      entry,
      200,
      scrollable: find.byType(Scrollable).first,
    );

    expect(find.textContaining('Shell endpoint unavailable'), findsOneWidget);
    expect(find.text('Selected model'), findsOneWidget);
    repository.shellError = null;
    await tester.tap(entry);
    await tester.pumpAndSettle();

    expect(repository.shellLoadCalls, 2);
    expect(find.text('bash'), findsOneWidget);
  });

  testWidgets('failed shell update retains the server-reported selection', (
    tester,
  ) async {
    final repository = _EmptyPermissionRepository()
      ..shellSelectError = const ProductException('Config write failed');
    final controller = await _controllerFor(
      'http://127.0.0.1:4096',
      repository: repository,
    );
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(home: SettingsScreen(controller: controller)),
    );
    await tester.pumpAndSettle();
    await _openCategory(tester, 'settings-category-coding');
    final entry = find.byKey(const ValueKey('default-shell-settings-entry'));
    await tester.scrollUntilVisible(
      entry,
      200,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(entry);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('server-shell-/usr/bin/fish')));
    await tester.pumpAndSettle();

    expect(repository.shellSelectCalls, 1);
    expect(repository.shellSettings.selected, 'bash');
    expect(find.textContaining('Config write failed'), findsOneWidget);
    expect(find.text('bash'), findsOneWidget);
  });

  testWidgets('default shell picker fits a compact large-text phone', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 480);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final repository = _EmptyPermissionRepository();
    final controller = await _controllerFor(
      'http://127.0.0.1:4096',
      repository: repository,
    );
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: const TextScaler.linear(2)),
          child: child!,
        ),
        home: SettingsScreen(controller: controller),
      ),
    );
    await tester.pumpAndSettle();
    await _openCategory(tester, 'settings-category-coding');
    final entry = find.byKey(const ValueKey('default-shell-settings-entry'));
    await tester.scrollUntilVisible(
      entry,
      200,
      scrollable: find.byType(Scrollable).first,
    );
    expect(tester.takeException(), isNull);
    tester.widget<ListTile>(entry).onTap!();
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.byType(BottomSheet), findsOneWidget);
    final pickerScroll = find.descendant(
      of: find.byType(BottomSheet),
      matching: find.byType(Scrollable),
    );
    await tester.scrollUntilVisible(
      find.text('Automatic (server default)'),
      120,
      scrollable: pickerScroll,
    );
    expect(find.text('Automatic (server default)'), findsOneWidget);
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('server-shell-/bin/bash')),
      120,
      scrollable: pickerScroll,
    );
    expect(
      find.byKey(const ValueKey('server-shell-/bin/bash')),
      findsOneWidget,
    );
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('server-shell-/usr/bin/fish')),
      120,
      scrollable: pickerScroll,
    );
    expect(
      find.byKey(const ValueKey('server-shell-/usr/bin/fish')),
      findsOneWidget,
    );
  });

  testWidgets('settings hub lists every category on a compact large-text '
      'phone', (tester) async {
    await tester.binding.setSurfaceSize(const Size(320, 640));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final controller = await _controllerFor(
      'http://127.0.0.1:4096',
      repository: _EmptyPermissionRepository(),
    );
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: const TextScaler.linear(2)),
          child: child!,
        ),
        home: SettingsScreen(controller: controller),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('settings-connection-summary')),
      findsOneWidget,
    );
    for (final key in const [
      'settings-category-server',
      'settings-category-coding',
      'settings-category-background',
      'settings-category-appearance',
      'settings-category-privacy',
      'settings-category-diagnostics',
      'settings-category-about',
    ]) {
      await tester.scrollUntilVisible(
        find.byKey(ValueKey(key)),
        240,
        scrollable: find.byType(Scrollable).first,
      );
      expect(find.byKey(ValueKey(key)), findsOneWidget);
    }
    expect(tester.takeException(), isNull);
  });
}
