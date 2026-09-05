import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';
import 'draft_attachments.dart';

enum SessionDraftFailure { storage, full, profileRemoved, attachments }

class SessionDraftWriteException implements Exception {
  const SessionDraftWriteException(this.failure);
  final SessionDraftFailure failure;
}

/// Composer text drafted in a chat session but never sent, scoped by server
/// profile and session identity.
///
/// Unlike the offline queue — which snapshots a *send* the user asked for —
/// a draft is text still being written. It survives navigating away from the
/// chat and app restarts, and disappears once the text is sent or deleted.
class SessionDraft {
  final String sessionID;

  /// The server profile the drafted session belongs to, so removing that
  /// server can take its drafts with it. Drafts written before this field
  /// existed carry `''` and are treated as unattributable — see
  /// [SessionDraftStore.withoutProfile].
  final String profileID;
  final String text;
  final int updatedAt;
  final List<DraftAttachmentRef> attachments;
  final String? directory;
  final String? workspace;

  const SessionDraft({
    required this.sessionID,
    this.profileID = '',
    required this.text,
    required this.updatedAt,
    this.attachments = const [],
    this.directory,
    this.workspace,
  });

  String get storageKey => keyFor(profileID, sessionID);
  static String keyFor(String profileID, String sessionID) =>
      profileID.isEmpty ? sessionID : jsonEncode([profileID, sessionID]);

  Map<String, dynamic> toJson() => {
    'sessionID': sessionID,
    if (profileID.isNotEmpty) 'profileID': profileID,
    'text': text,
    'updatedAt': updatedAt,
    if (attachments.isNotEmpty)
      'attachments': [for (final a in attachments) a.toJson()],
    if (attachments.isNotEmpty) 'directory': directory,
    if (attachments.isNotEmpty) 'workspace': workspace,
  };

  static SessionDraft? fromJson(Object? value) {
    if (value is! Map) return null;
    final sessionID = value['sessionID']?.toString() ?? '';
    final text = value['text']?.toString() ?? '';
    final attachments = <DraftAttachmentRef>[
      for (final a in value['attachments'] as List? ?? const [])
        DraftAttachmentRef.fromJson(Map<String, dynamic>.from(a as Map)),
    ];
    if (sessionID.isEmpty || (text.isEmpty && attachments.isEmpty)) return null;
    return SessionDraft(
      sessionID: sessionID,
      profileID: value['profileID']?.toString() ?? '',
      text: text,
      updatedAt: (value['updatedAt'] as num?)?.toInt() ?? 0,
      attachments: attachments,
      directory: value['directory'] as String?,
      workspace: value['workspace'] as String?,
    );
  }
}

/// Persists per-session composer drafts alongside the app's other
/// preferences. At most [maxDrafts] sessions are kept. A full store refuses
/// new entries instead of silently removing another unsent draft.
class SessionDraftStore {
  static const _key = 'oc.sessionDrafts';

  /// Bounds the number of drafts. Attachment payloads are not stored here.
  static const maxDrafts = 50;

  final SharedPreferences prefs;
  bool _readFailed = false;
  bool get readable {
    load();
    return !_readFailed;
  }

  SessionDraftStore({required this.prefs});

  /// Bytes the persisted drafts occupy, for the storage readout in settings.
  int storedBytes() {
    final payloads = <String, int>{};
    for (final draft in load().values) {
      for (final ref in draft.attachments) {
        if (ref.blob != null) {
          payloads['${draft.profileID}/${ref.blob}'] = ref.bytes;
        }
      }
    }
    return utf8.encode(prefs.getString(_key) ?? '').length +
        payloads.values.fold(0, (a, b) => a + b);
  }

  Map<String, SessionDraft> load() {
    _readFailed = false;
    final raw = prefs.getString(_key);
    if (raw == null || raw.isEmpty) return {};
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! List) throw const FormatException('Invalid draft index');
      final drafts = <String, SessionDraft>{};
      for (final entry in decoded) {
        final draft = SessionDraft.fromJson(entry);
        if (draft == null || drafts.containsKey(draft.storageKey)) {
          throw const FormatException('Invalid draft entry');
        }
        drafts[draft.storageKey] = draft;
      }
      return drafts;
    } catch (_) {
      _readFailed = true;
      return {};
    }
  }

  /// [drafts] minus everything owned by [profileID].
  ///
  /// A draft written before drafts recorded their profile has no owner this
  /// app can identify, and a draft belongs to exactly one server's session.
  /// Removing a server is an explicit "delete this server's local data"
  /// request, so unattributable drafts go with it rather than lingering as
  /// text the user believes they deleted.
  static Map<String, SessionDraft> withoutProfile(
    Map<String, SessionDraft> drafts,
    String profileID,
  ) => {
    for (final entry in drafts.entries)
      if (entry.value.profileID.isNotEmpty &&
          entry.value.profileID != profileID)
        entry.key: entry.value,
  };

  Future<bool> save(Map<String, SessionDraft> drafts) async {
    try {
      if (!readable) return false;
      if (drafts.length > maxDrafts) return false;
      final entries = drafts.values.toList()
        ..sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
      final saved = drafts.isEmpty
          ? await prefs.remove(_key)
          : await prefs.setString(
              _key,
              jsonEncode([for (final entry in entries) entry.toJson()]),
            );
      // SharedPreferences updates its cache before the platform write returns.
      // A refused write must not look successful to another store reader.
      if (!saved) await prefs.reload();
      return saved;
    } catch (_) {
      try {
        await prefs.reload();
      } catch (_) {}
      return false;
    }
  }
}
