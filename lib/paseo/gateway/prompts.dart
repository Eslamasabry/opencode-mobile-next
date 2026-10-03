part of '../gateway.dart';

extension _PaseoPrompts on PaseoGateway {
  Future<void> _createAgent(
    String id, {
    required String prompt,
    required String messageID,
    ModelRef? model,
    String? mode,
    String? variant,
  }) async {
    final scope = _scope;
    final locationEpoch = _locationEpoch;
    final provider = model == null || model.providerID.isEmpty
        ? (_draftProviders[id] ?? paseoDefaultProvider)
        : model.providerID;
    if (!isExistingPaseoProvider(provider)) {
      throw PaseoFailure(PaseoFailureKind.unavailable);
    }
    await _checkExistingProvider(provider);
    _checkLocation(scope, locationEpoch);
    final modes = _modesFor(provider);
    mode ??= defaultProviderModes[provider];
    if (defaultProviderModes.containsKey(provider) &&
        (mode == null || !modes.contains(mode))) {
      throw PaseoFailure(PaseoFailureKind.unavailable);
    }
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
            if (mode != null && modes.contains(mode)) 'modeId': mode,
            if (variant != null && variant.isNotEmpty)
              'thinkingOptionId': variant,
            if (title != null && title != 'New conversation') 'title': title,
          },
          'initialPrompt': prompt,
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
    final realID = paseoString(agent['id'], max: 256);
    _realIDs[id] = realID;
    _appIDs[realID] = id;
    _remember(agent);
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
