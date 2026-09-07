import 'dart:async';

import 'package:flutter/widgets.dart';

import '../domain/provider_quota.dart';
import '../quota/provider_quota_client.dart';
import 'connection.dart';
import 'profiles.dart';
import 'provider_quota_budgets.dart';

/// Screen-visit consent for one server identity and connection location.
///
/// Account quotas have no session/project scope. Location still owns the route:
/// changing it detaches the page instead of relabelling previously shown data.
/// Same-source transport recovery only interrupts work; the next explicit read
/// acquires a new dedicated gateway. No OpenCode action/recovery API is invoked.
///
/// A separate connection liveness signal invalidates consent before profile
/// deletion's first await and on parent disposal. Every completion also rechecks
/// the current profile; quota data is never persisted.
class ProviderQuotaOverview extends ChangeNotifier with WidgetsBindingObserver {
  final ConnectionController connection;
  final DateTime Function() clock;
  final ProviderQuotaGateway Function(ServerProfile, QuotaProvider)
  _gatewayFactory;
  QuotaProvider _provider = QuotaProvider.codex;
  QuotaProvider get provider => _provider;
  bool get providerSupported => quotaCollectionAvailable(_provider);
  final int _location;
  _QuotaProfileScope? _scope;
  int _connectionRevision;
  Object? _api;
  Object? _repository;
  bool _suspended;
  bool _backgrounded;
  bool _disposed = false;
  bool _consented = false;
  bool _detached = false;
  bool _loading = false;
  bool _refreshFailed = false;
  int _request = 0;
  ProviderQuotaGateway? _gateway;
  ProviderQuotaSnapshot? _snapshot;
  ProviderQuotaFailure? _failure;
  Timer? _expiry;
  late final ProviderQuotaBudgets budgets;

  ProviderQuotaOverview(
    this.connection, {
    ProviderQuotaGateway Function(ServerProfile)? gatewayFactory,
    ProviderQuotaGateway Function(ServerProfile, QuotaProvider)?
    providerGatewayFactory,
    DateTime Function()? clock,
  }) : clock = clock ?? DateTime.now,
       _gatewayFactory =
           providerGatewayFactory ??
           ((profile, provider) =>
               gatewayFactory?.call(profile) ??
               HttpProviderQuotaGateway(profile, provider: provider)),
       _scope = _QuotaProfileScope.capture(connection.profile),
       _location = connection.locationRevision,
       _connectionRevision = connection.connectionRevision,
       _api = connection.api,
       _repository = connection.repository,
       _suspended = connection.lifecycleSuspended,
       _backgrounded = _isBackgrounded(WidgetsBinding.instance.lifecycleState) {
    _detached = _scope == null;
    final profile = connection.profile;
    budgets = ProviderQuotaBudgets(
      preferences: connection.store.prefs,
      profileId: profile?.id ?? '',
      serverOrigin: '${profile?.baseUrl ?? ''}\n${profile?.username ?? ''}',
      isCurrent: () =>
          !_detached &&
          _scope?.matches(connection.profile) == true &&
          connection.isProfileReadable(profile?.id ?? ''),
      isProfilePresent: () => connection.isProfileReadable(profile?.id ?? ''),
      clock: this.clock,
    );
    budgets.addListener(_budgetsChanged);
    connection.addListener(_connectionChanged);
    connection.profileDataChanges.addListener(_connectionChanged);
    WidgetsBinding.instance.addObserver(this);
  }

  // These reads also fail closed after a store-only removal/edit, even if its
  // caller has not yet notified ConnectionController. Do not notify in a getter
  // during widget build; connection events and actions notify normally.
  bool get consented {
    _synchronize();
    return _consented;
  }

  bool get detached {
    _synchronize();
    return _detached;
  }

  bool get loading {
    _synchronize();
    return _loading;
  }

  ProviderQuotaSnapshot? get snapshot {
    _synchronize();
    return _snapshot;
  }

  ProviderQuotaFailure? get failure {
    _synchronize();
    return _failure;
  }

  /// Consent is separate from setup: this getter is safe before consent and
  /// does not acquire a gateway. Missing/unreadable passwords require setup.
  bool get setupNeeded {
    _synchronize();
    final scope = _scope;
    return scope == null ||
        !HttpProviderQuotaGateway.canReadProfile(scope.toProfile());
  }

  /// Whether refresh may read NOW. Includes consent, but not [loading]; UI
  /// should additionally disable repeat taps while loading. Explicit overlapping
  /// refresh calls supersede/cancel older ones rather than merging results.
  bool get canRead {
    _synchronize();
    return !_disposed &&
        !_detached &&
        providerSupported &&
        _consented &&
        !_backgrounded &&
        !_suspended &&
        !setupNeeded;
  }

  bool get snapshotIsStale {
    final value = snapshot;
    if (value == null) return false;
    final now = clock();
    return _refreshFailed ||
        value.freshness != QuotaFreshness.fresh ||
        now.isBefore(value.fetchedAt) ||
        value.isStale(now) ||
        value.windows.any(
          (window) =>
              window.resetsAt != null && !now.isBefore(window.resetsAt!),
        );
  }

  /// Changing provider is a new read scope: cancel work and obtain fresh consent
  /// before dispatching to its fixed route. Never relabel another provider's data.
  void selectProvider(QuotaProvider provider) {
    _synchronize(notify: true);
    if (_disposed || _detached || _provider == provider) return;
    _clear();
    _provider = provider;
    notifyListeners();
  }

  /// Call only after the user explicitly affirms that they installed and trust
  /// the collector on the displayed server. Consent never survives this object.
  Future<void> allowAndRefresh() async {
    _synchronize(notify: true);
    if (_disposed || _detached) return;
    if (!providerSupported) {
      _failure = const ProviderQuotaFailure(QuotaFailureKind.unsupported);
      notifyListeners();
      return;
    }
    _consented = true;
    await refresh();
  }

  Future<void> refresh() async {
    _synchronize(notify: true);
    if (_disposed || _detached || !_consented) return;
    if (!canRead) {
      _failure = const ProviderQuotaFailure(QuotaFailureKind.unavailable);
      _refreshFailed = true;
      notifyListeners();
      return;
    }
    _cancelRequest();
    final request = _request;
    if (!_current(request)) return;
    _loading = true;
    _failure = null;
    notifyListeners();

    ProviderQuotaGateway? gateway;
    try {
      if (!_current(request)) return;
      gateway = _gatewayFactory(_scope!.toProfile(), _provider);
      // A factory/listener can synchronously change scope. Never dispatch a
      // second operation or install its gateway into a replacement request.
      if (!_current(request)) {
        _close(gateway);
        return;
      }
      _gateway = gateway;
      final result = await gateway.readSnapshot();
      if (!_current(request) || !identical(_gateway, gateway)) return;
      if (result.provider != _provider) {
        throw const ProviderQuotaFailure(QuotaFailureKind.invalidResponse);
      }
      // Whole replacement even when account.ref changes. Never merge windows
      // across accounts or retain an old measurement behind a new status.
      _snapshot = result;
      _refreshFailed = false;
      _scheduleExpiry(result);
      unawaited(budgets.observe(result, stale: snapshotIsStale));
    } catch (error) {
      if (!_current(request)) return;
      _failure = ProviderQuotaFailure(
        error is ProviderQuotaFailure
            ? error.kind
            : error is FormatException
            ? QuotaFailureKind.invalidResponse
            : QuotaFailureKind.unavailable,
      );
      _refreshFailed = true;
    } finally {
      if (gateway != null && identical(_gateway, gateway)) {
        _gateway = null;
        _close(gateway);
      }
      if (_current(request)) {
        _loading = false;
        notifyListeners();
      }
    }
  }

  void disable() {
    if (_disposed) return;
    _clear();
    notifyListeners();
  }

  static bool _isBackgrounded(AppLifecycleState? state) =>
      state == AppLifecycleState.paused ||
      state == AppLifecycleState.hidden ||
      state == AppLifecycleState.detached;

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (_disposed) return;
    // Inactive alone is a transient dialog/focus transition. Once backgrounded,
    // however, stay blocked until an actual resumed signal arrives.
    if (state != AppLifecycleState.resumed && !_isBackgrounded(state)) return;
    final backgrounded = _isBackgrounded(state);
    if (_backgrounded == backgrounded) return;
    _backgrounded = backgrounded;
    if (backgrounded) _interrupt();
    _synchronize();
    notifyListeners();
  }

  void _connectionChanged() => _synchronize(notify: true);

  bool _current(int request) {
    if (_disposed || request != _request) return false;
    _synchronize(notify: true);
    return !_detached &&
        _consented &&
        !_backgrounded &&
        !_suspended &&
        request == _request;
  }

  void _synchronize({bool notify = false}) {
    if (_disposed || _detached) return;
    if (_scope == null ||
        !connection.isProfileReadable(_scope!._id) ||
        _scope?.matches(connection.profile) != true ||
        connection.locationRevision != _location) {
      _detached = true;
      _scope = null;
      _api = null;
      _repository = null;
      _clear();
      if (notify) notifyListeners();
      return;
    }
    if (_connectionRevision != connection.connectionRevision ||
        !identical(_api, connection.api) ||
        !identical(_repository, connection.repository) ||
        _suspended != connection.lifecycleSuspended) {
      _connectionRevision = connection.connectionRevision;
      _api = connection.api;
      _repository = connection.repository;
      _suspended = connection.lifecycleSuspended;
      _interrupt();
      if (notify) notifyListeners();
    }
  }

  void _interrupt() {
    budgets.clearAttention();
    if (_loading) {
      _failure = const ProviderQuotaFailure(QuotaFailureKind.unavailable);
    }
    if (_snapshot != null) _refreshFailed = true;
    _loading = false;
    _cancelRequest();
    _expiry?.cancel();
    _expiry = null;
  }

  void _clear() {
    budgets.clearAttention();
    _consented = false;
    _loading = false;
    _snapshot = null;
    _failure = null;
    _refreshFailed = false;
    _expiry?.cancel();
    _expiry = null;
    _cancelRequest();
  }

  void _cancelRequest() {
    _request++;
    final gateway = _gateway;
    _gateway = null;
    if (gateway != null) _close(gateway);
  }

  static void _close(ProviderQuotaGateway gateway) {
    try {
      gateway.close();
    } catch (_) {
      // A test/custom gateway must not leak raw teardown errors either.
    }
  }

  void _scheduleExpiry(ProviderQuotaSnapshot value) {
    _expiry?.cancel();
    _expiry = null;
    if (snapshotIsStale) return;
    var expires = value.expiresAt;
    for (final window in value.windows) {
      final reset = window.resetsAt;
      if (reset != null && reset.isBefore(expires)) expires = reset;
    }
    final delay = expires.difference(clock());
    if (delay <= Duration.zero) return;
    _expiry = Timer(delay, () {
      _expiry = null;
      _synchronize(notify: true);
      if (!_disposed && !_detached && identical(_snapshot, value)) {
        // No polling and no invented replenishment when a reset passes.
        notifyListeners();
      }
    });
  }

  @override
  void dispose() {
    if (_disposed) return;
    _disposed = true;
    connection.removeListener(_connectionChanged);
    connection.profileDataChanges.removeListener(_connectionChanged);
    WidgetsBinding.instance.removeObserver(this);
    _scope = null;
    _api = null;
    _repository = null;
    _detached = true;
    _clear();
    budgets.removeListener(_budgetsChanged);
    budgets.dispose();
    super.dispose();
  }

  void _budgetsChanged() {
    if (!_disposed) notifyListeners();
  }

  @override
  String toString() => 'ProviderQuotaOverview';
}

/// Immutable private values, not a mutable ServerProfile or a record whose
/// default toString would disclose the password. Each gateway gets a new copy.
class _QuotaProfileScope {
  final String _id;
  final String _baseUrl;
  final String _username;
  final String _password;
  final bool _requiresPasswordReentry;

  _QuotaProfileScope(ServerProfile profile)
    : _id = profile.id,
      _baseUrl = profile.baseUrl,
      _username = profile.username,
      _password = profile.password,
      _requiresPasswordReentry = profile.requiresPasswordReentry;

  static _QuotaProfileScope? capture(ServerProfile? profile) =>
      profile == null ? null : _QuotaProfileScope(profile);

  bool matches(ServerProfile? profile) =>
      profile != null &&
      profile.id == _id &&
      profile.baseUrl == _baseUrl &&
      profile.username == _username &&
      profile.password == _password &&
      profile.requiresPasswordReentry == _requiresPasswordReentry;

  ServerProfile toProfile() => ServerProfile(
    id: _id,
    name: '',
    baseUrl: _baseUrl,
    username: _username,
    password: _password,
    requiresPasswordReentry: _requiresPasswordReentry,
  );

  @override
  String toString() => 'QuotaProfileScope';
}
