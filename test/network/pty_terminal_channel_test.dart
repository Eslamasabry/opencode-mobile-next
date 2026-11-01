import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/pty_terminal_channel.dart';

import 'support/fake_platform_websocket.dart';

void main() {
  late FakePlatformWebSocket socket;
  late PtyTerminalChannel channel;

  setUp(() {
    socket = FakePlatformWebSocket();
    channel = PtyTerminalChannel(socket, initialCursor: 10);
  });

  tearDown(() async {
    if (!socket.controller.isClosed) await socket.controller.close();
  });

  test(
    'text frames are delivered and advance the cursor by UTF-8 bytes',
    () async {
      final output = <String>[];
      channel.output.listen(output.add);
      socket.controller.add('héllo');
      await Future<void>.delayed(Duration.zero);
      expect(output, ['héllo']);
      expect(channel.cursor, 10 + utf8.encode('héllo').length);
    },
  );

  test(
    'binary frames are decoded and advance the cursor by their length',
    () async {
      final output = <String>[];
      channel.output.listen(output.add);
      socket.controller.add(Uint8List.fromList(utf8.encode('ls\r\n')));
      await Future<void>.delayed(Duration.zero);
      expect(output, ['ls\r\n']);
      expect(channel.cursor, 14);
    },
  );

  test('a 0x00 meta frame sets the cursor and emits no text', () async {
    final output = <String>[];
    channel.output.listen(output.add);
    socket.controller.add(
      Uint8List.fromList([0, ...utf8.encode('{"cursor":42}')]),
    );
    await Future<void>.delayed(Duration.zero);
    expect(output, isEmpty);
    expect(channel.cursor, 42);
  });

  test('a malformed meta frame is ignored, not shown', () async {
    final output = <String>[];
    channel.output.listen(output.add);
    socket.controller.add(Uint8List.fromList([0, 1, 2]));
    socket.controller.add(
      Uint8List.fromList([0, ...utf8.encode('{"cursor":-1}')]),
    );
    await Future<void>.delayed(Duration.zero);
    expect(output, isEmpty);
    expect(channel.cursor, 10);
  });

  test('empty frames emit nothing', () async {
    final output = <String>[];
    channel.output.listen(output.add);
    socket.controller.add('');
    socket.controller.add(Uint8List(0));
    await Future<void>.delayed(Duration.zero);
    expect(output, isEmpty);
    expect(channel.cursor, 10);
  });

  test('writes go out as text frames and close closes the socket', () async {
    channel.write('echo hi\n');
    expect(socket.sent, ['echo hi\n']);
    await channel.close();
    expect(socket.closed, isTrue);
    await socket.done;
  });

  test('output is a broadcast stream shared by late listeners', () async {
    final first = <String>[];
    channel.output.listen(first.add);
    socket.controller.add('a');
    await Future<void>.delayed(Duration.zero);
    final second = <String>[];
    channel.output.listen(second.add);
    socket.controller.add('b');
    await Future<void>.delayed(Duration.zero);
    expect(first, ['a', 'b']);
    expect(second, ['b']);
  });
}
