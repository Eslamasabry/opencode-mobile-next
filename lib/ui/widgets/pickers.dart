import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../api/models.dart';
import '../../api/provider_presentation.dart';
import '../../api/product_repository.dart';
import '../../state/connection.dart';
import '../../state/model_library.dart';
import '../../l10n/app_localizations.dart';
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

/// Opens the model/agent sheet. With [sessionID] and
/// [ModelPickerApplyScope.session] the choice applies to that session only;
/// otherwise it becomes the profile default.
Future<void> showModelPicker(
  BuildContext context, {
  ModelPickerApplyScope applyScope = ModelPickerApplyScope.classic,
  String? sessionID,
}) {
  final controller = ProviderScope.containerOf(
    context,
    listen: false,
  ).read(connProvider);
  // Catalog membership is server-owned and can change while the app remains
  // connected. Refresh on every open so removed models are not retained until
  // a reconnect or lifecycle wake.
  unawaited(controller.refreshCatalog());
  if (sessionID != null && controller.serverOwnsSessionSelection) {
    unawaited(controller.ensureSession(sessionID));
  }
  if (applyScope == ModelPickerApplyScope.classic &&
      controller.serverOwnsSessionSelection) {
    applyScope = ModelPickerApplyScope.newSessions;
  }
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    // Theme default drag handle: the visible affordance that this sheet
    // collapses by dragging.
    clipBehavior: Clip.antiAlias,
    constraints: const BoxConstraints(maxWidth: 720),
    builder: (_) =>
        _ModelAgentSheet(applyScope: applyScope, sessionID: sessionID),
  );
}

class _ModelAgentSheet extends ConsumerWidget {
  const _ModelAgentSheet({
    this.applyScope = ModelPickerApplyScope.classic,
    this.sessionID,
  });

  final ModelPickerApplyScope applyScope;
  final String? sessionID;

  @override
  Widget build(BuildContext context, WidgetRef ref) => Padding(
    padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
    child: DraggableScrollableSheet(
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
          sessionID: sessionID,
        ),
      ),
    ),
  );
}

enum _ModelIntent { all, fast, reasoning, context }

enum _ModelCollection { all, favorites, recent }

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
    this.sessionID,
  });

  final ConnectionController controller;
  final ScrollController? scrollController;
  final VoidCallback? onApplied;
  final VoidCallback? onClose;
  final bool showHeader;
  final ModelPickerApplyScope applyScope;
  final String? sessionID;

  @override
  State<ModelCatalogView> createState() => _ModelCatalogViewState();
}

class _ModelCatalogViewState extends State<ModelCatalogView> {
  AppLocalizations get _strings =>
      lookupAppLocalizations(Localizations.localeOf(context));

  final _search = TextEditingController();
  // Preserve the search's focus and input connection when keyboard/large-text
  // layout moves the controls between the pinned header and scrollable list.
  final _controlsKey = GlobalKey();
  String _query = '';
  String _provider = '*';
  _ModelIntent _intent = _ModelIntent.all;
  _ModelCollection _collection = _ModelCollection.all;
  bool _showFilters = false;
  ModelRef? _draftModel;
  String _draftVariant = '';
  bool _applying = false;
  String? _saveError;
  ModelRef? _observedModel;
  String _observedVariant = '';
  bool _agentSaving = false;
  bool _optionsOpen = false;
  String? _agentError;
  String? _scopeProfile;
  int _scopeLocation = 0;

  bool get _sameScope =>
      widget.controller.profile?.id == _scopeProfile &&
      widget.controller.locationRevision == _scopeLocation;

  ScrollController? _ownedScroll;

  ScrollController get _listScroll =>
      widget.scrollController ?? (_ownedScroll ??= ScrollController());

  @override
  void initState() {
    super.initState();
    _scopeProfile = widget.controller.profile?.id;
    _scopeLocation = widget.controller.locationRevision;
    _syncDraft();
    _observedModel = _currentModel;
    _observedVariant = _currentVariant;
    widget.controller.addListener(_selectionChanged);
  }

  @override
  void didUpdateWidget(covariant ModelCatalogView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_selectionChanged);
      widget.controller.addListener(_selectionChanged);
    }
    if (oldWidget.controller != widget.controller ||
        oldWidget.sessionID != widget.sessionID ||
        oldWidget.applyScope != widget.applyScope) {
      _scopeProfile = widget.controller.profile?.id;
      _scopeLocation = widget.controller.locationRevision;
      _syncDraft();
      _observedModel = _currentModel;
      _observedVariant = _currentVariant;
    }
  }

  /// The session this picker edits, when the apply scope is per-session.
  String? get _scopedSessionID =>
      widget.applyScope == ModelPickerApplyScope.session
      ? widget.sessionID
      : null;

  ModelRef? get _currentModel => _scopedSessionID == null
      ? widget.controller.selectedModel
      : widget.controller.modelForSession(_scopedSessionID!);

  String get _currentVariant => _scopedSessionID == null
      ? widget.controller.selectedVariant
      : widget.controller.variantForSession(_scopedSessionID!);

  void _syncDraft() {
    final sessionID = _scopedSessionID;
    if (sessionID != null) {
      _draftModel = widget.controller.modelForSession(sessionID);
      _draftVariant = widget.controller.variantForSession(sessionID);
    } else {
      _draftModel = widget.controller.selectedModel;
      _draftVariant = widget.controller.selectedVariant;
    }
  }

  void _selectionChanged() {
    if (!mounted) return;
    final untouched =
        _draftModel?.wireName == _observedModel?.wireName &&
        _draftVariant == _observedVariant;
    _observedModel = _currentModel;
    _observedVariant = _currentVariant;
    if (untouched && !_applying && !_optionsOpen) setState(_syncDraft);
  }

  @override
  void dispose() {
    widget.controller.removeListener(_selectionChanged);
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
      return LayoutBuilder(
        builder: (context, constraints) {
          // At large text sizes or with the keyboard open, let the controls
          // scroll with the models. The apply action always remains in view.
          final compact =
              AppTheme.stackedActions(context) || constraints.maxHeight < 460;
          return Column(
            children: [
              if (!compact) ...[
                if (widget.showHeader) _header(context),
                if (catalog != null && catalog.models.isNotEmpty)
                  _pinnedControls(context),
              ],
              Expanded(
                child: catalog == null
                    ? Column(
                        children: [
                          if (compact && widget.showHeader) _header(context),
                          Expanded(child: _catalogState()),
                        ],
                      )
                    : _catalog(context, catalog, compact: compact),
              ),
              if (drafted != null)
                _applyBar(context, catalog!, drafted, compact: compact),
            ],
          );
        },
      );
    },
  );

  CatalogModel? _draftedModel(CatalogSnapshot catalog) {
    final draft = _draftModel;
    if (draft == null) return null;
    for (final model in catalog.models) {
      if (ModelLibrary.sameModel(
        draft,
        ModelRef(providerID: model.providerID, modelID: model.id),
      )) {
        return model.enabled ? model : null;
      }
    }
    return null;
  }

  Widget _header(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(20, 8, 8, 8),
    child: Row(
      children: [
        Expanded(
          child: Text(
            AppTheme.stackedActions(context)
                ? _strings.modelTitleCompact
                : _strings.modelChooseTitle,
            style: Theme.of(context).textTheme.titleLarge,
          ),
        ),
        if (widget.onClose != null)
          IconButton(
            tooltip: 'Close model selector',
            onPressed: widget.onClose,
            icon: const Icon(Icons.close_rounded),
          ),
      ],
    ),
  );

  Widget _pinnedControls(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Column(
      key: _controlsKey,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
          child: TextField(
            key: const Key('model-picker-search'),
            controller: _search,
            textInputAction: TextInputAction.search,
            onChanged: (value) => setState(() => _query = value),
            decoration: InputDecoration(
              hintText: _strings.modelSearchHint,
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
        ),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Row(
            children: [
              for (final section in _ModelCollection.values)
                Semantics(
                  selected: _collection == section,
                  child: TextButton(
                    key: ValueKey('model-collection-${section.name}'),
                    onPressed: () => setState(() => _collection = section),
                    style: TextButton.styleFrom(
                      foregroundColor: _collection == section
                          ? scheme.primary
                          : scheme.onSurfaceVariant,
                      backgroundColor: _collection == section
                          ? scheme.primary.withValues(alpha: .09)
                          : null,
                    ),
                    child: Text(switch (section) {
                      _ModelCollection.all => _strings.modelAll,
                      _ModelCollection.favorites => _strings.modelFavorites,
                      _ModelCollection.recent => _strings.modelRecent,
                    }),
                  ),
                ),
            ],
          ),
        ),
        const SizedBox(height: 8),
        const Divider(height: 1),
      ],
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

  Widget _catalog(
    BuildContext context,
    CatalogSnapshot catalog, {
    required bool compact,
  }) {
    final normalized = _query.trim().toLowerCase();
    final library = widget.controller.modelLibrary;
    final saved = switch (_collection) {
      _ModelCollection.all => null,
      _ModelCollection.favorites => library.favorites,
      _ModelCollection.recent => library.recent,
    };
    final models = presentModels(catalog.models, selected: _currentModel).where(
      (model) {
        final reference = ModelRef(
          providerID: model.providerID,
          modelID: model.id,
        );
        final providerName = presentedProviderName(
          model.providerID,
          catalog.providers,
        );
        return (saved == null ||
                (model.enabled &&
                    saved.any(
                      (ref) => ModelLibrary.sameModel(ref, reference),
                    ))) &&
            (_provider == '*' ||
                presentProvider(model.providerID).groupID == _provider) &&
            (normalized.isEmpty ||
                model.name.toLowerCase().contains(normalized) ||
                model.id.toLowerCase().contains(normalized) ||
                model.providerID.toLowerCase().contains(normalized) ||
                providerName.toLowerCase().contains(normalized)) &&
            switch (_intent) {
              _ModelIntent.all || _ModelIntent.context => true,
              _ModelIntent.fast => model.variants.any(
                (v) => !v.disabled && v.isFast,
              ),
              _ModelIntent.reasoning => model.reasoning,
            };
      },
    ).toList();
    if (_intent == _ModelIntent.context) {
      models.sort((a, b) => b.contextLimit.compareTo(a.contextLimit));
    } else if (saved != null) {
      int rank(CatalogModel model) => saved.indexWhere(
        (ref) => ModelLibrary.sameModel(
          ref,
          ModelRef(providerID: model.providerID, modelID: model.id),
        ),
      );
      models.sort((a, b) => rank(a).compareTo(rank(b)));
    }
    final unloaded = widget.controller.unloadedProviderIDs;
    final filtered =
        normalized.isNotEmpty ||
        _provider != '*' ||
        _intent != _ModelIntent.all;
    final leadItems = <Widget>[
      if (!_sameScope)
        _Notice(
          icon: Icons.info_outline_rounded,
          text: _strings.modelScopeChanged,
        ),
      if (_currentModel != null &&
          !widget.controller.modelAvailable(_currentModel!))
        _Notice(
          icon: Icons.info_outline_rounded,
          text:
              '${_currentModel!.wireName}\n${_strings.modelUnavailableSelection}',
        ),
      if (compact) ...[
        if (widget.showHeader) _header(context),
        if (catalog.models.isNotEmpty) _pinnedControls(context),
      ],
      if (!widget.controller.catalogDetailed)
        const _Notice(
          icon: Icons.info_outline_rounded,
          text:
              'This server returned a basic catalog. Capability and context details are unavailable.',
        ),
      if (unloaded.isNotEmpty)
        _Notice(
          key: const ValueKey('picker-unloaded-providers'),
          icon: Icons.sync_problem_rounded,
          text: unloadedProvidersNotice(
            unloaded
                .map((id) => presentedProviderName(id, catalog.providers))
                .toList(),
          ),
          action: TextButton.icon(
            key: const ValueKey('picker-reload-providers'),
            onPressed: widget.controller.catalogLoading
                ? null
                : widget.controller.reloadProviderRuntime,
            icon: const Icon(Icons.refresh_rounded, size: 18),
            label: const Text('Reload providers'),
          ),
        ),
      if (catalog.models.isNotEmpty)
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 2),
          child: Row(
            children: [
              Expanded(
                child: Text(
                  '${models.length} ${models.length == 1 ? 'model' : 'models'}',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ),
              if (AppTheme.stackedActions(context))
                IconButton(
                  key: const Key('model-picker-filters'),
                  tooltip: filtered ? 'Edit model filters' : 'Filter models',
                  isSelected: _showFilters || filtered,
                  onPressed: () => setState(() => _showFilters = !_showFilters),
                  icon: const Icon(Icons.tune_rounded),
                )
              else
                TextButton.icon(
                  key: const Key('model-picker-filters'),
                  onPressed: () => setState(() => _showFilters = !_showFilters),
                  icon: Icon(
                    _showFilters
                        ? Icons.expand_less_rounded
                        : Icons.tune_rounded,
                    size: 18,
                  ),
                  label: Text(filtered ? 'Filtered' : 'Filters'),
                ),
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
                    : const Icon(Icons.refresh_rounded, size: 20),
              ),
            ],
          ),
        ),
      if (_showFilters)
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
          child: Column(
            children: [
              _providerPicker(presentProviders(catalog.providers)),
              const SizedBox(height: 8),
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(
                  children: [
                    _intentChip('Any capability', _ModelIntent.all),
                    _intentChip('Fast modes', _ModelIntent.fast),
                    _intentChip('Reasoning', _ModelIntent.reasoning),
                    _intentChip('Largest context', _ModelIntent.context),
                  ],
                ),
              ),
            ],
          ),
        ),
      if (compact)
        if (_draftedModel(catalog) case final selected?)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: _selectionSummary(context, catalog, selected),
          ),
      if (models.isEmpty)
        _PickerState(
          icon: catalog.models.isEmpty
              ? Icons.inventory_2_outlined
              : !filtered && _collection == _ModelCollection.favorites
              ? Icons.star_outline_rounded
              : !filtered && _collection == _ModelCollection.recent
              ? Icons.history_rounded
              : Icons.search_off_rounded,
          title: catalog.models.isEmpty
              ? 'No models available'
              : !filtered && _collection == _ModelCollection.favorites
              ? 'Keep your go-to models here'
              : !filtered && _collection == _ModelCollection.recent
              ? 'Your next choice starts here'
              : 'No matching models',
          message: catalog.models.isEmpty
              ? 'Configure a provider on the OpenCode server, then refresh.'
              : !filtered && _collection == _ModelCollection.favorites
              ? 'Tap the star beside any model to find it here.'
              : !filtered && _collection == _ModelCollection.recent
              ? 'Models you use will appear here, most recent first.'
              : _intent == _ModelIntent.fast
              ? 'No model reports an explicit fast or low-effort mode.'
              : 'Try another search, provider, or capability filter.',
          action: TextButton(
            onPressed: () => setState(() {
              _search.clear();
              _query = '';
              _provider = '*';
              _intent = _ModelIntent.all;
              _collection = _ModelCollection.all;
              _showFilters = false;
            }),
            child: Text(filtered ? 'Clear filters' : 'Browse all models'),
          ),
        ),
    ];
    // Build only visible rows even with hundreds of server models.
    return ListView.builder(
      controller: _listScroll,
      keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
      padding: const EdgeInsets.only(bottom: 16),
      itemCount: leadItems.length + models.length,
      itemBuilder: (context, index) {
        if (index < leadItems.length) return leadItems[index];
        final model = models[index - leadItems.length];
        return _modelRow(
          context,
          model,
          presentedProviderName(model.providerID, catalog.providers),
        );
      },
    );
  }

  Widget _agentPicker(CatalogSnapshot catalog, {VoidCallback? onStateChanged}) {
    final visible = catalog.agents
        .where((agent) => !agent.hidden && agent.mode != 'subagent')
        .toList();
    final sessionID = _scopedSessionID;
    final selected = sessionID == null
        ? widget.controller.selectedAgent
        : widget.controller.agentForSession(sessionID);
    final value = selected.isEmpty ? null : selected;
    final unavailable =
        selected.isNotEmpty && !visible.any((agent) => agent.id == selected);
    return KeyedSubtree(
      key: const Key('model-picker-agent'),
      child: DropdownButtonFormField<String>(
        key: ValueKey(('model-picker-agent', value, _agentSaving)),
        isExpanded: true,
        initialValue: value,
        decoration: InputDecoration(
          labelText: 'Agent',
          prefixIcon: const Icon(Icons.support_agent_outlined),
          helperText: _agentSaving ? _strings.modelSelectionSaving : null,
          errorText: _agentError,
        ),
        hint: Text(visible.isEmpty ? 'No agents available' : 'Server default'),
        items: [
          if (unavailable)
            DropdownMenuItem(value: selected, child: Text(selected)),
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
        onChanged:
            !_sameScope ||
                visible.isEmpty ||
                _agentSaving ||
                _applying ||
                (sessionID != null &&
                    widget.controller.sessionSelectionSaving(sessionID))
            ? null
            : (value) async {
                if (value == null) return;
                setState(() {
                  _agentSaving = true;
                  _agentError = null;
                });
                onStateChanged?.call();
                try {
                  if (sessionID == null) {
                    await widget.controller.selectAgent(value);
                  } else {
                    await widget.controller.selectAgentForSession(
                      sessionID,
                      value,
                    );
                  }
                } catch (_) {
                  if (mounted) {
                    setState(() => _agentError = _strings.modelAgentSaveFailed);
                  }
                } finally {
                  if (mounted) setState(() => _agentSaving = false);
                  onStateChanged?.call();
                }
              },
      ),
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
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final reference = ModelRef(providerID: model.providerID, modelID: model.id);
    final current = ModelLibrary.sameModel(_currentModel, reference);
    final draft = ModelLibrary.sameModel(_draftModel, reference);
    final favorite = widget.controller.modelLibrary.isFavorite(reference);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      child: Material(
        color: draft
            ? scheme.primary.withValues(alpha: .08)
            : Colors.transparent,
        borderRadius: BorderRadius.circular(14),
        clipBehavior: Clip.antiAlias,
        child: Semantics(
          selected: draft,
          button: true,
          label: current ? 'Current model' : null,
          child: InkWell(
            key: ValueKey('model-option-${model.providerID}-${model.id}'),
            onTap: model.enabled ? () => _draft(reference) : null,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(12, 14, 4, 14),
              child: Row(
                children: [
                  SizedBox.square(
                    dimension: 28,
                    child: draft
                        ? Icon(Icons.check_rounded, color: scheme.primary)
                        : ProviderLogo(model.providerID, size: 24),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          model.name,
                          style: theme.textTheme.titleSmall?.copyWith(
                            color: model.enabled
                                ? scheme.onSurface
                                : scheme.onSurfaceVariant,
                          ),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                        const SizedBox(height: 4),
                        Text(
                          [
                            providerName,
                            if (model.contextLimit > 0)
                              '${_compactNumber(model.contextLimit)} context',
                            if (!model.enabled)
                              'Unavailable'
                            else if (model.deprecated)
                              'Deprecated'
                            else if (model.preview)
                              'Preview',
                          ].join(' · '),
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: scheme.onSurfaceVariant,
                          ),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    tooltip: favorite
                        ? 'Remove ${model.name} from favorites'
                        : 'Favorite ${model.name}',
                    onPressed: model.enabled
                        ? () => _toggleFavorite(reference)
                        : null,
                    icon: Icon(
                      favorite
                          ? Icons.star_rounded
                          : Icons.star_outline_rounded,
                      color: favorite ? scheme.primary : scheme.outline,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  static String _compactNumber(int value) => value >= 1000000
      ? '${(value / 1000000).toStringAsFixed(value % 1000000 == 0 ? 0 : 1)}M'
      : value >= 1000
      ? '${(value / 1000).round()}K'
      : '$value';
  void _draft(ModelRef model) => setState(() {
    _saveError = null;
    _draftModel = model;
    _draftVariant = ModelLibrary.sameModel(model, _currentModel)
        ? _currentVariant
        : '';
  });

  Future<void> _toggleFavorite(ModelRef model) async {
    try {
      await widget.controller.toggleModelFavorite(model);
    } catch (_) {
      if (mounted) {
        setState(() => _saveError = 'Could not save favorites. Try again.');
      }
    }
  }

  Widget _selectionSummary(
    BuildContext context,
    CatalogSnapshot catalog,
    CatalogModel model,
  ) => Row(
    children: [
      Expanded(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              model.name,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.titleSmall,
            ),
            Text(
              _draftVariant.isEmpty ? _strings.modelDefaultMode : _draftVariant,
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ],
        ),
      ),
      TextButton(
        key: const Key('model-picker-options'),
        onPressed: () => _showModelOptions(context, catalog, model),
        child: Text(_strings.modelOptions),
      ),
    ],
  );

  Widget _applyBar(
    BuildContext context,
    CatalogSnapshot catalog,
    CatalogModel model, {
    required bool compact,
  }) => Material(
    key: const Key('model-picker-apply-bar'),
    color: Theme.of(context).colorScheme.surfaceContainerLow,
    child: SafeArea(
      top: false,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (!compact) ...[
              _selectionSummary(context, catalog, model),
              const SizedBox(height: 8),
            ],
            if (_saveError case final error?)
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Semantics(
                  liveRegion: true,
                  child: Text(
                    error,
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: Theme.of(context).colorScheme.error,
                    ),
                  ),
                ),
              ),
            FilledButton(
              key: ValueKey('use-model-${model.providerID}-${model.id}'),
              onPressed:
                  !_sameScope ||
                      _applying ||
                      _agentSaving ||
                      (_scopedSessionID != null &&
                          widget.controller.sessionSelectionSaving(
                            _scopedSessionID!,
                          ))
                  ? null
                  : _applyDraft,
              child: _applying
                  ? const SizedBox.square(
                      dimension: 24,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : Text(switch (widget.applyScope) {
                      ModelPickerApplyScope.classic => 'Use model and mode',
                      ModelPickerApplyScope.session => 'Use for this session',
                      ModelPickerApplyScope.newSessions =>
                        'Use for new sessions',
                    }, textAlign: TextAlign.center),
            ),
          ],
        ),
      ),
    ),
  );

  Future<void> _showModelOptions(
    BuildContext context,
    CatalogSnapshot catalog,
    CatalogModel model,
  ) async {
    _optionsOpen = true;
    try {
      await showDialog<void>(
        context: context,
        builder: (context) => ListenableBuilder(
          listenable: widget.controller,
          builder: (context, _) => StatefulBuilder(
            builder: (context, updateDialog) => AlertDialog(
              title: Text(model.name),
              scrollable: true,
              content: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    '${presentedProviderName(model.providerID, catalog.providers)} · ${model.id}',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                  const SizedBox(height: 12),
                  Text(
                    [
                      if (model.contextLimit > 0)
                        '${_number(model.contextLimit)} context',
                      if (model.outputLimit > 0)
                        '${_number(model.outputLimit)} output',
                      if (model.reasoning) 'Reasoning',
                      if (model.tools) 'Tools',
                      if (model.attachments) 'Attachments',
                      ?modelCostLabel(model),
                    ].join(' · '),
                  ),
                  const SizedBox(height: 20),
                  Text(
                    _strings.modelThinkingMode,
                    style: Theme.of(context).textTheme.titleSmall,
                  ),
                  const SizedBox(height: 8),
                  _variantSelector(
                    context,
                    model,
                    onChanged: (value) {
                      setState(() => _draftVariant = value);
                      updateDialog(() {});
                    },
                  ),
                  const SizedBox(height: 20),
                  _agentPicker(
                    catalog,
                    onStateChanged: () {
                      if (context.mounted) updateDialog(() {});
                    },
                  ),
                  if (widget.applyScope == ModelPickerApplyScope.session) ...[
                    const SizedBox(height: 16),
                    Text(
                      _strings.modelSessionScopeNote,
                      key: const Key('model-picker-session-scope-note'),
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ],
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(context),
                  child: const Text('Done'),
                ),
              ],
            ),
          ),
        ),
      );
    } finally {
      _optionsOpen = false;
    }
  }

  Widget _variantSelector(
    BuildContext context,
    CatalogModel model, {
    required ValueChanged<String> onChanged,
  }) {
    final variants = model.variants
        .where((variant) => !variant.disabled)
        .toList();
    if (variants.isEmpty) return Text(_strings.modelDefaultMode);
    return Wrap(
      spacing: 8,
      runSpacing: 4,
      children: [
        ChoiceChip(
          key: ValueKey('model-variant-${model.id}-default'),
          label: const Text('Default'),
          selected: _draftVariant.isEmpty,
          onSelected: (_) => onChanged(''),
        ),
        for (final variant in variants)
          ChoiceChip(
            key: ValueKey('model-variant-${model.id}-${variant.id}'),
            label: Text(_variantLabel(variant)),
            selected: _draftVariant == variant.id,
            onSelected: (_) => onChanged(variant.id),
          ),
      ],
    );
  }

  Future<void> _applyDraft() async {
    final model = _draftModel;
    if (model == null || _applying || !_sameScope) return;
    final variant = _draftVariant;
    setState(() {
      _applying = true;
      _saveError = null;
    });
    try {
      final sessionID = _scopedSessionID;
      if (sessionID != null) {
        await widget.controller.selectModelForSession(
          sessionID,
          model,
          variant: variant,
        );
      } else {
        await widget.controller.selectModel(model, variant: variant);
      }
      if (!mounted || !_sameScope) return;
      if (!ModelLibrary.sameModel(_currentModel, model) ||
          _currentVariant != variant) {
        setState(
          () => _saveError =
              'This choice is no longer available. Refresh models and try again.',
        );
        return;
      }
      widget.onApplied?.call();
      if (mounted && widget.onApplied == null) setState(_syncDraft);
    } catch (_) {
      if (mounted) {
        setState(
          () => _saveError = 'Could not save the model choice. Try again.',
        );
      }
    } finally {
      if (mounted) setState(() => _applying = false);
    }
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

/// Copy for the picker notice about providers the server has signed in to
/// but not loaded; [names] are already presented for display.
String unloadedProvidersNotice(List<String> names) {
  final sorted = [...names]..sort();
  final list = switch (sorted.length) {
    0 => 'a provider',
    1 => sorted.single,
    2 => '${sorted[0]} and ${sorted[1]}',
    _ =>
      '${sorted.sublist(0, sorted.length - 1).join(', ')}, and ${sorted.last}',
  };
  final plural = sorted.length > 1;
  return 'OpenCode is signed in to $list but has not loaded '
      '${plural ? 'them' : 'it'} yet, so ${plural ? 'their' : 'its'} models '
      'fail with “Model not found”. Reload to pick up the sign-in.';
}

class _Notice extends StatelessWidget {
  const _Notice({
    super.key,
    required this.icon,
    required this.text,
    this.action,
  });
  final IconData icon;
  final String text;
  final Widget? action;

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
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(text),
                    if (action case final action?)
                      Align(
                        alignment: Alignment.centerRight,
                        child: Padding(
                          padding: const EdgeInsets.only(top: 4),
                          child: action,
                        ),
                      ),
                  ],
                ),
              ),
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
