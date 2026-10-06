part of '../gateway.dart';

/// Live stream bookkeeping for one agent.
class _PaseoLive {
  /// Message ids already announced, with the text streamed so far.
  final announced = <String, StringBuffer>{};

  /// Reasoning and id-less assistant text arrive as runs of deltas with no id
  /// of their own. A run keeps the id of its first delta until another item
  /// kind interrupts it.
  String? runID;
  String? runType;
  int lastSeq = 0;
  String? epoch;

  /// The replies of the turn in progress, announced without a finish time:
  /// marked finished when the daemon says the turn ended.
  final open = <String, MessageInfo>{};
}

extension _PaseoEvents on PaseoGateway {
  /// The turn ended: its replies are finished, whether or not the timeline
  /// is read again before the next turn starts (a reply left without a
  /// finish time reads as cut off).
  void _finishOpen(_PaseoLive live) {
    if (live.open.isEmpty) return;
    final now = DateTime.now().millisecondsSinceEpoch;
    for (final info in live.open.values) {
      _emit('message.updated', {
        'info': {
          ...paseoMessageJson(info),
          'time': {'created': info.time?.created ?? now, 'completed': now},
        },
      });
    }
    live.open.clear();
  }

  // ---- live events -------------------------------------------------------

  void _emit(String type, Map<String, dynamic> properties) {
    if (_closed) return;
    _events.add(
      EventEnvelope(type: type, properties: properties, directory: _directory),
    );
  }

  void _emitState(StreamStatus state) {
    if (!_closed && _listening) _streamStates.add(state);
  }

  void _emitStatus(String id, String status) {
    if (_statuses[id] == status) return;
    _statuses[id] = status;
    _emit('session.status', {
      'sessionID': id,
      'status': {'type': status},
    });
  }

  void _onEvent(PaseoEvent event) {
    if (_closed || event.epoch != transport.epoch) return;
    try {
      final p = event.payload;
      final rawID =
          p['agentId'] ?? (p['agent'] is Map ? p['agent']['id'] : null);
      if (_creating > 0 &&
          rawID is String &&
          !_sessions.containsKey(_app(rawID)) &&
          _heldEvents.length < 2048) {
        _heldEvents.add(event);
        return;
      }
      final agentID = rawID is String ? _app(rawID) : null;
      switch (event.type) {
        case 'agent_update':
          if (p['kind'] == 'remove') {
            _removed(agentID);
          } else if (p['agent'] is Map<String, dynamic>) {
            _upserted(p['agent'] as Map<String, dynamic>);
          }
        case 'agent_stream':
          final inner = p['event'];
          if (agentID != null &&
              _sessions.containsKey(agentID) &&
              inner is Map<String, dynamic>) {
            _onStream(agentID, inner, p);
          }
        case 'agent_permission_request':
          final request = p['request'];
          if (agentID != null &&
              _sessions.containsKey(agentID) &&
              request is Map<String, dynamic>) {
            _addPermission(agentID, request);
          }
        case 'agent_permission_resolved':
          final requestID = p['requestId'];
          if (requestID is String) _resolvePermission(requestID);
        case 'agent_deleted' || 'agent_archived':
          _removed(agentID);
        case 'providers_snapshot_update':
          _providerRevision++;
          _providerEntries = null;
      }
    } catch (_) {
      // A malformed push is dropped. No payload reaches logs.
    }
  }

  void _upserted(Map<String, dynamic> agent) {
    final realID = agent['id'];
    if (realID is! String) return;
    final id = _app(realID);
    final known = _agents.containsKey(id);
    final before = _statuses[id];
    if (agent['archivedAt'] is String) {
      _removed(id);
      return;
    }
    final session = _remember(agent);
    if (session == null) return;
    _emit(known ? 'session.updated' : 'session.created', {
      'info': paseoSessionJson(session),
    });
    final status = _statuses[id]!;
    if (before != status) {
      // _remember already stored the new value; announce the change.
      _statuses[id] = before ?? 'idle';
      _emitStatus(id, status);
      if (status == 'idle') _emit('session.idle', {'sessionID': id});
    }
  }

  void _removed(Object? id) {
    if (id is! String || !_sessions.containsKey(id)) return;
    final session = _sessions[id]!;
    _forget(id);
    _emit('session.deleted', {'info': paseoSessionJson(session)});
  }

  void _onStream(
    String id,
    Map<String, dynamic> event,
    Map<String, dynamic> payload,
  ) {
    final live = _live.putIfAbsent(id, _PaseoLive.new);
    final seq = payload['seq'];
    final epoch = payload['epoch'];
    if (seq is int && epoch is String) {
      if (live.epoch == epoch && seq <= live.lastSeq) return;
      live.epoch = epoch;
      live.lastSeq = seq;
    }
    switch (event['type']) {
      case 'turn_started':
        live.runID = null;
        _awaitingTurn.remove(id);
        _turnActive.add(id);
        _emitStatus(id, 'busy');
      case 'turn_completed' || 'turn_canceled':
        live.runID = null;
        _awaitingTurn.remove(id);
        _turnActive.remove(id);
        _finishOpen(live);
        _emitStatus(id, 'idle');
        // A completion prompts authoritative hydration; idle itself is not
        // reported as a successful run.
        _emit('session.idle', {'sessionID': id});
      case 'turn_failed':
        live.runID = null;
        _awaitingTurn.remove(id);
        _turnActive.remove(id);
        _finishOpen(live);
        _emitStatus(id, 'idle');
        _emit('session.error', {
          'sessionID': id,
          'error': {
            'name': 'PaseoTurnFailed',
            'data': {
              'message':
                  'The agent could not finish this reply. Check it on your computer.',
            },
          },
        });
        _emit('session.idle', {'sessionID': id});
      case 'permission_requested':
        final request = event['request'];
        if (request is Map<String, dynamic>) _addPermission(id, request);
      case 'permission_resolved':
        final requestID = event['requestId'];
        if (requestID is String) _resolvePermission(requestID);
      case 'timeline':
        final item = event['item'];
        if (item is Map<String, dynamic>) {
          _onTimelineItem(
            id,
            live,
            item,
            provider: paseoText(event['provider'], max: 128),
            seq: seq is int ? seq : null,
            at: paseoMillis(payload['timestamp']),
          );
        }
    }
  }

  void _onTimelineItem(
    String id,
    _PaseoLive live,
    Map<String, dynamic> item, {
    required String provider,
    required int? seq,
    required int? at,
  }) {
    final type = item['type'];
    if (type is! String) return;
    final streamed = type == 'assistant_message' || type == 'reasoning';
    String messageID;
    final own = type == 'reasoning' ? null : item['messageId'];
    if (streamed && (own is! String || own.isEmpty)) {
      // A run of id-less deltas shares the id of its first delta.
      if (live.runType != type || live.runID == null) {
        live.runType = type;
        live.runID = paseoItemID(item, fallbackSeq: seq);
      }
      messageID = live.runID!;
    } else {
      live.runID = null;
      live.runType = null;
      messageID = paseoItemID(item, fallbackSeq: seq);
    }
    final text = live.announced[messageID];
    if (streamed && text != null) {
      final delta = paseoText(item['text']);
      text.write(delta);
      _emit('message.part.delta', {
        'sessionID': id,
        'messageID': messageID,
        'partID': '$messageID:0',
        'field': 'text',
        'delta': delta,
      });
      return;
    }
    final message = paseoItemMessage(
      id,
      item,
      id: messageID,
      provider: provider,
      created: at ?? DateTime.now().millisecondsSinceEpoch,
      completed: type == 'user_message' ? at : null,
    );
    if (message == null) return;
    if (text == null) {
      live.announced[messageID] = StringBuffer(
        streamed ? paseoText(item['text']) : '',
      );
      while (live.announced.length > 4096) {
        live.announced.remove(live.announced.keys.first);
      }
      _emit('message.updated', {'info': paseoMessageJson(message.info)});
      if (message.info.role == 'assistant' &&
          message.info.time?.completed == null) {
        live.open[messageID] = message.info;
      }
    }
    for (final part in message.parts) {
      _emit('message.part.updated', {
        'sessionID': id,
        'part': {...paseoPartJson(part), 'sessionID': id},
      });
    }
  }
}
