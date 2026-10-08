part of '../connection.dart';

extension _PhoneAgentCheckPublication on ConnectionController {
  Future<AgentPhoneCheckResult> _runAgentPhoneCheck(String agentId) async {
    final host = _paEnsureHost();
    final owner = _paHostProfile;
    final result = await host.selfTest(agentId);
    if (_self._disposed || _paHost != host || _paHostProfile != owner) {
      return result;
    }
    // Install's done event can have scanned the temporarily cleared gate.
    // Drain that snapshot before reading the check's final persisted result.
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
