import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../api/models.dart';

/// One prompt drafted while the server was unreachable, waiting to send.
///
/// Entries snapshot everything the send needs — text, attachments (their
/// data/file URLs are self-contained), agent mentions, and the exact
/// model/agent/variant selected when the user pressed send — so a later
/// flush reproduces the send the user asked for, not the settings of the
/// moment the connection returned.
class QueuedPrompt {
  final String id;
  final String profileID;
  final String sessionID;
  final String text;
  final List<PromptAttachment> attachments;
  final List<PromptAgentMention> mentions;
  final String? modelProviderID;
  final String? modelID;
  final String? agent;
  final String? variant;
  final int createdAt;

  /// The last declared server failure from a flush attempt, shown inline.
  final String? error;

  const QueuedPrompt({
    required this.id,
    required this.profileID,
    required this.sessionID,
    required this.text,
    this.attachments = const [],
    this.mentions = const [],
    this.modelProviderID,
    this.modelID,
    this.agent,
    this.variant,
    required this.createdAt,
    this.error,
  });

  QueuedPrompt withError(String? error) => QueuedPrompt(
    id: id,
    profileID: profileID,
    sessionID: sessionID,
    text: text,
    attachments: attachments,
    mentions: mentions,
    modelProviderID: modelProviderID,
    modelID: modelID,
    agent: agent,
    variant: variant,
    createdAt: createdAt,
    error: error,
  );

  ModelRef? get model => modelProviderID != null && modelID != null
      ? ModelRef(providerID: modelProviderID!, modelID: modelID!)
      : null;

  /// Total bytes this entry persists, dominated by attachment URLs.
  int get payloadBytes =>
      text.length +
      attachments.fold(0, (sum, attachment) => sum + attachment.url.length);

  Map<String, dynamic> toJson() => {
    'id': id,
    'profileID': profileID,
    'sessionID': sessionID,
    'text': text,
    'attachments': [
      for (final attachment in attachments)
        {
          'mime': attachment.mime,
          'filename': attachment.filename,
          'url': attachment.url,
        },
    ],
    'mentions': [
      for (final mention in mentions)
        {
          'name': mention.name,
          'value': mention.value,
          'start': mention.start,
          'end': mention.end,
        },
    ],
    'modelProviderID': modelProviderID,
    'modelID': modelID,
    'agent': agent,
    'variant': variant,
    'createdAt': createdAt,
    'error': error,
  };

  static QueuedPrompt? fromJson(Object? value) {
    if (value is! Map) return null;
    final id = value['id']?.toString() ?? '';
    final profileID = value['profileID']?.toString() ?? '';
    final sessionID = value['sessionID']?.toString() ?? '';
    if (id.isEmpty || profileID.isEmpty || sessionID.isEmpty) return null;
    return QueuedPrompt(
      id: id,
      profileID: profileID,
      sessionID: sessionID,
      text: value['text']?.toString() ?? '',
      attachments: [
        if (value['attachments'] is List)
          for (final raw in value['attachments'] as List)
            if (raw is Map)
              PromptAttachment(
                mime: raw['mime']?.toString() ?? '',
                filename: raw['filename']?.toString() ?? '',
                url: raw['url']?.toString() ?? '',
              ),
      ],
      mentions: [
        if (value['mentions'] is List)
          for (final raw in value['mentions'] as List)
            if (raw is Map)
              PromptAgentMention(
                name: raw['name']?.toString() ?? '',
                value: raw['value']?.toString() ?? '',
                start: (raw['start'] as num?)?.toInt() ?? 0,
                end: (raw['end'] as num?)?.toInt() ?? 0,
              ),
      ],
      modelProviderID: value['modelProviderID']?.toString(),
      modelID: value['modelID']?.toString(),
      agent: value['agent']?.toString(),
      variant: value['variant']?.toString(),
      createdAt: (value['createdAt'] as num?)?.toInt() ?? 0,
      error: value['error']?.toString(),
    );
  }
}

/// Persists queued prompts alongside the app's other preferences. Entries
/// survive restarts; the composer's existing attachment caps bound each
/// entry's size before it ever reaches the queue.
class OfflineQueueStore {
  static const _key = 'oc.offlineQueue';

  /// Matches the composer's aggregate attachment cap; a queue entry can
  /// never legitimately exceed what the composer allowed.
  static const maxEntryBytes = 20 * 1024 * 1024;

  final SharedPreferences prefs;

  OfflineQueueStore({required this.prefs});

  List<QueuedPrompt> load() {
    final raw = prefs.getString(_key);
    if (raw == null || raw.isEmpty) return [];
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! List) return [];
      return [
        for (final entry in decoded) ?QueuedPrompt.fromJson(entry),
      ];
    } catch (_) {
      return [];
    }
  }

  Future<bool> save(List<QueuedPrompt> entries) async {
    try {
      if (entries.isEmpty) return await prefs.remove(_key);
      return await prefs.setString(
        _key,
        jsonEncode([for (final entry in entries) entry.toJson()]),
      );
    } catch (_) {
      return false;
    }
  }
}
