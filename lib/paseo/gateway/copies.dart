part of '../gateway.dart';

/// The older records of one conversation (Paseo keeps one per resume): the
/// title the newer record lost, and archiving the older ones the chat list
/// folds away.
extension PaseoGatewayCopies on PaseoGateway {
  /// The title [appID]'s record lost to a resume (the older record of the
  /// same conversation had it): shown here while the record has none of its
  /// own. Nothing is written to the helper (an update there moves the
  /// conversation to the top as if it had just been used).
  void keepTitle(String appID, String title) {
    if (title.trim().isEmpty) return;
    _titleHints[appID] = title;
    if (hasOwnTitle(appID)) return;
    final agent = _agents[appID];
    if (agent != null) _agents[appID] = {...agent, 'title': title};
    final session = _sessions[appID];
    if (session != null) _sessions[appID] = session.copyWith(title: title);
  }

  /// The title saved for [agent]'s conversation (see [savedTitle]), if any.
  String? _rememberedTitle(Map<String, dynamic> agent) {
    final lookup = savedTitle;
    final handle = agent['persistence'];
    if (lookup == null || handle is! Map) return null;
    final id = handle['sessionId'];
    if (id is! String || id.isEmpty) return null;
    final saved = lookup('${handle['provider']}:$id');
    return saved == null || saved.trim().isEmpty ? null : saved;
  }

  /// Archives [appID], an older record of a conversation whose newer record
  /// the chat list keeps. Only a record the daemon holds quiet is archived,
  /// at most once per gateway; any failure is dropped (the next refresh
  /// tries again). True when the daemon archived it.
  Future<bool> archiveOlderCopy(String appID) async {
    if (_closed ||
        _drafts.contains(appID) ||
        _isSubagent(appID) ||
        _archivedCopies.contains(appID)) {
      return false;
    }
    final agent = _agents[appID];
    if (agent == null) return false;
    switch (agent['status']) {
      case 'idle' || 'closed' || 'error':
        break;
      default:
        return false;
    }
    if (_statuses[appID] == 'busy' || _turnActive.contains(appID)) {
      return false;
    }
    _archivedCopies.add(appID);
    try {
      await deleteSession(appID);
      return true;
    } catch (_) {
      _archivedCopies.remove(appID);
      return false;
    }
  }
}
