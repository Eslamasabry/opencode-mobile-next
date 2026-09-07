import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/client.dart';
import 'package:opencode_mobile/api2/gateway_operations.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';

import 'api2_interaction_gateway_test.dart' show withServer, writeJson;

void main() {
  test(
    'v2 creates a location-scoped owned shell and retains only ownership metadata',
    () async {
      await withServer(
        handler: (request) => writeJson(request, {
          'data': {
            'id': 'sh_test',
            'command': 'npm run dev',
            'cwd': '/work/shopfront',
            'status': 'running',
            'time': {'started': 1234},
            'metadata': {
              'ocDevelopmentServiceOwner': 'owner',
              'unrelated': 'not mapped',
            },
          },
        }),
        (server, requests) async {
          final client = Api2Client.connect(
            baseUrl: 'http://${server.address.host}:${server.port}',
            password: 'synthetic',
            directory: '/work/shopfront',
            workspace: 'ws_preview',
          );
          addTearDown(client.close);
          final gateway = Api2OperationsGateway(client: client);
          final shell = await gateway.startManagedShell(
            command: 'npm run dev',
            directory: '/work/shopfront',
            ownerToken: 'owner',
          );
          expect(shell.ownerToken, 'owner');
          expect(shell.directory, '/work/shopfront');
          expect(shell.startedAt.millisecondsSinceEpoch, 1234);
          final request = requests.single;
          expect(request.method, 'POST');
          expect(request.uri.path, '/api/shell');
          expect(
            request.uri.queryParameters['location[directory]'],
            '/work/shopfront',
          );
          expect(
            request.uri.queryParameters['location[workspace]'],
            'ws_preview',
          );
          expect(request.body, {
            'command': 'npm run dev',
            'cwd': '/work/shopfront',
            'timeout': 0,
            'metadata': {'ocDevelopmentServiceOwner': 'owner'},
          });
          expect(api2ServerCapabilities.developmentServices, isTrue);
          expect(ServerCapabilities.allV1.developmentServices, isFalse);
        },
      );
    },
  );
}
