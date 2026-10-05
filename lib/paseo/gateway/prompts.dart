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
  }) async {
    final scope = _scope;
    final locationEpoch = _locationEpoch;
    final provider = model == null || model.providerID.isEmpty
        ? (_draftProviders[id] ?? paseoDefaultProvider)
        : model.providerID;
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
            if (title != null && title != 'New conversation') 'title': title,
          },
          'initialPrompt': prompt,
          if (images.isNotEmpty) 'images': images,
          'clientMessageId': messageID,
          'labels': <String, String>{},
        },
        mutation: true,
        timeout: const Duration(seconds: 90),
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
  }) async {
    final agent = _agents[id];
    if (agent == null) return;
    final available = agent['availableModes'];
    final modeIDs = available is List
        ? available.whereType<Map>().map((m) => m['id']).whereType<String>()
        : const <String>[];
    if (mode != null &&
        mode != agent['currentModeId'] &&
        modeIDs.contains(mode)) {
      await transport.request('set_agent_mode_request', {
        'agentId': _real(id),
        'modeId': mode,
      }, mutation: true);
      agent['currentModeId'] = mode;
    }
    if (model != null &&
        model.providerID == agent['provider'] &&
        model.modelID.isNotEmpty &&
        model.modelID != paseoDefaultModel &&
        model.modelID != agent['model']) {
      await transport.request('set_agent_model_request', {
        'agentId': _real(id),
        'modelId': model.modelID,
      }, mutation: true);
      agent['model'] = model.modelID;
    }
  }
}
