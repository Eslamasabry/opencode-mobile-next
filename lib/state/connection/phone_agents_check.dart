part of '../connection.dart';

extension _PhoneAgentCheckPublication on ConnectionController {
  Future<AgentPhoneCheckResult> _runAgentPhoneCheck(
    String agentId, {
    bool previousVersion = false,
  }) async {
    if (_paChecking) {
      return AgentPhoneCheckResult(
        agentId: agentId,
        architecture: null,
        passed: false,
        completed: const [],
        failure: AgentHostFailure.busy,
      );
    }
    final host = _paEnsureHost();
    final owner = _paHostProfile;
    AgentPhoneCheckResult result;
    // Fence every public refresh entry point before waiting for an existing
    // scan. Otherwise its auth child can make native update admission busy.
    _paChecking = true;
    try {
      await _paRefreshingRows;
      if (_self._disposed || _paHost != host || _paHostProfile != owner) {
        return AgentPhoneCheckResult(
          agentId: agentId,
          architecture: null,
          passed: false,
          completed: const [],
          failure: AgentHostFailure.stale,
        );
      }
      result = previousVersion && host is PhoneAgentRecoveryPort
          ? await (host as PhoneAgentRecoveryPort).usePreviousVersion(agentId)
          : await host.selfTest(agentId);
      result = await _paExplainUnavailable(result);
    } finally {
      _paChecking = false;
    }
    if (_self._disposed || _paHost != host || _paHostProfile != owner) {
      return result;
    }
    // Publish the check's final installation and authentication facts after
    // native update/restart admission has finished.
    await _paRefreshingRows;
    if (_self._disposed || _paHost != host || _paHostProfile != owner) {
      return result;
    }
    await refreshAgentRows();
    if (!_self._disposed && _paHost == host && _paHostProfile == owner) {
      _paChecks[agentId] = result;
      await _genUiRetryFailedVerification();
    }
    return result;
  }

  /// A Claude check that reached the final step and still says "unavailable"
  /// may only mean the account is signed out (the host's own status read can
  /// fail while the CLI works). Ask the sign-in status directly: a signed-out
  /// answer turns the dead end into "needs sign-in". Anything else is kept.
  Future<AgentPhoneCheckResult> _paExplainUnavailable(
    AgentPhoneCheckResult result,
  ) async {
    if (result.agentId != 'claude' ||
        result.passed ||
        result.failure != AgentHostFailure.unavailable ||
        !result.completed.contains(AgentPhoneCheckStep.daemon)) {
      return result;
    }
    try {
      final session = _paSession(result.agentId);
      await session.inspectStatus().timeout(const Duration(seconds: 15));
      if (session.state.phase != AgentSignInPhase.signedOut) return result;
    } catch (_) {
      return result;
    }
    return AgentPhoneCheckResult(
      agentId: result.agentId,
      architecture: result.architecture,
      passed: false,
      completed: result.completed,
      failure: AgentHostFailure.needsSignIn,
      observedHelperVersion: result.observedHelperVersion,
      expectedHelperVersion: result.expectedHelperVersion,
      details: result.details,
    );
  }
}

extension _PhoneAgentCapabilityPublication on ConnectionController {
  /// Only connected, unambiguous helper observations qualify a matrix row.
  String? get _paObservedHelperVersion {
    final observed = _paSources.values
        .map((source) => source.gateway.transport)
        .where((transport) => transport.connected)
        .map((transport) => transport.serverVersion)
        .whereType<String>()
        .toSet();
    final gateway = _paBackend?.api;
    if (gateway is PaseoGateway && gateway.transport.connected) {
      final version = gateway.transport.serverVersion;
      if (version != null) observed.add(version);
    }
    return observed.length == 1 ? observed.single : null;
  }

  /// Unknown or stale evidence never inherits catalog capabilities.
  AgentCapabilities _paHostCapabilities(
    AgentDescriptor descriptor,
    AgentArchitecture? architecture,
  ) => AgentCertificationMatrix.bundled.capabilitiesFor(
    descriptor: descriptor,
    architecture: architecture,
    helperVersion: _paObservedHelperVersion,
  );
}

/// Private account observations shared by phone-check publication and row
/// refresh. Public source overrides remain on the controller mixin.
extension _PhoneAgentAuthObservation on _ConnectionControllerPhoneAgents {
  static const _paSignInReadLimit = Duration(seconds: 10);

  AgentSignInPhase _paAuthPhase(AgentAuthProbeResult result) =>
      switch (result.state) {
        AgentAuthProbeState.signedIn => AgentSignInPhase.signedIn,
        AgentAuthProbeState.signedOut => AgentSignInPhase.signedOut,
        AgentAuthProbeState.error => AgentSignInPhase.failed,
      };

  /// A check that could not run this time (the phone was busy, or it took
  /// too long) is not news about the account: the last answer the agent
  /// itself gave stands, so a signed-in agent is not sent to sign in again.
  Future<AgentAuthProbeResult> _paProbeSignIn(
    PhoneAgentHostPort host,
    String id,
  ) async {
    final result = await _paProbeSignInOnce(host, id);
    final previous = _paAuthResults[id];
    final couldNotRun =
        result.error == AgentAuthProbeError.hostUnavailable ||
        result.error == AgentAuthProbeError.timedOut;
    return couldNotRun &&
            previous != null &&
            previous.state != AgentAuthProbeState.error
        ? previous
        : result;
  }

  Future<AgentAuthProbeResult> _paProbeSignInOnce(
    PhoneAgentHostPort host,
    String id,
  ) async {
    final revision = (_paAuthRevisions[id] ?? 0) + 1;
    _paAuthRevisions[id] = revision;
    try {
      if (host is PhoneAgentAuthPort) {
        final result = await (host as PhoneAgentAuthPort)
            .probeSignIn(id)
            .timeout(
              id == 'claude' ? const Duration(seconds: 65) : _paSignInReadLimit,
            );
        if (_paAuthRevisions[id] != revision) {
          return _paAuthResults[id] ??
              const AgentAuthProbeResult.failed(
                AgentAuthProbeError.hostUnavailable,
              );
        }
        if (id != 'claude' ||
            result.error != AgentAuthProbeError.probeUnsupported) {
          return result;
        }
      }
      // Preserve the existing direct Claude CLI status while lane BB supplies
      // the private multi-agent bridge. Never use generic setup receipts or
      // provider error wording as an auth fallback.
      if (id == 'claude') {
        await _paSession(id).inspectStatus().timeout(_paSignInReadLimit);
        final phase = _paSignIns[id]?.state.phase;
        return AgentAuthProbeResult(
          state: switch (phase) {
            AgentSignInPhase.signedIn => AgentAuthProbeState.signedIn,
            AgentSignInPhase.signedOut => AgentAuthProbeState.signedOut,
            _ => AgentAuthProbeState.error,
          },
        );
      }
      return const AgentAuthProbeResult.failed(
        AgentAuthProbeError.probeUnsupported,
      );
    } on TimeoutException {
      return const AgentAuthProbeResult.failed(AgentAuthProbeError.timedOut);
    } catch (_) {
      return const AgentAuthProbeResult.failed(
        AgentAuthProbeError.hostUnavailable,
      );
    }
  }
}
