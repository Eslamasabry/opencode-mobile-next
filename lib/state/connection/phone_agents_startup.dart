part of '../connection.dart';

// Launch acknowledgement is not helper readiness. Keep this presentation
// window separate from native admission and permission decisions.
extension _PhoneAgentStartup on _ConnectionControllerPhoneAgents {
  bool get _paHelperAvailable =>
      _paHelperObserved == true ||
      _paBusySubscriptions.keys.any((gateway) => gateway.transport.connected);

  bool get _paStarting =>
      _paOrdinaryStarts.isNotEmpty || _paStartupExpiry?.isActive == true;

  void _paBeginStartup() {
    if (_paStartupExpiry?.isActive == true) return;
    _paStartupExpiry = Timer(const Duration(seconds: 30), () {
      _paEndStartup();
      if (!_self._disposed) _self._notifyListeners();
    });
    _paStartupPoll = Timer.periodic(const Duration(seconds: 3), (_) {
      if (!_self._disposed) {
        unawaited(refreshAgentRows().catchError((Object _) {}));
      }
    });
  }

  void _paEndStartup() {
    _paStartupExpiry?.cancel();
    _paStartupExpiry = null;
    _paStartupPoll?.cancel();
    _paStartupPoll = null;
  }

  PhoneAgentRuntime _paWithHelperReadiness(PhoneAgentRuntime runtime) {
    if (!_paHelperAvailable || !runtime.installed) return runtime;
    return PhoneAgentRuntime(
      agentId: runtime.agentId,
      installed: runtime.installed,
      hostAvailable: true,
      architectureQualified: runtime.architectureQualified,
      capabilities: runtime.capabilities,
      signInPhase: runtime.signInPhase,
      resetAt: runtime.resetAt,
    );
  }
}
