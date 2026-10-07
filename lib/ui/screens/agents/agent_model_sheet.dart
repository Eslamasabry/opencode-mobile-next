import 'dart:async';

import 'package:flutter/material.dart';

import '../../../domain/model_display_name.dart';
import '../../../domain/phone_agents_source.dart';
import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../../kit/kit.dart';
import '../../widgets/product_states.dart' show productErrorText;

/// The model chip on New conversation while an agent on this phone (Claude
/// Code, …) is chosen: its own models, not the OpenCode server's.
class AgentModelChip extends StatefulWidget {
  const AgentModelChip({
    super.key,
    required this.agents,
    required this.agentId,
  });

  final PhoneAgentsSource agents;
  final String agentId;

  @override
  State<AgentModelChip> createState() => _AgentModelChipState();
}

class _AgentModelChipState extends State<AgentModelChip> {
  String? _named;

  /// A chosen model is named ("Sonnet 5"), not shown by its id: the names
  /// are read from the agent once.
  void _learnNames(String agentId) {
    if (_named == agentId) return;
    _named = agentId;
    unawaited(
      widget.agents.agentModels(agentId).then((models) {
        _AgentModelNames.remember(agentId, models);
        if (mounted) setState(() {});
      }, onError: (Object _) {}),
    );
  }

  @override
  Widget build(BuildContext context) {
    final agents = widget.agents;
    final agentId = widget.agentId;
    final l10n = AppLocalizations.of(context);
    final chosen = agents.selectedAgentModel(agentId);
    if (chosen != null && _AgentModelNames.of(agentId, chosen) == null) {
      _learnNames(agentId);
    }
    return KitComposerChips.model(
      chipKey: const ValueKey('chats-new-agent-model'),
      label: chosen == null
          ? l10n.agentsModelDefault
          : _AgentModelNames.of(agentId, chosen) ?? modelNameFromId(chosen),
      state: chosen == null
          ? KitModelChipState.serverDefault
          : KitModelChipState.chosen,
      onPressed: () => unawaited(
        showAgentModelSheet(context, agents: agents, agentId: agentId),
      ),
    );
  }
}

/// Names read the last time the sheet listed an agent's models, so the chip
/// can say "Sonnet" instead of an id without asking the host on every build.
abstract final class _AgentModelNames {
  static final _names = <String, Map<String, String>>{};

  static String? of(String agentId, String modelId) =>
      _names[agentId]?[modelId];

  static void remember(String agentId, List<AgentModelChoice> models) =>
      _names[agentId] = {for (final model in models) model.id: model.name};
}

/// "Choose a model": the agent's own default first, then each model it
/// offers, with a check on the current one. One sheet.
Future<void> showAgentModelSheet(
  BuildContext context, {
  required PhoneAgentsSource agents,
  required String agentId,
}) {
  final l10n = AppLocalizations.of(context);
  final models = agents.agentModels(agentId);
  return showKitSheet<void>(
    context,
    sheetKey: const ValueKey('agents-model-sheet'),
    title: l10n.agentsModelTitle,
    body: (sheetContext) => FutureBuilder<List<AgentModelChoice>>(
      future: models,
      builder: (context, snapshot) {
        if (snapshot.hasError) {
          return KitNotice.error(
            key: const ValueKey('agents-model-error'),
            message: productErrorText(snapshot.error!, l10n: l10n),
          );
        }
        final list = snapshot.data;
        if (list == null) {
          return KitText(
            l10n.agentsModelLoading,
            key: const ValueKey('agents-model-loading'),
            tone: KitTextTone.secondary,
          );
        }
        _AgentModelNames.remember(agentId, list);
        final chosen = agents.selectedAgentModel(agentId);
        Widget check(bool on) => on
            ? const KitIcon(AppIconography.check, size: KitIconSize.small)
            : const SizedBox.shrink();
        Future<void> pick(String? id) async {
          await agents.selectAgentModel(agentId, id);
          if (sheetContext.mounted) KitSheet.close(sheetContext, null);
        }

        return KitRowGroup(
          margin: EdgeInsets.zero,
          children: [
            KitRow(
              key: const ValueKey('agents-model-default'),
              title: l10n.agentsModelDefault,
              trailing: check(chosen == null),
              selected: chosen == null,
              onTap: () => unawaited(pick(null)),
            ),
            for (final model in list.where((m) => m.id != 'default'))
              KitRow(
                key: ValueKey('agents-model-${model.id}'),
                title: KitBidi.auto(model.name),
                trailing: check(chosen == model.id),
                selected: chosen == model.id,
                onTap: () => unawaited(pick(model.id)),
              ),
          ],
        );
      },
    ),
  );
}
