import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:opencode_mobile/api2/dialect.dart';

const capabilityKeys = [
  'version',
  'create',
  'models',
  'modelSwitch',
  'stream',
  'abort',
  'reconnect',
  'permissionAllow',
  'permissionDeny',
  'image',
  'cards',
];

class ProbeFailure implements Exception {
  final String code;
  const ProbeFailure(this.code);
  @override
  String toString() => 'Protocol assertion failed';
}

class ProbeOptions {
  final String directory;
  final String title;
  final String? model;
  final Set<String>? capabilities;
  final Future<void> Function(String id)? onSessionCreated;
  const ProbeOptions({
    required this.directory,
    required this.title,
    this.model,
    this.capabilities,
    this.onSessionCreated,
  });
}

/// Bodies, credentials and arbitrary server exceptions stay in memory.
class Fq3Wire {
  final Uri baseUrl;
  final String _authorization;
  HttpClient _client = HttpClient();
  HttpClient? _eventClient;
  StreamSubscription<String>? _eventSubscription;
  final events = <Map<String, dynamic>>[];
  final _notifications = StreamController<Map<String, dynamic>>.broadcast();
  Fq3Wire({required String baseUrl, required String password})
    : baseUrl = Uri.parse(baseUrl),
      _authorization =
          'Basic ${base64Encode(utf8.encode('opencode:$password'))}' {
    if (this.baseUrl.host != '127.0.0.1' ||
        this.baseUrl.scheme != 'http' ||
        this.baseUrl.hasQuery ||
        this.baseUrl.userInfo.isNotEmpty) {
      throw const ProbeFailure('invalid_loopback_endpoint');
    }
    _client.connectionTimeout = const Duration(seconds: 5);
  }

  Uri _uri(String path, Map<String, String>? query) {
    if (!path.startsWith('/') || path.contains('?') || path.contains('#')) {
      throw const ProbeFailure('invalid_request_path');
    }
    return baseUrl.replace(path: path, queryParameters: query);
  }

  bool _stableOc2 = false;
  bool get isStableOc2 => _stableOc2;
  Future<dynamic> request(
    String method,
    String path, {
    Object? body,
    Map<String, String>? query,
  }) async {
    if (path.startsWith('/api/') && _stableOc2) {
      final route = stableRoute(
        method,
        path.substring(4),
        body: body,
        query: query,
      );
      final result = await _rawRequest(
        route.method,
        '/api${route.path}',
        body: route.body,
        query: route.query?.map((k, v) => MapEntry(k, v.toString())),
      );
      return stableResponse(method, path.substring(4), result);
    }
    try {
      return await _rawRequest(method, path, body: body, query: query);
    } on ProbeFailure catch (error) {
      if (method != 'GET' ||
          path != '/api/health' ||
          error.code != 'http_404') {
        rethrow;
      }
      final info = await _rawRequest('GET', '/api/info');
      if (info is! Map ||
          info['version'] is! String ||
          (info['pid'] is! num && info['urls'] is! List)) {
        throw const ProbeFailure('invalid_stable_server_info');
      }
      _stableOc2 = true;
      return stableResponse('GET', '/health', info);
    }
  }

  Future<dynamic> _rawRequest(
    String method,
    String path, {
    Object? body,
    Map<String, String>? query,
  }) async {
    try {
      final request = await _client
          .openUrl(method, _uri(path, query))
          .timeout(const Duration(seconds: 8));
      request.followRedirects = false;
      request.headers.set(HttpHeaders.authorizationHeader, _authorization);
      if (body != null) {
        request.headers.contentType = ContentType.json;
        request.write(jsonEncode(body));
      }
      final response = await request.close().timeout(
        const Duration(seconds: 75),
      );
      if (response.statusCode < 200 || response.statusCode >= 300) {
        await response.drain<void>().timeout(const Duration(seconds: 3));
        throw ProbeFailure('http_${response.statusCode}');
      }
      final bytes = <int>[];
      await for (final chunk in response.timeout(const Duration(seconds: 75))) {
        bytes.addAll(chunk);
        if (bytes.length > 8 * 1024 * 1024) {
          throw const ProbeFailure('response_too_large');
        }
      }
      if (bytes.isEmpty) return null;
      return jsonDecode(utf8.decode(bytes));
    } on ProbeFailure {
      rethrow;
    } on TimeoutException {
      throw const ProbeFailure('timeout');
    } catch (_) {
      throw const ProbeFailure('request_failed');
    }
  }

  Future<void> openEvents(String path, {Map<String, String>? query}) async {
    await closeEvents();
    final client = HttpClient()..connectionTimeout = const Duration(seconds: 5);
    _eventClient = client;
    try {
      final request = await client.getUrl(_uri(path, query));
      request.followRedirects = false;
      request.headers.set(HttpHeaders.authorizationHeader, _authorization);
      request.headers.set(HttpHeaders.acceptHeader, 'text/event-stream');
      // OC2 is volatile: deliberately no Last-Event-ID header or replay cursor.
      final response = await request.close().timeout(
        const Duration(seconds: 8),
      );
      if (response.statusCode != 200 ||
          !((response.headers.contentType?.mimeType ?? '').contains(
            'text/event-stream',
          ))) {
        throw const ProbeFailure('event_stream_unavailable');
      }
      var data = <String>[];
      _eventSubscription = response
          .transform(utf8.decoder)
          .transform(const LineSplitter())
          .listen((line) {
            if (line.isEmpty) {
              if (data.isNotEmpty) {
                try {
                  final decoded = jsonDecode(data.join('\n'));
                  if (decoded is Map<String, dynamic>) {
                    events.add(decoded);
                    if (events.length > 2000) events.removeAt(0);
                    _notifications.add(decoded);
                  }
                } catch (_) {
                  /* Untrusted payloads never enter diagnostics. */
                }
                data = [];
              }
            } else if (line.startsWith('data:')) {
              if (data.length < 256 && line.length < 1024 * 1024) {
                data.add(line.substring(5).trimLeft());
              }
            }
          }, onError: (Object _) {});
    } on ProbeFailure {
      await closeEvents();
      rethrow;
    } catch (_) {
      await closeEvents();
      throw const ProbeFailure('event_stream_unavailable');
    }
  }

  Future<Map<String, dynamic>> waitFor(
    bool Function(Map<String, dynamic>) predicate, {
    Duration timeout = const Duration(seconds: 60),
  }) async {
    // Callers scope predicates by session and fresh message/event index.
    for (final event in events.reversed) {
      if (predicate(event)) return event;
    }
    try {
      return await _notifications.stream.firstWhere(predicate).timeout(timeout);
    } on TimeoutException {
      throw const ProbeFailure('timeout');
    }
  }

  Future<void> closeEvents() async {
    _eventClient?.close(force: true);
    _eventClient = null;
    await _eventSubscription?.cancel();
    _eventSubscription = null;
  }

  void reconnectHttp() {
    _client.close(force: true);
    _client = HttpClient()..connectionTimeout = const Duration(seconds: 5);
  }

  Future<void> close() async {
    await closeEvents();
    _client.close(force: true);
    await _notifications.close();
  }
}

class ProbeRun {
  final Fq3Wire wire;
  final ProbeOptions options;
  final results = <String, Map<String, Object?>>{};
  final sessionIDs = <String>[];
  final historyCounts = <String, int>{};
  String? observedVersion;
  ProbeRun(this.wire, this.options);

  void require(bool condition, String code) {
    if (!condition) throw ProbeFailure(code);
  }

  Future<void> check(
    String key,
    Future<Map<String, Object?>> Function() action,
  ) async {
    if (options.capabilities != null &&
        !options.capabilities!.contains(key) &&
        !const {'version', 'create', 'models'}.contains(key)) {
      return;
    }
    try {
      final facts = await action().timeout(const Duration(seconds: 180));
      if (facts.values.any((value) => value is! num && value is! bool)) {
        throw const ProbeFailure('invalid_evidence_facts');
      }
      results[key] = {
        'state': 'pass',
        'code': 'verified',
        'facts': <String, Object?>{...facts, 'asserted': true},
      };
    } catch (error) {
      final candidate = error is ProbeFailure
          ? error.code
          : error is TimeoutException
          ? 'timeout'
          : 'scenario_failed';
      final code = RegExp(r'^[a-z][a-z0-9_]{0,63}$').hasMatch(candidate)
          ? candidate
          : 'scenario_failed';
      results[key] = {
        'state': 'fail',
        'code': code,
        'facts': <String, Object?>{},
      };
    }
    stdout.writeln(
      '$key: ${results[key]!['state']} (${results[key]!['code']})',
    );
  }

  void completeMissing(String code) {
    for (final key in capabilityKeys) {
      results.putIfAbsent(
        key,
        () => {'state': 'fail', 'code': code, 'facts': <String, Object?>{}},
      );
    }
  }
}
