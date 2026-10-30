import 'dart:async';

import 'package:flutter/material.dart';

import '../../../domain/model_display_name.dart';
import '../../../domain/phone_agents_source.dart';
import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../../kit/kit.dart';
import '../../widgets/product_states.dart' show productErrorText;

/// The agent's model in words for a row value: the one the person chose
/// ("Sonnet"), else the agent's own default by its plain name ("Opus") once a
/// model list has said which it is, else "Default model". Names come from
/// the last model list ([rememberAgentModels]); until one is read a chosen
/// id is made readable instead. Never a raw id.
String agentModelLabel(
  AppLocalizations l10n,
  PhoneAgentsSource agents,
  String agentId,
) {
  final chosen = agents.selectedAgentModel(agentId);
  // A model's name is the agent's words: isolated, so "Opus 5.5" keeps its
  // order in a right-to-left row.
  if (chosen == null) {
    final fallback = _AgentModelNames.defaultOf(agentId);
    return fallback == null ? l10n.agentsModelDefault : KitBidi.auto(fallback);
  }
  return KitBidi.auto(
    _AgentModelNames.of(agentId, chosen) ?? modelNameFromId(chosen),
  );
}

/// Keeps [models]' names (and which one is the agent's default) for
/// [agentModelLabel]; the start screen calls it when it has read the list.
void rememberAgentModels(String agentId, List<AgentModelChoice> models) =>
    _AgentModelNames.remember(agentId, models);

/// The plain name of the model the agent uses when the person picks none,
/// or null when the list does not say: "Default (Opus)" names Opus, a real
/// model marked default names itself, and a bare "Default" alias (or
/// "Default (recommended)") names nothing.
String? agentDefaultModelName(List<AgentModelChoice> models) {
  final model = models.where((m) => m.isDefault).firstOrNull;
  if (model == null) return null;
  final name = model.name.trim();
  final inner = RegExp(
    r'^default\s*\((.+)\)$',
    caseSensitive: false,
  ).firstMatch(name)?.group(1)?.trim();
  if (inner != null) {
    const generic = {'recommended', 'auto', 'default'};
    return inner.isEmpty || generic.contains(inner.toLowerCase())
        ? null
        : inner;
  }
  if (model.id == 'default' || name.toLowerCase().startsWith('default')) {
    return null;
  }
  return name.isEmpty || name == model.id ? modelNameFromId(model.id) : name;
}

/// Names read the last time a list of an agent's models was read, so the
/// row can say "Sonnet" instead of an id without asking the host on every
/// build.
abstract final class _AgentModelNames {
  static final _names = <String, Map<String, String>>{};
  static final _defaults = <String, String?>{};

  static String? of(String agentId, String modelId) =>
      _names[agentId]?[modelId];

  static String? defaultOf(String agentId) => _defaults[agentId];

  static void remember(String agentId, List<AgentModelChoice> models) {
    _names[agentId] = {for (final model in models) model.id: model.name};
    _defaults[agentId] = agentDefaultModelName(models);
  }
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
