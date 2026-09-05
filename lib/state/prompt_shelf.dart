import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../api/models.dart';
import 'review_handoff.dart';

/// A deliberate saved prompt, separate from autosaved session draft text.
class StashedPrompt {
  const StashedPrompt({
    required this.id,
    required this.text,
    required this.createdAt,
    this.directory,
    this.workspace,
    this.attachments = const [],
    this.references = const [],
  });

  final String id;
  final String text;
  final int createdAt;
  final String? directory;
  final String? workspace;
  final List<PromptAttachment> attachments;
  final List<ReviewReference> references;

  bool get isEmpty => text.isEmpty && attachments.isEmpty && references.isEmpty;
  bool get locationBound =>
      references.isNotEmpty ||
      attachments.any((a) => Uri.tryParse(a.url)?.scheme == 'file');

  /// Data URLs survive restart; file URLs refer to the server's filesystem.
  /// Temporary browser/Android grants cannot be reused as server attachments.
  List<String> get unavailableAttachments => [
    for (final a in attachments)
      if (!canRestoreAttachment(a)) a.filename,
  ];

  static bool canRestoreAttachment(PromptAttachment attachment) => {
    'data',
    'file',
    'http',
    'https',
  }.contains(Uri.tryParse(attachment.url)?.scheme);

  Map<String, dynamic> toJson() => {
    'id': id,
    'text': text,
    'createdAt': createdAt,
    'directory': directory,
    'workspace': workspace,
    'attachments': [for (final a in attachments) a.toJson()],
    'references': [
      for (final r in references)
        {
          'id': r.id,
          'kind': r.kind.name,
          'path': r.path,
          'scope': r.scope.name,
          'lineLabel': r.lineLabel,
          'snippet': r.snippet,
          'comment': r.comment,
          'added': r.added,
          'removed': r.removed,
          'status': r.status,
        },
    ],
  };

  factory StashedPrompt.fromJson(Map<String, dynamic> value) => StashedPrompt(
    id: value['id'] as String,
    text: value['text'] as String,
    createdAt: (value['createdAt'] as num).toInt(),
    directory: value['directory'] as String?,
    workspace: value['workspace'] as String?,
    attachments: [
      for (final a in value['attachments'] as List)
        PromptAttachment(
          mime: a['mime'] as String,
          filename: a['filename'] as String,
          url: a['url'] as String,
        ),
    ],
    references: [
      for (final r in value['references'] as List)
        ReviewReference(
          id: r['id'] as String,
          kind: ReviewReferenceKind.values.byName(r['kind'] as String),
          path: r['path'] as String,
          scope: ReviewReferenceScope.values.byName(r['scope'] as String),
          lineLabel: r['lineLabel'] as String?,
          snippet: r['snippet'] as String?,
          comment: r['comment'] as String?,
          added: (r['added'] as num?)?.toInt(),
          removed: (r['removed'] as num?)?.toInt(),
          status: r['status'] as String?,
        ),
    ],
  );
}

class PromptShelfStore {
  PromptShelfStore(this.preferences);
  final SharedPreferences preferences;
  static const capacity = 50;
  final _stashes = <String, List<StashedPrompt>>{};
  final _history = <String, List<String>>{};
  final _writes = <String, Future<void>>{};

  List<StashedPrompt> stashes(String profile) => List.unmodifiable(
    _stashes.putIfAbsent(profile, () {
      final prefix = 'oc.promptStash.$profile.';
      // Refuse a corrupt shelf instead of silently dropping saved content.
      final entries = [
        for (final key in preferences.getKeys())
          if (key.startsWith(prefix))
            StashedPrompt.fromJson(
              jsonDecode(preferences.getString(key)!) as Map<String, dynamic>,
            ),
      ];
      entries.sort((a, b) => b.createdAt.compareTo(a.createdAt));
      return entries;
    }),
  );

  List<String> history(String profile) => List.unmodifiable(
    _history.putIfAbsent(
      profile,
      () =>
          (jsonDecode(
                    preferences.getString('oc.promptHistory.$profile') ?? '[]',
                  )
                  as List)
              .cast<String>(),
    ),
  );

  Future<void> _write(String profile, Future<void> Function() action) async {
    final next = (_writes[profile] ?? Future<void>.value())
        .catchError((Object _) {})
        .then((_) => action());
    _writes[profile] = next;
    try {
      await next;
    } finally {
      if (identical(_writes[profile], next)) _writes.remove(profile);
    }
  }

  Future<void> stash(String profile, StashedPrompt prompt) =>
      _write(profile, () async {
        final current = stashes(profile);
        if (current.any((p) => p.id == prompt.id)) {
          throw StateError('Duplicate stash');
        }
        if (current.length >= capacity) throw StateError('Stash is full');
        if (!await preferences.setString(
          'oc.promptStash.$profile.${prompt.id}',
          jsonEncode(prompt.toJson()),
        )) {
          throw StateError('Could not save prompt');
        }
        _stashes[profile] = [prompt, ...current];
      });

  Future<void> remove(String profile, String id) => _write(profile, () async {
    final current = stashes(profile);
    if (!await preferences.remove('oc.promptStash.$profile.$id')) {
      throw StateError('Could not remove prompt');
    }
    _stashes[profile] = current.where((p) => p.id != id).toList();
  });

  Future<void> recordSent(String profile, String text) =>
      _write(profile, () async {
        if (text.trim().isEmpty) return;
        final next = [
          text,
          ...history(profile).where((p) => p != text),
        ].take(capacity).toList();
        if (!await preferences.setString(
          'oc.promptHistory.$profile',
          jsonEncode(next),
        )) {
          throw StateError('Could not save prompt history');
        }
        _history[profile] = next;
      });

  Future<void> drain(String profile) =>
      _writes[profile] ?? Future<void>.value();
  void forget(String profile) {
    _stashes.remove(profile);
    _history.remove(profile);
  }
}
