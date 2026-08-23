import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../api/models.dart';
import '../../state/connection.dart';

/// Bottom sheet for choosing agent and provider + model.
Future<void> showModelPicker(BuildContext context, WidgetRef ref) {
  return showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (_) => const _ModelAgentSheet(),
  );
}

class _ModelAgentSheet extends ConsumerStatefulWidget {
  const _ModelAgentSheet();

  @override
  ConsumerState<_ModelAgentSheet> createState() => _ModelAgentSheetState();
}

class _ModelAgentSheetState extends ConsumerState<_ModelAgentSheet> {
  String? _pickedProvider;

  @override
  Widget build(BuildContext context) {
    final conn = ref.watch(connProvider);
    final providers = conn.providers;
    final theme = Theme.of(context);

    if (providers == null || providers.providers.isEmpty) {
      return Padding(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 28),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Text('No models available', style: theme.textTheme.titleSmall),
          const SizedBox(height: 6),
          Text(
            'Configure a provider on the opencode server first '
            '(run `opencode auth login` there).',
            textAlign: TextAlign.center,
            style: theme.textTheme.bodySmall!.copyWith(color: theme.hintColor),
          ),
        ]),
      );
    }

    final selected = conn.selectedModel;
    var currentProvider = _pickedProvider ?? selected?.providerID ?? '';
    if (!providers.providers.any((p) => p.id == currentProvider)) {
      currentProvider =
          providers.defaultProviderID ?? providers.providers.first.id;
      _pickedProvider = currentProvider;
    }
    final provider =
        providers.providers.firstWhere((p) => p.id == currentProvider);

    return DraggableScrollableSheet(
      expand: false,
      initialChildSize: .72,
      maxChildSize: .92,
      builder: (context, scroll) => ListView(
        controller: scroll,
        padding: const EdgeInsets.symmetric(horizontal: 20),
        children: [
          Text('AGENT',
              style: theme.textTheme.labelSmall!
                  .copyWith(color: theme.hintColor, letterSpacing: 1)),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 4,
            children: [
              for (final a in conn.agents)
                ChoiceChip(
                  label: Text(a.name),
                  selected: conn.selectedAgent == a.name,
                  onSelected: (_) => ref.read(connProvider).selectAgent(a.name),
                ),
              if (conn.agents.isEmpty)
                Text('No agents found',
                    style: theme.textTheme.bodySmall!
                        .copyWith(color: theme.hintColor)),
            ],
          ),
          const SizedBox(height: 18),
          Text('PROVIDER',
              style: theme.textTheme.labelSmall!
                  .copyWith(color: theme.hintColor, letterSpacing: 1)),
          const SizedBox(height: 8),
          DropdownButtonFormField<String>(
            initialValue: provider.id,
            decoration: const InputDecoration(border: OutlineInputBorder()),
            items: [
              for (final p in providers.providers)
                DropdownMenuItem(
                    value: p.id,
                    child: Text('${p.name} (${p.modelIDs.length})',
                        overflow: TextOverflow.ellipsis)),
            ],
            onChanged: (v) => setState(() => _pickedProvider = v),
          ),
          const SizedBox(height: 12),
          Text('MODEL',
              style: theme.textTheme.labelSmall!
                  .copyWith(color: theme.hintColor, letterSpacing: 1)),
          const SizedBox(height: 8),
          ...provider.modelIDs.map((m) {
            final active =
                selected?.providerID == provider.id && selected?.modelID == m;
            return Card.filled(
              margin: const EdgeInsets.symmetric(vertical: 2),
              color: active
                  ? theme.colorScheme.primaryContainer.withValues(alpha: .5)
                  : null,
              child: ListTile(
                dense: true,
                onTap: () async {
                  await ref.read(connProvider).selectModel(
                      ModelRef(providerID: provider.id, modelID: m));
                  if (context.mounted) Navigator.pop(context);
                },
                title: Text(m,
                    style:
                        const TextStyle(fontFamily: 'monospace', fontSize: 13)),
                trailing: active
                    ? Icon(Icons.check_rounded, color: theme.colorScheme.primary)
                    : null,
              ),
            );
          }),
          const SizedBox(height: 24),
        ],
      ),
    );
  }
}
