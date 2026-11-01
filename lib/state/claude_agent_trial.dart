/// The Claude Agent trial's state for the connected server
/// (lib/domain/claude_agent_trial.dart). Owned by [ConnectionController]
/// as [ConnectionController.claudeAgentTrial]; the model picker reads it
/// and calls [ClaudeAgentTrial.start] / [ClaudeAgentTrial.checkAgain].
///
/// Today's Claude Code through Paseo is untouched: this only adds OpenCode's
/// own provider on this phone's server, next to it.
library;

import 'dart:async';

import 'package:flutter/foundation.dart';

import '../builtin/builtin_linux.dart';
import '../builtin/claude_agent_link.dart';
import '../domain/claude_agent_trial.dart';
import '../domain/server_gateway.dart';
import 'connection.dart';
import 'profiles.dart';

/// What the trial needs from the connection. [ConnectionController] in the
/// app ([ClaudeAgentTrial.forConnection]); tests pass their own.
abstract interface class ClaudeAgentTrialHost {
  /// This phone's own OpenCode server (the in-app one), not a computer.
  bool get onPhoneServer;

  /// The connected server's release ("2.2.5").
  String? get serverVersion;

  /// The agent home Claude Code signs in to on this phone
  /// (`ProfileStore.phoneAgentOwnerId`); null with no connected profile.
  String? get agentOwnerId;

  /// Changes whenever the connection moves to another server or profile.
  String? get scope;

  ServerOperationsGateway? get operations;

  /// The loaded model list has Claude Agent's models; null before the
  /// list has loaded.
  bool? get hasClaudeAgentModels;

  Future<void> refreshCatalog();
}

class ClaudeAgentTrial extends ChangeNotifier {
  ClaudeAgentTrial(
    this._host, {
    ClaudeAgentLinkRunner? link,
    this.pollInterval = const Duration(milliseconds: 1500),
    this.pollAttempts = 30,
    this.lateLoadDelay = const Duration(seconds: 3),
  }) : _link = link ?? ClaudeAgentLink();

  ClaudeAgentTrial.forConnection(
    ConnectionController connection, {
    ClaudeAgentLinkRunner? link,
  }) : this(_ConnectionTrialHost(connection), link: link);

  final ClaudeAgentTrialHost _host;
  final ClaudeAgentLinkRunner _link;
  final Duration pollInterval;
  final int pollAttempts;

  /// OpenCode loads its built-in providers for a project on the first read
  /// there, so the first list after a server start can miss Claude Agent
  /// (seen on the emulator, 2026-11-01). One more read after this delay,
  /// once per server and profile.
  final Duration lateLoadDelay;
  String? _lateLoadScope;

  IntegrationInfo? _integration;
  String? _seenScope;
  bool _busy = false;
  ClaudeAgentTrialState? _outcome;
  int _generation = 0;

  /// The flag behind [ServerCapabilities.claudeAgent]: this phone's OpenCode
  /// is 2.2.3 or later and offers the provider.
  bool get capable =>
      _host.onPhoneServer &&
      serverVersionAtLeast(_host.serverVersion, claudeAgentMinimumServer) &&
      _integration != null &&
      _seenScope == _host.scope;

  ClaudeAgentTrialState get state {
    if (!capable) return ClaudeAgentTrialState.unavailable;
    if (_busy) return ClaudeAgentTrialState.preparing;
    final outcome = _outcome;
    if (outcome != null) return outcome;
    final connections = _integration!.connections;
    if (connections.isEmpty) return ClaudeAgentTrialState.available;
    // OpenCode lists the connection in use first, and reports its check
    // there.
    if (connections.first.status == 'needs_auth') {
      return ClaudeAgentTrialState.needsSignIn;
    }
    // Connected and no problem reported: ready unless the loaded list
    // proves Claude Agent has no models here.
    return _host.hasClaudeAgentModels == false
        ? ClaudeAgentTrialState.failed
        : ClaudeAgentTrialState.ready;
  }

  /// Takes the integrations a screen just read (the model picker reads them
  /// anyway), so the trial needs no request of its own.
  void observe(List<IntegrationInfo> integrations) {
    IntegrationInfo? found;
    for (final integration in integrations) {
      if (integration.id == claudeAgentProviderId &&
          integration.methods.any(
            (m) => m.type == 'external' && m.id == claudeAgentLoginMethodId,
          )) {
        found = integration;
      }
    }
    final scope = _host.scope;
    if (scope != _seenScope) _outcome = null;
    _integration = found;
    _seenScope = scope;
    notifyListeners();
    if (found == null &&
        scope != null &&
        _lateLoadScope != scope &&
        _host.onPhoneServer &&
        serverVersionAtLeast(_host.serverVersion, claudeAgentMinimumServer)) {
      _lateLoadScope = scope;
      unawaited(
        Future<void>.delayed(lateLoadDelay, () {
          if (_host.scope == scope && _integration == null) return refresh();
        }),
      );
    }
  }

  /// Reads the server's integrations itself.
  Future<void> refresh() async {
    final operations = _host.operations;
    if (operations == null || !_host.onPhoneServer) return;
    try {
      observe(await operations.listIntegrations());
    } catch (_) {
      // Unknown stays unknown; the picker's own read retries.
    }
  }

  /// Turns the trial on: points OpenCode at Claude Code, tells OpenCode the
  /// person chose Claude Code's own login, and waits for its check.
  Future<void> start() => _run(fresh: false);

  /// After signing in: the same, with a fresh check by OpenCode.
  Future<void> checkAgain() => _run(fresh: true);

  Future<void> _run({required bool fresh}) async {
    if (_busy || !capable) return;
    final owner = _host.agentOwnerId;
    final operations = _host.operations;
    final generation = ++_generation;
    _busy = true;
    _outcome = null;
    notifyListeners();
    bool current() => generation == _generation && _seenScope == _host.scope;
    try {
      if (owner == null ||
          operations == null ||
          operations is! ExternalIntegrationGateway) {
        _outcome = ClaudeAgentTrialState.failed;
        return;
      }
      final linked = await _link.link(owner);
      if (!current()) return;
      switch (linked) {
        case ClaudeAgentLinkResult.notInstalled:
          _outcome = ClaudeAgentTrialState.notInstalled;
          return;
        case ClaudeAgentLinkResult.blocked:
          _outcome = ClaudeAgentTrialState.blocked;
          return;
        case ClaudeAgentLinkResult.failed:
          _outcome = ClaudeAgentTrialState.failed;
          return;
        case ClaudeAgentLinkResult.linked:
        case ClaudeAgentLinkResult.linkedWithoutResume:
          break;
      }
      final before = {
        for (final connection
            in _integration?.connections ?? const <IntegrationConnectionInfo>[])
          ?connection.id,
      };
      // One connection: OpenCode checks the login when a connection starts,
      // so a fresh check is a new connection that replaces the old ones.
      if (before.isEmpty || fresh) {
        await (operations as ExternalIntegrationGateway)
            .connectIntegrationExternal(
              claudeAgentProviderId,
              claudeAgentLoginMethodId,
            );
        if (!current()) return;
      }
      await _waitForCheck(operations, current);
      if (!current()) return;
      if (fresh && before.isNotEmpty) {
        await _removeOld(operations, before);
      }
    } catch (_) {
      if (current()) _outcome = ClaudeAgentTrialState.failed;
    } finally {
      if (generation == _generation) {
        _busy = false;
        notifyListeners();
      }
    }
  }

  Future<void> _waitForCheck(
    ServerOperationsGateway operations,
    bool Function() current,
  ) async {
    for (var attempt = 0; attempt < pollAttempts; attempt++) {
      if (attempt > 0) await Future<void>.delayed(pollInterval);
      if (!current()) return;
      observe(await operations.listIntegrations());
      final connections = _integration?.connections ?? const [];
      final newest = connections.firstOrNull;
      if (newest?.status == 'needs_auth') return;
      await _host.refreshCatalog();
      if (!current()) return;
      if (_host.hasClaudeAgentModels == true) return;
    }
    if (current() && _host.hasClaudeAgentModels != true) {
      _outcome = ClaudeAgentTrialState.failed;
    }
  }

  Future<void> _removeOld(
    ServerOperationsGateway operations,
    Set<String> before,
  ) async {
    if (operations is! IntegrationCredentialGateway) return;
    final connections = _integration?.connections ?? const [];
    // Never remove the last one: the newest stays whatever happens.
    if (!connections.any((c) => c.id != null && !before.contains(c.id))) {
      return;
    }
    for (final id in before) {
      try {
        await (operations as IntegrationCredentialGateway).removeCredential(id);
      } catch (_) {
        // A leftover connection only shows as a second "Claude Agent".
      }
    }
    try {
      observe(await operations.listIntegrations());
    } catch (_) {}
  }
}

class _ConnectionTrialHost implements ClaudeAgentTrialHost {
  _ConnectionTrialHost(this._connection);

  final ConnectionController _connection;

  @override
  bool get onPhoneServer {
    final profile = _connection.profile;
    return profile != null &&
        !_connection.isIsolated &&
        BuiltinLinux.managesServerUrl(profile.baseUrl);
  }

  @override
  String? get serverVersion => _connection.version;

  @override
  String? get agentOwnerId {
    final profile = _connection.profile;
    return profile == null
        ? null
        : _connection.store.phoneAgentOwnerId(profile.id);
  }

  @override
  String? get scope {
    final profile = _connection.profile;
    return profile == null ? null : '${profile.id}@${_connection.version}';
  }

  @override
  ServerOperationsGateway? get operations => _connection.repository;

  @override
  bool? get hasClaudeAgentModels {
    final catalog = _connection.catalog;
    if (catalog == null) return null;
    return catalog.models.any((m) => m.providerID == claudeAgentProviderId);
  }

  @override
  Future<void> refreshCatalog() => _connection.refreshCatalog();
}
