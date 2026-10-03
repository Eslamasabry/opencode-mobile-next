import '../../domain/agent_catalog.dart';
import '../../l10n/app_localizations.dart';
import '../setup/components.dart';
import '../setup/setup_contract.dart';
import 'agent_scripts.dart';
import 'paseo_scripts.dart';

/// Same resumable setup engine; these tools do not create another server profile.
List<SetupComponent> phoneAgentComponents(
  AppLocalizations l10n,
  AgentDescriptor agent,
  String packageLock,
) {
  final common = setupComponents(l10n);
  final python = common.firstWhere((c) => c.id == SetupComponentIds.python);
  return [
    ...common.where(
      (c) => {
        SetupComponentIds.linux,
        SetupComponentIds.essentials,
      }.contains(c.id),
    ),
    python,
    SetupComponent(
      id: 'agent-user',
      title: 'Agent workspace',
      shortTitle: 'Workspace',
      dependsOn: const [SetupComponentIds.essentials],
      checkScript:
          r'[ "$(id -u oc)" = 1000 ] && [ "$(id -g oc)" = 1000 ] && [ -d /home/oc ];',
      installScript: AgentPhoneScripts.bootstrapUser,
    ),
    SetupComponent(
      id: 'agent-node',
      title: 'Agent host tools',
      shortTitle: 'Host tools',
      dependsOn: const ['agent-user'],
      agentUser: true,
      checkScript: PaseoPhoneScripts.nodeCheck,
      installScript: PaseoPhoneScripts.nodeInstall,
    ),
    SetupComponent(
      id: 'agent-paseo',
      title: 'Agent connection',
      shortTitle: 'Connection',
      dependsOn: const ['agent-node'],
      agentUser: true,
      checkScript: PaseoPhoneScripts.check,
      installScript: PaseoPhoneScripts.install(packageLock: packageLock),
    ),
    SetupComponent(
      id: 'agent-${agent.id}',
      title: agent.name,
      shortTitle: agent.name,
      dependsOn: const ['agent-paseo', SetupComponentIds.python],
      agentUser: true,
      checkScript: AgentPhoneScripts.check(agent),
      installScript: AgentPhoneScripts.install(agent),
    ),
  ];
}
