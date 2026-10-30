// Golden renders of the AI Team sheets migrated to the design kit
// (docs/design/design-standard.md §8, migration step 4): the Gate sheet
// (a confirmation with its one action block), the Work sheet, and the run
// Overview's Merge section, at 412x915, dark and light, with the app's
// real fonts.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/team_sheets_golden_test.dart
// and look at every changed image before committing it.
import 'dart:async';

import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/team/gate_sheet.dart';
import 'package:opencode_mobile/ui/screens/team/merge_section.dart';
import 'package:opencode_mobile/ui/screens/team/work_sheet.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;

final _clock = DateTime.utc(2026, 9, 11, 12, 30);
const _runId = 'oc-xru';

/// A front-like gateway over fixed lists, every control on, merge roles.
class _Gateway implements OrchestrationGateway, OrchestrationMergeGateway {
  final runList = <OrchestrationRun>[];
  final workList = <WorkItem>[];
  final agentList = <OrchestrationAgent>[];
  final gateList = <OrchestrationGate>[];
  MergeReadiness? readiness;
  final _stream = StreamController<OrchestrationEvent>.broadcast();
  bool _closed = false;

  @override
  OrchestrationCapabilities get capabilities =>
      OrchestrationCapabilities.gascityFront;

  @override
  OrchestrationHostIdentity? get host => const OrchestrationHostIdentity(
    provider: 'gascity',
    url: 'http://127.0.0.1:8373',
    hostMode: OrchestrationHostMode.computer,
  );

  @override
  bool get isClosed => _closed;

  @override
  Future<void> close() async {
    _closed = true;
    await _stream.close();
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
  }) => _stream.stream;

  MutationReceipt _ok(String requestId) => MutationReceipt(
    id: requestId,
    status: MutationReceiptStatus.accepted,
    upstreamStatus: 200,
  );

  @override
  Future<MutationReceipt> respond(
    String gateId,
    GateResponse response, {
    required String requestId,
  }) async => _ok(requestId);
  @override
  Future<MutationReceipt> message(
    String agentId,
    String text, {
    required String requestId,
  }) async => _ok(requestId);
  @override
  Future<MutationReceipt> controlAgent(
    String agentId,
    AgentControlAction action, {
    required String requestId,
  }) async => _ok(requestId);
  @override
  Future<MutationReceipt> cancelRun(
    String runId, {
    required String requestId,
  }) async => _ok(requestId);
  @override
  Future<MutationReceipt> assign(
    String workId, {
    required String agentId,
    required String requestId,
  }) async => _ok(requestId);
  @override
  Future<MutationReceipt> createWork({
    required String title,
    String? description,
    String? projectId,
    required String requestId,
  }) async => _ok(requestId);
  @override
  Future<MergeReadiness?> mergeReadiness(String runId) async => readiness;
  @override
  Future<MutationReceipt> approveMerge(
    String mergeRequestId, {
    required String requestId,
  }) async => _ok(requestId);
  @override
  Future<MutationReceipt> merge(
    String runId, {
    required String requestId,
  }) async => _ok(requestId);
}

final _run = OrchestrationRun(
  id: _runId,
  title: 'Offline-first sessions',
  state: RunState.completed,
  rawState: 'open',
  kind: RunKind.batch,
  stepCount: 2,
  completedSteps: 2,
  startedAt: _clock.subtract(const Duration(hours: 3)),
  updatedAt: _clock,
  raw: const {'id': _runId, 'issue_type': 'convoy'},
);

final _work = [
  WorkItem(
    id: 'oc-w1',
    title: 'Storage layer',
    state: WorkState.completed,
    runId: _runId,
    assignee: 'a-wolf',
    createdAt: _clock.subtract(const Duration(hours: 3)),
    updatedAt: _clock.subtract(const Duration(hours: 1)),
  ),
  WorkItem(
    id: 'oc-w2',
    title: 'Sync engine',
    state: WorkState.review,
    runId: _runId,
    assignee: 'a-wolf',
    dependsOn: const ['oc-w1'],
    createdAt: _clock.subtract(const Duration(hours: 3)),
    updatedAt: _clock.subtract(const Duration(minutes: 20)),
    raw: const {
      'description':
          'Queue writes while offline and replay them in order once the '
          'server answers again.',
      'metadata': {
        'branch': 'polecat/sync-engine',
        'gc.work_dir': '/home/dev/ocproof/.gc/worktrees/polecat-1',
      },
    },
  ),
];

final _agent = OrchestrationAgent(
  id: 'a-wolf',
  name: 'ocproof/polecat-1',
  state: AgentState.working,
  sessionId: 'bl-5qc',
  currentWorkId: 'oc-w2',
);

final _gate = OrchestrationGate(
  id: 'req-safe',
  kind: GateKind.confirmation,
  rawKind: 'confirmation',
  title: 'Continue with the plan?',
  prompt: 'Three steps remain: storage, sync, and the settings screen.',
  agentId: 'a-wolf',
  workId: 'oc-w2',
  runId: _runId,
  createdAt: _clock.subtract(const Duration(minutes: 3)),
  raw: const {'request_id': 'req-safe', 'session_id': 'bl-5qc'},
);

MergeReadiness _readiness() => MergeReadiness(
  runId: _runId,
  ready: true,
  rig: 'ocproof',
  targetBranch: 'main',
  lines: const [
    MergeReadinessLine(key: 'work', ok: true, detail: '2/2 work items'),
    MergeReadinessLine(key: 'tests', ok: true, detail: 'passed'),
    MergeReadinessLine(key: 'build', ok: true, detail: 'passed'),
    MergeReadinessLine(key: 'review', ok: true, detail: 'approved'),
    MergeReadinessLine(key: 'conflicts', ok: true, detail: 'merges cleanly'),
  ],
  files: 14,
  additions: 841,
  deletions: 203,
  changes: const [],
  boundaries: const [],
  mergeRequest: const MergeRequestInfo(
    id: 'gc-mr-14',
    title: 'Offline-first sessions',
  ),
  branches: const ['polecat/sync-engine'],
);

Future<OrchestrationController> _controller(OrchestrationStore store) async {
  final gateway = _Gateway()
    ..runList.add(_run)
    ..workList.addAll(_work)
    ..agentList.add(_agent)
    ..gateList.add(_gate)
    ..readiness = _readiness();
  final config = OrchestrationConfig(
    provider: OrchestrationProvider.gascity,
    url: 'http://127.0.0.1:8373',
    city: 'bright-lights',
    front: true,
    enabledAt: DateTime.utc(2026, 9, 10),
  );
  final controller = OrchestrationController(
    profile: ServerProfile(
      id: 'srv-1',
      name: 'Workstation',
      baseUrl: 'https://server.example:4096',
      orchestration: config,
    ),
    config: config,
    store: store,
    gatewayFactory: (_, _) => gateway,
    probe: (_) async => ProbeFound(
      host: gateway.host!,
      front: true,
      identityAllowed: true,
      capabilities: OrchestrationCapabilities.gascityFront,
    ),
    now: () => _clock,
  );
  await controller.start();
  return controller;
}

Future<void> _golden(
  WidgetTester tester,
  String name, {
  required bool light,
  required OrchestrationController controller,
  Widget? body,
  Future<void> Function(BuildContext context)? open,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final boundary = GlobalKey();
  final host = GlobalKey();
  try {
    await tester.pumpWidget(
      RepaintBoundary(
        key: boundary,
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: captureTheme(light: light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(disableAnimations: true),
            child: child!,
          ),
          home: Scaffold(
            key: host,
            body: SafeArea(child: body ?? const SizedBox.expand()),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    if (open != null) {
      unawaited(open(host.currentContext!));
      await tester.pumpAndSettle();
    }
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('${name}_${light ? 'light' : 'dark'}.png'),
    );
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    await controller.stop();
    controller.dispose();
    await tester.pump();
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  late OrchestrationStore store;
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    store = OrchestrationStore(await SharedPreferences.getInstance());
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

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';

    testWidgets('team · gate sheet (confirmation) · $mode', (tester) async {
      final controller = await _controller(store);
      await _golden(
        tester,
        'team_gate_sheet',
        light: light,
        controller: controller,
        open: (context) =>
            showGateSheet(context, controller, 'req-safe', now: () => _clock),
      );
    });

    testWidgets('team · work sheet · $mode', (tester) async {
      final controller = await _controller(store);
      await _golden(
        tester,
        'team_work_sheet',
        light: light,
        controller: controller,
        open: (context) =>
            showWorkSheet(context, controller, 'oc-w2', now: () => _clock),
      );
    });

    testWidgets('team · merge section · $mode', (tester) async {
      final controller = await _controller(store);
      // The gate card counts its wait on the phone's clock: the scene's.
      await withClock(
        Clock.fixed(_clock),
        () => _golden(
          tester,
          'team_merge',
          light: light,
          controller: controller,
          body: ListView(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            children: [
              TeamMergeSection(
                controller: controller,
                run: _run,
                now: () => _clock,
              ),
            ],
          ),
        ),
      );
    });
  }
}
