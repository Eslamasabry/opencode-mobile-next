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
      if (request['provider'] != null && request['provider'] != provider) {
        throw PaseoFailure(PaseoFailureKind.invalidResponse);
      }
      final hostRequest = paseoHostPermission(
        agentID,
        request,
        provider: provider as String?,
      );
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
        request['name'] == 'mcp__oc-ui__show' &&
            request['kind'] == 'tool' &&
            (!request.containsKey('provider') ||
                request['provider'] == 'claude'),
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
}
