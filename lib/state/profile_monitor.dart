import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:crypto/crypto.dart';

import '../api2/models.dart' show Api2FormInfo;
import '../api/models.dart';
import '../domain/profile_monitor.dart';
import '../domain/server_gateway.dart';
import 'profiles.dart';

export '../domain/profile_monitor.dart';

typedef MonitorGatewayPair = ({
  ServerGateway gateway,
  ServerOperationsGateway operations,
});
typedef MonitorGatewayFactory =
    MonitorGatewayPair Function(ServerProfile profile);
typedef MonitorAlert =
    Future<bool> Function(
      String profileID,
      MonitoredRequest request,
      String key,
      String token,
    );

/// Sequential, bounded snapshots: no subscriptions, active-connection mutation,
/// persistent session titles, or background-service ownership.
class ProfileMonitor extends ChangeNotifier {
  static const unsupportedProfileMessage =
      'Background attention is unavailable for this connection. Open the conversation to review current requests.';

  ProfileMonitor({
    required this.store,
    required this.createGateway,
    required this.isReadable,
    required this.networkWifi,
    required this.alert,
    required this.dismiss,
    this.alertsAllowed,
    DateTime Function()? now,
    this.foregroundInterval = const Duration(minutes: 1),
    this.backgroundInterval = const Duration(minutes: 5),
    this.timeout = const Duration(seconds: 8),
  }) : _now = now ?? DateTime.now;
  final ProfileStore store;
  final MonitorGatewayFactory createGateway;
  final bool Function(String) isReadable;

  /// null means the platform cannot establish Wi-Fi policy. Fail closed.
  final Future<bool?> Function() networkWifi;
  final MonitorAlert alert;
  final bool Function(String)? alertsAllowed;
  final Future<bool> Function(String) dismiss;
  final DateTime Function() _now;
  final Duration foregroundInterval, backgroundInterval, timeout;
  // Salted, runtime-only identity: never retain another raw credential copy.
  final _identitySalt = List<int>.generate(
    32,
    (_) => Random.secure().nextInt(256),
  );
  final _sources = <String, String>{};
  String _source(ServerProfile p) => Hmac(sha256, _identitySalt)
      .convert(
        utf8.encode(
          jsonEncode([p.baseUrl, p.username, p.password, p.flavor.name]),
        ),
      )
      .toString();
  bool _sameSource(String id) {
    final profile = store.profiles.where((p) => p.id == id).firstOrNull;
    return profile != null && _sources[id] == _source(profile);
  }

  Future<void> _reconcileSource(ServerProfile profile) async {
    final source = _source(profile);
    final prior = _sources[profile.id];
    if (prior == source) return;
    _sources[profile.id] = source;
    if (prior == null) return;
    _epochs[profile.id] = (_epochs[profile.id] ?? 0) + 1;
    _invalidatePoll(profile.id);
    _snapshots.remove(profile.id);
    _next.remove(profile.id);
    _failures.remove(profile.id);
    await _dismissProfile(profile.id);
    await store.prefs.remove(routesKey(profile.id));
    await _forgetBusy(profile.id);
  }

  final _snapshots = <String, ProfileAttentionSnapshot>{};
  final _failures = <String, int>{};
  final _epochs = <String, int>{};
  final _pollGenerations = <String, int>{};
  final _blocked = <String>{};
  final _next = <String, DateTime>{};
  final _alerts = <String, Set<String>>{};
  final _writes = <String, Future<void>>{};
  final _polls = <String, Future<void>>{};
  String? _activeProfileID;
  Timer? _timer;
  Future<void>? _refresh;
  ServerGateway? _activeGateway;
  bool _foreground = true, _backgroundAllowed = false, _disposed = false;
  int _cursor = 0;
  bool get refreshing => _refresh != null;
  bool get runningAllowed => _foreground || _backgroundAllowed;
  Map<String, ProfileAttentionSnapshot> get snapshots =>
      Map.unmodifiable(_snapshots);

  /// Background attention needs a pollable pending-request surface. Codex
  /// sessions do not expose one, so an enabled legacy rule remains stored but
  /// is retired before any transport factory or credential path is touched.
  bool supportsProfile(ServerProfile profile) =>
      profile.backend != ServerBackend.codex;

  int _beginPoll(String id) {
    final generation = (_pollGenerations[id] ?? 0) + 1;
    _pollGenerations[id] = generation;
    return generation;
  }

  void _invalidatePoll(String id) {
    _pollGenerations[id] = (_pollGenerations[id] ?? 0) + 1;
  }

  /// Durable route scope deliberately excludes credentials.
  static String routeSourceIdentity(ServerProfile profile) => sha256
      .convert(
        utf8.encode(
          jsonEncode([profile.baseUrl, profile.username, profile.flavor.name]),
        ),
      )
      .toString();
  static String rulesKey(String id) => 'oc.notifyRules.$id';
  static String alertsKey(String id) => 'oc.monitorAlerts.$id';
  static String routesKey(String id) => 'oc.monitorRoutes.$id';
  static String busyIntervalsKey(String id) => 'oc.monitorBusy.$id';

  /// The longest silence after which a busy observation no longer joins the
  /// one before it. Covers the background interval, the deepest failure
  /// backoff (15 min) and one more poll; anything longer — the app was
  /// killed, the phone slept, the server was unreachable — could hide an
  /// idle→busy turn, so the interval restarts rather than claiming the
  /// session ran continuously through the gap.
  static const busyObservationGap = Duration(minutes: 20);

  final _busy = <String, Map<String, ObservedBusyInterval>>{};

  Map<String, ObservedBusyInterval> _busyIntervals(String id) =>
      _busy.putIfAbsent(id, () {
        try {
          final raw = store.prefs.getString(busyIntervalsKey(id));
          if (raw == null) return {};
          final decoded = jsonDecode(raw);
          if (decoded is! List) return {};
          return {
            for (final interval
                in decoded
                    .map(ObservedBusyInterval.fromJson)
                    .whereType<ObservedBusyInterval>())
              interval.sessionID: interval,
          };
        } catch (_) {
          return {};
        }
      });

  /// Folds one successful status read into the profile's busy intervals.
  ///
  /// Only a complete, successful poll gets here: a failed or partial poll
  /// must neither end an interval (the session may still be working) nor
  /// extend it (nothing was observed). A session that is idle or absent from
  /// the status map ends its interval; a session seen busy again after more
  /// than [busyObservationGap] starts a fresh one.
  Future<List<ObservedBusyInterval>> _observeBusy(
    String id,
    Map<String, String> statuses,
    Map<String, Session> sessions,
    DateTime now,
    String? directory,
    String? workspace,
  ) async {
    final previous = _busyIntervals(id);
    final next = <String, ObservedBusyInterval>{};
    for (final entry in statuses.entries) {
      if (entry.value == 'idle') continue;
      final session = sessions[entry.key];
      final prior = previous[entry.key];
      final observedDirectory = session?.directory ?? directory;
      final observedWorkspace = session?.workspaceID ?? workspace;
      final continues =
          prior != null &&
          prior.directory == observedDirectory &&
          prior.workspace == observedWorkspace &&
          !prior.lastObservedBusyAt.isAfter(now) &&
          now.difference(prior.lastObservedBusyAt) <= busyObservationGap;
      next[entry.key] = continues
          ? prior.observedAgainAt(
              now,
              title: session?.title,
              directory: session?.directory,
              workspace: session?.workspaceID,
            )
          : ObservedBusyInterval(
              sessionID: entry.key,
              firstObservedBusyAt: now,
              lastObservedBusyAt: now,
              title: session?.title,
              directory: observedDirectory,
              workspace: observedWorkspace,
            );
    }
    _busy[id] = next;
    try {
      // Observation persistence is best effort. A reminder separately requires
      // its complete interval and dispatch claim to be durably saved first.
      if (next.isEmpty) {
        await store.prefs.remove(busyIntervalsKey(id));
      } else {
        await store.prefs.setString(
          busyIntervalsKey(id),
          jsonEncode([for (final interval in next.values) interval.toJson()]),
        );
      }
    } catch (_) {}
    return next.values.toList()
      ..sort((a, b) => a.firstObservedBusyAt.compareTo(b.firstObservedBusyAt));
  }

  Future<void> _forgetBusy(String id) async {
    _busy.remove(id);
    try {
      await store.prefs.remove(busyIntervalsKey(id));
    } catch (_) {}
  }

  Future<bool> _claimCheckIn(
    String id,
    MonitoredRequest request,
    bool Function() current,
  ) async {
    if (!current()) return false;
    final intervals = _busyIntervals(id);
    final interval = intervals[request.sessionID];
    if (interval == null ||
        interval.id != request.id ||
        interval.reminderClaimed) {
      return false;
    }
    final claimed = interval.claimReminder();
    final next = {...intervals, request.sessionID: claimed};
    try {
      if (!await store.prefs.setString(
        busyIntervalsKey(id),
        jsonEncode([for (final item in next.values) item.toJson()]),
      )) {
        await store.prefs.reload();
        return false;
      }
    } catch (_) {
      try {
        await store.prefs.reload();
      } catch (_) {}
      return false;
    }
    if (!current()) return false;
    _busy[id] = next;
    return true;
  }

  Map<String, dynamic> _routes(String id) {
    try {
      return Map<String, dynamic>.from(
        jsonDecode(store.prefs.getString(routesKey(id)) ?? '{}') as Map,
      );
    } catch (_) {
      return {};
    }
  }

  MonitoredRoute? routeForToken(String id, String token) {
    if (!isReadable(id) || _blocked.contains(id) || !rulesFor(id).enabled) {
      return null;
    }
    try {
      final profile = store.profiles.where((p) => p.id == id).firstOrNull;
      if (profile == null || !supportsProfile(profile)) return null;
      final route = MonitoredRoute.fromJson(
        id,
        Map<String, dynamic>.from(_routes(id)[token] as Map),
      );
      if ((_sources.containsKey(id) && !_sameSource(id)) ||
          route.createdAt.isAfter(_now()) ||
          profile.baseUrl != route.serverUrl ||
          route.sourceIdentity != routeSourceIdentity(profile) ||
          _now().difference(route.createdAt) > const Duration(days: 1)) {
        return null;
      }
      return route;
    } catch (_) {
      return null;
    }
  }

  Future<String?> _saveRoute(String id, MonitoredRequest request) async {
    if (_blocked.contains(id)) return null;
    final profile = store.profiles.where((p) => p.id == id).firstOrNull;
    if (profile == null) return null;
    final random = Random.secure();
    final token = base64UrlEncode(
      List.generate(24, (_) => random.nextInt(256)),
    ).replaceAll('=', '');
    final routes = _routes(id);
    while (routes.length >= 32) {
      routes.remove(routes.keys.first);
    }
    routes[token] = MonitoredRoute(
      profileID: id,
      requestID: request.id,
      sessionID: request.sessionID,
      kind: request.kind,
      createdAt: _now(),
      serverUrl: profile.baseUrl,
      sourceIdentity: routeSourceIdentity(profile),
      directory: request.directory,
      workspace: request.workspace,
    ).toJson();
    if (!await store.prefs.setString(routesKey(id), jsonEncode(routes))) {
      return null;
    }
    return token;
  }

  ProfileNotifyRules rulesFor(String id) {
    try {
      final raw = store.prefs.getString(rulesKey(id));
      if (raw != null) {
        return ProfileNotifyRules.fromJson(
          Map<String, dynamic>.from(jsonDecode(raw) as Map),
        );
      }
    } catch (_) {}
    return const ProfileNotifyRules();
  }

  ProfileAttentionSnapshot snapshotFor(String id) {
    final rules = rulesFor(id);
    if (!rules.enabled || _blocked.contains(id)) {
      return ProfileAttentionSnapshot(
        profileID: id,
        status: ProfileMonitorStatus.disabled,
      );
    }
    final profile = store.profiles.where((p) => p.id == id).firstOrNull;
    if (profile == null || !supportsProfile(profile)) {
      return ProfileAttentionSnapshot(
        profileID: id,
        status: ProfileMonitorStatus.unavailable,
      );
    }
    final value = _sameSource(id) ? _snapshots[id] : null;
    if (!runningAllowed) {
      return ProfileAttentionSnapshot(
        profileID: id,
        status: ProfileMonitorStatus.paused,
        checkedAt: value?.checkedAt,
      );
    }
    if (value == null) {
      return ProfileAttentionSnapshot(
        profileID: id,
        status: ProfileMonitorStatus.waiting,
      );
    }
    final location = store.locationFor(id);
    final maxAge = (_foreground ? foregroundInterval : backgroundInterval) * 2;
    if (value.isCurrent &&
        (location?.directory != value.directory ||
            location?.workspace != value.workspace ||
            value.checkedAt == null ||
            value.checkedAt!.isAfter(_now()) ||
            _now().difference(value.checkedAt!) > maxAge)) {
      return ProfileAttentionSnapshot(
        profileID: id,
        status: ProfileMonitorStatus.waiting,
        checkedAt: value.checkedAt,
        nextCheckAt: _next[id],
      );
    }
    return value;
  }

  int get currentPendingTotal => store.profiles
      .where((p) => isReadable(p.id))
      .fold(0, (sum, p) => sum + (snapshotFor(p.id).pendingCount ?? 0));
  int get unknownProfileCount => store.profiles
      .where((p) => isReadable(p.id) && !snapshotFor(p.id).isCurrent)
      .length;
  void start() {
    if (_disposed) return;
    _schedule();
    unawaited(refresh());
  }

  void setRuntime({required bool foreground, required bool backgroundAllowed}) {
    if (_disposed) return;
    final changed =
        _foreground != foreground || _backgroundAllowed != backgroundAllowed;
    _foreground = foreground;
    _backgroundAllowed = backgroundAllowed;
    if (!runningAllowed) {
      _timer?.cancel();
      _timer = null;
      _activeGateway?.close();
    } else if (changed) {
      if (foreground) _next.clear();
      _schedule();
      unawaited(refresh());
    }
    if (changed) notifyListeners();
  }

  void _schedule() {
    _timer?.cancel();
    if (_disposed ||
        !runningAllowed ||
        !store.profiles.any(
          (p) =>
              rulesFor(p.id).enabled && isReadable(p.id) && supportsProfile(p),
        )) {
      return;
    }
    _timer = Timer(_foreground ? foregroundInterval : backgroundInterval, () {
      _timer = null;
      unawaited(refresh());
    });
  }

  Future<void> setEnabled(String id, bool enabled) =>
      setRules(id, rulesFor(id).copyWith(enabled: enabled));
  Future<void> setRules(String id, ProfileNotifyRules value) {
    final profile = store.profiles.where((p) => p.id == id).firstOrNull;
    if (_disposed ||
        _blocked.contains(id) ||
        !isReadable(id) ||
        profile == null ||
        !supportsProfile(profile)) {
      return Future.error(StateError('Server unavailable'));
    }
    final operation = (_writes[id] ?? Future<void>.value())
        .catchError((Object _) {})
        .then((_) async {
          if (_disposed || _blocked.contains(id)) return;
          final before = rulesFor(id);
          if (!await store.prefs.setString(
            rulesKey(id),
            jsonEncode(value.toJson()),
          )) {
            throw StateError('Could not save monitoring settings');
          }
          _epochs[id] = (_epochs[id] ?? 0) + 1;
          _invalidatePoll(id);
          if (_activeProfileID == id) _activeGateway?.close();
          _next.remove(id);
          _snapshots.remove(id);
          if (!value.enabled || !value.notifications) {
            await _dismissProfile(id);
          } else if (value.checkInAfterMinutes != before.checkInAfterMinutes) {
            // A changed or removed duration retires posted reminders; the
            // next poll re-evaluates the intervals against the new rule.
            await _dismissCheckIns(id);
          }
          if (!value.enabled) await _forgetBusy(id);
          if (!_disposed) {
            notifyListeners();
            _schedule();
            if (value.enabled) unawaited(refresh());
          }
        });
    _writes[id] = operation;
    return operation;
  }

  /// Synchronously closes admission. Deletion drains writes before key sweep.
  void removeProfile(String id) {
    if (_activeProfileID == id) _activeGateway?.close();
    _blocked.add(id);
    _epochs[id] = (_epochs[id] ?? 0) + 1;
    _invalidatePoll(id);
    _snapshots.remove(id);
    _next.remove(id);
    _failures.remove(id);
    _sources.remove(id);
    _busy.remove(id);
    unawaited(_dismissProfile(id));
    if (!_disposed) notifyListeners();
  }

  Future<void> drain(String id) async {
    try {
      await _writes[id];
    } catch (_) {}
    try {
      await _polls[id];
    } catch (_) {}
    await _dismissProfile(id);
  }

  Set<String> _storedAlertKeys(String id) {
    try {
      return {...?store.prefs.getStringList(alertsKey(id))};
    } catch (_) {
      return {};
    }
  }

  Future<void> _dismissProfile(String id) async {
    final keys = {...?_alerts.remove(id), ..._storedAlertKeys(id)};
    for (final key in keys) {
      try {
        await dismiss(key).timeout(timeout, onTimeout: () => false);
      } catch (_) {
        /* No raw native errors enter state or logs. */
      }
    }
    try {
      await store.prefs.remove(alertsKey(id));
    } catch (_) {}
  }

  /// Retires only the check-in reminders of [id]; request alerts stay.
  Future<void> _dismissCheckIns(String id) async {
    final keys = _alerts.putIfAbsent(id, () => _storedAlertKeys(id));
    for (final key in keys.where(isCheckInAlertKey).toList()) {
      try {
        if (!await dismiss(key).timeout(timeout, onTimeout: () => false)) {
          continue;
        }
      } catch (_) {
        continue;
      }
      keys.remove(key);
    }
    try {
      await store.prefs.setStringList(alertsKey(id), keys.take(256).toList());
    } catch (_) {}
  }

  static bool isCheckInAlertKey(String key) =>
      key.contains(':${MonitoredRequestKind.checkIn.name}%3A');

  Future<void> refresh() {
    if (_disposed || !runningAllowed) return Future.value();
    return _refresh ??= _run().catchError((Object _) {}).whenComplete(() {
      _refresh = null;
      if (!_disposed) {
        _schedule();
        notifyListeners();
      }
    });
  }

  Future<void> _run() async {
    final profiles = store.profiles
        .where(
          (p) =>
              rulesFor(p.id).enabled &&
              isReadable(p.id) &&
              supportsProfile(p) &&
              !_blocked.contains(p.id),
        )
        .toList();
    if (profiles.isEmpty) return;
    bool? wifi;
    if (profiles.any((p) => rulesFor(p.id).wifiOnly)) {
      try {
        // Normalize platform failures before applying the timeout so an early
        // failed platform future always has an error handler attached.
        wifi = await networkWifi()
            .then<bool?>((value) => value, onError: (Object _) => null)
            .timeout(timeout, onTimeout: () => null);
      } catch (_) {
        wifi = null;
      }
    }
    for (var n = 0; n < profiles.length && n < 8; n++) {
      if (_disposed || !runningAllowed) return;
      final profile = profiles[(_cursor + n) % profiles.length];
      await _reconcileSource(profile);
      if (_disposed ||
          !isReadable(profile.id) ||
          _blocked.contains(profile.id)) {
        continue;
      }
      if (_next[profile.id]?.isAfter(_now()) == true) continue;
      final rules = rulesFor(profile.id);
      if (rules.wifiOnly && wifi != true) {
        _snapshots[profile.id] = ProfileAttentionSnapshot(
          profileID: profile.id,
          status: ProfileMonitorStatus.wifiRequired,
        );
        continue;
      }
      final generation = _beginPoll(profile.id);
      final poll = _poll(profile, generation);
      _polls[profile.id] = poll;
      try {
        await poll;
      } finally {
        _polls.remove(profile.id);
      }
    }
    _cursor = (_cursor + 8) % profiles.length;
  }

  static Future<List<PermissionRequest>> readPermissions(
    ServerGateway gateway,
    Duration timeout,
  ) async {
    List<PermissionRequest>? legacy, modern;
    try {
      legacy = await gateway.pendingPermissions().timeout(timeout);
    } catch (_) {}
    try {
      modern = await gateway.pendingPermissionsV2().timeout(timeout);
    } catch (_) {}
    if (legacy == null && modern == null) {
      throw StateError('Attention unavailable');
    }
    return {
      for (final p in legacy ?? const <PermissionRequest>[]) p.id: p,
      for (final p in modern ?? const <PermissionRequest>[]) p.id: p,
    }.values.toList();
  }

  static Future<List<PendingQuestion>> readQuestions(
    ServerGateway gateway,
    ServerOperationsGateway operations,
    Duration timeout,
  ) async {
    List<PendingQuestion>? legacy, modern;
    try {
      legacy = await operations.listQuestions().timeout(timeout);
    } catch (_) {}
    try {
      modern = (await gateway.pendingQuestionsV2().timeout(
        timeout,
      )).map(PendingQuestion.fromJson).toList();
    } catch (_) {}
    if (legacy == null && modern == null) {
      throw StateError('Attention unavailable');
    }
    return {
      for (final p in legacy ?? const <PendingQuestion>[]) p.id: p,
      for (final p in modern ?? const <PendingQuestion>[]) p.id: p,
    }.values.toList();
  }

  Future<void> _poll(ServerProfile profile, int generation) async {
    if (!supportsProfile(profile)) return;
    final id = profile.id, epoch = _epochs[profile.id] ?? 0;
    final location = store.locationFor(id);
    final address = (
      profile.baseUrl,
      profile.username,
      profile.password,
      profile.flavor,
    );
    bool current() =>
        !_disposed &&
        runningAllowed &&
        !_blocked.contains(id) &&
        isReadable(id) &&
        rulesFor(id).enabled &&
        (_pollGenerations[id] ?? 0) == generation &&
        (_epochs[id] ?? 0) == epoch &&
        store.profiles.any(
          (p) =>
              p.id == id &&
              (p.baseUrl, p.username, p.password, p.flavor) == address,
        ) &&
        store.locationFor(id)?.directory == location?.directory &&
        store.locationFor(id)?.workspace == location?.workspace;
    final prior = _snapshots[id];
    _snapshots[id] = ProfileAttentionSnapshot(
      profileID: id,
      status: ProfileMonitorStatus.checking,
      checkedAt: prior?.checkedAt,
    );
    notifyListeners();
    MonitorGatewayPair? pair;
    try {
      if (profile.requiresPasswordReentry ||
          validateServerProfileUrl(
                profile.baseUrl,
                username: profile.username,
                password: profile.password,
              ) !=
              null) {
        throw StateError('Unavailable');
      }
      pair = createGateway(profile);
      _activeGateway = pair.gateway;
      _activeProfileID = id;
      pair.gateway.setLocation(
        directory: location?.directory,
        workspace: location?.workspace,
      );
      pair.operations.setLocation(
        directory: location?.directory,
        workspace: location?.workspace,
      );
      final gateway = pair.gateway;
      final permissions = await readPermissions(gateway, timeout);
      final questions = await readQuestions(gateway, pair.operations, timeout);
      final forms = gateway.capabilities.forms
          ? await gateway.pendingForms().timeout(timeout)
          : const <Api2FormInfo>[];
      final statuses = await gateway.sessionStatuses().timeout(timeout);
      final page = await gateway.sessionPage(limit: 100).timeout(timeout);
      if (!current()) return;
      final sessions = {for (final s in page.items) s.id: s};
      MonitoredRequest row(
        String requestID,
        String sessionID,
        MonitoredRequestKind kind,
      ) {
        final session = sessions[sessionID];
        return MonitoredRequest(
          id: requestID,
          sessionID: sessionID,
          kind: kind,
          title: session?.title,
          directory: session?.directory ?? location?.directory,
          workspace: session?.workspaceID ?? location?.workspace,
        );
      }

      final requests = [
        for (final p in permissions)
          row(p.id, p.sessionID, MonitoredRequestKind.permission),
        for (final q in questions)
          row(q.id, q.sessionID, MonitoredRequestKind.question),
        for (final f in forms)
          row(f.id, f.sessionID, MonitoredRequestKind.form),
      ];
      final unique = {
        for (final r in requests) r.identity: r,
      }.values.take(256).toList();
      final now = _now();
      // Every read above succeeded, so this is a complete observation: the
      // status map may end or extend a busy interval. (A throw lands in the
      // catch below and leaves the intervals exactly as they were.)
      final busy = await _observeBusy(
        id,
        statuses,
        sessions,
        now,
        location?.directory,
        location?.workspace,
      );
      if (!current()) return;
      _failures.remove(id);
      _next[id] = now.add(
        _foreground ? foregroundInterval : backgroundInterval,
      );
      _snapshots[id] = ProfileAttentionSnapshot(
        profileID: id,
        status: requests.length <= 256
            ? ProfileMonitorStatus.current
            : ProfileMonitorStatus.unavailable,
        checkedAt: now,
        directory: location?.directory,
        workspace: location?.workspace,
        requests: List.unmodifiable(unique),
        busyIntervals: List.unmodifiable(busy),
        complete: requests.length <= 256,
        runningCount: statuses.values.where((v) => v != 'idle').length,
        nextCheckAt: _next[id],
      );
      if (!_foreground && _backgroundAllowed && current()) {
        final requestsAllowed = _notificationPolicyCurrent(id);
        final checkInsAllowed = _checkInPolicyCurrent(id);
        if (requestsAllowed || checkInsAllowed) {
          await _publishAlerts(
            id,
            [
              if (requestsAllowed) ...unique,
              if (checkInsAllowed)
                for (final interval in _snapshots[id]!.dueCheckIns(
                  rulesFor(id),
                ))
                  interval.toRequest(),
            ],
            current,
            () => _notificationPolicyCurrent(id) || _checkInPolicyCurrent(id),
          );
        }
      }
    } catch (_) {
      if (!current()) return;
      final attempts = (_failures[id] ?? 0) + 1;
      _failures[id] = attempts;
      final seconds =
          ((_foreground ? foregroundInterval : backgroundInterval).inSeconds *
                  (1 << attempts.clamp(0, 6)))
              .clamp(60, 900);
      _next[id] = _now().add(Duration(seconds: seconds));
      _snapshots[id] = ProfileAttentionSnapshot(
        profileID: id,
        status: ProfileMonitorStatus.unavailable,
        checkedAt: prior?.checkedAt,
        nextCheckAt: _next[id],
      );
    } finally {
      pair?.gateway.close();
      if (identical(_activeGateway, pair?.gateway)) {
        _activeGateway = null;
        _activeProfileID = null;
      }
    }
  }

  /// Reconciles posted alerts with [requests]: anything posted that is no
  /// longer in the list is dismissed, anything new is posted once. Because
  /// a check-in reminder's key names its observed busy interval, an interval
  /// that ends removes its reminder and a later interval posts a new one —
  /// and a key that was posted (or persisted from an earlier process) is
  /// never posted again.
  Future<void> _publishAlerts(
    String id,
    List<MonitoredRequest> requests,
    bool Function() current,
    bool Function() policy,
  ) async {
    if (!current() || !policy()) {
      await _dismissProfile(id);
      return;
    }
    final keys = _alerts.putIfAbsent(id, () => _storedAlertKeys(id));
    final valid = {for (final r in requests) alertKey(id, r)};
    for (final key in keys.difference(valid).toList()) {
      if (!current() || !policy()) {
        await _dismissProfile(id);
        return;
      }
      try {
        final removed = await dismiss(
          key,
        ).timeout(timeout, onTimeout: () => false);
        if (!removed) continue;
      } catch (_) {
        continue;
      }
      if (!current() || !policy()) {
        await _dismissProfile(id);
        return;
      }
      keys.remove(key);
    }
    bool mayDispatch() =>
        current() && policy() && !_foreground && _backgroundAllowed;
    var dispatched = 0;
    for (final request in requests) {
      if (!mayDispatch()) {
        return;
      }
      final key = alertKey(id, request);
      if (keys.contains(key)) continue;
      if (request.kind == MonitoredRequestKind.checkIn &&
          _busyIntervals(id)[request.sessionID]?.reminderClaimed == true) {
        continue;
      }
      if (dispatched >= 8) break;
      dispatched++;
      final String? token;
      try {
        token = await _saveRoute(id, request);
      } catch (_) {
        return;
      }
      if (token == null || !mayDispatch()) {
        return;
      }
      if (request.kind == MonitoredRequestKind.checkIn) {
        if (!await _claimCheckIn(id, request, current)) continue;
      }
      // Runtime admission can change while either persistence write is held.
      // Keep a recorded claim, but never dispatch after foreground return.
      if (!mayDispatch()) return;
      final bool delivered;
      try {
        delivered = await alert(
          id,
          request,
          key,
          token,
        ).timeout(timeout, onTimeout: () => false);
      } catch (_) {
        return;
      }
      if (!mayDispatch()) {
        if (delivered) {
          try {
            await dismiss(key).timeout(timeout, onTimeout: () => false);
          } catch (_) {}
        }
        return;
      }
      if (delivered) {
        keys.add(key);
      }
    }
    if (current() && policy()) {
      await store.prefs.setStringList(alertsKey(id), keys.take(256).toList());
    }
  }

  bool _notificationPolicyCurrent(String id) {
    if (_disposed || !runningAllowed || _blocked.contains(id)) return false;
    try {
      final rules = rulesFor(id);
      return rules.enabled &&
          rules.notifications &&
          !rules.quietAt(_now()) &&
          (alertsAllowed?.call(id) ?? true);
    } catch (_) {
      return false;
    }
  }

  /// Check-in reminders follow every request-alert policy except
  /// [alertsAllowed]: that hook exists because the active profile's live
  /// connection posts its own request alerts, and it has no reminder of its
  /// own to duplicate. The rule's duration must be set as well.
  bool _checkInPolicyCurrent(String id) {
    if (_disposed || !runningAllowed || _blocked.contains(id)) return false;
    try {
      final rules = rulesFor(id);
      return rules.enabled &&
          rules.notifications &&
          rules.checkInAfterMinutes != null &&
          !rules.quietAt(_now());
    } catch (_) {
      return false;
    }
  }

  static String alertKey(String id, MonitoredRequest r) =>
      'monitor:${Uri.encodeComponent(id)}:${Uri.encodeComponent(r.identity)}';
  @override
  void dispose() {
    _disposed = true;
    for (final id in _pollGenerations.keys.toList()) {
      _invalidatePoll(id);
    }
    _timer?.cancel();
    _activeGateway?.close();
    super.dispose();
  }
}
