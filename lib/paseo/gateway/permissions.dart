part of '../gateway.dart';

class _PaseoPermission {
  final int epoch;
  final PermissionRequest permission;
  final List<dynamic> suggestions;
  final HostAgentPermissionRequest? hostRequest;
  final bool genUiShow;
  bool genUiApproving = false;
  bool genUiAttempted = false;
  _PaseoPermission(
    this.epoch,
    this.permission,
    this.suggestions,
    this.hostRequest,
    this.genUiShow,
  );
}

extension _PaseoPermissions on PaseoGateway {
  void _addPermission(String agentID, Map<String, dynamic> request) {
    if (request['kind'] == 'question' || request['kind'] == 'plan') {
      _addQuestion(agentID, request);
      return;
    }
    final hostRequest = paseoHostPermission(
      agentID,
      request,
      provider: _agents[agentID]?['provider'] as String?,
    );
    final permission = hostRequest == null
        ? paseoPermission(agentID, request)
        : paseoPermission(agentID, {
            'id': hostRequest.requestId,
            'name': 'tool',
            'kind': 'tool',
            // Only the known structured task preview is exposed. Raw ACP
            // requests, diagnostic labels and arbitrary inputs stay private.
            'detail': request['detail'],
            'description': 'The agent needs your permission.',
          });
    if (_questions.containsKey(permission.id) ||
        _permissions.containsKey(permission.id) ||
        _answered.contains(permission.id)) {
      return;
    }
    while (_permissions.length >= 64) {
      _permissions.remove(_permissions.keys.first);
    }
    final suggestions = request['suggestions'];
    final pending = _PaseoPermission(
      transport.epoch,
      permission,
      hostRequest == null && suggestions is List ? suggestions : const [],
      hostRequest,
      request['name'] == 'mcp__oc-ui__show' &&
          request['kind'] == 'tool' &&
          (!request.containsKey('provider') || request['provider'] == 'claude'),
    );
    _permissions[permission.id] = pending;
    if (_tryAllowGenUiShow(pending)) return;
    _emitPermission(pending);
  }

  void _emitPermission(_PaseoPermission pending) {
    final permission = pending.permission;
    _emit('permission.asked', {
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
    });
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
        agent?['provider'] != 'claude' ||
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
}
