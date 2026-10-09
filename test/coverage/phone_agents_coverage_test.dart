// Coverage ratchets for the agents on this phone (Settings > Agents and the
// agent sheet): what the host says about an agent, the installer's progress,
// the phone check, a removal's result, the sign-in state and what an agent's
// sign-in prints. Each case goes through the app's real code and the real
// widgets, then is checked against its ledger (see paseo_coverage_support.dart).
//
// A few facts are drawn as marks, not words (a step's tick, the bar's length):
// the case reader adds them to the screen text as "[Step state]" / "[bar 40%]"
// from the widgets themselves, so a ledger "shown" for them is still checked.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/local_terminal.dart';
import 'package:opencode_mobile/domain/agent_auth_probe.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';
import 'package:opencode_mobile/domain/agent_sign_in.dart';
import 'package:opencode_mobile/domain/phone_agent_host.dart';
import 'package:opencode_mobile/domain/phone_agents.dart';
import 'package:opencode_mobile/domain/server_gateway/capabilities.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/agents/agent_sheet.dart';
import 'package:opencode_mobile/ui/screens/agents/agent_sign_in_terminal.dart';
import 'package:opencode_mobile/ui/screens/agents/agents_section.dart';
import 'package:opencode_mobile/ui/screens/agents/phone_check_view.dart';

import '../../tool/capture/fixtures.dart' show loadCaptureFonts;
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../support/agents_fakes.dart';
import '../support/chats_fakes.dart';
import '../support/fake_local_terminal.dart';
import '../support/fake_sign_in_foreground.dart';
import 'paseo_coverage_support.dart';
import 'servers_support.dart' show frames;

/// Settings > Agents with the agent's own status check answering [account].
class _Source extends FakeRemovableAgentsSource {
  _Source({super.rows, this.account});
  AgentAuthProbeResult? account;
  Completer<void>? hold;

  @override
  AgentAuthProbeResult? agentAccount(String agentId) => account;

  @override
  Future<void> recheckAgentSignIn(String agentId) async {
    calls.add('recheck:$agentId');
    if (hold != null) await hold!.future;
  }
}

T _byName<T extends Enum>(List<T> values, Object? name) =>
    values.firstWhere((value) => value.name == name);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });

  final families = {
    for (final name in [
      'phone_agent_row',
      'phone_agent_install',
      'phone_agent_check',
      'phone_agent_removal',
      'phone_agent_signin',
      'phone_agent_signin_output',
    ])
      name: CoverageFamily(name, prefix: ''),
  };
  for (final family in families.values) {
    group('ledger · ${family.name}', () => registerLedgerTests(family));
  }

  Future<GlobalKey> pump(
    WidgetTester tester,
    FakePhoneAgentsSource agents,
    Widget child, {
    LocalTerminalSessions? terminal,
  }) async {
    tester.view.physicalSize = const Size(412, 1600);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final boundary = GlobalKey();
    final host = FakeChatsHost(
      FakeChatFeedSource(
        items: const [],
        projects: [project('alpha')],
        lastUsed: '/root/projects/alpha',
      ),
    )..phoneAgents = agents;
    await tester.pumpWidget(
      chatsApp(
        host,
        RepaintBoundary(key: boundary, child: child),
        terminal: terminal,
      ),
    );
    await frames(tester, 14);
    return boundary;
  }

  /// The screen text plus the marks a person sees but the widgets do not spell.
  String readScreen(WidgetTester tester) {
    final lines = [...screenText(tester)];
    for (final view in tester.widgetList<KitProgressView>(
      find.byType(KitProgressView),
    )) {
      final value = view.progress.value;
      if (value != null) lines.add('[bar ${(value * 100).round()}%]');
    }
    for (final step in AgentPhoneCheckStep.values) {
      final row = find.byKey(ValueKey('agents-check-${step.name}'));
      if (row.evaluate().isEmpty) continue;
      final mark = find.descendant(of: row, matching: find.byType(KitTaskMark));
      final state = tester.widget<KitTaskMark>(mark).state.name;
      final title = switch (step) {
        AgentPhoneCheckStep.install => 'Installed',
        AgentPhoneCheckStep.version => 'Version',
        AgentPhoneCheckStep.daemon => 'Connection',
        AgentPhoneCheckStep.hello => 'Ready',
      };
      lines.add('[$title $state]');
    }
    return lines.join('\n');
  }

  Future<void> finish(
    WidgetTester tester,
    GlobalKey boundary,
    CoverageFamily family,
    Map variant,
    String screen,
  ) async {
    await writeCasePng(tester, boundary, 'phone_${variant['id']}');
    final problems = checkCase(family, variant, screen, primaryText: screen);
    await tester.pumpWidget(const SizedBox.shrink());
    expect(problems, isEmpty, reason: 'screen text:\n${flat(screen)}');
  }

  // ------------------------------------------------------------- the list
  final rowFamily = families['phone_agent_row']!;
  for (final variant in rowFamily.cases) {
    testWidgets('agents list · ${variant['id']}', (tester) async {
      final payload = variant['payload'] as Map;
      final r = Map<String, dynamic>.from(payload['runtime'] as Map);
      final caps = Map<String, dynamic>.from(r['capabilities'] as Map);
      final row = buildAgentRow(
        descriptor: AgentCatalog.builtIn.byId('claude')!,
        architecture: AgentArchitecture.arm64,
        serverCapabilities: ServerCapabilities.allV1,
        runtime: PhoneAgentRuntime(
          agentId: 'claude',
          installed: r['installed'] as bool,
          payloadPresent: r['payloadPresent'] as bool,
          hostAvailable: r['hostAvailable'] as bool,
          architectureQualified: r['architectureQualified'] as bool,
          stoppedInBackground: r['stoppedInBackground'] as bool,
          signInPhase: r['signInPhase'] == null
              ? null
              : _byName(AgentSignInPhase.values, r['signInPhase']),
          resetAt: r['resetAt'] == null
              ? null
              : DateTime.parse(r['resetAt'] as String),
          capabilities: AgentCapabilities(
            resumeVerified: caps['resumeVerified'] as bool,
            modelList: caps['modelList'] as bool,
            permissions: caps['permissions'] as bool,
            images: caps['images'] as bool,
            cancel: caps['cancel'] as bool,
          ),
        ),
      );
      final account = AgentAuthProbeResult.fromJson(
        Map<String, dynamic>.from(payload['account'] as Map),
      );
      final agents = _Source(rows: [row], account: account);
      final boundary = await pump(
        tester,
        agents,
        ListView(children: const [AgentsSection()]),
      );
      await finish(tester, boundary, rowFamily, variant, readScreen(tester));
    });
  }

  // ------------------------------------------------------------- install
  final installFamily = families['phone_agent_install']!;
  for (final variant in installFamily.cases) {
    testWidgets('agent install · ${variant['id']}', (tester) async {
      final p = Map<String, dynamic>.from(
        (variant['payload'] as Map)['progress'] as Map,
      );
      final agents = _Source(
        rows: [agentRowFor('codex', FakeAgentStage.notInstalled)],
      );
      agents.progress = AgentSetupProgress(
        agentId: 'codex',
        phase: _byName(AgentSetupPhase.values, p['phase']),
        fraction: (p['fraction'] as num?)?.toDouble(),
        componentId: p['componentId'] as String?,
        failure: p['failure'] == null
            ? null
            : _byName(AgentHostFailure.values, p['failure']),
      );
      final boundary = await pump(
        tester,
        agents,
        const AgentSheet(agentId: 'codex', step: AgentSheetStep.setup),
      );
      await finish(
        tester,
        boundary,
        installFamily,
        variant,
        readScreen(tester),
      );
    });
  }

  // --------------------------------------------------------------- check
  final checkFamily = families['phone_agent_check']!;
  for (final variant in checkFamily.cases) {
    testWidgets('phone check · ${variant['id']}', (tester) async {
      final c = Map<String, dynamic>.from(
        (variant['payload'] as Map)['check'] as Map,
      );
      final result = AgentPhoneCheckResult(
        agentId: c['agentId'] as String,
        architecture: _byName(AgentArchitecture.values, c['architecture']),
        passed: c['passed'] as bool,
        completed: [
          for (final step in (c['completed'] as List? ?? const []))
            _byName(AgentPhoneCheckStep.values, step),
        ],
        failure: c['failure'] == null
            ? null
            : _byName(AgentHostFailure.values, c['failure']),
      );
      final boundary = await pump(
        tester,
        _Source(),
        Padding(
          padding: const EdgeInsets.all(16),
          child: AgentPhoneCheckView(agent: 'Codex', result: result),
        ),
      );
      await finish(tester, boundary, checkFamily, variant, readScreen(tester));
    });
  }

  // ------------------------------------------------------------- removal
  final removalFamily = families['phone_agent_removal']!;
  for (final variant in removalFamily.cases) {
    testWidgets('agent removal · ${variant['id']}', (tester) async {
      final r = Map<String, dynamic>.from(
        (variant['payload'] as Map)['removal'] as Map,
      );
      final agents = _Source(
        rows: [agentRowFor('codex', FakeAgentStage.ready)],
        account: const AgentAuthProbeResult(
          state: AgentAuthProbeState.signedIn,
        ),
      )..removable.add('codex');
      agents.signIn['codex'] = const AgentSignInState(
        phase: AgentSignInPhase.signedIn,
        method: AgentSignInMethod.browserOAuthHost,
        inspected: true,
      );
      agents.nextRemoval = AgentRemovalResult(
        agentId: r['agentId'] as String,
        freedBytes: (r['freedBytes'] as num).toInt(),
        alreadyAbsent: r['alreadyAbsent'] as bool,
      );
      final boundary = await pump(
        tester,
        agents,
        const AgentSheet(agentId: 'codex', step: AgentSheetStep.signIn),
      );
      await tester.tap(find.byKey(const ValueKey('agents-remove')));
      await frames(tester, 10);
      await tester.tap(find.byKey(const ValueKey('agents-remove-confirm')));
      await frames(tester, 14);
      await finish(
        tester,
        boundary,
        removalFamily,
        variant,
        readScreen(tester),
      );
    });
  }

  // ------------------------------------------------------------- sign-in
  final signinFamily = families['phone_agent_signin']!;
  for (final variant in signinFamily.cases) {
    testWidgets('agent sign-in · ${variant['id']}', (tester) async {
      final s = Map<String, dynamic>.from(
        (variant['payload'] as Map)['state'] as Map,
      );
      final agents = _Source(
        rows: [agentRowFor('codex', FakeAgentStage.ready)],
        account: const AgentAuthProbeResult(
          state: AgentAuthProbeState.signedIn,
        ),
      );
      final inspected = s['inspected'] as bool;
      if (inspected) {
        agents.signIn['codex'] = AgentSignInState(
          phase: _byName(AgentSignInPhase.values, s['phase']),
          method: _byName(AgentSignInMethod.values, s['method']),
          inspected: true,
        );
      } else {
        agents.hold = Completer<void>();
      }
      final boundary = await pump(
        tester,
        agents,
        const AgentSheet(agentId: 'codex', step: AgentSheetStep.signIn),
      );
      await finish(tester, boundary, signinFamily, variant, readScreen(tester));
    });
  }

  // ------------------------------------------- what the agent's sign-in prints
  final outputFamily = families['phone_agent_signin_output']!;
  for (final variant in outputFamily.cases) {
    testWidgets('agent sign-in output · ${variant['id']}', (tester) async {
      final text = (variant['payload'] as Map)['text'] as String;
      final backend = FakeLocalTerminalBackend();
      final agents = _Source(
        rows: [agentRowFor('codex', FakeAgentStage.signedOut)],
      );
      final boundary = await pump(
        tester,
        agents,
        AgentSignInTerminalScreen(
          agents: agents,
          agentId: 'codex',
          agentName: 'Codex',
          sessions: LocalTerminalSessions(
            backend: backend,
            signInForeground: FakeSignInForeground(),
          ),
          openPage: (context, url) async {},
        ),
      );
      await tester.pump(const Duration(milliseconds: 200));
      backend.output(1, text);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 1));
      await frames(tester, 6);
      await finish(tester, boundary, outputFamily, variant, readScreen(tester));
    });
  }
}
