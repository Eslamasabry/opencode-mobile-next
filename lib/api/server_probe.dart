import 'dart:convert';
import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import 'models.dart';

/// Which protocol generation answered a probe.
///
/// [v2] servers (`opencode2 serve`) speak the `/api/...` surface behind
/// mandatory HTTP Basic auth; [v1] servers answer the unprefixed routes
/// (`/global/health`). [unknown] means the probe never got a protocol-shaped
/// answer (unreachable address, non-OpenCode responder).
enum ServerFlavor { v1, v2, unknown }

/// Outcome of a pre-save connection test against an OpenCode server.
class ServerProbeResult {
  final bool ok;

  /// Server version when the probe succeeded and the server reported one.
  final String? version;

  /// A product-facing explanation when the probe failed.
  final String? message;

  /// True for timeout/connection-refused failures, where the likeliest cause
  /// is that no OpenCode server exists at the address yet — the editor points
  /// at the host setup guide for those.
  final bool suggestsMissingServer;

  /// Protocol generation detected by the probe. A 401 from `/api/health` is
  /// itself a positive v2 signal, so failures can still carry [ServerFlavor.v2].
  final ServerFlavor flavor;

  /// True when the server demands its serve password: either none was
  /// supplied, or the supplied one was rejected (still a 401).
  final bool needsPassword;

  const ServerProbeResult.success(
    this.version, {
    this.flavor = ServerFlavor.v1,
  }) : ok = true,
       message = null,
       suggestsMissingServer = false,
       needsPassword = false;
  const ServerProbeResult.failure(
    this.message, {
    this.suggestsMissingServer = false,
    this.flavor = ServerFlavor.unknown,
    this.needsPassword = false,
  }) : ok = false,
       version = null;
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

/// Test seam: when set, [probeServerConnection] routes its HTTP through this
/// adapter instead of the real network. Tests restore it to null in tearDown.
@visibleForTesting
HttpClientAdapter Function()? serverProbeAdapterFactory;

/// Detects the server flavor and verifies the connection ahead of Save.
///
/// Order of checks (docs/opencode2-ui-design.md §1):
/// 1. `GET /api/health` with Basic `opencode:<password>`. A 401 (empty body,
///    status-only detection) is a POSITIVE v2 signal — the verdict then only
///    depends on whether a password was supplied. A 2xx carrying the v2
///    health JSON (`version` present) is v2 with valid/no auth. A 503 is a
///    v2 server still booting.
/// 2. Anything else falls through to the v1 `GET /global/health` check.
/// Transport failures map to the specific, truthful explanations in
/// [explainServerProbeFailure] (DNS vs refused vs timeout kept distinct).
Future<ServerProbeResult> probeServerConnection({
  required String baseUrl,
  String? username,
  String? password,
}) async {
  final hasPassword = password != null && password.isNotEmpty;
  final headers = <String, Object>{};
  if (hasPassword) {
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
  final adapterFactory = serverProbeAdapterFactory;
  if (adapterFactory != null) dio.httpClientAdapter = adapterFactory();
  try {
    final v2 = await _probeV2(dio, hasPassword: hasPassword);
    if (v2 != null) return v2;
    final response = await dio.get<Map>('/global/health');
    final health = Health.fromJson(
      Map<String, dynamic>.from(response.data ?? const {}),
    );
    if (!health.healthy) {
      return const ServerProbeResult.failure(
        'The server responded but reported itself unhealthy. '
        'Check its logs, then try again.',
        flavor: ServerFlavor.v1,
      );
    }
    return ServerProbeResult.success(health.version, flavor: ServerFlavor.v1);
  } on DioException catch (error) {
    return explainServerProbeFailure(error);
  } catch (error) {
    return ServerProbeResult.failure('Connection test failed: $error');
  } finally {
    dio.close(force: true);
  }
}

/// One-shot v2 flavor detection against `GET /api/health`. Returns null when
/// the answer is not v2-shaped so the caller falls through to the v1 checks.
/// Transport failures (DNS, refused, timeout) propagate as [DioException] —
/// they mean the address itself is unreachable, not a different flavor.
Future<ServerProbeResult?> _probeV2(
  Dio dio, {
  required bool hasPassword,
}) async {
  final response = await dio.get<dynamic>(
    '/api/health',
    options: Options(
      // Inspect every HTTP answer here; only transport failures throw.
      validateStatus: (status) => status != null,
      responseType: ResponseType.json,
    ),
  );
  final status = response.statusCode ?? 0;
  if (status == 401) {
    // The v2 Basic-auth gate answers 401 with an EMPTY body on every route,
    // /api/health included — the status alone is the v2 signal.
    if (!hasPassword) {
      return const ServerProbeResult.failure(
        'This server requires its serve password.',
        flavor: ServerFlavor.v2,
        needsPassword: true,
      );
    }
    return const ServerProbeResult.failure(
      'Password rejected. Copy the current "server password" line from the '
      'server output — it changes on every restart unless OPENCODE_PASSWORD '
      'is set.',
      flavor: ServerFlavor.v2,
      needsPassword: true,
    );
  }
  if (status >= 200 && status < 300) {
    final data = response.data;
    final map = data is Map ? Map<String, dynamic>.from(data) : null;
    if (map != null && map['version'] != null) {
      if (map['healthy'] == false) {
        return const ServerProbeResult.failure(
          'The server responded but reported itself unhealthy. '
          'Check its logs, then try again.',
          flavor: ServerFlavor.v2,
        );
      }
      return ServerProbeResult.success(
        map['version']?.toString(),
        flavor: ServerFlavor.v2,
      );
    }
    // A 2xx that is not the v2 health JSON: some other responder owns
    // /api/health. Let the v1 check decide.
    return null;
  }
  if (status == 503) {
    // v2 answers 503 (`service_starting`) while its app layer boots.
    return const ServerProbeResult.failure(
      'The server is starting. Try again in a moment.',
      flavor: ServerFlavor.v2,
    );
  }
  // 404 and friends: no v2 surface at this address — try the v1 route.
  return null;
}

/// Maps a transport failure to a truthful product-facing probe verdict.
/// Public so tests can exercise the mapping without a live network.
ServerProbeResult explainServerProbeFailure(DioException error) {
  final status = error.response?.statusCode;
  if (status == 401 || status == 403) {
    return const ServerProbeResult.failure(
      'The server refused the credentials. '
      'Check the username and password.',
    );
  }
  if (status != null) {
    return ServerProbeResult.failure(
      'The address responded, but not like an OpenCode server '
      '(HTTP $status). Check that the URL points at opencode serve.',
    );
  }
  // A hostname that never resolved is a spelling problem, not a server
  // problem — send the user to the address, not to the server.
  if (_isHostLookupFailure(error)) {
    return const ServerProbeResult.failure(
      'That host name could not be found. Check the address spelling.',
    );
  }
  return switch (error.type) {
    DioExceptionType.connectionTimeout ||
    DioExceptionType.receiveTimeout ||
    DioExceptionType.sendTimeout => const ServerProbeResult.failure(
      'The connection timed out. Check the address, and that the server '
      'is reachable from this phone.',
      suggestsMissingServer: true,
    ),
    DioExceptionType.badCertificate => const ServerProbeResult.failure(
      'The server’s TLS certificate was rejected. '
      'Use a certificate this phone trusts.',
    ),
    DioExceptionType.connectionError => const ServerProbeResult.failure(
      'The connection was refused. Is opencode serve running on that '
      'host and port?',
      suggestsMissingServer: true,
    ),
    _ => ServerProbeResult.failure(
      'Connection test failed: ${error.message ?? error.type.name}',
    ),
  };
}

/// True when the failure is DNS resolution (the host name never resolved),
/// as opposed to a reachable host refusing the connection. Checked through
/// [SocketException.osError] so a typo'd hostname is not mislabeled
/// "connection refused".
bool _isHostLookupFailure(DioException error) {
  final cause = error.error;
  if (cause is! SocketException) return false;
  final details = '${cause.message} ${cause.osError?.message ?? ''}'
      .toLowerCase();
  return details.contains('failed host lookup') ||
      details.contains('no address associated with hostname') ||
      details.contains('name or service not known') ||
      details.contains('nodename nor servname') ||
      details.contains('temporary failure in name resolution');
}
