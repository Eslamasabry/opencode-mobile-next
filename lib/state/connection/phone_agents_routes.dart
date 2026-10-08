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

  bool _paCanRemoveAgent(String id) =>
      phoneAgentsAvailable &&
      _paHost is PhoneAgentRemovalPort &&
      _removableAgents.contains(id) &&
      {
        PhoneAgentStatus.stoppedInBackground,
        PhoneAgentStatus.signedOut,
        PhoneAgentStatus.limitReached,
        PhoneAgentStatus.ready,
        PhoneAgentStatus.needsQualification,
      }.contains(_paRowFor(id)?.status);

  Future<void> _paRemoveAgent(String id) async {
    if (!_removableAgents.contains(id) || !phoneAgentsAvailable) {
      throw const ProductException('This agent cannot be removed here.');
    }
    if (_paRemovingAgent != null) {
      throw const ProductException(
        'An agent is being removed. Wait for it to finish and try again.',
      );
    }
    final host = _paEnsureHost();
    if (host is! PhoneAgentRemovalPort) {
      throw const ProductException(
        'Removal is not available on this phone yet. Update the app and try again.',
      );
    }
    final owner = _paHostProfile;
    _paRemovingAgent = id;
    _self._notifyListeners();
    try {
      await (host as PhoneAgentRemovalPort).removeAgent(id);
      if (_self._disposed || _paHost != host || _paHostProfile != owner) return;
      _paChecks.remove(id);
      await refreshAgentRows();
    } on AgentHostException catch (error) {
      throw ProductException(
        error.reason == AgentHostFailure.busy
            ? 'This agent is still in use. Finish its setup or conversation and try again.'
            : 'The agent could not be removed. Refresh Agents and try again.',
      );
    } catch (_) {
      throw const ProductException(
        'The agent could not be removed. Refresh Agents and try again.',
      );
    } finally {
      _paRemovingAgent = null;
      if (!_self._disposed) _self._notifyListeners();
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
