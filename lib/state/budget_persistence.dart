import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

/// Serialize per-profile preference writes across replacement screens. Merge
/// only this action's changed rules after reloading durable state, never a stale
/// screen's full map. Failed platform writes must also roll back the prefs cache.
class BudgetPersistence {
  static final Map<String, Future<void>> _pending = {};

  static Future<Map<String, dynamic>?> update({
    required SharedPreferences preferences,
    required String key,
    required Map<String, dynamic> changes,
    required bool Function() isCurrent,
    required bool Function() isProfilePresent,
    bool clear = false,
  }) async {
    final previous = _pending[key] ?? Future<void>.value();
    final work = previous.then((_) async {
      if (!isCurrent()) return null;
      try {
        await preferences.reload();
        if (!isCurrent()) return null;
        final raw = preferences.getString(key);
        Map<String, dynamic> rules = {};
        if (raw != null && !clear) {
          if (raw.length > 65536) throw const FormatException();
          final data = jsonDecode(raw) as Map<String, dynamic>;
          if (data['version'] != 1) throw const FormatException();
          rules = Map<String, dynamic>.of(
            data['rules'] as Map<String, dynamic>,
          );
        }
        for (final entry in changes.entries) {
          if (entry.value == null) {
            rules.remove(entry.key);
          } else {
            rules[entry.key] = entry.value;
          }
        }
        if (rules.length > 64) throw const FormatException();
        final success = rules.isEmpty
            ? await preferences.remove(key)
            : await preferences.setString(
                key,
                jsonEncode({'version': 1, 'rules': rules}),
              );
        if (!isProfilePresent()) {
          await preferences.remove(key);
          await preferences.reload();
          return null;
        }
        if (success) return rules;
      } catch (_) {
        // No raw platform, stored content, or credential diagnostics.
      }
      try {
        await preferences.reload();
      } catch (_) {}
      return null;
    });
    final tail = work.then<void>((_) {}, onError: (_) {});
    _pending[key] = tail;
    try {
      return await work;
    } finally {
      if (identical(_pending[key], tail)) _pending.remove(key);
    }
  }
}
