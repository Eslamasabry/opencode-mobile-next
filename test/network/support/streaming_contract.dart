/// The streaming contract every platform transport must satisfy, written
/// without `dart:io` so the same cases run on the VM (native `dart:io`
/// adapter and socket) and in Chrome (Fetch adapter and DOM socket).
///
/// [registerStreamingContractTests] takes the test server's base URL lazily
/// because the VM run starts the server in `setUpAll`.
library;

import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_probe.dart';
import 'package:opencode_mobile/api2/events.dart';
import 'package:opencode_mobile/api2/sse2.dart';
import 'package:opencode_mobile/api2/transport.dart';
import 'package:opencode_mobile/network/platform_dio.dart';
import 'package:opencode_mobile/network/platform_websocket.dart';
import 'package:opencode_mobile/orchestration/client/http.dart';
import 'package:opencode_mobile/orchestration/client/sse.dart';

const _wait = Duration(seconds: 8);

/// Runs one test body; the VM runner uses it to enter an `HttpOverrides`
/// zone with a real client in case a test binding installed the mock.
typedef ContractRunner = Future<void> Function(Future<void> Function() body);

Future<void> _direct(Future<void> Function() body) => body();

void registerStreamingContractTests(
  Uri Function() base, {
  ContractRunner run = _direct,
}) {
  void contract(String name, Future<void> Function() body) =>
      test(name, () => run(body));

  String root() {
    final text = base().toString();
    return text.endsWith('/') ? text.substring(0, text.length - 1) : text;
  }

  Dio newDio() {
    final dio = Dio(
      BaseOptions(
        baseUrl: root(),
        connectTimeout: const Duration(seconds: 8),
        validateStatus: (status) =>
            status != null && status >= 200 && status < 300,
      ),
    );
    configurePlatformDio(dio);
    addTearDown(() => dio.close(force: true));
    return dio;
  }

  Future<Map<String, Object?>> stats(Dio dio, String id) async {
    final response = await dio.get<Map<dynamic, dynamic>>(
      '/stream/stats',
      queryParameters: {'id': id},
    );
    return Map<String, Object?>.from(response.data ?? const {});
  }

  String uniqueId(String prefix) =>
      '$prefix-${DateTime.now().microsecondsSinceEpoch}';

  Future<Response<ResponseBody>> openHold(
    Dio dio,
    String id, {
    CancelToken? cancelToken,
  }) => dio.get<ResponseBody>(
    '/stream/hold',
    queryParameters: {'id': id},
    options: Options(
      responseType: ResponseType.stream,
      headers: {
        'Accept': 'text/event-stream',
        'Authorization': 'Basic ${base64Encode(utf8.encode('opencode:pw'))}',
      },
    ),
    cancelToken: cancelToken,
  );

  contract(
    'the first SSE frame arrives while the server still holds the response',
    () async {
      final dio = newDio();
      final id = uniqueId('hold');
      final response = await openHold(dio, id);
      final chunks = <String>[];
      final first = Completer<void>();
      final done = Completer<void>();
      final subscription = response.data!.stream.listen(
        (chunk) {
          chunks.add(utf8.decode(chunk, allowMalformed: true));
          if (!first.isCompleted) first.complete();
        },
        onDone: () {
          if (!done.isCompleted) done.complete();
        },
        onError: (Object error, StackTrace stackTrace) {
          if (!done.isCompleted) done.completeError(error, stackTrace);
        },
      );
      addTearDown(subscription.cancel);
      await first.future.timeout(
        _wait,
        onTimeout: () => fail(
          'No body chunk arrived while the server held the response open: '
          'this transport buffers the stream until the server closes it.',
        ),
      );
      expect(chunks.join(), contains('"seq":1'));
      final beforeRelease = await stats(dio, id);
      expect(
        beforeRelease['open'],
        isTrue,
        reason: 'the server had not closed the stream when the frame arrived',
      );
      expect(
        beforeRelease['hadAuthorization'],
        isTrue,
        reason: 'Authorization travels as a request header',
      );
      await dio.get<Map<dynamic, dynamic>>(
        '/stream/release',
        queryParameters: {'id': id},
      );
      await done.future.timeout(_wait);
      expect(chunks.join(), contains('"seq":2'));
    },
  );

  contract(
    'cancelling the token ends the stream and the server sees the disconnect',
    () async {
      final dio = newDio();
      final id = uniqueId('abort');
      final token = CancelToken();
      final response = await openHold(dio, id, cancelToken: token);
      final first = Completer<void>();
      final ended = Completer<void>();
      final subscription = response.data!.stream.listen(
        (_) {
          if (!first.isCompleted) first.complete();
        },
        onDone: () {
          if (!ended.isCompleted) ended.complete();
        },
        onError: (Object error) {
          if (!ended.isCompleted) ended.complete();
        },
      );
      await first.future.timeout(_wait);
      token.cancel('test abort');
      await ended.future.timeout(
        _wait,
        onTimeout: () => fail('the stream did not end after cancel'),
      );
      await subscription.cancel();
      // The server notices the dropped client on its next heartbeat write.
      final deadline = DateTime.now().add(_wait);
      var open = true;
      while (open && DateTime.now().isBefore(deadline)) {
        await Future<void>.delayed(const Duration(milliseconds: 250));
        open = (await stats(dio, id))['open'] == true;
      }
      expect(
        open,
        isFalse,
        reason: 'the aborted stream is still open server-side',
      );
    },
  );

  contract(
    'OpenCode 2 event stream sends Basic auth and reconnects after a close',
    () async {
      final transport = Api2Transport(baseUrl: root(), password: 'pw');
      addTearDown(transport.close);
      final events = <Api2EventEnvelope>[];
      final statuses = <Api2StreamStatus>[];
      final reconnected = Completer<void>();
      final stream = Api2EventStream(
        transport: transport,
        onEvent: (envelope, _) {
          events.add(envelope);
          if (events.length >= 2 && !reconnected.isCompleted) {
            reconnected.complete();
          }
        },
        onStatus: (status) {
          statuses.add(status);
        },
      );
      addTearDown(stream.dispose);
      stream.start();
      await reconnected.future.timeout(const Duration(seconds: 15));
      expect(events.length, greaterThanOrEqualTo(2));
      expect(events.first.event, isA<Api2ServerConnectedEvent>());
      expect(statuses, contains(Api2StreamStatus.reconnecting));
      final counters = await stats(newDio(), 'none');
      expect(counters['eventHadAuthorization'], isTrue);
      expect(counters['eventConnections'], greaterThanOrEqualTo(2));
    },
  );

  contract(
    'AI Team SSE resumes with Last-Event-ID after a drop, no credential sent',
    () async {
      final http = OrchestrationHttpClient(baseUrl: root(), city: 'demo');
      addTearDown(http.close);
      final client = OrchestrationSseClient(
        http: http,
        path: '/v0/city/demo/events/stream',
        backoffBase: const Duration(milliseconds: 100),
        backoffCap: const Duration(milliseconds: 500),
      );
      final seqs = <int>[];
      var heartbeats = 0;
      final reached = Completer<void>();
      final subscription = client.frames.listen((frame) {
        if (frame.isHeartbeat) {
          heartbeats += 1;
          return;
        }
        final seq = frame.seq;
        if (seq == null) return;
        seqs.add(seq);
        if (seq >= 6 && !reached.isCompleted) reached.complete();
      });
      addTearDown(subscription.cancel);
      await reached.future.timeout(const Duration(seconds: 15));
      expect(seqs, [1, 2, 3, 4, 5, 6]);
      expect(heartbeats, greaterThanOrEqualTo(1));
      expect(client.connectionCount, greaterThanOrEqualTo(2));
      final counters = await stats(newDio(), 'none');
      expect(counters['lastEventIds'], contains('3'));
      expect(counters['orchestrationHadAuthorization'], isFalse);
    },
  );

  contract(
    'platform WebSocket carries text and binary frames and closes in bound',
    () async {
      final uri = base().replace(
        scheme: 'ws',
        path: '/ws/echo',
        queryParameters: {'ticket': 'test-ticket'},
      );
      final socket = await connectPlatformWebSocket(uri);
      final received = <Object>[];
      final gotMeta = Completer<void>();
      final gotText = Completer<void>();
      final gotBinary = Completer<void>();
      final subscription = socket.messages.listen((message) {
        received.add(message);
        if (message is Uint8List && message.isNotEmpty && message.first == 0) {
          if (!gotMeta.isCompleted) gotMeta.complete();
        } else if (message == 'hello') {
          if (!gotText.isCompleted) gotText.complete();
        } else if (message is Uint8List && message.length == 3) {
          if (!gotBinary.isCompleted) gotBinary.complete();
        }
      });
      addTearDown(subscription.cancel);
      await gotMeta.future.timeout(_wait);
      expect(socket.isOpen, isTrue);
      socket.send('hello');
      await gotText.future.timeout(_wait);
      socket.send(Uint8List.fromList([1, 2, 3]));
      await gotBinary.future.timeout(_wait);
      expect(
        received.whereType<Uint8List>().map((bytes) => bytes.toList()),
        anyElement(equals([1, 2, 3])),
      );
      final stopwatch = Stopwatch()..start();
      await socket.close();
      expect(
        stopwatch.elapsed,
        lessThan(platformWebSocketCloseBound + const Duration(seconds: 2)),
      );
      expect(socket.isOpen, isFalse);
      await socket.done.timeout(_wait);
    },
  );

  contract(
    'a server-initiated close ends the frames with the peer code',
    () async {
      final uri = base().replace(scheme: 'ws', path: '/ws/echo');
      final socket = await connectPlatformWebSocket(uri);
      final ended = Completer<void>();
      final subscription = socket.messages.listen(
        (_) {},
        onDone: () {
          if (!ended.isCompleted) ended.complete();
        },
        onError: (Object _) {},
      );
      addTearDown(subscription.cancel);
      socket.send('close');
      await ended.future.timeout(_wait);
      await socket.done.timeout(_wait);
      expect(socket.closeCode, 4000);
      expect(socket.closeReason, 'bye');
      expect(socket.isOpen, isFalse);
      await socket.close();
    },
  );

  contract(
    'the connection probe reaches an OpenCode 2 server over the platform Dio',
    () async {
      final result = await probeServerConnection(
        baseUrl: root(),
        password: 'pw',
      );
      expect(result.ok, isTrue, reason: result.message);
      expect(result.flavor, ServerFlavor.v2);
      expect(result.version, 'streaming-test');
    },
  );

  contract('a closed port is reported without inventing a diagnosis', () async {
    final result = await probeServerConnection(
      baseUrl: 'http://${base().host}:1',
    );
    expect(result.ok, isFalse);
    expect(result.suggestsMissingServer, isTrue);
    if (platformDioUsesBrowserTransport) {
      expect(result.message, contains('CORS'));
      expect(result.message, isNot(contains('refused')));
    } else {
      expect(result.message, contains('refused'));
    }
  });
}
