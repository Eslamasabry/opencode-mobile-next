import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/termux/bridge.dart';

String _processStart(int pid) {
  final stat = File('/proc/$pid/stat').readAsStringSync();
  final fields = stat.substring(stat.lastIndexOf(') ') + 2).split(' ');
  return fields[19];
}

ProcessResult _runManagerClockProbe(String commands) {
  final directory = Directory.systemTemp.createTempSync('oc-manager-clock-');
  addTearDown(() => directory.deleteSync(recursive: true));
  final home = Directory('${directory.path}/home')..createSync();
  final manager = TermuxBridge.managerScriptForTesting();
  final functions = manager.substring(
    0,
    manager.indexOf('\ncase "\${1:-status}" in'),
  );
  final probe = File('${directory.path}/probe.sh')
    ..writeAsStringSync('$functions\n$commands\n');
  return Process.runSync(
    'bash',
    [probe.path],
    environment: {...Platform.environment, 'HOME': home.path},
  );
}

void main() {
  test('setup clock tolerates legacy and malformed timestamps', () {
    expect(
      TermuxSetupStatus.parse('phase=preparing').startedAtEpochSeconds,
      isNull,
    );
    for (final value in [
      '',
      'unknown',
      '-1',
      '1.5',
      '0xff',
      '+1',
      '999999999999999999999999',
    ]) {
      expect(
        TermuxSetupStatus.parse(
          'phase=preparing\nstarted_at=$value',
        ).startedAtEpochSeconds,
        isNull,
        reason: value,
      );
    }
    expect(TermuxSetupStatus.parse('started_at=0').startedAtEpochSeconds, 0);
    expect(
      TermuxSetupStatus.parse('started_at=1788800000').startedAtEpochSeconds,
      1788800000,
    );
  });

  test('install dispatch starts its clock only after accepting the launch', () {
    final script = TermuxBridge.installAndServeScript(
      password: 'test-password',
    );
    final dispatch = script.substring(script.indexOf('\nOC_MANAGER_EOF\n'));
    final clock = dispatch.indexOf(r'started_at=$(date +%s)');
    expect(clock, greaterThan(dispatch.indexOf('trap cleanup_dispatch EXIT')));
    expect(
      clock,
      greaterThan(dispatch.lastIndexOf('manager-already-running:')),
    );
    expect(clock, lessThan(dispatch.indexOf("printf 'phase=queued")));
    expect(dispatch, contains(r'started_at=%s'));
    expect(dispatch.split(r'$(date +%s)').length - 1, 1);
  });

  test('manager clock survives phase writes and a fresh status process', () {
    final result = _runManagerClockProbe(r'''
if [ "${1:-}" = reopened ]; then
  status
  exit 0
fi
printf 'started_at=100\n' > "$STATE"
for phase in preparing installing_dependencies installing_ubuntu installing_opencode refreshing_models starting_server ready; do
  write_state "$phase" 'Clock probe' 4096
  [ "$(read_state_value started_at)" = 100 ] || exit 81
done
# A fresh status shell detects missing server ownership and writes failed.
# That transition must retain the operation clock too.
bash "$0" reopened
''');
    expect(result.exitCode, 0, reason: '${result.stdout}\n${result.stderr}');
    final status = TermuxSetupStatus.parse(result.stdout as String);
    expect(status.phase, 'failed');
    expect(status.startedAtEpochSeconds, 100);
  });

  test('legacy manager state stays without an invented clock', () {
    final result = _runManagerClockProbe(r'''
write_state preparing 'Legacy operation' 4096
status
''');
    expect(result.exitCode, 0, reason: '${result.stdout}\n${result.stderr}');
    expect(
      TermuxSetupStatus.parse(result.stdout as String).startedAtEpochSeconds,
      isNull,
    );
  });

  test('restart renews the clock only after launch rejection gates', () {
    final result = _runManagerClockProbe(r'''
printf 'phase=stopped\nstarted_at=100\n' > "$STATE"
claim_direct_lock() { [ "${REJECT_LOCK:-0}" = 0 ]; }
recovery_preflight() { return 1; }
release_setup_lock() { :; }
cleanup_setup() { :; }
process_start() { printf '1'; }
process_group() { printf '%s' "$$"; }
ubuntu_usable() { return 1; }
date() { printf '%s' "$CLOCK"; }
CLOCK=200
if (restart 4096 'invalid!'); then exit 82; fi
[ "$(read_state_value started_at)" = 100 ] || exit 83
REJECT_LOCK=1
if (restart 4096 duplicate); then exit 84; fi
[ "$(read_state_value started_at)" = 100 ] || exit 85
REJECT_LOCK=0
if (restart 4096 revoked expected token); then exit 86; fi
[ "$(read_state_value started_at)" = 100 ] || exit 87
# Accepted operations stop at stubbed Ubuntu preflight, after writing the clock.
if (restart 4096 first); then exit 88; fi
[ "$(read_state_value started_at)" = 200 ] || exit 89
CLOCK=300
if (restart 4096 second); then exit 90; fi
[ "$(read_state_value started_at)" = 300 ] || exit 91
''');
    expect(result.exitCode, 0, reason: '${result.stdout}\n${result.stderr}');
  });

  test('generated termux scripts pass bash syntax validation', () {
    final directory = Directory.systemTemp.createTempSync('oc-scripts-');
    addTearDown(() => directory.deleteSync(recursive: true));
    final scripts = {
      'install.sh': TermuxBridge.installAndServeScript(
        port: 4096,
        password: 'test-password',
      ),
      'install-opencode2.sh': TermuxBridge.installAndServeScript(
        password: 'test-password',
        runtime: TermuxRuntime.openCode2,
      ),
      'manager.sh': TermuxBridge.managerScriptForTesting(),
      'restart.sh': TermuxBridge.restartScript(
        port: 4096,
        operationID: 'test-1',
      ),
      'diagnostics.sh': TermuxBridge.diagnosticsScript(),
      'snapshot.sh': TermuxBridge.setupSnapshotScript(),
      'status.sh': TermuxBridge.statusScript(),
      'installation.sh': TermuxBridge.installationScript(),
      'wake-lock.sh': TermuxBridge.ensureWakeLockScript,
      'unlock.sh': TermuxBridge.unlockCommand,
      'stop.sh': TermuxBridge.stopScript(port: 4096),
    };

    for (final entry in scripts.entries) {
      final file = File('${directory.path}/${entry.key}')
        ..writeAsStringSync(entry.value);
      final result = Process.runSync('bash', ['-n', file.path]);
      expect(result.exitCode, 0, reason: '${entry.key}: ${result.stderr}');
    }
  });

  test('setup status parses persisted manager state', () {
    final status = TermuxSetupStatus.parse('''
phase=ready
message=OpenCode is ready
port=4096
runner=proot
version=1.2.3
pid=1234
operation=restart-1
operation_result=completed
''');

    expect(status.isReady, isTrue);
    expect(status.isRunning, isFalse);
    expect(status.port, 4096);
    expect(status.version, '1.2.3');
    expect(status.pid, 1234);
    expect(status.operationID, 'restart-1');
    expect(status.operationResult, 'completed');
  });

  test('restart is treated as a running managed operation', () {
    final status = TermuxSetupStatus.parse('''
phase=restarting
message=Restarting the local server
port=4096
runner=proot
version=1.2.3
pid=1234
''');

    expect(status.isRunning, isTrue);
    expect(status.isReady, isFalse);
  });

  test('restart validates ownership and reuses the installed server', () {
    final script = TermuxBridge.restartScript(
      port: 4096,
      operationID: 'test-1',
    );
    final manager = TermuxBridge.managerScriptForTesting();
    final restart = manager.substring(
      manager.indexOf('restart() {'),
      manager.indexOf('\nstatus() {'),
    );
    final verifiedStop = manager.substring(
      manager.indexOf('stop_verified_server_process() {'),
      manager.indexOf('\nstop_verified_setup_process() {'),
    );

    expect(script, contains("\"\$MANAGER\" restart '4096' 'test-1' &"));
    expect(script, contains('another-managed-operation-is-running'));
    expect(script, isNot(contains('server.password.tmp')));
    expect(restart, contains('ubuntu_usable'));
    expect(restart, contains('The tracked process is not the managed'));
    expect(
      verifiedStop.indexOf(r'server_process "$pid" "$port"'),
      lessThan(verifiedStop.indexOf(r'kill -KILL -- "-$group"')),
    );
    expect(verifiedStop, contains(r'process_start "$pid"'));
    expect(verifiedStop, contains(r'kill -STOP "$pid"'));
    expect(restart, contains(r'/dev/tcp/127.0.0.1/$CURRENT_PORT'));
    expect(restart, contains(r'start_server "$installed_version" restarting'));
    expect(restart, isNot(contains('prepare_termux_dependencies')));
    expect(restart, isNot(contains('install_opencode')));
    expect(
      restart.indexOf('write_state restarting'),
      lessThan(restart.indexOf('ubuntu_usable ||')),
    );
    expect(manager, contains('operation_result=%s'));
    expect(manager, contains('not_performed'));
    expect(manager, contains(r'[ "${args[1]:-}" = "$MANAGER" ]'));
    expect(manager, contains(r'[ "${args[2]:-}" = restart ]'));
  });

  test('restart operation IDs cannot inject shell syntax', () {
    expect(
      () => TermuxBridge.restartScript(operationID: "bad'; echo injected"),
      throwsArgumentError,
    );
    expect(
      () => TermuxBridge.restartScript(operationID: ''),
      throwsArgumentError,
    );
  });

  test('setup snapshot keeps terminal output separate from status', () {
    final snapshot = TermuxSetupSnapshot.parse('''
phase=installing_opencode
message=Installing OpenCode
port=4096
runner=proot
version=
pid=1234
__OC_SETUP_OUTPUT__
Unpacking nodejs...
npm timing idealTree=7432
phase=failed
message=This belongs to the terminal
''');

    expect(snapshot.status.phase, 'installing_opencode');
    expect(snapshot.status.pid, 1234);
    expect(snapshot.output, contains('Unpacking nodejs...'));
    expect(snapshot.output, contains('idealTree=7432'));
    expect(snapshot.output, contains('phase=failed'));
    expect(snapshot.status.message, 'Installing OpenCode');
  });

  test('Android RESULT_OK and shell exit zero indicate success', () {
    final result = TermuxCommandResult.fromMap(const {
      'stdout': 'ok',
      'stderr': '',
      'exitCode': 0,
      'err': -1,
      'errorMessage': '',
    });

    expect(result.successful, isTrue);
  });

  test('manager is installed before the single-flight lock is acquired', () {
    final script = TermuxBridge.installAndServeScript(
      port: 4096,
      password: 'test-password',
    );

    final managerInstall = script.indexOf(r'cat > "$manager_tmp"');
    final lockAcquire = script.indexOf(r'if mkdir "$LOCK"');
    final passwordWrite = script.indexOf("printf '%s' 'test-password'");
    expect(managerInstall, greaterThanOrEqualTo(0));
    expect(lockAcquire, greaterThan(managerInstall));
    expect(passwordWrite, greaterThan(lockAcquire));
    expect(script, contains(r'$LOCK/owner'));
    expect(script, isNot(contains(r'ln "$lock_candidate"')));
    expect(script, contains(r'if [ -f "$LOCK" ]'));
    expect(script, contains(r'"$$" "$self_start"'));
    expect(script, contains(r'"$MANAGER" rotate-log install'));
    expect(script, contains(r'> >("$MANAGER" write-log install) 2>&1'));
    expect(script, contains(r'rm -f "$OC_DIR/server-log.active"'));
  });

  test('default setup installs the pinned OpenCode release', () {
    final script = TermuxBridge.installAndServeScript(
      port: 4096,
      password: 'test-password',
    );
    final manager = TermuxBridge.managerScriptForTesting();
    final pinned = TermuxBridge.defaultOpenCodeVersion;

    // A published APK must not install a server released after it. The pin
    // is an exact version, and the shell fallback has to agree with it or a
    // setup that reaches the default by a different route drifts.
    expect(pinned, matches(RegExp(r'^\d+\.\d+\.\d+$')));
    expect(script, contains("setup '4096' '$pinned'"));
    expect(manager, contains('opencode1) requested_version=$pinned'));
    expect(manager, contains('local main_package=opencode-ai'));
    expect(manager, contains(r'"$main_package@$OC_REQUESTED_VERSION"'));
    expect(
      manager,
      contains(r'npm_cache=$(mktemp -d /tmp/opencode-mobile-npm.XXXXXX)'),
    );
    expect(manager, contains(r'--cache "$npm_cache"'));
    expect(manager, contains(r'rm -rf -- "$npm_cache"'));
    expect(manager, contains('cleanup_legacy_npm_cache'));
    expect(
      manager,
      contains(r'proot-distro login "$PROOT_NAME" -- npm cache clean --force'),
    );
    expect(manager, contains('opencode models --refresh'));
    expect(manager, contains('refreshing_models'));
  });

  test('Ubuntu bootstrap disables io_uring before Node and npm run', () {
    final manager = TermuxBridge.managerScriptForTesting();
    final start = manager.indexOf("bash -s <<'OC_PROOT_SETUP'");
    final bootstrap = manager.substring(
      start,
      manager.indexOf('\nOC_PROOT_SETUP', start),
    );
    final compatibility = bootstrap.indexOf('export UV_USE_IO_URING=0');
    expect(compatibility, greaterThanOrEqualTo(0));
    expect(compatibility, lessThan(bootstrap.indexOf('node -p')));
    expect(compatibility, lessThan(bootstrap.indexOf('npm install -g')));
  });

  test('Ubuntu bootstrap trims recommendations but retains coding tools', () {
    final manager = TermuxBridge.managerScriptForTesting();
    final bootstrap = manager.substring(
      manager.indexOf("bash -s <<'OC_PROOT_SETUP'"),
      manager.indexOf('\ninstall_opencode() {'),
    );
    final joined = bootstrap.replaceAll(RegExp(r'\\\r?\n'), ' ');
    final install = RegExp(
      r'apt-get install[^\r\n]*',
    ).firstMatch(joined)!.group(0)!;
    final arguments = install.split(RegExp(r'\s+'));

    expect(arguments, contains('--no-install-recommends'));
    expect(arguments, contains('Acquire::Retries=5'));
    expect(
      arguments,
      containsAll([
        'nodejs',
        'npm',
        'curl',
        'ca-certificates',
        'git',
        'openssh-client',
      ]),
    );
    for (final command in ['node', 'npm', 'curl', 'git', 'ssh']) {
      expect(bootstrap, contains('! command -v $command >/dev/null 2>&1'));
    }
    expect(bootstrap, contains('[ ! -s /etc/ssl/certs/ca-certificates.crt ]'));
    expect(bootstrap, contains('apt-get update -y -o Acquire::Retries=5'));
    expect(bootstrap, isNot(contains('--allow-unauthenticated')));
  });

  test(
    'Ubuntu npm install requires its matching pinned binary and cleans cache',
    () {
      final manager = TermuxBridge.managerScriptForTesting();
      final block = manager.substring(
        manager.indexOf('install_opencode() {'),
        manager.indexOf('\ninstall_opencode ||'),
      );
      final directory = Directory.systemTemp.createTempSync('oc-npm-test-');
      addTearDown(() => directory.deleteSync(recursive: true));
      final calls = File('${directory.path}/calls');
      for (final runtime in TermuxRuntime.values) {
        final mainPackage = runtime == TermuxRuntime.openCode2
            ? '@opencode-ai/cli'
            : 'opencode-ai';
        final binaryPrefix = runtime == TermuxRuntime.openCode2
            ? '@opencode-ai/cli'
            : 'opencode';
        for (final scenario in [
          ('arm64', 0, '$binaryPrefix-linux-arm64'),
          ('x64', 0, '$binaryPrefix-linux-x64-baseline'),
          ('arm64', 7, '$binaryPrefix-linux-arm64'),
          ('arm', 0, ''),
        ]) {
          calls.writeAsStringSync('');
          final prelude = r'''
set -eu
node() { printf '%s\n' "$MOCK_ARCH"; }
npm() {
  printf '%s\n' "$@" >> "$NPM_CALLS"
  return "$MOCK_NPM_EXIT"
}
''';
          final script = '$prelude$block\ninstall_opencode\n';
          final result = Process.runSync(
            'bash',
            ['-c', script],
            environment: {
              'MOCK_ARCH': scenario.$1,
              'MOCK_NPM_EXIT': '${scenario.$2}',
              'NPM_CALLS': calls.path,
              'OC_RUNTIME': runtime.wireName,
              'OC_REQUESTED_VERSION': runtime.pinnedVersion,
            },
          );
          expect(
            result.exitCode,
            scenario.$1 == 'arm' ? 64 : scenario.$2,
            reason: '${result.stdout}\n${result.stderr}',
          );
          final arguments = calls.readAsLinesSync();
          if (scenario.$1 == 'arm') {
            expect(arguments, isEmpty);
            continue;
          }
          expect(
            arguments,
            containsAll([
              'install',
              '-g',
              '--include=optional',
              '--foreground-scripts',
              '--fetch-retries=5',
              '--fetch-timeout=300000',
              '${scenario.$3}@${runtime.pinnedVersion}',
              '$mainPackage@${runtime.pinnedVersion}',
            ]),
          );
          expect(arguments.where((value) => value.contains('musl')), isEmpty);
          expect(arguments, isNot(contains('--force')));
          final cache = arguments[arguments.indexOf('--cache') + 1];
          expect(cache, startsWith('/tmp/opencode-mobile-npm.'));
          expect(Directory(cache).existsSync(), isFalse);
        }
      }
    },
  );

  test(
    'runtime selection survives phase writes and a fresh status process',
    () {
      final result = _runManagerClockProbe(r'''
if [ "${1:-}" = reopened ]; then
  status
  exit 0
fi
printf opencode2 > "$RUNTIME_FILE"
printf 'started_at=100\n' > "$STATE"
for phase in preparing installing_opencode starting_server ready; do
  write_state "$phase" 'Runtime probe' 4096 proot 0.0.0-beta-18600
  [ "$(read_state_value runtime)" = opencode2 ] || exit 81
done
bash "$0" reopened
''');
      expect(result.exitCode, 0, reason: '${result.stdout}\n${result.stderr}');
      final status = TermuxSetupStatus.parse(result.stdout as String);
      expect(status.runtime, TermuxRuntime.openCode2);
      expect(status.startedAtEpochSeconds, 100);
      expect(status.phase, 'failed');
    },
  );

  test(
    'runtime version uses the recorded command and normalizes its prefix',
    () {
      final result = _runManagerClockProbe(r'''
proot-distro() {
  [ "$1" = login ] && [ "$2" = opencode-ubuntu ] &&
    [ "$3" = -- ] && [ "$5" = --version ] || return 83
  case "$4" in
    opencode) printf '1.18.29\r\n' ;;
    opencode2) printf 'opencode2 v0.0.0-beta-18600\r\n' ;;
    *) return 84 ;;
  esac
}
[ "$(runtime_version)" = 1.18.29 ] || exit 85
printf opencode2 > "$RUNTIME_FILE"
[ "$(runtime_version)" = 0.0.0-beta-18600 ] || exit 86
printf unsupported > "$RUNTIME_FILE"
if runtime_version; then exit 87; fi
if write_state preparing bad 4096; then exit 88; fi
''');
      expect(result.exitCode, 0, reason: '${result.stdout}\n${result.stderr}');
    },
  );

  test('first-run runtime choice cannot silently migrate an installation', () {
    final script = TermuxBridge.installAndServeScript(
      password: 'test-password',
      runtime: TermuxRuntime.openCode2,
    );
    final guard = script.substring(
      script.indexOf('\nold_runtime='),
      script.indexOf('\npassword_tmp='),
    );
    expect(script, contains("setup '4096' '0.0.0-beta-18600'"));
    expect(script, contains("\"\$self_start\" 'opencode2'"));
    expect(
      script.indexOf(guard),
      greaterThan(script.indexOf('trap cleanup_dispatch EXIT')),
    );
    for (final scenario in [
      (runtime: '', version: '', exit: 0),
      (runtime: 'opencode1', version: '', exit: 64),
      (runtime: '', version: '1.18.29', exit: 64),
      (runtime: 'opencode2', version: '0.0.0-beta-18600', exit: 0),
    ]) {
      final directory = Directory.systemTemp.createTempSync(
        'oc-runtime-guard-',
      );
      addTearDown(() => directory.deleteSync(recursive: true));
      if (scenario.runtime.isNotEmpty) {
        File('${directory.path}/runtime').writeAsStringSync(scenario.runtime);
      }
      File(
        '${directory.path}/state',
      ).writeAsStringSync('version=${scenario.version}\n');
      final result = Process.runSync(
        'bash',
        ['-c', 'set -eu\n$guard'],
        environment: {'OC_DIR': directory.path},
      );
      expect(
        result.exitCode,
        scenario.exit,
        reason: '${result.stdout}\n${result.stderr}',
      );
      final recorded = File('${directory.path}/runtime');
      expect(
        recorded.existsSync() ? recorded.readAsStringSync() : '',
        scenario.exit == 0 ? 'opencode2' : scenario.runtime,
      );
    }
  });

  test(
    'runtime inventory preserves legacy default and explicit beta choice',
    () {
      final legacy = TermuxInstallation.parse(
        'ubuntu=installed\nversion=1.18.29\n',
      );
      expect(legacy.runtime, TermuxRuntime.openCode1);
      expect(legacy.runtimeSelected, isFalse);
      final beta = TermuxInstallation.parse(
        'ubuntu=installed\nversion=0.0.0-beta-18600\nruntime=opencode2\n',
      );
      expect(beta.runtime, TermuxRuntime.openCode2);
      expect(beta.runtimeSelected, isTrue);
      expect(beta.openCodeVersion, '0.0.0-beta-18600');
      final queued = TermuxInstallation.parse(
        'ubuntu=absent\nversion=\nruntime=opencode2\n',
      );
      expect(queued.ubuntuInstalled, isFalse);
      expect(queued.runtimeSelected, isTrue);
      expect(
        TermuxSetupStatus.parse('phase=ready').runtime,
        TermuxRuntime.openCode1,
      );
      expect(
        () => TermuxSetupStatus.parse('phase=ready\nruntime=unknown'),
        throwsA(isA<TermuxBridgeException>()),
      );
      for (final suffix in ['runtime=unknown', 'runtime=opencode2\nextra=1']) {
        expect(
          () => TermuxInstallation.parse('ubuntu=installed\nversion=\n$suffix'),
          throwsA(isA<TermuxBridgeException>()),
        );
      }
    },
  );

  test('installation inventory accepts only a single sanitized version', () {
    final absent = TermuxInstallation.parse('ubuntu=absent\nversion=\n');
    expect(absent.ubuntuInstalled, isFalse);
    expect(absent.openCodeVersion, isNull);
    final ubuntu = TermuxInstallation.parse('ubuntu=installed\nversion=\n');
    expect(ubuntu.ubuntuInstalled, isTrue);
    expect(ubuntu.openCodeVersion, isNull);
    expect(
      TermuxInstallation.parse(
        'ubuntu=installed\nversion=1.18.29\n',
      ).openCodeVersion,
      '1.18.29',
    );
    for (final output in [
      '',
      'ubuntu=absent\nversion=1.18.29',
      'ubuntu=installed\nversion=not a version',
      'ubuntu=installed\nversion=1.18.29\nextra output',
    ]) {
      expect(
        () => TermuxInstallation.parse(output),
        throwsA(isA<TermuxBridgeException>()),
      );
    }
  });

  test('installation inventory distinguishes absence from a failed probe', () {
    final directory = Directory.systemTemp.createTempSync('oc-inventory-test-');
    addTearDown(() => directory.deleteSync(recursive: true));
    final calls = File('${directory.path}/calls');
    final executable = File('${directory.path}/proot-distro')
      ..writeAsStringSync(r'''#!/bin/bash
printf '%s\n' "$1" "$2" "$3" "$4" "$5" > "$PROOT_CALLS"
if [ "$PROBE_EXIT" != 0 ]; then exit "$PROBE_EXIT"; fi
printf 'ubuntu=installed\nversion=1.18.29\n'
''');
    expect(Process.runSync('chmod', ['+x', executable.path]).exitCode, 0);
    final script = TermuxBridge.installationScript();
    ProcessResult probe(int code) => Process.runSync(
      'bash',
      ['-c', script],
      environment: {
        'PREFIX': directory.path,
        'PROOT_CALLS': calls.path,
        'PATH': '${directory.path}:${Platform.environment['PATH']}',
        'PROBE_EXIT': '$code',
      },
    );
    final absent = probe(0);
    expect(absent.exitCode, 0);
    expect(
      TermuxInstallation.parse(absent.stdout as String).ubuntuInstalled,
      isFalse,
    );
    expect(calls.existsSync(), isFalse);
    Directory(
      '${directory.path}/var/lib/proot-distro/containers/opencode-ubuntu/rootfs',
    ).createSync(recursive: true);
    final installed = probe(0);
    expect(installed.exitCode, 0, reason: '${installed.stderr}');
    expect(
      TermuxInstallation.parse(installed.stdout as String).openCodeVersion,
      '1.18.29',
    );
    expect(calls.readAsLinesSync(), [
      'login',
      'opencode-ubuntu',
      '--',
      'bash',
      '-c',
    ]);
    expect(probe(9).exitCode, 9);
  });

  test('the npm dist-tag is reachable only by asking for it', () {
    expect(TermuxBridge.latestOpenCodeVersion, 'latest');
    final optedIn = TermuxBridge.installAndServeScript(
      port: 4096,
      password: 'test-password',
      version: TermuxBridge.latestOpenCodeVersion,
    );
    expect(optedIn, contains("setup '4096' 'latest'"));
    // ...and never by default.
    expect(
      TermuxBridge.installAndServeScript(port: 4096, password: 'p'),
      isNot(contains("'latest'")),
    );
  });

  test('managed server URL detection is narrow', () {
    expect(TermuxBridge.managesServerUrl('http://127.0.0.1:4096'), isTrue);
    expect(TermuxBridge.managesServerUrl('http://localhost:4096'), isFalse);
    expect(TermuxBridge.managesServerUrl('http://127.0.0.1:4747'), isFalse);
    expect(TermuxBridge.managesServerUrl('https://127.0.0.1:4096'), isFalse);
  });

  test('live snapshot converts manager errors into a failed status', () {
    final script = TermuxBridge.setupSnapshotScript();

    expect(script, contains('Could not read setup manager status'));
    expect(script, contains(r'manager_error="$manager_output"'));
    expect(script, contains(r'[ -f "$OC_DIR/server-log.active" ]'));
  });

  test('manager claims the dispatcher lock before package work', () {
    final manager = TermuxBridge.managerScriptForTesting();
    final claim = manager.indexOf(r'if ! claim_setup_lock');
    final packageWork = manager.indexOf('termux-wake-lock');

    expect(claim, greaterThanOrEqualTo(0));
    expect(packageWork, greaterThan(claim));
  });

  test('server owns the setup wake lock until it stops or exits', () {
    final manager = TermuxBridge.managerScriptForTesting();
    final cleanup = manager.substring(
      manager.indexOf('cleanup_setup() {'),
      manager.indexOf('ubuntu_rootfs_exists()'),
    );

    expect(cleanup, contains('termux-wake-unlock'));
    expect(
      cleanup.indexOf('termux-wake-unlock'),
      greaterThan(cleanup.indexOf('if [ "\${SETUP_SUCCEEDED:-0}" != 1 ]')),
    );
    expect(cleanup, contains('if [ "\${SERVER_STARTED:-0}" = 1 ]'));
    expect(manager, contains(r'"$manager" server-exited "$port" "$$" "$code"'));
    expect(manager, contains('server_exited() {'));
    expect(manager, contains("server-exited) shift; server_exited \"\$@\" ;;"));
    expect(manager, contains("stop() {"));
    expect(
      manager.split('termux-wake-unlock').length - 1,
      greaterThanOrEqualTo(4),
    );
  });

  test('wake lock refresh script is safe to invoke repeatedly', () {
    expect(TermuxBridge.ensureWakeLockScript, contains('termux-wake-lock'));
    expect(
      TermuxBridge.ensureWakeLockScript,
      contains('opencode-server-wake-lock-held'),
    );
  });

  test('setup reserves disk space and only cleans app-owned partial data', () {
    final manager = TermuxBridge.managerScriptForTesting();

    expect(manager, contains('DISK_RESERVE_KIB=524288'));
    expect(manager, contains('FRESH_SETUP_REQUIRED_KIB=1572864'));
    expect(manager, contains('require_setup_space'));
    expect(manager, contains(r'df -Pk "$HOME"'));
    expect(manager, contains('including a \${reserve_mib} MiB safety reserve'));
    expect(manager, contains('cleanup_app_owned_partial_install'));
    expect(manager, contains(r'[ -f "$UBUNTU_INSTALL_MARKER" ]'));
    expect(manager, contains(r'proot-distro remove "$PROOT_NAME"'));
    expect(manager, isNot(contains(r'rm -rf "$PREFIX/var/lib/proot-distro"')));
  });

  test('unhealthy setup repairs packages without allowing removals', () {
    final manager = TermuxBridge.managerScriptForTesting();
    final health = manager.indexOf('termux_dependencies_healthy()');
    final prepare = manager.indexOf('prepare_termux_dependencies()');
    final prepareCall = manager.indexOf('\n  prepare_termux_dependencies\n');
    final ubuntu = manager.indexOf("write_state installing_ubuntu");

    expect(health, greaterThanOrEqualTo(0));
    expect(prepare, greaterThan(health));
    expect(prepareCall, greaterThan(prepare));
    expect(ubuntu, greaterThan(prepareCall));
    expect(
      manager,
      contains('deb https://packages.termux.dev/apt/termux-main stable main'),
    );
    expect(manager, contains(r'$source_file.oc-before-opencode'));
    expect(manager, contains('DEBIAN_FRONTEND=noninteractive'));
    expect(manager, contains('--no-remove'));
    expect(manager, contains('--fix-broken install'));
    expect(manager, contains('Dpkg::Options::="--force-confold" upgrade'));
    expect(
      manager.indexOf('--fix-broken install'),
      lessThan(manager.indexOf('Dpkg::Options::="--force-confold" upgrade')),
    );
    expect(
      manager.indexOf('Dpkg::Options::="--force-confold" upgrade'),
      lessThan(manager.indexOf('install \\\n    proot-distro curl openssl')),
    );
    expect(manager, contains('curl --version >/dev/null 2>&1'));
    expect(manager, isNot(contains('full-upgrade')));
    expect(manager, isNot(contains('pkg install -y proot-distro curl')));
  });

  test(
    'healthy custom repository skips apt and drains long named-container help',
    () {
      final directory = Directory.systemTemp.createTempSync(
        'oc-termux-health-',
      );
      addTearDown(() => directory.deleteSync(recursive: true));
      final home = Directory('${directory.path}/home')..createSync();
      final prefix = Directory('${directory.path}/prefix')..createSync();
      final apt = Directory('${prefix.path}/etc/apt')
        ..createSync(recursive: true);
      final sources = File('${apt.path}/sources.list')
        ..writeAsStringSync(
          'deb https://healthy.example.test/termux-main stable main\n',
        );
      final bin = Directory('${directory.path}/bin')..createSync();
      final curl = File('${bin.path}/curl')
        ..writeAsStringSync('''#!/usr/bin/env bash
if [ "\${1:-}" = --version ]; then echo 'curl healthy'; exit 0; fi
exit 64
''');
      final proot = File('${bin.path}/proot-distro')
        ..writeAsStringSync('''#!/usr/bin/env bash
if [ "\${1:-}" = install ] && [ "\${2:-}" = --help ]; then
  i=0
  while [ "\$i" -lt 10000 ]; do printf 'long help line %s\\n' "\$i"; i=\$((i + 1)); done
  printf '%s\\n' '  --name NAME'
  exit 0
fi
exit 64
''');
      final aptMarker = File('${directory.path}/apt-called');
      final aptGet = File('${bin.path}/apt-get')
        ..writeAsStringSync('''#!/usr/bin/env bash
: > "\$APT_MARKER"
exit 99
''');
      final chmod = Process.runSync('chmod', [
        '700',
        curl.path,
        proot.path,
        aptGet.path,
      ]);
      expect(chmod.exitCode, 0, reason: '${chmod.stdout}\n${chmod.stderr}');

      final manager = TermuxBridge.managerScriptForTesting();
      final functionPrefix = manager.substring(
        0,
        manager.indexOf('\ninstall_ubuntu_base() {'),
      );
      final probe = File('${directory.path}/probe.sh')
        ..writeAsStringSync(
          '$functionPrefix\nCURRENT_PORT=4096\nprepare_termux_dependencies\n',
        );
      final result = Process.runSync(
        'bash',
        [probe.path],
        environment: {
          ...Platform.environment,
          'HOME': home.path,
          'PREFIX': prefix.path,
          'APT_MARKER': aptMarker.path,
          'PATH': '${bin.path}:${Platform.environment['PATH'] ?? ''}',
        },
      );

      expect(result.exitCode, 0, reason: '${result.stdout}\n${result.stderr}');
      expect(result.stdout, contains('package upgrade skipped'));
      expect(aptMarker.existsSync(), isFalse);
      expect(
        sources.readAsStringSync(),
        'deb https://healthy.example.test/termux-main stable main\n',
      );
      expect(File('${sources.path}.oc-before-opencode').existsSync(), isFalse);
    },
  );

  test('unexpected manager errors persist the active setup stage', () {
    final manager = TermuxBridge.managerScriptForTesting();

    expect(manager, contains(r'stage=$(read_state_value message)'));
    expect(
      manager,
      contains(
        r'write_state failed "$stage failed (exit $code; setup line $line)"',
      ),
    );
    expect(
      manager,
      contains(
        "fail_setup 'Could not complete the safe Termux package upgrade'",
      ),
    );
  });

  test(
    'install and server logs are bounded and rotated by executable code',
    () {
      final directory = Directory.systemTemp.createTempSync('oc-log-test-');
      addTearDown(() => directory.deleteSync(recursive: true));
      final home = Directory('${directory.path}/home')..createSync();
      final manager = File('${directory.path}/manager.sh')
        ..writeAsStringSync(TermuxBridge.managerScriptForTesting());
      final input = File('${directory.path}/input.log');
      final line = '${List.filled(1023, 'x').join()}\n';
      input.writeAsStringSync(List.filled(2300, line).join());

      final result = Process.runSync(
        'bash',
        [
          '-c',
          'bash "\$1" write-log install < "\$2"',
          '_',
          manager.path,
          input.path,
        ],
        environment: {...Platform.environment, 'HOME': home.path},
      );

      expect(result.exitCode, 0, reason: '${result.stdout}\n${result.stderr}');
      final logDirectory = Directory('${home.path}/.oc');
      final logs = logDirectory
          .listSync()
          .whereType<File>()
          .where((file) => file.path.contains('install.log'))
          .toList();
      expect(logs, isNotEmpty);
      expect(logs.length, lessThanOrEqualTo(3));
      for (final log in logs) {
        expect(log.lengthSync(), lessThanOrEqualTo(1048576), reason: log.path);
      }
    },
  );

  test(
    'server runs through the bounded logger and stop validates its runner',
    () {
      final manager = TermuxBridge.managerScriptForTesting();

      expect(manager, contains(r'2>&1 | "$manager" write-log server'));
      expect(manager, contains(r'[ "${args[1]:-}" = "$SERVER_RUNNER" ]'));
      expect(manager, contains(r'[ "${args[2]:-}" = "$port" ]'));
      expect(
        manager,
        contains(r'''printf '%s %s\n' "$server_pid" "$server_start"'''),
      );
      expect(manager, contains('group_is_managed_tree'));
      expect(manager, contains(r'kill -KILL -- "-$group"'));
    },
  );

  test(
    'manager stops its verified process group but refuses a decoy',
    () async {
      final home = Directory.systemTemp.createTempSync('oc-manager-stop-');
      addTearDown(() => home.deleteSync(recursive: true));
      final ocDirectory = Directory('${home.path}/.oc')..createSync();
      final manager = File('${ocDirectory.path}/manager.sh')
        ..writeAsStringSync(TermuxBridge.managerScriptForTesting());
      final runner = File('${ocDirectory.path}/server-runner.sh')
        ..writeAsStringSync('''#!/usr/bin/env bash
sleep 60 &
wait
''');
      final password = File('${ocDirectory.path}/server.password')
        ..writeAsStringSync('test');
      await Process.run('chmod', ['700', manager.path, runner.path]);

      final managed = await Process.start('setsid', [
        runner.path,
        '4096',
        password.path,
        manager.path,
      ]);
      addTearDown(() async {
        await Process.run('kill', ['-KILL', '--', '-${managed.pid}']);
      });
      await Future<void>.delayed(const Duration(milliseconds: 100));
      File(
        '${ocDirectory.path}/server.pid',
      ).writeAsStringSync('${managed.pid} ${_processStart(managed.pid)}\n');
      final stopped = await Process.run(
        'bash',
        [manager.path, 'stop', '4096'],
        environment: {...Platform.environment, 'HOME': home.path},
      );
      expect(
        stopped.exitCode,
        0,
        reason: '${stopped.stdout}\n${stopped.stderr}',
      );
      expect(
        await managed.exitCode.timeout(const Duration(seconds: 2)),
        isNot(0),
      );

      final decoy = await Process.start('setsid', ['sleep', '60']);
      addTearDown(() {
        decoy.kill(ProcessSignal.sigkill);
      });
      await Future<void>.delayed(const Duration(milliseconds: 50));
      File(
        '${ocDirectory.path}/server.pid',
      ).writeAsStringSync('${decoy.pid} ${_processStart(decoy.pid)}\n');
      final refused = await Process.run(
        'bash',
        [manager.path, 'stop', '4096'],
        environment: {...Platform.environment, 'HOME': home.path},
      );
      expect(refused.exitCode, isNot(0));
      expect(Process.runSync('kill', ['-0', '${decoy.pid}']).exitCode, 0);
      decoy.kill(ProcessSignal.sigkill);
      await decoy.exitCode;
    },
    skip: !Platform.isLinux,
  );

  test('Ubuntu setup bypasses registries and verifies Canonical archives', () {
    final manager = TermuxBridge.managerScriptForTesting();

    expect(manager, isNot(contains('proot-distro install ubuntu')));
    expect(manager, contains('cdimage.ubuntu.com/ubuntu-base/releases/24.04'));
    expect(manager, contains('ubuntu-base-24.04.4-base-arm64.tar.gz'));
    expect(manager, contains('ubuntu-base-24.04.4-base-armhf.tar.gz'));
    expect(manager, contains('ubuntu-base-24.04.4-base-amd64.tar.gz'));
    expect(
      manager,
      contains(
        '04207713ece899c3740823d33690441ad3a7f0ded1101aca744e2b0f37ac7ff2',
      ),
    );
    expect(manager, contains("printf '%s  %s\\n' \"\$checksum\""));
    expect(manager, contains('sha256sum -c -'));
    expect(
      manager,
      contains('proot-distro install "\$archive" --name "\$PROOT_NAME"'),
    );
  });

  test('OpenCode Ubuntu is isolated in its own v4 or v5 container', () {
    final manager = TermuxBridge.managerScriptForTesting();

    expect(manager, contains('PROOT_NAME=opencode-ubuntu'));
    expect(manager, contains(r'containers/$PROOT_NAME/rootfs'));
    expect(manager, contains(r'installed-rootfs/$PROOT_NAME'));
    expect(manager, contains(r'proot-distro login "$PROOT_NAME" -- true'));
    expect(manager, isNot(contains('proot-distro login ubuntu')));
    expect(manager, isNot(contains('proot-distro remove ubuntu')));
  });

  test('npm setup prefers IPv4 and retries transient downloads', () {
    final manager = TermuxBridge.managerScriptForTesting();

    expect(manager, contains('--dns-result-order=ipv4first'));
    expect(manager, contains('--fetch-retries=5'));
    expect(manager, contains('--fetch-timeout=300000'));
    expect(manager, contains('install_opencode ||'));
    expect(manager, contains('retrying in 10 seconds'));
  });

  test('only app-owned partial Ubuntu installs can be removed', () {
    final manager = TermuxBridge.managerScriptForTesting();

    expect(
      manager,
      contains(r'UBUNTU_INSTALL_MARKER="$OC_DIR/opencode-ubuntu-installing"'),
    );
    expect(manager, contains(r'[ -f "$UBUNTU_INSTALL_MARKER" ]'));
    expect(
      manager,
      contains(r'[ -f "$UBUNTU_INSTALL_MARKER" ] || ! ubuntu_usable'),
    );
    expect(manager, contains('setup will not delete it'));
    expect(manager, contains(r'proot-distro remove "$PROOT_NAME"'));
    expect(
      manager,
      contains('Could not remove the interrupted app-owned Ubuntu install'),
    );
  });

  test('only explicit manager launch markers are accepted', () {
    expect(TermuxBridge.isLaunchAcknowledged('manager-started:123'), isTrue);
    expect(
      TermuxBridge.isLaunchAcknowledged('manager-already-running:456'),
      isTrue,
    );
    expect(TermuxBridge.isLaunchAcknowledged(''), isFalse);
    expect(
      TermuxBridge.isLaunchAcknowledged('setup-lock-unavailable'),
      isFalse,
    );
  });

  test('manager-missing stop validates the lock owner before clearing', () {
    final script = TermuxBridge.stopScript(port: 4096);

    expect(script, contains(r'read -r lock_pid lock_start'));
    expect(script, contains(r'[ "$lock_start" = "$live_start" ]'));
    expect(script, contains(r'[ "${args[1]:-}" = "$OC_DIR/manager.sh" ]'));
    expect(script, contains(r'[ "${args[2]:-}" = setup ]'));
    expect(script, contains(r'[ "$stopped_start" != "$lock_start" ]'));
    expect(script, contains(r'kill -KILL -- "-$pid"'));
    expect(script, contains(r'[ "$lock_owned" = 1 ]'));
  });
}
