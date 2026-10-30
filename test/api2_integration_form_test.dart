import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/client.dart';
import 'package:opencode_mobile/api2/gateway_operations.dart';

/// OpenCode 2.1.1's real `opencode` integration (captured 2026-10-21): the
/// Console account sign-in has one hidden form field with a default. The app
/// must not ask it (it showed an empty "server" box and refused with "Select
/// an option"), and must still send its default.
void main() {
  test(
    'hidden OpenCode 2 form fields become preset answers, others prompts',
    () async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      addTearDown(() => server.close(force: true));
      server.listen((request) async {
        request.response.headers.contentType = ContentType.json;
        request.response.write(
          jsonEncode({
            'data': [
              {
                'id': 'opencode',
                'name': 'OpenCode Console',
                'methods': [
                  {'type': 'key', 'label': 'API key (service account)'},
                  {
                    'id': 'device',
                    'type': 'oauth',
                    'label': 'OpenCode Console account',
                    'form': [
                      {
                        'key': 'server',
                        'hidden': true,
                        'type': 'string',
                        'format': 'uri',
                        'default': 'https://opencode.ai/console',
                      },
                    ],
                  },
                ],
                'connections': <Object>[],
              },
              {
                'id': 'google-vertex',
                'name': 'Vertex',
                'methods': [
                  {
                    'id': 'google-adc',
                    'type': 'external',
                    'label': 'Google Cloud credentials',
                    'form': [
                      {
                        'key': 'location',
                        'title': 'Location',
                        'required': true,
                        'type': 'string',
                        'default': 'global',
                        'options': [
                          {'value': 'global', 'label': 'global'},
                          {'value': 'us', 'label': 'us'},
                        ],
                        'custom': true,
                      },
                      {
                        'key': 'tier',
                        'title': 'Tier',
                        'type': 'string',
                        'options': [
                          {'value': 'a', 'label': 'A'},
                        ],
                      },
                    ],
                  },
                ],
                'connections': <Object>[],
              },
            ],
          }),
        );
        await request.response.close();
      });
      final client = Api2Client.connect(
        baseUrl: 'http://${server.address.host}:${server.port}',
        password: 'fixture',
      );
      addTearDown(client.close);

      final integrations = await Api2OperationsGateway(
        client: client,
      ).listIntegrations();

      final console = integrations.firstWhere((i) => i.id == 'opencode');
      final device = console.methods.firstWhere((m) => m.id == 'device');
      expect(device.prompts, isEmpty, reason: 'a hidden field is never asked');
      expect(device.presetAnswers, {'server': 'https://opencode.ai/console'});

      final vertex = integrations.firstWhere((i) => i.id == 'google-vertex');
      final adc = vertex.methods.single;
      final location = adc.prompts.firstWhere((p) => p['key'] == 'location');
      expect(location['type'], 'text', reason: 'custom answers allowed: typed');
      expect(location['message'], 'Location');
      expect(location['default'], 'global');
      expect(location['required'], isTrue);
      final tier = adc.prompts.firstWhere((p) => p['key'] == 'tier');
      expect(tier['type'], 'select');
      expect(tier['required'], isFalse);
      expect(adc.presetAnswers, isEmpty);
    },
  );
}
