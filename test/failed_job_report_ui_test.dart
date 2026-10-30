// P8.4 "Failed jobs carry their log": a failed team run (the Gate sheet's
// Run failed), a failed AI Team start and a failed dev service offer
// "Report this failure", which opens Report a problem with the job's
// redacted log under its Details, exactly as the report sends it. A job that
// kept no log says so. Fakes only: no network, no upload, fake keys.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/team/builtin_team.dart';
import 'package:opencode_mobile/builtin/team/builtin_team_job.dart';
import 'package:opencode_mobile/diagnostics/failed_job_report.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/feedback/problem_report.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/development_service_store.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/app_diagnostics_screen.dart';
import 'package:opencode_mobile/ui/screens/development_services_screen.dart';
import 'package:opencode_mobile/ui/screens/team/gate_sheet.dart';
import 'package:opencode_mobile/ui/widgets/builtin_team_section.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/development_service_fakes.dart';

const _fakeKey =
    'sk-ant-'
    'api03-FAKEFAKEFAKEFAKEFAKE1234567890';

final _en = lookupAppLocalizations(const Locale('en'));

Finder _key(String key) => find.byKey(ValueKey(key));

Widget _app(Widget home) => MaterialApp(
  locale: const Locale('en'),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: home,
);

void _tall(WidgetTester tester) {
  tester.view.physicalSize = const Size(800, 1600);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

/// The job log lines the report carries, read from Details (exactly what
/// is sent): the lines after "Failed job log (last lines)".
Future<List<String>> _panelLines(WidgetTester tester) async {
  if (_key('report-problem-preview-text').evaluate().isEmpty) {
    await tester.ensureVisible(_key('report-problem-details'));
    await tester.pumpAndSettle();
    await tester.tap(_key('report-problem-details'));
    await tester.pumpAndSettle();
  }
  final text = tester.widget<KitText>(_key('report-problem-preview-text')).text;
  final lines = text.split('\n');
  final start = lines.indexOf('Failed job log (last lines)');
  if (start < 0) return const [];
  return lines.skip(start + 1).takeWhile((line) => line.isNotEmpty).toList();
}

Future<String> _previewText(WidgetTester tester) async {
  await tester.enterText(
    find.descendant(
      of: _key('report-problem-description'),
      matching: find.byType(EditableText),
    ),
    'It failed',
  );
  await tester.pumpAndSettle();
  // Details holds exactly what is sent.
  await tester.ensureVisible(_key('report-problem-details'));
  await tester.pumpAndSettle();
  await tester.tap(_key('report-problem-details'));
  await tester.pumpAndSettle();
  return tester.widget<KitText>(_key('report-problem-preview-text')).text;
}

/// A scripted team host that serves one agent's output.
class _Gateway
    implements OrchestrationGateway, OrchestrationAgentOutputGateway {
  _Gateway({this.capabilities = OrchestrationCapabilities.fixture});

  @override
  final OrchestrationCapabilities capabilities;
  final gateList = <OrchestrationGate>[];
  final runList = <OrchestrationRun>[];
  final workList = <WorkItem>[];
  final agentList = <OrchestrationAgent>[];
  final stream = StreamController<OrchestrationEvent>.broadcast();
  final outputs = <String, StreamController<AgentOutputEvent>>{};
  final outputRequests = <String>[];
  bool _closed = false;

  @override
  OrchestrationHostIdentity? get host => const OrchestrationHostIdentity(
    provider: 'fixture',
    url: 'fixture://scripted',
    hostMode: OrchestrationHostMode.computer,
  );

  @override
  bool get isClosed => _closed;

  @override
  Future<void> close() async {
    _closed = true;
    await stream.close();
  }

  @override
  Stream<AgentOutputEvent> agentOutput(String sessionId) {
    outputRequests.add(sessionId);
    return (outputs[sessionId] ??= StreamController.broadcast()).stream;
  }

  @override
  Future<List<OrchestrationProject>> projects() async => const [];
  @override
  Future<List<OrchestrationRun>> runs({String? projectId}) async => runList;
  @override
  Future<OrchestrationRun?> run(String id) async => null;
  @override
  Future<List<WorkItem>> work({String? projectId}) async => workList;
  @override
  Future<List<WorkItem>> readyWork({String? projectId}) async => const [];
  @override
  Future<WorkItem?> workItem(String id) async => null;
  @override
  Future<List<OrchestrationAgent>> agents() async => agentList;
  @override
  Future<OrchestrationAgent?> agent(String id) async => null;
  @override
  Future<List<OrchestrationGate>> gates() async => gateList;
  @override
  Future<OrchestrationUsage?> usage() async => null;
  @override
  Future<List<ActivityEvent>> activity({
    int? afterSeq,
    int limit = 100,
  }) async => const [];
  @override
  Stream<OrchestrationEvent> events({
    EventCursor resumeFrom = EventCursor.none,
  }) => stream.stream;

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

/// A team whose turn-on fails at once with a script's output.
class _FailingTeam extends BuiltinTeam {
  _FailingTeam() : super(linux: BuiltinLinux());

  @override
  Future<BuiltinTeamState> status() async =>
      const BuiltinTeamState(installed: true, hasCity: true);

  @override
  Future<void> prepare() async {}
}

class _Store extends ProfileStore {
  _Store({required super.prefs, required this.saved});

  final List<ServerProfile> saved;

  @override
  List<ServerProfile> get profiles => List.unmodifiable(saved);

  @override
  String? get activeId => saved.first.id;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    KitRedact.clearKnownSecrets();
    SharedPreferences.setMockInitialValues({});
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        );
  });
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          null,
        );
  });

  group('the report text', () {
    test('a job log leads the diagnostics, scrubbed, even with recent '
        'diagnostics left out', () {
      final report = ProblemReport.build(
        description: 'Setup stopped',
        version: '1.0.0+1',
        platform: 'Android',
        error: KitReport(
          title: 'Couldn\'t start the team',
          log:
              'curl http://10.0.0.7:4096/health\n'
              'auth $_fakeKey\n'
              'exit 7',
        ),
      );
      expect(report.diagnostics, startsWith('Failed job log (last lines)\n'));
      expect(report.diagnostics, contains('exit 7'));
      expect(report.diagnostics, contains('http://[server]/health'));
      expect(report.text, isNot(contains(_fakeKey)));
      expect(report.text, isNot(contains('10.0.0.7')));
    });

    test('a job that kept no log says so; a plain error adds nothing', () {
      final none = ProblemReport.build(
        description: '',
        version: '1',
        platform: 'Android',
        error: const KitReport(title: 'Run failed', log: ''),
      );
      expect(none.diagnostics, 'Failed job log: none was kept');
      final plain = ProblemReport.build(
        description: 'x',
        version: '1',
        platform: 'Android',
        error: const KitReport(title: 'Couldn\'t load files'),
      );
      expect(plain.diagnostics, isEmpty);
    });

    test('a failed job becomes the page\'s attachment with its log', () {
      final work = const WorkItem(
        id: 'w-1',
        title: 'Write tests',
        state: WorkState.failed,
        sessionId: 'ses-1',
      );
      final attached = FailedJobReport.teamWork(
        work,
        sessionId: 'ses-1',
        logTail: 'pytest: 3 failed',
      )!.toKitReport(title: 'Write tests stopped');
      expect(attached.title, 'Write tests stopped');
      expect(attached.log, 'pytest: 3 failed');
      expect(attached.source, contains('teamWork'));
      // Another session's output is never attached.
      final other = FailedJobReport.teamWork(
        work,
        sessionId: 'ses-2',
        logTail: 'unrelated',
      )!.toKitReport();
      expect(other.log, '');
      expect(other.title, 'Write tests');
    });
  });

  group('Report a problem with a job attached', () {
    testWidgets('says the log is attached and shows it under Details, '
        'redacted', (tester) async {
      _tall(tester);
      await tester.pumpWidget(
        _app(
          AppDiagnosticsScreen(
            error: const KitReport(
              title: 'Sync engine stopped',
              log: 'step 1 ok\nboom: token=abc\nexit 2',
            ),
            version: () async => '9.9.9+1',
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Sync engine stopped'), findsOneWidget);
      expect(find.text(_en.reportProblemJobLead), findsOneWidget);

      final preview = await _previewText(tester);
      expect(preview, contains('Failed job log (last lines)'));
      expect(preview, contains('step 1 ok'));
      expect(preview, contains('exit 2'));
    });

    testWidgets('says when the job kept no log', (tester) async {
      _tall(tester);
      await tester.pumpWidget(
        _app(
          AppDiagnosticsScreen(
            error: const KitReport(title: 'Run failed', log: ''),
            version: () async => '9.9.9+1',
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text(_en.reportProblemJobNoLogLead), findsOneWidget);
      final preview = await _previewText(tester);
      expect(preview, contains('Failed job log: none was kept'));
    });
  });

  group('Run failed gate', () {
    late SharedPreferences prefs;
    final clock = DateTime.utc(2026, 9, 27, 12);
    final profile = ServerProfile(
      id: 'srv-1',
      name: 'Workstation',
      baseUrl: 'https://server.example:4096',
    );

    Future<(OrchestrationController, _Gateway)> boot({
      OrchestrationCapabilities capabilities =
          OrchestrationCapabilities.fixture,
    }) async {
      prefs = await SharedPreferences.getInstance();
      final gateway = _Gateway(capabilities: capabilities);
      gateway.runList.add(
        OrchestrationRun(
          id: 'oc-loy',
          title: 'Add subtract() to calc.py',
          state: RunState.failed,
          lastError: 'tests failed',
          updatedAt: clock,
        ),
      );
      gateway.workList.add(
        const WorkItem(
          id: 'w-tests',
          title: 'Write tests for calc.py',
          state: WorkState.failed,
          runId: 'oc-loy',
          assignee: 'a-wolf',
          sessionId: 'ses-wolf',
        ),
      );
      gateway.agentList.add(
        OrchestrationAgent(
          id: 'a-wolf',
          name: 'Wolf',
          state: AgentState.waiting,
          sessionId: 'ses-wolf',
          currentWorkId: 'w-tests',
          lastActivity: clock,
        ),
      );
      gateway.gateList.add(
        OrchestrationGate(
          id: 'run:oc-loy',
          kind: GateKind.runFailed,
          rawKind: 'failed',
          title: 'Add subtract() to calc.py',
          prompt: 'tests failed',
          runId: 'oc-loy',
          createdAt: clock,
        ),
      );
      final controller = OrchestrationController(
        profile: profile,
        config: const OrchestrationConfig(
          provider: OrchestrationProvider.fixture,
          url: 'fixture://scripted',
          city: 'bright-lights',
        ),
        store: OrchestrationStore(prefs),
        gatewayFactory: (_, _) => gateway,
        now: () => clock,
      );
      addTearDown(controller.dispose);
      await controller.start();
      return (controller, gateway);
    }

    Future<void> openSheet(
      WidgetTester tester,
      OrchestrationController team,
    ) async {
      _tall(tester);
      await tester.pumpWidget(
        _app(
          Scaffold(
            body: Builder(
              builder: (context) => Center(
                child: TextButton(
                  key: const ValueKey('open'),
                  onPressed: () => showGateSheet(
                    context,
                    team,
                    'run:oc-loy',
                    now: () => clock,
                  ),
                  child: const Text('open'),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.tap(_key('open'));
      await tester.pumpAndSettle();
      await tester.tap(_key('kit-actions-more'));
      await tester.pumpAndSettle();
    }

    testWidgets('Report carries the failed work\'s session output', (
      tester,
    ) async {
      final (team, gateway) = await boot();
      await openSheet(tester, team);
      expect(find.text(_en.failedJobReport), findsOneWidget);
      await tester.tap(_key('team-gate-run-report'));
      await tester.pump();
      // The report waits for the agent's output to arrive.
      expect(gateway.outputRequests, ['ses-wolf']);
      gateway.outputs['ses-wolf']!.add(
        const AgentOutputText('FAILED test_subtract\nAssertionError: 1 != -1'),
      );
      await tester.pumpAndSettle();

      expect(_key('team-gate-sheet'), findsNothing);
      expect(find.byType(AppDiagnosticsScreen), findsOneWidget);
      expect(
        find.text(_en.teamUiGateRunStoppedTitle('Add subtract() to calc.py')),
        findsOneWidget,
      );
      expect(await _panelLines(tester), [
        'FAILED test_subtract',
        'AssertionError: 1 != -1',
      ]);
      await team.stop();
    });

    testWidgets('without agent output, Report still opens and says no log '
        'was kept', (tester) async {
      final (team, gateway) = await boot(
        capabilities: const OrchestrationCapabilities(
          runs: true,
          workGraph: true,
          agents: true,
          gatesInteractions: true,
          controlCancelRun: true,
        ),
      );
      _tall(tester);
      await tester.pumpWidget(
        _app(
          Scaffold(
            body: Builder(
              builder: (context) => Center(
                child: TextButton(
                  key: const ValueKey('open'),
                  onPressed: () => showGateSheet(
                    context,
                    team,
                    'run:oc-loy',
                    now: () => clock,
                  ),
                  child: const Text('open'),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.tap(_key('open'));
      await tester.pumpAndSettle();
      // With fewer actions, Report shows beside the agent's page.
      expect(_key('team-gate-run-report'), findsOneWidget);
      await tester.tap(_key('team-gate-run-report'));
      await tester.pumpAndSettle();
      expect(gateway.outputRequests, isEmpty);
      expect(find.byType(AppDiagnosticsScreen), findsOneWidget);
      expect(find.text(_en.reportProblemJobNoLogLead), findsOneWidget);
      await team.stop();
    });
  });

  testWidgets('a failed AI Team start offers Report with the script output', (
    tester,
  ) async {
    _tall(tester);
    debugPlatformCapabilities = const PlatformCapabilities.android();
    final team = _FailingTeam();
    debugBuiltinTeam = team;
    final job = BuiltinTeamJob(now: () => DateTime.utc(2026, 9, 27));
    BuiltinTeamJob.debugShared = job;
    addTearDown(() {
      debugBuiltinTeam = null;
      BuiltinTeamJob.debugShared = null;
      debugPlatformCapabilities = null;
    });
    final profile = ServerProfile(
      id: 'phone',
      name: 'This phone',
      baseUrl: BuiltinLinux.serverUrl,
      username: BuiltinLinux.serverUsername,
      password: 'secret',
    );
    final connection = ConnectionController(
      _Store(prefs: await SharedPreferences.getInstance(), saved: [profile]),
    )..directory = '/root/projects/my-app';
    addTearDown(connection.dispose);
    await job.run(
      stages: const [BuiltinTeamStage.starting, BuiltinTeamStage.waiting],
      work: (onStage) async {
        onStage(BuiltinTeamStage.starting);
        throw const BuiltinTeamException(
          BuiltinTeamStage.starting,
          'gc start: port 8372 in use\nsupervisor exited 1',
        );
      },
    );
    await tester.pumpWidget(
      _app(
        Scaffold(
          body: SingleChildScrollView(
            child: BuiltinTeamSection(connection: connection, profile: profile),
          ),
        ),
      ),
    );
    await tester.pump();
    await tester.pump();
    expect(_key('builtin-team-error-notice'), findsOneWidget);
    await tester.ensureVisible(_key('builtin-team-report'));
    await tester.tap(_key('builtin-team-report'));
    await tester.pumpAndSettle();
    expect(find.byType(AppDiagnosticsScreen), findsOneWidget);
    expect((await _panelLines(tester)).last, 'supervisor exited 1');
  });

  group('a failed dev service', () {
    Future<ServicesConnection> connect(ServiceRepository repository) async {
      final prefs = await SharedPreferences.getInstance();
      final store = ProfileStore(prefs: prefs);
      await store.upsert(
        ServerProfile(
          id: 'laptop',
          name: 'Laptop',
          baseUrl: 'http://192.168.1.20:4097',
        ),
      );
      await store.setActiveId('laptop');
      return ServicesConnection(store, repository)
        ..directory = sampleService.directory
        ..status = StreamStatus.connected;
    }

    testWidgets('offers Report in its log, which opens with this run\'s log', (
      tester,
    ) async {
      _tall(tester);
      final gateway = ServiceRepository();
      final connection = await connect(gateway);
      addTearDown(connection.dispose);
      await DevelopmentServiceStore(
        preferences: connection.store.prefs,
        profileID: 'laptop',
        canWrite: () => true,
      ).save(sampleService);
      await tester.pumpWidget(
        _app(DevelopmentServicesScreen(controller: connection)),
      );
      await tester.pumpAndSettle();
      await tester.tap(
        find.byKey(const ValueKey('development-service-start-vite')),
      );
      await tester.pumpAndSettle();
      KitUndo.commitPending();
      await tester.pumpAndSettle();

      // A running service offers no Report.
      await tester.tap(_key('development-service-vite'));
      await tester.pumpAndSettle();
      expect(_key('development-service-report-vite'), findsNothing);
      Navigator.of(tester.element(find.byType(KitLogPanel))).pop();
      await tester.pumpAndSettle();

      // It exits with an error.
      final shell = gateway.shells['sh_1']!;
      gateway.shells['sh_1'] = ManagedShell(
        id: shell.id,
        command: shell.command,
        directory: shell.directory,
        ownerToken: shell.ownerToken,
        startedAt: shell.startedAt,
        status: ManagedShellStatus.exited,
        exitCode: 1,
      );
      gateway.output = 'Error: listen EADDRINUSE :::5173\n';
      // The page polls every five seconds while it is open.
      await tester.pump(const Duration(seconds: 6));
      await tester.pumpAndSettle();
      expect(
        find.textContaining(_en.servicesExit(1), findRichText: true),
        findsOneWidget,
      );

      await tester.tap(_key('development-service-vite'));
      await tester.pumpAndSettle();
      expect(_key('development-service-report-vite'), findsOneWidget);
      await tester.tap(_key('development-service-report-vite'));
      await tester.pumpAndSettle();
      expect(_key('development-services-logs'), findsNothing);
      expect(find.byType(AppDiagnosticsScreen), findsOneWidget);
      expect(find.text('Shopfront preview'), findsOneWidget);
      expect(await _panelLines(tester), ['Error: listen EADDRINUSE :::5173']);
      KitUndo.commitPending();
      await tester.pumpWidget(const SizedBox());
      await tester.pumpAndSettle();
    });
  });
}
