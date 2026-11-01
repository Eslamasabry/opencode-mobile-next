/// Controllable HTTP/SSE/WebSocket server for the streaming contract tests.
///
/// Runs in-process for the VM run (`streaming_native_test.dart`) and as a
/// standalone process for the browser run, which cannot open listening
/// sockets itself:
///
/// ```bash
/// dart run test/network/support/streaming_test_server.dart   # prints the URL
/// flutter test --platform chrome \
///   --dart-define=OC_STREAM_TEST_SERVER=http://127.0.0.1:<port> \
///   test/network/streaming_browser_test.dart
/// ```
///
/// Every answer carries permissive CORS headers and `OPTIONS` preflights are
/// accepted, so a `flutter test --platform chrome` page (a different origin)
/// can send `Authorization`, `Accept: text/event-stream` and `Last-Event-ID`.
///
/// Endpoints:
/// - `GET /health`, `GET /api/health`: JSON; the latter looks like an
///   OpenCode 2 server so the connection probe can be exercised.
/// - `GET /stream/hold?id=X`: SSE. Sends `data: {"seq":1}` at once, then
///   `: ping` comments every 250 ms until `GET /stream/release?id=X`
///   arrives (then `data: {"seq":2}` and close) or the client disconnects.
/// - `GET /stream/stats?id=X`: JSON with the hold's `open`,
///   `hadAuthorization`, plus the event-stream and orchestration counters.
/// - `GET /api/event`: OpenCode 2 style event stream that closes ~300 ms
///   after `server.connected`, so a client must reconnect.
/// - `GET /v0/city/<city>/events/stream`: Gas City style stream. Emits three
///   `id:`-stamped frames continuing after `Last-Event-ID`, a heartbeat,
///   then closes.
/// - `/ws/echo`: WebSocket. Sends a `0x00{"cursor":7}` meta frame on
///   connect, echoes every frame (text as text, binary as binary), and
///   closes with 4000/"bye" on the text `close`.
library;

import 'dart:async';
import 'dart:convert';
import 'dart:io';

class _Hold {
  bool open = false;
  bool released = false;
  bool hadAuthorization = false;
  final release = Completer<void>();
}

class StreamingTestServer {
  StreamingTestServer._(this._server) {
    _server.listen(_handle);
  }

  final HttpServer _server;
  final _holds = <String, _Hold>{};

  int eventConnections = 0;
  bool eventOpen = false;
  bool eventHadAuthorization = false;

  int orchestrationConnections = 0;
  bool orchestrationHadAuthorization = false;
  final lastEventIds = <String>[];

  int webSocketConnections = 0;

  Uri get baseUri =>
      Uri.parse('http://${_server.address.address}:${_server.port}');

  static Future<StreamingTestServer> start({int port = 0}) async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, port);
    return StreamingTestServer._(server);
  }

  Future<void> close() => _server.close(force: true);

  Future<void> _handle(HttpRequest request) async {
    final path = request.uri.path;
    try {
      if (path == '/ws/echo' &&
          WebSocketTransformer.isUpgradeRequest(request)) {
        await _echoSocket(request);
        return;
      }
      final response = request.response;
      _cors(response);
      if (request.method == 'OPTIONS') {
        response.statusCode = HttpStatus.noContent;
        await response.close();
        return;
      }
      switch (path) {
        case '/health':
          await _json(response, {'ok': true});
        case '/api/health':
          await _json(response, {
            'healthy': true,
            'version': 'streaming-test',
            'pid': pid,
          });
        case '/stream/hold':
          await _hold(request);
        case '/stream/release':
          final hold = _holds[request.uri.queryParameters['id'] ?? ''];
          if (hold != null && !hold.release.isCompleted) {
            hold.released = true;
            hold.release.complete();
          }
          await _json(response, {'released': hold != null});
        case '/stream/stats':
          final hold = _holds[request.uri.queryParameters['id'] ?? ''];
          await _json(response, {
            'open': hold?.open ?? false,
            'released': hold?.released ?? false,
            'hadAuthorization': hold?.hadAuthorization ?? false,
            'eventConnections': eventConnections,
            'eventOpen': eventOpen,
            'eventHadAuthorization': eventHadAuthorization,
            'orchestrationConnections': orchestrationConnections,
            'orchestrationHadAuthorization': orchestrationHadAuthorization,
            'lastEventIds': lastEventIds,
            'webSocketConnections': webSocketConnections,
          });
        case '/api/event':
          await _v2Event(request);
        default:
          if (path.startsWith('/v0/city/') && path.endsWith('/events/stream')) {
            await _orchestration(request);
            return;
          }
          response.statusCode = HttpStatus.notFound;
          await _json(response, {'error': 'unknown path $path'});
      }
    } catch (_) {
      // A client that went away mid-write; nothing to answer.
    }
  }

  static void _cors(HttpResponse response) {
    response.headers
      ..set('Access-Control-Allow-Origin', '*')
      ..set('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
      ..set(
        'Access-Control-Allow-Headers',
        'authorization, accept, content-type, last-event-id, x-gc-request, '
            'x-opencode-ticket, idempotency-key',
      )
      ..set('Access-Control-Expose-Headers', 'content-type, x-gc-request-id')
      ..set('Access-Control-Max-Age', '600');
  }

  static Future<void> _json(HttpResponse response, Object body) async {
    response.headers.contentType = ContentType.json;
    response.write(jsonEncode(body));
    await response.close();
  }

  static void _sseHeaders(HttpResponse response) {
    response.statusCode = HttpStatus.ok;
    response.headers.contentType = ContentType(
      'text',
      'event-stream',
      charset: 'utf-8',
    );
    response.headers.set('Cache-Control', 'no-cache');
    response.bufferOutput = false;
  }

  Future<void> _hold(HttpRequest request) async {
    final id = request.uri.queryParameters['id'] ?? 'default';
    final hold = _holds.putIfAbsent(id, _Hold.new);
    hold.hadAuthorization = request.headers.value('authorization') != null;
    hold.open = true;
    final response = request.response;
    _sseHeaders(response);
    // Observe the TCP peer closing directly. HttpResponse.done alone does
    // not reliably expose a disconnected client before response.close().
    response.headers.chunkedTransferEncoding = false;
    response.persistentConnection = false;
    final socket = await response.detachSocket(writeHeaders: true);
    socket.listen(
      (_) {},
      onDone: () {
        hold.open = false;
        if (!hold.release.isCompleted) hold.release.complete();
      },
      onError: (Object _) {
        hold.open = false;
        if (!hold.release.isCompleted) hold.release.complete();
      },
    );
    try {
      socket.write('data: {"seq":1}\n\n');
      await socket.flush();
      while (!hold.release.isCompleted) {
        await Future.any<void>([
          hold.release.future,
          Future<void>.delayed(const Duration(milliseconds: 250)),
        ]);
        if (hold.release.isCompleted) break;
        socket.write(': ping\n\n');
        await socket.flush();
      }
      if (hold.open) {
        socket.write('data: {"seq":2}\n\n');
        await socket.flush();
      }
    } catch (_) {
      // Client disconnected.
    } finally {
      hold.open = false;
      socket.destroy();
    }
  }

  Future<void> _v2Event(HttpRequest request) async {
    eventConnections += 1;
    eventOpen = true;
    eventHadAuthorization = request.headers.value('authorization') != null;
    final response = request.response;
    _sseHeaders(response);
    try {
      response.write(
        'data: {"id":"evt_$eventConnections","type":"server.connected",'
        '"data":{}}\n\n',
      );
      await response.flush();
      await Future<void>.delayed(const Duration(milliseconds: 300));
      await response.close();
    } catch (_) {
      // Client disconnected first.
    } finally {
      eventOpen = false;
    }
  }

  Future<void> _orchestration(HttpRequest request) async {
    orchestrationConnections += 1;
    orchestrationHadAuthorization =
        request.headers.value('authorization') != null;
    final last = request.headers.value('last-event-id');
    if (last != null) lastEventIds.add(last);
    final start = (int.tryParse(last ?? '') ?? 0) + 1;
    final response = request.response;
    _sseHeaders(response);
    try {
      for (var seq = start; seq < start + 3; seq++) {
        response.write(
          'id: $seq\nevent: event\ndata: {"seq":$seq,"type":"test.tick"}\n\n',
        );
      }
      response.write('event: heartbeat\ndata: {"timestamp":"now"}\n\n');
      await response.flush();
      await Future<void>.delayed(const Duration(milliseconds: 200));
      await response.close();
    } catch (_) {
      // Client disconnected first.
    }
  }

  Future<void> _echoSocket(HttpRequest request) async {
    final socket = await WebSocketTransformer.upgrade(request);
    webSocketConnections += 1;
    socket.add([0, ...utf8.encode('{"cursor":7}')]);
    socket.listen(
      (dynamic message) {
        if (message == 'close') {
          socket.close(4000, 'bye');
          return;
        }
        socket.add(message);
      },
      onError: (Object _) {},
      cancelOnError: true,
    );
  }
}

Future<void> main(List<String> args) async {
  final port = args.isEmpty ? 0 : int.tryParse(args.first) ?? 0;
  final server = await StreamingTestServer.start(port: port);
  stdout.writeln('OC_STREAM_TEST_SERVER=${server.baseUri}');
  stdout.writeln('Press Ctrl-C to stop.');
  await ProcessSignal.sigint.watch().first;
  await server.close();
}
