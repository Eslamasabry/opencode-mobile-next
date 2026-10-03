// TEAM-114: the plugin-off regression (04-plugin-architecture §1.3, §8).
//
// With a profile whose `orchestration` is null the Workspace, Activity, the
// Settings hub and the server editor carry no `team-*` widget at all — not
// hidden, absent — and `ConnectionController.orchestration` is null. With
// the plugin on (fixture provider) the app makes exactly the same calls to
// `ServerGateway` during connect and the Workspace render as with it off:
// every orchestration read goes through `OrchestrationGateway`, never
// through `lib/api` or `lib/api2`.

import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/orchestration/adapters/fixture/fixture_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/activity_screen.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/first_run_path.dart';
import 'support/server_editor.dart';

Directory _findFixtureRoot() {
  var dir = Directory.current;
  for (var i = 0; i < 5; i++) {
    final candidate = Directory('${dir.path}/tool/qa/gascity_fixture');
    if (candidate.existsSync()) return candidate;
    dir = dir.parent;
  }
  throw StateError(
    'tool/qa/gascity_fixture not found from ${Directory.current}',
  );
}

/// Key prefixes the plugin's widgets use (grep `Key('team-` and
/// `activity-team-` in lib/). Every one of them must be absent while the
/// plugin is off. The Plugins settings entry (`settings-category-plugins`,
/// `plugins-ai-team-*`) and the server editor's "Add AI Team" section
/// (`server-editor-team-*`) are the entry points for turning it on and are
/// meant to exist regardless (TEAM-106). So is the Work tab's door to the
/// team while it is off (`team-discover-*`, docs/qa/team-discover-2026-09-25):
/// it holds no plugin state and makes no call.
const _pluginKeyPrefixes = ['team-', 'activity-team-'];
const _entryPointPrefixes = ['team-discover-'];

bool _isPluginKey(Key? key) => switch (key) {
  ValueKey<String>(:final value) =>
    _pluginKeyPrefixes.any((prefix) => value.startsWith(prefix)) &&
        !_entryPointPrefixes.any((prefix) => value.startsWith(prefix)),
  _ => false,
};

Finder _pluginKeys() =>
    find.byWidgetPredicate((w) => _isPluginKey(w.key), skipOffstage: false);

/// An event channel that reports itself connected as soon as it starts.
class _FakeChannel implements LiveEventChannel {
  _FakeChannel(this.onStatus);

  final void Function(StreamStatus status) onStatus;

  @override
  void start() => onStatus(StreamStatus.connected);

  @override
  Future<void> dispose() async {}
}

/// A live v2 gateway that records the name of every member the app calls
/// on it. Health, the event channels and the session list answer; every
/// other member is recorded and then throws, which the controller absorbs
/// per surface. Two connections that produce the same [calls] made the same
/// demands on the server API.
class _RecordingGateway implements ServerGateway {
  final calls = <String>[];
  String? _directory;
  String? _workspace;
  bool _closed = false;

  void _hit(String name) => calls.add(name);

  // No project catalogue: the Workspace renders its session list at once
  // instead of asking for a folder first, as the Work tab team suites do.
  @override
  ServerCapabilities get capabilities =>
      const ServerCapabilities(projectManagement: false);

  @override
  Future<Health> health() async {
    _hit('health');
    return Health(healthy: true, version: '0.0.0-beta-18600');
  }

  @override
  LiveEventChannel openEventChannel({
    required void Function(EventEnvelope event) onEvent,
    required void Function(StreamStatus status) onStatus,
    void Function(Object error)? onError,
  }) {
    _hit('openEventChannel');
    return _FakeChannel(onStatus);
  }

  @override
  LiveEventChannel openGlobalEventChannel({
    required void Function(EventEnvelope event) onEvent,
    required void Function(StreamStatus status) onStatus,
    void Function(Object error)? onError,
  }) {
    _hit('openGlobalEventChannel');
    return _FakeChannel(onStatus);
  }

  @override
  Future<ServerPage<Session>> sessionPage({
    String? cursor,
    int limit = 100,
  }) async {
    _hit('sessionPage');
    return const ServerPage(items: []);
  }

  @override
  Future<List<Session>> sessions() async {
    _hit('sessions');
    return const [];
  }

  @override
  Future<Map<String, String>> sessionStatuses() async {
    _hit('sessionStatuses');
    return const {};
  }

  @override
  String? get directory => _directory;

  @override
  String? get workspace => _workspace;

  @override
  bool get isClosed => _closed;

  @override
  void setLocation({String? directory, String? workspace}) {
    _directory = directory;
    _workspace = workspace;
  }

  @override
  void close() => _closed = true;

  @override
  dynamic noSuchMethod(Invocation invocation) {
    final name = invocation.memberName.toString();
    _hit(name.substring(8, name.length - 2)); // Symbol("x") → x
    throw UnimplementedError(name);
  }
}

class _FakeOperations implements ServerOperationsGateway {
  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnimplementedError('${invocation.memberName}');
}

/// Holds profiles in memory without the secure-storage channel, which is
/// unmocked in widget tests and would hang a real upsert.
class _MemoryStore extends ProfileStore {
  _MemoryStore({required super.prefs, List<ServerProfile> seeded = const []})
    : saved = List.of(seeded);

  final List<ServerProfile> saved;

  @override
  List<ServerProfile> get profiles => List.unmodifiable(saved);

  @override
  Future<void> upsert(ServerProfile profile) async {
    saved.removeWhere((p) => p.id == profile.id);
    saved.add(profile);
  }
}

class _EmptyStore extends ProfileStore {
  _EmptyStore({required super.prefs});

  @override
  List<ServerProfile> get profiles => const [];
}

/// The fixture gateway with read counters, so a test can show where the
/// plugin's reads went.
class _CountingFixture extends FixtureOrchestrationGateway {
  _CountingFixture({required super.fixturePath});

  final reads = <String, int>{};

  void _hit(String name) => reads[name] = (reads[name] ?? 0) + 1;

  @override
  Future<List<OrchestrationProject>> projects() {
    _hit('projects');
    return super.projects();
  }

  @override
  Future<List<OrchestrationRun>> runs({String? projectId}) {
    _hit('runs');
    return super.runs(projectId: projectId);
  }

  @override
  Future<List<WorkItem>> work({String? projectId}) {
    _hit('work');
    return super.work(projectId: projectId);
  }

  @override
  Future<List<OrchestrationAgent>> agents() {
    _hit('agents');
    return super.agents();
  }

  @override
  Future<List<OrchestrationGate>> gates() {
    _hit('gates');
    return super.gates();
  }

  @override
  Future<OrchestrationUsage?> usage() {
    _hit('usage');
    return super.usage();
  }

  @override
  Future<List<ActivityEvent>> activity({int? afterSeq, int limit = 100}) {
    _hit('activity');
    return super.activity(afterSeq: afterSeq, limit: limit);
  }
}

/// A connection whose plugin controller a test supplies directly, so the
/// plugin can run over a counting gateway while the server side stays the
/// recording gateway.
class _PluggedConnection extends ConnectionController {
  _PluggedConnection(super.store, {required super.v2GatewayFactory});

  OrchestrationController? team;

  @override
  OrchestrationController? get orchestration => team ?? super.orchestration;

  void attach(OrchestrationController? next) {
    team?.removeListener(notifyListeners);
    team = next?..addListener(notifyListeners);
  }

  @override
  void dispose() {
    attach(null);
    super.dispose();
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late String fixturePath;
  late SharedPreferences prefs;

  setUp(() async {
    fixturePath = _findFixtureRoot().path;
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    for (final channel in [
      'plugins.it_nomads.com/flutter_secure_storage',
      'oc/background',
      'oc/shortcut',
    ]) {
      messenger.setMockMethodCallHandler(
        MethodChannel(channel),
        (call) async => call.method == 'readAll' ? <String, String>{} : null,
      );
      addTearDown(
        () => messenger.setMockMethodCallHandler(MethodChannel(channel), null),
      );
    }
  });

  OrchestrationConfig fixtureConfig() => OrchestrationConfig(
    provider: OrchestrationProvider.fixture,
    url: fixturePath,
    city: 'bright-lights',
    enabledAt: DateTime.utc(2026, 9, 10),
  );

  ServerProfile profile({OrchestrationConfig? config}) => ServerProfile(
    id: 'v2-box',
    name: 'Workstation',
    baseUrl: 'http://127.0.0.1:1',
    password: 'serve-password',
    flavor: ServerFlavor.v2,
    orchestration: config,
  );

  Widget app(Widget home, {ConnectionController? scoped}) {
    final material = MaterialApp(
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(disableAnimations: true),
        child: child!,
      ),
      home: home,
    );
    return scoped == null
        ? material
        : ProviderScope(
            overrides: [connProvider.overrideWithValue(scoped)],
            child: material,
          );
  }

  /// A few frames without waiting on timers: the fixture answers at once
  /// and the plugin's refresh debounce must not stall the test.
  Future<void> settle(WidgetTester tester) async {
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
  }

  /// Connects [p] over a recording gateway and renders nothing (the Work screen is gone),
  /// returning the controller and the gateway's call log.
  Future<(ConnectionController, _RecordingGateway)> connectAndRender(
    WidgetTester tester,
    ServerProfile p, {
    ConnectionController Function(
      _MemoryStore store,
      V2GatewayPairFactory factory,
    )?
    build,
  }) async {
    final gateway = _RecordingGateway();
    final store = _MemoryStore(prefs: prefs, seeded: [p]);
    ({ServerGateway gateway, ServerOperationsGateway operations}) factory(
      ServerProfile _,
    ) => (gateway: gateway, operations: _FakeOperations());
    final controller =
        build?.call(store, factory) ??
        ConnectionController(store, v2GatewayFactory: factory);
    addTearDown(controller.dispose);
    await controller.connect(p);
    await tester.pump();
    await tester.pumpWidget(app(const SizedBox.shrink()));
    await settle(tester);
    return (controller, gateway);
  }

  /// Tears the tree and the controller down inside the test body so the
  /// connection's timers are cancelled before the binding checks for them.
  Future<void> teardown(
    WidgetTester tester,
    ConnectionController controller,
  ) async {
    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
    await tester.pump();
  }

  Future<void> expectNoPluginWidgets(WidgetTester tester) async {
    expect(find.byKey(const ValueKey('team-card')), findsNothing);
    final stray = _pluginKeys();
    expect(
      stray,
      findsNothing,
      reason:
          'plugin keys in the tree: '
          '${stray.evaluate().map((e) => e.widget.key).toList()}',
    );
  }

  group('plugin off: tree assertions', () {
    testWidgets('Activity has no AI Team rows', (tester) async {
      final (controller, _) = await connectAndRender(tester, profile());
      await tester.pumpWidget(
        app(
          Scaffold(
            body: ActivityScreen(controller: controller, embedded: true),
          ),
        ),
      );
      await settle(tester);
      expect(controller.orchestration, isNull);
      await expectNoPluginWidgets(tester);
      expect(tester.takeException(), isNull);
      await teardown(tester, controller);
    });

    testWidgets('Settings hub keeps the Plugins entry and nothing else', (
      tester,
    ) async {
      final (controller, _) = await connectAndRender(tester, profile());
      await tester.binding.setSurfaceSize(const Size(500, 1600));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await tester.pumpWidget(
        app(SettingsScreen(controller: controller), scoped: controller),
      );
      await settle(tester);
      // Tools (which holds Plugins) is the way in; it exists whether the
      // plugin is on or off (TEAM-106, slice-P3.10).
      expect(find.byKey(const ValueKey('settings-tools')), findsOneWidget);
      await expectNoPluginWidgets(tester);
      expect(tester.takeException(), isNull);
      await teardown(tester, controller);
    });

    testWidgets('server editor offers "Add AI Team" without a form', (
      tester,
    ) async {
      final store = _EmptyStore(prefs: prefs);
      final controller = ConnectionController(store);
      addTearDown(controller.dispose);
      await tester.binding.setSurfaceSize(const Size(500, 1600));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            bootstrapProvider.overrideWithValue(AppBootstrap(store)),
            connProvider.overrideWithValue(controller),
          ],
          child: app(const ServersScreen()),
        ),
      );
      await settle(tester);
      await openFirstRunConnect(tester);
      // The editor's entry point is meant to be there; the host form and
      // every other plugin widget are not.
      await openServerMoreOptions(tester);
      expect(
        find.byKey(const ValueKey('server-editor-team-section')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('server-editor-team-add')),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('team-host-form')), findsNothing);
      await expectNoPluginWidgets(tester);
      expect(tester.takeException(), isNull);
    });
  });

  group('plugin on adds no ServerGateway calls', () {
    testWidgets(
      'orchestration reads go through OrchestrationGateway, not lib/api',
      (tester) async {
        final fixture = _CountingFixture(fixturePath: fixturePath);
        final config = fixtureConfig();
        final store = OrchestrationStore(prefs);
        final team = OrchestrationController(
          profile: profile(config: config),
          config: config,
          store: store,
          gatewayFactory: (_, _) => fixture,
        );
        addTearDown(team.dispose);

        Future<void> renderActivity(ConnectionController controller) async {
          await tester.pumpWidget(
            app(
              Scaffold(
                body: ActivityScreen(controller: controller, embedded: true),
              ),
            ),
          );
          await settle(tester);
        }

        // Baseline: the same connection and renders with the plugin off.
        final (off, offGateway) = await connectAndRender(tester, profile());
        await renderActivity(off);
        final offCalls = List.of(offGateway.calls)..sort();
        await teardown(tester, off);
        SharedPreferences.setMockInitialValues({});
        prefs = await SharedPreferences.getInstance();

        late _PluggedConnection plugged;
        final (on, onGateway) = await connectAndRender(
          tester,
          profile(),
          build: (store, factory) {
            plugged = _PluggedConnection(store, v2GatewayFactory: factory);
            plugged.attach(team);
            return plugged;
          },
        );
        expect(on, same(plugged));
        await team.start();
        await settle(tester);
        // Activity too: it reads the plugin's gates and agents for its
        // AI Team rows (none in the fixture's normal run, so no rows).
        await renderActivity(on);
        expect(on.orchestration, same(team));

        // Every scope was read from the orchestration gateway…
        for (final scope in ['projects', 'runs', 'work', 'agents', 'gates']) {
          expect(
            fixture.reads[scope],
            greaterThan(0),
            reason: '$scope read through OrchestrationGateway',
          );
        }
        expect(team.snapshot.agents, isNotEmpty);
        // …and the server gateway saw nothing beyond the plugin-off
        // baseline: zero lib/api or lib/api2 calls for orchestration.
        expect(onGateway.calls..sort(), offCalls);
        expect(
          onGateway.calls.where(
            (name) =>
                name.toLowerCase().contains('orchestrat') ||
                name.toLowerCase().contains('team') ||
                name.toLowerCase().contains('agent') ||
                name.toLowerCase().contains('bead') ||
                name.toLowerCase().contains('convoy'),
          ),
          isEmpty,
        );
        expect(tester.takeException(), isNull);
        await teardown(tester, on);
      },
    );
  });
}
