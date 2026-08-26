import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/termux/bridge.dart';

void main() {
  test('generated termux scripts pass bash syntax validation', () {
    final directory = Directory.systemTemp.createTempSync('oc-scripts-');
    addTearDown(() => directory.deleteSync(recursive: true));
    final scripts = {
      'install.sh': TermuxBridge.installAndServeScript(
        port: 4096,
        password: 'test-password',
      ),
      'manager.sh': TermuxBridge.managerScriptForTesting(),
      'diagnostics.sh': TermuxBridge.diagnosticsScript(),
      'snapshot.sh': TermuxBridge.setupSnapshotScript(),
      'status.sh': TermuxBridge.statusScript(),
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
''');

    expect(status.isReady, isTrue);
    expect(status.isRunning, isFalse);
    expect(status.port, 4096);
    expect(status.version, '1.2.3');
    expect(status.pid, 1234);
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

  test('default setup tracks the latest stable OpenCode release', () {
    final script = TermuxBridge.installAndServeScript(
      port: 4096,
      password: 'test-password',
    );
    final manager = TermuxBridge.managerScriptForTesting();

    expect(TermuxBridge.defaultOpenCodeVersion, 'latest');
    expect(script, contains("setup '4096' 'latest'"));
    expect(manager, contains(r'local requested_version="${2:-latest}"'));
    expect(manager, contains('"opencode-ai@\$OC_REQUESTED_VERSION"'));
    expect(manager, contains('opencode models --refresh'));
    expect(manager, contains('refreshing_models'));
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
      expect(manager, contains(r'*"$SERVER_RUNNER $port "*'));
      expect(manager, contains(r'kill_tree "$pid"'));
    },
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
    expect(script, contains(r'$OC_DIR/manager.sh setup '));
    expect(script, contains(r'kill_tree "$pid"'));
    expect(script, contains(r'kill -KILL "$root"'));
    expect(script, contains(r'[ "$lock_owned" = 1 ]'));
  });
}
