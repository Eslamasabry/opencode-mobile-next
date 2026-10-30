import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'support/fake_server_gateway.dart';
import 'package:opencode_mobile/api2/client.dart';
import 'package:opencode_mobile/api2/gateway.dart';
import 'package:opencode_mobile/api2/gateway_operations.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/automatic_activity.dart';
import 'package:opencode_mobile/state/automation_policy.dart';
import 'package:opencode_mobile/orchestration/adapters/inapp/phone_engine_gateway.dart';

import 'phone_project_engine_controller_test.dart' show NativeBridge;
import 'phone_project_engine_gateway_test.dart' as engine;
import 'support/stash_memory_vault.dart';

class _ConnectingApi extends FakeServerGateway {
  _ConnectingApi() : super(baseUrl: 'http://127.0.0.1:4097');
  final healthGate = Completer<Health>();
  @override
  Future<Health> health() => healthGate.future;
}

class _ConnectingV2Api extends Api2Gateway {
  _ConnectingV2Api()
    : super(
        client: Api2Client.connect(
          baseUrl: 'http://127.0.0.1:4097',
          password: 'test-only',
        ),
      );
  final healthGate = Completer<Health>();
  @override
  Future<Health> health() => healthGate.future;
}

class _NoTeamApi extends _ConnectingApi {
  @override
  ServerCapabilities get capabilities => const ServerCapabilities();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final secrets = <String, String>{};
  late ProfileStore store;
  setUp(() async {
    addTearDown(AutomaticActivityController.resetShared);
    addTearDown(AutomationPolicyController.resetShared);
    SharedPreferences.setMockInitialValues({});
    secrets.clear();
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(
      const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
      (call) async {
        final args = call.arguments as Map? ?? {};
        final key = args['key'] as String?;
        switch (call.method) {
          case 'read':
            return secrets[key];
          case 'readAll':
            return Map<String, String>.of(secrets);
          case 'write':
            secrets[key!] = args['value'] as String;
            return null;
          case 'delete':
            secrets.remove(key);
            return null;
          case 'containsKey':
            return secrets.containsKey(key);
        }
        return null;
      },
    );
    messenger.setMockMethodCallHandler(
      const MethodChannel('oc/background'),
      (_) async => null,
    );
    store = ProfileStore(prefs: await SharedPreferences.getInstance());
    await store.upsert(
      ServerProfile(
        id: 'phone',
        name: 'Phone',
        baseUrl: 'http://127.0.0.1:4097',
      ),
    );
  });
  tearDown(() {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(
      const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
      null,
    );
    messenger.setMockMethodCallHandler(
      const MethodChannel('oc/background'),
      null,
    );
  });

  ConnectionController connection(
    NativeBridge bridge, {
    _ConnectingApi? api,
    _ConnectingV2Api? v2Api,
  }) {
    final controller = ConnectionController(
      store,
      phoneEngineBridge: bridge,
      phoneEngineGatewayBuilder:
          ({required baseUrl, required profileId, required bearerToken}) =>
              PhoneEngineGateway(
                baseUrl: baseUrl,
                profileId: profileId,
                bearerToken: bearerToken,
                adapter: engine.FakeEngineAdapter(
                  (request) async => engine.jsonBody(
                    request.path == '/v1/health'
                        ? engine.health(profileId)
                        : {'deleted': true},
                  ),
                ),
              ),
      apiFactory: api == null ? null : (_) => api,
      v2GatewayFactory: v2Api == null
          ? null
          : (_) => (
              gateway: v2Api,
              operations: Api2OperationsGateway(client: v2Api.client),
            ),
      localWakeLockEnsurer: () async {},
      draftAttachmentVault: StashMemoryVault(),
      stashAttachmentVault: StashMemoryVault(),
    );
    addTearDown(controller.dispose);
    return controller;
  }

  test(
    'connection deletion sweeps an ordinary builtin profile before first activation',
    () async {
      final bridge = NativeBridge();
      final controller = connection(bridge);
      final result = await controller.deleteProfileAndLocalData('phone');
      expect(result.removedProfile, isTrue);
      expect(bridge.calls, ['delete:phone']);
      expect(store.profiles, isEmpty);
    },
  );

  test(
    'connection deletion fences first attach before any token is persisted',
    () async {
      await store.upsert(
        ServerProfile(
          id: 'phone',
          name: 'Phone alias',
          baseUrl: 'https://alias.example',
        ),
      );
      final gate = Completer<void>();
      final bridge = NativeBridge(attachGate: gate);
      final controller = connection(bridge);
      final attach = controller.phoneProjectEngine.attach('phone');
      final attachRefused = expectLater(
        attach,
        throwsA(engine.safeError('profileDeleted')),
      );
      await Future<void>.delayed(Duration.zero);
      expect(store.profiles.single.teamEngineAuth, isEmpty);
      expect(store.profiles.single.orchestration, isNull);
      final deletion = controller.deleteProfileAndLocalData('phone');
      // The deletion closes engine admission before draining the admitted attach.
      for (
        var i = 0;
        i < 100 && store.prefs.getBool('oc.teamEngineDeleted.phone') != true;
        i++
      ) {
        await Future<void>.delayed(Duration.zero);
      }
      expect(store.prefs.getBool('oc.teamEngineDeleted.phone'), isTrue);
      gate.complete();
      await attachRefused;
      expect((await deletion).removedProfile, isTrue);
      expect(bridge.calls.last, 'delete:phone');
      expect(store.profiles, isEmpty);
      expect(secrets.containsKey('oc.teamEngineAuth.phone'), isFalse);
    },
  );

  test(
    'selected profile capabilities use only its matching live gateway',
    () async {
      final api = _NoTeamApi();
      final controller = connection(NativeBridge(), api: api);
      final selected = store.profiles.single;
      final connecting = controller.connect(selected, redetectOnFailure: false);
      await Future<void>.delayed(Duration.zero);
      expect(
        controller.serverCapabilitiesFor(selected).phoneTeamExecution,
        isFalse,
      );
      expect(
        controller
            .serverCapabilitiesFor(
              ServerProfile(
                id: 'other',
                name: 'Other',
                baseUrl: 'http://127.0.0.1:4097',
                flavor: ServerFlavor.v2,
              ),
            )
            .phoneTeamExecution,
        isTrue,
      );
      expect(
        controller
            .serverCapabilitiesFor(
              ServerProfile(
                id: 'pending-history',
                name: 'History import pending',
                baseUrl: 'http://127.0.0.1:4097',
                openCode2HistoryPending: true,
              ),
            )
            .phoneTeamExecution,
        isFalse,
      );
      await controller.disconnect();
      api.healthGate.complete(Health(healthy: true));
      await connecting;
    },
  );

  test(
    'OC2 phone installs admission fence and rejects retired dispatch',
    () async {
      final profile = ServerProfile(
        id: 'phone',
        name: 'Phone OC2',
        baseUrl: 'http://127.0.0.1:4097',
        flavor: ServerFlavor.v2,
      );
      await store.upsert(profile);
      final api = _ConnectingV2Api();
      final controller = connection(NativeBridge(), v2Api: api);
      final connecting = controller.connect(profile, redetectOnFailure: false);
      await Future<void>.delayed(Duration.zero);
      expect(api.beforeSessionDispatch, isNotNull);
      expect(api.sessionDispatchSettled, isNotNull);
      await controller.disconnect();
      await expectLater(
        api.promptAsync('ses_person', text: 'Help'),
        throwsA(
          isA<ApiException>().having(
            (error) => error.message,
            'message',
            'The chat connection changed. Reconnect before sending.',
          ),
        ),
      );
      api.healthGate.complete(Health(healthy: true, version: '2.1.1'));
      await connecting;
    },
  );

  test(
    'OC2 admission refuses dispatch if the previous phone engine cannot stop',
    () async {
      final profile = ServerProfile(
        id: 'phone',
        name: 'Phone OC2',
        baseUrl: 'http://127.0.0.1:4097',
        flavor: ServerFlavor.v2,
      );
      await store.upsert(profile);
      await store.upsert(
        ServerProfile(
          id: 'alias',
          name: 'Alias',
          baseUrl: 'http://127.0.0.1:4097',
          teamEngineAuth: 'private-engine-token',
          orchestration: const OrchestrationConfig(
            provider: OrchestrationProvider.phoneEngine,
            url: 'http://127.0.0.1:42761',
          ),
        ),
      );
      final bridge = NativeBridge()..failStop = true;
      final api = _ConnectingV2Api();
      final controller = connection(bridge, v2Api: api);
      final connecting = controller.connect(profile, redetectOnFailure: false);
      await Future<void>.delayed(Duration.zero);
      await expectLater(
        api.shell('ses_person', command: 'pwd', agent: 'build'),
        throwsA(
          isA<ApiException>().having(
            (error) => error.message,
            'message',
            'AI Team could not pause safely. Stop AI Team before sending.',
          ),
        ),
      );
      await expectLater(
        controller.prepareActionTransport(),
        throwsA(
          isA<ApiException>().having(
            (error) => error.message,
            'message',
            'AI Team could not pause safely. Stop AI Team before sending.',
          ),
        ),
      );
      expect(bridge.calls, ['stop:alias', 'stop:alias']);
      await controller.disconnect();
      api.healthGate.complete(Health(healthy: true, version: '2.1.1'));
      await connecting;
    },
  );
}
