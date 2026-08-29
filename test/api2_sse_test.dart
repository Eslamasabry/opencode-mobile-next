import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/events.dart';
import 'package:opencode_mobile/api2/sse2.dart';
import 'package:opencode_mobile/api2/transport.dart';

class _RealHttpOverrides extends HttpOverrides {}

List<Sse2Frame> parse(String wire, {bool flush = true, int? chunkSize}) {
  final frames = <Sse2Frame>[];
  final parser = Sse2Parser(frames.add);
  final bytes = utf8.encode(wire);
  if (chunkSize == null) {
    parser.add(bytes);
  } else {
    for (var i = 0; i < bytes.length; i += chunkSize) {
      parser.add(
        bytes.sublist(
          i,
          i + chunkSize > bytes.length ? bytes.length : i + chunkSize,
        ),
      );
    }
  }
  if (flush) parser.flush();
  return frames;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('parses bare data frames and keepalive comments', () {
    final frames = parse(
      'data: {"type":"server.connected","data":{}}\n\n'
      ': heartbeat\n\n'
      'data: {"type":"session.deleted"}\n\n',
    );
    expect(frames, hasLength(2));
    expect(frames[0].event, isNull);
    expect(frames[0].data, '{"type":"server.connected","data":{}}');
    expect(frames[1].data, '{"type":"session.deleted"}');
  });

  test('preserves the event: field the v1 consumer discarded', () {
    final frames = parse(
      'event: session.text.delta\n'
      'id: evt_42\n'
      'data: {"delta":"pong"}\n'
      '\n',
    );
    expect(frames.single.event, 'session.text.delta');
    expect(frames.single.id, 'evt_42');
    expect(frames.single.data, '{"delta":"pong"}');
  });

  test('joins multi-line data with newlines per the SSE spec', () {
    final frames = parse(
      'data: first\n'
      'data: second\n'
      'data:third\n'
      '\n',
    );
    expect(frames.single.data, 'first\nsecond\nthird');
  });

  test('handles CRLF line endings and chunk boundaries mid-line', () {
    for (final chunkSize in [1, 3, 7]) {
      final frames = parse(
        'event: ping\r\ndata: {"a":1}\r\n\r\ndata: {"b":2}\r\n\r\n',
        chunkSize: chunkSize,
      );
      expect(frames, hasLength(2), reason: 'chunkSize $chunkSize');
      expect(frames[0].event, 'ping');
      expect(frames[0].data, '{"a":1}');
      expect(frames[1].event, isNull);
      expect(frames[1].data, '{"b":2}');
    }
  });

  test('dispatches a trailing frame without terminator only on flush', () {
    final frames = parse('data: {"tail":true}', flush: false);
    expect(frames, isEmpty);
    expect(parse('data: {"tail":true}').single.data, '{"tail":true}');
  });

  test('drops oversized frames without corrupting the following ones', () {
    final frames = <Sse2Frame>[];
    final parser = Sse2Parser(frames.add, maxFrameBytes: 64);
    parser.add(utf8.encode('data: ${'x' * 200}\n\ndata: ok\n\n'));
    parser.flush();
    expect(frames.single.data, 'ok');
  });

  test('event stream delivers typed envelopes and reconnects', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      var connections = 0;
      server.listen((request) async {
        connections += 1;
        final response = request.response;
        response.headers.contentType = ContentType('text', 'event-stream');
        response.write('data: {"id":"evt_1","type":"server.connected","data":{}}\n\n');
        response.write(': heartbeat\n\n');
        response.write(
          'data: {"id":"evt_2","type":"session.text.delta",'
          '"data":{"sessionID":"ses_1","assistantMessageID":"msg_1",'
          '"ordinal":0,"delta":"pon"}}\n\n',
        );
        response.write('data: {"id":"evt_3","type":"totally.new.event","data":{"x":1}}\n\n');
        await response.flush();
        await response.close();
      });

      final transport = Api2Transport(
        baseUrl: 'http://${server.address.host}:${server.port}',
        password: 'pw',
      );
      final events = <Api2EventEnvelope>[];
      final statuses = <Api2StreamStatus>[];
      final reconnected = Completer<void>();
      final stream = Api2EventStream(
        transport: transport,
        onEvent: (envelope, frame) => events.add(envelope),
        onStatus: (status) {
          statuses.add(status);
          if (connections >= 2 &&
              status == Api2StreamStatus.connected &&
              !reconnected.isCompleted) {
            reconnected.complete();
          }
        },
      );
      stream.start();
      try {
        await reconnected.future.timeout(const Duration(seconds: 10));
        expect(connections, greaterThanOrEqualTo(2));
        expect(events.length, greaterThanOrEqualTo(3));
        expect(events[0].event, isA<Api2ServerConnectedEvent>());
        final delta = events[1].event as Api2SessionTextEvent;
        expect(delta.delta, 'pon');
        expect(events[2].event, isA<UnknownApi2Event>());
        expect(statuses.first, Api2StreamStatus.connecting);
        expect(statuses, contains(Api2StreamStatus.connected));
        expect(statuses, contains(Api2StreamStatus.reconnecting));
      } finally {
        await stream.dispose();
        transport.close();
        await server.close(force: true);
      }
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });

  test('session log stream tracks durable seq and surfaces log.synced', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final queries = <Map<String, String>>[];
      var connections = 0;
      server.listen((request) async {
        connections += 1;
        queries.add(request.uri.queryParameters);
        final response = request.response;
        response.headers.contentType = ContentType('text', 'event-stream');
        if (connections == 1) {
          response.write(
            'data: {"type":"session.renamed","data":{"sessionID":"ses_1","title":"a"},'
            '"durable":{"aggregateID":"ses_1","seq":6,"version":1}}\n\n',
          );
          response.write(
            'data: {"type":"log.synced","aggregateID":"ses_1","seq":7}\n\n',
          );
          response.write(
            'data: {"type":"session.renamed","data":{"sessionID":"ses_1","title":"b"},'
            '"durable":{"aggregateID":"ses_1","seq":8,"version":1}}\n\n',
          );
        }
        await response.flush();
        await response.close();
      });

      final transport = Api2Transport(
        baseUrl: 'http://${server.address.host}:${server.port}',
        password: 'pw',
      );
      final events = <Api2EventEnvelope>[];
      int? syncedSeq;
      final resumed = Completer<void>();
      final stream = Api2SessionLogStream(
        transport: transport,
        sessionID: 'ses_1',
        after: 5,
        onEvent: events.add,
        onSynced: (seq) => syncedSeq = seq,
        onStatus: (status) {
          if (queries.length >= 2 && !resumed.isCompleted) resumed.complete();
        },
      );
      stream.start();
      try {
        await resumed.future.timeout(const Duration(seconds: 10));
        expect(queries.first['after'], '5');
        expect(queries.first['follow'], 'true');
        expect(events, hasLength(2));
        expect(
          (events[0].event as Api2SessionRenamedEvent).title,
          'a',
        );
        expect(syncedSeq, 7);
        expect(stream.lastSeq, 8);
        expect(queries[1]['after'], '8',
            reason: 'reconnect must resume from the last durable seq');
      } finally {
        await stream.dispose();
        transport.close();
        await server.close(force: true);
      }
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });
}
