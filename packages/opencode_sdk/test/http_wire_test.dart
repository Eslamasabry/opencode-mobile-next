import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:opencode_sdk/opencode_sdk.dart';
import 'package:test/test.dart';

void main() {
  test('OpencodeSdk defaults and caller overrides match upstream', () {
    final defaults = OpencodeSdk();
    addTearDown(() => defaults.dio.close(force: true));
    expect(OpencodeSdk.basePath, 'http://localhost:4096');
    expect(defaults.dio.options.baseUrl, 'http://localhost:4096');
    expect(defaults.dio.options.receiveTimeout, isNull);

    final overridden = OpencodeSdk(basePathOverride: 'http://localhost:9876');
    addTearDown(() => overridden.dio.close(force: true));
    expect(overridden.dio.options.baseUrl, 'http://localhost:9876');
    expect(overridden.dio.options.receiveTimeout, isNull);
  });

  test('injected Dio configuration is preserved', () {
    final dio = Dio(
      BaseOptions(
        baseUrl: 'http://caller.invalid:7777',
        receiveTimeout: const Duration(seconds: 19),
      ),
    );
    addTearDown(() => dio.close(force: true));

    final sdk = OpencodeSdk(
      dio: dio,
      basePathOverride: 'http://ignored.invalid',
    );

    expect(identical(sdk.dio, dio), isTrue);
    expect(sdk.dio.options.baseUrl, 'http://caller.invalid:7777');
    expect(sdk.dio.options.receiveTimeout, const Duration(seconds: 19));
  });

  test('optional request bodies omit payload and Content-Type', () async {
    late String body;
    String? contentType;
    final fixture = await _Fixture.start((request) async {
      body = await utf8.decoder.bind(request).join();
      contentType = request.headers.value(HttpHeaders.contentTypeHeader);
      request.response.statusCode = HttpStatus.ok;
      await request.response.close();
    });
    addTearDown(fixture.close);

    await fixture.sdk.getSessionApi().partUpdate(
      sessionID: 'session',
      messageID: 'message',
      partID: 'part',
    );

    expect(body, isEmpty);
    expect(contentType, isNull);
  });

  test('path parameters are RFC3986-encoded as individual segments', () async {
    late String target;
    final fixture = await _Fixture.start((request) async {
      target = request.uri.toString();
      request.response.statusCode = HttpStatus.ok;
      await request.response.close();
    });
    addTearDown(fixture.close);

    await fixture.sdk.getSessionsApi().v2SessionGet(
      sessionID: "AZaz09-._~:/?#[]@!\$&'()*+,;=%",
    );

    expect(
      target,
      '/api/session/AZaz09-._~%3A%2F%3F%23%5B%5D%40%21%24%26%27%28%29%2A%2B%2C%3B%3D%25',
    );
  });

  test(
    'union query values use their scalar branch without JSON quotes',
    () async {
      final queries = <Map<String, String>>[];
      final fixture = await _Fixture.start((request) async {
        queries.add(request.uri.queryParameters);
        request.response
          ..statusCode = HttpStatus.ok
          ..headers.contentType = ContentType.json
          ..write('[]');
        await request.response.close();
      });
      addTearDown(fixture.close);

      await fixture.sdk.getSessionApi().sessionList(
        roots: OpencodeSdkRawUnion068('true'),
      );
      await fixture.sdk.getExperimentalApi().experimentalSessionList(
        roots: OpencodeSdkRawUnion051(false),
        archived: OpencodeSdkRawUnion052('false'),
      );

      expect(queries[0]['roots'], 'true');
      expect(queries[1], containsPair('roots', 'false'));
      expect(queries[1], containsPair('archived', 'false'));
    },
  );

  test('v2.session.list omits a null workspace query', () async {
    late Uri uri;
    final fixture = await _Fixture.start((request) async {
      uri = request.uri;
      request.response.statusCode = HttpStatus.ok;
      await request.response.close();
    });
    addTearDown(fixture.close);

    await fixture.sdk.getSessionsApi().v2SessionList();

    expect(uri.queryParameters, isNot(contains('workspace')));
    expect(uri.hasQuery, isFalse);
  });

  test(
    'v2.fs.read wrapper preserves and safely encodes wildcard segments',
    () async {
      late String target;
      final fixture = await _Fixture.start((request) async {
        target = request.uri.toString();
        request.response
          ..statusCode = HttpStatus.ok
          ..headers.contentType = ContentType('application', 'octet-stream')
          ..add(<int>[1, 2, 3]);
        await request.response.close();
      });
      addTearDown(fixture.close);

      final response = await fixture.sdk.v2FsReadPath(path: 'folder/a b#%.txt');

      expect(target, '/api/fs/read/folder/a%20b%23%25.txt');
      expect(response.data, Uint8List.fromList(<int>[1, 2, 3]));
      await expectLater(
        fixture.sdk.v2FsReadPath(path: '../secret'),
        throwsArgumentError,
      );
      await expectLater(
        fixture.sdk.v2FsReadPath(path: r'..\secret'),
        throwsArgumentError,
      );
      await expectLater(
        fixture.sdk.v2FsReadPath(path: 'C:/secret'),
        throwsArgumentError,
      );
    },
  );

  test(
    'standard SDK maps declared errors with caller Dio and interceptors',
    () async {
      var callerInterceptorRan = false;
      final fixture = await _Fixture.start((request) async {
        request.response
          ..statusCode = HttpStatus.notFound
          ..headers.contentType = ContentType.json
          ..write(
            '{"_tag":"SessionNotFoundError","sessionID":"missing","message":"missing"}',
          );
        await request.response.close();
      }, createSdk: false);
      addTearDown(fixture.close);
      fixture.dio.interceptors.add(
        InterceptorsWrapper(
          onRequest: (options, handler) {
            callerInterceptorRan = true;
            handler.next(options);
          },
        ),
      );
      final sdk = OpencodeSdk(
        dio: fixture.dio,
        interceptors: <Interceptor>[InterceptorsWrapper()],
      );

      OpenCodeApiException? caught;
      try {
        await sdk.getSessionsApi().v2SessionGet(sessionID: 'missing');
      } on OpenCodeApiException catch (error) {
        caught = error;
      }

      expect(callerInterceptorRan, isTrue);
      expect(
        fixture.dio.interceptors.whereType<OpenCodeApiErrorInterceptor>(),
        hasLength(1),
      );
      expect(caught, isNotNull);
      expect(caught!.operationId, 'v2.session.get');
      expect(caught!.statusCode, HttpStatus.notFound);
      expect(caught!.mediaType, 'application/json');
      expect(caught!.rawPayload, isA<Map<String, dynamic>>());
      expect(caught!.decodedPayload, isA<SessionNotFoundError>());
      expect(caught!.payloadAs<SessionNotFoundError>()!.sessionID, 'missing');
      final schema = caught!.schemaDescriptor as Map<String, dynamic>;
      expect(
        (schema['anyOf'] as List).cast<Map<String, dynamic>>().map(
          (branch) => branch[r'$ref'],
        ),
        everyElement('#/components/schemas/SessionNotFoundError'),
      );
    },
  );

  test(
    'permissive validateStatus cannot deserialize an error as success',
    () async {
      final fixture = await _Fixture.start((request) async {
        request.response
          ..statusCode = HttpStatus.notFound
          ..headers.contentType = ContentType.json
          ..write(
            '{"_tag":"SessionNotFoundError","sessionID":"missing","message":"missing"}',
          );
        await request.response.close();
      });
      addTearDown(fixture.close);

      await expectLater(
        fixture.sdk.getSessionsApi().v2SessionGet(
          sessionID: 'missing',
          validateStatus: (_) => true,
        ),
        throwsA(
          isA<OpenCodeApiException>().having(
            (error) => error.statusCode,
            'statusCode',
            HttpStatus.notFound,
          ),
        ),
      );
    },
  );

  test('direct API requests carry canonical operationId metadata', () async {
    String? operationId;
    final fixture = await _Fixture.start((request) async {
      request.response.statusCode = HttpStatus.ok;
      await request.response.close();
    }, createSdk: false);
    addTearDown(fixture.close);
    fixture.dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) {
          operationId = options.extra['operationId'] as String?;
          handler.next(options);
        },
      ),
    );

    await SessionsApi(fixture.dio).v2SessionGet(sessionID: 'session');

    expect(operationId, 'v2.session.get');
  });
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
