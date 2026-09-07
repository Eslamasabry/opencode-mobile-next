import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';

class _RealHttpOverrides extends HttpOverrides {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  for (final correlated in [false, true]) {
    test(
      'prompt uses the generated contract without wire drift (correlated: $correlated)',
      () async {
        await HttpOverrides.runZoned(
          () async {
            final server = await HttpServer.bind(
              InternetAddress.loopbackIPv4,
              0,
            );
            late String method;
            late Uri uri;
            late Object? body;
            server.listen((request) async {
              method = request.method;
              uri = request.uri;
              body = jsonDecode(await utf8.decoder.bind(request).join());
              request.response.statusCode = HttpStatus.noContent;
              await request.response.close();
            });

            final api = OpenCodeApi(
              baseUrl: 'http://${server.address.host}:${server.port}',
            )..setLocation(directory: '/work/acme', workspace: 'workspace-1');

            try {
              final messageID = correlated ? api.createPromptMessageID() : null;
              if (messageID != null) {
                expect(
                  messageID,
                  matches(RegExp(r'^msg_[0-9a-f]{12}[0-9A-Za-z]{14}$')),
                );
                expect(api.createPromptMessageID(), isNot(messageID));
              }
              Future<void> send({String? exactID}) => exactID == null
                  ? api.promptAsync(
                      'session-1',
                      text: 'Review these files',
                      model: ModelRef(
                        providerID: 'zai-coding-plan',
                        modelID: 'glm-5.2',
                      ),
                      agent: 'build',
                      variant: 'high',
                      agentMentions: const [
                        PromptAgentMention(
                          name: 'explore',
                          value: '@explore',
                          start: 7,
                          end: 15,
                        ),
                      ],
                      attachments: [
                        const PromptAttachment(
                          mime: 'text/plain',
                          filename: 'notes.txt',
                          url: 'data:text/plain;base64,bm90ZXM=',
                        ),
                        PromptAttachment.reference(
                          name: 'docs',
                          path: '/work/shared docs',
                        ),
                      ],
                    )
                  : api.promptWithMessageID(
                      'session-1',
                      messageID: exactID,
                      text: 'Review these files',
                      model: ModelRef(
                        providerID: 'zai-coding-plan',
                        modelID: 'glm-5.2',
                      ),
                      agent: 'build',
                      variant: 'high',
                      agentMentions: const [
                        PromptAgentMention(
                          name: 'explore',
                          value: '@explore',
                          start: 7,
                          end: 15,
                        ),
                      ],
                      attachments: [
                        const PromptAttachment(
                          mime: 'text/plain',
                          filename: 'notes.txt',
                          url: 'data:text/plain;base64,bm90ZXM=',
                        ),
                        PromptAttachment.reference(
                          name: 'docs',
                          path: '/work/shared docs',
                        ),
                      ],
                    );
              await send(exactID: messageID);

              expect(method, 'POST');
              expect(uri.path, '/session/session-1/prompt_async');
              expect(uri.queryParameters, {
                'directory': '/work/acme',
                'workspace': 'workspace-1',
              });
              expect(body, {
                'messageID': ?messageID,
                'model': {
                  'providerID': 'zai-coding-plan',
                  'modelID': 'glm-5.2',
                },
                'agent': 'build',
                'variant': 'high',
                'parts': [
                  {'type': 'text', 'text': 'Review these files'},
                  {
                    'type': 'agent',
                    'name': 'explore',
                    'source': {'value': '@explore', 'start': 7, 'end': 15},
                  },
                  {
                    'type': 'file',
                    'mime': 'text/plain',
                    'filename': 'notes.txt',
                    'url': 'data:text/plain;base64,bm90ZXM=',
                  },
                  {
                    'type': 'file',
                    'mime': PromptAttachment.directoryReferenceMime,
                    'filename': 'docs',
                    'url': 'file:///work/shared%20docs',
                  },
                ],
              });
            } finally {
              api.close();
              await server.close(force: true);
            }
          },
          createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null),
        );
      },
    );
  }

  test('declared prompt errors retain product-facing server details', () async {
    await HttpOverrides.runZoned(() async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      server.listen((request) async {
        await request.drain<void>();
        request.response.statusCode = HttpStatus.badRequest;
        request.response.headers.contentType = ContentType.json;
        request.response.write(
          jsonEncode({
            '_tag': 'InvalidRequestError',
            'requestID': 'request-1',
            'message': 'model is unavailable',
          }),
        );
        await request.response.close();
      });

      final api = OpenCodeApi(
        baseUrl: 'http://${server.address.host}:${server.port}',
      );
      try {
        await expectLater(
          api.promptAsync('session-1', text: 'Hello'),
          throwsA(
            isA<ApiException>()
                .having((error) => error.statusCode, 'statusCode', 400)
                .having(
                  (error) => error.errorTag,
                  'errorTag',
                  'InvalidRequestError',
                )
                .having((error) => error.requestID, 'requestID', 'request-1')
                .having(
                  (error) => error.message,
                  'message',
                  contains('model is unavailable'),
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
