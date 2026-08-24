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

  test('prompt request serializes OpenCode file parts', () {
    final body = promptRequestBody(
      text: 'Review this',
      attachments: const [
        PromptAttachment(
          mime: 'text/plain',
          filename: 'notes.txt',
          url: 'data:text/plain;base64,bm90ZXM=',
        ),
      ],
    );

    expect(body['parts'], [
      {'type': 'text', 'text': 'Review this'},
      {
        'type': 'file',
        'mime': 'text/plain',
        'filename': 'notes.txt',
        'url': 'data:text/plain;base64,bm90ZXM=',
      },
    ]);
  });

  test('command request serializes model as generated contract string', () {
    final body = commandRequestBody(
      'review',
      '--staged',
      model: ModelRef(providerID: 'anthropic', modelID: 'claude-sonnet'),
    );

    expect(body, {
      'command': 'review',
      'arguments': '--staged',
      'model': 'anthropic/claude-sonnet',
    });
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

  test('catalog accepts the current v2 capability response shape', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final locations = <String?>[];
      server.listen((request) async {
        locations.add(request.uri.queryParameters['location[directory]']);
        final data = switch (request.uri.path) {
          '/api/provider' => [
            {'id': 'opencode', 'name': 'OpenCode Zen'},
          ],
          '/api/model' => [
            {
              'id': 'model-1',
              'providerID': 'opencode',
              'name': 'Current model',
              'capabilities': {
                'tools': true,
                'input': ['text', 'image'],
                'output': ['text'],
              },
              'variants': [],
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
            },
          ],
          _ => <Object>[],
        };
        request.response.headers.contentType = ContentType.json;
        request.response.write(jsonEncode({'data': data}));
        await request.response.close();
      });

      try {
        final api = OpenCodeApi(
          baseUrl: 'http://${server.address.host}:${server.port}',
        );
        final repository = SdkProductRepository(api.sdkClient)
          ..setLocation(directory: '/work/acme');
        final catalog = await repository.loadCatalog();

        expect(catalog.providers.single.name, 'OpenCode Zen');
        expect(catalog.models.single.tools, isTrue);
        expect(catalog.models.single.attachments, isTrue);
        expect(catalog.models.single.contextLimit, 200000);
        expect(catalog.agents.single.id, 'build');
        expect(locations, everyElement('/work/acme'));
      } finally {
        await server.close(force: true);
      }
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });

  test(
    'terminal connection requests a guarded ticket before WebSocket',
    () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        final receivedInput = Completer<String>();
        String? tokenHeader;
        String? tokenDirectory;
        String? socketDirectory;
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
          expect(request.uri.queryParameters['ticket'], 'single-use-ticket');
          final socket = await WebSocketTransformer.upgrade(request);
          socket.listen((data) {
            if (!receivedInput.isCompleted) {
              receivedInput.complete(data as String);
            }
          });
          socket.add('ready');
        });

        try {
          final api = OpenCodeApi(
            baseUrl: 'http://${server.address.host}:${server.port}',
          );
          final repository = SdkProductRepository(api.sdkClient)
            ..setLocation(directory: '/work/acme');
          final channel = await repository.connectTerminal('pty_test');
          final output = channel.output.first;
          channel.write('pwd\r');

          expect(await output, 'ready');
          expect(await receivedInput.future, 'pwd\r');
          expect(tokenHeader, '1');
          expect(tokenDirectory, '/work/acme');
          expect(socketDirectory, '/work/acme');
          await channel.close();
        } finally {
          await server.close(force: true);
        }
      }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
    },
  );
}
