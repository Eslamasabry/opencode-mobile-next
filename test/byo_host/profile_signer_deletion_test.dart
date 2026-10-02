import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/byo_host.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'ssh_runner_test.dart' show FakeSigner;

class RefusingSigner extends FakeSigner {
  bool refuse = false;
  @override
  Future<void> deleteIdentity(String id) async {
    if (refuse) throw StateError('SECRET_NATIVE_FAILURE');
    await super.deleteIdentity(id);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final vault = <String, String>{};
  setUp(() {
    vault.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (call) async {
            final key = call.arguments?['key'] as String?;
            switch (call.method) {
              case 'read':
                return vault[key];
              case 'readAll':
                return Map<String, String>.of(vault);
              case 'delete':
                vault.remove(key);
                return null;
              case 'write':
                vault[key!] = call.arguments['value'] as String;
                return null;
            }
            return null;
          },
        );
  });
  tearDown(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          null,
        ),
  );

  test(
    'profile sweep deletes native identity, preserving other phone keys',
    () async {
      SharedPreferences.setMockInitialValues({
        'oc.byoHost.one': 'record',
        'oc.byoHost.two': 'keep',
      });
      final prefs = await SharedPreferences.getInstance();
      final signer = RefusingSigner();
      vault.addAll({
        'oc.byoHostSecrets.one': 'envelope',
        'oc.byoHostSecrets.two': 'keep',
      });
      await ProfileStore(prefs: prefs, byoHostSigner: signer).remove('one');
      expect(signer.deleted, ['one']);
      expect(prefs.getString('oc.byoHost.one'), isNull);
      expect(vault['oc.byoHostSecrets.one'], isNull);
      expect(vault['oc.byoHostSecrets.two'], 'keep');
    },
  );

  test(
    'native deletion refusal retains journal and envelope with safe error',
    () async {
      SharedPreferences.setMockInitialValues({'oc.byoHost.one': 'record'});
      final prefs = await SharedPreferences.getInstance();
      final signer = RefusingSigner()..refuse = true;
      vault['oc.byoHostSecrets.one'] = 'envelope';
      await expectLater(
        ProfileStore(prefs: prefs, byoHostSigner: signer).remove('one'),
        throwsA(
          isA<ByoHostFailure>()
              .having((e) => e.code, 'code', ByoHostFailureCode.storage)
              .having(
                (e) => e.toString(),
                'safe',
                isNot(contains('SECRET_NATIVE_FAILURE')),
              ),
        ),
      );
      expect(prefs.getString('oc.byoHost.one'), 'record');
      expect(vault['oc.byoHostSecrets.one'], 'envelope');
    },
  );

  test(
    'reset sign-ins removes orphan native identities and owned envelopes',
    () async {
      SharedPreferences.setMockInitialValues({});
      final signer = RefusingSigner();
      vault.addAll({
        'oc.byoHostSecrets.orphan': 'envelope',
        'unrelated': 'keep',
      });
      await ProfileStore(
        prefs: await SharedPreferences.getInstance(),
        byoHostSigner: signer,
      ).resetSavedSignIns();
      expect(signer.resets, 1);
      expect(vault, {'unrelated': 'keep'});
    },
  );
}
