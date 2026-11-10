part of 'pickers.dart';

extension _ModelCatalogModels on _ModelCatalogViewState {
  List<CatalogModel> _filteredModels(CatalogSnapshot catalog) {
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
              // OpenCode 2 lists a fast mode as a model of its own
              // ("Claude Opus 5.5 Fast", `gpt-6-sol-fast`), not as a
              // variant.
              _ModelIntent.fast =>
                model.id.toLowerCase().endsWith('-fast') ||
                    model.variants.any((v) => !v.disabled && v.isFast),
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
    return models;
  }

  String _intentLabel(_ModelIntent intent) => switch (intent) {
    _ModelIntent.all => _strings.e7ModelUiAnyCapability,
    _ModelIntent.fast => _strings.e7ModelUiFastModes,
    _ModelIntent.reasoning => _strings.e7ModelUiReasoning,
    _ModelIntent.context => _strings.e7ModelUiLargestContext,
  };

  void _resetFilters() => _set(() {
    _search.clear();
    _query = '';
    _provider = '*';
    _intent = _ModelIntent.all;
    _collection = _ModelCollection.all;
    _visible = _modelPage;
  });

  List<Widget> _modelSection(BuildContext context, CatalogSnapshot catalog) {
    final tokens = KitTokens.of(context);
    final gap = SizedBox(height: tokens.space3);
    final controller = widget.controller;
    final models = _filteredModels(catalog);
    final providers = presentProviders(catalog.providers);
    final filtered =
        _query.trim().isNotEmpty ||
        _provider != '*' ||
        _intent != _ModelIntent.all;
    String? providerName;
    for (final provider in providers) {
      if (provider.id == _provider) providerName = provider.name;
    }
    final activeFilter = [
      ?providerName,
      if (_intent != _ModelIntent.all) _intentLabel(_intent),
    ].join(' · ');
    final shown = models.take(_visible).toList();
    return [
      // Search with its filter menu, and Refresh for the catalog beside it.
      Row(
        key: _searchRow,
        children: [
          Expanded(
            child: KitSearchField(
              label: _strings.modelSearchHint,
              controller: _search,
              fieldKey: const Key('model-picker-search'),
              clearKey: const Key('model-picker-search-clear'),
              filterKey: const Key('model-picker-filters'),
              resultCount: _query.trim().isEmpty ? null : models.length,
              onChanged: (value) {
                _set(() {
                  _query = value;
                  _visible = _modelPage;
                });
                _revealSearch();
              },
              filters: [
                for (final intent in _ModelIntent.values)
                  KitMenuItem(
                    key: ValueKey('model-intent-${intent.name}'),
                    label: _intentLabel(intent),
                    group: 'intent',
                    checked: _intent == intent,
                    onSelected: () => _set(() {
                      _intent = intent;
                      _visible = _modelPage;
                    }),
                  ),
                KitMenuItem(
                  key: const ValueKey('model-provider-*'),
                  label: _strings.e7ModelUiAllProviders,
                  group: 'provider',
                  checked: _provider == '*',
                  onSelected: () => _set(() => _provider = '*'),
                ),
                for (final provider in providers)
                  KitMenuItem(
                    key: ValueKey('model-provider-${provider.id}'),
                    label: provider.name,
                    group: 'provider',
                    checked: _provider == provider.id,
                    onSelected: () => _set(() {
                      _provider = provider.id;
                      _visible = _modelPage;
                    }),
                  ),
              ],
              activeFilter: activeFilter.isEmpty ? null : activeFilter,
              onClearFilter: () => _set(() {
                _provider = '*';
                _intent = _ModelIntent.all;
              }),
            ),
          ),
          SizedBox(width: tokens.space2),
          KitIconButton(
            key: const Key('model-picker-refresh'),
            icon: AppIconography.retry,
            tooltip: _strings.e7ModelUiRefresh,
            working: controller.catalogLoading,
            onPressed: controller.catalogLoading
                ? null
                : controller.refreshCatalog,
          ),
        ],
      ),
      gap,
      _collections(),
      gap,
      if (models.isEmpty)
        _noMatch(filtered)
      else
        KitRowGroup(
          key: const Key('model-picker-list'),
          margin: EdgeInsets.zero,
          children: [
            for (final model in shown)
              _modelRow(
                context,
                model,
                presentedProviderName(model.providerID, catalog.providers),
              ),
            if (models.length > shown.length)
              KitRow(
                key: const Key('model-picker-more'),
                leading: KitRow.icon(context, AppIconography.unfoldMore),
                title: _strings.modelPickerShowMore(
                  models.length - shown.length,
                ),
                onTap: () => _set(() => _visible += _modelPage),
              ),
          ],
        ),
      if (_canConnect) ...[gap, _connectGroup(context)],
    ];
  }

  /// All / Favorites / Recent.
  Widget _collections() => KitSegmented<_ModelCollection>(
    semanticsLabel: _strings.modelPickerCollections,
    selected: _collection,
    onChanged: (value) => _set(() {
      _collection = value;
      _visible = _modelPage;
    }),
    segments: [
      KitSegment(
        key: const ValueKey('model-collection-all'),
        value: _ModelCollection.all,
        label: _strings.modelAll,
      ),
      KitSegment(
        key: const ValueKey('model-collection-favorites'),
        value: _ModelCollection.favorites,
        label: _strings.modelFavorites,
      ),
      KitSegment(
        key: const ValueKey('model-collection-recent'),
        value: _ModelCollection.recent,
        label: _strings.modelRecent,
      ),
    ],
  );

  /// The server has no model at all: the next step is signing in to a
  /// provider, which lives on the Providers screen.
  Widget _noModels(BuildContext context) {
    final controller = widget.controller;
    return KitStateView(
      key: const Key('model-picker-no-models'),
      icon: AppIconography.login,
      size: KitStateSize.inline,
      title: _strings.modelPickerSignInTitle,
      body: _strings.modelPickerSignInBody,
      primary: controller.isIsolated
          ? null
          : KitAction(
              key: const Key('model-picker-open-providers'),
              label: _strings.chatUiOpenProviders,
              onPressed: () => unawaited(_connect()),
            ),
      secondary: KitAction(
        label: _strings.e7ModelUiRefresh,
        icon: AppIconography.retry,
        working: controller.catalogLoading,
        onPressed: controller.refreshCatalog,
      ),
    );
  }

  Widget _noMatch(bool filtered) {
    if (_query.trim().isNotEmpty) {
      return KitSearchNoMatch(
        query: _query.trim(),
        onClear: _resetFilters,
        action: filtered && (_provider != '*' || _intent != _ModelIntent.all)
            ? KitAction(
                label: _strings.e7ModelUiClearFilters,
                onPressed: _resetFilters,
              )
            : null,
      );
    }
    final favorites = !filtered && _collection == _ModelCollection.favorites;
    final recent = !filtered && _collection == _ModelCollection.recent;
    return KitStateView(
      key: const Key('model-picker-empty'),
      icon: favorites
          ? AppIconography.star
          : recent
          ? AppIconography.history
          : AppIconography.search,
      size: KitStateSize.inline,
      title: favorites
          ? _strings.e7ModelUiFavoritesEmpty
          : recent
          ? _strings.e7ModelUiRecentEmpty
          : _strings.e7ModelUiNoMatches,
      body: favorites
          ? _strings.e7ModelUiFavoritesHint
          : recent
          ? _strings.e7ModelUiRecentHint
          : _intent == _ModelIntent.fast
          ? _strings.e7ModelUiNoFastModes
          : _strings.e7ModelUiNoMatchesHint,
      primary: KitAction(
        label: filtered
            ? _strings.e7ModelUiClearFilters
            : _strings.e7ModelUiBrowseAll,
        onPressed: _resetFilters,
      ),
    );
  }

  Widget _modelRow(
    BuildContext context,
    CatalogModel model,
    String providerName,
  ) {
    final reference = ModelRef(providerID: model.providerID, modelID: model.id);
    final current = ModelLibrary.sameModel(_currentModel, reference);
    final draft = ModelLibrary.sameModel(_draftModel, reference);
    final favorite = widget.controller.modelLibrary.isFavorite(reference);
    final favoriteLabel = favorite
        ? _strings.e7ModelUiUnfavorite(model.name)
        : _strings.e7ModelUiFavorite(model.name);
    return KitRow(
      key: ValueKey('model-option-${model.providerID}-${model.id}'),
      titleKey: ValueKey('model-name-${model.providerID}-${model.id}'),
      leading: KitRowIcon(
        draft ? AppIconography.check : AppIconography.model,
        current: draft,
      ),
      title: model.name,
      titleMaxLines: 2,
      supportingMaxLines: 2,
      supporting: TextSpan(
        text: [
          // Words, not colour: the one in use, and a release from the last
          // month (Opus 5.5, GPT-6 Sol) found without knowing its name.
          if (current) _strings.modelPickerInUse,
          if (isNewModel(model)) _strings.modelNewBadge,
          providerName,
          if (model.contextLimit > 0)
            _strings.e7ModelUiContext(
              _ModelCatalogViewState._compactNumber(model.contextLimit),
            ),
          if (model.deprecated)
            _strings.e7ModelUiDeprecated
          else if (model.preview)
            _strings.e7ModelUiPreview,
        ].join(' · '),
      ),
      selected: draft,
      below: draft ? _draftDetails(context, model) : null,
      enabled: model.enabled,
      disabledReason: model.enabled
          ? null
          : _strings.modelPickerUnavailableReason,
      onTap: model.enabled ? () => _draft(reference) : null,
      trailing: model.enabled
          ? KitIconButton(
              key: ValueKey('model-favorite-${model.providerID}-${model.id}'),
              icon: favorite ? AppIconography.starFilled : AppIconography.star,
              tooltip: favoriteLabel,
              selected: favorite,
              onPressed: () => _toggleFavorite(reference),
            )
          : null,
      menu: [
        if (model.enabled)
          KitMenuItem(
            label: favoriteLabel,
            icon: favorite ? AppIconography.starFilled : AppIconography.star,
            onSelected: () => _toggleFavorite(reference),
          ),
        KitMenuItem.copy(
          label: _strings.modelPickerCopyId,
          text: () => '${model.providerID}/${model.id}',
        ),
      ],
    );
  }

  void _draft(ModelRef model) => _set(() {
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
        _set(() => _saveError = _strings.e7ModelUiFavoritesFailed);
      }
    }
  }
}
