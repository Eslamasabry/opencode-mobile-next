import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../background/usage_widget_snapshot.dart';
import '../domain/usage_subscriptions.dart';
import 'notification_preferences.dart';
import 'usage_subscription_alerts.dart';

/// What a [UsageSource] is asked to read.
class UsageReadContext {
  const UsageReadContext({
    required this.connections,
    required this.now,
    this.force = false,
    this.background = false,
    this.lastReading,
    this.signedIn,
  });
  final List<UsageConnection> connections;
  final DateTime now;
  final bool force;

  /// The app is in the background: readers that cost a request on a computer
  /// keep to their slower cadence.
  final bool background;

  /// The last reading kept for a connection and provider, if any. Sources use
  /// it to tell "used before" from "never used".
  final UsageReading? Function(String connectionId, String providerId)?
  lastReading;

  /// The provider ids the last refresh saw signed in on a connection.
  final Set<String> Function(String connectionId)? signedIn;
}

/// What one source learned. Sources never connect to or switch the active
/// server; they use what is open or the short-lived gateways the monitors use.
class UsageSourceResult {
  const UsageSourceResult({
    this.readings = const [],
    this.signIns = const [],
    this.failures = const [],
    this.unreached = const {},
    this.authoritativeSignIns = const {},
  });

  final List<UsageReading> readings;
  final List<UsageSignIn> signIns;
  final List<UsageFailure> failures;

  /// Connections the source tried and could not reach at all.
  final Set<String> unreached;

  /// Connections whose [signIns] list is complete: a provider missing from it
  /// is signed out there.
  final Set<String> authoritativeSignIns;
}

abstract interface class UsageSource {
  Future<UsageSourceResult> read(UsageReadContext context);
}

/// A rule the old per-source quota monitor already holds.
class LegacyQuotaRule {
  const LegacyQuotaRule({
    required this.connectionId,
    required this.providerId,
    required this.accountRef,
    required this.threshold,
  });
  final String connectionId, providerId, accountRef;
  final double threshold;
}

/// One collector reading that should be watched at [threshold].
class QuotaWatch {
  const QuotaWatch({
    required this.connectionId,
    required this.providerId,
    required this.threshold,
  });
  final String connectionId, providerId;
  final double threshold;
}

/// The bridge to `ProviderQuotaMonitor`, which keeps its own timer, delivery
/// and dedupe markers. The controller decides what is watched and at what
/// level; the bridge enrols and adjusts the monitor to match.
abstract interface class UsageAlertBridge {
  List<LegacyQuotaRule> legacyRules();
  Future<void> reconcile(List<QuotaWatch> wanted, {required bool alertsOn});
}

/// An alert bridge that also says which readings it already alerts for, so
/// the second delivery path does not repeat them.
abstract interface class UsageAlertCoverage {
  bool covers(String connectionId, String providerId);
}

/// Subscriptions across every connection, with the alert state that goes with
/// them. See docs/design/usage-subscriptions-contract-2026-10-13.md.
class UsageSubscriptionsController extends ChangeNotifier {
  UsageSubscriptionsController({
    required this.prefs,
    required this.connections,
    required this.sources,
    required this.sharedRules,
    required this.setQuotaAlerts,
    this.alertBridge,
    this.readerNotes,
    this.subscriptionAlerts,
    this.widget,
    DateTime Function()? clock,
    this.foregroundInterval = const Duration(minutes: 5),
    this.backgroundInterval = const Duration(minutes: 15),
    this.sourceTimeout = const Duration(seconds: 25),
  }) : clock = clock ?? DateTime.now {
    _widgetSettings = UsageWidgetSettings.load(prefs);
    _loadAlerts();
    for (final c in connections()) {
      _loadCache(c.id);
    }
    _rebuild(loaded: false);
  }

  static const alertsKey = 'oc.usageAlerts.v1';
  static String lastKey(String connectionId) => 'oc.usageLast.$connectionId';

  /// The default level every readable subscription is watched at (D4).
  static const defaultAlertThreshold = 80.0;

  final SharedPreferences prefs;
  final List<UsageConnection> Function() connections;
  final List<UsageSource> sources;
  final SharedNotifyRules Function() sharedRules;
  final Future<void> Function(bool value) setQuotaAlerts;
  final UsageAlertBridge? alertBridge;

  /// What a computer reader last said (a plain reason), for Details.
  final UsageReaderNotes? readerNotes;

  /// The second alert path: plans no collector rule covers, and the reset
  /// message (D4, D5).
  final SubscriptionAlertDelivery? subscriptionAlerts;
  final UsageWidgetSnapshot? widget;
  final DateTime Function() clock;
  final Duration foregroundInterval, backgroundInterval, sourceTimeout;

  // connection id -> provider id -> last reading
  final _cache = <String, Map<String, UsageReading>>{};
  final _signIns = <String, List<UsageSignIn>>{};
  final _authoritative = <String>{};
  // Connections the last refresh could not reach: their state is unknown.
  final _offline = <String>{};
  UsageSnapshot _snapshot = const UsageSnapshot();
  Future<void>? _refresh;
  Timer? _timer;
  bool _foreground = true, _backgroundAllowed = false, _disposed = false;
  DateTime? _refreshedAt;
  bool _loaded = false;
  int _epoch = 0;

  double _default = defaultAlertThreshold;
  bool _resetDefault = false;
  final _overrides = <String, UsageAlertOverride>{};
  bool _migrated = false;
  late UsageWidgetSettings _widgetSettings;

  UsageSnapshot get snapshot => _snapshot;
  bool get refreshing => _refresh != null;
  bool get runningAllowed => !_disposed && (_foreground || _backgroundAllowed);

  /// Why the reader on [connectionId] could not read, or null.
  ComputerUsageProblem? readerProblemFor(String connectionId) =>
      readerNotes?.problemFor(connectionId);

  UsageSubscription? subscription(String key) {
    for (final s in _snapshot.subscriptions) {
      if (s.key == key) return s;
    }
    return null;
  }

  // -------------------------------------------------------------- refresh

  void start() {
    if (_disposed) return;
    _schedule();
    unawaited(refresh());
  }

  void setRuntime({required bool foreground, required bool backgroundAllowed}) {
    if (_disposed) return;
    if (_foreground == foreground && _backgroundAllowed == backgroundAllowed) {
      return;
    }
    _foreground = foreground;
    _backgroundAllowed = backgroundAllowed;
    _timer?.cancel();
    _timer = null;
    if (runningAllowed) {
      _schedule();
      unawaited(refresh());
    }
  }

  void _schedule() {
    _timer?.cancel();
    _timer = null;
    if (!runningAllowed) return;
    _timer = Timer(_foreground ? foregroundInterval : backgroundInterval, () {
      _timer = null;
      unawaited(refresh());
    });
  }

  Future<void> refresh({bool force = false}) {
    if (!runningAllowed) return Future.value();
    final running = _refresh;
    if (running != null) return running;
    final operation = Future<void>.microtask(() => _read(force));
    _refresh = operation;
    notifyListeners();
    return operation.whenComplete(() {
      _refresh = null;
      if (!_disposed) {
        _schedule();
        notifyListeners();
      }
    });
  }

  Future<void> _read(bool force) async {
    final epoch = _epoch;
    final list = connections();
    for (final c in list) {
      _loadCache(c.id);
    }
    final context = UsageReadContext(
      connections: list,
      now: clock(),
      force: force,
      background: !_foreground,
      lastReading: (connection, provider) => _cache[connection]?[provider],
      signedIn: (connection) => {
        for (final s in _signIns[connection] ?? const <UsageSignIn>[])
          s.providerId,
      },
    );
    final results = await Future.wait([
      for (final source in sources)
        source
            .read(context)
            .timeout(sourceTimeout)
            .catchError((Object _) => const UsageSourceResult()),
    ]);
    if (_disposed || epoch != _epoch) return;
    final now = clock();
    final known = {for (final c in list) c.id};
    final touched = <String>{};

    final unreached = <String>{for (final r in results) ...r.unreached};
    _offline
      ..clear()
      ..addAll(unreached.intersection(known));
    for (final id in unreached) {
      for (final reading in (_cache[id] ?? const {}).values.toList()) {
        if (!reading.hasNumbers) continue;
        _cache[id]![reading.providerId] = reading.copyWith(
          problem: UsageProblem.offline,
          checkedAt: now,
        );
        touched.add(id);
      }
    }
    for (final result in results) {
      for (final reading in result.readings) {
        if (!known.contains(reading.connectionId)) continue;
        final slot = _cache.putIfAbsent(reading.connectionId, () => {});
        final old = slot[reading.providerId];
        final resolvedAccount = reading.accountRef ?? old?.accountRef;
        // The older of two numbers never replaces the newer.
        if (old != null &&
            old.hasNumbers &&
            old.problem == null &&
            reading.hasNumbers &&
            old.fetchedAt.isAfter(reading.fetchedAt)) {
          if (old.accountRef == null && resolvedAccount != null) {
            slot[reading.providerId] = old.copyWith(
              accountRef: resolvedAccount,
              checkedAt: now,
            );
            touched.add(reading.connectionId);
          }
          continue;
        }
        slot[reading.providerId] = reading.copyWith(
          clearProblem: true,
          checkedAt: now,
          accountRef: resolvedAccount,
        );
        touched.add(reading.connectionId);
      }
      for (final failure in result.failures) {
        if (!known.contains(failure.connectionId)) continue;
        final slot = _cache.putIfAbsent(failure.connectionId, () => {});
        final old = slot[failure.providerId];
        if (old != null) {
          slot[failure.providerId] = old.copyWith(
            problem: failure.problem,
            checkedAt: now,
          );
        } else if (failure.problem == UsageProblem.broken) {
          slot[failure.providerId] = UsageReading(
            providerId: failure.providerId,
            displayName: failure.providerId,
            connectionId: failure.connectionId,
            status: UsageReadingStatus.noLimits,
            fetchedAt: now,
            checkedAt: now,
            problem: UsageProblem.broken,
          );
        } else {
          continue;
        }
        touched.add(failure.connectionId);
      }
      for (final id in result.authoritativeSignIns) {
        _signIns[id] = [
          for (final s in result.signIns)
            if (s.connectionId == id) s,
        ];
        _authoritative.add(id);
      }
      for (final id in result.unreached) {
        _authoritative.remove(id);
      }
    }
    // A reached connection is no longer offline.
    for (final id in known.difference(unreached)) {
      for (final reading in (_cache[id] ?? const {}).values.toList()) {
        if (reading.problem == UsageProblem.offline) {
          _cache[id]![reading.providerId] = reading.copyWith(
            clearProblem: true,
          );
          touched.add(id);
        }
      }
    }
    // Sign-ins seen without a reader (OpenCode servers): kept per connection.
    for (final result in results) {
      for (final s in result.signIns) {
        if (!result.authoritativeSignIns.contains(s.connectionId)) {
          final list = _signIns.putIfAbsent(s.connectionId, () => []);
          list.removeWhere(
            (x) =>
                x.providerId == s.providerId &&
                x.connectionId == s.connectionId,
          );
          list.add(s);
        }
      }
    }
    _refreshedAt = now;
    _loaded = true;
    for (final id in touched) {
      await _saveCache(id);
    }
    await _migrateOnce();
    _rebuild(loaded: true);
    await _syncAlerts();
    await _publishWidget();
  }

  // ------------------------------------------------------------- caching

  void _loadCache(String id) {
    if (_cache.containsKey(id)) return;
    final slot = <String, UsageReading>{};
    try {
      final raw = prefs.getString(lastKey(id));
      if (raw != null && raw.length <= 262144) {
        final data = jsonDecode(raw);
        if (data is List) {
          for (final item in data.take(32)) {
            final reading = UsageReading.fromJson(item, id);
            if (reading != null) slot[reading.providerId] = reading;
          }
        }
      }
    } catch (_) {
      // An unreadable cache is just an empty one.
    }
    _cache[id] = slot;
  }

  Future<void> _saveCache(String id) async {
    final slot = _cache[id];
    if (slot == null) return;
    try {
      if (slot.isEmpty) {
        await prefs.remove(lastKey(id));
      } else {
        await prefs.setString(
          lastKey(id),
          jsonEncode([for (final r in slot.values) r.toJson()]),
        );
      }
    } catch (_) {}
  }

  void _rebuild({required bool loaded}) {
    final list = connections();
    final readings = <UsageReading>[
      for (final c in list) ...?_cache[c.id]?.values,
    ];
    final signIns = <UsageSignIn>[for (final c in list) ...?_signIns[c.id]];
    _snapshot = mergeUsageSubscriptions(
      now: clock(),
      connections: list,
      readings: readings,
      signIns: signIns,
      authoritativeSignIns: _authoritative,
      offlineConnections: _offline,
      defaultThreshold: _default,
      overrides: _overrides,
      notifyOnResetDefault: _resetDefault,
      refreshedAt: _refreshedAt,
      loaded: loaded || _loaded,
    );
    if (!_disposed) notifyListeners();
  }

  // -------------------------------------------------------------- alerts

  bool get alertsOn => sharedRules().quotaAlerts;

  Future<void> setAlertsOn(bool value) async {
    await setQuotaAlerts(value);
    if (_disposed) return;
    await _syncAlerts();
    notifyListeners();
  }

  double get defaultThreshold => _default;
  bool get notifyOnResetDefault => _resetDefault;

  List<UsageSubscription> get alertOverrides => [
    for (final s in _snapshot.subscriptions)
      if (s.hasAlertOverride) s,
  ];

  Future<bool> setDefaultThreshold(double percent) async {
    if (!percent.isFinite || percent <= 0 || percent > 100) return false;
    _default = percent;
    return _commitAlerts();
  }

  Future<bool> setNotifyOnResetDefault(bool value) async {
    _resetDefault = value;
    return _commitAlerts();
  }

  Future<bool> setThresholdFor(String key, double? percent) async {
    if (percent != null &&
        (!percent.isFinite || percent <= 0 || percent > 100)) {
      return false;
    }
    final old = _overrides[key];
    _putOverride(
      key,
      UsageAlertOverride(threshold: percent, notifyOnReset: old?.notifyOnReset),
    );
    return _commitAlerts();
  }

  Future<bool> setNotifyOnReset(String key, bool value) async {
    final old = _overrides[key];
    _putOverride(
      key,
      UsageAlertOverride(threshold: old?.threshold, notifyOnReset: value),
    );
    return _commitAlerts();
  }

  void _putOverride(String key, UsageAlertOverride value) {
    if (value.isEmpty) {
      _overrides.remove(key);
    } else {
      _overrides[key] = value;
    }
  }

  Future<bool> _commitAlerts() async {
    final saved = await _saveAlerts();
    _rebuild(loaded: _loaded);
    await _syncAlerts();
    return saved;
  }

  void _loadAlerts() {
    try {
      final raw = prefs.getString(alertsKey);
      if (raw == null) return;
      final data = jsonDecode(raw) as Map;
      final d = data['default'];
      if (d is num && d.isFinite && d > 0 && d <= 100) _default = d.toDouble();
      _resetDefault = data['resetDefault'] == true;
      _migrated = data['migrated'] == true;
      final overrides = data['overrides'];
      if (overrides is Map) {
        for (final entry in overrides.entries.take(256)) {
          final v = entry.value;
          if (entry.key is! String || v is! Map) continue;
          final t = v['t'];
          _overrides[entry.key as String] = UsageAlertOverride(
            threshold: t is num && t.isFinite && t > 0 && t <= 100
                ? t.toDouble()
                : null,
            notifyOnReset: v['r'] is bool ? v['r'] as bool : null,
          );
        }
      }
    } catch (_) {
      // Start from the defaults rather than keep unknown content.
    }
  }

  Future<bool> _saveAlerts() async {
    try {
      return await prefs.setString(
        alertsKey,
        jsonEncode({
          'v': 1,
          'default': _default,
          'resetDefault': _resetDefault,
          'migrated': _migrated,
          'overrides': {
            for (final e in _overrides.entries)
              e.key: {'t': ?e.value.threshold, 'r': ?e.value.notifyOnReset},
          },
        }),
      );
    } catch (_) {
      return false;
    }
  }

  /// Keeps any threshold the person chose under the old per-source rules.
  /// The rules stay where they are; the monitor still needs them.
  Future<void> _migrateOnce() async {
    if (_migrated) return;
    final bridge = alertBridge;
    if (bridge == null) return;
    for (final rule in bridge.legacyRules()) {
      if ((rule.threshold - defaultAlertThreshold).abs() < 0.001) continue;
      final key = usageSubscriptionKey(
        rule.providerId,
        rule.accountRef,
        rule.connectionId,
      );
      final old = _overrides[key];
      _overrides[key] = UsageAlertOverride(
        threshold: old?.threshold ?? rule.threshold,
        notifyOnReset: old?.notifyOnReset,
      );
    }
    _migrated = true;
    await _saveAlerts();
  }

  /// Watches every readable collector subscription at its effective level.
  Future<void> _syncAlerts() async {
    final bridge = alertBridge;
    if (_disposed) return;
    await _deliverSubscriptionAlerts();
    if (bridge == null) return;
    final wanted = <QuotaWatch>[];
    for (final s in _snapshot.subscriptions) {
      final from = s.readFrom;
      if (from == null ||
          s.windows.isEmpty ||
          s.state == SubscriptionState.offlineStale ||
          s.state == SubscriptionState.readerBroken) {
        continue;
      }
      wanted.add(
        QuotaWatch(
          connectionId: from.id,
          providerId: s.providerId,
          threshold: s.alertThreshold,
        ),
      );
    }
    try {
      await bridge.reconcile(wanted, alertsOn: alertsOn);
    } catch (_) {
      // Alerts are best effort; the next refresh tries again.
    }
  }

  /// Whether a notification for [token] names a subscription read from
  /// [profileId]; true for the ones this controller sent.
  bool ownsAlertToken(String profileId, String token) =>
      subscriptionAlerts?.owns(_snapshot, profileId, token) ?? false;

  Future<void> _deliverSubscriptionAlerts() async {
    final alerts = subscriptionAlerts;
    if (alerts == null) return;
    final bridge = alertBridge;
    try {
      await alerts.evaluate(
        _snapshot,
        alertsOn: alertsOn,
        foreground: _foreground,
        covered: (s) {
          final from = s.readFrom;
          return from != null &&
              bridge is UsageAlertCoverage &&
              (bridge as UsageAlertCoverage).covers(from.id, s.providerId);
        },
      );
    } catch (_) {
      // Alerts are best effort; the next refresh tries again.
    }
  }

  // -------------------------------------------------------------- widget

  UsageWidgetSettings get widgetSettings => _widgetSettings;

  Future<void> setWidgetSettings(UsageWidgetSettings value) async {
    _widgetSettings = value;
    try {
      await prefs.setString(UsageWidgetSettings.key, value.toJson());
    } catch (_) {}
    await _publishWidget();
    if (!_disposed) notifyListeners();
  }

  Future<void> _publishWidget() async {
    try {
      await widget?.publishSubscriptions(_snapshot, _widgetSettings);
    } catch (_) {}
  }

  // ------------------------------------------------------------- deletion

  /// A saved server was removed: its cached numbers, sign-ins and the
  /// overrides of subscriptions that no longer exist anywhere go with it.
  void removeProfile(String profileId) {
    _epoch++;
    _cache.remove(profileId);
    _signIns.remove(profileId);
    _authoritative.remove(profileId);
    _offline.remove(profileId);
    unawaited(prefs.remove(lastKey(profileId)).catchError((_) => false));
    _rebuild(loaded: _loaded);
    final alive = {for (final s in _snapshot.subscriptions) s.key};
    final before = _overrides.length;
    _overrides.removeWhere((key, _) => !alive.contains(key));
    if (_overrides.length != before) unawaited(_saveAlerts());
    unawaited(subscriptionAlerts?.prune(alive) ?? Future<void>.value());
    unawaited(_publishWidget());
  }

  @override
  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _epoch++;
    _timer?.cancel();
    super.dispose();
  }
}
