part of '../gateway_operations.dart';

mixin _Api2CatalogOps on _Api2Core {
  // ---------------- Catalog ----------------

  @override
  Future<CatalogSnapshot> loadCatalog() =>
      _guard('Could not load models and agents', () async {
        final (providers, models, agents) = await waitForRequests(
          client.providers(),
          client.models(),
          client.agents(),
        );
        return CatalogSnapshot(
          providers: providers.map(mapApi2CatalogProvider).toList(),
          models: models.map(mapApi2CatalogModel).toList(),
          agents: agents.map(mapApi2CatalogAgent).toList(),
        );
      });

  @override
  Future<ChatDefaults> loadChatDefaults() =>
      _guard('Could not load chat defaults', () async {
        final entries = await client.config();
        ModelRef? model;
        String? agent;
        // Entries are lowest→highest priority; the last hit wins.
        for (final entry in entries) {
          final wireModel = entry.info['model']?.toString().trim();
          if (wireModel != null && wireModel.isNotEmpty) {
            final slash = wireModel.indexOf('/');
            if (slash > 0 && slash < wireModel.length - 1) {
              // Model reference strings parse as provider/model[#variant].
              final rest = wireModel.substring(slash + 1);
              final hash = rest.indexOf('#');
              model = ModelRef(
                providerID: wireModel.substring(0, slash),
                modelID: hash > 0 ? rest.substring(0, hash) : rest,
              );
            }
          }
          final wireAgent = entry.info['default_agent']?.toString().trim();
          if (wireAgent != null && wireAgent.isNotEmpty) agent = wireAgent;
        }
        return ChatDefaults(model: model, agent: agent);
      });

  @override
  Future<List<CommandInfo>> listCommands() =>
      _guard('Could not load commands', () async {
        final commands = await client.commands();
        return [
          for (final command in commands)
            CommandInfo(
              name: command.name,
              description: command.description,
              agent: null,
              subtask: false,
            ),
        ];
      });

  @override
  Future<List<SkillInfo>> listSkills() =>
      _guard('Could not load skills', () async {
        final skills = await client.skills();
        return [
          for (final skill in skills)
            SkillInfo(
              id: skill.id,
              name: skill.name ?? skill.id,
              description: skill.description,
              location: skill.location ?? '',
              content: skill.content ?? '',
              slashCommand: skill.slash,
            ),
        ];
      });

  @override
  Future<List<ReferenceInfo>> listReferences() =>
      _guard('Could not load references', () async {
        final json = await _transport.getJson('/reference', query: _loc());
        return [
          for (final item in _dataMaps(json))
            if (item['hidden'] != true &&
                (item['name'] ?? '').toString().isNotEmpty)
              ReferenceInfo(
                name: item['name'].toString(),
                path: (item['path'] ?? item['location'] ?? '').toString(),
                description: item['description']?.toString(),
              ),
        ];
      });

  // ---------------- MCP ----------------

  @override
  Future<List<McpServerInfo>> listMcpServers() => _guard(
    'Could not load MCP servers',
    () async {
      final json = await _transport.getJson('/mcp', query: _loc());
      return [
        for (final item in _dataMaps(json))
          if ((item['name'] ?? '').toString().isNotEmpty)
            McpServerInfo(
              name: item['name'].toString(),
              status: item['status'] is Map
                  ? ((item['status'] as Map)['status'] ?? 'unknown').toString()
                  : (item['status'] ?? 'unknown').toString(),
              error: item['status'] is Map
                  ? (item['status'] as Map)['error']?.toString()
                  : null,
            ),
      ];
    },
  );

  @override
  Future<List<McpResourceInfo>> listMcpResources() =>
      _guard('Could not load MCP resources', () async {
        final json = await _transport.getJson('/mcp/resource', query: _loc());
        final data = _dataMap(json);
        final resources = data['resources'];
        if (resources is! List) return const [];
        return [
          for (final item in resources)
            if (item is Map && (item['name'] ?? '').toString().isNotEmpty)
              McpResourceInfo(
                name: item['name'].toString(),
                server: (item['server'] ?? '').toString(),
                uri: (item['uri'] ?? '').toString(),
                description: item['description']?.toString(),
                mimeType: item['mimeType']?.toString(),
              ),
        ];
      });

  @override
  Future<void> connectMcp(String name) => _guard(
    'Could not connect the MCP server',
    () => _transport.postJson(
      '/mcp/${Uri.encodeComponent(name)}/connect',
      query: _loc(),
    ),
  );

  @override
  Future<void> disconnectMcp(String name) => _guard(
    'Could not disconnect the MCP server',
    () => _transport.postJson(
      '/mcp/${Uri.encodeComponent(name)}/disconnect',
      query: _loc(),
    ),
  );

  @override
  Future<void> removeMcpServer(String name) =>
      _guard('Could not remove the MCP server', () async {
        final location = _loc();
        await _transport.deleteJson(
          '/mcp/${Uri.encodeComponent(name)}',
          query: location,
        );
      });

  @override
  Future<McpAuthLaunch> startMcpAuthentication(String name) => Future.error(
    const ProductException('MCP authentication is unavailable on this server'),
  );

  @override
  Future<void> addMcpServer(
    McpServerDraft draft, {
    required McpConfigScope scope,
  }) => _guard('Could not add the MCP server', () async {
    if (scope != McpConfigScope.runtimeLocation) {
      throw const ProductException(
        'This server supports MCP additions for the current location until restart',
      );
    }
    final config = draft.toConfigJson();
    final location = _loc();
    // PUT replaces existing names. The Add form must not silently overwrite
    // one, and both requests must retain the originally selected location.
    final existing = _dataMaps(
      await _transport.getJson('/mcp', query: location),
    );
    if (existing.any((item) => item['name'] == draft.normalizedName)) {
      throw ProductException(
        'An MCP server named "${draft.normalizedName}" already exists in this location',
      );
    }
    if (draft.timeoutMs case final timeout?) {
      config['timeout'] = {
        'startup': timeout,
        'catalog': timeout,
        'execution': timeout,
      };
    }
    await _transport.putJson(
      '/mcp/${Uri.encodeComponent(draft.normalizedName)}',
      query: location,
      body: {'config': config},
    );
  });
}
