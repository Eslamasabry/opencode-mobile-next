// Dumps the generated Termux scripts to /tmp so they can be syntax-checked
// with real bash. Run: flutter test test/termux_scripts_test.dart
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/termux/bridge.dart';

void main() {
  test('generated termux scripts are written for bash -n', () {
    Directory('/tmp/opencode/scripts').createSync(recursive: true);
    File('/tmp/opencode/scripts/install.sh')
        .writeAsStringSync(TermuxBridge.installAndServeScript(port: 4096));
    File('/tmp/opencode/scripts/log.sh')
        .writeAsStringSync(TermuxBridge.logScript(port: 4096));
    File('/tmp/opencode/scripts/unlock.sh')
        .writeAsStringSync(TermuxBridge.unlockCommand);
    File('/tmp/opencode/scripts/stop.sh')
        .writeAsStringSync(TermuxBridge.stopScript(port: 4096));
  });
}
