import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:crypto/crypto.dart';
import 'package:opencode_mobile/domain/byo_host.dart';
import 'package:opencode_mobile/host/byo_host_ssh_runner.dart';

String publicKey() =>
    'ssh-ed25519 ${base64Encode([0, 0, 0, 11, ...ascii.encode('ssh-ed25519'), 0, 0, 0, 32, ...List.generate(32, (index) => index)])}';

class FakeShell implements ByoHostLocalShell {
  FakeShell(this.root);
  final Directory root;
  final List<String> nativeScripts = [];
  final List<String> privateCommands = [];
  final List<String> stopped = [];
  String? installInput;
  final Map<String, ServerSocket> listeners = {};
  bool supported = true;
  bool failInstall = false;
  String failureOutput = 'PRIVATE_ERROR_SENTINEL';
  @override
  Future<bool> available() async => supported;
  String translate(String script) => script.replaceAll(
    '/tmp/oc-byo-',
    '${root.path}/linux/ubuntu/tmp/oc-byo-',
  );
  @override
  Future<int> run(String script, {required Duration timeout}) async {
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
    } else if (command.contains('ssh-keygen')) {
      await File('${dir.path}/identity').writeAsString('PRIVATE_KEY_SENTINEL');
      await File(
        '${dir.path}/identity.pub',
      ).writeAsString('${publicKey()} oc-byo');
    } else if (command.contains('uname -m')) {
      output = 'x86_64\n';
    } else if (command.contains('sh -s')) {
      installInput = await File('${dir.path}/input').readAsString();
      output = failInstall
          ? failureOutput
          : jsonEncode({
              'hostId': 'machine_1',
              'bundleVersion': '1.0.0',
              'openCodeVersion': '1.18.32',
              'port': 4096,
            });
    }
    await File('${dir.path}/result').writeAsString(output);
    return failInstall && command.contains('sh -s') ? 255 : 0;
  }

  @override
  Future<void> start(String name, String script, int port) async {
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
  setUp(() async {
    root = await Directory.systemTemp.createTemp('byo-ssh-test-');
    await Directory('${root.path}/linux/ubuntu/tmp').create(recursive: true);
    shell = FakeShell(root);
    runner = BuiltinByoHostSshRunner(
      shell: shell,
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
      expect(identity.privateKey, 'PRIVATE_KEY_SENTINEL');
      expect(identity.publicKey, publicKey());
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
      privateKey: 'DEVICE_KEY_SENTINEL',
      publicKey: publicKey(),
    ),
    deviceToken: 'DEVICE_TOKEN_SENTINEL_1234567890123456',
    bundle: ByoHostBundle(
      version: '1.0.0',
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

  test('tunnel holds key only until exact service closes', () async {
    final tunnel = await runner.forward(
      profileId: 'profile_1',
      target: ByoHostTarget.parse('ubuntu@server.example'),
      hostKey: BuiltinByoHostSshRunner.keyFromPublicKey(publicKey()),
      identity: ByoHostIdentity(
        privateKey: 'DEVICE_KEY_SENTINEL',
        publicKey: publicKey(),
      ),
      remotePort: 4096,
    );
    expect(shell.listeners, hasLength(1));
    expect(shell.nativeScripts.last, contains('-N -L'));
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
    expect(dirs, hasLength(1));
    expect(
      await File('${dirs.single.path}/identity').readAsString(),
      'DEVICE_KEY_SENTINEL',
    );
    await tunnel.close();
    await tunnel.close();
    expect(shell.listeners, isEmpty);
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
  });

  test(
    'startup recovers only this profile orphan, preserving live and foreign data',
    () async {
      final identity = ByoHostIdentity(
        privateKey: 'DEVICE_KEY_SENTINEL',
        publicKey: publicKey(),
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
      final live = dirs.single;
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
        supportDirectory: () async => root,
      );
      try {
        await fresh.cleanup('profile_1');
        await fresh.cleanup('profile_1');
        expect(await orphan.exists(), isFalse);
        expect(await foreign.exists(), isTrue);
        expect(shell.stopped, ['byo-${'b' * 24}']);
        expect(shell.nativeScripts, isEmpty);
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
      identity: ByoHostIdentity(privateKey: 'LIVE_KEY', publicKey: publicKey()),
      remotePort: 4096,
    );
    await runner.cleanup('profile_1');
    expect(shell.listeners, hasLength(1));
    expect(
      await Directory('${root.path}/linux/ubuntu/tmp').list().toList(),
      hasLength(1),
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
                    'bundleVersion': '1.0.0',
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
