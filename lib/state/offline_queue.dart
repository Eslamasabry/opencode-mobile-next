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

/// What [OfflineQueueStore.enforceLimits] dropped, so the caller can say so
/// instead of letting a prompt vanish between one app start and the next.
class OfflineQueueEviction {
  /// The entries that stay, oldest first.
  final List<QueuedPrompt> kept;

  /// Dropped for being older than [OfflineQueueStore.maxAge].
  final int expired;

  /// Dropped because the queue held more than [OfflineQueueStore.maxEntries].
  final int overflowed;

  /// Dropped because the queue exceeded [OfflineQueueStore.maxTotalBytes].
  final int oversized;

  const OfflineQueueEviction({
    required this.kept,
    this.expired = 0,
    this.overflowed = 0,
    this.oversized = 0,
  });

  int get removed => expired + overflowed + oversized;

  /// One sentence for a snackbar. Null when nothing was dropped.
  String? get notice {
    if (removed == 0) return null;
    final reasons = [
      if (expired > 0) '$expired too old to send',
      if (overflowed + oversized > 0)
        '${overflowed + oversized} to stay within the queue limit',
    ];
    final noun = removed == 1 ? 'draft' : 'drafts';
    return 'Discarded $removed queued $noun: ${reasons.join(' and ')}.';
  }
}

/// Persists queued prompts alongside the app's other preferences. Entries
/// survive restarts; the composer's existing attachment caps bound each
/// entry's size before it ever reaches the queue.
class OfflineQueueWriteException implements Exception {
  const OfflineQueueWriteException();
}

class OfflineQueueStore {
  static const _key = 'oc.offlineQueue';

  /// Matches the composer's aggregate attachment cap; a queue entry can
  /// never legitimately exceed what the composer allowed.
  static const maxEntryBytes = 20 * 1024 * 1024;

  /// Ceiling for everything the queue persists.
  ///
  /// SharedPreferences is one blob the platform reads whole on every app
  /// start, so an unbounded queue is not just disk: it is startup latency
  /// and resident memory on the phone, growing silently because a queued
  /// prompt only leaves when the server comes back. Three maximum-size
  /// entries is already far past what a person composes offline.
  static const maxTotalBytes = 3 * maxEntryBytes;

  /// Ceiling on entry count, independent of size. Text-only prompts are
  /// tiny, so bytes alone would let thousands accumulate.
  static const maxEntries = 50;

  /// How long an unsent draft stays queued.
  ///
  /// A prompt written against a two-week-old working tree is not a send the
  /// user still wants delivered unseen; keeping it forever is a bigger
  /// surprise than dropping it with a notice.
  static const maxAge = Duration(days: 14);

  final SharedPreferences prefs;

  OfflineQueueStore({required this.prefs});

  /// Timestamps below this are not wall-clock times: a missing `createdAt`
  /// decodes to zero, and payloads written by older builds can carry
  /// placeholders. An unreadable timestamp means the age is unknown, and
  /// unknown age must never be grounds for deleting the user's prompt.
  static const _plausibleEpochFloor = 946684800000; // 2000-01-01

  /// Applies the age, count, and byte limits, dropping oldest first.
  ///
  /// Pure and total: the caller persists [OfflineQueueEviction.kept] and
  /// shows [OfflineQueueEviction.notice].
  static OfflineQueueEviction enforceLimits(
    List<QueuedPrompt> entries, {
    DateTime? now,
  }) {
    final at = (now ?? DateTime.now()).millisecondsSinceEpoch;
    final cutoff = at - maxAge.inMilliseconds;
    final ordered = [...entries]
      ..sort((a, b) => a.createdAt.compareTo(b.createdAt));

    final fresh = [
      for (final entry in ordered)
        if (entry.createdAt < _plausibleEpochFloor || entry.createdAt >= cutoff)
          entry,
    ];
    final expired = ordered.length - fresh.length;

    var overflowed = 0;
    if (fresh.length > maxEntries) {
      overflowed = fresh.length - maxEntries;
      fresh.removeRange(0, overflowed);
    }

    var oversized = 0;
    var total = fresh.fold(0, (sum, entry) => sum + entry.payloadBytes);
    while (fresh.length > 1 && total > maxTotalBytes) {
      total -= fresh.removeAt(0).payloadBytes;
      oversized += 1;
    }

    return OfflineQueueEviction(
      kept: fresh,
      expired: expired,
      overflowed: overflowed,
      oversized: oversized,
    );
  }

  List<QueuedPrompt> load() {
    final raw = prefs.getString(_key);
    if (raw == null || raw.isEmpty) return [];
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! List) return [];
      return [for (final entry in decoded) ?QueuedPrompt.fromJson(entry)];
    } catch (_) {
      return [];
    }
  }

  /// Bytes the persisted queue occupies, for the storage readout in
  /// settings. Measures the encoded blob, not the sum of the entries, so it
  /// matches what the device actually holds.
  int storedBytes() => prefs.getString(_key)?.length ?? 0;

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
