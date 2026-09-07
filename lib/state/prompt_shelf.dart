import 'dart:convert';
import 'dart:io';

import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../api/models.dart';
import 'draft_attachments.dart';
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
    this.attachmentRefs = const [],
    this.references = const [],
  });

  final String id;
  final String text;
  final int createdAt;
  final String? directory;
  final String? workspace;
  final List<PromptAttachment> attachments;
  final List<DraftAttachmentRef> attachmentRefs;
  final List<ReviewReference> references;

  int get attachmentCount => attachments.length + attachmentRefs.length;
  Iterable<String> get attachmentNames => [
    ...attachments.map((attachment) => attachment.filename),
    ...attachmentRefs.map((attachment) => attachment.filename),
  ];
  bool get isEmpty =>
      text.isEmpty && attachmentCount == 0 && references.isEmpty;
  bool get locationBound =>
      references.isNotEmpty ||
      attachments.any((a) => Uri.tryParse(a.url)?.scheme == 'file') ||
      attachmentRefs.any((a) => Uri.tryParse(a.url ?? '')?.scheme == 'file');

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
    if (attachmentRefs.isNotEmpty) ...{
      'attachmentFormat': 1,
      'attachmentRefs': [for (final a in attachmentRefs) a.toJson()],
    },
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

  factory StashedPrompt.fromJson(Map<String, dynamic> value) {
    final refs = value['attachmentRefs'];
    if ((value.containsKey('attachmentRefs') ||
            value.containsKey('attachmentFormat')) &&
        (value['attachmentFormat'] != 1 || refs is! List)) {
      throw const FormatException('Unsupported stash attachment metadata');
    }
    final inline = value['attachments'] as List;
    if (refs != null && inline.isNotEmpty) {
      throw const FormatException('Ambiguous stash attachments');
    }
    return StashedPrompt(
      id: value['id'] as String,
      text: value['text'] as String,
      createdAt: (value['createdAt'] as num).toInt(),
      directory: value['directory'] as String?,
      workspace: value['workspace'] as String?,
      attachments: [
        for (final a in inline)
          PromptAttachment(
            mime: a['mime'] as String,
            filename: a['filename'] as String,
            url: a['url'] as String,
          ),
      ],
      attachmentRefs: [
        for (final a in refs as List? ?? const [])
          DraftAttachmentRef.fromJson((a as Map).cast<String, dynamic>()),
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

  StashedPrompt _snapshot({List<DraftAttachmentRef>? fileRefs}) =>
      StashedPrompt(
        id: id,
        text: text,
        createdAt: createdAt,
        directory: directory,
        workspace: workspace,
        attachments: fileRefs == null
            ? List.unmodifiable(attachments)
            : const [],
        attachmentRefs: List.unmodifiable(fileRefs ?? attachmentRefs),
        references: List.unmodifiable(references),
      );
}

class PromptShelfStore {
  // Retained for legacy consumers; the live controller uses attachment files.
  PromptShelfStore(this.preferences) : _attachmentVault = null;

  /// Never share the ordinary draft vault root: its
  /// collector has no knowledge of stash references and would erase this data.
  PromptShelfStore.withAttachmentFiles(
    this.preferences, {
    DraftAttachmentVault? vault,
  }) : _attachmentVault =
           vault ??
           DraftAttachmentVault(
             directory: () async => Directory(
               p.join(
                 (await getApplicationSupportDirectory()).path,
                 attachmentDirectoryName,
               ),
             ),
           );

  final SharedPreferences preferences;
  final DraftAttachmentVault? _attachmentVault;
  static const attachmentDirectoryName = 'prompt-stash-attachments-v1';
  static String attachmentOwnerKey(String profile) =>
      'oc.stashAttachmentVault.$profile';
  static const capacity = 50;
  // Deliberate stash limits, independent of the ordinary draft/photo roots.
  // Legacy records above these limits remain inline and are never truncated.
  static const maxAttachments = 5;
  static const maxAttachmentBytes = DraftAttachmentVault.maxDraftBytes;
  static const maxDiskBytes = DraftAttachmentVault.maxDiskBytes;
  static final _blobID = RegExp(r'^[0-9a-f]{64}$');
  final _stashes = <String, List<StashedPrompt>>{};
  final _history = <String, List<String>>{};
  final _writes = <String, Future<void>>{};
  Future<void> _changes = Future<void>.value();
  bool _metadataCertain = true;

  List<StashedPrompt> _readStashes(String profile) {
    final prefix = 'oc.promptStash.$profile.';
    final entries = <StashedPrompt>[];
    for (final key in preferences.getKeys().where(
      (key) => key.startsWith(prefix),
    )) {
      final prompt = StashedPrompt.fromJson(
        jsonDecode(preferences.getString(key)!) as Map<String, dynamic>,
      );
      if (prompt.id.isEmpty || key != '$prefix${prompt.id}') {
        throw const FormatException('Stash identity changed');
      }
      entries.add(prompt._snapshot());
    }
    entries.sort((a, b) => b.createdAt.compareTo(a.createdAt));
    return entries;
  }

  List<StashedPrompt> stashes(String profile) {
    if (!_metadataCertain) throw StateError('Prompt storage must be reloaded');
    // Refuse a corrupt shelf instead of silently dropping saved content.
    final prompts = _stashes.putIfAbsent(profile, () => _readStashes(profile));
    if (_attachmentVault == null &&
        prompts.any((prompt) => prompt.attachmentRefs.isNotEmpty)) {
      throw StateError('Attachment storage is not enabled');
    }
    return List.unmodifiable(prompts);
  }

  List<String> history(String profile) {
    if (!_metadataCertain) throw StateError('Prompt storage must be reloaded');
    return List.unmodifiable(
      _history.putIfAbsent(
        profile,
        () =>
            (jsonDecode(
                      preferences.getString('oc.promptHistory.$profile') ??
                          '[]',
                    )
                    as List)
                .cast<String>(),
      ),
    );
  }

  Future<T> _write<T>(
    String profile,
    Future<T> Function() action, {
    void Function()? checkCurrent,
  }) async {
    // The disk budget spans profiles, so serialize complete transactions and
    // reads that can race collection, not only individual preference writes.
    final next = _changes.then((_) async {
      checkCurrent?.call();
      if (!_metadataCertain) await _reloadMetadata();
      checkCurrent?.call();
      return action();
    });
    final settled = next.then<void>(
      (_) {},
      onError: (Object _, StackTrace _) {},
    );
    _changes = settled;
    _writes[profile] = settled;
    try {
      return await next;
    } finally {
      if (identical(_writes[profile], settled)) _writes.remove(profile);
    }
  }

  Future<void> _reloadMetadata() async {
    _metadataCertain = false;
    await preferences.reload();
    _stashes.clear();
    _history.clear();
    _metadataCertain = true;
  }

  /// Reconcile after a wider profile sweep, including optimistic-cache refusal.
  Future<void> reload() => _write('', _reloadMetadata);

  Future<void> _commit(
    Future<bool> Function() change, {
    void Function()? checkCurrent,
  }) async {
    checkCurrent?.call();
    try {
      if (!_metadataCertain) await _reloadMetadata();
      checkCurrent?.call();
      if (!await change()) {
        throw StateError('Prompt storage refused the change');
      }
    } catch (_) {
      // SharedPreferences updates its local cache before the backend answers.
      // Never collect files using that optimistic cache after a failed write.
      _metadataCertain = false;
      try {
        await _reloadMetadata();
      } catch (_) {}
      throw StateError('Could not update prompt storage');
    }
  }

  Future<void> _ensureAttachmentOwner(String profile) async {
    if (_attachmentVault == null) {
      throw StateError('Attachment storage is not enabled');
    }
    if (!_metadataCertain) await _reloadMetadata();
    if (preferences.get(attachmentOwnerKey(profile)) == true) return;
    // This durable breadcrumb must precede publication, including publication
    // that later fails to acquire metadata. A restart can then find orphans.
    await _commit(() => preferences.setBool(attachmentOwnerKey(profile), true));
  }

  Future<void> _adoptExistingAttachmentFiles(String profile) async {
    if (preferences.containsKey(attachmentOwnerKey(profile))) {
      await _ensureAttachmentOwner(profile);
      return;
    }
    final bool mayHaveFiles;
    try {
      mayHaveFiles = _readStashes(
        profile,
      ).any((prompt) => prompt.attachmentRefs.any((ref) => ref.blob != null));
    } catch (_) {
      // Explicit deletion may erase corrupt metadata, but must not erase the
      // only remaining evidence that an older file-backed record owned bytes.
      await _ensureAttachmentOwner(profile);
      return;
    }
    if (mayHaveFiles) await _ensureAttachmentOwner(profile);
  }

  Future<bool> _collect(String profile) async {
    final vault = _attachmentVault;
    if (vault == null) return true;
    if (!_metadataCertain) return false;
    try {
      final refs = _readStashes(
        profile,
      ).expand((prompt) => prompt.attachmentRefs).toList();
      for (final ref in refs) {
        if (ref.blob != null &&
            (ref.url != null || !_blobID.hasMatch(ref.blob!))) {
          return false;
        }
        if (ref.blob == null && ref.url == null) return false;
      }
      if (refs.any((ref) => ref.blob != null)) {
        await _ensureAttachmentOwner(profile);
      }
      // Empty/text/URL-only shelves have never published files. In particular,
      // do not ask path_provider for the default root just to collect nothing.
      if (!preferences.containsKey(attachmentOwnerKey(profile))) return true;
      return await vault.collect({profile: refs}, owner: profile);
    } catch (_) {
      // Unknown/corrupt metadata is not permission to discard payloads.
      return false;
    }
  }

  Future<StashedPrompt> _externalize(
    String profile,
    StashedPrompt prompt, {
    void Function()? checkCurrent,
  }) async {
    final vault = _attachmentVault;
    if (vault == null || prompt.attachments.isEmpty) return prompt;
    if (prompt.attachments.length > maxAttachments) {
      throw StateError('Too many stash attachments');
    }
    final inline = [
      for (final attachment in prompt.attachments)
        if (Uri.tryParse(attachment.url)?.scheme == 'data') attachment,
    ];
    var data = <DraftAttachmentRef>[];
    if (inline.isNotEmpty) {
      await _ensureAttachmentOwner(profile);
      checkCurrent?.call();
      data = await vault.store(profile, inline);
      checkCurrent?.call();
    }
    var index = 0;
    return prompt._snapshot(
      fileRefs: [
        for (final attachment in prompt.attachments)
          if (Uri.tryParse(attachment.url)?.scheme == 'data')
            data[index++]
          else
            DraftAttachmentRef(
              filename: attachment.filename,
              mime: attachment.mime,
              url: attachment.url,
            ),
      ],
    );
  }

  Future<void> stash(
    String profile,
    StashedPrompt prompt, {
    void Function()? checkCurrent,
  }) {
    final snapshot = prompt._snapshot();
    return _write(profile, () async {
      if (profile.isEmpty || snapshot.id.isEmpty) {
        throw const FormatException('Missing stash identity');
      }
      if (snapshot.attachmentRefs.isNotEmpty) {
        throw StateError('Restore attachments before saving another stash');
      }
      final current = stashes(profile);
      if (current.any((p) => p.id == snapshot.id)) {
        throw StateError('Duplicate stash');
      }
      if (current.length >= capacity) throw StateError('Stash is full');
      await _collect(profile);
      checkCurrent?.call();
      try {
        final saved = await _externalize(
          profile,
          snapshot,
          checkCurrent: checkCurrent,
        );
        checkCurrent?.call();
        await _commit(
          () => preferences.setString(
            'oc.promptStash.$profile.${saved.id}',
            jsonEncode(saved.toJson()),
          ),
          checkCurrent: checkCurrent,
        );
        _stashes[profile] = [saved, ...current];
      } finally {
        await _collect(profile);
      }
      checkCurrent?.call();
    }, checkCurrent: checkCurrent);
  }

  Future<void> remove(
    String profile,
    String id, {
    void Function()? checkCurrent,
  }) => _write(profile, () async {
    final current = stashes(profile);
    await _adoptExistingAttachmentFiles(profile);
    checkCurrent?.call();
    await _commit(
      () => preferences.remove('oc.promptStash.$profile.$id'),
      checkCurrent: checkCurrent,
    );
    _stashes[profile] = current.where((p) => p.id != id).toList();
    if (!await _collect(profile)) {
      throw StateError('Could not remove stash attachments');
    }
    checkCurrent?.call();
  }, checkCurrent: checkCurrent);

  /// A failed entry stays at its original key with its complete inline data.
  /// Return deferred IDs so a caller can disclose partial migration and retry.
  Future<List<String>> migrateAttachments(
    String profile, {
    void Function()? checkCurrent,
  }) => _write(profile, () async {
    if (_attachmentVault == null) {
      throw StateError('Attachment storage is not enabled');
    }
    final deferred = <String>[];
    for (final prompt in List.of(stashes(profile))) {
      checkCurrent?.call();
      if (prompt.attachments.isEmpty) continue;
      try {
        if (!_metadataCertain) await _reloadMetadata();
        checkCurrent?.call();
        final saved = await _externalize(
          profile,
          prompt,
          checkCurrent: checkCurrent,
        );
        checkCurrent?.call();
        await _commit(
          () => preferences.setString(
            'oc.promptStash.$profile.${prompt.id}',
            jsonEncode(saved.toJson()),
          ),
          checkCurrent: checkCurrent,
        );
        _stashes.remove(profile);
      } catch (_) {
        checkCurrent?.call();
        deferred.add(prompt.id);
      } finally {
        await _collect(profile);
      }
    }
    checkCurrent?.call();
    return List.unmodifiable(deferred);
  }, checkCurrent: checkCurrent);

  /// Read without consuming the source. The controller must recheck the
  /// profile/location/session before applying this async result to a composer.
  Future<DraftAttachmentRecovery> restoreAttachments(
    String profile,
    String id, {
    required bool sameLocation,
    void Function()? checkCurrent,
  }) => _write(profile, () async {
    final prompt = stashes(profile).firstWhere((prompt) => prompt.id == id);
    if (prompt.attachmentRefs.isNotEmpty) {
      final vault = _attachmentVault;
      if (vault == null) throw StateError('Attachment storage is not enabled');
      final recovery = await vault.restore(
        profile,
        prompt.attachmentRefs,
        sameLocation: sameLocation,
      );
      checkCurrent?.call();
      return recovery;
    }
    final available = <PromptAttachment>[];
    final unavailable = <String>[];
    for (final attachment in prompt.attachments) {
      if (StashedPrompt.canRestoreAttachment(attachment) &&
          (sameLocation || Uri.tryParse(attachment.url)?.scheme != 'file')) {
        available.add(attachment);
      } else {
        unavailable.add(attachment.filename);
      }
    }
    return DraftAttachmentRecovery(
      List.unmodifiable(available),
      List.unmodifiable(unavailable),
    );
  }, checkCurrent: checkCurrent);

  /// The controller must block new profile writes BEFORE calling this method.
  /// Metadata removal precedes collection; retry also removes orphaned files
  /// if a previous attempt already removed metadata but failed file cleanup.
  Future<bool> clearForProfile(String profile) => _write(profile, () async {
    final prefix = 'oc.promptStash.$profile.';
    try {
      // Adopt pre-marker records before removing their metadata. If this write
      // fails, their file references and complete legacy JSON must stay put.
      await _adoptExistingAttachmentFiles(profile);
      for (final key
          in preferences
              .getKeys()
              .where(
                (key) =>
                    key.startsWith(prefix) ||
                    key == 'oc.promptHistory.$profile',
              )
              .toList()) {
        await _commit(() => preferences.remove(key));
      }
      forget(profile);
      if (!await _collect(profile)) return false;
      final ownerKey = attachmentOwnerKey(profile);
      if (preferences.containsKey(ownerKey)) {
        await _commit(() => preferences.remove(ownerKey));
      }
      return true;
    } catch (_) {
      forget(profile);
      return false;
    }
  }).catchError((Object _) => false);

  Future<void> recordSent(
    String profile,
    String text, {
    void Function()? checkCurrent,
  }) => _write(profile, () async {
    if (text.trim().isEmpty) return;
    final next = [
      text,
      ...history(profile).where((p) => p != text),
    ].take(capacity).toList();
    await _commit(
      () =>
          preferences.setString('oc.promptHistory.$profile', jsonEncode(next)),
      checkCurrent: checkCurrent,
    );
    _history[profile] = next;
  }, checkCurrent: checkCurrent);

  Future<void> drain(String profile) =>
      _writes[profile] ?? Future<void>.value();
  void forget(String profile) {
    _stashes.remove(profile);
    _history.remove(profile);
  }
}
