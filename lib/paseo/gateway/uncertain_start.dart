part of '../gateway.dart';

/// A first message whose create request got no answer (a cold daemon past
/// its 90 s, or a dropped link) may or may not have started an agent. The
/// draft stays uncertain until a read settles it: the daemon's agent whose
/// first prompt carries this message's id becomes the draft's agent;
/// with none, the draft is free to send again. Before this, every retry
/// failed at once and the chat could never recover (audit 2026-10-27).
typedef _UncertainStart = ({String messageID, String provider, int sentAt});

final _uncertainStarts = Expando<Map<String, _UncertainStart>>();

extension _PaseoUncertainStart on PaseoGateway {
  Map<String, _UncertainStart> get _starts =>
      _uncertainStarts[this] ??= <String, _UncertainStart>{};

  void _noteUncertainStart(String id, _UncertainStart start) =>
      _starts[id] = start;

  /// Whether the draft [id] became a daemon conversation after all.
  Future<bool> _draftStarted(String id) async =>
      _uncertain.contains(id) && await _adoptUncertainDraft(id);

  /// Whether a send to [id] must wait for a reload. An uncertain draft that
  /// started nothing may send again; one that did reloads first, so its
  /// first message is never sent twice.
  Future<bool> _sendIsUncertain(String id) async =>
      _uncertain.contains(id) &&
      (!_drafts.contains(id) || await _adoptUncertainDraft(id));

  /// Settles the uncertain draft [id]. True when the daemon's agent was
  /// found and adopted (the draft is now that conversation); false when
  /// nothing was started, and the draft may send again.
  Future<bool> _adoptUncertainDraft(String id) async {
    final start = _starts.remove(id);
    if (start == null) {
      _uncertain.remove(id);
      return false;
    }
    final scope = _scope;
    final epoch = _locationEpoch;
    final result = await transport.request('fetch_agents_request', {
      'sort': [
        {'key': 'updated_at', 'direction': 'desc'},
      ],
      'page': {'limit': 20},
    });
    _checkLocation(scope, epoch);
    for (final raw in paseoList(result['entries'], max: 200)) {
      final entry = raw is Map<String, dynamic> ? raw['agent'] : null;
      if (entry is! Map<String, dynamic>) continue;
      final realID = entry['id'];
      final created = paseoMillis(entry['createdAt']);
      // Only an agent no conversation here owns yet, started for this
      // folder and runtime around the send (ten minutes allows for clocks).
      if (realID is! String ||
          _appIDs.containsKey(realID) ||
          entry['cwd'] != scope ||
          entry['provider'] != start.provider ||
          created == null ||
          created < start.sentAt - 600000) {
        continue;
      }
      final timeline = await transport.request('fetch_agent_timeline_request', {
        'agentId': realID,
        'direction': 'tail',
        'limit': 0,
        'projection': 'projected',
      }, timeout: const Duration(seconds: 45));
      _checkLocation(scope, epoch);
      if (!_startedBy(timeline, start.messageID)) continue;
      _realIDs[id] = realID;
      _appIDs[realID] = id;
      if (_remember(entry) == null) {
        _realIDs.remove(id);
        _appIDs.remove(realID);
        continue;
      }
      if (entry['status'] != 'error' && entry['status'] != 'closed') {
        _liveAgentSessions.add(id);
      }
      _drafts.remove(id);
      _draftProviders.remove(id);
      _draftModels.remove(id);
      return true;
    }
    _uncertain.remove(id);
    return false;
  }

  static bool _startedBy(Map<String, dynamic> timeline, String messageID) {
    for (final entry in paseoList(timeline['entries'], max: 20000)) {
      final item = entry is Map<String, dynamic> ? entry['item'] : null;
      if (item is! Map<String, dynamic> || item['type'] != 'user_message') {
        continue;
      }
      return item['messageId'] == messageID ||
          item['clientMessageId'] == messageID;
    }
    return false;
  }
}
