import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/foundation.dart';
import 'package:image_picker/image_picker.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../api/models.dart';
import 'draft_attachments.dart';

enum PromptPhotoFailure { storage, pending, tooLarge, unsupported, unavailable }

class PromptPhotoException implements Exception {
  const PromptPhotoException(this.failure);
  final PromptPhotoFailure failure;
}

class PendingPromptPhoto {
  const PendingPromptPhoto({
    required this.id,
    required this.profileID,
    required this.sessionID,
    this.directory,
    this.workspace,
    this.ref,
    this.path,
    this.name,
    this.failed = false,
  });
  final String id;
  final String profileID;
  final String sessionID;
  final String? directory;
  final String? workspace;
  final DraftAttachmentRef? ref;
  final String? path;
  final String? name;
  final bool failed;
  Map<String, dynamic> toJson() => {
    'id': id,
    'profileID': profileID,
    'sessionID': sessionID,
    'directory': directory,
    'workspace': workspace,
    'ref': ref?.toJson(),
    'path': path,
    'name': name,
    'failed': failed,
  };
  factory PendingPromptPhoto.fromJson(Map<String, dynamic> json) =>
      PendingPromptPhoto(
        id: json['id'] as String,
        profileID: json['profileID'] as String,
        sessionID: json['sessionID'] as String,
        directory: json['directory'] as String?,
        workspace: json['workspace'] as String?,
        path: json['path'] as String?,
        name: json['name'] as String?,
        ref: json['ref'] == null
            ? null
            : DraftAttachmentRef.fromJson(
                Map<String, dynamic>.from(json['ref'] as Map),
              ),
        failed: json['failed'] == true,
      );
  PendingPromptPhoto withResult({
    DraftAttachmentRef? ref,
    String? path,
    String? name,
    bool failed = false,
  }) => PendingPromptPhoto(
    id: id,
    profileID: profileID,
    sessionID: sessionID,
    directory: directory,
    workspace: workspace,
    ref: ref ?? this.ref,
    path: path ?? this.path,
    name: name ?? this.name,
    failed: failed,
  );
}

/// One native picker request at a time. The origin is committed before launch;
/// recovered data never silently enters whichever conversation happens to open.
class PromptPhotoStore extends ChangeNotifier {
  PromptPhotoStore(
    this.prefs, {
    ImagePicker? picker,
    DraftAttachmentVault? vault,
  }) : _picker = picker ?? ImagePicker(),
       _vault =
           vault ??
           DraftAttachmentVault(
             directory: () async => Directory(
               p.join(
                 (await getApplicationSupportDirectory()).path,
                 'prompt-photo-recovery-v1',
               ),
             ),
           );
  static const key = 'oc.pendingPromptPhoto';
  static const maxBytes = 10 * 1024 * 1024;
  final SharedPreferences prefs;
  final ImagePicker _picker;
  final DraftAttachmentVault _vault;
  bool _busy = false;
  Future<void> _writes = Future.value();
  bool get busy => _busy;
  PendingPromptPhoto? get pending {
    final raw = prefs.getString(key);
    if (raw == null) return null;
    try {
      return PendingPromptPhoto.fromJson(
        jsonDecode(raw) as Map<String, dynamic>,
      );
    } catch (_) {
      return null;
    }
  }

  Future<T> _serial<T>(Future<T> Function() action) {
    final next = _writes.then((_) => action());
    _writes = next.then<void>((_) {}, onError: (Object _, StackTrace _) {});
    return next;
  }

  Future<void> _save(PendingPromptPhoto photo) async {
    try {
      if (!await prefs.setString(key, jsonEncode(photo.toJson()))) {
        throw const PromptPhotoException(PromptPhotoFailure.storage);
      }
    } catch (_) {
      try {
        await prefs.reload();
      } catch (_) {}
      throw const PromptPhotoException(PromptPhotoFailure.storage);
    }
    notifyListeners();
  }

  Future<PendingPromptPhoto?> pick({
    required String profileID,
    required String sessionID,
    required String? directory,
    required String? workspace,
    required ImageSource source,
  }) async {
    if (_busy || prefs.containsKey(key)) {
      throw const PromptPhotoException(PromptPhotoFailure.pending);
    }
    _busy = true;
    final request = PendingPromptPhoto(
      id: DateTime.now().microsecondsSinceEpoch.toString(),
      profileID: profileID,
      sessionID: sessionID,
      directory: directory,
      workspace: workspace,
    );
    try {
      await _serial(() => _save(request));
      final file = await _picker.pickImage(
        source: source,
        requestFullMetadata: false,
      );
      if (file == null) {
        await discard(request.id);
        return null;
      }
      return await _serial(() => _complete(request, file));
    } catch (_) {
      await _serial(() async {
        if (pending?.id == request.id) {
          if (pending!.path == null && pending!.ref == null) {
            await _discard(request.id);
          } else {
            await _save(pending!.withResult(failed: true));
          }
        }
      });
      rethrow;
    } finally {
      _busy = false;
      notifyListeners();
    }
  }

  Future<PendingPromptPhoto?> _complete(
    PendingPromptPhoto request,
    XFile file,
  ) async {
    if (pending?.id != request.id) {
      return null; // Deleted while picker was open.
    }
    await _save(request.withResult(path: file.path, name: file.name));
    final attachment = await readPhoto(file);
    if (pending?.id != request.id) return null;
    final refs = await _vault.store(request.profileID, [attachment]);
    final complete = request.withResult(ref: refs.single, name: file.name);
    await _save(complete);
    return complete;
  }

  /// Called during bootstrap before chat routes can read drafts. The plugin
  /// clears its lost-result cache, so save its returned path before reading it.
  Future<void> recoverLostData() async {
    // Every picker launch first commits an origin record. Without one there
    // is nothing this app can recover safely, and startup needs no native call.
    if (_busy || pending == null || pending!.ref != null) return;
    _busy = true;
    try {
      await _serial(() async {
        final request = pending;
        final lost = await _picker.retrieveLostData();
        if (request == null || request.ref != null) return;
        final files = lost.files;
        final file = files?.isNotEmpty == true
            ? files!.first
            : lost.file ??
                  (request.path == null
                      ? null
                      : XFile(request.path!, name: request.name));
        if (file != null) {
          await _complete(request, file);
        } else if (lost.exception != null || request.failed) {
          await _save(request.withResult(failed: true));
        } else {
          await _discard(request.id);
        }
      });
    } catch (_) {
      final request = pending;
      if (request != null) {
        try {
          await _serial(() => _save(request.withResult(failed: true)));
        } catch (_) {}
      }
    } finally {
      _busy = false;
    }
  }

  Future<PromptAttachment> readPending(String id) => _serial(() async {
    var request = pending;
    if (request == null || request.id != id) {
      throw const PromptPhotoException(PromptPhotoFailure.unavailable);
    }
    if (request.ref == null && request.path != null) {
      request = await _complete(
        request,
        XFile(request.path!, name: request.name),
      );
    }
    final ref = request?.ref;
    if (request == null || ref == null) {
      throw const PromptPhotoException(PromptPhotoFailure.unavailable);
    }
    final restored = await _vault.restore(request.profileID, [
      ref,
    ], sameLocation: true);
    if (restored.attachments.isEmpty) {
      throw const PromptPhotoException(PromptPhotoFailure.unavailable);
    }
    return restored.attachments.single;
  });

  Future<void> discard(String id) => _serial(() => _discard(id));
  Future<void> _discard(String id) async {
    if (pending?.id != id) return;
    final request = pending!;
    if ((request.ref != null || request.path != null) &&
        !await _vault.collect({}, owner: request.profileID)) {
      throw const PromptPhotoException(PromptPhotoFailure.storage);
    }
    try {
      if (!await prefs.remove(key)) {
        throw const PromptPhotoException(PromptPhotoFailure.storage);
      }
    } catch (_) {
      try {
        await prefs.reload();
      } catch (_) {}
      throw const PromptPhotoException(PromptPhotoFailure.storage);
    }
    notifyListeners();
  }

  Future<bool> clearForProfile(String profileID) => _serial(() async {
    final request = pending;
    if (request == null) return !prefs.containsKey(key);
    if (request.profileID != profileID && request.profileID.isNotEmpty) {
      return true;
    }
    try {
      await _discard(request.id);
      return true;
    } catch (_) {
      return false;
    }
  });

  @visibleForTesting
  static Future<PromptAttachment> readPhoto(XFile file) async {
    if (await file.length() > maxBytes) {
      throw const PromptPhotoException(PromptPhotoFailure.tooLarge);
    }
    final bytes = BytesBuilder(copy: false);
    await for (final chunk in file.openRead()) {
      if (bytes.length + chunk.length > maxBytes) {
        throw const PromptPhotoException(PromptPhotoFailure.tooLarge);
      }
      bytes.add(chunk);
    }
    final payload = bytes.takeBytes();
    bool prefix(List<int> value) =>
        payload.length >= value.length &&
        listEquals(payload.sublist(0, value.length), value);
    final String? mime;
    if (prefix([137, 80, 78, 71, 13, 10, 26, 10])) {
      mime = 'image/png';
    } else if (prefix([255, 216, 255])) {
      mime = 'image/jpeg';
    } else if (prefix(ascii.encode('GIF87a')) ||
        prefix(ascii.encode('GIF89a'))) {
      mime = 'image/gif';
    } else if (prefix(ascii.encode('RIFF')) &&
        payload.length >= 12 &&
        listEquals(payload.sublist(8, 12), ascii.encode('WEBP'))) {
      mime = 'image/webp';
    } else {
      mime = null;
    }
    if (mime == null || !mime.startsWith('image/') || payload.isEmpty) {
      throw const PromptPhotoException(PromptPhotoFailure.unsupported);
    }
    return PromptAttachment(
      filename: file.name,
      mime: mime,
      url: 'data:$mime;base64,${base64Encode(payload)}',
    );
  }
}
