import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

/// Successful idle-history watermarks, never transcript or card content.
/// Missing/corrupt/evicted entries simply require another bounded recovery.
final class GenUiCoverage {
  GenUiCoverage(this.prefs);
  final SharedPreferences prefs;
  final _closed = <String>{};
  Future<void> _writes = Future.value();
  static String key(String profile) => 'oc.genui.covered.$profile';

  Map<String, String> _read(String profile) {
    try {
      final text = prefs.getString(key(profile));
      if (text == null || text.length > 262144) return {};
      final raw = jsonDecode(text);
      if (raw is! Map || raw['v'] != 1 || raw['entries'] is! Map) return {};
      final entries = raw['entries'] as Map;
      if (entries.length > 200 ||
          entries.entries.any((e) => e.key is! String || e.value is! String)) {
        return {};
      }
      return Map<String, String>.from(entries);
    } catch (_) {
      return {};
    }
  }

  bool contains(String profile, String identity, String stamp) =>
      !_closed.contains(profile) && _read(profile)[identity] == stamp;

  Future<void> remember(String profile, String identity, String stamp) {
    final work = _writes.then((_) async {
      if (_closed.contains(profile)) return;
      final entries = _read(profile)..remove(identity);
      entries[identity] = stamp;
      while (entries.length > 200) {
        entries.remove(entries.keys.first);
      }
      var text = jsonEncode({'v': 1, 'entries': entries});
      while (text.length > 262144 && entries.isNotEmpty) {
        entries.remove(entries.keys.first);
        text = jsonEncode({'v': 1, 'entries': entries});
      }
      await prefs.setString(key(profile), text);
    });
    _writes = work.then<void>((_) {}, onError: (Object _) {});
    return work;
  }

  Future<void> closeProfile(String profile) {
    _closed.add(profile);
    return _writes;
  }

  void reopenProfile(String profile) => _closed.remove(profile);
}
