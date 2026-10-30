import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_probe.dart' show ServerFlavor;
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/termux/opencode_ubuntu_setup.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel(BuiltinLinux.channelName);
  final calls = <MethodCall>[];
  Object? Function(MethodCall call)? answer;

  setUp(() {
    calls.clear();
    answer = null;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          calls.add(call);
          return answer?.call(call);
        });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  group('bridge', () {
    test('status reads asked at the same time share one call; a read after '
        'another request is fresh', () async {
      answer = (call) => call.method == 'status' ? {'installed': true} : null;
      final linux = BuiltinLinux();
      final reads = await Future.wait([
        linux.status(),
        BuiltinLinux().status(),
        linux.status(),
      ]);
      expect(calls.map((c) => c.method), ['status']);
      expect(reads.every((s) => s.installed), isTrue);

      calls.clear();
      final before = linux.status();
      await linux.startServer('true');
      final after = linux.status();
      await Future.wait([before, after]);
      expect(calls.map((c) => c.method), ['status', 'startServer', 'status']);
    });

    test('status reads the contract map', () async {
      answer = (_) => {
        'installed': true,
        'phase': 'ready',
        'message': null,
        'serverRunning': true,
        'serverPort': 4097,
        'abi': 'arm64-v8a',
        'bytesUsed': 734003200,
      };
      final status = await BuiltinLinux().status();
      expect(calls.single.method, 'status');
      expect(status.installed, isTrue);
      expect(status.phase, BuiltinLinuxPhase.ready);
      expect(status.message, isNull);
      expect(status.serverRunning, isTrue);
      expect(status.serverPort, 4097);
      expect(status.abi, 'arm64-v8a');
      expect(status.bytesUsed, 734003200);
    });

    test('an unknown phase and missing fields read as idle', () async {
      answer = (_) => {'phase': 'something-new'};
      final status = await BuiltinLinux().status();
      expect(status.installed, isFalse);
      expect(status.phase, BuiltinLinuxPhase.idle);
      expect(status.serverRunning, isFalse);
      expect(status.bytesUsed, isNull);
      expect(status.restorePhase, BuiltinServerRestorePhase.idle);
      expect(status.restoreReason, isNull);
    });

    test('restoration status exposes fixed state and timeout reason', () async {
      answer = (_) => {
        'restorePhase': 'unavailable',
        'restoreReason': 'systemTimeout',
      };
      final status = await BuiltinLinux().status();
      expect(status.restorePhase, BuiltinServerRestorePhase.unavailable);
      expect(status.restoreReason, BuiltinServerRestoreReason.systemTimeout);
    });

    test('unknown restoration status stays unavailable without raw copy', () {
      final status = BuiltinLinuxStatus.fromMap({
        'restorePhase': 'unexpected',
        'restoreReason': 'private native error',
      });
      expect(status.restorePhase, BuiltinServerRestorePhase.unavailable);
      expect(status.restoreReason, BuiltinServerRestoreReason.ownershipUnknown);
    });

    test('authored start passes only canonical restoration metadata', () async {
      await BuiltinLinux().startServer(
        'serve',
        restoreRecipe: const BuiltinServerRestoreRecipe(
          profileId: 'phone-profile',
          runtime: TermuxRuntime.openCode2,
        ),
      );
      expect(calls.single.method, 'startServer');
      expect(calls.single.arguments, {
        'script': 'serve',
        'port': 4097,
        'restoreRecipe': {
          'version': 1,
          'profileId': 'phone-profile',
          'runtime': 'openCode2',
        },
      });
    });

    test(
      'restoration start failures expose safe copy and way forward',
      () async {
        answer = (_) => throw PlatformException(
          code: 'native_internal_error',
          message: 'private native error',
        );
        await expectLater(
          BuiltinLinux().startServer(
            'serve',
            restoreRecipe: const BuiltinServerRestoreRecipe(
              profileId: 'phone-profile',
              runtime: TermuxRuntime.openCode2,
            ),
          ),
          throwsA(
            isA<BuiltinLinuxException>()
                .having((e) => e.code, 'code', 'server_start_unavailable')
                .having(
                  (e) => e.message,
                  'message',
                  'The phone server could not start. Open setup and try again.',
                ),
          ),
        );
      },
    );

    test('a start failure keeps the phone\'s details', () async {
      answer = (_) => throw PlatformException(
        code: 'builtin_linux',
        message: 'The phone operation could not finish. Try again.',
        details: 'IllegalStateException: Check failed.',
      );
      await expectLater(
        BuiltinLinux().startServer('serve'),
        throwsA(
          isA<BuiltinLinuxException>().having(
            (e) => e.details,
            'details',
            'IllegalStateException: Check failed.',
          ),
        ),
      );
    });

    test('run sends the script and timeout and reads the result', () async {
      answer = (_) => {'exitCode': 3, 'output': 'boom'};
      final result = await BuiltinLinux().run(
        'echo hi',
        timeout: const Duration(minutes: 5),
      );
      expect(calls.single.method, 'run');
      expect(calls.single.arguments, {
        'script': 'echo hi',
        'timeoutSeconds': 300,
      });
      expect(result.exitCode, 3);
      expect(result.output, 'boom');
      expect(result.ok, isFalse);
    });

    test('every other method speaks the contract', () async {
      answer = (call) => call.method == 'serverLog' ? 'line\n' : null;
      final linux = BuiltinLinux();
      await linux.installUbuntu();
      await linux.startServer('serve', port: 4097);
      await linux.stopServer();
      expect(await linux.serverLog(tailBytes: 100), 'line\n');
      final beforeRemoval = setupToolsChanged.value;
      await linux.uninstall();
      expect(setupToolsChanged.value, beforeRemoval + 1);
      expect(calls.map((call) => call.method), [
        'installUbuntu',
        'startServer',
        'stopServer',
        'serverLog',
        'removeRuntime',
      ]);
      expect(calls[1].arguments, {'script': 'serve', 'port': 4097});
      expect(calls[3].arguments, {'tailBytes': 100});
    });

    test('a platform error becomes the human message', () async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            throw PlatformException(
              code: 'download_failed',
              message: 'Could not download Ubuntu: no network',
            );
          });
      await expectLater(
        BuiltinLinux().installUbuntu(),
        throwsA(
          isA<BuiltinLinuxException>()
              .having((e) => e.message, 'message', contains('no network'))
              .having((e) => e.code, 'code', 'download_failed'),
        ),
      );
    });

    test('recovery map errors expose only safe copy and code', () async {
      answer = (_) => throw PlatformException(
        code: 'raw_native',
        message: 'Raw JSON parser detail',
        details: {'private': 'diagnostic'},
      );
      final linux = BuiltinLinux();
      final operations = [
        () => linux.stageServerRecovery('phone', {'version': 1}),
        () => linux.bindServerRecovery(profileId: 'phone', enabled: true),
        () => linux.serverRecoveryBudget('phone'),
        () => linux.updateServerRecoveryReceipt('phone', {}),
        () => linux.confirmManualServerStart('phone'),
      ];
      for (final operation in operations) {
        await expectLater(
          operation(),
          throwsA(
            isA<BuiltinLinuxException>()
                .having(
                  (error) => error.message,
                  'safe copy',
                  'The phone server could not restart.',
                )
                .having(
                  (error) => error.code,
                  'safe code',
                  'recovery_unavailable',
                ),
          ),
        );
      }
    });

    test(
      'recovery receipt list errors expose only safe copy and code',
      () async {
        answer = (_) => throw PlatformException(
          code: 'raw_native',
          message: 'Raw JSON parser detail',
        );
        await expectLater(
          BuiltinLinux().serverRecoveryReceipts('phone'),
          throwsA(
            isA<BuiltinLinuxException>()
                .having(
                  (error) => error.message,
                  'safe copy',
                  'The phone server could not restart.',
                )
                .having(
                  (error) => error.code,
                  'safe code',
                  'recovery_unavailable',
                ),
          ),
        );
        answer = (_) => ['malformed'];
        await expectLater(
          BuiltinLinux().serverRecoveryReceipts('phone'),
          throwsA(
            isA<BuiltinLinuxException>().having(
              (error) => error.code,
              'safe code',
              'recovery_unavailable',
            ),
          ),
        );
      },
    );

    test('a build without the channel says so', () async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
      await expectLater(
        BuiltinLinux().status(),
        throwsA(
          isA<BuiltinLinuxException>().having(
            (e) => e.code,
            'code',
            'missing_plugin',
          ),
        ),
      );
    });
  });

  group('server address', () {
    test('is its own port, never the Termux-managed one', () {
      expect(BuiltinLinux.serverUrl, 'http://127.0.0.1:4097');
      expect(BuiltinLinux.managesServerUrl(BuiltinLinux.serverUrl), isTrue);
      expect(TermuxBridge.managesServerUrl(BuiltinLinux.serverUrl), isFalse);
      expect(
        BuiltinLinux.managesServerUrl(TermuxBridge.managedServerUrl),
        isFalse,
      );
      expect(BuiltinLinux.managesServerUrl('http://0.0.0.0:4097'), isFalse);
    });
  });

  group('scripts', () {
    test('fresh script defaults install and start OC2 at its pin', () {
      expect(
        BuiltinLinux.installOpenCodeScript(),
        contains("export OC_RUNTIME='opencode2'"),
      );
      expect(
        BuiltinLinux.installOpenCodeScript(),
        contains("export OC_REQUESTED_VERSION='2.1.1'"),
      );
      expect(
        BuiltinLinux.serverScript(),
        contains('exec opencode2 serve --hostname 127.0.0.1 --port 4097'),
      );
    });

    test('the install uses the shared setup text at the pinned version', () {
      final script = BuiltinLinux.installOpenCodeScript(
        runtime: TermuxRuntime.openCode2,
      );
      expect(
        script,
        contains(
          "export OC_REQUESTED_VERSION='${TermuxRuntime.openCode2.pinnedVersion}'",
        ),
      );
      expect(script, contains("export OC_RUNTIME='opencode2'"));
      expect(
        script,
        contains(
          "bash -s <<'OC_PROOT_SETUP'\n${openCodeUbuntuSetupScript}OC_PROOT_SETUP\n",
        ),
      );
      expect(script, isNot(contains('opencode models --refresh')));

      final two = BuiltinLinux.installOpenCodeScript(
        runtime: TermuxRuntime.openCode2,
      );
      expect(
        two,
        contains(
          "export OC_REQUESTED_VERSION='${TermuxRuntime.openCode2.pinnedVersion}'",
        ),
      );
      expect(two, contains("export OC_RUNTIME='opencode2'"));
      expect(two, isNot(contains('models --refresh')));
      expect(
        () => BuiltinLinux.installOpenCodeScript(version: '1; rm -rf /'),
        throwsArgumentError,
      );
    });

    test('the server binds 127.0.0.1 only and reads its password file', () {
      final script = BuiltinLinux.serverScript(
        runtime: TermuxRuntime.openCode2,
      );
      expect(
        script,
        contains('exec opencode2 serve --hostname 127.0.0.1 --port 4097\n'),
      );
      expect(script, contains('cd /root/projects'));
      expect(script, contains('export OPENCODE_SERVER_USERNAME=opencode'));
      expect(script, contains('export OPENCODE_SERVER_PASSWORD="\$password"'));
      expect(script, contains('export OPENCODE_PASSWORD="\$password"'));
      expect(script, contains('cat ${BuiltinLinux.passwordFile}'));
      expect(script, contains('XDG_DATA_HOME'));
      expect(script, isNot(contains('0.0.0.0')));

      final two = BuiltinLinux.serverScript(runtime: TermuxRuntime.openCode2);
      expect(
        two,
        contains('exec opencode2 serve --hostname 127.0.0.1 --port 4097\n'),
      );
      for (final line in const [
        'export XDG_DATA_HOME=/root/.oc-opencode2/data',
        'export XDG_CACHE_HOME=/root/.oc-opencode2/cache',
        'export XDG_STATE_HOME=/root/.oc-opencode2/state',
        'export XDG_CONFIG_HOME=/root/.oc-opencode2/config',
        'export OPENCODE_CONFIG_DIR=/root/.oc-opencode2/config/opencode',
        'export OPENCODE_DB=/root/.oc-opencode2/data/opencode/opencode.db',
        'unset OPENCODE_CONFIG OPENCODE_CONFIG_CONTENT',
      ]) {
        expect(two, contains(line));
      }
    });

    test('the password is quoted and never in the server script', () {
      final write = BuiltinLinux.writePasswordScript("it's-secret");
      expect(write, contains('umask 077'));
      expect(write, contains("'it'\"'\"'s-secret'"));
      expect(BuiltinLinux.serverScript(), isNot(contains('secret')));
      expect(() => BuiltinLinux.writePasswordScript(''), throwsArgumentError);
    });

    test('versions are read the way the Termux manager trims them', () {
      expect(BuiltinLinux.parseVersion('1.18.29\n'), '1.18.29');
      expect(BuiltinLinux.parseVersion('opencode2 v2.0.10'), '2.0.10');
      expect(BuiltinLinux.parseVersion('opencode v2.0.10\n\n'), '2.0.10');
      expect(BuiltinLinux.parseVersion(''), isNull);
      expect(BuiltinLinux.parseVersion('command not found'), isNull);
    });

    test('every script parses as shell', () async {
      final scripts = {
        'install1': BuiltinLinux.installOpenCodeScript(
          runtime: TermuxRuntime.openCode2,
        ),
        'install2': BuiltinLinux.installOpenCodeScript(
          runtime: TermuxRuntime.openCode2,
        ),
        'server1': BuiltinLinux.serverScript(runtime: TermuxRuntime.openCode2),
        'server2': BuiltinLinux.serverScript(runtime: TermuxRuntime.openCode2),
        'password': BuiltinLinux.writePasswordScript('pw'),
        'version': BuiltinLinux.versionScript(TermuxRuntime.openCode2),
        'list': BuiltinLinux.listProjectsScript(),
        'exists': BuiltinLinux.folderExistsScript('/root/projects/a b'),
        'create': BuiltinLinux.createFolderScript("/root/projects/it's"),
      };
      for (final entry in scripts.entries) {
        final result = await Process.run('sh', ['-n', '-c', entry.value]);
        expect(result.exitCode, 0, reason: '${entry.key}: ${result.stderr}');
      }
      // The heredoc body itself is bash (arrays), not sh.
      final body = await Process.run('bash', [
        '-n',
        '-c',
        openCodeUbuntuSetupScript,
      ]);
      expect(body.exitCode, 0, reason: '${body.stderr}');
    });

    test('project folders: create makes a git project once, list and '
        'exists read them back', () async {
      final root = await Directory.systemTemp.createTemp('oc-projects-');
      addTearDown(() => root.delete(recursive: true));
      // A quote in the path proves the quoting, not just the happy path.
      final path = "${root.path}/it's-new";

      Future<ProcessResult> sh(String script) =>
          Process.run('sh', ['-c', script]);

      expect((await sh(BuiltinLinux.folderExistsScript(path))).exitCode, 1);
      final created = await sh(BuiltinLinux.createFolderScript(path));
      expect(created.exitCode, 0, reason: '${created.stderr}');
      expect(created.stdout, 'created $path\n');
      expect(Directory('$path/.git').existsSync(), isTrue);
      // A new project has its first commit, so a team can branch from it.
      expect((await sh(BuiltinLinux.repoStateScript(path))).stdout, 'ready\n');
      final other = await Directory.systemTemp.createTemp('oc-bare-');
      addTearDown(() => other.delete(recursive: true));
      final bare = Directory('${other.path}/bare')..createSync();
      await Process.run('git', ['init', '-q', bare.path]);
      expect(
        (await sh(BuiltinLinux.repoStateScript(bare.path))).stdout,
        'empty\n',
      );
      expect(
        (await sh(BuiltinLinux.repoStateScript('${root.path}/nope'))).stdout,
        'notgit\n',
      );
      expect((await sh(BuiltinLinux.folderExistsScript(path))).exitCode, 0);

      final again = await sh(BuiltinLinux.createFolderScript(path));
      expect(again.stdout, 'exists $path\n');

      final listed = await sh(
        BuiltinLinux.listProjectsScript().replaceAll(
          BuiltinLinux.projectsDir,
          root.path,
        ),
      );
      Directory('${root.path}/.cache').createSync();
      expect(BuiltinLinux.parseProjectList('${listed.stdout}.cache\n'), [
        "it's-new",
      ]);
    });

    test('create refuses a relative path', () {
      expect(
        () => BuiltinLinux.createFolderScript('projects/x'),
        throwsArgumentError,
      );
    });

    test('legacy identity is detection only; it cannot install or start', () {
      expect(BuiltinLinux.runtimeFor(ServerFlavor.v2), TermuxRuntime.openCode2);
      expect(
        () => BuiltinLinux.installOpenCodeScript(
          runtime: TermuxRuntime.openCode1,
        ),
        throwsA(isA<TermuxBridgeException>()),
      );
      expect(
        () => BuiltinLinux.serverScript(runtime: TermuxRuntime.openCode1),
        throwsA(isA<TermuxBridgeException>()),
      );
    });
  });

  group('shared OpenCode setup script', () {
    // The Termux manager is now composed from the shared constant. This pins
    // it to the reviewed text, so the
    // refactor provably changed nothing for Termux. A deliberate edit to the
    // manager script or the shared setup text updates this hash with it.
    // Pinned so the manager on people's phones never changes by accident.
    // Last deliberate change: npm is told to allow exactly the OpenCode
    // package's own install script (`--allow-scripts`, npm 11.19;
    // slice-builtin-opencode-pin, 2026-09-28). Then: the OpenCode 2 runner
    // copies the OpenCode 1 chat database across before its first start
    // (oc2-switch-keeps-chats, 2026-10-14). Then: the OpenCode 2 pin moved
    // from 2.0.28 to 2.0.29 (AI Team on OpenCode 2, 2026-10-14). Then: a
    // fresh setup defaults to OpenCode 2 while an existing install keeps its
    // runtime (oc2-default phase 2, 2026-10-15). Then: refuse an unsafe
    // two-history move and propagate fresh-copy failures (release blockers).
    test('the Termux manager script changes only on purpose', () {
      final script = TermuxBridge.managerScriptForTesting();
      if (Platform.environment['WRITE_SCRIPT_HASHES'] == '1') {
        final file = File('test/builtin_linux_test.dart');
        file.writeAsStringSync(
          file
              .readAsStringSync()
              .replaceFirst(
                RegExp(r'const reviewedLength = \d+;'),
                'const reviewedLength = ${script.length};',
              )
              .replaceFirst(
                RegExp(r"const reviewedHash =\s*'[a-f0-9]+';"),
                "const reviewedHash = '${sha256.convert(utf8.encode(script))}';",
              ),
        );
        return;
      }
      const reviewedLength = 56249;
      const reviewedHash =
          '2136b3927c9b35f61f89f960991554aae07b53da39a5bb9f9298796493b968f8';
      expect(script.length, reviewedLength);
      expect(sha256.convert(utf8.encode(script)).toString(), reviewedHash);
    });

    test('the Termux manager embeds the shared text in its heredoc', () {
      final script = TermuxBridge.managerScriptForTesting();
      expect(
        script,
        contains(
          "bash -s <<'OC_PROOT_SETUP'\n${openCodeUbuntuSetupScript}OC_PROOT_SETUP\n",
        ),
      );
      expect(openCodeUbuntuSetupScript, endsWith('"\$command" --version\n'));
      expect(openCodeUbuntuSetupScript, isNot(contains('\nOC_PROOT_SETUP\n')));
    });
  });
}
