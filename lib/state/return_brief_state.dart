import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../domain/return_brief.dart';

/// Exact local dismissals. A failed write never publishes an acknowledgement.
/// Controller deletion drains this queue before sweeping profile keys.
class ReturnBriefStore {
  ReturnBriefStore(this.preferences);
  static const prefix = 'oc.returnBrief.';
  static const maxLocations = 64;
  final SharedPreferences preferences;
  final _profiles = <String, Map<String, ReturnBriefAck>>{};
  final _writes = <String, Future<void>>{};

  Map<String, ReturnBriefAck> _load(String profile) =>
      _profiles.putIfAbsent(profile, () {
        try {
          final raw = jsonDecode(
            preferences.getString('$prefix$profile') ?? '{}',
          );
          if (raw is Map) {
            final entries = <String, ReturnBriefAck>{};
            for (final entry in raw.entries.take(maxLocations)) {
              final ack = ReturnBriefAck.fromJson(entry.value);
              if (entry.key is String && ack != null) {
                entries[entry.key as String] = ack;
              }
            }
            return entries;
          }
        } catch (_) {
          // Corrupt or absent data means never dismissed.
        }
        return {};
      });

  ReturnBriefAck acknowledged(String profile, String location) =>
      _load(profile)[location] ?? ReturnBriefAck.empty;

  Future<void> acknowledge(
    String profile,
    String location,
    ReturnBrief shown,
  ) async {
    // Capture before joining the write queue. Only these visible identities
    // may be hidden, even when an event or page arrives during the save.
    final snapshot = shown.acknowledgement(null);
    final previous = _writes[profile] ?? Future<void>.value();
    final writing = previous.catchError((Object _) {}).then((_) async {
      final entries = Map<String, ReturnBriefAck>.of(_load(profile));
      final ack = (entries.remove(location) ?? ReturnBriefAck.empty).merge(
        shownRuns: snapshot.runs,
        shownRequestIDs: snapshot.requestIDs,
      );
      entries[location] = ack;
      while (entries.length > maxLocations) {
        entries.remove(entries.keys.first);
      }
      try {
        final saved = await preferences.setString(
          '$prefix$profile',
          jsonEncode({
            for (final entry in entries.entries)
              entry.key: entry.value.toJson(),
          }),
        );
        if (!saved) throw StateError('Brief dismissal could not be saved');
      } catch (_) {
        // SharedPreferences mutates its cache before the platform responds.
        // Refresh it so a new store cannot mistake a refused write for a save.
        try {
          await preferences.reload();
        } catch (_) {}
        rethrow;
      }
      _profiles[profile] = entries;
    });
    _writes[profile] = writing;
    try {
      await writing;
    } finally {
      if (identical(_writes[profile], writing)) _writes.remove(profile);
    }
  }

  Future<void> drain(String profile) =>
      _writes[profile] ?? Future<void>.value();
  void forgetProfile(String profile) => _profiles.remove(profile);
}
