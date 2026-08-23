import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/termux/bridge.dart';

void main() {
  test('generated termux scripts pass bash syntax validation', () {
    final directory = Directory('/tmp/opencode/scripts')
      ..createSync(recursive: true);
    final scripts = {
      'install.sh': TermuxBridge.installAndServeScript(
        port: 4096,
        password: 'test-password',
      ),
      'manager.sh': TermuxBridge.managerScriptForTesting(),
      'diagnostics.sh': TermuxBridge.diagnosticsScript(),
      'snapshot.sh': TermuxBridge.setupSnapshotScript(),
      'status.sh': TermuxBridge.statusScript(),
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
    expect(script, contains(r'> "$OC_DIR/install.log" 2>&1'));
    expect(script, contains(r'rm -f "$OC_DIR/server-log.active"'));
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
    expect(manager, contains('proot-distro install "\$archive" --name ubuntu'));
  });

  test('Ubuntu detection supports v4 and v5 proot-distro layouts', () {
    final manager = TermuxBridge.managerScriptForTesting();

    expect(manager, contains('containers/ubuntu/rootfs'));
    expect(manager, contains('installed-rootfs/ubuntu'));
    expect(manager, contains('proot-distro login ubuntu -- true'));
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

    expect(manager, contains('UBUNTU_INSTALL_MARKER='));
    expect(manager, contains(r'[ -f "$UBUNTU_INSTALL_MARKER" ]'));
    expect(
      manager,
      contains(r'[ -f "$UBUNTU_INSTALL_MARKER" ] || ! ubuntu_usable'),
    );
    expect(manager, contains('setup will not delete it'));
    expect(manager, contains('proot-distro remove ubuntu'));
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
