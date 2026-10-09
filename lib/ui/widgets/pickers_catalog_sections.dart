part of 'pickers.dart';

extension _ModelCatalogSections on _ModelCatalogViewState {
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

  /// Everything above the pinned action, top to bottom.
  List<Widget> _items(
    BuildContext context,
    CatalogSnapshot? catalog,
    CatalogModel? drafted,
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
      // Only a loaded catalog can say a model is missing from it.
      if (current != null &&
          catalog != null &&
          catalog.models.isNotEmpty &&
          !controller.modelAvailable(current))
        KitNotice(
          key: const Key('model-picker-current-unavailable'),
          icon: AppIconography.warning,
          title: current.wireName,
          message: _strings.modelUnavailableSelection,
        ),
      if (_saveError case final error?)
        KitNotice(
          key: const Key('model-picker-save-error'),
          tone: AppStatusTone.failure,
          message: error,
        ),
      if (_connectStatus case final status?)
        KitNotice(
          key: const Key('model-picker-connect-status'),
          tone: AppStatusTone.ok,
          message: status,
        ),
      if (catalog != null && _canConnect && _freeOnly(catalog)) _freeOnlyNote(),
      if (catalog != null) ..._catalogNotices(context, catalog),
    ];
    return [
      if (widget.showHeader) ...[_header(context), gap],
      for (final notice in notices) ...[notice, gap],
      if (catalog == null)
        _catalogState()
      else if (catalog.models.isEmpty)
        _noModels(context)
      else
        ..._modelSection(context, catalog),
    ];
  }

  Widget _header(BuildContext context) => Row(
    children: [
      Expanded(
        child: Semantics(
          header: true,
          child: KitText(_strings.modelChooseTitle, role: KitTextRole.headline),
        ),
      ),
      if (widget.onClose case final close?)
        KitIconButton(
          icon: AppIconography.close,
          tooltip: _strings.e7ModelUiClose,
          onPressed: close,
        ),
    ],
  );

  /// Notices about the catalog itself, each only when it applies.
  List<Widget> _catalogNotices(BuildContext context, CatalogSnapshot catalog) {
    final controller = widget.controller;
    final unloaded = controller.unloadedProviderIDs;
    // A basic catalog is worth saying only when the rows really lack their
    // details; a notice over rows that show "200K context" contradicts them.
    final detailsMissing =
        !controller.catalogDetailed &&
        catalog.models.isNotEmpty &&
        catalog.models.every(
          (model) => model.contextLimit <= 0 && model.cost == null,
        );
    return [
      // A row, not a notice or a dialog: what is wrong and the one fix, in
      // the sheet itself.
      if (unloaded.isNotEmpty)
        KitRowGroup(
          key: const ValueKey('picker-unloaded-providers'),
          margin: EdgeInsets.zero,
          children: [
            KitRow(
              key: const ValueKey('picker-reload-providers'),
              leading: KitRow.icon(context, AppIconography.retry),
              title: _strings.modelChoiceReloadProviders,
              supporting: TextSpan(
                text: unloadedProvidersNotice(
                  unloaded
                      .map((id) => presentedProviderName(id, catalog.providers))
                      .toList(),
                  strings: _strings,
                  unusable: controller.unloadedProvidersUnusable,
                  waitingOnReplies: controller.providerReloadWaitingOn,
                ),
              ),
              supportingMaxLines: 4,
              enabled: !controller.catalogLoading,
              disabledReason: controller.catalogLoading
                  ? _strings.e7ModelUiLoading
                  : null,
              onTap: () => unawaited(controller.reloadProviderRuntime()),
            ),
          ],
        ),
      if (detailsMissing)
        KitNotice(
          key: const Key('model-picker-basic-catalog'),
          icon: AppIconography.info,
          message: _strings.e7ModelUiBasicCatalog,
        ),
    ];
  }

  Widget _catalogState() {
    final controller = widget.controller;
    if (controller.catalogError case final error?) {
      return KitStateView.error(
        title: _strings.e7ModelUiLoadFailed,
        details: error,
        size: KitStateSize.inline,
        retry: KitAction(
          label: _strings.e7ModelUiRetry,
          icon: AppIconography.retry,
          working: controller.catalogLoading,
          onPressed: controller.refreshCatalog,
        ),
      );
    }
    return Semantics(
      liveRegion: true,
      label: _strings.e7ModelUiLoading,
      child: const KitSkeletonRows(count: 6),
    );
  }

  // --- Connecting a provider from here -----------------------------------

  bool get _canConnect =>
      !widget.controller.isIsolated &&
      widget.controller.capabilities.serverCatalog;

  Future<void> _loadIntegrations() async {
    final controller = widget.controller;
    final repository = controller.repository;
    if (!_canConnect || repository == null) return;
    try {
      final integrations = await repository.listIntegrations();
      if (mounted) _set(() => _integrations = integrations);
    } catch (_) {
      // The rows fall back to the general "Connect a provider" one.
    }
  }

  bool _hasModelsOf(String id) =>
      _catalog?.models.any((m) => m.providerID == id) ?? false;

  /// Opens Providers (for [id], straight into its key dialog or sign-in)
  /// and comes back here with the catalog refreshed.
  Future<void> _connect({String? id}) async {
    final controller = widget.controller;
    await pushKitPage<void>(
      context,
      (_) => IntegrationsScreen(
        controller: controller,
        mode: IntegrationsMode.providers,
        connectProviderID: id,
      ),
    );
    if (!mounted) return;
    await controller.refreshCatalog();
    unawaited(_loadIntegrations());
    if (!mounted) return;
    _set(() {
      _connectStatus = null;
      if (id == null) return;
      final integration = _integrations
          ?.where((candidate) => candidate.id == id)
          .firstOrNull;
      final name = integration == null
          ? presentedProviderName(id, controller.catalog?.providers ?? const [])
          : presentIntegrations([integration]).first.name;
      if (controller.unloadedProviderIDs.contains(id)) {
        // The reload row above names it and any running replies it waits on.
        _connectStatus = _strings.pickerProviderNotLoaded(name);
      } else if (_hasModelsOf(id)) {
        _provider = id;
        _visible = _modelPage;
        _connectStatus = _strings.pickerProviderReady(name);
      }
    });
  }

  /// One provider that could give models: an API key when the server offers
  /// one, else its sign-in.
  Widget? _providerAction(BuildContext context, IntegrationInfo integration) {
    final key = integration.methods.any((m) => m.type == 'key');
    final other = integration.methods.any(
      (m) => m.type == 'oauth' || m.type == 'command',
    );
    if (!key && !other) return null;
    final name =
        presentIntegrations([integration]).firstOrNull?.name ??
        integration.name;
    final unloaded = widget.controller.unloadedProviderIDs.contains(
      integration.id,
    );
    return KitRow(
      key: ValueKey('picker-connect-${integration.id}'),
      leading: KitRow.icon(
        context,
        key ? AppIconography.permissions : AppIconography.login,
      ),
      title: key
          ? _strings.pickerAddKeyFor(name)
          : _strings.pickerSignInTo(name),
      supporting: TextSpan(
        text: !key
            ? _strings.pickerSignInHint
            : unloaded
            ? _strings.integrationsSignedInUnusable
            : _strings.pickerAddKeyNotConnectedHint,
      ),
      supportingMaxLines: 2,
      onTap: () => unawaited(_connect(id: integration.id)),
    );
  }

  /// Providers worth an action of their own: signed in but not loaded, or
  /// Anthropic and Google (key-led) while they have no models here.
  List<IntegrationInfo> _actionableProviders() {
    final unloaded = widget.controller.unloadedProviderIDs;
    return [
      for (final integration in _integrations ?? const <IntegrationInfo>[])
        if (unloaded.contains(integration.id) ||
            (providerKeyPageUrl(integration.id) != null &&
                integration.connectionCount == 0 &&
                !_hasModelsOf(integration.id)))
          integration,
    ];
  }

  /// The end of the list: each provider that could give models, then the
  /// general way in. Nothing when this server has no providers to connect.
  Widget _connectGroup(BuildContext context) {
    final rows = [
      for (final integration in _actionableProviders())
        ?_providerAction(context, integration),
    ];
    return KitRowGroup(
      key: const Key('model-picker-connect'),
      margin: EdgeInsets.zero,
      children: [
        ...rows,
        KitRow(
          key: const Key('model-picker-connect-provider'),
          leading: KitRow.icon(context, AppIconography.add),
          title: _strings.pickerConnectProvider,
          supporting: TextSpan(text: _strings.pickerConnectProviderHint),
          supportingMaxLines: 2,
          onTap: () => unawaited(_connect()),
        ),
      ],
    );
  }

  /// Only OpenCode's free models are listed: nobody is signed in.
  bool _freeOnly(CatalogSnapshot catalog) =>
      catalog.models.isNotEmpty &&
      catalog.models.every((m) => m.providerID == openCodeFreeProviderID);

  Widget _freeOnlyNote() => KitNotice(
    key: const Key('model-picker-free-only'),
    icon: AppIconography.speed,
    message: _strings.pickerFreeOnlyNote,
    actions: [
      KitAction(
        key: const Key('model-picker-free-add-key'),
        label: _strings.freeModelSignIn,
        onPressed: () => unawaited(_connectFirstKeyProvider()),
      ),
      KitAction(
        key: const Key('model-picker-free-connect'),
        label: _strings.pickerConnectProvider,
        onPressed: () => unawaited(_connect()),
      ),
    ],
  );

  /// "Add an API key" from the note: the first provider that takes one
  /// (Anthropic, Google), else the general Providers page.
  Future<void> _connectFirstKeyProvider() {
    final key = _actionableProviders().where(
      (i) => i.methods.any((m) => m.type == 'key'),
    );
    return _connect(id: key.firstOrNull?.id);
  }

  // --- Footer: thinking and agent ----------------------------------------

  /// What the chosen model can do, its output limit and prices, in words,
  /// under its checked row. The context window is already on the row's
  /// supporting line, and the id is in the row's menu (Copy model ID).
  Widget? _draftDetails(BuildContext context, CatalogModel model) {
    final cost = model.cost;
    final lines = <String>[
      if (model.outputLimit > 0)
        _strings.modelPickerDetailsOutput(_number(model.outputLimit)),
      if (model.reasoning) _strings.modelPickerCanThink,
      if (model.tools) _strings.modelPickerCanUseTools,
      if (model.attachments) _strings.modelPickerCanReadAttachments,
      if (cost != null &&
          (cost.inputPerMillion > 0 || cost.outputPerMillion > 0))
        _strings.modelPickerDetailsPrice(
          _ModelCatalogViewState._money(cost.inputPerMillion),
          _ModelCatalogViewState._money(cost.outputPerMillion),
        ),
    ];
    if (lines.isEmpty) return null;
    return KitText(
      lines.join(' · '),
      key: const Key('model-picker-details'),
      role: KitTextRole.secondary,
      tone: KitTextTone.secondary,
    );
  }

  /// A server agent in words: the built-in ones say what they do, others
  /// use the server's description, then their mode.
  String _agentSupporting(CatalogAgent agent) {
    final description = agent.description?.trim();
    if (description != null && description.isNotEmpty) return description;
    return switch (agent.id) {
      'build' => _strings.modelPickerAgentBuild,
      'plan' => _strings.modelPickerAgentPlan,
      _ => [
        if (agent.mode != 'unknown') agent.mode,
        if (agent.model?.isNotEmpty == true) agent.model!,
      ].join(' · '),
    };
  }

  /// The built-in agents by name ("Build", "Plan"); any other agent by the
  /// id its server gave it.
  String _agentTitle(String id) => switch (id) {
    'build' => _strings.modelPickerAgentBuildName,
    'plan' => _strings.modelPickerAgentPlanName,
    _ => id,
  };
}
