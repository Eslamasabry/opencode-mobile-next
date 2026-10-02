import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/host/byo_host_signer.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('oc/byo_host_signer');
  final calls = <MethodCall>[];
  late AndroidByoHostSigner signer;
  setUp(() {
    calls.clear();
    signer = AndroidByoHostSigner(channel: channel);
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          calls.add(call);
          if (call.method == 'ensureIdentity') {
            return {
              'keyAlias': 'oc.byoHostSsh.phone1',
              'publicKey': 'ecdsa-sha2-nistp256 AAAA',
            };
          }
          return null;
        });
  });
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test('identity contains only profile alias and public key', () async {
    final identity = await signer.ensureIdentity('phone1');
    expect(identity.keyAlias, 'oc.byoHostSsh.phone1');
    expect(identity.publicKey, startsWith('ecdsa-sha2-nistp256 '));
    expect(calls.single.arguments, {'profileId': 'phone1'});
  });
  test('agent lifecycle passes no private key or signing payload', () async {
    await signer.openAgent(
      profileId: 'phone1',
      socketPath: '/private/files/linux/ubuntu/tmp/.oa-0123456789abcdef/s',
      user: 'owner',
    );
    await signer.closeAgent('phone1');
    await signer.deleteIdentity('phone1');
    expect(calls.map((call) => call.method), [
      'openAgent',
      'closeAgent',
      'deleteIdentity',
    ]);
    expect((calls.first.arguments as Map).keys.toSet(), {
      'profileId',
      'socketPath',
      'user',
    });
  });
  test('reset covers orphan aliases without sending key material', () async {
    await signer.deleteAllIdentities();
    expect(calls.single.method, 'deleteAllIdentities');
    expect(calls.single.arguments, isEmpty);
  });
  test('unsafe aliases users and socket paths never reach native', () async {
    await expectLater(
      signer.ensureIdentity('../phone1'),
      throwsA(isA<ByoHostSignerException>()),
    );
    await expectLater(
      signer.openAgent(
        profileId: 'phone1',
        socketPath: '/tmp/agent',
        user: 'root',
      ),
      throwsA(isA<ByoHostSignerException>()),
    );
    await expectLater(
      signer.openAgent(
        profileId: 'phone1',
        socketPath: '/private/files/linux/ubuntu/tmp/.oa-0123456789abcdef/s',
        user: 'owner; id',
      ),
      throwsA(isA<ByoHostSignerException>()),
    );
    expect(calls, isEmpty);
  });
  test('platform errors never expose native detail', () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (_) async {
          throw PlatformException(code: 'failed', message: 'sensitive detail');
        });
    try {
      await signer.ensureIdentity('phone1');
      fail('expected signer error');
    } catch (error) {
      expect(error.toString(), 'ByoHostSignerException(unavailable)');
    }
  });
  test('unexpected identity alias is rejected', () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          channel,
          (_) async => {
            'keyAlias': 'oc.byoHostSsh.someoneElse',
            'publicKey': 'ecdsa-sha2-nistp256 AAAA',
          },
        );
    await expectLater(
      signer.ensureIdentity('phone1'),
      throwsA(isA<ByoHostSignerException>()),
    );
  });
}
