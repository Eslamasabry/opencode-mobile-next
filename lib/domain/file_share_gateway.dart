import 'package:flutter/foundation.dart';

/// One file the person explicitly opened and now wants to hand to another
/// device on the same Wi-Fi. Bytes are streamed to the downloader, never
/// copied whole into a response buffer.
@immutable
class FileShareSource {
  const FileShareSource({
    required this.name,
    required this.length,
    required this.open,
    this.mimeType,
  });

  /// A file whose content the app already holds (a project file the viewer
  /// fetched). Sent in small slices.
  factory FileShareSource.bytes(
    String name,
    Uint8List bytes, {
    String? mimeType,
  }) => FileShareSource(
    name: name,
    length: bytes.length,
    mimeType: mimeType,
    open: () async* {
      const slice = 64 * 1024;
      for (var i = 0; i < bytes.length; i += slice) {
        yield Uint8List.sublistView(
          bytes,
          i,
          i + slice > bytes.length ? bytes.length : i + slice,
        );
      }
    },
  );

  /// The name the downloader gets (a base name, never a path).
  final String name;
  final int length;
  final String? mimeType;

  /// A fresh read of the content for each download.
  final Stream<List<int>> Function() open;
}

/// Why a share could not start. Plain categories only; the screen words
/// them.
enum FileShareFailure {
  /// Not on Wi-Fi (or a hotspot): nothing else is offered.
  noWifi,

  /// The file lives in the app's private storage (profiles, keys).
  privateFile,

  /// The file could not be read, or the server could not start.
  unavailable,
}

class FileShareException implements Exception {
  const FileShareException(this.failure);
  final FileShareFailure failure;
}

/// What a running share looks like right now.
@immutable
class FileShareState {
  const FileShareState({
    required this.url,
    required this.name,
    required this.expiresAt,
    this.downloads = 0,
  });

  final String url;
  final String name;
  final DateTime expiresAt;
  final int downloads;

  Duration remaining(DateTime now) {
    final left = expiresAt.difference(now);
    return left.isNegative ? Duration.zero : left;
  }

  FileShareState withDownloads(int count) => FileShareState(
    url: url,
    name: name,
    expiresAt: expiresAt,
    downloads: count,
  );
}

/// Shares one opened file on the local Wi-Fi, read-only, for a short time.
/// One share at a time: starting another stops the first. [state] is null
/// when nothing is shared.
abstract class FileShareGateway implements ValueListenable<FileShareState?> {
  /// Whether this platform can share at all (not in a browser).
  bool get supported;

  /// Starts sharing [source]. Throws [FileShareException].
  Future<FileShareState> start(FileShareSource source);

  /// Ends the share now. Safe when nothing is shared.
  Future<void> stop();
}

/// The share service the screens use. Set once at start-up; a test swaps it.
FileShareGateway? fileShareGateway;
