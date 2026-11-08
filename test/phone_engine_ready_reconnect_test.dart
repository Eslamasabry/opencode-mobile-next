import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/api2/client.dart';
import 'package:opencode_mobile/api2/gateway.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/domain/phone_project_engine.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/automatic_activity.dart';
import 'package:opencode_mobile/state/automation_policy.dart';
import 'package:opencode_mobile/orchestration/adapters/inapp/phone_engine_gateway.dart';

import 'phone_project_engine_controller_test.dart' show NativeBridge;
import 'phone_project_engine_gateway_test.dart' as engine;
import 'support/stash_memory_vault.dart';

class _PendingApi extends Api2Gateway {
  _PendingApi()
    : super(
        client: Api2Client.connect(
          baseUrl: 'http://127.0.0.1:4097',
          password: 'test-only',
        ),
      );
  final gate = Completer<Health>();
  @override
  Future<Health> health() => gate.future;
}

class _Operations implements ProductRepository {
  @override
  void setLocation({String? directory, String? workspace}) {}
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Engine extends PhoneEngineGateway {
  _Engine({
    required super.baseUrl,
    required super.profileId,
    required super.bearerToken,
    required bool ready,
    required this.beats,
  }) : super(
         health: PhoneEngineHealth.fromJson(
           engine.health(profileId, execution: ready),
           profileId,
         ),
       );
  final List<bool> beats;
  @override
  Future<PhoneEngineHealth> probe() async => health!;
  @override
  Future<TeamWorkspace> teamWorkspace() async =>
      TeamWorkspace.fromJson(engine.workspace());
  @override
  Future<List<ActivityEvent>> activity({
    int? afterSeq,
    int limit = 100,
  }) async => [];
  @override
  Future<void> sendChatBusy({
    required int until,
    required List<String> sessionIds,
    required List<String> directories,
    required bool known,
    required String appInstance,
    required int sequence,
  }) async {
    beats.add(known);
  }
}

class _Fixture {
  _Fixture(this.connection, this.retained, this.pending, this.beats);
  final ConnectionController connection;
  final _PendingApi retained;
  final List<_PendingApi> pending;
  final List<bool> beats;
  Future<void> close() async {
    final retry = connection.manualReconnectInProgress
        ? connection.retryConnection()
        : Future<void>.value();
    connection.dispose();
    for (final api in [retained, ...pending]) {
      if (!api.gate.isCompleted) {
        api.gate.complete(Health(healthy: true));
      }
    }
    await retry;
    await connection.phoneProjectEngine.close();
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final secrets = <String, String>{};
  final fixtures = <_Fixture>[];
  late ProfileStore store;
  setUp(() async {
    addTearDown(AutomaticActivityController.resetShared);
    addTearDown(AutomationPolicyController.resetShared);
    secrets.clear();
    SharedPreferences.setMockInitialValues({});
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
  });
  tearDown(() async {
    for (final fixture in fixtures) {
      await fixture.close();
    }
    fixtures.clear();
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

  Future<_Fixture> setup({
    bool ready = true,
    bool isolated = false,
    String url = 'http://127.0.0.1:4097',
    NativeBridge? bridge,
  }) async {
    final profile = ServerProfile(
      id: 'phone',
      name: 'Phone',
      baseUrl: url,
      teamEngineAuth: 'old-engine-token',
      orchestration: const OrchestrationConfig(
        provider: OrchestrationProvider.phoneEngine,
        url: 'http://127.0.0.1:4098',
      ),
    );
    await store.upsert(profile);
    final retained = _PendingApi();
    final pending = <_PendingApi>[];
    final beats = <bool>[];
    final connection = ConnectionController(
      store,
      isIsolated: isolated,
      v2GatewayFactory: (_) {
        final api = _PendingApi();
        pending.add(api);
        return (gateway: api, operations: _Operations());
      },
      phoneEngineBridge: bridge ?? NativeBridge(),
      phoneEngineGatewayBuilder:
          ({required baseUrl, required profileId, required bearerToken}) =>
              _Engine(
                baseUrl: baseUrl,
                profileId: profileId,
                bearerToken: bearerToken,
                ready: ready,
                beats: beats,
              ),
      draftAttachmentVault: StashMemoryVault(),
      stashAttachmentVault: StashMemoryVault(),
    );
    connection.adoptConnectedProfileForTesting(profile);
    connection.api = retained;
    connection.directory = '/root/projects/person';
    retained.setLocation(directory: connection.directory);
    connection.status = StreamStatus.disconnected;
    connection.lastError = 'Event stream lost: Connection refused';
    final fixture = _Fixture(connection, retained, pending, beats);
    fixtures.add(fixture);
    return fixture;
  }

  test(
    'ready attach reconnects the retained failed phone API without forging chat idle',
    () async {
      final fixture = await setup();
      final health = await fixture.connection.phoneProjectEngine.attach(
        'phone',
      );
      expect(health.canExecute, isTrue);
      expect(store.profiles.single.teamEngineAuth, 'engine-private-token');
      expect(fixture.pending, hasLength(1));
      expect(fixture.connection.api, same(fixture.pending.single));
      expect(fixture.retained.isClosed, isTrue);
      expect(fixture.connection.status, StreamStatus.connecting);
      expect(fixture.connection.isConnected, isFalse);
      expect(fixture.connection.lastError, isNull);
      expect(fixture.connection.manualReconnectInProgress, isTrue);
      await fixture.connection.phoneProjectEngine.pushChatHeartbeat(
        'phone',
        force: true,
      );
      expect(fixture.beats, isNotEmpty);
      expect(fixture.beats, everyElement(isFalse));
    },
  );

  test(
    'repeated ready attaches share the existing reconnect operation',
    () async {
      final fixture = await setup();
      await fixture.connection.phoneProjectEngine.attach('phone');
      final retry = fixture.connection.retryConnection();
      await fixture.connection.phoneProjectEngine.attach('phone');
      expect(fixture.pending, hasLength(1));
      expect(fixture.connection.retryConnection(), same(retry));
    },
  );

  test(
    'store-only attach and ready health polling never request reconnect',
    () async {
      final unavailable = await setup(ready: false);
      expect(
        (await unavailable.connection.phoneProjectEngine.attach(
          'phone',
        )).canExecute,
        isFalse,
      );
      expect(unavailable.pending, isEmpty);
      await unavailable.close();
      fixtures.remove(unavailable);
      final ready = await setup();
      expect(
        (await ready.connection.phoneProjectEngine.probe('phone')).canExecute,
        isTrue,
      );
      expect(ready.pending, isEmpty);
    },
  );

  test(
    'already connected isolated suspended and remote owners do not auto-reconnect',
    () async {
      for (final mode in ['connected', 'isolated', 'suspended', 'remote']) {
        final fixture = await setup(
          isolated: mode == 'isolated',
          url: mode == 'remote'
              ? 'https://laptop.example'
              : 'http://127.0.0.1:4097',
        );
        if (mode == 'connected') {
          fixture.connection.status = StreamStatus.connected;
        }
        if (mode == 'suspended') {
          fixture.connection.suspendForLifecycle();
        }
        await fixture.connection.phoneProjectEngine.attach('phone');
        expect(fixture.pending, isEmpty, reason: mode);
        await fixture.close();
        fixtures.remove(fixture);
      }
    },
  );

  test(
    'profile switch while attachment is pending cannot reconnect the new owner',
    () async {
      final gate = Completer<void>();
      final fixture = await setup(bridge: NativeBridge(attachGate: gate));
      final attach = fixture.connection.phoneProjectEngine.attach('phone');
      await Future<void>.delayed(Duration.zero);
      fixture.connection.adoptConnectedProfileForTesting(
        ServerProfile(
          id: 'other',
          name: 'Other',
          baseUrl: 'http://127.0.0.1:4097',
        ),
      );
      gate.complete();
      await attach;
      expect(fixture.pending, isEmpty);
      expect(fixture.connection.api, same(fixture.retained));
    },
  );

  test(
    'disposed owner rejects a pending attachment before readiness can reconnect',
    () async {
      final gate = Completer<void>();
      final fixture = await setup(bridge: NativeBridge(attachGate: gate));
      final attach = fixture.connection.phoneProjectEngine.attach('phone');
      final refused = expectLater(
        attach,
        throwsA(engine.safeError('engineClosed')),
      );
      await Future<void>.delayed(Duration.zero);
      fixture.connection.dispose();
      gate.complete();
      await refused;
      expect(fixture.pending, isEmpty);
    },
  );
}
