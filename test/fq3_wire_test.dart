import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../tool/qa/fq3/common.dart';

void main() {
  test(
    'credentials only reach Authorization; stable OC2 maps actual routes',
    () async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final paths = <String>[];
      final bodies = <Map<String, dynamic>>[];
      final wire = Fq3Wire(
        baseUrl: 'http://127.0.0.1:${server.port}',
        password: 'fixture-secret',
      );
      addTearDown(() async {
        await wire.close();
        await server.close(force: true);
      });
      server.listen((request) async {
        paths.add(request.uri.path);
        expect(request.uri.query, isNot(contains('fixture-secret')));
        expect(
          request.headers.value('authorization'),
          'Basic ${base64Encode(utf8.encode('opencode:fixture-secret'))}',
        );
        final body = await utf8.decoder.bind(request).join();
        if (body.isNotEmpty) {
          bodies.add(jsonDecode(body) as Map<String, dynamic>);
        }
        request.response.headers.contentType = ContentType.json;
        if (request.uri.path == '/api/health') {
          request.response.statusCode = 404;
          request.response.write('{"error":"fixture-secret"}');
        } else if (request.uri.path == '/api/info') {
          request.response.write('{"version":"2.0.10","pid":123}');
        } else {
          request.response.write('{}');
        }
        await request.response.close();
      });
      expect((await wire.request('GET', '/api/health'))['healthy'], isTrue);
      expect(wire.isStableOc2, isTrue);
      await wire.request(
        'POST',
        '/api/session/ses_owned/permission/per_owned/reply',
        body: {'reply': 'once'},
      );
      expect(paths, [
        '/api/health',
        '/api/info',
        '/api/session/ses_owned/permission/per_owned/reply',
      ]);
      expect(bodies.single, {'decision': 'once'});
    },
  );

  test(
    'failed HTTP bodies never enter thrown errors or result evidence',
    () async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final wire = Fq3Wire(
        baseUrl: 'http://127.0.0.1:${server.port}',
        password: 'fixture-secret',
      );
      addTearDown(() async {
        await wire.close();
        await server.close(force: true);
      });
      server.listen((request) async {
        request.response.statusCode = 401;
        request.response.write('fixture-secret provider-api-key');
        await request.response.close();
      });
      final run = ProbeRun(
        wire,
        const ProbeOptions(directory: '/fixture', title: 'fq3-fixture'),
      );
      await run.check('stream', () async {
        await wire.request('GET', '/event');
        return {};
      });
      expect(run.results['stream']?['code'], 'http_401');
      expect(jsonEncode(run.results), isNot(contains('fixture-secret')));
      expect(jsonEncode(run.results), isNot(contains('provider-api-key')));
    },
  );

  test(
    'external URLs and credential-bearing URLs are refused before requests',
    () {
      for (final url in [
        'http://example.com',
        'https://127.0.0.1',
        'http://user:secret@127.0.0.1',
        'http://127.0.0.1?secret=yes',
      ]) {
        expect(
          () => Fq3Wire(baseUrl: url, password: 'secret'),
          throwsA(isA<ProbeFailure>()),
        );
      }
    },
  );

  test('redirects never send auth to another endpoint', () async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    final other = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    var leaked = false;
    other.listen((request) {
      leaked = true;
      request.response.close();
    });
    final wire = Fq3Wire(
      baseUrl: 'http://127.0.0.1:${server.port}',
      password: 'fixture-secret',
    );
    addTearDown(() async {
      await wire.close();
      await server.close(force: true);
      await other.close(force: true);
    });
    server.listen((request) async {
      request.response.statusCode = 302;
      request.response.headers.set(
        'location',
        'http://127.0.0.1:${other.port}/secret',
      );
      await request.response.close();
    });
    await expectLater(
      wire.request('GET', '/global/health'),
      throwsA(isA<ProbeFailure>()),
    );
    expect(leaked, isFalse);
  });

  test(
    'stable detection requires shaped info and never follows unauthorized health',
    () async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final paths = <String>[];
      final wire = Fq3Wire(
        baseUrl: 'http://127.0.0.1:${server.port}',
        password: 'fixture-secret',
      );
      addTearDown(() async {
        await wire.close();
        await server.close(force: true);
      });
      server.listen((request) async {
        paths.add(request.uri.path);
        request.response.statusCode = 401;
        await request.response.close();
      });
      await expectLater(
        wire.request('GET', '/api/health'),
        throwsA(isA<ProbeFailure>()),
      );
      expect(paths, ['/api/health']);
      expect(wire.isStableOc2, isFalse);
    },
  );

  test(
    'SSE reconnect has no volatile replay cursor and filters bounded payloads',
    () async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final replayHeaders = <String?>[];
      final wire = Fq3Wire(
        baseUrl: 'http://127.0.0.1:${server.port}',
        password: 'fixture-secret',
      );
      addTearDown(() async {
        await wire.close();
        await server.close(force: true);
      });
      server.listen((request) async {
        replayHeaders.add(request.headers.value('last-event-id'));
        expect(request.uri.queryParameters.containsKey('after'), isFalse);
        request.response.headers.contentType = ContentType(
          'text',
          'event-stream',
        );
        request.response.bufferOutput = false;
        request.response.write(
          'data: {"type":"server.connected","data":{}}\n\n',
        );
        await request.response.flush();
      });
      await wire.openEvents('/api/event');
      await wire.waitFor(
        (event) => event['type'] == 'server.connected',
        timeout: const Duration(seconds: 2),
      );
      await wire.closeEvents();
      wire.reconnectHttp();
      await wire.openEvents('/api/event');
      expect(replayHeaders, [null, null]);
    },
  );
}
