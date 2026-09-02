import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/ui/screens/terminal_screen.dart';

class _RecordingChannel implements TerminalChannel {
  final controller = StreamController<String>();
  final writes = <String>[];

  @override
  Stream<String> get output => controller.stream;

  @override
  int? get cursor => null;

  @override
  void write(String value) => writes.add(value);

  @override
  Future<void> close() async {
    if (!controller.isClosed) await controller.close();
  }
}

class _TerminalRepository implements ProductRepository {
  final channels = <_RecordingChannel>[];

  @override
  Future<TerminalChannel> connectTerminal(String id, {int? cursor}) async {
    final channel = _RecordingChannel();
    channels.add(channel);
    return channel;
  }

  @override
  Future<void> resizeTerminal(
    String id, {
    required int rows,
    required int cols,
  }) async {}

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

const _process = TerminalProcess(
  id: 'pty-1',
  title: 'Shell',
  command: 'bash',
  arguments: [],
  directory: '/work',
  running: true,
  pid: 42,
);

const _typed = 'the quick brown fox';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('queued keystrokes leave as one ordered write, none repeated', () async {
    final sent = <String>[];
    final queue = TerminalInputQueue(sent.add);
    for (final rune in _typed.runes) {
      queue.write(String.fromCharCode(rune));
    }
    expect(sent, isEmpty, reason: 'nothing leaves before the flush');
    await Future<void>.delayed(Duration.zero);
    expect(sent, [_typed]);
    expect(queue.hasPending, isFalse);

    queue.write('a');
    queue.write('b');
    queue.flush();
    expect(sent, [_typed, 'ab']);
    queue.flush();
    expect(sent, [_typed, 'ab'], reason: 'an empty flush sends nothing');
  });

  test('a channel that goes away drops what was never sent', () {
    final sent = <String>[];
    final queue = TerminalInputQueue(sent.add);
    queue.write('lost');
    queue.discard();
    queue.flush();
    expect(sent, isEmpty);
    queue.close();
    queue.write('after close');
    queue.flush();
    expect(sent, isEmpty);
  });

  testWidgets('fast desktop typing reaches the PTY in order, once', (
    tester,
  ) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.linux;
    try {
      final repository = _TerminalRepository();
      await tester.pumpWidget(
        MaterialApp(
          home: TerminalSurface(repository: repository, process: _process),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));
      expect(repository.channels, hasLength(1));
      final channel = repository.channels.single;

      for (final rune in _typed.runes) {
        final character = String.fromCharCode(rune);
        final key = character == ' '
            ? LogicalKeyboardKey.space
            : LogicalKeyboardKey.findKeyByKeyId(
                LogicalKeyboardKey.keyA.keyId + (rune - 'a'.codeUnitAt(0)),
              )!;
        // No pump between keys: this is a burst, not one key per frame.
        await tester.sendKeyDownEvent(key, character: character);
        await tester.sendKeyUpEvent(key);
      }
      await tester.pump();

      // Every character arrives exactly once and in the order it was typed,
      // never coalesced out of order or re-sent as an accumulated delta.
      expect(channel.writes.join(), _typed);
      expect(channel.writes.length, lessThanOrEqualTo(_typed.length));
    } finally {
      debugDefaultTargetPlatformOverride = null;
    }
  });
}
