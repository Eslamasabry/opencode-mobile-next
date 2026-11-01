import 'dart:io';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:dio/io.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/transport.dart';
import 'package:opencode_mobile/network/platform_dio.dart';
import 'package:opencode_mobile/network/transport_failure.dart';
import 'package:opencode_mobile/orchestration/client/http.dart';

DioException _failure(
  DioExceptionType type, {
  Object? cause,
  String? message,
}) => DioException(
  requestOptions: RequestOptions(path: '/health'),
  type: type,
  error: cause,
  message: message,
);

void main() {
  test('native builds keep the dart:io adapter Dio created', () {
    final dio = Dio();
    final before = dio.httpClientAdapter;
    configurePlatformDio(dio);
    expect(dio.httpClientAdapter, same(before));
    expect(dio.httpClientAdapter, isA<IOHttpClientAdapter>());
    expect(platformDioUsesBrowserTransport, isFalse);
  });

  test('every transport configures the platform adapter on its own Dio', () {
    final v2 = Api2Transport(baseUrl: 'http://127.0.0.1:1', password: 'pw');
    addTearDown(v2.close);
    expect(v2.dio.httpClientAdapter, isA<IOHttpClientAdapter>());

    final team = OrchestrationHttpClient(
      baseUrl: 'http://127.0.0.1:1',
      city: 'demo',
    );
    addTearDown(team.close);
    expect(team.dio.httpClientAdapter, isA<IOHttpClientAdapter>());
  });

  test('an injected Dio is left to its owner', () {
    final injected = Dio();
    final fake = _NoopAdapter();
    injected.httpClientAdapter = fake;
    final team = OrchestrationHttpClient(
      baseUrl: 'http://127.0.0.1:1',
      city: 'demo',
      dio: injected,
    );
    addTearDown(team.close);
    expect(team.dio.httpClientAdapter, same(fake));
  });

  test('only stream responses take the incremental path', () {
    expect(
      platformDioStreamsRequest(
        RequestOptions(path: '/event', responseType: ResponseType.stream),
      ),
      isTrue,
    );
    for (final type in [
      ResponseType.json,
      ResponseType.plain,
      ResponseType.bytes,
    ]) {
      expect(
        platformDioStreamsRequest(
          RequestOptions(path: '/x', responseType: type),
        ),
        isFalse,
        reason: '$type',
      );
    }
  });

  test('host lookup detection reads the socket error, not the message', () {
    expect(
      isHostLookupFailure(
        const SocketException(
          "Failed host lookup: 'opencode.exampel'",
          osError: OSError('No address associated with hostname', 7),
        ),
      ),
      isTrue,
    );
    expect(
      isHostLookupFailure(
        const SocketException(
          'Connection refused',
          osError: OSError('Connection refused', 111),
        ),
      ),
      isFalse,
    );
    expect(isHostLookupFailure('Failed host lookup'), isFalse);
    expect(isHostLookupFailure(null), isFalse);
  });

  test('browser failure copy lists the real possibilities and no guess', () {
    final message = browserNetworkFailureMessage();
    expect(message, contains('CORS'));
    expect(message, contains('https'));
    expect(message, isNot(contains('refused')));
    expect(message, isNot(contains('host name')));
    // The browser's own error text says nothing a person can act on.
    expect(message, isNot(contains('(Type')));
    expect(message, isNot(contains('Failed to fetch')));
  });

  test('OpenCode 2 no-status failures tell the browser truth', () {
    final blocked = Api2Transport.mapError(
      _failure(
        DioExceptionType.connectionError,
        message: 'The XMLHttpRequest onError callback was called.',
      ),
      'GET /health',
      browser: true,
    );
    expect(blocked, isA<Api2NetworkError>());
    expect(blocked.message, startsWith('GET /health failed: '));
    expect(blocked.message, contains('CORS'));
    expect(blocked.message, isNot(contains('XMLHttpRequest')));

    final timedOut =
        Api2Transport.mapError(
              _failure(DioExceptionType.receiveTimeout, message: 'slow'),
              'GET /health',
              browser: true,
            )
            as Api2NetworkError;
    expect(timedOut.timedOut, isTrue);
    expect(timedOut.message, isNot(contains('CORS')));

    final cancelled =
        Api2Transport.mapError(
              _failure(DioExceptionType.cancel, message: 'stopped'),
              'GET /health',
              browser: true,
            )
            as Api2NetworkError;
    expect(cancelled.cancelled, isTrue);
    expect(cancelled.message, contains('stopped'));

    final native = Api2Transport.mapError(
      _failure(DioExceptionType.connectionError, message: 'refused'),
      'GET /health',
      browser: false,
    );
    expect(native.message, 'GET /health failed: refused');
  });
}

class _NoopAdapter implements HttpClientAdapter {
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async => ResponseBody.fromString('', 204);

  @override
  void close({bool force = false}) {}
}
