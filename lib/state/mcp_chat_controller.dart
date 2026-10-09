import 'dart:async';

import 'package:flutter/foundation.dart';

import '../api/mcp_oauth.dart';
import '../domain/mcp_catalog.dart';
import '../domain/mcp_chat.dart';
import '../domain/server_gateway.dart';

/// One explicitly accepted catalog suggestion, pinned to its conversation.
/// The host supplies a fresh transport for each operation (browser background
/// suspension can replace it) and invalidates identity changes via [current].
final class McpChatController extends ChangeNotifier {
  McpChatController({
    required this.item,
    required Future<McpGateway> Function() gateway,
    required ServerCapabilities Function() capabilities,
    required bool Function() current,
  }) : _gateway = gateway,
       _capabilities = capabilities,
       _current = current;

  final McpCatalogItem item;
  final Future<McpGateway> Function() _gateway;
  final ServerCapabilities Function() _capabilities;
  final bool Function() _current;
  McpChatSnapshot _snapshot = const McpChatSnapshot(McpChatPhase.suggested);
  McpChatSnapshot get snapshot => _snapshot;
  bool get isDisposed => _disposed;
  Future<void>? _work;
  bool _disposed = false,
      _invalidated = false,
      _addDispatched = false,
      _authDispatched = false;
  int _oauthRevision = 0;
  McpAuthLaunch? _launch;
  McpOAuthLoopbackListener? _listener;

  bool _check() {
    if (_disposed || _invalidated) return false;
    if (!_current()) {
      invalidate();
      return false;
    }
    return true;
  }

  void _set(
    McpChatPhase phase, {
    McpChatFailure? failure,
    Uri? url,
    bool manualCodeRequired = false,
  }) {
    if (_disposed) return;
    _snapshot = McpChatSnapshot(
      phase,
      failure: failure,
      authorizationUrl: url,
      manualCodeRequired: manualCodeRequired,
    );
    notifyListeners();
  }

  void _fail(McpChatFailure failure) => _set(
    failure == McpChatFailure.unavailable
        ? McpChatPhase.unavailable
        : McpChatPhase.failed,
    failure: failure,
  );

  /// No remote cancellation here: invalidation cannot revoke unrelated or
  /// previously established credentials on the server.
  void invalidate() {
    if (_disposed) return;
    _invalidated = true;
    _clearLaunch();
    if (_snapshot.failure != McpChatFailure.sourceChanged) {
      _fail(McpChatFailure.sourceChanged);
    }
  }

  void _clearLaunch() {
    ++_oauthRevision;
    _launch = null;
    final listener = _listener;
    _listener = null;
    if (listener != null) unawaited(listener.close());
  }

  Future<void> _run(Future<void> Function() action, McpChatFailure failure) {
    if (_disposed) return Future.value();
    final pending = _work;
    if (pending != null) return pending;
    // Start in a microtask so even synchronous failure shares one reservation.
    final work = Future<void>.microtask(() async {
      try {
        if (_check()) await action();
      } catch (_) {
        if (_check()) _fail(failure);
      }
    });
    _work = work;
    return work.whenComplete(() {
      if (identical(_work, work)) _work = null;
    });
  }

  bool get _supported =>
      _capabilities().mcpChatConnect &&
      _capabilities().serverCatalog &&
      _capabilities().mcpRuntimeAdds;

  Future<void> connect(String serverId) {
    // Validate before coalescing; a different name cannot borrow another call.
    if (serverId != item.serverName) {
      if (!_disposed) _fail(McpChatFailure.invalidSuggestion);
      return Future.value();
    }
    return _run(() async {
      if (!_supported) {
        _fail(McpChatFailure.unavailable);
        return;
      }
      if (_addDispatched) {
        await _refresh();
        return;
      }
      final draft = item.draft;
      if (draft == null ||
          item.runtime != McpCatalogRuntime.hosted ||
          item.needsKey ||
          item.needsSettings ||
          draft.headers.isNotEmpty ||
          draft.environment.isNotEmpty ||
          draft.command.isNotEmpty ||
          Uri.tryParse(draft.url ?? '')?.scheme != 'https') {
        _fail(McpChatFailure.setupRequired);
        return;
      }
      _set(McpChatPhase.connecting);
      final gateway = await _gateway();
      if (!_check()) return;
      final servers = await gateway.listMcpServers();
      if (!_check()) return;
      if (servers.any((server) => server.name == serverId)) {
        _fail(McpChatFailure.nameConflict);
        return;
      }
      // An uncertain response must be reconciled, never automatically re-added.
      _addDispatched = true;
      await gateway.addMcpServer(draft, scope: McpConfigScope.runtimeLocation);
      if (!_check()) return;
      await _refresh();
    }, McpChatFailure.connectFailed);
  }

  Future<void> refresh() => _run(_refresh, McpChatFailure.connectFailed);

  Future<void> _refresh() async {
    if (!_supported) {
      _fail(McpChatFailure.unavailable);
      return;
    }
    if (!_addDispatched) return;
    _set(McpChatPhase.checkingTools);
    final gateway = await _gateway();
    if (!_check()) return;
    final rows = await gateway.listMcpServers();
    if (!_check()) return;
    final matches = rows.where((row) => row.name == item.serverName).toList();
    if (matches.length != 1) {
      _fail(McpChatFailure.notConnected);
      return;
    }
    switch (matches.single.status) {
      case 'connected':
        _clearLaunch();
        _authDispatched = false;
        _set(
          _capabilities().mcpChatToolRefresh
              ? McpChatPhase.toolsReady
              : McpChatPhase.connectedReadinessUnknown,
        );
      case 'needs_auth':
        _set(
          _launch == null
              ? McpChatPhase.needsAuthentication
              : McpChatPhase.authorizing,
          url: _launch?.authorizationUrl,
          manualCodeRequired: _launch != null && _listener == null,
        );
      case 'needs_client_registration':
        _fail(McpChatFailure.setupRequired);
      default:
        _fail(McpChatFailure.notConnected);
    }
  }

  Future<void> startOAuth() {
    final requestRevision = _oauthRevision;
    return _run(() async {
      if (!_supported ||
          !_capabilities().mcpChatOAuth ||
          !_capabilities().mcpOAuth) {
        _fail(McpChatFailure.oauthUnavailable);
        return;
      }
      if (!_addDispatched ||
          _authDispatched ||
          _snapshot.phase != McpChatPhase.needsAuthentication) {
        return;
      }
      final gateway = await _gateway();
      if (!_check()) return;
      if (requestRevision != _oauthRevision) return;
      _authDispatched = true;
      final launch = await gateway.startMcpAuthentication(item.serverName);
      if (!_check() || requestRevision != _oauthRevision) return;
      final url = launch.authorizationUrl;
      if (url.scheme != 'https' ||
          url.host.isEmpty ||
          url.userInfo.isNotEmpty ||
          RegExp(r'[\s\x00-\x1f\x7f]').hasMatch(url.toString()) ||
          launch.oauthState.isEmpty ||
          url.queryParameters['state'] != launch.oauthState) {
        _fail(McpChatFailure.authenticationFailed);
        return;
      }
      _launch = launch;
      final revision = ++_oauthRevision;
      final redirect = mcpLoopbackRedirect(url);
      if (redirect != null) {
        try {
          final listener = await McpOAuthLoopbackListener.bind(
            redirect: redirect,
            expectedState: launch.oauthState,
            timeout: const Duration(minutes: 3),
          );
          if (!_check() || revision != _oauthRevision) {
            await listener.close();
            return;
          }
          _listener = listener;
          unawaited(_receiveCode(listener, revision));
        } catch (_) {
          // The server on this same phone may already own the loopback port.
          // Require the manual return-URL/code fallback; browser return alone
          // is not completion when the server has no pending callback waiter.
        }
      }
      if (_check()) {
        _set(
          McpChatPhase.authorizing,
          url: url,
          manualCodeRequired: _listener == null,
        );
      }
    }, McpChatFailure.authenticationFailed);
  }

  Future<void> _receiveCode(
    McpOAuthLoopbackListener listener,
    int revision,
  ) async {
    try {
      final code = await listener.code;
      // Wait for startOAuth to release its reservation before completion.
      await _work;
      if (_check() && revision == _oauthRevision) await completeOAuth(code);
    } catch (_) {
      if (!_disposed && revision == _oauthRevision && _check()) {
        _clearLaunch();
        _fail(McpChatFailure.authenticationFailed);
      }
    }
  }

  Future<void> completeOAuth(String callbackOrCode) => _run(() async {
    final launch = _launch;
    if (launch == null || !_authDispatched || !_capabilities().mcpChatOAuth) {
      _fail(McpChatFailure.authenticationFailed);
      return;
    }
    if (callbackOrCode.length > 8192) {
      _fail(McpChatFailure.authenticationFailed);
      return;
    }
    final code = parseMcpAuthorizationCode(
      callbackOrCode,
      expectedState: launch.oauthState,
    );
    if (code.trim().isEmpty) {
      _fail(McpChatFailure.authenticationFailed);
      return;
    }
    final gateway = await _gateway();
    if (!_check()) return;
    // Ignore the mutation response; the subsequent inventory read is authority.
    await gateway.completeMcpAuthentication(item.serverName, code);
    if (!_check()) return;
    _clearLaunch();
    _authDispatched = false;
    await _refresh();
  }, McpChatFailure.authenticationFailed);

  Future<void> cancelOAuth() async {
    // Cancellation cannot coalesce into an outstanding start/completion call.
    // Hide and close the local launch immediately; then cancel any owned start
    // whose response arrives after the tap, without starting another OAuth flow.
    if (!_check()) return;
    final pendingWork = _work;
    if (!_authDispatched &&
        !(_snapshot.phase == McpChatPhase.needsAuthentication &&
            pendingWork != null)) {
      return;
    }
    _clearLaunch();
    if (!_disposed) _set(McpChatPhase.checkingTools);
    await pendingWork;
    _clearLaunch();
    await _run(() async {
      if (!_authDispatched) {
        _set(McpChatPhase.needsAuthentication);
        return;
      }
      final gateway = await _gateway();
      if (!_check()) return;
      await gateway.cancelMcpAuthentication(item.serverName);
      if (!_check()) return;
      _authDispatched = false;
      _set(McpChatPhase.needsAuthentication);
    }, McpChatFailure.authenticationFailed);
  }

  @override
  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _clearLaunch();
    super.dispose();
  }
}
