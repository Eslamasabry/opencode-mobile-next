import 'dart:async';

import 'package:flutter/widgets.dart';

import '../../builtin/builtin_folders.dart';
import '../../domain/file_share_gateway.dart';
import '../../domain/phone_file.dart';
import '../../platform/wifi_file_share.dart';
import 'file_preview.dart';
import 'file_share_sheet.dart';

/// Whether a tapped file link can offer "Share on Wi-Fi" (not in a browser).
bool canShareLinkedFile() => (fileShareGateway ??= WifiFileShare()).supported;

/// "Share on Wi-Fi" for a file the person tapped in a reply.
///
/// On this phone the file is read from disk and streamed (shared storage
/// directly, the built-in Linux and its projects through the app's own
/// folder for it). A file on another server, or one this phone cannot
/// reach on disk, is shared from [data], the bytes the tap already fetched.
/// Keys and credentials are refused either way.
Future<void> shareLinkedFile(
  BuildContext context, {
  required String path,
  required bool onPhone,
  FilePreviewData? data,
}) async {
  final gateway = fileShareGateway ??= WifiFileShare();
  final share = gateway is WifiFileShare ? gateway : WifiFileShare();
  final name = path.substring(path.lastIndexOf('/') + 1);
  FileShareSource? source;
  FileShareFailure? refused;
  if (WifiFileShare.looksSecret(path)) {
    refused = FileShareFailure.privateFile;
  } else {
    if (onPhone) {
      final host = await _hostFile(path);
      if (host != null) {
        try {
          source = await share.fileSource(host, mimeType: data?.mimeType);
        } on FileShareException catch (error) {
          // Only a refusal is final; an unreadable disk file falls back to
          // the bytes the tap fetched.
          if (error.failure == FileShareFailure.privateFile) {
            refused = error.failure;
          }
        }
      }
    }
    final bytes = data?.exportBytes;
    if (source == null && refused == null && bytes != null) {
      source = FileShareSource.bytes(name, bytes, mimeType: data?.mimeType);
    }
    if (source == null && refused == null) {
      refused = FileShareFailure.unavailable;
    }
  }
  if (!context.mounted) return;
  await showFileShareSheet(
    context,
    gateway: gateway,
    source: source,
    refused: refused,
  );
}

/// Where [path] is on this phone's disk, or null when it is not reachable
/// from the app.
Future<String?> _hostFile(String path) async {
  if (isPhoneSharedStoragePath(path)) return path;
  final normal = BuiltinRootfsFolders.normalize(path);
  final parent = normal == null ? null : BuiltinRootfsFolders.parentOf(normal);
  if (normal == null || parent == null) return null;
  final folder = await BuiltinRootfsFolders().hostDirectory(parent);
  return folder == null
      ? null
      : '$folder/${normal.substring(normal.lastIndexOf('/') + 1)}';
}
