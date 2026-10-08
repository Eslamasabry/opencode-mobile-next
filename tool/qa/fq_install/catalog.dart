import 'dart:convert';
import 'dart:io';
import 'package:opencode_mobile/domain/agent_catalog.dart';
import 'package:opencode_mobile/builtin/agents/agent_scripts.dart';

void main() {
  const ids = ['codex', 'gemini', 'qwen', 'goose', 'omp-acp', 'fx'];
  stdout.write(
    jsonEncode({
      for (final id in ids)
        id: {
          'name': AgentCatalog.builtIn.byId(id)!.name,
          'version': AgentCatalog.builtIn.byId(id)!.recipe!.version,
          'executable': AgentCatalog.builtIn.byId(id)!.recipe!.executable,
          'sha256': AgentCatalog.builtIn
              .byId(id)!
              .artifactFor(AgentArchitecture.x64)!
              .sha256,
          'downloadBytes': AgentCatalog.builtIn
              .byId(id)!
              .artifactFor(AgentArchitecture.x64)!
              .downloadBytes,
          'authScript': AgentPhoneScripts.authProbe(
            AgentCatalog.builtIn.byId(id)!,
          ),
          'launchArgs': AgentCatalog.builtIn.byId(id)!.recipe!.launchArgs,
        },
    }),
  );
}
