import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../api/models.dart';
import '../../api/provider_presentation.dart';
import '../../api/product_repository.dart';
import '../../state/connection.dart';
import '../app_theme.dart';
import 'agent_color.dart';
import 'provider_logo.dart';

/// How applying a model/agent selection is scoped and labeled.
///
/// [classic] keeps the v1 wording ("Use model and mode") — selection is
/// client-side state attached to each prompt. On OpenCode 2 servers the
/// selection is session state instead: [session] labels the apply action
/// "Use for this session" (picker opened with an active session), and
/// [newSessions] labels it "Use for new sessions" (no session yet — the
/// choice becomes the default for `POST /api/session`).
enum ModelPickerApplyScope { classic, session, newSessions }

Future<void> showModelPicker(
  BuildContext context, {
  ModelPickerApplyScope applyScope = ModelPickerApplyScope.classic,
}) {
  final controller = ProviderScope.containerOf(
    context,
    listen: false,
  ).read(connProvider);
  // Catalog membership is server-owned and can change while the app remains
  // connected. Refresh on every open so removed models are not retained until
  // a reconnect or lifecycle wake.
  unawaited(controller.refreshCatalog());
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    // Theme default drag handle: the visible affordance that this sheet
    // collapses by dragging.
    clipBehavior: Clip.antiAlias,
    constraints: const BoxConstraints(maxWidth: 720),
    builder: (_) => _ModelAgentSheet(applyScope: applyScope),
  );
}

class _ModelAgentSheet extends ConsumerWidget {
  const _ModelAgentSheet({this.applyScope = ModelPickerApplyScope.classic});

  final ModelPickerApplyScope applyScope;

  @override
  Widget build(BuildContext context, WidgetRef ref) => DraggableScrollableSheet(
    expand: false,
    minChildSize: .56,
    initialChildSize: .88,
    maxChildSize: .96,
    snap: true,
    snapSizes: const [.88, .96],
    builder: (context, scrollController) => Material(
      color: Theme.of(context).colorScheme.surfaceContainerLow,
      child: ModelCatalogView(
        controller: ref.watch(connProvider),
        scrollController: scrollController,
        onApplied: () => Navigator.maybePop(context),
        onClose: () => Navigator.maybePop(context),
        applyScope: applyScope,
      ),
    ),
  );
}

enum _ModelIntent { all, fast, reasoning, context }

/// The single model, mode, provider, and agent selector used throughout the app.
class ModelCatalogView extends StatefulWidget {
  const ModelCatalogView({
    super.key,
    required this.controller,
    this.scrollController,
    this.onApplied,
    this.onClose,
    this.showHeader = true,
    this.applyScope = ModelPickerApplyScope.classic,
  });

  final ConnectionController controller;
  final ScrollController? scrollController;
  final VoidCallback? onApplied;
  final VoidCallback? onClose;
  final bool showHeader;
  final ModelPickerApplyScope applyScope;

  @override
  State<ModelCatalogView> createState() => _ModelCatalogViewState();
}

class _ModelCatalogViewState extends State<ModelCatalogView> {
  final _search = TextEditingController();
  String _query = '';
  String _provider = '*';
  _ModelIntent _intent = _ModelIntent.all;
  ModelRef? _draftModel;
  String _draftVariant = '';

  /// True once the catalog has scrolled: the header drops its tagline and
  /// icon tile to a single line so the list keeps the room.
  bool _collapsed = false;
  ScrollController? _ownedScroll;

  ScrollController get _listScroll =>
      widget.scrollController ?? (_ownedScroll ??= ScrollController());

  @override
  void initState() {
    super.initState();
    _syncDraft();
    _listScroll.addListener(_scrolled);
  }

  @override
  void didUpdateWidget(covariant ModelCatalogView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) _syncDraft();
    if (oldWidget.scrollController != widget.scrollController) {
      (oldWidget.scrollController ?? _ownedScroll)?.removeListener(_scrolled);
      _listScroll.addListener(_scrolled);
    }
  }

  void _syncDraft() {
    _draftModel = widget.controller.selectedModel;
    _draftVariant = widget.controller.selectedVariant;
  }

  void _scrolled() {
    final collapsed = _listScroll.hasClients && _listScroll.offset > 12;
    if (collapsed != _collapsed) setState(() => _collapsed = collapsed);
  }

  @override
  void dispose() {
    _listScroll.removeListener(_scrolled);
    _ownedScroll?.dispose();
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: widget.controller,
    builder: (context, _) {
      final catalog = widget.controller.catalog;
      final drafted = catalog == null ? null : _draftedModel(catalog);
      // Both pinned regions cap at a share of the available height and
      // scroll internally, so extreme accessibility text scales degrade
      // gracefully instead of overflowing. Together they never take more
      // than half: the list keeps at least 50% even on a small phone at 2x.
      return LayoutBuilder(
        builder: (context, constraints) => Column(
          children: [
            ConstrainedBox(
              constraints: BoxConstraints(
                maxHeight: constraints.maxHeight * (_collapsed ? .18 : .26),
              ),
              child: SingleChildScrollView(
                child: Column(
                  children: [
                    if (widget.showHeader) _header(context),
                    if (widget.showHeader) const Divider(height: 1),
                    if (catalog != null && catalog.models.isNotEmpty)
                      _pinnedControls(context),
                  ],
                ),
              ),
            ),
            Expanded(
              child: catalog == null
                  ? _catalogState()
                  : _catalog(context, catalog),
            ),
            if (drafted != null)
              ConstrainedBox(
                constraints: BoxConstraints(
                  maxHeight: constraints.maxHeight * .24,
                ),
                child: SingleChildScrollView(
                  child: _applyBar(context, catalog!, drafted),
                ),
              ),
          ],
        ),
      );
    },
  );

  CatalogModel? _draftedModel(CatalogSnapshot catalog) {
    final draft = _draftModel;
    if (draft == null) return null;
    for (final model in catalog.models) {
      if (model.providerID == draft.providerID && model.id == draft.modelID) {
        return model.enabled ? model : null;
      }
    }
    return null;
  }

  /// The search field stays fixed above the list so it never scrolls out of
  /// reach while browsing a large catalog.
  Widget _pinnedControls(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 10, 16, 6),
      child: TextField(
        key: const Key('model-picker-search'),
        controller: _search,
        textInputAction: TextInputAction.search,
        onChanged: (value) => setState(() => _query = value),
        decoration: InputDecoration(
          hintText: 'Search models or providers',
          prefixIcon: const Icon(Icons.search_rounded),
          isDense: true,
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
    );
  }

  List<Widget> _headerActions() => [
    if (widget.onClose != null)
      IconButton(
        key: const Key('model-picker-refresh'),
        tooltip: 'Refresh models',
        onPressed: widget.controller.catalogLoading
            ? null
            : widget.controller.refreshCatalog,
        icon: widget.controller.catalogLoading
            ? const SizedBox.square(
                dimension: 18,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
            : const Icon(Icons.refresh_rounded),
      ),
    if (widget.onClose != null)
      IconButton(
        tooltip: 'Close model selector',
        onPressed: widget.onClose,
        icon: const Icon(Icons.close_rounded),
      ),
  ];

  Widget _header(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    if (_collapsed) {
      return Padding(
        key: const Key('model-picker-header-compact'),
        padding: const EdgeInsets.fromLTRB(20, 4, 12, 2),
        child: Row(
          children: [
            Icon(Icons.tune_rounded, size: 20, color: scheme.primary),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                'Model, mode & agent',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.titleMedium,
              ),
            ),
            ..._headerActions(),
          ],
        ),
      );
    }
    final selected = widget.controller.selectedModel;
    final selectedProviderName = selected == null
        ? null
        : presentedProviderName(
            selected.providerID,
            widget.controller.catalog?.providers ?? const [],
          );
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 14, 12, 12),
      child: Row(
        children: [
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: scheme.primaryContainer,
              borderRadius: BorderRadius.circular(AppTheme.radiusCard),
            ),
            child: Icon(Icons.tune_rounded, color: scheme.onPrimaryContainer),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Model, mode & agent', style: theme.textTheme.titleLarge),
                const SizedBox(height: 2),
                Text(
                  selected == null
                      ? 'Choose how new prompts run'
                      : [
                          '$selectedProviderName · ${selected.modelID}',
                          if (widget.controller.selectedVariant.isNotEmpty)
                            widget.controller.selectedVariant,
                        ].join(' · '),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: scheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
          ..._headerActions(),
        ],
      ),
    );
  }

  Widget _catalogState() {
    final controller = widget.controller;
    if (controller.catalogError != null) {
      return _PickerState(
        icon: Icons.sync_problem_rounded,
        title: 'Could not load models',
        message: controller.catalogError!,
        action: FilledButton.tonalIcon(
          onPressed: controller.catalogLoading
              ? null
              : controller.refreshCatalog,
          icon: const Icon(Icons.refresh_rounded),
          label: const Text('Try again'),
        ),
      );
    }
    return const _CatalogLoading();
  }

  Widget _catalog(BuildContext context, CatalogSnapshot catalog) {
    if (catalog.models.isEmpty) {
      return const _PickerState(
        icon: Icons.inventory_2_outlined,
        title: 'No models available',
        message: 'Configure a provider on the OpenCode server, then refresh.',
      );
    }
    final normalized = _query.trim().toLowerCase();
    final providers = presentProviders(catalog.providers);
    final models =
        presentModels(
          catalog.models,
          selected: widget.controller.selectedModel,
        ).where((model) {
          final presentation = presentProvider(model.providerID);
          final providerName = presentedProviderName(
            model.providerID,
            catalog.providers,
          );
          final matchesProvider =
              _provider == '*' || presentation.groupID == _provider;
          final matchesQuery =
              normalized.isEmpty ||
              model.name.toLowerCase().contains(normalized) ||
              model.id.toLowerCase().contains(normalized) ||
              model.providerID.toLowerCase().contains(normalized) ||
              providerName.toLowerCase().contains(normalized);
          final matchesIntent = switch (_intent) {
            _ModelIntent.all || _ModelIntent.context => true,
            _ModelIntent.fast => model.variants.any(
              (variant) => !variant.disabled && variant.isFast,
            ),
            _ModelIntent.reasoning => model.reasoning,
          };
          return matchesProvider && matchesQuery && matchesIntent;
        }).toList();
    if (_intent == _ModelIntent.context) {
      models.sort((a, b) => b.contextLimit.compareTo(a.contextLimit));
    }

    final leadItems = <Widget>[
      if (!widget.controller.catalogDetailed)
        const _Notice(
          icon: Icons.info_outline_rounded,
          text:
              'This server returned a basic catalog. Capability and context details are unavailable.',
        ),
      _agentPicker(catalog),
      const SizedBox(height: 12),
      _providerPicker(providers),
      const SizedBox(height: 12),
      SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(
          children: [
            _intentChip('All', _ModelIntent.all),
            _intentChip('Fast modes', _ModelIntent.fast),
            _intentChip('Reasoning', _ModelIntent.reasoning),
            _intentChip('Largest context', _ModelIntent.context),
          ],
        ),
      ),
      const SizedBox(height: 14),
      Row(
        children: [
          Text('Models', style: Theme.of(context).textTheme.titleSmall),
          const SizedBox(width: 8),
          Text('${models.length}'),
        ],
      ),
      const SizedBox(height: 4),
      if (models.isEmpty)
        _PickerState(
          icon: Icons.search_off_rounded,
          title: 'No matching models',
          message: _intent == _ModelIntent.fast
              ? 'No model reports an explicit fast or low-effort mode.'
              : 'Try another search, provider, or capability filter.',
        ),
    ];

    // The catalog can hold hundreds of models; rows must build lazily.
    return ListView.builder(
      controller: _listScroll,
      keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
      padding: const EdgeInsets.fromLTRB(16, 6, 16, 16),
      itemCount: leadItems.length + models.length,
      itemBuilder: (context, index) {
        if (index < leadItems.length) return leadItems[index];
        final model = models[index - leadItems.length];
        return Column(
          children: [
            _modelRow(
              context,
              model,
              presentedProviderName(model.providerID, catalog.providers),
            ),
            const Divider(height: 1),
          ],
        );
      },
    );
  }

  Widget _agentPicker(CatalogSnapshot catalog) {
    final visible = catalog.agents
        .where((agent) => !agent.hidden && agent.mode != 'subagent')
        .toList();
    final value =
        visible.any((agent) => agent.id == widget.controller.selectedAgent)
        ? widget.controller.selectedAgent
        : null;
    return DropdownButtonFormField<String>(
      key: const Key('model-picker-agent'),
      isExpanded: true,
      initialValue: value,
      decoration: const InputDecoration(
        labelText: 'Agent',
        prefixIcon: Icon(Icons.support_agent_outlined),
      ),
      hint: Text(visible.isEmpty ? 'No agents available' : 'Server default'),
      items: [
        for (final agent in visible)
          DropdownMenuItem(
            value: agent.id,
            child: Row(
              children: [
                // The agent's own colour, as the terminal client shows it,
                // so the same agent is recognisable across both clients.
                Container(
                  width: 12,
                  height: 12,
                  decoration: BoxDecoration(
                    color: agentColor(
                      agent.color,
                      Theme.of(context).colorScheme,
                    ),
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    [
                      agent.mode == 'unknown'
                          ? agent.id
                          : '${agent.id} · ${agent.mode}',
                      if (agent.model?.isNotEmpty == true) agent.model!,
                    ].join(' · '),
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            ),
          ),
      ],
      onChanged: visible.isEmpty
          ? null
          : (value) {
              if (value != null) widget.controller.selectAgent(value);
            },
    );
  }

  Widget _providerPicker(List<PresentedProvider> providers) {
    return DropdownButtonFormField<String>(
      key: const Key('model-picker-provider'),
      isExpanded: true,
      initialValue: providers.any((provider) => provider.id == _provider)
          ? _provider
          : '*',
      decoration: const InputDecoration(
        labelText: 'Provider',
        prefixIcon: Icon(Icons.cloud_outlined),
      ),
      items: [
        const DropdownMenuItem(value: '*', child: Text('All providers')),
        for (final provider in providers)
          DropdownMenuItem(
            value: provider.id,
            child: Row(
              children: [
                ProviderLogo(provider.providerIDs.first, size: 18),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(provider.name, overflow: TextOverflow.ellipsis),
                ),
              ],
            ),
          ),
      ],
      onChanged: (value) => setState(() => _provider = value ?? '*'),
    );
  }

  Widget _intentChip(String label, _ModelIntent intent) => Padding(
    padding: const EdgeInsets.only(right: 8),
    child: ChoiceChip(
      label: Text(label),
      selected: _intent == intent,
      onSelected: (_) => setState(() => _intent = intent),
    ),
  );

  Widget _modelRow(
    BuildContext context,
    CatalogModel model,
    String providerName,
  ) {
    final scheme = Theme.of(context).colorScheme;
    final current =
        widget.controller.selectedModel?.providerID == model.providerID &&
        widget.controller.selectedModel?.modelID == model.id;
    final draft =
        _draftModel?.providerID == model.providerID &&
        _draftModel?.modelID == model.id;
    final enabled = model.enabled;
    return Semantics(
      selected: current,
      button: true,
      label: '${model.name}, $providerName${current ? ', current model' : ''}',
      child: Material(
        color: draft
            ? scheme.primary.withValues(alpha: .07)
            : Colors.transparent,
        child: ListTile(
          key: ValueKey('model-option-${model.providerID}-${model.id}'),
          enabled: enabled,
          contentPadding: const EdgeInsets.symmetric(horizontal: 8),
          title: Text(
            model.name,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontWeight: current ? FontWeight.w700 : FontWeight.w500,
            ),
          ),
          subtitle: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: const EdgeInsets.only(top: 1),
                child: ProviderLogo(model.providerID, size: 18),
              ),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  [
                    '$providerName · ${model.id}',
                    if (model.contextLimit > 0)
                      '${_number(model.contextLimit)} context',
                    if (model.outputLimit > 0)
                      '${_number(model.outputLimit)} output',
                    // Price per million tokens, straight from the catalog, so
                    // the trade-off between models is visible where the
                    // choice is made.
                    ?modelCostLabel(model),
                    if (model.deprecated)
                      'Deprecated'
                    else if (model.preview)
                      'Preview',
                  ].join(' · '),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
          trailing: Icon(
            draft
                ? Icons.radio_button_checked_rounded
                : current
                ? Icons.check_circle_rounded
                : Icons.radio_button_off_rounded,
            color: draft || current ? scheme.primary : scheme.outline,
          ),
          onTap: enabled
              ? () => setState(() {
                  _draftModel = ModelRef(
                    providerID: model.providerID,
                    modelID: model.id,
                  );
                  _draftVariant = current
                      ? widget.controller.selectedVariant
                      : '';
                })
              : null,
        ),
      ),
    );
  }

  /// The drafted model's identity, thinking modes, and single apply action,
  /// pinned to the sheet bottom so applying never requires scrolling.
  Widget _applyBar(
    BuildContext context,
    CatalogSnapshot catalog,
    CatalogModel model,
  ) {
    final theme = Theme.of(context);
    final variants = model.variants
        .where((variant) => !variant.disabled)
        .toList();
    final providerName = presentedProviderName(
      model.providerID,
      catalog.providers,
    );
    final capabilities = <String>[
      if (model.reasoning) 'Reasoning',
      if (model.tools) 'Tools',
      if (model.attachments) 'Attachments',
    ];
    // Past the stacked-actions text scale the bar keeps only what the tap
    // needs — name, mode, button — so the button stays inside its cap.
    final compact = AppTheme.stackedActions(context);
    return Material(
      key: const Key('model-picker-apply-bar'),
      color: theme.colorScheme.surfaceContainerHigh,
      elevation: 6,
      child: SafeArea(
        top: false,
        child: Padding(
          padding: compact
              ? const EdgeInsets.fromLTRB(16, 6, 16, 6)
              : const EdgeInsets.fromLTRB(16, 10, 16, 10),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                model.name,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.titleSmall,
              ),
              if (!compact)
                Text(
                  [
                    '$providerName · ${model.id}',
                    if (capabilities.isNotEmpty) capabilities.join(' · '),
                  ].join('  —  '),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              SizedBox(height: compact ? 4 : 8),
              if (variants.isEmpty)
                Text(
                  'This provider exposes only its default mode.',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: AppTheme.mutedOf(theme),
                  ),
                )
              else
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(
                    children: [
                      Padding(
                        padding: const EdgeInsets.only(right: 8),
                        child: ChoiceChip(
                          key: ValueKey('model-variant-${model.id}-default'),
                          label: const Text('Default'),
                          selected: _draftVariant.isEmpty,
                          onSelected: (_) => setState(() => _draftVariant = ''),
                        ),
                      ),
                      for (final variant in variants)
                        Padding(
                          padding: const EdgeInsets.only(right: 8),
                          child: ChoiceChip(
                            key: ValueKey(
                              'model-variant-${model.id}-${variant.id}',
                            ),
                            label: Text(_variantLabel(variant)),
                            selected: _draftVariant == variant.id,
                            onSelected: (_) =>
                                setState(() => _draftVariant = variant.id),
                          ),
                        ),
                    ],
                  ),
                ),
              const SizedBox(height: 10),
              if (widget.applyScope == ModelPickerApplyScope.session)
                Padding(
                  padding: const EdgeInsets.only(bottom: 4),
                  child: Text(
                    "Applies to this session's next turns.",
                    key: const Key('model-picker-session-scope-note'),
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ),
              FilledButton.icon(
                key: ValueKey('use-model-${model.providerID}-${model.id}'),
                onPressed: () async {
                  await widget.controller.selectModel(
                    _draftModel!,
                    variant: _draftVariant,
                  );
                  widget.onApplied?.call();
                  if (mounted && widget.onApplied == null) {
                    setState(_syncDraft);
                  }
                },
                icon: const Icon(Icons.check_rounded),
                label: Text(switch (widget.applyScope) {
                  ModelPickerApplyScope.classic => 'Use model and mode',
                  ModelPickerApplyScope.session => 'Use for this session',
                  ModelPickerApplyScope.newSessions => 'Use for new sessions',
                }),
              ),
            ],
          ),
        ),
      ),
    );
  }

  static String _variantLabel(CatalogVariant variant) {
    final effort = variant.reasoningEffort;
    if (effort == null || variant.id.toLowerCase() == effort.toLowerCase()) {
      return variant.id;
    }
    return '${variant.id} · $effort effort';
  }

  static String _number(int value) {
    final text = value.toString();
    final out = StringBuffer();
    for (var index = 0; index < text.length; index++) {
      if (index > 0 && (text.length - index) % 3 == 0) out.write(',');
      out.write(text[index]);
    }
    return out.toString();
  }
}

class _Notice extends StatelessWidget {
  const _Notice({required this.icon, required this.text});
  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: scheme.secondaryContainer.withValues(alpha: .45),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(icon, size: 19, color: scheme.onSecondaryContainer),
              const SizedBox(width: 8),
              Expanded(child: Text(text)),
            ],
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
        itemCount: 6,
        separatorBuilder: (_, _) => const SizedBox(height: 12),
        itemBuilder: (_, index) => Container(
          height: index < 3 ? 54 : 72,
          decoration: BoxDecoration(
            color: scheme.surfaceContainerHighest.withValues(alpha: .55),
            borderRadius: BorderRadius.circular(14),
          ),
        ),
      ),
    );
  }
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
            const SizedBox(height: 12),
            Text(title, style: theme.textTheme.titleMedium),
            const SizedBox(height: 6),
            Text(
              message,
              textAlign: TextAlign.center,
              style: theme.textTheme.bodySmall?.copyWith(
                color: scheme.onSurfaceVariant,
              ),
            ),
            if (action != null) ...[const SizedBox(height: 16), action!],
          ],
        ),
      ),
    );
  }
}

/// "\$3.00 in · \$15.00 out /1M" for a model with published pricing; null when
/// the catalog carries no cost.
String? modelCostLabel(CatalogModel model) {
  final cost = model.cost;
  if (cost == null) return null;
  final input = cost.inputPerMillion;
  final output = cost.outputPerMillion;
  if (input <= 0 && output <= 0) return null;
  String money(double value) => value >= 1
      ? '\$${value.toStringAsFixed(2)}'
      : '\$${value.toStringAsFixed(3)}';
  return '${money(input)} in · ${money(output)} out /1M';
}
