import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/server_probe.dart' show ServerFlavor;
import 'package:opencode_mobile/builtin/builtin_linux.dart';
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
              runtime: TermuxRuntime.openCode1,
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
      await linux.uninstall();
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
    test('the install uses the shared setup text at the pinned version', () {
      final script = BuiltinLinux.installOpenCodeScript();
      expect(
        script,
        contains(
          "export OC_REQUESTED_VERSION='${TermuxBridge.defaultOpenCodeVersion}'",
        ),
      );
      expect(script, contains("export OC_RUNTIME='opencode1'"));
      expect(
        script,
        contains(
          "bash -s <<'OC_PROOT_SETUP'\n${openCodeUbuntuSetupScript}OC_PROOT_SETUP\n",
        ),
      );
      expect(script, contains('opencode models --refresh'));

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
      final script = BuiltinLinux.serverScript();
      expect(
        script,
        contains('exec opencode serve --hostname 127.0.0.1 --port 4097\n'),
      );
      expect(script, contains('cd /root/projects'));
      expect(script, contains('export OPENCODE_SERVER_USERNAME=opencode'));
      expect(script, contains('export OPENCODE_SERVER_PASSWORD="\$password"'));
      expect(script, contains('export OPENCODE_PASSWORD="\$password"'));
      expect(script, contains('cat ${BuiltinLinux.passwordFile}'));
      expect(script, isNot(contains('XDG_DATA_HOME')));
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
        'install1': BuiltinLinux.installOpenCodeScript(),
        'install2': BuiltinLinux.installOpenCodeScript(
          runtime: TermuxRuntime.openCode2,
        ),
        'server1': BuiltinLinux.serverScript(),
        'server2': BuiltinLinux.serverScript(runtime: TermuxRuntime.openCode2),
        'password': BuiltinLinux.writePasswordScript('pw'),
        'version': BuiltinLinux.versionScript(TermuxRuntime.openCode1),
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

    test('a profile runs the runtime it was set up with', () {
      expect(BuiltinLinux.runtimeFor(ServerFlavor.v2), TermuxRuntime.openCode2);
      expect(BuiltinLinux.runtimeFor(ServerFlavor.v1), TermuxRuntime.openCode1);
    });
  });

  group('shared OpenCode setup script', () {
    // The Termux manager is now composed from the shared constant. This pins
    // it to the exact text it had before the split (48944 characters), so the
    // refactor provably changed nothing for Termux. A deliberate edit to the
    // manager script or the shared setup text updates this hash with it.
    // Pinned so the manager on people's phones never changes by accident.
    // Last deliberate change: npm is told to allow exactly the OpenCode
    // package's own install script (`--allow-scripts`, npm 11.19;
    // slice-builtin-opencode-pin, 2026-09-28).
    test('the Termux manager script changes only on purpose', () {
      final script = TermuxBridge.managerScriptForTesting();
      expect(script.length, 51956);
      expect(
        sha256.convert(utf8.encode(script)).toString(),
        '8239c9f461a74e49a531393090dcee0a23dfe51194bfb2009a4a72d2a1f869b3',
      );
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
