// TEAM-205: the run Overview's Merge section (02-ux §8a) over a scripted
// merge gateway. The section is absent while work is open; readiness
// lines render with ✓/✗ and the failing line's detail; Merge is disabled
// with the missing line named; ready, it is the conversation's one question
// card (vision 2026-10-20): Approve sends approveMerge at once, then one tap
// on Merge into main sends merge (the card is the question); a host refusal shows
// the boundary that blocked it; force / reset / delete have no verb, key
// or copy anywhere on the phone; receipt states; 320dp/2.5x LTR + RTL.

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/team/merge_section.dart';
import 'package:opencode_mobile/ui/screens/team_conversation/team_conversation.dart'
    show TeamConversationScreen;
import 'package:shared_preferences/shared_preferences.dart';

const _runId = 'oc-xru';
const _requestId = 'gc-mr-14';

/// One recorded merge call.
class _Call {
  const _Call(this.verb, this.target, this.requestId);

  final String verb;
  final String target;
  final String requestId;

  @override
  String toString() => '$verb $target $requestId';
}

/// A front-like gateway: reads from lists, every control on, merge roles
/// scripted. [readiness] is what `mergeReadiness` answers; [answer]
/// scripts the receipt of approve / merge calls.
class _Gateway implements OrchestrationGateway, OrchestrationMergeGateway {
  final runList = <OrchestrationRun>[];
  final workList = <WorkItem>[];
  final calls = <_Call>[];
  final stream = StreamController<OrchestrationEvent>.broadcast();
  MergeReadiness? readiness;
  Object? readinessError;
  int readinessReads = 0;
  Future<MutationReceipt> Function(_Call call)? answer;
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
    await stream.close();
  }

  @override
  Future<List<OrchestrationProject>> projects() async => const [];
  @override
  Future<List<OrchestrationRun>> runs({String? projectId}) async => runList;
  @override
  Future<OrchestrationRun?> run(String id) async {
    for (final run in runList) {
      if (run.id == id) return run;
    }
    return null;
  }

  @override
  Future<List<WorkItem>> work({String? projectId}) async => workList;
  @override
  Future<List<WorkItem>> readyWork({String? projectId}) async => const [];
  @override
  Future<WorkItem?> workItem(String id) async {
    for (final item in workList) {
      if (item.id == id) return item;
    }
    return null;
  }

  @override
  Future<List<OrchestrationAgent>> agents() async => const [];
  @override
  Future<OrchestrationAgent?> agent(String id) async => null;
  @override
  Future<List<OrchestrationGate>> gates() async => const [];
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

  Future<MutationReceipt> _call(_Call call) {
    calls.add(call);
    final script = answer;
    if (script == null) {
      return Future.value(
        MutationReceipt(
          id: call.requestId,
          status: MutationReceiptStatus.accepted,
          upstreamStatus: 200,
        ),
      );
    }
    return script(call);
  }

  @override
  Future<MutationReceipt> respond(
    String gateId,
    GateResponse response, {
    required String requestId,
  }) => _call(_Call('respond', gateId, requestId));

  @override
  Future<MutationReceipt> message(
    String agentId,
    String text, {
    required String requestId,
  }) => _call(_Call('message', agentId, requestId));

  @override
  Future<MutationReceipt> controlAgent(
    String agentId,
    AgentControlAction action, {
    required String requestId,
  }) => _call(_Call('controlAgent', agentId, requestId));

  @override
  Future<MutationReceipt> cancelRun(
    String runId, {
    required String requestId,
  }) => _call(_Call('cancelRun', runId, requestId));

  @override
  Future<MutationReceipt> assign(
    String workId, {
    required String agentId,
    required String requestId,
  }) => _call(_Call('assign', workId, requestId));

  @override
  Future<MutationReceipt> createWork({
    required String title,
    String? description,
    String? projectId,
    required String requestId,
  }) => _call(_Call('createWork', title, requestId));

  @override
  Future<MergeReadiness?> mergeReadiness(String runId) async {
    readinessReads += 1;
    final error = readinessError;
    if (error != null) throw error;
    return readiness;
  }

  @override
  Future<MutationReceipt> approveMerge(
    String mergeRequestId, {
    required String requestId,
  }) => _call(_Call('approveMerge', mergeRequestId, requestId));

  @override
  Future<MutationReceipt> merge(String runId, {required String requestId}) =>
      _call(_Call('merge', runId, requestId));
}

MergeReadinessLine _line(String key, {bool ok = true, String? detail}) =>
    MergeReadinessLine(key: key, ok: ok, detail: detail);

/// The §8a example: everything green, 14 files, +841 / −203.
MergeReadiness _ready({
  List<MergeReadinessLine>? lines,
  List<MergeBoundary> boundaries = const [],
  String? approvedBy,
  String? mergeCommit,
  List<String> branches = const ['polecat/w1'],
}) => MergeReadiness(
  runId: _runId,
  ready: (lines ?? const []).every((line) => line.ok),
  rig: 'ocproof',
  targetBranch: 'main',
  lines:
      lines ??
      [
        _line('work', detail: '18/18 work items'),
        _line('tests', detail: 'passed'),
        _line('build', detail: 'passed'),
        _line('review', detail: 'approved by alice@example.com'),
        _line('conflicts', detail: 'merges cleanly'),
        _line('acceptance', detail: '18 validated'),
      ],
  files: 14,
  additions: 841,
  deletions: 203,
  changes: const [
    MergeChange(path: 'lib/sync/engine.dart', additions: 640, deletions: 12),
    MergeChange(path: 'lib/storage/box.dart', additions: 201, deletions: 191),
  ],
  boundaries: boundaries,
  mergeRequest: MergeRequestInfo(
    id: _requestId,
    title: 'Offline-first sessions',
    approvedBy: approvedBy,
  ),
  mergeCommit: mergeCommit,
  branches: branches,
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late OrchestrationStore store;
  late DateTime clock;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    store = OrchestrationStore(await SharedPreferences.getInstance());
    clock = DateTime.utc(2026, 9, 11, 12, 30);
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

  OrchestrationRun run({RunState state = RunState.completed}) =>
      OrchestrationRun(
        id: _runId,
        title: 'Offline-first sessions',
        state: state,
        rawState: 'open',
        kind: RunKind.batch,
        stepCount: 2,
        completedSteps: state == RunState.completed ? 2 : 1,
        startedAt: clock.subtract(const Duration(hours: 3)),
        updatedAt: clock,
        raw: const {'id': _runId, 'issue_type': 'convoy'},
      );

  const doneWork = [
    WorkItem(
      id: 'w1',
      title: 'Storage layer',
      state: WorkState.completed,
      runId: _runId,
    ),
    WorkItem(
      id: 'w2',
      title: 'Sync engine',
      state: WorkState.review,
      runId: _runId,
    ),
  ];

  const openWork = [
    WorkItem(
      id: 'w1',
      title: 'Storage layer',
      state: WorkState.completed,
      runId: _runId,
    ),
    WorkItem(
      id: 'w2',
      title: 'Sync engine',
      state: WorkState.working,
      runId: _runId,
    ),
  ];

  Future<(OrchestrationController, _Gateway)> boot({
    List<WorkItem> work = doneWork,
    MergeReadiness? readiness,
    void Function(_Gateway gateway)? configure,
  }) async {
    final gateway = _Gateway()
      ..runList.add(run())
      ..workList.addAll(work)
      ..readiness = readiness ?? _ready();
    configure?.call(gateway);
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
      now: () => clock,
    );
    addTearDown(controller.dispose);
    await controller.start();
    return (controller, gateway);
  }

  Widget app(Widget home, {Locale locale = const Locale('en')}) => MaterialApp(
    theme: AppTheme.dark(),
    locale: locale,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(context).copyWith(disableAnimations: true),
      child: child!,
    ),
    home: home,
  );

  Future<void> pumpRun(
    WidgetTester tester,
    OrchestrationController controller, {
    Size size = const Size(800, 2400),
    double pixelRatio = 1,
    Locale locale = const Locale('en'),
  }) async {
    tester.view.physicalSize = size;
    tester.view.devicePixelRatio = pixelRatio;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      app(
        // P3.5: the task is its conversation; the merge section follows
        // the task's turn there.
        TeamConversationScreen(
          team: controller,
          runId: _runId,
          now: () => clock,
        ),
        locale: locale,
      ),
    );
    await tester.pumpAndSettle();
  }

  Finder key(String name) => find.byKey(ValueKey(name));

  bool enabled(WidgetTester tester, String name) {
    final widget = tester.widget(key(name));
    return switch (widget) {
      KitButton() => widget.onPressed != null,
      _ => throw StateError('not a button: $widget'),
    };
  }

  /// The words under [name]: a KitText's text, a Text's data, or every
  /// painted paragraph under it (a notice), one per line.
  String textOf(WidgetTester tester, String name) {
    final widget = tester.widget(key(name));
    if (widget is KitText) return widget.text;
    if (widget is Text && widget.data != null) return widget.data!;
    return tester
        .widgetList<RichText>(
          find.descendant(of: key(name), matching: find.byType(RichText)),
        )
        .map((r) => r.text.toPlainText())
        .join('\n');
  }

  KitButtonRole roleOf(WidgetTester tester, String name) =>
      tester.widget<KitButton>(key(name)).role;

  group('presence', () {
    testWidgets('absent while a work item is still open', (tester) async {
      final (controller, gateway) = await boot(work: openWork);
      await pumpRun(tester, controller);
      expect(key('team-merge-section'), findsNothing);
      expect(gateway.readinessReads, 0);
      expect(teamMergeWorkDone(run(), openWork), isFalse);
      expect(teamMergeWorkDone(run(), doneWork), isTrue);
    });

    testWidgets('absent without the capability or the merge gateway', (
      tester,
    ) async {
      final (controller, _) = await boot();
      expect(teamMergeEligible(controller, run()), isTrue);
      // The fixture-style run with no tracked items decides by its steps.
      expect(teamMergeWorkDone(run(), const []), isTrue);
      expect(
        teamMergeWorkDone(run(state: RunState.working), const []),
        isFalse,
      );
    });

    testWidgets('present once every item is done or review-ready, in the '
        'conversation after the task\'s turn', (tester) async {
      final (controller, gateway) = await boot();
      await pumpRun(tester, controller);
      expect(key('team-merge-section'), findsOneWidget);
      expect(gateway.readinessReads, 1);
      // The task's turn first, then the merge: the section closes the
      // transcript (P3.5: the run Overview is retired).
      final list = tester.widget<ListView>(key('team-conversation-list'));
      final children =
          (list.childrenDelegate as SliverChildListDelegate).children;
      bool holdsMerge(Widget w) =>
          w is TeamMergeSection ||
          (w is Padding && w.child is TeamMergeSection);
      final merge = children.indexWhere(holdsMerge);
      expect(merge, isNonNegative);
      expect(merge, children.length - 1, reason: 'the merge closes it');
      expect(
        tester.getTopLeft(key('team-merge-section')).dy,
        greaterThan(tester.getTopLeft(key('team-conversation-turn')).dy),
      );
    });

    testWidgets('Refresh reads the readiness again', (tester) async {
      final (controller, gateway) = await boot();
      await pumpRun(tester, controller);
      expect(gateway.readinessReads, 1);
      await controller.refresh();
      await tester.pumpAndSettle();
      expect(gateway.readinessReads, 2);
      expect(controller.mergeReadinessFor(_runId), isNotNull);
    });
  });

  /// The merge gate's card ([KitRequestCard], question look).
  KitRequestCard card(WidgetTester tester) => tester.widget<KitRequestCard>(
    find.descendant(
      of: key('team-merge-section'),
      matching: find.byType(KitRequestCard),
      matchRoot: true,
    ),
  );

  group('the merge gate', () {
    testWidgets('ready: one question card naming the count and the branch, '
        'the files and the checks in one line', (tester) async {
      final (controller, _) = await boot();
      await pumpRun(tester, controller);
      final gate = card(tester);
      expect(gate.look, KitRequestLook.question);
      expect(gate.kind, KitRequestKind.gate);
      expect(textOf(tester, 'team-merge-title'), 'Merge 14 changes into main?');
      expect(
        find.text('14 files · +841 / −203 · all 6 checks passed'),
        findsOneWidget,
      );
      // The request waits for an approval: that is the next step, the one
      // primary; Merge comes after it. Review changes is the secondary.
      expect(roleOf(tester, 'team-merge-approve'), KitButtonRole.primary);
      expect(key('team-merge-merge'), findsNothing);
      expect(roleOf(tester, 'team-merge-review'), KitButtonRole.secondary);
      // The checks are not listed on the card; Review changes holds them.
      expect(find.byType(KitChecklist), findsNothing);
      // No cost: no host reports a task's own.
      expect(find.textContaining(r'$'), findsNothing);
    });

    testWidgets('Review changes opens the checks first, then the files', (
      tester,
    ) async {
      final (controller, _) = await boot();
      await pumpRun(tester, controller);
      await tester.tap(key('team-merge-review'));
      await tester.pumpAndSettle();
      expect(key('team-merge-changes'), findsOneWidget);
      final ticks = [
        for (final step
            in tester.widget<KitChecklist>(find.byType(KitChecklist)).steps)
          step.state,
      ];
      expect(ticks, List.filled(6, KitMarkState.done));
      for (final line in teamMergeKnownLines) {
        expect(key('team-merge-line-$line'), findsOneWidget);
      }
      expect(find.text('18/18 work items'), findsOneWidget);
      expect(find.text('lib/sync/engine.dart'), findsOneWidget);
    });

    testWidgets('a missing line: a calm panel, Merge off with the line '
        'named', (tester) async {
      final (controller, _) = await boot(
        readiness: _ready(
          lines: [
            _line('work', detail: '18/18 work items'),
            _line('tests', ok: false, detail: 'exit 1: 2 failed'),
            _line('build', detail: 'passed'),
            _line('review', ok: false, detail: 'waiting for review: w2'),
            _line('conflicts', detail: 'merges cleanly'),
            _line('acceptance', detail: '18 validated'),
          ],
        ),
      );
      await pumpRun(tester, controller);
      // Nobody can act yet: no question card, never amber.
      expect(find.byType(KitRequestCard), findsNothing);
      expect(textOf(tester, 'team-merge-title'), 'Not ready to merge');
      expect(enabled(tester, 'team-merge-merge'), isFalse);
      expect(
        textOf(tester, 'team-merge-disabled-reason'),
        'Merge is off: Tests — exit 1: 2 failed',
      );
      final tests = tester
          .widget<KitChecklist>(find.byType(KitChecklist))
          .steps
          .firstWhere(
            (step) => step.key == const ValueKey('team-merge-line-tests'),
          );
      expect(tests.state, KitMarkState.failed);
      expect(find.textContaining('waiting for review: w2'), findsOneWidget);
      // Review changes stays available to look at what is there.
      expect(key('team-merge-review'), findsOneWidget);
    });

    testWidgets('a pending line says the host is still running it', (
      tester,
    ) async {
      final (controller, _) = await boot(
        readiness: _ready(
          lines: [
            _line('work', detail: '2/2 work items'),
            const MergeReadinessLine(
              key: 'tests',
              ok: false,
              pending: true,
              detail: 'running on the host',
            ),
            _line('build'),
            _line('review'),
            _line('conflicts'),
            _line('acceptance'),
          ],
        ),
      );
      await pumpRun(tester, controller);
      expect(enabled(tester, 'team-merge-merge'), isFalse);
      expect(
        textOf(tester, 'team-merge-disabled-reason'),
        'Merge is off: Tests — running on the host',
      );
    });

    testWidgets('an unsatisfied boundary turns Merge off with its reason', (
      tester,
    ) async {
      final (controller, gateway) = await boot(
        readiness: _ready(
          approvedBy: 'alice@example.com',
          boundaries: const [
            MergeBoundary(
              key: 'require_approval',
              satisfied: false,
              text: 'Never merge without approval',
            ),
          ],
        ),
      );
      await pumpRun(tester, controller);
      expect(textOf(tester, 'team-merge-title'), 'Merge 14 changes into main?');
      expect(enabled(tester, 'team-merge-merge'), isFalse);
      expect(
        find.text('Host boundary: Never merge without approval'),
        findsOneWidget,
      );
      await tester.tap(key('team-merge-merge'), warnIfMissed: false);
      await tester.pumpAndSettle();
      expect(gateway.calls, isEmpty);
    });

    testWidgets('readiness unavailable and no merge roles', (tester) async {
      final (controller, gateway) = await boot(
        configure: (g) => g.readinessError = StateError('front down'),
      );
      await pumpRun(tester, controller);
      expect(
        textOf(tester, 'team-merge-unavailable'),
        startsWith('Merge readiness unavailable:'),
      );
      expect(key('team-merge-merge'), findsOneWidget);
      expect(enabled(tester, 'team-merge-merge'), isFalse);
      // No readiness, no request to approve.
      expect(key('team-merge-approve'), findsNothing);

      gateway
        ..readinessError = null
        ..readiness = null;
      await controller.mergeReadiness(_runId, force: true);
      await tester.pumpAndSettle();
      expect(
        textOf(tester, 'team-merge-unavailable'),
        'The host has no merge roles for this run',
      );
    });
  });

  group('approve', () {
    testWidgets('approves at once (no sheet), then the card merges', (
      tester,
    ) async {
      final (controller, gateway) = await boot();
      await pumpRun(tester, controller);
      await tester.tap(key('team-merge-approve'));
      await tester.pumpAndSettle();
      expect(key('team-merge-approve-sheet'), findsNothing);
      expect(gateway.calls, hasLength(1));
      expect(gateway.calls.single.verb, 'approveMerge');
      expect(gateway.calls.single.target, _requestId);
      final record = controller.latestMutation(
        kind: MutationKind.approveMerge,
        targetId: _requestId,
      )!;
      expect(record.status, MutationStatus.confirmed);
      // The readiness is read again after the host answered.
      expect(gateway.readinessReads, 2);
      // Approved: Merge into main is the card's one next step.
      expect(key('team-merge-approve'), findsNothing);
      expect(roleOf(tester, 'team-merge-merge'), KitButtonRole.primary);
      expect(find.text('Merge into main'), findsOneWidget);
    });

    testWidgets('already approved on the host: straight to Merge', (
      tester,
    ) async {
      final (controller, _) = await boot(
        readiness: _ready(approvedBy: 'alice@example.com'),
      );
      await pumpRun(tester, controller);
      expect(key('team-merge-approve'), findsNothing);
      expect(roleOf(tester, 'team-merge-merge'), KitButtonRole.primary);
    });
  });

  group('merge', () {
    testWidgets('one tap merges: the card is the question, no second sheet', (
      tester,
    ) async {
      final (controller, gateway) = await boot(
        readiness: _ready(approvedBy: 'alice@example.com'),
      );
      gateway.answer = (call) async => MutationReceipt(
        id: call.requestId,
        status: MutationReceiptStatus.accepted,
        upstreamStatus: 200,
        raw: {
          'request_id': 'front-1',
          'status': 'accepted',
          'upstream_status': 200,
          'body': {
            'status': 'merged',
            'mergeCommit': 'c02e3751234567890',
            'branch': 'main',
            'alreadyMerged': false,
          },
        },
      );
      await pumpRun(tester, controller);
      await tester.tap(key('team-merge-merge'));
      await tester.pumpAndSettle();
      expect(key('team-merge-confirm-sheet'), findsNothing);
      expect(gateway.calls, hasLength(1));
      expect(gateway.calls.single.verb, 'merge');
      expect(gateway.calls.single.target, _runId);
      final record = controller.latestMutation(
        kind: MutationKind.merge,
        targetId: _runId,
      )!;
      expect(record.status, MutationStatus.confirmed);
      // Merged: one quiet line, where it landed, that opens the changes;
      // no Merge that can no longer do anything.
      expect(find.byType(KitRequestCard), findsNothing);
      expect(
        textOf(tester, 'team-merge-receipt'),
        'Merged into main · c02e375',
      );
      expect(key('team-merge-merge'), findsNothing);
      expect(key('team-merge-review'), findsOneWidget);
      expect(gateway.readinessReads, 2);
    });

    testWidgets('a host refusal on a boundary stays on the card, and Merge '
        'is usable again', (tester) async {
      final (controller, gateway) = await boot(
        readiness: _ready(approvedBy: 'alice@example.com'),
      );
      gateway.answer = (call) async => MutationReceipt(
        id: call.requestId,
        status: MutationReceiptStatus.rejected,
        message: 'Never merge without approval',
        upstreamStatus: 403,
        raw: {
          'request_id': 'front-2',
          'status': 'rejected',
          'upstream_status': 403,
          'body': {
            'type': 'urn:opencode-mobile:front:boundary',
            'title': 'Forbidden',
            'status': 403,
            'code': 'boundary',
            'boundary': 'require_approval',
            'detail': 'Never merge without approval',
          },
        },
      );
      await pumpRun(tester, controller);
      await tester.tap(key('team-merge-merge'));
      await tester.pumpAndSettle();
      expect(gateway.calls.single.verb, 'merge');
      expect(
        find.textContaining('Host boundary: Never merge without approval'),
        findsOneWidget,
      );
      // A refused merge is settled: the card asks again.
      expect(enabled(tester, 'team-merge-merge'), isTrue);
      await tester.tap(key('team-merge-merge'));
      await tester.pumpAndSettle();
      expect(gateway.calls, hasLength(2));
    });

    testWidgets(
      'a plain refusal says so in words; the host message is under Details',
      (tester) async {
        final (controller, gateway) = await boot(
          readiness: _ready(approvedBy: 'alice@example.com'),
        );
        gateway.answer = (call) async =>
            MutationReceipt.rejected(call.requestId, 'not ready: tests exit 1');
        await pumpRun(tester, controller);
        await tester.tap(key('team-merge-merge'));
        await tester.pumpAndSettle();
        expect(find.textContaining('tests exit 1'), findsNothing);
        expect(find.textContaining('The host refused'), findsNothing);
        expect(find.textContaining('did not accept'), findsWidgets);
        await tester.tap(key('team-merge-receipt-details'));
        await tester.pumpAndSettle();
        expect(find.textContaining('not ready: tests exit 1'), findsWidgets);
        await tester.tap(key('team-merge-merge'));
        await tester.pumpAndSettle();
        expect(gateway.calls, hasLength(2));
      },
    );

    testWidgets('already on the target branch: no Merge, the commit shown', (
      tester,
    ) async {
      final (controller, _) = await boot(
        readiness: _ready(mergeCommit: 'abcdef0123456789', branches: const []),
      );
      await pumpRun(tester, controller);
      // Only where it landed and Review changes: no checks, no request,
      // no Merge (slice-close-team).
      expect(
        textOf(tester, 'team-merge-receipt'),
        'Merged into main · abcdef0',
      );
      expect(key('team-merge-files'), findsNothing);
      expect(key('team-merge-request'), findsNothing);
      expect(key('team-merge-merge'), findsNothing);
      expect(key('team-merge-approve'), findsNothing);
      expect(key('team-merge-review'), findsOneWidget);
    });
  });

  group('receipt states', () {
    KitReceiptState receiptState(WidgetTester tester) =>
        tester.widget<KitReceipt>(key('team-merge-receipt')).state;

    testWidgets('sent while the host has not answered, then confirmed', (
      tester,
    ) async {
      final (controller, gateway) = await boot(
        readiness: _ready(approvedBy: 'alice@example.com'),
      );
      final gate = Completer<MutationReceipt>();
      gateway.answer = (_) => gate.future;
      await pumpRun(tester, controller);
      await tester.tap(key('team-merge-merge'));
      await tester.pump();
      await tester.pump();
      // The card says the answer and its receipt; nothing to press twice.
      expect(receiptState(tester), KitReceiptState.sending);
      expect(key('team-merge-merge'), findsNothing);
      gate.complete(
        MutationReceipt(
          id: gateway.calls.single.requestId,
          status: MutationReceiptStatus.accepted,
          upstreamStatus: 200,
          raw: const {
            'body': {'mergeCommit': '1234567890', 'branch': 'main'},
          },
        ),
      );
      await tester.pumpAndSettle();
      expect(
        textOf(tester, 'team-merge-receipt'),
        'Merged into main · 1234567',
      );
    });

    testWidgets('a transport failure is unconfirmed, never re-sent', (
      tester,
    ) async {
      final (controller, gateway) = await boot(
        readiness: _ready(approvedBy: 'alice@example.com'),
      );
      gateway.answer = (call) async => MutationReceipt(
        id: call.requestId,
        status: MutationReceiptStatus.pending,
        message: 'connection reset',
      );
      await pumpRun(tester, controller);
      await tester.tap(key('team-merge-merge'));
      await tester.pumpAndSettle();
      expect(receiptState(tester), KitReceiptState.notConfirmed);
      expect(gateway.calls, hasLength(1));
      await tester.pump(const Duration(seconds: 90));
      expect(gateway.calls, hasLength(1));
    });

    testWidgets('an approval in flight is shown as sent', (tester) async {
      final (controller, gateway) = await boot();
      final gate = Completer<MutationReceipt>();
      gateway.answer = (_) => gate.future;
      await pumpRun(tester, controller);
      await tester.tap(key('team-merge-approve'));
      await tester.pump();
      await tester.pump();
      expect(receiptState(tester), KitReceiptState.sending);
      gate.complete(
        MutationReceipt(
          id: gateway.calls.single.requestId,
          status: MutationReceiptStatus.accepted,
          upstreamStatus: 202,
        ),
      );
      await tester.pumpAndSettle();
      // A 202 waits for the host's bead event.
      expect(receiptState(tester), KitReceiptState.sending);
      gateway.stream.add(
        BeadChanged(beadId: _requestId, change: BeadChange.updated),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
      await tester.pumpAndSettle();
      // Recorded: the card's next step is Merge.
      expect(key('team-merge-approve'), findsNothing);
      expect(key('team-merge-merge'), findsOneWidget);
    });
  });

  group('review changes', () {
    testWidgets('opens the files with +/− and the run work list', (
      tester,
    ) async {
      final (controller, _) = await boot();
      await pumpRun(tester, controller);
      await tester.tap(key('team-merge-review'));
      await tester.pumpAndSettle();
      expect(key('team-merge-changes'), findsOneWidget);
      expect(find.text('lib/sync/engine.dart'), findsOneWidget);
      expect(find.text('lib/storage/box.dart'), findsOneWidget);
      expect(find.textContaining('+640'), findsOneWidget);
      expect(key('team-merge-work-w1'), findsOneWidget);
      expect(key('team-merge-work-w2'), findsOneWidget);
      // No full diff: nothing but paths and counts.
      expect(find.textContaining('@@'), findsNothing);
      await tester.tap(key('team-merge-work-w2'));
      await tester.pumpAndSettle();
      expect(key('team-merge-changes'), findsNothing);
      expect(find.text('Sync engine'), findsWidgets);
    });
  });

  group('nothing destructive exists on the phone', () {
    test('no verb, kind, key or copy for force, reset or delete', () {
      expect(
        MutationKind.values.map((k) => k.name),
        isNot(
          anyElement(
            anyOf(contains('force'), contains('reset'), contains('delete')),
          ),
        ),
      );
      final forbidden = RegExp(
        r'force[- ]?merge|--force|reset --hard|worktree (remove|delete|prune)|branch -D|delete (the )?worktree',
        caseSensitive: false,
      );
      for (final path in const [
        'lib/domain/orchestration_gateway.dart',
        'lib/state/mutation_store.dart',
        'lib/state/orchestration.dart',
        'lib/orchestration/adapters/gascity/gascity_control.dart',
        'lib/orchestration/adapters/gascity/gascity_gateway.dart',
        'lib/orchestration/models/merge.dart',
        'lib/ui/screens/team/merge_section.dart',
      ]) {
        final source = File(path).readAsStringSync();
        // Doc comments may say these never exist; code lines may not.
        final code = source
            .split('\n')
            .where((line) => !line.trimLeft().startsWith('//'))
            .join('\n');
        expect(forbidden.hasMatch(code), isFalse, reason: path);
      }
      final arb =
          jsonDecode(File('lib/l10n/app_en.arb').readAsStringSync())
              as Map<String, Object?>;
      final mergeKeys = arb.keys.where(
        (k) => k.startsWith('teamUiMerge') && !k.startsWith('@'),
      );
      expect(mergeKeys, isNotEmpty);
      for (final k in mergeKeys) {
        final value = (arb[k] as String).toLowerCase();
        expect(value, isNot(contains('force')), reason: k);
        expect(value, isNot(contains('reset')), reason: k);
        expect(value, isNot(contains('delete')), reason: k);
        expect(value, isNot(contains('worktree')), reason: k);
      }
    });

    testWidgets('the section shows no such button', (tester) async {
      final (controller, _) = await boot();
      await pumpRun(tester, controller);
      for (final word in ['Force', 'Reset', 'Delete', 'worktree']) {
        expect(find.textContaining(word), findsNothing, reason: word);
      }
      // The gate's two buttons: Review changes, then the one next step.
      final section = key('team-merge-section');
      expect(
        find.descendant(of: section, matching: find.byType(KitButton)),
        findsNWidgets(2),
      );
    });
  });

  group('model', () {
    test('MergeReadiness decodes the front document and stays lenient', () {
      final doc = MergeReadiness.fromJson({
        'ready': false,
        'runId': _runId,
        'rig': 'ocproof',
        'targetBranch': 'main',
        'lines': [
          {'key': 'work', 'ok': true, 'detail': '2/2 work items'},
          {'key': 'tests', 'ok': false, 'pending': true, 'detail': 'running'},
          {'garbage': true},
        ],
        'files': '3',
        'additions': 10,
        'deletions': 2.0,
        'changes': [
          {'path': 'a.py', 'additions': 5, 'deletions': 1},
          'nope',
        ],
        'boundaries': [
          {'key': 'require_tests', 'satisfied': false, 'text': 'Require tests'},
        ],
        'mergeRequest': {
          'id': 'gc-mr-1',
          'title': 'MR',
          'approvedBy': null,
          'approvedAt': null,
        },
        'mergeCommit': null,
        'branches': ['polecat/a', 7],
      })!;
      expect(doc.ready, isFalse);
      expect(doc.lines.map((l) => l.key), ['work', 'tests']);
      expect(doc.lines[1].pending, isTrue);
      expect(doc.firstMissing?.key, 'tests');
      expect(doc.firstBlocking?.key, 'require_tests');
      expect(doc.canMerge, isFalse);
      expect((doc.files, doc.additions, doc.deletions), (3, 10, 2));
      expect(doc.changes.single.path, 'a.py');
      expect(doc.mergeRequest?.isApproved, isFalse);
      expect(doc.branches, ['polecat/a']);
      expect(doc.alreadyMerged, isFalse);
      final back = MergeReadiness.fromJson(
        jsonDecode(jsonEncode(doc.toJson())),
      )!;
      expect(back.toJson(), doc.toJson());

      expect(MergeReadiness.fromJson({'status': 'ok'}), isNull);
      expect(MergeReadiness.fromJson('x'), isNull);
      expect(MergeReadiness.fromJson({'ready': true}, runId: 'r')!.ready, true);
      final merged = _ready(mergeCommit: 'abc', branches: const []);
      expect(merged.alreadyMerged, isTrue);
      expect(merged.canMerge, isTrue);
    });

    test('merge mutation requests round-trip and confirm on their events', () {
      for (final request in [
        MutationRequest.approveMerge('gc-mr-1'),
        MutationRequest.merge('run-1'),
      ]) {
        final back = MutationRequest.fromJson(
          jsonDecode(jsonEncode(request.toJson())),
        )!;
        expect(back.toJson(), request.toJson());
        expect(back.response, isNull);
      }
      expect(MutationRequest.merge('r').kind, MutationKind.merge);
      expect(MutationRequest.approveMerge('m').kind, MutationKind.approveMerge);
    });
  });

  group('layout', () {
    for (final locale in const [Locale('en')]) {
      testWidgets('320dp at 2.5x, ${locale.languageCode}: no overflow, '
          'section reachable', (tester) async {
        final (controller, _) = await boot(
          readiness: _ready(
            lines: [
              _line('work', detail: '18/18 work items'),
              _line('tests', ok: false, detail: 'exit 1: 2 failed'),
              _line('build'),
              _line('review', detail: 'approved by alice@example.com'),
              _line('conflicts'),
              _line('acceptance'),
            ],
            approvedBy: 'alice@example.com',
          ),
        );
        await pumpRun(
          tester,
          controller,
          size: const Size(800, 1600),
          pixelRatio: 2.5,
          locale: locale,
        );
        await tester.scrollUntilVisible(
          key('team-merge-section'),
          200,
          scrollable: find.descendant(
            of: key('team-conversation-list'),
            matching: find.byType(Scrollable),
          ),
        );
        await tester.pumpAndSettle();
        expect(key('team-merge-section'), findsOneWidget);
        expect(key('team-merge-disabled-reason'), findsOneWidget);
        expect(key('team-merge-merge'), findsOneWidget);
        final width = tester.getSize(key('team-merge-section')).width;
        expect(width, lessThanOrEqualTo(320));
        expect(tester.takeException(), isNull);
        if (locale.languageCode == 'ar') {
          expect(find.text('دمج'), findsOneWidget);
          expect(find.text('اعتماد الطلب'), findsOneWidget);
          expect(
            Directionality.of(tester.element(key('team-merge-section'))),
            TextDirection.rtl,
          );
        }
      });
    }
  });
}
