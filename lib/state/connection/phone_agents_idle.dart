part of '../connection.dart';

// BB owns native idle admission and foreground server recovery. These hooks
// supply local work truth and restore only its recorded helper generation.
extension _PhoneAgentIdle on _ConnectionControllerPhoneAgents {
  static const _idleUnavailable = ProductException(
    'The agents could not reconnect. Open agent setup and try again.',
    cause: 'agent_idle_resume_unavailable',
  );
  static const _idleStale = ProductException(
    'The agents could not reconnect. Open agent setup and try again.',
    cause: 'idle_resume_stale',
  );

  String? _paRequestedOwner(String profileId) {
    final requested = _self.store.profiles
        .where((p) => p.id == profileId)
        .firstOrNull;
    if (requested == null ||
        !BuiltinLinux.managesServerUrl(requested.baseUrl)) {
      return null;
    }
    return _self.store.phoneAgentOwnerId(requested.id);
  }

  bool? _paLocalWorkBusy(String profileId) {
    final owner = _paRequestedOwner(profileId);
    if (_self._disposed ||
        owner == null ||
        owner != _paProfile?.id ||
        owner != _paHostProfile ||
        _paClosingOwners.containsKey(owner)) {
      return null;
    }
    if (_paIdleResume != null ||
        _paOrdinaryStarts.isNotEmpty ||
        _paRemovingAgent != null ||
        _paBackendLive?.busySessions.isNotEmpty == true) {
      return true;
    }
    final readings = [
      for (final gateway in _paBusySubscriptions.keys) gateway.localWorkBusy,
    ];
    if (readings.contains(true)) return true;
    if (_paHelperObserved == false &&
        _paBusySubscriptions.keys.every(
          (gateway) => !gateway.transport.connected,
        )) {
      return false;
    }
    if (readings.isNotEmpty && readings.every((reading) => reading == false)) {
      return false;
    }
    return null;
  }

  void _paWatchLocalWork(PaseoGateway gateway) {
    if (_paBusySubscriptions.containsKey(gateway)) return;
    final host = _paHost;
    late StreamSubscription<void> sub;
    void changed() {
      if (!_self._disposed && identical(_paHost, host)) {
        _self._notifyListeners();
      }
    }

    sub = gateway.localWorkChanges.listen(
      (_) => changed(),
      onDone: () {
        if (identical(_paBusySubscriptions[gateway], sub)) {
          _paBusySubscriptions.remove(gateway);
          changed();
        }
      },
    );
    _paBusySubscriptions[gateway] = sub;
  }

  Future<void> _paObserveHelper(PhoneAgentHostPort host, String? owner) async {
    bool? observed;
    if (host is PhoneAgentLivenessPort) {
      try {
        observed = await (host as PhoneAgentLivenessPort)
            .helperRunning()
            .timeout(const Duration(seconds: 8));
      } catch (_) {}
    }
    if (!_self._disposed &&
        identical(_paHost, host) &&
        _paHostProfile == owner) {
      _paHelperObserved = observed;
    }
  }

  bool _paIdleCurrent(
    PhoneAgentHostPort host,
    String owner,
    int epoch,
    int generation,
  ) =>
      !_self._disposed &&
      !_self._lifecycleWasBackgrounded &&
      _paIdleLifecycleEpoch == epoch &&
      _self._generation == generation &&
      identical(_paHost, host) &&
      _paHostProfile == owner &&
      _paProfile?.id == owner &&
      !_paClosingOwners.containsKey(owner);

  Future<void> _paResumeAfterIdle(
    String profileId,
    int expectedIdleGeneration,
  ) {
    final owner = _paRequestedOwner(profileId);
    if (_self._disposed ||
        _self._lifecycleWasBackgrounded ||
        expectedIdleGeneration < 0 ||
        owner == null ||
        owner != _paProfile?.id) {
      return Future.error(_idleStale);
    }
    final pending = _paIdleResume;
    if (pending != null) {
      return _paIdleOwner == owner &&
              _paIdleGeneration == expectedIdleGeneration
          ? pending
          : Future.error(_idleStale);
    }
    final PhoneAgentHostPort host;
    try {
      host = _paEnsureHost();
    } catch (_) {
      return Future.error(_idleUnavailable);
    }
    if (host is! PhoneAgentIdleHostPort) return Future.error(_idleUnavailable);
    final epoch = _paIdleLifecycleEpoch;
    final generation = _self._generation;
    bool current() => _paIdleCurrent(host, owner, epoch, generation);
    final token = Object();
    _paIdleToken = token;
    _paIdleOwner = owner;
    _paIdleGeneration = expectedIdleGeneration;
    _paHelperObserved = null;
    late Future<void> operation;
    operation = () async {
      try {
        final port = host as PhoneAgentIdleHostPort;
        final state = await port.idleState().timeout(
          const Duration(seconds: 8),
        );
        if (!current()) throw _idleStale;
        if (state.supported != true ||
            state.generation != expectedIdleGeneration ||
            state.idleStopped == null ||
            state.serverRestartWanted != true ||
            state.serverRunning != true ||
            state.helperStopped == null) {
          throw _idleStale;
        }
        // A prior-stopped helper is never bootstrapped by foreground return.
        if (state.helperStopped == false) return;
        Future<void> authorize() async {
          final receipt = await port.idleState().timeout(
            const Duration(seconds: 8),
          );
          if (!current() ||
              receipt.supported != true ||
              receipt.generation != expectedIdleGeneration ||
              receipt.idleStopped == null ||
              receipt.helperStopped == null ||
              receipt.serverRestartWanted != true ||
              receipt.serverRunning != true) {
            throw _idleStale;
          }
        }

        await port.resumeAfterIdle(
          expectedIdleGeneration: expectedIdleGeneration,
          stillCurrent: current,
        );
        if (!current()) throw _idleStale;
        await authorize();
        _paHostRunning = true;
        _paHelperObserved = true;
        // Cold controllers have no conversation backend. Build/reconcile feeds
        // from retained directories without opening or resuming an agent chat.
        await _paSyncSources(assumeRunning: true);
        if (!current()) throw _idleStale;
        await refreshAgentRows();
        if (!current()) throw _idleStale;
        final backend = _paBackendLive;
        if (backend != null && !backend.isConnected) {
          await backend.retryConnection();
          if (!current()) throw _idleStale;
        }
        await refreshChatFeed();
        if (!current()) throw _idleStale;
        await authorize();
      } on AgentHostException catch (error) {
        if (identical(_paIdleToken, token)) {
          _paHelperObserved = null;
          _paHostRunning = false;
        }
        throw error.reason == AgentHostFailure.stale
            ? _idleStale
            : _idleUnavailable;
      } on ProductException {
        if (identical(_paIdleToken, token)) {
          _paHelperObserved = null;
          _paHostRunning = false;
        }
        rethrow;
      } catch (_) {
        if (identical(_paIdleToken, token)) {
          _paHelperObserved = null;
          _paHostRunning = false;
        }
        throw _idleUnavailable;
      } finally {
        if (identical(_paIdleToken, token)) {
          _paIdleToken = null;
          _paIdleResume = null;
          _paIdleOwner = null;
          _paIdleGeneration = null;
          if (!_self._disposed) _self._notifyListeners();
        }
      }
    }();
    _paIdleResume = operation;
    _self._notifyListeners();
    return operation;
  }

  Future<void> _paStartOrdinaryHost(
    PhoneAgentHostPort host, {
    required bool automatic,
  }) async {
    final owner = _paHostProfile;
    final epoch = _paIdleLifecycleEpoch;
    final generation = _self._generation;
    bool current() =>
        owner != null && _paIdleCurrent(host, owner, epoch, generation);
    if (!current()) throw _idleStale;
    if (host is PhoneAgentIdleHostPort) {
      final state = await (host as PhoneAgentIdleHostPort).idleState().timeout(
        const Duration(seconds: 8),
      );
      if (!current()) throw _idleStale;
      final explicitAllowed =
          state.supported == false ||
          state.legacyUnsupported ||
          (state.supported == true &&
              state.idleStopped == false &&
              state.helperStopped == false &&
              state.generation != null &&
              state.serverRestartWanted == true &&
              state.serverRunning == true);
      if (!(automatic ? state.automaticStartAllowed : explicitAllowed)) {
        throw _idleStale;
      }
    }
    if (!current()) throw _idleStale;
    final token = Object();
    _paOrdinaryStarts.add(token);
    _paHelperObserved = null;
    _self._notifyListeners();
    try {
      if (host is PhoneAgentGuardedStartPort) {
        await (host as PhoneAgentGuardedStartPort).startWhileCurrent(
          stillCurrent: current,
        );
      } else {
        await host.start();
      }
      if (!current()) throw _idleStale;
    } finally {
      _paOrdinaryStarts.remove(token);
      if (!_self._disposed) _self._notifyListeners();
    }
  }

  Future<void> _paResumeOrdinaryHost({required bool automatic}) async {
    final host = _paEnsureHost();
    await _paStartOrdinaryHost(host, automatic: automatic);
    await refreshAgentRows();
    if (_self._disposed) return;
    unawaited(refreshChatFeed().catchError((Object _) {}));
  }

  void _paClearIdleOwner() {
    ++_paIdleLifecycleEpoch;
    _paIdleToken = null;
    _paIdleResume = null;
    _paIdleOwner = null;
    _paIdleGeneration = null;
    _paHelperObserved = null;
    _paOrdinaryStarts.clear();
    for (final sub in _paBusySubscriptions.values) {
      unawaited(sub.cancel());
    }
    _paBusySubscriptions.clear();
  }
}
