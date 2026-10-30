part of 'pickers.dart';

/// The server's default models, most authoritative first. A phone agent
/// (Paseo) marks its own default on one model of each agent's list; when any
/// model carries that mark, only marked ones count, because the list's
/// fallback default there is merely its first model. Otherwise it is the
/// server's reported default (OpenCode 2 `/model/default`, OpenCode 1's
/// `default`).
List<ModelRef> _serverDefaults(ConnectionController controller) {
  final providers = controller.providers;
  if (providers == null) return const [];
  final marked = [
    for (final provider in providers.providers)
      for (final entry in provider.modelData.entries)
        if (entry.value['isDefault'] == true)
          ModelRef(providerID: provider.id, modelID: entry.key),
  ];
  if (marked.isNotEmpty) return marked;
  final provider = providers.defaultProviderID;
  final model = providers.defaultModelID;
  if (provider == null || provider.isEmpty || model == null || model.isEmpty) {
    return const [];
  }
  return [ModelRef(providerID: provider, modelID: model)];
}

/// The model sheet's plain choices for [catalog], or null when the sheet
/// should show every model instead: no catalog models, nothing honest to
/// recommend besides the model in use, or no more models than choices. An empty list means the
/// catalog is still loading (or failed): the choices step shows that.
/// [sessionID] is the session the sheet edits, if any.
List<ModelRecommendation>? modelChoicesFor(
  ConnectionController controller,
  CatalogSnapshot? catalog, {
  String? sessionID,
}) {
  if (catalog == null) return const [];
  if (catalog.models.isEmpty) return null;
  final current = sessionID == null
      ? controller.selectedModel
      : controller.modelForSession(sessionID);
  final models = presentModels(catalog.models, selected: current);
  final library = controller.modelLibrary;
  final choices = recommendModels(
    models,
    current: current,
    defaults: _serverDefaults(controller),
    recent: library.recent,
    favorites: library.favorites,
  );
  // A first view with nothing but the model in use offers no choice.
  final alternative = choices.any(
    (choice) => !ModelLibrary.sameModel(choice.ref, current),
  );
  if (!alternative || models.length <= choices.length) return null;
  return choices;
}

String _choiceKey(CatalogModel model) => '${model.providerID}/${model.id}';

extension _ModelCatalogChoices on _ModelCatalogViewState {
  /// The first view: a few plain choices that lead with the reason, and
  /// "All models (N)" under them.
  List<Widget> _choiceItems(
    BuildContext context,
    CatalogSnapshot? catalog,
    List<ModelRecommendation> choices,
  ) {
    final tokens = KitTokens.of(context);
    final gap = SizedBox(height: tokens.space3);
    final controller = widget.controller;
    final current = _currentModel;
    final notices = <Widget>[
      if (!_sameScope)
        KitNotice(
          key: const Key('model-picker-scope-changed'),
          icon: AppIconography.info,
          message: _strings.modelScopeChanged,
        ),
      if (current != null &&
          catalog != null &&
          catalog.models.isNotEmpty &&
          !controller.modelAvailable(current))
        KitNotice(
          key: const Key('model-picker-current-unavailable'),
          icon: AppIconography.warning,
          title: modelNameFromId(current.modelID),
          message: _strings.modelUnavailableSelection,
        ),
      if (_saveError case final error?)
        KitNotice(
          key: const Key('model-picker-save-error'),
          tone: AppStatusTone.failure,
          message: error,
        ),
    ];
    if (catalog == null) {
      return [
        for (final notice in notices) ...[notice, gap],
        if (controller.catalogError != null)
          _catalogState()
        else
          Semantics(
            liveRegion: true,
            label: _strings.e7ModelUiLoading,
            child: const KitSkeletonRows(count: 3),
          ),
      ];
    }
    String? selected;
    for (final choice in choices) {
      if (ModelLibrary.sameModel(choice.ref, current)) {
        selected = _choiceKey(choice.model);
      }
    }
    final choosing = _choosing;
    final count = presentModels(catalog.models, selected: current).length;
    final largeText = MediaQuery.textScalerOf(context).scale(1) >= 1.3;
    return [
      for (final notice in notices) ...[notice, gap],
      KitChoiceTiles<String>(
        key: const Key('model-choices'),
        semanticsLabel: widget._apply?.choicesTitle,
        selected: selected,
        busy: _applying && choosing != null
            ? '${choosing.providerID}/${choosing.modelID}'
            : null,
        choices: [
          for (final choice in choices)
            KitChoice<String>(
              key: ValueKey('model-choice-${choice.reason.name}'),
              value: _choiceKey(choice.model),
              title: _reasonTitle(choice.reason),
              // At large text each fact takes its own line, so a dot never
              // dangles at a line's end.
              supporting: [
                _choiceName(choice.model),
                if (choice.alsoCheapest) _strings.modelPickerReasonCheapest,
                // A name that already says Free ("GLM-5.3-Flash Free") does
                // not say it twice.
                if (choice.free &&
                    !_saysFree(
                      _choiceName(choice.model),
                      _strings.modelPickerFree,
                    ))
                  _strings.modelPickerFree,
              ].join(largeText ? '\n' : ' · '),
              enabled: _lockedReason == null,
              disabledReason: _lockedReason,
            ),
        ],
        onSelected: (key) {
          for (final choice in choices) {
            if (_choiceKey(choice.model) == key) {
              unawaited(_chooseNow(choice.model));
            }
          }
        },
        more: KitChoiceMore(
          key: const Key('model-picker-all'),
          label: _strings.modelPickerAllModels(count),
          onTap: () => widget._apply?.openAll(),
        ),
      ),
    ];
  }

  String _reasonTitle(ModelReason reason) => switch (reason) {
    ModelReason.inUse => _strings.modelPickerInUse,
    ModelReason.serverDefault => _strings.modelPickerReasonDefault,
    ModelReason.recent => _strings.modelPickerReasonRecent,
    ModelReason.cheapest => _strings.modelPickerReasonCheapest,
  };

  /// The catalog's name; an id the catalog left unnamed is read as words.
  String _choiceName(CatalogModel model) =>
      model.name.trim().isEmpty || model.name == model.id
      ? modelNameFromId(model.id)
      : model.name;

  /// A tap on a choice: it is applied (the model only; the thinking level
  /// starts at its default and the agent stays) and the sheet closes.
  /// The model already in use just closes it.
  Future<void> _chooseNow(CatalogModel model) async {
    if (_applying || _lockedReason != null) return;
    final ref = ModelRef(providerID: model.providerID, modelID: model.id);
    if (ModelLibrary.sameModel(ref, _currentModel)) {
      widget.onApplied?.call();
      return;
    }
    _set(() {
      _saveError = null;
      _draftModel = ref;
      _draftVariant = '';
      _draftAgent = _currentAgent;
      _choosing = ref;
    });
    await _applyDraft();
  }
}

/// Whether [name] already ends with the word [free] (any case).
bool _saysFree(String name, String free) =>
    name.trimRight().toLowerCase().endsWith(free.toLowerCase());
