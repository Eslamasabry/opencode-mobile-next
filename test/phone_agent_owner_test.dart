import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

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
          'oc.agentPhoneGate.one': '{"claude":"fingerprint"}',
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
        extra: {'oc.agentPhoneGate.checked': '{"claude":"proof"}'},
      );
      expect(s.phoneAgentOwnerId('empty'), 'checked');
      expect(s.phoneAgentOwnerId('two'), 'checked');
      expect(s.phoneAgentOwnerId('termux'), 'termux');
      expect(s.phoneAgentOwnerId('remote'), 'remote');
      expect(secrets.keys, ['oc.agentHostSecret.checked']);
    },
  );

  test(
    'deleting owner alias preserves agents until last protocol is removed',
    () async {
      secrets['oc.agentHostSecret.one'] = 'private';
      final s = await store(
        [profile('one'), profile('two', flavor: ServerFlavor.v2)],
        extra: {
          'oc.agentPhoneGate.one': '{"claude":"proof"}',
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
      expect(s.prefs.containsKey('oc.sessionAutoApproval.one.agents'), isTrue);
      await s.load();
      expect(s.phoneAgentOwnerId('two'), 'one');
      await s.remove('two');
      expect(cleaned, ['one']);
      expect(secrets.containsKey('oc.agentHostSecret.one'), isFalse);
      expect(s.prefs.containsKey('oc.agentPhoneGate.one'), isFalse);
      expect(s.prefs.containsKey('oc.agentFeed.one'), isFalse);
      expect(s.prefs.containsKey('oc.sessionAutoApproval.one.agents'), isFalse);
    },
  );
}
