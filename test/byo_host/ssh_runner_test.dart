import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:crypto/crypto.dart';
import 'package:opencode_mobile/domain/byo_host.dart';
import 'package:opencode_mobile/host/byo_host_ssh_runner.dart';
import 'package:opencode_mobile/host/byo_host_signer.dart';
import 'package:opencode_mobile/host/byo_host_tailnet.dart';

String publicKey() =>
    'ssh-ed25519 ${base64Encode([0, 0, 0, 11, ...ascii.encode('ssh-ed25519'), 0, 0, 0, 32, ...List.generate(32, (index) => index)])}';

String devicePublicKey() {
  List<int> text(String value) => [
    0,
    0,
    0,
    value.length,
    ...ascii.encode(value),
  ];
  List<int> hex(String value) => List.generate(
    value.length ~/ 2,
    (i) => int.parse(value.substring(i * 2, i * 2 + 2), radix: 16),
  );
  final point = [
    4,
    ...hex('6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296'),
    ...hex('4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5'),
  ];
  return 'ecdsa-sha2-nistp256 ${base64Encode([...text('ecdsa-sha2-nistp256'), ...text('nistp256'), 0, 0, 0, point.length, ...point])}';
}

class FakeSigner implements ByoHostSigner {
  final List<String> ensured = [];
  final Map<String, String> agents = {};
  final List<String> closed = [];
  final List<String> deleted = [];
  int resets = 0;
  @override
  Future<ByoHostSignerIdentity> ensureIdentity(String profileId) async {
    ensured.add(profileId);
    return ByoHostSignerIdentity(
      keyAlias: 'oc.byoHostSsh.$profileId',
      publicKey: devicePublicKey(),
    );
  }

  @override
  Future<void> openAgent({
    required String profileId,
    required String socketPath,
    required String user,
  }) async {
    expect(user, 'ubuntu');
    expect(socketPath, contains('/linux/ubuntu/tmp/.oa-'));
    expect(await Directory(File(socketPath).parent.path).exists(), isTrue);
    agents[profileId] = socketPath;
  }

  @override
  Future<void> closeAgent(String profileId) async {
    closed.add(profileId);
    agents.remove(profileId);
  }

  @override
  Future<void> deleteIdentity(String profileId) async {
    deleted.add(profileId);
  }

  @override
  Future<void> deleteAllIdentities() async {
    resets++;
  }
}

ByoHostTailnetResolver fakeTailnet() => ByoHostTailnetResolver(
  lookup: (_) async => [InternetAddress('100.64.0.10')],
);

class FakeShell implements ByoHostLocalShell {
  FakeShell(this.root);
  final Directory root;
  final List<String> nativeScripts = [];
  final List<String> privateCommands = [];
  final List<String> stopped = [];
  String? installInput;
  final Map<String, String> observedFiles = {};
  Future<void> observeFiles() async {
    await for (final entry in Directory(
      '${root.path}/linux/ubuntu/tmp',
    ).list(recursive: true)) {
      if (entry is File) observedFiles[entry.path] = await entry.readAsString();
    }
  }

  final Map<String, ServerSocket> listeners = {};
  bool supported = true;
  bool failInstall = false;
  String failureOutput = 'PRIVATE_ERROR_SENTINEL';
  @override
  Future<bool> available() async => supported;
  String translate(String script) => script
      .replaceAll('/tmp/oc-byo-', '${root.path}/linux/ubuntu/tmp/oc-byo-')
      .replaceAll('/tmp/.oa-', '${root.path}/linux/ubuntu/tmp/.oa-');
  @override
  Future<int> run(String script, {required Duration timeout}) async {
    await observeFiles();
    nativeScripts.add(script);
    if (script.startsWith('umask')) {
      return (await Process.run('sh', ['-c', translate(script)])).exitCode;
    }
    final match = RegExp("^sh '([^']+)/command'").firstMatch(script)!;
    final dir = Directory(translate(match.group(1)!));
    final command = await File('${dir.path}/command').readAsString();
    privateCommands.add(command);
    var output = '';
    if (command.contains('ssh-keyscan')) {
      output = 'server.example ${publicKey()}\n';
    } else if (command.contains('uname -m')) {
      output = 'x86_64\n';
    } else if (command.contains('sh -s')) {
      installInput = await File('${dir.path}/input').readAsString();
      output = failInstall
          ? failureOutput
          : jsonEncode({
              'hostId': 'machine_1',
              'bundleVersion': '1.1.0',
              'openCodeVersion': '1.18.32',
              'port': 4096,
            });
    }
    await File('${dir.path}/result').writeAsString(output);
    return failInstall && command.contains('sh -s') ? 255 : 0;
  }

  @override
  Future<void> start(String name, String script, int port) async {
    await observeFiles();
    nativeScripts.add(script);
    final listener = await ServerSocket.bind(
      InternetAddress.loopbackIPv4,
      port,
    );
    listeners[name] = listener;
    listener.listen((socket) => socket.destroy());
  }

  @override
  Future<void> stop(String name) async {
    stopped.add(name);
    await listeners.remove(name)?.close();
  }

  @override
  Future<bool> running(String name) async => listeners.containsKey(name);
}

class TestTunnel implements ByoHostTunnel {
  TestTunnel(this.localPort);
  @override
  final int localPort;
  @override
  Future<void> close() async {}
}

void main() {
  late Directory root;
  late FakeShell shell;
  late BuiltinByoHostSshRunner runner;
  late FakeSigner signer;
  setUp(() async {
    root = await Directory.systemTemp.createTemp('byo-ssh-test-');
    await Directory('${root.path}/linux/ubuntu/tmp').create(recursive: true);
    shell = FakeShell(root);
    signer = FakeSigner();
    runner = BuiltinByoHostSshRunner(
      shell: shell,
      signer: signer,
      tailnet: fakeTailnet(),
      supportDirectory: () async => root,
    );
  });
  tearDown(() async {
    await runner.dispose();
    await root.delete(recursive: true);
  });

  test(
    'scans only a public key and computes SHA256 from SSH wire encoding',
    () async {
      final expected = BuiltinByoHostSshRunner.keyFromPublicKey(publicKey());
      final key = await runner.inspectKey(
        ByoHostTarget.parse('ubuntu@server.example'),
      );
      expect(key.publicKey, expected.publicKey);
      expect(key.fingerprint, expected.fingerprint);
      expect(
        shell.privateCommands.single,
        contains('ssh-keyscan -T 10 -t ed25519'),
      );
      expect(shell.privateCommands.single, isNot(contains('ssh -')));
      expect(shell.privateCommands.single, contains("'100.64.0.10'"));
      expect(shell.privateCommands.single, isNot(contains('server.example')));
      expect(
        await Directory('${root.path}/linux/ubuntu/tmp').list().toList(),
        isEmpty,
      );
      expect(
        () => BuiltinByoHostSshRunner.keyFromPublicKey(
          'ssh-ed25519 ${base64Encode(List.filled(51, 0))}',
        ),
        throwsA(isA<ByoHostFailure>()),
      );
    },
  );

  test(
    'key generation never returns secrets through native command/output',
    () async {
      final identity = await runner.generateIdentity('profile_1');
      expect(identity.keyAlias, 'oc.byoHostSsh.profile_1');
      expect(identity.publicKey, devicePublicKey());
      expect(signer.ensured, ['profile_1']);
      expect(shell.privateCommands, isEmpty);
      expect(shell.observedFiles, isEmpty);
      expect(
        shell.nativeScripts.join('\n'),
        isNot(contains('PRIVATE_KEY_SENTINEL')),
      );
      expect(
        await Directory('${root.path}/linux/ubuntu/tmp').list().toList(),
        isEmpty,
      );
    },
  );

  Future<ByoHostDescriptor> install(ByoHostLogin login) => runner.install(
    profileId: 'profile_1',
    target: ByoHostTarget.parse('ubuntu@server.example'),
    hostKey: BuiltinByoHostSshRunner.keyFromPublicKey(publicKey()),
    login: login,
    identity: ByoHostIdentity(
      keyAlias: 'oc.byoHostSsh.profile_1',
      publicKey: devicePublicKey(),
    ),
    deviceToken: 'DEVICE_TOKEN_SENTINEL_1234567890123456',
    bundle: ByoHostBundle(
      version: '1.1.0',
      openCodeVersion: '1.18.32',
      artifacts: {
        'x64': ByoHostArtifact(
          url: 'https://downloads.example.invalid/pinned.tar.gz',
          sha256: 'a' * 64,
        ),
      },
    ),
  );

  test(
    'installation uses private stdin, strict host pin, and bundled digest before execution',
    () async {
      final login = ByoHostLogin(password: 'PASSWORD_SENTINEL');
      final result = await install(login);
      expect(result.hostId, 'machine_1');
      expect(login.password, isNull);
      final scripts = shell.nativeScripts.join('\n');
      for (final secret in [
        'PASSWORD_SENTINEL',
        'DEVICE_KEY_SENTINEL',
        'DEVICE_TOKEN_SENTINEL',
      ]) {
        expect(scripts, isNot(contains(secret)));
      }
      final command = shell.privateCommands.last;
      for (final option in [
        'StrictHostKeyChecking=yes',
        'IdentityAgent=none',
        'ForwardAgent=no',
        'PermitLocalCommand=no',
        'HostKeyAlias=oc-byo-host',
        'GlobalKnownHostsFile=/dev/null',
      ]) {
        expect(command, contains(option));
      }
      expect(command, isNot(contains('PASSWORD_SENTINEL')));
      expect(command, contains("'100.64.0.10'"));
      expect(command, isNot(contains('server.example')));
      expect(
        shell.observedFiles.keys.any(
          (path) => path.endsWith('/identity') || path.endsWith('/private_key'),
        ),
        isFalse,
      );
      expect(signer.ensured, isEmpty);
      expect(signer.agents, isEmpty);
      expect(shell.installInput, contains('DEVICE_TOKEN_SENTINEL'));
      expect(
        shell.installInput!.indexOf('sha256sum -c'),
        lessThan(shell.installInput!.indexOf('sh "\$work/install.sh"')),
      );
      expect(shell.installInput, contains('a' * 64));
      expect(shell.installInput, isNot(contains('.sha256')));
      expect(
        await Directory('${root.path}/linux/ubuntu/tmp').list().toList(),
        isEmpty,
      );
    },
  );

  test(
    'failed install hides remote output, consumes login and deletes private files',
    () async {
      shell.failInstall = true;
      final login = ByoHostLogin(password: 'PASSWORD_SENTINEL');
      await expectLater(
        install(login),
        throwsA(
          isA<ByoHostFailure>()
              .having((e) => e.code, 'code', ByoHostFailureCode.uncertain)
              .having(
                (e) => e.toString(),
                'safe',
                isNot(contains('PRIVATE_ERROR_SENTINEL')),
              ),
        ),
      );
      expect(login.password, isNull);
      expect(
        await Directory('${root.path}/linux/ubuntu/tmp').list().toList(),
        isEmpty,
      );
    },
  );

  test('changed host key is rejected before bootstrap success', () async {
    shell.failInstall = true;
    shell.failureOutput = 'Host key verification failed';
    final login = ByoHostLogin(password: 'PASSWORD_SENTINEL');
    await expectLater(
      install(login),
      throwsA(
        isA<ByoHostFailure>().having(
          (error) => error.code,
          'code',
          ByoHostFailureCode.hostKeyChanged,
        ),
      ),
    );
    expect(login.password, isNull);
    expect(signer.agents, isEmpty);
    expect(
      await Directory('${root.path}/linux/ubuntu/tmp').list().toList(),
      isEmpty,
    );
  });

  test(
    'unavailable runner consumes initial credentials without shell activity',
    () async {
      shell.supported = false;
      final login = ByoHostLogin(password: 'PASSWORD_SENTINEL');
      await expectLater(
        install(login),
        throwsA(
          isA<ByoHostFailure>().having(
            (e) => e.code,
            'code',
            ByoHostFailureCode.unavailable,
          ),
        ),
      );
      expect(login.password, isNull);
      expect(shell.nativeScripts, isEmpty);
    },
  );

  test(
    'ambiguous password plus passphrase authentication is refused',
    () async {
      final login = ByoHostLogin(
        password: 'PASSWORD_SENTINEL',
        passphrase: 'PHRASE_SENTINEL',
      );
      await expectLater(
        install(login),
        throwsA(
          isA<ByoHostFailure>().having(
            (e) => e.code,
            'code',
            ByoHostFailureCode.authentication,
          ),
        ),
      );
      expect(shell.privateCommands, isEmpty);
      expect(login.passphrase, isNull);
    },
  );

  for (final login in [
    ByoHostLogin(privateKey: '-----BEGIN OPENSSH PRIVATE KEY-----'),
    ByoHostLogin(passphrase: 'PHRASE_SENTINEL'),
  ]) {
    test(
      'imported key/passphrase is refused without writing anything: ${login.privateKey != null ? 'key' : 'passphrase'}',
      () async {
        await expectLater(
          install(login),
          throwsA(
            isA<ByoHostFailure>().having(
              (error) => error.code,
              'code',
              ByoHostFailureCode.authentication,
            ),
          ),
        );
        expect(login.privateKey, isNull);
        expect(login.passphrase, isNull);
        expect(shell.nativeScripts, isEmpty);
        expect(shell.observedFiles, isEmpty);
        expect(signer.ensured, isEmpty);
      },
    );
  }

  test(
    'non-tailnet SSH is refused before scan login or signer activity',
    () async {
      final target = ByoHostTarget.parse('ubuntu@203.0.113.7');
      final denial = throwsA(
        isA<ByoHostFailure>().having(
          (error) => error.code,
          'code',
          ByoHostFailureCode.tailnetRequired,
        ),
      );
      await expectLater(runner.inspectKey(target), denial);
      final login = ByoHostLogin(password: 'PASSWORD_SENTINEL');
      await expectLater(
        runner.install(
          profileId: 'profile_1',
          target: target,
          hostKey: BuiltinByoHostSshRunner.keyFromPublicKey(publicKey()),
          login: login,
          identity: ByoHostIdentity(
            keyAlias: 'oc.byoHostSsh.profile_1',
            publicKey: devicePublicKey(),
          ),
          deviceToken: 't' * 32,
          bundle: ByoHostBundle(
            version: '1.1.0',
            openCodeVersion: '1.18.32',
            artifacts: {
              'x64': ByoHostArtifact(
                url: 'https://downloads.example.invalid/pinned.tar.gz',
                sha256: 'a' * 64,
              ),
            },
          ),
        ),
        denial,
      );
      await expectLater(
        runner.forward(
          profileId: 'profile_1',
          target: target,
          hostKey: BuiltinByoHostSshRunner.keyFromPublicKey(publicKey()),
          identity: ByoHostIdentity(
            keyAlias: 'oc.byoHostSsh.profile_1',
            publicKey: devicePublicKey(),
          ),
          remotePort: 4096,
        ),
        denial,
      );
      expect(login.password, isNull);
      expect(shell.nativeScripts, isEmpty);
      expect(signer.ensured, isEmpty);
      expect(shell.listeners, isEmpty);
    },
  );

  test(
    'tunnel uses public key plus native agent and never writes a private key',
    () async {
      final tunnel = await runner.forward(
        profileId: 'profile_1',
        target: ByoHostTarget.parse('ubuntu@server.example'),
        hostKey: BuiltinByoHostSshRunner.keyFromPublicKey(publicKey()),
        identity: ByoHostIdentity(
          keyAlias: 'oc.byoHostSsh.profile_1',
          publicKey: devicePublicKey(),
        ),
        remotePort: 4096,
      );
      expect(shell.listeners, hasLength(1));
      expect(shell.nativeScripts.last, contains('-N -L'));
      expect(shell.nativeScripts.last, contains("'100.64.0.10'"));
      expect(shell.nativeScripts.last, isNot(contains('server.example')));
      expect(
        shell.nativeScripts.last,
        contains('127.0.0.1:${tunnel.localPort}:127.0.0.1:4096'),
      );
      expect(
        shell.nativeScripts.join('\n'),
        isNot(contains('DEVICE_KEY_SENTINEL')),
      );
      final dirs = await Directory(
        '${root.path}/linux/ubuntu/tmp',
      ).list().toList();
      expect(dirs, hasLength(2));
      final work = dirs.singleWhere((entry) => entry.path.contains('/oc-byo-'));
      expect(
        await File('${work.path}/device.pub').readAsString(),
        '${devicePublicKey()}\n',
      );
      expect(await File('${work.path}/identity').exists(), isFalse);
      expect(signer.agents.keys, ['profile_1']);
      expect(shell.nativeScripts.last, contains('IdentityAgent=/tmp/.oa-'));
      expect(
        shell.nativeScripts.last,
        contains('PubkeyAcceptedAlgorithms=ecdsa-sha2-nistp256'),
      );
      expect(
        shell.observedFiles.keys.any(
          (path) => path.endsWith('/identity') || path.endsWith('/private_key'),
        ),
        isFalse,
      );
      expect(
        shell.observedFiles.values.join('\n'),
        isNot(contains('PRIVATE KEY')),
      );
      expect(shell.privateCommands.join('\n'), isNot(contains('ssh-keygen')));
      await tunnel.close();
      await tunnel.close();
      expect(shell.listeners, isEmpty);
      expect(signer.agents, isEmpty);
      expect(shell.stopped, hasLength(1));
      expect(
        await Directory('${root.path}/linux/ubuntu/tmp').list().toList(),
        isEmpty,
      );
      await expectLater(
        runner.describe(
          tunnel: tunnel,
          deviceId: 'profile_1',
          deviceToken: 't' * 32,
        ),
        throwsA(isA<ByoHostFailure>()),
      );
    },
  );

  test(
    'startup recovers only this profile orphan, preserving live and foreign data',
    () async {
      final identity = ByoHostIdentity(
        keyAlias: 'oc.byoHostSsh.profile_1',
        publicKey: devicePublicKey(),
      );
      final tunnel = await runner.forward(
        profileId: 'profile_1',
        target: ByoHostTarget.parse('ubuntu@server.example'),
        hostKey: BuiltinByoHostSshRunner.keyFromPublicKey(publicKey()),
        identity: identity,
        remotePort: 4096,
      );
      final dirs = await Directory(
        '${root.path}/linux/ubuntu/tmp',
      ).list().toList();
      final live = dirs.singleWhere((entry) => entry.path.contains('/oc-byo-'));
      final stale = Directory(
        '${live.path.substring(0, live.path.length - 24)}${'a' * 24}',
      );
      await stale.create();
      await File('${stale.path}/identity').writeAsString('OLD_SECRET');
      final foreign = Directory('${root.path}/linux/ubuntu/tmp/foreign');
      await foreign.create();
      await runner.generateIdentity('profile_1');
      expect(await stale.exists(), isFalse);
      expect(await live.exists(), isTrue);
      expect(await foreign.exists(), isTrue);
      expect(shell.stopped, contains('byo-${'a' * 24}'));
      await tunnel.close();
    },
  );

  test(
    'fresh runner cleanup erases orphan without reconnect or credential creation',
    () async {
      final digest = sha256
          .convert(utf8.encode('profile_1'))
          .toString()
          .substring(0, 16);
      final orphan = Directory(
        '${root.path}/linux/ubuntu/tmp/oc-byo-$digest-${'b' * 24}',
      );
      await orphan.create();
      await File('${orphan.path}/identity').writeAsString('OLD_SECRET');
      final foreign = Directory('${root.path}/linux/ubuntu/tmp/oc-byo-foreign');
      await foreign.create();
      final fresh = BuiltinByoHostSshRunner(
        shell: shell,
        signer: signer,
        tailnet: fakeTailnet(),
        supportDirectory: () async => root,
      );
      try {
        await fresh.cleanup('profile_1');
        await fresh.cleanup('profile_1');
        expect(await orphan.exists(), isFalse);
        expect(await foreign.exists(), isTrue);
        expect(shell.stopped, ['byo-${'b' * 24}']);
        expect(shell.nativeScripts, isEmpty);
        expect(signer.ensured, isEmpty);
        expect(signer.deleted, ['profile_1', 'profile_1']);
      } finally {
        await fresh.dispose();
      }
    },
  );

  test('explicit cleanup preserves active keys until tunnel closed', () async {
    final tunnel = await runner.forward(
      profileId: 'profile_1',
      target: ByoHostTarget.parse('ubuntu@server.example'),
      hostKey: BuiltinByoHostSshRunner.keyFromPublicKey(publicKey()),
      identity: ByoHostIdentity(
        keyAlias: 'oc.byoHostSsh.profile_1',
        publicKey: devicePublicKey(),
      ),
      remotePort: 4096,
    );
    await expectLater(
      runner.cleanup('profile_1'),
      throwsA(
        isA<ByoHostFailure>().having(
          (e) => e.code,
          'code',
          ByoHostFailureCode.busy,
        ),
      ),
    );
    expect(signer.agents.keys, ['profile_1']);
    expect(signer.deleted, isEmpty);
    expect(shell.listeners, hasLength(1));
    expect(
      await Directory('${root.path}/linux/ubuntu/tmp').list().toList(),
      hasLength(2),
    );
    await tunnel.close();
    await runner.cleanup('profile_1');
    expect(
      await Directory('${root.path}/linux/ubuntu/tmp').list().toList(),
      isEmpty,
    );
  });

  test(
    'cleanup unavailable fails closed instead of claiming erased secrets',
    () async {
      shell.supported = false;
      await expectLater(
        runner.cleanup('profile_1'),
        throwsA(
          isA<ByoHostFailure>().having(
            (e) => e.code,
            'code',
            ByoHostFailureCode.unavailable,
          ),
        ),
      );
      expect(shell.nativeScripts, isEmpty);
    },
  );

  for (final entry in <String, ByoHostFailureCode>{
    'unsupportedHost': ByoHostFailureCode.unsupportedHost,
    'checksumMismatch': ByoHostFailureCode.checksum,
    'userServiceUnavailable': ByoHostFailureCode.unavailable,
    'hostBusy': ByoHostFailureCode.busy,
    'sshPolicyRequired': ByoHostFailureCode.sshPolicyRequired,
    'invalidPairing': ByoHostFailureCode.protocol,
    'unsupportedBundle': ByoHostFailureCode.bundleUnavailable,
  }.entries) {
    test(
      'installer ${entry.key} becomes fixed typed failure without stderr',
      () async {
        shell.failInstall = true;
        shell.failureOutput =
            'PRIVATE_ERROR_SENTINEL\n${jsonEncode({'error': entry.key})}\n';
        await expectLater(
          install(ByoHostLogin(password: 'PASSWORD_SENTINEL')),
          throwsA(
            isA<ByoHostFailure>()
                .having((e) => e.code, 'code', entry.value)
                .having(
                  (e) => e.toString(),
                  'safe error',
                  isNot(contains('PRIVATE_ERROR_SENTINEL')),
                ),
          ),
        );
        expect(
          await Directory('${root.path}/linux/ubuntu/tmp').list().toList(),
          isEmpty,
        );
      },
    );
  }

  test('target validation refuses shell/SSH option injection', () {
    for (final target in [
      'ubuntu@-oProxyCommand=evil',
      'ubuntu@host;evil',
      'ubuntu@\$(evil)',
      '-o@host',
      'ubuntu@host\nother',
    ]) {
      expect(() => ByoHostTarget.parse(target), throwsA(isA<ByoHostFailure>()));
    }
  });

  test(
    'symlink shared-files boundary is rejected before any key write',
    () async {
      await Directory('${root.path}/linux/ubuntu/tmp').delete();
      await Link('${root.path}/linux/ubuntu/tmp').create(root.path);
      await expectLater(
        runner.generateIdentity('profile_1'),
        throwsA(
          isA<ByoHostFailure>().having(
            (e) => e.code,
            'code',
            ByoHostFailureCode.storage,
          ),
        ),
      );
      expect(shell.nativeScripts, isEmpty);
    },
  );

  test(
    'descriptor and revoke use authenticated loopback without redirects',
    () async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final seen = <String>[];
      server.listen((request) async {
        seen.add(request.uri.path);
        expect(
          request.headers.value(HttpHeaders.authorizationHeader),
          'Basic ${base64Encode(utf8.encode('profile_1:${'t' * 32}'))}',
        );
        request.response.headers.contentType = ContentType.json;
        request.response.write(
          jsonEncode(
            request.uri.path == '/_oc/host'
                ? {
                    'hostId': 'machine_1',
                    'bundleVersion': '1.1.0',
                    'openCodeVersion': '1.18.32',
                    'port': 4096,
                  }
                : {'revoked': true},
          ),
        );
        await request.response.close();
      });
      try {
        final tunnel = TestTunnel(server.port);
        expect(
          (await runner.describe(
            tunnel: tunnel,
            deviceId: 'profile_1',
            deviceToken: 't' * 32,
          )).hostId,
          'machine_1',
        );
        expect(
          await runner.revoke(
            tunnel: tunnel,
            deviceId: 'profile_1',
            deviceToken: 't' * 32,
          ),
          isTrue,
        );
        expect(seen, ['/_oc/host', '/_oc/revoke']);
      } finally {
        await server.close(force: true);
      }
    },
  );

  test(
    'redirect cannot exfiltrate Basic credentials and failed revoke returns false',
    () async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      var calls = 0;
      server.listen((request) async {
        calls++;
        request.response.statusCode = HttpStatus.temporaryRedirect;
        request.response.headers.set(
          HttpHeaders.locationHeader,
          'http://127.0.0.1:${server.port}/exfiltrate',
        );
        await request.response.close();
      });
      try {
        expect(
          await runner.revoke(
            tunnel: TestTunnel(server.port),
            deviceId: 'profile_1',
            deviceToken: 't' * 32,
          ),
          isFalse,
        );
        expect(calls, 1);
      } finally {
        await server.close(force: true);
      }
    },
  );
}
