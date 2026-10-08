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
