part of '../gateway.dart';

extension _PaseoPrompts on PaseoGateway {
  /// Whether the daemon holds [agent] in memory with its runtime session
  /// open: only a loaded agent's snapshot names its `activeTurn` (a turn or
  /// null); a saved record the daemon has not loaded has none, whatever its
  /// last status says (session.js listAgentPayloads, agent-projections.js,
  /// Paseo 0.11.2). A message to a held agent continues that live session,
  /// so it needs no restoration proof, whichever gateway created it: the
  /// chat opens on its own gateway, not the list's that created or resumed
  /// it, and without this every follow-up to an ACP agent (Kilo, Gemini)
  /// was refused as an old conversation before reaching the daemon.
  bool _daemonHolds(Map<String, dynamic> agent) =>
      agent.containsKey('activeTurn') &&
      const {'initializing', 'running', 'idle'}.contains(agent['status']);

  /// Reading a timeline loads the agent in the helper under its own id
  /// (session.js handleFetchAgentTimelineRequest → ensureAgentLoaded, Paseo
  /// 0.11.2), and the answer carries that loaded snapshot. Kept, so a saved
  /// conversation opened from the list continues in place: the explicit
  /// `resume_agent_request` would mint a new agent id (it resumes without
  /// one), and the chat stayed bound to the old id while the new agent ran
  /// and failed out of sight (emulator, 2026-10-27).
  void _noteLoadedAgent(String id, Map<String, dynamic> timeline) {
    final loaded = timeline['agent'];
    if (loaded is! Map<String, dynamic>) return;
    if (loaded['id'] != _real(id) || !_daemonHolds(loaded)) return;
    if (_agents[_app(id)]?['status'] == 'error') {
      _live.putIfAbsent(_app(id), _PaseoLive.new).failedBeforeLoad = true;
    }
    _remember(loaded);
  }

  /// What the helper says about [id]'s last turn (Paseo 0.11.2). Its
  /// `lastError` comes with the agent only while the helper still holds the
  /// failure: a saved record's payload leaves it out and loading the agent
  /// again starts without it (agent-projections.js buildStoredAgentPayload,
  /// agent-loading.js). What outlives a restart is the record's status
  /// "error" (until the helper loads it: [_PaseoLive.failedBeforeLoad]) and
  /// an `error` attention, which Paseo sets only when none is unread; a later
  /// turn that worked leaves it too, so the caller asks only about a newest
  /// turn that got no reply.
  AgentLastTurn _lastAgentTurn(String id) {
    final agent = _agents[_app(id)];
    if (agent == null) {
      return (failed: false, reason: null, sameConversation: const <String>[]);
    }
    final error = agent['lastError'];
    final provider = agent['provider'];
    final model = agent['model'];
    final reason = error is String && error.trim().isNotEmpty
        ? AgentRunFailure(
            reason: paseoText(error, max: 2000),
            provider: provider is String ? provider : null,
            model: model is String && model != paseoDefaultModel ? model : null,
          )
        : null;
    final failed =
        reason != null ||
        agent['status'] == 'error' ||
        _live[_app(id)]?.failedBeforeLoad == true ||
        (agent['requiresAttention'] == true &&
            agent['attentionReason'] == 'error');
    // Older app builds reopened with `resume_agent_request`, which forked the
    // agent: the forks share the runtime's session (the list shows one row).
    final key = sessionKey(_app(id));
    final forks = key == null
        ? const <String>[]
        : [
            for (final other in _agents.keys)
              if (other != _app(id) && sessionKey(other) == key) _real(other),
          ];
    return (failed: failed, reason: reason, sameConversation: forks);
  }

  /// A send that failed: the session no longer waits for a turn, an
  /// uncertain delivery is remembered, and a refusal the host explained (a
  /// `send_agent_message_response {accepted: false, error}`, a refused model
  /// change on the way, or a provider entry that can't start) is thrown again
  /// with its reason kept with the agent and model ([AgentRunFailure]).
  Never _sendFailed(
    String sessionID,
    Object error,
    StackTrace stack,
    ModelRef? model,
  ) {
    _awaitingTurn.remove(sessionID);
    if (error is PaseoFailure &&
        error.kind == PaseoFailureKind.deliveryUnknown) {
      _uncertain.add(sessionID);
    }
    final reason =
        error is PaseoFailure && error.kind == PaseoFailureKind.unavailable
        ? error.reason
        : null;
    if (reason == null || reason.isEmpty) {
      Error.throwWithStackTrace(error, stack);
    }
    final agent = _agents[sessionID];
    String? named(Object? value) =>
        value is String && value.isNotEmpty && value != paseoDefaultModel
        ? value
        : null;
    final runtime = agent?['runtimeInfo'];
    Error.throwWithStackTrace(
      PaseoFailure(
        PaseoFailureKind.unavailable,
        reason: reason,
        runFailure: AgentRunFailure(
          reason: reason,
          provider:
              named(agent?['provider']) ??
              named(model?.providerID) ??
              named(_draftProviders[sessionID]),
          model:
              named(model?.modelID) ??
              named(agent?['model']) ??
              named(runtime is Map ? runtime['model'] : null) ??
              named(_draftModels[sessionID]?.modelID),
        ),
      ),
      stack,
    );
  }

  Future<void> _createAgent(
    String id, {
    required String prompt,
    required String messageID,
    ModelRef? model,
    String? mode,
    String? variant,
    List<Map<String, String>> images = const [],
    void Function()? beforeSend,
  }) async {
    final browserRequested = _browserRequestedFor(id);
    final browserRevision = _browserRevision(id);
    if (browserRequested) _requireBrowserScope();
    final scope = _scope;
    final locationEpoch = _locationEpoch;
    final provider = model == null || model.providerID.isEmpty
        ? (_draftProviders[id] ?? paseoDefaultProvider)
        : model.providerID;
    if (browserRequested && provider != 'claude') throw _browserUnavailable;
    await _requireProviderAvailable(provider);
    _checkLocation(scope, locationEpoch);
    final modes = _modesFor(provider);
    mode ??= defaultProviderModes[provider];
    // A provider whose snapshot lists modes must list the required one. Paseo
    // 0.9.2 can list none for Claude on a phone; the required mode is then
    // still sent by name, and the daemon refuses it if it doesn't know it.
    if (defaultProviderModes.containsKey(provider) &&
        (mode == null || (modes.isNotEmpty && !modes.contains(mode)))) {
      throw PaseoFailure(PaseoFailureKind.unavailable);
    }
    final sendMode =
        mode != null &&
        (modes.contains(mode) ||
            (modes.isEmpty && defaultProviderModes[provider] == mode));
    final draft = _sessions[id];
    final title = draft?.title;
    final Map<String, dynamic> result;
    _creating++;
    final sentAt = DateTime.now().millisecondsSinceEpoch;
    try {
      result = await transport.request(
        'create_agent_request',
        {
          'config': {
            'provider': provider,
            'cwd': _scope,
            if (model != null &&
                model.modelID.isNotEmpty &&
                model.modelID != paseoDefaultModel)
              'model': model.modelID,
            if (sendMode) 'modeId': mode,
            if (variant != null && variant.isNotEmpty)
              'thinkingOptionId': variant,
            if (_draftFeatureValues[id]?.isNotEmpty == true)
              'featureValues': _draftFeatureValues[id],
            if (title != null && title != 'New conversation') 'title': title,
          },
          if (!browserRequested) 'initialPrompt': prompt,
          if (!browserRequested && images.isNotEmpty) 'images': images,
          if (!browserRequested) 'clientMessageId': messageID,
          'labels': <String, String>{},
        },
        mutation: true,
        timeout: const Duration(seconds: 90),
        beforeSend: () {
          _checkLocation(scope, locationEpoch);
          if (_browserRequestedFor(id) != browserRequested ||
              (browserRequested && _browserRevision(id) != browserRevision)) {
            throw _browserUnavailable;
          }
          if (!browserRequested) beforeSend?.call();
        },
      );
    } on PaseoFailure catch (error) {
      if (error.kind == PaseoFailureKind.deliveryUnknown && !browserRequested) {
        final start = (
          messageID: messageID,
          provider: provider,
          sentAt: sentAt,
        );
        _noteUncertainStart(id, start);
      }
      rethrow;
    } finally {
      _creating--;
      if (_creating == 0 && _closed) _heldEvents.clear();
    }
    final agent = paseoObject(result['agent']);
    _checkLocation(scope, locationEpoch);
    if (agent['provider'] != provider) {
      throw PaseoFailure(PaseoFailureKind.invalidResponse);
    }
    final realID = paseoString(agent['id'], max: 256);
    if (browserRequested &&
        (_browserRevision(id) != browserRevision || !_drafts.contains(id))) {
      // The cold session was created by this request but never received a turn.
      // Retire only its validated daemon ID; never resurrect the removed draft.
      if (agent['cwd'] == scope) {
        try {
          await transport.request('archive_agent_request', {
            'agentId': realID,
          }, mutation: true);
        } catch (_) {}
      }
      throw _browserUnavailable;
    }
    _moveBrowserRequest(id, realID);
    _realIDs[id] = realID;
    _appIDs[realID] = id;
    if (_remember(agent) == null) {
      throw PaseoFailure(PaseoFailureKind.scopeMismatch);
    }
    if (agent['status'] != 'error' && agent['status'] != 'closed') {
      _liveAgentSessions.add(id);
    }
    _drafts.remove(id);
    _draftProviders.remove(id);
    _draftModels.remove(id);
    if (_creating == 0) {
      final held = _heldEvents.toList();
      _heldEvents.clear();
      held.forEach(_onEvent);
    }
    if (browserRequested) {
      final reservation = await _beforeBrowserLaunch(id, requiredBrowser: true);
      _checkLocation(scope, locationEpoch);
      await transport.request(
        'send_agent_message_request',
        {
          'agentId': realID,
          'text': prompt,
          'messageId': messageID,
          if (images.isNotEmpty) 'images': images,
        },
        mutation: true,
        timeout: const Duration(seconds: 60),
        beforeSend: () {
          _checkLocation(scope, locationEpoch);
          _checkBrowserLaunch(id, reservation);
          beforeSend?.call();
        },
      );
    }

    if (_creating == 0) {
      final held = _heldEvents.toList();
      _heldEvents.clear();
      held.forEach(_onEvent);
    }
  }

  /// Applies a changed mode or model before a follow-up turn. A selection the
  /// agent's runtime does not offer is ignored rather than refused: the
  /// composer's choices are global while modes and models are per runtime.
  Future<void> _applySelection(
    String id, {
    ModelRef? model,
    String? mode,
    String? variant,
  }) async {
    final scope = _scope, epoch = _locationEpoch;
    final agent = _agents[id];
    if (agent == null) return;
    final reservation = await _beforeBrowserLaunch(id);
    final available = agent['availableModes'];
    final modeIDs = available is List
        ? available.whereType<Map>().map((m) => m['id']).whereType<String>()
        : const <String>[];
    if (mode != null &&
        mode != agent['currentModeId'] &&
        modeIDs.contains(mode)) {
      await transport.request(
        'set_agent_mode_request',
        {'agentId': _real(id), 'modeId': mode},
        mutation: true,
        beforeSend: () => _checkBrowserLaunch(id, reservation),
      );
      agent['currentModeId'] = mode;
    }
    if (model != null &&
        model.providerID == agent['provider'] &&
        model.modelID.isNotEmpty &&
        model.modelID != paseoDefaultModel &&
        model.modelID != agent['model']) {
      await transport.request(
        'set_agent_model_request',
        {'agentId': _real(id), 'modelId': model.modelID},
        mutation: true,
        beforeSend: () => _checkBrowserLaunch(id, reservation),
      );
      agent['model'] = model.modelID;
    }
    _checkLocation(scope, epoch);
    if (variant != null) {
      await _setThinking(id, agent, variant, reservation);
      _checkLocation(scope, epoch);
      _remember(agent);
    }
  }

  Future<void> _setThinking(
    String id,
    Map<String, dynamic> agent,
    String variant,
    BrowserLaunchReservation? reservation,
  ) async {
    final scope = _scope, epoch = _locationEpoch;
    final thinking = variant.isEmpty ? null : variant;
    if (thinking == null && agent['thinkingOptionId'] == null) return;
    await transport.request(
      'set_agent_thinking_request',
      {'agentId': _real(id), 'thinkingOptionId': thinking},
      mutation: true,
      beforeSend: () {
        _checkLocation(scope, epoch);
        _checkBrowserLaunch(id, reservation);
      },
    );
    _checkLocation(scope, epoch);
    agent['thinkingOptionId'] = thinking;
  }
}
