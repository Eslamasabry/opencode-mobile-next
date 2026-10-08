part of '../phone_agents_controller_test.dart';

void _phoneCheckPublicationTests() {
  test(
    'finished phone check publishes fresh qualification before the next agent',
    () async {
      final w = await _world(null);
      addTearDown(w.controller.dispose);
      w.state.runtimes = {'claude': _ready('claude'), 'fx': _ready('fx')};
      await w.controller.refreshAgentRows();
      final oldScanAtFx = Completer<void>();
      final releaseOldScan = Completer<void>();
      final nextCheck = Completer<AgentPhoneCheckResult>();
      var blockOldScan = true;
      w.state.probeHandler = (id) async {
        if (id == 'fx' && blockOldScan) {
          blockOldScan = false;
          oldScanAtFx.complete();
          await releaseOldScan.future;
        }
        return const AgentAuthProbeResult(state: AgentAuthProbeState.signedIn);
      };
      w.state.selfTestHandler = (id) async {
        if (id == 'fx') return nextCheck.future;
        w.state.runtimes['claude'] = const PhoneAgentRuntime(
          agentId: 'claude',
          installed: true,
          hostAvailable: true,
          signInPhase: AgentSignInPhase.signedIn,
        );
        w.host.setup.add(
          const AgentSetupProgress(
            agentId: 'claude',
            phase: AgentSetupPhase.done,
          ),
        );
        await oldScanAtFx.future;
        w.state.runtimes['claude'] = _ready('claude');
        return AgentPhoneCheckResult(
          agentId: 'claude',
          architecture: AgentArchitecture.arm64,
          passed: true,
          completed: AgentPhoneCheckStep.values,
        );
      };
      final finished = w.controller.runAgentPhoneCheck('claude');
      await oldScanAtFx.future;
      releaseOldScan.complete();
      expect((await finished).passed, isTrue);
      final fxCheck = w.controller.runAgentPhoneCheck('fx');
      expect(w.controller.agentPhoneCheck('claude')!.passed, isTrue);
      expect(
        w.controller.agentRows.singleWhere((row) => row.id == 'claude').status,
        PhoneAgentStatus.ready,
      );
      nextCheck.complete(
        AgentPhoneCheckResult(
          agentId: 'fx',
          architecture: AgentArchitecture.arm64,
          passed: true,
          completed: AgentPhoneCheckStep.values,
        ),
      );
      await fxCheck;
    },
  );

  test('phone check retries a failed live cards verification', () async {
    final installer = _RetryCardsInstaller();
    final w = await _world(null, genUiInstaller: installer);
    addTearDown(w.controller.dispose);
    w.controller
      ..directory = '/work/project'
      ..status = StreamStatus.connected;
    w.state.runtimes['claude'] = _ready('claude');
    await w.controller.setGenUiEnabled(true);
    expect(installer.calls, 1);
    expect(w.controller.genUiStatus, isA<GenUiSetupPartial>());
    await w.controller.runAgentPhoneCheck('claude');
    expect(installer.calls, 2);
    expect(w.controller.genUiStatus.agents, contains(GenUiAgent.openCode1));
  });

  test('phone checks leave Cards explicitly off', () async {
    final installer = _RetryCardsInstaller();
    final w = await _world(null, genUiInstaller: installer);
    addTearDown(w.controller.dispose);
    w.state.runtimes['claude'] = _ready('claude');
    await w.controller.setGenUiEnabled(false);
    await w.controller.runAgentPhoneCheck('claude');
    expect(installer.calls, 1);
    expect(w.controller.genUiEnabled, isFalse);
    expect(w.controller.genUiStatus, isA<GenUiSetupOff>());
  });

  test(
    'phone check completion cannot republish a retired host result',
    () async {
      final w = await _world(null);
      addTearDown(w.controller.dispose);
      final result = Completer<AgentPhoneCheckResult>();
      w.state.selfTestHandler = (_) => result.future;
      final pending = w.controller.runAgentPhoneCheck('claude');
      await w.controller.closePhoneAgentsForSignInReset();
      result.complete(
        AgentPhoneCheckResult(
          agentId: 'claude',
          architecture: AgentArchitecture.arm64,
          passed: true,
          completed: AgentPhoneCheckStep.values,
        ),
      );
      await pending;
      expect(w.controller.agentPhoneCheck('claude'), isNull);
    },
  );

  test('failed phone recheck never borrows the earlier ready result', () async {
    final w = await _world(null);
    addTearDown(w.controller.dispose);
    w.state.runtimes['claude'] = _ready('claude');
    await w.controller.runAgentPhoneCheck('claude');
    w.state.selfTestHandler = (id) async {
      w.state.runtimes[id] = PhoneAgentRuntime(
        agentId: id,
        installed: true,
        hostAvailable: true,
        signInPhase: AgentSignInPhase.signedIn,
      );
      return AgentPhoneCheckResult(
        agentId: id,
        architecture: AgentArchitecture.arm64,
        passed: false,
        completed: const [],
      );
    };
    expect((await w.controller.runAgentPhoneCheck('claude')).passed, isFalse);
    expect(
      w.controller.agentRows.singleWhere((row) => row.id == 'claude').status,
      PhoneAgentStatus.needsQualification,
    );
  });
}

class _RetryCardsInstaller implements GenUiInstaller {
  var calls = 0;
  @override
  Future<GenUiSetupStatus> setEnabled({
    required String profileId,
    required Set<GenUiAgent> agents,
    required bool enabled,
  }) async {
    calls++;
    if (!enabled) return const GenUiSetupOff();
    return calls == 1
        ? GenUiSetupPartial(
            agents: [GenUiAgent.claude],
            affected: [GenUiAgent.openCode1],
            reason: GenUiSetupProblem.verificationFailed,
          )
        : GenUiSetupOn(agents: [GenUiAgent.claude, GenUiAgent.openCode1]);
  }
}
