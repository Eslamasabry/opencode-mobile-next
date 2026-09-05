import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api2/client.dart';
import 'package:opencode_mobile/api2/gateway.dart';

void main() {
  for (final linkOnly in [false, true]) {
    test('v1 message pages retain opaque continuation ($linkOnly)', () async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      addTearDown(() => server.close(force: true));
      final requests = <Uri>[];
      const token = 'opaque/older+token=';
      server.listen((request) async {
        requests.add(request.uri);
        final older = request.uri.queryParameters['before'] != null;
        request.response.headers.contentType = ContentType.json;
        if (!older) {
          if (linkOnly) {
            request.response.headers.set(
              'Link',
              '<https://ignored.example/history?before=${Uri.encodeQueryComponent(token)}>; rel="next"',
            );
          } else {
            request.response.headers.set('X-Next-Cursor', token);
          }
        }
        request.response.write(
          jsonEncode([
            for (final id in older ? ['oldest', 'older'] : ['recent', 'newest'])
              {
                'info': {
                  'id': id,
                  'sessionID': 'session-1',
                  'role': 'user',
                  'time': {'created': 1},
                },
                'parts': [
                  {'id': 'p-$id', 'type': 'text', 'text': id},
                ],
              },
          ]),
        );
        await request.response.close();
      });
      final api = OpenCodeApi(
        baseUrl: 'http://${server.address.host}:${server.port}',
      )..setLocation(directory: '/work', workspace: 'workspace');
      addTearDown(api.close);
      final first = await api.messagePage('session-1', limit: 2);
      expect(first.items.map((message) => message.info.id), [
        'recent',
        'newest',
      ]);
      expect(first.nextCursor, token);
      final second = await api.messagePage(
        'session-1',
        limit: 2,
        cursor: first.nextCursor,
      );
      expect(second.items.map((message) => message.info.id), [
        'oldest',
        'older',
      ]);
      expect(second.hasMore, isFalse);
      expect(requests.first.queryParameters, {
        'directory': '/work',
        'workspace': 'workspace',
        'limit': '2',
      });
      expect(requests.last.queryParameters, {
        'directory': '/work',
        'workspace': 'workspace',
        'limit': '2',
        'before': token,
      });
      expect(requests, hasLength(2));
    });
  }

  test('v2 opens beyond old history cap with one newest-first page', () async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    addTearDown(() => server.close(force: true));
    final requests = <Uri>[];
    server.listen((request) async {
      requests.add(request.uri);
      final older = request.uri.queryParameters['cursor'] != null;
      request.response.headers.contentType = ContentType.json;
      request.response.write(
        jsonEncode({
          'data': [
            for (final id
                in older
                    ? ['message-4999', 'message-4998']
                    : ['message-5001', 'message-5000'])
              {'id': id, 'type': 'user', 'text': id},
          ],
          'cursor': older ? {} : {'next': 'next+opaque/token='},
        }),
      );
      await request.response.close();
    });
    final client = Api2Client.connect(
      baseUrl: 'http://${server.address.host}:${server.port}',
      password: 'fixture',
      directory: '/work',
    );
    addTearDown(client.close);
    final api = Api2Gateway(client: client);
    final first = await api.messagePage('session-1', limit: 2);
    expect(requests, hasLength(1));
    expect(first.items.map((message) => message.info.id), [
      'message-5000',
      'message-5001',
    ]);
    final second = await api.messagePage(
      'session-1',
      cursor: first.nextCursor,
      limit: 2,
    );
    expect(second.items.map((message) => message.info.id), [
      'message-4998',
      'message-4999',
    ]);
    expect(second.hasMore, isFalse);
    expect(requests.first.queryParameters, {'limit': '2', 'order': 'desc'});
    expect(requests.last.queryParameters, {'cursor': 'next+opaque/token='});
  });
}
