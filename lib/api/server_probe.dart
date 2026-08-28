import 'dart:convert';

import 'package:dio/dio.dart';

import 'models.dart';

/// Outcome of a pre-save connection test against an OpenCode server.
class ServerProbeResult {
  final bool ok;

  /// Server version when the probe succeeded and the server reported one.
  final String? version;

  /// A product-facing explanation when the probe failed.
  final String? message;

  const ServerProbeResult.success(this.version) : ok = true, message = null;
  const ServerProbeResult.failure(this.message) : ok = false, version = null;
}

typedef ServerProbe =
    Future<ServerProbeResult> Function({
      required String baseUrl,
      String? username,
      String? password,
    });

/// The active probe. Production leaves this at [probeServerConnection];
/// widget tests replace it to simulate server behavior and restore it in
/// tearDown.
ServerProbe serverProbe = probeServerConnection;

/// Calls the server's global health endpoint with a bounded timeout and maps
/// each transport failure to a specific, truthful explanation. Credentials
/// follow the exact Basic scheme the app's transport uses, including the
/// `opencode` default username when only a password is set.
Future<ServerProbeResult> probeServerConnection({
  required String baseUrl,
  String? username,
  String? password,
}) async {
  final headers = <String, Object>{};
  if (password != null && password.isNotEmpty) {
    final user = (username == null || username.isEmpty)
        ? 'opencode'
        : username;
    headers['Authorization'] =
        'Basic ${base64Encode(utf8.encode('$user:$password'))}';
  }
  final dio = Dio(
    BaseOptions(
      baseUrl: baseUrl.endsWith('/')
          ? baseUrl.substring(0, baseUrl.length - 1)
          : baseUrl,
      connectTimeout: const Duration(seconds: 8),
      receiveTimeout: const Duration(seconds: 8),
      headers: headers,
      validateStatus: (status) => status != null && status >= 200 && status < 300,
    ),
  );
  try {
    final response = await dio.get<Map>('/global/health');
    final health = Health.fromJson(
      Map<String, dynamic>.from(response.data ?? const {}),
    );
    if (!health.healthy) {
      return const ServerProbeResult.failure(
        'The server responded but reported itself unhealthy. '
        'Check its logs, then try again.',
      );
    }
    return ServerProbeResult.success(health.version);
  } on DioException catch (error) {
    return ServerProbeResult.failure(_explain(error));
  } catch (error) {
    return ServerProbeResult.failure('Connection test failed: $error');
  } finally {
    dio.close(force: true);
  }
}

String _explain(DioException error) {
  final status = error.response?.statusCode;
  if (status == 401 || status == 403) {
    return 'The server refused the credentials. '
        'Check the username and password.';
  }
  if (status != null) {
    return 'The address responded, but not like an OpenCode server '
        '(HTTP $status). Check that the URL points at opencode serve.';
  }
  return switch (error.type) {
    DioExceptionType.connectionTimeout ||
    DioExceptionType.receiveTimeout ||
    DioExceptionType.sendTimeout =>
      'The connection timed out. Check the address, and that the server '
          'is reachable from this phone.',
    DioExceptionType.badCertificate =>
      'The server’s TLS certificate was rejected. '
          'Use a certificate this phone trusts.',
    DioExceptionType.connectionError =>
      'The connection was refused. Is opencode serve running on that '
          'host and port?',
    _ => 'Connection test failed: ${error.message ?? error.type.name}',
  };
}
