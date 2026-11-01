import 'dart:collection';
import 'dart:convert';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';

import '../domain/models.dart';
import 'draft_attachment_storage.dart';
import 'draft_attachment_storage_io.dart'
    if (dart.library.js_interop) 'draft_attachment_storage_web.dart'
    as platform;

export 'draft_attachment_storage.dart';

/// Small persisted metadata. Data-URL payloads live in the platform's
/// app-private payload store (files on native builds, IndexedDB in a
/// browser), never in preferences.
class DraftAttachmentRef {
  const DraftAttachmentRef({
    required this.filename,
    required this.mime,
    this.blob,
    this.url,
    this.bytes = 0,
  });
  final String filename;
  final String mime;
  final String? blob;
  final String? url;
  final int bytes;
  Map<String, dynamic> toJson() => {
    'filename': filename,
    'mime': mime,
    'blob': ?blob,
    'url': ?url,
    'bytes': bytes,
  };
  factory DraftAttachmentRef.fromJson(Map<String, dynamic> value) =>
      DraftAttachmentRef(
        filename: value['filename'] as String,
        mime: value['mime'] as String,
        blob: value['blob'] as String?,
        url: value['url'] as String?,
        bytes: (value['bytes'] as num?)?.toInt() ?? 0,
      );
}

class DraftAttachmentRecovery {
  const DraftAttachmentRecovery(this.attachments, this.unavailable);
  final List<PromptAttachment> attachments;
  final List<String> unavailable;
}

/// Called under the controller's draft transaction queue. Payloads are
/// committed before metadata; unreferenced payloads can be collected after
/// metadata succeeds.
///
/// The vault decides identity (content hash), bounds, owner isolation and
/// ordering; where the bytes live is a [DraftAttachmentStorage] chosen per
/// platform — app-private files on native builds, the origin's IndexedDB in
/// a browser. Metadata ([DraftAttachmentRef]) is identical on both, so a
/// draft index written by one build reads on the other.
class DraftAttachmentVault {
  /// [directory] pins a native vault to a directory (tests); [storage]
  /// supplies a store outright; otherwise [root] names this vault's
  /// app-private root — a directory under app support on native, an
  /// IndexedDB database in a browser — resolved on first use.
  DraftAttachmentVault({
    Future<platform.VaultDirectory> Function()? directory,
    DraftAttachmentStorage? storage,
    String root = defaultRoot,
  }) : _storage = storage ?? _platformStorage(directory, root);

  static DraftAttachmentStorage _platformStorage(
    Future<platform.VaultDirectory> Function()? directory,
    String root,
  ) => directory == null
      ? platform.defaultDraftAttachmentStorage(root)
      : platform.directoryDraftAttachmentStorage(directory);

  /// The ordinary composer drafts' root; stash and photo recovery use
  /// their own so one collector never sees another's payloads.
  static const defaultRoot = 'draft-attachments-v1';
  static const maxDiskBytes = 256 * 1024 * 1024;
  static const maxDraftBytes = 32 * 1024 * 1024;
  final DraftAttachmentStorage _storage;
  final _cache = Expando<DraftAttachmentRef>();
  static final _hash = RegExp(r'^[0-9a-f]{64}$');
  static String ownerKey(String owner) =>
      sha256.convert(utf8.encode(owner)).toString();
  static String _id(String value) =>
      sha256.convert(utf8.encode(value)).toString();

  /// Validates the identity before any store sees it: a ref from a corrupt
  /// index must never become a key or a path.
  static String _checked(String id) {
    if (!_hash.hasMatch(id)) {
      throw const FormatException('Invalid attachment identity');
    }
    return id;
  }

  Future<List<DraftAttachmentRef>> store(
    String owner,
    List<PromptAttachment> attachments,
  ) async {
    if (attachments.isEmpty) return [];
    if (attachments.length > 5) {
      throw const FormatException('Too many draft attachments');
    }
    final refs = <DraftAttachmentRef>[];
    final ownerId = ownerKey(owner);
    var draftBytes = 0;
    int? diskBytes;
    for (final attachment in attachments) {
      final scheme = Uri.tryParse(attachment.url)?.scheme;
      if (scheme != 'data') {
        if (!{'file', 'http', 'https'}.contains(scheme)) {
          throw const FormatException('Attachment cannot survive restart');
        }
        refs.add(
          DraftAttachmentRef(
            filename: attachment.filename,
            mime: attachment.mime,
            url: attachment.url,
          ),
        );
        continue;
      }
      var ref = _cache[attachment];
      if (ref == null) {
        final bytes = utf8.encode(attachment.url);
        ref = DraftAttachmentRef(
          filename: attachment.filename,
          mime: attachment.mime,
          blob: sha256.convert(bytes).toString(),
          bytes: bytes.length,
        );
        _cache[attachment] = ref;
      }
      draftBytes += ref.bytes;
      if (draftBytes > maxDraftBytes) {
        throw const DraftAttachmentStorageException(
          DraftAttachmentStorageFailure.limit,
          DraftAttachmentStorageException.draftLimitMessage,
        );
      }
      final id = _checked(ref.blob!);
      var intact = false;
      final existing = await _storage.length(ownerId, id);
      if (existing == ref.bytes) {
        try {
          intact =
              sha256
                  .convert(await _storage.read(ownerId, id, ref.bytes))
                  .toString() ==
              ref.blob;
        } on FormatException {
          intact = false;
        }
      }
      if (!intact) {
        if (existing != null) await _storage.delete(ownerId, id);
        if (diskBytes == null) {
          var total = 0;
          for (final entry in await _storage.list()) {
            total += entry.bytes;
          }
          diskBytes = total;
        }
        if (diskBytes + ref.bytes > maxDiskBytes) {
          throw const DraftAttachmentStorageException(
            DraftAttachmentStorageFailure.limit,
            DraftAttachmentStorageException.rootLimitMessage,
          );
        }
        await _storage.write(
          ownerId,
          id,
          Uint8List.fromList(utf8.encode(attachment.url)),
        );
        diskBytes = diskBytes + ref.bytes;
      }
      refs.add(ref);
    }
    return refs;
  }

  Future<DraftAttachmentRecovery> restore(
    String owner,
    List<DraftAttachmentRef> refs, {
    required bool sameLocation,
  }) async {
    final attachments = <PromptAttachment>[];
    final missing = <String>[];
    final ownerId = ownerKey(owner);
    var restoredBytes = 0;
    for (final ref in refs) {
      try {
        if (attachments.length >= 5) {
          throw const FormatException('Too many draft attachments');
        }
        final String url;
        if (ref.blob != null) {
          if (ref.bytes < 0 || ref.bytes > maxDraftBytes - restoredBytes) {
            throw const FormatException('Attachment size changed');
          }
          // A malformed negative length must not buy more recovery capacity.
          restoredBytes += ref.bytes;
          final id = _checked(ref.blob!);
          if (await _storage.length(ownerId, id) != ref.bytes) {
            throw const FormatException('Attachment size changed');
          }
          url = utf8.decode(await _storage.read(ownerId, id, ref.bytes));
          if (_id(url) != ref.blob || Uri.tryParse(url)?.scheme != 'data') {
            throw const FormatException('Attachment content changed');
          }
        } else {
          url = ref.url ?? '';
          final scheme = Uri.tryParse(url)?.scheme;
          if (!{'file', 'http', 'https'}.contains(scheme) ||
              (scheme == 'file' && !sameLocation)) {
            throw const FormatException(
              'Attachment belongs to another location',
            );
          }
        }
        final attachment = PromptAttachment(
          filename: ref.filename,
          mime: ref.mime,
          url: url,
        );
        if (ref.blob != null) _cache[attachment] = ref;
        attachments.add(attachment);
      } catch (_) {
        missing.add(ref.filename);
      }
    }
    return DraftAttachmentRecovery(attachments, missing);
  }

  /// Deletes only this vault's unreferenced payloads, never user-selected files.
  /// Owner filtering lets profile deletion report its own cleanup outcome.
  Future<bool> collect(
    Map<String, Iterable<DraftAttachmentRef>> retained, {
    String? owner,
  }) async {
    try {
      final keep = <String>{};
      for (final entry in retained.entries) {
        for (final ref in entry.value) {
          if (ref.blob != null && _hash.hasMatch(ref.blob!)) {
            keep.add('${ownerKey(entry.key)}/${ref.blob}');
          }
        }
      }
      final entries = await _storage.list(
        ownerKey: owner == null ? null : ownerKey(owner),
      );
      for (final entry in entries) {
        if (!entry.committed || !keep.contains(entry.key)) {
          await _storage.delete(entry.ownerKey, entry.id);
        }
      }
      return true;
    } catch (_) {
      return false;
    }
  }
}

/// Mutations from picker, paste, editor and chip removal all autosave alike.
class DraftAttachmentList extends ListBase<PromptAttachment> {
  DraftAttachmentList(this.changed);
  final void Function() changed;
  final List<PromptAttachment> _items = [];
  @override
  int get length => _items.length;
  @override
  set length(int value) {
    _items.length = value;
    changed();
  }

  @override
  PromptAttachment operator [](int index) => _items[index];
  @override
  void operator []=(int index, PromptAttachment value) {
    _items[index] = value;
    changed();
  }

  @override
  void add(PromptAttachment element) {
    _items.add(element);
    changed();
  }

  @override
  void addAll(Iterable<PromptAttachment> iterable) {
    _items.addAll(iterable);
    changed();
  }

  @override
  void insert(int index, PromptAttachment element) {
    _items.insert(index, element);
    changed();
  }

  @override
  void insertAll(int index, Iterable<PromptAttachment> iterable) {
    _items.insertAll(index, iterable);
    changed();
  }

  @override
  void clear() {
    if (_items.isEmpty) return;
    _items.clear();
    changed();
  }

  @override
  bool remove(Object? element) {
    final removed = _items.remove(element);
    if (removed) changed();
    return removed;
  }

  @override
  PromptAttachment removeAt(int index) {
    final value = _items.removeAt(index);
    changed();
    return value;
  }
}
