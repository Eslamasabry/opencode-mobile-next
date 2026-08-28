import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';

class _RealHttpOverrides extends HttpOverrides {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('configured providers retain OpenCode model variant metadata', () {
    final response = ProvidersResponse.fromJson({
      'providers': [
        {
          'id': 'provider',
          'name': 'Provider',
          'models': {
            'model': {
              'name': 'Model',
              'variants': {
                'fast': {'reasoningEffort': 'low'},
              },
            },
          },
        },
      ],
      'default': {'provider': 'model'},
    });

    expect(
      response.providers.single.modelData['model']?['variants'],
      contains('fast'),
    );
  });

  test('provider list keeps only connected providers in connection order', () {
    final response = ProvidersResponse.fromJson({
      'all': [
        {
          'id': 'opencode',
          'name': 'OpenCode Zen',
          'models': {'big-pickle': <String, Object?>{}},
        },
        {
          'id': 'zai-coding-plan',
          'name': 'Z.AI Coding Plan',
          'models': {
            'glm-5.2': {
              'variants': {
                'max': {'reasoningEffort': 'max'},
              },
            },
          },
        },
        {
          'id': 'unconnected',
          'name': 'Unconnected',
          'models': {'hidden-model': <String, Object?>{}},
        },
      ],
      'connected': ['zai-coding-plan', 'opencode'],
      'default': {
        'unconnected': 'hidden-model',
        'opencode': 'big-pickle',
        'zai-coding-plan': 'glm-5.2',
      },
    });

    expect(response.providers.map((provider) => provider.id), [
      'zai-coding-plan',
      'opencode',
    ]);
    expect(response.providers.first.modelIDs, ['glm-5.2']);
    expect(response.availableProviders.map((provider) => provider.id), [
      'opencode',
      'zai-coding-plan',
      'unconnected',
    ]);
    expect(response.defaultProviderID, 'zai-coding-plan');
    expect(response.defaultModelID, 'glm-5.2');
  });

  test(
    'chat defaults use the exact generated project config contract',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        Uri? requestUri;
        server.listen((request) async {
          requestUri = request.uri;
          request.response.headers.contentType = ContentType.json;
          request.response.write(
            jsonEncode({
              'model': 'openai/gpt-5.6-sol',
              'default_agent': 'plan',
            }),
          );
          await request.response.close();
        });

        try {
          final api = OpenCodeApi(
            baseUrl: 'http://${server.address.host}:${server.port}',
          );
          final repository = SdkProductRepository(api.sdkClient)
            ..setLocation(directory: '/work/acme', workspace: 'workspace-1');

          final defaults = await repository.loadChatDefaults();

          expect(requestUri?.path, '/config');
          expect(requestUri?.queryParameters, {
            'directory': '/work/acme',
            'workspace': 'workspace-1',
          });
          expect(defaults.model?.providerID, 'openai');
          expect(defaults.model?.modelID, 'gpt-5.6-sol');
          expect(defaults.agent, 'plan');
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
    },
  );

  test('project reference uses the upstream directory file-part contract', () {
    final attachment = PromptAttachment.reference(
      name: 'docs',
      path: '/workspace/../shared-docs',
    );

    expect(attachment.isDirectoryReference, isTrue);
    expect(attachment.toJson(), {
      'type': 'file',
      'mime': 'application/x-directory',
      'filename': 'docs',
      'url': 'file:///shared-docs',
    });
  });

  test('project reference preserves a remote Windows server path', () {
    final attachment = PromptAttachment.reference(
      name: 'platform-docs',
      path: r'C:\Shared Docs\platform',
    );

    expect(attachment.url, 'file:///C:/Shared%20Docs/platform');
    expect(attachment.isDirectoryReference, isTrue);
  });

  test('shell request serializes the selected thinking variant', () {
    final body = shellRequestBody(
      'flutter test',
      model: ModelRef(providerID: 'provider', modelID: 'model'),
      variant: 'high',
    );

    expect(body['variant'], 'high');
    expect(body['model'], {'providerID': 'provider', 'modelID': 'model'});
  });

  test('binary file content preserves metadata and decodes bytes', () {
    final content = FileContent.fromJson({
      'type': 'binary',
      'content': 'AAEC/w==',
      'encoding': 'base64',
      'mimeType': 'application/octet-stream',
    });

    expect(content.isBinary, isTrue);
    expect(content.encoding, 'base64');
    expect(content.mimeType, 'application/octet-stream');
    expect(content.bytes(), [0, 1, 2, 255]);
  });

  test('session parsing retains project and workspace ownership', () {
    final session = Session.fromJson({
      'id': 'session-1',
      'title': 'Mobile work',
      'projectID': 'project-1',
      'workspaceID': 'workspace-1',
      'directory': '/work/acme/packages/app',
      'path': 'packages/app',
      'time': {'created': 1, 'updated': 2},
    });

    expect(session.projectID, 'project-1');
    expect(session.workspaceID, 'workspace-1');
    expect(session.directory, '/work/acme/packages/app');
    expect(session.path, 'packages/app');
  });

  test('current OpenCode diff shape preserves patch and server counts', () {
    final diff = FileDiff.fromJson({
      'file': 'lib/main.dart',
      'patch': '@@ -1 +1 @@\n-old\n+new',
      'additions': 7,
      'deletions': 3,
      'status': 'modified',
    });

    expect(diff.patch, contains('+new'));
    expect(diff.counts, (added: 7, removed: 3));
    expect(diff.status, 'modified');
  });

  test('generated project response maps to app model with location', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      Uri? requestUri;
      server.listen((request) async {
        requestUri = request.uri;
        request.response.headers.contentType = ContentType.json;
        request.response.write(
          jsonEncode([
            {
              'id': 'project-1',
              'worktree': '/work/acme',
              'name': 'Acme',
              'time': {'created': 1000, 'updated': 2000},
              'sandboxes': ['/work/acme-sandbox'],
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

        final projects = await repository.listProjects();

        expect(projects.single.id, 'project-1');
        expect(projects.single.name, 'Acme');
        expect(projects.single.directory, '/work/acme');
        expect(projects.single.worktrees, ['/work/acme-sandbox']);
        expect(projects.single.updatedAt, 2000);
        expect(requestUri?.path, '/project');
        expect(requestUri?.queryParameters['directory'], '/work/acme');
        expect(requestUri?.queryParameters['workspace'], 'workspace-1');
      } finally {
        await server.close(force: true);
      }
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });

  test(
    'saved-location validation uses generated current-project truth',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        Uri? requestUri;
        server.listen((request) async {
          requestUri = request.uri;
          request.response.headers.contentType = ContentType.json;
          request.response.write(
            jsonEncode({
              'id': 'project-1',
              'worktree': '/work/acme',
              'name': 'Acme',
              'time': {'created': 1000, 'updated': 2000},
              'sandboxes': ['/work/acme-proof'],
            }),
          );
          await request.response.close();
        });

        try {
          final api = OpenCodeApi(
            baseUrl: 'http://${server.address.host}:${server.port}',
          );
          final repository = SdkProductRepository(api.sdkClient)
            ..setLocation(
              directory: '/work/acme-proof',
              workspace: 'workspace-1',
            );

          final project = await repository.loadCurrentProject();

          expect(requestUri?.path, '/project/current');
          expect(requestUri?.queryParameters, {
            'directory': '/work/acme-proof',
            'workspace': 'workspace-1',
          });
          expect(project?.id, 'project-1');
          expect(project?.directory, '/work/acme');
          expect(project?.worktrees, ['/work/acme-proof']);
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
    },
  );

  test(
    'worktree lifecycle uses the primary project context and exact bodies',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        final requests = <({String method, Uri uri, String body})>[];
        server.listen((request) async {
          final body = await utf8.decoder.bind(request).join();
          requests.add((method: request.method, uri: request.uri, body: body));
          request.response.headers.contentType = ContentType.json;
          if (request.uri.path == '/vcs/status') {
            request.response.write(
              jsonEncode([
                {
                  'file': 'lib/main.dart',
                  'status': 'modified',
                  'additions': 4,
                  'deletions': 1,
                },
              ]),
            );
          } else if (request.uri.path == '/project/project-1/directories') {
            request.response.write(
              jsonEncode([
                {
                  'directory': '/data/worktree/project-1/mobile-review',
                  'strategy': 'git_worktree',
                },
                {'directory': '/work/app'},
              ]),
            );
          } else if (request.method == 'GET') {
            request.response.write(
              jsonEncode([
                '/stale/alias/mobile-review',
                '/data/worktree/project-1/mobile-review',
              ]),
            );
          } else if (request.method == 'POST' &&
              request.uri.path == '/experimental/worktree') {
            request.response.write(
              jsonEncode({
                'name': 'mobile-review',
                'branch': 'opencode/mobile-review',
                'directory': '/data/worktree/project-1/mobile-review',
              }),
            );
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
            ..setLocation(
              directory: '/data/worktree/project-1/selected',
              workspace: 'phone',
            );

          final listed = await repository.listWorktrees(
            projectDirectory: '/work/app',
            projectID: 'project-1',
          );
          final created = await repository.createWorktree(
            projectDirectory: '/work/app',
            name: '  mobile review  ',
          );
          final statuses = await repository.listWorktreeFileStatuses(
            created.directory,
          );
          await repository.resetWorktree(
            projectDirectory: '/work/app',
            directory: created.directory,
          );
          await repository.removeWorktree(
            projectDirectory: '/work/app',
            directory: created.directory,
          );

          expect(listed.single.name, 'mobile-review');
          expect(created.branch, 'opencode/mobile-review');
          expect(statuses.single.path, 'lib/main.dart');
          expect(requests.map((request) => request.method), [
            'GET',
            'GET',
            'POST',
            'GET',
            'POST',
            'DELETE',
          ]);
          expect(requests.map((request) => request.uri.path), [
            '/experimental/worktree',
            '/project/project-1/directories',
            '/experimental/worktree',
            '/vcs/status',
            '/experimental/worktree/reset',
            '/experimental/worktree',
          ]);
          for (final request in [
            requests[0],
            requests[1],
            requests[2],
            requests[4],
            requests[5],
          ]) {
            expect(request.uri.queryParameters, {
              'directory': '/work/app',
              'workspace': 'phone',
            });
          }
          expect(requests[3].uri.queryParameters, {
            'directory': '/data/worktree/project-1/mobile-review',
            'workspace': 'phone',
          });
          expect(jsonDecode(requests[2].body), {'name': 'mobile review'});
          expect(jsonDecode(requests[4].body), {
            'directory': '/data/worktree/project-1/mobile-review',
          });
          expect(jsonDecode(requests[5].body), {
            'directory': '/data/worktree/project-1/mobile-review',
          });
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
    },
  );

  test(
    'stale project directory metadata cannot resurrect a worktree',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        server.listen((request) async {
          request.response.headers.contentType = ContentType.json;
          if (request.uri.path == '/experimental/worktree') {
            request.response.write('[]');
          } else if (request.uri.path == '/project/project-1/directories') {
            request.response.write(
              jsonEncode([
                {
                  'directory': '/stale/worktree/mobile-review',
                  'strategy': 'git_worktree',
                },
              ]),
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
          final repository = SdkProductRepository(api.sdkClient);

          final listed = await repository.listWorktrees(
            projectDirectory: '/work/app',
            projectID: 'project-1',
          );

          expect(listed, isEmpty);
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
    },
  );

  test('worktree errors surface the typed OpenCode message', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      server.listen((request) async {
        request.response.statusCode = HttpStatus.badRequest;
        request.response.headers.contentType = ContentType.json;
        request.response.write(
          jsonEncode({
            'name': 'WorktreeNotGitError',
            'data': {
              'message': 'Worktrees are only supported for git projects',
            },
          }),
        );
        await request.response.close();
      });

      try {
        final api = OpenCodeApi(
          baseUrl: 'http://${server.address.host}:${server.port}',
        );
        final repository = SdkProductRepository(api.sdkClient);

        await expectLater(
          repository.listWorktrees(projectDirectory: '/work/plain'),
          throwsA(
            isA<ProductException>().having(
              (error) => error.message,
              'message',
              'Worktrees are only supported for git projects',
            ),
          ),
        );
      } finally {
        await server.close(force: true);
      }
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });

  test(
    'global session finder uses generated server search and cursor contract',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        Uri? requestUri;
        server.listen((request) async {
          requestUri = request.uri;
          request.response.headers.contentType = ContentType.json;
          request.response.write(
            jsonEncode([
              {
                'id': 'ses_global_1',
                'slug': 'brisk-river',
                'projectID': 'project-2',
                'workspaceID': 'workspace-2',
                'directory': '/work/second',
                'path': 'packages/mobile',
                'title': 'Repair Android wake cycle',
                'version': '1.18.23',
                'share': {'url': 'https://example.test/share/1'},
                'time': {
                  'created': 1700000000000,
                  'updated': 1700000003000,
                  'archived': 1700000004000,
                },
                'project': {
                  'id': 'project-2',
                  'name': 'Second project',
                  'worktree': '/work/second',
                },
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
            ..setLocation(directory: '/work/current', workspace: 'current');

          final results = await repository.listGlobalSessions(
            search: '  wake  ',
            includeArchived: true,
            cursor: 1700000005000,
            limit: 37,
          );

          expect(requestUri?.path, '/experimental/session');
          expect(requestUri?.queryParameters, {
            'roots': 'true',
            'cursor': '1700000005000',
            'search': 'wake',
            'limit': '37',
            'archived': 'true',
          });
          final result = results.single;
          expect(result.session.id, 'ses_global_1');
          expect(result.session.workspaceID, 'workspace-2');
          expect(result.session.directory, '/work/second');
          expect(result.session.path, 'packages/mobile');
          expect(result.session.archived, isTrue);
          expect(result.session.shareUrl, 'https://example.test/share/1');
          expect(result.projectName, 'Second project');
          expect(result.projectDirectory, '/work/second');
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
    },
  );

  test('subagent navigation uses exact generated session contracts', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final requests = <Uri>[];
      Map<String, dynamic> session(
        String id, {
        String? parentID,
        required int created,
      }) => {
        'id': id,
        'slug': id,
        'projectID': 'project-1',
        'workspaceID': 'workspace-1',
        'directory': '/work/acme',
        'parentID': ?parentID,
        'title': id == 'parent' ? 'Primary work' : 'Delegated $id',
        'version': '1.18.23',
        'time': {'created': created, 'updated': created + 1},
      };

      server.listen((request) async {
        requests.add(request.uri);
        request.response.headers.contentType = ContentType.json;
        if (request.uri.path == '/session/child-2') {
          request.response.write(
            jsonEncode(session('child-2', parentID: 'parent', created: 30)),
          );
        } else if (request.uri.path == '/session/parent/children') {
          request.response.write(
            jsonEncode([
              session('child-2', parentID: 'parent', created: 30),
              session('child-1', parentID: 'parent', created: 20),
              session('unrelated', parentID: 'other', created: 10),
            ]),
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
          ..setLocation(directory: '/work/acme', workspace: 'workspace-1');

        final child = await repository.getSessionDetails('child-2');
        final children = await repository.listSessionChildren('parent');

        expect(child.parentID, 'parent');
        expect(children.map((session) => session.id), ['child-1', 'child-2']);
        expect(requests.map((uri) => uri.path), [
          '/session/child-2',
          '/session/parent/children',
        ]);
        for (final request in requests) {
          expect(request.queryParameters, {
            'directory': '/work/acme',
            'workspace': 'workspace-1',
          });
        }
      } finally {
        await server.close(force: true);
      }
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });

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
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
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
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
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
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
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
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
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
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
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
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
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
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
    },
  );

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
            'POST',
            'POST',
          ]);
          expect(requests.map((request) => request.uri.path), [
            '/api/integration/zai-coding-plan/connect/key',
            '/auth/zai-coding-plan',
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
          expect(requests[2].uri.queryParameters, {
            'directory': '/root',
            'workspace': 'phone',
          });
          expect(requests[3].uri.queryParameters, isEmpty);
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
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
            'POST',
            'POST',
          ]);
          expect(requests.map((request) => request.uri.path), [
            '/auth/zai-coding-plan',
            '/api/credential/credential%2Fphone%20key',
            '/instance/dispose',
            '/instance/dispose',
          ]);
          expect(requests[1].uri.queryParameters, {
            'location[directory]': '/root',
            'location[workspace]': 'phone',
          });
          expect(requests[2].uri.queryParameters, {
            'directory': '/root',
            'workspace': 'phone',
          });
          expect(requests[3].uri.queryParameters, isEmpty);
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
    },
  );

  test(
    'integration listing retains exact credential and environment identities',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        server.listen((request) async {
          request.response.headers.contentType = ContentType.json;
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
              ],
            }),
          );
          await request.response.close();
        });

        try {
          final api = OpenCodeApi(
            baseUrl: 'http://${server.address.host}:${server.port}',
          );
          final repository = SdkProductRepository(api.sdkClient)
            ..setLocation(directory: '/root', workspace: 'phone');

          final integration = (await repository.listIntegrations()).single;

          expect(integration.id, 'cloud');
          expect(integration.connectionCount, 2);
          expect(integration.credentialIDs, ['credential-1']);
          expect(integration.hasEnvironmentConnection, isTrue);
          expect(
            integration.connections.map((connection) => connection.label),
            ['Phone key', 'CLOUD_TOKEN'],
          );
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
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
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
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
            '/instance/dispose',
            '/instance/dispose',
          ]);
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
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
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
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
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
    },
  );

  test(
    'provider OAuth keeps and completes the server attempt contract',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        final requests = <({String method, Uri uri, String body})>[];
        server.listen((request) async {
          final body = await utf8.decoder.bind(request).join();
          requests.add((method: request.method, uri: request.uri, body: body));
          request.response.headers.contentType = ContentType.json;
          final location = {
            'directory': '/root',
            'workspaceID': 'phone',
            'project': {'id': 'project-1', 'directory': '/root'},
          };
          if (request.method == 'POST' &&
              request.uri.path == '/api/integration/cloud/connect/oauth') {
            request.response.write(
              jsonEncode({
                'location': location,
                'data': {
                  'attemptID': 'attempt-1',
                  'url': 'https://auth.example.com/authorize',
                  'instructions': 'Paste the returned code',
                  'mode': 'code',
                  'time': {'created': 100, 'expires': 999},
                },
              }),
            );
          } else if (request.method == 'GET') {
            request.response.write(
              jsonEncode({
                'location': location,
                'data': {
                  'status': 'complete',
                  'time': {'created': 100, 'expires': 999},
                },
              }),
            );
          } else {
            request.response.statusCode = HttpStatus.noContent;
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
            'oauth-1',
            inputs: const {'tenant': 'acme'},
            label: 'Work',
          );
          final status = await repository.integrationOAuthStatus(
            launch.attemptID,
          );
          await repository.completeIntegrationOAuth(
            launch.attemptID,
            code: 'returned-code',
          );
          await repository.cancelIntegrationOAuth(launch.attemptID);

          expect(launch.attemptID, 'attempt-1');
          expect(launch.mode, IntegrationAuthMode.code);
          expect(launch.expiresAt, 999);
          expect(status.state, IntegrationAuthState.complete);
          expect(status.expiresAt, 999);
          expect(requests.map((request) => request.method), [
            'POST',
            'GET',
            'POST',
            'DELETE',
          ]);
          expect(requests.map((request) => request.uri.path), [
            '/api/integration/cloud/connect/oauth',
            '/api/integration/attempt/attempt-1',
            '/api/integration/attempt/attempt-1/complete',
            '/api/integration/attempt/attempt-1',
          ]);
          expect(jsonDecode(requests[0].body), {
            'methodID': 'oauth-1',
            'inputs': {'tenant': 'acme'},
            'label': 'Work',
          });
          expect(jsonDecode(requests[2].body), {'code': 'returned-code'});
          for (final request in requests) {
            expect(request.uri.queryParameters, {
              'location[directory]': '/root',
              'location[workspace]': 'phone',
            });
          }
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
    },
  );

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
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
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
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });

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
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
    },
  );

  test('fork session sends the selected OpenCode message point', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      String? body;
      Uri? uri;
      server.listen((request) async {
        uri = request.uri;
        body = await utf8.decoder.bind(request).join();
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
        expect(uri?.path, '/session/session-1/fork');
        expect(uri?.queryParameters, {
          'directory': '/work/acme',
          'workspace': 'phone',
        });
        expect(jsonDecode(body!), {'messageID': 'message-7'});
      } finally {
        await server.close(force: true);
      }
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
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
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
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
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
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
          socket.add('ready');
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

          expect(await output, 'ready');
          expect(await receivedInput.future, 'pwd\r');
          expect(tokenHeader, '1');
          expect(tokenDirectory, '/work/acme');
          expect(socketDirectory, '/work/acme');
          expect(socketCursor, '17');
          expect(channel.cursor, 47);
          await channel.close();
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
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
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
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
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });
}
