import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/offline_queue.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
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
  BackgroundLiveController? backgroundLive,
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
  return ConnectionController(store, backgroundLive: backgroundLive)
    ..api = _HealthyApi()
    ..repository = repository
    ..version = '1.18.23'
    ..status = StreamStatus.connected;
}

/// A live-background controller that never touches the platform channel, so
/// the settings screen can be driven through a simulated native event.
Future<BackgroundLiveController> _liveController(
  SharedPreferences preferences, {
  bool enabled = true,
}) async {
  final controller = BackgroundLiveController(
    preferences: preferences,
    invoke: (method, [arguments]) async {
      if (method == 'getBackgroundPause') {
        return const {
          'supported': true,
          'active': false,
          'paused': false,
          'reason': 'none',
          'at': null,
          'canResume': false,
        };
      }
      return {
        'enabled': enabled,
        'active': enabled,
        'notificationGranted': true,
        'batteryOptimizationIgnored': true,
      };
    },
  );
  await controller.restore();
  return controller;
}

/// The hub-and-spoke Settings places every section one level deep; open the
/// category that owns the rows a test asserts on.
Future<void> _openCategory(WidgetTester tester, String key) async {
  final row = find.byKey(ValueKey(key));
  await tester.ensureVisible(row);
  await tester.pumpAndSettle();
  await tester.tap(row);
  await tester.pumpAndSettle();
}

Finder get _verticalScroll => find
    .byWidgetPredicate(
      (widget) =>
          widget is Scrollable &&
          axisDirectionToAxis(widget.axisDirection) == Axis.vertical,
    )
    .first;

Future<void> _tapVisible(WidgetTester tester, Finder target) async {
  await tester.ensureVisible(target);
  await tester.pumpAndSettle();
  expect(target.hitTestable(), findsOneWidget);
  await tester.tap(target);
}

void main() {
  testWidgets('Settings has one Report a problem row with an error badge', (
    tester,
  ) async {
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
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        // Report a problem is the hub's own row (P8.2), with the count of
        // errors kept as its badge.
        home: SettingsScreen(controller: controller),
      ),
    );
    await tester.pumpAndSettle();

    await tester.scrollUntilVisible(
      find.byKey(const Key('library-report-bug')),
      320,
      scrollable: _verticalScroll,
    );
    final badge = find.descendant(
      of: find.byKey(const Key('library-report-bug')),
      matching: find.byType(KitRowValue),
    );
    expect(tester.widget<KitRowValue>(badge).count, 1);
    expect(tester.widget<KitRowValue>(badge).value, '1 error kept');
    expect(
      find.descendant(of: badge, matching: find.text('1')),
      findsOneWidget,
    );
    await tester.ensureVisible(find.byKey(const Key('library-report-bug')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('library-report-bug')));
    await tester.pumpAndSettle();

    expect(find.byType(AppDiagnosticsScreen), findsOneWidget);
  });

  testWidgets('managed local profile opens the in-app updater', (tester) async {
    final controller = await _controllerFor('http://127.0.0.1:4096');
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: SettingsScreen(controller: controller),
        routes: {
          '/this-phone': (_) => const Scaffold(body: Text('Managed updater')),
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
    final controller = await _controllerFor('http://203.0.113.10:4747');
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: SettingsScreen(controller: controller),
      ),
    );
    await tester.pumpAndSettle();
    await _openCategory(tester, 'settings-category-server');

    // The row is the copy and names the server; no trailing copy icon.
    expect(find.textContaining('Copy update commands for '), findsOneWidget);
    expect(
      find.text(
        "Run them in a terminal on the server's computer; this app can't "
        'update it.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('remote update event offers the exact generated upgrade', (
    tester,
  ) async {
    final repository = _EmptyPermissionRepository();
    final controller = await _controllerFor(
      'http://203.0.113.10:4747',
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
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: SettingsScreen(controller: controller),
      ),
    );
    await tester.pumpAndSettle();
    await _openCategory(tester, 'settings-category-server');

    expect(find.text('Update OpenCode to 1.19.0'), findsOneWidget);
    // The running version is said once, on the health row (R3).
    expect(
      find.text(
        "Uses OpenCode's official installer; restart the server afterwards.",
      ),
      findsOneWidget,
    );
    await tester.tap(find.byKey(const Key('server-updates-tile')));
    await tester.pumpAndSettle();

    expect(find.text('Update remote OpenCode?'), findsOneWidget);
    expect(find.textContaining('on its computer to use'), findsOneWidget);
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
      'http://203.0.113.10:4747',
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
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: SettingsScreen(controller: controller),
      ),
    );
    await tester.pumpAndSettle();
    await _openCategory(tester, 'settings-category-server');
    await tester.scrollUntilVisible(
      find.byKey(const Key('server-updates-tile')),
      120,
      scrollable: _verticalScroll,
    );
    tester
        .widget<KitRow>(find.byKey(const Key('server-updates-tile')))
        .onTap!();
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('confirm-server-upgrade')));
    await tester.pumpAndSettle();

    expect(find.textContaining('Unknown installation method'), findsOneWidget);
    expect(controller.availableServerVersion, '1.19.0');
    expect(controller.installedServerVersion, isNull);
    expect(
      tester.widget<KitRow>(find.byKey(const Key('server-updates-tile'))).onTap,
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
      'http://203.0.113.10:4747',
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
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
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
      scrollable: _verticalScroll,
    );
    tester
        .widget<KitRow>(find.byKey(const Key('server-updates-tile')))
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
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: NotificationsSettingsScreen(controller: controller),
      ),
    );
    await tester.pumpAndSettle();
    // Durable grants sit inside What runs by itself (P6.1), the last section
    // of Notifications and background: they are about how the agent works.
    expect(find.byKey(const ValueKey('settings-automation')), findsNothing);
    await tester.ensureVisible(
      find.byKey(const ValueKey('automation-saved-permissions')),
    );
    await tester.pumpAndSettle();
    final inside = find.byKey(const ValueKey('automation-saved-permissions'));
    expect(find.text('Always allowed actions'), findsOneWidget);
    await tester.tap(inside);
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
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: SettingsScreen(controller: controller),
      ),
    );
    await tester.pumpAndSettle();
    await _openCategory(tester, 'settings-category-appearance');

    final entry = find.byKey(const ValueKey('appearance-settings-entry'));
    await tester.scrollUntilVisible(entry, 200, scrollable: _verticalScroll);
    expect(find.text('Dark'), findsOneWidget);
    // Light or dark is chosen inline and applies at once (the separate
    // light-or-dark sheet was removed by slice-P3.1).
    await tester.tap(find.byKey(const ValueKey('appearance-mode-system')));
    await tester.pumpAndSettle();
    expect(controller.appearance.value, AppAppearance.system);
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
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: SettingsScreen(controller: controller),
        ),
      );
      await tester.pumpAndSettle();
      final entry = find.byKey(const ValueKey('default-shell-settings-entry'));
      await tester.scrollUntilVisible(entry, 200, scrollable: _verticalScroll);
      expect(find.text('bash'), findsOneWidget);
      await _tapVisible(tester, entry);
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
      // The selected value on the row is the save confirmation.
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
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: SettingsScreen(controller: controller),
      ),
    );
    await tester.pumpAndSettle();
    final entry = find.byKey(const ValueKey('default-shell-settings-entry'));
    await tester.scrollUntilVisible(entry, 200, scrollable: _verticalScroll);

    expect(find.textContaining('Shell endpoint unavailable'), findsOneWidget);
    // Scoped: the neighbouring default still renders.
    expect(
      find.byKey(const ValueKey('settings-model-and-mode')),
      findsOneWidget,
    );
    repository.shellError = null;
    await _tapVisible(tester, entry);
    await tester.pumpAndSettle();

    expect(repository.shellLoadCalls, 2);
    expect(find.text('bash'), findsOneWidget);

    // A failed refresh with cached choices must retry the request, rather than
    // opening the stale chooser behind a row labelled "Tap to retry".
    repository.shellError = const ProductException('Shell refresh unavailable');
    // The real order: the hub hosts lifecycle listeners that assert on it.
    for (final state in const [
      AppLifecycleState.inactive,
      AppLifecycleState.hidden,
      AppLifecycleState.paused,
      AppLifecycleState.hidden,
      AppLifecycleState.inactive,
      AppLifecycleState.resumed,
    ]) {
      tester.binding.handleAppLifecycleStateChanged(state);
    }
    await tester.pumpAndSettle();
    expect(find.textContaining('Shell refresh unavailable'), findsOneWidget);
    final loadsBeforeRetry = repository.shellLoadCalls;
    repository.shellError = null;
    await _tapVisible(tester, entry);
    await tester.pumpAndSettle();
    expect(repository.shellLoadCalls, loadsBeforeRetry + 1);
    expect(find.byKey(const ValueKey('server-shell-/bin/bash')), findsNothing);
    expect(find.text('bash'), findsOneWidget);
    // The resume above arms the phone server's recovery check (6ed0ec26),
    // which the connection scope owns: end that scope inside the test.
    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
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
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: SettingsScreen(controller: controller),
      ),
    );
    await tester.pumpAndSettle();
    final entry = find.byKey(const ValueKey('default-shell-settings-entry'));
    await tester.scrollUntilVisible(entry, 200, scrollable: _verticalScroll);
    await _tapVisible(tester, entry);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('server-shell-/usr/bin/fish')));
    await tester.pumpAndSettle();

    expect(repository.shellSelectCalls, 1);
    expect(repository.shellSettings.selected, 'bash');
    expect(find.textContaining('Config write failed'), findsOneWidget);
    // The failed-save reason occupies the row; reopening still marks the
    // server-reported choice, never the attempted fish selection.
    await _tapVisible(tester, entry);
    await tester.pumpAndSettle();
    expect(find.text('bash'), findsOneWidget);
    expect(
      tester
          .widget<KitChoiceRow<String>>(
            find.byKey(const ValueKey('server-shell-/bin/bash')),
          )
          .selected,
      isTrue,
    );
    expect(
      tester
          .widget<KitChoiceRow<String>>(
            find.byKey(const ValueKey('server-shell-/usr/bin/fish')),
          )
          .selected,
      isFalse,
    );
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
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
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
    final entry = find.byKey(const ValueKey('default-shell-settings-entry'));
    await tester.scrollUntilVisible(entry, 200, scrollable: _verticalScroll);
    expect(tester.takeException(), isNull);
    tester.widget<KitRow>(entry).onTap!();
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
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
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
      'settings-model-and-mode',
      'settings-tools',
      'default-shell-settings-entry',
      'settings-category-server',
      'settings-category-background',
      'settings-category-appearance',
      'settings-category-privacy',
      'settings-setup-guide',
      'settings-about-notices',
    ]) {
      await tester.scrollUntilVisible(
        find.byKey(ValueKey(key)),
        240,
        scrollable: _verticalScroll,
      );
      expect(find.byKey(ValueKey(key)), findsOneWidget);
    }
    expect(tester.takeException(), isNull);
  });

  testWidgets('privacy settings size and clear the unsent work on device', (
    tester,
  ) async {
    final controller = await _controllerFor(
      'http://127.0.0.1:4096',
      repository: _EmptyPermissionRepository(),
    );
    addTearDown(controller.dispose);
    await controller.queuePrompt(
      QueuedPrompt(
        id: 'queued-1',
        profileID: 'server',
        sessionID: 'session-1',
        text: 'unsent prompt',
        createdAt: DateTime.now().millisecondsSinceEpoch,
      ),
    );
    await controller.saveSessionDraft('session-1', 'half-typed thought');

    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: SettingsScreen(controller: controller),
      ),
    );
    await tester.pumpAndSettle();
    await _openCategory(tester, 'settings-category-privacy');

    // The readout names both stores and the expiry, so "where did my draft
    // go" has an answer before it happens.
    final usage = find.byKey(const ValueKey('local-storage-usage'));
    await tester.scrollUntilVisible(usage, 200, scrollable: _verticalScroll);
    expect(find.textContaining('1 queued prompt'), findsOneWidget);
    expect(find.textContaining('1 draft'), findsOneWidget);
    expect(find.textContaining('discarded after 14 days'), findsOneWidget);

    // Clearing confirms first and says what it deletes.
    await tester.tap(find.byKey(const ValueKey('clear-queued-prompts')));
    await tester.pumpAndSettle();
    expect(find.text('Delete queued prompts?'), findsOneWidget);
    expect(
      find.textContaining('Nothing on the server is affected'),
      findsOneWidget,
    );
    await tester.tap(
      find.widgetWithText(FilledButton, 'Delete 1 queued prompt'),
    );
    await tester.pumpAndSettle();

    expect(controller.totalQueuedPromptCount, 0);
    expect(find.text('Queued prompts deleted'), findsOneWidget);
    expect(find.textContaining('Nothing is waiting to send'), findsOneWidget);

    await tester.pump(const Duration(seconds: 5));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('clear-session-drafts')));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Delete 1 draft'));
    await tester.pumpAndSettle();

    expect(controller.totalSessionDraftCount, 0);
    expect(find.text('Drafts deleted'), findsOneWidget);
  });

  testWidgets(
    'unreadable queue can be explicitly cleared from privacy settings',
    (tester) async {
      final controller = await _controllerFor(
        'http://127.0.0.1:4096',
        repository: _EmptyPermissionRepository(),
      );
      addTearDown(controller.dispose);
      await controller.store.prefs.setString('oc.offlineQueue', '{broken');
      expect(controller.totalQueuedPromptCount, 0);
      expect(controller.queuedPromptStorageReadable, isFalse);
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: SettingsScreen(controller: controller),
        ),
      );
      await tester.pumpAndSettle();
      await _openCategory(tester, 'settings-category-privacy');
      final clear = find.byKey(const ValueKey('clear-queued-prompts'));
      await tester.scrollUntilVisible(clear, 200, scrollable: _verticalScroll);
      expect(tester.widget<KitRow>(clear).enabled, isTrue);
      expect(
        find.textContaining('number of queued prompts is unknown'),
        findsOneWidget,
      );
      expect(find.textContaining('0 queued prompts'), findsNothing);
      expect(
        find.textContaining('Saved queued prompts could not be read'),
        findsOneWidget,
      );
      await tester.tap(clear);
      await tester.pumpAndSettle();
      expect(
        find.textContaining('contents and count are unknown'),
        findsOneWidget,
      );
      expect(controller.store.prefs.getString('oc.offlineQueue'), '{broken');
      await tester.tap(
        find.widgetWithText(FilledButton, 'Delete queued prompts'),
      );
      await tester.pumpAndSettle();
      expect(controller.store.prefs.getString('oc.offlineQueue'), isNull);
      expect(controller.queuedPromptStorageReadable, isTrue);
      expect(tester.widget<KitRow>(clear).enabled, isFalse);
    },
  );

  testWidgets('the clear rows are inert when there is nothing to clear', (
    tester,
  ) async {
    final controller = await _controllerFor(
      'http://127.0.0.1:4096',
      repository: _EmptyPermissionRepository(),
    );
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: SettingsScreen(controller: controller),
      ),
    );
    await tester.pumpAndSettle();
    await _openCategory(tester, 'settings-category-privacy');
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('clear-queued-prompts')),
      200,
      scrollable: _verticalScroll,
    );

    expect(
      tester
          .widget<KitRow>(find.byKey(const ValueKey('clear-queued-prompts')))
          .enabled,
      isFalse,
    );
    expect(
      tester
          .widget<KitRow>(find.byKey(const ValueKey('clear-session-drafts')))
          .enabled,
      isFalse,
    );
    expect(
      find.text(
        '0 B of unsent work — 0 queued prompts (0 B) and 0 '
        'drafts (0 B). Queued prompts are discarded after 14 days.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('an Android service timeout turns the switch off and says why', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({
      BackgroundLiveController.preferenceKey: true,
    });
    final preferences = await SharedPreferences.getInstance();
    final live = await _liveController(preferences);
    final controller = await _controllerFor(
      'http://127.0.0.1:4096',
      repository: _EmptyPermissionRepository(),
      backgroundLive: live,
    );
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: SettingsScreen(controller: controller),
      ),
    );
    await tester.pumpAndSettle();
    await _openCategory(tester, 'settings-category-background');

    // The limit is stated on the switch itself, before it is ever hit, in
    // the short line d0047ca3 gave it.
    expect(
      find.textContaining('Android stops this after 6 hours a day'),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('background-timeout-notice')),
      findsNothing,
    );
    expect(
      tester
          .widget<KitSwitchRow>(
            find.byKey(const ValueKey('background-live-switch')),
          )
          .value,
      isTrue,
    );

    // Android stops the service; Dart hears about it immediately.
    live.handleNativeTimeout(const {'reason': 'systemTimeout'});
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('background-timeout-notice')),
      findsOneWidget,
    );
    expect(find.text('Android stopped the live connection'), findsOneWidget);
    expect(
      tester
          .widget<KitSwitchRow>(
            find.byKey(const ValueKey('background-live-switch')),
          )
          .value,
      isFalse,
      reason: 'the switch must not claim a service the system killed',
    );
    expect(controller.keepLiveInBackground, isFalse);

    // Turning it back on is the answer to the notice, so the notice goes.
    await tester.ensureVisible(
      find.byKey(const ValueKey('background-live-switch')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('background-live-switch')));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('background-timeout-notice')),
      findsNothing,
    );
  });
}
