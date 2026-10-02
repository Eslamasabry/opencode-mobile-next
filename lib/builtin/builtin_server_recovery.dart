import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../api/server_probe.dart';
import '../state/automation_policy.dart';
import '../state/profiles.dart';
import 'builtin_linux.dart';
import 'builtin_server.dart';

enum BuiltinRecoveryPhase {
  idle,
  paused,
  stopped,
  checking,
  restarting,
  waiting,
  ready,
  exhausted,
  unconfirmed,
  storageUnavailable,
}

@immutable
class BuiltinRecoveryState {
  const BuiltinRecoveryState({
    this.phase = BuiltinRecoveryPhase.idle,
    this.profileId,
    this.attempts = 0,
    this.nextAttemptAt,
  });

  final BuiltinRecoveryPhase phase;
  final String? profileId;
  final int attempts;
  final DateTime? nextAttemptAt;
  int get maxAttempts => BuiltinServerRecovery.maxAttempts;
}

typedef BuiltinRestartRecorder =
    Future<bool> Function({
      required String profileId,
      required String eventId,
      required DateTime at,
    });

/// Foreground-only recovery. The shell calls [check] on its bounded foreground
/// health timer. Android remains the authority for crash versus explicit stop.
/// An attempt is persisted before dispatch; recreation/resume never buys retries.
/// Only a confirmed explicit start through the shared starter resets the budget.
class BuiltinServerRecovery extends ChangeNotifier {
  BuiltinServerRecovery({
    required this.store,
    required this.linux,
    required this.starter,
    required this.onRestart,
    DateTime Function()? now,
    this.recoveringInterval = const Duration(seconds: 5),
    this.steadyInterval = const Duration(seconds: 45),
  }) : _now = now ?? DateTime.now {
    _manualCount = starter.manualReadyCount;
    starter.addListener(_starterChanged);
    (_owners[store.prefs] ??= {}).add(this);
  }

  static final _owners =
      Map<SharedPreferences, Set<BuiltinServerRecovery>>.identity();

  /// Closes admission synchronously, then drains in-flight preference writes.
  /// Await this before ProfileStore's scoped-key deletion sweep.
  static Future<void> suspendForProfile(
    SharedPreferences prefs,
    String profileId,
  ) {
    return Future.wait([
      for (final owner in _owners[prefs]?.toList() ?? <BuiltinServerRecovery>[])
        owner.suspendProfile(profileId),
    ]);
  }

  static const maxAttempts = 3;

  /// How soon the next check runs while a restart is under way or its
  /// outcome is unconfirmed.
  final Duration recoveringInterval;

  /// How soon the next check runs while the server is up, stopped on
  /// purpose, or out of attempts. Each check is two native status reads and
  /// a health probe (`/api/health`, then `/global/health` on OpenCode 1);
  /// every 5 s that was most of the app's traffic while a person chatted.
  /// A resume still checks at once ([setForeground]).
  final Duration steadyInterval;
  static const retryDelay = Duration(seconds: 15);
  static String keyFor(String profileId) => 'oc.builtinRecovery.$profileId';

  final ProfileStore store;
  final BuiltinLinux linux;
  final BuiltinServerStarter starter;
  final BuiltinRestartRecorder onRestart;
  final DateTime Function() _now;
  BuiltinRecoveryState _value = const BuiltinRecoveryState();
  BuiltinRecoveryState get value => _value;
  bool _foreground = false;
  bool _disposed = false;
  bool _checking = false;
  bool _storageFailed = false;
  Completer<void>? _activeCheck;
  Future<void> _nativeCancellation = Future<void>.value();
  int _generation = 0;
  late int _manualCount;
  final Set<String> _manualResets = {};
  String? _profileId;
  ServerProfile? _profile;
  bool _profileBound = false;
  Timer? _timer;
  int _scheduleId = 0;
  bool _steadyWait = false;
  AutomationPolicyController? _policy;

  /// Selects the saved phone profile even when a remote profile is selected.
  /// The shell resynchronizes this after profile edits/deletion.
  void setProfile(ServerProfile? profile) {
    _profileBound = true;
    if (identical(profile, _profile) && _profileId == profile?.id) return;
    _policy?.removeListener(_policyChanged);
    _profile = profile;
    _profileId = profile?.id;
    _generation++;
    unawaited(_cancelNative());
    _policy = profile == null
        ? null
        : AutomationPolicyController.forProfile(store.prefs, profile.id);
    _policy?.addListener(_policyChanged);
    _schedule();
  }

  Future<void> suspendProfile(String profileId) async {
    if (_profileId == profileId) setProfile(null);
    _manualResets.remove(profileId);
    await _nativeCancellation;
    await _activeCheck?.future;
  }

  Future<void> _cancelNative() {
    _nativeCancellation = _nativeCancellation.then((_) async {
      try {
        await linux.cancelServerRecovery();
      } catch (_) {}
    });
    return _nativeCancellation;
  }

  void _policyChanged() {
    _generation++;
    unawaited(_cancelNative());
    _schedule();
  }

  /// Checks now, then again after each check at a pace set by its outcome:
  /// [recoveringInterval] while a restart is in play or the app's own
  /// connection to this server is down ([connectionUnsettled]),
  /// [steadyInterval] otherwise. Only in the foreground.
  void _schedule() {
    _timer?.cancel();
    _timer = null;
    _scheduleId++;
    _steadyWait = false;
    if (_disposed || !_foreground || _profile == null) return;
    unawaited(_tick(_scheduleId, _profile));
  }

  Future<void> _tick(int schedule, ServerProfile? profile) async {
    await check(profile);
    if (_disposed ||
        !_foreground ||
        schedule != _scheduleId ||
        !identical(profile, _profile)) {
      return;
    }
    final steady = _steadyNow;
    _steadyWait = steady;
    _timer = Timer(
      steady ? steadyInterval : recoveringInterval,
      () => unawaited(_tick(schedule, profile)),
    );
  }

  bool get _steadyNow =>
      !(connectionUnsettled?.call() ?? false) &&
      switch (_value.phase) {
        BuiltinRecoveryPhase.checking ||
        BuiltinRecoveryPhase.restarting ||
        BuiltinRecoveryPhase.waiting ||
        BuiltinRecoveryPhase.unconfirmed => false,
        _ => true,
      };

  /// True while the app's own connection to this server is down: checks
  /// then run at [recoveringInterval], since a ready check is what
  /// reconnects it. Set by the owner ([PhoneServerHealing]).
  bool Function()? connectionUnsettled;

  /// Brings a steady wait forward to [recoveringInterval]: the connection
  /// to this server just dropped.
  void expedite() {
    final pending = _timer;
    if (!_steadyWait || pending == null || !pending.isActive) return;
    pending.cancel();
    _steadyWait = false;
    final schedule = _scheduleId;
    final profile = _profile;
    _timer = Timer(
      recoveringInterval,
      () => unawaited(_tick(schedule, profile)),
    );
  }

  void setForeground(bool value) {
    if (_foreground == value) return;
    _foreground = value;
    _generation++;
    if (!value) {
      unawaited(_cancelNative());
      _publish(BuiltinRecoveryPhase.paused);
    }
    _schedule();
  }

  void _starterChanged() {
    if (starter.manualReadyCount == _manualCount) return;
    _manualCount = starter.manualReadyCount;
    final id = starter.manuallyStartedProfileId;
    if (id != null) {
      _manualResets.add(id);
      for (final profile in store.profiles) {
        if (profile.id == id &&
            (!_profileBound || identical(profile, _profile))) {
          unawaited(check(profile));
          break;
        }
      }
    }
  }

  bool _eligible(ServerProfile profile, int generation) {
    if (_disposed || !_foreground || generation != _generation) return false;
    if (!store.profiles.any((p) => identical(p, profile))) return false;
    final policy = AutomationPolicyController.forProfile(
      store.prefs,
      profile.id,
    ).value;
    return policy.allows(AutomationBehavior.restartPhoneServer) &&
        policy.allows(AutomationBehavior.pollRestartHealth);
  }

  void _publish(BuiltinRecoveryPhase phase, [_Budget? budget]) {
    if (_disposed) return;
    _value = BuiltinRecoveryState(
      phase: phase,
      profileId: _profileId,
      attempts: budget?.attempts ?? _value.attempts,
      nextAttemptAt: budget?.nextAt,
    );
    notifyListeners();
  }

  Future<bool> _save(ServerProfile profile, _Budget budget) async {
    if (!store.profiles.any((p) => identical(p, profile))) return false;
    try {
      final saved = await store.prefs.setString(
        keyFor(profile.id),
        jsonEncode(budget.toJson()),
      );
      if (saved) return true;
    } catch (_) {}
    // SharedPreferences may change its cache before the durable write fails.
    // Never admit another attempt from that optimistic value.
    _storageFailed = true;
    try {
      await store.prefs.reload();
    } catch (_) {}
    return false;
  }

  /// Safe to call concurrently: only one native start/health confirmation can
  /// be in flight. A profile change invalidates the old operation immediately.
  Future<void> check(ServerProfile? profile) async {
    if (_profileBound && !identical(profile, _profile)) return;
    if (_profileId != profile?.id) {
      _profileId = profile?.id;
      _generation++;
    }
    if (_disposed) return;
    if (_checking) {
      await _activeCheck?.future;
      return;
    }
    if (!looksLikeInAppServer(profile)) {
      _publish(BuiltinRecoveryPhase.idle);
      return;
    }
    final phone = profile!;
    final generation = _generation;
    if (!_eligible(phone, generation) && !_manualResets.contains(phone.id)) {
      _publish(BuiltinRecoveryPhase.paused);
      return;
    }
    _checking = true;
    _activeCheck = Completer<void>();
    try {
      await _nativeCancellation;
      if (!_eligible(phone, generation) && !_manualResets.contains(phone.id)) {
        return;
      }
      var budget = _Budget.read(store.prefs.getString(keyFor(phone.id)));
      if (budget?.confirmedAt != null) {
        // A receipt is historical health evidence, independent of a process
        // that may since have stopped or changed its native generation.
        if (!await _recordConfirmation(phone, budget!)) {
          _publish(BuiltinRecoveryPhase.storageUnavailable, budget);
          return;
        }
        budget = budget.finished();
      }
      if (_manualResets.contains(phone.id)) {
        budget = const _Budget();
        if (!await _save(phone, budget)) {
          _publish(BuiltinRecoveryPhase.storageUnavailable);
          return;
        }
        _manualResets.remove(phone.id);
        _storageFailed = false;
      }
      if (budget == null || _storageFailed) {
        _publish(BuiltinRecoveryPhase.storageUnavailable);
        return;
      }
      if (!_eligible(phone, generation)) return;
      _publish(BuiltinRecoveryPhase.checking, budget);
      final status = await linux.status();
      if (!_eligible(phone, generation)) return;
      starter.observeStatus(status);
      if (!status.installed ||
          !status.serverRestartWanted ||
          status.serverRecoveryGeneration == null) {
        _publish(BuiltinRecoveryPhase.stopped, budget);
        return;
      }
      if (starter.starting) return;
      if (status.serverRunning) {
        final healthy = await serverProbe(
          baseUrl: phone.baseUrl,
          username: phone.username,
          password: phone.password,
        );
        if (!_eligible(phone, generation)) return;
        if (!healthy.ok) {
          _publish(BuiltinRecoveryPhase.unconfirmed, budget);
          return;
        }
        // Recheck stop intent after asynchronous health; Stop wins.
        final confirmed = await linux.status();
        if (!_eligible(phone, generation)) return;
        if (!confirmed.serverRunning || !confirmed.serverRestartWanted) {
          _publish(BuiltinRecoveryPhase.stopped, budget);
          return;
        }
        if (budget.pending) {
          if (confirmed.serverRecoveryGeneration != budget.recoveryGeneration) {
            // Another owner or an explicit start replaced this attempt. Never
            // attribute that process to an old automatic act.
            budget = budget.finished();
            if (!await _save(phone, budget)) {
              _publish(BuiltinRecoveryPhase.storageUnavailable, budget);
              return;
            }
          } else {
            await linux.confirmServerRecovery(
              expectedGeneration: budget.recoveryGeneration!,
            );
            if (!_eligible(phone, generation)) return;
          }
        }
        await _confirm(phone, budget, generation);
        return;
      }
      // A pending dispatch with no process is a confirmed failed attempt.
      if (budget.pending) {
        budget = budget.finished();
        if (!await _save(phone, budget)) {
          _publish(BuiltinRecoveryPhase.storageUnavailable, budget);
          return;
        }
      }
      if (budget.attempts >= maxAttempts) {
        _publish(BuiltinRecoveryPhase.exhausted, budget);
        return;
      }
      if (budget.nextAt != null && _now().isBefore(budget.nextAt!)) {
        _publish(BuiltinRecoveryPhase.waiting, budget);
        return;
      }
      final at = _now();
      budget = _Budget(
        attempts: budget.attempts + 1,
        nextAt: at.add(retryDelay * (budget.attempts + 1)),
        pending: true,
        recoveryGeneration: status.serverRecoveryGeneration,
        eventId:
            'builtin-restart:${at.microsecondsSinceEpoch}:${budget.attempts + 1}',
      );
      if (!await _save(phone, budget)) {
        _publish(BuiltinRecoveryPhase.storageUnavailable, budget);
        return;
      }
      if (!_eligible(phone, generation)) return;
      _publish(BuiltinRecoveryPhase.restarting, budget);
      final failure = await starter.start(
        phone,
        automatic: true,
        recoveryGeneration: status.serverRecoveryGeneration,
        stillWanted: () => _eligible(phone, generation),
      );
      if (!_eligible(phone, generation)) return;
      if (failure == null) {
        final confirmed = await linux.status();
        if (!_eligible(phone, generation)) return;
        if (confirmed.serverRunning && confirmed.serverRestartWanted) {
          await _confirm(phone, budget, generation);
          return;
        }
      }
      // Do not dispatch again until a later status proves the process absent.
      _publish(BuiltinRecoveryPhase.unconfirmed, budget);
    } catch (_) {
      if (_eligible(phone, generation)) {
        _publish(BuiltinRecoveryPhase.unconfirmed);
      }
    } finally {
      _checking = false;
      _activeCheck?.complete();
      _activeCheck = null;
    }
  }

  Future<bool> _recordConfirmation(ServerProfile phone, _Budget budget) async {
    final recorded = await onRestart(
      profileId: phone.id,
      eventId: budget.eventId!,
      at: budget.confirmedAt!,
    );
    if (!recorded) return false;
    return _save(phone, budget.finished());
  }

  Future<void> _confirm(
    ServerProfile phone,
    _Budget budget,
    int generation,
  ) async {
    if (budget.pending && budget.eventId != null) {
      starter.confirmRecovered(phone);
      // Persist the health-confirmed receipt before asking the existing act
      // store to record it. A pause or later crash cannot erase that evidence.
      final receipt = budget.confirmed(_now());
      if (!await _save(phone, receipt)) {
        _publish(BuiltinRecoveryPhase.storageUnavailable, budget);
        return;
      }
      if (!await _recordConfirmation(phone, receipt)) {
        _publish(BuiltinRecoveryPhase.storageUnavailable, receipt);
        return;
      }
    }
    if (_eligible(phone, generation)) {
      _publish(BuiltinRecoveryPhase.ready, budget);
    }
  }

  @override
  void dispose() {
    _disposed = true;
    _owners[store.prefs]?.remove(this);
    _generation++;
    _timer?.cancel();
    _policy?.removeListener(_policyChanged);
    unawaited(_cancelNative());
    starter.removeListener(_starterChanged);
    super.dispose();
  }
}

class _Budget {
  const _Budget({
    this.attempts = 0,
    this.nextAt,
    this.pending = false,
    this.eventId,
    this.recoveryGeneration,
    this.confirmedAt,
  });
  final int attempts;
  final DateTime? nextAt;
  final bool pending;
  final String? eventId;
  final int? recoveryGeneration;
  final DateTime? confirmedAt;
  _Budget confirmed(DateTime at) => _Budget(
    attempts: attempts,
    nextAt: nextAt,
    pending: pending,
    eventId: eventId,
    recoveryGeneration: recoveryGeneration,
    confirmedAt: at,
  );
  _Budget finished() => _Budget(attempts: attempts, nextAt: nextAt);
  Map<String, Object?> toJson() => {
    'version': 1,
    'attempts': attempts,
    'nextAt': nextAt?.millisecondsSinceEpoch,
    'pending': pending,
    'eventId': eventId,
    'recoveryGeneration': recoveryGeneration,
    'confirmedAt': confirmedAt?.millisecondsSinceEpoch,
  };
  static _Budget? read(String? raw) {
    if (raw == null) return const _Budget();
    try {
      final json = jsonDecode(raw);
      if (json is! Map ||
          json['version'] != 1 ||
          json['attempts'] is! int ||
          json['attempts'] < 0 ||
          json['attempts'] > BuiltinServerRecovery.maxAttempts ||
          json['pending'] is! bool ||
          (json['nextAt'] != null && json['nextAt'] is! int) ||
          (json['confirmedAt'] != null &&
              (json['confirmedAt'] is! int || json['pending'] != true)) ||
          (json['pending'] == true &&
              (json['eventId'] is! String ||
                  json['recoveryGeneration'] is! int))) {
        return null;
      }
      return _Budget(
        attempts: json['attempts'] as int,
        nextAt: json['nextAt'] == null
            ? null
            : DateTime.fromMillisecondsSinceEpoch(json['nextAt'] as int),
        pending: json['pending'] as bool,
        eventId: json['eventId'] as String?,
        recoveryGeneration: json['recoveryGeneration'] as int?,
        confirmedAt: json['confirmedAt'] == null
            ? null
            : DateTime.fromMillisecondsSinceEpoch(json['confirmedAt'] as int),
      );
    } catch (_) {
      return null;
    }
  }
}
