import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';

class _RealHttpOverrides extends HttpOverrides {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('message delete uses the generated location-scoped contract', () async {
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

        await repository.deleteMessage(
          sessionID: 'session-1',
          messageID: 'msg_123',
        );

        expect(method, 'DELETE');
        expect(uri.path, '/session/session-1/message/msg_123');
        expect(uri.queryParameters, {
          'directory': '/work/acme',
          'workspace': 'workspace-1',
        });
        expect(sawBody, isFalse);
      } finally {
        api.close();
        await server.close(force: true);
      }
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });

  test('declared message delete errors retain product details', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      server.listen((request) async {
        await request.drain<void>();
        request.response.statusCode = HttpStatus.badRequest;
        request.response.headers.contentType = ContentType.json;
        request.response.write(
          jsonEncode({
            '_tag': 'InvalidRequestError',
            'requestID': 'request-delete-1',
            'message': 'message is part of an active response',
          }),
        );
        await request.response.close();
      });

      final api = OpenCodeApi(
        baseUrl: 'http://${server.address.host}:${server.port}',
      );
      try {
        final repository = SdkProductRepository(api.sdkClient)
          ..setLocation(directory: '/work/acme');

        await expectLater(
          repository.deleteMessage(
            sessionID: 'session-1',
            messageID: 'msg_123',
          ),
          throwsA(
            isA<ProductException>().having(
              (error) => error.toString(),
              'message',
              contains('message is part of an active response'),
            ),
          ),
        );
      } finally {
        api.close();
        await server.close(force: true);
      }
    }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
  });

  test('a repository without message deletion stays a bounded error', () async {
    final repository = _MinimalRepository();
    await expectLater(
      repository.deleteMessage(sessionID: 's', messageID: 'm'),
      throwsA(
        isA<ProductException>().having(
          (error) => error.toString(),
          'message',
          contains('Message deletion is unavailable'),
        ),
      ),
    );
  });
}

class _MinimalRepository extends ProductRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}
