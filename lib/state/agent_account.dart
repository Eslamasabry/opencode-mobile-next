import 'dart:async';

import 'package:flutter/foundation.dart';

import '../domain/agent_account.dart';

enum AccountPanelStatus { loading, ready, unavailable, error, disconnected }

enum AccountLoginStatus {
  idle,
  starting,
  waiting,
  cancelling,
  cancelled,
  failed,
  uncertain,
  completed,
}

/// Transient account state for one open panel. No preference or credential writes.
class AgentAccountController extends ChangeNotifier {
  final AgentAccountSession session;
  late final StreamSubscription<AccountEvent> _subscription;
  bool _disposed = false;
  int _readRevision = 0;
  int _loginRevision = 0;
  bool _refreshQueued = false;
  AccountPanelStatus status = AccountPanelStatus.loading;
  AccountLoginStatus loginStatus = AccountLoginStatus.idle;
  AgentAccount? account;
  List<AccountRateBucket>? limits;
  AccountTokenUsage? usage;
  AccountDeviceCode? deviceCode;
  bool metricsLoading = false;
  DateTime? updatedAt;

  AgentAccountController(this.session) {
    _subscription = session.events.listen(_event);
  }

  bool get canSignIn =>
      status == AccountPanelStatus.ready &&
      account?.signedIn == false &&
      account?.requiresSignIn == true &&
      !loginPending &&
      loginStatus != AccountLoginStatus.uncertain;
  bool get loginPending => {
    AccountLoginStatus.starting,
    AccountLoginStatus.waiting,
    AccountLoginStatus.cancelling,
  }.contains(loginStatus);
  void _notify() {
    if (!_disposed) notifyListeners();
  }

  void _event(AccountEvent event) {
    if (_disposed) return;
    switch (event.kind) {
      case AccountEventKind.disconnected:
        invalidate();
      case AccountEventKind.loginCompleted:
        ++_loginRevision;
        deviceCode = null;
        loginStatus = event.success
            ? AccountLoginStatus.completed
            : AccountLoginStatus.failed;
        _queueRefresh();
      case AccountEventKind.changed:
      case AccountEventKind.limitsChanged:
        _queueRefresh();
    }
  }

  void _queueRefresh() {
    // Remove stale identity/metrics synchronously on account notifications.
    ++_readRevision;
    account = null;
    limits = null;
    usage = null;
    updatedAt = null;
    status = AccountPanelStatus.loading;
    _notify();
    if (_refreshQueued) return;
    _refreshQueued = true;
    scheduleMicrotask(() {
      _refreshQueued = false;
      if (!_disposed) unawaited(refresh());
    });
  }

  Future<void> refresh() async {
    if (_disposed) return;
    if (!session.active) {
      invalidate();
      return;
    }
    final revision = ++_readRevision;
    account = null;
    limits = null;
    usage = null;
    updatedAt = null;
    metricsLoading = false;
    status = session.supported
        ? AccountPanelStatus.loading
        : AccountPanelStatus.unavailable;
    _notify();
    if (!session.supported) return;
    try {
      final value = await session.read();
      if (!_current(revision)) return;
      account = value;
      status = AccountPanelStatus.ready;
      updatedAt = DateTime.now();
      // API-key and other host auth cannot be labelled subscription usage.
      metricsLoading = value.type == 'chatgpt';
      _notify();
      if (!metricsLoading) return;
      // Independent failures must not erase a successful account read.
      final fetchedLimits = await _readOrNull(session.rateLimits);
      if (!_current(revision)) return;
      limits = fetchedLimits;
      _notify();
      final fetchedUsage = await _readOrNull(session.usage);
      if (!_current(revision)) return;
      usage = fetchedUsage;
      metricsLoading = false;
      updatedAt = DateTime.now();
      _notify();
    } on AgentAccountException catch (error) {
      if (!_current(revision)) return;
      status = switch (error.kind) {
        AgentAccountFailure.unavailable => AccountPanelStatus.unavailable,
        AgentAccountFailure.disconnected => AccountPanelStatus.disconnected,
        _ => AccountPanelStatus.error,
      };
      _notify();
    } catch (_) {
      if (!_current(revision)) return;
      status = AccountPanelStatus.error;
      _notify();
    }
  }

  Future<T?> _readOrNull<T>(Future<T> Function() read) async {
    try {
      return await read();
    } catch (_) {
      return null;
    }
  }

  bool _current(int revision) =>
      !_disposed && revision == _readRevision && session.active;

  Future<void> signIn() async {
    if (!canSignIn) return;
    final revision = ++_loginRevision;
    loginStatus = AccountLoginStatus.starting;
    deviceCode = null;
    _notify();
    try {
      final value = await session.startDeviceLogin();
      if (_disposed || revision != _loginRevision || !session.active) return;
      deviceCode = value;
      loginStatus = AccountLoginStatus.waiting;
    } on AgentAccountException catch (error) {
      if (_disposed || revision != _loginRevision) return;
      loginStatus = error.kind == AgentAccountFailure.uncertain
          ? AccountLoginStatus.uncertain
          : AccountLoginStatus.failed;
    } catch (_) {
      if (_disposed || revision != _loginRevision) return;
      loginStatus = AccountLoginStatus.failed;
    }
    _notify();
  }

  Future<void> cancel() async {
    if (_disposed ||
        !loginPending ||
        loginStatus == AccountLoginStatus.cancelling)
      return;
    final revision = ++_loginRevision;
    deviceCode = null;
    loginStatus = AccountLoginStatus.cancelling;
    _notify();
    final confirmed = await session.cancelLogin();
    if (_disposed || revision != _loginRevision) return;
    loginStatus = confirmed
        ? AccountLoginStatus.cancelled
        : AccountLoginStatus.uncertain;
    _notify();
    await refresh();
  }

  void invalidate() {
    if (_disposed) return;
    ++_readRevision;
    ++_loginRevision;
    deviceCode = null;
    account = null;
    limits = null;
    usage = null;
    updatedAt = null;
    metricsLoading = false;
    if (loginPending) loginStatus = AccountLoginStatus.uncertain;
    status = AccountPanelStatus.disconnected;
    _notify();
  }

  @override
  void dispose() {
    _disposed = true;
    ++_readRevision;
    ++_loginRevision;
    deviceCode = null;
    account = null;
    limits = null;
    usage = null;
    unawaited(_subscription.cancel());
    unawaited(session.close());
    super.dispose();
  }
}
