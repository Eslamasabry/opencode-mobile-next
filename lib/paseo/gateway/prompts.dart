part of '../gateway.dart';

extension _PaseoPrompts on PaseoGateway {
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
