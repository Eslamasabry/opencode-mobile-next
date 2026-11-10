// TEAM-113: usage surfaces. The shared formatter returns null when the
// host reported neither cost nor tokens, writes tokens compactly and puts
// "est." after every cost; Task details (slice-P3.5: the retired run
// Overview's Details moved onto that sheet) says a task's cost is not
// reported (slice-P5.2: no host figure is one task's), never the team's
// day; the Gas City read capabilities include `usage`.

import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/orchestration/adapters/fixture/fixture_gateway.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/team/agent_screen.dart';
import 'package:opencode_mobile/ui/screens/team/task_details_sheet.dart';
import 'package:opencode_mobile/ui/widgets/team_vocabulary.dart';
import 'package:shared_preferences/shared_preferences.dart';

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

/// The fixture with the usage read, the capabilities and the run / work /
/// agent lists replaceable per test.
class _Gateway implements OrchestrationGateway {
  _Gateway(this.inner);

  final FixtureOrchestrationGateway inner;
  final stream = StreamController<OrchestrationEvent>.broadcast();
  OrchestrationCapabilities? capabilitiesOverride;
  Future<OrchestrationUsage?> Function()? usageOverride;
  List<OrchestrationRun>? runsOverride;
  List<WorkItem>? workOverride;
  List<OrchestrationAgent>? agentsOverride;

  @override
  OrchestrationCapabilities get capabilities =>
      capabilitiesOverride ?? inner.capabilities;
  @override
  OrchestrationHostIdentity? get host => inner.host;
  @override
  bool get isClosed => inner.isClosed;
  @override
  Future<void> close() async {
    await stream.close();
    await inner.close();
  }

  @override
  Future<List<OrchestrationProject>> projects() => inner.projects();

  @override
  Future<List<OrchestrationRun>> runs({String? projectId}) async =>
      runsOverride ?? await inner.runs(projectId: projectId);

  @override
  Future<OrchestrationRun?> run(String id) => inner.run(id);

  @override
  Future<List<WorkItem>> work({String? projectId}) async =>
      workOverride ?? await inner.work(projectId: projectId);

  @override
  Future<List<WorkItem>> readyWork({String? projectId}) =>
      inner.readyWork(projectId: projectId);

  @override
  Future<WorkItem?> workItem(String id) => inner.workItem(id);

  @override
  Future<List<OrchestrationAgent>> agents() async =>
      agentsOverride ?? await inner.agents();

  @override
  Future<OrchestrationAgent?> agent(String id) => inner.agent(id);

  @override
  Future<List<OrchestrationGate>> gates() => inner.gates();

  @override
  Future<OrchestrationUsage?> usage() => usageOverride?.call() ?? inner.usage();

  @override
  Future<List<ActivityEvent>> activity({int? afterSeq, int limit = 100}) =>
      inner.activity(afterSeq: afterSeq, limit: limit);

  @override
  Stream<OrchestrationEvent> events({
    EventCursor resumeFrom = EventCursor.none,
  }) => stream.stream;

  @override
  Future<MutationReceipt> respond(
    String gateId,
    GateResponse response, {
    required String requestId,
  }) => inner.respond(gateId, response, requestId: requestId);

  @override
  Future<MutationReceipt> message(
    String agentId,
    String text, {
    required String requestId,
  }) => inner.message(agentId, text, requestId: requestId);

  @override
  Future<MutationReceipt> controlAgent(
    String agentId,
    AgentControlAction action, {
    required String requestId,
  }) => inner.controlAgent(agentId, action, requestId: requestId);

  @override
  Future<MutationReceipt> cancelRun(
    String runId, {
    required String requestId,
  }) => inner.cancelRun(runId, requestId: requestId);

  @override
  Future<MutationReceipt> assign(
    String workId, {
    required String agentId,
    required String requestId,
  }) => inner.assign(workId, agentId: agentId, requestId: requestId);

  @override
  Future<MutationReceipt> createWork({
    required String title,
    String? description,
    String? projectId,
    required String requestId,
  }) => inner.createWork(
    title: title,
    description: description,
    projectId: projectId,
    requestId: requestId,
  );
}

/// A day's usage as the PC's Gas City reports it once agents have run:
/// `source: local_estimate`, tokens and an estimated cost.
const _richUsage = OrchestrationUsage(
  inputTokens: 9400,
  outputTokens: 3000,
  costUsd: 0.42,
  raw: {'source': 'local_estimate', 'available': true},
);

/// `/status` counts only: not [OrchestrationUsage.isEmpty], but nothing to
/// price.
const _countsOnly = OrchestrationUsage(
  activeAgents: 2,
  workOpen: 3,
  raw: {'source': 'unavailable'},
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final en = lookupAppLocalizations(const Locale('en'));
  final ar = lookupAppLocalizations(const Locale('ar'));
  final arabicIndicDigits = RegExp('[٠-٩]');

  group('teamUsageLabel', () {
    test('is null when the host reported neither cost nor tokens', () {
      expect(teamUsageLabel(en, null), isNull);
      expect(teamUsageLabel(en, const OrchestrationUsage()), isNull);
      expect(teamUsageLabel(en, _countsOnly), isNull);
      expect(teamUsageCostLabel(en, _countsOnly), isNull);
      expect(teamUsageTokensLabel(en, _countsOnly), isNull);
      expect(teamUsageTokens(_countsOnly), isNull);
    });

    test('writes tokens compactly', () {
      expect(teamCompactCount(0), '0');
      expect(teamCompactCount(980), '980');
      expect(teamCompactCount(1000), '1.0k');
      expect(teamCompactCount(12400), '12.4k');
      expect(teamCompactCount(1200000), '1.2M');
      expect(teamUsageTokens(_richUsage), 12400);
      expect(teamUsageTokensLabel(en, _richUsage), '12.4k tokens');
      // Either side alone still counts.
      expect(
        teamUsageTokensLabel(en, const OrchestrationUsage(outputTokens: 12)),
        '12 tokens',
      );
    });

    test('every cost carries "est." (05-beads TEAM-113)', () {
      expect(teamUsageCostLabel(en, _richUsage), r'$0.42 est.');
      expect(
        teamUsageCostLabel(en, const OrchestrationUsage(costUsd: 0)),
        r'$0.00 est.',
      );
      expect(
        teamUsageCostLabel(en, const OrchestrationUsage(costUsd: 0.004)),
        r'$0.004 est.',
      );
      expect(
        teamUsageCostLabel(en, const OrchestrationUsage(costUsd: 12.5)),
        r'$12.50 est.',
      );
      // A figure without an estimate source still says est.: the plugin
      // never shows a cost as if it were a bill.
      expect(
        teamUsageCostLabel(
          en,
          const OrchestrationUsage(costUsd: 1, raw: {'source': 'billing'}),
        ),
        r'$1.00 est.',
      );
      for (final l10n in [en, ar]) {
        expect(
          teamUsageLabel(l10n, _richUsage),
          contains(l10n.teamUiUsageCostEstimated(r'$0.42')),
        );
      }
    });

    test('joins cost and tokens, either alone', () {
      expect(teamUsageLabel(en, _richUsage), r'$0.42 est. · 12.4k tokens');
      expect(
        teamUsageLabel(en, const OrchestrationUsage(costUsd: 0.42)),
        r'$0.42 est.',
      );
      expect(
        teamUsageLabel(en, const OrchestrationUsage(inputTokens: 500)),
        '500 tokens',
      );
    });

    test('Arabic keeps Latin figures and the suffix after the amount', () {
      final label = teamUsageLabel(ar, _richUsage)!;
      expect(label, contains(r'$0.42'));
      expect(label, contains('12.4k'));
      expect(label, isNot(matches(arabicIndicDigits)));
      expect(
        label,
        '${ar.teamUiUsageCostEstimated(r'$0.42')}'
        '$teamUsageSeparator${ar.teamUiUsageTokens('12.4k')}',
      );
      expect(ar.teamUiUsageCostEstimated(r'$0.42'), startsWith(r'$0.42 '));
    });
  });

  group('capabilities', () {
    test('Gas City read path includes usage; the front keeps it', () {
      expect(OrchestrationCapabilities.gascityRead.usage, isTrue);
      expect(OrchestrationCapabilities.gascityFront.usage, isTrue);
      expect(OrchestrationCapabilities.fixture.usage, isTrue);
      expect(OrchestrationCapabilities.none.usage, isFalse);
    });
  });

  group('surfaces', () {
    late String fixturePath;
    late OrchestrationStore store;
    late DateTime clock;

    setUp(() async {
      fixturePath = _findFixtureRoot().path;
      SharedPreferences.setMockInitialValues({});
      store = OrchestrationStore(await SharedPreferences.getInstance());
      clock = DateTime.utc(2026, 9, 11, 9, 41);
    });

    OrchestrationRun run({Map<String, Object?> extraRaw = const {}}) =>
        OrchestrationRun(
          id: 'oc-xru',
          title: 'Offline-first sessions',
          state: RunState.working,
          rawState: 'open',
          kind: RunKind.batch,
          stepCount: 3,
          completedSteps: 1,
          startedAt: clock.subtract(const Duration(hours: 3)),
          updatedAt: clock,
          raw: {'id': 'oc-xru', 'issue_type': 'convoy', ...extraRaw},
        );

    OrchestrationAgent fox({int? context = 63}) => OrchestrationAgent(
      id: 'fox',
      name: 'fox',
      state: AgentState.working,
      rawState: 'active',
      sessionId: 'bl-5qc',
      pool: 'gastown.polecat',
      provider: 'opencode',
      currentWorkId: 'w2',
      contextPercent: context,
      workDir: '/home/eslam/city/.gc/worktrees/ocproof/polecats/fox',
      branch: 'polecat/oc-cq6',
      sessionStartedAt: clock.subtract(const Duration(hours: 3)),
      raw: const {'id': 'bl-5qc'},
    );

    Future<(OrchestrationController, _Gateway)> boot({
      OrchestrationUsage? usage = _richUsage,
      OrchestrationCapabilities? capabilities,
      OrchestrationRun? runOverride,
      int? context = 63,
      bool fixtureUsage = false,
    }) async {
      final gateway = _Gateway(
        FixtureOrchestrationGateway(fixturePath: fixturePath),
      );
      gateway
        ..capabilitiesOverride = capabilities
        ..usageOverride = fixtureUsage ? null : (() async => usage)
        ..runsOverride = [runOverride ?? run()]
        ..workOverride = const [
          WorkItem(
            id: 'w1',
            title: 'Storage layer',
            state: WorkState.completed,
            runId: 'oc-xru',
          ),
          WorkItem(
            id: 'w2',
            title: 'Sync engine',
            state: WorkState.working,
            runId: 'oc-xru',
          ),
        ]
        ..agentsOverride = [fox(context: context)];
      final config = OrchestrationConfig(
        provider: OrchestrationProvider.fixture,
        url: fixturePath,
        city: 'bright-lights',
        enabledAt: DateTime.utc(2026, 9, 10),
      );
      final controller = OrchestrationController(
        profile: ServerProfile(
          id: 'srv-1',
          name: 'Development PC',
          baseUrl: 'https://server.example:4096',
          orchestration: config,
        ),
        config: config,
        store: store,
        gatewayFactory: (_, _) => gateway,
        now: () => clock,
      );
      addTearDown(controller.dispose);
      await controller.start();
      return (controller, gateway);
    }

    Widget app(Widget home, {Locale locale = const Locale('en')}) =>
        MaterialApp(
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

    Future<void> pump(
      WidgetTester tester,
      Widget home, {
      Locale locale = const Locale('en'),
    }) async {
      tester.view.physicalSize = const Size(800, 2400);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpWidget(app(home, locale: locale));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
    }

    Finder key(String name) => find.byKey(ValueKey(name));

    String text(WidgetTester tester, Finder finder) =>
        _textOf(tester, finder).data!;

    Widget details(OrchestrationController controller) => Scaffold(
      body: SingleChildScrollView(
        child: TeamTaskDetails(
          controller: controller,
          runId: 'oc-xru',
          now: () => clock,
        ),
      ),
    );

    final usageKey = find.byKey(const ValueKey('team-task-details-usage'));

    Future<void> pumpRun(
      WidgetTester tester,
      OrchestrationController controller, {
      Locale locale = const Locale('en'),
    }) => pump(tester, details(controller), locale: locale);

    // slice-P5.2: the host reports the whole team's day and a worker's
    // recent window, never one task's cost (docs/qa/codex-p52-2026-09-27).
    // The task page says so; it never shows the team's day total or a
    // figure from the run's raw payload as this task's cost.
    group('Task details: a task\'s cost is unreported', () {
      testWidgets('says so under the steps, with no figure', (tester) async {
        final (controller, _) = await boot(fixtureUsage: true);
        await pumpRun(tester, controller);
        expect(usageKey, findsOneWidget);
        expect(text(tester, usageKey), en.teamRunCostUnreported);
        expect(find.text('Usage'), findsOneWidget);
        expect(
          tester.getTopLeft(usageKey).dy,
          greaterThan(
            tester.getBottomLeft(key('team-task-details-steps')).dy - 1,
          ),
        );
        expect(find.textContaining(r'$'), findsNothing);
      });

      testWidgets('never shows the team\'s day as the task\'s cost', (
        tester,
      ) async {
        final (controller, _) = await boot();
        await pumpRun(tester, controller);
        expect(text(tester, usageKey), en.teamRunCostUnreported);
        expect(find.textContaining('est.'), findsNothing);
        expect(find.textContaining('12.4k'), findsNothing);
      });

      testWidgets('ignores a figure in the run\'s raw payload', (tester) async {
        final (controller, _) = await boot(
          runOverride: run(
            extraRaw: const {
              'usage': {
                'input_tokens': 700,
                'output_tokens': 250,
                'cost_usd_estimate': 0.07,
              },
            },
          ),
        );
        await pumpRun(tester, controller);
        expect(text(tester, usageKey), en.teamRunCostUnreported);
        expect(find.textContaining(r'$0.07'), findsNothing);
        expect(find.textContaining('950'), findsNothing);
      });

      testWidgets('is absent when the usage capability is off', (tester) async {
        final (controller, _) = await boot(
          capabilities: const OrchestrationCapabilities(
            projects: true,
            runs: true,
            runSteps: true,
            workGraph: true,
            agents: true,
            gatesInteractions: true,
            gatesBeads: true,
          ),
        );
        await pumpRun(tester, controller);
        expect(controller.capabilities.usage, isFalse);
        expect(key('team-task-details-body'), findsOneWidget);
        expect(usageKey, findsNothing);
      });

      testWidgets('Arabic: the line reads right to left', (tester) async {
        final (controller, _) = await boot();
        await pumpRun(tester, controller, locale: const Locale('ar'));
        expect(text(tester, usageKey), ar.teamRunCostUnreported);
        expect(Directionality.of(tester.element(usageKey)), TextDirection.rtl);
        // Right-aligned with its own label.
        final usage = tester.getRect(usageKey);
        final label = tester.getRect(find.text(ar.teamUiRunDetailsUsage));
        expect(usage.right, moreOrLessEquals(label.right, epsilon: 1));
        expect(tester.takeException(), isNull);
      });
    });

    group('agent page', () {
      // The team's tokens and cost are not this agent's (owner rule
      // 2026-09-27: nothing shown twice, nothing that is not about the
      // page's subject): the agent page keeps its own context on the
      // status row and shows no team usage panel.
      testWidgets('no team usage panel; the context stays on the status row', (
        tester,
      ) async {
        final (controller, _) = await boot();
        await pump(
          tester,
          AgentScreen(controller: controller, agentId: 'fox', now: () => clock),
        );
        expect(key('team-agent-usage-row'), findsNothing);
        expect(key('team-agent-usage'), findsNothing);
        expect(find.text('Tokens / context / cost'), findsNothing);
        expect(find.textContaining('est.'), findsNothing);
        expect(find.textContaining('ctx 63%'), findsNothing);
        expect(find.textContaining('Context 63%'), findsOneWidget);
      });
    });
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
