import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/server_probe.dart';

DioException _transportFailure({
  DioExceptionType type = DioExceptionType.connectionError,
  Object? cause,
}) => DioException(
  requestOptions: RequestOptions(path: '/global/health'),
  type: type,
  error: cause,
);

void main() {
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
