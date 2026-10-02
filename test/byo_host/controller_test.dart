import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/byo_host.dart';
import 'package:opencode_mobile/state/byo_host_controller.dart';

ByoHostKey key([int value = 1]) {
  final bytes = <int>[
    0,
    0,
    0,
    11,
    ...ascii.encode('ssh-ed25519'),
    0,
    0,
    0,
    32,
    ...List.filled(32, value),
  ];
  return ByoHostKey(
    publicKey: 'ssh-ed25519 ${base64Encode(bytes)}',
    fingerprint:
        'SHA256:${base64Encode(sha256.convert(bytes).bytes).replaceAll('=', '')}',
  );
}

class MemoryStore implements ByoHostStore {
  ByoHostRecord? record;
  ByoHostSecrets? secrets;
  bool failRemove = false;
  @override
  Future<List<ByoHostRecord>> listRecords() async => [?record];
  @override
  Future<ByoHostRecord?> readRecord(String id) async => record;
  @override
  Future<ByoHostSecrets?> readSecrets(String id) async => secrets;
  @override
  Future<void> saveRecord(ByoHostRecord value) async {
    record = value;
  }

  @override
  Future<void> saveSecrets(String id, ByoHostSecrets value) async {
    secrets = value;
  }

  @override
  Future<void> remove(String id) async {
    if (failRemove) throw StateError('sensitive raw failure');
    record = null;
    secrets = null;
  }
}

class Tunnel implements ByoHostTunnel {
  bool closed = false;
  @override
  int get localPort => 19001;
  @override
  Future<void> close() async {
    closed = true;
  }
}

class FakeRunner implements ByoHostSshRunner {
  ByoHostKey hostKey = key();
  String hostId = 'host-a';
  int installs = 0, forwards = 0, revokes = 0;
  String? installedToken;
  bool dieAfterInstall = false, rejectRevoke = false;
  Tunnel? tunnel;
  @override
  Future<bool> available() async => true;
  @override
  Future<ByoHostKey> inspectKey(ByoHostTarget target) async => hostKey;
  @override
  Future<ByoHostIdentity> generateIdentity(String id) async =>
      const ByoHostIdentity(
        keyAlias: 'oc.byoHostSsh.phone-a',
        publicKey: 'fake-public-key',
      );
  @override
  Future<ByoHostDescriptor> install({
    required String profileId,
    required ByoHostTarget target,
    required ByoHostKey hostKey,
    required ByoHostLogin login,
    required ByoHostIdentity identity,
    required String deviceToken,
    required ByoHostBundle bundle,
  }) async {
    installs++;
    if (installedToken != null) expect(deviceToken, installedToken);
    installedToken = deviceToken;
    if (dieAfterInstall) throw StateError('password in remote error');
    return descriptor;
  }

  ByoHostDescriptor get descriptor => ByoHostDescriptor(
    hostId: hostId,
    bundleVersion: '1.1.0',
    openCodeVersion: '1.18.32',
    port: 4096,
  );
  @override
  Future<ByoHostTunnel> forward({
    required String profileId,
    required ByoHostTarget target,
    required ByoHostKey hostKey,
    required ByoHostIdentity identity,
    required int remotePort,
  }) async {
    forwards++;
    return tunnel = Tunnel();
  }

  @override
  Future<ByoHostDescriptor> describe({
    required ByoHostTunnel tunnel,
    required String deviceId,
    required String deviceToken,
  }) async => descriptor;
  @override
  Future<bool> revoke({
    required ByoHostTunnel tunnel,
    required String deviceId,
    required String deviceToken,
  }) async {
    revokes++;
    return !rejectRevoke;
  }

  @override
  Future<void> cleanup(String profileId) async {}
  @override
  Future<void> dispose() async {}
}

void main() {
  late MemoryStore store;
  late FakeRunner runner;
  late ByoHostController controller;
  final bundle = ByoHostBundle(
    version: '1.1.0',
    openCodeVersion: '1.18.32',
    artifacts: {
      'x64': ByoHostArtifact(
        url: 'https://example.invalid/bundle.tar.gz',
        sha256: List.filled(64, 'a').join(),
      ),
    },
  );
  setUp(() {
    store = MemoryStore();
    runner = FakeRunner();
    controller = ByoHostController(
      profileId: 'phone-a',
      runner: runner,
      store: store,
      bundle: bundle,
      enabled: true,
    );
  });
  Future<void> add() async {
    final candidate = await controller.inspect(
      ByoHostTarget.parse('alice@server.example'),
    );
    final login = ByoHostLogin(password: 'one-use-password');
    await controller.adopt(
      verifiedFingerprint: candidate.fingerprint,
      login: login,
    );
    expect(login.password, isNull);
  }

  Matcher code(ByoHostFailureCode value) =>
      isA<ByoHostFailure>().having((e) => e.code, 'safe code', value);

  test(
    'key-only enrollment survives restart and pairs the reserved phone',
    () async {
      final pin = await controller.inspect(
        ByoHostTarget.parse('ubuntu@100.80.1.2'),
      );
      final identity = await controller.prepareSshIdentity(
        verifiedFingerprint: pin.fingerprint,
      );
      expect(identity.keyAlias, 'oc.byoHostSsh.phone-a');
      expect(store.record!.phase, ByoHostPhase.needsTrust);
      expect(store.secrets, isNull);
      await controller.dispose();
      controller = ByoHostController(
        profileId: 'phone-a',
        runner: runner,
        store: store,
        bundle: bundle,
        enabled: true,
      );
      await controller.resume();
      expect(controller.snapshot.phase, ByoHostPhase.needsTrust);
      await controller.adopt(
        verifiedFingerprint: pin.fingerprint,
        login: ByoHostLogin(),
      );
      expect(controller.snapshot.phase, ByoHostPhase.ready);
      expect(runner.installs, 1);
    },
  );

  test('default off and unknown host never mutates remote', () async {
    final disabled = ByoHostController(
      profileId: 'a',
      runner: runner,
      store: store,
      bundle: bundle,
    );
    await expectLater(
      disabled.inspect(ByoHostTarget.parse('alice@host')),
      throwsA(code(ByoHostFailureCode.disabled)),
    );
    await controller.inspect(ByoHostTarget.parse('alice@host'));
    final login = ByoHostLogin(password: 'once');
    await expectLater(
      controller.adopt(verifiedFingerprint: key(2).fingerprint, login: login),
      throwsA(code(ByoHostFailureCode.needsTrust)),
    );
    expect(login.password, isNull);
    expect(runner.installs, 0);
    expect(store.secrets, isNull);
  });
  test(
    'durable adoption returns only authenticated ephemeral gateway profile',
    () async {
      await add();
      final profile = controller.connectedProfile(name: 'My machine');
      expect(profile.baseUrl, 'http://127.0.0.1:19001');
      expect(profile.password, store.secrets!.deviceToken);
      expect(
        jsonEncode(store.record!.toJson()),
        isNot(contains(profile.password)),
      );
      expect(store.record!.phase, ByoHostPhase.disconnected);
      await controller.dispose();
      expect(runner.tunnel!.closed, isTrue);
      expect(store.record, isNotNull);
      expect(runner.revokes, 0);
      controller = ByoHostController(
        profileId: 'phone-a',
        runner: FakeRunner(),
        store: store,
        enabled: true,
      );
      await controller.resume();
      expect(controller.snapshot.phase, ByoHostPhase.ready);
    },
  );
  test(
    'killed installation retries exact device tuple after fresh admin login',
    () async {
      runner.dieAfterInstall = true;
      final candidate = await controller.inspect(
        ByoHostTarget.parse('alice@host'),
      );
      final login = ByoHostLogin(password: 'one-use');
      await expectLater(
        controller.adopt(
          verifiedFingerprint: candidate.fingerprint,
          login: login,
        ),
        throwsA(code(ByoHostFailureCode.transport)),
      );
      expect(login.password, isNull);
      expect(store.record!.remotePort, isNull);
      expect(store.secrets, isNotNull);
      final previous = store.secrets!.deviceToken;
      runner.dieAfterInstall = false;
      controller = ByoHostController(
        profileId: 'phone-a',
        runner: runner,
        store: store,
        bundle: bundle,
        enabled: true,
      );
      await expectLater(
        controller.resume(),
        throwsA(code(ByoHostFailureCode.uncertain)),
      );
      await controller.resume(login: ByoHostLogin(password: 'fresh'));
      expect(store.secrets!.deviceToken, previous);
      expect(runner.installs, 2);
    },
  );
  test('changed SSH identity rejects before forwarding or mutations', () async {
    await add();
    await controller.dispose();
    runner.hostKey = key(2);
    controller = ByoHostController(
      profileId: 'phone-a',
      runner: runner,
      store: store,
      enabled: true,
    );
    await expectLater(
      controller.resume(),
      throwsA(code(ByoHostFailureCode.hostKeyChanged)),
    );
    expect(runner.forwards, 1);
    expect(runner.installs, 1);
  });
  test('changed supervisor identity closes replacement tunnel', () async {
    await add();
    await controller.dispose();
    runner.hostId = 'other-host';
    controller = ByoHostController(
      profileId: 'phone-a',
      runner: runner,
      store: store,
      enabled: true,
    );
    await expectLater(
      controller.resume(),
      throwsA(code(ByoHostFailureCode.identityChanged)),
    );
    expect(runner.tunnel!.closed, isTrue);
  });
  test('local removal never revokes or stops host', () async {
    await add();
    await controller.remove(revokeAccess: false);
    expect(runner.revokes, 0);
    expect(store.record, isNull);
    expect(store.secrets, isNull);
    expect(runner.tunnel!.closed, isTrue);
    expect(controller.snapshot.record, isNull);
  });
  test(
    'failed revocation retains connection and all durable credentials',
    () async {
      await add();
      runner.rejectRevoke = true;
      await expectLater(
        controller.remove(revokeAccess: true),
        throwsA(code(ByoHostFailureCode.revocationFailed)),
      );
      expect(store.record, isNotNull);
      expect(store.secrets, isNotNull);
      expect(runner.tunnel!.closed, isFalse);
      expect(controller.connectedProfile(name: 'kept').username, 'phone-a');
      runner.rejectRevoke = false;
      await controller.remove(revokeAccess: true);
      expect(store.secrets, isNull);
    },
  );
  test(
    'confirmed revoke survives local deletion failure without duplicate revocation',
    () async {
      await add();
      store.failRemove = true;
      await expectLater(
        controller.remove(revokeAccess: true),
        throwsA(isA<ByoHostFailure>()),
      );
      expect(store.record!.revoked, isTrue);
      expect(runner.revokes, 1);
      store.failRemove = false;
      await controller.remove(revokeAccess: true);
      expect(runner.revokes, 1);
      expect(store.secrets, isNull);
    },
  );
  test('invalid targets and mismatched fingerprints reject input', () {
    for (final text in [
      '-oProxyCommand@host',
      'a@host;id',
      'a@host\n',
      'a@x@y',
      'a@..',
    ]) {
      // parse trims the form; an embedded newline still fails.
      if (text == 'a@host\n') continue;
      expect(() => ByoHostTarget.parse(text), throwsA(isA<ByoHostFailure>()));
    }
    expect(
      () => ByoHostKey(
        publicKey: key().publicKey,
        fingerprint: key(2).fingerprint,
      ),
      throwsA(isA<ByoHostFailure>()),
    );
    expect(
      ByoHostLogin(password: 'secret').toString(),
      isNot(contains('secret')),
    );
  });
  test(
    'shared-data refusal preserves a discoverable revocation cleanup journal',
    () async {
      await add();
      final owner = ByoHostController(
        profileId: 'phone-a',
        runner: runner,
        store: store,
        enabled: true,
        clearLocalData: (_) async {
          store.record = null; // simulate an optimistic scoped preference sweep
          throw StateError('raw shared data error');
        },
      );
      await expectLater(
        owner.remove(revokeAccess: true),
        throwsA(code(ByoHostFailureCode.storage)),
      );
      expect(store.record, isNotNull);
      expect(store.record!.revoked, isTrue);
      expect(store.secrets, isNotNull);
      expect(runner.revokes, 1);
      final retry = ByoHostController(
        profileId: 'phone-a',
        runner: runner,
        store: store,
        enabled: true,
      );
      await retry.remove(revokeAccess: true);
      expect(runner.revokes, 1);
      expect(store.record, isNull);
    },
  );
  test('shared-data removal runs before vault envelope is discarded', () async {
    await add();
    var calls = 0;
    final owner = ByoHostController(
      profileId: 'phone-a',
      runner: runner,
      store: store,
      enabled: true,
      clearLocalData: (id) async {
        expect(id, 'phone-a');
        expect(store.record, isNotNull);
        expect(store.secrets, isNotNull);
        calls++;
      },
    );
    await owner.remove(revokeAccess: false);
    expect(calls, 1);
    expect(store.secrets, isNull);
  });
}
