part of '../connection.dart';

/// The agents' conversation rows from the last time the list read them, kept
/// on the phone so the list paints them at once while the agents' helper
/// starts (Android stops it with the app). Live rows replace them folder by
/// folder as soon as each folder has been read.
final class _AgentFeedCache {
  _AgentFeedCache(this._prefs);

  final SharedPreferences _prefs;
  static const _max = 200;

  static String _key(String profileID) => 'oc.agentFeed.$profileID';

  static String _usedKey(String profileID) => 'oc.phoneAgentsUsed.$profileID';

  /// Whether this profile's agents had conversations the last time the list
  /// read them (the list then waits for them before its first paint).
  bool usedBefore(String? profileID) {
    if (profileID == null) return false;
    try {
      return _prefs.getBool(_usedKey(profileID)) ?? false;
    } catch (_) {
      return false;
    }
  }

  Future<void> rememberUsed(String? profileID, bool used) async {
    if (profileID == null || used == usedBefore(profileID)) return;
    try {
      await _prefs.setBool(_usedKey(profileID), used);
    } catch (_) {}
  }

  /// Rows by identity, with whether each could be reopened when saved.
  List<({ChatFeedItem item, bool canReopen})> read(String profileID) {
    try {
      final raw = _prefs.getString(_key(profileID));
      if (raw == null) return const [];
      final decoded = jsonDecode(raw);
      if (decoded is! List) return const [];
      return List.unmodifiable([for (final entry in decoded) ?_decode(entry)]);
    } catch (_) {
      return const [];
    }
  }

  Future<void> write(
    String profileID,
    Iterable<({ChatFeedItem item, bool canReopen})> rows,
  ) async {
    final encoded = jsonEncode([
      for (final row in rows.take(_max))
        {
          'sourceId': row.item.sourceId,
          'sessionID': row.item.sessionID,
          'title': row.item.title,
          'directory': row.item.directory,
          'projectName': row.item.projectName,
          'isGit': row.item.isGit,
          'at': row.item.lastActivity.millisecondsSinceEpoch,
          'parentID': row.item.parentID,
          'agentId': row.item.agentId,
          'agentLabel': row.item.agentLabel,
          'sourceLabel': row.item.sourceLabel,
          'canReopen': row.canReopen,
        },
    ]);
    try {
      if (_prefs.getString(_key(profileID)) == encoded) return;
      await _prefs.setString(_key(profileID), encoded);
    } catch (_) {
      // The next read saves again.
    }
  }

  static ({ChatFeedItem item, bool canReopen})? _decode(Object? entry) {
    if (entry is! Map) return null;
    final sourceId = entry['sourceId'];
    final sessionID = entry['sessionID'];
    final directory = entry['directory'];
    final at = entry['at'];
    if (sourceId is! String ||
        !sourceId.startsWith('paseo:') ||
        sessionID is! String ||
        directory is! String ||
        at is! int) {
      return null;
    }
    String? text(String key) =>
        entry[key] is String ? entry[key] as String : null;
    return (
      item: ChatFeedItem(
        sessionID: sessionID,
        title: text('title') ?? 'New chat',
        directory: directory,
        projectName: text('projectName') ?? directory.split('/').last,
        isGit: entry['isGit'] == true,
        // A saved row never claims to be running: that is only known live.
        status: ChatStatus.idle,
        lastActivity: DateTime.fromMillisecondsSinceEpoch(at),
        parentID: text('parentID'),
        agentId: text('agentId') ?? 'paseo',
        agentLabel: text('agentLabel'),
        sourceId: sourceId,
        sourceLabel: text('sourceLabel'),
      ),
      canReopen: entry['canReopen'] == true,
    );
  }
}

/// Claude conversations used to run on the phone profile itself, so their
/// approval choices were saved under it: carried over to the agents' own
/// profile once, without the phone server's "approve everything" (that one
/// is OpenCode's).
Future<void> _carryApprovalChoices(
  SharedPreferences prefs, {
  required String from,
  required String to,
}) async {
  final target = SessionAutoApprovalStore.keyFor(to);
  try {
    if (prefs.containsKey(target)) return;
    final raw = prefs.getString(SessionAutoApprovalStore.keyFor(from));
    final decoded = raw == null ? null : jsonDecode(raw);
    if (decoded is! Map) return;
    decoded.remove(SessionAutoApprovalStore.serverWideKey);
    await prefs.setString(target, jsonEncode(decoded));
  } catch (_) {
    // Conversations ask again; nothing else depends on the copy.
  }
}
