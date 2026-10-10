part of '../gateway.dart';

// ---- OD1 agent lane: an agent's own switches (fast mode and the like) ------

final _featureIdPattern = RegExp(r'^[A-Za-z0-9_.:-]{1,64}$');

extension _PaseoAgentFeatures on PaseoGateway {
  String? _featureText(Object? value, int max) {
    if (value is! String) return null;
    final clean = value
        .replaceAll(RegExp(r'[\x00-\x1f\x7f]'), ' ')
        .replaceAll(RegExp(r'\s+'), ' ')
        .trim();
    if (clean.isEmpty) return null;
    return clean.length > max ? clean.substring(0, max) : clean;
  }

  /// The switches an agent snapshot or a provider listing carries. Anything
  /// malformed is skipped, never fatal: switches are an extra.
  List<AgentFeature> _parseFeatures(Object? raw) {
    if (raw is! List) return const [];
    final out = <AgentFeature>[];
    for (final item in raw.take(16)) {
      if (item is! Map) continue;
      final id = item['id'];
      final label = _featureText(item['label'], 80);
      if (id is! String || !_featureIdPattern.hasMatch(id) || label == null) {
        continue;
      }
      final description = _featureText(item['description'], 200);
      switch (item['type']) {
        case 'toggle':
          if (item['value'] is! bool) continue;
          out.add(
            AgentFeature(
              id: id,
              label: label,
              description: description,
              kind: AgentFeatureKind.toggle,
              on: item['value'] as bool,
            ),
          );
        case 'select':
          final options = <AgentFeatureOption>[];
          final rawOptions = item['options'];
          if (rawOptions is List) {
            for (final option in rawOptions.take(32)) {
              if (option is! Map) continue;
              final optionId = option['id'];
              final optionLabel = _featureText(option['label'], 80);
              if (optionId is String &&
                  optionId.isNotEmpty &&
                  optionId.length <= 128 &&
                  optionLabel != null) {
                options.add(
                  AgentFeatureOption(id: optionId, label: optionLabel),
                );
              }
            }
          }
          if (options.isEmpty) continue;
          final value = item['value'];
          out.add(
            AgentFeature(
              id: id,
              label: label,
              description: description,
              kind: AgentFeatureKind.choice,
              selected: value is String ? value : null,
              options: options,
            ),
          );
      }
    }
    return out;
  }

  Future<List<AgentFeature>> _agentFeatures(String id) async {
    if (_isSubagent(id)) return const [];
    if (_drafts.contains(id)) return _draftFeatures(id);
    final agent = await _fetchAgent(id);
    return _parseFeatures(agent['features']);
  }

  /// Before the first message there is no agent yet: the daemon lists what
  /// the chosen agent and model would offer.
  Future<List<AgentFeature>> _draftFeatures(String id) async {
    final scope = _scope, epoch = _locationEpoch;
    final provider = _draftProviders[id] ?? paseoDefaultProvider;
    if (!isPaseoProviderId(provider)) return const [];
    final model = _draftModels[id]?.modelID;
    final values = _draftFeatureValues[id];
    final result = await transport.request('list_provider_features_request', {
      'draftConfig': {
        'provider': provider,
        'cwd': scope,
        if (model != null && model.isNotEmpty && model != paseoDefaultModel)
          'model': model,
        if (values != null && values.isNotEmpty) 'featureValues': values,
      },
    });
    _checkLocation(scope, epoch);
    return _parseFeatures(result['features']);
  }

  Future<List<AgentFeature>> _setAgentFeature(
    String id,
    String featureId,
    Object value,
  ) async {
    if (!_featureIdPattern.hasMatch(featureId) || _isSubagent(id)) {
      throw PaseoFailure(PaseoFailureKind.unavailable);
    }
    final known = (await _agentFeatures(
      id,
    )).where((feature) => feature.id == featureId).firstOrNull;
    final valid =
        known != null &&
        switch (known.kind) {
          AgentFeatureKind.toggle => value is bool,
          AgentFeatureKind.choice =>
            value is String && known.options.any((o) => o.id == value),
        };
    if (!valid) throw PaseoFailure(PaseoFailureKind.unavailable);
    if (_drafts.contains(id)) {
      _draftFeatureValues[id] = {...?_draftFeatureValues[id], featureId: value};
      return _draftFeatures(id);
    }
    final scope = _scope, epoch = _locationEpoch;
    final reservation = await _beforeBrowserLaunch(id);
    final result = await transport.request(
      'set_agent_feature_request',
      {'agentId': _real(id), 'featureId': featureId, 'value': value},
      mutation: true,
      beforeSend: () => _checkBrowserLaunch(id, reservation),
    );
    _checkLocation(scope, epoch);
    if (result['accepted'] == false) {
      throw PaseoFailure(PaseoFailureKind.unavailable);
    }
    // The agent's own record is the answer, not what was asked for.
    return _agentFeatures(id);
  }
}
