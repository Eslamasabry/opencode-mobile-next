/// Reads the zip a setup backup is saved as (written by
/// ProjectExportBridge.kt with java.util.zip): its central directory, and
/// one entry at a time streamed out to a file. Pure dart:io, so a restore
/// needs no new package (a new package would need a release).
///
/// Only what such a zip holds is supported: stored or deflated entries, no
/// encryption, no zip64 (an entry or archive past 4 GB is refused).
library;

import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

class ZipEntry {
  const ZipEntry({
    required this.name,
    required this.method,
    required this.compressedSize,
    required this.size,
    required this.localOffset,
  });

  final String name;

  /// 0 stored, 8 deflated.
  final int method;
  final int compressedSize;
  final int size;
  final int localOffset;
}

class ZipFormatException implements Exception {
  const ZipFormatException(this.message);
  final String message;
  @override
  String toString() => 'ZipFormatException: $message';
}

int _u16(Uint8List b, int at) => b[at] | (b[at + 1] << 8);
int _u32(Uint8List b, int at) =>
    b[at] | (b[at + 1] << 8) | (b[at + 2] << 16) | (b[at + 3] << 24);

/// The entries of the zip at [path].
Future<List<ZipEntry>> readZipEntries(String path) async {
  final file = await File(path).open();
  try {
    final length = await file.length();
    if (length < 22) throw const ZipFormatException('too short');
    final tailSize = length < 65557 ? length : 65557;
    await file.setPosition(length - tailSize);
    final tail = await file.read(tailSize);
    var eocd = -1;
    for (var i = tail.length - 22; i >= 0; i--) {
      if (_u32(tail, i) == 0x06054b50) {
        eocd = i;
        break;
      }
    }
    if (eocd < 0) throw const ZipFormatException('no end of directory');
    final count = _u16(tail, eocd + 10);
    final cdSize = _u32(tail, eocd + 12);
    final cdOffset = _u32(tail, eocd + 16);
    if (count == 0xFFFF || cdSize == 0xFFFFFFFF || cdOffset == 0xFFFFFFFF) {
      throw const ZipFormatException('zip64 is not supported');
    }
    if (cdOffset + cdSize > length) {
      throw const ZipFormatException('directory past the end');
    }
    await file.setPosition(cdOffset);
    final cd = await file.read(cdSize);
    final entries = <ZipEntry>[];
    var at = 0;
    for (var i = 0; i < count; i++) {
      if (at + 46 > cd.length || _u32(cd, at) != 0x02014b50) {
        throw const ZipFormatException('bad directory entry');
      }
      final flags = _u16(cd, at + 8);
      if (flags & 1 != 0) throw const ZipFormatException('encrypted');
      final nameLength = _u16(cd, at + 28);
      final extraLength = _u16(cd, at + 30);
      final commentLength = _u16(cd, at + 32);
      final compressed = _u32(cd, at + 20);
      final size = _u32(cd, at + 24);
      final local = _u32(cd, at + 42);
      if (compressed == 0xFFFFFFFF || size == 0xFFFFFFFF) {
        throw const ZipFormatException('zip64 is not supported');
      }
      final name = String.fromCharCodes(
        cd.sublist(at + 46, at + 46 + nameLength),
      );
      entries.add(
        ZipEntry(
          name: name,
          method: _u16(cd, at + 10),
          compressedSize: compressed,
          size: size,
          localOffset: local,
        ),
      );
      at += 46 + nameLength + extraLength + commentLength;
    }
    return entries;
  } finally {
    await file.close();
  }
}

/// Streams [entry] of the zip at [path] into [target] (overwritten).
/// [onBytes] hears the compressed bytes read so far.
Future<void> extractZipEntry(
  String path,
  ZipEntry entry,
  File target, {
  void Function(int read)? onBytes,
}) async {
  if (entry.method != 0 && entry.method != 8) {
    throw ZipFormatException('method ${entry.method}');
  }
  final file = await File(path).open();
  final out = target.openWrite();
  try {
    await file.setPosition(entry.localOffset);
    final header = await file.read(30);
    if (header.length < 30 || _u32(header, 0) != 0x04034b50) {
      throw const ZipFormatException('bad local header');
    }
    await file.setPosition(
      entry.localOffset + 30 + _u16(header, 26) + _u16(header, 28),
    );
    final done = Completer<void>();
    final ByteConversionSink sink = entry.method == 8
        ? ZLibDecoder(
            raw: true,
          ).startChunkedConversion(_IOSinkAdapter(out, done))
        : _PassThrough(out, done);
    var left = entry.compressedSize;
    var read = 0;
    while (left > 0) {
      final chunk = await file.read(left < 65536 ? left : 65536);
      if (chunk.isEmpty) throw const ZipFormatException('cut short');
      left -= chunk.length;
      read += chunk.length;
      sink.add(chunk);
      onBytes?.call(read);
    }
    sink.close();
    await done.future;
  } finally {
    await file.close();
    await out.close();
  }
}

class _IOSinkAdapter implements Sink<List<int>> {
  _IOSinkAdapter(this.out, this.done);
  final IOSink out;
  final Completer<void> done;
  @override
  void add(List<int> data) => out.add(data);
  @override
  void close() {
    if (!done.isCompleted) done.complete();
  }
}

class _PassThrough extends ByteConversionSinkBase {
  _PassThrough(this.out, this.done);
  final IOSink out;
  final Completer<void> done;
  @override
  void add(List<int> chunk) => out.add(chunk);
  @override
  void close() {
    if (!done.isCompleted) done.complete();
  }
}
