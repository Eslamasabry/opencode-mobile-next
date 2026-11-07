// Issue #124: what the three lists of the Providers and MCP page get from
// an OpenCode 2 server, and how the app sorts a list that did not load.
// The bodies are the exact answers of OpenCode 2.1.1 and 2.2.6 (2026-11-07,
// `opencode serve` with no MCP, a failing MCP, a pending MCP, a working MCP
// with resources), with the scratch folder paths shortened.
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/client.dart';
import 'package:opencode_mobile/api2/gateway_operations.dart';
import 'package:opencode_mobile/api2/transport.dart';
import 'package:opencode_mobile/domain/list_load_problem.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';

typedef _Answer = ({int status, String body});

Future<void> _withServer(
  Map<String, _Answer> answers,
  Future<void> Function(Api2OperationsGateway gateway) body,
) async {
  final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
  server.listen((request) async {
    final answer =
        answers[request.uri.path] ?? (status: 404, body: ''); // no route
    request.response.statusCode = answer.status;
    if (answer.body.isNotEmpty) {
      request.response.headers.contentType = ContentType.json;
      request.response.write(answer.body);
    }
    await request.response.close();
  });
  final gateway = Api2OperationsGateway(
    client: Api2Client.connect(
      baseUrl: 'http://${server.address.host}:${server.port}',
      password: 'fixture',
      directory: '/work/project',
    ),
  );
  try {
    await body(gateway);
  } finally {
    gateway.client.close();
    await server.close(force: true);
  }
}

const _location = '{"directory":"/work/project"}';

Future<Object> _failureOf(Future<Object?> Function() call) async {
  try {
    await call();
  } catch (error) {
    return error;
  }
  fail('expected the call to fail');
}

void main() {
  group('what OpenCode 2 answers', () {
    test('no MCP: every list is empty, none is a failure', () async {
      await _withServer(
        {
          '/api/mcp': (status: 200, body: '{"location":$_location,"data":[]}'),
          '/api/mcp/resource': (
            status: 200,
            body:
                '{"location":$_location,"data":{"resources":[],"templates":[]}}',
          ),
          '/api/integration': (
            status: 200,
            body:
                '{"location":$_location,"data":[{"id":"302ai","name":"302.AI",'
                '"methods":[{"type":"key"},{"type":"env","names":["302AI_API_KEY"]}],'
                '"connections":[]}]}',
          ),
        },
        (gateway) async {
          expect(await gateway.listMcpServers(), isEmpty);
          expect(await gateway.listMcpResources(), isEmpty);
          final integrations = await gateway.listIntegrations();
          expect(integrations.single.id, '302ai');
        },
      );
    });

    test('an MCP server that failed to start, one still starting, and a '
        'remote one that is refused are rows, not a failed list', () async {
      await _withServer(
        {
          '/api/mcp': (
            status: 200,
            body:
                '{"location":$_location,"data":['
                '{"name":"broken","status":{"status":"failed","error":'
                '"NotFound: ChildProcess.spawn (/nonexistent/mcp-server )"}},'
                '{"name":"mhanginit","status":{"status":"pending"}},'
                '{"name":"remote","status":{"status":"failed","error":'
                '"ECONNREFUSED: Unable to connect. Is the computer able to '
                'access the url?"},"integrationID":"mcp_935e03619075f6fb"}]}',
          ),
          '/api/mcp/resource': (
            status: 200,
            body:
                '{"location":$_location,"data":{"resources":[],"templates":[]}}',
          ),
        },
        (gateway) async {
          final servers = await gateway.listMcpServers();
          expect(
            [for (final s in servers) '${s.name}:${s.status}'],
            ['broken:failed', 'mhanginit:pending', 'remote:failed'],
          );
          expect(servers.first.error, contains('ChildProcess.spawn'));
          expect(await gateway.listMcpResources(), isEmpty);
        },
      );
    });

    test('a working MCP server lists its resources', () async {
      await _withServer(
        {
          '/api/mcp/resource': (
            status: 200,
            body:
                '{"location":$_location,"data":{"resources":[{"server":"withres",'
                '"name":"hello","uri":"note://hello","description":"A note",'
                '"mimeType":"text/plain"}],"templates":[]}}',
          ),
        },
        (gateway) async {
          final resource = (await gateway.listMcpResources()).single;
          expect(resource.server, 'withres');
          expect(resource.uri, 'note://hello');
          expect(resource.mimeType, 'text/plain');
        },
      );
    });
  });

  group('a list that did not load is sorted by what the person can do', () {
    test('a server without the route (a bare 404) does not offer it', () async {
      await _withServer({}, (gateway) async {
        for (final call in <Future<Object?> Function()>[
          gateway.listMcpResources,
          gateway.listMcpServers,
          gateway.listIntegrations,
        ]) {
          final error = await _failureOf(call);
          expect(error, isA<ProductException>());
          expect(listLoadProblemOf(error), ListLoadProblem.unsupported);
        }
      });
    });

    test('a project folder the server does not have', () async {
      const body =
          '{"_tag":"LocationNotFoundError","location":{"directory":'
          '"/nonexistent/dir"},"message":"Location not found: /nonexistent/dir"}';
      await _withServer(
        {
          '/api/mcp/resource': (status: 404, body: body),
          '/api/integration': (status: 404, body: body),
        },
        (gateway) async {
          expect(
            listLoadProblemOf(await _failureOf(gateway.listMcpResources)),
            ListLoadProblem.folderMissing,
          );
          expect(
            listLoadProblemOf(await _failureOf(gateway.listIntegrations)),
            ListLoadProblem.folderMissing,
          );
        },
      );
    });

    test('a refused password', () async {
      await _withServer(
        {
          '/api/mcp': (
            status: 401,
            body:
                '{"_tag":"UnauthorizedError","message":"Authentication required"}',
          ),
        },
        (gateway) async {
          expect(
            listLoadProblemOf(await _failureOf(gateway.listMcpServers)),
            ListLoadProblem.signIn,
          );
        },
      );
    });

    test('a server fault, a 405 and a 501', () async {
      await _withServer(
        {
          '/api/mcp': (status: 500, body: '{"message":"boom"}'),
          '/api/mcp/resource': (status: 501, body: ''),
          '/api/integration': (status: 405, body: ''),
        },
        (gateway) async {
          expect(
            listLoadProblemOf(await _failureOf(gateway.listMcpServers)),
            ListLoadProblem.failed,
          );
          expect(
            listLoadProblemOf(await _failureOf(gateway.listMcpResources)),
            ListLoadProblem.unsupported,
          );
          expect(
            listLoadProblemOf(await _failureOf(gateway.listIntegrations)),
            ListLoadProblem.unsupported,
          );
        },
      );
    });

    test('a server that cannot be reached', () async {
      final closed = await ServerSocket.bind(InternetAddress.loopbackIPv4, 0);
      final port = closed.port;
      await closed.close();
      final gateway = Api2OperationsGateway(
        client: Api2Client.connect(
          baseUrl: 'http://127.0.0.1:$port',
          password: 'fixture',
        ),
      );
      addTearDown(gateway.client.close);
      expect(
        listLoadProblemOf(await _failureOf(gateway.listIntegrations)),
        ListLoadProblem.unreachable,
      );
    });

    test('a timeout, and the transport types on their own', () {
      expect(
        listLoadProblemOf(
          const ProductException(
            'Could not load MCP resources',
            cause: Api2NetworkError('GET /mcp/resource failed', timedOut: true),
          ),
        ),
        ListLoadProblem.timedOut,
      );
      expect(
        listLoadProblemOf(
          const Api2RequestError(
            'GET /session/x failed (HTTP 404)',
            statusCode: 404,
            tag: 'SessionNotFoundError',
          ),
        ),
        ListLoadProblem.failed,
      );
      expect(
        listLoadProblemOf(UnsupportedError('no MCP here')),
        ListLoadProblem.unsupported,
      );
      expect(
        listLoadProblemOf(const ProductException('Could not load')),
        ListLoadProblem.failed,
      );
    });
  });
}
