part of '../gateway.dart';

// Claude Code's sub-agents (its Agent tool) as the helper reports them
// (`agent.provider_subagents.*`, Paseo 0.9): each one is a read-only child
// session of the conversation that started it, with its own timeline, so
// the chat's sub-agent card opens it the way an OpenCode task opens its
// child session.

/// The session id of the helper's sub-agent [subagentId].
String paseoSubagentSessionId(String subagentId) => 'sub_$subagentId';

final class _PaseoSubagent {
  _PaseoSubagent({required this.parentReal, required this.subagentId});

  /// The daemon's id of the agent that started it.
  final String parentReal;
  final String subagentId;
}

extension _PaseoSubagents on PaseoGateway {
  bool _isSubagent(String id) => _subagents.containsKey(id);

  /// One `agent.provider_subagents.update` push.
  void _onSubagentUpdate(Map<String, dynamic> payload) {
    switch (payload['kind']) {
      case 'upsert':
        final subagent = payload['subagent'];
        if (subagent is Map<String, dynamic>) _upsertSubagent(subagent);
      case 'timeline':
        final subagentId = payload['subagentId'];
        final item = payload['item'];
        if (subagentId is! String || item is! Map<String, dynamic>) return;
        final id = paseoSubagentSessionId(subagentId);
        if (!_subagents.containsKey(id)) return;
        final live = _live.putIfAbsent(id, _PaseoLive.new);
        final seq = payload['seq'];
        final epoch = payload['epoch'];
        if (seq is int && epoch is String) {
          if (live.epoch == epoch && seq <= live.lastSeq) return;
          live.epoch = epoch;
          live.lastSeq = seq;
        }
        _onTimelineItem(
          id,
          live,
          item,
          provider: paseoText(payload['provider'], max: 128),
          seq: seq is int ? seq : null,
          at: paseoMillis(payload['timestamp']),
        );
      case 'remove':
        final subagentId = payload['subagentId'];
        if (subagentId is! String) return;
        final id = paseoSubagentSessionId(subagentId);
        if (_subagents.remove(id) == null) return;
        _sessions.remove(id);
        _statuses.remove(id);
        _live.remove(id);
        _subagentByCall.removeWhere((_, child) => child == id);
        _emit('session.deleted', {
          'info': {'id': id},
        });
    }
  }

  /// Records a sub-agent [descriptor] of a conversation in this folder and
  /// announces it as that conversation's child session.
  void _upsertSubagent(Map<String, dynamic> descriptor) {
    final subagentId = descriptor['id'];
    final parentReal = descriptor['parentAgentId'];
    if (subagentId is! String || parentReal is! String) return;
    final parentApp = _app(parentReal);
    if (!_agents.containsKey(parentApp) && !_sessions.containsKey(parentApp)) {
      return;
    }
    final id = paseoSubagentSessionId(subagentId);
    final known = _subagents.containsKey(id);
    _subagents[id] = _PaseoSubagent(
      parentReal: parentReal,
      subagentId: subagentId,
    );
    // A sub-agent started by another sub-agent is that one's child.
    final nested = descriptor['parentSubagentId'];
    final parentID = nested is String && nested.isNotEmpty
        ? paseoSubagentSessionId(nested)
        : parentApp;
    final title = [descriptor['title'], descriptor['description']]
        .whereType<String>()
        .map((text) => text.trim())
        .firstWhere((text) => text.isNotEmpty, orElse: () => 'Sub-agent');
    final session = Session(
      id: id,
      title: paseoText(title, max: 4096),
      parentID: parentID,
      directory: _directory,
      time: SessionTime(
        created: paseoMillis(descriptor['createdAt']),
        updated: paseoMillis(descriptor['updatedAt']),
      ),
      // It works with its main conversation's model; nothing to choose.
      selection: _sessions[parentApp]?.selection,
    );
    _sessions[id] = session;
    _emit(known ? 'session.updated' : 'session.created', {
      'info': paseoSessionJson(session),
    });
    final status = descriptor['status'] == 'running' ? 'busy' : 'idle';
    if (_statuses[id] != status) {
      _emitStatus(id, status);
      if (status == 'idle' && known) _emit('session.idle', {'sessionID': id});
    }
    final callID = descriptor['toolCallId'];
    if (callID is String && callID.isNotEmpty) {
      final before = _subagentByCall[callID];
      _subagentByCall[callID] = id;
      // The card already shown for the call that started it now opens it.
      final shown = _toolParts[callID];
      if (before != id && shown != null) {
        _emit('message.part.updated', {
          'sessionID': shown['sessionID'],
          'part': _linkedToolPart(shown),
        });
      }
    }
  }

  /// [part] (a tool part's json) with the sub-agent its call started, for
  /// the card's Open.
  Map<String, dynamic> _linkedToolPart(Map<String, dynamic> part) {
    final callID = part['callID'];
    final child = callID is String ? _subagentByCall[callID] : null;
    if (child == null) return part;
    final state = part['state'];
    final stateMap = state is Map<String, dynamic>
        ? state
        : <String, dynamic>{};
    final metadata = stateMap['metadata'];
    return {
      ...part,
      'state': {
        ...stateMap,
        'metadata': {
          if (metadata is Map<String, dynamic>) ...metadata,
          'sessionId': child,
        },
      },
    };
  }

  /// Remembers a tool part shown in [sessionID] so a sub-agent reported
  /// after it still links to it.
  void _rememberToolPart(String sessionID, Map<String, dynamic> part) {
    final callID = part['callID'];
    if (part['type'] != 'tool' || callID is! String || callID.isEmpty) return;
    _toolParts.remove(callID);
    _toolParts[callID] = {...part, 'sessionID': sessionID};
    while (_toolParts.length > 2048) {
      _toolParts.remove(_toolParts.keys.first);
    }
  }

  /// The sub-agents the helper knows for [parentID] (an old conversation's
  /// too), so its cards link to them.
  Future<void> _loadSubagents(String parentID) async {
    if (_drafts.contains(parentID)) return;
    try {
      final result = await transport.request(
        'agent.provider_subagents.list.request',
        {'parentAgentId': _real(parentID)},
        timeout: const Duration(seconds: 8),
      );
      for (final subagent in paseoList(result['subagents'], max: 500)) {
        if (subagent is Map<String, dynamic>) _upsertSubagent(subagent);
      }
    } catch (_) {
      // An older helper, or none yet: the cards stay as they are.
    }
  }

  /// A sub-agent's own conversation.
  Future<List<MessageWithParts>> _subagentMessages(String id) async {
    final subagent = _subagents[id]!;
    final result = await transport
        .request('agent.provider_subagents.timeline.get.request', {
          'parentAgentId': subagent.parentReal,
          'subagentId': subagent.subagentId,
          'direction': 'tail',
          'limit': 200,
        }, timeout: const Duration(seconds: 45));
    final provider = result['provider'];
    final busy = _statuses[id] == 'busy';
    final messages = paseoTimelineMessages(id, {
      'entries': [
        for (final row in paseoList(result['rows'], max: 20000))
          if (row is Map<String, dynamic>) {...row, 'provider': ?provider},
      ],
    }, busy: busy);
    final live = _live.putIfAbsent(id, _PaseoLive.new);
    live.announced.clear();
    live.runID = null;
    live.runType = null;
    for (final message in messages) {
      live.announced[message.info.id] = StringBuffer(
        message.parts.isEmpty ? '' : message.parts.first.text,
      );
    }
    final epoch = result['epoch'];
    if (epoch is String) live.epoch = epoch;
    final end = result['endCursor'];
    if (end is Map && end['seq'] is int) live.lastSeq = end['seq'] as int;
    return _linkMessages(id, messages);
  }

  /// [messages] with their sub-agent cards linked, each tool part
  /// remembered for later links.
  List<MessageWithParts> _linkMessages(
    String sessionID,
    List<MessageWithParts> messages,
  ) => [
    for (final message in messages)
      MessageWithParts(
        info: message.info,
        parts: [
          for (final part in message.parts)
            if (part.type != 'tool')
              part
            else
              () {
                final json = paseoPartJson(part);
                _rememberToolPart(sessionID, json);
                final linked = _linkedToolPart(json);
                return identical(linked, json) ? part : Part.fromJson(linked);
              }(),
        ],
      ),
  ];

  static const _readOnly = ProductException(
    "Claude's sub-agents answer only their main conversation. Write there.",
  );
}
