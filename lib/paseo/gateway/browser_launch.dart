part of '../gateway.dart';

const _browserUnavailable = ProductException(
  'The agent browser is unavailable for this chat. Turn it off to continue, '
  'or start a new chat.',
);

extension _PaseoBrowserLaunch on PaseoGateway {
  int _browserRevision(String id) => _browserRevisions[_app(id)] ?? 0;

  bool get _browserHasRequestedSource =>
      _browserProfileId != null &&
      _browserSourceId != null &&
      _browserLaunches?.hasRequestedSource(
            profileId: _browserProfileId!,
            sourceId: _browserSourceId!,
          ) ==
          true;

  bool _browserRequestedFor(String id) {
    final profile = _browserProfileId, source = _browserSourceId;
    if (profile == null || source == null) return false;
    // The shared registry is authoritative across the list and chat gateway.
    return _browserLaunches?.isRequested(
          profileId: profile,
          sourceId: source,
          sessionId: _real(id),
        ) ==
        true;
  }

  void _moveBrowserRequest(String oldID, String newID) {
    final profile = _browserProfileId, source = _browserSourceId;
    if (profile == null || source == null) return;
    _browserLaunches?.moveRequest(
      profileId: profile,
      sourceId: source,
      oldSessionId: oldID,
      newSessionId: newID,
    );
  }

  Future<List<CommandInfo>> _listBrowserCommands(String id) async {
    id = _app(id);
    if (_drafts.contains(id) || !_browserRequestedFor(id)) {
      throw _browserUnavailable;
    }
    final scope = _scope, epoch = _locationEpoch;
    if (!_agents.containsKey(id)) await _fetchAgent(id);
    _checkLocation(scope, epoch);
    final reservation = await _beforeBrowserLaunch(id);
    final result = await transport.request(
      'list_commands_request',
      {'agentId': _real(id)},
      timeout: const Duration(seconds: 45),
      beforeSend: () {
        _checkLocation(scope, epoch);
        _checkBrowserLaunch(id, reservation);
      },
    );
    _checkLocation(scope, epoch);
    return _browserCommandInfos(result);
  }

  void _requireBrowserScope() {
    if (_browserLaunches == null ||
        _browserProfileId == null ||
        _browserSourceId == null) {
      throw _browserUnavailable;
    }
  }

  Future<BrowserLaunchReservation?> _beforeBrowserLaunch(
    String id, {
    bool requiredBrowser = false,
  }) async {
    if (!_browserRequestedFor(id)) {
      if (requiredBrowser) throw _browserUnavailable;
      return null;
    }
    _requireBrowserScope();
    final scope = _scope, epoch = _locationEpoch;
    final realID = _real(id), revision = _browserRevision(id);
    if (_agents[_app(id)]?['provider'] != 'claude' ||
        !transport.connected ||
        transport.serverVersion != '0.9.2') {
      throw _browserUnavailable;
    }
    final reservation = await _browserLaunches!.reserve(
      BrowserEnrollmentTarget(
        profileId: _browserProfileId!,
        sourceId: _browserSourceId!,
        sessionId: realID,
        daemonAgentId: realID,
      ),
    );
    try {
      _checkLocation(scope, epoch);
      if (reservation == null || _browserRevision(id) != revision) {
        throw _browserUnavailable;
      }
      if (_real(id) != realID) throw _browserUnavailable;
      _checkBrowserLaunch(id, reservation);
    } catch (_) {
      await _revokeBrowserSession(realID);
      rethrow;
    }
    return reservation;
  }

  void _checkBrowserLaunch(String id, BrowserLaunchReservation? reservation) {
    if (reservation != null) {
      if (!_browserRequestedFor(id) ||
          _browserLaunches?.isCurrent(reservation) != true ||
          _real(id) != reservation.target.daemonAgentId) {
        throw _browserUnavailable;
      }
    } else if (_browserRequestedFor(id)) {
      throw _browserUnavailable;
    }
  }

  Future<void> _revokeBrowserSession(String id) async {
    _browserRevisions[_app(id)] = _browserRevision(id) + 1;
    final profile = _browserProfileId, source = _browserSourceId;
    if (profile == null || source == null) return;
    await _browserLaunches?.revokeSession(
      profileId: profile,
      sourceId: source,
      sessionId: _real(id),
    );
  }

  Future<void> _revokeBrowserSource({bool clearRequests = true}) async {
    final profile = _browserProfileId, source = _browserSourceId;
    if (profile == null || source == null) return;
    await _browserLaunches?.revokeSource(
      profileId: profile,
      sourceId: source,
      clearRequests: clearRequests,
    );
  }
}
