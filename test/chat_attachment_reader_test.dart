import 'dart:io';
import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';

void main() {
  test('reads a streamed attachment at the exact byte limit', () async {
    final file = PlatformFile(
      name: 'exact.txt',
      size: 5,
      readStream: Stream<List<int>>.fromIterable(const [
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

    final file = PlatformFile(
      name: 'growing.txt',
      size: 3,
      readStream: growingStream(),
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
    final file = PlatformFile(name: 'changed.txt', path: path, size: 3);

    final bytes = await readAttachmentBytesWithinLimit(file, maxBytes: 5);

    expect(bytes, isNull);
  });

  test('rejects oversized in-memory picker data without copying it', () async {
    final file = PlatformFile(
      name: 'memory.txt',
      size: 2,
      bytes: Uint8List.fromList([1, 2, 3]),
    );

    final bytes = await readAttachmentBytesWithinLimit(file, maxBytes: 2);

    expect(bytes, isNull);
  });
}
