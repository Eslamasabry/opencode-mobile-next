part of 'pickers.dart';

extension _ModelCatalogFooter on _ModelCatalogViewState {
  /// What sits pinned with the apply action: the thinking level and the
  /// agent, each a chip that opens its menu. A chip that has nothing to
  /// choose is left out (a model with one level, a server without agents).
  Widget _footer(BuildContext context) {
    final catalog = _catalog;
    if (catalog == null || catalog.models.isEmpty) {
      return const SizedBox.shrink();
    }
    // With the keyboard up the results need the room: the thinking and
    // agent choices wait until it closes.
    if (MediaQuery.viewInsetsOf(context).bottom > 0) {
      return const SizedBox.shrink();
    }
    final drafted = _draftedModel(catalog);
    final variants =
        drafted?.variants.where((variant) => !variant.disabled).toList() ??
        const <CatalogVariant>[];
    final agents = _choosableAgents(catalog);
    CatalogVariant? chosenVariant;
    for (final variant in variants) {
      if (variant.id == _draftVariant) chosenVariant = variant;
    }
    final chips = <Widget>[
      if (drafted != null && variants.isNotEmpty)
        Builder(
          builder: (chipContext) => KitChip.summary(
            key: const Key('model-picker-thinking'),
            icon: AppIconography.idea,
            label: _strings.modelPickerThinkingChip(
              chosenVariant == null
                  ? _strings.e7ModelUiDefault
                  : _variantLabel(chosenVariant),
            ),
            expanded: false,
            onPressed: () =>
                unawaited(_chooseThinking(chipContext, drafted, variants)),
          ),
        ),
      if (agents.isNotEmpty)
        Builder(
          builder: (chipContext) {
            if (_agentPending) {
              _agentPending = false;
              WidgetsBinding.instance.addPostFrameCallback((_) {
                if (mounted && chipContext.mounted) {
                  unawaited(_chooseAgent(chipContext));
                }
              });
            }
            return KitChip.summary(
              key: const Key('model-picker-agent'),
              icon: AppIconography.agent,
              label: _strings.modelPickerAgentChip(
                _draftAgent.isEmpty
                    ? _strings.e7ModelUiServerDefault
                    : _agentTitle(_draftAgent),
              ),
              expanded: false,
              onPressed: () => unawaited(_chooseAgent(chipContext)),
            );
          },
        ),
    ];
    if (chips.isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: EdgeInsetsDirectional.only(bottom: KitTokens.of(context).space2),
      child: KitChipWrap(
        key: const Key('model-picker-footer'),
        children: chips,
      ),
    );
  }

  /// The agents a person picks from: primary ones the server does not hide.
  List<CatalogAgent> _choosableAgents(CatalogSnapshot catalog) =>
      widget.controller.capabilities.agentSelection
      ? catalog.agents
            .where((agent) => !agent.hidden && agent.mode != 'subagent')
            .toList()
      : const [];

  Future<void> _chooseThinking(
    BuildContext anchor,
    CatalogModel model,
    List<CatalogVariant> variants,
  ) async {
    final locked = _lockedReason;
    final known = variants.any((variant) => variant.id == _draftVariant);
    await showKitMenu(
      anchor,
      semanticsLabel: _strings.modelPickerThinking,
      items: [
        KitMenuItem(
          key: ValueKey('model-variant-${model.id}-default'),
          label: _strings.e7ModelUiDefault,
          checked: !known,
          enabled: locked == null,
          disabledReason: locked,
          onSelected: () => _setVariant(model, ''),
        ),
        for (final variant in variants)
          KitMenuItem(
            key: ValueKey('model-variant-${model.id}-${variant.id}'),
            label: _variantLabel(variant),
            checked: variant.id == _draftVariant,
            enabled: locked == null,
            disabledReason: locked,
            onSelected: () => _setVariant(model, variant.id),
          ),
      ],
    );
  }

  /// A level chosen from the menu belongs to the model the menu listed:
  /// if the draft moved on while it was open (another screen chose a
  /// model), the pick brings the draft back to that model rather than
  /// giving its level to a model that may not have it.
  void _setVariant(CatalogModel model, String value) {
    if (!mounted) return;
    _set(() {
      _draftModel = ModelRef(providerID: model.providerID, modelID: model.id);
      _draftVariant = value;
      _saveError = null;
    });
  }

  Future<void> _chooseAgent(BuildContext anchor) async {
    final catalog = _catalog;
    if (catalog == null) return;
    final agents = _choosableAgents(catalog);
    if (agents.isEmpty) return;
    final selected = _draftAgent;
    final unavailable =
        selected.isNotEmpty && !agents.any((agent) => agent.id == selected);
    final locked = _lockedReason;
    await showKitMenu(
      anchor,
      semanticsLabel: _strings.e7ModelUiAgent,
      items: [
        if (unavailable)
          KitMenuItem(
            label: _agentTitle(selected),
            checked: true,
            enabled: false,
            disabledReason: _strings.modelPickerUnavailableReason,
            onSelected: () {},
          ),
        for (final agent in agents)
          KitMenuItem(
            key: ValueKey('model-picker-agent-${agent.id}'),
            label: _agentTitle(agent.id),
            supporting: _agentSupporting(agent),
            checked: agent.id == selected,
            enabled: locked == null,
            disabledReason: locked,
            onSelected: () {
              if (!mounted) return;
              _set(() {
                _draftAgent = agent.id;
                _saveError = null;
              });
            },
          ),
      ],
    );
  }

  // --- Models --------------------------------------------------------------

  /// What the apply action says: with the classic scope it names the model
  /// and agent it applies ("Use Claude Opus 5.5 · Build"); the OpenCode 2
  /// scopes name where it applies ("Use for this conversation").
  String _applyText(CatalogModel? drafted) {
    if (widget.applyScope != ModelPickerApplyScope.classic || drafted == null) {
      return _applyLabel(_strings, widget.applyScope);
    }
    final agent = widget.controller.capabilities.agentSelection
        ? _draftAgent
        : '';
    return agent.isEmpty
        ? _strings.modelPickerUseModel(drafted.name)
        : _strings.e7ModelUiUseModelMode(drafted.name, _agentTitle(agent));
  }

  /// Why the apply action cannot run yet; null when it can.
  String? _applyBlockedBy(CatalogModel? drafted) =>
      drafted == null ? _strings.modelPickerChooseFirst : _lockedReason;

  /// The footer and apply action for a view that pins its own (hosted on
  /// its own); inside the sheet the sheet pins them.
  Widget _applyBlock(BuildContext context, CatalogModel? drafted) {
    final reason = _applyBlockedBy(drafted);
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _footer(context),
        KitActionBlock(
          key: const Key('model-picker-apply-bar'),
          primary: KitAction(
            key: drafted == null
                ? const Key('model-picker-apply')
                : ValueKey('use-model-${drafted.providerID}-${drafted.id}'),
            label: _applyText(drafted),
            working: _applying,
            onPressed: reason == null || _applying ? _applyDraft : null,
            disabledReason: _applying ? null : reason,
          ),
        ),
      ],
    );
  }

  Future<void> _applyDraft() async {
    final model = _draftModel;
    if (_applying || !_sameScope) return;
    final catalog = _catalog;
    if (model == null || catalog == null || _draftedModel(catalog) == null) {
      _set(() => _saveError = _strings.modelPickerChooseFirst);
      return;
    }
    final variant = _draftVariant;
    final agent = _draftAgent;
    _set(() {
      _setApplying(true);
      _saveError = null;
    });
    var modelConfirmed = false;
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
      if (!_sameScope) return;
      // A runtime may resolve Default to the model's advertised default ID.
      final effectiveDefault =
          variant.isEmpty &&
          _draftedModel(catalog)!.variants.any(
            (option) =>
                !option.disabled &&
                option.id == _currentVariant &&
                option.options['isDefault'] == true,
          );
      if (!ModelLibrary.sameModel(_currentModel, model) ||
          (_currentVariant != variant && !effectiveDefault)) {
        if (mounted) {
          _set(() => _saveError = _strings.e7ModelUiSelectionGone);
        }
        return;
      }
      modelConfirmed = true;
      if (widget.controller.capabilities.agentSelection &&
          agent.isNotEmpty &&
          agent != _currentAgent) {
        if (sessionID != null) {
          await widget.controller.selectAgentForSession(sessionID, agent);
        } else {
          await widget.controller.selectAgent(agent);
        }
        if (!mounted || !_sameScope) return;
        if (_currentAgent != agent) {
          _set(() => _saveError = _strings.modelChoicePartialSaveError);
          return;
        }
      }
      if (!mounted) return;
      widget.onApplied?.call();
      if (mounted && widget.onApplied == null) _set(_syncDraft);
    } catch (_) {
      if (mounted) {
        _set(
          () => _saveError = modelConfirmed
              ? _strings.modelChoicePartialSaveError
              : _strings.modelChoiceModelSaveError,
        );
      }
    } finally {
      if (mounted) {
        _set(() => _setApplying(false));
      } else {
        _applying = false;
      }
    }
  }
}
