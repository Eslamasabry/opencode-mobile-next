import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';
import 'package:opencode_mobile/domain/agent_sign_in.dart';
import 'package:opencode_mobile/domain/phone_agent_host.dart';
import 'package:opencode_mobile/domain/phone_agents.dart';
import 'package:opencode_mobile/domain/phone_agents_source.dart';
import 'package:opencode_mobile/ui/screens/agents/agent_sheet.dart';
import 'package:opencode_mobile/ui/screens/chats/chats_host.dart';
import 'package:widgetbook/widgetbook.dart';

import '../catalog.dart';
import '../fakes/fake_app.dart';
import '../fakes/fake_chats_host.dart';

class _CheckAgents extends ChangeNotifier
    implements PhoneAgentsSource, PhoneAgentRecoverySource {
  _CheckAgents(
    this.failure, {
    this.downloading = false,
    this.previous,
    this.agentId = 'claude',
    this.setup = false,
  });
  final String agentId;
  final bool setup;
  final AgentHostFailure? failure;
  final bool downloading;
  final String? previous;

  @override
  List<AgentRow> get agentRows => [
    buildAgentRow(
      descriptor: AgentCatalog.builtIn.byId(agentId)!,
      architecture: AgentArchitecture.arm64,
      serverCapabilities: catalogServerCapabilities,
      runtime: PhoneAgentRuntime(
        agentId: agentId,
        installed: !setup,
        checkRequiredReason: 'helperVersion',
      ),
    ),
  ];

  @override
  List<ChatAgentChoice> get chatAgentChoices => const [];

  @override
  AgentSetupProgress get agentSetupProgress => AgentSetupProgress(
    agentId: agentId,
    phase: setup
        ? AgentSetupPhase.idle
        : downloading
        ? AgentSetupPhase.installing
        : AgentSetupPhase.done,
    componentId: 'agent-claude',
    downloadedBytes: 60000000,
    totalBytes: 240000000,
    checkStep: AgentPhoneCheckStep.install,
  );

  @override
  Future<void> refreshAgentRows() async {}

  @override
  AgentSignInState? agentSignInState(String agentId) => const AgentSignInState(
    phase: AgentSignInPhase.signedOut,
    method: AgentSignInMethod.browserOAuthHost,
    inspected: true,
  );

  @override
  Future<void> recheckAgentSignIn(String agentId) async {}

  @override
  Future<AgentPhoneCheckResult> runAgentPhoneCheck(String agentId) async {
    if (downloading) return Completer<AgentPhoneCheckResult>().future;
    return AgentPhoneCheckResult(
      agentId: agentId,
      architecture: AgentArchitecture.arm64,
      passed: false,
      completed: const [
        AgentPhoneCheckStep.install,
        AgentPhoneCheckStep.version,
        AgentPhoneCheckStep.daemon,
      ],
      failure: failure,
      previousVersion: previous,
      details: 'Paseo expected 0.11.2; observed 0.9.2',
    );
  }

  @override
  Future<AgentPhoneCheckResult> usePreviousAgentVersion(String agentId) =>
      runAgentPhoneCheck(agentId);

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _CheckHost extends FakeHost {
  _CheckHost(this.phone) : super(FakeFeed());
  final _CheckAgents phone;
  @override
  PhoneAgentsSource get agents => phone;
  @override
  Listenable get listenable => phone;
}

class _CheckScene implements SceneResource {
  _CheckScene(this.app, this.agents);
  final FakeApp app;
  final _CheckAgents agents;
  @override
  void dispose() {
    agents.dispose();
    app.dispose();
  }
}

Widget _check({
  AgentHostFailure? failure,
  bool downloading = false,
  String? previous,
  String agentId = 'claude',
  AgentSheetStep step = AgentSheetStep.check,
}) => FakeAppScene<_CheckScene>(
  platform: FakePlatform.android,
  create: () async => _CheckScene(
    await FakeApp.create(),
    _CheckAgents(
      failure,
      downloading: downloading,
      previous: previous,
      agentId: agentId,
      setup: step == AgentSheetStep.setup,
    ),
  ),
  builder: (context, scene) => ProviderScope(
    overrides: [chatsHostProvider.overrideWithValue(_CheckHost(scene.agents))],
    child: AgentSheet(agentId: agentId, step: step),
  ),
);

CatalogComponent agentPhoneCheck() => catalogComponent(
  area: 'Agents',
  screen: 'AgentSheet',
  name: 'Check this phone',
  useCases: [
    WidgetbookUseCase(
      name: 'Antigravity install',
      builder: (_) =>
          _check(agentId: 'antigravity', step: AgentSheetStep.setup),
    ),
    WidgetbookUseCase(
      name: 'Antigravity Google sign-in',
      builder: (_) =>
          _check(agentId: 'antigravity', step: AgentSheetStep.signIn),
    ),
    WidgetbookUseCase(
      name: 'Gemini switch guidance',
      builder: (_) => _check(agentId: 'gemini', step: AgentSheetStep.setup),
    ),
    WidgetbookUseCase(
      name: 'Downloading a resumed update',
      builder: (_) => _check(downloading: true),
    ),
    WidgetbookUseCase(
      name: 'Paseo needs an update',
      builder: (_) => _check(failure: AgentHostFailure.helperVersion),
    ),
    WidgetbookUseCase(
      name: 'Sign in needed',
      builder: (_) => _check(failure: AgentHostFailure.needsSignIn),
    ),
    WidgetbookUseCase(
      name: 'Retry or use the previous version',
      builder: (_) =>
          _check(failure: AgentHostFailure.hello, previous: '2.1.283'),
    ),
  ],
);
