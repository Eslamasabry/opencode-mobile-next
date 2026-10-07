part of '../gateway.dart';

extension _PaseoGenUiHistory on PaseoGateway {
  Future<bool> _genUiIdle(String sessionID) async {
    sessionID = _app(sessionID);
    validateGenUiHistoryRequest(sessionID, null, 1);
    if (_isSubagent(sessionID) || _drafts.contains(sessionID)) return false;
    final scope = _scope;
    final locationEpoch = _locationEpoch;
    final realID = _real(sessionID);
    try {
      final result = await transport.requestBoundedHistory({
        'agentId': realID,
      }, agentSnapshot: true);
      _checkLocation(scope, locationEpoch);
      final agent = result['agent'];
      if (agent is! Map<String, dynamic> ||
          agent['id'] != realID ||
          agent['cwd'] != scope ||
          _real(sessionID) != realID) {
        throw genUiHistoryFailure;
      }
      return agent['status'] == 'idle' &&
          agent['archivedAt'] == null &&
          !_awaitingTurn.contains(sessionID) &&
          !_turnActive.contains(sessionID);
    } catch (_) {
      throw genUiHistoryFailure;
    }
  }

  Future<GenUiHistoryPage> _boundedGenUiHistory(
    String sessionID, {
    String? cursor,
    required int limit,
  }) async {
    validateGenUiHistoryRequest(sessionID, cursor, limit);
    if (_isSubagent(sessionID) || _drafts.contains(sessionID)) {
      throw genUiHistoryFailure;
    }
    final scope = _scope;
    final locationEpoch = _locationEpoch;
    final realID = _real(sessionID);
    Map<String, dynamic>? before;
    try {
      if (cursor != null) {
        final value = jsonDecode(cursor);
        if (value is! Map<String, dynamic> || !_validHistoryCursor(value)) {
          throw genUiHistoryFailure;
        }
        before = value;
      }
      final result = await transport
          .requestBoundedHistory({
            'agentId': realID,
            'direction': before == null ? 'tail' : 'before',
            'cursor': ?before,
            'limit': limit,
            'projection': 'projected',
          })
          .timeout(genUiHistoryTimeout);
      _checkLocation(scope, locationEpoch);
      if (_closed || realID != _real(sessionID)) throw genUiHistoryFailure;
      final entries = result['entries'];
      final epoch = result['epoch'];
      if (result['agentId'] != realID ||
          entries is! List ||
          entries.length > limit ||
          entries.any((entry) => entry is! Map<String, dynamic>) ||
          epoch is! String ||
          epoch.isEmpty ||
          result['hasOlder'] is! bool ||
          result['hasNewer'] is! bool ||
          result['staleCursor'] == true ||
          result['gap'] == true ||
          (before == null && result['hasNewer'] == true) ||
          (before != null &&
              (result['reset'] == true || epoch != before['epoch']))) {
        throw genUiHistoryFailure;
      }
      final hasMore = result['hasOlder'] as bool;
      final start = result['startCursor'];
      String? next;
      if (hasMore) {
        if (start is! Map<String, dynamic> ||
            !_validHistoryCursor(start) ||
            start['epoch'] != epoch) {
          throw genUiHistoryFailure;
        }
        next = jsonEncode({'epoch': start['epoch'], 'seq': start['seq']});
        if (next == cursor) throw genUiHistoryFailure;
      }
      return GenUiHistoryPage(
        items: List.unmodifiable(
          paseoTimelineMessages(
            sessionID,
            result,
            busy: _statuses[sessionID] == 'busy',
          ),
        ),
        olderCursor: next,
        hasMore: hasMore,
      );
    } catch (_) {
      throw genUiHistoryFailure;
    }
  }

  bool _validHistoryCursor(Map<String, dynamic> value) =>
      value['epoch'] is String &&
      (value['epoch'] as String).isNotEmpty &&
      (value['epoch'] as String).length <= 512 &&
      value['seq'] is int &&
      (value['seq'] as int) >= 0;
}
