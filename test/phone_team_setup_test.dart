import 'support/fake_server_capabilities.dart';
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/diagnostics/app_diagnostics.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/domain/phone_project_engine.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/orchestration/adapters/fixture/project_fixture_gateway.dart';
import 'package:opencode_mobile/orchestration/adapters/inapp/phone_engine_gateway.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/phone_team_setup.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/team/projects/team_execution_gate.dart';
import 'package:opencode_mobile/ui/screens/team/projects/team_projects_screen.dart';
import 'package:opencode_mobile/ui/screens/team/team_phone_setup_screen.dart';
import 'package:opencode_mobile/ui/screens/team/team_page.dart'
    show PhoneTeamOffPage;
import 'package:opencode_mobile/ui/screens/team/team_migration.dart'
    show teamMigrationOffered;
import 'package:shared_preferences/shared_preferences.dart';

import 'team_project_fixture_test.dart' show MemoryPersistence;
import 'phone_project_engine_controller_test.dart' show NativeBridge;
import 'phone_project_engine_gateway_test.dart' as engine;

class _OwnershipFailureLinux extends BuiltinLinux {
  int starts = 0;
  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
    String? input,
  }) async => const BuiltinLinuxRunResult(exitCode: 0, output: '');
  @override
  Future<void> startServer(
    String script, {
    int port = 4097,
    BuiltinServerRestoreRecipe? restoreRecipe,
  }) async {
    starts++;
    throw const BuiltinLinuxException(
      'The server on this phone could not restart safely.',
      code: 'runtime_ownership_unknown',
    );
  }
}

/// A phone whose stop takes [work] on the test's fake clock.
class _SlowStopPhone extends _Phone {
  _SlowStopPhone(this.advance);
  final void Function(Duration) advance;
  @override
  Future<void> stopServer() async {
    advance(const Duration(seconds: 10));
    await super.stopServer();
  }
}

final _en = lookupAppLocalizations(const Locale('en'));

PhoneEngineHealth _health({
  bool execution = true,
  bool boundary = true,
  bool oc2Verified = true,
  bool oc2 = true,
  bool restartRequired = false,
  String reason = '',
  String tier = 'none',
}) => PhoneEngineHealth(
  boundaryTier: tier,
  profileId: 'p',
  engineVersion: '1',
  execution: execution,
  boundary: boundary,
  oc2: oc2,
  oc2Verified: oc2 && oc2Verified,
  commandActions: const {},
  restartRequired: restartRequired,
  boundaryReason: reason,
);

/// A scripted phone: every call is recorded, nothing reaches Android.
class _Phone extends ChangeNotifier implements PhoneTeamSetupPorts {
  bool reply = false;
  PhoneTeamHostState host = const PhoneTeamHostState();
  PhoneEngineHealth started = _health();
  PhoneEngineHealth probed = _health();

  /// When set, the engine start fails with this native code.
  String? startFailure;
  String? serverFailure;
  bool restoreResult = true;
  bool on = false;
  bool server = true;
  final calls = <String>[];
  int startCalls = 0;

  /// Holds the engine's proof open (a golden of the step while it works).
  Future<void>? holdStart;
  Future<void>? holdStop;
  bool engineRunning = false;
  bool failStop = false;
  bool requireReleasedStart = false;

  @override
  bool get hasServer => server;
  @override
  bool get wasOn => on;
  @override
  bool get replyRunning => reply;
  @override
  Listenable get replyChanges => this;
  void finishReply() {
    reply = false;
    notifyListeners();
  }

  @override
  Future<PhoneTeamHostState> inspect() async {
    calls.add('inspect');
    return host;
  }

  @override
  Future<void> stopServer() async => calls.add('stopServer');
  @override
  Future<void> closeTerminals() async => calls.add('closeTerminals');
  @override
  Future<PhoneEngineHealth> startEngine() async {
    calls.add('startEngine');
    startCalls++;
    await holdStart;
    if (engineRunning && requireReleasedStart) {
      throw const PhoneEngineException('engine_in_use');
    }
    engineRunning = true;
    final failure = startFailure;
    if (failure != null) throw PhoneEngineException(failure);
    return started;
  }

  @override
  Future<void> stopEngine() async {
    calls.add('stopEngine');
    await holdStop;
    if (failStop) throw StateError('private native details');
    engineRunning = false;
  }

  @override
  Future<PhoneEngineHealth> probeEngine() async {
    calls.add('probe');
    return probed;
  }

  @override
  Future<String?> startServer() async {
    calls.add('startServer');
    return serverFailure;
  }

  @override
  Future<bool> restoreServer() async {
    calls.add('restoreServer');
    return restoreResult;
  }

  @override
  Future<void> attach() async => calls.add('attach');
}

PhoneTeamSetupController _flow(_Phone phone) =>
    PhoneTeamSetupController(phone, delay: (_) async {}, readyAttempts: 2);

Future<ConnectionController> _connection() async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  final connection = ConnectionController(store);
  addTearDown(connection.dispose);
  return connection;
}

void _mockChannels() {
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
}

Widget _app(Widget home) => ProviderScope(
  child: MaterialApp(
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(context).copyWith(disableAnimations: true),
      child: child!,
    ),
    home: home,
  ),
);

void _size(WidgetTester tester, [Size size = const Size(412, 915)]) {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 12; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

/// Fixture projects whose engine can (or cannot) run work yet.
class _Gateway extends ProjectFixtureGateway {
  _Gateway({
    required this.lanes,
    this.simulated = false,
    this.phoneOffline = false,
  }) : super(persistence: MemoryPersistence());
  final bool lanes;
  final bool simulated;
  final bool phoneOffline;
  TeamWorkspace _shape(TeamWorkspace w) => w.copyWith(
    simulated: simulated,
    servers: phoneOffline
        ? [for (final s in w.servers) s.copyWith(phone: true, online: false)]
        : null,
  );
  @override
  Future<TeamWorkspace> teamWorkspace() async =>
      _shape(await super.teamWorkspace());
  @override
  Stream<TeamWorkspace> watchTeamWorkspace() =>
      super.watchTeamWorkspace().map(_shape);
  @override
  OrchestrationCapabilities get capabilities => OrchestrationCapabilities(
    projects: true,
    projectLifecycle: true,
    projectLanes: lanes,
    projectPromotion: lanes,
    projectMergeQueue: lanes,
    projectVerification: lanes,
    projectPlacement: lanes,
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  tearDown(() => PhoneTeamSetup.debugPorts = null);

  test('eligibility does not allocate a flow or a private starter', () async {
    _mockChannels();
    final connection = await _connection();
    var created = 0;
    PhoneTeamSetup.debugPorts = (_) {
      created++;
      return _Phone();
    };
    expect(PhoneTeamSetup.hasServer(connection), isFalse);
    expect(created, 0);
  });

  testWidgets('migration and off-page access share the shell server owner', (
    tester,
  ) async {
    _mockChannels();
    _size(tester);
    final connection = await _connection();
    final profile = ServerProfile(
      id: 'phone',
      name: 'This phone',
      baseUrl: BuiltinLinux.serverUrl,
      flavor: ServerFlavor.v2,
      password: 'fixture-server-password',
      orchestration: const OrchestrationConfig(
        provider: OrchestrationProvider.gascity,
        url: 'http://127.0.0.1:8472',
      ),
    );
    await connection.store.upsert(profile);
    await connection.store.setActiveId(profile.id);
    connection.adoptConnectedProfileForTesting(profile);
    expect(teamMigrationOffered(connection), isTrue);
    final linux = _OwnershipFailureLinux();
    final starter = BuiltinServerStarter(linux: linux);
    var claimed = 0;
    starter.beforeManualStart = (_) async => claimed++;
    addTearDown(starter.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [builtinServerStarterProvider.overrideWithValue(starter)],
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: PhoneTeamOffPage(connection: connection),
        ),
      ),
    );
    await _settle(tester);
    final context = tester.element(find.byType(PhoneTeamOffPage));
    final flow = phoneTeamSetupForContext(context, connection);
    addTearDown(flow.dispose);
    expect((flow.ports as BuiltinPhoneTeamSetupPorts).starter, same(starter));
    expect(await flow.ports.startServer(), 'componentRecovery');
    expect(linux.starts, 1);
    expect(claimed, 1);
    expect(
      starter.failureFor(profile)?.problem,
      BuiltinStartProblem.componentRecovery,
    );
    expect(PhoneTeamSetup.of(connection, starter: starter), same(flow));
    await tester.pumpWidget(const SizedBox.shrink());
  });

  test('phone-team capability is opt-in and survives managed-host overlay', () {
    expect(const ServerCapabilities().phoneTeamExecution, isFalse);
    for (final capabilities in [
      testServerCapabilities,
      api2ServerCapabilities,
    ]) {
      expect(capabilities.phoneTeamExecution, isTrue);
      expect(capabilities.withGenUi(true).phoneTeamExecution, isTrue);
      expect(capabilities.withGenUi(false).phoneTeamExecution, isTrue);
    }
  });

  test(
    'setup accepts inactive OC2 and prefers the active supported phone',
    () async {
      _mockChannels();
      debugPlatformCapabilities = const PlatformCapabilities.android();
      addTearDown(() => debugPlatformCapabilities = null);
      final connection = await _connection();
      final store = connection.store;
      await store.upsert(
        ServerProfile(
          id: 'remote',
          name: 'Remote',
          baseUrl: 'https://example.org',
          backend: ServerBackend.paseo,
        ),
      );
      await store.upsert(
        ServerProfile(
          id: 'phone2',
          name: 'Phone 2',
          baseUrl: 'http://127.0.0.1:4097',
          flavor: ServerFlavor.v2,
          orchestration: const OrchestrationConfig(
            provider: OrchestrationProvider.phoneEngine,
            url: 'http://127.0.0.1:4098',
          ),
          teamEngineAuth: 'fixture-engine-token',
        ),
      );
      await store.setActiveId('remote');
      final linux = BuiltinLinux();
      final starter = BuiltinServerStarter(linux: linux);
      addTearDown(starter.dispose);
      final ports = BuiltinPhoneTeamSetupPorts(
        connection: connection,
        linux: linux,
        starter: starter,
        notice: 'fixture',
      );
      expect(ports.hasServer, isTrue);
      expect(ports.wasOn, isTrue);

      await store.upsert(
        ServerProfile(
          id: 'phone1',
          name: 'Phone 1',
          baseUrl: 'http://127.0.0.1:4097',
        ),
      );
      await store.setActiveId('phone1');
      expect(ports.wasOn, isFalse);
      await store.setActiveId('phone2');
      expect(ports.wasOn, isTrue);
    },
  );

  for (final historyPending in [false, true]) {
    test(
      'setup does not borrow remote capabilities while phone migration is pending ($historyPending)',
      () async {
        _mockChannels();
        debugPlatformCapabilities = const PlatformCapabilities.android();
        addTearDown(() => debugPlatformCapabilities = null);
        final connection = await _connection();
        await connection.store.upsert(
          ServerProfile(
            id: 'migrating',
            name: 'Migrating phone',
            baseUrl: 'http://127.0.0.1:4097',
            requiresOpenCode2Migration: !historyPending,
            openCode2HistoryPending: historyPending,
          ),
        );
        await connection.store.upsert(
          ServerProfile(
            id: 'remote',
            name: 'Remote',
            baseUrl: 'https://example.org',
          ),
        );
        await connection.store.setActiveId('remote');
        final linux = BuiltinLinux();
        final starter = BuiltinServerStarter(linux: linux);
        addTearDown(starter.dispose);
        final ports = BuiltinPhoneTeamSetupPorts(
          connection: connection,
          linux: linux,
          starter: starter,
          notice: 'fixture',
        );
        expect(connection.capabilities.phoneTeamExecution, isTrue);
        expect(
          connection
              .serverCapabilitiesFor(
                connection.store.profiles.firstWhere(
                  (p) => p.id == 'migrating',
                ),
              )
              .phoneTeamExecution,
          isFalse,
        );
        expect(ports.hasServer, isFalse);
      },
    );
  }

  test(
    'real setup ports stop the attempted profile after selection changes',
    () async {
      _mockChannels();
      debugPlatformCapabilities = const PlatformCapabilities.android();
      addTearDown(() => debugPlatformCapabilities = null);
      SharedPreferences.setMockInitialValues({});
      final store = ProfileStore(prefs: await SharedPreferences.getInstance());
      for (final id in ['older', 'oc2']) {
        await store.upsert(
          ServerProfile(
            id: id,
            name: id,
            baseUrl: BuiltinLinux.serverUrl,
            flavor: ServerFlavor.v2,
          ),
        );
      }
      await store.setActiveId('older');
      final bridge = NativeBridge();
      final connection = ConnectionController(
        store,
        phoneEngineBridge: bridge,
        phoneEngineGatewayBuilder:
            ({required baseUrl, required profileId, required bearerToken}) =>
                PhoneEngineGateway(
                  baseUrl: baseUrl,
                  profileId: profileId,
                  bearerToken: bearerToken,
                  adapter: engine.FakeEngineAdapter(
                    (_) async => engine.jsonBody(engine.health(profileId)),
                  ),
                ),
      );
      addTearDown(connection.dispose);
      addTearDown(connection.phoneProjectEngine.close);
      final linux = BuiltinLinux();
      final starter = BuiltinServerStarter(linux: linux);
      addTearDown(starter.dispose);
      final ports = BuiltinPhoneTeamSetupPorts(
        connection: connection,
        linux: linux,
        starter: starter,
        notice: 'fixture',
      );
      await ports.startEngine();
      bridge.calls.clear();
      await store.setActiveId('oc2');
      await ports.stopEngine();
      expect(bridge.calls, ['stop:older']);
      await ports.startEngine();
      expect(bridge.calls, contains('start:oc2'));
    },
  );

  group('the flow', () {
    test('a running reply is waited for, never cut short', () async {
      final phone = _Phone()..reply = true;
      final flow = _flow(phone);
      final run = flow.run();
      await Future<void>.delayed(Duration.zero);
      expect(flow.waitingForReply, isTrue);
      expect(phone.calls, isEmpty);
      phone.finishReply();
      await run;
      expect(flow.phase, PhoneTeamSetupPhase.done);
      expect(phone.calls.first, 'inspect');
    });

    test('a reply whose grace ends without an event does not hold the '
        'flow', () async {
      // replyRunning turns false with no notification (a just-sent
      // prompt's grace window expiring); the flow must still move on.
      final phone = _Phone()..reply = true;
      final flow = _flow(phone);
      final run = flow.run();
      await Future<void>.delayed(Duration.zero);
      expect(flow.waitingForReply, isTrue);
      phone.reply = false; // no notifyListeners
      await run.timeout(const Duration(seconds: 5));
      expect(flow.phase, PhoneTeamSetupPhase.done);
    });

    test('a passed check before OpenCode restarts is not a failure '
        '(boundary_attested)', () async {
      // Right after the proof, OpenCode is not verified yet: the check
      // passes on the boundary and the server step waits for canExecute.
      final phone = _Phone()
        ..started = _health(oc2Verified: false, reason: 'boundary_attested');
      final flow = _flow(phone);
      await flow.run();
      expect(flow.isDone, isTrue);
    });

    test(
      'nothing running: no stop question, steps skipped, then ready',
      () async {
        final phone = _Phone();
        final flow = _flow(phone);
        await flow.run();
        expect(flow.isDone, isTrue);
        expect(flow.wasSkipped(PhoneTeamSetupStep.stop), isTrue);
        expect(phone.calls, [
          'inspect',
          'startEngine',
          'startServer',
          'probe',
          'attach',
        ]);
      },
    );

    test('a running server asks first; no stops before a yes', () async {
      final phone = _Phone()
        ..host = const PhoneTeamHostState(serverRunning: true, terminals: 2);
      final flow = _flow(phone);
      final run = flow.run();
      await Future<void>.delayed(Duration.zero);
      await Future<void>.delayed(Duration.zero);
      expect(flow.phase, PhoneTeamSetupPhase.confirming);
      expect(phone.calls, ['inspect']);
      flow.answer(true);
      await run;
      expect(flow.isDone, isTrue);
      expect(
        phone.calls,
        containsAllInOrder([
          'stopServer',
          'closeTerminals',
          'startEngine',
          'startServer',
        ]),
      );
    });

    test('the stop step times the work, not the wait for the yes', () async {
      var t = DateTime(2026, 10, 15, 12);
      final phone = _SlowStopPhone((d) => t = t.add(d))
        ..host = const PhoneTeamHostState(serverRunning: true, terminals: 2);
      final flow = PhoneTeamSetupController(
        phone,
        now: () => t,
        delay: (_) async {},
        readyAttempts: 2,
      );
      final run = flow.run();
      await Future<void>.delayed(Duration.zero);
      await Future<void>.delayed(Duration.zero);
      expect(flow.phase, PhoneTeamSetupPhase.confirming);
      t = t.add(const Duration(minutes: 2));
      flow.answer(true);
      await run;
      expect(flow.took(PhoneTeamSetupStep.stop), const Duration(seconds: 10));
    });

    test('saying no changes nothing and ends in plain words', () async {
      final phone = _Phone()
        ..host = const PhoneTeamHostState(serverRunning: true);
      final flow = _flow(phone);
      final run = flow.run();
      await Future<void>.delayed(Duration.zero);
      await Future<void>.delayed(Duration.zero);
      flow.answer(false);
      await run;
      expect(flow.phase, PhoneTeamSetupPhase.failed);
      expect(flow.problem, PhoneTeamSetupProblem.declined);
      expect(phone.calls, ['inspect']);
    });

    test(
      'an engine that finds an old server asks, once, then retries',
      () async {
        final phone = _Phone()..started = _health(restartRequired: true);
        final flow = _flow(phone);
        final run = flow.run();
        await Future<void>.delayed(Duration.zero);
        await Future<void>.delayed(Duration.zero);
        await Future<void>.delayed(Duration.zero);
        expect(flow.phase, PhoneTeamSetupPhase.confirming);
        // The second start answers clean.
        phone.started = _health();
        flow.answer(true);
        await run;
        expect(flow.isDone, isTrue);
        expect(phone.startCalls, 2);
      },
    );

    test(
      'issue #111: a failed proof names its step in Details and diagnostics',
      () async {
        final lines = <String>[];
        final phone = _Phone()
          ..startFailure = 'boundary_proof_failed:native.signal-31';
        final flow = PhoneTeamSetupController(
          phone,
          delay: (_) async {},
          readyAttempts: 2,
          onFailure: lines.add,
        );
        await flow.run();
        expect(flow.phase, PhoneTeamSetupPhase.failed);
        expect(flow.failedStep, PhoneTeamSetupStep.check);
        expect(flow.details, 'boundary_proof_failed:native.signal-31');
        expect(phone.calls.last, 'stopEngine');
        expect(lines, hasLength(1));
        expect(lines.single, contains('step check'));
        expect(lines.single, contains('code boundary_proof_failed'));
        expect(lines.single, contains('step native signal-31'));
        expect(lines.single, contains('kind signal'));
        expect(lines.single, contains('signal 31'));
        // The diagnostics export masks long tokens with digits: the step
        // survives it, field by field.
        final exported = AppDiagnosticsController().sanitize(lines.single);
        expect(exported, contains('step native signal-31'));
        final long = PhoneProofStep.parse(
          'boundary_proof_failed:ubuntu.deny-proc-root-write.exit-1',
        )!;
        expect(
          AppDiagnosticsController().sanitize(long.diagnostic),
          contains('step ubuntu deny-proc-root-write exit-1'),
        );
      },
    );

    test('a failed proof stays off, keeps reasons for Details', () async {
      final phone = _Phone()
        ..started = _health(boundary: false, reason: 'boundary_proof_failed');
      final flow = _flow(phone);
      await flow.run();
      expect(flow.phase, PhoneTeamSetupPhase.failed);
      expect(flow.problem, PhoneTeamSetupProblem.unsafe);
      expect(flow.failedStep, PhoneTeamSetupStep.check);
      expect(flow.details, 'boundary_proof_failed');
      expect(phone.calls, isNot(contains('startServer')));
      expect(phone.calls.last, 'stopEngine');
      expect(phone.engineRunning, isFalse);
    });

    test(
      'B-3: a failure after the stop puts OpenCode back, once, and says so',
      () async {
        final phone = _Phone()
          ..host = const PhoneTeamHostState(serverRunning: true, terminals: 1)
          ..started = _health(boundary: false, reason: 'boundary_proof_failed');
        final flow = _flow(phone);
        final run = flow.run();
        await Future<void>.delayed(Duration.zero);
        await Future<void>.delayed(Duration.zero);
        flow.answer(true);
        await run;
        expect(flow.phase, PhoneTeamSetupPhase.failed);
        expect(flow.serverState, PhoneTeamServerState.backOn);
        expect(flow.terminalsClosed, isTrue);
        expect(phone.calls.where((c) => c == 'restoreServer'), hasLength(1));
        expect(phone.calls.indexOf('restoreServer'), greaterThan(2));

        phone.restoreResult = false;
        phone.started = _health(boundary: false, reason: 'x');
        final again = _flow(phone);
        final second = again.run();
        await Future<void>.delayed(Duration.zero);
        await Future<void>.delayed(Duration.zero);
        again.answer(true);
        await second;
        expect(again.serverState, PhoneTeamServerState.stillOff);
      },
    );

    test(
      'B-3: a failure that never stopped OpenCode restores nothing',
      () async {
        final phone = _Phone()
          ..started = _health(boundary: false, reason: 'boundary_proof_failed');
        final flow = _flow(phone);
        await flow.run();
        expect(flow.serverState, PhoneTeamServerState.untouched);
        expect(phone.calls, isNot(contains('restoreServer')));
      },
    );

    test('OpenCode that does not come back is its own failure', () async {
      final phone = _Phone()..serverFailure = 'timedOut';
      final flow = _flow(phone);
      await flow.run();
      expect(flow.problem, PhoneTeamSetupProblem.serverStart);
      expect(flow.failedStep, PhoneTeamSetupStep.server);
      expect(phone.calls.last, 'stopEngine');
    });

    test(
      'an engine that still cannot run work after the probe is not ready',
      () async {
        final phone = _Phone()..probed = _health(execution: false);
        final flow = _flow(phone);
        await flow.run();
        expect(flow.problem, PhoneTeamSetupProblem.notReady);
        expect(phone.calls.where((c) => c == 'probe'), hasLength(2));
        expect(phone.engineRunning, isFalse);
      },
    );

    test(
      'unsupported OC1 readiness releases the engine before an OC2 retry',
      () async {
        final phone = _Phone()
          ..requireReleasedStart = true
          ..host = const PhoneTeamHostState(serverRunning: true)
          ..probed = _health(
            oc2Verified: false,
            reason: 'unsupported_server_version',
          );
        final flow = _flow(phone);
        final first = flow.run();
        await Future<void>.delayed(Duration.zero);
        flow.answer(true);
        await first;
        expect(flow.problem, PhoneTeamSetupProblem.notReady);
        expect(flow.details, 'unsupported_server_version');
        expect(phone.engineRunning, isFalse);
        expect(
          phone.calls,
          containsAllInOrder(['probe', 'stopEngine', 'restoreServer']),
        );

        phone.host = const PhoneTeamHostState();
        phone.probed = _health(oc2: true);
        await flow.run();
        expect(flow.isDone, isTrue);
        expect(phone.engineRunning, isTrue);
        expect(phone.startCalls, 2);
      },
    );

    test(
      'failure waits for engine cleanup before restoring or accepting retry',
      () async {
        final stop = Completer<void>();
        final phone = _Phone()
          ..host = const PhoneTeamHostState(serverRunning: true)
          ..probed = _health(execution: false)
          ..holdStop = stop.future;
        final flow = _flow(phone);
        final first = flow.run();
        await Future<void>.delayed(Duration.zero);
        flow.answer(true);
        await Future<void>.delayed(Duration.zero);
        expect(phone.calls, contains('stopEngine'));
        expect(phone.calls, isNot(contains('restoreServer')));
        expect(flow.isRunning, isTrue);
        final joined = flow.run();
        expect(identical(joined, first), isTrue);
        expect(phone.startCalls, 1);
        stop.complete();
        await first;
        expect(phone.engineRunning, isFalse);
        expect(phone.calls.last, 'restoreServer');
      },
    );

    test(
      'cleanup failure retains the refusal and only a safe cleanup code',
      () async {
        final phone = _Phone()
          ..probed = _health(
            execution: false,
            reason: 'unsupported_server_version',
          )
          ..failStop = true;
        final flow = _flow(phone);
        await flow.run();
        expect(flow.details, 'unsupported_server_version; engineStopFailed');
        expect(flow.details, isNot(contains('private')));
        expect(flow.phase, PhoneTeamSetupPhase.failed);
        phone.calls.clear();
        phone.failStop = false;
        phone.probed = _health();
        await flow.run();
        expect(phone.calls.first, 'stopEngine');
        expect(flow.isDone, isTrue);
      },
    );

    test(
      'declining a restart discovered after launch releases that engine',
      () async {
        final phone = _Phone()..started = _health(restartRequired: true);
        final flow = _flow(phone);
        final run = flow.run();
        await Future<void>.delayed(Duration.zero);
        expect(flow.phase, PhoneTeamSetupPhase.confirming);
        expect(phone.engineRunning, isTrue);
        flow.answer(false);
        await run;
        expect(flow.problem, PhoneTeamSetupProblem.declined);
        expect(phone.calls, ['inspect', 'startEngine', 'stopEngine']);
        expect(phone.engineRunning, isFalse);
      },
    );

    test('no OpenCode on this phone says so, changes nothing', () async {
      final phone = _Phone()..server = false;
      final flow = _flow(phone);
      await flow.run();
      expect(flow.problem, PhoneTeamSetupProblem.noServer);
      expect(phone.calls, isEmpty);
    });

    test(
      'after an update, a team that was on is checked again alone',
      () async {
        final off = _Phone();
        await _flow(off).autoProof();
        expect(off.calls, isEmpty, reason: 'never turned on: nothing to redo');

        final healthy = _Phone()..on = true;
        await _flow(healthy).autoProof();
        expect(healthy.calls, ['probe'], reason: 'proof still holds');

        final stale = _Phone()
          ..on = true
          ..probed = _health(execution: false)
          ..host = const PhoneTeamHostState(serverRunning: true);
        final flow = _flow(stale);
        unawaited(flow.autoProof());
        for (var i = 0; i < 5; i++) {
          await Future<void>.delayed(Duration.zero);
        }
        expect(flow.automatic, isTrue);
        expect(flow.phase, PhoneTeamSetupPhase.confirming);
        expect(
          stale.calls,
          isNot(contains('stopServer')),
          reason: 'the person\'s chat is never killed silently',
        );
        flow.dispose();
      },
    );
  });

  group('issue #111: proof step', () {
    test('parses every step shape into a plain kind', () {
      PhoneProofStep step(String code) =>
          PhoneProofStep.parse('boundary_proof_failed:$code')!;
      expect(step('native.signal-31').kind, PhoneProofStepKind.signal);
      expect(step('native.signal-31').signal, 31);
      final io = step('native.io-uring.signal-11');
      expect(io.kind, PhoneProofStepKind.checkSignal);
      expect(io.check, 'io-uring');
      expect(io.signal, 11);
      expect(step('native.chmod.errno-38').kind, PhoneProofStepKind.notBlocked);
      expect(step('native.open-0-0.allowed').check, 'open-0-0');
      expect(
        step('native.sandbox.landlock_rule_failed').kind,
        PhoneProofStepKind.sandbox,
      );
      expect(step('native.no-answer').kind, PhoneProofStepKind.noAnswer);
      expect(
        step('native.worker-write.errno-13').kind,
        PhoneProofStepKind.other,
      );
      final git = step('ubuntu.git-commit.exit-128');
      expect(git.kind, PhoneProofStepKind.ubuntu);
      expect(git.check, 'git-commit');
      expect(
        step('ubuntu.git-init.no-answer').kind,
        PhoneProofStepKind.noAnswer,
      );
      expect(step('fixture-changed').kind, PhoneProofStepKind.fixture);
      expect(
        step('setup.proof_fixture_unavailable').kind,
        PhoneProofStepKind.fixture,
      );
      expect(
        step('proot-tier.daemonProcDenied').kind,
        PhoneProofStepKind.proot,
      );
      expect(step('setup.restart_required').check, 'restart_required');
      // A cleanup suffix after the code does not leak into the step.
      expect(
        PhoneProofStep.parse(
          'boundary_proof_failed:native.signal-31; engineStopFailed',
        )!.code,
        'native.signal-31',
      );
    });

    test('a bare or unrelated code has no step; odd text is refused', () {
      expect(PhoneProofStep.parse('boundary_proof_failed'), isNull);
      expect(PhoneProofStep.parse('timeout:startEngine'), isNull);
      expect(PhoneProofStep.parse(null), isNull);
      expect(PhoneProofStep.parse('boundary_proof_failed:/data/x\'y'), isNull);
    });

    test('Details: plain sentence, the step in plain words, then the code', () {
      final text = phoneTeamDetailsText(
        _en,
        PhoneTeamSetupProblem.engine,
        'boundary_proof_failed:native.signal-31',
      )!;
      expect(text.split('\n'), [
        _en.phoneTeamWhyUnsafe,
        _en.phoneTeamProofSignal(31),
        'boundary_proof_failed:native.signal-31',
      ]);
      expect(
        phoneTeamProofStepText(
          _en,
          'boundary_proof_failed:ubuntu.git-commit.exit-128',
        ),
        _en.phoneTeamProofUbuntu('git-commit'),
      );
      // A failure with no step keeps its old two lines.
      expect(
        phoneTeamDetailsText(
          _en,
          PhoneTeamSetupProblem.engine,
          'timeout:startEngine',
        ),
        '${_en.phoneTeamWhyEngine}\ntimeout:startEngine',
      );
    });
  });

  group('the page', () {
    testWidgets('waiting for a reply, then the stop question, then ready', (
      tester,
    ) async {
      _size(tester);
      final connection = await _connection();
      final phone = _Phone()
        ..reply = true
        ..host = const PhoneTeamHostState(serverRunning: true, terminals: 1);
      final flow = _flow(phone);
      await tester.pumpWidget(
        _app(TeamPhoneSetupScreen(connection: connection, controller: flow)),
      );
      await _settle(tester);
      expect(find.text(_en.phoneTeamStepReply), findsOneWidget);
      expect(find.textContaining(_en.phoneTeamReplyWaiting), findsOneWidget);

      phone.finishReply();
      await _settle(tester);
      expect(find.text(_en.phoneTeamStopTitle), findsOneWidget);
      expect(find.text(_en.phoneTeamStopBody), findsOneWidget);
      expect(phone.calls, ['inspect'], reason: 'nothing stops before a yes');

      await tester.tap(find.byKey(const ValueKey('phone-team-stop-confirm')));
      await _settle(tester);
      expect(find.text(_en.phoneTeamDoneTitle), findsOneWidget);
      expect(find.text(_en.teamProjectNew), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('issue #111: Details says where the safety check stopped', (
      tester,
    ) async {
      _size(tester);
      final connection = await _connection();
      final phone = _Phone()
        ..startFailure = 'boundary_proof_failed:native.io-uring.signal-11';
      final flow = _flow(phone);
      await tester.pumpWidget(
        _app(TeamPhoneSetupScreen(connection: connection, controller: flow)),
      );
      await _settle(tester);
      final plain = _en.phoneTeamProofCheckSignal('io-uring', 11);
      // The step names technical checks, so it lives in Details (COPY-11).
      expect(find.textContaining(plain), findsNothing);
      expect(
        find.textContaining('boundary_proof_failed:native.io-uring.signal-11'),
        findsNothing,
      );
      await tester.tap(find.text('Details'));
      await _settle(tester);
      expect(find.textContaining(_en.phoneTeamWhyUnsafe), findsOneWidget);
      expect(find.textContaining(plain), findsOneWidget);
      expect(
        find.textContaining('boundary_proof_failed:native.io-uring.signal-11'),
        findsOneWidget,
      );
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('a failed proof: plain words, Details, Start again', (
      tester,
    ) async {
      _size(tester);
      final connection = await _connection();
      final phone = _Phone()
        ..started = _health(boundary: false, reason: 'boundary_proof_failed');
      final flow = _flow(phone);
      await tester.pumpWidget(
        _app(TeamPhoneSetupScreen(connection: connection, controller: flow)),
      );
      await _settle(tester);
      expect(find.text(_en.phoneTeamFailUnsafeTitle), findsOneWidget);
      // B-4: the body says exactly what state OpenCode is in; nothing was
      // stopped here, and it never claims "nothing else was changed".
      expect(find.textContaining(_en.phoneTeamFailUnsafeBody), findsOneWidget);
      expect(find.textContaining(_en.phoneTeamStateNotStopped), findsOneWidget);
      expect(find.textContaining('Nothing else was changed'), findsNothing);
      // The code is not plain copy; it is behind Details.
      expect(find.text('boundary_proof_failed'), findsNothing);
      expect(find.text(_en.phoneTeamStepCheck), findsOneWidget);
      await tester.tap(find.text('Details'));
      await _settle(tester);
      // Details: one plain sentence, then the code.
      expect(find.textContaining(_en.phoneTeamWhyUnsafe), findsOneWidget);
      expect(find.textContaining('boundary_proof_failed'), findsOneWidget);

      // Start again runs it afresh and can succeed.
      phone.started = _health();
      await tester.tap(find.byKey(const ValueKey('phone-team-start-again')));
      await _settle(tester);
      expect(find.text(_en.phoneTeamDoneTitle), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets(
      'unknown ownership offers Android recovery without another start',
      (tester) async {
        _size(tester);
        final connection = await _connection();
        final phone = _Phone()..serverFailure = 'componentRecovery';
        final flow = _flow(phone);
        var settings = 0;
        const channel = MethodChannel('oc/termux');
        final messenger =
            TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
        messenger.setMockMethodCallHandler(channel, (call) async {
          if (call.method == 'openAppSettings') settings++;
          return true;
        });
        addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
        await tester.pumpWidget(
          _app(TeamPhoneSetupScreen(connection: connection, controller: flow)),
        );
        await _settle(tester);
        expect(flow.needsComponentRecovery, isTrue);
        expect(find.text(_en.inAppServerOwnershipRecoveryBody), findsOneWidget);
        expect(find.textContaining('componentRecovery'), findsNothing);
        expect(
          find.byKey(const ValueKey('phone-team-start-again')),
          findsNothing,
        );
        await tester.tap(
          find.byKey(const ValueKey('phone-team-recover-ownership')),
        );
        await _settle(tester);
        expect(settings, 1);
        expect(
          phone.calls.where((call) => call == 'startServer'),
          hasLength(1),
        );
        await tester.pumpWidget(const SizedBox.shrink());
      },
    );

    testWidgets('B-3/B-4: after a stop, the failure says OpenCode is back on', (
      tester,
    ) async {
      _size(tester);
      final connection = await _connection();
      final phone = _Phone()
        ..host = const PhoneTeamHostState(serverRunning: true, terminals: 1)
        ..started = _health(boundary: false, reason: 'engine_bundle_invalid');
      await tester.pumpWidget(
        _app(
          TeamPhoneSetupScreen(
            connection: connection,
            controller: _flow(phone),
          ),
        ),
      );
      await _settle(tester);
      await tester.tap(find.byKey(const ValueKey('phone-team-stop-confirm')));
      await _settle(tester);
      expect(find.textContaining(_en.phoneTeamStateBackOn), findsOneWidget);
      expect(
        find.textContaining(_en.phoneTeamStateTerminalsClosed),
        findsOneWidget,
      );
      expect(phone.calls, contains('restoreServer'));
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('no from the question leaves a calm page, nothing stopped', (
      tester,
    ) async {
      _size(tester);
      final connection = await _connection();
      final phone = _Phone()
        ..host = const PhoneTeamHostState(serverRunning: true);
      await tester.pumpWidget(
        _app(
          TeamPhoneSetupScreen(
            connection: connection,
            controller: _flow(phone),
          ),
        ),
      );
      await _settle(tester);
      await tester.tap(find.text(_en.phoneTeamStopCancel));
      await _settle(tester);
      expect(find.text(_en.phoneTeamFailDeclinedTitle), findsOneWidget);
      expect(phone.calls, ['inspect']);
      await tester.pumpWidget(const SizedBox.shrink());
    });
  });

  group('execution gating', () {
    Future<(OrchestrationController, ConnectionController)> team({
      required bool lanes,
      bool simulated = false,
      bool phoneOffline = false,
      String tier = '',
    }) async {
      _mockChannels();
      final connection = await _connection();
      final prefs = await SharedPreferences.getInstance();
      final owner = OrchestrationController(
        profile: ServerProfile(
          id: 'p',
          name: 'This phone',
          baseUrl: 'http://127.0.0.1:4097',
        ),
        config: const OrchestrationConfig(
          provider: OrchestrationProvider.fixture,
          url: 'fixture://gate',
        ),
        store: OrchestrationStore(prefs),
        gatewayFactory: (_, _) => _Gateway(
          lanes: lanes,
          simulated: simulated,
          phoneOffline: phoneOffline,
        ),
        probe: (config) async => ProbeFound(
          host: OrchestrationHostIdentity(
            provider: 'fixture',
            url: config.url,
            hostMode: config.hostMode,
          ),
          readOnly: !lanes,
          boundaryTier: tier,
        ),
      );
      addTearDown(owner.dispose);
      await owner.start();
      return (owner, connection);
    }

    testWidgets('blocked: one plain line and the fix, no New project', (
      tester,
    ) async {
      _size(tester);
      final (owner, connection) = await team(lanes: false);
      TeamExecutionGate.bind(owner, connection);
      final phone = _Phone()..reply = true;
      PhoneTeamSetup.debugPorts = (_) => phone;
      await tester.pumpWidget(
        _app(TeamProjectsScreen(controller: owner.projectController!)),
      );
      await _settle(tester);
      expect(find.text(_en.phoneTeamBlocked), findsOneWidget);
      expect(find.text(_en.teamProjectNew), findsNothing);
      expect(find.text(_en.teamProjectQuick), findsNothing);

      // The fix is the setup flow, waiting for the reply it found.
      await tester.tap(find.byKey(const ValueKey('team-execution-set-up')));
      await _settle(tester);
      expect(find.byKey(const ValueKey('phone-team-steps')), findsOneWidget);
      expect(find.textContaining(_en.phoneTeamReplyWaiting), findsOneWidget);
      // Let the flow finish so its reply poll ends with the test.
      phone.finishReply();
      await tester.pump(const Duration(seconds: 3));
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(seconds: 3));
    });

    testWidgets('ready engine: no line, New project is there', (tester) async {
      _size(tester);
      final (owner, connection) = await team(lanes: true);
      TeamExecutionGate.bind(owner, connection);
      await tester.pumpWidget(
        _app(TeamProjectsScreen(controller: owner.projectController!)),
      );
      await _settle(tester);
      expect(find.text(_en.phoneTeamBlocked), findsNothing);
      expect(find.text(_en.teamProjectNew), findsOneWidget);
    });

    testWidgets('ready engine: one plain protection line for the proven '
        'boundary, nothing when unknown', (tester) async {
      _size(tester);
      for (final (tier, line) in [
        ('proot', _en.phoneTeamProtectedProot),
        ('landlock', _en.phoneTeamProtectedLandlock),
        ('none', null),
        ('', null),
      ]) {
        final (owner, connection) = await team(lanes: true, tier: tier);
        TeamExecutionGate.bind(owner, connection);
        await tester.pumpWidget(
          _app(TeamProjectsScreen(controller: owner.projectController!)),
        );
        await _settle(tester);
        expect(
          find.byKey(const ValueKey('team-protection-line')),
          line == null ? findsNothing : findsOneWidget,
          reason: tier,
        );
        if (line != null) expect(find.text(line), findsOneWidget);
        await tester.pumpWidget(const SizedBox.shrink());
      }
      // Not shown while the engine cannot run work.
      final (owner, connection) = await team(lanes: false, tier: 'proot');
      TeamExecutionGate.bind(owner, connection);
      await tester.pumpWidget(
        _app(TeamProjectsScreen(controller: owner.projectController!)),
      );
      await _settle(tester);
      expect(find.text(_en.phoneTeamProtectedProot), findsNothing);
    });

    testWidgets('the Ready page shows the protection line from the real '
        'health shape (boundaryTier proot)', (tester) async {
      _size(tester);
      final connection = await _connection();
      final real = PhoneEngineHealth.fromJson({
        'schemaVersion': 1,
        'engineVersion': '1',
        'profileId': 'p',
        'boundaryTier': 'proot',
        'capabilities': {
          'execution': true,
          'boundary': true,
          'boundaryTier': 'proot',
          'oc2Verified': true,
          'oc2': true,
        },
        'commandActions': <String>[],
      }, 'p');
      final phone = _Phone()
        ..started = real
        ..probed = real;
      final flow = _flow(phone);
      await tester.pumpWidget(
        _app(TeamPhoneSetupScreen(connection: connection, controller: flow)),
      );
      await flow.run();
      await _settle(tester);
      expect(find.text(_en.phoneTeamDoneTitle), findsOneWidget);
      expect(find.text(_en.phoneTeamProtectedProot), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('project Servers: This phone not reachable says why and '
        'offers the fix', (tester) async {
      _size(tester);
      for (final lanes in [false, true]) {
        final (owner, connection) = await team(
          lanes: lanes,
          phoneOffline: true,
          tier: 'proot',
        );
        TeamExecutionGate.bind(owner, connection);
        final c = owner.projectController!;
        await c.load();
        final snap = c.snapshot!;
        await tester.pumpWidget(
          _app(
            TeamProjectServers(
              controller: c,
              projectId: snap.projects.first.id,
            ),
          ),
        );
        await _settle(tester);
        expect(
          find.text(
            lanes ? _en.teamServerPhoneNoAnswer : _en.teamServerPhoneNotReady,
          ),
          findsOneWidget,
          reason: 'lanes=$lanes',
        );
        expect(
          find.byKey(
            ValueKey(lanes ? 'team-server-retry' : 'team-server-set-up'),
          ),
          findsOneWidget,
        );
        await tester.pumpWidget(const SizedBox.shrink());
      }
    });

    testWidgets('the demo is never gated, even when its page is bound to a '
        'phone team that is not ready', (tester) async {
      _size(tester);
      final (owner, connection) = await team(lanes: false, simulated: true);
      TeamExecutionGate.bind(owner, connection);
      await tester.pumpWidget(
        _app(TeamProjectsScreen(controller: owner.projectController!)),
      );
      await _settle(tester);
      expect(find.text(_en.phoneTeamBlocked), findsNothing);
      expect(find.text(_en.teamProjectNew), findsOneWidget);
    });

    testWidgets('the demo is never gated', (tester) async {
      _size(tester);
      final (owner, _) = await team(lanes: false);
      await tester.pumpWidget(
        _app(TeamProjectsScreen(controller: owner.projectController!)),
      );
      await _settle(tester);
      expect(find.text(_en.phoneTeamBlocked), findsNothing);
      expect(find.text(_en.teamProjectNew), findsOneWidget);
    });
  });
}

// Shared with the golden scenes.
PhoneTeamSetupPortsForGolden phoneForGolden() => _Phone();
PhoneEngineHealth healthForGolden({bool boundary = true, String reason = ''}) =>
    _health(boundary: boundary, reason: reason);
typedef PhoneTeamSetupPortsForGolden = _Phone;
