part of '../phone_agents_controller_test.dart';

void _phoneCheckPublicationTests() {
  test(
    'phone check drains prior auth refresh and suppresses refresh during restart',
    () async {
      final w = await _world(null);
      addTearDown(w.controller.dispose);
      w.state.runtimes = {'claude': _ready('claude'), 'fx': _ready('fx')};
      await w.controller.refreshAgentRows();
      final oldScanAtFx = Completer<void>();
      final releaseOldScan = Completer<void>();
      final nextCheck = Completer<AgentPhoneCheckResult>();
      var blockOldScan = true;
      var checkEntered = false;
      var checking = false;
      var probesDuringCheck = 0;
      var probesAfterCheck = 0;
      w.state.probeHandler = (id) async {
        if (checking) probesDuringCheck++;
        if (checkEntered && !checking) probesAfterCheck++;
        if (id == 'fx' && blockOldScan) {
          blockOldScan = false;
          oldScanAtFx.complete();
          await releaseOldScan.future;
        }
        return const AgentAuthProbeResult(state: AgentAuthProbeState.signedIn);
      };
      w.state.selfTestHandler = (id) async {
        if (id == 'fx') return nextCheck.future;
        checkEntered = true;
        checking = true;
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
            checkStep: AgentPhoneCheckStep.daemon,
          ),
        );
        // Cover both the setup listener and independent screen/timer refresh.
        await w.controller.refreshAgentRows();
        await Future<void>.delayed(Duration.zero);
        w.state.runtimes['claude'] = _ready('claude');
        checking = false;
        return AgentPhoneCheckResult(
          agentId: 'claude',
          architecture: AgentArchitecture.arm64,
          passed: true,
          completed: AgentPhoneCheckStep.values,
        );
      };
      final priorRefresh = w.controller.refreshAgentRows();
      await oldScanAtFx.future;
      final finished = w.controller.runAgentPhoneCheck('claude');
      await Future<void>.delayed(Duration.zero);
      expect(
        checkEntered,
        isFalse,
        reason: 'An auth child from the prior refresh must drain first.',
      );
      releaseOldScan.complete();
      await priorRefresh;
      expect((await finished).passed, isTrue);
      expect(probesDuringCheck, 0);
      expect(probesAfterCheck, greaterThan(0));
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
    expect(w.controller.genUiStatus.agents, contains(GenUiAgent.openCode2));
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

  Future<AgentPhoneCheckResult> unavailableClaudeCheck(
    _World w,
    AgentSignInPhase signIn,
  ) async {
    w.signIn.phase = signIn;
    w.state.selfTestHandler = (id) async => AgentPhoneCheckResult(
      agentId: id,
      architecture: AgentArchitecture.arm64,
      passed: false,
      completed: const [
        AgentPhoneCheckStep.install,
        AgentPhoneCheckStep.version,
        AgentPhoneCheckStep.daemon,
      ],
      failure: AgentHostFailure.unavailable,
      details: 'Check: unavailable.',
    );
    return w.controller.runAgentPhoneCheck('claude');
  }

  test(
    'Claude unavailable while signed out is a needs-sign-in result',
    () async {
      final w = await _world(null);
      addTearDown(w.controller.dispose);
      w.state.runtimes['claude'] = _ready('claude');
      final result = await unavailableClaudeCheck(
        w,
        AgentSignInPhase.signedOut,
      );
      expect(result.failure, AgentHostFailure.needsSignIn);
      expect(result.details, 'Check: unavailable.');
      expect(
        w.controller.agentPhoneCheck('claude')?.failure,
        AgentHostFailure.needsSignIn,
      );
    },
  );

  test('Claude unavailable while signed in stays unavailable', () async {
    final w = await _world(null);
    addTearDown(w.controller.dispose);
    w.state.runtimes['claude'] = _ready('claude');
    final result = await unavailableClaudeCheck(w, AgentSignInPhase.signedIn);
    expect(result.failure, AgentHostFailure.unavailable);
  });

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
            affected: [GenUiAgent.openCode2],
            reason: GenUiSetupProblem.verificationFailed,
          )
        : GenUiSetupOn(agents: [GenUiAgent.claude, GenUiAgent.openCode2]);
  }
}
