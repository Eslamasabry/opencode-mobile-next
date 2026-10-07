part of '../connection.dart';

// The agents' own connection (see one-backend-per-conversation): created on
// first use, kept connected while the helper runs, recovered when Android
// stops the helper, and closed with the phone agents.

extension _PhoneAgentBackend on _ConnectionControllerPhoneAgents {
  /// Connects this phone's agent backend at [directory] and returns it. The
  /// helper is started first when Android stopped it. The backend's
  /// transport is built from the host on every (re)connect, which also keeps
  /// the helper running.
  Future<ConnectionController> _paBackendFor(
    String directory, {
    String? agentName,
    String? agentId,
  }) async {
    if (agentId != null) _paBackendAgentId = agentId;
    final profile = _paProfile;
    if (profile == null) throw _ConnectionControllerPhoneAgents._notReady;
    final host = _paEnsureHost();
    // Proves the helper answers (starting it when stopped) and caches its
    // credential for the synchronous transport builds below.
    await _paReachSource(directory);
    var backend = _paBackend;
    if (backend == null || backend._disposed || _paBackendHost != host) {
      backend?.dispose();
      backend = ConnectionController.agentBackend(
        _self.store,
        paseoGatewayFactory: (target) {
          _paKeepHostUp();
          final gateway = host.newGatewaySync(target.codexDirectory);
          _paWireBrowserGateway(gateway, target.codexDirectory);
          for (final hint in _paTitleHints.entries) {
            gateway.keepTitle(hint.key, hint.value);
          }
          return (gateway: gateway, operations: gateway);
        },
        backgroundLive: _self.backgroundLive,
        diagnostics: _self.diagnostics,
        draftAttachmentVault: _self._draftAttachmentVault,
        promptPhotoStore: _self._promptPhotoStore,
      ).._agentBackendRecover = recoverPhoneAgentBackend;
      _paBackend = backend;
      final watched = backend;
      backend._turnStallProbe = () async {
        final agent = _paHostProbeAgent;
        bool? running;
        if (host is PhoneAgentLivenessPort) {
          try {
            running = await (host as PhoneAgentLivenessPort).helperRunning();
          } catch (_) {}
        } else if (agent != null) {
          try {
            running = (await host.inspect(agent)).hostAvailable;
          } catch (_) {}
        }
        if (running == false) {
          return TurnStallEvidence(
            transportConnected: watched.isConnected,
            helperRunning: false,
          );
        }
        bool? reachable;
        try {
          reachable = (await watched.api?.health())?.healthy;
        } catch (_) {
          reachable = false;
        }
        return TurnStallEvidence(
          transportConnected: watched.isConnected,
          helperRunning: running,
          endpointReachable: reachable,
        );
      };
      _paBackendHost = host;
      _paWatchBackend();
      backend.addListener(_paBackendChanged);
    }
    backend._genUiParent = _self._genUiPhoneController;
    final source = _paSources[directory];
    if (source != null) {
      _self._genUiPhoneController._genUiAttachPhone(
        GenUiScope(
          profileID: profile.id,
          sourceId: _paseoSourceId(directory),
          directory: directory,
        ),
        source.gateway,
        sessions: () =>
            source.source.chatFeed().items.map((row) => row.sessionID),
      );
    }
    final id = '${profile.id}$agentBackendProfileSuffix';
    await _carryApprovalChoices(_self.store.prefs, from: profile.id, to: id);
    final name = agentName ?? backend._connectedProfile?.name ?? 'Claude Code';
    final current = backend._connectedProfile;
    if (current == null || current.id != id) {
      await backend.connect(
        ServerProfile(
          id: id,
          name: name,
          baseUrl: 'ws://127.0.0.1:4099',
          backend: ServerBackend.paseo,
          codexDirectory: directory,
          transientTransport: true,
        ),
      );
    } else {
      current.name = name;
      if (backend.directory != directory) {
        await backend.selectLocationForExistingSession(directory: directory);
      } else if (!backend.isConnected) {
        await backend.retryConnection();
      }
    }
    return backend;
  }

  /// Brings the agent backend back after Android stopped the agent's helper:
  /// starts it, waits until it answers (it takes a few seconds to listen),
  /// then reconnects. The banner's Restart and the watchdog use it.
  Future<void> recoverPhoneAgentBackend() async {
    final backend = _paBackend;
    final host = _paHost;
    final directory = backend?.directory;
    if (backend == null || host == null || directory == null) return;
    if (_paRecovering) return;
    _paRecovering = true;
    try {
      // Only a stopped helper is started: starting replaces a running one.
      final agent = _paHostProbeAgent;
      final running =
          agent != null && (await host.inspect(agent)).hostAvailable;
      if (!running) {
        try {
          await host.start();
        } on AgentHostException catch (error) {
          if (error.reason != AgentHostFailure.busy) rethrow;
        }
      }
      final deadline = DateTime.now().add(const Duration(seconds: 30));
      while (DateTime.now().isBefore(deadline)) {
        try {
          final probe = await host.openGateway(directory);
          probe.close();
          break;
        } catch (_) {
          await Future<void>.delayed(const Duration(seconds: 1));
        }
      }
      if (_paBackend == backend && !backend._disposed) {
        await backend.retryConnection();
      }
    } catch (_) {
      // The banner stays; its Restart tries again.
    } finally {
      _paRecovering = false;
    }
  }

  /// While the agent backend exists, a lost connection is recovered without
  /// a tap: checked every 10 s, recovered when offline twice.
  void _paWatchBackend() {
    _paBackendWatch?.cancel();
    _paOfflineTicks = 0;
    _paBackendWatch = Timer.periodic(const Duration(seconds: 10), (_) {
      final backend = _paBackend;
      if (_self._disposed || backend == null || backend._disposed) {
        _paBackendWatch?.cancel();
        _paBackendWatch = null;
        return;
      }
      // Offline at two checks in a row (reconnect attempts alone don't
      // restart a stopped helper).
      if (backend.isConnected) {
        _paOfflineTicks = 0;
      } else if (++_paOfflineTicks >= 2) {
        _paOfflineTicks = 0;
        unawaited(recoverPhoneAgentBackend());
      }
    });
  }

  /// Every (re)connect of the agent backend: Android stops the helper in the
  /// background, and a reconnect alone can't bring it back. Checks it at
  /// most every 20 s and starts it when it isn't running; the next connect
  /// attempt then reaches it.
  void _paKeepHostUp() {
    final host = _paHost;
    if (host == null) return;
    final now = DateTime.now();
    final last = _paKeptUpAt;
    if (last != null && now.difference(last) < const Duration(seconds: 20)) {
      return;
    }
    _paKeptUpAt = now;
    final agent = _paHostProbeAgent;
    if (agent == null) return;
    unawaited(() async {
      try {
        final runtime = await host.inspect(agent);
        if (!runtime.hostAvailable && runtime.installed) await host.start();
      } catch (_) {
        // The next reconnect checks again.
      }
    }());
  }

  /// Something changed in an open agent conversation (a message, a finished
  /// turn, a reconnect): the conversations list reads the agents again, at
  /// most every two seconds, so its rows keep up with the conversation.
  void _paBackendChanged() {
    if (_paListRefresh?.isActive ?? false) return;
    _paListRefresh = Timer(const Duration(seconds: 2), () {
      if (_self._disposed) return;
      // The conversation's helper answers, so a folder the list never
      // reached (the helper was still starting) is connected now.
      if (_paSourcesMissing) {
        unawaited(
          _paSyncSources(assumeRunning: true).catchError((Object _) {}),
        );
        return;
      }
      unawaited(_paMerged?.refreshChatFeed() ?? Future<void>.value());
    });
  }

  /// A folder the list should read live has no connection yet.
  bool get _paSourcesMissing =>
      phoneAgentsAvailable &&
      _paDesiredDirectories()
          .take(_ConnectionControllerPhoneAgents._maxPaseoSources)
          .any((directory) => !_paSources.containsKey(directory));

  /// At app start on a phone whose agents had conversations: their helper
  /// is checked (and started when Android stopped it) beside the in-app
  /// server's own start, not after the app has connected to it. The helper
  /// takes about 20 s to boot.
  void _paWarmUp() {
    if (!phoneAgentsAvailable || !_paUsedBefore) return;
    scheduleMicrotask(() {
      if (_self._disposed) return;
      unawaited(refreshAgentRows().catchError((Object _) {}));
    });
  }

  void _paDisposeBackend() {
    _paBackendWatch?.cancel();
    _paBackendWatch = null;
    _paListRefresh?.cancel();
    _paListRefresh = null;
    _paBackend?.removeListener(_paBackendChanged);
    final backend = _paBackend;
    _paBackend = null;
    _paBackendHost = null;
    _paOwners.clear();
    _paOpenCodeOpened.clear();
    if (backend != null && !backend._disposed) backend.dispose();
  }
}
