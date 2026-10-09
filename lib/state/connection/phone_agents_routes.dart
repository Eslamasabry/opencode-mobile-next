part of '../connection.dart';

// Opening and replacing feed rows retains their captured source and directory.
extension _PhoneAgentRoutes on _ConnectionControllerPhoneAgents {
  static const _removableAgents = {
    'codex',
    'gemini',
    'qwen',
    'goose',
    'omp-acp',
    'fx',
  };
  static const _signInStopFailed = ProductException(
    'Sign-in could not be stopped. Keep the app open and try again.',
  );
  static const _removalBusy = ProductException(
    'This agent is in use. Finish its work and try again.',
  );
  static const _removalUnsupported = ProductException(
    "This agent can't be removed here.",
  );
  static const _removalUnconfirmed = ProductException(
    "Couldn't confirm this agent was removed. Check this phone and try again.",
  );

  Future<void> _paCloseOwned(String? owner, {required bool stopHost}) async {
    if (owner == null) return;
    final scope = _paClosingOwners[owner] ?? _paCaptureClose(owner);
    if (scope == null) return;
    await scope.close(stopHost: stopHost);
    if (identical(_paClosingOwners[owner], scope)) {
      _paClosingOwners.remove(owner);
    }
  }

  /// Detach only this generation, before a terminal drain can suspend us.
  /// Failed drains retain this snapshot for retry rather than losing the PTY.
  _PhoneAgentCloseScope? _paCaptureClose(String owner) {
    if (_paHostProfile != null && _paHostProfile != owner) return null;
    _paClearIdleOwner();
    final host = _paHost;
    final binding = _paForegroundBinding;
    final backend = _paBackend;
    final setupSub = _paSetupSub;
    final merged = _paMerged;
    final mergedSub = _paMergedSub;
    final signIns = _paSignIns.values.toList();
    final signInSubs = _paSignInSubs.values.toList();
    final sources = _paSources.values.toList();
    final directories = _paSources.keys.toList();
    // Feed sockets and local subscriptions do not own the sign-in PTY. Stop
    // their listeners now so no heartbeat/retry survives widget disposal.
    // gateway.close starts browser revocation before closing its transport.
    final sourceDisposals = <Future<void>>[];
    for (final entry in sources) {
      sourceDisposals.add(entry.source.dispose());
      entry.gateway.close();
    }
    final mergedDisposal = merged?.dispose();
    final mergedCancellation = mergedSub?.cancel();
    final setupCancellation = setupSub?.cancel();
    final signInCancellations = [for (final sub in signInSubs) sub.cancel()];
    final scope = _PhoneAgentCloseScope(
      binding: binding,
      host: host,
      revokeBrowser: () =>
          _self._browserLaunches.revokeProfile(profileId: owner),
      beforeHost: [
        if (backend != null) () => backend._modelLibraryWrite,
        for (final cancellation in signInCancellations) () => cancellation,
        for (final session in signIns)
          () async {
            try {
              await session.close();
            } catch (_) {
              // Native removal also drains the owner's account processes.
            }
          },
        if (setupCancellation != null) () => setupCancellation,
      ],
      afterHost: [
        if (mergedCancellation != null) () => mergedCancellation,
        if (mergedDisposal != null) () => mergedDisposal,
        for (final disposal in sourceDisposals) () => disposal,
        for (final entry in sources)
          () async {
            await entry.gateway.revokeBrowserClaudeLaunches();
          },
      ],
    );
    _paClosingOwners[owner] = scope;
    _paForegroundBinding = null;
    _paHost = null;
    _paHostProfile = null;
    _paSetupSub = null;
    _paSignIns.clear();
    _paSignInSubs.clear();
    _paMerged = null;
    _paMergedSub = null;
    _paSources.clear();
    _paBackendWatch?.cancel();
    _paBackendWatch = null;
    _paListRefresh?.cancel();
    _paListRefresh = null;
    backend?.removeListener(_paBackendChanged);
    // This secondary controller owns only transport/listeners; its service
    // port is borrowed. Retire it now without releasing the main PTY lease.
    if (backend != null && !backend._disposed) backend.dispose();
    _paBackend = null;
    _paBackendHost = null;
    _paOwners.clear();
    _paOpenCodeOpened.clear();
    _paHoldTimer?.cancel();
    _paHoldTimer = null;
    _paReadingTimer?.cancel();
    _paReadingTimer = null;
    _paSyncTimer?.cancel();
    _paSyncTimer = null;
    _paSignInRecheck?.cancel();
    _paSignInRecheck = null;
    _paRemovalToken = null;
    _paRemovingAgent = null;
    _paLive.clear();
    _paAuthResults.clear();
    _paAuthRevisions.clear();
    _paChecks.clear();
    _paRows = const [];
    _paHostRunning = false;
    // The folder helper addresses the current profile. An older owner's
    // captured feeds close below without touching the replacement's cards.
    if (_paProfile?.id == owner) {
      for (final directory in directories) {
        _self._genUiDropPhone(directory);
      }
    }
    return scope;
  }

  void _paShutdownOwned() {
    final current = _paCloseAll(stopHost: false);
    final pending = _paClosingOwners.keys.toList();
    final browser = _self._browserLaunches;
    final ownsBrowser = _self._ownsBrowserLaunches;
    unawaited(() async {
      try {
        await current;
        for (final owner in pending) {
          await _paCloseOwned(owner, stopHost: false);
        }
        if (ownsBrowser) await browser.close();
      } catch (_) {
        // Keep failed owner drains captured. Disposal never exposes PTY text.
      }
    }());
  }

  bool _paCanRemoveAgent(String id) {
    if (_self._disposed ||
        !phoneAgentsAvailable ||
        _paHost is! PhoneAgentRemovalPort ||
        _paRemovingAgent != null ||
        !_removableAgents.contains(id)) {
      return false;
    }
    final row = _paRowFor(id);
    if (row == null || !row.setupVisible) return false;
    if ({
      PhoneAgentStatus.stoppedInBackground,
      PhoneAgentStatus.signedOut,
      PhoneAgentStatus.limitReached,
      PhoneAgentStatus.ready,
      PhoneAgentStatus.needsQualification,
    }.contains(row.status)) {
      return true;
    }
    // These unavailable rows still describe a known installed payload.
    if (row.status == PhoneAgentStatus.unavailable &&
        {
          PhoneAgentHiddenReason.hostUnavailable,
          PhoneAgentHiddenReason.runtimeUnknown,
          PhoneAgentHiddenReason.signedOut,
        }.contains(row.hiddenReason)) {
      return true;
    }
    final progress = agentSetupProgress;
    return progress.agentId == id &&
        {
          AgentSetupPhase.failed,
          AgentSetupPhase.interrupted,
        }.contains(progress.phase);
  }

  Future<void> _paInstallAgent(String id) async {
    if (_paRemovingAgent != null) throw _removalBusy;
    final host = _paEnsureHost();
    await host.install(id);
    if (!_self._disposed) _self._notifyListeners();
  }

  Future<AgentRemovalResult> _paRemoveAgent(String id) async {
    if (_self._disposed ||
        !_removableAgents.contains(id) ||
        !phoneAgentsAvailable) {
      throw _removalUnsupported;
    }
    if (_paRemovingAgent != null) throw _removalBusy;
    final PhoneAgentHostPort host;
    try {
      host = _paEnsureHost();
    } catch (_) {
      throw _removalUnconfirmed;
    }
    if (host is! PhoneAgentRemovalPort) throw _removalUnsupported;
    final owner = _paHostProfile;
    final token = Object();
    bool current() =>
        !_self._disposed &&
        _paHost == host &&
        _paHostProfile == owner &&
        _paProfile?.id == owner &&
        identical(_paRemovalToken, token);
    _paRemovalToken = token;
    _paRemovingAgent = id;
    _self._notifyListeners();
    try {
      final result = await (host as PhoneAgentRemovalPort).removeAgent(id);
      if (!current() ||
          result.agentId != id ||
          result.freedBytes < 0 ||
          (result.alreadyAbsent && result.freedBytes != 0)) {
        throw _removalUnconfirmed;
      }
      // Drain an inspection that began before deletion, then read new truth.
      // Keep the target's accounts, chats and phone-check proof unchanged.
      await _paRefreshingRows;
      if (!current()) throw _removalUnconfirmed;
      await refreshAgentRows();
      if (!current()) throw _removalUnconfirmed;
      return result;
    } on AgentHostException catch (error) {
      throw error.reason == AgentHostFailure.busy
          ? _removalBusy
          : _removalUnconfirmed;
    } catch (_) {
      throw _removalUnconfirmed;
    } finally {
      if (identical(_paRemovalToken, token)) {
        _paRemovalToken = null;
        _paRemovingAgent = null;
        if (!_self._disposed) _self._notifyListeners();
      }
    }
  }

  Future<ChatFeedRoute> _paOpenChatFeedItem(ChatFeedItem item) async {
    // A saved row whose folder has not been read yet opens on the agent's
    // backend directly; it starts the helper when needed.
    final saved = _paSavedRowFor(item);
    if (saved != null) {
      final row = saved.item;
      final descriptor =
          _paCatalog.byId(row.agentId) ??
          _paCatalog.agents
              .where((agent) => agent.providerId == row.agentId)
              .firstOrNull;
      _paOwners[row.sessionID] = await _paBackendFor(
        row.directory,
        agentId: descriptor?.id,
        agentName: row.agentLabel ?? descriptor?.name,
      );
      return ChatFeedRoute(
        sourceId: row.sourceId!,
        sessionID: row.sessionID,
        directory: row.directory,
        agentId: row.agentId,
      );
    }
    // A saved row of a server still connecting opens on that server.
    if (_self._isSideSavedRow(item)) {
      final side = _self._sideForSource(item.sourceId)!;
      _paOwners[item.sessionID] = side;
      _paOpenCodeOpened.remove(item.sessionID);
      await side.selectLocationForExistingSession(directory: item.directory);
      return ChatFeedRoute(
        sourceId: item.sourceId!,
        sessionID: item.sessionID,
        directory: item.directory,
        agentId: item.agentId,
      );
    }
    final merged = _paMerged;
    if (merged == null) {
      if (item.sourceId != null && item.sourceId != _openCodeSourceId) {
        throw _ConnectionControllerPhoneAgents._gone;
      }
      _paOwners.remove(item.sessionID);
      _paOpenCodeOpened.add(item.sessionID);
      await _self.selectLocationForExistingSession(directory: item.directory);
      return ChatFeedRoute(
        sourceId: _openCodeSourceId,
        sessionID: item.sessionID,
        directory: item.directory,
        agentId: item.agentId,
      );
    }
    final current = merged
        .chatFeed(const ChatFeedFilter(includeSubagents: true))
        .items
        .where((row) => row.identity == item.identity)
        .toList();
    if (current.length != 1) throw _ConnectionControllerPhoneAgents._gone;
    var route = merged.routeFor(current.single);
    if (route.sourceId.startsWith('paseo:')) {
      final row = current.single;
      final source = merged.sourceFor(route);
      if (source is PaseoChatFeedSource &&
          !source.gateway.isAgentLoaded(row.sessionID) &&
          source.gateway.canResumeAgent(row.sessionID)) {
        try {
          final id = await source.gateway.resumeHostAgentChat(row.sessionID);
          _paLive.add(row.identity);
          route = ChatFeedRoute(
            sourceId: route.sourceId,
            sessionID: id,
            directory: route.directory,
            agentId: route.agentId,
          );
        } catch (_) {
          _paResumeFailed.add(row.identity);
          if (!_self._disposed) _self._notifyListeners();
          throw ProductException(
            '${row.agentLabel ?? 'The agent'} could not reopen this conversation. '
            'Tap it again to start a new one from it.',
          );
        }
      }
      if (source is PaseoChatFeedSource) {
        // The feed retains a draft's local ID after its first prompt creates
        // the daemon agent. The independent chat gateway only knows the
        // daemon ID, both when attaching live and after an explicit resume.
        route = ChatFeedRoute(
          sourceId: route.sourceId,
          sessionID: source.gateway.daemonSessionId(row.sessionID),
          directory: route.directory,
          agentId: route.agentId,
        );
      }
      final descriptor =
          _paCatalog.byId(row.agentId) ??
          _paCatalog.agents
              .where((agent) => agent.providerId == row.agentId)
              .firstOrNull;
      // The conversation's own header says what its row says.
      _paTitleHints[route.sessionID] = row.title;
      final backend = await _paBackendFor(
        route.directory,
        agentId: descriptor?.id,
        agentName: row.agentLabel ?? descriptor?.name,
      );
      _paOwners[route.sessionID] = backend;
      final api = backend.api;
      if (api is PaseoGateway) api.keepTitle(route.sessionID, row.title);
    } else if (_self._sideForSource(route.sourceId) case final side?) {
      // Another server's conversation opens on that server's own
      // connection; this one stays where it is.
      _paOwners[route.sessionID] = side;
      _paOpenCodeOpened.remove(route.sessionID);
      await side.selectLocationForExistingSession(directory: route.directory);
    } else {
      // The page is found by id alone: this one is OpenCode's again.
      _paOwners.remove(route.sessionID);
      _paOpenCodeOpened.add(route.sessionID);
      await _self.selectLocationForExistingSession(directory: route.directory);
    }
    return route;
  }

  AgentResumeNotice _paAgentResumeNotice(ChatFeedItem item) {
    if (item.agentId == openCodeChatAgentId ||
        (item.sourceId ?? _openCodeSourceId) == _openCodeSourceId) {
      return const AgentResumeNotice(canReopen: true);
    }
    if (_paLive.contains(item.identity)) {
      return const AgentResumeNotice(canReopen: true);
    }
    // Not read live yet: what it was when saved, so the label doesn't flash.
    final saved = _paSavedRowFor(item);
    if (saved != null && saved.canReopen) {
      return const AgentResumeNotice(canReopen: true);
    }
    // Still loaded in the helper (running or idle since it last started):
    // opening it shows the live conversation, whoever started it. One the
    // helper no longer holds reopens through the runtime's own resume,
    // unless that already failed here.
    for (final entry in _paSources.entries) {
      if (_paseoSourceId(entry.key) != item.sourceId) continue;
      final gateway = entry.value.gateway;
      if (gateway.isAgentLoaded(item.sessionID) ||
          (gateway.canResumeAgent(item.sessionID) &&
              !_paResumeFailed.contains(item.identity))) {
        return const AgentResumeNotice(canReopen: true);
      }
    }
    // The agent can resume in general, but this conversation has nothing to
    // resume by (no session handle, or the helper refused): a new one.
    return const AgentResumeNotice(
      canReopen: false,
      label: agentResumeUnverifiedLabel,
      note: agentStartsNewChatNote,
      requiresAcknowledgement: true,
    );
  }

  Future<String> _paStartNewChatReplacing(
    ChatFeedItem old, {
    required bool newChatAcknowledged,
  }) async {
    if (!newChatAcknowledged) {
      throw const ProductException('Choose Start new chat to continue.');
    }
    final merged = _paMerged;
    if (merged == null) throw _ConnectionControllerPhoneAgents._gone;
    final current = merged
        .chatFeed(const ChatFeedFilter(includeSubagents: true))
        .items
        .where((row) => row.identity == old.identity)
        .toList();
    if (current.length != 1) throw _ConnectionControllerPhoneAgents._gone;
    final source = merged.sourceFor(merged.routeFor(current.single));
    if (source is! PaseoChatFeedSource) {
      throw _ConnectionControllerPhoneAgents._gone;
    }
    final id = await source.gateway.startNewHostAgentChat(
      old.sessionID,
      newChatAcknowledged: true,
    );
    _paLive.add(jsonEncode([old.sourceId, id, old.directory]));
    await source.refreshChatFeed();
    _paOwners[id] = await _paBackendFor(old.directory);
    return id;
  }
}

/// A teardown owns immutable resource references, never the live controller's
/// replacement fields. A failed drain retains native/auth cleanup for retry.
final class _PhoneAgentCloseScope {
  _PhoneAgentCloseScope({
    required this.binding,
    required this.host,
    required this.revokeBrowser,
    required this.beforeHost,
    required this.afterHost,
  });

  final AgentSignInForegroundBinding? binding;
  final PhoneAgentHostPort? host;
  final Future<void> Function() revokeBrowser;
  final List<Future<void> Function()> beforeHost;
  final List<Future<void> Function()> afterHost;
  Future<void>? _closing;
  bool _stopHost = false;

  Future<void> close({required bool stopHost}) {
    _stopHost |= stopHost;
    return _closing ??= _close().whenComplete(() => _closing = null);
  }

  Future<void> _close() async {
    if (binding != null) {
      try {
        // This must be the first await: registered PTYs retain service
        // protection until they drain, before host/account-home teardown.
        await AgentSignInForegroundRegistry.unbind(binding!);
      } catch (_) {
        throw _PhoneAgentRoutes._signInStopFailed;
      }
    }
    await revokeBrowser();
    for (final cleanup in beforeHost) {
      await cleanup();
    }
    if (host != null) {
      try {
        await host!.cancelInstall();
      } catch (_) {}
      if (_stopHost) {
        try {
          await host!.stop();
        } catch (_) {}
      }
      try {
        await host!.dispose();
      } catch (_) {}
    }
    for (final cleanup in afterHost) {
      await cleanup();
    }
  }
}
