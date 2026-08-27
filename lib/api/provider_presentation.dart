import 'models.dart';
import 'product_repository.dart';

/// User-facing identity for provider IDs that OpenCode exposes as compatibility
/// aliases. Wire IDs stay untouched; this layer only removes duplicate choices
/// and legacy branding from the UI.
class ProviderPresentation {
  const ProviderPresentation({required this.groupID, required this.name});

  final String groupID;
  final String name;
}

ProviderPresentation presentProvider(String providerID, [String? serverName]) {
  return switch (providerID.toLowerCase()) {
    'zai' ||
    'zhipuai' => const ProviderPresentation(groupID: 'zai', name: 'Z.AI'),
    'zai-coding-plan' || 'zhipuai-coding-plan' => const ProviderPresentation(
      groupID: 'zai-coding-plan',
      name: 'Z.AI Coding Plan',
    ),
    _ => ProviderPresentation(
      groupID: providerID,
      name: serverName?.trim().isNotEmpty == true
          ? serverName!.trim()
          : providerID,
    ),
  };
}

bool isConsolidatedProviderAlias(String providerID) =>
    switch (providerID.toLowerCase()) {
      'zai' || 'zhipuai' || 'zai-coding-plan' || 'zhipuai-coding-plan' => true,
      _ => false,
    };

String presentedModelLabel(String providerID, String modelID) =>
    isConsolidatedProviderAlias(providerID)
    ? '${presentProvider(providerID).name} · $modelID'
    : '$providerID/$modelID';

int _aliasPriority(String providerID) => switch (providerID.toLowerCase()) {
  'zai' || 'zai-coding-plan' => 0,
  'zhipuai' || 'zhipuai-coding-plan' => 1,
  _ => 0,
};

class PresentedProvider {
  const PresentedProvider({
    required this.id,
    required this.name,
    required this.providerIDs,
  });

  final String id;
  final String name;
  final Set<String> providerIDs;
}

List<PresentedProvider> presentProviders(Iterable<CatalogProvider> providers) {
  final groups = <String, ({String name, Set<String> ids})>{};
  for (final provider in providers.where((provider) => provider.enabled)) {
    final presentation = presentProvider(provider.id, provider.name);
    final current = groups[presentation.groupID];
    groups[presentation.groupID] = (
      name: presentation.name,
      ids: {...?current?.ids, provider.id},
    );
  }
  return [
    for (final entry in groups.entries)
      PresentedProvider(
        id: entry.key,
        name: entry.value.name,
        providerIDs: entry.value.ids,
      ),
  ];
}

List<CatalogModel> presentModels(
  Iterable<CatalogModel> models, {
  ModelRef? selected,
}) {
  final result = <String, CatalogModel>{};
  for (final model in models) {
    final groupID = presentProvider(model.providerID).groupID;
    final key = '$groupID\u0000${model.id}';
    final current = result[key];
    if (current == null) {
      result[key] = model;
      continue;
    }
    final candidateSelected =
        selected?.providerID == model.providerID &&
        selected?.modelID == model.id;
    final currentSelected =
        selected?.providerID == current.providerID &&
        selected?.modelID == current.id;
    if (candidateSelected ||
        (!currentSelected &&
            _aliasPriority(model.providerID) <
                _aliasPriority(current.providerID))) {
      result[key] = model;
    }
  }
  return result.values.toList();
}

String presentedProviderName(
  String providerID,
  Iterable<CatalogProvider> providers,
) {
  final match = providers.where((provider) => provider.id == providerID);
  return presentProvider(
    providerID,
    match.isEmpty ? null : match.first.name,
  ).name;
}

class PresentedIntegration {
  const PresentedIntegration({
    required this.integration,
    required this.name,
    required this.connected,
  });

  final IntegrationInfo integration;
  final String name;
  final bool connected;
}

List<PresentedIntegration> presentIntegrations(
  Iterable<IntegrationInfo> integrations,
) {
  final groups = <String, List<IntegrationInfo>>{};
  for (final integration in integrations) {
    final groupID = presentProvider(integration.id, integration.name).groupID;
    groups.putIfAbsent(groupID, () => []).add(integration);
  }
  return [
    for (final entries in groups.values) _presentIntegrationGroup(entries),
  ];
}

PresentedIntegration _presentIntegrationGroup(List<IntegrationInfo> entries) {
  entries.sort((a, b) {
    final byMethods = (b.methods.isNotEmpty ? 1 : 0).compareTo(
      a.methods.isNotEmpty ? 1 : 0,
    );
    if (byMethods != 0) return byMethods;
    return _aliasPriority(a.id).compareTo(_aliasPriority(b.id));
  });
  final integration = entries.first;
  return PresentedIntegration(
    integration: integration,
    name: presentProvider(integration.id, integration.name).name,
    connected: entries.any((entry) => entry.connectionCount > 0),
  );
}
