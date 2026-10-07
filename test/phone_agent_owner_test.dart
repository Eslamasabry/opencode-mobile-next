import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/domain/phone_agent_host.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

class _RefusingGateStore extends InMemorySharedPreferencesStore {
  _RefusingGateStore(super.data, this.refused) : super.withData();
  String? refused;

  @override
  Future<bool> setValue(String type, String key, Object value) async =>
      key == 'flutter.$refused' ? false : super.setValue(type, key, value);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final secrets = <String, String>{};
  final cleaned = <String>[];
  const channel = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  setUp(() {
    secrets.clear();
    cleaned.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          final args = call.arguments as Map;
          switch (call.method) {
            case 'read':
              return secrets[args['key']];
            case 'write':
              secrets[args['key'] as String] = args['value'] as String;
            case 'delete':
              secrets.remove(args['key']);
            case 'readAll':
              return Map.of(secrets);
          }
          return null;
        });
  });
  tearDown(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null),
  );

  ServerProfile profile(
    String id, {
    ServerFlavor flavor = ServerFlavor.v1,
    String url = 'http://127.0.0.1:4097',
  }) => ServerProfile(id: id, name: 'This phone', baseUrl: url, flavor: flavor);

  Future<ProfileStore> store(
    List<ServerProfile> profiles, {
    Map<String, Object> extra = const {},
  }) async {
    SharedPreferences.setMockInitialValues({
      'oc.profiles': jsonEncode(profiles.map((p) => p.toJson()).toList()),
      ...extra,
    });
    final result = ProfileStore(
      prefs: await SharedPreferences.getInstance(),
      agentHostCleanup: (id) async => cleaned.add(id),
    );
    await result.load();
    return result;
  }

  test(
    'new protocol profile reuses checked home through restart and back',
    () async {
      final s = await store(
        [profile('one')],
        extra: {
          'oc.agentPhoneGate.one': '{"claude":{"fingerprint":"pin"}}',
          'oc.agentFeed.one': '[{"sessionID":"chat"}]',
          'oc.phoneAgentsUsed.one': true,
        },
      );
      final two = profile('two', flavor: ServerFlavor.v2);
      await s.upsert(two);
      expect(s.phoneAgentOwnerProfile(two).id, 'one');
      expect(s.phoneAgentOwnerProfile(two).flavor, ServerFlavor.v2);
      await s.load();
      expect(s.phoneAgentOwnerId('two'), 'one');
      expect(s.phoneAgentOwnerId('one'), 'one');
      expect(s.prefs.getString('oc.agentPhoneGate.one'), contains('claude'));
      expect(s.prefs.getString('oc.agentFeed.one'), contains('chat'));
    },
  );

  test(
    'legacy profiles prefer checked existing owner without copying secrets',
    () async {
      secrets['oc.agentHostSecret.checked'] = 'private';
      final s = await store(
        [
          profile('empty'),
          profile('checked'),
          profile('two', flavor: ServerFlavor.v2),
          profile('termux', url: 'http://127.0.0.1:4096'),
          profile('remote', url: 'http://192.168.1.2:4097'),
        ],
        extra: {
          'oc.agentPhoneGate.empty': '{}',
          'oc.agentPhoneGate.checked': '{"claude":{"fingerprint":"proof"}}',
        },
      );
      expect(s.phoneAgentOwnerId('empty'), 'checked');
      expect(s.phoneAgentOwnerId('two'), 'checked');
      expect(s.phoneAgentOwnerId('termux'), 'termux');
      expect(s.phoneAgentOwnerId('remote'), 'remote');
      expect(secrets.keys, ['oc.agentHostSecret.checked']);
    },
  );

  test(
    'already mapped homes adopt every legacy check once without reviving retries',
    () async {
      final s = await store(
        [profile('one'), profile('two', flavor: ServerFlavor.v2)],
        extra: {
          'oc.phoneAgentOwner.one': 'one',
          'oc.phoneAgentOwner.two': 'one',
          'oc.agentPhoneGate.one':
              '{"claude":{"fingerprint":"current"},"pi":{"fingerprint":"owner"}}',
          'oc.agentPhoneGate.two':
              '{"fx":{"fingerprint":"fx-pin","architecture":"x64"},'
              '"omp":{"fingerprint":"omp-pin"},'
              '"pi":{"fingerprint":"old"},"invalid":{},"empty":{"fingerprint":""}}',
        },
      );
      var gates =
          jsonDecode(s.prefs.getString('oc.agentPhoneGate.one')!) as Map;
      expect(gates.keys, unorderedEquals(['claude', 'pi', 'fx', 'omp']));
      expect(gates['pi']['fingerprint'], 'owner');
      expect(gates['fx']['architecture'], 'x64');
      expect(s.prefs.getStringList('oc.phoneAgentGateMigration.one'), ['two']);
      // A failed phone-check retry removes the old proof before doing work.
      gates.remove('fx');
      await s.prefs.setString('oc.agentPhoneGate.one', jsonEncode(gates));
      await s.load();
      gates = jsonDecode(s.prefs.getString('oc.agentPhoneGate.one')!) as Map;
      expect(gates.containsKey('fx'), isFalse);
      expect(gates.containsKey('omp'), isTrue);
      await s.remove('one');
      await s.load();
      gates = jsonDecode(s.prefs.getString('oc.agentPhoneGate.one')!) as Map;
      expect(gates.containsKey('fx'), isFalse);
      expect(s.prefs.getStringList('oc.phoneAgentGateMigration.one'), ['two']);
      // Retaining the separate legacy home never repeats its migration.
      expect(s.prefs.getString('oc.agentPhoneGate.two'), contains('fx-pin'));
    },
  );

  test('gate adoption excludes remote and Termux homes', () async {
    final s = await store(
      [
        profile('one'),
        profile('two', flavor: ServerFlavor.v2),
        profile('termux', url: 'http://127.0.0.1:4096'),
        profile('remote', url: 'http://192.168.1.2:4097'),
      ],
      extra: {
        'oc.agentPhoneGate.one': '{"claude":{"fingerprint":"pin"}}',
        'oc.agentPhoneGate.two': '{"fx":{"fingerprint":"fx-pin"}}',
        'oc.agentPhoneGate.termux': '{"pi":{"fingerprint":"termux"}}',
        'oc.agentPhoneGate.remote': '{"omp":{"fingerprint":"remote"}}',
      },
    );
    final gates =
        jsonDecode(s.prefs.getString('oc.agentPhoneGate.one')!) as Map;
    expect(gates.keys, unorderedEquals(['claude', 'fx']));
    expect(s.prefs.getStringList('oc.phoneAgentGateMigration.one'), ['two']);
  });

  test('malformed migration receipt refuses to revive old proof', () async {
    await expectLater(
      store(
        [profile('one'), profile('two', flavor: ServerFlavor.v2)],
        extra: {
          'oc.phoneAgentOwner.one': 'one',
          'oc.phoneAgentGateMigration.one': 'broken',
          'oc.agentPhoneGate.one': '{}',
          'oc.agentPhoneGate.two': '{"fx":{"fingerprint":"old"}}',
        },
      ),
      throwsA(
        isA<AgentHostException>().having(
          (e) => e.reason,
          'reason',
          AgentHostFailure.storage,
        ),
      ),
    );
  });

  test('malformed donor does not suppress another checked home', () async {
    final s = await store(
      [profile('one'), profile('bad'), profile('two', flavor: ServerFlavor.v2)],
      extra: {
        'oc.phoneAgentOwner.one': 'one',
        'oc.agentPhoneGate.bad': 'broken',
        'oc.agentPhoneGate.two': '{"fx":{"fingerprint":"fx-pin"}}',
      },
    );
    expect(s.prefs.getString('oc.agentPhoneGate.one'), contains('fx-pin'));
  });

  for (final refused in [
    'oc.agentPhoneGate.one',
    'oc.phoneAgentGateMigration.one',
  ]) {
    test(
      'refused $refused write preserves migration for a persisted retry',
      () async {
        SharedPreferences.setMockInitialValues({
          'oc.profiles': jsonEncode([
            profile('one').toJson(),
            profile('two').toJson(),
          ]),
          'oc.phoneAgentOwner.one': 'one',
          'oc.agentPhoneGate.one': '{}',
          'oc.agentPhoneGate.two': '{"fx":{"fingerprint":"fx-pin"}}',
        });
        final original = SharedPreferencesStorePlatform.instance;
        final disk = _RefusingGateStore(await original.getAll(), refused);
        SharedPreferencesStorePlatform.instance = disk;
        SharedPreferences.resetStatic();
        addTearDown(() => SharedPreferencesStorePlatform.instance = original);
        final prefs = await SharedPreferences.getInstance();
        final s = ProfileStore(prefs: prefs);
        await expectLater(s.load(), throwsA(isA<AgentHostException>()));
        expect(prefs.getStringList('oc.phoneAgentGateMigration.one'), isNull);
        if (refused == 'oc.agentPhoneGate.one') {
          expect(prefs.getString('oc.agentPhoneGate.one'), '{}');
        }
        disk.refused = null;
        await s.load();
        await prefs.reload();
        expect(prefs.getString('oc.agentPhoneGate.one'), contains('fx-pin'));
        expect(prefs.getStringList('oc.phoneAgentGateMigration.one'), ['two']);
      },
    );
  }

  test(
    'deleting owner alias preserves agents until last protocol is removed',
    () async {
      secrets['oc.agentHostSecret.one'] = 'private';
      final s = await store(
        [profile('one'), profile('two', flavor: ServerFlavor.v2)],
        extra: {
          'oc.agentPhoneGate.one': '{"claude":{"fingerprint":"proof"}}',
          'oc.agentFeed.one': '[]',
          'oc.sessionAutoApproval.one.agents': '{"chat":true}',
        },
      );
      expect(
        s.retainedPhoneAgentPreferenceKeys('one'),
        contains('oc.agentPhoneGate.one'),
      );
      await s.remove('one');
      expect(cleaned, isEmpty);
      expect(secrets.containsKey('oc.agentHostSecret.one'), isTrue);
      expect(s.prefs.containsKey('oc.agentPhoneGate.one'), isTrue);
      expect(s.prefs.containsKey('oc.phoneAgentGateMigration.one'), isTrue);
      expect(s.prefs.containsKey('oc.sessionAutoApproval.one.agents'), isTrue);
      await s.load();
      expect(s.phoneAgentOwnerId('two'), 'one');
      await s.remove('two');
      expect(cleaned, ['one']);
      expect(secrets.containsKey('oc.agentHostSecret.one'), isFalse);
      expect(s.prefs.containsKey('oc.agentPhoneGate.one'), isFalse);
      expect(s.prefs.containsKey('oc.phoneAgentGateMigration.one'), isFalse);
      expect(s.prefs.containsKey('oc.agentFeed.one'), isFalse);
      expect(s.prefs.containsKey('oc.sessionAutoApproval.one.agents'), isFalse);
    },
  );
}
