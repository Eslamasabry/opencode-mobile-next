import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/codex/transport.dart';

class _Socket implements CodexSocket {
  final input = StreamController<Object?>.broadcast(sync: true);
  final sent = <Map<String, dynamic>>[];
  void Function(Map<String, dynamic>)? onSend;
  bool closed = false;

  @override
  Stream<Object?> get messages => input.stream;

  @override
  void send(String message) {
    if (closed) throw StateError('closed');
    final frame = jsonDecode(message) as Map<String, dynamic>;
    sent.add(frame);
    onSend?.call(frame);
  }

  void result(Object id, Map<String, dynamic> value) {
    input.add(jsonEncode({'id': id, 'result': value}));
  }

  @override
  Future<void> close() async {
    if (closed) return;
    closed = true;
    await input.close();
  }
}

Future<void> _settle() => pumpEventQueue();

void main() {
  test(
    'socket loss fails all pending work and never replays a mutation',
    () async {
      final first = _Socket();
      final second = _Socket();
      final sockets = <_Socket>[first, second];
      first.onSend = (frame) {
        if (frame['method'] == 'initialize') first.result(frame['id'], {});
      };
      second.onSend = (frame) {
        if (frame['method'] == 'initialize') second.result(frame['id'], {});
        if (frame['method'] == 'thread/list') {
          second.result(frame['id'], {'data': [], 'nextCursor': null});
        }
      };
      final transport = CodexTransport(
        endpoint: 'wss://fixture.invalid',
        token: 'fixture',
        socketFactory: (_, _) async => sockets.removeAt(0),
      );
      try {
        final mutation = transport.request('turn/start', {
          'threadId': 'thread-1',
        }, mutation: true);
        final read = transport.request('thread/list', {});
        await _settle();
        expect(
          first.sent.where((f) => f['method'] == 'turn/start'),
          hasLength(1),
        );
        expect(
          first.sent.where((f) => f['method'] == 'thread/list'),
          hasLength(1),
        );

        final mutationError = expectLater(
          mutation,
          throwsA(
            isA<CodexFailure>().having(
              (error) => error.kind,
              'kind',
              CodexFailureKind.deliveryUnknown,
            ),
          ),
        );
        final readError = expectLater(
          read,
          throwsA(
            isA<CodexFailure>().having(
              (error) => error.kind,
              'kind',
              CodexFailureKind.disconnected,
            ),
          ),
        );
        await first.close();
        await Future.wait([mutationError, readError]);

        final recovered = await transport.request('thread/list', {});
        expect(recovered['data'], isEmpty);
        expect(second.sent.where((f) => f['method'] == 'turn/start'), isEmpty);
        expect(
          second.sent.where((f) => f['method'] == 'thread/list'),
          hasLength(1),
        );
      } finally {
        await transport.close();
      }
    },
  );

  test(
    'old epoch cannot answer an approval or event after socket loss',
    () async {
      final socket = _Socket();
      socket.onSend = (frame) {
        if (frame['method'] == 'initialize') socket.result(frame['id'], {});
      };
      final transport = CodexTransport(
        endpoint: 'wss://fixture.invalid',
        token: 'fixture',
        socketFactory: (_, _) async => socket,
      );
      final events = <CodexRpcEvent>[];
      final subscription = transport.events.listen(events.add);
      try {
        await transport.connect();
        final oldEpoch = transport.epoch;
        await socket.close();
        await _settle();
        expect(transport.epoch, greaterThan(oldEpoch));

        // A stale server callback is ignored once loss advances the epoch. The
        // closed fixture stream cannot emit, so construct the same guard at the
        // public reply boundary with the old request epoch.
        expect(
          () => transport.reply(
            CodexRpcEvent(oldEpoch, 'request', const {}, 7),
            const {},
          ),
          throwsA(
            isA<CodexFailure>().having(
              (error) => error.kind,
              'kind',
              CodexFailureKind.staleRequest,
            ),
          ),
        );
        expect(events, isEmpty);
      } finally {
        await subscription.cancel();
        await transport.close();
      }
    },
  );

  test(
    'request waits for replacement initialization before sending its frame',
    () async {
      final first = _Socket();
      final second = _Socket();
      var socketNumber = 0;
      first.onSend = (frame) {
        if (frame['method'] == 'initialize') first.result(frame['id'], {});
      };
      second.onSend = (frame) {
        // Deliberately leave initialize pending. A request frame sent to this
        // socket would prove that the caller escaped the connection handoff.
        if (frame['method'] != 'initialize') {
          throw StateError('request sent before replacement initialized');
        }
      };
      final transport = CodexTransport(
        endpoint: 'wss://fixture.invalid',
        token: 'fixture',
        requestTimeout: const Duration(milliseconds: 10),
        socketFactory: (_, _) async => socketNumber++ == 0 ? first : second,
      );
      try {
        await transport.connect();
        await first.close();
        await _settle();

        final request = transport.request('thread/list', {});
        await expectLater(
          request,
          throwsA(
            isA<CodexFailure>().having(
              (error) => error.kind,
              'kind',
              CodexFailureKind.disconnected,
            ),
          ),
        );
        expect(second.sent.map((frame) => frame['method']), ['initialize']);
      } finally {
        await transport.close();
      }
    },
  );
}
