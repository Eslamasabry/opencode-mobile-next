import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/client.dart';
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/api2/transport.dart';

class _RealHttpOverrides extends HttpOverrides {}

class Recorded {
  final String method;
  final Uri uri;
  final dynamic body;
  Recorded(this.method, this.uri, this.body);
}

Future<void> withServer(
  Future<void> Function(HttpServer server, List<Recorded> requests) body, {
  required Future<void> Function(HttpRequest request) handler,
}) async {
  await HttpOverrides.runZoned(() async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    final requests = <Recorded>[];
    server.listen((request) async {
      final text = await utf8.decoder.bind(request).join();
      requests.add(
        Recorded(
          request.method,
          request.uri,
          text.isEmpty ? null : jsonDecode(text),
        ),
      );
      await handler(request);
    });
    try {
      await body(server, requests);
    } finally {
      await server.close(force: true);
    }
  }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
}

Api2Client clientFor(HttpServer server, {String? directory}) =>
    Api2Client.connect(
      baseUrl: 'http://${server.address.host}:${server.port}',
      password: 'pw',
      directory: directory,
    );

Future<void> writeJson(HttpRequest request, Object? payload,
    {int status = 200}) async {
  request.response.statusCode = status;
  request.response.headers.contentType = ContentType.json;
  if (payload != null) request.response.write(jsonEncode(payload));
  await request.response.close();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('walks session pages by passing cursors back verbatim', () async {
    const cursor = 'eyJhbmNob3IiOnsiZGlyZWN0aW9uIjoibmV4dCJ9fQ';
    await withServer(handler: (request) async {
      if (request.uri.queryParameters['cursor'] == cursor) {
        await writeJson(request, {
          'data': [
            {'id': 'ses_page2', 'title': 'second'},
          ],
          'cursor': {'previous': 'prev-token'},
        });
        return;
      }
      await writeJson(request, {
        'data': [
          {'id': 'ses_page1', 'title': 'first'},
        ],
        'cursor': {'next': cursor},
      });
    }, (server, requests) async {
      final client = clientFor(server, directory: '/work/app');
      try {
        final first = await client.sessions(limit: 1, rootsOnly: true);
        expect(first.data.single.id, 'ses_page1');
        expect(first.hasNext, isTrue);

        final second = await client.sessions(cursor: first.nextCursor);
        expect(second.data.single.id, 'ses_page2');
        expect(second.hasNext, isFalse);
        expect(second.hasPrevious, isTrue);

        expect(requests[0].uri.path, '/api/session');
        expect(requests[0].uri.queryParameters['directory'], '/work/app');
        expect(requests[0].uri.queryParameters['limit'], '1');
        expect(requests[0].uri.queryParameters['parentID'], 'null');
        expect(requests[1].uri.queryParameters, {'cursor': cursor},
            reason: 'a cursor must be passed back alone');
        client.setLocation(directory: '/work/app', workspace: 'ws_mobile');
        await client.sessions(unscoped: true, search: 'find me');
        expect(requests.last.uri.queryParameters, {'search': 'find me'});
        await client.sessions();
        expect(requests.last.uri.queryParameters['directory'], '/work/app');
        expect(requests.last.uri.queryParameters['workspace'], 'ws_mobile');
      } finally {
        client.close();
      }
    });
  });

  test('walks message pages and parses the union', () async {
    await withServer(handler: (request) async {
      if (request.uri.queryParameters.containsKey('cursor')) {
        await writeJson(request, {
          'data': [
            {'id': 'msg_2', 'type': 'assistant', 'content': []},
          ],
          'cursor': {},
        });
        return;
      }
      await writeJson(request, {
        'data': [
          {'id': 'msg_1', 'type': 'user', 'text': 'hello'},
        ],
        'cursor': {'next': 'tok'},
      });
    }, (server, requests) async {
      final client = clientFor(server);
      try {
        final page1 = await client.messages('ses_1', limit: 1, order: 'asc');
        expect(page1.data.single, isA<Api2UserMessage>());
        final page2 = await client.messages('ses_1', cursor: page1.nextCursor);
        expect(page2.data.single, isA<Api2AssistantMessage>());
        expect(page2.hasNext, isFalse);
        expect(requests[0].uri.path, '/api/session/ses_1/message');
        expect(requests[0].uri.queryParameters['order'], 'asc');
        expect(requests[1].uri.queryParameters, {'cursor': 'tok'});
      } finally {
        client.close();
      }
    });
  });

  test('prompt sends v2 wire shape and returns the inbox receipt', () async {
    await withServer(handler: (request) async {
      await writeJson(request, {
        'data': {
          'id': 'msg_receipt',
          'sessionID': 'ses_1',
          'timeCreated': 1787961231753,
          'type': 'user',
          'payload': {'text': 'look at @notes'},
          'delivery': 'queue',
        },
      });
    }, (server, requests) async {
      final client = clientFor(server);
      try {
        final receipt = await client.prompt(
          'ses_1',
          text: 'look at @notes',
          files: [
            Api2Client.inlineAttachment(
              utf8.encode('notes'),
              mime: 'text/plain',
              name: 'notes.txt',
            ),
            Api2Client.serverFileAttachment(
              '/work/app/README.md',
              startLine: 1,
              endLine: 5,
            ),
          ],
          agents: const [
            Api2PromptAgentMention(
              name: 'explore',
              mention: Api2Mention(start: 8, end: 14, text: '@notes'),
            ),
          ],
          delivery: Api2Delivery.queue,
          resume: true,
        );
        expect(receipt.id, 'msg_receipt');
        expect(receipt.delivery, Api2Delivery.queue);
        expect(receipt.promptText, 'look at @notes');

        final request = requests.single;
        expect(request.method, 'POST');
        expect(request.uri.path, '/api/session/ses_1/prompt');
        final body = request.body as Map;
        expect(body['text'], 'look at @notes');
        expect(body['delivery'], 'queue');
        expect(body['resume'], true);
        expect(body.containsKey('model'), isFalse,
            reason: 'v2 prompts carry no model/agent selection');
        final files = body['files'] as List;
        expect(files[0]['uri'], 'data:text/plain;base64,${base64Encode(utf8.encode('notes'))}');
        expect(files[0]['name'], 'notes.txt');
        expect(files[1]['uri'], 'file:///work/app/README.md?start=1&end=5');
        final agents = body['agents'] as List;
        expect(agents[0]['name'], 'explore');
        expect(agents[0]['mention']['text'], '@notes');
      } finally {
        client.close();
      }
    });
  });

  test('session mutations hit the v2 endpoints with 204 responses', () async {
    await withServer(handler: (request) async {
      if (request.uri.path.endsWith('/interrupt')) {
        await writeJson(request, {'interrupted': true});
        return;
      }
      if (request.method == 'POST' && request.uri.path == '/api/session') {
        await writeJson(request, {
          'data': {'id': 'ses_new', 'title': 'made'},
        });
        return;
      }
      request.response.statusCode = HttpStatus.noContent;
      await request.response.close();
    }, (server, requests) async {
      final client = clientFor(server, directory: '/work/app');
      try {
        final created = await client.createSession(
          title: 'made',
          agent: 'build',
          model: const Api2ModelRef(id: 'glm-5.2', providerID: 'zai'),
        );
        expect(created.id, 'ses_new');
        await client.renameSession('ses_new', 'renamed');
        await client.switchAgent('ses_new', 'plan');
        await client.switchModel(
          'ses_new',
          const Api2ModelRef(id: 'glm-5.2', providerID: 'zai', variant: 'high'),
        );
        expect(await client.interrupt('ses_new'), isTrue);
        await client.wait('ses_new');
        await client.deleteSession('ses_new');

        expect(requests[0].body['location'], {'directory': '/work/app'});
        expect(requests[0].body['model'], {'id': 'glm-5.2', 'providerID': 'zai'});
        expect(requests[1].uri.path, '/api/session/ses_new/rename');
        expect(requests[1].body, {'title': 'renamed'});
        expect(requests[2].body, {'agent': 'plan'});
        expect(requests[3].body, {
          'model': {'id': 'glm-5.2', 'providerID': 'zai', 'variant': 'high'},
        });
        expect(requests[4].uri.path, '/api/session/ses_new/interrupt');
        expect(requests[5].uri.path, '/api/session/ses_new/wait');
        expect(requests[6].method, 'DELETE');
      } finally {
        client.close();
      }
    });
  });

  test('location-scoped reads carry the deep-object location params', () async {
    await withServer(handler: (request) async {
      await writeJson(request, {'data': []});
    }, (server, requests) async {
      final client = clientFor(server, directory: '/work/acme app');
      try {
        await client.models();
        await client.agents();
        await client.pendingPermissions();
        await client.pendingForms();
        await client.fsList('lib');
        for (final recorded in requests) {
          expect(
            recorded.uri.queryParameters['location[directory]'],
            '/work/acme app',
            reason: recorded.uri.path,
          );
        }
        expect(requests[2].uri.path, '/api/permission/request');
        expect(requests[3].uri.path, '/api/form/request');
        expect(requests[4].uri.queryParameters['path'], 'lib');
      } finally {
        client.close();
      }
    });
  });

  test('permission and form replies post the documented bodies', () async {
    await withServer(handler: (request) async {
      if (request.uri.path.endsWith('/state')) {
        await writeJson(request, {
          'data': {'status': 'pending'},
        });
        return;
      }
      request.response.statusCode = HttpStatus.noContent;
      await request.response.close();
    }, (server, requests) async {
      final client = clientFor(server);
      try {
        await client.replyPermission(
          'ses_1',
          'per_1',
          Api2PermissionReply.reject,
          message: 'not on main',
        );
        final state = await client.formState('ses_1', 'frm_1');
        expect(state.status, Api2FormStatus.pending);
        await client.replyForm('ses_1', 'frm_1', {'env': 'prod', 'ok': true});
        await client.cancelForm('ses_1', 'frm_1');

        expect(
          requests[0].uri.path,
          '/api/session/ses_1/permission/per_1/reply',
        );
        expect(requests[0].body, {'reply': 'reject', 'message': 'not on main'});
        expect(requests[2].body, {
          'answer': {'env': 'prod', 'ok': true},
        });
        expect(requests[3].uri.path, '/api/session/ses_1/form/frm_1/cancel');
      } finally {
        client.close();
      }
    });
  });

  test('fs read returns raw bytes without a JSON envelope', () async {
    await withServer(handler: (request) async {
      request.response.headers.contentType = ContentType.text;
      request.response.write('# raw bytes, not JSON');
      await request.response.close();
    }, (server, requests) async {
      final client = clientFor(server, directory: '/work/app');
      try {
        final text = await client.fsRead('docs/read me.md');
        expect(text, '# raw bytes, not JSON');
        expect(requests.single.uri.path, '/api/fs/read/docs/read%20me.md');
      } finally {
        client.close();
      }
    });
  });

  test('active sessions map to their status types', () async {
    await withServer(handler: (request) async {
      await writeJson(request, {
        'data': {
          'ses_a': {'type': 'running'},
          'ses_b': {'type': 'queued'},
        },
      });
    }, (server, requests) async {
      final client = clientFor(server);
      try {
        expect(await client.activeSessions(), {
          'ses_a': 'running',
          'ses_b': 'queued',
        });
      } finally {
        client.close();
      }
    });
  });

  test('surfaces typed errors from tagged envelopes', () async {
    await withServer(handler: (request) async {
      await writeJson(
        request,
        {
          '_tag': 'SessionBusyError',
          'sessionID': 'ses_1',
          'message': 'Session is busy',
        },
        status: HttpStatus.conflict,
      );
    }, (server, requests) async {
      final client = clientFor(server);
      try {
        await client.renameSession('ses_1', 'nope');
        fail('expected Api2RequestError');
      } on Api2RequestError catch (e) {
        expect(e.isConflict, isTrue);
        expect(e.tag, 'SessionBusyError');
        expect(e.detail('sessionID'), 'ses_1');
      } finally {
        client.close();
      }
    });
  });
}
