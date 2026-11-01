import 'dart:io';
import 'dart:typed_data';

import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

import 'draft_attachment_storage.dart';

/// What a vault root is on this platform: an app-private directory. Tests
/// inject a temporary one through `DraftAttachmentVault(directory:)`.
typedef VaultDirectory = Directory;

/// The store for a named root under the app-support directory, resolved on
/// first use so constructing a vault never calls path_provider.
DraftAttachmentStorage defaultDraftAttachmentStorage(String root) =>
    FileDraftAttachmentStorage(
      () async => Directory(
        p.join((await getApplicationSupportDirectory()).path, root),
      ),
    );

/// The store for an explicitly supplied directory.
DraftAttachmentStorage directoryDraftAttachmentStorage(
  Future<VaultDirectory> Function() directory,
) => FileDraftAttachmentStorage(directory);

/// Payloads as `<root>/<ownerKey>/<id>.blob` files, published through a
/// sibling `.tmp` and a rename so a reader never sees a partial file.
class FileDraftAttachmentStorage implements DraftAttachmentStorage {
  FileDraftAttachmentStorage(this._directory);

  final Future<Directory> Function() _directory;
  static final _hash = RegExp(r'^[0-9a-f]{64}$');
  static final _payload = RegExp(r'^[0-9a-f]{64}\.(blob|tmp)$');

  Future<File> _file(String ownerKey, String id) async {
    // The vault validates both; refuse here too so no caller can ever turn
    // a segment into a path.
    if (!_hash.hasMatch(ownerKey) || !_hash.hasMatch(id)) {
      throw const FormatException('Invalid attachment identity');
    }
    return File(p.join((await _directory()).path, ownerKey, '$id.blob'));
  }

  @override
  Future<List<DraftPayloadEntry>> list({String? ownerKey}) async {
    final root = await _directory();
    if (!await root.exists()) return [];
    final entries = <DraftPayloadEntry>[];
    await for (final dir in root.list(followLinks: false)) {
      final owner = p.basename(dir.path);
      if (dir is! Directory ||
          !_hash.hasMatch(owner) ||
          (ownerKey != null && owner != ownerKey)) {
        continue;
      }
      await for (final file in dir.list(followLinks: false)) {
        final name = p.basename(file.path);
        if (file is! File || !_payload.hasMatch(name)) continue;
        entries.add(
          DraftPayloadEntry(
            ownerKey: owner,
            id: name.substring(0, 64),
            bytes: await file.length(),
            committed: name.endsWith('.blob'),
          ),
        );
      }
    }
    return entries;
  }

  @override
  Future<int?> length(String ownerKey, String id) async {
    final file = await _file(ownerKey, id);
    return await file.exists() ? file.length() : null;
  }

  @override
  Future<Uint8List> read(String ownerKey, String id, int expectedBytes) async {
    final file = await _file(ownerKey, id);
    if (!await file.exists()) {
      throw const FormatException('Attachment missing');
    }
    // A length check followed by readAsBytes is not a bound: the file can
    // grow between the two. Enforce the expected size while streaming.
    final bytes = BytesBuilder(copy: false);
    await for (final chunk in file.openRead()) {
      if (chunk.length > expectedBytes - bytes.length) {
        throw const FormatException('Attachment size changed');
      }
      bytes.add(chunk);
    }
    if (bytes.length != expectedBytes) {
      throw const FormatException('Attachment size changed');
    }
    return bytes.takeBytes();
  }

  @override
  Future<void> write(String ownerKey, String id, Uint8List bytes) async {
    final file = await _file(ownerKey, id);
    try {
      await file.parent.create(recursive: true);
      final temporary = File(p.setExtension(file.path, '.tmp'));
      await temporary.writeAsBytes(bytes, flush: true);
      await temporary.rename(file.path);
    } on FileSystemException catch (error) {
      throw _failure(error);
    }
  }

  @override
  Future<void> delete(String ownerKey, String id) async {
    final file = await _file(ownerKey, id);
    try {
      if (await file.exists()) await file.delete();
    } on FileSystemException catch (error) {
      throw _failure(error);
    }
  }

  /// ENOSPC on Linux and Android; ERROR_HANDLE_DISK_FULL and ERROR_DISK_FULL
  /// on Windows. Everything else is a filesystem problem, not a space one.
  static const _diskFullCodes = {28, 39, 112};

  static DraftAttachmentStorageException _failure(FileSystemException error) {
    final code = error.osError?.errorCode;
    if (code != null && _diskFullCodes.contains(code)) {
      return DraftAttachmentStorageException(
        DraftAttachmentStorageFailure.quota,
        'This device is out of storage space. Free up space, then try again.',
        cause: error,
      );
    }
    return DraftAttachmentStorageException(
      DraftAttachmentStorageFailure.unavailable,
      'Attachment storage on this device is not available right now. '
      'Try again.',
      cause: error,
    );
  }
}
