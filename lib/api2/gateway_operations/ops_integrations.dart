part of '../gateway_operations.dart';

mixin _Api2IntegrationOps on _Api2Core {
  /// v2 OAuth attempt routes are integration-scoped, but the domain contract
  /// only carries the attempt ID (matrix risk 7). Remember the owning
  /// integration for every attempt this gateway started.
  final Map<String, ({String integrationID, Map<String, dynamic> location})>
  _oauthAttempts = {};
  final Map<String, IntegrationAuthStatus> _oauthTerminalStatuses = {};

  // ---------------- Integrations ----------------

  @override
  Future<List<IntegrationInfo>> listIntegrations() =>
      _guard('Could not load integrations', () async {
        final json = await _transport.getJson('/integration', query: _loc());
        return [
          for (final item in _dataMaps(json))
            if ((item['id'] ?? '').toString().isNotEmpty) _integration(item),
        ];
      });

  IntegrationInfo _integration(Map<String, dynamic> json) {
    final methods = <IntegrationMethodInfo>[];
    if (json['methods'] is List) {
      for (final raw in json['methods'] as List) {
        if (raw is! Map) continue;
        final method = Map<String, dynamic>.from(raw);
        final type = (method['type'] ?? '').toString();
        if (type.isEmpty) continue;
        methods.add(
          IntegrationMethodInfo(
            type: type,
            id: method['id']?.toString(),
            label: (method['label'] ?? type).toString(),
            prompts: method['form'] is List
                ? [
                    for (final field in method['form'] as List)
                      if (field is Map) Map<String, dynamic>.from(field),
                  ]
                : const [],
            environmentNames: method['names'] is List
                ? (method['names'] as List)
                      .map((value) => value.toString())
                      .toList()
                : const [],
          ),
        );
      }
    }
    final connections = <IntegrationConnectionInfo>[];
    if (json['connections'] is List) {
      for (final raw in json['connections'] as List) {
        if (raw is! Map) continue;
        final connection = Map<String, dynamic>.from(raw);
        final type = (connection['type'] ?? '').toString();
        connections.add(
          IntegrationConnectionInfo(
            type: type,
            id: connection['id']?.toString(),
            label: (connection['label'] ?? connection['name'] ?? type)
                .toString(),
          ),
        );
      }
    }
    return IntegrationInfo(
      id: json['id'].toString(),
      name: (json['name'] ?? json['id']).toString(),
      methods: methods,
      connections: connections,
      connectionCount: connections.length,
    );
  }

  @override
  Future<void> renameCredential(
    String id,
    String label,
  ) => _guard('Could not rename the credential', () async {
    final location = _loc();
    final path = _credentialPath(id);
    final normalizedLabel = label.trim();
    if (normalizedLabel.isEmpty ||
        normalizedLabel.runes.length > 128 ||
        label.contains(RegExp(r'[\x00-\x1f\x7f-\x9f]'))) {
      throw const ProductException(
        'Enter a credential label of 1–128 characters without control characters',
      );
    }
    await _transport.patchJson(
      path,
      query: location,
      body: {'label': normalizedLabel},
    );
  });

  @override
  Future<void> activateCredential(String id) =>
      _guard('Could not activate the credential', () async {
        final location = _loc();
        await _transport.postJson(
          '${_credentialPath(id)}/activate',
          query: location,
        );
      });

  @override
  Future<void> removeCredential(String id) =>
      _guard('Could not remove the credential', () async {
        final location = _loc();
        await _transport.deleteJson(_credentialPath(id), query: location);
      });

  static String _credentialPath(String id) {
    if (id.trim().isEmpty) {
      throw const ProductException('Enter a nonempty credential ID');
    }
    return '/credential/${Uri.encodeComponent(id)}';
  }

  @override
  Future<void> connectIntegrationKey(String id, String key, {String? label}) =>
      _guard('Could not connect the integration', () {
        KitRedact.registerKnownSecret(key);
        return _transport.postJson(
          '/integration/${Uri.encodeComponent(id)}/connect/key',
          query: _loc(),
          body: {'key': key, 'label': ?label},
        );
      });

  @override
  Future<void> disconnectIntegration(IntegrationInfo integration) =>
      _guard('Could not disconnect the integration', () async {
        final credentialIDs = integration.credentialIDs;
        if (credentialIDs.isEmpty) {
          throw ProductException(
            integration.hasEnvironmentConnection
                ? 'This integration is connected through server environment '
                      'variables and cannot be disconnected from the app'
                : 'This integration has no stored credential to remove',
          );
        }
        for (final credentialID in credentialIDs) {
          await _transport.deleteJson(
            '/credential/${Uri.encodeComponent(credentialID)}',
          );
        }
      });

  @override
  Future<void> refreshProviderRuntime() => Future.error(
    const ProductException(
      'Provider runtime refresh is unavailable on this server',
    ),
  );

  @override
  Future<IntegrationAuthLaunch> startIntegrationOAuth(
    String id,
    String methodID, {
    Map<String, String> inputs = const {},
    String? label,
  }) => _commandAuthGuard('Could not start the sign-in', () async {
    final location = _loc();
    final json = await _transport.postJson(
      '/integration/${Uri.encodeComponent(id)}/connect/oauth',
      query: location,
      body: {
        'methodID': methodID,
        if (inputs.isNotEmpty) 'answer': inputs,
        'label': ?label,
      },
    );
    final data = _dataMap(json);
    final attemptID = (data['attemptID'] ?? '').toString();
    if (attemptID.isEmpty) {
      throw const ProductException('OpenCode returned no sign-in attempt');
    }
    _oauthAttempts[attemptID] = (integrationID: id, location: location);
    if (_oauthAttempts.length > 32) {
      _oauthAttempts.remove(_oauthAttempts.keys.first);
    }
    _oauthTerminalStatuses.remove(attemptID);
    final time = data['time'];
    return IntegrationAuthLaunch(
      attemptID: attemptID,
      url: (data['url'] ?? '').toString(),
      instructions: (data['instructions'] ?? '').toString(),
      mode: data['mode']?.toString() == 'code'
          ? IntegrationAuthMode.code
          : IntegrationAuthMode.auto,
      expiresAt: time is Map ? (time['expires'] as num?)?.toInt() : null,
    );
  });

  ({String integrationID, Map<String, dynamic> location}) _attempt(
    String attemptID,
  ) {
    final attempt = _oauthAttempts[attemptID];
    if (attempt == null) {
      throw const ProductException(
        'This sign-in attempt must be restored at its original location',
      );
    }
    return attempt;
  }

  @override
  Future<IntegrationAuthStatus> integrationOAuthStatus(String attemptID) =>
      _commandAuthGuard('Could not check the sign-in status', () async {
        if (_oauthTerminalStatuses[attemptID] case final cached?) return cached;
        final attempt = _attempt(attemptID);
        final json = await _transport.getJson(
          '/integration/${Uri.encodeComponent(attempt.integrationID)}'
          '/connect/oauth/${Uri.encodeComponent(attemptID)}',
          query: attempt.location,
        );
        final data = _dataMap(json);
        final status =
            (data['status'] ??
                    (json is Map<String, dynamic> ? json['status'] : null))
                ?.toString();
        final result = IntegrationAuthStatus(
          state: switch (status) {
            'complete' => IntegrationAuthState.complete,
            'failed' => IntegrationAuthState.failed,
            'expired' => IntegrationAuthState.expired,
            'pending' => IntegrationAuthState.pending,
            _ => throw const ProductException(
              'OpenCode returned an invalid sign-in status',
            ),
          },
        );
        if (result.state != IntegrationAuthState.pending) {
          _oauthAttempts.remove(attemptID);
          _oauthTerminalStatuses[attemptID] = result;
          if (_oauthTerminalStatuses.length > 32) {
            _oauthTerminalStatuses.remove(_oauthTerminalStatuses.keys.first);
          }
        }
        return result;
      });

  @override
  Future<void> completeIntegrationOAuth(String attemptID, {String? code}) =>
      _commandAuthGuard('Could not finish the sign-in', () async {
        if (_oauthTerminalStatuses[attemptID]?.state ==
            IntegrationAuthState.complete) {
          return;
        }
        final attempt = _attempt(attemptID);
        await _transport.postJson(
          '/integration/${Uri.encodeComponent(attempt.integrationID)}'
          '/connect/oauth/${Uri.encodeComponent(attemptID)}/complete',
          query: attempt.location,
          body: {'code': ?code},
        );
      });

  @override
  Future<void> cancelIntegrationOAuth(String attemptID) =>
      _commandAuthGuard('Could not cancel the sign-in', () async {
        if (_oauthTerminalStatuses.remove(attemptID) != null) return;
        final attempt = _attempt(attemptID);
        await _transport.deleteJson(
          '/integration/${Uri.encodeComponent(attempt.integrationID)}'
          '/connect/oauth/${Uri.encodeComponent(attemptID)}',
          query: attempt.location,
        );
        _oauthAttempts.remove(attemptID);
      });

  final _commandAttemptLocations = <(String, String), Map<String, dynamic>>{};

  @override
  void restoreIntegrationAuthAttempt({
    required String integrationID,
    required String attemptID,
    required bool command,
    String? directory,
    String? workspace,
  }) {
    _validateCommandAuthID(integrationID);
    _validateCommandAuthID(attemptID);
    final location = <String, dynamic>{
      'location[directory]': ?directory,
      'location[workspace]': ?workspace,
    };
    if (command) {
      _commandAttemptLocations[(integrationID, attemptID)] = location;
      if (_commandAttemptLocations.length > 32) {
        _commandAttemptLocations.remove(_commandAttemptLocations.keys.first);
      }
    } else {
      // Attempt IDs alone are not a source identity. A previous location's
      // terminal cache must never satisfy a restored attempt at another source.
      _oauthTerminalStatuses.remove(attemptID);
      _oauthAttempts[attemptID] = (
        integrationID: integrationID,
        location: location,
      );
      if (_oauthAttempts.length > 32) {
        _oauthAttempts.remove(_oauthAttempts.keys.first);
      }
    }
  }

  // No browser or local command is involved. Rehydrated attempts retain the
  // original location even if this gateway later changes its selected location.
  @override
  Future<IntegrationAuthLaunch> startIntegrationCommand(
    String integrationID,
    String methodID, {
    String? label,
  }) => _commandAuthGuard('Could not start command sign-in', () async {
    final location = _loc();
    final path = _commandAuthPath(integrationID);
    _validateCommandAuthID(methodID);
    final json = await _transport.postJson(
      path,
      query: location,
      body: {'methodID': methodID, 'label': ?label},
    );
    final data = _commandAuthData(json);
    final attemptID = data['attemptID'];
    if (attemptID is! String) {
      throw const ProductException(
        'OpenCode returned an invalid sign-in attempt',
      );
    }
    _validateCommandAuthID(attemptID);
    _commandAttemptLocations[(integrationID, attemptID)] = location;
    if (_commandAttemptLocations.length > 32) {
      _commandAttemptLocations.remove(_commandAttemptLocations.keys.first);
    }
    return IntegrationAuthLaunch(
      attemptID: attemptID,
      url: '',
      instructions: '',
      mode: IntegrationAuthMode.auto,
      expiresAt: _commandAuthExpiry(data),
    );
  });

  @override
  Future<IntegrationAuthStatus> integrationCommandStatus(
    String integrationID,
    String attemptID,
  ) => _commandAuthGuard('Could not check command sign-in status', () async {
    final location =
        _commandAttemptLocations[(integrationID, attemptID)] ?? _loc();
    final json = await _transport.getJson(
      _commandAuthPath(integrationID, attemptID),
      query: location,
    );
    final data = _commandAuthData(json);
    final state = switch (data['status']) {
      'pending' => IntegrationAuthState.pending,
      'complete' => IntegrationAuthState.complete,
      'failed' => IntegrationAuthState.failed,
      'expired' => IntegrationAuthState.expired,
      _ => throw const ProductException(
        'OpenCode returned an invalid command sign-in status',
      ),
    };
    return IntegrationAuthStatus(
      state: state,
      // Provider messages can contain credentials or executable commands.
      message: state == IntegrationAuthState.failed
          ? 'Command sign-in failed'
          : null,
      expiresAt: _commandAuthExpiry(data),
    );
  });

  @override
  Future<void> cancelIntegrationCommand(
    String integrationID,
    String attemptID,
  ) => _commandAuthGuard('Could not cancel command sign-in', () async {
    final location =
        _commandAttemptLocations[(integrationID, attemptID)] ?? _loc();
    await _transport.deleteJson(
      _commandAuthPath(integrationID, attemptID),
      query: location,
    );
    _commandAttemptLocations.remove((integrationID, attemptID));
  });

  static void _validateCommandAuthID(String id) {
    if (id.trim().isEmpty ||
        id == '.' ||
        id == '..' ||
        id.contains(RegExp(r'[\x00-\x1f\x7f-\x9f]'))) {
      throw const ProductException('Enter a valid command sign-in ID');
    }
  }

  static String _commandAuthPath(String integrationID, [String? attemptID]) {
    _validateCommandAuthID(integrationID);
    if (attemptID != null) _validateCommandAuthID(attemptID);
    return '/integration/${Uri.encodeComponent(integrationID)}/connect/command'
        '${attemptID == null ? '' : '/${Uri.encodeComponent(attemptID)}'}';
  }

  static Map<dynamic, dynamic> _commandAuthData(dynamic json) {
    final data = json is Map && json.containsKey('data') ? json['data'] : json;
    if (data is! Map) {
      throw const ProductException(
        'OpenCode returned an invalid command sign-in response',
      );
    }
    return data;
  }

  static int? _commandAuthExpiry(Map<dynamic, dynamic> data) {
    final time = data['time'];
    if (time == null) return null;
    if (time is Map) {
      final expires = time['expires'];
      if (expires == null) return null;
      if (expires is int && expires >= 0) return expires;
    }
    throw const ProductException('OpenCode returned an invalid sign-in expiry');
  }

  static Future<T> _commandAuthGuard<T>(
    String message,
    Future<T> Function() action,
  ) async {
    try {
      return await action();
    } on ProductException {
      rethrow;
    } catch (_) {
      // Do not retain raw transport/provider errors even as a diagnostic cause.
      throw ProductException(message);
    }
  }
}
