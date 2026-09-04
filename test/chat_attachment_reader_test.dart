import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:cross_file/cross_file.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/domain/prompt_attachment.dart';

base class _TestPlatformFile extends PlatformFile {
  _TestPlatformFile({
    required this.name,
    required this.size,
    this.path,
    Uint8List? bytes,
    Stream<List<int>>? stream,
  }) : _bytes = bytes,
       _stream = stream;

  @override
  final String name;
  final int size;
  @override
  final String? path;
  final Uint8List? _bytes;
  final Stream<List<int>>? _stream;

  @override
  Uri get uri =>
      path == null ? Uri.dataFromBytes(_bytes ?? []) : Uri.file(path!);

  @override
  XFile get xFile => path == null
      ? XFile.fromData(_bytes ?? Uint8List(0), name: name, length: size)
      : XFile(path!, name: name, length: size);

  @override
  Future<int> length() async => size;

  @override
  Future<Uint8List> readAsBytes() async {
    if (_bytes != null) return _bytes;
    if (path != null) return File(path!).readAsBytes();
    final builder = BytesBuilder();
    await for (final chunk in _stream ?? const Stream.empty()) {
      builder.add(chunk);
    }
    return builder.takeBytes();
  }

  @override
  Stream<Uint8List> readAsByteStream() {
    if (_stream != null) return _stream.map(Uint8List.fromList);
    if (_bytes != null) return Stream.value(_bytes);
    if (path != null) return File(path!).openRead().map(Uint8List.fromList);
    return const Stream.empty();
  }
}

void main() {
  group('prompt attachment MIME', () {
    final text = Uint8List.fromList('hello'.codeUnits);

    test('normalizes Markdown and HTML to text/plain', () {
      expect(
        promptAttachmentMime(
          filename: 'notes.md',
          bytes: text,
          declaredMime: 'text/markdown',
        ),
        'text/plain',
      );
      expect(
        promptAttachmentMime(
          filename: 'report.html',
          bytes: text,
          declaredMime: 'application/octet-stream',
        ),
        'text/plain',
      );
    });

    test('keeps Unicode source and configuration attachments readable', () {
      final bytes = Uint8List.fromList(utf8.encode('title: "مرحبا 👋"\n'));
      for (final filename in ['settings.yaml', 'app.kt', 'Dockerfile']) {
        expect(
          promptAttachmentMime(filename: filename, bytes: bytes),
          'text/plain',
          reason: filename,
        );
      }
      expect(
        promptAttachmentMime(
          filename: 'corrupt.txt',
          bytes: Uint8List.fromList([0xc3, 0x28]),
        ),
        isNull,
      );
    });

    test('detects generic textual bytes and rejects unknown binary bytes', () {
      expect(
        promptAttachmentMime(
          filename: 'README',
          bytes: text,
          declaredMime: 'application/octet-stream',
        ),
        'text/plain',
      );
      expect(
        promptAttachmentMime(
          filename: 'archive.bin',
          bytes: Uint8List.fromList([0, 1, 2, 255]),
          declaredMime: 'application/octet-stream',
        ),
        isNull,
      );
      expect(
        promptAttachmentMime(
          filename: 'disguised.txt',
          bytes: Uint8List.fromList([0, 1, 2, 255]),
          declaredMime: 'application/octet-stream',
        ),
        isNull,
      );
      expect(
        promptAttachmentMime(
          filename: 'declared.txt',
          bytes: Uint8List.fromList([0, 1, 2, 255]),
          declaredMime: 'text/plain',
        ),
        isNull,
      );
      expect(
        promptAttachmentMime(
          filename: 'declared.json',
          bytes: Uint8List.fromList([0, 1, 2, 255]),
          declaredMime: 'application/json',
        ),
        isNull,
      );
    });

    test('preserves supported images and PDFs', () {
      expect(
        promptAttachmentMime(
          filename: 'photo.jpg',
          bytes: Uint8List.fromList([0, 1]),
        ),
        'image/jpeg',
      );
      expect(
        promptAttachmentMime(
          filename: 'document.pdf',
          bytes: Uint8List.fromList([0, 1]),
        ),
        'application/pdf',
      );
      expect(
        promptAttachmentMime(
          filename: 'legacy.bmp',
          bytes: Uint8List.fromList([0x42, 0x4D, 0, 1]),
          declaredMime: 'image/bmp',
        ),
        isNull,
      );
      expect(
        promptAttachmentMime(
          filename: 'misnamed.png',
          bytes: Uint8List.fromList([0x42, 0x4D, 0, 1]),
          declaredMime: 'image/bmp',
        ),
        isNull,
      );
    });
  });

  test('reads a streamed attachment at the exact byte limit', () async {
    final file = _TestPlatformFile(
      name: 'exact.txt',
      size: 5,
      stream: Stream<List<int>>.fromIterable(const [
        [1, 2],
        [3, 4, 5],
      ]),
    );

    final bytes = await readAttachmentBytesWithinLimit(file, maxBytes: 5);

    expect(bytes, Uint8List.fromList([1, 2, 3, 4, 5]));
  });

  test('stops a growing stream after the overflow detection byte', () async {
    var readPastOverflow = false;

    Stream<List<int>> growingStream() async* {
      yield const [1, 2, 3];
      yield const [4, 5, 6];
      readPastOverflow = true;
      yield List<int>.filled(1024 * 1024, 7);
    }

    final file = _TestPlatformFile(
      name: 'growing.txt',
      size: 3,
      stream: growingStream(),
    );

    final bytes = await readAttachmentBytesWithinLimit(file, maxBytes: 5);

    expect(bytes, isNull);
    expect(readPastOverflow, isFalse);
  });

  test('does not trust stale metadata when reading a file path', () async {
    final directory = await Directory.systemTemp.createTemp(
      'oc-app-attachment-reader-',
    );
    addTearDown(() => directory.delete(recursive: true));
    final path = '${directory.path}/changed.txt';
    await File(path).writeAsBytes([1, 2, 3, 4, 5, 6]);
    final file = _TestPlatformFile(name: 'changed.txt', path: path, size: 3);

    final bytes = await readAttachmentBytesWithinLimit(file, maxBytes: 5);

    expect(bytes, isNull);
  });

  test('rejects oversized in-memory picker data without copying it', () async {
    final file = _TestPlatformFile(
      name: 'memory.txt',
      size: 2,
      bytes: Uint8List.fromList([1, 2, 3]),
    );

    final bytes = await readAttachmentBytesWithinLimit(file, maxBytes: 2);

    expect(bytes, isNull);
  });
}
