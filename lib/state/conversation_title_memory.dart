import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

/// The title of a conversation whose older helper records were archived,
/// per saved server, keyed by the runtime's session (`provider:sessionId`),
/// not by the helper's agent id: the record that carried the title is gone,
/// and the conversation's row and header keep saying it.
///
/// Kept under `oc.conversationTitles.<profileId>` (one JSON entry per
/// conversation, newest last), so deleting the server sweeps it
/// (ProfileStore's scoped-key sweep).
abstract final class ConversationTitleMemory {
  static const maxEntries = 300;

  static String keyFor(String profileId) => 'oc.conversationTitles.$profileId';

  static List<Map<String, Object?>> _read(
    SharedPreferences prefs,
    String profileId,
  ) {
    try {
      return [
        for (final raw in prefs.getStringList(keyFor(profileId)) ?? const [])
          if (jsonDecode(raw) case final Map<String, Object?> entry) entry,
      ];
    } catch (_) {
      return [];
    }
  }

  static String? read(
    SharedPreferences prefs,
    String profileId,
    String sessionKey,
  ) {
    for (final entry in _read(prefs, profileId).reversed) {
      if (entry['k'] != sessionKey) continue;
      final title = entry['t'];
      return title is String && title.trim().isNotEmpty ? title : null;
    }
    return null;
  }

  /// Saves [title] for [sessionKey]. False when storage refused it.
  static Future<bool> remember(
    SharedPreferences prefs,
    String profileId,
    String sessionKey,
    String title,
  ) async {
    if (title.trim().isEmpty) return false;
    var kept = _read(prefs, profileId)
      ..removeWhere((entry) => entry['k'] == sessionKey)
      ..add({'k': sessionKey, 't': title});
    if (kept.length > maxEntries) {
      kept = kept.sublist(kept.length - maxEntries);
    }
    try {
      return await prefs.setStringList(keyFor(profileId), [
        for (final entry in kept) jsonEncode(entry),
      ]);
    } catch (_) {
      return false;
    }
  }
}
