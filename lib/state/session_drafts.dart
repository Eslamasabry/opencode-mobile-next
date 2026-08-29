import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

/// Composer text drafted in a chat session but never sent, keyed by session.
///
/// Unlike the offline queue — which snapshots a *send* the user asked for —
/// a draft is text still being written. It survives navigating away from the
/// chat and app restarts, and disappears once the text is sent or deleted.
class SessionDraft {
  final String sessionID;
  final String text;
  final int updatedAt;

  const SessionDraft({
    required this.sessionID,
    required this.text,
    required this.updatedAt,
  });

  Map<String, dynamic> toJson() => {
    'sessionID': sessionID,
    'text': text,
    'updatedAt': updatedAt,
  };

  static SessionDraft? fromJson(Object? value) {
    if (value is! Map) return null;
    final sessionID = value['sessionID']?.toString() ?? '';
    final text = value['text']?.toString() ?? '';
    if (sessionID.isEmpty || text.isEmpty) return null;
    return SessionDraft(
      sessionID: sessionID,
      text: text,
      updatedAt: (value['updatedAt'] as num?)?.toInt() ?? 0,
    );
  }
}

/// Persists per-session composer drafts alongside the app's other
/// preferences, mirroring [OfflineQueueStore]'s storage pattern. At most
/// [maxDrafts] sessions are kept; when the cap is exceeded the oldest
/// drafts are evicted first (newest wins).
class SessionDraftStore {
  static const _key = 'oc.sessionDrafts';

  /// Bounds storage: drafts are plain text, so 50 sessions stay tiny.
  static const maxDrafts = 50;

  final SharedPreferences prefs;

  SessionDraftStore({required this.prefs});

  Map<String, SessionDraft> load() {
    final raw = prefs.getString(_key);
    if (raw == null || raw.isEmpty) return {};
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! List) return {};
      final drafts = <String, SessionDraft>{};
      for (final entry in decoded) {
        final draft = SessionDraft.fromJson(entry);
        if (draft != null) drafts[draft.sessionID] = draft;
      }
      return drafts;
    } catch (_) {
      return {};
    }
  }

  Future<bool> save(Map<String, SessionDraft> drafts) async {
    try {
      if (drafts.isEmpty) return await prefs.remove(_key);
      final entries = drafts.values.toList()
        ..sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
      if (entries.length > maxDrafts) {
        entries.removeRange(maxDrafts, entries.length);
      }
      return await prefs.setString(
        _key,
        jsonEncode([for (final entry in entries) entry.toJson()]),
      );
    } catch (_) {
      return false;
    }
  }
}
