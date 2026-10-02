part of '../gateway.dart';

class _PaseoPermission {
  final int epoch;
  final PermissionRequest permission;
  final List<dynamic> suggestions;
  final HostAgentPermissionRequest? hostRequest;
  _PaseoPermission(
    this.epoch,
    this.permission,
    this.suggestions,
    this.hostRequest,
  );
}

extension _PaseoPermissions on PaseoGateway {
  void _addPermission(String agentID, Map<String, dynamic> request) {
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
    if (_permissions.containsKey(permission.id) ||
        _answered.contains(permission.id)) {
      return;
    }
    while (_permissions.length >= 64) {
      _permissions.remove(_permissions.keys.first);
    }
    final suggestions = request['suggestions'];
    _permissions[permission.id] = _PaseoPermission(
      transport.epoch,
      permission,
      hostRequest == null && suggestions is List ? suggestions : const [],
      hostRequest,
    );
    _emit('permission.asked', {
      'id': permission.id,
      'sessionID': agentID,
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

  void _resolvePermission(String requestID) {
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
  }

  void _checkPermissionEpoch(_PaseoPermission pending) {
    if (!transport.connected || pending.epoch != transport.epoch) {
      throw PaseoFailure(PaseoFailureKind.staleRequest);
    }
  }
}
