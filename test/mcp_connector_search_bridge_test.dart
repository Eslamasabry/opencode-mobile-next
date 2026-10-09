import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/mcp_connector_search_bridge.dart';

void main() {
  late McpConnectorSearchBridge bridge;
  late HttpClient client;
  late bool current;
  late Future<Map<String, Object?>> Function(Map<String, dynamic>) search;

  setUp(() async {
    current = true;
    search = (query) async => {
      'results': [query['query']],
    };
    bridge = await McpConnectorSearchBridge.start(
      search: (query) => search(query),
      isCurrent: () => current,
    );
    client = HttpClient()..connectionTimeout = const Duration(seconds: 2);
  });

  tearDown(() async {
    client.close(force: true);
    await bridge.close();
  });

  Future<(int, Map<String, dynamic>)> request({
    String method = 'POST',
    String? path,
    String? token,
    bool authorize = true,
    String body = '{"query":"design"}',
  }) async {
    final outgoing = await client.openUrl(
      method,
      path == null ? bridge.endpoint : bridge.endpoint.replace(path: path),
    );
    if (authorize) {
      outgoing.headers.set(
        HttpHeaders.authorizationHeader,
        'Bearer ${token ?? bridge.bearer}',
      );
    }
    outgoing.headers.contentType = ContentType.json;
    if (method == 'POST') outgoing.write(body);
    final incoming = await outgoing.close();
    final text = await utf8.decoder.bind(incoming).join();
    return (incoming.statusCode, jsonDecode(text) as Map<String, dynamic>);
  }

  test(
    'binds loopback with an ephemeral port and independent bearer',
    () async {
      expect(bridge.endpoint.scheme, 'http');
      expect(bridge.endpoint.host, '127.0.0.1');
      expect(bridge.endpoint.port, greaterThan(0));
      expect(bridge.endpoint.path, '/find-connectors');
      expect(bridge.endpoint.userInfo, isEmpty);
      expect(bridge.endpoint.query, isEmpty);
      expect(bridge.bearer, matches(RegExp(r'^[a-f0-9]{64}$')));
      expect(bridge.toString(), isNot(contains(bridge.bearer)));
      final other = await McpConnectorSearchBridge.start(
        search: (_) async => {},
        isCurrent: () => true,
      );
      try {
        expect(other.bearer, isNot(bridge.bearer));
      } finally {
        await other.close();
      }
    },
  );

  test(
    'returns only the supplied search response for an authorized map',
    () async {
      final response = await request();
      expect(response.$1, 200);
      expect(response.$2, {
        'results': ['design'],
      });
    },
  );

  test('rejects missing or incorrect bearer without calling search', () async {
    var calls = 0;
    search = (_) async {
      calls++;
      return {};
    };
    expect((await request(authorize: false)).$1, 401);
    expect((await request(token: 'wrong')).$1, 401);
    expect(calls, 0);
  });

  test('accepts only POST on the fixed path', () async {
    expect((await request(method: 'GET')).$1, 405);
    expect((await request(path: '/different')).$1, 404);
  });

  test('rejects malformed JSON and non-map JSON', () async {
    expect((await request(body: '{')).$1, 400);
    expect((await request(body: '[]')).$1, 400);
    expect((await request(body: 'null')).$1, 400);
  });

  test('enforces request limit in UTF8 bytes', () async {
    expect((await request(body: jsonEncode({'query': 'é' * 1024}))).$1, 413);
    expect((await request(body: jsonEncode({'query': 'x' * 2048}))).$1, 413);
  });

  test('rejects a stale source before invoking search', () async {
    var calls = 0;
    search = (_) async {
      calls++;
      return {};
    };
    current = false;
    expect((await request()).$1, 403);
    expect(calls, 0);
  });

  test('rejects a source that changes during asynchronous search', () async {
    final entered = Completer<void>();
    final finish = Completer<Map<String, Object?>>();
    search = (_) {
      entered.complete();
      return finish.future;
    };
    final pending = request();
    await entered.future;
    current = false;
    finish.complete({'private': 'old source result'});
    final response = await pending;
    expect(response.$1, 403);
    expect(jsonEncode(response.$2), isNot(contains('old source result')));
  });

  test('limits concurrent search calls to four', () async {
    var calls = 0;
    final entered = Completer<void>();
    final finish = Completer<Map<String, Object?>>();
    search = (_) {
      if (++calls == 4) entered.complete();
      return finish.future;
    };
    final pending = [for (var i = 0; i < 4; i++) request()];
    await entered.future;
    expect((await request()).$1, 429);
    expect(calls, 4);
    finish.complete({'results': []});
    expect((await Future.wait(pending)).every((r) => r.$1 == 200), isTrue);
    expect((await request()).$1, 200);
  });

  test('returns a generic error without leaking search failures', () async {
    search = (_) async => throw StateError('secret-token-and-host-details');
    final response = await request();
    expect(response.$1, 500);
    expect(jsonEncode(response.$2), isNot(contains('secret-token')));
  });

  test('rejects a response exceeding 16384 UTF8 bytes', () async {
    search = (_) async => {'results': 'é' * 8192};
    final response = await request();
    expect(response.$1, 502);
    expect(
      utf8.encode(jsonEncode(response.$2)).length,
      lessThanOrEqualTo(16384),
    );
  });

  test(
    'times out search after five seconds without releasing its slot',
    () async {
      var calls = 0;
      final finish = Completer<Map<String, Object?>>();
      search = (_) {
        calls++;
        return finish.future;
      };
      final pending = [for (var i = 0; i < 4; i++) request()];
      final responses = await Future.wait(pending);
      expect(responses.every((r) => r.$1 == 504), isTrue);
      expect((await request()).$1, 429);
      expect(calls, 4);
      finish.complete({'results': []});
    },
  );

  test('close is idempotent and stops accepting connections', () async {
    await bridge.close();
    await bridge.close();
    await expectLater(request(), throwsA(isA<SocketException>()));
  });
}
