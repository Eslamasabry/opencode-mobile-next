import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/termux/bridge.dart';

/// Every shell script the bridge embeds, rendered the way the app sends it.
/// The SHA-256 of each is pinned in test/fixtures/termux_script_hashes.json.
/// The original snapshot at f4308b6e guarded the extraction into scripts/.
/// The six aiteamVerbScript snapshots also include the reviewed BC1 package
/// repair checks (77b77fd5) and BC4 update journal helpers (6c84e33d):
/// aiteamVerbScript embeds aiteamScript, whose packages part uses
/// withSetupPrelude. BC2 storage admission and BC3 Paseo checks do not change
/// these Termux payloads. Unrelated script hashes retain the original snapshot.
/// Refresh only after reviewing intentional changes, including shared helpers;
/// byte drift otherwise fails here, even when the bridge itself is unchanged.
Map<String, String> _scripts() => {
  'setupBaseScript': TermuxBridge.setupBaseScript,
  'managerScript': TermuxBridge.managerScriptForTesting(),
  'toolsScript': TermuxBridge.toolsScriptForTesting(),
  'localAgentsScript': TermuxBridge.localAgentsScriptForTesting(),
  'ensureWakeLockScript': TermuxBridge.ensureWakeLockScript,
  'unlockCommand': TermuxBridge.unlockCommand,
  'storageScript': TermuxBridge.storageScript(),
  'installationScript': TermuxBridge.installationScript(),
  'statusScript': TermuxBridge.statusScript(),
  'setupSnapshotScript': TermuxBridge.setupSnapshotScript(),
  'diagnosticsScript': TermuxBridge.diagnosticsScript(),
  'stopScript': TermuxBridge.stopScript(),
  'stopScript:5000': TermuxBridge.stopScript(port: 5000),
  'createProjectFolderScript': TermuxBridge.createProjectFolderScript('demo'),
  'recoveryControlScript:enable': TermuxBridge.recoveryControlScript(
    'tok_1',
    enable: true,
  ),
  'recoveryControlScript:disable': TermuxBridge.recoveryControlScript(
    'tok_1',
    enable: false,
  ),
  for (final verb in TermuxBridge.toolVerbs)
    'toolsCommandScript:$verb': TermuxBridge.toolsCommandScript(
      verb,
      argument: verb == 'storage-clean' ? 'cache' : '',
    ),
  'installAndServeScript:1': TermuxBridge.installAndServeScript(
    password: "p'w\$d",
  ),
  'installAndServeScript:2': TermuxBridge.installAndServeScript(
    port: 4100,
    password: 'secret',
    runtime: TermuxRuntime.openCode2,
  ),
  'installAndServeScript:latest': TermuxBridge.installAndServeScript(
    password: 'secret',
    version: TermuxBridge.latestOpenCodeVersion,
  ),
  'restartScript:plain': TermuxBridge.restartScript(operationID: 'op1'),
  'restartScript:recovery': TermuxBridge.restartScript(
    port: 4097,
    operationID: 'op2',
    recoveryToken: 'rt',
    expectedOperationID: 'op1',
  ),
  'restartScript:switch': TermuxBridge.restartScript(
    operationID: 'op3',
    switchTarget: TermuxRuntime.openCode2,
    switchPassword: "pw'x",
  ),
  'aiteamProjectsScript': TermuxBridge.aiteamProjectsScript(),
  'aiteamLogTailScript': TermuxBridge.aiteamLogTailScript(),
  for (final verb in ['status', 'log', 'install', 'start', 'stop'])
    'aiteamVerbScript:$verb': TermuxBridge.aiteamVerbScript(
      verb,
      pinsFile: 'a=b\n',
    ),
  'aiteamVerbScript:init': TermuxBridge.aiteamVerbScript(
    'init',
    args: ['/root/projects/demo', '--rig', 'x'],
    pinsFile: 'a=b\n',
  ),
  'localAgentsPinsFile': TermuxBridge.localAgentsPinsFile(),
  'localAgentsLogTailScript': TermuxBridge.localAgentsLogTailScript(),
  for (final verb in TermuxBridge.localAgentsVerbs)
    'localAgentsVerbScript:$verb': TermuxBridge.localAgentsVerbScript(
      verb,
      args: verb == 'ensure-project' ? ['demo'] : const [],
    ),
};

String _sha(String text) => sha256.convert(utf8.encode(text)).toString();

void main() {
  const fixture = 'test/fixtures/termux_script_hashes.json';

  test('every embedded script matches its reviewed byte snapshot', () {
    final hashes = {
      for (final entry in _scripts().entries) entry.key: _sha(entry.value),
    };
    if (Platform.environment['WRITE_SCRIPT_HASHES'] == '1') {
      File(fixture).writeAsStringSync(
        '${const JsonEncoder.withIndent('  ').convert(hashes)}\n',
      );
      return;
    }
    final expected = (jsonDecode(File(fixture).readAsStringSync()) as Map)
        .cast<String, String>();
    expect(hashes.keys.toSet(), expected.keys.toSet());
    for (final entry in expected.entries) {
      expect(hashes[entry.key], entry.value, reason: entry.key);
    }
  });
}
