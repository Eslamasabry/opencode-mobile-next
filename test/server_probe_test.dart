import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/server_probe.dart';

/// Serves canned HTTP answers per path so every probe branch is exercised
/// without a network.
class _FakeAdapter implements HttpClientAdapter {
  _FakeAdapter(this.handler);

  final ResponseBody Function(RequestOptions options) handler;
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    return handler(options);
  }

  @override
  void close({bool force = false}) {}
}

ResponseBody _json(String body, int status) => ResponseBody.fromString(
  body,
  status,
  headers: {
    Headers.contentTypeHeader: ['application/json'],
  },
);

ResponseBody _empty(int status) => ResponseBody.fromString('', status);

DioException _transportFailure({
  DioExceptionType type = DioExceptionType.connectionError,
  Object? cause,
}) => DioException(
  requestOptions: RequestOptions(path: '/global/health'),
  type: type,
  error: cause,
);

void main() {
  tearDown(() => serverProbeAdapterFactory = null);

  Future<ServerProbeResult> probe({
    required ResponseBody Function(RequestOptions options) handler,
    String? password,
    String? username,
    void Function(_FakeAdapter adapter)? capture,
  }) {
    final adapter = _FakeAdapter(handler);
    capture?.call(adapter);
    serverProbeAdapterFactory = () => adapter;
    return probeServerConnection(
      baseUrl: 'http://server.test:4097',
      username: username,
      password: password,
    );
  }

  test('a v2 401 with no password is a positive v2 needs-password verdict',
      () async {
    // v2's Basic-auth gate answers 401 with an EMPTY body — status-only
    // detection, no envelope to parse.
    final result = await probe(
      handler: (options) => options.path.endsWith('/api/health')
          ? _empty(401)
          : _empty(404),
    );
    expect(result.ok, isFalse);
    expect(result.flavor, ServerFlavor.v2);
    expect(result.needsPassword, isTrue);
    expect(result.message, 'This server requires its serve password.');
    expect(result.suggestsMissingServer, isFalse);
  });

  test('a v2 401 with a password supplied is the wrong-password verdict',
      () async {
    late _FakeAdapter adapter;
    final result = await probe(
      handler: (options) => options.path.endsWith('/api/health')
          ? _empty(401)
          : _empty(404),
      password: 'not-the-password',
      capture: (a) => adapter = a,
    );
    expect(result.ok, isFalse);
    expect(result.flavor, ServerFlavor.v2);
    expect(result.needsPassword, isTrue);
    expect(result.message, contains('Password rejected'));
    expect(result.message, contains('OPENCODE_PASSWORD'));
    // The probe authenticates exactly like the transport: Basic with the
    // `opencode` default username.
    expect(
      adapter.requests.single.headers['Authorization'],
      'Basic ${base64Encode(utf8.encode('opencode:not-the-password'))}',
    );
  });

  test('a v2 200 health answer connects with flavor and version', () async {
    final result = await probe(
      handler: (options) => options.path.endsWith('/api/health')
          ? _json(
              '{"healthy":true,"version":"0.0.0-beta-18600","pid":42}',
              200,
            )
          : _empty(404),
      password: 'the-password',
    );
    expect(result.ok, isTrue);
    expect(result.flavor, ServerFlavor.v2);
    expect(result.version, '0.0.0-beta-18600');
    expect(result.needsPassword, isFalse);
  });

  test('a v2 503 while booting says the server is starting', () async {
    final result = await probe(
      handler: (options) => options.path.endsWith('/api/health')
          ? _json('{"code":"service_starting"}', 503)
          : _empty(404),
    );
    expect(result.ok, isFalse);
    expect(result.flavor, ServerFlavor.v2);
    expect(result.message, contains('starting'));
    expect(result.needsPassword, isFalse);
  });

  test('a v2 health answer reporting unhealthy fails without a v1 fallback',
      () async {
    final result = await probe(
      handler: (options) => options.path.endsWith('/api/health')
          ? _json('{"healthy":false,"version":"0.0.0-beta-18600"}', 200)
          : _empty(404),
    );
    expect(result.ok, isFalse);
    expect(result.flavor, ServerFlavor.v2);
    expect(result.message, contains('unhealthy'));
  });

  test('a 404 on /api/health falls through to the v1 health check', () async {
    final result = await probe(
      handler: (options) => options.path.endsWith('/api/health')
          ? _empty(404)
          : _json('{"healthy":true,"version":"0.3.5"}', 200),
    );
    expect(result.ok, isTrue);
    expect(result.flavor, ServerFlavor.v1);
    expect(result.version, '0.3.5');
  });

  test('an address answering neither health route is not an OpenCode server',
      () async {
    final result = await probe(handler: (_) => _empty(404));
    expect(result.ok, isFalse);
    expect(result.flavor, ServerFlavor.unknown);
    expect(result.message, contains('not like an OpenCode server'));
    expect(result.message, contains('404'));
  });

  test('a 200 that is not the v2 health JSON still reaches the v1 check',
      () async {
    // Some other web app answering 200 on /api/health must not read as v2.
    final result = await probe(
      handler: (options) => options.path.endsWith('/api/health')
          ? _json('{"status":"ok"}', 200)
          : _json('{"healthy":true,"version":"0.3.9"}', 200),
    );
    expect(result.ok, isTrue);
    expect(result.flavor, ServerFlavor.v1);
    expect(result.version, '0.3.9');
  });

  test('a failed host lookup is explained as an address spelling problem', () {
    // Android surfaces DNS failures as a SocketException whose osError says
    // no address was associated with the hostname.
    final result = explainServerProbeFailure(
      _transportFailure(
        cause: const SocketException(
          "Failed host lookup: 'opencode.exampel'",
          osError: OSError('No address associated with hostname', 7),
        ),
      ),
    );
    expect(result.ok, isFalse);
    expect(
      result.message,
      'That host name could not be found. Check the address spelling.',
    );
    // A typo'd hostname is not a missing server; the guide pointer stays off.
    expect(result.suggestsMissingServer, isFalse);
  });

  test('glibc-style resolver errors also map to the spelling verdict', () {
    final result = explainServerProbeFailure(
      _transportFailure(
        cause: const SocketException(
          "Failed host lookup: 'opencode.exampel'",
          osError: OSError('Name or service not known', -2),
        ),
      ),
    );
    expect(
      result.message,
      'That host name could not be found. Check the address spelling.',
    );
  });

  test('a refused connection keeps the is-the-server-running verdict', () {
    final result = explainServerProbeFailure(
      _transportFailure(
        cause: const SocketException(
          'Connection refused',
          osError: OSError('Connection refused', 111),
        ),
      ),
    );
    expect(result.ok, isFalse);
    expect(
      result.message,
      'The connection was refused. Is opencode serve running on that '
      'host and port?',
    );
    expect(result.suggestsMissingServer, isTrue);
  });

  test('timeouts keep their verdict and point at the setup guide', () {
    for (final type in [
      DioExceptionType.connectionTimeout,
      DioExceptionType.receiveTimeout,
      DioExceptionType.sendTimeout,
    ]) {
      final result = explainServerProbeFailure(_transportFailure(type: type));
      expect(
        result.message,
        'The connection timed out. Check the address, and that the server '
        'is reachable from this phone.',
      );
      expect(result.suggestsMissingServer, isTrue);
    }
  });

  test('credential and TLS failures never claim a missing server', () {
    final credentials = explainServerProbeFailure(
      DioException(
        requestOptions: RequestOptions(path: '/global/health'),
        type: DioExceptionType.badResponse,
        response: Response(
          requestOptions: RequestOptions(path: '/global/health'),
          statusCode: 401,
        ),
      ),
    );
    expect(credentials.message, contains('refused the credentials'));
    expect(credentials.suggestsMissingServer, isFalse);

    final tls = explainServerProbeFailure(
      _transportFailure(type: DioExceptionType.badCertificate),
    );
    expect(tls.message, contains('TLS certificate'));
    expect(tls.suggestsMissingServer, isFalse);
  });
}
