import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';

/// A read-only bridge for the app-owned connector search helper.
///
/// Credentials are handed directly to the helper, never embedded in [endpoint]
/// or diagnostic text. The callback owns catalog and connection policy.
final class McpConnectorSearchBridge {
  McpConnectorSearchBridge._(this._server, this._search, this._isCurrent)
    : bearer = _newBearer(),
      endpoint = Uri(
        scheme: 'http',
        host: InternetAddress.loopbackIPv4.address,
        port: _server.port,
        path: '/find-connectors',
      );

  static const _requestLimit = 2048;
  static const _responseLimit = 16384;
  static const _deadline = Duration(seconds: 5);

  static String _newBearer() {
    final random = Random.secure();
    return List<String>.generate(
      32,
      (_) => random.nextInt(256).toRadixString(16).padLeft(2, '0'),
    ).join();
  }

  final HttpServer _server;
  final Future<Map<String, Object?>> Function(Map<String, dynamic>) _search;
  final bool Function() _isCurrent;

  final Uri endpoint;
  final String bearer;
  int _active = 0;
  bool _closed = false;
  Future<void>? _closing;

  static Future<McpConnectorSearchBridge> start({
    required Future<Map<String, Object?>> Function(Map<String, dynamic>) search,
    required bool Function() isCurrent,
  }) async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    final bridge = McpConnectorSearchBridge._(server, search, isCurrent);
    server.listen(
      (request) => unawaited(bridge._serve(request)),
      onError: (Object _) {},
    );
    return bridge;
  }

  bool get _current {
    if (_closed) return false;
    try {
      return _isCurrent();
    } catch (_) {
      return false;
    }
  }

  bool _authorized(HttpRequest request) {
    final values = request.headers[HttpHeaders.authorizationHeader];
    if (values == null || values.length != 1) return false;
    final expected = 'Bearer $bearer';
    final actual = values.single;
    if (actual.length != expected.length) return false;
    var difference = 0;
    for (var index = 0; index < expected.length; index++) {
      difference |= actual.codeUnitAt(index) ^ expected.codeUnitAt(index);
    }
    return difference == 0;
  }

  Future<void> _serve(HttpRequest request) async {
    _BridgeReply reply;
    if (!_authorized(request)) {
      reply = const _BridgeReply.failure(401, 'Unauthorized');
    } else if (request.uri.path != '/find-connectors' || request.uri.hasQuery) {
      reply = const _BridgeReply.failure(404, 'Not found');
    } else if (request.method != 'POST') {
      reply = const _BridgeReply.failure(405, 'Method not allowed');
    } else if (!_current) {
      reply = const _BridgeReply.failure(403, 'Source unavailable');
    } else if (_active >= 4) {
      reply = const _BridgeReply.failure(429, 'Search busy');
    } else {
      _active++;
      final work = _resolve(request);
      // Timing out the HTTP response must not release admission while the
      // callback is still running; otherwise retries exceed four real calls.
      unawaited(
        work.then<void>(
          (_) => _active--,
          onError: (Object _, StackTrace _) => _active--,
        ),
      );
      try {
        reply = await work.timeout(_deadline);
      } on TimeoutException {
        reply = const _BridgeReply.failure(504, 'Search timed out');
      } catch (_) {
        reply = const _BridgeReply.failure(500, 'Search unavailable');
      }
    }
    if (_closed) return;
    try {
      final response = request.response;
      response.statusCode = reply.status;
      response.persistentConnection = false;
      response.headers.contentType = ContentType.json;
      response.headers.set(HttpHeaders.cacheControlHeader, 'no-store');
      response.headers.set('X-Content-Type-Options', 'nosniff');
      final bytes = reply.bytes;
      response.contentLength = bytes.length;
      response.add(bytes);
      await response.close();
    } catch (_) {
      // A helper may exit or disconnect while its search is pending.
    }
  }

  Future<_BridgeReply> _resolve(HttpRequest request) async {
    try {
      final body = await _readBody(request);
      final value = jsonDecode(utf8.decode(body));
      if (value is! Map<String, dynamic>) {
        return const _BridgeReply.failure(400, 'Invalid request');
      }
      if (!_current) {
        return const _BridgeReply.failure(403, 'Source unavailable');
      }
      final result = await _search(value);
      if (!_current) {
        return const _BridgeReply.failure(403, 'Source unavailable');
      }
      final bytes = utf8.encode(jsonEncode(result));
      if (bytes.length > _responseLimit) {
        return const _BridgeReply.failure(502, 'Search response too large');
      }
      return _BridgeReply.success(bytes);
    } on _RequestTooLarge {
      return const _BridgeReply.failure(413, 'Request too large');
    } on FormatException {
      return const _BridgeReply.failure(400, 'Invalid request');
    } on TimeoutException {
      return const _BridgeReply.failure(504, 'Search timed out');
    } catch (_) {
      if (!_current) {
        return const _BridgeReply.failure(403, 'Source unavailable');
      }
      return const _BridgeReply.failure(500, 'Search unavailable');
    }
  }

  Future<List<int>> _readBody(HttpRequest request) {
    if (request.contentLength > _requestLimit) {
      return Future.error(const _RequestTooLarge());
    }
    final complete = Completer<List<int>>();
    final bytes = <int>[];
    late final StreamSubscription<List<int>> subscription;
    late final Timer timer;
    void fail(Object error) {
      if (complete.isCompleted) return;
      timer.cancel();
      complete.completeError(error);
      unawaited(subscription.cancel());
    }

    timer = Timer(_deadline, () => fail(TimeoutException('Request timed out')));
    subscription = request.listen(
      (chunk) {
        if (complete.isCompleted) return;
        if (bytes.length + chunk.length > _requestLimit) {
          fail(const _RequestTooLarge());
          return;
        }
        bytes.addAll(chunk);
      },
      onError: (Object _) => fail(const FormatException('Invalid request')),
      onDone: () {
        if (complete.isCompleted) return;
        timer.cancel();
        complete.complete(bytes);
      },
    );
    return complete.future;
  }

  Future<void> close() {
    _closed = true;
    return _closing ??= _server.close(force: true).then<void>((_) {});
  }
}

final class _RequestTooLarge implements Exception {
  const _RequestTooLarge();
}

final class _BridgeReply {
  const _BridgeReply.success(List<int> value)
    : status = 200,
      _bytes = value,
      _error = null;

  const _BridgeReply.failure(this.status, String error)
    : _bytes = null,
      _error = error;

  final int status;
  final List<int>? _bytes;
  final String? _error;

  List<int> get bytes => _bytes ?? utf8.encode(jsonEncode({'error': _error}));
}
