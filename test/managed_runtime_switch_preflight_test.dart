import 'dart:async';
import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/server_probe.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/offline_queue.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/termux/managed_server_recovery.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

import 'support/profile_monitor_fixture.dart';

class _RecoveryDisk extends InMemorySharedPreferencesStore {
  _RecoveryDisk(super.data) : super.withData();
  bool refuse = false;
  bool refuseActiveClear = false;
  Completer<void>? gate;
  final entered = Completer<void>();

  @override
  Future<bool> remove(String key) async {
    if (refuseActiveClear && key.endsWith('oc.activeProfile')) return false;
    return super.remove(key);
  }

  @override
  Future<bool> setValue(String type, String key, Object value) async {
    if (key.contains('oc.managedServerRecovery.')) {
      if (!entered.isCompleted) entered.complete();
      await gate?.future;
      if (refuse) return false;
    }
    return super.setValue(type, key, value);
  }
}

class _WrongRuntimeV1 extends OpenCodeApi {
  _WrongRuntimeV1() : super(baseUrl: TermuxBridge.managedServerUrl);

  @override
  Future<Health> health() async =>
      throw ApiException('Wrong runtime', statusCode: 401);
}

class _WrongRuntimeV2 extends MonitorTestGateway {
  @override
  Future<Health> health() async =>
      throw ApiException('Wrong runtime', statusCode: 404);
}

class _HeldPromptGateway extends MonitorTestGateway {
  final entered = Completer<void>();
  final accepted = Completer<void>();

  @override
  Future<void> promptAsync(
    String sessionID, {
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment>? attachments,
    List<PromptAgentMention>? agentMentions,
    PromptDelivery? delivery,
  }) async {
    entered.complete();
    await accepted.future;
  }
}

class _UnusedRepository implements ProductRepository {
  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw StateError('No server work should follow a runtime mismatch');
}

void main() {
  final binding = TestWidgetsFlutterBinding.ensureInitialized();
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  late ProfileStore store;
  late ConnectionController controller;
  late ServerProfile local;
  late ServerProfile remote;

  setUp(() async {
    binding.defaultBinaryMessenger.setMockMethodCallHandler(
      secure,
      (call) async => call.method == 'readAll' ? <String, String>{} : null,
    );
    SharedPreferences.setMockInitialValues({});
    store = ProfileStore(prefs: await SharedPreferences.getInstance());
    await store.load();
    local = ServerProfile(
      id: 'local',
      name: 'This phone',
      baseUrl: TermuxBridge.managedServerUrl,
      password: 'fixture-local-password',
    );
    remote = ServerProfile(
      id: 'remote',
      name: 'Computer',
      baseUrl: 'https://server.example',
    );
    await store.upsert(local);
    await store.upsert(remote);
    await store.setActiveId(local.id);
    controller = ConnectionController(
      store,
      monitorGatewayFactory: (_) =>
          (gateway: MonitorTestGateway(), operations: MonitorTestOperations()),
      backgroundLive: BackgroundLiveController(
        preferences: store.prefs,
        invoke: (method, [arguments]) async {
          if (method == 'getBackgroundPause') {
            return const {
              'supported': true,
              'active': false,
              'paused': false,
              'reason': 'none',
              'at': null,
              'canResume': false,
            };
          }
          return const {};
        },
      ),
    );
    controller.adoptConnectedProfileForTesting(local);
    controller.status = StreamStatus.connected;
  });

  tearDown(() {
    serverProbe = probeServerConnection;
    controller.dispose();
    ManagedServerRecovery.disposeForPreferences(store.prefs);
    binding.defaultBinaryMessenger.setMockMethodCallHandler(secure, null);
  });

  // A record as the app has written it since 2de4744d: the shared retry
  // budget ('attempts') is part of it, and since 6ed0ec26 a record without
  // a readable budget fails closed.
  Future<void> recoveryOn(String id) => store.prefs.setString(
    ManagedServerRecovery.preferenceKey(id),
    jsonEncode({
      'enabled': true,
      'token': '',
      'attempts': 0,
      'operation': '',
      'pendingOperation': '',
    }),
  );

  // Since 6ed0ec26 a runtime switch suspends recovery for the managed
  // profiles without turning off the person's automation policy.
  bool recoverySuspended(String id) =>
      (jsonDecode(
            store.prefs.getString(ManagedServerRecovery.preferenceKey(id))!,
          )
          as Map)['manuallySuspended'] ==
      true;

  bool recoveryEnabled(String id) =>
      (jsonDecode(
            store.prefs.getString(ManagedServerRecovery.preferenceKey(id))!,
          )
          as Map)['enabled'] ==
      true;

  Future<void> queueFor(String id, {bool dispatched = false}) async {
    await controller.queuePrompt(
      QueuedPrompt(
        id: 'queued-$id',
        profileID: id,
        sessionID: 'session',
        text: 'Fixture prompt',
        createdAt: DateTime.now().millisecondsSinceEpoch,
        dispatchedAt: dispatched ? DateTime.now().millisecondsSinceEpoch : null,
      ),
    );
  }

  test(
    'idle local clears selection and preserves profile and credentials',
    () async {
      await recoveryOn(local.id);
      final profileBefore = jsonEncode(local.toJson());
      await controller.prepareManagedRuntimeSwitch();
      expect(controller.status, StreamStatus.disconnected);
      expect(store.activeId, isNull);
      expect(jsonEncode(local.toJson()), profileBefore);
      expect(local.password, 'fixture-local-password');
      expect(store.profiles, containsAll([local, remote]));
      expect(recoverySuspended(local.id), isTrue);
    },
  );

  test('disables all local aliases and leaves remote work connected', () async {
    final alias = ServerProfile(
      id: 'local-v2',
      name: 'This phone beta',
      baseUrl: TermuxBridge.managedServerUrl,
      flavor: ServerFlavor.v2,
    );
    await store.upsert(alias);
    await recoveryOn(local.id);
    await recoveryOn(alias.id);
    await recoveryOn(remote.id);
    await store.setActiveId(remote.id);
    controller.adoptConnectedProfileForTesting(remote);
    controller.busySessions.add('remote-busy');
    await queueFor(remote.id);

    await controller.prepareManagedRuntimeSwitch();

    expect(controller.status, StreamStatus.connected);
    expect(store.activeId, remote.id);
    expect(controller.busySessions, contains('remote-busy'));
    expect(controller.queuedPromptCountForProfile(remote.id), 1);
    expect(recoverySuspended(local.id), isTrue);
    expect(recoverySuspended(alias.id), isTrue);
    expect(recoverySuspended(remote.id), isFalse);
    expect(recoveryEnabled(remote.id), isTrue);
  });

  for (final url in ['http://127.0.0.1:4097', 'http://localhost:4096']) {
    test('does not disconnect unmanaged endpoint $url', () async {
      local.baseUrl = url;
      controller.busySessions.add('other-server');
      await controller.prepareManagedRuntimeSwitch();
      expect(controller.status, StreamStatus.connected);
      expect(controller.busySessions, contains('other-server'));
    });
  }

  test(
    'does not treat another backend at local URL as managed OpenCode',
    () async {
      local.backend = ServerBackend.codex;
      controller.busySessions.add('codex-work');
      await recoveryOn(local.id);
      await controller.prepareManagedRuntimeSwitch();
      expect(controller.status, StreamStatus.connected);
      expect(recoveryEnabled(local.id), isTrue);
    },
  );

  test(
    'known local running work blocks before recovery or disconnect',
    () async {
      await recoveryOn(local.id);
      controller.busySessions.add('private-session');
      await expectLater(
        controller.prepareManagedRuntimeSwitch(),
        throwsStateError,
      );
      expect(controller.status, StreamStatus.connected);
      expect(controller.busySessions, contains('private-session'));
      expect(recoveryEnabled(local.id), isTrue);
      expect(recoverySuspended(local.id), isFalse);
    },
  );

  test('pending local permission blocks without needing busy status', () async {
    controller.permissions['request'] = PermissionRequest(
      id: 'request',
      sessionID: 'session',
      permission: 'edit',
    );
    await expectLater(
      controller.prepareManagedRuntimeSwitch(),
      throwsStateError,
    );
    expect(controller.status, StreamStatus.connected);
    expect(controller.permissions, isNotEmpty);
  });

  test('admitted local inbox send blocks before server reports busy', () async {
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'session.inbox.enqueued',
        properties: {
          'sessionID': 'session',
          'inboxID': 'inbox',
          'item': {
            'type': 'prompt',
            'payload': {'text': 'Fixture prompt'},
          },
        },
      ),
    );
    expect(controller.inboxItemsFor('session'), hasLength(1));
    await expectLater(
      controller.prepareManagedRuntimeSwitch(),
      throwsStateError,
    );
    expect(controller.status, StreamStatus.connected);
  });

  for (final dispatched in [false, true]) {
    test(
      'retains saved local queue during switch (dispatched=$dispatched)',
      () async {
        await queueFor(local.id, dispatched: dispatched);
        final before = store.prefs.getString('oc.offlineQueue');
        await controller.prepareManagedRuntimeSwitch();
        expect(store.prefs.getString('oc.offlineQueue'), before);
        expect(controller.status, StreamStatus.disconnected);
        expect(store.activeId, isNull);
      },
    );
  }

  test('unreadable saved queue is untouched during switch', () async {
    await store.prefs.setString('oc.offlineQueue', '{broken');
    await controller.prepareManagedRuntimeSwitch();
    expect(store.prefs.getString('oc.offlineQueue'), '{broken');
    expect(controller.status, StreamStatus.disconnected);
  });

  test(
    'background monitor local running work blocks with remote active',
    () async {
      await store.setActiveId(remote.id);
      controller.adoptConnectedProfileForTesting(remote);
      await controller.profileMonitor.setEnabled(local.id, true);
      await controller.profileMonitor.refresh();
      expect(controller.profileMonitor.snapshotFor(local.id).runningCount, 1);
      await expectLater(
        controller.prepareManagedRuntimeSwitch(),
        throwsStateError,
      );
      expect(controller.status, StreamStatus.connected);
      expect(store.activeId, remote.id);
    },
  );

  test(
    'recovery persistence failure is actionable and never disconnects',
    () async {
      await recoveryOn(local.id);
      final disk = _RecoveryDisk(
        await SharedPreferencesStorePlatform.instance.getAll(),
      )..refuse = true;
      SharedPreferencesStorePlatform.instance = disk;
      await expectLater(
        controller.prepareManagedRuntimeSwitch(),
        throwsA(
          isA<StateError>().having(
            (error) => error.message,
            'message',
            'Automatic server recovery could not be disabled. Try again before '
                'switching OpenCode versions.',
          ),
        ),
      );
      expect(controller.status, StreamStatus.connected);
      expect(store.activeId, local.id);
    },
  );

  test(
    'new work during recovery persistence blocks final disconnect',
    () async {
      await recoveryOn(local.id);
      final disk = _RecoveryDisk(
        await SharedPreferencesStorePlatform.instance.getAll(),
      )..gate = Completer<void>();
      SharedPreferencesStorePlatform.instance = disk;
      final switching = controller.prepareManagedRuntimeSwitch();
      final blocked = expectLater(switching, throwsStateError);
      await disk.entered.future;
      controller.busySessions.add('arrived-during-save');
      disk.gate!.complete();
      await blocked;
      expect(controller.status, StreamStatus.connected);
      expect(controller.busySessions, contains('arrived-during-save'));
    },
  );
  test('in-flight local send blocks without closing its transport', () async {
    final gateway = _HeldPromptGateway();
    controller.api = gateway;
    await queueFor(local.id);
    final flush = controller.flushOfflineQueue();
    await gateway.entered.future;
    await expectLater(
      controller.prepareManagedRuntimeSwitch(),
      throwsStateError,
    );
    expect(controller.status, StreamStatus.connected);
    expect(store.activeId, local.id);
    expect(gateway.isClosed, isFalse);
    expect(controller.queuedPromptSending('queued-local'), isTrue);
    gateway.accepted.complete();
    await flush;
    expect(controller.queuedPromptCountForProfile(local.id), 0);
    await controller.prepareManagedRuntimeSwitch();
    expect(controller.status, StreamStatus.disconnected);
    expect(store.activeId, isNull);
  });

  test('selection persistence failure prevents switch completion', () async {
    final disk = _RecoveryDisk(
      await SharedPreferencesStorePlatform.instance.getAll(),
    )..refuseActiveClear = true;
    SharedPreferencesStorePlatform.instance = disk;
    await expectLater(
      controller.prepareManagedRuntimeSwitch(),
      throwsA(
        isA<StateError>().having(
          (error) => error.message,
          'message',
          'The local connection could not be cleared. Try again before '
              'switching OpenCode versions.',
        ),
      ),
    );
    expect(controller.status, StreamStatus.connected);
    expect(store.profiles, contains(local));
  });

  for (final savedFlavor in [ServerFlavor.v1, ServerFlavor.v2]) {
    test(
      'managed $savedFlavor profile never adopts the other running runtime',
      () async {
        controller.dispose();
        final detected = savedFlavor == ServerFlavor.v1
            ? ServerFlavor.v2
            : ServerFlavor.v1;
        local.flavor = savedFlavor;
        local.serverVersion = savedFlavor == ServerFlavor.v1
            ? '1.18.29'
            : '0.0.0-beta-18600';
        await store.upsert(local);
        final before = jsonEncode(local.toJson());
        var v1Builds = 0;
        var v2Builds = 0;
        var probeCalls = 0;
        serverProbe = ({required baseUrl, username, password}) async {
          probeCalls++;
          return ServerProbeResult.success(
            detected == ServerFlavor.v1 ? '1.18.29' : '0.0.0-beta-18600',
            flavor: detected,
          );
        };
        controller = ConnectionController(
          store,
          apiFactory: (_) {
            v1Builds++;
            return _WrongRuntimeV1();
          },
          repositoryFactory: (_) => _UnusedRepository(),
          v2GatewayFactory: (_) {
            v2Builds++;
            return (
              gateway: _WrongRuntimeV2(),
              operations: _UnusedRepository(),
            );
          },
          localWakeLockEnsurer: () async {},
          backgroundLive: BackgroundLiveController(
            preferences: store.prefs,
            invoke: (method, [arguments]) async {
              if (method == 'getBackgroundPause') {
                return const {
                  'supported': true,
                  'active': false,
                  'paused': false,
                  'reason': 'none',
                  'at': null,
                  'canResume': false,
                };
              }
              return const {};
            },
          ),
        );
        await controller.connect(local);
        expect(probeCalls, 1);
        expect(
          v1Builds + v2Builds,
          1,
          reason: 'Never retry as another runtime',
        );
        expect(controller.status, StreamStatus.disconnected);
        expect(controller.api, isNull);
        expect(controller.managedRuntimeMismatch, isTrue);
        expect(
          controller.lastError,
          ConnectionController.managedRuntimeMismatchMessage,
        );
        expect(jsonEncode(local.toJson()), before);
        expect(local.password, 'fixture-local-password');
        final reloaded = ProfileStore(prefs: store.prefs);
        await reloaded.load();
        final persisted = reloaded.profiles.firstWhere(
          (value) => value.id == local.id,
        );
        expect(jsonEncode(persisted.toJson()), before);
      },
    );
  }
}
