import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'support/product_repository_fixtures.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'session destinations and Console orgs use exact generated contracts',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        final requests = <({String method, Uri uri, String body})>[];
        server.listen((request) async {
          final body = await utf8.decoder.bind(request).join();
          requests.add((method: request.method, uri: request.uri, body: body));
          request.response.headers.contentType = ContentType.json;
          switch (request.uri.path) {
            case '/project/project-1/directories':
              request.response.write(
                jsonEncode([
                  {'directory': '/work/acme'},
                  {'directory': '/work/acme-copy', 'strategy': 'git_worktree'},
                ]),
              );
            case '/experimental/control-plane/move-session':
            case '/experimental/workspace/warp':
            case '/session/session-1/prompt_async':
              request.response.statusCode = HttpStatus.noContent;
            case '/experimental/console/orgs':
              request.response.write(
                jsonEncode({
                  'orgs': [
                    {
                      'accountID': 'account-1',
                      'accountEmail': 'dev@example.com',
                      'accountUrl': 'https://console.example.com',
                      'orgID': 'org-1',
                      'orgName': 'Acme',
                      'active': false,
                    },
                  ],
                }),
              );
            case '/experimental/console/switch':
            case '/instance/dispose':
              request.response.write('true');
            default:
              request.response.statusCode = HttpStatus.notFound;
          }
          await request.response.close();
        });

        try {
          final api = OpenCodeApi(
            baseUrl: 'http://${server.address.host}:${server.port}',
          );
          final repository = SdkProductRepository(api.sdkClient)
            ..setLocation(directory: '/work/acme', workspace: 'workspace-1');

          final directories = await repository.listProjectDirectories(
            'project-1',
          );
          await repository.moveSession(
            'session-1',
            directory: '/work/acme-copy',
            moveChanges: true,
          );
          await repository.warpSession(
            'session-1',
            workspaceID: 'workspace-2',
            copyChanges: false,
          );
          final organizations = await repository.listConsoleOrganizations();
          await repository.switchConsoleOrganization(organizations.single);
          await repository.addSessionLocationReminder(
            'session-1',
            '/work/acme-copy',
          );

          expect(directories.map((item) => item.directory), [
            '/work/acme',
            '/work/acme-copy',
          ]);
          expect(directories.last.strategy, 'git_worktree');
          expect(organizations.single.orgName, 'Acme');
          expect(requests.map((request) => request.uri.path), [
            '/project/project-1/directories',
            '/experimental/control-plane/move-session',
            '/experimental/workspace/warp',
            '/experimental/console/orgs',
            '/experimental/console/switch',
            '/instance/dispose',
            '/session/session-1/prompt_async',
          ]);
          expect(jsonDecode(requests[1].body), {
            'sessionID': 'session-1',
            'destination': {'directory': '/work/acme-copy'},
            'moveChanges': true,
          });
          expect(requests[1].uri.queryParameters, isEmpty);
          expect(jsonDecode(requests[2].body), {
            'id': 'workspace-2',
            'sessionID': 'session-1',
            'copyChanges': false,
          });
          expect(requests[2].uri.queryParameters, {
            'directory': '/work/acme',
            'workspace': 'workspace-1',
          });
          expect(jsonDecode(requests[4].body), {
            'accountID': 'account-1',
            'orgID': 'org-1',
          });
          expect(requests[4].uri.queryParameters, {
            'directory': '/work/acme',
            'workspace': 'workspace-1',
          });
          expect(requests[5].uri.queryParameters, {
            'directory': '/work/acme',
            'workspace': 'workspace-1',
          });
          expect(jsonDecode(requests[6].body), {
            'noReply': true,
            'parts': [
              {
                'type': 'text',
                'text': contains('/work/acme-copy'),
                'synthetic': true,
              },
            ],
          });
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
    },
  );

  test('workspace listing survives an older server without status', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final paths = <String>[];
      server.listen((request) async {
        paths.add(request.uri.path);
        request.response.headers.contentType = ContentType.json;
        if (request.uri.path == '/experimental/workspace') {
          request.response.write(
            jsonEncode([
              {
                'id': 'workspace-1',
                'type': 'cloud',
                'name': 'Remote workspace',
                'projectID': 'project-1',
                'timeUsed': 1,
              },
            ]),
          );
        } else {
          request.response.statusCode = HttpStatus.notFound;
          request.response.write(jsonEncode({'message': 'not found'}));
        }
        await request.response.close();
      });

      try {
        final api = OpenCodeApi(
          baseUrl: 'http://${server.address.host}:${server.port}',
        );
        final repository = SdkProductRepository(api.sdkClient)
          ..setLocation(directory: '/work/acme');

        final workspaces = await repository.listWorkspaces();

        expect(paths, [
          '/experimental/workspace',
          '/experimental/workspace/status',
        ]);
        expect(workspaces.single.name, 'Remote workspace');
        expect(workspaces.single.status, isNull);
      } finally {
        await server.close(force: true);
      }
    }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
  });

  test(
    'managed workspace lifecycle uses generated project-root contracts',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        final requests = <({String method, Uri uri, String body})>[];
        server.listen((request) async {
          final body = await utf8.decoder.bind(request).join();
          requests.add((method: request.method, uri: request.uri, body: body));
          request.response.headers.contentType = ContentType.json;
          switch ('${request.method} ${request.uri.path}') {
            case 'GET /experimental/workspace/adapter':
              request.response.write(
                jsonEncode([
                  {
                    'type': 'cloud',
                    'name': 'Cloud runner',
                    'description': 'Create an isolated remote runner',
                  },
                ]),
              );
            case 'GET /experimental/workspace':
              request.response.write(
                jsonEncode([
                  {
                    'id': 'wrk_remote',
                    'type': 'cloud',
                    'name': 'Remote runner',
                    'branch': 'feature/mobile',
                    'directory': '/remote/acme',
                    'projectID': 'project-1',
                    'timeUsed': 1,
                  },
                ]),
              );
            case 'GET /experimental/workspace/status':
              request.response.write(
                jsonEncode([
                  {'workspaceID': 'wrk_remote', 'status': 'connected'},
                ]),
              );
            case 'POST /experimental/workspace/sync-list':
              request.response.statusCode = HttpStatus.noContent;
            case 'POST /experimental/workspace':
              request.response.write(
                jsonEncode({
                  'id': 'wrk_created',
                  'type': 'cloud',
                  'name': 'Created runner',
                  'branch': 'feature/phone',
                  'directory': '/remote/created',
                  'projectID': 'project-1',
                  'timeUsed': 2,
                }),
              );
            case 'DELETE /experimental/workspace/wrk_remote':
              request.response.write(
                jsonEncode({
                  'id': 'wrk_remote',
                  'type': 'cloud',
                  'name': 'Remote runner',
                  'projectID': 'project-1',
                  'timeUsed': 1,
                }),
              );
            default:
              request.response.statusCode = HttpStatus.notFound;
              request.response.write(jsonEncode({'message': 'not found'}));
          }
          await request.response.close();
        });

        try {
          final api = OpenCodeApi(
            baseUrl: 'http://${server.address.host}:${server.port}',
          );
          final repository = SdkProductRepository(api.sdkClient)
            ..setLocation(directory: '/remote/active', workspace: 'wrk_active');

          final adapters = await repository.listWorkspaceAdapters(
            projectDirectory: '/work/acme',
          );
          final workspaces = await repository.listManagedWorkspaces(
            projectDirectory: '/work/acme',
          );
          await repository.syncWorkspaceList(projectDirectory: '/work/acme');
          final created = await repository.createManagedWorkspace(
            projectDirectory: '/work/acme',
            type: ' cloud ',
            branch: ' feature/phone ',
          );
          await repository.removeManagedWorkspace(
            projectDirectory: '/work/acme',
            id: 'wrk_remote',
          );

          expect(adapters.single.name, 'Cloud runner');
          expect(workspaces.single.status, 'connected');
          expect(created.id, 'wrk_created');
          expect(created.directory, '/remote/created');
          expect(
            requests.map((request) => '${request.method} ${request.uri.path}'),
            [
              'GET /experimental/workspace/adapter',
              'GET /experimental/workspace',
              'GET /experimental/workspace/status',
              'POST /experimental/workspace/sync-list',
              'POST /experimental/workspace',
              'DELETE /experimental/workspace/wrk_remote',
            ],
          );
          for (final request in requests) {
            expect(request.uri.queryParameters, {'directory': '/work/acme'});
            expect(
              request.uri.queryParameters.containsKey('workspace'),
              isFalse,
            );
          }
          expect(jsonDecode(requests[4].body), {
            'type': 'cloud',
            'branch': 'feature/phone',
          });
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
    },
  );

  test(
    'catalog uses generated v2 transport and capability response shape',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        final requests = <Uri>[];
        server.listen((request) async {
          requests.add(request.uri);
          final location = {
            'directory': '/work/acme',
            'workspaceID': 'phone',
            'project': {'id': 'project-1', 'directory': '/work/acme'},
          };
          final data = switch (request.uri.path) {
            '/api/provider' => [
              {
                'id': 'opencode',
                'integrationID': 'zen',
                'name': 'OpenCode Zen',
                'disabled': false,
                'api': {
                  'id': 'opencode',
                  'type': 'aisdk',
                  'package': '@ai-sdk/openai-compatible',
                },
                'request': {
                  'headers': <String, String>{},
                  'body': <String, Object?>{},
                },
              },
            ],
            '/api/model' => [
              {
                'id': 'model-1',
                'providerID': 'opencode',
                'family': 'test',
                'name': 'Current model',
                'api': {
                  'id': 'model-1',
                  'type': 'aisdk',
                  'package': '@ai-sdk/openai-compatible',
                },
                'capabilities': {
                  'tools': true,
                  'input': ['text', 'image'],
                  'output': ['text'],
                },
                'request': {
                  'headers': <String, String>{},
                  'body': <String, Object?>{},
                },
                'variants': [
                  {
                    'id': 'fast',
                    'headers': <String, String>{},
                    'body': {'reasoningEffort': 'low'},
                  },
                ],
                'time': {'released': 1},
                'cost': [
                  {
                    'input': 0,
                    'output': 0,
                    'cache': {'read': 0, 'write': 0},
                  },
                ],
                'status': 'active',
                'enabled': true,
                'limit': {'context': 200000, 'output': 32000},
              },
            ],
            '/api/agent' => [
              {
                'id': 'build',
                'mode': 'primary',
                'hidden': false,
                'description': 'Default agent',
                'request': {
                  'headers': <String, String>{},
                  'body': <String, Object?>{},
                },
                'permissions': <Object?>[],
              },
            ],
            _ => <Object>[],
          };
          request.response.headers.contentType = ContentType.json;
          request.response.write(
            jsonEncode({'location': location, 'data': data}),
          );
          await request.response.close();
        });

        try {
          final api = OpenCodeApi(
            baseUrl: 'http://${server.address.host}:${server.port}',
          );
          final repository = SdkProductRepository(api.sdkClient)
            ..setLocation(directory: '/work/acme', workspace: 'phone');
          final catalog = await repository.loadCatalog();

          expect(catalog.providers.single.name, 'OpenCode Zen');
          expect(catalog.providers.single.integrationID, 'zen');
          expect(catalog.models.single.tools, isTrue);
          expect(catalog.models.single.attachments, isTrue);
          expect(catalog.models.single.contextLimit, 200000);
          expect(catalog.models.single.variants.single.id, 'fast');
          expect(catalog.models.single.variants.single.reasoningEffort, 'low');
          expect(catalog.models.single.variants.single.isFast, isTrue);
          expect(catalog.agents.single.id, 'build');
          expect(catalog.agents.single.description, 'Default agent');
          expect(requests.map((request) => request.path).toSet(), {
            '/api/provider',
            '/api/model',
            '/api/agent',
          });
          for (final request in requests) {
            expect(request.queryParameters, {
              'location[directory]': '/work/acme',
              'location[workspace]': 'phone',
            });
          }
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
    },
  );

  test(
    'tool inventory uses exact generated model and location contracts',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        final requests = <HttpRequest>[];
        server.listen((request) async {
          requests.add(request);
          final data = switch (request.uri.path) {
            '/experimental/capabilities' => {'backgroundSubagents': true},
            '/experimental/tool/ids' => ['bash', 'read', 'mcp_docs'],
            '/experimental/tool' => [
              {
                'id': 'mcp_docs',
                'description': 'Search project documentation',
                'parameters': {
                  'type': 'object',
                  'properties': {
                    'query': {'type': 'string'},
                  },
                  'required': ['query'],
                },
              },
            ],
            _ => <Object>[],
          };
          request.response.headers.contentType = ContentType.json;
          request.response.write(jsonEncode(data));
          await request.response.close();
        });

        try {
          final api = OpenCodeApi(
            baseUrl: 'http://${server.address.host}:${server.port}',
          );
          final repository = SdkProductRepository(api.sdkClient)
            ..setLocation(directory: '/work/acme', workspace: 'phone');

          final capabilities = await repository.loadExperimentalCapabilities();
          final ids = await repository.listCodingToolIDs();
          final tools = await repository.listCodingTools(
            providerID: ' openai ',
            modelID: ' gpt-5.6-sol ',
          );

          expect(capabilities.backgroundSubagents, isTrue);
          expect(ids, ['bash', 'read', 'mcp_docs']);
          expect(tools.single.id, 'mcp_docs');
          expect(tools.single.description, 'Search project documentation');
          expect(tools.single.parameters, {
            'type': 'object',
            'properties': {
              'query': {'type': 'string'},
            },
            'required': ['query'],
          });
          expect(requests.map((request) => request.uri.path), [
            '/experimental/capabilities',
            '/experimental/tool/ids',
            '/experimental/tool',
          ]);
          expect(requests[0].uri.queryParameters, {
            'directory': '/work/acme',
            'workspace': 'phone',
          });
          expect(requests[1].uri.queryParameters, {
            'directory': '/work/acme',
            'workspace': 'phone',
          });
          expect(requests[2].uri.queryParameters, {
            'directory': '/work/acme',
            'workspace': 'phone',
            'provider': 'openai',
            'model': 'gpt-5.6-sol',
          });
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
    },
  );

  test('fork session sends the selected OpenCode message point', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      // The fork, then the best-effort plain-name lookup and rename.
      final requests = <({String method, Uri uri, String body})>[];
      server.listen((request) async {
        final text = await utf8.decoder.bind(request).join();
        requests.add((method: request.method, uri: request.uri, body: text));
        request.response.headers.contentType = ContentType.json;
        request.response.write(
          jsonEncode({
            'id': 'forked-session',
            'slug': 'forked-session',
            'projectID': 'project-1',
            'directory': '/work/acme',
            'title': 'Forked session',
            'version': '1',
            'time': {'created': 1, 'updated': 1},
          }),
        );
        await request.response.close();
      });

      try {
        final api = OpenCodeApi(
          baseUrl: 'http://${server.address.host}:${server.port}',
        );
        final repository = SdkProductRepository(api.sdkClient)
          ..setLocation(directory: '/work/acme', workspace: 'phone');

        final id = await repository.forkSession(
          'session-1',
          messageID: 'message-7',
        );

        expect(id, 'forked-session');
        final fork = requests.firstWhere(
          (request) => request.uri.path == '/session/session-1/fork',
        );
        expect(fork.method, 'POST');
        expect(fork.uri.queryParameters, {
          'directory': '/work/acme',
          'workspace': 'phone',
        });
        expect(jsonDecode(fork.body), {'messageID': 'message-7'});
      } finally {
        await server.close(force: true);
      }
    }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
  });

  test(
    'shell settings use generated config and server shell contracts',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        final requests = <({String method, Uri uri, Object? body})>[];
        var selectedShell = 'zsh';
        server.listen((request) async {
          final text = await utf8.decoder.bind(request).join();
          final body = text.isEmpty ? null : jsonDecode(text);
          requests.add((method: request.method, uri: request.uri, body: body));
          request.response.headers.contentType = ContentType.json;
          switch ((request.method, request.uri.path)) {
            case ('GET', '/global/config'):
              request.response.write(jsonEncode({'shell': selectedShell}));
            case ('GET', '/pty/shells'):
              request.response.write(
                jsonEncode([
                  {'path': '/bin/bash', 'name': 'bash', 'acceptable': true},
                  {
                    'path': '/usr/bin/fish',
                    'name': 'fish',
                    'acceptable': false,
                  },
                ]),
              );
            case ('PATCH', '/global/config'):
              selectedShell = (body as Map<String, dynamic>)['shell'] as String;
              request.response.write(jsonEncode({'shell': selectedShell}));
            default:
              request.response.statusCode = HttpStatus.notFound;
              request.response.write(jsonEncode({'message': 'Unexpected'}));
          }
          await request.response.close();
        });

        try {
          final api = OpenCodeApi(
            baseUrl: 'http://${server.address.host}:${server.port}',
          );
          final repository = SdkProductRepository(api.sdkClient)
            ..setLocation(directory: '/work/acme', workspace: 'phone');

          final settings = await repository.loadTerminalShellSettings();
          expect(settings.selected, 'zsh');
          expect(settings.options, hasLength(2));
          expect(settings.options.first.path, '/bin/bash');
          expect(settings.options.first.acceptable, isTrue);
          expect(settings.options.last.name, 'fish');
          expect(settings.options.last.acceptable, isFalse);

          await repository.selectTerminalShell('/usr/bin/fish');

          expect(requests.map((request) => request.method), [
            'GET',
            'GET',
            'PATCH',
            'GET',
          ]);
          expect(requests.map((request) => request.uri.path), [
            '/global/config',
            '/pty/shells',
            '/global/config',
            '/global/config',
          ]);
          expect(requests[1].uri.queryParameters, {
            'directory': '/work/acme',
            'workspace': 'phone',
          });
          expect(requests[0].uri.queryParameters, isEmpty);
          expect(requests[2].uri.queryParameters, isEmpty);
          expect(requests[3].uri.queryParameters, isEmpty);
          expect(requests[2].body, {'shell': '/usr/bin/fish'});
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
    },
  );

  test(
    'shell update fails closed when global config does not retain it',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        server.listen((request) async {
          await request.drain<void>();
          request.response.headers.contentType = ContentType.json;
          request.response.write(jsonEncode({'shell': 'bash'}));
          await request.response.close();
        });
        try {
          final api = OpenCodeApi(
            baseUrl: 'http://${server.address.host}:${server.port}',
          );
          final repository = SdkProductRepository(api.sdkClient);

          await expectLater(
            repository.selectTerminalShell('fish'),
            throwsA(
              isA<ProductException>().having(
                (error) => error.toString(),
                'message',
                contains('did not retain'),
              ),
            ),
          );
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
    },
  );

  test(
    'terminal connection requests a guarded ticket before WebSocket',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        final receivedInput = Completer<String>();
        String? tokenHeader;
        String? tokenDirectory;
        String? socketDirectory;
        String? socketCursor;
        server.listen((request) async {
          if (request.uri.path.endsWith('/connect-token')) {
            tokenHeader = request.headers.value('x-opencode-ticket');
            tokenDirectory = request.uri.queryParameters['directory'];
            request.response.headers.contentType = ContentType.json;
            request.response.write(
              jsonEncode({'ticket': 'single-use-ticket', 'expires_in': 60}),
            );
            await request.response.close();
            return;
          }
          socketDirectory = request.uri.queryParameters['directory'];
          socketCursor = request.uri.queryParameters['cursor'];
          expect(request.uri.queryParameters['ticket'], 'single-use-ticket');
          final socket = await WebSocketTransformer.upgrade(request);
          socket.listen((data) {
            if (!receivedInput.isCompleted) {
              receivedInput.complete(data as String);
            }
          });
          socket.add([
            0,
            ...utf8.encode(jsonEncode({'cursor': 42})),
          ]);
          socket.add('ready مرحبا 👋');
        });

        try {
          final api = OpenCodeApi(
            baseUrl: 'http://${server.address.host}:${server.port}',
          );
          final repository = SdkProductRepository(api.sdkClient)
            ..setLocation(directory: '/work/acme');
          final channel = await repository.connectTerminal(
            'pty_test',
            cursor: 17,
          );
          final output = channel.output.first;
          channel.write('pwd\r');

          expect(await output, 'ready مرحبا 👋');
          expect(await receivedInput.future, 'pwd\r');
          expect(tokenHeader, '1');
          expect(tokenDirectory, '/work/acme');
          expect(socketDirectory, '/work/acme');
          expect(socketCursor, '17');
          expect(channel.cursor, 42 + utf8.encode('ready مرحبا 👋').length);
          await channel.close();
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
    },
  );

  test('server upgrade accepts only exact semantic versions', () {
    expect(isExactServerVersion('1.19.0'), isTrue);
    expect(isExactServerVersion('1.19.0-beta.2+mobile'), isTrue);
    expect(isExactServerVersion('latest'), isFalse);
    expect(isExactServerVersion('1.19'), isFalse);
    expect(isExactServerVersion(' 1.19.0'), isFalse);
  });

  test('remote upgrade uses the generated exact-version contract', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      String? method;
      Uri? uri;
      Map<String, dynamic>? body;
      server.listen((request) async {
        method = request.method;
        uri = request.uri;
        body = Map<String, dynamic>.from(
          jsonDecode(await utf8.decoder.bind(request).join()) as Map,
        );
        request.response.headers.contentType = ContentType.json;
        request.response.write(
          jsonEncode({'success': true, 'version': '1.19.0'}),
        );
        await request.response.close();
      });

      try {
        final api = OpenCodeApi(
          baseUrl: 'http://${server.address.host}:${server.port}',
        );
        final repository = SdkProductRepository(api.sdkClient)
          ..setLocation(directory: '/work/acme', workspace: 'phone');

        expect(await repository.upgradeServer('1.19.0'), '1.19.0');
        expect(method, 'POST');
        expect(uri?.path, '/global/upgrade');
        expect(uri?.queryParameters, isEmpty);
        expect(body, {'target': '1.19.0'});
      } finally {
        await server.close(force: true);
      }
    }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
  });

  test('client diagnostics use the generated redacted log contract', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final requests = <({Uri uri, Map<String, dynamic> body})>[];
      server.listen((request) async {
        requests.add((
          uri: request.uri,
          body: Map<String, dynamic>.from(
            jsonDecode(await utf8.decoder.bind(request).join()) as Map,
          ),
        ));
        request.response.headers.contentType = ContentType.json;
        request.response.write(jsonEncode(requests.length == 1));
        await request.response.close();
      });

      try {
        final api = OpenCodeApi(
          baseUrl: 'http://${server.address.host}:${server.port}',
        );
        final repository = SdkProductRepository(api.sdkClient)
          ..setLocation(directory: '/work/acme', workspace: 'phone');
        await repository.writeClientLog(
          message: 'OpenCode Mobile diagnostics (1 handled errors)',
          extra: {
            'entryCount': 1,
            'entries': [
              {'source': 'flutter', 'message': 'render failed'},
            ],
          },
        );

        expect(requests.single.uri.path, '/log');
        expect(requests.single.uri.queryParameters, {
          'directory': '/work/acme',
          'workspace': 'phone',
        });
        expect(requests.single.body, {
          'service': 'opencode-mobile',
          'level': 'error',
          'message': 'OpenCode Mobile diagnostics (1 handled errors)',
          'extra': {
            'entryCount': 1,
            'entries': [
              {'source': 'flutter', 'message': 'render failed'},
            ],
          },
        });

        await expectLater(
          repository.writeClientLog(
            message: 'OpenCode Mobile diagnostics (1 handled errors)',
          ),
          throwsA(
            isA<ProductException>().having(
              (error) => error.toString(),
              'message',
              contains('did not accept'),
            ),
          ),
        );
      } finally {
        await server.close(force: true);
      }
    }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
  });
}
