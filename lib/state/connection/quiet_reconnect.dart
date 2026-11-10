part of '../connection.dart';

extension _QuietReconnect on ConnectionController {
  void _observeQuietReconnect(StreamStatus next) {
    // A fresh/manual connection attempt is visible even after a healthy link.
    if (status != StreamStatus.connected ||
        (next != StreamStatus.reconnecting &&
            next != StreamStatus.disconnected) ||
        api == null ||
        isIsolated ||
        _lifecycleSuspended ||
        _quietReconnectToken != null ||
        !automationPolicy.allows(AutomationBehavior.reconnect)) {
      return;
    }
    final token = Object();
    _quietReconnectToken = token;
    _quietReconnectOwner = (_connectedProfile ?? profile)?.id;
    _quietReconnectAttempt = connectionAttemptRevision;
    _quietReconnectTimer = Timer(const Duration(seconds: 15), () {
      if (_disposed || !identical(token, _quietReconnectToken)) return;
      _resetQuietReconnect();
      _notifyListeners();
    });
    if (isAgentBackend) unawaited(readConnectionHelperExit());
  }

  void _resetQuietReconnect() {
    _quietReconnectTimer?.cancel();
    _quietReconnectTimer = null;
    _quietReconnectToken = null;
    _quietReconnectOwner = null;
    _quietReconnectAttempt = null;
  }

  Future<void> _refreshAfterReconnect(
    int generation,
    ServerGateway currentApi,
  ) async {
    final token = _quietReconnectToken;
    final location = locationRevision;
    await refreshSessions();
    if (!_isCurrent(generation, currentApi) ||
        status != StreamStatus.connected ||
        location != locationRevision ||
        !identical(token, _quietReconnectToken) ||
        sessionsError != null) {
      return;
    }
    _resetQuietReconnect();
    _notifyListeners();
  }

  Future<AgentHelperStatus?> _readConnectionHelperExit() async {
    if (_disposed || !isAgentBackend) return null;
    final generation = _generation;
    final owner = _connectedProfile?.id;
    AgentHelperStatus? observed;
    try {
      observed = await phoneAgentHelperStatus().timeout(
        const Duration(seconds: 2),
      );
    } catch (_) {
      // Older APKs and unavailable diagnostics are not evidence of a kill.
    }
    if (_disposed ||
        generation != _generation ||
        owner != _connectedProfile?.id) {
      return null;
    }
    if (_lastConnectionHelperOwner != owner) {
      _lastConnectionHelperExit = null;
      _lastConnectionHelperOwner = owner;
    }
    final at = observed?.lastExitAt;
    final previous = _lastConnectionHelperExit?.lastExitAt;
    if (at != null &&
        observed?.lastStopRequested == false &&
        (previous == null || !at.isBefore(previous))) {
      _lastConnectionHelperExit = observed;
    }
    return _lastConnectionHelperExit;
  }
}
