part of '../gateway.dart';

extension _PaseoSessions on PaseoGateway {
  /// Records a daemon agent snapshot. Returns null when it belongs to another
  /// project folder or is archived.
  Session? _remember(
    Map<String, dynamic> agent, {
    bool reconcileStatus = true,
  }) {
    final realID = agent['id'];
    if (realID is String && _appIDs.containsKey(realID)) {
      agent = {...agent, 'id': _appIDs[realID]};
    }
    // A record the daemon reloaded (after its helper restarted) can come
    // back without its title: the one already known stays.
    final title = agent['title'];
    final known = _agents[agent['id']]?['title'] ?? _titleHints[agent['id']];
    if ((title is! String || title.trim().isEmpty) &&
        known is String &&
        known.trim().isNotEmpty) {
      agent = {...agent, 'title': known};
    }
    final canonical = _canonicalModelId(agent['provider'], agent['model']);
    if (canonical != null && canonical != agent['model']) {
      agent = {...agent, 'model': canonical};
    }
    final session = paseoSession(agent);
    if (session.directory != _directory || agent['archivedAt'] is String) {
      return null;
    }
    _agents[session.id] = agent;
    _sessions.remove(session.id);
    _sessions[session.id] = session;
    final status = agent['status'];
    if (status == 'error' || status == 'closed') {
      unawaited(_revokeBrowserSession(session.id));
      _liveAgentSessions.remove(session.id);
      _awaitingTurn.remove(session.id);
      _turnActive.remove(session.id);
    }
    if (reconcileStatus) {
      // Snapshot reads are authoritative too: the controller may still hold
      // idle from before this read (or before a transport reconnect).
      final next =
          _awaitingTurn.contains(session.id) || _turnActive.contains(session.id)
          ? 'busy'
          : paseoSessionStatus(agent);
      final previous = _statuses[session.id];
      _emitStatus(session.id, next);
      if (previous != next && next == 'idle') {
        _emit('session.idle', {'sessionID': session.id});
      }
    }
    _drafts.remove(session.id);
    _draftProviders.remove(session.id);
    _syncPermissions(session.id, agent['pendingPermissions']);
    while (_sessions.length > 1024) {
      _forget(_sessions.keys.first);
    }
    return session;
  }

  /// The id the model list knows a running agent's model by: the daemon
  /// reports the resolved model, the list names it by its short id and
  /// keeps the long one as an alias. Null when the list is not loaded or
  /// does not know it (then the agent's own value stays).
  String? _canonicalModelId(Object? provider, Object? model) {
    if (provider is! String || model is! String) return null;
    for (final entry in _providerEntries ?? const <Map<String, dynamic>>[]) {
      if (entry['provider'] != provider) continue;
      final models = entry['models'];
      if (models is! List) return null;
      for (final item in models) {
        if (item is Map && item['id'] == model) return model;
      }
      for (final item in models) {
        if (item is! Map || item['id'] is! String) continue;
        final aliases = item['aliases'];
        if (aliases is List && aliases.contains(model)) {
          return item['id'] as String;
        }
      }
    }
    return null;
  }

  void _forget(String id) {
    unawaited(setBrowserRequestedForSession(id, requested: false));
    _browserRequested.remove(id);
    _browserRequested.remove(_real(id));
    _agents.remove(id);
    _sessions.remove(id);
    _statuses.remove(id);
    _drafts.remove(id);
    _draftProviders.remove(id);
    _draftModels.remove(id);
    _draftFeatureValues.remove(id);
    _liveAgentSessions.remove(id);
    _uncertain.remove(id);
    _awaitingTurn.remove(id);
    _turnActive.remove(id);
    _live.remove(id);
    final permissionIDs = _permissions.values
        .where((value) => value.permission.sessionID == id)
        .map((value) => value.permission.id)
        .toList();
    permissionIDs.forEach(_resolvePermission);
    final questionIDs = _questions.values
        .where((value) => value.question.sessionID == id)
        .map((value) => value.question.id)
        .toList();
    questionIDs.forEach(_resolveQuestion);
    final realID = _realIDs.remove(id);
    if (realID != null) _appIDs.remove(realID);
  }

  Future<Map<String, dynamic>> _fetchAgent(
    String id, {
    bool reconcileStatus = true,
  }) async {
    final scope = _scope;
    final epoch = _locationEpoch;
    final workRevision = _localWork.revision;
    final result = await transport.request('fetch_agent_request', {
      'agentId': _real(paseoString(id, max: 256)),
    });
    _checkLocation(scope, epoch);
    final agent = paseoObject(result['agent']);
    if (agent['id'] != _real(id)) {
      if (_trackLocalWork) _localWork.malformed();
      throw PaseoFailure(PaseoFailureKind.invalidResponse);
    }
    if (_trackLocalWork) {
      _localWork.startEpoch(transport.epoch);
      if (!_localWork.agent(agent, snapshotRevision: workRevision)) {
        _localWork.malformed();
      }
      _localWork.changed();
    }

    if (_remember(agent, reconcileStatus: reconcileStatus) == null) {
      throw PaseoFailure(PaseoFailureKind.scopeMismatch);
    }
    return agent;
  }
}
