import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'support/product_repository_fixtures.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'provider key connection synchronizes runtime auth and refreshes instances',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        final requests = <({String method, Uri uri, String body})>[];
        server.listen((request) async {
          final body = await utf8.decoder.bind(request).join();
          requests.add((method: request.method, uri: request.uri, body: body));
          request.response.headers.contentType = ContentType.json;
          if (request.uri.path.startsWith('/api/integration/')) {
            request.response.statusCode = HttpStatus.noContent;
          } else if (request.uri.path == '/session/status') {
            request.response.write('{}');
          } else {
            request.response.write('true');
          }
          await request.response.close();
        });

        try {
          final api = OpenCodeApi(
            baseUrl: 'http://${server.address.host}:${server.port}',
          );
          final repository = SdkProductRepository(api.sdkClient)
            ..setLocation(directory: '/root', workspace: 'phone');

          await repository.connectIntegrationKey(
            'zai-coding-plan',
            'test-secret',
            label: 'Coding plan',
          );

          expect(requests.map((request) => request.method), [
            'POST',
            'PUT',
            'GET',
            'GET',
            'POST',
            'POST',
          ]);
          expect(requests.map((request) => request.uri.path), [
            '/api/integration/zai-coding-plan/connect/key',
            '/auth/zai-coding-plan',
            '/session/status',
            '/session/status',
            '/instance/dispose',
            '/instance/dispose',
          ]);
          expect(jsonDecode(requests[0].body), {
            'key': 'test-secret',
            'label': 'Coding plan',
          });
          expect(jsonDecode(requests[1].body), {
            'type': 'api',
            'key': 'test-secret',
          });
          expect(requests[0].uri.queryParameters, {
            'location[directory]': '/root',
            'location[workspace]': 'phone',
          });
          // Running replies are counted in both locations the refresh
          // disposes, before anything is disposed.
          expect(requests[2].uri.queryParameters, {
            'directory': '/root',
            'workspace': 'phone',
          });
          expect(requests[3].uri.queryParameters, isEmpty);
          expect(requests[4].uri.queryParameters, {
            'directory': '/root',
            'workspace': 'phone',
          });
          expect(requests[5].uri.queryParameters, isEmpty);
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
    },
  );

  test(
    'provider disconnect removes exact legacy and v2 credentials then refreshes',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        final requests = <({String method, Uri uri})>[];
        server.listen((request) async {
          requests.add((method: request.method, uri: request.uri));
          request.response.headers.contentType = ContentType.json;
          if (request.uri.path.startsWith('/api/credential/')) {
            request.response.statusCode = HttpStatus.noContent;
          } else if (request.uri.path == '/session/status') {
            request.response.write('{}');
          } else {
            request.response.write('true');
          }
          await request.response.close();
        });

        try {
          final api = OpenCodeApi(
            baseUrl: 'http://${server.address.host}:${server.port}',
          );
          final repository = SdkProductRepository(api.sdkClient)
            ..setLocation(directory: '/root', workspace: 'phone');

          await repository.disconnectIntegration(
            const IntegrationInfo(
              id: 'zai-coding-plan',
              name: 'Z.AI Coding Plan',
              methods: [],
              connections: [
                IntegrationConnectionInfo(
                  type: 'credential',
                  id: 'credential/phone key',
                  label: 'Phone key',
                ),
              ],
              connectionCount: 1,
            ),
          );

          expect(requests.map((request) => request.method), [
            'DELETE',
            'DELETE',
            'GET',
            'GET',
            'POST',
            'POST',
          ]);
          expect(requests.map((request) => request.uri.path), [
            '/auth/zai-coding-plan',
            '/api/credential/credential%2Fphone%20key',
            '/session/status',
            '/session/status',
            '/instance/dispose',
            '/instance/dispose',
          ]);
          expect(requests[1].uri.queryParameters, {
            'location[directory]': '/root',
            'location[workspace]': 'phone',
          });
          // Running replies are counted in both locations the refresh
          // disposes, before anything is disposed.
          expect(requests[2].uri.queryParameters, {
            'directory': '/root',
            'workspace': 'phone',
          });
          expect(requests[3].uri.queryParameters, isEmpty);
          expect(requests[4].uri.queryParameters, {
            'directory': '/root',
            'workspace': 'phone',
          });
          expect(requests[5].uri.queryParameters, isEmpty);
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
    },
  );

  test(
    'integration listing retains exact credential and environment identities',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        server.listen((request) async {
          request.response.headers.contentType = ContentType.json;
          if (request.uri.path == '/api/integration') {
            request.response.write(
              jsonEncode({
                'location': {
                  'directory': '/root',
                  'workspaceID': 'phone',
                  'project': {'id': 'project-1', 'directory': '/root'},
                },
                'data': [
                  {
                    'id': 'cloud',
                    'name': 'Cloud Provider',
                    'methods': [
                      {'type': 'key', 'label': 'API key'},
                      {
                        'id': 'oauth-cloud',
                        'type': 'oauth',
                        'label': 'Cloud OAuth',
                      },
                      {
                        'type': 'env',
                        'names': ['CLOUD_TOKEN'],
                      },
                    ],
                    'connections': [
                      {
                        'type': 'credential',
                        'id': 'credential-1',
                        'label': 'Phone key',
                      },
                      {'type': 'env', 'name': 'CLOUD_TOKEN'},
                    ],
                  },
                  {
                    'id': 'stale',
                    'name': 'Stale Provider',
                    'methods': [
                      {'type': 'key', 'label': 'API key'},
                    ],
                    'connections': [
                      {
                        'type': 'credential',
                        'id': 'credential-stale',
                        'label': 'Old key',
                      },
                    ],
                  },
                ],
              }),
            );
          } else if (request.uri.path == '/provider/auth') {
            request.response.write(
              jsonEncode({
                'cloud': [
                  {'type': 'oauth', 'label': 'Cloud OAuth'},
                  {'type': 'oauth', 'label': 'Legacy-only OAuth'},
                ],
                'legacy': [
                  {'type': 'oauth', 'label': 'Legacy Login'},
                ],
              }),
            );
          } else if (request.uri.path == '/provider') {
            request.response.write(
              jsonEncode({
                'all': <Object>[],
                'default': <String, String>{},
                'connected': ['cloud'],
              }),
            );
          } else {
            request.response.statusCode = HttpStatus.notFound;
          }
          await request.response.close();
        });

        try {
          final api = OpenCodeApi(
            baseUrl: 'http://${server.address.host}:${server.port}',
          );
          final repository = SdkProductRepository(api.sdkClient)
            ..setLocation(directory: '/root', workspace: 'phone');

          final integrations = await repository.listIntegrations();
          final integration = integrations.singleWhere(
            (integration) => integration.id == 'cloud',
          );

          expect(integration.id, 'cloud');
          expect(integration.connectionCount, 2);
          expect(integration.credentialIDs, ['credential-1']);
          expect(integration.hasEnvironmentConnection, isTrue);
          expect(
            integration.methods
                .where((method) => method.type == 'oauth')
                .map((method) => (method.id, method.label)),
            [('0', 'Cloud OAuth'), ('1', 'Legacy-only OAuth')],
          );
          expect(
            integration.connections.map((connection) => connection.label),
            ['Phone key', 'CLOUD_TOKEN'],
          );
          final legacy = integrations.singleWhere(
            (integration) => integration.id == 'legacy',
          );
          expect(legacy.name, 'legacy');
          expect(legacy.methods.single.id, '0');
          expect(legacy.methods.single.label, 'Legacy Login');
          expect(legacy.connectionCount, 0);
          final stale = integrations.singleWhere(
            (integration) => integration.id == 'stale',
          );
          expect(stale.connectionCount, 0);
          expect(stale.credentialIDs, ['credential-stale']);
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
    },
  );

  test(
    'OC1 keeps API keys available and refuses browser auth without a legacy method',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        final requests = <String>[];
        server.listen((request) async {
          requests.add(request.uri.path);
          request.response.headers.contentType = ContentType.json;
          final response = switch (request.uri.path) {
            '/api/integration' => {
              'location': {
                'directory': '/root',
                'project': {'id': 'project-1', 'directory': '/root'},
              },
              'data': [
                for (final id in ['anthropic', 'google'])
                  {
                    'id': id,
                    'name': id,
                    'methods': [
                      {'type': 'key', 'label': 'API key'},
                      {'type': 'oauth', 'id': 'browser', 'label': 'Browser'},
                    ],
                    'connections': <Object>[],
                  },
              ],
            },
            '/provider/auth' => <String, Object>{},
            '/provider' => {
              'all': <Object>[],
              'default': <String, String>{},
              // A stored OAuth credential is not a callable browser method.
              'connected': ['anthropic', 'google'],
            },
            _ => null,
          };
          if (response == null) {
            request.response.statusCode = HttpStatus.notFound;
          } else {
            request.response.write(jsonEncode(response));
          }
          await request.response.close();
        });

        final api = OpenCodeApi(
          baseUrl: 'http://${server.address.host}:${server.port}',
        );
        try {
          final repository = SdkProductRepository(api.sdkClient);
          final integrations = await repository.listIntegrations();
          expect(integrations, hasLength(2));
          for (final integration in integrations) {
            expect(integration.methods.single.type, 'key');
            // This is credential presence, not an inference readiness claim.
            expect(integration.connectionCount, 1);
            await expectLater(
              repository.startIntegrationOAuth(integration.id, '0'),
              throwsA(isA<ProductException>()),
            );
          }
          expect(requests.where((path) => path.contains('/oauth/')), isEmpty);
        } finally {
          api.sdkClient.dio.close(force: true);
          await server.close(force: true);
        }
      }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
    },
  );

  test(
    'provider disconnect preserves visible v2 connection when legacy removal fails',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        final requests = <({String method, Uri uri})>[];
        server.listen((request) async {
          requests.add((method: request.method, uri: request.uri));
          request.response.statusCode = HttpStatus.internalServerError;
          request.response.headers.contentType = ContentType.json;
          request.response.write(jsonEncode({'message': 'write failed'}));
          await request.response.close();
        });

        try {
          final api = OpenCodeApi(
            baseUrl: 'http://${server.address.host}:${server.port}',
          );
          final repository = SdkProductRepository(api.sdkClient)
            ..setLocation(directory: '/root', workspace: 'phone');
          const integration = IntegrationInfo(
            id: 'cloud',
            name: 'Cloud',
            methods: [],
            connections: [
              IntegrationConnectionInfo(
                type: 'credential',
                id: 'credential-1',
                label: 'default',
              ),
            ],
            connectionCount: 1,
          );

          await expectLater(
            repository.disconnectIntegration(integration),
            throwsA(
              isA<ProductException>().having(
                (error) => error.message,
                'message',
                contains('Nothing else was removed'),
              ),
            ),
          );

          expect(requests, hasLength(1));
          expect(requests.single.method, 'DELETE');
          expect(requests.single.uri.path, '/auth/cloud');
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
    },
  );

  test(
    'provider disconnect refreshes runtime and reports a retained v2 credential',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        final requests = <({String method, Uri uri})>[];
        server.listen((request) async {
          requests.add((method: request.method, uri: request.uri));
          request.response.headers.contentType = ContentType.json;
          if (request.uri.path.startsWith('/api/credential/')) {
            request.response.statusCode = HttpStatus.internalServerError;
            request.response.write(jsonEncode({'message': 'database busy'}));
          } else if (request.uri.path == '/session/status') {
            request.response.write('{}');
          } else {
            request.response.write('true');
          }
          await request.response.close();
        });

        try {
          final api = OpenCodeApi(
            baseUrl: 'http://${server.address.host}:${server.port}',
          );
          final repository = SdkProductRepository(api.sdkClient)
            ..setLocation(directory: '/root', workspace: 'phone');
          const integration = IntegrationInfo(
            id: 'cloud',
            name: 'Cloud',
            methods: [],
            connections: [
              IntegrationConnectionInfo(
                type: 'credential',
                id: 'credential-1',
                label: 'default',
              ),
            ],
            connectionCount: 1,
          );

          await expectLater(
            repository.disconnectIntegration(integration),
            throwsA(
              isA<ProductException>().having(
                (error) => error.message,
                'message',
                contains('connection remains visible'),
              ),
            ),
          );

          expect(requests.map((request) => request.uri.path), [
            '/auth/cloud',
            '/api/credential/credential-1',
            '/session/status',
            '/session/status',
            '/instance/dispose',
            '/instance/dispose',
          ]);
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
    },
  );

  test(
    'coding health uses generated VCS, file, LSP, and formatter contracts',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        final requests = <Uri>[];
        server.listen((request) async {
          requests.add(request.uri);
          request.response.headers.contentType = ContentType.json;
          switch (request.uri.path) {
            case '/project/current':
              request.response.write(
                jsonEncode({
                  'id': 'project-1',
                  'worktree': '/work/app',
                  'vcs': 'git',
                  'time': {'created': 1, 'updated': 2},
                  'sandboxes': <Object?>[],
                }),
              );
            case '/vcs':
              request.response.write(
                jsonEncode({
                  'branch': 'feature/mobile',
                  'default_branch': 'main',
                }),
              );
            case '/vcs/status':
              request.response.write(
                jsonEncode([
                  {
                    'file': 'lib/main.dart',
                    'additions': 7,
                    'deletions': 2,
                    'status': 'modified',
                  },
                ]),
              );
            case '/lsp':
              request.response.write(
                jsonEncode([
                  {
                    'id': 'dart',
                    'name': 'Dart analysis server',
                    'root': '/work/app',
                    'status': 'connected',
                  },
                ]),
              );
            case '/formatter':
              request.response.write(
                jsonEncode([
                  {
                    'name': 'dart format',
                    'extensions': ['.dart'],
                    'enabled': true,
                  },
                ]),
              );
            case '/find/symbol':
              request.response.write(
                jsonEncode([
                  {
                    'name': 'ProjectHealthScreen',
                    'kind': 5,
                    'location': {
                      'uri':
                          'file:///work/app/lib/ui/project_health_screen.dart',
                      'range': {
                        'start': {'line': 41, 'character': 3},
                        'end': {'line': 41, 'character': 22},
                      },
                    },
                  },
                ]),
              );
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
            ..setLocation(directory: '/work/app', workspace: 'phone');

          final vcs = await repository.loadVersionControlHealth();
          final files = await repository.listFileStatuses();
          final languageServices = await repository.listLanguageServices();
          final formatters = await repository.listFormatters();
          final symbols = await repository.findWorkspaceSymbols(
            'ProjectHealth',
          );

          expect(vcs.branch, 'feature/mobile');
          expect(vcs.setupState, VersionControlSetupState.git);
          expect(vcs.defaultBranch, 'main');
          expect(vcs.additions, 7);
          expect(vcs.deletions, 2);
          expect(vcs.changes.single.path, 'lib/main.dart');
          expect(vcs.changes.single.status, 'modified');
          expect(files.single.path, 'lib/main.dart');
          expect(files.single.status, 'modified');
          expect(files.single.additions, 7);
          expect(files.single.deletions, 2);
          expect(languageServices.single.name, 'Dart analysis server');
          expect(languageServices.single.connected, isTrue);
          expect(formatters.single.name, 'dart format');
          expect(formatters.single.extensions, ['.dart']);
          expect(formatters.single.enabled, isTrue);
          expect(symbols.single.name, 'ProjectHealthScreen');
          expect(symbols.single.kind, 5);
          expect(symbols.single.path, 'lib/ui/project_health_screen.dart');
          expect(symbols.single.line, 42);
          expect(symbols.single.column, 4);
          expect(requests.map((uri) => uri.path).toSet(), {
            '/project/current',
            '/vcs',
            '/vcs/status',
            '/lsp',
            '/formatter',
            '/find/symbol',
          });
          expect(
            requests.where((uri) => uri.path == '/vcs/status'),
            hasLength(2),
          );
          for (final uri in requests) {
            expect(uri.queryParameters, {
              'directory': '/work/app',
              'workspace': 'phone',
              if (uri.path == '/find/symbol') 'query': 'ProjectHealth',
            });
          }
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
    },
  );

  test(
    'project Git setup is detected and initialized through generated APIs',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        final requests = <({String method, Uri uri, String body})>[];
        var initCalls = 0;
        server.listen((request) async {
          final body = await utf8.decoder.bind(request).join();
          requests.add((method: request.method, uri: request.uri, body: body));
          request.response.headers.contentType = ContentType.json;
          switch (request.uri.path) {
            case '/project/current':
              request.response.write(
                jsonEncode({
                  'id': 'global',
                  'worktree': '/',
                  'time': {'created': 1, 'updated': 1},
                  'sandboxes': <Object?>[],
                }),
              );
            case '/vcs':
              request.response.write(
                jsonEncode({'branch': null, 'default_branch': null}),
              );
            case '/vcs/status':
              request.response.write(jsonEncode(<Object?>[]));
            case '/project/git/init':
              initCalls++;
              request.response.write(
                jsonEncode({
                  'id': 'global',
                  'worktree': '/work/new-project',
                  if (initCalls == 1) 'vcs': 'git',
                  'time': {'created': 1, 'updated': 2},
                  'sandboxes': <Object?>[],
                }),
              );
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
            ..setLocation(directory: '/work/new-project', workspace: 'phone');

          final health = await repository.loadVersionControlHealth();
          expect(health.setupState, VersionControlSetupState.absent);
          expect(health.branch, isNull);
          expect(health.changes, isEmpty);

          await repository.initializeGitRepository();
          final initRequest = requests.singleWhere(
            (request) => request.uri.path == '/project/git/init',
          );
          expect(initRequest.method, 'POST');
          expect(initRequest.uri.queryParameters, {
            'directory': '/work/new-project',
            'workspace': 'phone',
          });
          expect(initRequest.body, isEmpty);

          await expectLater(
            repository.initializeGitRepository(),
            throwsA(
              isA<ProductException>().having(
                (error) => error.toString(),
                'message',
                contains('did not confirm'),
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
    'provider code OAuth writes the legacy auth store used by v1 chat',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        final requests = <({String method, Uri uri, String body})>[];
        server.listen((request) async {
          final body = await utf8.decoder.bind(request).join();
          requests.add((method: request.method, uri: request.uri, body: body));
          request.response.headers.contentType = ContentType.json;
          if (request.method == 'GET' && request.uri.path == '/provider/auth') {
            request.response.write(
              jsonEncode({
                'cloud': [
                  {'type': 'oauth', 'label': 'Cloud OAuth'},
                ],
              }),
            );
          } else if (request.method == 'POST' &&
              request.uri.path == '/provider/cloud/oauth/authorize') {
            request.response.write(
              jsonEncode({
                'url': 'https://auth.example.com/authorize',
                'instructions': 'Paste the returned code',
                'method': 'code',
              }),
            );
          } else if (request.method == 'POST' &&
              request.uri.path == '/provider/cloud/oauth/callback') {
            request.response.write('true');
          } else {
            request.response.statusCode = HttpStatus.notFound;
          }
          await request.response.close();
        });

        try {
          final api = OpenCodeApi(
            baseUrl: 'http://${server.address.host}:${server.port}',
          );
          final repository = SdkProductRepository(api.sdkClient)
            ..setLocation(directory: '/root', workspace: 'phone');

          final launch = await repository.startIntegrationOAuth(
            'cloud',
            '0',
            inputs: const {'tenant': 'acme'},
          );
          final resumedRepository = SdkProductRepository(api.sdkClient)
            ..setLocation(directory: '/root', workspace: 'phone');
          final pending = await resumedRepository.integrationOAuthStatus(
            launch.attemptID,
          );
          await resumedRepository.completeIntegrationOAuth(
            launch.attemptID,
            code:
                'https://auth.example.com/callback?code=returned-code&state=state-1',
          );
          final complete = await resumedRepository.integrationOAuthStatus(
            launch.attemptID,
          );
          await resumedRepository.cancelIntegrationOAuth(launch.attemptID);

          expect(launch.attemptID, startsWith('provider-oauth-'));
          expect(launch.mode, IntegrationAuthMode.code);
          expect(pending.state, IntegrationAuthState.pending);
          expect(complete.state, IntegrationAuthState.complete);
          expect(requests.map((request) => request.method), [
            'GET',
            'POST',
            'POST',
          ]);
          expect(requests.map((request) => request.uri.path), [
            '/provider/auth',
            '/provider/cloud/oauth/authorize',
            '/provider/cloud/oauth/callback',
          ]);
          expect(jsonDecode(requests[1].body), {
            'method': 0,
            'inputs': {'tenant': 'acme'},
          });
          expect(jsonDecode(requests[2].body), {
            'method': 0,
            'code': 'returned-code',
          });
          for (final request in requests) {
            expect(request.uri.queryParameters, {
              'directory': '/root',
              'workspace': 'phone',
            });
          }
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
    },
  );

  test(
    'provider automatic OAuth completes through the legacy callback',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        final requests = <({String method, Uri uri, String body})>[];
        server.listen((request) async {
          final body = await utf8.decoder.bind(request).join();
          requests.add((method: request.method, uri: request.uri, body: body));
          request.response.headers.contentType = ContentType.json;
          if (request.method == 'GET' && request.uri.path == '/provider/auth') {
            request.response.write(
              jsonEncode({
                'openai': [
                  {'type': 'oauth', 'label': 'ChatGPT Pro/Plus (headless)'},
                ],
              }),
            );
          } else if (request.uri.path == '/provider/openai/oauth/authorize') {
            request.response.write(
              jsonEncode({
                'url': 'https://auth.openai.com/codex/device',
                'instructions': 'Enter code: ABCD-EFGH',
                'method': 'auto',
              }),
            );
          } else if (request.uri.path == '/provider/openai/oauth/callback') {
            request.response.write('true');
          } else {
            request.response.statusCode = HttpStatus.notFound;
          }
          await request.response.close();
        });

        try {
          final api = OpenCodeApi(
            baseUrl: 'http://${server.address.host}:${server.port}',
          );
          final repository = SdkProductRepository(api.sdkClient);
          final launch = await repository.startIntegrationOAuth('openai', '0');
          final resumedRepository = SdkProductRepository(api.sdkClient);
          final status = await resumedRepository.integrationOAuthStatus(
            launch.attemptID,
          );

          expect(launch.mode, IntegrationAuthMode.auto);
          expect(launch.instructions, 'Enter code: ABCD-EFGH');
          expect(status.state, IntegrationAuthState.complete);
          expect(requests.map((request) => request.uri.path), [
            '/provider/auth',
            '/provider/openai/oauth/authorize',
            '/provider/openai/oauth/callback',
          ]);
          expect(jsonDecode(requests.last.body), {'method': 0});
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
    },
  );
}
