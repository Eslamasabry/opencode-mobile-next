import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'support/product_repository_fixtures.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'saved permissions use the current project and exact generated routes',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        final requests = <({String method, Uri uri})>[];
        server.listen((request) async {
          requests.add((method: request.method, uri: request.uri));
          if (request.uri.path == '/project/current') {
            request.response.headers.contentType = ContentType.json;
            request.response.write(
              jsonEncode({
                'id': 'project-1',
                'worktree': '/work/acme',
                'vcs': 'git',
                'time': {'created': 1, 'updated': 2},
                'sandboxes': <String>[],
              }),
            );
          } else if (request.uri.path == '/api/permission/saved') {
            request.response.headers.contentType = ContentType.json;
            request.response.write(
              jsonEncode({
                'data': [
                  {
                    'id': 'grant/1',
                    'projectID': 'project-1',
                    'action': 'bash',
                    'resource': 'git status',
                  },
                  {
                    'id': 'wrong-project',
                    'projectID': 'project-2',
                    'action': 'edit',
                    'resource': '*',
                  },
                ],
              }),
            );
          } else if (request.method == 'DELETE') {
            request.response.statusCode = HttpStatus.noContent;
          }
          await request.response.close();
        });

        try {
          final api = OpenCodeApi(
            baseUrl: 'http://${server.address.host}:${server.port}',
          );
          final repository = SdkProductRepository(api.sdkClient)
            ..setLocation(directory: '/work/acme', workspace: 'workspace-1');

          final permissions = await repository.listSavedPermissions();
          await repository.removeSavedPermission('grant/1');

          expect(permissions, hasLength(1));
          expect(permissions.single.id, 'grant/1');
          expect(permissions.single.projectID, 'project-1');
          expect(permissions.single.action, 'bash');
          expect(permissions.single.resource, 'git status');
          expect(requests, hasLength(3));
          expect(requests[0].method, 'GET');
          expect(requests[0].uri.path, '/project/current');
          expect(requests[0].uri.queryParameters, {
            'directory': '/work/acme',
            'workspace': 'workspace-1',
          });
          expect(requests[1].method, 'GET');
          expect(requests[1].uri.path, '/api/permission/saved');
          expect(requests[1].uri.queryParameters, {'projectID': 'project-1'});
          expect(requests[2].method, 'DELETE');
          expect(requests[2].uri.path, '/api/permission/saved/grant%2F1');
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
    },
  );

  test('generated API failures become product-facing errors', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      server.listen((request) async {
        request.response.statusCode = HttpStatus.internalServerError;
        request.response.headers.contentType = ContentType.json;
        request.response.write(jsonEncode({'message': 'database unavailable'}));
        await request.response.close();
      });

      try {
        final api = OpenCodeApi(
          baseUrl: 'http://${server.address.host}:${server.port}',
        );
        final repository = SdkProductRepository(api.sdkClient);
        await expectLater(
          repository.listProjects(),
          throwsA(
            isA<ProductException>().having(
              (error) => error.message,
              'message',
              contains('Could not load projects'),
            ),
          ),
        );
      } finally {
        await server.close(force: true);
      }
    }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
  });

  test('project MCP setup persists one exact config patch', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final requests = <({String method, Uri uri, Object? body})>[];
      server.listen((request) async {
        final text = await utf8.decoder.bind(request).join();
        final body = text.isEmpty ? null : jsonDecode(text);
        requests.add((method: request.method, uri: request.uri, body: body));
        request.response.headers.contentType = ContentType.json;
        request.response.write(
          request.method == 'GET' ? jsonEncode({'mcp': {}}) : jsonEncode(body),
        );
        await request.response.close();
      });

      final api = OpenCodeApi(
        baseUrl: 'http://${server.address.host}:${server.port}',
      );
      final repository = SdkProductRepository(api.sdkClient)
        ..setLocation(directory: '/work/mobile', workspace: 'workspace-1');
      try {
        await repository.addMcpServer(
          const McpServerDraft(
            name: 'remote-docs',
            kind: McpServerKind.remote,
            url: 'https://mcp.example.com/rpc',
            headers: {'Authorization': 'Bearer test-token'},
            detectOAuth: false,
            timeoutMs: 12000,
          ),
          scope: McpConfigScope.project,
        );

        expect(requests.map((request) => request.method), ['GET', 'PATCH']);
        expect(requests.map((request) => request.uri.path), [
          '/config',
          '/config',
        ]);
        for (final request in requests) {
          expect(request.uri.queryParameters, {
            'directory': '/work/mobile',
            'workspace': 'workspace-1',
          });
        }
        expect(requests.last.body, {
          'mcp': {
            'remote-docs': {
              'type': 'remote',
              'url': 'https://mcp.example.com/rpc',
              'headers': {'Authorization': 'Bearer test-token'},
              'oauth': false,
              'timeout': 12000,
            },
          },
        });
      } finally {
        api.close();
        await server.close(force: true);
      }
    }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
  });

  test('global MCP setup persists local command configuration', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final requests = <({String method, Uri uri, Object? body})>[];
      server.listen((request) async {
        final text = await utf8.decoder.bind(request).join();
        final body = text.isEmpty ? null : jsonDecode(text);
        requests.add((method: request.method, uri: request.uri, body: body));
        request.response.headers.contentType = ContentType.json;
        request.response.write(
          request.method == 'GET' ? jsonEncode({'mcp': {}}) : jsonEncode(body),
        );
        await request.response.close();
      });

      final api = OpenCodeApi(
        baseUrl: 'http://${server.address.host}:${server.port}',
      );
      final repository = SdkProductRepository(api.sdkClient);
      try {
        await repository.addMcpServer(
          const McpServerDraft(
            name: 'local-tools',
            kind: McpServerKind.local,
            command: ['npx', '-y', '@example/mcp-server'],
            cwd: '/work/mobile',
            environment: {'LOG_LEVEL': 'warn'},
            timeoutMs: 9000,
          ),
          scope: McpConfigScope.global,
        );

        expect(requests.map((request) => request.method), ['GET', 'PATCH']);
        expect(requests.map((request) => request.uri.path), [
          '/global/config',
          '/global/config',
        ]);
        expect(requests.every((request) => !request.uri.hasQuery), isTrue);
        expect(requests.last.body, {
          'mcp': {
            'local-tools': {
              'type': 'local',
              'command': ['npx', '-y', '@example/mcp-server'],
              'cwd': '/work/mobile',
              'environment': {'LOG_LEVEL': 'warn'},
              'timeout': 9000,
            },
          },
        });
      } finally {
        api.close();
        await server.close(force: true);
      }
    }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
  });

  test(
    'MCP OAuth preserves state and completes through generated routes',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        final requests = <({String method, Uri uri, Object? body})>[];
        server.listen((request) async {
          final text = await utf8.decoder.bind(request).join();
          requests.add((
            method: request.method,
            uri: request.uri,
            body: text.isEmpty ? null : jsonDecode(text),
          ));
          request.response.headers.contentType = ContentType.json;
          if (request.method == 'DELETE') {
            request.response.write(jsonEncode({'success': true}));
          } else if (request.uri.path.endsWith('/auth/callback')) {
            request.response.write(jsonEncode({'status': 'connected'}));
          } else {
            request.response.write(
              jsonEncode({
                'authorizationUrl':
                    'https://mcp-auth.example.com/authorize?redirect_uri='
                    'http%3A%2F%2F127.0.0.1%3A19876%2Fmcp%2Foauth%2Fcallback',
                'oauthState': 'state-1',
              }),
            );
          }
          await request.response.close();
        });

        final api = OpenCodeApi(
          baseUrl: 'http://${server.address.host}:${server.port}',
        );
        final repository = SdkProductRepository(api.sdkClient)
          ..setLocation(directory: '/work/mobile', workspace: 'workspace-1');
        try {
          final launch = await repository.startMcpAuthentication(
            'remote/tools',
          );
          expect(launch.oauthState, 'state-1');
          expect(launch.authorizationUrl.host, 'mcp-auth.example.com');

          final status = await repository.completeMcpAuthentication(
            'remote/tools',
            'code-1',
          );
          expect(status.name, 'remote/tools');
          expect(status.status, 'connected');

          await repository.cancelMcpAuthentication('remote/tools');

          expect(requests.map((request) => request.method), [
            'POST',
            'POST',
            'DELETE',
          ]);
          expect(requests.map((request) => request.uri.path), [
            '/mcp/remote%2Ftools/auth',
            '/mcp/remote%2Ftools/auth/callback',
            '/mcp/remote%2Ftools/auth',
          ]);
          expect(
            requests.every(
              (request) =>
                  request.uri.queryParameters['directory'] == '/work/mobile' &&
                  request.uri.queryParameters['workspace'] == 'workspace-1',
            ),
            isTrue,
          );
          expect(requests[1].body, {'code': 'code-1'});
        } finally {
          api.close();
          await server.close(force: true);
        }
      }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
    },
  );

  test(
    'persistent MCP setup rejects duplicate names before patching',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        var requestCount = 0;
        server.listen((request) async {
          requestCount += 1;
          request.response.headers.contentType = ContentType.json;
          request.response.write(
            jsonEncode({
              'mcp': {
                'existing': {
                  'type': 'remote',
                  'url': 'https://existing.example.com/mcp',
                },
              },
            }),
          );
          await request.response.close();
        });

        final api = OpenCodeApi(
          baseUrl: 'http://${server.address.host}:${server.port}',
        );
        final repository = SdkProductRepository(api.sdkClient);
        try {
          await expectLater(
            repository.addMcpServer(
              const McpServerDraft(
                name: 'existing',
                kind: McpServerKind.remote,
                url: 'https://replacement.example.com/mcp',
              ),
              scope: McpConfigScope.global,
            ),
            throwsA(
              isA<ProductException>().having(
                (error) => error.message,
                'message',
                contains('already exists'),
              ),
            ),
          );
          expect(requestCount, 1);
        } finally {
          api.close();
          await server.close(force: true);
        }
      }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
    },
  );

  test('MCP drafts fail closed on unsafe transport data', () {
    for (final draft in [
      const McpServerDraft(
        name: 'remote',
        kind: McpServerKind.remote,
        url: 'ftp://mcp.example.com',
      ),
      const McpServerDraft(
        name: 'header',
        kind: McpServerKind.remote,
        url: 'https://mcp.example.com',
        headers: {'Authorization\nInjected': 'secret'},
      ),
      const McpServerDraft(name: 'local', kind: McpServerKind.local),
      const McpServerDraft(
        name: 'timeout',
        kind: McpServerKind.local,
        command: ['server'],
        timeoutMs: 0,
      ),
    ]) {
      expect(draft.toConfigJson, throwsA(isA<ProductException>()));
    }
  });

  test(
    'VCS diff scopes use generated API modes and preserve patches',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        final modes = <String?>[];
        server.listen((request) async {
          modes.add(request.uri.queryParameters['mode']);
          expect(request.uri.path, '/vcs/diff');
          expect(request.uri.queryParameters['directory'], '/work/acme');
          expect(request.uri.queryParameters['workspace'], 'workspace-1');
          expect(request.uri.queryParameters['context'], '3');
          request.response.headers.contentType = ContentType.json;
          request.response.write(
            jsonEncode([
              {
                'file': 'lib/main.dart',
                'patch': '@@ -1 +1 @@\n-old\n+new',
                'additions': 1,
                'deletions': 1,
                'status': 'modified',
              },
            ]),
          );
          await request.response.close();
        });

        try {
          final api = OpenCodeApi(
            baseUrl: 'http://${server.address.host}:${server.port}',
          );
          final repository = SdkProductRepository(api.sdkClient)
            ..setLocation(directory: '/work/acme', workspace: 'workspace-1');

          final working = await repository.listVcsDiffs(
            VcsDiffMode.workingTree,
          );
          final branch = await repository.listVcsDiffs(VcsDiffMode.branch);

          expect(modes, ['git', 'branch']);
          expect(working.single.file, 'lib/main.dart');
          expect(working.single.patch, contains('+new'));
          expect(working.single.counts, (added: 1, removed: 1));
          expect(working.single.status, 'modified');
          expect(branch.single.file, 'lib/main.dart');
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
    },
  );
}
