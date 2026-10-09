part of '../product_repository.dart';

mixin _SdkCatalogOps on _SdkCore {
  @override
  Future<ChatDefaults> loadChatDefaults() =>
      _guard('Could not load chat defaults', () async {
        final response = await _client.getConfigApi().configGet(
          directory: _directory,
          workspace: _workspace,
        );
        final config = response.data;
        final wireModel = config?.model?.trim();
        ModelRef? model;
        if (wireModel?.isNotEmpty == true) {
          final slash = wireModel!.indexOf('/');
          if (slash > 0 && slash < wireModel.length - 1) {
            model = ModelRef(
              providerID: wireModel.substring(0, slash),
              modelID: wireModel.substring(slash + 1),
            );
          }
        }
        final agent = config?.defaultAgent?.trim();
        return ChatDefaults(
          model: model,
          agent: agent?.isNotEmpty == true ? agent : null,
        );
      });

  @override
  Future<CatalogSnapshot> loadCatalog() => _guard(
    'Could not load models and agents',
    () async {
      final providersRequest = _client.getProvidersApi().v2ProviderList(
        locationLeftSquareBracketDirectoryRightSquareBracket: _directory,
        locationLeftSquareBracketWorkspaceRightSquareBracket: _workspace,
      );
      final modelsRequest = _client.getModelsApi().v2ModelList(
        locationLeftSquareBracketDirectoryRightSquareBracket: _directory,
        locationLeftSquareBracketWorkspaceRightSquareBracket: _workspace,
      );
      final agentsRequest = _client.getOpencodeHttpApiApi().v2AgentList(
        locationLeftSquareBracketDirectoryRightSquareBracket: _directory,
        locationLeftSquareBracketWorkspaceRightSquareBracket: _workspace,
      );
      final (providerResponse, modelResponse, agentResponse) =
          await waitForRequests(providersRequest, modelsRequest, agentsRequest);
      final providers = (providerResponse.data?.data ?? const [])
          .map((provider) {
            return CatalogProvider(
              id: provider.id,
              name: provider.name,
              enabled: provider.disabled != true,
              integrationID: provider.integrationID,
            );
          })
          .where((provider) => provider.id.isNotEmpty)
          .toList();
      final models = (modelResponse.data?.data ?? const [])
          .map((model) {
            final variants = model.variants
                .where((variant) => variant.id.isNotEmpty)
                .map(
                  (variant) => CatalogVariant(
                    id: variant.id,
                    options: _stringMap(variant.body),
                  ),
                )
                .toList();
            return CatalogModel(
              id: model.id,
              providerID: model.providerID,
              name: model.name,
              family: model.family,
              enabled: model.enabled,
              status: model.status.value.toString(),
              contextLimit: model.limit.context,
              outputLimit: model.limit.output,
              reasoning: false,
              attachments: model.capabilities.input.any(
                (input) => input != 'text',
              ),
              tools: model.capabilities.tools,
              variants: variants,
              cost: _catalogModelCost(model.cost),
              released: _catalogReleased(model.time.released),
            );
          })
          .where((model) => model.id.isNotEmpty)
          .toList();
      final agents = (agentResponse.data?.data ?? const [])
          .map((agent) {
            final color = agent.color?.value;
            final model = agent.model;
            return CatalogAgent(
              id: agent.id,
              mode: agent.mode.value.toString(),
              description: agent.description,
              hidden: agent.hidden,
              maxSteps: agent.steps,
              color: color is String && color.isNotEmpty ? color : null,
              model: model == null ? null : '${model.providerID}/${model.id}',
            );
          })
          .where((agent) => agent.id.isNotEmpty)
          .toList();
      return CatalogSnapshot(
        providers: providers,
        models: models,
        agents: agents,
      );
    },
  );

  /// v2 model prices are already USD per million tokens; take the base
  /// (untiered) entry, or the first one when every entry is a context tier.
  static ModelCost? _catalogModelCost(List<sdk.ModelCost> costs) {
    if (costs.isEmpty) return null;
    final base = costs.firstWhere(
      (cost) => cost.tiers == null || cost.tiers!.isEmpty,
      orElse: () => costs.first,
    );
    return ModelCost(
      inputPerMillion: base.input.toDouble(),
      outputPerMillion: base.output.toDouble(),
      cacheReadPerMillion: base.cache.read.toDouble(),
      cacheWritePerMillion: base.cache.write.toDouble(),
    );
  }

  @override
  Future<BackgroundWorkSupport> loadBackgroundWorkSupport() async =>
      (await loadExperimentalCapabilities()).backgroundSubagents
      ? BackgroundWorkSupport.subagents
      : BackgroundWorkSupport.unavailable;

  @override
  Future<BackgroundWorkResult> backgroundSession(String sessionID) =>
      _guard('Could not background subagents', () async {
        final response = await _client
            .getExperimentalApi()
            .experimentalSessionBackground(
              sessionID: sessionID,
              directory: _directory,
              workspace: _workspace,
            );
        return response.data == true
            ? BackgroundWorkResult.promoted
            : BackgroundWorkResult.unchanged;
      });

  static DateTime? _catalogReleased(num released) => released <= 0
      ? null
      : DateTime.fromMillisecondsSinceEpoch(released.toInt());

  @override
  Future<ExperimentalServerCapabilities> loadExperimentalCapabilities() =>
      _guard('Could not load server capabilities', () async {
        final response = await _client
            .getExperimentalApi()
            .experimentalCapabilitiesGet(
              directory: _directory,
              workspace: _workspace,
            );
        final capabilities = response.data;
        if (capabilities == null) {
          throw const ProductException(
            'OpenCode returned no capability information',
          );
        }
        return ExperimentalServerCapabilities(
          backgroundSubagents: capabilities.backgroundSubagents,
        );
      });

  @override
  Future<List<String>> listCodingToolIDs() =>
      _guard('Could not load registered tools', () async {
        final response = await _client.getExperimentalApi().toolIds(
          directory: _directory,
          workspace: _workspace,
        );
        final seen = <String>{};
        return [
          for (final rawID in response.data ?? const <String>[])
            if (rawID.trim().isNotEmpty && seen.add(rawID.trim())) rawID.trim(),
        ];
      });

  @override
  Future<List<CodingToolInfo>> listCodingTools({
    required String providerID,
    required String modelID,
  }) => _guard('Could not load model tools', () async {
    final provider = providerID.trim();
    final model = modelID.trim();
    if (provider.isEmpty || model.isEmpty) {
      throw const ProductException('Choose a valid provider and model');
    }
    final response = await _client.getExperimentalApi().toolList(
      provider: provider,
      model: model,
      directory: _directory,
      workspace: _workspace,
    );
    return [
      for (final tool in response.data ?? const <sdk.ToolListItem>[])
        if (tool.id.trim().isNotEmpty)
          CodingToolInfo(
            id: tool.id.trim(),
            description: tool.description.trim(),
            parameters: tool.parameters,
          ),
    ];
  });

  @override
  Future<List<McpServerInfo>> listMcpServers() =>
      _guard('Could not load MCP servers', () async {
        final response = await _client.getMcpApi().mcpStatus(
          directory: _directory,
          workspace: _workspace,
        );
        return (response.data ?? const {}).entries
            .map((entry) => _mcpServerInfo(entry.key, entry.value))
            .toList();
      });

  @override
  Future<List<McpResourceInfo>> listMcpResources() =>
      _guard('Could not load MCP resources', () async {
        final response = await _client
            .getExperimentalApi()
            .experimentalResourceList(
              directory: _directory,
              workspace: _workspace,
            );
        return (response.data ?? const {}).values
            .map(
              (resource) => McpResourceInfo(
                name: resource.name,
                server: resource.client,
                uri: resource.uri,
                description: resource.description,
                mimeType: resource.mimeType,
              ),
            )
            .toList();
      });

  @override
  Future<void> connectMcp(String name) => _guard(
    'Could not connect the MCP server',
    () async => _client.getMcpApi().mcpConnect(
      name: name,
      directory: _directory,
      workspace: _workspace,
    ),
  );

  @override
  Future<void> disconnectMcp(String name) => _guard(
    'Could not disconnect the MCP server',
    () async => _client.getMcpApi().mcpDisconnect(
      name: name,
      directory: _directory,
      workspace: _workspace,
    ),
  );

  @override
  Future<McpAuthLaunch> startMcpAuthentication(String name) =>
      _guard('Could not start authentication', () async {
        final response = await _client.getMcpApi().mcpAuthStart(
          name: name,
          directory: _directory,
          workspace: _workspace,
        );
        final data = response.data;
        final url = Uri.tryParse(data?.authorizationUrl.trim() ?? '');
        final state = data?.oauthState.trim() ?? '';
        if (url == null || url.toString().isEmpty || state.isEmpty) {
          throw const ProductException('No authorization link was returned');
        }
        return McpAuthLaunch(authorizationUrl: url, oauthState: state);
      });

  @override
  Future<McpServerInfo> completeMcpAuthentication(String name, String code) =>
      _guard('Could not complete authentication', () async {
        final response = await _client.getMcpApi().mcpAuthCallback(
          name: name,
          directory: _directory,
          workspace: _workspace,
          mcpAuthCallbackRequest: sdk.McpAuthCallbackRequest(code: code),
        );
        final status = response.data;
        if (status == null) {
          throw const ProductException(
            'OpenCode did not return the MCP connection status',
          );
        }
        return _mcpServerInfo(name, status);
      });

  @override
  Future<void> cancelMcpAuthentication(String name) => _guard(
    'Could not cancel authentication',
    () => _client.getMcpApi().mcpAuthRemove(
      name: name,
      directory: _directory,
      workspace: _workspace,
    ),
  );

  @override
  Future<void> addMcpServer(
    McpServerDraft draft, {
    required McpConfigScope scope,
  }) => _guard('Could not save the MCP server', () async {
    final name = draft.normalizedName;
    final config = draft.toConfigJson();
    final patch = sdk.Config(mcp: {name: sdk.OpencodeSdkRawUnion013(config)});

    switch (scope) {
      case McpConfigScope.runtimeLocation:
        final directory = _directory;
        final workspace = _workspace;
        final revision = _locationRevision;
        final current = await _client.getMcpApi().mcpStatus(
          directory: directory,
          workspace: workspace,
        );
        final inventory = current.data;
        if (inventory == null) {
          throw const ProductException(
            'Could not verify the existing MCP servers',
          );
        }
        if (_locationRevision != revision) {
          throw const ProductException(
            'The connection changed. Reopen the connector card.',
          );
        }
        if (inventory.containsKey(name)) {
          throw ProductException(
            'An MCP server named "$name" already exists at this location',
          );
        }
        await _client.getMcpApi().mcpAdd(
          directory: directory,
          workspace: workspace,
          mcpAddRequest: sdk.McpAddRequest(
            name: name,
            config: sdk.OpencodeSdkRawUnion056(config),
          ),
        );
      case McpConfigScope.project:
        if (_directory?.trim().isNotEmpty != true) {
          throw const ProductException(
            'Select a project before adding a project MCP server',
          );
        }
        final current = await _client.getConfigApi().configGet(
          directory: _directory,
          workspace: _workspace,
        );
        _requireUniqueMcpName(current.data, name, 'current project');
        await _client.getConfigApi().configUpdate(
          directory: _directory,
          workspace: _workspace,
          config: patch,
        );
      case McpConfigScope.global:
        final current = await _client.getGlobalApi().globalConfigGet();
        _requireUniqueMcpName(current.data, name, 'global configuration');
        await _client.getGlobalApi().globalConfigUpdate(config: patch);
    }
  });

  static void _requireUniqueMcpName(
    sdk.Config? config,
    String name,
    String scope,
  ) {
    if (config == null) {
      throw ProductException('Could not verify the existing $scope');
    }
    if (config.mcp?.containsKey(name) == true) {
      throw ProductException(
        'An MCP server named "$name" already exists in the $scope',
      );
    }
  }
}
