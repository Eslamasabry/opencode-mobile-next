import 'dart:async';
import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/domain/phone_project_engine.dart';
import 'package:opencode_mobile/state/phone_project_engine.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/orchestration/adapters/gascity/gascity_probe.dart'
    show ProbeFound;
import 'package:opencode_mobile/orchestration/adapters/inapp/phone_engine_gateway.dart';

import 'phone_project_engine_gateway_test.dart' as fake;

class NativeBridge implements PhoneProjectEngineBridge {
  final calls = <String>[];
  bool failDelete = false;
  bool failStop = false;
  Object? startFailure;
  Completer<void>? stopGate;
  final Completer<void>? attachGate;
  NativeBridge({this.attachGate});
  @override
  Future<void> start(String id, {int port = 4098, String? notice}) async {
    calls.add('start:$id');
    if (startFailure != null) throw startFailure!;
  }

  @override
  Future<({String baseUrl, String bearerToken})> credentials(String id) async {
    calls.add('credentials:$id');
    await attachGate?.future;
    return (
      baseUrl: 'http://127.0.0.1:4098',
      bearerToken: 'engine-private-token',
    );
  }

  @override
  Future<void> stop(String id) async {
    calls.add('stop:$id');
    await stopGate?.future;
    if (failStop) throw StateError('unsafe stop details');
  }

  @override
  Future<void> delete(String id) async {
    calls.add('delete:$id');
    if (failDelete) throw StateError('unsafe native details');
  }
}

class ManualChatSchedule {
  void Function()? tick;
  bool cancelled = false;
  void Function() schedule(Duration period, void Function() callback) {
    expect(period, const Duration(seconds: 10));
    tick = callback;
    return () {
      cancelled = true;
    };
  }
}

class CountingGateway extends PhoneEngineGateway {
  CountingGateway({
    required super.baseUrl,
    required super.profileId,
    required super.bearerToken,
    required super.adapter,
  });
  int closeCalls = 0;
  @override
  Future<void> close() {
    closeCalls++;
    return super.close();
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final secrets = <String, String>{};
  late SharedPreferences prefs;
  late ProfileStore store;
  setUp(() async {
    secrets.clear();
    SharedPreferences.setMockInitialValues({});
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
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
              case 'deleteAll':
                secrets.clear();
                return null;
              case 'containsKey':
                return secrets.containsKey(key);
            }
            return null;
          },
        );
    prefs = await SharedPreferences.getInstance();
    store = ProfileStore(prefs: prefs);
    await store.upsert(
      ServerProfile(
        id: 'phone',
        name: 'Phone',
        baseUrl: 'http://127.0.0.1:4097',
      ),
    );
    await store.upsert(
      ServerProfile(
        id: 'remote',
        name: 'Remote',
        baseUrl: 'https://example.org',
      ),
    );
    await store.setActiveId('remote');
  });
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          null,
        );
  });
  PhoneProjectEngineController controller(
    PhoneProjectEngineBridge bridge,
    List<String> requests,
  ) => PhoneProjectEngineController(
    store: store,
    bridge: bridge,
    gatewayBuilder:
        ({required baseUrl, required profileId, required bearerToken}) =>
            PhoneEngineGateway(
              baseUrl: baseUrl,
              profileId: profileId,
              bearerToken: bearerToken,
              adapter: fake.FakeEngineAdapter((r) async {
                requests.add(r.method);
                return fake.jsonBody(
                  r.path == '/v1/health'
                      ? fake.health(profileId)
                      : {'deleted': true},
                );
              }),
            ),
  );
  test(
    'native start codes survive controller without native payloads',
    () async {
      const channel = MethodChannel('test.phone_engine_setup_codes');
      final calls = <MethodCall>[];
      final native = BuiltinPhoneProjectEngineBridge(
        BuiltinLinux(channel: channel),
      );
      final c = controller(native, []);
      store.profiles.firstWhere((p) => p.id == 'phone').password =
          'fixture-server-password';
      try {
        for (final code in [
          'restart_required',
          'boundary_not_packaged',
          'boundary_unavailable',
          'boundary_unsupported',
        ]) {
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
              .setMockMethodCallHandler(channel, (call) async {
                calls.add(call);
                if (call.method == 'run') {
                  return {'exitCode': 0, 'output': ''};
                }
                if (call.method == 'stopPhoneEngine') {
                  return {'profileId': 'phone', 'running': false};
                }
                throw PlatformException(
                  code: code,
                  message: 'fixture-private-message',
                  details: 'fixture-private-details',
                );
              });
          await expectLater(
            c.start('phone'),
            throwsA(
              isA<PhoneEngineException>()
                  .having((e) => e.code, 'code', code)
                  .having(
                    (e) => e.toString(),
                    'safe error',
                    isNot(contains('fixture-private')),
                  ),
            ),
          );
        }
        expect(
          calls.where((call) => call.method == 'startPhoneEngine'),
          hasLength(4),
        );
        expect(
          calls.where((call) => call.method == 'phoneEngineCredentials'),
          isEmpty,
        );
        expect(
          store.profiles.firstWhere((p) => p.id == 'phone').teamEngineAuth,
          isEmpty,
        );
      } finally {
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(channel, null);
        await c.close();
      }
    },
  );

  test(
    'native snapshots the app-owned server password after preparation',
    () async {
      const channel = MethodChannel('test.phone_engine_auth_preparation');
      final calls = <String>[];
      final profile = store.profiles.firstWhere((p) => p.id == 'phone');
      profile.password = 'fixture-current-server-password';
      final native = BuiltinPhoneProjectEngineBridge(
        BuiltinLinux(channel: channel),
      );
      final c = controller(native, []);
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            calls.add(call.method);
            if (call.method == 'stopPhoneEngine') return {'running': false};
            if (call.method == 'run') {
              expect(
                (call.arguments as Map)['script'],
                BuiltinLinux.writePasswordScript,
              );
              expect((call.arguments as Map)['stdin'], profile.password);
              return {'exitCode': 0, 'output': ''};
            }
            if (call.method == 'startPhoneEngine') {
              throw PlatformException(code: 'boundary_unsupported');
            }
            throw StateError('Unexpected native operation');
          });
      try {
        await expectLater(
          c.start('phone'),
          throwsA(fake.safeError('boundary_unsupported')),
        );
        expect(calls, ['stopPhoneEngine', 'run', 'startPhoneEngine']);
        calls.clear();
        profile.password = '';
        await expectLater(
          c.start('phone'),
          throwsA(fake.safeError('server_auth_unavailable')),
        );
        expect(calls, ['stopPhoneEngine']);
      } finally {
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(channel, null);
        await c.close();
      }
    },
  );

  test('OC2 secure password is prepared before native engine launch', () async {
    final profile = store.profiles.firstWhere((p) => p.id == 'phone');
    profile.flavor = ServerFlavor.v2;
    profile.password = 'fixture-oc2-server-password';
    await store.upsert(profile);
    final restored = ProfileStore(prefs: prefs);
    await restored.load();
    final restoredProfile = restored.profiles.firstWhere(
      (p) => p.id == 'phone',
    );
    expect(restoredProfile.password, profile.password);
    expect(restoredProfile.toJson().values, isNot(contains(profile.password)));

    const channel = MethodChannel('test.phone_engine_oc2_auth');
    final calls = <String>[];
    final c = PhoneProjectEngineController(
      store: restored,
      bridge: BuiltinPhoneProjectEngineBridge(BuiltinLinux(channel: channel)),
    );
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          calls.add(call.method);
          if (call.method == 'stopPhoneEngine') return {'running': false};
          if (call.method == 'run') {
            expect(
              (call.arguments as Map)['script'],
              BuiltinLinux.writePasswordScript,
            );
            expect((call.arguments as Map)['stdin'], restoredProfile.password);
            return {'exitCode': 0, 'output': ''};
          }
          if (call.method == 'startPhoneEngine') {
            expect(
              call.arguments.toString(),
              isNot(contains(profile.password)),
            );
            throw PlatformException(code: 'boundary_unsupported');
          }
          throw StateError('Unexpected native operation');
        });
    try {
      await expectLater(
        c.start('phone'),
        throwsA(fake.safeError('boundary_unsupported')),
      );
      expect(calls, ['stopPhoneEngine', 'run', 'startPhoneEngine']);
    } finally {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
      await c.close();
    }
  });

  test(
    'start preserves typed bridge failure and safely maps unknown failure',
    () async {
      final bridge = NativeBridge()
        ..startFailure = const PhoneEngineException('boundary_unavailable');
      final c = controller(bridge, []);
      await expectLater(
        c.start('phone'),
        throwsA(fake.safeError('boundary_unavailable')),
      );
      bridge.startFailure = StateError('private implementation error');
      await expectLater(
        c.start('phone'),
        throwsA(fake.safeError('engineUnavailable')),
      );
      await c.close();
    },
  );

  test(
    'fresh start closes prior gateway before rotating native credentials',
    () async {
      final bridge = NativeBridge();
      final c = controller(bridge, []);
      await c.attach('phone');
      final client = c.gateway(
        store.profiles.firstWhere((p) => p.id == 'phone'),
      );
      expect(client.isClosed, isFalse);
      await c.start('phone');
      expect(client.isClosed, isTrue);
      expect(bridge.calls.where((call) => call == 'start:phone').length, 1);
      await c.close();
    },
  );

  test(
    'setup rollback stops native engine and closes clients without deleting data',
    () async {
      final bridge = NativeBridge();
      final c = controller(bridge, []);
      addTearDown(c.close);
      await c.start('phone');
      final profile = store.profiles.firstWhere((p) => p.id == 'phone');
      final savedConfig = profile.orchestration;
      final savedAuth = profile.teamEngineAuth;
      final client = c.gateway(profile);
      bridge.calls.clear();
      await c.stop('phone');
      expect(bridge.calls, ['stop:phone']);
      expect(client.isClosed, isTrue);
      expect(profile.orchestration, same(savedConfig));
      expect(profile.teamEngineAuth, savedAuth);
      await c.start('phone');
      expect(bridge.calls.where((call) => call == 'start:phone'), hasLength(1));
    },
  );

  test(
    'rollback queues behind an unfinished start and then releases its engine',
    () async {
      final gate = Completer<void>();
      final bridge = NativeBridge(attachGate: gate);
      final c = controller(bridge, []);
      addTearDown(c.close);
      final starting = c.start('phone');
      await Future<void>.delayed(Duration.zero);
      expect(bridge.calls.last, 'credentials:phone');
      final stopping = c.stop('phone');
      await Future<void>.delayed(Duration.zero);
      expect(bridge.calls.where((call) => call == 'stop:phone'), hasLength(1));
      gate.complete();
      await starting;
      await stopping;
      expect(bridge.calls.last, 'stop:phone');
      expect(bridge.calls.where((call) => call == 'stop:phone'), hasLength(2));
    },
  );

  test(
    'rollback surfaces a safe error if native ownership cannot be released',
    () async {
      final bridge = NativeBridge();
      final c = controller(bridge, []);
      addTearDown(c.close);
      await c.start('phone');
      bridge.failStop = true;
      await expectLater(
        c.stop('phone'),
        throwsA(fake.safeError('engineStopFailed')),
      );
    },
  );

  test(
    'human dispatch cannot bypass the old generation while fresh start stops it',
    () async {
      final gate = Completer<void>();
      final bridge = NativeBridge()..stopGate = gate;
      final c = controller(bridge, []);
      final starting = c.start('phone');
      await Future<void>.delayed(Duration.zero);
      expect(bridge.calls, contains('stop:phone'));
      var admitted = false;
      final dispatch = c
          .beforePersonDispatch('phone')
          .then((_) => admitted = true);
      await Future<void>.delayed(Duration.zero);
      expect(admitted, isFalse);
      gate.complete();
      await dispatch;
      await starting;
      expect(admitted, isTrue);
      expect(
        bridge.calls.indexOf('stop:phone'),
        lessThan(bridge.calls.indexOf('start:phone')),
      );
      await c.close();
    },
  );

  test(
    'legacy provider parsing stays compatible; new provider round trips',
    () {
      expect(
        OrchestrationProvider.fromName(null),
        OrchestrationProvider.gascity,
      );
      expect(
        OrchestrationProvider.fromName('unknown'),
        OrchestrationProvider.gascity,
      );
      expect(
        OrchestrationProvider.fromName('fixture'),
        OrchestrationProvider.fixture,
      );
      final config = OrchestrationConfig(
        provider: OrchestrationProvider.phoneEngine,
        url: 'http://127.0.0.1:4098',
        hostMode: OrchestrationHostMode.phone,
      );
      expect(
        OrchestrationConfig.fromJson(config.toJson())!.provider,
        OrchestrationProvider.phoneEngine,
      );
    },
  );
  test(
    'attach persists only nonsecret config and restores auth from Keystore',
    () async {
      final bridge = NativeBridge();
      final calls = <String>[];
      final engine = controller(bridge, calls);
      final health = await engine.attach('phone');
      expect(health.canExecute, isFalse);
      expect(bridge.calls, ['credentials:phone']);
      final profile = store.profiles.firstWhere((p) => p.id == 'phone');
      expect(
        profile.orchestration!.provider,
        OrchestrationProvider.phoneEngine,
      );
      expect(
        jsonEncode(profile.toJson()),
        isNot(contains('engine-private-token')),
      );
      expect(
        prefs.getKeys().any(
          (k) =>
              (prefs.get(k)?.toString() ?? '').contains('engine-private-token'),
        ),
        isFalse,
      );
      expect(secrets['oc.teamEngineAuth.phone'], 'engine-private-token');
      final restored = ProfileStore(prefs: prefs);
      await restored.load();
      expect(
        restored.profiles.firstWhere((p) => p.id == 'phone').teamEngineAuth,
        'engine-private-token',
      );
      await engine.close();
    },
  );
  test(
    'inactive profile deletion uses durable HTTP then native and erases auth',
    () async {
      final bridge = NativeBridge();
      final calls = <String>[];
      final engine = controller(bridge, calls);
      await engine.attach('phone');
      calls.clear();
      await engine.deleteProfile('phone');
      expect(calls, ['GET', 'DELETE']);
      expect(bridge.calls.last, 'delete:phone');
      expect(secrets.containsKey('oc.teamEngineAuth.phone'), isFalse);
      expect(prefs.getBool('oc.teamEngineDeleted.phone'), isTrue);
      await expectLater(
        engine.attach('phone'),
        throwsA(fake.safeError('profileDeleted')),
      );
      final restarted = controller(bridge, calls);
      await expectLater(
        restarted.start('phone'),
        throwsA(fake.safeError('profileDeleted')),
      );
    },
  );
  test('ordinary phone profile edits retain Keystore engine auth', () async {
    final engine = controller(NativeBridge(), []);
    await engine.attach('phone');
    final original = store.profiles.firstWhere((p) => p.id == 'phone');
    final edited = ServerProfile(
      id: original.id,
      name: 'Renamed phone',
      baseUrl: original.baseUrl,
      orchestration: original.orchestration,
    );
    expect(edited.teamEngineAuth, isEmpty);
    await store.upsert(edited);
    expect(edited.teamEngineAuth, 'engine-private-token');
    expect(secrets['oc.teamEngineAuth.phone'], 'engine-private-token');
    final reloaded = ProfileStore(prefs: prefs);
    await reloaded.load();
    expect(
      reloaded.profiles.firstWhere((p) => p.id == 'phone').teamEngineAuth,
      'engine-private-token',
    );
    expect(
      prefs.getString('oc.profiles'),
      isNot(contains('engine-private-token')),
    );
    await store.clearTeamEngineAuth('phone');
    // The store keeps its own copy of the saved profile.
    final cleared = store.profiles.firstWhere((p) => p.id == 'phone');
    expect(cleared.teamEngineAuth, isEmpty);
    expect(secrets.containsKey('oc.teamEngineAuth.phone'), isFalse);
    await store.upsert(cleared);
    expect(cleared.teamEngineAuth, isEmpty);
    await engine.close();
  });
  test('closed probe and caller clients leave controller ownership', () async {
    final created = <CountingGateway>[];
    final engine = PhoneProjectEngineController(
      store: store,
      bridge: NativeBridge(),
      gatewayBuilder:
          ({required baseUrl, required profileId, required bearerToken}) {
            final gateway = CountingGateway(
              baseUrl: baseUrl,
              profileId: profileId,
              bearerToken: bearerToken,
              adapter: fake.FakeEngineAdapter(
                (_) async => fake.jsonBody(fake.health(profileId)),
              ),
            );
            created.add(gateway);
            return gateway;
          },
    );
    await engine.attach('phone');
    for (var i = 0; i < 20; i++) {
      await engine.probe('phone');
    }
    final callerClient = engine.gateway(
      store.profiles.firstWhere((p) => p.id == 'phone'),
    );
    await callerClient.close();
    // Keep another client open, covering callback removal during owner close.
    engine.gateway(store.profiles.firstWhere((p) => p.id == 'phone'));
    await engine.close();
    expect(created, hasLength(23));
    expect(created.every((client) => client.closeCalls == 1), isTrue);
  });
  test(
    'failed native deletion stays blocked, reports safe code, permits deletion retry',
    () async {
      final bridge = NativeBridge()..failDelete = true;
      final calls = <String>[];
      final engine = controller(bridge, calls);
      await engine.attach('phone');
      await expectLater(
        engine.deleteProfile('phone'),
        throwsA(fake.safeError('deleteFailed')),
      );
      expect(secrets.containsKey('oc.teamEngineAuth.phone'), isTrue);
      expect(prefs.getBool('oc.teamEngineDeleted.phone'), isTrue);
      await expectLater(
        engine.probe('phone'),
        throwsA(fake.safeError('profileDeleted')),
      );
      final restarted = controller(bridge, []);
      await expectLater(
        restarted.start('phone'),
        throwsA(fake.safeError('profileDeleted')),
      );
      bridge.failDelete = false;
      await restarted.deleteProfile('phone');
      expect(secrets.containsKey('oc.teamEngineAuth.phone'), isFalse);
      expect(prefs.getBool('oc.teamEngineDeleted.phone'), isTrue);
      await expectLater(
        restarted.attach('phone'),
        throwsA(fake.safeError('profileDeleted')),
      );
    },
  );
  test(
    'attach running during deletion cannot resurrect profile config or auth',
    () async {
      final gate = Completer<void>();
      final bridge = NativeBridge(attachGate: gate);
      final engine = controller(bridge, []);
      final attach = engine.attach('phone');
      // Pump the queued native credential call without starting any processes.
      await Future<void>.delayed(Duration.zero);
      final deletion = engine.deleteProfile('phone');
      gate.complete();
      await expectLater(attach, throwsA(fake.safeError('profileDeleted')));
      await deletion;
      expect(store.profiles.first.teamEngineAuth, isEmpty);
      expect(store.profiles.first.orchestration, isNull);
    },
  );
  test(
    'idle, busy and unknown renew every ten seconds; shutdown sends unknown',
    () async {
      var now = DateTime.now();
      final scheduler = ManualChatSchedule();
      var activity = const PhoneChatActivity(
        known: true,
        directories: ['/chat'],
      );
      final bodies = <Map<String, dynamic>>[];
      final client = PhoneEngineGateway(
        baseUrl: 'http://127.0.0.1:4098',
        profileId: 'phone',
        bearerToken: 'engine-private-token',
        now: () => now,
        adapter: fake.FakeEngineAdapter((r) async {
          bodies.add(Map<String, dynamic>.from(r.data as Map));
          return fake.jsonBody({'accepted': true});
        }),
      );
      final heartbeat = PhoneChatHeartbeat(
        gateway: client,
        source: () => activity,
        now: () => now,
        schedule: scheduler.schedule,
        appInstance: 'app-test',
      );
      heartbeat.start();
      await heartbeat.push();
      expect(bodies.single['known'], isTrue);
      expect(bodies.single['sessionIds'], isEmpty);
      final firstExpiry = bodies.single['until'] as int;
      now = now.add(const Duration(seconds: 10));
      scheduler.tick!();
      await heartbeat.push();
      expect(bodies.last['until'], firstExpiry + 10000);
      activity = const PhoneChatActivity(
        known: true,
        sessionIds: ['human'],
        directories: ['/chat'],
      );
      await heartbeat.push();
      expect(bodies.last['sessionIds'], ['human']);
      now = now.add(const Duration(seconds: 10));
      scheduler.tick!();
      await heartbeat.push();
      expect(bodies.last['sessionIds'], ['human']);
      activity = const PhoneChatActivity();
      await heartbeat.push();
      expect(bodies.last['known'], isFalse);
      await heartbeat.stop();
      expect(scheduler.cancelled, isTrue);
      expect(bodies.last['known'], isFalse);
      expect(client.isClosed, isTrue);
      final count = bodies.length;
      scheduler.tick!();
      await heartbeat.push();
      expect(bodies.length, count);
      expect(
        bodies.map((b) => b['sequence']),
        orderedEquals([1, 2, 3, 4, 5, 6]),
      );
    },
  );
  test('expired queued observation cannot renew an old idle lease', () async {
    var now = DateTime.now();
    final entered = Completer<void>();
    final release = Completer<void>();
    final bodies = <Map<String, dynamic>>[];
    final client = PhoneEngineGateway(
      baseUrl: 'http://127.0.0.1:4098',
      profileId: 'phone',
      bearerToken: 'engine-private-token',
      now: () => now,
      adapter: fake.FakeEngineAdapter((r) async {
        bodies.add(Map<String, dynamic>.from(r.data as Map));
        if (bodies.length == 1) {
          entered.complete();
          await release.future;
        }
        return fake.jsonBody({'accepted': true});
      }),
    );
    final heartbeat = PhoneChatHeartbeat(
      gateway: client,
      source: () =>
          const PhoneChatActivity(known: true, directories: ['/chat']),
      now: () => now,
    );
    final first = heartbeat.push(force: true);
    await entered.future;
    final stale = heartbeat.push(force: true, requireDelivery: true);
    now = now.add(const Duration(seconds: 31));
    release.complete();
    await first;
    await expectLater(stale, throwsA(fake.safeError('chatLeaseExpired')));
    expect(bodies, hasLength(1));
    await heartbeat.stop(publishUnknown: false);
  });
  test(
    'pre-dispatch lease is delivered despite newer coalesced observations',
    () async {
      final now = DateTime.now();
      final entered = Completer<void>();
      final release = Completer<void>();
      final sequences = <int>[];
      final client = PhoneEngineGateway(
        baseUrl: 'http://127.0.0.1:4098',
        profileId: 'phone',
        bearerToken: 'engine-private-token',
        now: () => now,
        adapter: fake.FakeEngineAdapter((r) async {
          sequences.add((r.data as Map)['sequence'] as int);
          if (sequences.length == 1) {
            entered.complete();
            await release.future;
          }
          return fake.jsonBody({'accepted': true});
        }),
      );
      final heartbeat = PhoneChatHeartbeat(
        gateway: client,
        source: () => const PhoneChatActivity(
          known: true,
          sessionIds: ['human'],
          directories: ['/chat'],
        ),
        now: () => now,
      );
      final first = heartbeat.push(force: true);
      await entered.future;
      final fence = heartbeat.push(force: true, requireDelivery: true);
      final latest = heartbeat.push(force: true);
      release.complete();
      await first;
      await fence;
      await latest;
      expect(sequences, [1, 2, 3]);
      await heartbeat.stop(publishUnknown: false);
    },
  );
  test(
    'stale idle before dispatch or before settlement never clears turn latch',
    () {
      final tracker = PhoneChatDispatchTracker();
      final before = tracker.epoch;
      tracker.begin('person', '/chat');
      tracker.reconcile({}, before, '/chat');
      expect(tracker.sessionIds, ['person']);
      final during = tracker.epoch;
      tracker.settled('person');
      tracker.reconcile({}, during, '/chat');
      expect(tracker.sessionIds, ['person']);
      tracker.reconcile({}, tracker.epoch, '/another');
      expect(tracker.sessionIds, ['person']);
      tracker.reconcile({}, tracker.epoch, '/chat');
      expect(tracker.sessionIds, ['person']);
      tracker.observeBusy('person');
      tracker.reconcile({}, tracker.epoch, '/chat');
      expect(tracker.sessionIds, isEmpty);
    },
  );
  test(
    'concurrent dispatch remains busy until every request settles and fresh idle',
    () {
      final tracker = PhoneChatDispatchTracker();
      tracker.begin('person', '/chat');
      tracker.begin('person', '/chat');
      tracker.settled('person');
      tracker.reconcile({}, tracker.epoch, '/chat');
      expect(tracker.sessionIds, ['person']);
      tracker.settled('person');
      tracker.reconcile({}, tracker.epoch, '/chat');
      expect(tracker.sessionIds, [
        'person',
      ]); // Async start is still unobserved.
      tracker.reconcile({'person': 'busy'}, tracker.epoch, '/chat');
      expect(tracker.sessionIds, ['person']);
      tracker.reconcile({}, tracker.epoch, '/chat');
      expect(tracker.sessionIds, isEmpty);
    },
  );
  test(
    'failed heartbeat safely stops only engine; failed stop remains retryable',
    () async {
      final bridge = NativeBridge();
      final scheduler = ManualChatSchedule();
      final engine = PhoneProjectEngineController(
        store: store,
        bridge: bridge,
        chatSource: (_) => const PhoneChatActivity(
          known: true,
          sessionIds: ['human'],
          directories: ['/chat'],
        ),
        chatSchedule: scheduler.schedule,
        gatewayBuilder:
            ({required baseUrl, required profileId, required bearerToken}) =>
                PhoneEngineGateway(
                  baseUrl: baseUrl,
                  profileId: profileId,
                  bearerToken: bearerToken,
                  adapter: fake.FakeEngineAdapter((r) async {
                    if (r.path == '/v1/chatBusy') {
                      throw DioException(
                        requestOptions: r,
                        message: 'unsafe auth detail',
                      );
                    }
                    return fake.jsonBody(fake.health(profileId));
                  }),
                ),
      );
      await engine.attach('phone');
      bridge.failStop = true;
      await expectLater(
        engine.beforePersonDispatch('phone'),
        throwsA(fake.safeError('engineStopFailed')),
      );
      bridge.failStop = false;
      await engine.beforePersonDispatch('phone');
      expect(bridge.calls.where((c) => c == 'stop:phone'), hasLength(2));
      final count = bridge.calls.length;
      await engine.beforePersonDispatch('phone');
      expect(bridge.calls.length, count);
      expect(scheduler.cancelled, isTrue);
      await engine.close();
    },
  );
  test(
    'another phone alias cannot dispatch until previous engine stop is confirmed',
    () async {
      final profile = store.profiles.firstWhere((p) => p.id == 'phone');
      profile.orchestration = const OrchestrationConfig(
        provider: OrchestrationProvider.phoneEngine,
        url: 'http://127.0.0.1:4098',
        hostMode: OrchestrationHostMode.phone,
      );
      await store.upsert(profile);
      final bridge = NativeBridge()..failStop = true;
      final engine = controller(bridge, []);
      await expectLater(
        engine.preparePhoneAliasDispatch('another-alias'),
        throwsA(fake.safeError('engineStopFailed')),
      );
      bridge.failStop = false;
      await engine.preparePhoneAliasDispatch('another-alias');
      expect(bridge.calls, ['stop:phone', 'stop:phone']);
      await engine.close();
    },
  );
  test(
    'deletion drains heartbeat and cancels renewal before durable native erase',
    () async {
      final entered = Completer<void>();
      final release = Completer<void>();
      final scheduler = ManualChatSchedule();
      final bridge = NativeBridge();
      final order = <String>[];
      final engine = PhoneProjectEngineController(
        store: store,
        bridge: bridge,
        chatSource: (_) =>
            const PhoneChatActivity(known: true, directories: ['/chat']),
        chatSchedule: scheduler.schedule,
        gatewayBuilder:
            ({required baseUrl, required profileId, required bearerToken}) =>
                PhoneEngineGateway(
                  baseUrl: baseUrl,
                  profileId: profileId,
                  bearerToken: bearerToken,
                  adapter: fake.FakeEngineAdapter((r) async {
                    if (r.path == '/v1/chatBusy') {
                      entered.complete();
                      await release.future;
                      order.add('heartbeat');
                    }
                    if (r.method == 'DELETE') {
                      order.add('delete');
                    }
                    return fake.jsonBody(
                      r.path == '/v1/health'
                          ? fake.health(profileId)
                          : r.path == '/v1/chatBusy'
                          ? {'accepted': true}
                          : {'deleted': true},
                    );
                  }),
                ),
      );
      await engine.attach('phone');
      await entered.future;
      final deletion = engine.deleteProfile('phone');
      await Future<void>.delayed(Duration.zero);
      expect(order, isEmpty);
      expect(scheduler.cancelled, isTrue);
      release.complete();
      await deletion;
      expect(order, ['heartbeat', 'delete']);
      expect(bridge.calls.last, 'delete:phone');
      final count = order.length;
      scheduler.tick!();
      await engine.pushChatHeartbeat('phone');
      expect(order.length, count);
      await engine.close();
    },
  );
  test(
    'older busy while overlapping dispatch is pending cannot prove its start',
    () {
      final tracker = PhoneChatDispatchTracker();
      tracker.begin('person', '/chat');
      tracker.begin('person', '/chat');
      tracker.observeBusy('person');
      tracker.settled('person');
      tracker.observeBusy('person');
      tracker.settled('person');
      tracker.reconcile({}, tracker.epoch, '/chat');
      expect(tracker.sessionIds, ['person']);
      tracker.reconcile({'person': 'retry'}, tracker.epoch, '/chat');
      tracker.reconcile({}, tracker.epoch, '/chat');
      expect(tracker.sessionIds, isEmpty);
    },
  );
  test('unsent later prompt cannot clear an older uncertain turn latch', () {
    final tracker = PhoneChatDispatchTracker();
    tracker.begin('person', '/chat');
    tracker.settled('person');
    tracker.begin('person', '/chat');
    tracker.settled('person', removeUnsent: true);
    tracker.reconcile({}, tracker.epoch, '/chat');
    expect(tracker.sessionIds, ['person']);
    tracker.observeBusy('person');
    tracker.reconcile({}, tracker.epoch, '/chat');
    expect(tracker.sessionIds, isEmpty);
  });

  test('the orchestration probe carries the proven boundary tier from the '
      'real health shape', () async {
    final c = PhoneProjectEngineController(
      store: store,
      bridge: NativeBridge(),
      gatewayBuilder:
          ({required baseUrl, required profileId, required bearerToken}) =>
              PhoneEngineGateway(
                baseUrl: baseUrl,
                profileId: profileId,
                bearerToken: bearerToken,
                adapter: fake.FakeEngineAdapter((r) async {
                  final wire = fake.health(profileId, execution: true)
                    ..['boundaryTier'] = 'proot';
                  (wire['capabilities'] as Map)['boundaryTier'] = 'proot';
                  return fake.jsonBody(wire);
                }),
              ),
    );
    addTearDown(c.close);
    final verdict = await c.orchestrationProbe(
      ServerProfile(
        id: 'phone',
        name: 'Phone',
        baseUrl: 'http://127.0.0.1:4097',
        teamEngineAuth: 'engine-private-token',
        orchestration: const OrchestrationConfig(
          provider: OrchestrationProvider.phoneEngine,
          url: 'http://127.0.0.1:4098',
        ),
      ),
    );
    expect(verdict, isA<ProbeFound>());
    expect((verdict as ProbeFound).boundaryTier, 'proot');
    expect(verdict.readOnly, isFalse);
  });
}
