// AI Team on this phone through phone setup v2 (programme P1.7), over a
// fake TermuxTeamRuntime whose verbs the test scripts and fake setup
// engines for both hosts.
//
// Covers: "Set up AI Team on this phone" opens Add tools with AI Team on
// the connected server's host and runs it as that host's v2 job; installed
// already goes straight on; an unfinished job never opens the ready page;
// the ready page's stages, failure and retry, project choice and "Give the
// team a first task"; the team page's "Android stopped the team" line;
// the AI Team page › On this phone; the failure copy; and the
// layout at 320 dp / 2.5x in LTR English and RTL Arabic.

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/diagnostics/failed_job_report.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/builtin/setup/components.dart';
import 'package:opencode_mobile/builtin/setup/phone_setup.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/builtin/team/builtin_team.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/termux/team_runtime.dart';
import 'package:opencode_mobile/ui/screens/team/team_page.dart';
import 'package:opencode_mobile/ui/screens/team/team_home_screen.dart';
import 'package:opencode_mobile/ui/widgets/builtin_team_section.dart'
    show debugBuiltinTeam;
import 'package:opencode_mobile/ui/widgets/team_phone_onboarding.dart';
import 'package:opencode_mobile/ui/widgets/team_phone_section.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/fake_setup_engine.dart';

import 'team_open_settings.dart';

const _profileId = 'phone';

TeamRuntimeStatus _status(
  TeamRuntimePhase phase, {
  String rawPhase = '',
  String? reason,
  String verb = '',
  bool installed = false,
  bool busy = false,
  String city = '',
  String project = '',
  int? agents,
  String? health,
  bool killed = false,
  String? lastError,
  Map<String, String> versions = const {},
}) => TeamRuntimeStatus(
  phase: phase,
  rawPhase: rawPhase.isEmpty ? phase.name : rawPhase,
  reason: reason,
  verb: verb,
  installed: installed,
  busy: busy,
  city: city,
  project: project,
  agents: agents,
  health: health,
  killedByAndroid: killed,
  lastError: lastError,
  versions: versions,
);

const _versions = {'gc': '1.4.1', 'bd': '1.2.2', 'dolt': '2.3.3'};

TeamRuntimeStatus _ready({int agents = 1, bool killed = false}) => _status(
  TeamRuntimePhase.ready,
  installed: true,
  city: 'phone',
  project: '/root/projects/calc',
  agents: killed ? null : agents,
  health: killed ? null : 'ok',
  killed: killed,
  versions: _versions,
);

/// A scripted runtime: [current] is what `status` answers; each verb waits
/// on its gate (when set), then answers its scripted result and makes it
/// the current status.
class _FakeRuntime extends TermuxTeamRuntime {
  _FakeRuntime({this.supported = true})
    : super(
        runner: (_, {timeout = Duration.zero}) async => '',
        manifestLoader: () async => null,
        archProbe: () async => 'aarch64',
      );

  final bool supported;
  TeamRuntimeStatus current = _status(TeamRuntimePhase.idle);
  String log = '';
  List<String> projects = const ['/root/projects/calc'];
  final calls = <String>[];
  final results = <String, TeamRuntimeStatus>{};
  final gates = <String, Completer<void>>{};
  Object? thrown;

  @override
  Future<bool> get supportsAiTeam async => supported;

  @override
  Future<TeamRuntimeManifest?> manifest() async => null;

  @override
  Future<TeamRuntimeStatus> status() async => current;

  @override
  Future<String> logTail({int lines = 200}) async => log;

  @override
  Future<List<String>> managedProjects() async => projects;

  @override
  Future<String> createManagedProject(String name) async {
    calls.add('create $name');
    return '/root/projects/$name';
  }

  Future<TeamRuntimeStatus> _verb(String verb) async {
    calls.add(verb);
    final error = thrown;
    if (error != null) {
      thrown = null;
      throw error;
    }
    final gate = gates[verb];
    if (gate != null) await gate.future;
    final result = results[verb] ?? current;
    current = result;
    return result;
  }

  @override
  Future<TeamRuntimeStatus> install({String? manifestUrl}) => _verb('install');

  @override
  Future<TeamRuntimeStatus> init(
    String projectPath, {
    String? city,
    String? rig,
  }) {
    calls.add('init:$projectPath');
    return _verb('init');
  }

  @override
  Future<TeamRuntimeStatus> start() => _verb('start');

  @override
  Future<TeamRuntimeStatus> stop() => _verb('stop');

  @override
  Future<TeamRuntimeStatus> remove() => _verb('remove');
}

/// A live v2 gateway that answers health, the event channels and an empty
/// session list, so the Workspace renders without a server.
class _FakeChannel implements LiveEventChannel {
  _FakeChannel(this.onStatus);
  final void Function(StreamStatus status) onStatus;

  @override
  void start() => onStatus(StreamStatus.connected);

  @override
  Future<void> dispose() async {}
}

class _Gateway implements ServerGateway {
  String? _directory;
  String? _workspace;
  bool _closed = false;

  @override
  ServerCapabilities get capabilities =>
      const ServerCapabilities(projectManagement: false);

  @override
  Future<Health> health() async => Health(healthy: true, version: '1.0.0');

  @override
  LiveEventChannel openEventChannel({
    required void Function(EventEnvelope event) onEvent,
    required void Function(StreamStatus status) onStatus,
    void Function(Object error)? onError,
  }) => _FakeChannel(onStatus);

  @override
  LiveEventChannel openGlobalEventChannel({
    required void Function(EventEnvelope event) onEvent,
    required void Function(StreamStatus status) onStatus,
    void Function(Object error)? onError,
  }) => _FakeChannel(onStatus);

  @override
  Future<ServerPage<Session>> sessionPage({String? cursor, int limit = 100}) =>
      Future.value(const ServerPage(items: []));

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

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
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnimplementedError('${invocation.memberName}');
}

class _Operations implements ServerOperationsGateway {
  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnimplementedError('${invocation.memberName}');
}

/// Profiles in memory: no secure-storage channel in widget tests.
class _MemoryStore extends ProfileStore {
  _MemoryStore({required super.prefs, List<ServerProfile> seeded = const []})
    : saved = List.of(seeded);

  final List<ServerProfile> saved;
  String? _activeId;

  @override
  List<ServerProfile> get profiles => List.unmodifiable(saved);

  @override
  String? get activeId => _activeId;

  @override
  Future<void> setActiveId(String? id) async => _activeId = id;

  @override
  Future<void> upsert(ServerProfile profile) async {
    final i = saved.indexWhere((p) => p.id == profile.id);
    if (i < 0) {
      saved.add(profile);
    } else {
      saved[i] = profile;
    }
  }
}

ServerProfile _phoneProfile({OrchestrationConfig? config}) => ServerProfile(
  id: _profileId,
  name: 'My phone',
  baseUrl: TermuxBridge.managedServerUrl,
  password: 'phone-secret',
  flavor: ServerFlavor.v2,
  orchestration: config,
);

OrchestrationConfig _phoneConfig() => OrchestrationConfig(
  provider: OrchestrationProvider.gascity,
  url: TermuxBridge.aiteamSupervisorUrl,
  city: 'phone',
  hostMode: OrchestrationHostMode.phone,
  enabledAt: DateTime.utc(2026, 9, 11),
);

/// The Termux bridge as the setup screen sees a phone whose managed
/// OpenCode 2 server is ready: every command answers at once.
Future<Object?> _termux(MethodCall call) async {
  switch (call.method) {
    case 'getCapabilities':
      return <String, Object>{
        'installed': true,
        'version': '0.118',
        'serviceAvailable': true,
        'protocolSupported': true,
        'permissionGranted': true,
      };
    case 'runInTermux':
      final script = (call.arguments as Map)['script'] as String;
      String stdout = '';
      if (script.contains("printf 'opencode-bridge-ok'")) {
        stdout = 'opencode-bridge-ok';
      } else if (script.contains('ubuntu=absent')) {
        stdout = 'ubuntu=installed\nversion=1.0.0\nruntime=opencode2\n';
      } else if (script.contains('__OC_SETUP_OUTPUT__')) {
        stdout =
            'phase=ready\nmessage=OpenCode is ready\nport=4096\nrunner=proot\n'
            'version=1.0.0\nruntime=opencode2\npid=123\n'
            '__OC_SETUP_OUTPUT__\n[oc] authenticated server ready\n';
      }
      return <String, Object>{
        'stdout': stdout,
        'stderr': '',
        'exitCode': 0,
        'err': -1,
        'errorMessage': '',
      };
    default:
      return true;
  }
}

/// The fake registry with AI Team, as both hosts' engines list it.
final _registry = [
  ...FakeSetupEngine.fakeRegistry,
  const SetupComponent(
    id: SetupComponentIds.aiTeam,
    title: 'AI Team',
    shortTitle: 'AI Team',
    checkScript: 'true',
    installScript: 'true',
    dependsOn: ['essentials', 'opencode'],
    estimatedSeconds: 150,
    downloadBytes: 112000000,
  ),
];

/// An Add tools job that installed AI Team, ended in [state].
SetupProgress _teamJob(SetupState state) => SetupProgress(
  state: state,
  components: [
    ComponentProgress(
      id: SetupComponentIds.aiTeam,
      state: state == SetupState.done
          ? ComponentState.done
          : ComponentState.failed,
    ),
  ],
  overall: state == SetupState.done ? 1 : 0.5,
  jobId: 'job-team',
  adding: const [SetupComponentIds.aiTeam],
);

ServerProfile _inAppProfile() => ServerProfile(
  id: 'in-app',
  name: 'This phone',
  baseUrl: 'http://127.0.0.1:4097',
  password: 'in-app-secret',
  flavor: ServerFlavor.v2,
);

/// The in-app team: turning on records the project and passes each stage.
class _BuiltinTeam extends BuiltinTeam {
  final turnedOn = <String>[];

  @override
  Future<void> turnOn(
    String path, {
    required String notice,
    void Function(BuiltinTeamStage stage)? onStage,
    Duration healthTimeout = const Duration(minutes: 6),
  }) async {
    turnedOn.add(path);
    for (final stage in BuiltinTeamStage.values) {
      onStage?.call(stage);
    }
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late SharedPreferences prefs;
  late _FakeRuntime runtime;
  late FakeSetupEngine termuxEngine;
  late FakeSetupEngine inAppEngine;
  final l10n = lookupAppLocalizations(const Locale('en'));

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
    runtime = _FakeRuntime();
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    // The secure-storage channel answers (a real call to it never returns
    // in a widget test); the plugin's sweep deletes the reserved grant key.
    for (final channel in [
      'oc/background',
      'oc/shortcut',
      'plugins.it_nomads.com/flutter_secure_storage',
    ]) {
      messenger.setMockMethodCallHandler(
        MethodChannel(channel),
        (call) async => call.method == 'readAll' ? <String, String>{} : null,
      );
      addTearDown(
        () => messenger.setMockMethodCallHandler(MethodChannel(channel), null),
      );
    }
    messenger.setMockMethodCallHandler(
      const MethodChannel('oc/termux'),
      _termux,
    );
    // Add tools' pre-flight asks the device at once, and nothing waits.
    messenger.setMockMethodCallHandler(
      const MethodChannel('oc/voice'),
      (call) async =>
          call.method == 'getDeviceInfo' ? <String, Object?>{} : null,
    );
    addTearDown(
      () => messenger.setMockMethodCallHandler(
        const MethodChannel('oc/voice'),
        null,
      ),
    );
    addTearDown(
      () => messenger.setMockMethodCallHandler(
        const MethodChannel('oc/termux'),
        null,
      ),
    );
    debugTeamPhoneRuntime = runtime;
    addTearDown(() => debugTeamPhoneRuntime = null);
    termuxEngine = FakeSetupEngine(registry: _registry);
    inAppEngine = FakeSetupEngine(registry: _registry);
    PhoneSetup.termux = termuxEngine;
    PhoneSetup.engine = inAppEngine;
    addTearDown(() => debugTeamPhoneRunJob = null);
  });

  /// A connection over the fake v2 gateway, connected to [profile].
  Future<(ConnectionController, _MemoryStore)> connect(
    ServerProfile profile,
  ) async {
    final store = _MemoryStore(prefs: prefs, seeded: [profile]);
    final controller = ConnectionController(
      store,
      v2GatewayFactory: (_) => (gateway: _Gateway(), operations: _Operations()),
    );
    addTearDown(controller.dispose);
    await controller.connect(profile);
    return (controller, store);
  }

  Widget app(
    Widget home, {
    required ConnectionController controller,
    required ProfileStore store,
    Locale locale = const Locale('en'),
    double textScale = 1,
    bool rtl = false,
  }) => ProviderScope(
    overrides: [
      bootstrapProvider.overrideWithValue(AppBootstrap(store)),
      connProvider.overrideWithValue(controller),
    ],
    child: MaterialApp(
      locale: locale,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(
          textScaler: TextScaler.linear(textScale),
          disableAnimations: true,
        ),
        child: Directionality(
          textDirection: rtl ? TextDirection.rtl : TextDirection.ltr,
          child: child!,
        ),
      ),
      routes: {
        '/home': (_) => const Scaffold(body: Text('home')),
        '/this-phone': (_) => const Scaffold(body: Text('this phone')),
      },
      home: home,
    ),
  );

  /// Frames without waiting on timers: the fakes answer at once and the
  /// steps view's 2 s poll must not stall the test.
  Future<void> settle(WidgetTester tester, {int frames = 12}) async {
    for (var i = 0; i < frames; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
  }

  /// Scrolls the outer list until [finder] is built and on screen: the
  /// setup screen's list builds its children lazily, so a block below the
  /// fold has no element until the list reaches it.
  Future<void> reveal(WidgetTester tester, Finder finder) async {
    if (finder.evaluate().isEmpty || finder.hitTestable().evaluate().isEmpty) {
      await tester.scrollUntilVisible(
        finder,
        120,
        scrollable: find.byType(Scrollable).first,
        maxScrolls: 120,
      );
      await tester.pump();
    }
    await tester.ensureVisible(finder);
    await settle(tester, frames: 2);
    expect(finder.hitTestable(), findsOneWidget);
  }

  Future<void> tapRevealed(WidgetTester tester, Finder finder) async {
    await reveal(tester, finder);
    await tester.tap(finder);
    await settle(tester);
  }

  Future<void> teardown(
    WidgetTester tester,
    ConnectionController controller,
  ) async {
    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
    await tester.pump();
  }

  /// A page whose one button is "Set up AI Team on this phone".
  Future<(ConnectionController, _MemoryStore)> pumpDoor(
    WidgetTester tester, {
    ServerProfile? profile,
    String? directory = '/root/projects/calc',
  }) async {
    final (controller, store) = await connect(profile ?? _phoneProfile());
    controller.directory = directory;
    await tester.pumpWidget(
      app(
        Builder(
          builder: (context) => Scaffold(
            body: Center(
              child: TextButton(
                onPressed: () =>
                    openTeamOnThisPhone(context, controller, runtime: runtime),
                child: const Text('set up'),
              ),
            ),
          ),
        ),
        controller: controller,
        store: store,
      ),
    );
    await settle(tester);
    return (controller, store);
  }

  /// The Termux turn-on answering every verb as it should.
  void turnOnSucceeds({int agents = 1}) {
    runtime.results['install'] = _status(
      TeamRuntimePhase.installed,
      installed: true,
    );
    runtime.results['init'] = _status(
      TeamRuntimePhase.cityReady,
      installed: true,
      city: 'phone',
    );
    runtime.results['start'] = _ready(agents: agents);
  }

  group('Set up AI Team on this phone (v2)', () {
    testWidgets('Termux: Add tools › AI Team runs as a Termux v2 job, then '
        'the ready page turns it on and offers a first task', (tester) async {
      final jobs = <(SetupHostKind, Set<String>)>[];
      debugTeamPhoneRunJob = (context, host, ids) async {
        jobs.add((host, ids));
        termuxEngine.emit(_teamJob(SetupState.done));
      };
      turnOnSucceeds();
      final (controller, store) = await pumpDoor(tester);
      await tester.tap(find.text('set up'));
      await settle(tester);
      // Add tools, with AI Team switched on.
      expect(
        find.byKey(const ValueKey('phone-setup-customize-sheet')),
        findsOneWidget,
      );
      await tapRevealed(
        tester,
        find.byKey(const ValueKey('phone-setup-customize-done')),
      );
      expect(jobs.single.$1, SetupHostKind.termux);
      expect(jobs.single.$2, contains(SetupComponentIds.aiTeam));
      // The ready page turned it on for the open project.
      expect(runtime.calls, [
        'install',
        'init:/root/projects/calc',
        'init',
        'start',
      ]);
      expect(
        find.byKey(const ValueKey('team-phone-ready-done')),
        findsOneWidget,
      );
      expect(find.text(l10n.teamUiPhoneSuccessTitle), findsOneWidget);
      expect(find.text(l10n.teamPhoneReadyFirstTask), findsOneWidget);
      expect(
        store.saved.single.orchestration?.hostMode,
        OrchestrationHostMode.phone,
      );
      expect(controller.orchestration, isNotNull);
      await teardown(tester, controller);
    });

    testWidgets('installed already: straight to the ready page', (
      tester,
    ) async {
      var jobRan = false;
      debugTeamPhoneRunJob = (_, _, _) async => jobRan = true;
      termuxEngine.optionalInstalled = {SetupComponentIds.aiTeam};
      turnOnSucceeds();
      final (controller, _) = await pumpDoor(tester);
      await tester.tap(find.text('set up'));
      await settle(tester);
      expect(
        find.byKey(const ValueKey('phone-setup-customize-sheet')),
        findsNothing,
      );
      expect(jobRan, isFalse);
      expect(
        find.byKey(const ValueKey('team-phone-ready-done')),
        findsOneWidget,
      );
      await teardown(tester, controller);
    });

    testWidgets('a job that did not finish never opens the ready page', (
      tester,
    ) async {
      debugTeamPhoneRunJob = (_, _, _) async =>
          termuxEngine.emit(_teamJob(SetupState.failed));
      final (controller, _) = await pumpDoor(tester);
      await tester.tap(find.text('set up'));
      await settle(tester);
      await tapRevealed(
        tester,
        find.byKey(const ValueKey('phone-setup-customize-done')),
      );
      expect(find.byKey(const ValueKey('team-phone-ready')), findsNothing);
      expect(runtime.calls, isEmpty);
      expect(controller.orchestration, isNull);
      await teardown(tester, controller);
    });

    testWidgets('in-app OC2 Add tools sends its runtime to the real job path', (
      tester,
    ) async {
      // Do not replace debugTeamPhoneRunJob: exercise the same params that
      // the production entry point gives the setup engine.
      inAppEngine.afterRun = _teamJob(SetupState.running);
      final (controller, _) = await pumpDoor(tester, profile: _inAppProfile());
      await tester.tap(find.text('set up'));
      await settle(tester);
      await tapRevealed(
        tester,
        find.byKey(const ValueKey('phone-setup-customize-done')),
      );
      expect(inAppEngine.runs.single, contains(SetupComponentIds.aiTeam));
      expect(inAppEngine.runParams.single, {
        ...SetupJobParams.adding(inAppEngine.runs.single),
        'opencode': {'runtime': 'opencode2'},
      });
      expect(termuxEngine.runs, isEmpty);
      expect(runtime.calls, isEmpty);
      await teardown(tester, controller);
    });

    testWidgets('OpenCode inside the app: the in-app engine and team', (
      tester,
    ) async {
      final jobs = <SetupHostKind>[];
      debugTeamPhoneRunJob = (context, host, ids) async {
        jobs.add(host);
        inAppEngine.emit(_teamJob(SetupState.done));
      };
      final team = _BuiltinTeam();
      debugBuiltinTeam = team;
      addTearDown(() => debugBuiltinTeam = null);
      final (controller, store) = await pumpDoor(
        tester,
        profile: _inAppProfile(),
        directory: '/root/projects/calc',
      );
      await tester.tap(find.text('set up'));
      await settle(tester);
      await tapRevealed(
        tester,
        find.byKey(const ValueKey('phone-setup-customize-done')),
      );
      expect(jobs, [SetupHostKind.builtin]);
      expect(team.turnedOn, ['/root/projects/calc']);
      expect(runtime.calls, isEmpty, reason: 'Termux is not asked');
      expect(
        find.byKey(const ValueKey('team-phone-ready-done')),
        findsOneWidget,
      );
      expect(
        BuiltinTeam.isBuiltinConfig(store.saved.single.orchestration),
        isTrue,
      );
      await teardown(tester, controller);
    });
  });

  group('the ready page', () {
    Future<(ConnectionController, _MemoryStore)> pumpReady(
      WidgetTester tester, {
      String? directory = '/root/projects/calc',
      Future<String?> Function(BuildContext context)? chooseProject,
      Future<void> Function(BuildContext, OrchestrationController)? startTask,
      double textScale = 1,
      bool rtl = false,
      Locale locale = const Locale('en'),
    }) async {
      final (controller, store) = await connect(_phoneProfile());
      controller.directory = directory;
      await tester.pumpWidget(
        app(
          TeamPhoneReadyScreen(
            connection: controller,
            host: SetupHostKind.termux,
            runtime: runtime,
            chooseProject: chooseProject,
            startTask: startTask,
          ),
          controller: controller,
          store: store,
          textScale: textScale,
          rtl: rtl,
          locale: locale,
        ),
      );
      await settle(tester);
      return (controller, store);
    }

    testWidgets('shows the stages while it turns on', (tester) async {
      turnOnSucceeds();
      runtime.gates['start'] = Completer<void>();
      final (controller, _) = await pumpReady(tester);
      expect(find.text(l10n.teamPhoneReadyTurningOnTitle), findsOneWidget);
      expect(
        find.byKey(const ValueKey('builtin-team-stage-starting')),
        findsOneWidget,
      );
      expect(
        find.text(l10n.aiteamComponentStageProject('calc')),
        findsOneWidget,
      );
      runtime.gates['start']!.complete();
      await settle(tester);
      expect(
        find.byKey(const ValueKey('team-phone-ready-done')),
        findsOneWidget,
      );
      await teardown(tester, controller);
    });

    testWidgets('a failure says why and turns on again', (tester) async {
      turnOnSucceeds();
      runtime.results['init'] = _status(
        TeamRuntimePhase.failed,
        rawPhase: 'failed:project-not-git',
        reason: 'project-not-git',
        verb: 'init',
      );
      final (controller, _) = await pumpReady(tester);
      expect(
        find.byKey(const ValueKey('team-phone-ready-failed')),
        findsOneWidget,
      );
      expect(find.text(l10n.teamPhoneReadyFailedTitle), findsOneWidget);
      expect(find.text(l10n.teamUiPhoneFailedProject), findsOneWidget);
      expect(controller.orchestration, isNull);
      // Report this failure (P8.4) sits under the retry.
      expect(
        find.byKey(const ValueKey('team-phone-ready-report')),
        findsOneWidget,
      );
      runtime.results['init'] = _status(
        TeamRuntimePhase.cityReady,
        installed: true,
        city: 'phone',
      );
      await tapRevealed(
        tester,
        find.byKey(const ValueKey('team-phone-ready-retry')),
      );
      expect(
        find.byKey(const ValueKey('team-phone-ready-done')),
        findsOneWidget,
      );
      expect(controller.orchestration, isNotNull);
      await teardown(tester, controller);
    });

    // Issue #112: the report said "the output above says why" and carried
    // no output. It now carries aiteam.sh's last lines, gc's own words.
    testWidgets('a failed store reports what gc said', (tester) async {
      turnOnSucceeds();
      runtime.results['init'] = _status(
        TeamRuntimePhase.failed,
        rawPhase: 'failed:gc-init',
        reason: 'gc-init',
        verb: 'init',
        lastError:
            'Gas City could not make the team store; the output above says why',
      );
      runtime.log =
          '[aiteam] creating-city: Preparing the team store (at 3s)\n'
          'gc init: provider "opencode": provider not found in PATH\n'
          '[oc] The team store could not be made\n';
      final (controller, _) = await pumpReady(tester);
      expect(find.text(l10n.teamUiPhoneFailedCity), findsOneWidget);
      final report = FailedJobReport.teamSetup(teamPhoneTermuxJob)!;
      expect(report.logExcerpt, contains('aiteam.sh init: failed:gc-init'));
      expect(
        report.logExcerpt,
        contains('gc init: provider "opencode": provider not found in PATH'),
      );
      expect(
        report.logExcerpt,
        contains('[oc] The team store could not be made'),
      );
      await teardown(tester, controller);
    });

    testWidgets('no project open: it asks for one first', (tester) async {
      turnOnSucceeds();
      final (controller, _) = await pumpReady(
        tester,
        directory: null,
        chooseProject: (_) async => '/root/projects/notes',
      );
      expect(runtime.calls, isEmpty);
      expect(find.text(l10n.teamPhoneReadyChooseTitle), findsOneWidget);
      await tapRevealed(
        tester,
        find.byKey(const ValueKey('team-phone-ready-choose-project')),
      );
      expect(runtime.calls, contains('init:/root/projects/notes'));
      expect(
        find.byKey(const ValueKey('team-phone-ready-done')),
        findsOneWidget,
      );
      await teardown(tester, controller);
    });

    testWidgets('Give the team a first task starts it on this team', (
      tester,
    ) async {
      turnOnSucceeds();
      OrchestrationController? started;
      final (controller, _) = await pumpReady(
        tester,
        startTask: (_, team) async => started = team,
      );
      await tapRevealed(
        tester,
        find.byKey(const ValueKey('team-phone-ready-first-task')),
      );
      expect(started, isNotNull);
      expect(identical(started, controller.orchestration), isTrue);
      await teardown(tester, controller);
    });
  });

  group('the team page', () {
    testWidgets('a team Android stopped says so, with Start the team again', (
      tester,
    ) async {
      runtime.current = _ready(killed: true);
      final (controller, store) = await connect(
        _phoneProfile(config: _phoneConfig()),
      );
      final team = controller.orchestration!;
      await tester.pumpWidget(
        app(
          TeamHomeScreen(controller: team),
          controller: controller,
          store: store,
        ),
      );
      await settle(tester);
      expect(find.byKey(const ValueKey('team-phone-killed')), findsOneWidget);
      expect(find.text(l10n.teamUiPhoneKilled), findsOneWidget);
      // P3.4: the page says nothing that contradicts the line (no "not
      // answering, the app keeps trying" under it, however long it waits),
      // and its subtitle says the team is stopped.
      await tester.pump(const Duration(seconds: 9));
      await settle(tester);
      expect(
        find.byKey(const ValueKey('team-home-not-answering')),
        findsNothing,
      );
      expect(find.byKey(const ValueKey('team-home-error')), findsNothing);
      expect(find.byKey(const ValueKey('team-home-stopped')), findsOneWidget);
      expect(
        find.text(
          '${l10n.teamUiHostPhrasePhone} · ${l10n.teamHomeHostStopped}',
        ),
        findsOneWidget,
      );
      runtime.results['start'] = _ready(agents: 1);
      await tester.tap(find.byKey(const ValueKey('team-phone-killed-start')));
      await settle(tester);
      expect(runtime.calls, ['start']);
      expect(find.byKey(const ValueKey('team-phone-killed')), findsNothing);
      await teardown(tester, controller);
    });

    testWidgets('a running phone team shows no such line', (tester) async {
      runtime.current = _ready();
      final (controller, store) = await connect(
        _phoneProfile(config: _phoneConfig()),
      );
      await tester.pumpWidget(
        app(
          TeamHomeScreen(controller: controller.orchestration!),
          controller: controller,
          store: store,
        ),
      );
      await settle(tester);
      expect(find.byKey(const ValueKey('team-phone-killed')), findsNothing);
      await teardown(tester, controller);
    });
  });

  group('failure copy', () {
    testWidgets('failure copy per reason', (tester) async {
      String text(String reason, {String? error, String verb = 'start'}) =>
          teamPhoneFailureText(
            l10n,
            _status(
              TeamRuntimePhase.failed,
              rawPhase: 'failed:$reason',
              reason: reason,
              verb: verb,
              lastError: error,
            ),
          );
      expect(text('unsupported-arch'), l10n.teamUiPhoneFailedUnsupportedArch);
      expect(text('packages'), l10n.teamUiPhoneFailedPackages);
      expect(text('project-not-git'), l10n.teamUiPhoneFailedProject);
      expect(text('gc-init'), l10n.teamUiPhoneFailedCity);
      expect(text('supervisor-exited'), l10n.teamUiPhoneFailedSupervisorExited);
      expect(
        text('health-timeout'),
        l10n.teamUiPhoneFailedHealth(TermuxBridge.aiteamSupervisorUrl),
      );
      expect(text('interrupted'), l10n.teamUiPhoneFailedInterrupted);
      expect(
        text('', error: 'start stopped unexpectedly'),
        l10n.teamUiPhoneFailedInterrupted,
      );
      expect(
        text('something-new', error: 'odd'),
        l10n.teamUiPhoneFailedReason('odd'),
      );
      // Download failures name the server and what went wrong (#87).
      const gh = 'github.com';
      expect(
        text('download dns $gh 6', verb: 'install'),
        l10n.teamUiPhoneFailedDownloadDns(gh),
      );
      expect(
        text('download connect $gh 7', verb: 'install'),
        l10n.teamUiPhoneFailedDownloadConnect(gh),
      );
      expect(
        text('download timeout $gh 28', verb: 'install'),
        l10n.teamUiPhoneFailedDownloadTimeout(gh),
      );
      expect(
        text('download tls $gh 60', verb: 'install'),
        l10n.teamUiPhoneFailedDownloadTls(gh),
      );
      expect(
        text('download http $gh 404', verb: 'install'),
        l10n.teamUiPhoneFailedDownloadHttp(gh, '404'),
      );
      expect(
        text('download interrupted $gh 56', verb: 'install'),
        l10n.teamUiPhoneFailedDownloadInterrupted(gh),
      );
      expect(
        text('download write $gh 23', verb: 'install'),
        l10n.teamUiPhoneFailedDownloadWrite,
      );
      expect(
        text('download other $gh 99', verb: 'install'),
        l10n.teamUiPhoneFailedDownloadOther(gh, '99'),
      );
      // An older script's bare token keeps the general sentence.
      expect(text('download', verb: 'install'), l10n.teamUiPhoneFailedDownload);
      expect(
        text('manifest-download', verb: 'install'),
        l10n.teamUiPhoneFailedDownload,
      );
      expect(l10n.teamUiPhoneFailedDownloadHttp(gh, '404'), contains(gh));
    });
  });

  group('AI Team page › On this phone', () {
    Future<(ConnectionController, _MemoryStore)> pumpPlugins(
      WidgetTester tester, {
      ServerProfile? profile,
      double textScale = 1,
      bool rtl = false,
      Locale locale = const Locale('en'),
    }) async {
      final (controller, store) = await connect(profile ?? _phoneProfile());
      await tester.pumpWidget(
        app(
          TeamPage(connection: controller, runtime: runtime),
          controller: controller,
          store: store,
          textScale: textScale,
          rtl: rtl,
          locale: locale,
        ),
      );
      await settle(tester);
      return (controller, store);
    }

    // The one AI Team page; the phone team's own controls are in its menu,
    // in every state of the page.
    Future<void> openSheet(WidgetTester tester) async {
      await openTeamSettingsFromHome(tester);
      await settle(tester);
      await tester.tap(find.byKey(const ValueKey('team-home-phone-controls')));
      await settle(tester);
      expect(find.byKey(const ValueKey('team-phone-section')), findsOneWidget);
    }

    String statusLine(WidgetTester tester) =>
        _textOf(tester, find.byKey(const ValueKey('team-phone-status'))).data!;

    testWidgets('running: agents, versions folded, Stop is two-step', (
      tester,
    ) async {
      runtime.current = _ready(agents: 2);
      final (controller, _) = await pumpPlugins(
        tester,
        profile: _phoneProfile(config: _phoneConfig()),
      );
      await openSheet(tester);
      expect(statusLine(tester), l10n.teamUiPhoneStatusRunning(2));
      // The engine's versions wait under Technical details, never on the
      // status row every visit.
      expect(
        find.textContaining(
          l10n.teamUiPhoneVersions('1.4.1', '1.2.2', '2.3.3'),
          findRichText: true,
        ),
        findsNothing,
      );
      await tapRevealed(
        tester,
        find.byKey(const ValueKey('team-phone-technical')),
      );
      expect(find.byKey(const ValueKey('team-phone-versions')), findsOneWidget);
      // Stop is a row of the team's own panel, named for where it runs.
      expect(find.text(l10n.teamPhoneStopTeamRow), findsOneWidget);
      runtime.results['stop'] = _status(
        TeamRuntimePhase.stopped,
        installed: true,
        city: 'phone',
        versions: _versions,
      );
      await tapRevealed(tester, find.byKey(const ValueKey('team-phone-stop')));
      expect(runtime.calls, isEmpty, reason: 'first tap only asks');
      expect(
        find.byKey(const ValueKey('team-phone-stop-sheet')),
        findsOneWidget,
      );
      await tapRevealed(
        tester,
        find.byKey(const ValueKey('team-phone-stop-confirm')),
      );
      expect(runtime.calls, ['stop']);
      expect(statusLine(tester), l10n.teamUiPhoneStatusStopped);
      // Stopped: Start is one tap and writes the config when it was gone.
      await tapRevealed(tester, find.byKey(const ValueKey('team-phone-start')));
      expect(runtime.calls, ['stop', 'start']);
      await teardown(tester, controller);
    });

    testWidgets('killed by Android: copy and Start again calls start', (
      tester,
    ) async {
      runtime.current = _ready(killed: true);
      final (controller, store) = await pumpPlugins(
        tester,
        profile: _phoneProfile(config: _phoneConfig()),
      );
      await openSheet(tester);
      expect(statusLine(tester), l10n.teamUiPhoneStatusStopped);
      // In the phone team's own sheet (the page under it says it too).
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('team-phone-section')),
          matching: find.text(l10n.teamUiPhoneKilled),
        ),
        findsOneWidget,
      );
      runtime.results['start'] = _ready(agents: 1);
      await tapRevealed(
        tester,
        find.byKey(const ValueKey('team-phone-start-again')),
      );
      expect(runtime.calls, ['start']);
      expect(statusLine(tester), l10n.teamUiPhoneStatusRunning(1));
      expect(
        store.saved.single.orchestration?.hostMode,
        OrchestrationHostMode.phone,
      );
      await teardown(tester, controller);
    });

    testWidgets('Keep it running opens the tips with the ADB steps', (
      tester,
    ) async {
      runtime.current = _ready();
      final (controller, _) = await pumpPlugins(
        tester,
        profile: _phoneProfile(config: _phoneConfig()),
      );
      await openSheet(tester);
      await tapRevealed(
        tester,
        find.byKey(const ValueKey('team-phone-keep-running')),
      );
      expect(
        find.byKey(const ValueKey('team-phone-tips-sheet')),
        findsOneWidget,
      );
      expect(find.text(l10n.teamUiPhoneTipWakeLock), findsOneWidget);
      expect(find.text(l10n.teamUiPhoneTipBattery), findsOneWidget);
      expect(find.text(l10n.teamUiPhoneTipPhantom), findsOneWidget);
      // The commands are a KitCodeBlock (kind command): left to right.
      Finder command(String text) => find.descendant(
        of: find.byKey(const ValueKey('team-phone-tips-commands')),
        matching: find.textContaining(text, findRichText: true),
      );
      final phantom = command(
        'adb shell settings put global settings_enable_monitor_phantom_procs false',
      );
      expect(phantom, findsOneWidget);
      expect(
        command(
          'adb shell device_config put activity_manager max_phantom_processes 2147483647',
        ),
        findsOneWidget,
      );
      expect(
        tester
            .widget<Directionality>(
              find
                  .ancestor(of: phantom, matching: find.byType(Directionality))
                  .first,
            )
            .textDirection,
        TextDirection.ltr,
      );
      String? copied;
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        (call) async {
          if (call.method == 'Clipboard.setData') {
            copied = (call.arguments as Map)['text'] as String;
          }
          return null;
        },
      );
      addTearDown(
        () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
          SystemChannels.platform,
          null,
        ),
      );
      await tapRevealed(
        tester,
        find.byKey(const ValueKey('team-phone-tips-copy')),
      );
      expect(copied, teamPhoneAdbCommands);
      await teardown(tester, controller);
    });

    testWidgets(
      'Remove is two-step, calls remove, drops the config and the offer',
      (tester) async {
        runtime.current = _ready();
        final (controller, store) = await pumpPlugins(
          tester,
          profile: _phoneProfile(config: _phoneConfig()),
        );
        await openSheet(tester);
        final remove = find.byKey(const ValueKey('team-phone-remove'));
        await tapRevealed(tester, remove);
        expect(runtime.calls, isEmpty, reason: 'first tap only asks');
        expect(
          find.byKey(const ValueKey('team-phone-remove-sheet')),
          findsOneWidget,
        );
        expect(find.text(l10n.teamPhoneRemoveBody), findsOneWidget);
        runtime.results['remove'] = _status(TeamRuntimePhase.idle);
        await tapRevealed(
          tester,
          find.byKey(const ValueKey('team-phone-remove-confirm')),
        );
        expect(runtime.calls, ['remove']);
        expect(store.saved.single.orchestration, isNull);
        expect(controller.orchestration, isNull);
        expect(
          controller.orchestrationStore.phoneOffer(_profileId),
          PhoneOffer.dismissed,
        );
        // The sheet closed with the removal, Team settings closed itself,
        // and the page is off.
        for (var i = 0; i < 10; i++) {
          await tester.pump(const Duration(milliseconds: 100));
        }
        expect(find.byKey(const ValueKey('team-phone-section')), findsNothing);
        expect(find.byKey(const ValueKey('team-intro')), findsOneWidget);
        await teardown(tester, controller);
      },
    );

    testWidgets('not available copy when the runtime is unsupported', (
      tester,
    ) async {
      runtime = _FakeRuntime(supported: false);
      debugTeamPhoneRuntime = runtime;
      final (controller, _) = await pumpPlugins(
        tester,
        profile: _phoneProfile(config: _phoneConfig()),
      );
      expect(find.byKey(const ValueKey('plugins-phone-offer')), findsNothing);
      await openSheet(tester);
      expect(find.text(l10n.teamUiPhoneNotAvailable), findsOneWidget);
      expect(find.byKey(const ValueKey('team-phone-status')), findsNothing);
      await teardown(tester, controller);
    });

    testWidgets('not installed: the page, off, sets it up through Add tools', (
      tester,
    ) async {
      final (controller, _) = await pumpPlugins(tester);
      expect(find.byKey(const ValueKey('team-intro')), findsOneWidget);
      await tapRevealed(
        tester,
        find.byKey(const ValueKey('team-intro-set-up')),
      );
      // Phone setup v2's Add tools, on the Termux host (P1.7).
      expect(
        find.byKey(const ValueKey('phone-setup-customize-sheet')),
        findsOneWidget,
      );
      await teardown(tester, controller);
    });
  });

  group('layout at 320dp and 2.5x', () {
    for (final rtl in [false, true]) {
      final locale = Locale(rtl ? 'ar' : 'en');
      final label = rtl ? 'RTL ar' : 'LTR en';

      testWidgets('the ready page, turning on and ready · $label', (
        tester,
      ) async {
        tester.view.physicalSize = const Size(320, 844);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        turnOnSucceeds();
        runtime.gates['start'] = Completer<void>();
        final (controller, store) = await connect(_phoneProfile());
        controller.directory = '/root/projects/calc';
        await tester.pumpWidget(
          app(
            TeamPhoneReadyScreen(
              connection: controller,
              host: SetupHostKind.termux,
              runtime: runtime,
            ),
            controller: controller,
            store: store,
            textScale: 2.5,
            rtl: rtl,
            locale: locale,
          ),
        );
        await settle(tester);
        expect(tester.takeException(), isNull);
        await reveal(
          tester,
          find.byKey(const ValueKey('builtin-team-stage-waiting')),
        );
        runtime.gates['start']!.complete();
        await settle(tester);
        expect(tester.takeException(), isNull);
        await reveal(
          tester,
          find.byKey(const ValueKey('team-phone-ready-first-task')),
        );
        await teardown(tester, controller);
      });

      testWidgets('On this phone section and tips · $label', (tester) async {
        tester.view.physicalSize = const Size(320, 844);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        runtime.current = _ready(killed: true);
        final (controller, store) = await connect(
          _phoneProfile(config: _phoneConfig()),
        );
        await tester.pumpWidget(
          app(
            TeamPage(connection: controller, runtime: runtime),
            controller: controller,
            store: store,
            textScale: 2.5,
            rtl: rtl,
            locale: locale,
          ),
        );
        await settle(tester);
        expect(tester.takeException(), isNull);
        expect(tester.takeException(), isNull);
        // The team page's menu opens the phone team's own controls.
        await openTeamSettingsFromHome(tester);
        await settle(tester);
        await tester.tap(
          find.byKey(const ValueKey('team-home-phone-controls')),
        );
        await settle(tester);
        expect(tester.takeException(), isNull);
        await reveal(tester, find.byKey(const ValueKey('team-phone-status')));
        await reveal(
          tester,
          find.byKey(const ValueKey('team-phone-start-again')),
        );
        await reveal(tester, find.byKey(const ValueKey('team-phone-remove')));
        await tapRevealed(
          tester,
          find.byKey(const ValueKey('team-phone-keep-running')),
        );
        expect(tester.takeException(), isNull);
        final command = find.descendant(
          of: find.byKey(const ValueKey('team-phone-tips-commands')),
          matching: find.textContaining(
            'pkg install android-tools',
            findRichText: true,
          ),
        );
        expect(
          tester
              .widget<Directionality>(
                find
                    .ancestor(
                      of: command,
                      matching: find.byType(Directionality),
                    )
                    .first,
              )
              .textDirection,
          TextDirection.ltr,
        );
        await reveal(
          tester,
          find.byKey(const ValueKey('team-phone-tips-copy')),
        );
        await teardown(tester, controller);
      });
    }
  });
}

/// The [Text] a keyed text draws: the widget itself, or the one inside a
/// KitText (its key sits on the KitText).
Text _textOf(WidgetTester tester, Finder finder) {
  final widget = tester.widget(finder);
  if (widget is Text) return widget;
  return tester.widget<Text>(
    find.descendant(of: finder, matching: find.byType(Text)).first,
  );
}
