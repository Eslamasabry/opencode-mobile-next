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
}
