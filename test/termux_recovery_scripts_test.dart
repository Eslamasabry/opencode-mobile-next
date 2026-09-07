import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/termux/bridge.dart';

void main() {
  test(
    'storage accepts checked counts and rejects malformed or impossible responses',
    () {
      final snapshot = TermuxStorageSnapshot.parse(
        'total_kib=1048576\navailable_kib=524288\n',
      );
      expect(snapshot.totalBytes, 1073741824);
      expect(snapshot.availableBytes, 536870912);
      for (final value in [
        '',
        'total_kib=12',
        'total_kib=0\navailable_kib=0',
        'total_kib=1\navailable_kib=2',
        'total_kib=1\navailable_kib=-1',
        'total_kib=1\navailable_kib=0\navailable_kib=1',
        'total_kib=1\navailable_kib=0\nother=secret',
      ]) {
        expect(
          () => TermuxStorageSnapshot.parse(value),
          throwsA(isA<TermuxBridgeException>()),
        );
      }
    },
  );

  test('recovery scripts validate syntax and shell identities', () {
    final directory = Directory.systemTemp.createTempSync(
      'oc-recovery-syntax-',
    );
    addTearDown(() => directory.deleteSync(recursive: true));
    final scripts = [
      TermuxBridge.storageScript(),
      TermuxBridge.managerScriptForTesting(),
      TermuxBridge.recoveryControlScript('permit-1', enable: true),
      TermuxBridge.recoveryControlScript('permit-1', enable: false),
      TermuxBridge.restartScript(
        operationID: 'attempt-1',
        recoveryToken: 'permit-1',
        expectedOperationID: '',
      ),
    ];
    for (var i = 0; i < scripts.length; i++) {
      final script = File('${directory.path}/$i.sh')
        ..writeAsStringSync(scripts[i]);
      final result = Process.runSync('bash', ['-n', script.path]);
      expect(result.exitCode, 0, reason: '${result.stderr}');
    }
    expect(
      () =>
          TermuxBridge.recoveryControlScript("x'; touch /tmp/no", enable: true),
      throwsArgumentError,
    );
    expect(
      () => TermuxBridge.restartScript(
        operationID: 'one',
        recoveryToken: 'token',
        expectedOperationID: "';bad",
      ),
      throwsArgumentError,
    );
  });

  test(
    'executed preflight refuses changed operation, revoked permit, live PID and occupied port',
    () {
      final directory = Directory.systemTemp.createTempSync(
        'oc-recovery-policy-',
      );
      addTearDown(() => directory.deleteSync(recursive: true));
      final manager = TermuxBridge.managerScriptForTesting();
      final functions = manager.substring(
        0,
        manager.indexOf('\ncase "\${1:-status}" in'),
      );
      for (final scenario in [
        'allowed',
        'operation',
        'permit',
        'live',
        'port',
        'setup-failed',
        'stopped',
      ]) {
        final home = Directory('${directory.path}/$scenario')..createSync();
        final script = File('${home.path}/fixture.sh')
          ..writeAsStringSync('''
$functions
CURRENT_PORT=4096
CURRENT_OPERATION=original
write_state failed 'synthetic crash' 4096 proot '' '' '' crash
printf '%s' permit > "\$RECOVERY_PERMIT"
printf '123 456\\n' > "\$SERVER_PID"
kill() { [ '$scenario' = live ]; }
recovery_port_busy() { [ '$scenario' = port ]; }
case '$scenario' in
  operation) CURRENT_OPERATION=another; write_state failed changed 4096 proot '' '' '' crash ;;
  permit) rm "\$RECOVERY_PERMIT" ;;
  setup-failed) write_state failed 'install failed' 4096 ;;
  stopped) write_state stopped stopped 4096 ;;
esac
recovery_preflight original permit
''');
        final result = Process.runSync(
          'bash',
          [script.path],
          environment: {...Platform.environment, 'HOME': home.path},
        );
        expect(
          result.exitCode,
          scenario == 'allowed' ? 0 : 75,
          reason: '$scenario: ${result.stderr}',
        );
      }
    },
  );

  test(
    'executed disarm revokes before cancelling its owned startup and retries incomplete cancellation',
    () {
      final directory = Directory.systemTemp.createTempSync(
        'oc-recovery-disarm-',
      );
      addTearDown(() => directory.deleteSync(recursive: true));
      final manager = TermuxBridge.managerScriptForTesting();
      final functions = manager.substring(
        0,
        manager.indexOf('\ncase "\${1:-status}" in'),
      );
      final script = File('${directory.path}/fixture.sh')
        ..writeAsStringSync('''
$functions
CURRENT_OPERATION=attempt-1
CURRENT_RECOVERY=permit
write_state restarting synthetic 4096
printf '%s' permit > "\$RECOVERY_PERMIT"
stop_setup() { [ ! -e "\$RECOVERY_PERMIT" ]; return 75; }
if recovery_disarm permit; then exit 99; fi
[ ! -e "\$RECOVERY_PERMIT" ]
stop_setup() { [ ! -e "\$RECOVERY_PERMIT" ]; printf 'setup\\n'; }
stop_server() { printf 'server\\n'; }
recovery_disarm permit
[ "\$(read_state_value phase)" = stopped ]
''');
      final result = Process.runSync(
        'bash',
        [script.path],
        environment: {...Platform.environment, 'HOME': directory.path},
      );
      expect(result.exitCode, 0, reason: '${result.stderr}');
      expect(result.stdout, 'setup\nserver\n');
    },
  );
}
