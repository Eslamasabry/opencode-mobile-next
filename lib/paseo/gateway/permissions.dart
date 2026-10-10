part of '../gateway.dart';

class _PaseoPermission {
  final int epoch;
  final PermissionRequest permission;
  final List<dynamic> suggestions;
  final HostAgentPermissionRequest? hostRequest;
  final bool genUiShow;
  final String wireRevision;
  bool genUiApproving = false;
  bool genUiAttempted = false;
  _PaseoPermission(
    this.epoch,
    this.permission,
    this.suggestions,
    this.hostRequest,
    this.genUiShow,
    this.wireRevision,
  );
}

extension _PaseoPermissions on PaseoGateway {
  void _addPermission(String agentID, Map<String, dynamic> request) {
    final id = paseoString(request['id'], max: 512);
    final previous = _permissions[id];
    if ((previous != null && previous.permission.sessionID != agentID) ||
        (_questions[id] != null &&
            _questions[id]!.question.sessionID != agentID) ||
        _answered.contains(id)) {
      return;
    }
    if (request['kind'] == 'question' || request['kind'] == 'plan') {
      if (previous != null) _resolvePermission(id);
      _addQuestion(agentID, request);
      return;
    }
    _PaseoPermission pending;
    try {
      // Display mappers are intentionally lenient. Approval admission must
      // reject malformed replacement input instead of retaining an old grant.
      for (final key in ['name', 'kind', 'provider']) {
        if (request.containsKey(key) && request[key] is! String) {
          throw PaseoFailure(PaseoFailureKind.invalidResponse);
        }
      }
      for (final key in ['detail', 'input', 'metadata']) {
        if (request.containsKey(key) && request[key] is! Map<String, dynamic>) {
          throw PaseoFailure(PaseoFailureKind.invalidResponse);
        }
      }
      final detail = request['detail'];
      if (detail is Map &&
          detail['type'] == 'shell' &&
          detail['command'] is! String) {
        throw PaseoFailure(PaseoFailureKind.invalidResponse);
      }
      if (request.containsKey('suggestions') &&
          request['suggestions'] is! List) {
        throw PaseoFailure(PaseoFailureKind.invalidResponse);
      }
      final provider = _agents[agentID]?['provider'];
      final hostRequest = paseoHostPermission(
        agentID,
        request,
        provider: provider as String?,
      );
      // The trusted session provider decides ACP admission. A contradictory
      // request must retain its restricted card instead of hiding the wait;
      // it cannot opt into native suggestions or implicit one-call approval.
      if (hostRequest == null &&
          request['provider'] != null &&
          request['provider'] != provider) {
        throw PaseoFailure(PaseoFailureKind.invalidResponse);
      }
      final permission = hostRequest == null
          ? paseoPermission(agentID, request)
          : paseoPermission(agentID, {
              'id': hostRequest.requestId,
              'name': 'tool',
              'kind': 'tool',
              'detail': request['detail'],
              'description': 'The agent needs your permission.',
            });
      final suggestions = request['suggestions'];
      pending = _PaseoPermission(
        transport.epoch,
        permission,
        hostRequest == null && suggestions is List
            ? jsonDecode(jsonEncode(suggestions)) as List<dynamic>
            : const [],
        hostRequest,
        _isCardShowRequest(request),
        crypto.sha256
            .convert(
              utf8.encode(jsonEncode(_canonicalPermissionValue(request))),
            )
            .toString(),
      );
    } catch (_) {
      if (previous != null) _resolvePermission(id);
      _resolveQuestion(id);
      return;
    }
    if (previous != null &&
        previous.epoch == pending.epoch &&
        _permissionRevision(previous) == _permissionRevision(pending)) {
      return;
    }
    _resolveQuestion(id);
    if (previous == null && _permissions.length >= 64) {
      _resolvePermission(_permissions.keys.first);
    }
    // Replacing an in-flight revision cannot restart automatic approval.
    pending.genUiAttempted = previous?.genUiAttempted == true;
    _permissions[id] = pending;
    if (_tryAllowGenUiShow(pending)) return;
    _emitPermission(pending);
  }

  Map<String, dynamic> _permissionJson(PermissionRequest permission) => {
    'id': permission.id,
    'sessionID': permission.sessionID,
    'permission': permission.permission,
    'patterns': permission.patterns,
    'metadata': permission.metadata,
    'always': permission.always,
    if (permission.tool != null)
      'tool': {
        'messageID': permission.tool!.messageID,
        'callID': permission.tool!.callID,
      },
    if (permission.message != null) 'message': permission.message,
  };

  Object? _canonicalPermissionValue(Object? value) {
    if (value is Map) {
      final keys = value.keys.cast<String>().toList()..sort();
      return {
        for (final key in keys) key: _canonicalPermissionValue(value[key]),
      };
    }
    if (value is List) return value.map(_canonicalPermissionValue).toList();
    return value;
  }

  String _permissionRevision(_PaseoPermission pending) {
    return jsonEncode(
      _canonicalPermissionValue({
        'permission': _permissionJson(pending.permission),
        'suggestions': pending.suggestions,
        'host': pending.hostRequest == null
            ? null
            : [
                for (final choice in pending.hostRequest!.choices)
                  [choice.actionId, choice.behavior.name],
              ],
        'genUiShow': pending.genUiShow,
        'wireRevision': pending.wireRevision,
      }),
    );
  }

  void _emitPermission(_PaseoPermission pending) {
    _emit('permission.asked', _permissionJson(pending.permission));
    _nativePermissionChanges.add(null);
  }

  /// A permission request for the display-only card tool, from an agent
  /// whose adapter allows showing cards without asking. A request with no
  /// provider is attributed to Claude Code, the only agent the daemon omits
  /// it for.
  bool _isCardShowRequest(Map<String, dynamic> request) {
    final provider = request.containsKey('provider')
        ? request['provider']
        : AgentToolAdapter.claude.paseoProvider;
    final agent = AgentToolAdapters.forPaseoProvider(provider);
    return agent != null &&
        agent.preAllowsCards &&
        request['kind'] == 'tool' &&
        request['name'] == agent.cardShowName;
  }

  bool _canAllowGenUiShow(_PaseoPermission pending) {
    final agent = _agents[pending.permission.sessionID];
    if (!pending.genUiShow ||
        pending.genUiAttempted ||
        pending.hostRequest != null ||
        _closed ||
        !transport.connected ||
        transport.serverVersion != '0.9.2' ||
        pending.epoch != transport.epoch ||
        !(AgentToolAdapters.forPaseoProvider(
              agent?['provider'],
            )?.preAllowsCards ??
            false) ||
        _directory == null ||
        agent?['cwd'] != _directory) {
      return false;
    }
    try {
      return _genUiShowAllowed?.call() == true;
    } catch (_) {
      return false;
    }
  }

  bool _tryAllowGenUiShow(_PaseoPermission pending) {
    if (pending.genUiApproving) return true;
    if (!_canAllowGenUiShow(pending)) return false;
    pending.genUiApproving = true;
    _nativePermissionChanges.add(null);
    // A qualification sweep may run inside an event callback. Defer the
    // reply because the synchronous event stream cannot emit recursively.
    unawaited(
      Future<void>.microtask(() async {
        if (!identical(_permissions[pending.permission.id], pending)) return;
        try {
          if (_canAllowGenUiShow(pending)) {
            // A failed or uncertain send belongs back with the person. Event
            // listeners may rebind qualification, but must never retry it.
            pending.genUiAttempted = true;
            await respondPermission(pending.permission.id, 'once');
          }
        } catch (_) {
          // Keep a still-current request visible if its one-call send fails.
        } finally {
          pending.genUiApproving = false;
          if (identical(_permissions[pending.permission.id], pending)) {
            _emitPermission(pending);
          }
        }
      }),
    );
    return true;
  }

  void _resolvePermission(String requestID) {
    _resolveQuestion(requestID);
    final removed = _permissions.remove(requestID);
    if (removed == null) return;
    _emit('permission.replied', {
      'sessionID': removed.permission.sessionID,
      'requestID': requestID,
    });
    _nativePermissionChanges.add(null);
  }

  /// An agent snapshot lists everything still waiting, so it both adds
  /// requests this client missed and retires ones answered elsewhere.
  void _syncPermissions(String agentID, Object? pending) {
    if (pending is! List) return;
    final current = <String>{};
    for (final raw in pending.take(64)) {
      if (raw is! Map<String, dynamic>) continue;
      try {
        final id = raw['id'];
        if (id is String) current.add(id);
        _addPermission(agentID, raw);
      } on PaseoFailure {
        // A malformed request cannot become an approval card.
      }
    }
    final resolved = _permissions.entries
        .where(
          (entry) =>
              entry.value.permission.sessionID == agentID &&
              !current.contains(entry.key),
        )
        .map((entry) => entry.key)
        .toList();
    resolved.forEach(_resolvePermission);
    final resolvedQuestions = _questions.entries
        .where(
          (entry) =>
              entry.value.question.sessionID == agentID &&
              !current.contains(entry.key),
        )
        .map((entry) => entry.key)
        .toList();
    resolvedQuestions.forEach(_resolveQuestion);
  }

  void _checkPermissionEpoch(_PaseoPermission pending) {
    if (!transport.connected || pending.epoch != transport.epoch) {
      throw PaseoFailure(PaseoFailureKind.staleRequest);
    }
  }

  Future<void> _respondPermission(
    String requestID,
    String reply, {
    String? legacySessionID,
    String? message,
  }) async {
    final pending = _permissions[requestID];
    if (pending == null ||
        (legacySessionID != null &&
            legacySessionID != pending.permission.sessionID)) {
      throw PaseoFailure(PaseoFailureKind.staleRequest);
    }
    if (pending.hostRequest != null) {
      if (reply == 'always') throw PaseoFailure(PaseoFailureKind.unavailable);
      if (!{'once', 'reject', 'cancel'}.contains(reply)) {
        throw PaseoFailure(PaseoFailureKind.unavailable);
      }
      String? action;
      if (reply == 'once') {
        final allow = pending.hostRequest!.choices
            .where(
              (choice) =>
                  choice.behavior == HostAgentPermissionBehavior.allowOnce,
            )
            .toList();
        // Multiple choices require the exact-action card, not a guessed choice.
        if (allow.length != 1) throw PaseoFailure(PaseoFailureKind.unavailable);
        action = allow.single.actionId;
      }
      return _respondHostAgentPermission(requestID, selectedActionId: action);
    }
    if (reply == 'always' && pending.suggestions.isEmpty) {
      throw PaseoFailure(PaseoFailureKind.unavailable);
    }
    final response = switch (reply) {
      'once' => <String, dynamic>{'behavior': 'allow'},
      // The provider's own suggested rule ("accept edits for this session").
      'always' => <String, dynamic>{
        'behavior': 'allow',
        if (pending.suggestions.isNotEmpty)
          'updatedPermissions': pending.suggestions,
      },
      'reject' => <String, dynamic>{
        'behavior': 'deny',
        if (message != null && message.trim().isNotEmpty)
          'message': paseoText(message.trim(), max: 4096),
      },
      'cancel' => <String, dynamic>{'behavior': 'deny', 'interrupt': true},
      _ => throw PaseoFailure(PaseoFailureKind.unavailable),
    };
    _checkPermissionEpoch(pending);
    transport.send('agent_permission_response', {
      'agentId': _real(pending.permission.sessionID),
      'requestId': requestID,
      'response': response,
    }, expectedEpoch: pending.epoch);
    _answered.add(requestID);
    while (_answered.length > 256) {
      _answered.remove(_answered.first);
    }
    _resolvePermission(requestID);
  }

  Future<void> _respondHostAgentPermission(
    String requestId, {
    String? selectedActionId,
  }) async {
    final pending = _permissions[requestId];
    final request = pending?.hostRequest;
    if (pending == null || request == null) {
      throw PaseoFailure(PaseoFailureKind.staleRequest);
    }
    _checkPermissionEpoch(pending);
    final scope = _scope;
    final locationEpoch = _locationEpoch;
    final realAgentId = _real(request.sessionId);
    HostAgentPermissionChoice? choice;
    if (selectedActionId != null) {
      for (final offered in request.choices) {
        if (offered.actionId == selectedActionId) choice = offered;
      }
      if (choice == null) throw PaseoFailure(PaseoFailureKind.staleRequest);
    } else {
      for (final offered in request.choices) {
        if (offered.behavior == HostAgentPermissionBehavior.rejectOnce) {
          choice = offered;
          break;
        }
      }
    }
    if (choice == null) {
      // Paseo's implicit deny falls back to reject_always. Cancel the turn
      // instead, which resolves ACP pending requests with outcome=cancelled.
      final result = await transport.request(
        'cancel_agent_request',
        {'agentId': realAgentId},
        mutation: true,
        expectedEpoch: pending.epoch,
      );
      _checkLocation(scope, locationEpoch);
      _checkPermissionEpoch(pending);
      final current = _permissions[requestId];
      if (current != null && !identical(current, pending)) {
        throw PaseoFailure(PaseoFailureKind.staleRequest);
      }
      final agent = result['agent'];
      final stillPending = agent is Map ? agent['pendingPermissions'] : null;
      // An idle agent may acknowledge cancel without interrupting anything.
      // Acknowledgement alone must not retire an unanswered approval card.
      if (identical(_permissions[requestId], pending) &&
          (agent is! Map ||
              agent['id'] != realAgentId ||
              agent['cwd'] != _scope ||
              stillPending is! List ||
              stillPending.any(
                (raw) => raw is Map && raw['id'] == requestId,
              ))) {
        throw PaseoFailure(PaseoFailureKind.unavailable);
      }
    } else {
      transport.send('agent_permission_response', {
        'agentId': _real(request.sessionId),
        'requestId': requestId,
        'response': {
          'behavior': choice.behavior == HostAgentPermissionBehavior.allowOnce
              ? 'allow'
              : 'deny',
          'selectedActionId': choice.actionId,
        },
      }, expectedEpoch: pending.epoch);
    }
    _answered.add(requestId);
    while (_answered.length > 256) {
      _answered.remove(_answered.first);
    }
    _resolvePermission(requestId);
  }
}
