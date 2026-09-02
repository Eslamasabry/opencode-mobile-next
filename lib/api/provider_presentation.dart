import 'models.dart';
import 'product_repository.dart';

/// User-facing identity for closely related provider IDs exposed by OpenCode.
/// Wire IDs stay untouched; the UI shares a product-family filter while keeping
/// each regional backend route explicit and independently selectable.
class ProviderPresentation {
  const ProviderPresentation({
    required this.groupID,
    required this.name,
    this.route,
  });

  final String groupID;
  final String name;
  final String? route;
}

ProviderPresentation presentProvider(String providerID, [String? serverName]) {
  return switch (providerID.toLowerCase()) {
    'zai' => const ProviderPresentation(
      groupID: 'zai',
      name: 'Z.AI',
      route: 'Global',
    ),
    'zhipuai' => const ProviderPresentation(
      groupID: 'zai',
      name: 'Z.AI',
      route: 'China',
    ),
    'zai-coding-plan' => const ProviderPresentation(
      groupID: 'zai-coding-plan',
      name: 'Z.AI Coding Plan',
      route: 'Global',
    ),
    'zhipuai-coding-plan' => const ProviderPresentation(
      groupID: 'zai-coding-plan',
      name: 'Z.AI Coding Plan',
      route: 'China',
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
    ? '${presentedProviderRouteName(providerID)} · $modelID'
    : '$providerID/$modelID';

String presentedProviderRouteName(String providerID, [String? serverName]) {
  final presentation = presentProvider(providerID, serverName);
  return presentation.route == null
      ? presentation.name
      : '${presentation.name} · ${presentation.route}';
}

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
  final groups = <String, List<CatalogModel>>{};
  for (final model in models) {
    final groupID = presentProvider(model.providerID).groupID;
    final key = '$groupID\u0000${model.id}';
    groups.putIfAbsent(key, () => []).add(model);
  }
  for (final entries in groups.values) {
    entries.sort((a, b) {
      final aSelected =
          selected?.providerID == a.providerID && selected?.modelID == a.id;
      final bSelected =
          selected?.providerID == b.providerID && selected?.modelID == b.id;
      if (aSelected != bSelected) return aSelected ? -1 : 1;
      return _aliasPriority(
        a.providerID,
      ).compareTo(_aliasPriority(b.providerID));
    });
  }

  final orderedKeys = groups.keys.toList();
  if (selected != null) {
    final selectedGroup = presentProvider(selected.providerID).groupID;
    final selectedKey = '$selectedGroup\u0000${selected.modelID}';
    final currentProviderKeys = orderedKeys
        .where(
          (key) => key != selectedKey && key.startsWith('$selectedGroup\u0000'),
        )
        .toList();
    orderedKeys.removeWhere(
      (key) => key == selectedKey || currentProviderKeys.contains(key),
    );
    orderedKeys.insertAll(0, [
      if (groups.containsKey(selectedKey)) selectedKey,
      ...currentProviderKeys,
    ]);
  }

  return [for (final key in orderedKeys) ...groups[key]!];
}

String presentedProviderName(
  String providerID,
  Iterable<CatalogProvider> providers,
) {
  final match = providers.where((provider) => provider.id == providerID);
  return presentedProviderRouteName(
    providerID,
    match.isEmpty ? null : match.first.name,
  );
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
    for (final entries in groups.values) ..._presentIntegrationGroup(entries),
  ];
}

List<PresentedIntegration> _presentIntegrationGroup(
  List<IntegrationInfo> entries,
) {
  entries.sort((a, b) {
    return _aliasPriority(a.id).compareTo(_aliasPriority(b.id));
  });
  return [
    for (final integration in entries)
      PresentedIntegration(
        integration: integration,
        name: presentedProviderRouteName(integration.id, integration.name),
        connected: integration.connectionCount > 0,
      ),
  ];
}

/// The web domain whose favicon stands in for a provider's logo.
///
/// Wire ids are matched case-insensitively; aliases that share a company
/// (the Z.AI / Zhipu routes, Google's Gemini and Vertex routes) share a
/// domain. Unknown ids fall back to `<id>.com`, which is right often enough
/// to be worth a try and cheap when it is not: a missing favicon renders as
/// a monogram.
String providerLogoDomain(String providerID) {
  final id = providerID.trim().toLowerCase();
  return switch (id) {
    'anthropic' => 'anthropic.com',
    'openai' => 'openai.com',
    'google' || 'gemini' || 'google-vertex' => 'gemini.google.com',
    'openrouter' => 'openrouter.ai',
    'github-copilot' => 'github.com',
    'ollama' => 'ollama.com',
    'groq' => 'groq.com',
    'xai' => 'x.ai',
    'mistral' => 'mistral.ai',
    'deepseek' => 'deepseek.com',
    'zai' ||
    'zhipuai' ||
    'zai-coding-plan' ||
    'zhipuai-coding-plan' => 'zhipuai.cn',
    'amazon-bedrock' => 'aws.amazon.com',
    'azure' => 'azure.microsoft.com',
    'together' => 'together.ai',
    'fireworks-ai' => 'fireworks.ai',
    'cerebras' => 'cerebras.ai',
    'huggingface' => 'huggingface.co',
    'vercel' => 'vercel.com',
    'lmstudio' => 'lmstudio.ai',
    'opencode' || 'opencode-zen' => 'opencode.ai',
    _ => '${id.replaceAll(RegExp(r'[^a-z0-9-]'), '')}.com',
  };
}

/// True for the app's own provider ids, which draw the prompt glyph instead
/// of a fetched logo.
bool isOpenCodeProvider(String providerID) =>
    switch (providerID.trim().toLowerCase()) {
      'opencode' || 'opencode-zen' => true,
      _ => false,
    };

/// Google's favicon service, 128px PNG. Same endpoint that
/// `google.com/s2/favicons` redirects to, addressed directly so the image
/// pipeline does not pay for the redirect.
Uri providerLogoUrl(String providerID) =>
    Uri.https('t1.gstatic.com', '/faviconV2', {
      'client': 'SOCIAL',
      'type': 'FAVICON',
      'fallback_opts': 'TYPE,SIZE,URL',
      'url': 'https://${providerLogoDomain(providerID)}',
      'size': '128',
    });

/// One- or two-letter monogram for a provider whose logo is missing or still
/// loading: initials of the first two words of its presented name
/// ("fireworks-ai" -> "FA"), or the first two letters of a single word
/// ("groq" -> "GR").
String providerMonogram(String providerID) {
  final words = presentProvider(providerID).name
      .split(RegExp(r'[^A-Za-z0-9]+'))
      .where((word) => word.isNotEmpty)
      .toList();
  if (words.isEmpty) return '?';
  if (words.length == 1) {
    final word = words.single;
    return word.substring(0, word.length >= 2 ? 2 : 1).toUpperCase();
  }
  return '${words[0][0]}${words[1][0]}'.toUpperCase();
}
