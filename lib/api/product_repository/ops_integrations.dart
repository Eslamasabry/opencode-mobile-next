part of '../product_repository.dart';

mixin _SdkIntegrationOps on _SdkCore {
  @override
  Future<List<IntegrationInfo>> listIntegrations() => _guard(
    'Could not load integrations',
    () async {
      final response = await _client.getIntegrationsApi().v2IntegrationList(
        locationLeftSquareBracketDirectoryRightSquareBracket: _directory,
        locationLeftSquareBracketWorkspaceRightSquareBracket: _workspace,
      );
      final authMethods = await _loadProviderAuthMethods();
      final providerResponse = await _client.getProviderApi().providerList(
        directory: _directory,
        workspace: _workspace,
      );
      final connectedProviderIDs =
          providerResponse.data?.connected.toSet() ?? const <String>{};
      final integrations = (response.data?.data ?? const []).map((integration) {
        final storedConnections = integration.connections
            .map((connection) {
              final value = connection.objectValue ?? const <String, dynamic>{};
              final type = (value['type'] ?? 'unknown').toString();
              return IntegrationConnectionInfo(
                type: type,
                id: value['id']?.toString(),
                label: switch (type) {
                  'credential' =>
                    (value['label'] ?? 'Stored credential').toString(),
                  'env' => (value['name'] ?? 'Server environment').toString(),
                  _ => 'Server-managed connection',
                },
              );
            })
            .toList(growable: false);
        final connected = connectedProviderIDs.contains(integration.id);
        final connections = <IntegrationConnectionInfo>[
          ...storedConnections,
          if (connected && storedConnections.isEmpty)
            const IntegrationConnectionInfo(
              type: 'runtime',
              label: 'Connected to OpenCode',
            ),
        ];
        final legacyMethods = authMethods[integration.id] ?? const [];
        final methods = <IntegrationMethodInfo>[];
        final matchedLegacyMethods = <int>{};
        for (final method in integration.methods) {
          final value = method.objectValue ?? const <String, dynamic>{};
          final type = (value['type'] ?? 'unknown').toString();
          final label = (value['label'] ?? _methodLabel(type)).toString();
          final legacyIndex = type == 'oauth'
              ? _legacyOAuthMethodIndex(legacyMethods, label)
              : null;
          // OpenCode 1 chat reads the legacy provider auth store. Do not offer
          // a v2-only OAuth method that cannot populate that store.
          if (type == 'oauth' && legacyIndex == null) continue;
          if (legacyIndex != null) matchedLegacyMethods.add(legacyIndex);
          final names = value['names'];
          final prompts = value['prompts'];
          methods.add(
            IntegrationMethodInfo(
              type: type,
              id: type == 'oauth'
                  ? legacyIndex.toString()
                  : value['id']?.toString(),
              label: label,
              prompts: prompts is List
                  ? prompts
                        .whereType<Map>()
                        .map((item) => Map<String, dynamic>.from(item))
                        .toList()
                  : const [],
              environmentNames: names is List
                  ? names.map((name) => name.toString()).toList()
                  : const [],
            ),
          );
        }
        for (var index = 0; index < legacyMethods.length; index++) {
          final method = legacyMethods[index];
          if (method.type != sdk.ProviderAuthMethodTypeEnum.oauth ||
              matchedLegacyMethods.contains(index)) {
            continue;
          }
          methods.add(_legacyOAuthMethod(method, index));
        }
        return IntegrationInfo(
          id: integration.id,
          name: integration.name,
          methods: methods,
          connections: connections,
          connectionCount: connected ? connections.length : 0,
        );
      }).toList();
      final listedIDs = integrations
          .map((integration) => integration.id)
          .toSet();
      final providerNames = {
        for (final provider in providerResponse.data?.all ?? const [])
          provider.id: provider.name,
      };
      for (final entry in authMethods.entries) {
        if (listedIDs.contains(entry.key)) continue;
        final methods = <IntegrationMethodInfo>[];
        for (var index = 0; index < entry.value.length; index++) {
          final method = entry.value[index];
          if (method.type == sdk.ProviderAuthMethodTypeEnum.oauth) {
            methods.add(_legacyOAuthMethod(method, index));
          }
        }
        if (methods.isEmpty) continue;
        final connected = connectedProviderIDs.contains(entry.key);
        final connections = <IntegrationConnectionInfo>[
          if (connected)
            const IntegrationConnectionInfo(
              type: 'runtime',
              label: 'Connected to OpenCode',
            ),
        ];
        integrations.add(
          IntegrationInfo(
            id: entry.key,
            name: providerNames[entry.key] ?? entry.key,
            methods: methods,
            connections: connections,
            connectionCount: connections.length,
          ),
        );
      }
      return integrations;
    },
  );

  static IntegrationMethodInfo _legacyOAuthMethod(
    sdk.ProviderAuthMethod method,
    int index,
  ) => IntegrationMethodInfo(
    type: 'oauth',
    id: index.toString(),
    label: method.label,
    prompts:
        method.prompts
            ?.map((prompt) => prompt.objectValue)
            .whereType<Map<String, dynamic>>()
            .toList(growable: false) ??
        const [],
  );

  static int? _legacyOAuthMethodIndex(
    List<sdk.ProviderAuthMethod> methods,
    String label,
  ) {
    final normalizedLabel = label.trim().toLowerCase();
    for (var index = 0; index < methods.length; index++) {
      final method = methods[index];
      if (method.type == sdk.ProviderAuthMethodTypeEnum.oauth &&
          method.label.trim().toLowerCase() == normalizedLabel) {
        return index;
      }
    }
    return null;
  }

  Future<Map<String, List<sdk.ProviderAuthMethod>>>
  _loadProviderAuthMethods() async {
    // The generated nested Map<String, List<ProviderAuthMethod>> decoder in
    // the current SDK casts each method as a list. Decode this one endpoint at
    // the boundary until the generator can represent nested collection maps.
    final response = await _client.dio.get<Object>(
      '/provider/auth',
      queryParameters: {
        if (_directory != null) 'directory': _directory,
        if (_workspace != null) 'workspace': _workspace,
      },
    );
    final raw = response.data;
    if (raw is! Map) {
      throw StateError('OpenCode returned invalid provider auth methods');
    }
    final methods = <String, List<sdk.ProviderAuthMethod>>{};
    for (final entry in raw.entries) {
      final values = entry.value;
      if (values is! List) {
        throw StateError('OpenCode returned invalid provider auth methods');
      }
      methods[entry.key.toString()] = values
          .map((value) {
            if (value is! Map) {
              throw StateError(
                'OpenCode returned an invalid provider auth method',
              );
            }
            return sdk.ProviderAuthMethod.fromJson(
              Map<String, dynamic>.from(value),
            );
          })
          .toList(growable: false);
    }
    return methods;
  }

  @override
  Future<void> connectIntegrationKey(String id, String key, {String? label}) =>
      _guard('Could not connect the provider', () async {
        KitRedact.registerKnownSecret(key);
        await _client.getIntegrationsApi().v2IntegrationConnectKey(
          integrationID: id,
          locationLeftSquareBracketDirectoryRightSquareBracket: _directory,
          locationLeftSquareBracketWorkspaceRightSquareBracket: _workspace,
          v2IntegrationConnectKeyRequest: sdk.V2IntegrationConnectKeyRequest(
            key: key,
            label: label,
          ),
        );
        // OpenCode 1.18.x keeps the new integration credential store and the
        // provider runtime's legacy auth store separate. Chat execution still
        // reads the latter, so keep both surfaces synchronized until upstream
        // unifies them. Never log or otherwise expose [key].
        await _client.getControlApi().authSet(
          providerID: id,
          auth: sdk.Auth({'type': 'api', 'key': key}),
        );
        try {
          await refreshProviderRuntime();
        } on ProviderRuntimeBusyException {
          // The key is saved; the app reloads providers once replies finish.
        }
      });

  @override
  Future<void> disconnectIntegration(IntegrationInfo integration) =>
      _guard('Could not disconnect the provider', () async {
        final credentialIDs = integration.credentialIDs.toSet().toList();

        // OpenCode 1.18.x can retain the same key in its legacy provider auth
        // store and its v2 integration credential store. Remove the legacy
        // copy first: if that write fails, the visible v2 connection remains
        // untouched and the user can safely retry from this row.
        try {
          final response = await _client.getControlApi().authRemove(
            providerID: integration.id,
          );
          if (response.data != true) {
            throw StateError('The server did not confirm auth removal');
          }
        } catch (error) {
          throw ProductException(
            'OpenCode could not remove the provider runtime credential. '
            'Nothing else was removed; try again.',
            cause: error,
          );
        }

        Object? credentialFailure;
        for (final credentialID in credentialIDs) {
          try {
            await _client.getOpencodeHttpApiApi().v2CredentialRemove(
              credentialID: credentialID,
              locationLeftSquareBracketDirectoryRightSquareBracket: _directory,
              locationLeftSquareBracketWorkspaceRightSquareBracket: _workspace,
            );
          } catch (error) {
            credentialFailure ??= error;
          }
        }

        Object? refreshFailure;
        try {
          await refreshProviderRuntime();
        } on ProviderRuntimeBusyException {
          // Removed from the store; the runtime drops it once replies finish.
        } catch (error) {
          refreshFailure = error;
        }

        if (credentialFailure != null) {
          throw ProductException(
            'The runtime credential was removed, but OpenCode could not '
            'remove every stored connection. The connection remains visible '
            'so you can retry.',
            cause: credentialFailure,
          );
        }
        if (refreshFailure != null) {
          throw ProductException(
            'The provider credentials were removed, but OpenCode could not '
            'refresh its model runtime. Reconnect or restart the server.',
            cause: refreshFailure,
          );
        }
      });

  @override
  Future<void> refreshProviderRuntime() =>
      _guard('Could not refresh the provider runtime', () async {
        // Disposing an instance aborts every reply running in it, and the
        // transcript then says the person stopped it. Count what is running
        // in both locations this refresh disposes first and refuse while any
        // reply runs; an unreadable status also refuses, since losing a reply
        // costs more than a provider that loads later.
        final running = await _runningSessionCount();
        if (running > 0) throw ProviderRuntimeBusyException(running);
        // Provider inventories are cached per server instance. Match
        // OpenCode's own compatibility client: invalidate the selected
        // location and the server-default location so newly authenticated or
        // pre-existing provider credentials are immediately available.
        await _client.getInstanceApi().instanceDispose(
          directory: _directory,
          workspace: _workspace,
        );
        await _client.getInstanceApi().instanceDispose();
      });

  /// Sessions that are not idle in the selected location and in the
  /// server-default location: the two instances a runtime refresh disposes.
  Future<int> _runningSessionCount() async {
    final running = <String>{};
    for (final location in <Map<String, String>>[
      {'directory': ?_directory, 'workspace': ?_workspace},
      const {},
    ]) {
      final response = await _client.dio.get<Object>(
        '/session/status',
        queryParameters: location,
      );
      final data = response.data;
      if (data is! Map) {
        throw const ProductException(
          'Could not read which replies are running',
        );
      }
      data.forEach((id, status) {
        final type = status is Map ? status['type']?.toString() : null;
        if (type != null && type != 'idle') running.add(id.toString());
      });
    }
    return running.length;
  }

  @override
  Future<IntegrationAuthLaunch> startIntegrationOAuth(
    String id,
    String methodID, {
    Map<String, String> inputs = const {},
    String? label,
  }) => _guard('Could not start provider authentication', () async {
    final methodIndex = int.tryParse(methodID);
    final methods = (await _loadProviderAuthMethods())[id];
    if (methodIndex == null ||
        methods == null ||
        methodIndex < 0 ||
        methodIndex >= methods.length ||
        methods[methodIndex].type != sdk.ProviderAuthMethodTypeEnum.oauth) {
      throw const ProductException(
        'OpenCode did not return a matching provider authentication method. '
        'Refresh providers and try again.',
      );
    }
    final response = await _client.getProviderApi().providerOauthAuthorize(
      providerID: id,
      directory: _directory,
      workspace: _workspace,
      providerOauthAuthorizeRequest: sdk.ProviderOauthAuthorizeRequest(
        method: methodIndex,
        inputs: inputs.isEmpty ? null : inputs,
      ),
    );
    final authorization = response.data;
    if (authorization == null || authorization.url.isEmpty) {
      throw const ProductException('No authorization link was returned');
    }
    final mode =
        authorization.method == sdk.ProviderAuthAuthorizationMethodEnum.code
        ? IntegrationAuthMode.code
        : IntegrationAuthMode.auto;
    final attempt = _LegacyProviderOAuthAttempt(
      providerID: id,
      methodIndex: methodIndex,
      mode: mode,
    );
    final attemptID = _providerOAuthAttemptID(attempt);
    _providerOAuthAttempts[attemptID] = attempt;
    return IntegrationAuthLaunch(
      attemptID: attemptID,
      url: authorization.url,
      instructions: authorization.instructions,
      mode: mode,
    );
  });

  @override
  Future<IntegrationAuthStatus> integrationOAuthStatus(String attemptID) =>
      _guard('Could not check provider authentication', () async {
        final attempt = _providerOAuthAttempt(attemptID);
        if (attempt == null) {
          return const IntegrationAuthStatus(
            state: IntegrationAuthState.expired,
            message: 'This authentication attempt is no longer active.',
          );
        }
        if (attempt.status.state != IntegrationAuthState.pending) {
          _providerOAuthAttempts.remove(attemptID);
          return attempt.status;
        }
        if (attempt.mode == IntegrationAuthMode.code) {
          return attempt.status;
        }
        final activeCompletion = attempt.completion;
        if (activeCompletion != null) return activeCompletion;
        final completion = _completeProviderOAuth(attempt);
        attempt.completion = completion;
        try {
          final status = await completion;
          _providerOAuthAttempts.remove(attemptID);
          return status;
        } catch (_) {
          if (identical(attempt.completion, completion)) {
            attempt.completion = null;
          }
          rethrow;
        }
      });

  @override
  Future<void> completeIntegrationOAuth(String attemptID, {String? code}) =>
      _guard('Could not complete provider authentication', () async {
        final attempt = _providerOAuthAttempt(attemptID);
        if (attempt == null) {
          throw const ProductException(
            'This authentication attempt is no longer active.',
          );
        }
        await _completeProviderOAuth(attempt, code: _oauthCode(code));
      });

  @override
  Future<void> cancelIntegrationOAuth(String attemptID) async {
    _providerOAuthAttempts.remove(attemptID);
  }

  String _providerOAuthAttemptID(_LegacyProviderOAuthAttempt attempt) {
    final payload = base64Url
        .encode(
          utf8.encode(
            jsonEncode({
              'provider': attempt.providerID,
              'method': attempt.methodIndex,
              'mode': attempt.mode.name,
              'server': _client.dio.options.baseUrl,
              'nonce': ++_providerOAuthAttemptSerial,
            }),
          ),
        )
        .replaceAll('=', '');
    return '$_providerOAuthAttemptPrefix$payload';
  }

  _LegacyProviderOAuthAttempt? _providerOAuthAttempt(String attemptID) {
    final active = _providerOAuthAttempts[attemptID];
    if (active != null || !attemptID.startsWith(_providerOAuthAttemptPrefix)) {
      return active;
    }
    try {
      var encoded = attemptID.substring(_providerOAuthAttemptPrefix.length);
      encoded += '=' * ((4 - encoded.length % 4) % 4);
      final value = jsonDecode(utf8.decode(base64Url.decode(encoded)));
      if (value is! Map || value['server'] != _client.dio.options.baseUrl) {
        return null;
      }
      final providerID = value['provider'];
      final methodIndex = value['method'];
      final mode = switch (value['mode']) {
        'auto' => IntegrationAuthMode.auto,
        'code' => IntegrationAuthMode.code,
        _ => null,
      };
      if (providerID is! String ||
          providerID.isEmpty ||
          methodIndex is! num ||
          mode == null) {
        return null;
      }
      final restored = _LegacyProviderOAuthAttempt(
        providerID: providerID,
        methodIndex: methodIndex.toInt(),
        mode: mode,
      );
      _providerOAuthAttempts[attemptID] = restored;
      return restored;
    } catch (_) {
      return null;
    }
  }

  Future<IntegrationAuthStatus> _completeProviderOAuth(
    _LegacyProviderOAuthAttempt attempt, {
    String? code,
  }) async {
    final response = await _client.getProviderApi().providerOauthCallback(
      providerID: attempt.providerID,
      directory: _directory,
      workspace: _workspace,
      providerOauthCallbackRequest: sdk.ProviderOauthCallbackRequest(
        method: attempt.methodIndex,
        code: code,
      ),
    );
    if (response.data != true) {
      throw const ProductException(
        'OpenCode did not confirm provider authentication.',
      );
    }
    return attempt.status = const IntegrationAuthStatus(
      state: IntegrationAuthState.complete,
    );
  }

  static String? _oauthCode(String? value) {
    final code = value?.trim();
    if (code == null || code.isEmpty) return null;
    final parsed = Uri.tryParse(code)?.queryParameters['code']?.trim();
    return parsed?.isNotEmpty == true ? parsed : code;
  }
}
