import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/diagnostics/perf_trace.dart';

import '../../integration_test/bd9_device_smoke_fixture.dart';

void main() {
  late Bd9DeviceSmokeFixture fixture;

  setUp(() async {
    PerfTrace.logSink = null;
    fixture = await Bd9DeviceSmokeFixture.start();
  });
  tearDown(() => fixture.close());

  test(
    'real API and SDK parse health and the global conversation inventory',
    () async {
      final api = OpenCodeApi(baseUrl: fixture.baseUrl);
      addTearDown(api.close);
      expect((await api.health()).healthy, isTrue);
      final sessions = await api.sessions();
      expect(sessions.single.title, Bd9DeviceSmokeFixture.title);
      final repository = SdkProductRepository(api.sdkClient);
      final global = await repository.listGlobalSessions();
      expect(global.items.single.session.id, Bd9DeviceSmokeFixture.sessionID);
      expect(global.items.single.session.title, Bd9DeviceSmokeFixture.title);
      expect(
        global.items.single.projectDirectory,
        Bd9DeviceSmokeFixture.directory,
      );
      expect(
        fixture.reads,
        containsAll(['/global/health', '/session', '/experimental/session']),
      );
      expect(fixture.refusedWrites, isEmpty);
    },
  );

  test(
    'fixture refuses model dispatch and records it without response content',
    () async {
      final client = HttpClient();
      addTearDown(() => client.close(force: true));
      final request = await client.postUrl(
        Uri.parse('${fixture.baseUrl}/session/model/prompt'),
      );
      request.write('{"prompt":"test-only"}');
      final response = await request.close();
      expect(response.statusCode, HttpStatus.methodNotAllowed);
      expect(jsonDecode(await utf8.decoder.bind(response).join()), {
        'error': 'read_only_fixture',
      });
      expect(fixture.refusedWrites, {'/session/model/prompt'});
    },
  );

  test(
    'unsupported read paths fail rather than claim fake protocol support',
    () async {
      final client = HttpClient();
      addTearDown(() => client.close(force: true));
      final request = await client.getUrl(
        Uri.parse('${fixture.baseUrl}/unknown'),
      );
      final response = await request.close();
      expect(response.statusCode, HttpStatus.notFound);
      await response.drain<void>();
      expect(fixture.unknownReads, {'/unknown'});
    },
  );
}
