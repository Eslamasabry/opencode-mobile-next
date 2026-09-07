import 'dart:async';
import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/state/draft_attachments.dart';

/// Deterministic widget-test storage. Each instance is an isolated vault root;
/// retain the instance across controller reconstruction to simulate a restart.
/// Real file publication, corruption and quotas belong to backend tests.
class StashMemoryVault extends DraftAttachmentVault {
  final _payloads = <String, Map<String, String>>{};
  final missingFilenames = <String>{};
  bool failStore = false;
  Completer<void>? storeGate;
  Completer<void>? restoreGate;
  int storeCalls = 0;
  int restoreCalls = 0;
  int collectCalls = 0;

  void releaseGates() {
    for (final gate in [storeGate, restoreGate]) {
      if (gate != null && !gate.isCompleted) gate.complete();
    }
  }

  @override
  Future<List<DraftAttachmentRef>> store(
    String owner,
    List<PromptAttachment> attachments,
  ) async {
    storeCalls++;
    final snapshot = List<PromptAttachment>.of(attachments);
    await storeGate?.future;
    if (failStore) throw StateError('Synthetic attachment write failure');
    if (snapshot.length > 5) {
      throw const FormatException('Too many draft attachments');
    }
    return [
      for (final attachment in snapshot) _storeAttachment(owner, attachment),
    ];
  }

  DraftAttachmentRef _storeAttachment(
    String owner,
    PromptAttachment attachment,
  ) {
    final scheme = Uri.tryParse(attachment.url)?.scheme;
    if (scheme == 'data') {
      final bytes = utf8.encode(attachment.url);
      final blob = sha256.convert(bytes).toString();
      _payloads.putIfAbsent(owner, () => {})[blob] = attachment.url;
      return DraftAttachmentRef(
        filename: attachment.filename,
        mime: attachment.mime,
        blob: blob,
        bytes: bytes.length,
      );
    }
    if (!{'file', 'http', 'https'}.contains(scheme)) {
      throw const FormatException('Attachment cannot survive restart');
    }
    return DraftAttachmentRef(
      filename: attachment.filename,
      mime: attachment.mime,
      url: attachment.url,
    );
  }

  @override
  Future<DraftAttachmentRecovery> restore(
    String owner,
    List<DraftAttachmentRef> refs, {
    required bool sameLocation,
  }) async {
    restoreCalls++;
    final snapshot = List<DraftAttachmentRef>.of(refs);
    await restoreGate?.future;
    final attachments = <PromptAttachment>[];
    final unavailable = <String>[];
    for (final ref in snapshot) {
      final url = ref.blob == null ? ref.url : _payloads[owner]?[ref.blob];
      final scheme = url == null ? null : Uri.tryParse(url)?.scheme;
      final available =
          !missingFilenames.contains(ref.filename) &&
          url != null &&
          attachments.length < 5 &&
          (ref.blob != null
              ? scheme == 'data' && utf8.encode(url).length == ref.bytes
              : {'http', 'https'}.contains(scheme) ||
                    (scheme == 'file' && sameLocation));
      if (available) {
        attachments.add(
          PromptAttachment(filename: ref.filename, mime: ref.mime, url: url),
        );
      } else {
        unavailable.add(ref.filename);
      }
    }
    return DraftAttachmentRecovery(attachments, unavailable);
  }

  @override
  Future<bool> collect(
    Map<String, Iterable<DraftAttachmentRef>> retained, {
    String? owner,
  }) async {
    collectCalls++;
    for (final profile in _payloads.keys.toList()) {
      if (owner != null && profile != owner) continue;
      final keep = (retained[profile] ?? const <DraftAttachmentRef>[])
          .map((ref) => ref.blob)
          .toSet();
      _payloads[profile]!.removeWhere((blob, _) => !keep.contains(blob));
    }
    return true;
  }
}
