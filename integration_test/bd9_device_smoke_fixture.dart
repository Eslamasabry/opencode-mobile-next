import 'dart:async';
import 'dart:convert';
import 'dart:io';

/// A loopback-only, read-only OpenCode fixture. No model or provider credentials.
class Bd9DeviceSmokeFixture {
  Bd9DeviceSmokeFixture._(this._server);

  final HttpServer _server;
  final reads = <String>{};
  final refusedWrites = <String>{};
  final unknownReads = <String>{};

  static const title = 'BD9 release conversation';
  static const sessionID = 'ses_bd9_smoke';
  static const directory = '/project/bd9';

  String get baseUrl => 'http://127.0.0.1:${_server.port}';

  static Future<Bd9DeviceSmokeFixture> start() async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    final fixture = Bd9DeviceSmokeFixture._(server);
    server.listen(fixture._serve);
    return fixture;
  }

  Future<void> close() async => _server.close(force: true);

  static Map<String, Object?> get _session => {
    'id': sessionID,
    'slug': 'bd9-release-smoke',
    'projectID': 'bd9-project',
    'directory': directory,
    'title': title,
    'version': '1.0.0',
    'time': {'created': 1791324000000, 'updated': 1791324000000},
  };

  Future<void> _serve(HttpRequest request) async {
    final path = request.uri.path;
    if (request.method != 'GET') {
      refusedWrites.add(path);
      request.response.statusCode = HttpStatus.methodNotAllowed;
      request.response.write('{"error":"read_only_fixture"}');
      await request.response.close();
      return;
    }
    reads.add(path);
    if (path == '/event' || path == '/global/event') {
      request.response.headers.contentType = ContentType(
        'text',
        'event-stream',
      );
      request.response.headers.set(HttpHeaders.cacheControlHeader, 'no-cache');
      final event = {
        'type': 'server.connected',
        'properties': <String, Object>{},
      };
      final data = path == '/global/event'
          ? {'directory': directory, 'payload': event}
          : event;
      request.response.write('data: ${jsonEncode(data)}\n\n');
      await request.response.flush();
      // Deliberately keep the real SSE connection open until close(force:true).
      return;
    }

    final Object? data = switch (path) {
      '/global/health' => {'healthy': true, 'version': '1.0.0'},
      '/session' => [_session],
      '/experimental/session' => [
        {
          ..._session,
          'project': {
            'id': 'bd9-project',
            'name': 'BD9 project',
            'worktree': directory,
          },
        },
      ],
      '/session/status' => {
        sessionID: {'type': 'idle'},
      },
      '/project' => [
        {
          'id': 'bd9-project',
          'worktree': directory,
          'name': 'BD9 project',
          'sandboxes': <String>[],
          'time': {'created': 1791324000000, 'updated': 1791324000000},
        },
      ],
      '/path' => {
        'home': '/home/bd9',
        'state': '/tmp/bd9',
        'config': '/tmp/bd9',
        'worktree': directory,
        'directory': directory,
      },
      '/provider' ||
      '/config/providers' => {'all': [], 'default': {}, 'connected': []},
      '/api/provider' ||
      '/api/model' ||
      '/api/agent' ||
      '/api/integration' => {'data': []},
      '/agent' || '/permission' || '/question' || '/command' => <Object>[],
      '/provider/auth' || '/config' || '/mcp' => <String, Object>{},
      '/experimental/capabilities' => {'backgroundSubagents': false},
      '/vcs' => {'branch': 'bd9'},
      _ => null,
    };
    request.response.headers.contentType = ContentType.json;
    if (data == null) {
      unknownReads.add(path);
      request.response.statusCode = HttpStatus.notFound;
      request.response.write('{"error":"fixture_route_missing"}');
    } else {
      request.response.write(jsonEncode(data));
    }
    await request.response.close();
  }
}
