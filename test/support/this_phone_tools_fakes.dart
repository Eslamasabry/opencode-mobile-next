// Fakes for This phone's per-tool removal (slice P1.4): a registry shaped
// like the real one, and the phone's Linux answering the tools' scripts by
// name.
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/builtin/team/builtin_team.dart';

import '../revamp/screen_phone_1_fixtures.dart';

SetupComponent _tool(
  String id,
  String title, {
  List<String> dependsOn = const [],
  bool required = false,
  bool removable = true,
}) => SetupComponent(
  id: id,
  title: title,
  shortTitle: title,
  checkScript: 'check:$id',
  presenceScript: 'presence:$id',
  installScript: 'install:$id',
  removeScript: removable ? 'remove:$id' : null,
  sizeScript: removable ? 'size:$id' : null,
  dependsOn: dependsOn,
  required: required,
);

/// The registry's shape: required parts, Python, AI Team, and a made-up
/// optional tool that needs Python (no real one does yet).
final toolsRegistry = [
  _tool('node', 'Node.js', required: true, removable: false),
  _tool('opencode', 'OpenCode', dependsOn: ['node'], required: true),
  _tool('python', 'Python'),
  SetupComponent(
    id: 'aiteam',
    title: 'AI Team',
    shortTitle: 'AI Team',
    checkScript: 'check:aiteam',
    presenceScript: 'presence:aiteam',
    installScript: 'install:aiteam',
    removeScript: _aiTeamRemove,
    sizeScript: 'size:aiteam',
    dependsOn: const ['opencode'],
  ),
  _tool('notebooks', 'Notebooks', dependsOn: ['python']),
];

// ComponentRemovalService only accepts the registry's own AI Team script.
const _aiTeamRemove = '''set -eu
rm -f /usr/local/bin/gc /usr/local/bin/bd /usr/local/bin/dolt
rm -rf /opt/aiteam /var/cache/oc-setup/aiteam /root/aiteam /root/.gc
''';

/// The phone's Linux answering the tools' scripts by name.
class ToolsLinux extends PhoneLinux {
  ToolsLinux({this.present = const {'node', 'opencode', 'python', 'aiteam'}})
    : super(running: true) {
    installedTools.addAll(present);
  }

  final Set<String> present;
  final installedTools = <String>{};
  final ran = <String>[];
  int removeExit = 0;
  int uninstallFailures = 0;

  @override
  Future<String?> setupStatus() async => null;

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
    String? input,
  }) async {
    ran.add(script);
    final parts = script.split(':');
    if (parts.length == 2) {
      final id = parts[1];
      switch (parts[0]) {
        case 'presence':
          return BuiltinLinuxRunResult(
            exitCode: installedTools.contains(id) ? 0 : 1,
            output: '',
          );
        case 'size':
          return const BuiltinLinuxRunResult(exitCode: 0, output: '25600\n');
        case 'remove':
          if (removeExit == 0) installedTools.remove(id);
          return BuiltinLinuxRunResult(exitCode: removeExit, output: '');
      }
    }
    return super.run(script, timeout: timeout);
  }

  @override
  Future<void> uninstall() async {
    if (uninstallFailures > 0) {
      uninstallFailures--;
      throw const BuiltinLinuxException('rm: /data/…: permission denied');
    }
    await super.uninstall();
  }
}

class ToolsTeam extends BuiltinTeam {
  ToolsTeam(this.phone) : super(linux: phone);

  final ToolsLinux phone;
  int removes = 0;

  @override
  Future<void> remove() async {
    removes++;
    phone.installedTools.remove('aiteam');
  }
}
