import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:dio/dio.dart';
import 'package:opencode_sdk/opencode_sdk.dart';
import 'package:test/test.dart';

void main() {
  test('SSE reconnect defaults match the upstream JavaScript SDK', () {
    const options = SseReconnectOptions();
    expect(options.maxReconnectAttempts, isNull);
    expect(options.initialBackoff, const Duration(seconds: 3));
    expect(options.maxBackoff, const Duration(seconds: 30));
  });

  test(
    'global.event parses split UTF-8, CRLF, multiline data, and metadata',
    () async {
      final fixture = await _Fixture.start((request) async {
        expect(request.uri.path, '/global/event');
        expect(
          request.headers.value(HttpHeaders.acceptHeader),
          'text/event-stream',
        );
        _prepareSse(request.response);
        final bytes = utf8.encode(
          ': keepalive\r\n'
          'id: cursor-1\r\n'
          'event: global\r\n'
          'retry: 7\r\n'
          'data: {\r\n'
          'data: "directory":"caf\u00e9",\r\n'
          'data: "payload":{"type":"test"}\r\n'
          'data: }\r\n\r\n',
        );
        final split = bytes.indexOf(0xc3) + 1;
        request.response.add(bytes.sublist(0, split));
        await request.response.flush();
        await Future<void>.delayed(const Duration(milliseconds: 2));
        request.response.add(bytes.sublist(split));
        await request.response.close();
      });
      addTearDown(fixture.close);

      final events = await fixture.sdk
          .globalEventStream(
            reconnect: const SseReconnectOptions(maxReconnectAttempts: 0),
          )
          .toList();

      expect(events, hasLength(1));
      expect(events.single.id, 'cursor-1');
      expect(events.single.event, 'global');
      expect(events.single.retry, const Duration(milliseconds: 7));
      expect(events.single.data.directory, 'caf\u00e9');
    },
  );

  test('event.subscribe does not reconnect after clean EOF', () async {
    var requests = 0;
    final fixture = await _Fixture.start((request) async {
      requests++;
      expect(request.uri.path, '/event');
      expect(request.uri.queryParameters['directory'], '/work');
      expect(request.uri.queryParameters['workspace'], 'primary');
      _prepareSse(request.response);
      expect(request.headers.value('Last-Event-ID'), isNull);
      request.response.write('id: one\nretry: 1\ndata: {"sequence":1}\n\n');
      await request.response.close();
    });
    addTearDown(fixture.close);

    final events = await fixture.sdk
        .eventSubscribeStream(
          directory: '/work',
          workspace: 'primary',
          reconnect: const SseReconnectOptions(maxReconnectAttempts: 3),
        )
        .toList()
        .timeout(const Duration(seconds: 1));

    expect(requests, 1);
    expect(events.map((event) => event.id), ['one']);
    expect(events.single.data.objectValue?['sequence'], 1);
  });

  test('v2.event.subscribe cancellation closes the stream normally', () async {
    final requestStarted = Completer<void>();
    final fixture = await _Fixture.start((request) async {
      expect(request.uri.path, '/api/event');
      _prepareSse(request.response);
      request.response.write(': waiting\n\n');
      await request.response.flush();
      requestStarted.complete();
      try {
        await request.response.done;
      } catch (_) {
        // The client intentionally closes the socket on cancellation.
      }
    });
    addTearDown(fixture.close);
    final cancellation = CancelToken();

    final streamResult = fixture.sdk
        .v2EventSubscribeStream(cancelToken: cancellation)
        .toList();
    await requestStarted.future;
    cancellation.cancel('test cancellation');

    await expectLater(
      streamResult.timeout(const Duration(seconds: 2)),
      completion(isEmpty),
    );
  });

  test('v2.session.events performs the contentMediaType JSON decode', () async {
    final fixture = await _Fixture.start((request) async {
      expect(request.uri.path, '/api/session/ses_1/event');
      expect(request.uri.queryParameters['after'], '42');
      _prepareSse(request.response);
      final durable = jsonEncode(<String, Object?>{
        'type': 'session.next.synthetic',
        'sequence': 43,
      });
      request.response.write(
        'id: 43\nevent: durable\ndata: ${jsonEncode(durable)}\n\n',
      );
      await request.response.close();
    });
    addTearDown(fixture.close);

    final events = await fixture.sdk
        .v2SessionEventsStream(
          sessionID: 'ses_1',
          after: '42',
          reconnect: const SseReconnectOptions(maxReconnectAttempts: 0),
        )
        .toList();

    expect(events, hasLength(1));
    expect(events.single.id, '43');
    expect(events.single.data.objectValue?['sequence'], 43);
  });

  test(
    'v2.session.events exposes Effect failure frames as stream errors',
    () async {
      final fixture = await _Fixture.start((request) async {
        _prepareSse(request.response);
        final cause = jsonEncode(<Object?>[
          <String, Object?>{'_tag': 'Die', 'defect': 'boom'},
        ]);
        request.response.write(
          'id: failed\n'
          'event: effect/httpapi/stream/failure\n'
          'data: $cause\n\n',
        );
        await request.response.close();
      });
      addTearDown(fixture.close);

      await expectLater(
        fixture.sdk.v2SessionEventsStream(
          sessionID: 'ses_failure',
          reconnect: const SseReconnectOptions(maxReconnectAttempts: 0),
        ),
        emitsError(
          isA<EffectStreamFailure>()
              .having((error) => error.event.id, 'id', 'failed')
              .having(
                (error) => (error.cause as List).single,
                'cause',
                <String, Object?>{'_tag': 'Die', 'defect': 'boom'},
              ),
        ),
      );
    },
  );

  test(
    'durable reconnect keeps after and sends Last-Event-ID without claiming replay suppression',
    () async {
      var requests = 0;
      final fixture = await _Fixture.start((request) async {
        requests++;
        expect(request.uri.queryParameters['after'], '42');
        _prepareSse(request.response);
        final durable = jsonEncode(<String, Object?>{
          'type': 'session.next.synthetic',
          'sequence': 43,
        });
        final frame =
            'id: 43\nretry: 1\nevent: durable\ndata: ${jsonEncode(durable)}\n\n';
        if (requests == 1) {
          expect(request.headers.value('Last-Event-ID'), isNull);
          final bytes = utf8.encode(frame);
          request.response.contentLength = bytes.length + 20;
          request.response.add(bytes);
        } else {
          expect(request.headers.value('Last-Event-ID'), '43');
          request.response.write(frame);
        }
        try {
          await request.response.close();
        } on HttpException {
          if (requests != 1) rethrow;
          // The first response intentionally truncates its declared body.
        }
      });
      addTearDown(fixture.close);

      final events = await fixture.sdk
          .v2SessionEventsStream(
            sessionID: 'ses_retry',
            after: '42',
            reconnect: const SseReconnectOptions(maxReconnectAttempts: 1),
          )
          .toList()
          .timeout(const Duration(seconds: 2));

      expect(requests, 2);
      expect(events.map((event) => event.data.objectValue?['sequence']), [
        43,
        43,
      ]);
    },
  );

  test('all SSE replacements attach operation IDs and map non-2xx', () async {
    final seenOperationIds = <String>[];
    final fixture = await _Fixture.start((request) async {
      request.response
        ..statusCode = HttpStatus.badRequest
        ..headers.contentType = ContentType.json
        ..write('{}');
      await request.response.close();
    }, createSdk: false);
    addTearDown(fixture.close);
    fixture.dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) {
          seenOperationIds.add(options.extra['operationId'] as String);
          handler.next(options);
        },
      ),
    );
    final sdk = OpencodeSdk(dio: fixture.dio);
    final streams = <String, Stream<Object>>{
      'global.event': sdk.globalEventStream(
        reconnect: const SseReconnectOptions(maxReconnectAttempts: 0),
      ),
      'event.subscribe': sdk.eventSubscribeStream(
        reconnect: const SseReconnectOptions(maxReconnectAttempts: 0),
      ),
      'v2.session.events': sdk.v2SessionEventsStream(
        sessionID: 'ses_error',
        reconnect: const SseReconnectOptions(maxReconnectAttempts: 0),
      ),
      'v2.event.subscribe': sdk.v2EventSubscribeStream(
        reconnect: const SseReconnectOptions(maxReconnectAttempts: 0),
      ),
    };

    for (final entry in streams.entries) {
      OpenCodeApiException? caught;
      try {
        await entry.value.drain<void>();
      } on OpenCodeApiException catch (error) {
        caught = error;
      }
      expect(caught, isNotNull, reason: entry.key);
      expect(caught!.operationId, entry.key);
      expect(caught!.statusCode, HttpStatus.badRequest);
      expect(caught!.decodedPayload, isNot(isA<DioException>()));
      expect(caught!.contract.isDeclared, entry.key != 'event.subscribe');
    }
    expect(seenOperationIds, streams.keys);
  });

  test('unlimited retry backoff remains cancellable', () async {
    final firstRequest = Completer<void>();
    final fixture = await _Fixture.start((request) async {
      request.response.statusCode = HttpStatus.internalServerError;
      await request.response.close();
      if (!firstRequest.isCompleted) firstRequest.complete();
    });
    addTearDown(fixture.close);
    final cancellation = CancelToken();
    final result = fixture.sdk
        .eventSubscribeStream(cancelToken: cancellation)
        .drain<void>();
    await firstRequest.future;
    cancellation.cancel('cancel retry delay');

    await expectLater(result.timeout(const Duration(seconds: 1)), completes);
  });
}

void _prepareSse(HttpResponse response) {
  response.bufferOutput = false;
  response.headers.contentType = ContentType(
    'text',
    'event-stream',
    charset: 'utf-8',
  );
}

class _Fixture {
  _Fixture(this.server, this.dio, this._sdk);

  final HttpServer server;
  final Dio dio;
  final OpencodeSdk? _sdk;

  OpencodeSdk get sdk => _sdk!;

  static Future<_Fixture> start(
    Future<void> Function(HttpRequest request) handler, {
    bool createSdk = true,
  }) async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    server.listen((request) async {
      try {
        await handler(request);
      } catch (error, stackTrace) {
        Zone.current.handleUncaughtError(error, stackTrace);
      }
    });
    final dio = Dio(
      BaseOptions(baseUrl: 'http://${server.address.address}:${server.port}'),
    );
    return _Fixture(server, dio, createSdk ? OpencodeSdk(dio: dio) : null);
  }

  Future<void> close() async {
    dio.close(force: true);
    await server.close(force: true);
  }
}
