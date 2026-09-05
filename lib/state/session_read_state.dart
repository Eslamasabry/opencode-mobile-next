import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

/// Device-only completion watermarks, isolated by saved server and location.
class SessionReadStore {
  SessionReadStore(this.preferences);
  static const prefix = 'oc.sessionViews.';
  static const maxEntries = 2048;
  final SharedPreferences preferences;
  final _profiles = <String, Map<String, int>>{};
  final _writes = <String, Future<void>>{};

  Map<String, int> _load(String profileID) => _profiles.putIfAbsent(
    profileID,
    () {
      try {
        final raw = jsonDecode(
          preferences.getString('$prefix$profileID') ?? '{}',
        );
        if (raw is Map) {
          return {
            for (final entry in raw.entries)
              if (entry.key is String && entry.value is int && entry.value >= 0)
                entry.key as String: entry.value as int,
          };
        }
      } catch (_) {
        // A malformed cache must not prevent reading a session.
      }
      return {};
    },
  );

  int viewed(String profileID, String key) => _load(profileID)[key] ?? 0;

  Future<void> record(String profileID, String key, int idle) async {
    final entries = _load(profileID);
    if ((entries[key] ?? 0) >= idle) return;
    entries[key] = idle;
    if (entries.length > maxEntries) {
      final oldest = entries.keys.toList()
        ..sort((a, b) => entries[a]!.compareTo(entries[b]!));
      for (final key in oldest.take(entries.length - maxEntries)) {
        entries.remove(key);
      }
    }
    // Retain the in-memory watermark if storage fails; callers can still show
    // truthful read state for this process. Never turn a cache failure into a
    // server acknowledgement or drop the user's transcript.
    final snapshot = jsonEncode(entries);
    final previous = _writes[profileID] ?? Future<void>.value();
    final writing = previous.catchError((Object _) {}).then((_) async {
      try {
        await preferences.setString('$prefix$profileID', snapshot);
      } catch (_) {
        // Keep reading usable even when platform storage rejects this write.
        // A later record writes the complete retained in-memory snapshot.
      }
    });
    _writes[profileID] = writing;
    try {
      await writing;
    } finally {
      if (identical(_writes[profileID], writing)) _writes.remove(profileID);
    }
  }

  Future<void> drain(String profileID) => _writes[profileID] ?? Future.value();

  void forgetProfile(String profileID) => _profiles.remove(profileID);
}
