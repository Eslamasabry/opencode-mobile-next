import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';

import 'support/product_repository_fixtures.dart';

typedef _Request = ({String method, Uri uri, Object? body});

Future<void> _withGateway({
  Map<String, Object?> inventory = const {},
  void Function()? beforeResponse,
  required Future<void> Function(
    SdkProductRepository repository,
    List<_Request> requests,
  )
  run,
}) => HttpOverrides.runZoned(() async {
  final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
  final requests = <_Request>[];
  server.listen((request) async {
    final text = await utf8.decoder.bind(request).join();
    requests.add((
      method: request.method,
      uri: request.uri,
      body: text.isEmpty ? null : jsonDecode(text),
    ));
    beforeResponse?.call();
    request.response.headers.contentType = ContentType.json;
    request.response.write(
      jsonEncode(
        request.method == 'GET'
            ? inventory
            : {
                'chat-docs': {'status': 'connected'},
              },
      ),
    );
    await request.response.close();
  });
  final api = OpenCodeApi(
    baseUrl: 'http://${server.address.host}:${server.port}',
  );
  final repository = SdkProductRepository(api.sdkClient)
    ..setLocation(directory: '/work/chat', workspace: 'workspace-chat');
  try {
    await run(repository, requests);
  } finally {
    api.close();
    await server.close(force: true);
  }
}, createHttpClient: (_) => RealHttpOverrides().createHttpClient(null));

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const draft = McpServerDraft(
    name: ' chat-docs ',
    kind: McpServerKind.remote,
    url: 'https://docs.example.test/mcp',
    timeoutMs: 12000,
  );

  test('chat connector uses runtime add at the current location', () async {
    await _withGateway(
      run: (repository, requests) async {
        await repository.addMcpServer(
          draft,
          scope: McpConfigScope.runtimeLocation,
        );

        expect(requests.map((request) => request.method), ['GET', 'POST']);
        expect(requests.map((request) => request.uri.path), ['/mcp', '/mcp']);
        for (final request in requests) {
          expect(request.uri.queryParameters, {
            'directory': '/work/chat',
            'workspace': 'workspace-chat',
          });
        }
        expect(requests.last.body, {
          'name': 'chat-docs',
          'config': {
            'type': 'remote',
            'url': 'https://docs.example.test/mcp',
            'timeout': 12000,
          },
        });
      },
    );
  });

  test('chat connector cannot replace an existing runtime name', () async {
    await _withGateway(
      inventory: const {
        'chat-docs': {'status': 'disabled'},
      },
      run: (repository, requests) async {
        await expectLater(
          repository.addMcpServer(draft, scope: McpConfigScope.runtimeLocation),
          throwsA(
            isA<ProductException>().having(
              (error) => error.message,
              'message',
              contains('already exists'),
            ),
          ),
        );
        expect(requests, hasLength(1));
        expect(requests.single.method, 'GET');
        expect(requests.single.uri.path, '/mcp');
      },
    );
  });

  test(
    'invalid chat connector is rejected before contacting the server',
    () async {
      await _withGateway(
        run: (repository, requests) async {
          await expectLater(
            repository.addMcpServer(
              const McpServerDraft(
                name: '',
                kind: McpServerKind.remote,
                url: 'https://docs.example.test/mcp',
              ),
              scope: McpConfigScope.runtimeLocation,
            ),
            throwsA(isA<ProductException>()),
          );
          expect(requests, isEmpty);
        },
      );
    },
  );

  test(
    'changing location during inventory read prevents runtime mutation',
    () async {
      SdkProductRepository? currentRepository;
      await _withGateway(
        beforeResponse: () => currentRepository?.setLocation(
          directory: '/work/other',
          workspace: 'workspace-other',
        ),
        run: (repository, requests) async {
          currentRepository = repository;
          await expectLater(
            repository.addMcpServer(
              draft,
              scope: McpConfigScope.runtimeLocation,
            ),
            throwsA(
              isA<ProductException>().having(
                (error) => error.message,
                'message',
                contains('connection changed'),
              ),
            ),
          );
          expect(requests, hasLength(1));
          expect(requests.single.method, 'GET');
          expect(requests.single.uri.queryParameters, {
            'directory': '/work/chat',
            'workspace': 'workspace-chat',
          });
        },
      );
    },
  );
}
