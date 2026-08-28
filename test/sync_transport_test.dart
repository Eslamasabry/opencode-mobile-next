import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';

class _RealHttpOverrides extends HttpOverrides {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('workspace sync start uses the generated location-scoped contract', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      late String method;
      late Uri uri;
      var sawBody = false;
      server.listen((request) async {
        method = request.method;
        uri = request.uri;
        final body = await utf8.decoder.bind(request).join();
        sawBody = body.isNotEmpty;
        request.response.headers.contentType = ContentType.json;
        request.response.write(jsonEncode(true));
        await request.response.close();
      });

      final api = OpenCodeApi(
        baseUrl: 'http://${server.address.host}:${server.port}',
      );
      try {
        final repository = SdkProductRepository(api.sdkClient)
          ..setLocation(directory: '/work/acme', workspace: 'workspace-1');

        final started = await repository.startWorkspaceSync();

        expect(method, 'POST');
        expect(uri.path, '/sync/start');
        expect(uri.queryParameters, {
          'directory': '/work/acme',
          'workspace': 'workspace-1',
        });
        expect(sawBody, isFalse);
        expect(started, isTrue);
      } finally {
        api.close();
        await server.close(force: true);
      }
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });

  test('session steal posts the exact generated body and returns the '
      'confirmed session', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      late String method;
      late Uri uri;
      Object? body;
      server.listen((request) async {
        method = request.method;
        uri = request.uri;
        body = jsonDecode(await utf8.decoder.bind(request).join());
        request.response.headers.contentType = ContentType.json;
        request.response.write(jsonEncode({'sessionID': 'session-7'}));
        await request.response.close();
      });

      final api = OpenCodeApi(
        baseUrl: 'http://${server.address.host}:${server.port}',
      );
      try {
        final repository = SdkProductRepository(api.sdkClient)
          ..setLocation(directory: '/work/acme', workspace: 'workspace-1');

        final stolen = await repository.stealSessionIntoWorkspace('session-7');

        expect(method, 'POST');
        expect(uri.path, '/sync/steal');
        expect(uri.queryParameters, {
          'directory': '/work/acme',
          'workspace': 'workspace-1',
        });
        expect(body, {'sessionID': 'session-7'});
        expect(stolen, 'session-7');
      } finally {
        api.close();
        await server.close(force: true);
      }
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });

  test('declared steal errors surface OpenCode detail as the product message',
      () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      server.listen((request) async {
        await request.drain<void>();
        request.response.statusCode = HttpStatus.badRequest;
        request.response.headers.contentType = ContentType.json;
        request.response.write(
          jsonEncode({
            '_tag': 'InvalidRequestError',
            'requestID': 'request-steal-1',
            'message': 'session belongs to another project',
          }),
        );
        await request.response.close();
      });

      final api = OpenCodeApi(
        baseUrl: 'http://${server.address.host}:${server.port}',
      );
      try {
        final repository = SdkProductRepository(api.sdkClient)
          ..setLocation(directory: '/work/acme', workspace: 'workspace-1');

        await expectLater(
          repository.stealSessionIntoWorkspace('session-7'),
          throwsA(
            isA<ProductException>().having(
              (error) => error.message,
              'message',
              'session belongs to another project',
            ),
          ),
        );
      } finally {
        api.close();
        await server.close(force: true);
      }
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });

  test('an old server without sync keeps the bounded product error', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      server.listen((request) async {
        await request.drain<void>();
        request.response.statusCode = HttpStatus.notFound;
        await request.response.close();
      });

      final api = OpenCodeApi(
        baseUrl: 'http://${server.address.host}:${server.port}',
      );
      try {
        final repository = SdkProductRepository(api.sdkClient);

        await expectLater(
          repository.stealSessionIntoWorkspace('session-7'),
          throwsA(
            isA<ProductException>().having(
              (error) => error.message,
              'message',
              'Could not steal the session into this workspace',
            ),
          ),
        );
      } finally {
        api.close();
        await server.close(force: true);
      }
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });
}
