import 'dart:convert';
import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import '../diagnostics/perf_trace.dart';
import '../ui/kit/kit_redact.dart';
import '../domain/loopback_host.dart' show isCleartextRemoteBase;

/// The supported protocol detected at a server address.
enum ServerFlavor { v2, unknown }

/// Install the supported server without modifying its conversation data.
const openCode2UpgradeCommand =
    'npm uninstall -g opencode-ai && npm install -g @opencode/cli@2.1.1';
const unsupportedOpenCode1Message =
    'This server runs OpenCode 1, which this app no longer supports. '
    'Update it to OpenCode 2.';

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

  /// A legacy server was recognized, but no client connection was attempted.
  final bool unsupportedLegacy;

  const ServerProbeResult.success(this.version, {this.flavor = ServerFlavor.v2})
    : ok = true,
      message = null,
      suggestsMissingServer = false,
      needsPassword = false,
      unsupportedLegacy = false;
  const ServerProbeResult.failure(
    this.message, {
    this.suggestsMissingServer = false,
    this.flavor = ServerFlavor.unknown,
    this.needsPassword = false,
    this.unsupportedLegacy = false,
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
  KitRedact.registerKnownSecret(password ?? '');
  final hasPassword = password != null && password.isNotEmpty;
  final headers = <String, Object>{};
  if (hasPassword) {
    final user = (username == null || username.isEmpty) ? 'opencode' : username;
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
      followRedirects: !isCleartextRemoteBase(baseUrl),
      maxRedirects: isCleartextRemoteBase(baseUrl) ? 0 : 5,
      headers: headers,
      validateStatus: (status) =>
          status != null && status >= 200 && status < 300,
    ),
  );
  PerfTraceInterceptor.attach(dio, 'probe');
  final adapterFactory = serverProbeAdapterFactory;
  if (adapterFactory != null) dio.httpClientAdapter = adapterFactory();
  try {
    final v2 = await _probeV2(dio, hasPassword: hasPassword);
    if (v2 != null) return v2;
    // Detection only: never construct a client or mutate legacy data.
    final response = await dio.get<Map>('/global/health');
    final body = response.data;
    if (body != null && body['healthy'] is bool && body['version'] is String) {
      return const ServerProbeResult.failure(
        unsupportedOpenCode1Message,
        unsupportedLegacy: true,
      );
    }
    return const ServerProbeResult.failure(
      'This address did not answer as an OpenCode 2 server. '
      'Check the server address.',
    );
  } on DioException catch (error) {
    return explainServerProbeFailure(error);
  } catch (error) {
    return const ServerProbeResult.failure(
      'The connection test could not finish. Check the server address and try again.',
    );
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
  // The beta answers at /api/health. The stable line (2.0.4 and later,
  // `@opencode/cli`) dropped that route and reports its version at /api/info,
  // so a clean 404 on the first is followed by one look at the second.
  var response = await dio.get<dynamic>(
    '/api/health',
    options: Options(
      // Inspect every HTTP answer here; only transport failures throw.
      validateStatus: (status) => status != null,
      responseType: ResponseType.json,
    ),
  );
  if (response.statusCode == 404) {
    final info = await dio.get<dynamic>(
      '/api/info',
      options: Options(
        validateStatus: (status) => status != null,
        responseType: ResponseType.json,
      ),
    );
    // Only the stable line's own shape counts (`version` beside `pid` or
    // `urls`): an OpenCode 1 server, or anything else that answers every
    // path, must still fall through to the v1 check.
    final body = info.data;
    final isInfo =
        info.statusCode == 200 &&
        body is Map &&
        body['version'] is String &&
        (body['pid'] is num || body['urls'] is List);
    if (isInfo || info.statusCode == 401 || info.statusCode == 503) {
      response = info;
    }
  }
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
      '(HTTP $status). Check that the URL points at opencode2 serve.',
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
      'The connection was refused. Is opencode2 serve running on that '
      'host and port?',
      suggestsMissingServer: true,
    ),
    _ => const ServerProbeResult.failure(
      'The connection test could not finish. Try again.',
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
