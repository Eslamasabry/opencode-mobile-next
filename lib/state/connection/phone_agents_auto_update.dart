part of '../connection.dart';

/// Moves installed agents to the version this app pins, by themselves, after
/// an app update (SPEC "Layer 2"). The same rules as `InAppAutoUpdater`:
/// only when nothing is running, one try per pin per app run, and a failure
/// leaves the row's normal error and Check. The install keeps the old
/// version's folder and switches the launcher only after the new one runs
/// (lib/builtin/agents/agent_scripts.dart), so a failed update leaves the old
/// version where it was; its sign-in lives in the agent's own home, which an
/// install never touches.
extension _PhoneAgentAutoUpdate on ConnectionController {
  String _paAutoKey(String agentId) =>
      '$agentId:${_paCatalog.byId(agentId)?.recipe?.version}:'
      '${PaseoPhoneScripts.version}:${PaseoPhoneScripts.packageLockSha256}';

  /// The rows as the screens see them: an agent owed an update says so
  /// instead of "the required version changed" while the update is waiting
  /// for a quiet moment or running.
  List<AgentRow> _paWithAutoUpdate(List<AgentRow> rows) {
    if (!rows.any(phoneAgentOwesUpdate)) return rows;
    return [
      for (final row in rows)
        if (!phoneAgentOwesUpdate(row))
          row
        else if (_paAutoUpdating == row.id)
          row.withAutoUpdate(
            AgentAutoUpdate.updating,
            version: row.checkRequiredReason == 'agentVersion' || row.behindPin
                ? _paCatalog.byId(row.id)?.recipe?.version
                : null,
          )
        else if (_paAutoWifiWaiting.contains(row.id))
          row.withAutoUpdate(AgentAutoUpdate.waitingWifi)
        else if (!_paAutoAttempted.contains(_paAutoKey(row.id)))
          row.withAutoUpdate(AgentAutoUpdate.waiting)
        else
          row,
    ];
  }

  /// The biggest download this agent's update would start.
  int _paAutoDownloadBytes(String agentId) {
    var most = 0;
    for (final artifact
        in _paCatalog.byId(agentId)?.recipe?.artifacts.values ??
            const <AgentArtifact>[]) {
      final bytes = artifact.downloadBytes ?? 0;
      if (bytes > most) most = bytes;
    }
    return most;
  }

  /// What stands between the person and Send right now, for the composer:
  /// the update is switching versions (no older version to run work on for a
  /// moment), so Send waits and shows the progress instead of being refused.
  void _paPublishBlock(String agentId, {double? fraction}) {
    final host = _paHost;
    final canYield =
        host is PhoneAgentAutoUpdatePort &&
        (host as PhoneAgentAutoUpdatePort).autoUpdateCanYield;
    final row = _paRowFor(agentId);
    final usable = row != null && row.chatSelectable;
    agentUpdateBlock.value = canYield && usable
        ? null
        : AgentUpdateBlock(
            agentName: row?.name ?? agentId,
            fraction: fraction,
            everyAgent: !canYield,
          );
  }

  bool get _paAutoBusy {
    final profile = _paProfile?.id;
    if (profile == null) return true;
    if (_self.busySessions.isNotEmpty) return true;
    if (_paLocalWorkBusy(profile) != false) return true;
    return false;
  }

  bool get _paSetupJobRunning {
    try {
      return PhoneSetup.engineIfCreated?.progress.value.state ==
          SetupState.running;
    } catch (_) {
      return false;
    }
  }

  /// Starts the next owed update that may start now. Called after every row
  /// scan and by a short timer while one waits for work to end.
  Future<void> _paConsiderAutoUpdate() async {
    if (_paAutoRunning || _self._disposed || !phoneAgentsAvailable) return;
    _paAutoRunning = true;
    try {
      while (!_self._disposed && phoneAgentsAvailable) {
        final owed = [
          for (final row in _paRows)
            if (phoneAgentOwesUpdate(row) &&
                !_paAutoAttempted.contains(_paAutoKey(row.id)))
              row,
        ];
        if (owed.isEmpty) {
          _paAutoRetry?.cancel();
          _paAutoRetry = null;
          return;
        }
        final row = owed.first;
        final key = _paAutoKey(row.id);
        final network = await autoUpdateNetworkReader();
        final wifiOk = autoUpdateNetworkAllowed(
          downloadBytes: _paAutoDownloadBytes(row.id),
          network: network,
        );
        if (wifiOk) {
          _paAutoWifiWaiting.remove(row.id);
        } else if (_paAutoWifiWaiting.add(row.id)) {
          _self._notifyListeners();
        }
        if (!shouldAutoUpdateAgent(
          owesUpdate: true,
          busy: _paAutoBusy,
          checkRunning: _paChecking,
          setupJobRunning: _paSetupJobRunning,
          attemptedThisRun: false,
          networkAllowed: wifiOk,
        )) {
          // Look again when work may have ended or Wi-Fi came: no polling
          // unless one waits.
          _paAutoRetry ??= Timer(const Duration(seconds: 20), () {
            _paAutoRetry = null;
            if (!_self._disposed) {
              unawaited(_paConsiderAutoUpdate().catchError((Object _) {}));
            }
          });
          return;
        }
        _paAutoAttempted.add(key);
        _paAutoUpdating = row.id;
        _self._notifyListeners();
        final host = _paHost;
        double? fraction;
        StreamSubscription<AgentSetupProgress>? progress;
        if (host != null) {
          progress = host.setupChanges.listen((p) {
            if (p.agentId != row.id) return;
            fraction = p.fraction ?? fraction;
            _paPublishBlock(row.id, fraction: fraction);
          });
        }
        _paPublishBlock(row.id);
        try {
          await _runAgentPhoneCheck(row.id, automatic: true);
        } catch (_) {
          // The check's own result carries a failure; anything else is left
          // to the row's Check.
        } finally {
          await progress?.cancel();
          _paAutoUpdating = null;
          agentUpdateBlock.value = null;
          if (host is PhoneAgentAutoUpdatePort &&
              (host as PhoneAgentAutoUpdatePort).lastAutoUpdateYielded) {
            // Set aside for the person's work, not failed: it tries again
            // when nothing is running.
            _paAutoAttempted.remove(key);
            _paAutoRetry ??= Timer(const Duration(seconds: 20), () {
              _paAutoRetry = null;
              if (!_self._disposed) {
                unawaited(_paConsiderAutoUpdate().catchError((Object _) {}));
              }
            });
          }
          if (!_self._disposed) _self._notifyListeners();
        }
        if (_paAutoRetry != null) return;
      }
    } finally {
      _paAutoRunning = false;
    }
  }
}
