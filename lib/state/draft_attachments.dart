import 'dart:collection';
import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

import '../api/models.dart';

/// Small persisted metadata. Data-URL payloads live in app-private files.
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

/// Called under the controller's draft transaction queue. Files are committed
/// before metadata; unreferenced files can be collected after metadata succeeds.
class DraftAttachmentVault {
  DraftAttachmentVault({Future<Directory> Function()? directory})
    : _directory =
          directory ??
          (() async => Directory(
            p.join(
              (await getApplicationSupportDirectory()).path,
              'draft-attachments-v1',
            ),
          ));
  static const maxDiskBytes = 256 * 1024 * 1024;
  static const maxDraftBytes = 32 * 1024 * 1024;
  final Future<Directory> Function() _directory;
  final _cache = Expando<DraftAttachmentRef>();
  static final _hash = RegExp(r'^[0-9a-f]{64}$');
  static String ownerKey(String owner) =>
      sha256.convert(utf8.encode(owner)).toString();
  static String _id(String value) =>
      sha256.convert(utf8.encode(value)).toString();

  Future<File> _file(String owner, String id) async {
    if (!_hash.hasMatch(id)) {
      throw const FormatException('Invalid attachment identity');
    }
    return File(p.join((await _directory()).path, ownerKey(owner), '$id.blob'));
  }

  Future<List<File>> _files({String? owner}) async {
    final root = await _directory();
    if (!await root.exists()) return [];
    final files = <File>[];
    await for (final dir in root.list(followLinks: false)) {
      if (dir is! Directory ||
          !_hash.hasMatch(p.basename(dir.path)) ||
          (owner != null && p.basename(dir.path) != ownerKey(owner))) {
        continue;
      }
      await for (final file in dir.list(followLinks: false)) {
        if (file is File &&
            RegExp(
              r'^[0-9a-f]{64}\.(blob|tmp)$',
            ).hasMatch(p.basename(file.path))) {
          files.add(file);
        }
      }
    }
    return files;
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
        throw const FileSystemException(
          'Draft attachment storage limit reached',
        );
      }
      final file = await _file(owner, ref.blob!);
      var intact = false;
      if (await file.exists() && await file.length() == ref.bytes) {
        intact =
            sha256.convert(await file.readAsBytes()).toString() == ref.blob;
      }
      if (!intact) {
        if (await file.exists()) await file.delete();
        if (diskBytes == null) {
          var total = 0;
          for (final existing in await _files()) {
            total += await existing.length();
          }
          diskBytes = total;
        }
        if (diskBytes + ref.bytes > maxDiskBytes) {
          throw const FileSystemException('Draft attachment storage is full');
        }
        await file.parent.create(recursive: true);
        final temporary = File(p.setExtension(file.path, '.tmp'));
        await temporary.writeAsString(
          attachment.url,
          encoding: utf8,
          flush: true,
        );
        await temporary.rename(file.path);
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
    var restoredBytes = 0;
    for (final ref in refs) {
      try {
        if (attachments.length >= 5) {
          throw const FormatException('Too many draft attachments');
        }
        final String url;
        if (ref.blob != null) {
          restoredBytes += ref.bytes;
          final file = await _file(owner, ref.blob!);
          if (ref.bytes < 0 ||
              restoredBytes > maxDraftBytes ||
              ref.bytes > maxDraftBytes ||
              await file.length() != ref.bytes) {
            throw const FormatException('Attachment size changed');
          }
          url = await file.readAsString(encoding: utf8);
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
            keep.add('${ownerKey(entry.key)}/${ref.blob}.blob');
          }
        }
      }
      for (final file in await _files(owner: owner)) {
        final key = '${p.basename(file.parent.path)}/${p.basename(file.path)}';
        if (!keep.contains(key)) await file.delete();
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
