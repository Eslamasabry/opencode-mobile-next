import 'dart:io';

import 'package:opencode_mobile/builtin/agents/agent_scripts.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';

void main(List<String> args) {
  if (args.length != 2 || !RegExp(r'^[A-Za-z0-9_-]{1,80}$').hasMatch(args[0])) {
    throw ArgumentError('Provide the existing owner profile and output file.');
  }
  final home = '/home/oc/.oc-profiles/${args[0]}';
  File(args[1]).writeAsStringSync(
    "export HOME='$home' CLAUDE_CONFIG_DIR='$home/claude' CODEX_HOME='$home/codex' "
    'DISABLE_AUTOUPDATER=1 CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1\n'
    '${AgentPhoneScripts.authProbe(AgentCatalog.builtIn.byId('claude')!)}',
  );
}
