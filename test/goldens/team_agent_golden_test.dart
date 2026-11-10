// Golden renders of the AI Team agent page and the Gate sheet on the kit
// (screen-team-1): 412x915 and 1280x800, dark and light, the app's real
// fonts, a pinned clock and the recorded Gas City fixture.
//
// team_agent: the short status page (the question waiting as a needs-you
// row, the status panel, Technical details folded, and the pinned block:
// Live output here — no OpenCode server to find its conversation on —
// then Message fox, two fallbacks and the rest under More).
// team_agent_details: Technical details opened, scrolled to its end.
// team_gate_choice / team_gate_free_text / team_gate_run_failed: the Gate
// sheet's variants over the agent page.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/team_agent_golden_test.dart
// and look at every changed image before committing it.
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/orchestration/adapters/fixture/fixture_gateway.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/team/agent_screen.dart';
import 'package:opencode_mobile/ui/screens/team/gate_sheet.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;

final _clock = DateTime.utc(2026, 9, 11, 9, 41);

/// The recorded fixture with the agent, work and questions the pages show.
class _Gateway extends FixtureOrchestrationGateway {
  _Gateway({required super.fixturePath});

  @override
  Future<List<OrchestrationRun>> runs({String? projectId}) async => [
    OrchestrationRun(
      id: 'oc-xru',
      title: 'Offline-first sessions',
      state: RunState.working,
      kind: RunKind.batch,
      stepCount: 3,
      completedSteps: 1,
      startedAt: _clock.subtract(const Duration(hours: 3)),
      updatedAt: _clock,
    ),
  ];

  @override
  Future<List<WorkItem>> work({String? projectId}) async => const [
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
      assignee: 'fox',
    ),
    WorkItem(
      id: 'w4',
      title: 'Conflict policy',
      state: WorkState.ready,
      runId: 'oc-xru',
    ),
  ];

  @override
  Future<List<OrchestrationAgent>> agents() async => [
    OrchestrationAgent(
      id: 'fox',
      name: 'fox',
      state: AgentState.working,
      rawState: 'active',
      sessionId: 'bl-5qc',
      sessionName: 'ocproof--polecat--fox',
      pool: 'gastown.polecat',
      provider: 'opencode',
      model: 'openai/gpt-x',
      harness: 'OpenCode',
      currentWorkId: 'w2',
      lastActivity: _clock.subtract(const Duration(minutes: 12)),
      contextPercent: 63,
      workDir: '/home/eslam/city/.gc/worktrees/ocproof/polecats/fox',
      branch: 'polecat/oc-cq6',
      sessionStartedAt: _clock.subtract(const Duration(hours: 3, minutes: 14)),
    ),
  ];

  @override
  Future<List<OrchestrationGate>> gates() async => [
    OrchestrationGate(
      id: 'g1',
      kind: GateKind.choice,
      title: 'Which persistence strategy?',
      workId: 'w2',
      agentId: 'bl-5qc',
      choices: const ['SQLite', 'Filesystem'],
      createdAt: _clock,
    ),
    OrchestrationGate(
      id: 'g2',
      kind: GateKind.freeText,
      title: 'What should the offline banner say?',
      workId: 'w4',
      createdAt: _clock.subtract(const Duration(minutes: 4)),
    ),
    OrchestrationGate(
      id: 'g3',
      kind: GateKind.runFailed,
      title: 'Offline-first sessions failed',
      prompt: 'npm test exited with code 1: 3 tests failed in sync_test.ts',
      runId: 'oc-xru',
      createdAt: _clock.subtract(const Duration(minutes: 9)),
    ),
  ];
}

Directory _fixtureRoot() {
  var dir = Directory.current;
  for (var i = 0; i < 5; i++) {
    final candidate = Directory('${dir.path}/tool/qa/gascity_fixture');
    if (candidate.existsSync()) return candidate;
    dir = dir.parent;
  }
  throw StateError('tool/qa/gascity_fixture not found');
}

Future<OrchestrationController> _controller() async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final path = _fixtureRoot().path;
  final config = OrchestrationConfig(
    provider: OrchestrationProvider.fixture,
    url: path,
    city: 'bright-lights',
    enabledAt: DateTime.utc(2026, 9, 10),
  );
  final controller = OrchestrationController(
    profile: ServerProfile(
      id: 'golden',
      name: 'Development PC',
      baseUrl: 'https://server.example',
      orchestration: config,
    ),
    config: config,
    store: OrchestrationStore(prefs),
    gatewayFactory: (_, _) => _Gateway(fixturePath: path),
    now: () => _clock,
  );
  await controller.start();
  return controller;
}

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

Future<void> _golden(
  WidgetTester tester,
  String name, {
  required bool light,
  Size size = _phone,
  Future<void> Function(OrchestrationController controller)? before,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final controller = await _controller();
  final boundary = GlobalKey();
  try {
    await tester.pumpWidget(
      MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: captureTheme(light: light),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(disableAnimations: true),
          child: RepaintBoundary(key: boundary, child: child),
        ),
        home: AgentScreen(
          controller: controller,
          agentId: 'fox',
          now: () => _clock,
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    await before?.call(controller);
    expect(tester.takeException(), isNull);
    final suffix = size == _phone ? '' : '_${size.width.toInt()}x800';
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('$name${suffix}_${light ? 'light' : 'dark'}.png'),
    );
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
    await tester.pump();
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  Future<void> Function(OrchestrationController) gate(
    WidgetTester tester,
    String id,
  ) => (controller) async {
    final context = tester.element(find.byType(AgentScreen));
    showGateSheet(context, controller, id, now: () => _clock);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 600));
  };

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';

    for (final size in [_phone, _wide]) {
      final at = size == _phone ? 'phone' : 'wide';

      testWidgets('agent · top · $mode · $at', (tester) async {
        await _golden(tester, 'team_agent_top', light: light, size: size);
      });

      testWidgets('gate · choice · $mode · $at', (tester) async {
        await _golden(
          tester,
          'team_gate_choice',
          light: light,
          size: size,
          before: gate(tester, 'g1'),
        );
      });
    }

    testWidgets('agent · details · $mode', (tester) async {
      await _golden(
        tester,
        'team_agent_details',
        light: light,
        before: (_) async {
          await tester.tap(find.byKey(const ValueKey('team-agent-technical')));
          await tester.pump(const Duration(milliseconds: 500));
          await tester.drag(
            find.byKey(const ValueKey('team-agent-list')),
            const Offset(0, -2000),
          );
          await tester.pump(const Duration(milliseconds: 500));
        },
      );
    });

    testWidgets('gate · free text · $mode', (tester) async {
      await _golden(
        tester,
        'team_gate_free_text',
        light: light,
        before: gate(tester, 'g2'),
      );
    });

    testWidgets('gate · run failed · $mode', (tester) async {
      await _golden(
        tester,
        'team_gate_run_failed',
        light: light,
        before: gate(tester, 'g3'),
      );
    });
  }
}
