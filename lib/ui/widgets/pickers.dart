import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../api/models.dart';
import '../../state/connection.dart';

/// Bottom sheet for choosing the active agent, provider, and model.
Future<void> showModelPicker(BuildContext context) {
  return showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: false,
    clipBehavior: Clip.antiAlias,
    constraints: const BoxConstraints(maxWidth: 720),
    builder: (_) => const _ModelAgentSheet(),
  );
}

class _ModelAgentSheet extends ConsumerStatefulWidget {
  const _ModelAgentSheet();

  @override
  ConsumerState<_ModelAgentSheet> createState() => _ModelAgentSheetState();
}

class _ModelAgentSheetState extends ConsumerState<_ModelAgentSheet> {
  final _search = TextEditingController();
  String? _pickedProvider;
  String _query = '';

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final conn = ref.watch(connProvider);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final providers = conn.providers;
    final selected = conn.selectedModel;

    return DraggableScrollableSheet(
      expand: false,
      minChildSize: .52,
      initialChildSize: .82,
      maxChildSize: .94,
      snap: true,
      snapSizes: const [.82, .94],
      builder: (context, scrollController) => Material(
        color: scheme.surfaceContainerLow,
        child: Column(
          children: [
            _PickerHeader(
              selected: selected,
              onClose: () => Navigator.pop(context),
            ),
            Divider(color: scheme.outlineVariant.withValues(alpha: .7)),
            if (providers == null)
              Expanded(
                child: conn.catalogError != null
                    ? _CatalogError(
                        message: conn.catalogError!,
                        onRetry: conn.catalogLoading
                            ? null
                            : conn.refreshCatalog,
                      )
                    : const _CatalogLoading(),
              )
            else if (providers.providers.isEmpty)
              const Expanded(child: _CatalogEmpty())
            else
              Expanded(
                child: _buildCatalog(
                  context,
                  conn,
                  providers,
                  selected,
                  scrollController,
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildCatalog(
    BuildContext context,
    ConnectionController conn,
    ProvidersResponse providers,
    ModelRef? selected,
    ScrollController scrollController,
  ) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    var providerID = _pickedProvider ?? selected?.providerID;
    if (!providers.providers.any((provider) => provider.id == providerID)) {
      providerID = providers.defaultProviderID ?? providers.providers.first.id;
    }
    final provider = providers.providers.firstWhere(
      (candidate) => candidate.id == providerID,
    );
    final currentAgent =
        conn.agents.any((agent) => agent.name == conn.selectedAgent)
        ? conn.selectedAgent
        : conn.agents.firstOrNull?.name;
    final normalizedQuery = _query.trim().toLowerCase();
    final models = provider.modelIDs
        .where(
          (model) =>
              normalizedQuery.isEmpty ||
              model.toLowerCase().contains(normalizedQuery),
        )
        .toList();

    final agentPicker = _PickerSelect<String>(
      label: 'Agent',
      value: currentAgent,
      placeholder: conn.agents.isEmpty ? 'No agents' : 'Choose agent',
      items: [
        for (final agent in conn.agents) (value: agent.name, label: agent.name),
      ],
      onChanged: (value) {
        if (value != null) conn.selectAgent(value);
      },
    );
    final providerPicker = _PickerSelect<String>(
      label: 'Provider',
      value: provider.id,
      placeholder: 'Choose provider',
      items: [
        for (final item in providers.providers)
          (value: item.id, label: '${item.name} · ${item.modelIDs.length}'),
      ],
      onChanged: (value) {
        if (value == null) return;
        setState(() {
          _pickedProvider = value;
          _query = '';
          _search.clear();
        });
      },
    );

    return CustomScrollView(
      controller: scrollController,
      keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
      slivers: [
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(20, 16, 20, 0),
          sliver: SliverToBoxAdapter(
            child: TextField(
              key: const Key('model-picker-search'),
              controller: _search,
              textInputAction: TextInputAction.search,
              onChanged: (value) => setState(() => _query = value),
              decoration: InputDecoration(
                hintText: 'Search ${provider.name} models',
                prefixIcon: const Icon(Icons.search_rounded),
                suffixIcon: _query.isEmpty
                    ? null
                    : IconButton(
                        tooltip: 'Clear model search',
                        onPressed: () {
                          _search.clear();
                          setState(() => _query = '');
                        },
                        icon: const Icon(Icons.close_rounded),
                      ),
              ),
            ),
          ),
        ),
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(20, 16, 20, 14),
          sliver: SliverToBoxAdapter(
            child: LayoutBuilder(
              builder: (context, constraints) {
                if (constraints.maxWidth < 520) {
                  return Column(
                    children: [
                      agentPicker,
                      const SizedBox(height: 12),
                      providerPicker,
                    ],
                  );
                }
                return Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(child: agentPicker),
                    const SizedBox(width: 12),
                    Expanded(child: providerPicker),
                  ],
                );
              },
            ),
          ),
        ),
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 2, 20, 10),
            child: Row(
              children: [
                Text('Models', style: theme.textTheme.titleSmall),
                const SizedBox(width: 8),
                Text(
                  '${models.length}',
                  style: theme.textTheme.labelMedium?.copyWith(
                    color: scheme.onSurfaceVariant,
                  ),
                ),
                const Spacer(),
                if (selected?.providerID == provider.id)
                  Flexible(
                    child: Text(
                      'Current · ${selected?.modelID ?? 'None'}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.labelMedium?.copyWith(
                        color: scheme.primary,
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
        SliverToBoxAdapter(
          child: Divider(color: scheme.outlineVariant.withValues(alpha: .65)),
        ),
        if (models.isEmpty)
          SliverFillRemaining(
            hasScrollBody: false,
            child: _NoModelResults(
              hasQuery: normalizedQuery.isNotEmpty,
              onClear: normalizedQuery.isEmpty
                  ? null
                  : () {
                      _search.clear();
                      setState(() => _query = '');
                    },
            ),
          )
        else
          SliverPadding(
            padding: const EdgeInsets.only(bottom: 24),
            sliver: SliverList.separated(
              itemCount: models.length,
              separatorBuilder: (_, _) => Divider(
                indent: 20,
                endIndent: 20,
                color: scheme.outlineVariant.withValues(alpha: .45),
              ),
              itemBuilder: (context, index) {
                final model = models[index];
                final active =
                    selected?.providerID == provider.id &&
                    selected?.modelID == model;
                return _ModelRow(
                  key: ValueKey('model-option-${provider.id}-$model'),
                  model: model,
                  providerName: provider.name,
                  active: active,
                  onTap: () async {
                    await conn.selectModel(
                      ModelRef(providerID: provider.id, modelID: model),
                    );
                    if (context.mounted) Navigator.pop(context);
                  },
                );
              },
            ),
          ),
      ],
    );
  }
}

class _PickerHeader extends StatelessWidget {
  const _PickerHeader({required this.selected, required this.onClose});

  final ModelRef? selected;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 14, 12, 12),
      child: Row(
        children: [
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: scheme.primaryContainer,
              borderRadius: BorderRadius.circular(13),
            ),
            child: Icon(Icons.tune_rounded, color: scheme.onPrimaryContainer),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Model & agent', style: theme.textTheme.titleLarge),
                const SizedBox(height: 2),
                Text(
                  selected == null
                      ? 'Choose how this conversation runs'
                      : '${selected!.providerID} / ${selected!.modelID}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: scheme.onSurfaceVariant,
                    fontFamily: selected == null ? null : 'monospace',
                  ),
                ),
              ],
            ),
          ),
          IconButton(
            tooltip: 'Close model selector',
            onPressed: onClose,
            icon: const Icon(Icons.close_rounded),
          ),
        ],
      ),
    );
  }
}

class _PickerSelect<T> extends StatelessWidget {
  const _PickerSelect({
    required this.label,
    required this.value,
    required this.placeholder,
    required this.items,
    required this.onChanged,
  });

  final String label;
  final T? value;
  final String placeholder;
  final List<({T value, String label})> items;
  final ValueChanged<T?> onChanged;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(left: 2, bottom: 7),
          child: Text(
            label,
            style: theme.textTheme.labelMedium?.copyWith(
              color: scheme.onSurfaceVariant,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
        ConstrainedBox(
          constraints: const BoxConstraints(minHeight: 54),
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: scheme.surfaceContainer,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(
                color: scheme.outlineVariant.withValues(alpha: .8),
              ),
            ),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 14),
              child: DropdownButtonHideUnderline(
                child: DropdownButton<T>(
                  value: value,
                  isExpanded: true,
                  borderRadius: BorderRadius.circular(14),
                  menuMaxHeight: 360,
                  hint: Text(placeholder),
                  items: [
                    for (final item in items)
                      DropdownMenuItem<T>(
                        value: item.value,
                        child: Text(
                          item.label,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                  ],
                  onChanged: items.isEmpty ? null : onChanged,
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class _ModelRow extends StatelessWidget {
  const _ModelRow({
    super.key,
    required this.model,
    required this.providerName,
    required this.active,
    required this.onTap,
  });

  final String model;
  final String providerName;
  final bool active;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return Semantics(
      selected: active,
      button: true,
      label: '$model, $providerName${active ? ', current model' : ''}',
      child: Material(
        color: active
            ? scheme.primary.withValues(alpha: .09)
            : Colors.transparent,
        child: InkWell(
          onTap: onTap,
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 64),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 11),
              child: Row(
                children: [
                  AnimatedContainer(
                    duration: const Duration(milliseconds: 180),
                    width: 4,
                    height: active ? 32 : 10,
                    decoration: BoxDecoration(
                      color: active ? scheme.primary : scheme.outlineVariant,
                      borderRadius: BorderRadius.circular(4),
                    ),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text(
                          model,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.bodyMedium?.copyWith(
                            fontFamily: 'monospace',
                            fontWeight: active
                                ? FontWeight.w700
                                : FontWeight.w500,
                            color: active ? scheme.primary : scheme.onSurface,
                          ),
                        ),
                        if (active) ...[
                          const SizedBox(height: 2),
                          Text(
                            'Active for new prompts',
                            style: theme.textTheme.labelSmall?.copyWith(
                              color: scheme.onSurfaceVariant,
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                  const SizedBox(width: 12),
                  Icon(
                    active
                        ? Icons.check_circle_rounded
                        : Icons.arrow_forward_rounded,
                    size: active ? 22 : 18,
                    color: active ? scheme.primary : scheme.onSurfaceVariant,
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _CatalogLoading extends StatelessWidget {
  const _CatalogLoading();

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Semantics(
      liveRegion: true,
      label: 'Loading model catalog',
      child: ListView.separated(
        physics: const NeverScrollableScrollPhysics(),
        padding: const EdgeInsets.all(20),
        itemCount: 5,
        separatorBuilder: (_, _) => const SizedBox(height: 12),
        itemBuilder: (_, index) => Container(
          height: index < 2 ? 54 : 64,
          decoration: BoxDecoration(
            color: scheme.surfaceContainerHighest.withValues(alpha: .55),
            borderRadius: BorderRadius.circular(14),
          ),
        ),
      ),
    );
  }
}

class _CatalogError extends StatelessWidget {
  const _CatalogError({required this.message, required this.onRetry});

  final String message;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) => _PickerState(
    icon: Icons.sync_problem_rounded,
    title: 'Could not load models',
    message: message,
    action: FilledButton.tonalIcon(
      onPressed: onRetry,
      icon: const Icon(Icons.refresh_rounded),
      label: const Text('Try again'),
    ),
  );
}

class _CatalogEmpty extends StatelessWidget {
  const _CatalogEmpty();

  @override
  Widget build(BuildContext context) => const _PickerState(
    icon: Icons.inventory_2_outlined,
    title: 'No models available',
    message:
        'Configure a provider on the OpenCode server, then reopen this selector.',
  );
}

class _NoModelResults extends StatelessWidget {
  const _NoModelResults({required this.hasQuery, required this.onClear});

  final bool hasQuery;
  final VoidCallback? onClear;

  @override
  Widget build(BuildContext context) => _PickerState(
    icon: hasQuery ? Icons.search_off_rounded : Icons.inventory_2_outlined,
    title: hasQuery ? 'No matching models' : 'No models from this provider',
    message: hasQuery
        ? 'Try a shorter model name or clear the search.'
        : 'Choose another provider to continue.',
    action: onClear == null
        ? null
        : TextButton.icon(
            onPressed: onClear,
            icon: const Icon(Icons.close_rounded),
            label: const Text('Clear search'),
          ),
  );
}

class _PickerState extends StatelessWidget {
  const _PickerState({
    required this.icon,
    required this.title,
    required this.message,
    this.action,
  });

  final IconData icon;
  final String title;
  final String message;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(28),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 36, color: scheme.onSurfaceVariant),
            const SizedBox(height: 14),
            Text(title, style: theme.textTheme.titleMedium),
            const SizedBox(height: 6),
            Text(
              message,
              textAlign: TextAlign.center,
              style: theme.textTheme.bodySmall?.copyWith(
                color: scheme.onSurfaceVariant,
              ),
            ),
            if (action != null) ...[const SizedBox(height: 18), action!],
          ],
        ),
      ),
    );
  }
}
