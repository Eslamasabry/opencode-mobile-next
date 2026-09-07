import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:crypto/crypto.dart';
import 'package:flutter/foundation.dart';

import '../domain/provider_quota.dart';
import 'budget_persistence.dart';
import 'profiles.dart';

typedef QuotaMonitorAlert =
    Future<bool> Function({
      required String profileID,
      required String key,
      required String token,
    });

enum QuotaMonitorStatus {
  disabled,
  waiting,
  checking,
  current,
  paused,
  wifiRequired,
  unavailable,
  sourceChanged,
}

class QuotaMonitorTarget {
  final String profileID;
  final QuotaProvider provider;
  const QuotaMonitorTarget(this.profileID, this.provider);
}

class QuotaMonitorRules {
  final String source, account, token;
  final bool notifications, wifiOnly;
  final int? quietStart, quietEnd;
  final double threshold;
  final Map<String, String> alerted;
  const QuotaMonitorRules({
    required this.source,
    required this.account,
    required this.token,
    this.notifications = false,
    this.wifiOnly = false,
    this.quietStart,
    this.quietEnd,
    this.alerted = const {},
    this.threshold = 100,
  });
  bool quietAt(DateTime now) {
    if (quietStart == null || quietEnd == null) {
      return false;
    }
    final minute = now.hour * 60 + now.minute;
    return quietStart == quietEnd ||
        (quietStart! < quietEnd!
            ? minute >= quietStart! && minute < quietEnd!
            : minute >= quietStart! || minute < quietEnd!);
  }

  Map<String, dynamic> toJson() => {
    'source': source,
    'account': account,
    'token': token,
    'notifications': notifications,
    'wifiOnly': wifiOnly,
    'quietStart': quietStart,
    'quietEnd': quietEnd,
    'alerted': alerted,
    'threshold': threshold,
  };
  factory QuotaMonitorRules.fromJson(Map<String, dynamic> value) {
    final threshold = value['threshold'] ?? 100;
    if (threshold is! num ||
        !threshold.isFinite ||
        threshold <= 0 ||
        threshold > 100) {
      throw const FormatException('Invalid quota monitor threshold');
    }
    bool hash(Object? v) =>
        v is String && RegExp(r'^[a-f0-9]{64}$').hasMatch(v);
    bool minute(Object? v) => v == null || v is int && v >= 0 && v < 1440;
    if (!hash(value['source']) ||
        !hash(value['account']) ||
        !hash(value['token']) ||
        value['notifications'] is! bool ||
        value['wifiOnly'] is! bool ||
        !minute(value['quietStart']) ||
        !minute(value['quietEnd'])) {
      throw const FormatException('Invalid quota monitor rule');
    }
    final alerted = Map<String, String>.from(value['alerted'] as Map);
    if (alerted.length > 64 ||
        alerted.entries.any((e) => !hash(e.key) || !hash(e.value))) {
      throw const FormatException('Invalid quota monitor marker');
    }
    return QuotaMonitorRules(
      source: value['source'],
      account: value['account'],
      token: value['token'],
      notifications: value['notifications'],
      wifiOnly: value['wifiOnly'],
      quietStart: value['quietStart'],
      quietEnd: value['quietEnd'],
      alerted: Map.unmodifiable(alerted),
      threshold: threshold.toDouble(),
    );
  }
}

class QuotaMonitorObservation {
  final QuotaMonitorStatus status;
  final ProviderQuotaSnapshot? snapshot;
  final DateTime? checkedAt;
  const QuotaMonitorObservation(this.status, {this.snapshot, this.checkedAt});
}

/// Explicit collector consent is independent of page consent and session
/// monitoring. This object never starts an FGS, connects OpenCode, or persists
/// measured quotas. One dedicated bounded read runs at a time, fairly rotated.
class ProviderQuotaMonitor extends ChangeNotifier {
  final ProfileStore store;
  final ProviderQuotaGateway Function(ServerProfile, QuotaProvider)
  createGateway;
  final bool Function(String) isReadable;
  final Future<bool?> Function() networkWifi;
  final QuotaMonitorAlert alert;
  final Future<bool> Function(String) dismiss;
  final DateTime Function() clock;
  final Duration foregroundInterval, backgroundInterval, timeout;
  final _observations = <String, QuotaMonitorObservation>{};
  final _epochs = <String, int>{};
  final _blocked = <String>{};
  final _pausedSources = <String>{};
  final _writes = <String, Future<bool>>{};
  final _expiry = <String, Timer>{};
  Future<void>? _refresh;
  ProviderQuotaGateway? _gateway;
  String? _readingProfile;
  Timer? _timer;
  bool _foreground = true, _backgroundAllowed = false, _disposed = false;
  int _runtimeEpoch = 0, _cursor = 0;

  ProviderQuotaMonitor({
    required this.store,
    required this.createGateway,
    required this.isReadable,
    required this.networkWifi,
    required this.alert,
    required this.dismiss,
    DateTime Function()? clock,
    this.foregroundInterval = const Duration(minutes: 5),
    this.backgroundInterval = const Duration(minutes: 15),
    this.timeout = const Duration(seconds: 10),
  }) : clock = clock ?? DateTime.now;

  static String key(String profileID) => 'oc.quotaMonitor.$profileID';
  static String _id(String profileID, QuotaProvider provider) =>
      '$profileID:${provider.name}';
  static String _alertKey(String id, QuotaProvider provider) =>
      'quota:$id:${provider.name}';
  static String _hash(Object? value) =>
      sha256.convert(utf8.encode(jsonEncode(value))).toString();
  static String _source(ServerProfile profile) =>
      _hash([profile.baseUrl, profile.username]);
  bool get runningAllowed => !_disposed && (_foreground || _backgroundAllowed);
  bool get refreshing => _refresh != null;
  ServerProfile? _profile(String id) =>
      store.profiles.where((p) => p.id == id).firstOrNull;
  bool _readable(String id) =>
      !_disposed && !_blocked.contains(id) && isReadable(id);

  QuotaMonitorRules? rulesFor(String id, QuotaProvider provider) {
    if (!_readable(id) || !quotaCollectionAvailable(provider)) {
      return null;
    }
    try {
      final raw = store.prefs.getString(key(id));
      if (raw == null || raw.length > 65536) {
        return null;
      }
      final data = jsonDecode(raw) as Map<String, dynamic>;
      if (data['version'] != 1) {
        return null;
      }
      final rule = (data['rules'] as Map)[provider.name];
      return rule == null
          ? null
          : QuotaMonitorRules.fromJson(Map<String, dynamic>.from(rule));
    } catch (_) {
      return null;
    }
  }

  List<QuotaMonitorTarget> get sources => [
    for (final profile in store.profiles)
      for (final provider in QuotaProvider.values)
        if (rulesFor(profile.id, provider) != null)
          QuotaMonitorTarget(profile.id, provider),
  ];

  bool _fresh(ProviderQuotaSnapshot snapshot) =>
      snapshot.canShowWindows &&
      snapshot.freshness == QuotaFreshness.fresh &&
      !snapshot.isStale(clock()) &&
      !clock().isBefore(snapshot.fetchedAt) &&
      !snapshot.windows.any(
        (w) => w.resetsAt != null && !clock().isBefore(w.resetsAt!),
      );

  QuotaMonitorObservation observationFor(String id, QuotaProvider provider) {
    final rules = rulesFor(id, provider);
    if (rules == null) {
      return const QuotaMonitorObservation(QuotaMonitorStatus.disabled);
    }
    if (_pausedSources.contains(_id(id, provider))) {
      return const QuotaMonitorObservation(QuotaMonitorStatus.paused);
    }
    final profile = _profile(id);
    if (profile == null || _source(profile) != rules.source) {
      return const QuotaMonitorObservation(QuotaMonitorStatus.sourceChanged);
    }
    if (!runningAllowed) {
      return const QuotaMonitorObservation(QuotaMonitorStatus.paused);
    }
    final value = _observations[_id(id, provider)];
    if (value?.snapshot case final snapshot?) {
      if (!_fresh(snapshot) || snapshot.account.ref != rules.account) {
        return QuotaMonitorObservation(
          QuotaMonitorStatus.waiting,
          checkedAt: value?.checkedAt,
        );
      }
    }
    return value ?? const QuotaMonitorObservation(QuotaMonitorStatus.waiting);
  }

  /// Only the exact source just reviewed by the user can be enrolled. Consent
  /// never silently follows an account or server credential-owner change.
  Future<bool> enroll(
    String id,
    ProviderQuotaSnapshot snapshot, {
    required bool notifications,
    bool wifiOnly = false,
    double threshold = 100,
    int? quietStart,
    int? quietEnd,
  }) async {
    final profile = _profile(id);
    if (!_readable(id) ||
        profile == null ||
        !quotaCollectionAvailable(snapshot.provider) ||
        !_fresh(snapshot) ||
        snapshot.account.ref == null) {
      return false;
    }
    final random = Random.secure();
    final rules = QuotaMonitorRules(
      source: _source(profile),
      account: snapshot.account.ref!,
      token: List.generate(
        32,
        (_) => random.nextInt(256).toRadixString(16).padLeft(2, '0'),
      ).join(),
      notifications: notifications,
      wifiOnly: wifiOnly,
      quietStart: quietStart,
      quietEnd: quietEnd,
      threshold: threshold,
    );
    return _save(id, snapshot.provider, rules);
  }

  Future<bool> setPolicy(
    String id,
    QuotaProvider provider, {
    required bool notifications,
    required bool wifiOnly,
    double? threshold,
    int? quietStart,
    int? quietEnd,
  }) async {
    final old = rulesFor(id, provider);
    if (old == null) {
      return false;
    }
    return _save(
      id,
      provider,
      QuotaMonitorRules(
        source: old.source,
        account: old.account,
        token: old.token,
        notifications: notifications,
        wifiOnly: wifiOnly,
        quietStart: quietStart,
        quietEnd: quietEnd,
        alerted: old.alerted,
        threshold: threshold ?? old.threshold,
      ),
    );
  }

  Future<bool> disable(String id, QuotaProvider provider) =>
      _save(id, provider, null);

  Future<bool> _save(
    String id,
    QuotaProvider provider,
    QuotaMonitorRules? rules,
  ) {
    if (!_readable(id)) {
      return Future.value(false);
    }
    if (rules == null) {
      _pausedSources.add(_id(id, provider));
    }
    if (rules != null) {
      try {
        QuotaMonitorRules.fromJson(rules.toJson());
      } catch (_) {
        return Future.value(false);
      }
    }
    final epoch = (_epochs[id] ?? 0) + 1;
    _epochs[id] = epoch;
    if (_readingProfile == id) {
      _closeGateway();
    }
    _observations.remove(_id(id, provider));
    _expiry.remove(_id(id, provider))?.cancel();
    final operation =
        BudgetPersistence.update(
          preferences: store.prefs,
          key: key(id),
          changes: {provider.name: rules?.toJson()},
          isCurrent: () => _readable(id) && _epochs[id] == epoch,
          isProfilePresent: () => isReadable(id),
        ).then((result) async {
          await _dismiss(id, provider);
          if (result == null) {
            return false;
          }
          _pausedSources.remove(_id(id, provider));
          if (!_disposed) {
            notifyListeners();
            _schedule();
          }
          return true;
        });
    _writes[id] = operation;
    return operation;
  }

  QuotaMonitorTarget? routeForToken(String id, String token) {
    if (!_readable(id)) {
      return null;
    }
    for (final provider in QuotaProvider.values) {
      final rules = rulesFor(id, provider), profile = _profile(id);
      if (rules != null &&
          profile != null &&
          rules.notifications &&
          !_pausedSources.contains(_id(id, provider)) &&
          _observations[_id(id, provider)]?.status !=
              QuotaMonitorStatus.sourceChanged &&
          rules.token == token &&
          rules.source == _source(profile)) {
        return QuotaMonitorTarget(id, provider);
      }
    }
    return null;
  }

  Future<QuotaMonitorTarget?> resolveRoute(String id, String token) async {
    final target = routeForToken(id, token);
    if (target == null) {
      return null;
    }
    await refreshSource(target);
    if (routeForToken(id, token) == null) {
      return null;
    }
    // A historical alert remains reviewable during a temporary outage. The
    // review shows unavailable, never an expired measurement.
    await _dismiss(id, target.provider);
    return target;
  }

  Future<void> refreshSource(QuotaMonitorTarget target) async {
    while (_refresh != null) {
      await _refresh;
    }
    if (!runningAllowed) {
      return;
    }
    final operation = Future<void>.microtask(
      () => _read(target.profileID, target.provider),
    );
    _refresh = operation;
    try {
      await operation;
    } finally {
      _refresh = null;
      if (!_disposed) {
        _schedule();
        notifyListeners();
      }
    }
  }

  void _closeGateway() {
    try {
      _gateway?.close();
    } catch (_) {}
  }

  void start() {
    if (!_disposed && sources.isNotEmpty) {
      _schedule();
      unawaited(refresh());
    }
  }

  void setRuntime({required bool foreground, required bool backgroundAllowed}) {
    if (_disposed) {
      return;
    }
    if (_foreground == foreground && _backgroundAllowed == backgroundAllowed) {
      return;
    }
    _foreground = foreground;
    _backgroundAllowed = backgroundAllowed;
    _runtimeEpoch++;
    _closeGateway();
    _observations.clear();
    for (final timer in _expiry.values) {
      timer.cancel();
    }
    _expiry.clear();
    _timer?.cancel();
    _timer = null;
    if (runningAllowed) {
      _schedule();
      unawaited(refresh());
    }
    if (!runningAllowed) {
      for (final target in sources) {
        unawaited(_dismiss(target.profileID, target.provider));
      }
    }
    notifyListeners();
  }

  void _schedule() {
    _timer?.cancel();
    _timer = null;
    if (!runningAllowed || sources.isEmpty) {
      return;
    }
    _timer = Timer(_foreground ? foregroundInterval : backgroundInterval, () {
      _timer = null;
      unawaited(refresh());
    });
  }

  Future<void> refresh() {
    if (!runningAllowed || sources.isEmpty) {
      return Future.value();
    }
    if (_refresh != null) {
      return _refresh!;
    }
    final operation = Future<void>.microtask(_cycle);
    _refresh = operation;
    return operation.whenComplete(() {
      _refresh = null;
      if (!_disposed) {
        _schedule();
        notifyListeners();
      }
    });
  }

  Future<void> _cycle() async {
    final list = sources;
    if (list.isEmpty) {
      return;
    }
    // At most three sources per cycle; never starve sources beyond this bound.
    final count = min(3, list.length), start = _cursor % list.length;
    _cursor = (start + count) % list.length;
    for (var i = 0; i < count && runningAllowed; i++) {
      final target = list[(start + i) % list.length];
      await _read(target.profileID, target.provider);
    }
  }

  Future<void> _read(String id, QuotaProvider provider) async {
    final rules = rulesFor(id, provider), profile = _profile(id);
    if (rules == null ||
        profile == null ||
        _pausedSources.contains(_id(id, provider))) {
      return;
    }
    final epoch = _epochs[id] ?? 0, runtime = _runtimeEpoch;
    final url = profile.baseUrl,
        username = profile.username,
        password = profile.password;
    bool current() {
      final p = _profile(id), r = rulesFor(id, provider);
      return runningAllowed &&
          _readable(id) &&
          (_epochs[id] ?? 0) == epoch &&
          _runtimeEpoch == runtime &&
          p?.baseUrl == url &&
          p?.username == username &&
          p?.password == password &&
          r?.token == rules.token &&
          r?.source == _source(profile);
    }

    void publish(QuotaMonitorStatus status, [ProviderQuotaSnapshot? snapshot]) {
      if (!current()) {
        return;
      }
      _observations[_id(id, provider)] = QuotaMonitorObservation(
        status,
        snapshot: snapshot,
        checkedAt: clock(),
      );
      notifyListeners();
    }

    if (_source(profile) != rules.source) {
      await _dismiss(id, provider);
      return;
    }
    ProviderQuotaGateway? gateway;
    try {
      if (rules.wifiOnly) {
        bool? wifi;
        try {
          wifi = await networkWifi().timeout(timeout);
        } catch (_) {
          wifi = null;
        }
        if (!current()) {
          return;
        }
        if (wifi != true) {
          publish(QuotaMonitorStatus.wifiRequired);
          await _dismiss(id, provider);
          return;
        }
      }
      if (!current()) {
        return;
      }
      publish(QuotaMonitorStatus.checking);
      gateway = createGateway(
        ServerProfile(
          id: id,
          name: '',
          baseUrl: url,
          username: username,
          password: password,
          requiresPasswordReentry: profile.requiresPasswordReentry,
        ),
        provider,
      );
      _gateway = gateway;
      _readingProfile = id;
      final snapshot = await gateway.readSnapshot().timeout(timeout);
      if (!current()) {
        return;
      }
      if (snapshot.provider != provider ||
          snapshot.account.ref != rules.account) {
        publish(QuotaMonitorStatus.sourceChanged);
        await _dismiss(id, provider);
        return;
      }
      if (!_fresh(snapshot)) {
        publish(QuotaMonitorStatus.unavailable);
        return;
      }
      publish(QuotaMonitorStatus.current, snapshot);
      final sourceID = _id(id, provider);
      _expiry.remove(sourceID)?.cancel();
      var expires = snapshot.expiresAt;
      for (final window in snapshot.windows) {
        if (window.resetsAt != null && window.resetsAt!.isBefore(expires)) {
          expires = window.resetsAt!;
        }
      }
      final untilExpiry = expires.difference(clock());
      if (untilExpiry > Duration.zero) {
        _expiry[sourceID] = Timer(untilExpiry, () {
          _expiry.remove(sourceID);
          // The native alert records a past threshold observation. Expiring
          // its measurement must not erase the only deduplicated event.
          if (!_disposed) {
            notifyListeners();
          }
        });
      }
      final markers = Map<String, String>.of(rules.alerted);
      var changed = false;
      var reached = false;
      for (final window in snapshot.windows) {
        if (window.usedPercent == null ||
            window.usedPercent! < rules.threshold) {
          continue;
        }
        reached = true;
        final windowID = _hash([window.id, window.durationSeconds]);
        final marker = _hash([
          windowID,
          window.resetsAt?.millisecondsSinceEpoch,
          rules.threshold,
        ]);
        if (markers[windowID] == marker) {
          continue;
        }
        markers[windowID] = marker;
        changed = true;
      }
      if (!reached) {
        // Missing windows do not prove recovery of a previously alerted limit.
        final recovered = {
          for (final window in snapshot.windows)
            if (window.usedPercent != null &&
                window.usedPercent! < rules.threshold)
              _hash([window.id, window.durationSeconds]),
        };
        if (rules.alerted.keys.every(recovered.contains)) {
          await _dismiss(id, provider);
        }
        return;
      }
      // Foreground use elsewhere and quiet hours suppress new delivery. They
      // do not acknowledge a historical alert; confirmed recovery above does.
      if (_foreground ||
          !rules.notifications ||
          rules.quietAt(clock().toLocal())) {
        return;
      }
      if (!changed || !current()) {
        return;
      }
      final claimed = QuotaMonitorRules(
        source: rules.source,
        account: rules.account,
        token: rules.token,
        notifications: rules.notifications,
        wifiOnly: rules.wifiOnly,
        quietStart: rules.quietStart,
        quietEnd: rules.quietEnd,
        alerted: markers,
        threshold: rules.threshold,
      );
      final durable = await BudgetPersistence.update(
        preferences: store.prefs,
        key: key(id),
        changes: {provider.name: claimed.toJson()},
        isCurrent: current,
        isProfilePresent: () => isReadable(id),
      );
      if (durable == null || !current()) {
        return;
      }
      if (!_fresh(snapshot)) {
        await BudgetPersistence.update(
          preferences: store.prefs,
          key: key(id),
          changes: {provider.name: rules.toJson()},
          isCurrent: current,
          isProfilePresent: () => isReadable(id),
        );
        return;
      }
      final shown = await alert(
        profileID: id,
        key: _alertKey(id, provider),
        token: rules.token,
      );
      if (!current() || !_fresh(snapshot)) {
        await _dismiss(id, provider);
        return;
      }
      if (!shown) {
        // Retry on a later scheduled check; an unavailable native sink is not a delivery.
        await BudgetPersistence.update(
          preferences: store.prefs,
          key: key(id),
          changes: {provider.name: rules.toJson()},
          isCurrent: current,
          isProfilePresent: () => isReadable(id),
        );
      }
    } catch (_) {
      publish(QuotaMonitorStatus.unavailable);
    } finally {
      try {
        gateway?.close();
      } catch (_) {}
      if (identical(_gateway, gateway)) {
        _gateway = null;
        _readingProfile = null;
      }
    }
  }

  Future<void> _dismiss(String id, QuotaProvider provider) async {
    try {
      await dismiss(_alertKey(id, provider));
    } catch (_) {}
  }

  void removeProfile(String id) {
    _blocked.add(id);
    _epochs[id] = (_epochs[id] ?? 0) + 1;
    if (_readingProfile == id) {
      _closeGateway();
    }
    for (final provider in QuotaProvider.values) {
      _observations.remove(_id(id, provider));
      _expiry.remove(_id(id, provider))?.cancel();
      unawaited(_dismiss(id, provider));
    }
    if (!_disposed) {
      notifyListeners();
    }
  }

  Future<void> drain(String id) async {
    await _writes[id];
    await _refresh;
  }

  @override
  void dispose() {
    if (_disposed) {
      return;
    }
    _disposed = true;
    _runtimeEpoch++;
    _timer?.cancel();
    _closeGateway();
    _observations.clear();
    for (final timer in _expiry.values) {
      timer.cancel();
    }
    _expiry.clear();
    super.dispose();
  }
}
