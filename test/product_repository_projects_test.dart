import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'support/product_repository_fixtures.dart';

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

  test('explicit empty connected list does not expose catalog-only models', () {
    final response = ProvidersResponse.fromJson({
      'all': [
        {
          'id': 'openai',
          'name': 'OpenAI',
          'models': {'gpt-5.6-sol': <String, Object?>{}},
        },
      ],
      'connected': <String>[],
      'default': {'openai': 'gpt-5.6-sol'},
    });

    expect(response.providers, isEmpty);
    expect(response.availableProviders.single.id, 'openai');
    expect(response.defaultProviderID, isNull);
    expect(response.defaultModelID, isNull);
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
      }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
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
    }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
  });

  test('project rename uses generated project-root patch contract', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      late String method;
      late Uri uri;
      late Object? body;
      server.listen((request) async {
        method = request.method;
        uri = request.uri;
        final text = await utf8.decoder.bind(request).join();
        body = text.isEmpty ? null : jsonDecode(text);
        request.response.headers.contentType = ContentType.json;
        request.response.write(
          jsonEncode({
            'id': 'project/phone',
            'worktree': '/work/acme',
            'name': 'Phone workspace',
            'time': {'created': 1000, 'updated': 3000},
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
            directory: '/remote/active',
            workspace: 'workspace-active',
          );

        final updated = await repository.renameProject(
          projectID: ' project/phone ',
          projectDirectory: ' /work/acme ',
          name: ' Phone workspace ',
        );

        expect(updated.id, 'project/phone');
        expect(updated.name, 'Phone workspace');
        expect(updated.directory, '/work/acme');
        expect(method, 'PATCH');
        expect(uri.path, '/project/project%2Fphone');
        expect(uri.queryParameters, {'directory': '/work/acme'});
        expect(uri.queryParameters.containsKey('workspace'), isFalse);
        expect(body, {'name': 'Phone workspace'});
      } finally {
        await server.close(force: true);
      }
    }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
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
      }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
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
      }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
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
      }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
    },
  );

  test('worktree errors keep server prose in the technical cause', () async {
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
            isA<ProductException>()
                .having(
                  (error) => error.message,
                  'message',
                  'Could not load worktrees',
                )
                .having(
                  (error) => error.cause.toString().contains(
                    'Worktrees are only supported for git projects',
                  ),
                  'original cause preserved',
                  isTrue,
                ),
          ),
        );
      } finally {
        await server.close(force: true);
      }
    }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
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
          if (!request.uri.queryParameters.containsKey('cursor')) {
            request.response.headers.set('X-Next-Cursor', '1700000003000');
          }
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

          await expectLater(
            repository.listGlobalSessions(cursor: 'not-a-v1-token'),
            throwsA(isA<ProductException>()),
          );
          expect(requestUri, isNull);
          final first = await repository.listGlobalSessions(
            search: '  wake  ',
            includeArchived: true,
            limit: 37,
          );
          expect(first.nextCursor, '1700000003000');
          final results = await repository.listGlobalSessions(
            search: '  wake  ',
            includeArchived: true,
            cursor: first.nextCursor,
            limit: 37,
          );

          expect(requestUri?.path, '/experimental/session');
          expect(requestUri?.queryParameters, {
            'roots': 'true',
            'cursor': '1700000003000',
            'search': 'wake',
            'limit': '37',
            'archived': 'true',
          });
          final result = results.items.single;
          expect(results.hasMore, isFalse);
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
      }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
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
    }, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));
  });
}
