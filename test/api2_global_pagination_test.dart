import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/client.dart';
import 'package:opencode_mobile/api2/gateway_operations.dart';

void main() {
  test(
    'global finder requests newest first and preserves cursor after filtering',
    () async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      addTearDown(() => server.close(force: true));
      final requests = <Uri>[];
      server.listen((request) async {
        requests.add(request.uri);
        request.response.headers.contentType = ContentType.json;
        final next = request.uri.queryParameters['cursor'];
        request.response.write(
          jsonEncode({
            'data': next == null
                ? [
                    {
                      'id': 'archived',
                      'title': 'Old',
                      'time': {'archived': 123},
                    },
                  ]
                : [
                    {'id': 'older', 'title': 'Older result'},
                  ],
            'cursor': next == null ? {'next': 'opaque/next+token='} : {},
          }),
        );
        await request.response.close();
      });
      final client = Api2Client.connect(
        baseUrl: 'http://${server.address.host}:${server.port}',
        password: 'fixture',
        directory: '/selected/workspace',
        workspace: 'selected',
      );
      addTearDown(client.close);
      final repository = Api2OperationsGateway(client: client);
      final first = await repository.listGlobalSessions(
        search: ' work ',
        limit: 1,
      );
      expect(first.items, isEmpty);
      expect(first.nextCursor, 'opaque/next+token=');
      final second = await repository.listGlobalSessions(
        search: 'work',
        cursor: first.nextCursor,
        limit: 1,
      );
      expect(second.items.single.session.id, 'older');
      expect(second.hasMore, isFalse);
      expect(requests.first.queryParameters, {
        'order': 'desc',
        'search': 'work',
        'parentID': 'null',
        'limit': '1',
      });
      expect(requests.last.queryParameters, {'cursor': 'opaque/next+token='});
    },
  );
}
