part of '../gateway.dart';

extension _PaseoProviders on PaseoGateway {
  Future<void> _requireProviderAvailable(String id) async {
    if (!isPaseoProviderId(id)) {
      throw PaseoFailure(PaseoFailureKind.unavailable);
    }
    final matches = (await _providers()).where(
      (entry) => entry['provider'] == id,
    );
    if (matches.length != 1 || !paseoProviderCanStart(matches.single)) {
      throw PaseoFailure(PaseoFailureKind.unavailable);
    }
  }

  /// Paseo 0.9.2 sends model-specific options in the ordinary (uncompacted)
  /// provider snapshot. Keep its IDs verbatim for set_agent_thinking_request.
  Map<String, dynamic> _thinkingModelData(Map<String, dynamic> model) {
    final options = model['thinkingOptions'];
    if (options is! List) return const {};
    final variants = <String, Map<String, dynamic>>{};
    for (final option in options.take(64)) {
      if (option is! Map) continue;
      final id = option['id'];
      if (id is! String ||
          id.isEmpty ||
          id.length > 256 ||
          variants.containsKey(id)) {
        continue;
      }
      final label = option['label'];
      variants[id] = {
        if (label is String && label.isNotEmpty) 'label': label,
        if (option['isDefault'] == true ||
            model['defaultThinkingOptionId'] == id)
          'isDefault': true,
      };
    }
    if (variants.isEmpty) return const {};
    return {
      'variants': variants,
      'capabilities': {'reasoning': true},
    };
  }

  Future<void> _setSessionModel(
    String id,
    ModelRef model,
    String variant,
  ) async {
    final scope = _scope, epoch = _locationEpoch;
    if (_drafts.contains(id)) {
      final provider = providerIdForSession(id);
      if (provider != null && provider != model.providerID) {
        throw PaseoFailure(PaseoFailureKind.unavailable);
      }
      _draftProviders[id] = model.providerID;
      _draftModels[id] = model;
      final draft = _sessions[id];
      if (draft != null) {
        _sessions[id] = draft.copyWith(
          selection: SessionSelection(
            model: model,
            variant: variant,
            agent: draft.selection?.agent,
          ),
        );
      }
      return;
    }
    if (!_agents.containsKey(id)) await _fetchAgent(id);
    final agent = _agents[id];
    if (agent == null || model.providerID != agent['provider']) {
      throw PaseoFailure(PaseoFailureKind.unavailable);
    }
    final reservation = await _beforeBrowserLaunch(id);
    await transport.request(
      'set_agent_model_request',
      {'agentId': _real(id), 'modelId': model.modelID},
      mutation: true,
      beforeSend: () => _checkBrowserLaunch(id, reservation),
    );
    _checkLocation(scope, epoch);
    agent['model'] = model.modelID;
    // Remember a successful model change even if the subsequent thinking
    // request fails; the daemon applies these changes in the same order.
    _remember(agent);
    await _setThinking(id, agent, variant, reservation);
    _checkLocation(scope, epoch);
    _remember(agent);
  }

  /// Agents read before the model list arrived carry the daemon's long model
  /// id; once the list is known they are shown under the list's own id.
  void _refreshCanonicalModels() {
    for (final agent in _agents.values.toList()) {
      final canonical = _canonicalModelId(agent['provider'], agent['model']);
      if (canonical == null || canonical == agent['model']) continue;
      final session = _remember(agent, reconcileStatus: false);
      if (session != null) {
        _emit('session.updated', {'info': paseoSessionJson(session)});
      }
    }
  }

  // ---- runtimes, models and modes ---------------------------------------

  Future<List<Map<String, dynamic>>> _providers() async {
    final cached = _providerEntries;
    if (cached != null) return cached;
    final scope = _scope;
    final epoch = _locationEpoch;
    final revision = _providerRevision;
    // Just after the daemon starts, every runtime reports `loading` while it
    // asks each CLI for its models. The app reads providers once per
    // connection, so answering with that empty moment left the composer with
    // no default model for the whole session. Wait briefly for a settled
    // snapshot; past the limit, answer with what is ready and cache nothing.
    var entries = const <Map<String, dynamic>>[];
    for (var attempt = 0; attempt < providerWarmupAttempts; attempt++) {
      if (attempt > 0) await Future<void>.delayed(providerWarmupInterval);
      final result = await transport.request('get_providers_snapshot_request', {
        'cwd': scope,
      }, timeout: const Duration(seconds: 45));
      _checkLocation(scope, epoch);
      entries = paseoList(
        result['entries'],
        max: 256,
      ).whereType<Map<String, dynamic>>().toList();
      if (entries.every((entry) => entry['status'] != 'loading')) {
        if (revision == _providerRevision) {
          _providerEntries = entries;
          _refreshCanonicalModels();
        }
        break;
      }
    }
    return entries;
  }

  /// The mode a runtime's agents start in, as its snapshot names it.
  String? _defaultModeFor(String provider) {
    for (final entry in _providerEntries ?? const <Map<String, dynamic>>[]) {
      if (entry['provider'] != provider) continue;
      final mode = entry['defaultModeId'];
      return mode is String && mode.isNotEmpty ? mode : null;
    }
    return null;
  }

  List<String> _modesFor(String provider) {
    for (final entry in _providerEntries ?? const <Map<String, dynamic>>[]) {
      if (entry['provider'] != provider) continue;
      final modes = entry['modes'];
      if (modes is! List) return const [];
      return modes
          .whereType<Map>()
          .map((mode) => mode['id'])
          .whereType<String>()
          .toList();
    }
    return const [];
  }
}
